package org.example.app;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public final class AppLifecycle {
    private static final AtomicBoolean STOPPING = new AtomicBoolean(false);

    public static boolean isStopping() {
        return STOPPING.get();
    }

    public static void install(Logger logger, Runnable cleanup) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            STOPPING.set(true);
            try {
                cleanup.run();
            } catch (Throwable t) {
                logger.warning("shutdown cleanup failed: " + t.getMessage());
            }
        }, "shutdown-hook"));
    }
}
