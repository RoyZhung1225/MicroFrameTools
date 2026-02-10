package org.example.workspace;
import org.example.ProgramConfig;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.logging.Logger;

public final class WorkspaceGuard {

    private WorkspaceGuard() {}

    /** workspace marker folder name */
    private static final String MARKER = ".kitconfig";

    /**
     * Fail-fast：找不到 marker 就印訊息並退出。
     * 找到就把 program.workFolder 設為 workspace root。
     */
    public static void ensureWorkspaceOrExit(ProgramConfig program, Logger logger) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(logger, "logger");

        Path start = startDir(program);

        WorkspaceScanResult r = scan(start, logger);
        if (!r.ok) {
            printInvalidWorkspace(logger, start, r);
            System.exit(2);
            return;
        }

        program.setWorkFolder(r.root.toFile());
        logger.info("workDir: " + r.root.toFile());
    }

    /**
     * 若你想 open/reload 明確指定起點，也可以用這個 overload。
     */
    public static void ensureWorkspaceOrExit(Path start, ProgramConfig program, Logger logger) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(logger, "logger");

        WorkspaceScanResult r = scan(start, logger);
        if (!r.ok) {
            printInvalidWorkspace(logger, start, r);
            System.exit(2);
            return;
        }
        program.setWorkFolder(r.root.toFile());
    }

    // ---------------- internal ----------------

    private static Path startDir(ProgramConfig program) {
        File wf = program.getWorkFolder();
        if (wf != null) {
            return wf.toPath().toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    private static WorkspaceScanResult scan(Path start, Logger logger) {
        Path cur = start.toAbsolutePath().normalize();

        while (cur != null) {
            Path marker = cur.resolve(MARKER);

            if (Files.isDirectory(marker)) {
                return WorkspaceScanResult.ok(cur, marker);
            }

            // ✅ 只用傳入的 logger，不碰 Application
            // 這種 log 建議用 fine，避免刷屏；你也可以直接移除
            logger.fine("[workspace] searching marker at: " + marker);

            cur = cur.getParent();
        }

        return WorkspaceScanResult.missing(start.resolve(MARKER));
    }

    private static void printInvalidWorkspace(Logger logger, Path start, WorkspaceScanResult r) {
        logger.warning("[workspace] invalid workspace");
        logger.warning("[workspace] startDir : " + start);

        if (r.expectedMarker != null) {
            logger.warning("[workspace] missing  : " + r.expectedMarker.getFileName() + " (searched upward)");
        }

        logger.warning("[workspace] fix:");
        logger.warning("  - run this tool from the workspace root (the folder that contains " + MARKER + ")");
        logger.warning("  - or create a folder named " + MARKER + " in your workspace root");
    }

    // ---------------- result model ----------------

    private static final class WorkspaceScanResult {
        final boolean ok;
        final Path root;           // workspace root
        final Path marker;         // found marker path
        final Path expectedMarker; // for error message

        private WorkspaceScanResult(boolean ok, Path root, Path marker, Path expectedMarker) {
            this.ok = ok;
            this.root = root;
            this.marker = marker;
            this.expectedMarker = expectedMarker;
        }

        static WorkspaceScanResult ok(Path root, Path marker) {
            return new WorkspaceScanResult(true, root, marker, null);
        }

        static WorkspaceScanResult missing(Path expectedMarker) {
            return new WorkspaceScanResult(false, null, null, expectedMarker);
        }
    }

    public static void checkWorkspace(Path root, Logger logger) throws IOException {
        try (var stream = Files.walk(root)) {
            stream.filter(p -> p.toString().endsWith(".tmp"))
                    .forEach(p -> logger.warning("found temp file: " + p));
        }
    }
}
