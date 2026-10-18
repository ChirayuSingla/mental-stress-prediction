package com.stressml.report;

import com.stressml.model.FeatureWindow;
import com.stressml.model.Session;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** CSV export (ML-pipeline compatible) and simple daily/weekly text reports. */
public class ReportGenerator {

    public void exportCsv(Path path, String participantCode, List<FeatureWindow> windows, boolean demo) throws IOException {
        String[] columns = {"SnKeyStrokes", "SnChars", "SnSpecialKeys", "SnDirectionKeys",
                "SnErrorKeys", "SnShortcutKeys", "SnSpaces", "CharactersRatio", "ErrorKeyRatio",
                "SnLeftClicked", "SnRightClicked", "SnDoubleClicked", "SnWheel", "SnDragged",
                "SnMouseDistance", "SnAppChange"};
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(path))) {
            pw.println(String.join(",", FeatureWindow.CSV_HEADER) + ",is_synthetic,data_source");
            int idx = 0;
            for (FeatureWindow w : windows) {
                java.util.List<String> row = new java.util.ArrayList<>();
                row.add(participantCode.replace(",", "_"));
                row.add(String.valueOf(w.sessionId)); row.add(w.windowStart.toString()); row.add(String.valueOf(idx++));
                java.util.Map<String, Double> measured = w.toSwellFeatureMap();
                for (String column : columns) row.add(measured.containsKey(column) ? f(measured.get(column)) : "");
                row.add(w.stressLabel == null ? "" : String.valueOf(w.stressLabel));
                row.add(demo ? "1" : "0"); row.add(demo ? "SYNTHETIC_DEMO" : "LIVE_DESKTOP");
                pw.println(String.join(",", row));
            }
        }
    }

    public String dailyReport(LocalDate day, List<double[]> sessionScores, List<Session> sessions, boolean demo) {
        StringBuilder sb = new StringBuilder();
        if (demo) sb.append("*** DEMO MODE — SYNTHETIC DATA ***\n");
        sb.append("Daily report — ").append(day.format(DateTimeFormatter.ISO_DATE)).append('\n');
        sb.append("Sessions today: ").append(sessions.stream().filter(s -> s.startedAt.toLocalDate().equals(day)).count()).append('\n');
        double avg = sessionScores.stream().mapToDouble(a -> a[1]).average().orElse(Double.NaN);
        sb.append(String.format(Locale.ROOT, "Average stress score (recent sessions): %.1f / 10%n", avg));
        sb.append("Note: behavioural indicators only — not a medical assessment.\n");
        return sb.toString();
    }

    public String weeklyReport(List<double[]> sessionScores, boolean demo) {
        StringBuilder sb = new StringBuilder();
        if (demo) sb.append("*** DEMO MODE — SYNTHETIC DATA ***\n");
        sb.append("Weekly report\n");
        for (double[] s : sessionScores) sb.append(String.format(Locale.ROOT, "Session %d: stress %.1f, fatigue %.1f, breaks %d, duration %.0f min%n",
                (int) s[0], s[1], s[2], (int) s[3], s[4]));
        long high = sessionScores.stream().filter(s -> s[1] >= 6).count();
        sb.append("Sessions above user alert preference (6/10): ").append(high).append(" of ").append(sessionScores.size()).append('\n');
        return sb.toString();
    }

    private static String f(double v) { return String.format(Locale.ROOT, "%.4f", v); }
}
