package org.example;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.logging.Logger;

public final class WorkspaceGuard {

    private WorkspaceGuard() {}

    /** 你的 workspace marker */
    private static final String MARKER = ".kitconfig";

    /**
     * Fail-fast：找不到 marker 就印訊息並退出。
     * 找到就把 program.workFolder 設為 workspace root。
     */
    public static void ensureWorkspaceOrExit(ProgramConfig program, Logger logger) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(logger, "logger");

        Path start = startDir(program);

        WorkspaceScanResult r = scan(start);
        if (!r.ok) {
            printInvalidWorkspace(logger, start, r);
            // 退出碼你可自訂：2 = usage/workspace error 常見
            System.exit(2);
            return;
        }

        // 更新 workFolder -> workspace root
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

        WorkspaceScanResult r = scan(start);
        if (!r.ok) {
            printInvalidWorkspace(logger, start, r);
            System.exit(2);
            return;
        }
        program.setWorkFolder(r.root.toFile());
    }

    // ---------------- internal ----------------

    private static Path startDir(ProgramConfig program) {
        // ✅ 推薦：你未來加 launchFolder，就改成：
        // File launch = program.getLaunchFolder();
        // if (launch != null) return launch.toPath().toAbsolutePath().normalize();

        File wf = program.getWorkFolder();
        if (wf != null) {
            return wf.toPath().toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    private static WorkspaceScanResult scan(Path start) {
        Path cur = start.toAbsolutePath().normalize();

        while (cur != null) {
            Path marker = cur.resolve(".kitconfig");

            // marker 是資料夾，找到就把 cur 當 workspace root
            if (Files.isDirectory(marker)) {
                return WorkspaceScanResult.ok(cur, marker);
            }
            Application.getInstance().getLogger().warning("finding rootWorkDir.....");
            cur = cur.getParent();
        }

        return WorkspaceScanResult.missing(start.resolve(".kitconfig"));
    }


    private static void printInvalidWorkspace(Logger logger, Path start, WorkspaceScanResult r) {
        logger.warning("[workspace] invalid workspace");
        logger.warning("[workspace] startDir : " + start);

        if (r.expectedMarker != null) {
            logger.warning("[workspace] missing  : " + r.expectedMarker.getFileName() + " (searched upward)");
        }

        // 給使用者可操作的修復指引
        logger.warning("[workspace] fix:");
        logger.warning("  - open this tool from the workspace root (the folder that contains " + MARKER + ")");
        logger.warning("  - or create an empty " + MARKER + " file in your workspace root");
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



