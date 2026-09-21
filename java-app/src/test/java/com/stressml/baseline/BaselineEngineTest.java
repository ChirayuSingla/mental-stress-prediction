package com.stressml.baseline;

import com.stressml.model.FeatureWindow;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BaselineEngineTest {
    private FeatureWindow window(double wpm, double err) { FeatureWindow w = new FeatureWindow(); w.typingSpeedWpm = wpm; w.errorRate = err; return w; }

    @Test
    void deviationIsRelativeToPersonalMean() {
        BaselineEngine e = new BaselineEngine(3);
        e.addCalibrationWindow(window(50, 0.04)); e.addCalibrationWindow(window(60, 0.04)); e.addCalibrationWindow(window(55, 0.04));
        assertTrue(e.hasEnoughCalibrationData());
        e.computeProfile();
        Map<String, Double> d = e.deviations(window(38, 0.056));
        assertEquals(-0.309, d.get("dev_typing_speed_wpm"), 0.01);   // (38-55)/55
        assertEquals(0.40, d.get("dev_error_rate"), 0.01);            // +40 %
    }

    @Test
    void noDeviationsBeforeCalibration() {
        BaselineEngine e = new BaselineEngine(30);
        assertTrue(e.deviations(window(50, 0.04)).isEmpty());
        assertFalse(e.isCalibrated());
    }

    @Test
    void quantileInterpolates() {
        assertEquals(2.5, BaselineEngine.quantile(java.util.List.of(1.0, 2.0, 3.0, 4.0), 0.5), 1e-9);
    }
}
