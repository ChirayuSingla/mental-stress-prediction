package com.stressml.monitor;

import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseInputListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseWheelEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseWheelListener;

/** Global mouse listener recording movement geometry, click timing, wheel, and drag operations. */
public class MouseMonitor implements NativeMouseInputListener, NativeMouseWheelListener {
    private final MouseStats stats;
    private boolean dragging;
    public MouseMonitor(MouseStats stats) { this.stats = stats; }
    public void reset() { dragging = false; }

    @Override
    public void nativeMouseMoved(NativeMouseEvent e) {
        stats.onMove(e.getX(), e.getY(), System.currentTimeMillis());
    }

    @Override
    public void nativeMouseDragged(NativeMouseEvent e) {
        if (!dragging) { stats.onDragStart(); dragging = true; }
        stats.onDrag(e.getX(), e.getY(), System.currentTimeMillis());
    }

    @Override
    public void nativeMouseClicked(NativeMouseEvent e) {
        stats.onClick(e.getX(), e.getY(), System.currentTimeMillis(), e.getClickCount(), e.getButton());
    }

    @Override
    public void nativeMouseWheelMoved(NativeMouseWheelEvent e) {
        stats.onWheel(System.currentTimeMillis());
    }

    @Override public void nativeMousePressed(NativeMouseEvent e) {}
    @Override public void nativeMouseReleased(NativeMouseEvent e) { dragging = false; }
}
