package com.stressml.ui;

import com.stressml.model.Prediction;
import com.stressml.util.AppContext;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Dashboard: stress level/score, fatigue, burnout indicator, session duration, live trend, recommendations. */
public class DashboardView extends VBox {
    public DashboardView(AppContext ctx) {
        getStyleClass().add("page");
        getChildren().add(UiKit.title("Dashboard — session mean features"));
        Label evidence = new Label("Nested participant validation has not established useful behavioral prediction. Population references are not individualized; desktop validity is unmeasured.");
        evidence.setWrapText(true); evidence.getStyleClass().add("card-sub"); getChildren().add(evidence);

        Label level = new Label("—"), score = new Label("—"), fatigue = new Label("—"), burnout = new Label("—"), duration = new Label("0h 00m");
        FlowPane cards = new FlowPane(14, 14);
        cards.getChildren().addAll(UiKit.card("STRESS ESTIMATE", level, "SWELL continuous model rating"), UiKit.card("STRESS SCORE", score, "0–10 SWELL questionnaire rating"),
                UiKit.card("ACTIVITY HEURISTIC", fatigue, "0–100; unvalidated"), UiKit.card("SCORE HISTORY", burnout, "descriptive heuristic, not a diagnosis"),
                UiKit.card("SESSION DURATION", duration, "current session"));
        getChildren().add(cards);

        NumberAxis x = new NumberAxis(), y = new NumberAxis(0, 10, 2); x.setLabel("Window"); y.setLabel("Stress score (0–10)");
        LineChart<Number, Number> chart = new LineChart<>(x, y); chart.setTitle("Stress score — current session"); chart.setCreateSymbols(false); chart.setAnimated(false); chart.setPrefHeight(260);
        XYChart.Series<Number, Number> series = new XYChart.Series<>(); series.setName("stress score"); chart.getData().add(series);
        getChildren().add(chart);

        Label recTitle = new Label("Recommendations"); recTitle.getStyleClass().add("card-title");
        VBox recs = new VBox(6); VBox recCard = new VBox(8, recTitle, recs); recCard.getStyleClass().add("card");
        HBox actions = new HBox(10);
        Button breakBtn = new Button("I took a break"); breakBtn.getStyleClass().add("primary"); breakBtn.setOnAction(e -> ctx.recordBreak());
        Label report = new Label("How stressed do you feel right now?");
        Button low = new Button("Low"), mod = new Button("Moderate"), high = new Button("High");
        low.setOnAction(e -> ctx.submitSelfReport(0)); mod.setOnAction(e -> ctx.submitSelfReport(1)); high.setOnAction(e -> ctx.submitSelfReport(2));
        actions.getChildren().addAll(breakBtn, report, low, mod, high);
        getChildren().addAll(recCard, actions);

        Runnable refresh = () -> {
            Prediction p = ctx.latestPrediction.get();
            level.setText(Double.isFinite(p.stressScore) ? ("reference_baseline".equals(p.predictionKind) ? "Population reference" : "Research estimate") : "Unavailable"); level.getStyleClass().removeAll("status-low", "status-moderate", "status-high"); level.getStyleClass().add(UiKit.stressStyle(p.stressClass));
            score.setText(Double.isFinite(p.stressScore) ? String.format("%.1f", p.stressScore) : "Unavailable"); fatigue.setText(String.format("%.0f", p.fatigueScore));
            burnout.setText(p.burnoutLabel.replace(" (indicator)", ""));
            recs.getChildren().clear();
            for (String r : p.recommendations) { Label l = new Label("• " + r); l.setWrapText(true); l.getStyleClass().add("rec-item"); recs.getChildren().add(l); }
            series.getData().clear();
            int i = 0; for (Prediction q : ctx.sessionPredictions) series.getData().add(new XYChart.Data<>(i++, q.stressScore));
        };
        ctx.latestPrediction.addListener((o, a, b) -> refresh.run());
        ctx.sessionPredictions.addListener((javafx.collections.ListChangeListener<Prediction>) c -> refresh.run()); refresh.run();
        Timeline clock = new Timeline(new KeyFrame(Duration.seconds(1), e -> duration.setText(UiKit.fmtDuration(ctx.sessionDuration()))));
        clock.setCycleCount(Timeline.INDEFINITE); clock.play();
    }
}
