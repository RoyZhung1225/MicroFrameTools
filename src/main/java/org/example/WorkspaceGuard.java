package org.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

public final class WorkspaceGuard {

    public static void checkWorkspace(Path root, Logger logger) throws IOException {
        try (var stream = Files.walk(root)) {
            stream.filter(p -> p.toString().endsWith(".tmp"))
                    .forEach(p -> logger.warning("found temp file: " + p));
        }
    }
}
