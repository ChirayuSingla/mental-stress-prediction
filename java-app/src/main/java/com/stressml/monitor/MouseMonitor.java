package com.stressml.monitor;

import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseInputListener;

/** Global mouse listener recording movement geometry and click timing only. */
public class MouseMonitor implements NativeMouseInputListener {
    private final MouseStats stats;
    public MouseMonitor(MouseStats stats) { this.stats = stats; }

    @Override public void nativeMouseMoved(NativeMouseEvent e) { stats.onMove(e.getX(), e.getY(), System.currentTimeMillis()); }
    @Override public void nativeMouseDragged(NativeMouseEvent e) { stats.onMove(e.getX(), e.getY(), System.currentTimeMillis()); }
    @Override public void nativeMouseClicked(NativeMouseEvent e) { stats.onClick(e.getX(), e.getY(), System.currentTimeMillis(), e.getClickCount()); }
    @Override public void nativeMousePressed(NativeMouseEvent e) {}
    @Override public void nativeMouseReleased(NativeMouseEvent e) {}
}
