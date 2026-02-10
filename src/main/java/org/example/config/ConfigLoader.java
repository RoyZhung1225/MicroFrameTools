package org.example;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class ConfigLoader {

    private final Path workDir;
    private final Path programDir;

    // 你原本寫死 ".kitconfig\\xxx"；這裡改成跨平台
    private static final String CONFIG_DIR = ".kitconfig";

    public ConfigLoader(Path workDir, Path programDir) {
        this.workDir = normalizeDir(workDir);
        this.programDir = normalizeDir(programDir);
    }

    // -------- public API (keep same semantics) --------

    public String getCustomFile(String filename) {
        return loadString(filename);
    }

    public String getConfig() {
        return loadString("config.yml");
    }

    public String getIgnoreFolder() {
        return loadString("ignore-list.txt");
    }

    public String getIgnoreFile() {
        return loadString("ignore-file.txt");
    }

    public String getPackageFormatFile() {
        return loadString("package-info.h");
    }

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

        // work/.kitconfig/<file>
        Path fromWork = workDir.resolve(CONFIG_DIR).resolve(fileName).normalize();
        if (Files.isRegularFile(fromWork)) return fromWork;

        // program/.kitconfig/<file>
        Path fromProgram = programDir.resolve(CONFIG_DIR).resolve(fileName).normalize();
        if (Files.isRegularFile(fromProgram)) return fromProgram;

        return null;
    }

    private static Path normalizeDir(Path p) {
        if (p == null) return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return p.toAbsolutePath().normalize();
    }
}
