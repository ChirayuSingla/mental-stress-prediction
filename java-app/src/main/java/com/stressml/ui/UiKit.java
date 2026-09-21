package com.stressml.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Small helpers to keep the pages consistent. */
final class UiKit {
    private UiKit() {}

    static VBox card(String title, Label value, String subtitle) {
        Label t = new Label(title); t.getStyleClass().add("card-title");
        value.getStyleClass().add("card-value");
        Label s = new Label(subtitle); s.getStyleClass().add("card-sub");
        VBox box = new VBox(t, value, s); box.getStyleClass().add("card"); box.setMinWidth(200);
        return box;
    }

    static Label title(String text) { Label l = new Label(text); l.getStyleClass().add("page-title"); return l; }

    static String stressStyle(int cls) { return cls == 0 ? "status-low" : cls == 1 ? "status-moderate" : "status-high"; }

    static String fmtDuration(java.time.Duration d) { return String.format("%dh %02dm", d.toHours(), d.toMinutesPart()); }
}
