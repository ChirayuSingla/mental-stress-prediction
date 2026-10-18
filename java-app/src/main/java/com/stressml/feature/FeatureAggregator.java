package com.stressml.feature;

import com.stressml.model.FeatureWindow;
import com.stressml.monitor.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Converts raw monitor snapshots into one FeatureWindow per interval.
 * Also tracks work/idle/continuous-work durations across windows.
 */
public class FeatureAggregator {
    private final int windowSeconds, idleThresholdSeconds;
    private final KeyboardStats keyboardStats = new KeyboardStats();
    private final MouseStats mouseStats = new MouseStats();
    private final ApplicationMonitor appMonitor = new ApplicationMonitor(new ActiveWindowProvider(), new AppCategoryMapper());
    private double continuousWorkS;
    private long lastKeyActivity = -1;

    public FeatureAggregator(int windowSeconds, int idleThresholdSeconds) {
        this.windowSeconds = windowSeconds; this.idleThresholdSeconds = idleThresholdSeconds;
    }

    public KeyboardStats keyboardStats() { return keyboardStats; }
    public MouseStats mouseStats() { return mouseStats; }
    public ApplicationMonitor applicationMonitor() { return appMonitor; }

    /** Builds the window from current accumulators and resets them. */
    public FeatureWindow buildWindow() {
        return build(keyboardStats.drain(), mouseStats.drain(), appMonitor.drain(), LocalDateTime.now());
    }

    /** Pure function used by tests and demo mode. */
    public FeatureWindow build(KeyboardStats.Snapshot k, MouseStats.Snapshot m, ApplicationMonitor.Snapshot a, LocalDateTime start) {
        FeatureWindow w = new FeatureWindow();
        w.windowStart = start; w.windowSeconds = windowSeconds;
        double minutes = windowSeconds / 60.0;

        // ---- keyboard ----
        w.keystrokesPerMin = k.keyPresses() / minutes;
        w.typingSpeedWpm = w.keystrokesPerMin / 5.0;                       // standard 5-char word convention
        w.avgKeyHoldMs = mean(k.holdMs());
        List<Long> lat = k.latencyMs().stream().filter(x -> x < 1500).toList();  // inter-key latency inside bursts
        w.avgKeyLatencyMs = mean(lat);
        w.typingRhythmCv = cv(lat);
        w.backspaceRate = k.keyPresses() == 0 ? 0 : (double) k.corrections() / k.keyPresses();
        w.errorRate = w.backspaceRate;                                     // correction-based proxy (no text stored)
        w.pauseCount = k.pauseCount();
        w.avgPauseMs = k.pauseCount() == 0 ? 0 : (double) k.totalPauseMs() / k.pauseCount();
        w.burstCount = k.burstCount();

        // SWELL-KW keyboard metrics
        w.snKeyStrokes = k.keyPresses();
        w.snChars = k.chars();
        w.snSpaces = k.spaces();
        w.snErrorKeys = k.corrections();
        w.snDirectionKeys = k.directionKeys();
        w.snShortcutKeys = k.shortcutKeys();
        w.snSpecialKeys = k.specialKeys();
        w.charactersRatio = w.snKeyStrokes == 0 ? 0.0 : w.snChars / (double) w.snKeyStrokes;
        w.errorKeyRatio = (w.snChars + w.snSpaces) == 0 ? 0.0 : w.snErrorKeys / (w.snChars + w.snSpaces);

        // ---- mouse ----
        w.mouseDistancePx = m.distance();
        w.mouseSpeedPxS = m.speeds().isEmpty() ? 0 : mean(m.speeds());
        w.mouseSpeedCv = cv(m.speeds());
        w.mouseAccelPxS2 = m.avgAccel();
        w.clickCount = m.clicks();
        w.clickRate = m.clicks() / minutes;
        w.doubleClickRate = m.doubleClicks() / minutes;
        w.avgClickDistancePx = mean(m.clickDistances());
        w.directionChanges = m.directionChanges();

        // SWELL-KW mouse metrics
        w.snMouseAct = m.mouseActs();
        w.snLeftClicked = m.leftClicks();
        w.snRightClicked = m.rightClicks();
        w.snDoubleClicked = m.doubleClicks();
        w.snWheel = m.wheelEvents();
        w.snDragged = m.dragEvents();
        w.snMouseDistance = m.distance();

        // ---- application / work-idle ----
        w.appSwitchCount = a.switches();
        w.appSwitchRate = a.switches() / minutes;
        w.activeAppCategory = a.dominant();
        w.timeInAppS = a.switches() == 0 ? windowSeconds : (double) windowSeconds / (a.switches() + 1);

        // SWELL-KW application metrics
        w.snAppChange = a.switches();
        w.snTabfocusChange = Double.NaN; // not measured by foreground process polling
        // SWELL counts are per minute, independent of the configured interval.
        w.snKeyStrokes /= minutes; w.snChars /= minutes; w.snSpaces /= minutes;
        w.snErrorKeys /= minutes; w.snDirectionKeys /= minutes; w.snShortcutKeys /= minutes;
        w.snSpecialKeys /= minutes; w.snMouseAct /= minutes; w.snLeftClicked /= minutes;
        w.snRightClicked /= minutes; w.snDoubleClicked /= minutes; w.snWheel /= minutes;
        w.snDragged /= minutes; w.snMouseDistance /= minutes; w.snAppChange /= minutes;

        boolean anyActivity = k.keyPresses() > 0 || m.clicks() > 0 || m.distance() > 50;
        double activityFraction = Math.min(1.0, (k.keyPresses() + m.clicks() + m.speeds().size() / 20.0) / 40.0);
        w.idleDurationS = anyActivity ? windowSeconds * (1 - activityFraction) : windowSeconds;
        w.mouseIdleS = m.speeds().isEmpty() ? windowSeconds : Math.max(0, windowSeconds - m.speeds().size() / 10.0);
        w.workDurationS = windowSeconds - w.idleDurationS;
        if (w.idleDurationS >= idleThresholdSeconds && !anyActivity) continuousWorkS = 0;  // a break resets the streak
        else continuousWorkS += w.workDurationS;
        w.continuousWorkS = continuousWorkS;
        return w;
    }

    public void resetContinuousWork() { continuousWorkS = 0; }

    static double mean(List<? extends Number> xs) {
        if (xs == null || xs.isEmpty()) return 0;
        double s = 0; for (Number x : xs) s += x.doubleValue(); return s / xs.size();
    }

    /** Coefficient of variation — the "rhythm variability" feature. */
    static double cv(List<? extends Number> xs) {
        if (xs == null || xs.size() < 2) return 0;
        double m = mean(xs), v = 0;
        for (Number x : xs) v += (x.doubleValue() - m) * (x.doubleValue() - m);
        return m == 0 ? 0 : Math.sqrt(v / xs.size()) / m;
    }
}
