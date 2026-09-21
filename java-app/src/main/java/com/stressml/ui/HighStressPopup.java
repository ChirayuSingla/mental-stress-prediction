package com.stressml.ui;

import com.stressml.model.Prediction;
import com.stressml.util.AppContext;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * Non-intrusive floating toast/popup displayed on screen whenever elevated or high stress is detected.
 * Appears in the bottom-right corner of the primary display with actionable recommendations.
 */
public class HighStressPopup {
    private static Stage activePopup;

    public static synchronized void show(AppContext ctx, Prediction p, Runnable onOpenDashboard) {
        Platform.runLater(() -> {
            if (activePopup != null) {
                try { activePopup.close(); } catch (Exception ignored) {}
                activePopup = null;
            }

            Stage stage = new Stage();
            stage.initStyle(StageStyle.UNDECORATED);
            stage.initModality(Modality.NONE);
            stage.setAlwaysOnTop(true);
            stage.setTitle("High Stress Alert");

            VBox root = new VBox(12);
            root.getStyleClass().addAll("card", "stress-popup-card");
            root.setPadding(new Insets(16, 20, 16, 20));
            root.setPrefWidth(380);

            // Header row with Warning badge and Stress Score
            HBox header = new HBox(8);
            header.setAlignment(Pos.CENTER_LEFT);
            Label warningBadge = new Label("⚠️  HIGH STRESS DETECTED");
            warningBadge.getStyleClass().add("popup-warning-badge");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label scoreLabel = new Label(String.format("%.0f/100", p.stressScore));
            scoreLabel.getStyleClass().addAll("popup-score-badge", "status-high");

            header.getChildren().addAll(warningBadge, spacer, scoreLabel);

            // Subtitle
            Label sub = new Label("Your keyboard & mouse patterns show notable deviation from baseline.");
            sub.getStyleClass().add("card-sub");
            sub.setWrapText(true);

            // Primary factor / explanation
            VBox factorsBox = new VBox(4);
            if (!p.topFactors.isEmpty()) {
                Prediction.Factor top = p.topFactors.get(0);
                Label factorLabel = new Label("• Key factor: " + top.humanReadable);
                factorLabel.getStyleClass().add("popup-factor-text");
                factorLabel.setWrapText(true);
                factorsBox.getChildren().add(factorLabel);
            }

            // Recommendation text
            String rec = p.recommendations.isEmpty()
                    ? "Consider taking a short 5-minute breather or stretch."
                    : p.recommendations.get(0);
            Label recLabel = new Label(rec);
            recLabel.getStyleClass().add("popup-rec-text");
            recLabel.setWrapText(true);

            // Action buttons
            HBox actions = new HBox(8);
            actions.setAlignment(Pos.CENTER_RIGHT);

            Button breakBtn = new Button("☕ Take Break");
            breakBtn.getStyleClass().addAll("button", "primary");
            breakBtn.setOnAction(e -> {
                ctx.recordBreak();
                breakBtn.setText("✓ Break Logged");
                breakBtn.setDisable(true);
                Timeline closeLater = new Timeline(new KeyFrame(Duration.millis(800), ev -> stage.close()));
                closeLater.play();
            });

            Button viewBtn = new Button("📊 Dashboard");
            viewBtn.getStyleClass().addAll("button");
            viewBtn.setOnAction(e -> {
                stage.close();
                if (onOpenDashboard != null) onOpenDashboard.run();
            });

            Button dismissBtn = new Button("✕");
            dismissBtn.getStyleClass().add("popup-dismiss-btn");
            dismissBtn.setOnAction(e -> stage.close());

            actions.getChildren().addAll(breakBtn, viewBtn, dismissBtn);

            root.getChildren().addAll(header, sub, factorsBox, recLabel, actions);

            Scene scene = new Scene(root);
            try {
                scene.getStylesheets().add(HighStressPopup.class.getResource("/styles.css").toExternalForm());
            } catch (Exception ignored) {}

            stage.setScene(scene);

            // Position at bottom-right corner of primary screen
            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
            double x = bounds.getMaxX() - 400;
            double y = bounds.getMaxY() - 250;
            stage.setX(Math.max(x, 10));
            stage.setY(Math.max(y, 10));

            // Auto-close after 25 seconds if not interacted with
            Timeline autoDismiss = new Timeline(new KeyFrame(Duration.seconds(25), e -> {
                try { stage.close(); } catch (Exception ignored) {}
            }));
            autoDismiss.setCycleCount(1);
            autoDismiss.play();

            activePopup = stage;
            stage.show();
        });
    }
}
