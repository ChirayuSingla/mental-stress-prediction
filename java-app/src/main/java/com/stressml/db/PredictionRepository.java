package com.stressml.db;

import com.google.gson.Gson;
import com.stressml.model.Prediction;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PredictionRepository {
    private final DatabaseManager db; private final Gson gson = new Gson();
    public PredictionRepository(DatabaseManager db) { this.db = db; }

    public long insert(Prediction p) throws SQLException {
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO predictions (feature_id, predicted_at, stress_class, prob_low, prob_moderate, " +
                    "prob_high, stress_score, fatigue_score, burnout_score, burnout_label, model_name, explanation_json) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, p.featureId); ps.setTimestamp(2, Timestamp.valueOf(p.predictedAt)); ps.setInt(3, p.stressClass);
                ps.setDouble(4, p.probLow); ps.setDouble(5, p.probModerate); ps.setDouble(6, p.probHigh); ps.setDouble(7, p.stressScore);
                ps.setDouble(8, p.fatigueScore); ps.setDouble(9, p.burnoutScore); ps.setString(10, p.burnoutLabel);
                ps.setString(11, "SWELL3:" + p.modelName); ps.setString(12, gson.toJson(p.topFactors));
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) { rs.next(); p.id = rs.getLong(1); }
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO recommendations (prediction_id, rule_code, message) VALUES (?,?,?)")) {
                for (String r : p.recommendations) { ps.setLong(1, p.id); ps.setString(2, "ML_SERVICE"); ps.setString(3, r); ps.addBatch(); }
                ps.executeBatch();
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO swell_prediction_inputs (prediction_id, observation_windows, feature_json) VALUES (?,?,?)")) {
                ps.setLong(1, p.id); ps.setInt(2, p.observationWindows); ps.setString(3, gson.toJson(p.inputFeatures)); ps.executeUpdate();
            }
            c.commit();
            return p.id;
        }
    }

    /** Session-level mean stress score history (for burnout indicator + history charts). */
    public List<double[]> sessionScores(int userId, int limit) throws SQLException {
        List<double[]> out = new ArrayList<>();
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(
                "SELECT s.session_id, AVG(p.stress_score), AVG(p.fatigue_score), s.break_count, " +
                "TIMESTAMPDIFF(MINUTE, s.started_at, COALESCE(s.ended_at, CURRENT_TIMESTAMP)) " +
                "FROM predictions p JOIN behavior_features f ON p.feature_id=f.feature_id JOIN sessions s ON f.session_id=s.session_id " +
                "WHERE s.user_id=? AND p.model_name LIKE 'SWELL3:%' GROUP BY s.session_id, s.break_count, s.started_at, s.ended_at ORDER BY s.started_at DESC LIMIT ?")) {
            ps.setInt(1, userId); ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(0, new double[]{rs.getInt(1), rs.getDouble(2), rs.getDouble(3), rs.getInt(4), rs.getDouble(5)});
            }
        }
        return out;
    }

    public List<Prediction> recentForSession(int sessionId, int limit) throws SQLException {
        List<Prediction> out = new ArrayList<>();
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(
                "SELECT p.predicted_at, p.stress_class, p.stress_score, p.fatigue_score FROM predictions p JOIN behavior_features f ON p.feature_id=f.feature_id " +
                "WHERE f.session_id=? AND p.model_name LIKE 'SWELL3:%' ORDER BY p.predicted_at DESC LIMIT ?")) {
            ps.setInt(1, sessionId); ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Prediction p = new Prediction(); p.predictedAt = rs.getTimestamp(1).toLocalDateTime();
                    p.stressClass = rs.getInt(2); p.stressScore = rs.getDouble(3); p.fatigueScore = rs.getDouble(4);
                    out.add(0, p);
                }
            }
        }
        return out;
    }

    public List<double[]> dailyAverages(int userId, int days) throws SQLException {
        List<double[]> out = new ArrayList<>();
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(
                "SELECT CAST(p.predicted_at AS DATE), AVG(p.stress_score) FROM predictions p JOIN behavior_features f ON p.feature_id=f.feature_id " +
                "JOIN sessions s ON f.session_id=s.session_id WHERE s.user_id=? AND p.model_name LIKE 'SWELL3:%' AND p.predicted_at >= ? GROUP BY CAST(p.predicted_at AS DATE) ORDER BY 1")) {
            ps.setInt(1, userId); ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now().minusDays(days)));
            try (ResultSet rs = ps.executeQuery()) {
                int i = 0; while (rs.next()) out.add(new double[]{i++, rs.getDouble(2), rs.getDate(1).toLocalDate().toEpochDay()});
            }
        }
        return out;
    }
}
