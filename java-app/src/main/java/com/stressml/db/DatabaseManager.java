package com.stressml.db;

import com.stressml.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Connection pool + schema bootstrap (for embedded H2). All queries use prepared statements. */
public class DatabaseManager implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);
    private final HikariDataSource ds;
    private final boolean embedded;

    public DatabaseManager(AppConfig config) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.dbUrl()); hc.setUsername(config.dbUser()); hc.setPassword(config.dbPassword());
        hc.setMaximumPoolSize(4);
        ds = new HikariDataSource(hc);
        embedded = config.dbUrl().startsWith("jdbc:h2");
    }

    public Connection connection() throws SQLException { return ds.getConnection(); }

    /** For H2 demo runs, create tables from the MySQL schema (H2 in MySQL mode accepts it, minus the DB statements). */
    public void initialiseSchemaIfEmbedded() {
        if (!embedded) return;
        try (InputStream in = getClass().getResourceAsStream("/schema.sql")) {
            if (in == null) { log.warn("schema.sql not on classpath; skipping bootstrap"); return; }
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("(?i)CREATE DATABASE[^;]*;", "").replaceAll("(?i)USE [^;]*;", "")
                    .replaceAll("(?i)ENGINE=InnoDB", "").replaceAll("(?i)ON UPDATE CURRENT_TIMESTAMP", "")
                    .replaceAll("(?i),\\s*INDEX [^,)]*\\([^)]*\\)", "");
            try (Connection c = connection(); Statement st = c.createStatement()) {
                for (String stmt : sql.split(";")) if (!stmt.isBlank()) st.execute(stmt);
            }
            log.info("Embedded schema ready");
        } catch (IOException | SQLException e) {
            log.error("Schema bootstrap failed", e);
        }
    }

    @Override public void close() { ds.close(); }
}
