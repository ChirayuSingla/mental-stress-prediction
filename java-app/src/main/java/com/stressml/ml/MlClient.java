package com.stressml.ml;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.stressml.config.AppConfig;
import com.stressml.model.BaselineProfile;
import com.stressml.model.FeatureWindow;
import com.stressml.model.Prediction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/** REST client for the Python prediction service (ml/api/server.py). */
public class MlClient {
    private static final Logger log = LoggerFactory.getLogger(MlClient.class);
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final Gson gson = new GsonBuilder().create();
    private final String baseUrl; private final int timeoutMs;

    public MlClient(AppConfig config) { this.baseUrl = config.mlBaseUrl(); this.timeoutMs = config.mlTimeoutMs(); }

    public boolean isHealthy() {
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/health")).timeout(Duration.ofMillis(timeoutMs)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return r.statusCode() == 200 && r.body().contains("\"model_loaded\":true");
        } catch (Exception e) { return false; }
    }

    public Optional<Prediction> predict(String participantCode, FeatureWindow w, BaselineProfile baseline,
                                        List<Double> recentScores, List<Double> recentDurations, List<Integer> recentBreaks, boolean demo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("participant_id", participantCode);
        body.put("features", w.toFeatureMap());
        body.put("active_app_category", w.activeAppCategory.name());
        if (baseline != null && baseline.isComplete()) {
            Map<String, Map<String, Double>> b = new LinkedHashMap<>();
            baseline.stats.forEach((k, s) -> b.put(k, Map.of("mean", s.mean, "std", s.std)));
            body.put("baseline", b);
        }
        body.put("recent_stress_scores", recentScores); body.put("recent_session_durations_min", recentDurations);
        body.put("recent_break_counts", recentBreaks); body.put("demo_mode", demo);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/predict")).timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body))).build();
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) { log.warn("ML service returned {}: {}", r.statusCode(), r.body()); return Optional.empty(); }
            return Optional.of(parse(r.body(), w.id));
        } catch (IOException | InterruptedException e) {
            log.warn("ML service unreachable: {}", e.getMessage());
            return Optional.empty();
        }
    }

    Prediction parse(String json, long featureId) {
        JsonObject o = gson.fromJson(json, JsonObject.class);
        Prediction p = new Prediction(); p.featureId = featureId;
        p.stressClass = o.get("stress_class").getAsInt(); p.stressLabel = o.get("stress_label").getAsString();
        JsonObject pr = o.getAsJsonObject("probabilities");
        p.probLow = pr.get("Low Stress").getAsDouble(); p.probModerate = pr.get("Moderate Stress").getAsDouble(); p.probHigh = pr.get("High Stress").getAsDouble();
        p.stressScore = o.get("stress_score").getAsDouble(); p.fatigueScore = o.get("fatigue_score").getAsDouble();
        p.burnoutScore = o.get("burnout_score").getAsDouble(); p.burnoutLabel = o.get("burnout_risk").getAsString();
        p.modelName = o.get("model_name").getAsString(); p.dataMode = o.get("data_mode").getAsString(); p.demoMode = o.get("demo_mode").getAsBoolean();
        o.getAsJsonArray("top_factors").forEach(el -> {
            JsonObject f = el.getAsJsonObject(); Prediction.Factor fac = new Prediction.Factor();
            fac.feature = f.get("feature").getAsString(); fac.contribution = f.get("contribution").getAsDouble();
            fac.direction = f.get("direction").getAsString(); fac.humanReadable = f.get("human_readable").getAsString();
            p.topFactors.add(fac);
        });
        o.getAsJsonArray("recommendations").forEach(el -> p.recommendations.add(el.getAsString()));
        return p;
    }
}
