package org.example;
import org.example.util.buffer.StringBuff;
import org.example.util.terminal.CommandHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class CommandGuardPlan implements CommandHandler {

    @Override
    public boolean onCommand(StringBuff args, Logger logger) {
        Args a = Args.parse(args);

        // ✅ 先 reload，讓 config.guard / config.path 讀到最新
        try {
            Application.getInstance().getGlobal().reload();
        } catch (Throwable t) {
            logger.warning("config reload failed: " + t.getMessage());
            return true;
        }

        String newPrefix = readGuardPrefixFromConfig();
        if (newPrefix == null || newPrefix.isBlank()) {
            logger.warning("config.guard is empty; cannot run guard.");
            return true;
        }

        if (a.target == Target.NONE) {
            printUsage(logger);
            return true;
        }

        if (!a.refreshPrefix && !a.regenUuid) {
            logger.warning("missing action: use --refresh-prefix and/or --regen-uuid");
            printUsage(logger);
            return true;
        }

        // ✅ confirm gate：沒有 --yes 就不允許真的寫檔
        if (a.apply && !a.yes) {
            logger.warning("refusing to modify files without --yes (safety). Showing plan only.");
            a.apply = false;
        }

        GuardAction action = new GuardAction(newPrefix, a.refreshPrefix, a.regenUuid);

        Result result;
        try {
            if (a.target == Target.FILE) {
                Path file = resolveFile(a.file, logger);
                if (file == null) return true;
                result = GuardRunner.runSingle(file, action, a.apply, logger);
            } else {
                Path root = resolveRoot(a, logger);
                if (root == null) return true;
                result = GuardRunner.runTree(root, action, a.apply, logger);
            }
        } catch (IOException e) {
            logger.warning("guard failed: " + e.getMessage());
            return true;
        }

        logger.info("------------------------------------------------------------");
        logger.info("[guard] newPrefix(config.guard): " + newPrefix);
        logger.info("[guard] actions: " + actionsString(a.refreshPrefix, a.regenUuid));
        logger.info("[guard] mode: " + (a.apply ? "apply" : "plan"));
        logger.info("[guard] scanned: " + result.scanned);
        logger.info("[guard] planned: " + result.planned);
        logger.info("[guard] applied: " + result.applied);
        logger.info("[guard] failed: " + result.failed);
        logger.info("[guard] skipped(package-info.h): " + result.skippedPackageInfo);
        logger.info("[guard] skipped(no-guard): " + result.skippedNoGuard);
        logger.info("[guard] skipped(no-uuid-suffix): " + result.skippedNoUuidSuffix);
        logger.info("[guard] skipped(invalid): " + result.skippedInvalid);
        logger.info("------------------------------------------------------------");
        return true;
    }

    @Override
    public String getDescription() {
        return "Plan/apply include-guard updates for .h files (prefix from config.guard; refresh prefix and/or regenerate UUID).";
    }

    // ---------------- args ----------------

    private enum Target { NONE, FILE, ALL, DIR }

    private static final class Args {
        Target target = Target.NONE;
        String file;           // --file
        String dir;            // --dir
        boolean refreshPrefix; // --refresh-prefix
        boolean regenUuid;     // --regen-uuid
        boolean apply;         // --apply
        boolean yes;           // --yes

        static Args parse(StringBuff sb) {
            Args a = new Args();
            List<String> tokens = new ArrayList<>();
            while (sb.remaining() > 0) {
                String t = sb.get();
                if (t != null && !t.isBlank()) tokens.add(t);
            }

            for (int i = 0; i < tokens.size(); i++) {
                String t = tokens.get(i);

                if ("--file".equalsIgnoreCase(t) && i + 1 < tokens.size()) {
                    a.target = Target.FILE;
                    a.file = tokens.get(++i);
                    continue;
                }

                if ("--all".equalsIgnoreCase(t)) {
                    a.target = Target.ALL;
                    continue;
                }

                if ("--dir".equalsIgnoreCase(t) && i + 1 < tokens.size()) {
                    a.target = Target.DIR;
                    a.dir = tokens.get(++i);
                    continue;
                }

                if ("--refresh-prefix".equalsIgnoreCase(t)) {
                    a.refreshPrefix = true;
                    continue;
                }

                if ("--regen-uuid".equalsIgnoreCase(t)) {
                    a.regenUuid = true;
                    continue;
                }

                if ("--apply".equalsIgnoreCase(t)) {
                    a.apply = true;
                    continue;
                }

                if ("--yes".equalsIgnoreCase(t)) {
                    a.yes = true;
                }
            }

            if (a.file != null) a.file = a.file.trim();
            if (a.dir != null) a.dir = a.dir.trim();
            return a;
        }
    }

    private static void printUsage(Logger logger) {
        logger.info("usage:");
        logger.info("  guard --file <path/to/x.h> --refresh-prefix [--regen-uuid] [--apply --yes]");
        logger.info("  guard --file <path/to/x.h> --regen-uuid [--refresh-prefix] [--apply --yes]");
        logger.info("  guard --all --refresh-prefix [--regen-uuid] [--apply --yes]");
        logger.info("  guard --all --regen-uuid [--refresh-prefix] [--apply --yes]");
        logger.info("  guard --dir <dir> --refresh-prefix [--regen-uuid] [--apply --yes]");
        logger.info("notes:");
        logger.info("  - new prefix is read from config.guard");
        logger.info("  - default is plan; use --apply --yes to modify files");
        logger.info("  - excludes package-info.h");
        logger.info("  - skip entries are not printed; only planned/applied files are printed");
    }

    private static String actionsString(boolean refresh, boolean regen) {
        if (refresh && regen) return "refresh-prefix + regen-uuid";
        if (regen) return "regen-uuid";
        if (refresh) return "refresh-prefix";
        return "none";
    }

    // ---------------- config / path resolving ----------------

    private static String readGuardPrefixFromConfig() {
        try {
            return Application.getInstance().getGlobal().getConfig().getGuard();
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static Path workFolder() {
        return Application.getInstance().getGlobal().getProgram().getWorkFolder()
                .toPath().toAbsolutePath().normalize();
    }

    private static String configPath() {
        try {
            return Application.getInstance().getGlobal().getConfig().getPath(); // e.g. "src\\"
        } catch (Throwable ignore) {
            return "";
        }
    }

    private static Path resolveRoot(Args a, Logger logger) {
        Path work = workFolder();
        Path base = work.resolve(configPath()).toAbsolutePath().normalize();

        Path root;
        if (a.target == Target.DIR) {
            Path p = Paths.get(a.dir);
            root = p.isAbsolute() ? p : work.resolve(p);
        } else {
            root = base; // --all
        }

        root = root.toAbsolutePath().normalize();
        if (!root.startsWith(work)) {
            logger.warning("invalid dir/root (blocked path traversal): " + root);
            return null;
        }
        if (!Files.isDirectory(root)) {
            logger.warning("root is not a directory: " + root);
            return null;
        }
        return root;
    }

    private static Path resolveFile(String path, Logger logger) {
        if (path == null || path.isBlank()) {
            logger.warning("--file path is empty");
            return null;
        }
        Path work = workFolder();
        Path p = Paths.get(path);
        Path file = p.isAbsolute() ? p : work.resolve(p);
        file = file.toAbsolutePath().normalize();

        if (!file.startsWith(work)) {
            logger.warning("invalid file (blocked path traversal): " + file);
            return null;
        }
        if (!Files.isRegularFile(file)) {
            logger.warning("file not found: " + file);
            return null;
        }
        return file;
    }

    // ---------------- runner / counters ----------------

    private record Result(
            int scanned,
            int planned,
            int applied,
            int failed,
            int skippedPackageInfo,
            int skippedNoGuard,
            int skippedNoUuidSuffix,
            int skippedInvalid
    ) {}

    private static final class GuardRunner {

        static Result runSingle(Path file, GuardAction action, boolean apply, Logger logger) throws IOException {
            Counters c = new Counters();
            runOne(file, file.getParent(), action, apply, logger, c);
            return c.toResult();
        }

        static Result runTree(Path root, GuardAction action, boolean apply, Logger logger) throws IOException {
            Counters c = new Counters();
            try (Stream<Path> s = Files.walk(root)) {
                s.filter(Files::isRegularFile)
                        .filter(GuardRunner::isHeader)
                        .forEach(p -> {
                            try {
                                runOne(p, root, action, apply, logger, c);
                            } catch (IOException e) {
                                c.failed++;
                            }
                        });
            }
            return c.toResult();
        }

        private static void runOne(Path file, Path root, GuardAction action, boolean apply, Logger logger, Counters c) throws IOException {
            c.scanned++;

            if (isPackageInfo(file)) {
                c.skippedPackageInfo++;
                return;
            }

            GuardParse parsed = GuardParser.parse(file);
            if (parsed.kind == GuardParseKind.NO_GUARD) {
                c.skippedNoGuard++;
                return;
            }
            if (parsed.kind != GuardParseKind.OK) {
                c.skippedInvalid++;
                return;
            }

            String oldGuard = parsed.guard;
            String newGuard = action.computeNewGuardOrNull(oldGuard);

            // refresh-prefix 需要抓到 UUID suffix；抓不到就 skip
            if (newGuard == null) {
                c.skippedNoUuidSuffix++;
                return;
            }

            if (Objects.equals(oldGuard, newGuard)) {
                return; // noop
            }

            c.planned++;

            String rel = rel(root, file);
            logger.info("[plan] " + rel);
            logger.info("  #ifndef " + oldGuard + "  ->  #ifndef " + newGuard);
            logger.info("  #define " + oldGuard + "  ->  #define " + newGuard);

            if (!apply) return;

            try {
                boolean changed = applyOne(file, oldGuard, newGuard);
                if (changed) {
                    c.applied++;
                    logger.info("[apply] " + rel);
                } else {
                    // 理論上不會發生（因為 plan 判斷 guard 已存在），但保險
                    c.failed++;
                }
            } catch (Throwable t) {
                c.failed++;
                logger.warning("[apply failed] " + rel + " : " + t.getMessage());
            }
        }

        private static String rel(Path root, Path file) {
            try { return root.relativize(file).toString(); }
            catch (Throwable ignore) { return file.toString(); }
        }

        private static boolean isHeader(Path p) {
            String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
            return n.endsWith(".h");
        }

        private static boolean isPackageInfo(Path p) {
            return p.getFileName().toString().equalsIgnoreCase("package-info.h");
        }

        private static final class Counters {
            int scanned, planned, applied, failed, skippedPackageInfo, skippedNoGuard, skippedNoUuidSuffix, skippedInvalid;
            Result toResult() {
                return new Result(scanned, planned, applied, failed, skippedPackageInfo, skippedNoGuard, skippedNoUuidSuffix, skippedInvalid);
            }
        }
    }

    // ---------------- apply (real modification) ----------------

    private static final Pattern IFNDEF_LINE = Pattern.compile("^(\\s*#ifndef\\s+)([A-Za-z_][A-Za-z0-9_]*)(\\s*)$");
    private static final Pattern DEFINE_LINE = Pattern.compile("^(\\s*#define\\s+)([A-Za-z_][A-Za-z0-9_]*)(\\s*)$");
    private static final Pattern ENDIF_COMMENT = Pattern.compile("^(\\s*#endif\\b.*?)([A-Za-z_][A-Za-z0-9_]*)(.*)$");

    private static boolean applyOne(Path file, String oldGuard, String newGuard) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);

        // preserve original line ending style
        String eol = text.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = splitLines(text);

        int limit = Math.min(lines.size(), 300);

        int ifndefIdx = -1;
        int defineIdx = -1;
        int endifIdx = -1;

        // find #ifndef + #define pair in header area
        for (int i = 0; i < limit; i++) {
            String line = lines.get(i);

            if (ifndefIdx < 0) {
                Matcher m = IFNDEF_LINE.matcher(line);
                if (m.matches() && oldGuard.equals(m.group(2))) {
                    ifndefIdx = i;
                    continue;
                }
            }

            if (ifndefIdx >= 0 && defineIdx < 0) {
                Matcher m = DEFINE_LINE.matcher(line);
                if (m.matches() && oldGuard.equals(m.group(2))) {
                    defineIdx = i;
                    break;
                }
            }
        }

        if (ifndefIdx < 0 || defineIdx < 0) {
            // guard not found as expected
            return false;
        }

        // replace only these directive lines (preserve spacing)
        lines.set(ifndefIdx, replaceDirectiveMacro(lines.get(ifndefIdx), IFNDEF_LINE, oldGuard, newGuard));
        lines.set(defineIdx, replaceDirectiveMacro(lines.get(defineIdx), DEFINE_LINE, oldGuard, newGuard));

        // optional: update first matching #endif comment if it contains oldGuard
        // (your templates show "#endif /* UFM_... */" - this keeps them consistent)
        for (int i = Math.max(defineIdx, 0); i < Math.min(lines.size(), 1200); i++) {
            String line = lines.get(i);
            if (line.contains("#endif") && line.contains(oldGuard)) {
                Matcher m = ENDIF_COMMENT.matcher(line);
                if (m.matches() && oldGuard.equals(m.group(2))) {
                    endifIdx = i;
                    break;
                }
            }
        }
        if (endifIdx >= 0) {
            lines.set(endifIdx, lines.get(endifIdx).replace(oldGuard, newGuard));
        }

        String out = String.join(eol, lines);
        atomicWriteString(file, out, StandardCharsets.UTF_8);
        return true;
    }

    private static String replaceDirectiveMacro(String line, Pattern p, String oldGuard, String newGuard) {
        Matcher m = p.matcher(line);
        if (!m.matches()) return line;
        if (!oldGuard.equals(m.group(2))) return line;
        return m.group(1) + newGuard + m.group(3);
    }

    private static List<String> splitLines(String text) {
        // split on \r\n or \n, drop last empty created by trailing newline is OK
        String[] arr = text.split("\\r?\\n", -1);
        return new ArrayList<>(Arrays.asList(arr));
    }

    private static void atomicWriteString(Path file, String content, java.nio.charset.Charset cs) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        if (dir == null) throw new IOException("no parent dir for: " + file);

        String base = file.getFileName().toString();
        Path tmp = dir.resolve(base + ".tmp-" + UUID.randomUUID());

        Files.writeString(tmp, content, cs, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ---------------- action (prefix refresh / uuid regen) ----------------

    private static final class GuardAction {
        private final String newPrefix;
        private final boolean refreshPrefix;
        private final boolean regenUuid;

        GuardAction(String newPrefix, boolean refreshPrefix, boolean regenUuid) {
            this.newPrefix = Objects.requireNonNullElse(newPrefix, "");
            this.refreshPrefix = refreshPrefix;
            this.regenUuid = regenUuid;
        }

        String computeNewGuardOrNull(String oldGuard) {
            if (oldGuard == null || oldGuard.isBlank()) return null;

            // both flags allowed: regen wins (still uses newPrefix)
            if (regenUuid) {
                return newPrefix + uuidMacro();
            }

            if (refreshPrefix) {
                String uuid = extractUuidSuffix(oldGuard);
                if (uuid == null) return null;
                return newPrefix + uuid;
            }

            return null;
        }

        private static String uuidMacro() {
            return UUID.randomUUID().toString().toUpperCase(Locale.ROOT).replace("-", "_");
        }
    }

    // ---------------- UUID suffix extraction ----------------

    private static final Pattern UUID_MACRO =
            Pattern.compile("([0-9A-Fa-f]{8}_[0-9A-Fa-f]{4}_[0-9A-Fa-f]{4}_[0-9A-Fa-f]{4}_[0-9A-Fa-f]{12})");

    private static String extractUuidSuffix(String guard) {
        if (guard == null) return null;
        Matcher m = UUID_MACRO.matcher(guard);
        if (!m.find()) return null;
        return m.group(1).toUpperCase(Locale.ROOT);
    }

    // ---------------- parser (existing) ----------------

    private enum GuardParseKind { OK, NO_GUARD, INVALID }

    private record GuardParse(GuardParseKind kind, String guard, String reason) {}

    private static final class GuardParser {
        private static final Pattern IFNDEF = Pattern.compile("^\\s*#ifndef\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*$");
        private static final Pattern DEFINE = Pattern.compile("^\\s*#define\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*$");

        static GuardParse parse(Path file) throws IOException {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

            String ifndef = null;
            String define = null;

            int limit = Math.min(lines.size(), 200);
            for (int i = 0; i < limit; i++) {
                String line = lines.get(i);

                if (ifndef == null) {
                    Matcher m = IFNDEF.matcher(line);
                    if (m.matches()) {
                        ifndef = m.group(1);
                        continue;
                    }
                }

                if (ifndef != null && define == null) {
                    Matcher m = DEFINE.matcher(line);
                    if (m.matches()) {
                        define = m.group(1);
                        break;
                    }
                }
            }

            if (ifndef == null && define == null) {
                return new GuardParse(GuardParseKind.NO_GUARD, null, "no #ifndef/#define found");
            }
            if (ifndef == null || define == null) {
                return new GuardParse(GuardParseKind.INVALID, null, "incomplete guard (missing ifndef/define)");
            }
            if (!ifndef.equals(define)) {
                return new GuardParse(GuardParseKind.INVALID, null, "ifndef != define (" + ifndef + " vs " + define + ")");
            }
            return new GuardParse(GuardParseKind.OK, ifndef, null);
        }
    }
}
