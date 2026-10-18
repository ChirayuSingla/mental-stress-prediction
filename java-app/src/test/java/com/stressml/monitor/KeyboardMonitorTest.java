package com.stressml.monitor;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class KeyboardMonitorTest {
    private void key(KeyboardMonitor monitor, int code, int mods) {
        monitor.nativeKeyPressed(new NativeKeyEvent(NativeKeyEvent.NATIVE_KEY_PRESSED, mods, 0, code, NativeKeyEvent.CHAR_UNDEFINED));
    }
    @Test void countsNoncontiguousLetterCodesAndOverlappingSwellCategories() {
        KeyboardStats stats = new KeyboardStats(); KeyboardMonitor m = new KeyboardMonitor(stats);
        key(m, NativeKeyEvent.VC_A, 0); key(m, NativeKeyEvent.VC_Z, 0); key(m, NativeKeyEvent.VC_Q, 0);
        key(m, NativeKeyEvent.VC_1, 0); key(m, NativeKeyEvent.VC_SPACE, 0);
        key(m, NativeKeyEvent.VC_BACKSPACE, 0); key(m, NativeKeyEvent.VC_LEFT, 0);
        key(m, NativeKeyEvent.VC_Z, NativeKeyEvent.CTRL_MASK);
        key(m, NativeKeyEvent.VC_C, NativeKeyEvent.CTRL_MASK);
        key(m, NativeKeyEvent.VC_TAB, NativeKeyEvent.SHIFT_MASK);
        var s = stats.drain();
        assertEquals(10, s.keyPresses()); assertEquals(5, s.chars()); assertEquals(5, s.specialKeys());
        assertEquals(2, s.corrections()); assertEquals(3, s.shortcutKeys());
        assertEquals(1, s.spaces()); assertEquals(1, s.directionKeys());
    }
}
