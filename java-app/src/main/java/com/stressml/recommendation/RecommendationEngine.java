package com.stressml.recommendation;

import com.stressml.model.FeatureWindow;
import com.stressml.model.Prediction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rule-based recommendation engine (Java fallback used when the ML service provides none, and for
 * fully offline operation). Rules are personalised through baseline deviations where available.
 */
public class RecommendationEngine {
    public static final int LONG_SESSION_MIN = 45, VERY_LONG_SESSION_MIN = 90;

    public List<String> recommend(Prediction p, FeatureWindow w, Map<String, Double> deviations) {
        List<String> out = new ArrayList<>();
        double contMin = w.continuousWorkS / 60.0;
        double devErr = deviations.getOrDefault("dev_error_rate", 0.0);
        double devSwitch = deviations.getOrDefault("dev_app_switch_rate", 0.0);
        double devSpeed = deviations.getOrDefault("dev_typing_speed_wpm", 0.0);

        if (p.stressClass == 2 && contMin >= LONG_SESSION_MIN) out.add("High stress during a long continuous session — take a 5–10 minute break away from the screen.");
        else if (p.stressClass == 2) out.add("Stress indicators are high — pause briefly and breathe slowly before continuing.");
        if (devErr > 0.30) out.add("Typing errors are well above your usual level — a short rest usually restores accuracy.");
        if (contMin >= VERY_LONG_SESSION_MIN) out.add("More than 90 minutes of continuous work — stand up, stretch and move around.");
        if (devSwitch > 0.40 || w.appSwitchRate > 8) out.add("Application switching is unusually frequent — try single-tasking for the next block.");
        if (devSpeed < -0.25) out.add("Typing speed has dropped noticeably from your baseline, which often signals fatigue.");
        if (p.fatigueScore >= 60) out.add("Fatigue indicators are elevated — plan a longer break or wrap up the current task.");
        if (p.burnoutScore >= 65) out.add("Stress has been persistently high across recent sessions — plan a proper recovery period.");
        if (out.isEmpty()) out.add("Behaviour is within your normal range — keep the current pace and take regular micro-breaks.");
        if (deviations.isEmpty()) out.add("Calibration in progress — recommendations become personalised after the baseline sessions.");
        return out;
    }
}
