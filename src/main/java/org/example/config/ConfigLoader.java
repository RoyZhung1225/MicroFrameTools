package org.example.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class ConfigLoader {

    private final Path workDir;
    private final Path programDir;

    private static final String CONFIG_DIR = ".kitconfig";

    public ConfigLoader(Path workDir, Path programDir) {
        this.workDir = normalizeDir(workDir);
        this.programDir = normalizeDir(programDir);
    }

    // -------- public API --------

    public String getCustomFile(String filename) { return loadString(filename); }
    public String getConfig() { return loadString("config.yml"); }
    public String getIgnoreFolder() { return loadString("ignore-list.txt"); }
    public String getIgnoreFile() { return loadString("ignore-file.txt"); }
    public String getPackageFormatFile() { return loadString("package-info.h"); }

    // -------- internals --------

    private String loadString(String fileName) {
        Path p = resolveConfigFile(fileName);
        if (p == null) return "";

        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private Path resolveConfigFile(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        if (programDir == null || workDir == null) return null;

        Path workCfgDir = workDir.resolve(CONFIG_DIR).toAbsolutePath().normalize();
        Path progCfgDir = programDir.resolve(CONFIG_DIR).toAbsolutePath().normalize();

        Path fromWork = workCfgDir.resolve(fileName).normalize();
        Path fromProgram = progCfgDir.resolve(fileName).normalize();

        // ✅ 防止 fileName 夾帶 ../ 跳出 .kitconfig
        if (!fromWork.startsWith(workCfgDir)) return null;
        if (!fromProgram.startsWith(progCfgDir)) return null;

        // 1) work 有就用 work
        if (Files.isRegularFile(fromWork)) return fromWork;

        // 2) work 沒有，program 有 -> copy-up 到 work，之後一律以 work 為主
        if (Files.isRegularFile(fromProgram)) {
            try {
                Files.createDirectories(workCfgDir);
                // 只在 work 不存在時才 copy，避免覆蓋使用者 workspace 的檔
                if (!Files.exists(fromWork)) {
                    Files.copy(fromProgram, fromWork);
                }
                return fromWork;
            } catch (IOException e) {
                // copy 失敗：保底直接用 program 那份（至少能跑）
                return fromProgram;
            }
        }

        // 3) 兩邊都沒有
        return null;
    }

    private static Path normalizeDir(Path p) {
        if (p == null) return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return p.toAbsolutePath().normalize();
    }
}
