package org.example.util.terminal;

import org.example.Application;
import org.example.util.buffer.StringBuff;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

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

        // target must be exactly one of: --file / --all / --dir / --dir-name
        if (a.targetCount != 1) {
            logger.warning("invalid target: choose exactly one of --file / --all / --dir / --dir-name");
            printUsage(logger);
            return true;
        }


        // reload config so config.guard is fresh
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

        GuardAction action = new GuardAction(newPrefix, a.refreshPrefix, a.regenUuid);

        // 先跑一次（plan 或 apply 都會先算 planned）
        Result result;
        try {
            if (a.target == Target.FILE) {
                String rerunFlags = buildRerunFlags(a);
                Path file = resolveFile(a.file, a.pick, rerunFlags, logger);
                if (file == null) return true;
                result = GuardRunner.runSingle(file, action, false, logger); // plan pass
            } else {
                Path root = resolveRoot(a, logger);
                if (root == null) return true;
                result = GuardRunner.runTree(root, action, false, logger);   // plan pass
            }
        } catch (IOException e) {
            logger.warning("guard plan failed: " + e.getMessage());
            return true;
        }

        // summary for plan pass
        logger.info("------------------------------------------------------------");
        logger.info("[guard] newPrefix(config.guard): " + newPrefix);
        logger.info("[guard] actions: " + actionsString(a.refreshPrefix, a.regenUuid));
        logger.info("[guard] mode: " + (a.apply ? "apply" : "plan"));
        logger.info("[guard] scanned: " + result.scanned);
        logger.info("[guard] planned: " + result.planned);
        logger.info("[guard] skipped(package-info.h): " + result.skippedPackageInfo);
        logger.info("[guard] skipped(no-guard): " + result.skippedNoGuard);
        logger.info("[guard] skipped(no-uuid-suffix): " + result.skippedNoUuidSuffix);
        logger.info("[guard] skipped(invalid): " + result.skippedInvalid);
        logger.info("------------------------------------------------------------");

        // 如果只是 plan，到這裡就結束
        if (!a.apply) return true;

        // 沒有 planned 也沒必要問
        if (result.planned <= 0) {
            logger.info("[guard] nothing to apply.");
            return true;
        }

        // ✅ confirmation gate：除非 --force，否則要人工確認
        if (!a.force) {
            boolean ok = confirmApply(result.planned, logger);
            if (!ok) {
                logger.info("[guard] cancelled.");
                return true;
            }
        }

        // ✅ 第二趟真正 apply
        Result applied;
        try {
            if (a.target == Target.FILE) {
                String rerunFlags = buildRerunFlags(a);
                Path file = resolveFile(a.file, a.pick, rerunFlags, logger);
                if (file == null) return true;
                applied = GuardRunner.runSingle(file, action, true, logger);
            } else {
                Path root = resolveRoot(a, logger);
                if (root == null) return true;
                applied = GuardRunner.runTree(root, action, true, logger);
            }
        } catch (IOException e) {
            logger.warning("guard apply failed: " + e.getMessage());
            return true;
        }

        logger.info("------------------------------------------------------------");
        logger.info("[guard] applied: " + applied.applied);
        logger.info("[guard] failed: " + applied.failed);
        logger.info("------------------------------------------------------------");

        return true;
    }

    @Override
    public String getDescription() {
        return "Plan/apply include-guard updates for .h files (prefix from config.guard; refresh prefix and/or regenerate UUID).";
    }

    // ---------------- args ----------------

    private enum Target { NONE, FILE, ALL, DIR, DIR_NAME }

    private static final class Args {
        Target target = Target.NONE;
        String file;           // --file
        String dir;            // --dir
        String dirName;        // --dir-name
        Integer pick;          // --pick (1-based)

        boolean refreshPrefix; // --refresh-prefix
        boolean regenUuid;     // --regen-uuid
        boolean apply;         // --apply
        boolean force;         // --force (skip confirmation)

        // 用來檢查互斥 target：--file/--all/--dir/--dir-name 只能選一個
        int targetCount = 0;

        static Args parse(StringBuff sb) {
            Args a = new Args();
            List<String> tokens = new ArrayList<>();
            while (sb.remaining() > 0) {
                String t = sb.get();
                if (t != null && !t.isBlank()) tokens.add(t);
            }

            for (int i = 0; i < tokens.size(); i++) {
                String t = tokens.get(i);

                // -------- targets (mutually exclusive) --------

                if ("--file".equalsIgnoreCase(t) && i + 1 < tokens.size()) {
                    a.target = Target.FILE;
                    a.file = tokens.get(++i);
                    a.targetCount++;
                    continue;
                }

                if ("--all".equalsIgnoreCase(t)) {
                    a.target = Target.ALL;
                    a.targetCount++;
                    continue;
                }

                if ("--dir".equalsIgnoreCase(t) && i + 1 < tokens.size()) {
                    a.target = Target.DIR;
                    a.dir = tokens.get(++i);
                    a.targetCount++;
                    continue;
                }

                if ("--dir-name".equalsIgnoreCase(t) && i + 1 < tokens.size()) {
                    a.target = Target.DIR_NAME;
                    a.dirName = tokens.get(++i);
                    a.targetCount++;
                    continue;
                }

                if ("--pick".equalsIgnoreCase(t) && i + 1 < tokens.size()) {
                    String v = tokens.get(++i);
                    try {
                        a.pick = Integer.parseInt(v);
                    } catch (Throwable ignore) {
                        a.pick = null;
                    }
                    continue;
                }

                // -------- actions --------

                if ("--refresh-prefix".equalsIgnoreCase(t)) {
                    a.refreshPrefix = true;
                    continue;
                }

                if ("--regen-uuid".equalsIgnoreCase(t)) {
                    a.regenUuid = true;
                    continue;
                }

                // -------- mode --------

                if ("--apply".equalsIgnoreCase(t)) {
                    a.apply = true;
                    continue;
                }

                if ("--force".equalsIgnoreCase(t)) {
                    a.force = true;
                }
            }

            if (a.file != null) a.file = a.file.trim();
            if (a.dir != null) a.dir = a.dir.trim();
            if (a.dirName != null) a.dirName = a.dirName.trim();

            return a;
        }
    }


    private static void printUsage(Logger logger) {
        logger.info("usage:");
        logger.info("  guard --file <filename.h> --refresh-prefix [--regen-uuid] [--apply] [--force]");
        logger.info("  guard --file <filename.h> --regen-uuid [--refresh-prefix] [--apply] [--force]");
        logger.info("  guard --all --refresh-prefix [--regen-uuid] [--apply] [--force]");
        logger.info("  guard --all --regen-uuid [--refresh-prefix] [--apply] [--force]");
        logger.info("  guard --dir <dirname> --refresh-prefix [--regen-uuid] [--apply] [--force]");
        logger.info("notes:");
        logger.info("  - new prefix is read from config.guard");
        logger.info("  - default is plan; add --apply to modify files");
        logger.info("  - without --force, --apply will ask for confirmation (y/yes)");
        logger.info("  - excludes package-info.h");
        logger.info("  - skip entries are not printed; only planned/applied files are printed");
    }

    private static String buildRerunFlags(Args a) {
        StringBuilder sb = new StringBuilder();
        if (a.refreshPrefix) sb.append(" --refresh-prefix");
        if (a.regenUuid) sb.append(" --regen-uuid");
        if (a.apply) sb.append(" --apply");
        if (a.force) sb.append(" --force");
        return sb.toString().trim();
    }


    private static String actionsString(boolean refresh, boolean regen) {
        if (refresh && regen) return "refresh-prefix + regen-uuid";
        if (regen) return "regen-uuid";
        if (refresh) return "refresh-prefix";
        return "none";
    }

    // ---------------- confirmation ----------------

    private static boolean confirmApply(int plannedCount, Logger logger) {
        // Use a temporary terminal/reader for confirmation.
        // This avoids needing to pass your REPL's LineReader around.
        try (Terminal terminal = TerminalBuilder.builder().system(true).jna(true).build()) {
            LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();

            terminal.writer().println();
            terminal.writer().println("[confirm] about to modify " + plannedCount + " file(s).");
            terminal.writer().print("[confirm] continue? (y/N): ");
            terminal.writer().flush();

            String ans;
            try {
                ans = reader.readLine("");
            } catch (UserInterruptException | EndOfFileException e) {
                return false;
            }

            if (ans == null) return false;
            ans = ans.trim().toLowerCase(Locale.ROOT);
            return ans.equals("y") || ans.equals("yes");
        } catch (IOException e) {
            // If confirmation cannot be performed safely, cancel.
            logger.warning("confirmation failed: " + e.getMessage());
            return false;
        }
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
            return Application.getInstance().getGlobal().getConfig().getPath();
        } catch (Throwable ignore) {
            return "";
        }
    }

    private static Path resolveRoot(Args a, Logger logger) {
        Path work = workFolder();
        Path base = work.resolve(configPath()).toAbsolutePath().normalize();

        Path root;

        if (a.target == Target.DIR_NAME) {
            return resolveRootByDirName(base, work, a.dirName, a.pick, logger);
        }

        if (a.target == Target.DIR) {
            if (a.dir == null || a.dir.isBlank()) {
                logger.warning("--dir is empty");
                return null;
            }

            Path p = Paths.get(a.dir);

            if (p.isAbsolute()) {
                root = p;
            } else {
                // 1) 先試 work/<dir>
                Path cand1 = work.resolve(p).toAbsolutePath().normalize();

                // 2) 再試 base/<dir>（base = work + configPath）
                Path cand2 = base.resolve(p).toAbsolutePath().normalize();

                // 優先選第一個存在的 directory
                if (Files.isDirectory(cand1)) {
                    root = cand1;
                } else if (Files.isDirectory(cand2)) {
                    root = cand2;
                } else {
                    // 都不存在：維持你原本錯誤訊息風格，但把兩個候選都印出來
                    logger.warning("root is not a directory: " + cand1);
                    logger.warning("root is not a directory: " + cand2);
                    return null;
                }
            }
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

    private static Path resolveRootByDirName(Path base, Path work, String dirName, Integer pick, Logger logger) {

        if (dirName == null || dirName.isBlank()) {
            logger.warning("--dir-name is empty");
            return null;
        }

        Path rootBase = base.toAbsolutePath().normalize();
        if (!Files.isDirectory(rootBase)) {
            logger.warning("base is not a directory: " + rootBase);
            return null;
        }
        if (!rootBase.startsWith(work)) {
            logger.warning("invalid base (blocked path traversal): " + rootBase);
            return null;
        }

        List<Path> candidates = new ArrayList<>();
        try (Stream<Path> s = Files.walk(rootBase)) {
            s.filter(Files::isDirectory)
                    .filter(p -> {
                        Path fn = p.getFileName();
                        if (fn == null) return false;
                        return fn.toString().equalsIgnoreCase(dirName);
                    })
                    .forEach(candidates::add);
        } catch (IOException e) {
            logger.warning("search dir-name failed under base: " + rootBase + " : " + e.getMessage());
            return null;
        }

        if (candidates.isEmpty()) {
            logger.warning("dir-name not found under base: " + rootBase + " (name: " + dirName + ")");
            return null;
        }

        // stable order (relative path)
        candidates.sort(Comparator.comparing(p -> {
            try { return rootBase.relativize(p).toString().toLowerCase(Locale.ROOT); }
            catch (Throwable ignore) { return p.toString().toLowerCase(Locale.ROOT); }
        }));

        if (candidates.size() == 1) {
            Path only = candidates.get(0).toAbsolutePath().normalize();
            if (!only.startsWith(work)) {
                logger.warning("invalid dir (blocked path traversal): " + only);
                return null;
            }
            return only;
        }

        // multiple matches -> must pick
        logger.warning("multiple directories matched name: " + dirName);
        for (int i = 0; i < Math.min(candidates.size(), 50); i++) {
            Path p = candidates.get(i);
            String rel;
            try { rel = rootBase.relativize(p).toString(); }
            catch (Throwable ignore) { rel = p.toString(); }
            logger.warning("  " + (i + 1) + ") " + rel);
        }
        logger.warning("use --pick <1.." + candidates.size() + "> to select one.");

        if (pick == null) return null;

        int idx = pick; // 1-based
        if (idx < 1 || idx > candidates.size()) {
            logger.warning("invalid --pick: " + idx + " (valid range: 1.." + candidates.size() + ")");
            return null;
        }

        Path chosen = candidates.get(idx - 1).toAbsolutePath().normalize();
        if (!chosen.startsWith(work)) {
            logger.warning("invalid dir (blocked path traversal): " + chosen);
            return null;
        }
        return chosen;
    }



    private static Path resolveFile(String input, Integer pick, String rerunFlags, Logger logger)
    {
        if (input == null || input.isBlank()) {
            logger.warning("--file is empty");
            return null;
        }

        Path work = workFolder();
        Path root = work.resolve(configPath()).toAbsolutePath().normalize();
        logger.warning("[debug] work=" + workFolder());
        logger.warning("[debug] root=" + root);


        // 1) 先當成 path 試著解析（absolute or relative to work）
        Path p = Paths.get(input);
        Path file = p.isAbsolute() ? p : work.resolve(p);
        file = file.toAbsolutePath().normalize();
        boolean isBareName = isBareFileName(input);
        logger.warning("[debug] work=" + work);
        logger.warning("[debug] configPath=" + configPath());
        logger.warning("[debug] root=" + root);
        logger.warning("[debug] input=" + input);
        logger.warning("[debug] resolvedAsPath=" + file + " exists=" + Files.isRegularFile(file));



        // 沙箱：必須在 workDir 內
        if (!file.startsWith(work)) {
            logger.warning("invalid file (blocked path traversal): " + file);
            return null;
        }

        // 若 path 真的存在，直接回傳
        if (!isBareName && Files.isRegularFile(file)) {
            return file;
        }

        // 2) path 不存在：fallback 當作「檔名」在 root(work+config.path) 下搜尋
        String name = Paths.get(input).getFileName().toString(); // 保守：只取最後一段
        if (name.isBlank()) {
            logger.warning("invalid file name: " + input);
            return null;
        }

        if (!Files.isDirectory(root)) {
            logger.warning("search root is not a directory: " + root);
            return null;
        }

        // 只允許 .h（你這個指令只處理 header）
        if (!name.toLowerCase(Locale.ROOT).endsWith(".h")) {
            logger.warning("guard --file expects a .h file name (got: " + name + ")");
            return null;
        }

        // 排除 package-info.h
        if (name.equalsIgnoreCase("package-info.h")) {
            logger.warning("package-info.h is excluded");
            return null;
        }

        List<Path> matches = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root)) {
            logger.warning("[debug] matches=" + matches.size());
            matches.stream().limit(5).forEach(p2 -> logger.warning("[debug] hit=" + p2));

            s.filter(Files::isRegularFile)
                    .filter(GuardRunner::isHeader)          // 你已經有這個 helper
                    .filter(f -> !GuardRunner.isPackageInfo(f))
                    .filter(f -> f.getFileName().toString().equalsIgnoreCase(name))
                    .limit(50) // 避免極端情況
                    .forEach(matches::add);
        } catch (IOException e) {
            logger.warning("search failed under root: " + root + " : " + e.getMessage());
            return null;
        }

        if (matches.isEmpty()) {
            logger.warning("file not found under root: " + root + " (name: " + name + ")");
            return null;
        }

        if (matches.size() == 1) {
            return matches.get(0).toAbsolutePath().normalize();
        }

        // 多個匹配：拒絕，列候選，要求使用者改用更精確的相對路徑或 --dir
        logger.warning("multiple files matched under root: " + root + " (name: " + name + ")");
        matches.stream()
                .map(p2 -> {
                    try { return root.relativize(p2).toString(); }
                    catch (Throwable ignore) { return p2.toString(); }
                })
                .sorted()
                .limit(10)
                .forEach(rel -> logger.warning("  - " + rel));
        logger.warning("please use a more specific relative path (under workDir) or use --dir + --all mode.");
        // 多個匹配：列出候選 + 支援 --pick（1-based）
        logger.warning("multiple files matched under root: " + root + " (name: " + name + ")");

// 先排序，讓 index 穩定
        matches.sort(Comparator.comparing(p2 -> {
            try { return root.relativize(p2).toString().toLowerCase(Locale.ROOT); }
            catch (Throwable ignore) { return p2.toString().toLowerCase(Locale.ROOT); }
        }));

// 列出候選（建議最多 20 個，太多會刷屏）
        int show = Math.min(matches.size(), 20);
        for (int i = 0; i < show; i++) {
            Path p2 = matches.get(i);
            String rel;
            try { rel = root.relativize(p2).toString(); }
            catch (Throwable ignore) { rel = p2.toString(); }
            logger.warning("  " + (i + 1) + ") " + rel);
        }
        if (matches.size() > show) {
            logger.warning("  ... (" + (matches.size() - show) + " more)");
        }

// ✅ 沒 pick：印出可複製貼上指令（你要的 UX）
        if (pick == null) {
            logger.warning("copy/paste one of:");

            // 你要印的 command：guard --file Boolean.h --pick 2 --regen-uuid --apply --force
            // 這裡我們只負責補 pick，其它 flags 在外層已經由使用者輸入
            // 但你要求要完整印出，所以需要把「當次 flags」帶進來；目前 resolveFile 只有 input/pick
            // => 最低改動：先印出「只補 pick 的版本」，外層 flags 使用者自己保留（但你說要完整）
            // => 因此我這裡先提供兩種：A) 最低改動版（不完整） B) 完整版（需多帶參數）

            // A) 最低改動（無法知道使用者是否有 --regen-uuid/--apply/--force）
            for (int i = 0; i < show; i++) {
                logger.warning("  guard --file " + quoteIfNeeded(input) + " --pick " + (i + 1) + " ...");
            }
            logger.warning("hint: rerun with --pick N (pick does not get skipped by --force).");
            return null;
        }

// ✅ 有 pick：選中對應檔案
        int idx = pick; // 1-based
        if (idx < 1 || idx > matches.size()) {
            logger.warning("invalid --pick: " + idx + " (valid range: 1.." + matches.size() + ")");
            return null;
        }
        return matches.get(idx - 1).toAbsolutePath().normalize();

    }

    private static boolean isBareFileName(String input) {
        if (input == null) return false;
        String s = input.trim();
        if (s.isEmpty()) return false;

        // Windows / *nix path markers
        if (s.contains("\\") || s.contains("/") || s.contains(":")) return false;

        try {
            return Paths.get(s).getNameCount() == 1;
        } catch (Throwable t) {
            return false;
        }
    }


    private static String quoteIfNeeded(String s) {
        if (s == null) return "\"\"";
        String t = s.trim();
        if (t.isEmpty()) return "\"\"";
        // 如果含空白或 tab，就包引號
        if (t.indexOf(' ') >= 0 || t.indexOf('\t') >= 0) {
            // 把雙引號 escape
            t = t.replace("\"", "\\\"");
            return "\"" + t + "\"";
        }
        return t;
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

            if (newGuard == null) {
                c.skippedNoUuidSuffix++;
                return;
            }

            if (Objects.equals(oldGuard, newGuard)) {
                return;
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

    // ---------------- apply ----------------

    private static final Pattern IFNDEF_LINE = Pattern.compile("^(\\s*#ifndef\\s+)([A-Za-z_][A-Za-z0-9_]*)(\\s*)$");
    private static final Pattern DEFINE_LINE = Pattern.compile("^(\\s*#define\\s+)([A-Za-z_][A-Za-z0-9_]*)(\\s*)$");
    private static final Pattern ENDIF_COMMENT = Pattern.compile("^(\\s*#endif\\b.*?)([A-Za-z_][A-Za-z0-9_]*)(.*)$");

    private static boolean applyOne(Path file, String oldGuard, String newGuard) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);

        String eol = text.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = splitLines(text);

        int limit = Math.min(lines.size(), 300);

        int ifndefIdx = -1;
        int defineIdx = -1;
        int endifIdx = -1;

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
            return false;
        }

        lines.set(ifndefIdx, replaceDirectiveMacro(lines.get(ifndefIdx), IFNDEF_LINE, oldGuard, newGuard));
        lines.set(defineIdx, replaceDirectiveMacro(lines.get(defineIdx), DEFINE_LINE, oldGuard, newGuard));

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

    // ---------------- action ----------------

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

    // ---------------- parser ----------------

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
