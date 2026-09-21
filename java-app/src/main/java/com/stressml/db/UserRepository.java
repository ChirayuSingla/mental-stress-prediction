package com.stressml.db;

import java.sql.*;
import java.time.LocalDateTime;

public class UserRepository {
    private final DatabaseManager db;
    public UserRepository(DatabaseManager db) { this.db = db; }

    /** Returns the user id for the pseudonymous code, creating it if needed. */
    public int findOrCreate(String participantCode) throws SQLException {
        try (Connection c = db.connection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT user_id FROM users WHERE participant_code = ?")) {
                ps.setString(1, participantCode);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO users (participant_code, consent_given, consent_at) VALUES (?, 1, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, participantCode); ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) { rs.next(); return rs.getInt(1); }
            }
        }
    }

    /** GDPR-style deletion: cascades to sessions, features, predictions, recommendations, baselines. */
    public void deleteAllData(int userId) throws SQLException {
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE user_id = ?")) {
            ps.setInt(1, userId); ps.executeUpdate();
        }
    }
}
