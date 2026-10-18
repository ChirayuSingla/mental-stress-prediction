package com.stressml.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class ConsentDialogTest {
    @Test
    @EnabledIfSystemProperty(named="javafx.ui.tests", matches="true")
    void ownerSceneIsSafeAndDeclineClosesDialog() throws Exception {
        CompletableFuture<Void> done = new CompletableFuture<>();
        com.stressml.FxTestSupport.run(() -> {
            try {
                Stage owner = new Stage(); owner.setScene(new Scene(new StackPane()));
                var dialog = ConsentDialog.createDialog(owner);
                assertEquals(owner, dialog.getOwner());
                Platform.runLater(() -> { dialog.setResult(javafx.scene.control.ButtonType.NO); dialog.close(); });
                assertEquals(javafx.scene.control.ButtonType.NO, dialog.showAndWait().orElseThrow());
                owner.close();
                done.complete(null);
            } catch (Throwable t) { done.completeExceptionally(t); }
        });
        done.get(15, TimeUnit.SECONDS);
    }
}
