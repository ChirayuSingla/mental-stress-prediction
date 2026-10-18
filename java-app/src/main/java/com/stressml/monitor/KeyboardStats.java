package com.stressml.monitor;

import java.util.ArrayList;
import java.util.List;

/** Thread-safe accumulator of keystroke timing and SWELL-KW key classification metadata for the current window. */
public class KeyboardStats {
    private final List<Long> holdMs = new ArrayList<>();
    private final List<Long> latencyMs = new ArrayList<>();
    private int keyPresses, corrections, pauseCount, burstCount;
    private int chars, spaces, directionKeys, shortcutKeys;
    private long lastPressTime = -1, totalPauseMs, currentBurst;
    private static final long PAUSE_MS = 1500, BURST_GAP_MS = 250;

    public synchronized void onKeyPress(long now, boolean correction) {
        onKeyPress(now, correction, false, false, false, false);
    }

    public synchronized void onKeyPress(long now, boolean correction, boolean isChar, boolean isSpace, boolean isDir, boolean isShortcut) {
        keyPresses++;
        if (correction) corrections++;
        if (isChar) chars++;
        if (isSpace) spaces++;
        if (isDir) directionKeys++;
        if (isShortcut) shortcutKeys++;

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
        int special = keyPresses - chars; // SWELL: all non-letter keys, categories overlap
        Snapshot s = new Snapshot(keyPresses, corrections, pauseCount, burstCount, totalPauseMs,
                new ArrayList<>(holdMs), new ArrayList<>(latencyMs), chars, spaces, directionKeys, shortcutKeys, special);
        holdMs.clear(); latencyMs.clear();
        keyPresses = corrections = pauseCount = burstCount = 0;
        chars = spaces = directionKeys = shortcutKeys = 0;
        totalPauseMs = 0; currentBurst = 0;
        return s;
    }

    public record Snapshot(int keyPresses, int corrections, int pauseCount, int burstCount, long totalPauseMs,
                           List<Long> holdMs, List<Long> latencyMs,
                           int chars, int spaces, int directionKeys, int shortcutKeys, int specialKeys) {
        public Snapshot(int keyPresses, int corrections, int pauseCount, int burstCount, long totalPauseMs,
                        List<Long> holdMs, List<Long> latencyMs) {
            this(keyPresses, corrections, pauseCount, burstCount, totalPauseMs, holdMs, latencyMs,
                 0, 0, 0, 0, keyPresses); // legacy fixture has no measured key categories
        }
    }
}
