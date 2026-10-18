package com.stressml.report;
import com.stressml.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ReportGeneratorTest {
    @TempDir Path temp;
    @Test void csvHeadersAndOrdinalLabelsMatchMeasuredRows() throws Exception {
        FeatureWindow w=new FeatureWindow(); w.windowStart=LocalDateTime.now(); w.sessionId=1;
        w.snChars=7; w.stressLabel=2;
        Path out=temp.resolve("features.csv");
        new ReportGenerator().exportCsv(out,"P001",List.of(w),false);
        var lines=Files.readAllLines(out);
        String[] header=lines.get(0).split(","), values=lines.get(1).split(",",-1);
        assertEquals(header.length,values.length);
        assertEquals("self_report_ordinal3",header[header.length-3]);
        assertEquals("2",values[values.length-3]);
        assertEquals("LIVE_DESKTOP",values[values.length-1]);
        assertFalse(lines.get(0).contains("SnMouseDistance"));
        assertFalse(Arrays.asList(header).contains("stress"));
    }
    @Test void dailyAverageUsesOnlyTheRequestedDay() {
        Session a=new Session();a.id=1;a.startedAt=LocalDateTime.of(2026,10,18,9,0);
        Session b=new Session();b.id=2;b.startedAt=a.startedAt.minusDays(1);
        String report=new ReportGenerator().dailyReport(a.startedAt.toLocalDate(),
            List.of(new double[]{1,2},new double[]{2,9}),List.of(a,b),false);
        assertTrue(report.contains("2.0 / 10"));assertFalse(report.contains("5.5 / 10"));
    }
}
