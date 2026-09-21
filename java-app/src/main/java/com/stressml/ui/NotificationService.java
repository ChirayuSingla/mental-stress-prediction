package com.stressml.ui;

import com.stressml.model.Prediction;
import com.stressml.util.AppContext;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Manages background execution, system tray integration, and high-stress notifications/popups.
 */
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final Stage mainStage;
    private final AppContext ctx;
    private TrayIcon trayIcon;
    private boolean traySupported;
    private long lastAlertTimeMs = 0;

    public NotificationService(Stage mainStage, AppContext ctx) {
        this.mainStage = mainStage;
        this.ctx = ctx;
        initSystemTray();
        setupWindowCloseHandler();
    }

    private void initSystemTray() {
        if (!ctx.config.trayEnabled()) return;
        if (!GraphicsEnvironment.isHeadless() && SystemTray.isSupported()) {
            try {
                SystemTray tray = SystemTray.getSystemTray();
                Image image = createTrayIconImage();

                PopupMenu popup = new PopupMenu();

                MenuItem showItem = new MenuItem("Open Dashboard");
                showItem.addActionListener(e -> Platform.runLater(this::restoreMainStage));
                popup.add(showItem);

                MenuItem breakItem = new MenuItem("Take a 5-Min Break");
                breakItem.addActionListener(e -> Platform.runLater(ctx::recordBreak));
                popup.add(breakItem);

                popup.addSeparator();

                MenuItem pauseItem = new MenuItem("Pause Monitoring");
                pauseItem.addActionListener(e -> Platform.runLater(() -> {
                    boolean nowActive = !ctx.monitoringActive.get();
                    ctx.setMonitoring(nowActive);
                    pauseItem.setLabel(nowActive ? "Pause Monitoring" : "Resume Monitoring");
                }));
                popup.add(pauseItem);

                popup.addSeparator();

                MenuItem exitItem = new MenuItem("Exit Application");
                exitItem.addActionListener(e -> Platform.runLater(this::exitApplication));
                popup.add(exitItem);

                trayIcon = new TrayIcon(image, "Behavioural Stress Monitor", popup);
                trayIcon.setImageAutoSize(true);
                trayIcon.addActionListener(e -> Platform.runLater(this::restoreMainStage));

                tray.add(trayIcon);
                traySupported = true;
                log.info("System tray initialized successfully.");
            } catch (Exception e) {
                log.warn("Failed to initialize system tray: {}", e.getMessage());
                traySupported = false;
            }
        } else {
            traySupported = false;
            log.info("System tray not supported on this environment.");
        }
    }

    private Image createTrayIconImage() {
        int size = 16;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Gradient or vibrant blue badge
        g.setColor(new Color(37, 99, 235));
        g.fillRoundRect(1, 1, size - 2, size - 2, 5, 5);

        // White pulse/heartbeat symbol or letter S
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.drawString("S", 4, 12);
        g.dispose();
        return img;
    }

    private void setupWindowCloseHandler() {
        mainStage.setOnCloseRequest(e -> {
            if (traySupported) {
                e.consume();
                mainStage.hide();
                showTrayMessage(
                        "Stress Monitor Running in Background",
                        "The app is continuously monitoring and will pop up an alert if high stress is detected.",
                        TrayIcon.MessageType.INFO
                );
            } else {
                exitApplication();
            }
        });
    }

    public void restoreMainStage() {
        if (!mainStage.isShowing()) mainStage.show();
        mainStage.setIconified(false);
        mainStage.toFront();
        mainStage.requestFocus();
    }

    public synchronized void notifyHighStress(Prediction p) {
        long now = System.currentTimeMillis();
        long cooldownMs = ctx.config.notificationCooldownSeconds() * 1000L;
        if (now - lastAlertTimeMs < cooldownMs) {
            log.debug("High-stress alert suppressed due to cooldown.");
            return;
        }
        lastAlertTimeMs = now;

        log.info("Triggering High-Stress Alert: Score={}", p.stressScore);

        // 1. Native Windows System Tray Notification
        if (traySupported && trayIcon != null) {
            String rec = p.recommendations.isEmpty()
                    ? "Elevated stress detected. Please consider taking a short break."
                    : p.recommendations.get(0);
            showTrayMessage(
                    "⚠️ High Stress Detected (" + Math.round(p.stressScore) + "/100)",
                    rec,
                    TrayIcon.MessageType.WARNING
            );
        }

        // 2. On-screen floating Toast / Popup Window
        HighStressPopup.show(ctx, p, this::restoreMainStage);
    }

    public void showTrayMessage(String title, String message, TrayIcon.MessageType type) {
        if (traySupported && trayIcon != null) {
            try {
                trayIcon.displayMessage(title, message, type);
            } catch (Exception e) {
                log.warn("Tray displayMessage failed: {}", e.getMessage());
            }
        }
    }

    public boolean hasTray() {
        return traySupported;
    }

    public void exitApplication() {
        if (traySupported && trayIcon != null) {
            try {
                SystemTray.getSystemTray().remove(trayIcon);
            } catch (Exception ignored) {}
        }
        ctx.shutdown();
        System.exit(0);
    }
}
