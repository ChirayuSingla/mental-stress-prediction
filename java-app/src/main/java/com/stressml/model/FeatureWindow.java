package com.stressml.model;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One aggregated behavioural window (default 60 s). Contains ONLY behavioural metadata —
 * never character content, window titles, or application names.
 * Supports the 18 multimodal Computer Interaction features of the SWELL-KW dataset.
 */
public class FeatureWindow {
    public long id;
    public int sessionId;
    public LocalDateTime windowStart;
    public int windowSeconds = 60;

    // SWELL-KW Computer Interaction Features: Keyboard (9)
    public double snKeyStrokes;
    public double snChars;
    public double snSpecialKeys;
    public double snDirectionKeys;
    public double snErrorKeys;
    public double snShortcutKeys;
    public double snSpaces;
    public double charactersRatio;
    public double errorKeyRatio;

    // SWELL-KW Computer Interaction Features: Mouse (7)
    public double snMouseAct;
    public double snLeftClicked;
    public double snRightClicked;
    public double snDoubleClicked;
    public double snWheel;
    public double snDragged;
    public double snMouseDistance;

    // SWELL-KW Computer Interaction Features: Application (2)
    public double snAppChange;
    public double snTabfocusChange;

    // Legacy / physical metrics (for backward compatibility and database persistence)
    public double typingSpeedWpm, keystrokesPerMin, avgKeyHoldMs, avgKeyLatencyMs, backspaceRate, errorRate,
            avgPauseMs, typingRhythmCv;
    public int pauseCount, burstCount;
    public double mouseDistancePx, mouseSpeedPxS, mouseAccelPxS2, clickRate, doubleClickRate, avgClickDistancePx,
            mouseIdleS, mouseSpeedCv;
    public int clickCount, directionChanges;
    public int appSwitchCount;
    public double appSwitchRate, timeInAppS, workDurationS, idleDurationS, continuousWorkS;
    public AppCategory activeAppCategory = AppCategory.OTHER;
    public Integer stressLabel; // self-reported questionnaire rating, optional

    /**
     * Feature map transmitted to the Python ML prediction service.
     * Contains the 18 real SWELL-KW interaction features alongside legacy metrics.
     */
    public Map<String, Double> toFeatureMap() {
        Map<String, Double> m = new LinkedHashMap<>();

        // Real SWELL-KW Multimodal Features (18 features)
        m.put("SnKeyStrokes", snKeyStrokes);
        m.put("SnChars", snChars);
        m.put("SnSpecialKeys", snSpecialKeys);
        m.put("SnDirectionKeys", snDirectionKeys);
        m.put("SnErrorKeys", snErrorKeys);
        m.put("SnShortcutKeys", snShortcutKeys);
        m.put("SnSpaces", snSpaces);
        m.put("CharactersRatio", charactersRatio);
        m.put("ErrorKeyRatio", errorKeyRatio);
        // Native movement event frequency differs from uLog; not a model input.
        m.put("SnLeftClicked", snLeftClicked);
        m.put("SnRightClicked", snRightClicked);
        m.put("SnDoubleClicked", snDoubleClicked);
        m.put("SnWheel", snWheel);
        m.put("SnDragged", snDragged);
        m.put("SnMouseDistance", snMouseDistance);
        m.put("SnAppChange", snAppChange);
        // In-app tab focus is not measured.

        // Legacy metrics for backwards compatibility
        m.put("typing_speed_wpm", typingSpeedWpm);
        m.put("keystrokes_per_min", keystrokesPerMin);
        m.put("avg_key_hold_ms", avgKeyHoldMs);
        m.put("avg_key_latency_ms", avgKeyLatencyMs);
        m.put("backspace_rate", backspaceRate);
        m.put("error_rate", errorRate);
        m.put("pause_count", (double) pauseCount);
        m.put("avg_pause_ms", avgPauseMs);
        m.put("burst_count", (double) burstCount);
        m.put("typing_rhythm_cv", typingRhythmCv);
        m.put("mouse_distance_px", mouseDistancePx);
        m.put("mouse_speed_px_s", mouseSpeedPxS);
        m.put("mouse_accel_px_s2", mouseAccelPxS2);
        m.put("click_count", (double) clickCount);
        m.put("click_rate", clickRate);
        m.put("double_click_rate", doubleClickRate);
        m.put("avg_click_distance_px", avgClickDistancePx);
        m.put("mouse_idle_s", mouseIdleS);
        m.put("direction_changes", (double) directionChanges);
        m.put("mouse_speed_cv", mouseSpeedCv);
        m.put("app_switch_count", (double) appSwitchCount);
        m.put("app_switch_rate", appSwitchRate);
        m.put("time_in_app_s", timeInAppS);
        m.put("work_duration_s", workDurationS);
        m.put("idle_duration_s", idleDurationS);
        m.put("continuous_work_s", continuousWorkS);

        return m;
    }

    public Map<String, Double> toSwellFeatureMap() {
        if (recordedSwellFeatures != null) {
            Map<String, Double> measured = new LinkedHashMap<>(recordedSwellFeatures);
            measured.keySet().removeAll(java.util.Set.of("SnMouseAct", "SnTabfocusChange", "SnMouseDistance"));
            return measured;
        }
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("SnKeyStrokes", snKeyStrokes);
        m.put("SnChars", snChars);
        m.put("SnSpecialKeys", snSpecialKeys);
        m.put("SnDirectionKeys", snDirectionKeys);
        m.put("SnErrorKeys", snErrorKeys);
        m.put("SnShortcutKeys", snShortcutKeys);
        m.put("SnSpaces", snSpaces);
        m.put("CharactersRatio", charactersRatio);
        m.put("ErrorKeyRatio", errorKeyRatio);
        // Unsupported uLog mouse-action aggregate omitted.
        m.put("SnLeftClicked", snLeftClicked);
        m.put("SnRightClicked", snRightClicked);
        m.put("SnDoubleClicked", snDoubleClicked);
        m.put("SnWheel", snWheel);
        m.put("SnDragged", snDragged);
        m.put("SnAppChange", snAppChange);
        // Unsupported in-app focus aggregate omitted.
        return m;
    }

    /** Null for a newly collected window; empty for historical rows without SWELL measurements. */
    public Map<String, Double> recordedSwellFeatures;

    public static final String[] CSV_HEADER = {"participant_id", "session_id", "timestamp", "window_index",
            "SnKeyStrokes", "SnChars", "SnSpecialKeys", "SnDirectionKeys", "SnErrorKeys", "SnShortcutKeys",
            "SnSpaces", "CharactersRatio", "ErrorKeyRatio", "SnLeftClicked", "SnRightClicked",
            "SnDoubleClicked", "SnWheel", "SnDragged", "SnAppChange",
            "self_report_ordinal3"};
}
