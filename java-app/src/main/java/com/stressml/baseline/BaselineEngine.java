package com.stressml.baseline;

import com.stressml.model.BaselineProfile;
import com.stressml.model.FeatureWindow;

import java.util.*;

/**
 * Personal Baseline Engine — the core research contribution.
 *
 * During calibration sessions, windows are accumulated; the profile stores mean / median / std / IQR range
 * for each feature in {@link #BASELINE_FEATURES}. Afterwards, every new window is expressed as
 *   relative deviation  = (current - mean) / |mean|
 *   z-score deviation   = (current - mean) / std
 * exactly mirroring ml/preprocessing/baseline.py so training and runtime features agree.
 */
public class BaselineEngine {
    public static final List<String> BASELINE_FEATURES = List.of("typing_speed_wpm", "error_rate", "avg_key_latency_ms",
            "mouse_speed_px_s", "click_rate", "app_switch_rate", "idle_duration_s", "continuous_work_s", "mouse_speed_cv");
    private static final double EPS = 1e-6;

    private final int minWindows;
    private final Map<String, List<Double>> calibration = new HashMap<>();
    private BaselineProfile profile = new BaselineProfile();

    public BaselineEngine(int minWindows) {
        this.minWindows = minWindows;
        for (String f : BASELINE_FEATURES) calibration.put(f, new ArrayList<>());
    }

    public void addCalibrationWindow(FeatureWindow w) {
        Map<String, Double> m = w.toFeatureMap();
        for (String f : BASELINE_FEATURES) calibration.get(f).add(m.get(f));
    }

    public int calibrationWindows() { return calibration.get(BASELINE_FEATURES.get(0)).size(); }
    public boolean isCalibrated() { return profile.isComplete(); }
    public boolean hasEnoughCalibrationData() { return calibrationWindows() >= minWindows; }
    public BaselineProfile profile() { return profile; }
    public void setProfile(BaselineProfile p) { this.profile = p == null ? new BaselineProfile() : p; }

    /** Finalise calibration into a profile (mean, median, std, IQR fences). */
    public BaselineProfile computeProfile() {
        BaselineProfile p = new BaselineProfile();
        for (String f : BASELINE_FEATURES) {
            List<Double> xs = new ArrayList<>(calibration.get(f));
            if (xs.isEmpty()) continue;
            Collections.sort(xs);
            double mean = xs.stream().mapToDouble(d -> d).average().orElse(0);
            double var = xs.stream().mapToDouble(d -> (d - mean) * (d - mean)).sum() / xs.size();
            double q1 = quantile(xs, 0.25), q3 = quantile(xs, 0.75), iqr = q3 - q1;
            p.stats.put(f, new BaselineProfile.Stat(mean, quantile(xs, 0.5), Math.sqrt(var), q1 - 1.5 * iqr, q3 + 1.5 * iqr, xs.size()));
        }
        profile = p;
        return p;
    }

    /** Deviation features for one window; empty map when not calibrated. */
    public Map<String, Double> deviations(FeatureWindow w) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (!profile.isComplete()) return out;
        Map<String, Double> m = w.toFeatureMap();
        for (String f : BASELINE_FEATURES) {
            BaselineProfile.Stat s = profile.stats.get(f);
            if (s == null) continue;
            out.put("dev_" + f, (m.get(f) - s.mean) / (Math.abs(s.mean) + EPS));
            out.put("z_" + f, Math.max(-10, Math.min(10, (m.get(f) - s.mean) / (s.std + EPS))));   // capped, mirrors Python
        }
        return out;
    }

    static double quantile(List<Double> sorted, double q) {
        if (sorted.isEmpty()) return 0;
        double pos = q * (sorted.size() - 1);
        int lo = (int) Math.floor(pos), hi = (int) Math.ceil(pos);
        return sorted.get(lo) + (pos - lo) * (sorted.get(hi) - sorted.get(lo));
    }
}
