package com.stressml.monitor;

import java.util.ArrayList;
import java.util.List;

/** Accumulates mouse geometry, timing, and SWELL-KW mouse action metrics for the current window. */
public class MouseStats {
    private double lastX = Double.NaN, lastY, lastClickX = Double.NaN, lastClickY, lastSpeed = -1, lastAngle = Double.NaN;
    private long lastMoveTime = -1, lastActivity = -1;
    private double distance, accelSum;
    private final List<Double> speeds = new ArrayList<>();
    private final List<Double> clickDistances = new ArrayList<>();
    private int clicks, doubleClicks, directionChanges, accelSamples;
    private int leftClicks, rightClicks, wheelEvents, dragEvents, mouseActs;

    public synchronized void onMove(int x, int y, long now) {
        mouseActs++;
        if (!Double.isNaN(lastX)) {
            double dx = x - lastX, dy = y - lastY, d = Math.hypot(dx, dy);
            long dt = Math.max(now - lastMoveTime, 1);
            double speed = d / (dt / 1000.0);
            distance += d; speeds.add(speed);
            if (lastSpeed >= 0) { accelSum += Math.abs(speed - lastSpeed) / (dt / 1000.0); accelSamples++; }
            double angle = Math.atan2(dy, dx);
            if (!Double.isNaN(lastAngle) && d > 3 && Math.abs(angleDiff(angle, lastAngle)) > Math.PI / 2) directionChanges++;
            if (d > 3) lastAngle = angle;
            lastSpeed = speed;
        }
        lastX = x; lastY = y; lastMoveTime = now; lastActivity = now;
    }

    private static double angleDiff(double a, double b) { double d = a - b; while (d > Math.PI) d -= 2 * Math.PI; while (d < -Math.PI) d += 2 * Math.PI; return d; }

    public synchronized void onClick(int x, int y, long now, int clickCount, int button) {
        mouseActs++;
        clicks++;
        if (button == 1) leftClicks++;
        else if (button == 2) rightClicks++;

        if (clickCount >= 2) doubleClicks++;
        if (!Double.isNaN(lastClickX)) clickDistances.add(Math.hypot(x - lastClickX, y - lastClickY));
        lastClickX = x; lastClickY = y; lastActivity = now;
    }

    public synchronized void onDrag(int x, int y, long now) {
        onMove(x, y, now);
    }

    public synchronized void onDragStart() { dragEvents++; }

    public synchronized void onWheel(long now) {
        mouseActs++;
        wheelEvents++;
        lastActivity = now;
    }

    public synchronized long lastActivity() { return lastActivity; }
    public synchronized void reset() {
        drain(); lastX = lastClickX = lastAngle = Double.NaN;
        lastSpeed = -1; lastMoveTime = lastActivity = -1;
    }

    public synchronized Snapshot drain() {
        Snapshot s = new Snapshot(distance, new ArrayList<>(speeds), accelSamples == 0 ? 0 : accelSum / accelSamples,
                clicks, doubleClicks, new ArrayList<>(clickDistances), directionChanges,
                leftClicks, rightClicks, wheelEvents, dragEvents, mouseActs);
        distance = 0; accelSum = 0; accelSamples = 0; speeds.clear(); clickDistances.clear();
        clicks = doubleClicks = directionChanges = 0;
        leftClicks = rightClicks = wheelEvents = dragEvents = mouseActs = 0;
        return s;
    }

    public record Snapshot(double distance, List<Double> speeds, double avgAccel, int clicks, int doubleClicks,
                           List<Double> clickDistances, int directionChanges,
                           int leftClicks, int rightClicks, int wheelEvents, int dragEvents, int mouseActs) {
        public Snapshot(double distance, List<Double> speeds, double avgAccel, int clicks, int doubleClicks,
                        List<Double> clickDistances, int directionChanges) {
            this(distance, speeds, avgAccel, clicks, doubleClicks, clickDistances, directionChanges,
                 clicks, 0, 0, 0, clicks + speeds.size());
        }
    }
}
