package com.stressml.util;
import com.stressml.FxTestSupport;
import com.stressml.baseline.BaselineEngine;
import com.stressml.config.AppConfig;
import com.stressml.db.DatabaseManager;
import com.stressml.demo.SyntheticDataGenerator;
import com.stressml.feature.FeatureAggregator;
import com.stressml.ml.MlClient;
import com.stressml.model.*;
import com.stressml.monitor.MonitoringService;
import com.stressml.recommendation.RecommendationEngine;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="javafx.ui.tests", matches="true")
class AppContextTest {
    AppContext ctx; FakeMl client; DatabaseManager db;
    final Map<String,String> previous = new HashMap<>();
    void property(String key, String value) { previous.put(key, System.getProperty(key)); System.setProperty(key,value); }
    static class FakeMonitor extends MonitoringService {
        boolean active;
        FakeMonitor(FeatureAggregator a, AppConfig c) { super(a,c); }
        @Override public synchronized void start() { active=true; }
        @Override public synchronized void stop() { active=false; }
        @Override public boolean isActive() { return active; }
    }
    static class FakeMl extends MlClient {
        CountDownLatch requested = new CountDownLatch(1), release = new CountDownLatch(0), finished = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        FakeMl(AppConfig c) { super(c); }
        @Override public Optional<Prediction> predict(String code, FeatureWindow w, BaselineProfile b,
                    List<Double> s, List<Double> d, List<Integer> br, boolean demo) {
            calls.incrementAndGet(); requested.countDown();
            try { release.await(4, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            Prediction p = new Prediction(); p.featureId=w.id; p.stressScore=3.2;
            p.modelName="IsolatedTest"; p.stressLabel="Test fixture";
            finished.countDown(); return Optional.of(p);
        }
    }
    @BeforeEach void setup() throws Exception {
        property("db.url","jdbc:h2:mem:lifecycle_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        property("monitor.window.seconds","1"); property("app.mode","real");
        property("app.participant.code","ISOLATED");
        var config=AppConfig.load(); db=new DatabaseManager(config); db.initialiseSchemaIfEmbedded();
        var agg=new FeatureAggregator(1,20); client=new FakeMl(config);
        FxTestSupport.run(() -> ctx=new AppContext(config,db,client,new BaselineEngine(30),
                new RecommendationEngine(),agg,new FakeMonitor(agg,config),new SyntheticDataGenerator(1)));
    }
    long count(String table) throws Exception {
        try(var c=db.connection();var st=c.createStatement();var r=st.executeQuery("SELECT COUNT(*) FROM "+table)) {r.next();return r.getLong(1);}
    }
    @AfterEach void cleanup() throws Exception {
        client.release.countDown();
        FxTestSupport.run(ctx::shutdown);
        previous.forEach((k,v)->{if(v==null)System.clearProperty(k);else System.setProperty(k,v);});
    }
    @Test void pauseResumeWaitsForACompleteNewInterval() throws Exception {
        FxTestSupport.run(ctx::startSession); FxTestSupport.run(()->ctx.setMonitoring(false));
        Thread.sleep(1100);
        assertEquals(0,count("behavior_features"));
        FxTestSupport.run(()->ctx.setMonitoring(true));
        ctx.aggregator.keyboardStats().onKeyPress(System.currentTimeMillis(),false,true,false,false,false);
        Thread.sleep(150);
        assertEquals(0,count("behavior_features"));
        assertTrue(client.requested.await(3,TimeUnit.SECONDS));
        assertEquals(1,client.calls.get());
    }
    @Test void deletionDoesNotWaitForHttpOrAllowAnOldResponseToRecreateData() throws Exception {
        client.release=new CountDownLatch(1);
        FxTestSupport.run(ctx::startSession);
        ctx.aggregator.keyboardStats().onKeyPress(System.currentTimeMillis(),false,true,false,false,false);
        assertTrue(client.requested.await(3,TimeUnit.SECONDS));
        long before=System.nanoTime(); FxTestSupport.run(ctx::deleteAllData);
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-before)<1000);
        assertEquals(0,count("users")); assertEquals(0,count("behavior_features"));
        client.release.countDown(); assertTrue(client.finished.await(2,TimeUnit.SECONDS)); Thread.sleep(100);
        assertEquals(0,count("predictions")); assertEquals(0,count("swell_prediction_inputs"));
        assertFalse(ctx.monitoringActive.get());
    }
    @Test void emptyIntervalStoresMeasurementsButInventsNoPrediction() throws Exception {
        FxTestSupport.run(ctx::startSession);
        Thread.sleep(1250); FxTestSupport.run(()->{});
        assertEquals(1,count("behavior_features")); assertEquals(0,count("predictions"));
        assertEquals(0,client.calls.get());
        assertTrue(Double.isNaN(ctx.latestPrediction.get().stressScore));
        assertEquals("No interaction in completed window",ctx.predictionStatus.get());
    }
}
