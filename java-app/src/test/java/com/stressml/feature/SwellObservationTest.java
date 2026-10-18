package com.stressml.feature;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class SwellObservationTest {
    @Test void invalidOrChangingSchemasDoNotAdvanceObservation() {
        SwellObservation obs=new SwellObservation();obs.add(Map.of("SnChars",10.));
        assertThrows(IllegalArgumentException.class,()->obs.add(Map.of("SnChars",Double.NaN)));
        assertThrows(IllegalArgumentException.class,()->obs.add(Map.of("SnSpaces",1.)));
        assertEquals(1,obs.windows());
    }
    @Test void averagesOnlyCompletedCurrentSessionMeasurements() {
        SwellObservation obs = new SwellObservation();
        obs.add(Map.of("SnChars", 10., "ErrorKeyRatio", .1));
        var second = obs.add(Map.of("SnChars", 30., "ErrorKeyRatio", .3));
        assertEquals(20., second.get("SnChars")); assertEquals(.2, second.get("ErrorKeyRatio"), 1e-9);
        assertEquals(2, obs.windows());
        assertEquals(100., new SwellObservation().add(Map.of("SnChars", 100.)).get("SnChars"));
    }
}
