package com.stressml.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

/** Explicit consent screen shown before any real monitoring starts. */
public final class ConsentDialog {
    private ConsentDialog() {}

    public static boolean requestConsent(Stage owner) {
        return createDialog(owner).showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    static Alert createDialog(Stage owner) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        if (owner != null && owner.getScene() != null) a.initOwner(owner);
        a.setTitle("Consent to behavioural monitoring");
        a.setHeaderText("This research tool records behavioural METADATA only");
        a.setContentText("""
                Collected: typing timing/counts, mouse movement geometry, application CATEGORY, work/idle durations.
                NOT collected: typed text, passwords, messages, window titles, screenshots, clipboard.
                Data is stored locally in your database and can be deleted at any time from Settings.
                Monitoring can be paused at any time and its state is always visible on screen.
                This is a behavioural research prediction tool, not a medical diagnostic system.

                Do you consent to start monitoring?""");
        a.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
        return a;
    }
}
