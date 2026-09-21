package com.stressml.recommendation;

import com.stressml.model.FeatureWindow;
import com.stressml.model.Prediction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationEngineTest {
    @Test
    void highStressLongSessionRecommendsBreak() {
        Prediction p = new Prediction(); p.stressClass = 2;
        FeatureWindow w = new FeatureWindow(); w.continuousWorkS = 50 * 60;
        List<String> r = new RecommendationEngine().recommend(p, w, Map.of("dev_error_rate", 0.5));
        assertTrue(r.stream().anyMatch(s -> s.contains("break")));
        assertTrue(r.stream().anyMatch(s -> s.contains("errors")));
    }

    @Test
    void lowStressGivesReassurance() {
        Prediction p = new Prediction(); p.stressClass = 0;
        List<String> r = new RecommendationEngine().recommend(p, new FeatureWindow(), Map.of("dev_error_rate", 0.0));
        assertEquals(1, r.size());
    }
}
