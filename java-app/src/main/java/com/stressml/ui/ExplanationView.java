package com.stressml.ui;

import com.stressml.model.BaselineProfile;
import com.stressml.model.FeatureWindow;
import com.stressml.model.Prediction;
import com.stressml.util.AppContext;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.util.Map;

/** Explainable-AI page: SHAP-style top factors + personal baseline comparison table. */
public class ExplanationView extends VBox {
    public ExplanationView(AppContext ctx) {
        getStyleClass().add("page");
        getChildren().add(UiKit.title("Why this prediction?"));
        Label q = new Label(); q.getStyleClass().add("card-sub");

        CategoryAxis x = new CategoryAxis(); NumberAxis y = new NumberAxis(); y.setLabel("score difference vs median replacement");
        BarChart<String, Number> bars = new BarChart<>(x, y); bars.setTitle("Model sensitivity (not SHAP or causality)"); bars.setLegendVisible(false); bars.setAnimated(false); bars.setPrefHeight(260);
        VBox factorList = new VBox(4); VBox factorCard = new VBox(8, new Label("Main contributing factors"), factorList); factorCard.getStyleClass().add("card");

        GridPane grid = new GridPane(); grid.setHgap(18); grid.setVgap(4);
        VBox baseCard = new VBox(8, new Label("Personal baseline comparison"), grid); baseCard.getStyleClass().add("card");
        getChildren().addAll(q, bars, factorCard, baseCard);

        Runnable refresh = () -> {
            Prediction p = ctx.latestPrediction.get(); FeatureWindow w = ctx.latestWindow.get();
            q.setText(p.stressLabel + " (model: " + p.modelName + ", data: " + p.dataMode + "). Personal baseline is descriptive only; it is not a model input.");
            XYChart.Series<String, Number> s = new XYChart.Series<>();
            factorList.getChildren().clear();
            for (Prediction.Factor f : p.topFactors) { s.getData().add(new XYChart.Data<>(f.feature, f.contribution)); Label l = new Label("• " + f.humanReadable + " (" + f.direction + " model score)"); l.setWrapText(true); factorList.getChildren().add(l); }
            bars.getData().setAll(s);
            grid.getChildren().clear();
            String[] hdr = {"Feature", "Baseline mean", "Current", "Deviation"};
            for (int i = 0; i < hdr.length; i++) { Label h = new Label(hdr[i]); h.getStyleClass().add("card-title"); grid.add(h, i, 0); }
            BaselineProfile bp = ctx.baseline.profile(); Map<String, Double> cur = w.toFeatureMap(); Map<String, Double> dev = ctx.latestDeviations.get();
            int row = 1;
            if (!bp.isComplete()) grid.add(new Label("Baseline not yet calibrated — " + ctx.calibrationStatus.get()), 0, row, 4, 1);
            for (Map.Entry<String, BaselineProfile.Stat> e : bp.stats.entrySet()) {
                double d = dev.getOrDefault("dev_" + e.getKey(), 0.0);
                grid.add(new Label(e.getKey()), 0, row); grid.add(new Label(String.format("%.2f", e.getValue().mean)), 1, row);
                grid.add(new Label(String.format("%.2f", cur.getOrDefault(e.getKey(), 0.0))), 2, row);
                Label dl = new Label(String.format("%+.0f%%", d * 100)); dl.getStyleClass().add(Math.abs(d) > 0.25 ? "status-high" : "status-low"); grid.add(dl, 3, row++);
            }
        };
        ctx.latestPrediction.addListener((o, a, b) -> refresh.run()); refresh.run();
    }
}
