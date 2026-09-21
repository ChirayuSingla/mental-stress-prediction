package com.stressml.db;

import com.stressml.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {
    @Test
    void testH2SchemaBootstrap() throws Exception {
        AppConfig config = AppConfig.load();
        try (DatabaseManager db = new DatabaseManager(config)) {
            db.initialiseSchemaIfEmbedded();
            try (Connection conn = db.connection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT count(*) FROM users")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 0);
            }
        }
    }
}
