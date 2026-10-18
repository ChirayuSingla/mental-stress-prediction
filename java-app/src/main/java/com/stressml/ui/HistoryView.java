package com.stressml.ui;

import com.stressml.model.Session;
import com.stressml.util.AppContext;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.beans.property.SimpleStringProperty;

import java.sql.SQLException;
import java.util.List;

/** History page: daily trend, session trend, session table. */
public class HistoryView extends VBox {
    public HistoryView(AppContext ctx) {
        getStyleClass().add("page");
        getChildren().add(UiKit.title("History"));
        try {
            List<double[]> daily = ctx.predictions.dailyAverages(ctx.userId(), 30);
            CategoryAxis dx = new CategoryAxis(); NumberAxis dy = new NumberAxis(0, 10, 2);
            BarChart<String, Number> dailyChart = new BarChart<>(dx, dy); dailyChart.setTitle("Daily SWELL score (0-10; legacy predictions excluded)"); dailyChart.setLegendVisible(false); dailyChart.setPrefHeight(240);
            XYChart.Series<String, Number> ds = new XYChart.Series<>();
            for (double[] d : daily) ds.getData().add(new XYChart.Data<>(java.time.LocalDate.ofEpochDay((long) d[2]).toString(), d[1]));
            dailyChart.getData().add(ds);

            List<double[]> sessionsScores = ctx.predictions.sessionScores(ctx.userId(), 30);
            NumberAxis sx = new NumberAxis(), sy = new NumberAxis(0, 10, 2); sx.setLabel("Session (oldest → newest)");
            LineChart<Number, Number> sessionChart = new LineChart<>(sx, sy); sessionChart.setTitle("Session trend: stress vs fatigue"); sessionChart.setAnimated(false); sessionChart.setPrefHeight(240);
            XYChart.Series<Number, Number> st = new XYChart.Series<>(), ft = new XYChart.Series<>(); st.setName("questionnaire estimate (0-10)"); ft.setName("fatigue heuristic / 10");
            int i = 0; for (double[] s : sessionsScores) { st.getData().add(new XYChart.Data<>(i, s[1])); ft.getData().add(new XYChart.Data<>(i++, s[2] / 10.0)); }
            sessionChart.getData().addAll(st, ft);

            TableView<Session> table = new TableView<>(); table.setPrefHeight(200);
            TableColumn<Session, String> c1 = new TableColumn<>("Started"); c1.setCellValueFactory(s -> new SimpleStringProperty(String.valueOf(s.getValue().startedAt)));
            TableColumn<Session, String> c2 = new TableColumn<>("Ended"); c2.setCellValueFactory(s -> new SimpleStringProperty(String.valueOf(s.getValue().endedAt)));
            TableColumn<Session, String> c3 = new TableColumn<>("Type"); c3.setCellValueFactory(s -> new SimpleStringProperty((s.getValue().demo ? "DEMO " : "") + (s.getValue().calibration ? "calibration" : "monitoring")));
            TableColumn<Session, String> c4 = new TableColumn<>("Breaks"); c4.setCellValueFactory(s -> new SimpleStringProperty(String.valueOf(s.getValue().breakCount)));
            TableColumn<Session, String> c5 = new TableColumn<>("Self-report"); c5.setCellValueFactory(s -> new SimpleStringProperty(s.getValue().selfReportStress == null ? "—" : String.valueOf(s.getValue().selfReportStress)));
            table.getColumns().addAll(c1, c2, c3, c4, c5); table.getItems().addAll(ctx.sessions.recent(ctx.userId(), 50));
            getChildren().addAll(dailyChart, sessionChart, table);
        } catch (SQLException e) {
            getChildren().add(new Label("Could not load history: " + e.getMessage()));
        }
    }
}
