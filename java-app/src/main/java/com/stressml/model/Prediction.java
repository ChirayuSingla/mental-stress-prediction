package com.stressml.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Result returned by the ML service for one feature window. */
public class Prediction {
    public long id;
    public long featureId;
    public LocalDateTime predictedAt = LocalDateTime.now();
    public int stressClass = -1;
    public String stressLabel = "Unknown";
    public double probLow, probModerate, probHigh;
    public double stressScore = Double.NaN;
    public double fatigueScore, burnoutScore;
    public String burnoutLabel = "Insufficient history";
    public String modelName = "n/a";
    public String dataMode = "n/a";
    public boolean demoMode;
    public java.util.Map<String, Double> inputFeatures = java.util.Map.of();
    public int observationWindows = 1;
    public List<Factor> topFactors = new ArrayList<>();
    public List<String> recommendations = new ArrayList<>();

    public static class Factor {
        public String feature; public double contribution; public String direction; public String humanReadable;
    }
}
