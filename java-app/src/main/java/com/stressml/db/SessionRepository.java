package com.stressml.db;

import com.stressml.model.Session;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SessionRepository {
    private final DatabaseManager db;
    public SessionRepository(DatabaseManager db) { this.db = db; }

    public int start(int userId, boolean calibration, boolean demo) throws SQLException {
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO sessions (user_id, started_at, is_calibration, is_demo) VALUES (?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId); ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            ps.setBoolean(3, calibration); ps.setBoolean(4, demo); ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { rs.next(); return rs.getInt(1); }
        }
    }

    public void end(int sessionId, int breakCount, Integer selfReport, String instrument) throws SQLException {
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(
                "UPDATE sessions SET ended_at=?, break_count=?, self_report_stress=COALESCE(?,self_report_stress), self_report_instrument=COALESCE(?,self_report_instrument) WHERE session_id=?")) {
            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now())); ps.setInt(2, breakCount);
            if (selfReport == null) ps.setNull(3, Types.TINYINT); else ps.setInt(3, selfReport);
            ps.setString(4, instrument); ps.setInt(5, sessionId); ps.executeUpdate();
        }
    }

    public int countCompleted(int userId) throws SQLException {
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM sessions WHERE user_id=? AND ended_at IS NOT NULL")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    public void saveSelfReport(int sessionId, int score, String instrument) throws SQLException {
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(
                "UPDATE sessions SET self_report_stress=?, self_report_instrument=? WHERE session_id=?")) {
            ps.setInt(1, score); ps.setString(2, instrument); ps.setInt(3, sessionId); ps.executeUpdate();
        }
    }

    public List<Session> recent(int userId, int limit) throws SQLException {
        List<Session> out = new ArrayList<>();
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(
                "SELECT session_id, user_id, started_at, ended_at, is_calibration, is_demo, break_count, self_report_stress " +
                "FROM sessions WHERE user_id=? ORDER BY started_at DESC LIMIT ?")) {
            ps.setInt(1, userId); ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Session s = new Session();
                    s.id = rs.getInt(1); s.userId = rs.getInt(2);
                    s.startedAt = rs.getTimestamp(3).toLocalDateTime();
                    Timestamp e = rs.getTimestamp(4); s.endedAt = e == null ? null : e.toLocalDateTime();
                    s.calibration = rs.getBoolean(5); s.demo = rs.getBoolean(6); s.breakCount = rs.getInt(7);
                    int sr = rs.getInt(8); s.selfReportStress = rs.wasNull() ? null : sr;
                    out.add(s);
                }
            }
        }
        return out;
    }
}
