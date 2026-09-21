package com.stressml.monitor;

import java.util.ArrayList;
import java.util.List;

/** Thread-safe accumulator of keystroke timing metadata for the current window. */
public class KeyboardStats {
    private final List<Long> holdMs = new ArrayList<>();
    private final List<Long> latencyMs = new ArrayList<>();
    private int keyPresses, corrections, pauseCount, burstCount;
    private long lastPressTime = -1, totalPauseMs, currentBurst;
    private static final long PAUSE_MS = 1500, BURST_GAP_MS = 250;

    public synchronized void onKeyPress(long now, boolean correction) {
        keyPresses++;
        if (correction) corrections++;
        if (lastPressTime > 0) {
            long gap = now - lastPressTime;
            latencyMs.add(gap);
            if (gap >= PAUSE_MS) { pauseCount++; totalPauseMs += gap; currentBurst = 0; }
            else if (gap <= BURST_GAP_MS) { if (++currentBurst == 5) burstCount++; }
            else currentBurst = 0;
        }
        lastPressTime = now;
    }

    public synchronized void onKeyHold(long ms) { if (ms > 0 && ms < 2000) holdMs.add(ms); }

    /** Snapshot and reset for the next window. */
    public synchronized Snapshot drain() {
        Snapshot s = new Snapshot(keyPresses, corrections, pauseCount, burstCount, totalPauseMs,
                new ArrayList<>(holdMs), new ArrayList<>(latencyMs));
        holdMs.clear(); latencyMs.clear();
        keyPresses = corrections = pauseCount = burstCount = 0; totalPauseMs = 0; currentBurst = 0;
        return s;
    }

    public record Snapshot(int keyPresses, int corrections, int pauseCount, int burstCount, long totalPauseMs,
                           List<Long> holdMs, List<Long> latencyMs) {}
}
