package com.stressml.db;

import com.stressml.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {
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
