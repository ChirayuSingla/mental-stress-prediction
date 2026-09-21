package com.stressml.monitor;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.Psapi;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;

/**
 * Returns the process/executable name of the foreground window (used only to derive a category).
 * Windows: JNA. Linux: xdotool (if installed). macOS: osascript. Falls back to null.
 */
public class ActiveWindowProvider {
    private final String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

    public String foregroundProcessName() {
        try {
            if (os.contains("win")) return windows();
            if (os.contains("mac")) return exec("osascript", "-e",
                    "tell application \"System Events\" to get name of first application process whose frontmost is true");
            return exec("sh", "-c", "xdotool getactivewindow getwindowclassname 2>/dev/null || xdotool getactivewindow getwindowname");
        } catch (Throwable t) {
            return null;
        }
    }

    private String windows() {
        WinDef.HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        if (hwnd == null) return null;
        IntByReference pid = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
        WinNT.HANDLE h = Kernel32.INSTANCE.OpenProcess(0x0400 | 0x0010, false, pid.getValue());
        if (h == null) return null;
        try {
            char[] buf = new char[512];
            int len = Psapi.INSTANCE.GetModuleFileNameExW(h, null, buf, buf.length);
            if (len > 0) {
                String path = Native.toString(buf).substring(0, Math.min(len, buf.length));
                int lastSlash = Math.max(path.lastIndexOf('\\'), path.lastIndexOf('/'));
                return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
            }
            return null;
        } finally { Kernel32.INSTANCE.CloseHandle(h); }
    }

    private String exec(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line = r.readLine();
            p.waitFor();
            return line;
        }
    }
}
