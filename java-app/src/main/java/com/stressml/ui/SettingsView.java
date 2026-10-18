package com.stressml.ui;

import com.stressml.util.AppContext;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

/** Settings: monitoring on/off, privacy summary, calibration status, data deletion, break preferences. */
public class SettingsView extends VBox {
    public SettingsView(AppContext ctx) {
        getStyleClass().add("page");
        getChildren().add(UiKit.title("Settings"));

        CheckBox monitor = new CheckBox("Monitoring active"); monitor.setSelected(ctx.monitoringActive.get());
        monitor.setOnAction(e -> ctx.setMonitoring(monitor.isSelected()));
        VBox monCard = new VBox(6, new Label("Monitoring"), monitor, new Label("Mode: " + (ctx.isDemoMode() ? "DEMO (synthetic data)" : "REAL-DATA (live behavioural metadata)")));
        monCard.getStyleClass().add("card");

        Label privacy = new Label("""
                Privacy: only timing, counts, speeds and categories are stored. No typed characters, passwords, window titles,
                screenshots or clipboard contents are ever captured. Data is stored in your local database.
                Predictions use the local Python service. External Gemini re-phrasing is disabled.""");
        privacy.setWrapText(true); VBox privCard = new VBox(6, new Label("Privacy"), privacy); privCard.getStyleClass().add("card");

        Label calib = new Label(); calib.textProperty().bind(ctx.calibrationStatus);
        VBox calibCard = new VBox(6, new Label("Personal baseline calibration"), calib); calibCard.getStyleClass().add("card");

        Spinner<Integer> breakEvery = new Spinner<>(15, 180, 45, 5);
        VBox breakCard = new VBox(6, new Label("Preferred break reminder interval (minutes)"), breakEvery); breakCard.getStyleClass().add("card");

        Label bgInfo = new Label("""
                Background Operation & System Tray:
                When this window is closed using [X], the application continues monitoring silently in the background.
                If elevated or high stress is detected, a floating alert popup and system tray balloon will appear.
                Use the button below or right-click the system tray icon to completely quit the application.""");
        bgInfo.setWrapText(true);
        Button quitBtn = new Button("Quit & Exit Application");
        quitBtn.getStyleClass().add("danger");
        quitBtn.setOnAction(e -> {
            ctx.shutdown();
            System.exit(0);
        });
        VBox bgCard = new VBox(8, new Label("Background Operation & System Tray"), bgInfo, quitBtn);
        bgCard.getStyleClass().add("card");

        Button delete = new Button("Delete ALL my data"); delete.getStyleClass().add("danger");
        delete.setOnAction(e -> {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Permanently delete all sessions, features, predictions and baseline for this participant?", ButtonType.YES, ButtonType.NO);
            if (a.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) ctx.deleteAllData();
        });
        VBox delCard = new VBox(6, new Label("Data deletion"), delete); delCard.getStyleClass().add("card");
        getChildren().addAll(monCard, bgCard, privCard, calibCard, breakCard, delCard);
    }
}
