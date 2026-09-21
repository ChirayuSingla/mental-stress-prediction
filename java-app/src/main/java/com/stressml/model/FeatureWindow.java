package com.stressml.model;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One aggregated behavioural window (default 60 s). Contains ONLY behavioural metadata —
 * never characters, window titles or application names.
 */
public class FeatureWindow {
    public long id;
    public int sessionId;
    public LocalDateTime windowStart;
    public int windowSeconds;
    // keyboard
    public double typingSpeedWpm, keystrokesPerMin, avgKeyHoldMs, avgKeyLatencyMs, backspaceRate, errorRate,
            avgPauseMs, typingRhythmCv;
    public int pauseCount, burstCount;
    // mouse
    public double mouseDistancePx, mouseSpeedPxS, mouseAccelPxS2, clickRate, doubleClickRate, avgClickDistancePx,
            mouseIdleS, mouseSpeedCv;
    public int clickCount, directionChanges;
    // application / work
    public int appSwitchCount;
    public double appSwitchRate, timeInAppS, workDurationS, idleDurationS, continuousWorkS;
    public AppCategory activeAppCategory = AppCategory.OTHER;
    public Integer stressLabel; // self-reported, optional

    /** Absolute features keyed exactly as the Python pipeline expects (see ml/preprocessing/features.py). */
    public Map<String, Double> toFeatureMap() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("typing_speed_wpm", typingSpeedWpm); m.put("keystrokes_per_min", keystrokesPerMin);
        m.put("avg_key_hold_ms", avgKeyHoldMs); m.put("avg_key_latency_ms", avgKeyLatencyMs);
        m.put("backspace_rate", backspaceRate); m.put("error_rate", errorRate);
        m.put("pause_count", (double) pauseCount); m.put("avg_pause_ms", avgPauseMs);
        m.put("burst_count", (double) burstCount); m.put("typing_rhythm_cv", typingRhythmCv);
        m.put("mouse_distance_px", mouseDistancePx); m.put("mouse_speed_px_s", mouseSpeedPxS);
        m.put("mouse_accel_px_s2", mouseAccelPxS2); m.put("click_count", (double) clickCount);
        m.put("click_rate", clickRate); m.put("double_click_rate", doubleClickRate);
        m.put("avg_click_distance_px", avgClickDistancePx); m.put("mouse_idle_s", mouseIdleS);
        m.put("direction_changes", (double) directionChanges); m.put("mouse_speed_cv", mouseSpeedCv);
        m.put("app_switch_count", (double) appSwitchCount); m.put("app_switch_rate", appSwitchRate);
        m.put("time_in_app_s", timeInAppS); m.put("work_duration_s", workDurationS);
        m.put("idle_duration_s", idleDurationS); m.put("continuous_work_s", continuousWorkS);
        return m;
    }

    public static final String[] CSV_HEADER = {"participant_id", "session_id", "timestamp", "window_index",
            "typing_speed_wpm", "keystrokes_per_min", "avg_key_hold_ms", "avg_key_latency_ms", "backspace_rate",
            "error_rate", "pause_count", "avg_pause_ms", "burst_count", "typing_rhythm_cv", "mouse_distance_px",
            "mouse_speed_px_s", "mouse_accel_px_s2", "click_count", "click_rate", "double_click_rate",
            "avg_click_distance_px", "mouse_idle_s", "direction_changes", "mouse_speed_cv", "app_switch_count",
            "app_switch_rate", "active_app_category", "time_in_app_s", "work_duration_s", "idle_duration_s",
            "continuous_work_s", "stress_label"};
}
