package com.stressml;
import javafx.application.Platform;
import java.util.concurrent.*;
public final class FxTestSupport {
    private static boolean started;
    public static synchronized void start() throws Exception {
        if (!started) {
            CompletableFuture<Void> ready = new CompletableFuture<>();
            Platform.startup(() -> { Platform.setImplicitExit(false); ready.complete(null); });
            ready.get(10, TimeUnit.SECONDS); started = true;
        }
    }
    public static void run(Runnable task) throws Exception {
        start();
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try { task.run(); done.complete(null); }
            catch (Throwable e) { done.completeExceptionally(e); }
        });
        done.get(5, TimeUnit.SECONDS);
    }
}
