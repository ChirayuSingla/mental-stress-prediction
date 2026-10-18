package com.stressml.db;

import com.stressml.model.BaselineProfile;

import java.sql.*;
import java.util.Map;

public class BaselineRepository {
    private final DatabaseManager db;
    public BaselineRepository(DatabaseManager db) { this.db = db; }

    public void save(int userId, BaselineProfile p) throws SQLException {
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try (PreparedStatement del = c.prepareStatement("DELETE FROM baseline_profiles WHERE user_id=?")) { del.setInt(1, userId); del.executeUpdate(); }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO baseline_profiles (user_id, feature_name, mean_value, median_value, std_value, " +
                    "low_value, high_value, sample_count) VALUES (?,?,?,?,?,?,?,?)")) {
                for (Map.Entry<String, BaselineProfile.Stat> e : p.stats.entrySet()) {
                    BaselineProfile.Stat s = e.getValue();
                    ps.setInt(1, userId); ps.setString(2, e.getKey()); ps.setDouble(3, s.mean); ps.setDouble(4, s.median);
                    ps.setDouble(5, s.std); ps.setDouble(6, s.low); ps.setDouble(7, s.high); ps.setInt(8, s.n); ps.addBatch();
                }
                ps.executeBatch();
            }
            c.commit();
        }
    }

    public BaselineProfile load(int userId) throws SQLException {
        BaselineProfile p = new BaselineProfile();
        try (Connection c = db.connection(); PreparedStatement ps = c.prepareStatement(
                "SELECT feature_name, mean_value, median_value, std_value, low_value, high_value, sample_count FROM baseline_profiles WHERE user_id=?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) p.stats.put(rs.getString(1), new BaselineProfile.Stat(rs.getDouble(2), rs.getDouble(3), rs.getDouble(4),
                        rs.getDouble(5), rs.getDouble(6), rs.getInt(7)));
            }
        }
        return p;
    }
}
