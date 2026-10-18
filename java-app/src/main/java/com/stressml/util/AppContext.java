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
    private java.util.concurrent.ScheduledFuture<?> pendingWindow;
    private final Object cycleLock = new Object();
    private volatile long generation;
    private long lastWindowNanos;
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
            calibrationSession = !baseline.isCalibrated();
            sessionId = sessions.start(userId, calibrationSession, isDemoMode());
            sessionStart = LocalDateTime.now();
            updateCalibrationStatus(completed);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not start session; monitoring has not started", e);
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "window-cycle"); t.setDaemon(true); return t; });

        setMonitoring(true);
    }

    public void setMonitoring(boolean on) {
        synchronized (cycleLock) {
            generation++;
            collecting = false;
            if (pendingWindow != null) pendingWindow.cancel(false);
            monitoring.stop();
            aggregator.discardWindow();
            if (on && !closed && sessionId > 0) {
                if (!isDemoMode()) monitoring.start();
                collecting = isDemoMode() || monitoring.isActive();
                lastWindowNanos = System.nanoTime();
                if (collecting) scheduleWindow(generation);
            }
            boolean active = collecting;
            Platform.runLater(() -> {
                monitoringActive.set(active);
                predictionStatus.set(active ? "Waiting for a full measurement window" : "Monitoring paused");
            });
        }
    }

    private void scheduleWindow(long expectedGeneration) {
        long period = TimeUnit.SECONDS.toNanos(isDemoMode() ? 3 : config.windowSeconds());
        long delay = Math.max(0L, lastWindowNanos + period - System.nanoTime());
        pendingWindow = scheduler.schedule(() -> cycleSafely(expectedGeneration), delay, TimeUnit.NANOSECONDS);
    }

    private void cycleSafely(long expectedGeneration) {
        try { cycle(expectedGeneration); }
        catch (Exception e) {
            log.error("Measurement/prediction cycle failed ({})", e.getClass().getSimpleName());
            Platform.runLater(() -> {
                if (generation == expectedGeneration) predictionStatus.set("Measurement could not be saved; check local database/backend");
            });
        } finally {
            synchronized (cycleLock) {
                if (collecting && !closed && generation == expectedGeneration) scheduleWindow(expectedGeneration);
            }
        }
    }

    private void cycle(long expectedGeneration) throws SQLException {
        FeatureWindow w, input = new FeatureWindow();
        Map<String, Double> dev;
        List<double[]> hist;
        int observationWindows;
        synchronized (cycleLock) {
            if (!collecting || closed || generation != expectedGeneration) return;
            w = isDemoMode() ? demo.next(calibrationSession) : aggregator.buildWindow();
            lastWindowNanos = System.nanoTime();
            w.sessionId = sessionId;
            features.insert(w);
            if (calibrationSession) {
                baseline.addCalibrationWindow(w);
                if (baseline.hasEnoughCalibrationData()) {
                    baselines.save(userId, baseline.computeProfile()); calibrationSession = false;
                }
                String status = baseline.isCalibrated() ? "Calibrated" :
                        "Calibrating: " + baseline.calibrationWindows() + "/" + config.baselineMinWindows() + " windows";
                Platform.runLater(() -> calibrationStatus.set(status));
            }
            dev = baseline.deviations(w);
            hist = predictions.sessionScores(userId, 10);
            input.id = w.id; input.activeAppCategory = w.activeAppCategory;
            input.recordedSwellFeatures = observation.add(w.toSwellFeatureMap());
            observationWindows = observation.windows();
        }

        boolean empty = w.toSwellFeatureMap().entrySet().stream()
                .filter(e -> !e.getKey().endsWith("Ratio")).allMatch(e -> e.getValue() == 0);
        Optional<Prediction> result = empty ? Optional.empty() : ml.predict(config.participantCode(), input, null,
                hist.stream().map(a -> a[1]).toList(), hist.stream().map(a -> a[4]).toList(),
                hist.stream().map(a -> (int) a[3]).toList(), isDemoMode());
        Prediction p = result.orElseGet(() -> {
            Prediction unavailable = new Prediction();
            unavailable.stressLabel = empty ? "No interaction in completed window" : "Prediction unavailable";
            unavailable.recommendations = List.of(empty ? "Waiting for measurable interaction." : ml.lastError());
            return unavailable;
        });
        synchronized (cycleLock) {
            // HTTP happens outside this lock: pause/delete stays responsive and
            // an old response can never repopulate data after deletion or resume.
            if (!collecting || closed || generation != expectedGeneration) return;
            if (result.isPresent()) {
                p.inputFeatures = input.toSwellFeatureMap(); p.observationWindows = observationWindows;
                predictions.insert(p);
                log.info("Prediction saved: model={}, mode={}", p.modelName, p.demoMode ? "DEMO" : "REAL_SWELL");
            }
            Platform.runLater(() -> {
                if (generation != expectedGeneration || closed) return;
                mlOnline.set(result.isPresent());
                predictionStatus.set(result.isPresent() ? p.stressLabel :
                        empty ? "No interaction in completed window" : ml.lastError());
                latestWindow.set(w); latestPrediction.set(p); latestDeviations.set(dev);
                sessionWindows.add(w);
                if (result.isPresent()) sessionPredictions.add(p);
            });
            if (result.isPresent() && !"reference_baseline".equals(p.predictionKind)
                    && p.stressScore >= config.highStressThreshold() && highStressListener != null)
                highStressListener.accept(p);
        }
    }

    private java.util.function.Consumer<Prediction> highStressListener;
    public void setHighStressListener(java.util.function.Consumer<Prediction> listener) {
        this.highStressListener = listener;
    }

    public void recordBreak() {
        synchronized (cycleLock) { sessionBreaks.set(sessionBreaks.get() + 1); aggregator.resetContinuousWork(); }
    }

    public void submitSelfReport(int level) {
        if (level < 0 || level > 2) throw new IllegalArgumentException("Ordinal report must be 0, 1 or 2");
        synchronized (cycleLock) {
            if (sessionId <= 0 || closed) return;
            FeatureWindow w = latestWindow.get();
            try {
                sessions.saveSelfReport(sessionId, level, "ORDINAL-3");
                if (w != null && w.id > 0) { features.saveOrdinalSelfReport(w.id, sessionId, level); w.stressLabel = level; }
            } catch (SQLException e) { predictionStatus.set("Self-report could not be saved"); }
        }
    }

    public void deleteAllData() {
        setMonitoring(false);
        synchronized (cycleLock) {
            try {
                users.deleteAllData(userId); baseline.setProfile(null); sessionId = 0; userId = 0;
                Platform.runLater(() -> {
                    sessionWindows.clear(); sessionPredictions.clear(); latestWindow.set(new FeatureWindow());
                    latestPrediction.set(new Prediction()); latestDeviations.set(Map.of());
                    predictionStatus.set("Data deleted. Restart the app for a new session.");
                });
            } catch (SQLException e) { predictionStatus.set("Data deletion failed; monitoring remains paused"); }
        }
    }

    private void updateCalibrationStatus(int completed) {
        Platform.runLater(() -> calibrationStatus.set(baseline.isCalibrated() ? "Calibrated" :
                "Calibration session " + (completed + 1) + " of " + config.calibrationSessions()));
    }

    public void shutdown() {
        synchronized (cycleLock) {
            if (closed) return;
            closed = true; generation++; collecting = false;
            if (scheduler != null) scheduler.shutdownNow();
            monitoring.stop();
            try { if (sessionId > 0) sessions.end(sessionId, sessionBreaks.get(), null, null); }
            catch (SQLException e) { log.warn("Could not close session ({})", e.getClass().getSimpleName()); }
            db.close();
        }
    }
}
