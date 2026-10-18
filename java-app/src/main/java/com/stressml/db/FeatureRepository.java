package com.stressml.db;

import com.stressml.model.AppCategory;
import com.stressml.model.FeatureWindow;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FeatureRepository {
    private static final String INSERT = "INSERT INTO behavior_features (session_id, window_start, window_seconds, " +
            "typing_speed_wpm, keystrokes_per_min, avg_key_hold_ms, avg_key_latency_ms, backspace_rate, error_rate, pause_count, " +
            "avg_pause_ms, burst_count, typing_rhythm_cv, mouse_distance_px, mouse_speed_px_s, mouse_accel_px_s2, click_count, " +
            "click_rate, double_click_rate, avg_click_distance_px, mouse_idle_s, direction_changes, mouse_speed_cv, app_switch_count, " +
            "app_switch_rate, active_app_category, time_in_app_s, work_duration_s, idle_duration_s, continuous_work_s, stress_label) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    private static final String SELECT = "SELECT feature_id, session_id, window_start, window_seconds, typing_speed_wpm, keystrokes_per_min, " +
            "avg_key_hold_ms, avg_key_latency_ms, backspace_rate, error_rate, pause_count, avg_pause_ms, burst_count, typing_rhythm_cv, " +
            "mouse_distance_px, mouse_speed_px_s, mouse_accel_px_s2, click_count, click_rate, double_click_rate, avg_click_distance_px, " +
            "mouse_idle_s, direction_changes, mouse_speed_cv, app_switch_count, app_switch_rate, active_app_category, time_in_app_s, " +
            "work_duration_s, idle_duration_s, continuous_work_s, stress_label, " +
            "(SELECT feature_json FROM swell_feature_windows sw WHERE sw.feature_id=behavior_features.feature_id) AS swell_json FROM behavior_features ";
    private final DatabaseManager db;
    public FeatureRepository(DatabaseManager db) { this.db = db; }

    public long insert(FeatureWindow w) throws SQLException {
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            c.setAutoCommit(false);
            int i = 1;
            ps.setInt(i++, w.sessionId); ps.setTimestamp(i++, Timestamp.valueOf(w.windowStart)); ps.setInt(i++, w.windowSeconds);
            ps.setDouble(i++, w.typingSpeedWpm); ps.setDouble(i++, w.keystrokesPerMin); ps.setDouble(i++, w.avgKeyHoldMs);
            ps.setDouble(i++, w.avgKeyLatencyMs); ps.setDouble(i++, w.backspaceRate); ps.setDouble(i++, w.errorRate);
            ps.setInt(i++, w.pauseCount); ps.setDouble(i++, w.avgPauseMs); ps.setInt(i++, w.burstCount); ps.setDouble(i++, w.typingRhythmCv);
            ps.setDouble(i++, w.mouseDistancePx); ps.setDouble(i++, w.mouseSpeedPxS); ps.setDouble(i++, w.mouseAccelPxS2);
            ps.setInt(i++, w.clickCount); ps.setDouble(i++, w.clickRate); ps.setDouble(i++, w.doubleClickRate);
            ps.setDouble(i++, w.avgClickDistancePx); ps.setDouble(i++, w.mouseIdleS); ps.setInt(i++, w.directionChanges);
            ps.setDouble(i++, w.mouseSpeedCv); ps.setInt(i++, w.appSwitchCount); ps.setDouble(i++, w.appSwitchRate);
            ps.setString(i++, w.activeAppCategory.name()); ps.setDouble(i++, w.timeInAppS); ps.setDouble(i++, w.workDurationS);
            ps.setDouble(i++, w.idleDurationS); ps.setDouble(i++, w.continuousWorkS);
            if (w.stressLabel == null) ps.setNull(i, Types.TINYINT); else ps.setInt(i, w.stressLabel);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { rs.next(); w.id = rs.getLong(1); }
            try (PreparedStatement sw = c.prepareStatement("INSERT INTO swell_feature_windows (feature_id, feature_json) VALUES (?,?)")) {
                sw.setLong(1, w.id); sw.setString(2, new com.google.gson.Gson().toJson(w.toSwellFeatureMap())); sw.executeUpdate();
            }
            c.commit();
            return w.id;
        }
    }

    public List<FeatureWindow> bySession(int sessionId) throws SQLException {
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(SELECT + "WHERE session_id=? ORDER BY window_start")) {
            ps.setInt(1, sessionId); return read(ps);
        }
    }

    public List<FeatureWindow> byUser(int userId) throws SQLException {
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(SELECT +
                "WHERE session_id IN (SELECT session_id FROM sessions WHERE user_id=?) ORDER BY window_start")) {
            ps.setInt(1, userId); return read(ps);
        }
    }

    private List<FeatureWindow> read(PreparedStatement ps) throws SQLException {
        List<FeatureWindow> out = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                FeatureWindow w = new FeatureWindow(); int i = 1;
                w.id = rs.getLong(i++); w.sessionId = rs.getInt(i++); w.windowStart = rs.getTimestamp(i++).toLocalDateTime();
                w.windowSeconds = rs.getInt(i++); w.typingSpeedWpm = rs.getDouble(i++); w.keystrokesPerMin = rs.getDouble(i++);
                w.avgKeyHoldMs = rs.getDouble(i++); w.avgKeyLatencyMs = rs.getDouble(i++); w.backspaceRate = rs.getDouble(i++);
                w.errorRate = rs.getDouble(i++); w.pauseCount = rs.getInt(i++); w.avgPauseMs = rs.getDouble(i++);
                w.burstCount = rs.getInt(i++); w.typingRhythmCv = rs.getDouble(i++); w.mouseDistancePx = rs.getDouble(i++);
                w.mouseSpeedPxS = rs.getDouble(i++); w.mouseAccelPxS2 = rs.getDouble(i++); w.clickCount = rs.getInt(i++);
                w.clickRate = rs.getDouble(i++); w.doubleClickRate = rs.getDouble(i++); w.avgClickDistancePx = rs.getDouble(i++);
                w.mouseIdleS = rs.getDouble(i++); w.directionChanges = rs.getInt(i++); w.mouseSpeedCv = rs.getDouble(i++);
                w.appSwitchCount = rs.getInt(i++); w.appSwitchRate = rs.getDouble(i++);
                try { w.activeAppCategory = AppCategory.valueOf(rs.getString(i++)); } catch (Exception e) { w.activeAppCategory = AppCategory.OTHER; }
                w.timeInAppS = rs.getDouble(i++); w.workDurationS = rs.getDouble(i++); w.idleDurationS = rs.getDouble(i++);
                w.continuousWorkS = rs.getDouble(i++); int lbl = rs.getInt(i); w.stressLabel = rs.wasNull() ? null : lbl;
                String json = rs.getString("swell_json");
                w.recordedSwellFeatures = json == null ? java.util.Map.of() : new com.google.gson.Gson().fromJson(json,
                        new com.google.gson.reflect.TypeToken<java.util.Map<String, Double>>(){}.getType());
                out.add(w);
            }
        }
        return out;
    }
}
