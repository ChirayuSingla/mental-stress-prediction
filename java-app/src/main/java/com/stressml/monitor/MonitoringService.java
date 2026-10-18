package com.stressml.monitor;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.stressml.config.AppConfig;
import com.stressml.feature.FeatureAggregator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/** Starts/stops the native hooks and the 1-second application poller. Monitoring state is always visible in the UI. */
public class MonitoringService {
    private static final Logger log = LoggerFactory.getLogger(MonitoringService.class);
    private final FeatureAggregator aggregator;
    private final KeyboardMonitor keyboardMonitor;
    private final MouseMonitor mouseMonitor;
    private final ApplicationMonitor appMonitor;
    private ScheduledExecutorService scheduler;
    private volatile boolean active;

    public MonitoringService(FeatureAggregator aggregator, AppConfig config) {
        this.aggregator = aggregator;
        this.keyboardMonitor = new KeyboardMonitor(aggregator.keyboardStats());
        this.mouseMonitor = new MouseMonitor(aggregator.mouseStats());
        this.appMonitor = aggregator.applicationMonitor();
    }

    public synchronized void start() {
        if (active) return;
        keyboardMonitor.reset(); mouseMonitor.reset(); aggregator.discardWindow();
        try {
            java.util.logging.Logger.getLogger(GlobalScreen.class.getPackage().getName()).setLevel(Level.WARNING);
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(keyboardMonitor);
            GlobalScreen.addNativeMouseListener(mouseMonitor);
            GlobalScreen.addNativeMouseMotionListener(mouseMonitor);
            GlobalScreen.addNativeMouseWheelListener(mouseMonitor);
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "app-poller"); t.setDaemon(true); return t; });
            scheduler.scheduleAtFixedRate(appMonitor::tick, 1, 1, TimeUnit.SECONDS);
            active = true;
            log.info("Monitoring started (behavioural metadata only)");
        } catch (NativeHookException e) {
            log.error("Could not register native hook (accessibility permission?)", e);
        }
    }

    public synchronized void stop() {
        if (!active) return;
        try {
            GlobalScreen.removeNativeKeyListener(keyboardMonitor);
            GlobalScreen.removeNativeMouseListener(mouseMonitor);
            GlobalScreen.removeNativeMouseMotionListener(mouseMonitor);
            GlobalScreen.removeNativeMouseWheelListener(mouseMonitor);
            GlobalScreen.unregisterNativeHook();
        } catch (NativeHookException e) {
            log.warn("Error unregistering hook", e);
        }
        if (scheduler != null) scheduler.shutdownNow();
        active = false;
        log.info("Monitoring paused");
    }

    public boolean isActive() { return active; }
}
