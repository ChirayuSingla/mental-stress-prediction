package com.stressml.util;

import com.stressml.baseline.BaselineEngine;
import com.stressml.config.AppConfig;
import com.stressml.db.*;
import com.stressml.demo.SyntheticDataGenerator;
import com.stressml.feature.FeatureAggregator;
import com.stressml.ml.MlClient;
import com.stressml.model.FeatureWindow;
import com.stressml.model.Prediction;
import com.stressml.monitor.MonitoringService;
import com.stressml.recommendation.RecommendationEngine;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates one processing cycle per window:
 * monitor → aggregate → store → baseline deviation → ML predict → recommend → notify UI.
 * All JavaFX-bound properties are updated on the FX thread.
 */
public class AppContext {
    private static final Logger log = LoggerFactory.getLogger(AppContext.class);
    public final AppConfig config; public final DatabaseManager db; public final MlClient ml; public final BaselineEngine baseline;
    public final RecommendationEngine recEngine; public final FeatureAggregator aggregator; public final MonitoringService monitoring;
    public final SyntheticDataGenerator demo;
    public final UserRepository users; public final SessionRepository sessions; public final FeatureRepository features;
    public final PredictionRepository predictions; public final BaselineRepository baselines;

    public final ObjectProperty<Prediction> latestPrediction = new SimpleObjectProperty<>(new Prediction());
    public final ObjectProperty<FeatureWindow> latestWindow = new SimpleObjectProperty<>(new FeatureWindow());
    public final ObservableList<FeatureWindow> sessionWindows = FXCollections.observableArrayList();
    public final ObservableList<Prediction> sessionPredictions = FXCollections.observableArrayList();
    public final BooleanProperty monitoringActive = new SimpleBooleanProperty(false);
    public final BooleanProperty mlOnline = new SimpleBooleanProperty(false);
    public final StringProperty predictionStatus = new SimpleStringProperty("Waiting for first completed window");
    private volatile boolean collecting;
    private volatile boolean closed;
    public final StringProperty calibrationStatus = new SimpleStringProperty("Not calibrated");
    public final IntegerProperty sessionBreaks = new SimpleIntegerProperty(0);
    public final ObjectProperty<Map<String, Double>> latestDeviations = new SimpleObjectProperty<>(Map.of());

    private int userId, sessionId; private boolean calibrationSession;
    private LocalDateTime sessionStart;
    private ScheduledExecutorService scheduler;
    private final com.stressml.feature.SwellObservation observation = new com.stressml.feature.SwellObservation();

    public AppContext(AppConfig config, DatabaseManager db, MlClient ml, BaselineEngine baseline, RecommendationEngine recEngine,
                      FeatureAggregator aggregator, MonitoringService monitoring, SyntheticDataGenerator demo) {
        this.config = config; this.db = db; this.ml = ml; this.baseline = baseline; this.recEngine = recEngine;
        this.aggregator = aggregator; this.monitoring = monitoring; this.demo = demo;
        users = new UserRepository(db); sessions = new SessionRepository(db); features = new FeatureRepository(db);
        predictions = new PredictionRepository(db); baselines = new BaselineRepository(db);
    }

    public boolean isDemoMode() { return config.demoMode(); }
    public int userId() { return userId; }
    public int sessionId() { return sessionId; }
    public Duration sessionDuration() { return sessionStart == null ? Duration.ZERO : Duration.between(sessionStart, LocalDateTime.now()); }
    public boolean isCalibrationSession() { return calibrationSession; }

