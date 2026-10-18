package com.stressml.db;

import com.stressml.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {
    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="stressml.integration", matches="true")
    void actualApiDemoCyclePersistsWithoutTouchingPersonalDatabase() throws Exception {
        var config=AppConfig.load();
        try (DatabaseManager db=new DatabaseManager(config)) {
            db.initialiseSchemaIfEmbedded();
            int user=new UserRepository(db).findOrCreate("EXPLICIT_DEMO_FIXTURE");
            int session=new SessionRepository(db).start(user,true,true);
            var w=new com.stressml.demo.SyntheticDataGenerator(60).next(true);w.sessionId=session;
            new FeatureRepository(db).insert(w);
            new PredictionRepository(db).sessionScores(user,10);
            var result=new com.stressml.ml.MlClient(config).predict("EXPLICIT_DEMO_FIXTURE",w,null,
                    java.util.List.of(),java.util.List.of(),java.util.List.of(),true);
            assertTrue(result.isPresent());
            new PredictionRepository(db).insert(result.orElseThrow());
        }
    }
    @Test void deletionCascadesOnlyForTheChosenParticipantAndDemoNeverEntersRealHistory() throws Exception {
        try (DatabaseManager db = new DatabaseManager(AppConfig.load())) {
            db.initialiseSchemaIfEmbedded();
            var users=new UserRepository(db); var sessions=new SessionRepository(db);
            int first=users.findOrCreate("DELETE_TEST"), sentinel=users.findOrCreate("KEEP_TEST");
            for (int user : new int[]{first,sentinel}) {
                int session=sessions.start(user,false,user==sentinel);
                var w=new com.stressml.model.FeatureWindow(); w.sessionId=session; w.windowStart=java.time.LocalDateTime.now();
                new FeatureRepository(db).insert(w);
                sessions.saveSelfReport(session,2,"ORDINAL-3");
                assertNull(sessions.recent(user,1).get(0).endedAt);
                sessions.end(session,0,null,null);
                assertEquals(2,sessions.recent(user,1).get(0).selfReportStress);
                var p=new com.stressml.model.Prediction();p.featureId=w.id;p.stressScore=3.;p.modelName="Fixture";
                p.recommendations=java.util.List.of("fixture");new PredictionRepository(db).insert(p);
            }
            assertTrue(new PredictionRepository(db).sessionScores(sentinel,10).isEmpty());
            users.deleteAllData(first);
            try(var c=db.connection();var st=c.createStatement()) {
                for (String table : new String[]{"users","sessions","behavior_features","swell_feature_windows","predictions","swell_prediction_inputs","recommendations"})
                    try(var rs=st.executeQuery("SELECT COUNT(*) FROM "+table)){assertTrue(rs.next());assertEquals(1,rs.getInt(1),table);}
            }
        }
    }

    @Test
    void retainsMeasuredInputsAndRegressionScale() throws Exception {
        try (DatabaseManager db = new DatabaseManager(AppConfig.load())) {
            db.initialiseSchemaIfEmbedded();
            int user = new UserRepository(db).findOrCreate("ISOLATED_TEST");
            int session = new SessionRepository(db).start(user, false, false);
            var w = new com.stressml.model.FeatureWindow();
            w.sessionId = session; w.snChars = 19.; w.windowStart = java.time.LocalDateTime.now();
            var features = new FeatureRepository(db); features.insert(w);
            assertEquals(19., features.bySession(session).get(0).toSwellFeatureMap().get("SnChars"));
            var p = new com.stressml.model.Prediction(); p.featureId = w.id;
            p.stressScore = 2.4; p.modelName = "TestRegressor";
            p.inputFeatures = w.toSwellFeatureMap(); p.observationWindows = 2;
            new PredictionRepository(db).insert(p);
            assertEquals(2.4, new PredictionRepository(db).sessionScores(user, 10).get(0)[1]);
            try (var c = db.connection(); var st = c.createStatement(); var rs = st.executeQuery("SELECT observation_windows FROM swell_prediction_inputs")) {
                assertTrue(rs.next()); assertEquals(2, rs.getInt(1));
            }
        }
    }

    @Test
    void testH2SchemaBootstrap() throws Exception {
        AppConfig config = AppConfig.load();
        try (DatabaseManager db = new DatabaseManager(config)) {
            db.initialiseSchemaIfEmbedded();
            try (Connection conn = db.connection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT count(*) FROM users")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 0);
            }
        }
    }
}
