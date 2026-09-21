package com.stressml.feature;

import com.stressml.model.AppCategory;
import com.stressml.model.FeatureWindow;
import com.stressml.monitor.ApplicationMonitor;
import com.stressml.monitor.KeyboardStats;
import com.stressml.monitor.MouseStats;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FeatureAggregatorTest {
    @Test
    void computesTypingAndMouseFeatures() {
        FeatureAggregator agg = new FeatureAggregator(60, 20);
        KeyboardStats.Snapshot k = new KeyboardStats.Snapshot(300, 15, 2, 4, 4000, List.of(90L, 110L), List.of(150L, 250L, 200L));
        MouseStats.Snapshot m = new MouseStats.Snapshot(12000, List.of(400.0, 600.0), 900, 12, 2, List.of(100.0, 300.0), 40);
        ApplicationMonitor.Snapshot a = new ApplicationMonitor.Snapshot(3, AppCategory.IDE, Map.of(AppCategory.IDE, 60.0), 60);
        FeatureWindow w = agg.build(k, m, a, LocalDateTime.now());
        assertEquals(60.0, w.typingSpeedWpm, 1e-9);           // 300 keys / 5
        assertEquals(0.05, w.errorRate, 1e-9);                // 15 / 300
        assertEquals(100.0, w.avgKeyHoldMs, 1e-9);
        assertEquals(500.0, w.mouseSpeedPxS, 1e-9);
        assertEquals(3.0, w.appSwitchRate, 1e-9);
        assertEquals(AppCategory.IDE, w.activeAppCategory);
        assertTrue(w.continuousWorkS > 0);
        assertEquals(26, w.toFeatureMap().size());            // matches ml/preprocessing/features.py ABSOLUTE
    }

    @Test
    void idleWindowResetsContinuousWork() {
        FeatureAggregator agg = new FeatureAggregator(60, 20);
        KeyboardStats.Snapshot active = new KeyboardStats.Snapshot(100, 0, 0, 0, 0, List.of(), List.of());
        KeyboardStats.Snapshot idle = new KeyboardStats.Snapshot(0, 0, 0, 0, 0, List.of(), List.of());
        MouseStats.Snapshot none = new MouseStats.Snapshot(0, List.of(), 0, 0, 0, List.of(), 0);
        ApplicationMonitor.Snapshot a = new ApplicationMonitor.Snapshot(0, AppCategory.OTHER, Map.of(), 60);
        agg.build(active, none, a, LocalDateTime.now());
        FeatureWindow w = agg.build(idle, none, a, LocalDateTime.now());
        assertEquals(0.0, w.continuousWorkS, 1e-9);
        assertEquals(60.0, w.idleDurationS, 1e-9);
    }
}
