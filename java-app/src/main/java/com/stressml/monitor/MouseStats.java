package com.stressml.monitor;

import java.util.ArrayList;
import java.util.List;

/** Accumulates mouse geometry/timing for the current window. */
public class MouseStats {
    private double lastX = Double.NaN, lastY, lastClickX = Double.NaN, lastClickY, lastSpeed = -1, lastAngle = Double.NaN;
    private long lastMoveTime = -1, lastActivity = -1;
    private double distance, accelSum;
    private final List<Double> speeds = new ArrayList<>();
    private final List<Double> clickDistances = new ArrayList<>();
    private int clicks, doubleClicks, directionChanges, accelSamples;

    public synchronized void onMove(int x, int y, long now) {
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

    public synchronized void onClick(int x, int y, long now, int clickCount) {
        clicks++;
        if (clickCount >= 2) doubleClicks++;
        if (!Double.isNaN(lastClickX)) clickDistances.add(Math.hypot(x - lastClickX, y - lastClickY));
        lastClickX = x; lastClickY = y; lastActivity = now;
    }

    public synchronized long lastActivity() { return lastActivity; }

    public synchronized Snapshot drain() {
        Snapshot s = new Snapshot(distance, new ArrayList<>(speeds), accelSamples == 0 ? 0 : accelSum / accelSamples,
                clicks, doubleClicks, new ArrayList<>(clickDistances), directionChanges);
        distance = 0; accelSum = 0; accelSamples = 0; speeds.clear(); clickDistances.clear();
        clicks = doubleClicks = directionChanges = 0;
        return s;
    }

    public record Snapshot(double distance, List<Double> speeds, double avgAccel, int clicks, int doubleClicks,
                           List<Double> clickDistances, int directionChanges) {}
}