    public void startSession() {
        try {
            userId = users.findOrCreate(config.participantCode() + (isDemoMode() ? "_DEMO" : ""));
            baseline.setProfile(baselines.load(userId));
            int completed = sessions.countCompleted(userId);
            calibrationSession = !baseline.isCalibrated() && completed < config.calibrationSessions();
            sessionId = sessions.start(userId, calibrationSession, isDemoMode());
            sessionStart = LocalDateTime.now();
            updateCalibrationStatus(completed);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not start session; monitoring has not started", e);
        }

        int period = isDemoMode() ? 3 : config.windowSeconds();   // demo: fast-forward one "minute" every 3 s
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "window-cycle"); t.setDaemon(true); return t; });
        scheduler.scheduleAtFixedRate(this::cycleSafely, period, period, TimeUnit.SECONDS);
        setMonitoring(true);
    }

    public void setMonitoring(boolean on) {
        if (on && sessionId <= 0) { predictionStatus.set("Restart the app to begin a new consented session"); return; }
        if (on) { if (!isDemoMode()) monitoring.start(); }
        else { monitoring.stop(); }
        collecting = on && (isDemoMode() || monitoring.isActive());
        if (on) aggregator.buildWindow(); // discard paused/partial accumulators
        Platform.runLater(() -> monitoringActive.set(collecting));
    }

    private void cycleSafely() { try { cycle(); } catch (Exception e) { log.error("Cycle failed", e); } }

    private void cycle() throws SQLException {
        if (!collecting) return;
        FeatureWindow w = isDemoMode() ? demo.next(calibrationSession) : aggregator.buildWindow();
        w.sessionId = sessionId;
        features.insert(w);
        if (calibrationSession) {
            baseline.addCalibrationWindow(w);
            if (baseline.hasEnoughCalibrationData()) { baselines.save(userId, baseline.computeProfile()); calibrationSession = false; }
            Platform.runLater(() -> calibrationStatus.set(baseline.isCalibrated() ? "Calibrated (" + baseline.calibrationWindows() + " windows)"
                    : "Calibrating: " + baseline.calibrationWindows() + "/" + config.baselineMinWindows() + " windows"));
        }
        Map<String, Double> dev = baseline.deviations(w);

        List<double[]> hist = predictions.sessionScores(userId, 10);
        List<Double> scores = hist.stream().map(a -> a[1]).toList();
        List<Double> durs = hist.stream().map(a -> a[4]).toList();
        List<Integer> breaks = hist.stream().map(a -> (int) a[3]).toList();

        FeatureWindow input = new FeatureWindow();
        input.id = w.id; input.activeAppCategory = w.activeAppCategory;
        input.recordedSwellFeatures = observation.add(w.toSwellFeatureMap());
        Optional<Prediction> result = ml.predict(config.participantCode(), input, null, scores, durs, breaks, isDemoMode());
        Prediction p = result.orElseGet(() -> {
            Prediction unavailable = new Prediction();
            unavailable.stressLabel = "Prediction unavailable";
            unavailable.recommendations = List.of(ml.lastError());
            return unavailable;
        });
        if (result.isPresent()) {
            p.inputFeatures = input.toSwellFeatureMap(); p.observationWindows = observation.windows();
            predictions.insert(p);
            log.info("Real prediction saved: feature={}, score={}, model={}, data={}", w.id, p.stressScore, p.modelName, p.dataMode);
        }
        Platform.runLater(() -> {
            mlOnline.set(result.isPresent());
            predictionStatus.set(result.isPresent() ? "Real SWELL model connected" : ml.lastError());
            latestWindow.set(w); latestPrediction.set(p); latestDeviations.set(dev);
            sessionWindows.add(w);
            if (result.isPresent()) sessionPredictions.add(p);
        });

        boolean isHigh = p.stressClass == 2 || p.stressScore >= config.highStressThreshold();
        if (isHigh && highStressListener != null) {
            highStressListener.accept(p);
        }
    }

    private java.util.function.Consumer<Prediction> highStressListener;
    public void setHighStressListener(java.util.function.Consumer<Prediction> listener) {
        this.highStressListener = listener;
    }

    public void recordBreak() { sessionBreaks.set(sessionBreaks.get() + 1); aggregator.resetContinuousWork(); }

    public void submitSelfReport(int level) {
        FeatureWindow w = latestWindow.get(); if (w != null) w.stressLabel = level;
        try { sessions.saveSelfReport(sessionId, level, "ORDINAL-3"); } catch (SQLException e) { log.warn("self-report save failed", e); }
    }

    public void deleteAllData() {
        setMonitoring(false);
        try { users.deleteAllData(userId); baseline.setProfile(null); sessionId = 0; userId = 0;
            Platform.runLater(() -> { sessionWindows.clear(); sessionPredictions.clear();
                latestPrediction.set(new Prediction()); predictionStatus.set("Data deleted. Restart the app for a new session."); }); }
        catch (SQLException e) { log.error("Delete failed", e); }
    }

    private void updateCalibrationStatus(int completed) {
        Platform.runLater(() -> calibrationStatus.set(baseline.isCalibrated() ? "Calibrated" :
                "Calibration session " + (completed + 1) + " of " + config.calibrationSessions()));
    }

    public synchronized void shutdown() {
        if (closed) return;
        closed = true; collecting = false;
        if (scheduler != null) scheduler.shutdownNow();
        monitoring.stop();
        try { if (sessionId > 0) sessions.end(sessionId, sessionBreaks.get(), null, null); } catch (SQLException ignored) { }
        db.close();
        Platform.exit();
    }
}
