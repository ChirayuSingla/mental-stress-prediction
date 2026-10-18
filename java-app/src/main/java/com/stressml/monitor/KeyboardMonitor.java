package com.stressml.monitor;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global keyboard listener that records timing and key classification metadata only.
 * Key codes are categorized into SWELL-KW counts (characters, spaces, error keys, direction,
 * shortcuts, special keys). Typed text content and characters are never stored.
 */
public class KeyboardMonitor implements NativeKeyListener {
    private final KeyboardStats stats;
    private final Map<Integer, Long> pressTimes = new ConcurrentHashMap<>();
    private static final java.util.Set<Integer> LETTER_KEYS = java.util.Set.of(
            NativeKeyEvent.VC_A, NativeKeyEvent.VC_B, NativeKeyEvent.VC_C, NativeKeyEvent.VC_D,
            NativeKeyEvent.VC_E, NativeKeyEvent.VC_F, NativeKeyEvent.VC_G, NativeKeyEvent.VC_H,
            NativeKeyEvent.VC_I, NativeKeyEvent.VC_J, NativeKeyEvent.VC_K, NativeKeyEvent.VC_L,
            NativeKeyEvent.VC_M, NativeKeyEvent.VC_N, NativeKeyEvent.VC_O, NativeKeyEvent.VC_P,
            NativeKeyEvent.VC_Q, NativeKeyEvent.VC_R, NativeKeyEvent.VC_S, NativeKeyEvent.VC_T,
            NativeKeyEvent.VC_U, NativeKeyEvent.VC_V, NativeKeyEvent.VC_W, NativeKeyEvent.VC_X,
            NativeKeyEvent.VC_Y, NativeKeyEvent.VC_Z);

    public KeyboardMonitor(KeyboardStats stats) { this.stats = stats; }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        long now = System.currentTimeMillis();
        int code = e.getKeyCode();
        pressTimes.put(code, now);

        boolean ctrl = (e.getModifiers() & NativeKeyEvent.CTRL_MASK) != 0;
        boolean correction = code == NativeKeyEvent.VC_BACKSPACE || code == NativeKeyEvent.VC_DELETE || (ctrl && code == NativeKeyEvent.VC_Z);
        boolean isChar = LETTER_KEYS.contains(code);
        boolean isSpace = code == NativeKeyEvent.VC_SPACE;
        boolean isDir = code == NativeKeyEvent.VC_UP || code == NativeKeyEvent.VC_DOWN
                || code == NativeKeyEvent.VC_LEFT || code == NativeKeyEvent.VC_RIGHT;
        boolean isShortcut = (ctrl && java.util.Set.of(NativeKeyEvent.VC_C, NativeKeyEvent.VC_X,
                NativeKeyEvent.VC_V, NativeKeyEvent.VC_Z, NativeKeyEvent.VC_S, NativeKeyEvent.VC_A).contains(code))
                || (code == NativeKeyEvent.VC_TAB && (e.getModifiers() & NativeKeyEvent.SHIFT_MASK) != 0);

        stats.onKeyPress(now, correction, isChar, isSpace, isDir, isShortcut);
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        Long pressed = pressTimes.remove(e.getKeyCode());
        if (pressed != null) stats.onKeyHold(System.currentTimeMillis() - pressed);
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) { /* character content intentionally ignored for privacy */ }
}
