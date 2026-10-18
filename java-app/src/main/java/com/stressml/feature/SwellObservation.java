package com.stressml.feature;

import java.util.LinkedHashMap;
import java.util.Map;

/** Mean per-minute measurements over completed windows in the current session.
 * Mirrors SWELL's within-observation mean; participant baselines are not inputs.
 * A desktop session is not an experimentally controlled SWELL condition.
 */
public final class SwellObservation {
    private final Map<String, Double> sums = new LinkedHashMap<>();
    private int windows;

    public Map<String, Double> add(Map<String, Double> measured) {
        if (measured.isEmpty() || measured.values().stream().anyMatch(v -> v == null || !Double.isFinite(v) || v < 0))
            throw new IllegalArgumentException("Finite nonnegative measurements required");
        if (windows > 0 && !sums.keySet().equals(measured.keySet()))
            throw new IllegalArgumentException("Observation feature schema changed");
        measured.forEach((key, value) -> sums.merge(key, value, Double::sum));
        windows++;
        Map<String, Double> means = new LinkedHashMap<>();
        sums.forEach((key, value) -> means.put(key, value / windows));
        return means;
    }
    public int windows() { return windows; }
}
