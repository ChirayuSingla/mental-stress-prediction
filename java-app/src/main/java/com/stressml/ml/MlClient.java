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
    private HttpClient http;
    private synchronized HttpClient http() {
        if (http == null) http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3)).build();
        return http;
    }
    private final Gson gson = new GsonBuilder().create();
    private final String baseUrl; private final int timeoutMs;
    private volatile String lastError = "Waiting for first completed measurement window";
    public String lastError() { return lastError; }

    public MlClient(AppConfig config) { this.baseUrl = config.mlBaseUrl(); this.timeoutMs = config.mlTimeoutMs(); }

    public boolean isHealthy() {
        try {
            HttpResponse<String> r = http().send(HttpRequest.newBuilder(URI.create(baseUrl + "/health")).timeout(Duration.ofMillis(timeoutMs)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonObject status = gson.fromJson(r.body(), JsonObject.class);
            return r.statusCode() == 200 && status.get("model_loaded").getAsBoolean()
                    && "REAL_SWELL".equals(status.get("data_mode").getAsString());
        } catch (Exception e) { return false; }
    }

    public Optional<Prediction> predict(String participantCode, FeatureWindow w, BaselineProfile baseline,
                                        List<Double> recentScores, List<Double> recentDurations, List<Integer> recentBreaks, boolean demo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("participant_id", participantCode);
        body.put("features", w.toSwellFeatureMap());
        body.put("active_app_category", w.activeAppCategory.name());
        body.put("recent_stress_scores", recentScores.stream().filter(v -> Double.isFinite(v) && v >= 0 && v <= 10).toList()); body.put("recent_session_durations_min", recentDurations);
        body.put("recent_break_counts", recentBreaks); body.put("demo_mode", demo);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/predict")).timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body))).build();
            HttpResponse<String> r = http().send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                lastError = "ML service HTTP " + r.statusCode() +
                        (r.statusCode() == 503 ? ": trained model unavailable" : ": prediction request rejected");
                log.warn("{}", lastError); return Optional.empty();
            }
            Prediction result = parse(r.body(), w.id);
            lastError = "";
            return Optional.of(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lastError = "Prediction request interrupted";
            return Optional.empty();
        } catch (IOException | RuntimeException e) {
            lastError = "Prediction unavailable: " + e.getClass().getSimpleName();
            log.warn("{}", lastError);
            return Optional.empty();
        }
    }

    Prediction parse(String json, long featureId) {
        JsonObject o = gson.fromJson(json, JsonObject.class);
        if (o == null || !o.has("stress_score") || !o.has("model_name") || !o.has("data_mode")
                || !"REAL_SWELL".equals(o.get("data_mode").getAsString()))
            throw new IllegalArgumentException("Invalid real regression response");
        double score = o.get("stress_score").getAsDouble();
        if (!Double.isFinite(score) || score < 0 || score > 10)
            throw new IllegalArgumentException("Invalid questionnaire score");
        Prediction p = new Prediction(); p.featureId = featureId;
        p.stressClass = -1;
        p.stressLabel = o.has("stress_label") ? o.get("stress_label").getAsString() : "Stress Score";
        if (o.has("probabilities") && o.get("probabilities").isJsonObject()) {
            JsonObject pr = o.getAsJsonObject("probabilities");
            p.probLow = pr.has("Low Stress") ? pr.get("Low Stress").getAsDouble() : 0.0;
            p.probModerate = pr.has("Moderate Stress") ? pr.get("Moderate Stress").getAsDouble() : 0.0;
            p.probHigh = pr.has("High Stress") ? pr.get("High Stress").getAsDouble() : 0.0;
        }
        p.stressScore = o.has("stress_score") ? o.get("stress_score").getAsDouble() : 0.0;
        p.fatigueScore = o.has("fatigue_score") ? o.get("fatigue_score").getAsDouble() : 0.0;
        p.burnoutScore = o.has("burnout_score") ? o.get("burnout_score").getAsDouble() : 0.0;
        p.burnoutLabel = o.has("burnout_risk") ? o.get("burnout_risk").getAsString() : "Low";
        p.modelName = o.has("model_name") ? o.get("model_name").getAsString() : "n/a";
        p.dataMode = o.has("data_mode") ? o.get("data_mode").getAsString() : "REAL_SWELL";
        p.demoMode = o.has("demo_mode") && o.get("demo_mode").getAsBoolean();
        p.predictionKind = o.has("prediction_kind") ? o.get("prediction_kind").getAsString() : "behavioral_regression";
        if (o.has("top_factors") && o.get("top_factors").isJsonArray()) {
            o.getAsJsonArray("top_factors").forEach(el -> {
                JsonObject f = el.getAsJsonObject(); Prediction.Factor fac = new Prediction.Factor();
                fac.feature = f.has("feature") ? f.get("feature").getAsString() : "factor";
                fac.contribution = f.has("contribution") ? f.get("contribution").getAsDouble() : 0.0;
                fac.direction = f.has("direction") ? f.get("direction").getAsString() : "affects";
                fac.humanReadable = f.has("human_readable") ? f.get("human_readable").getAsString() : "";
                p.topFactors.add(fac);
            });
        }
        if (o.has("recommendations") && o.get("recommendations").isJsonArray()) {
            o.getAsJsonArray("recommendations").forEach(el -> p.recommendations.add(el.getAsString()));
        }
        return p;
    }
}
