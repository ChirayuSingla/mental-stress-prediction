package com.stressml.monitor;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global keyboard listener that records ONLY timing metadata.
 * The key code is used transiently to pair press/release and to detect Backspace/Delete; it is never stored.
 */
public class KeyboardMonitor implements NativeKeyListener {
    private final KeyboardStats stats;
    private final Map<Integer, Long> pressTimes = new ConcurrentHashMap<>();

    public KeyboardMonitor(KeyboardStats stats) { this.stats = stats; }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        long now = System.currentTimeMillis();
        pressTimes.put(e.getKeyCode(), now);
        boolean correction = e.getKeyCode() == NativeKeyEvent.VC_BACKSPACE || e.getKeyCode() == NativeKeyEvent.VC_DELETE;
        stats.onKeyPress(now, correction);
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        Long pressed = pressTimes.remove(e.getKeyCode());
        if (pressed != null) stats.onKeyHold(System.currentTimeMillis() - pressed);
    }

    @Override public void nativeKeyTyped(NativeKeyEvent e) { /* character intentionally ignored */ }
}
