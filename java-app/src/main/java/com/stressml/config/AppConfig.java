package com.stressml.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Loads application.properties (classpath) then overrides from ./application.properties and env vars. */
public final class AppConfig {
    private final Properties props = new Properties();

    private AppConfig() {}

    public static AppConfig load() {
        AppConfig c = new AppConfig();
        try (InputStream in = AppConfig.class.getResourceAsStream("/application.properties")) {
            if (in != null) c.props.load(in);
            Path local = Path.of("application.properties");
            if (Files.exists(local)) try (InputStream l = Files.newInputStream(local)) { c.props.load(l); }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load configuration", e);
        }
        return c;
    }

    private String get(String key, String def) {
        String override = System.getProperty(key);
        if (override != null) return override;
        String env = System.getenv(key.toUpperCase().replace('.', '_'));
        if (env != null && !env.isBlank()) return env;
        return props.getProperty(key, def);
    }

    public String dbUrl() { return get("db.url", "jdbc:h2:./data/stressdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE"); }
    public String dbUser() { return get("db.user", "sa"); }
    public String dbPassword() { return get("db.password", ""); }
    public String mlBaseUrl() { return get("ml.base.url", "http://127.0.0.1:8000"); }
    public int mlTimeoutMs() { return Integer.parseInt(get("ml.timeout.ms", "4000")); }
    public int windowSeconds() { return Integer.parseInt(get("monitor.window.seconds", "60")); }
    public int idleThresholdSeconds() { return Integer.parseInt(get("monitor.idle.threshold.seconds", "20")); }
    public int calibrationSessions() { return Integer.parseInt(get("baseline.calibration.sessions", "3")); }
    public int baselineMinWindows() { return Integer.parseInt(get("baseline.min.windows", "30")); }
    public boolean demoMode() { return "demo".equalsIgnoreCase(get("app.mode", "real")); }
    public String participantCode() { return get("app.participant.code", "P001"); }
    public boolean trayEnabled() { return Boolean.parseBoolean(get("app.tray.enabled", "true")); }
    public int notificationCooldownSeconds() {
        int def = demoMode() ? 20 : 300;
        return Integer.parseInt(get("notification.cooldown.seconds", String.valueOf(def)));
    }
    public double highStressThreshold() { return Double.parseDouble(get("notification.high.stress.threshold", "6.0")); }
}
