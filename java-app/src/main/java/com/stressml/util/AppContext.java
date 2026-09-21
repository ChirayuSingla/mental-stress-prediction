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
    public final StringProperty calibrationStatus = new SimpleStringProperty("Not calibrated");
    public final IntegerProperty sessionBreaks = new SimpleIntegerProperty(0);
    public final ObjectProperty<Map<String, Double>> latestDeviations = new SimpleObjectProperty<>(Map.of());

    private int userId, sessionId; private boolean calibrationSession;
    private LocalDateTime sessionStart;
    private ScheduledExecutorService scheduler;

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
            log.error("Could not start session", e);
        }
        mlOnline.set(ml.isHealthy());
        int period = isDemoMode() ? 3 : config.windowSeconds();   // demo: fast-forward one "minute" every 3 s
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "window-cycle"); t.setDaemon(true); return t; });
        scheduler.scheduleAtFixedRate(this::cycleSafely, period, period, TimeUnit.SECONDS);
        setMonitoring(true);
    }

    public void setMonitoring(boolean on) {
        if (on) { if (!isDemoMode()) monitoring.start(); }
        else { monitoring.stop(); }
        Platform.runLater(() -> monitoringActive.set(on));
    }

    private void cycleSafely() { try { cycle(); } catch (Exception e) { log.error("Cycle failed", e); } }

    private void cycle() throws SQLException {
        if (!monitoringActive.get()) return;
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

        Prediction p = ml.predict(config.participantCode(), w, baseline.profile(), scores, durs, breaks, isDemoMode()).orElseGet(() -> offlinePrediction(w));
        mlOnline.set(!"offline-heuristic".equals(p.modelName));
        if (p.recommendations.isEmpty()) p.recommendations = recEngine.recommend(p, w, dev);
        predictions.insert(p);
        Platform.runLater(() -> {
            latestWindow.set(w); latestPrediction.set(p); latestDeviations.set(dev);
            sessionWindows.add(w); sessionPredictions.add(p);
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

    /** Transparent fallback when the ML service is down: keeps the app usable, clearly labelled as heuristic. */
    private Prediction offlinePrediction(FeatureWindow w) {
        Prediction p = new Prediction(); p.featureId = w.id; p.modelName = "offline-heuristic"; p.dataMode = "n/a"; p.demoMode = isDemoMode();
        Map<String, Double> dev = baseline.deviations(w);
        double risk = Math.max(0, dev.getOrDefault("dev_error_rate", 0.0)) + Math.max(0, -dev.getOrDefault("dev_typing_speed_wpm", 0.0))
                + Math.max(0, dev.getOrDefault("dev_app_switch_rate", 0.0)) * 0.5;
        p.stressClass = risk < 0.25 ? 0 : risk < 0.6 ? 1 : 2;
        p.stressLabel = new String[]{"Low Stress", "Moderate Stress", "High Stress"}[p.stressClass] + " (heuristic)";
        p.probLow = p.stressClass == 0 ? 0.7 : 0.15; p.probModerate = p.stressClass == 1 ? 0.7 : 0.15; p.probHigh = p.stressClass == 2 ? 0.7 : 0.15;
        p.stressScore = 100 * (0.5 * p.probModerate + p.probHigh); p.fatigueScore = Math.min(100, w.continuousWorkS / 72.0);
        return p;
    }

    public void recordBreak() { sessionBreaks.set(sessionBreaks.get() + 1); aggregator.resetContinuousWork(); }

    public void submitSelfReport(int level) {
        FeatureWindow w = latestWindow.get(); if (w != null) w.stressLabel = level;
        try { sessions.end(sessionId, sessionBreaks.get(), level, "VAS-3"); } catch (SQLException e) { log.warn("self-report save failed", e); }
    }

    public void deleteAllData() {
        try { users.deleteAllData(userId); baseline.setProfile(null); Platform.runLater(() -> { sessionWindows.clear(); sessionPredictions.clear(); }); }
        catch (SQLException e) { log.error("Delete failed", e); }
    }

    private void updateCalibrationStatus(int completed) {
        Platform.runLater(() -> calibrationStatus.set(baseline.isCalibrated() ? "Calibrated" :
                "Calibration session " + (completed + 1) + " of " + config.calibrationSessions()));
    }

    public void shutdown() {
        if (scheduler != null) scheduler.shutdownNow();
        monitoring.stop();
        try { sessions.end(sessionId, sessionBreaks.get(), null, null); } catch (SQLException ignored) { }
        db.close();
        Platform.exit();
    }
}
