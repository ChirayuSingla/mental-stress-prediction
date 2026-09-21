package com.stressml.ui;

import com.stressml.model.AppCategory;
import com.stressml.model.FeatureWindow;
import com.stressml.util.AppContext;
import javafx.collections.ListChangeListener;
import javafx.scene.chart.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/** Behaviour page: typing speed, error rate, mouse activity, application-usage distribution. */
public class BehaviourView extends VBox {
    public BehaviourView(AppContext ctx) {
        getStyleClass().add("page");
        getChildren().add(UiKit.title("Behaviour"));
        GridPane grid = new GridPane(); grid.setHgap(14); grid.setVgap(14);
        grid.add(line(ctx, "Typing speed (WPM)", w -> w.typingSpeedWpm), 0, 0);
        grid.add(line(ctx, "Error / correction rate", w -> w.errorRate), 1, 0);
        grid.add(line(ctx, "Mouse speed (px/s)", w -> w.mouseSpeedPxS), 0, 1);
        grid.add(line(ctx, "App switches per minute", w -> w.appSwitchRate), 1, 1);
        getChildren().add(grid);

        PieChart pie = new PieChart(); pie.setTitle("Application usage by category (this session)"); pie.setPrefHeight(260);
        Runnable refreshPie = () -> {
            Map<AppCategory, Integer> counts = new EnumMap<>(AppCategory.class);
            for (FeatureWindow w : ctx.sessionWindows) counts.merge(w.activeAppCategory, 1, Integer::sum);
            pie.getData().clear(); counts.forEach((c, n) -> pie.getData().add(new PieChart.Data(c.name(), n)));
        };
        ctx.sessionWindows.addListener((ListChangeListener<FeatureWindow>) c -> refreshPie.run()); refreshPie.run();
        getChildren().add(pie);
    }

    private LineChart<Number, Number> line(AppContext ctx, String title, ToDoubleFunction<FeatureWindow> f) {
        NumberAxis x = new NumberAxis(), y = new NumberAxis(); x.setLabel("Window");
        LineChart<Number, Number> chart = new LineChart<>(x, y); chart.setTitle(title); chart.setLegendVisible(false); chart.setAnimated(false); chart.setCreateSymbols(false); chart.setPrefSize(440, 220);
        XYChart.Series<Number, Number> s = new XYChart.Series<>(); chart.getData().add(s);
        Runnable refresh = () -> { s.getData().clear(); int i = 0; for (FeatureWindow w : ctx.sessionWindows) s.getData().add(new XYChart.Data<>(i++, f.applyAsDouble(w))); };
        ctx.sessionWindows.addListener((ListChangeListener<FeatureWindow>) c -> refresh.run()); refresh.run();
        return chart;
    }
}
