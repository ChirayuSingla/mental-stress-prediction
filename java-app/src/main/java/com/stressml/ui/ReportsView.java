package com.stressml.ui;

import com.stressml.report.ReportGenerator;
import com.stressml.util.AppContext;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDate;

/** Reports page: daily/weekly text reports and ML-compatible CSV export. */
public class ReportsView extends VBox {
    public ReportsView(AppContext ctx) {
        getStyleClass().add("page");
        getChildren().add(UiKit.title("Reports"));
        ReportGenerator gen = new ReportGenerator();
        TextArea out = new TextArea(); out.setEditable(false); out.setPrefHeight(360);
        Button daily = new Button("Daily report"), weekly = new Button("Weekly report"), csv = new Button("Export measured features (CSV)");
        daily.getStyleClass().add("primary"); weekly.getStyleClass().add("primary"); csv.getStyleClass().add("primary");
        daily.setOnAction(e -> run(out, () -> gen.dailyReport(LocalDate.now(), ctx.predictions.sessionScores(ctx.userId(), 10), ctx.sessions.recent(ctx.userId(), 50), ctx.isDemoMode())));
        weekly.setOnAction(e -> run(out, () -> gen.weeklyReport(ctx.predictions.sessionScores(ctx.userId(), 20), ctx.isDemoMode())));
        csv.setOnAction(e -> {
            FileChooser fc = new FileChooser(); fc.setInitialFileName((ctx.isDemoMode() ? "demo_" : "") + "behaviour_export.csv");
            File f = fc.showSaveDialog(getScene().getWindow());
            if (f != null) run(out, () -> { gen.exportCsv(f.toPath(), ctx.config.participantCode(), ctx.features.byUser(ctx.userId()), ctx.isDemoMode()); return "Exported to " + f; });
        });
        getChildren().addAll(new HBox(10, daily, weekly, csv), out);
    }

    private interface Task { String run() throws Exception; }
    private static void run(TextArea out, Task t) { try { out.setText(t.run()); } catch (Exception e) { out.setText("Error: " + e.getMessage()); } }
}
