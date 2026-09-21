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
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(path))) {
            pw.println(String.join(",", FeatureWindow.CSV_HEADER) + ",is_synthetic");
            int idx = 0; int lastSession = -1;
            for (FeatureWindow w : windows) {
                if (w.sessionId != lastSession) { idx = 0; lastSession = w.sessionId; }
                pw.println(String.join(",", participantCode, participantCode + "_S" + w.sessionId, w.windowStart.toString(), String.valueOf(idx++),
                        f(w.typingSpeedWpm), f(w.keystrokesPerMin), f(w.avgKeyHoldMs), f(w.avgKeyLatencyMs), f(w.backspaceRate), f(w.errorRate),
                        String.valueOf(w.pauseCount), f(w.avgPauseMs), String.valueOf(w.burstCount), f(w.typingRhythmCv), f(w.mouseDistancePx),
                        f(w.mouseSpeedPxS), f(w.mouseAccelPxS2), String.valueOf(w.clickCount), f(w.clickRate), f(w.doubleClickRate),
                        f(w.avgClickDistancePx), f(w.mouseIdleS), String.valueOf(w.directionChanges), f(w.mouseSpeedCv),
                        String.valueOf(w.appSwitchCount), f(w.appSwitchRate), w.activeAppCategory.name(), f(w.timeInAppS), f(w.workDurationS),
                        f(w.idleDurationS), f(w.continuousWorkS), w.stressLabel == null ? "" : String.valueOf(w.stressLabel), demo ? "1" : "0"));
            }
        }
    }

    public String dailyReport(LocalDate day, List<double[]> sessionScores, List<Session> sessions, boolean demo) {
        StringBuilder sb = new StringBuilder();
        if (demo) sb.append("*** DEMO MODE — SYNTHETIC DATA ***\n");
        sb.append("Daily report — ").append(day.format(DateTimeFormatter.ISO_DATE)).append('\n');
        sb.append("Sessions today: ").append(sessions.stream().filter(s -> s.startedAt.toLocalDate().equals(day)).count()).append('\n');
        double avg = sessionScores.stream().mapToDouble(a -> a[1]).average().orElse(Double.NaN);
        sb.append(String.format(Locale.ROOT, "Average stress score (recent sessions): %.1f / 100%n", avg));
        sb.append("Note: behavioural indicators only — not a medical assessment.\n");
        return sb.toString();
    }

    public String weeklyReport(List<double[]> sessionScores, boolean demo) {
        StringBuilder sb = new StringBuilder();
        if (demo) sb.append("*** DEMO MODE — SYNTHETIC DATA ***\n");
        sb.append("Weekly report\n");
        for (double[] s : sessionScores) sb.append(String.format(Locale.ROOT, "Session %d: stress %.1f, fatigue %.1f, breaks %d, duration %.0f min%n",
                (int) s[0], s[1], s[2], (int) s[3], s[4]));
        long high = sessionScores.stream().filter(s -> s[1] >= 60).count();
        sb.append("High-stress sessions: ").append(high).append(" of ").append(sessionScores.size()).append('\n');
        return sb.toString();
    }

    private static String f(double v) { return String.format(Locale.ROOT, "%.4f", v); }
}
