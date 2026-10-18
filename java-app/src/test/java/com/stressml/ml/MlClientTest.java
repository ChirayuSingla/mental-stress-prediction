package com.stressml.ml;

import com.stressml.config.AppConfig;
import com.stressml.model.FeatureWindow;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MlClientTest {
    @Test void timesOutWithoutReturningReplacementScore() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/predict", exchange -> {
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        try {
            System.setProperty("ml.base.url", "http://127.0.0.1:" + server.getAddress().getPort());
            System.setProperty("ml.timeout.ms", "200");
            MlClient client = new MlClient(AppConfig.load());
            assertTrue(client.predict("TEST", new FeatureWindow(), null, List.of(), List.of(), List.of(), false).isEmpty());
            assertTrue(client.lastError().contains("HttpTimeoutException"), client.lastError());
        } finally { System.clearProperty("ml.base.url"); System.clearProperty("ml.timeout.ms"); server.stop(0); }
    }

    @Test void reportsHttpErrorsAndMalformedResponses() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var status = new java.util.concurrent.atomic.AtomicInteger(503);
        server.createContext("/predict", exchange -> {
            byte[] bytes = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        try {
            System.setProperty("ml.base.url", "http://127.0.0.1:" + server.getAddress().getPort());
            MlClient client = new MlClient(AppConfig.load());
            assertTrue(client.predict("TEST", new FeatureWindow(), null, List.of(), List.of(), List.of(), false).isEmpty());
            assertTrue(client.lastError().contains("HTTP 503"));
            status.set(200);
            assertTrue(client.predict("TEST", new FeatureWindow(), null, List.of(), List.of(), List.of(), false).isEmpty());
            assertTrue(client.lastError().contains("Invalid real regression response"));
        } finally { System.clearProperty("ml.base.url"); server.stop(0); }
    }

    @Test void rejectsMissingScoreAndInvalidScale() {
        MlClient client = new MlClient(AppConfig.load());
        assertThrows(RuntimeException.class, () -> client.parse("{}", 1));
        assertThrows(RuntimeException.class, () -> client.parse("{\"stress_score\":80,\"model_name\":\"test\",\"data_mode\":\"REAL_SWELL\"}", 1));
    }

    @Test void parsesContinuousRegressionWithoutProbabilities() {
        var p = new MlClient(AppConfig.load()).parse("{\"stress_score\":3.27,\"model_name\":\"SVR\",\"data_mode\":\"REAL_SWELL\"}", 42);
        assertEquals(3.27, p.stressScore); assertEquals(-1, p.stressClass); assertEquals(42, p.featureId);
    }

    @Test
    @EnabledIfSystemProperty(named="stressml.integration", matches="true")
    void actualPythonServicePredictsWorkbookObservation() throws Exception {
        FeatureWindow w = new FeatureWindow();
        Path csv = Path.of("../ml/data/processed/swell_aggregated.csv");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(csv), "Preprocess the licensed workbook first");
        List<String> rows;
        try (var lines = Files.lines(csv)) { rows = lines.limit(2).toList(); }
        String[] headers = rows.get(0).split(","), values = rows.get(1).split(",");
        w.recordedSwellFeatures = new java.util.LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++)
            if (headers[i].startsWith("Sn") || headers[i].endsWith("Ratio"))
                w.recordedSwellFeatures.put(headers[i], Double.parseDouble(values[i]));
        MlClient client = new MlClient(AppConfig.load());
        assertTrue(client.isHealthy());
        var p = client.predict("JAVA_INTEGRATION", w, null, List.of(), List.of(), List.of(), false);
        assertTrue(p.isPresent(), client.lastError());
        assertTrue(p.get().stressScore >= 0 && p.get().stressScore <= 10);
        assertEquals("REAL_SWELL", p.get().dataMode);
        assertFalse(p.get().demoMode);
        System.out.println("ACTUAL JAVA -> PYTHON: score=" + p.get().stressScore + ", model=" + p.get().modelName);
    }
}
