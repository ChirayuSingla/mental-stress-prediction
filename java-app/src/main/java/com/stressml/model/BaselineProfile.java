package com.stressml.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Per-user baseline statistics for each calibrated feature. */
public class BaselineProfile {
    public static class Stat {
        public double mean, median, std, low, high; public int n;
        public Stat(double mean, double median, double std, double low, double high, int n) {
            this.mean = mean; this.median = median; this.std = std; this.low = low; this.high = high; this.n = n;
        }
    }
    public final Map<String, Stat> stats = new LinkedHashMap<>();
    public boolean isComplete() { return !stats.isEmpty(); }
}
