package com.stressml;

import com.stressml.baseline.BaselineEngine;
import com.stressml.config.AppConfig;
import com.stressml.db.DatabaseManager;
import com.stressml.demo.SyntheticDataGenerator;
import com.stressml.feature.FeatureAggregator;
import com.stressml.ml.MlClient;
import com.stressml.monitor.MonitoringService;
import com.stressml.recommendation.RecommendationEngine;
import com.stressml.ui.ConsentDialog;
import com.stressml.ui.MainLayout;
import com.stressml.ui.NotificationService;
import com.stressml.util.AppContext;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** JavaFX application entry point. Wires configuration, database, monitoring, ML client and UI. */
public class App extends Application {
    private static final Logger log = LoggerFactory.getLogger(App.class);
    private AppContext ctx;

    @Override
    public void start(Stage stage) {
        AppConfig config = AppConfig.load();
        DatabaseManager db = new DatabaseManager(config);
        db.initialiseSchemaIfEmbedded();

        MlClient mlClient = new MlClient(config);
        BaselineEngine baseline = new BaselineEngine(config.baselineMinWindows());
        RecommendationEngine recEngine = new RecommendationEngine();
        FeatureAggregator aggregator = new FeatureAggregator(config.windowSeconds(), config.idleThresholdSeconds());
        MonitoringService monitoring = new MonitoringService(aggregator, config);
        SyntheticDataGenerator demo = new SyntheticDataGenerator(config.windowSeconds());

        ctx = new AppContext(config, db, mlClient, baseline, recEngine, aggregator, monitoring, demo);

        if (!ctx.isDemoMode() && !ConsentDialog.requestConsent(stage)) {
            log.info("Consent declined — exiting without monitoring.");
            stage.close();
            return;
        }
        ctx.startSession();

        // Allow app to continue monitoring in background when main window is hidden
        Platform.setImplicitExit(false);
        NotificationService notificationService = new NotificationService(stage, ctx);
        ctx.setHighStressListener(notificationService::notifyHighStress);

        // Calculate safe responsive dimensions: fits 720p/1080p at 100%, 125%, 150% DPI scaling
        javafx.geometry.Rectangle2D bounds = javafx.stage.Screen.getPrimary().getVisualBounds();
        double targetWidth = Math.min(1000.0, Math.max(720.0, bounds.getWidth() * 0.85));
        double targetHeight = Math.min(560.0, Math.max(440.0, bounds.getHeight() * 0.80));

        Scene scene = new Scene(new MainLayout(ctx), targetWidth, targetHeight);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        stage.setTitle("Behavioural Stress Monitor" + (ctx.isDemoMode() ? " — DEMO MODE" : ""));
        stage.setScene(scene);

        stage.setWidth(targetWidth);
        stage.setHeight(targetHeight);
        stage.setMinWidth(700);
        stage.setMinHeight(400);
        stage.setMaxWidth(bounds.getWidth() - 20);
        stage.setMaxHeight(bounds.getHeight() - 20);

        // Position centered away from screen bezels and Windows taskbar
        double posX = bounds.getMinX() + Math.max(10.0, (bounds.getWidth() - targetWidth) / 2.0);
        double posY = bounds.getMinY() + Math.max(20.0, (bounds.getHeight() - targetHeight) / 2.0);
        stage.setX(posX);
        stage.setY(posY);

        // Re-verify bounds once native OS window decorations (title bar & borders) are realized
        stage.setOnShown(e -> {
            double actualW = stage.getWidth();
            double actualH = stage.getHeight();
            if (actualH > bounds.getHeight() - 30) {
                actualH = bounds.getHeight() - 30;
                stage.setHeight(actualH);
            }
            if (actualW > bounds.getWidth() - 30) {
                actualW = bounds.getWidth() - 30;
                stage.setWidth(actualW);
            }
            double safeX = bounds.getMinX() + Math.max(10.0, (bounds.getWidth() - actualW) / 2.0);
            double safeY = bounds.getMinY() + Math.max(15.0, (bounds.getHeight() - actualH) / 2.0);
            stage.setX(safeX);
            stage.setY(safeY);
        });

        stage.show();
    }

    private void shutdown() {
        if (ctx != null) ctx.shutdown();
    }

    public static void main(String[] args) { launch(args); }
}
