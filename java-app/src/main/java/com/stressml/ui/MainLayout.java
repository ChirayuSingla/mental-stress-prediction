package com.stressml.ui;

import com.stressml.util.AppContext;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Sidebar navigation + content area + persistent status/DEMO banner. */
public class MainLayout extends BorderPane {
    private final Map<String, Supplier<Node>> pages = new LinkedHashMap<>();
    private final VBox sidebar = new VBox();

    public MainLayout(AppContext ctx) {
        pages.put("Dashboard", () -> new DashboardView(ctx));
        pages.put("Behaviour", () -> new BehaviourView(ctx));
        pages.put("History", () -> new HistoryView(ctx));
        pages.put("Explanation", () -> new ExplanationView(ctx));
        pages.put("Reports", () -> new ReportsView(ctx));
        pages.put("Settings", () -> new SettingsView(ctx));

        sidebar.getStyleClass().add("sidebar");
        Label brand = new Label("Stress Monitor"); brand.getStyleClass().add("brand");
        sidebar.getChildren().add(brand);
        pages.forEach((name, supplier) -> {
            Button b = new Button(name);
            b.setOnAction(e -> {
                setPage(supplier.get());
                sidebar.getChildren().forEach(n -> n.getStyleClass().remove("active"));
                b.getStyleClass().add("active");
            });
            sidebar.getChildren().add(b);
        });
        Region spacer = new Region(); VBox.setVgrow(spacer, Priority.ALWAYS);
        Label disclaimer = new Label("Behavioural research tool.\nNot a medical diagnosis."); disclaimer.getStyleClass().add("disclaimer"); disclaimer.setWrapText(true);
        sidebar.getChildren().addAll(spacer, disclaimer);
        setLeft(sidebar);

        HBox top = new HBox(16); top.setPadding(new Insets(8, 16, 8, 16)); top.getStyleClass().add("top-bar");
        if (ctx.isDemoMode()) { Label demo = new Label("DEMO MODE — SYNTHETIC DATA (not real measurements)"); demo.getStyleClass().add("demo-banner"); top.getChildren().add(demo); }
        Label mon = new Label(); mon.textProperty().bind(ctx.monitoringActive.map(a -> a ? "● Monitoring ACTIVE" : "● Monitoring PAUSED"));
        ctx.monitoringActive.addListener((o, a, b) -> { mon.getStyleClass().setAll("label", b ? "monitor-on" : "monitor-off"); });
        mon.getStyleClass().add(ctx.monitoringActive.get() ? "monitor-on" : "monitor-off");
        Label ml = new Label(); ml.textProperty().bind(ctx.predictionStatus); ml.setWrapText(true);
        top.getChildren().addAll(mon, ml);
        setTop(top);
        setPage(pages.get("Dashboard").get());
    }

    private void setPage(Node content) {
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("content-scroll");
        setCenter(scroll);
    }
}
