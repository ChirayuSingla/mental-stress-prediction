package com.stressml.demo;

import com.stressml.model.AppCategory;
import com.stressml.model.FeatureWindow;

import java.time.LocalDateTime;
import java.util.Random;

/**
 * DEMO MODE ONLY — produces clearly-labelled synthetic feature windows so the dashboard can be shown
 * without real participants. Mirrors ml/synthetic/generate_synthetic.py. Never treat output as real data.
 */
public class SyntheticDataGenerator {
    private final Random rng = new Random(7);
    private final int windowSeconds;
    private final double baseWpm = 55, baseErr = 0.04, baseMouse = 420, baseClick = 14, baseSwitch = 3;
    private double continuousWork;
    private int stressLevel = 0; // 0..2, drifts over time to make the demo interesting

    public SyntheticDataGenerator(int windowSeconds) { this.windowSeconds = windowSeconds; }

    public void setStressLevel(int level) { stressLevel = Math.max(0, Math.min(2, level)); }
    public int stressLevel() { return stressLevel; }

    public FeatureWindow next(boolean calibration) {
        int s = calibration ? 0 : stressLevel;
        if (!calibration && rng.nextDouble() < 0.1) stressLevel = Math.max(0, Math.min(2, stressLevel + (rng.nextBoolean() ? 1 : -1)));
        double fatigue = Math.min(continuousWork / (3 * 3600), 1.0);
        FeatureWindow w = new FeatureWindow();
        w.windowStart = LocalDateTime.now(); w.windowSeconds = windowSeconds;
        w.typingSpeedWpm = clamp(baseWpm * (1 - 0.10 * s - 0.08 * fatigue) * g(0.08), 5, 150);
        w.keystrokesPerMin = w.typingSpeedWpm * 5 * g(0.05);
        w.avgKeyHoldMs = 95 * (1 + 0.06 * s) * g(0.06);
        w.avgKeyLatencyMs = 180 * (1 + 0.12 * s + 0.05 * fatigue) * g(0.08);
        w.errorRate = clamp(baseErr * (1 + 0.35 * s + 0.2 * fatigue) * g(0.15), 0, 1);
        w.backspaceRate = w.errorRate * (0.8 + 0.5 * rng.nextDouble());
        w.pauseCount = poisson(4 + 3 * s); w.avgPauseMs = 2500 * (1 + 0.1 * s) * g(0.2);
        w.burstCount = poisson(6 - s); w.typingRhythmCv = clamp(0.25 + 0.08 * s + rng.nextGaussian() * 0.03, 0.05, 1);
        w.mouseSpeedPxS = baseMouse * (1 + 0.15 * s) * g(0.1);
        w.mouseDistancePx = w.mouseSpeedPxS * windowSeconds * (0.3 + 0.4 * rng.nextDouble());
        w.mouseAccelPxS2 = w.mouseSpeedPxS * (1.5 + 0.5 * s) * g(0.1);
        w.clickRate = baseClick * (1 + 0.2 * s) * g(0.12); w.clickCount = (int) Math.round(w.clickRate * windowSeconds / 60.0);
        w.doubleClickRate = w.clickRate * 0.1; w.avgClickDistancePx = 200 * (1 + 0.1 * s) * g(0.3);
        w.mouseIdleS = clamp(8 * (1 - 0.2 * s) + rng.nextGaussian() * 3, 0, windowSeconds);
        w.directionChanges = poisson(40 + 25 * s); w.mouseSpeedCv = clamp(0.4 + 0.12 * s + rng.nextGaussian() * 0.04, 0.05, 2);
        w.appSwitchRate = baseSwitch * (1 + 0.4 * s) * g(0.15); w.appSwitchCount = (int) Math.round(w.appSwitchRate * windowSeconds / 60.0);
        w.activeAppCategory = s == 2 && rng.nextDouble() < 0.3 ? AppCategory.COMMUNICATION : AppCategory.values()[rng.nextInt(3)];
        w.timeInAppS = windowSeconds / (w.appSwitchCount + 1.0);
        w.idleDurationS = clamp(10 * (1 - 0.3 * s) + rng.nextGaussian() * 4, 0, windowSeconds);
        w.workDurationS = windowSeconds - w.idleDurationS;
        continuousWork = w.idleDurationS > 20 ? 0 : continuousWork + w.workDurationS;
        w.continuousWorkS = continuousWork;

        // Populate SWELL-KW feature equivalents
        w.snKeyStrokes = w.keystrokesPerMin;
        w.snChars = Math.round(w.snKeyStrokes * 0.7);
        w.snSpaces = Math.round(w.snKeyStrokes * 0.15);
        w.snErrorKeys = Math.round(w.snKeyStrokes * w.errorRate);
        w.snDirectionKeys = 3;
        w.snShortcutKeys = 1;
        w.snSpecialKeys = Math.max(0, w.snKeyStrokes - w.snChars - w.snSpaces - w.snErrorKeys - 4);
        w.charactersRatio = w.snKeyStrokes == 0 ? 0 : w.snChars / w.snKeyStrokes;
        w.errorKeyRatio = (w.snChars + w.snSpaces) == 0 ? 0 : w.snErrorKeys / (w.snChars + w.snSpaces);
        w.snLeftClicked = w.clickCount;
        w.snRightClicked = Math.max(0, (int) Math.round(w.clickCount * 0.1));
        w.snDoubleClicked = (int) Math.round(w.doubleClickRate);
        w.snWheel = 5;
        w.snDragged = 2;
        w.snMouseDistance = w.mouseDistancePx;
        w.snMouseAct = w.snLeftClicked + w.snRightClicked + w.snWheel + w.snDragged + 20;
        w.snAppChange = w.appSwitchCount;
        w.snTabfocusChange = w.appSwitchCount;

        return w;
    }

    private double g(double sd) { return 1 + rng.nextGaussian() * sd; }
    private int poisson(double lambda) { double l = Math.exp(-Math.max(lambda, 0.1)), p = 1; int k = 0; do { k++; p *= rng.nextDouble(); } while (p > l); return k - 1; }
    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
