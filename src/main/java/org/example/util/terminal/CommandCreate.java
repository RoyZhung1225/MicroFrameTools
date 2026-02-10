package org.example.util.terminal;

import org.example.app.Application;
import org.example.cli_core.buffer.CommandHandler;
import org.example.cli_core.buffer.StringBuff;
import org.example.completion.CompletableCommand;
import org.example.completion.CompletionRequest;
import org.example.infra_fs.SafeFiles;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.example.cli_core.buffer.CommandHelp.*;

/**
 * CLI command: create
 *
 * 用途：
 * - 依照指定的 template（class/struct/enum/interface）產生對應的檔案
 * - 支援把模板中的變數進行替換：
 *   $GUARD$     -> include guard
 *   $NAMESPACE$ -> namespace / folder
 *   $CLASSNAME$ -> 檔名 / 類名
 *
 * 參數格式（依你目前解析邏輯）：
 * create <type> <name> [namespace]
 *
 * 注意：
 * - 目前 namespace 預設為 config.namespace，但若第三個 token 有值會覆蓋它
 * - 寫檔採「若檔案已存在就失敗」（不覆蓋）
 */
public class CommandCreate implements CommandHandler, CompletableCommand {


    /**
     * key -> handler function
     * handler: (logger, replacementMap) -> true if success
     */
    private final Map<String, BiPredicate<Logger, Map<String, String>>> option;

    /**
     * 產生 include guard。
     * guard = (config.guard + UUID).toUpperCase() 並把 '-' 換成 '_'
     *
     * 風險/特性：
     * - 每次呼叫都會產生不同 guard（因為 UUID）
     * - 如果你希望同名 class 每次生成 guard 一致，應改成 deterministic（見下方建議）
     */

    private boolean dryRun = false;

    private boolean force = false;


    private String getGuard() {
        String result = (Application.getInstance().getGlobal().getConfig().getGuard() + UUID.randomUUID()).toUpperCase();
        return result.replace("-", "_");
    }

    private static final List<String> TYPES = List.of("class", "struct", "enum", "interface");

    private static final List<String> OPTIONS = List.of( "--force", "-f", "--namespace", "--ns");



    @Override
    public void complete(CompletionRequest req, List<String> out) {
        List<String> w = req.getTokens();
        String prefix = req.getPrefix();
        int idx = req.getWordIndex();

        // 外層 completer 已處理 command 名（wordIndex==0）
        if (idx == 0) return;

        if (prefix == null) prefix = "";

        // 1) 先處理「補 option」
        if (prefix.startsWith("-")) {
            addStartsWith(out, OPTIONS, prefix);
            return;
        }

        // 3) 最後用「位置參數數量」來判斷要補什麼（不要用 idx 判斷）
        int pos = countCreatePositionals(w, idx);

        // create [options] <type> <name> [namespace]
        // pos==0：還沒確定 type → 補 TYPES
        if (pos == 0) {
            addStartsWith(out, TYPES, prefix);
            return;
        }

        // pos==1：正在輸入 name → 通常不補（避免噪音）
        // 但仍可以讓使用者補 options（prefix 不以 '-' 開頭時就不補）
        if (pos == 1) {
            return;
        }

        if (pos >= 2) {
            addStartsWith(out, namespaceCandidatesFromWorkspace(null /* or logger */), prefix);
            addStartsWith(out, OPTIONS, prefix);

        }
    }


    private static List<String> namespaceCandidatesFromWorkspace(Logger logger) {
        // 你現有的來源
        var app = Application.getInstance();
        Path work = app.getGlobal().getProgram().getWorkFolder().toPath().toAbsolutePath().normalize();
        String base = app.getGlobal().getConfig().getPath(); // 例如 "src" 或 "include"

        // base 必須是相對路徑：避免掃到 workFolder 外
        Path basePath = Paths.get(base == null ? "" : base);
        if (basePath.isAbsolute()) {
            // 安全起見，直接不掃（或給 warning）
            if (logger != null) logger.warning("config.path must be relative; absolute path is blocked: " + basePath);
            return List.of();
        }

        Path root = work.resolve(basePath).toAbsolutePath().normalize();
        if (!root.startsWith(work)) {
            if (logger != null) logger.warning("scan root is outside workFolder (blocked): " + root);
            return List.of();
        }

        if (!Files.isDirectory(root)) return List.of();

        final int maxDepth = 6;      // 你可以調
        final int maxResults = 200;  // 你可以調

        ArrayList<String> out = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();

        // 可選：把 root 本身也當 namespace ""（通常不用）
        // out.add("");

        try (Stream<Path> st = Files.walk(root, maxDepth)) {
            st.filter(Files::isDirectory)
                    .filter(p -> !p.equals(root)) // 不要把 root 自己當候選
                    .forEach(p -> {
                        if (out.size() >= maxResults) return;

                        Path rel = root.relativize(p);
                        // rel: my/core/utils -> my::core::utils
                        String ns = toNamespace(rel);
                        if (ns.isBlank()) return;

                        // 可選：只保留合法 segment（避免補出 weird folder）
                        if (!isValidNamespace(ns)) return;

                        if (seen.add(ns)) out.add(ns);
                    });
        } catch (IOException e) {
            if (logger != null) logger.warning("scan namespace failed: " + e.getMessage());
            return List.of();
        }

        // 排序：更穩定
        out.sort(String::compareToIgnoreCase);
        return out;
    }

    private static String toNamespace(Path rel) {
        if (rel == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Path seg : rel) {
            String s = seg.toString();
            if (s == null || s.isBlank()) continue;

            // 跳過隱藏/特殊資料夾（你可依需求調整）
            if (s.equals(".") || s.equals("..")) return "";

            if (sb.length() > 0) sb.append("::");
            sb.append(s);
        }
        return sb.toString();
    }

    // namespace segment 基本合法性（可放寬）
    private static boolean isValidNamespace(String ns) {
        // my::core::v2 這種 OK；含空白/奇怪符號就略過
        String[] parts = ns.split("::");
        for (String p : parts) {
            if (p.isEmpty()) return false;
            // 允許英數底線，且不能以數字開頭
            if (!p.matches("[A-Za-z_][A-Za-z0-9_]*")) return false;
        }
        return true;
    }



    private static int countCreatePositionals(List<String> tokens, int wordIndex) {
        // tokens: ["create", ...]
        if (tokens == null || tokens.size() <= 1) return 0;

        int endExclusive = Math.min(wordIndex, tokens.size());

        int count = 0;
        for (int i = 1; i < endExclusive; i++) { // i=1 跳過 command
            String t = tokens.get(i);
            if (t == null || t.isBlank()) continue;

            if (t.startsWith("-")) {
                // 帶值 option：--namespace/--ns <value>
                if ("--namespace".equalsIgnoreCase(t) || "--ns".equalsIgnoreCase(t)) {
                    if (i + 1 < endExclusive) i++; // 跳過 value token
                }
                continue;
            }

            // 非 option → positional
            count++;
        }
        return count;
    }



    private static String prevToken(List<String> words, int wordIndex) {
        // wordIndex 指向正在補的 token，上一個 token 是 wordIndex-1
        int i = wordIndex - 1;
        if (words == null || i < 0 || i >= words.size()) return "";
        return words.get(i);
    }

    private static void addStartsWith(List<String> out, List<String> src, String prefix) {
        String p = prefix == null ? "" : prefix;
        for (String s : src) {
            if (s == null || s.isBlank()) continue;
            if (p.isEmpty() || s.startsWith(p)) out.add(s);
        }
    }

    private static final class ParsedArgs {
        boolean force;
        boolean yes;       // --yes / -y：跳過確認
        String type;       // class/struct/enum/interface
        String name;       // Foo
        String namespace;  // my::core
    }


    private ParsedArgs parseArgs(StringBuff sb) {
        ParsedArgs a = new ParsedArgs();
        String defaultNs = Application.getInstance().getGlobal().getConfig().getNamespace();
        a.namespace = defaultNs;

        ArrayList<String> tokens = new ArrayList<>();
        while (sb.remaining() > 0) {
            String t = sb.get();
            if (t != null && !t.isBlank()) tokens.add(t);
        }

        ArrayList<String> positional = new ArrayList<>();
        boolean nsByOption = false;

        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);

            if ("--force".equalsIgnoreCase(t) || "-f".equalsIgnoreCase(t)) {
                a.force = true;
                continue;
            }

            if ("--yes".equalsIgnoreCase(t) || "-y".equalsIgnoreCase(t)) {
                a.yes = true;
                continue;
            }

            if ("--namespace".equalsIgnoreCase(t) || "--ns".equalsIgnoreCase(t)) {
                if (i + 1 < tokens.size()) {
                    a.namespace = tokens.get(++i);
                    nsByOption = true;
                }
                continue;
            }

            positional.add(t);
        }

        if (positional.size() > 0) a.type = positional.get(0);
        if (positional.size() > 1) a.name = positional.get(1);
        if (positional.size() > 2 && !nsByOption) a.namespace = positional.get(2);

        return a;
    }




    public CommandCreate() {
        this.option = new LinkedHashMap<>();

        // 支援長/短參數別名
        this.option.put("class", this::createClass);
        this.option.put("-c", this::createClass);

        this.option.put("enum", this::createEnum);
        this.option.put("-e", this::createEnum);

        this.option.put("struct", this::createStruct);
        this.option.put("-s", this::createStruct);

        this.option.put("interface", this::createInterface);
        this.option.put("-i", this::createInterface);

    }

    /** 取得程式設定的工作目錄（生成檔案都會落在 workFolder 之下） */
    private File getWorkFolder() {
        return Application.getInstance().getGlobal().getProgram().getWorkFolder();
    }

    /**
     * 把 namespace/path 轉成實際資料夾。
     *
     * 實作方式：
     * - base = config.path
     * - 把 '::' 轉成 Windows 路徑分隔符 '\\'
     * - 再以 workFolder 為根目錄 new File(workFolder, base+convertedPath)
     *
     * 風險：
     * - 寫死 Windows 分隔符，非跨平台
     * - 未處理 '..' 等路徑穿越（客戶可寫到 workFolder 外面）
     */
    private File getFolder(String nsOrPath, Logger logger) {
        String base = Application.getInstance().getGlobal().getConfig().getPath();

        // 把 namespace 轉成相對路徑：my::core -> my/core
        String rel = (nsOrPath == null) ? "" : nsOrPath.replace("::", File.separator);

        // 組合：workFolder / base / rel
        Path work = this.getWorkFolder().toPath().toAbsolutePath().normalize();
        Path target = work.resolve(Paths.get(base, rel)).toAbsolutePath().normalize();

        // 沙箱檢查：target 必須在 work 之下
        if (!target.startsWith(work)) {
            logger.warning("invalid namespace/path (path traversal blocked): " + nsOrPath);
            return null;
        }

        return target.toFile();
    }


    @Override
    public boolean onCommand(StringBuff stringBuff, Logger logger) {
        ParsedArgs a = parseArgs(stringBuff);
        this.force = a.force;

        if (a.type == null || a.type.isBlank()) {
            this.sendUsage(logger);
            return true;
        }
        if (a.name == null || a.name.isBlank()) {
            logger.warning("file name is required.");
            this.sendUsage(logger);
            return true;
        }

        BiPredicate<Logger, Map<String, String>> func = this.option.get(a.type);
        if (func == null) {
            logger.warning("unknown type: " + a.type);
            logger.info("available types: class, struct, enum, interface");
            this.sendUsage(logger);
            return true;
        }

        Map<String, String> map = new HashMap<>();
        map.put("$GUARD$", this.getGuard());
        map.put("$NAMESPACE$", a.namespace);
        map.put("$CLASSNAME$", a.name);

        // ===== Confirm (預設) =====
        if (!a.yes) {
            this.dryRun = true;
            func.test(logger, map);

            if (!askYesNo(logger, "Continue? [y/N] ")) {
                logger.info("aborted.");
                return true;
            }
        }


        // ===== Execute =====
        this.dryRun = false;
        boolean ok = func.test(logger, map);
        if (ok) logger.info("success");
        return true;
    }




    @Override
    public String getDescription() {
        return this.getCreateHelpList();
    }

    private String getCreateHelpList(){
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(BOLD + "USAGE" + RESET).append(NL);
        stringBuilder.append(TAB+TAB).append("usage: create <class|struct|enum|interface> <Name> [Namespace]").append(NL);
        stringBuilder.append(TAB+TAB).append("--------------------------Create commandList--------------------------").append(NL);
        for(Map.Entry<String, BiPredicate<Logger, Map<String, String>>> entry : this.option.entrySet()){
            stringBuilder.append(TAB+TAB).append(entry.getKey());
            stringBuilder.append(NL);
        }
        stringBuilder.append(TAB+TAB).append("----------------------------------------------------------------------").append(NL);
        return stringBuilder.toString();
    }

    /** 顯示 usage（目前訊息沒有包含 namespace 參數） */
    public void sendUsage(Logger logger) {
        logger.info("usage: create <class|struct|enum|interface> <Name> [Namespace]");
        logger.info("example:");
        logger.info("  create class/enum/interface <className>");
        logger.info("  create class/enum/interface <fileName> <folderName>");
    }

    /**
     * 將模板 source 內的 key 替換成 map value。
     * - null value 會當成 ""
     */
    private static String fileReplace(String source, Map<String, String> map) {
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String value = entry.getValue();
            if (value == null)
                value = "";

            source = source.replace(entry.getKey(), value);
        }
        return source;
    }




    /**
     * 寫檔（若檔案存在就直接失敗）
     *
     * 注意：
     * - 任何 exception 都吞掉，會讓使用者只看到 failure，但不知道原因
     * - mkdirs/createNewFile/writeString 任何一步失敗都回 false
     */
    private boolean writeFile(File file, String source, Logger logger) {
        try {
            SafeFiles.atomicWriteString(
                    file.toPath(),
                    source,
                    StandardCharsets.UTF_8,
                    this.force
            );
            return true;
        } catch (java.nio.file.FileAlreadyExistsException e) {
            return false;
        } catch (Throwable e) {
            // 建議至少在 verbose 模式印出，或寫到 crash log
            logger.warning("write file failed: " + file + " - " + e.getMessage());
            return false;
        }
    }



    /** class 會生成 .h + .cpp（先 .h 成功才做 .cpp） */
    private boolean createClass(Logger logger, Map<String, String> map) {
        // 讀模板
        String hTpl, cppTpl;
        try {
            hTpl = Application.getInstance().getConfigLoader().getCustomFile("class.h");
            cppTpl = Application.getInstance().getConfigLoader().getCustomFile("class.cpp");
        } catch (Throwable e) {
            logger.warning("load template failed: " + e.getMessage());
            return false;
        }
        if (hTpl == null || hTpl.isBlank() || cppTpl == null || cppTpl.isBlank()) {
            logger.warning("template not found/empty: class.h / class.cpp");
            return false;
        }

        // folder / className
        File folder = this.getFolder(map.get("$NAMESPACE$"), logger);
        if (folder == null) return false;

        String className = map.get("$CLASSNAME$");
        if (className == null || className.isBlank()) {
            logger.warning("file name is required.");
            return false;
        }

        File hFile = new File(folder, className + ".h");
        File cppFile = new File(folder, className + ".cpp");

        // dry-run
        if (this.dryRun) {
            boolean hExists = hFile.exists();
            boolean cppExists = cppFile.exists();
            boolean anyExists = hExists || cppExists;

            String action = anyExists ? (this.force ? "overwrite" : "skip(exists)") : "create";
            logger.info(String.format(
                    "[preview] " + NL + NL + "would %s: " +
                            NL + TAB + "%s" +
                            NL + TAB + "%s " + NL + TAB + "(template=class.h/class.cpp)",
                    action, hFile, cppFile));

            return !anyExists || this.force;
        }

        // render
        String hOut = fileReplace(hTpl, map);
        String cppOut = fileReplace(cppTpl, map);

        // write pair
        try {
            boolean ok = atomicWritePair(
                    hFile.toPath(), hOut,
                    cppFile.toPath(), cppOut,
                    StandardCharsets.UTF_8,
                    this.force
            );
            if (!ok) return false;

            logger.info("make new file " + hFile + ".");
            logger.info("make new file " + cppFile + ".");
            return true;

        } catch (FileAlreadyExistsException e) {
            logger.warning("file already exists: " + e.getMessage() + " (use --force to overwrite)");
            return false;

        } catch (Throwable e) {
            logger.warning("write class pair failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }

    private boolean askYesNo(Logger logger, String msg) {
        var p = Application.getInstance().getPrompter();
        if (p == null) {
            // 沒有 prompter（非 REPL 模式），保守：拒絕或當作 No
            logger.warning("no interactive terminal; aborted.");
            return false;
        }

        String line = p.readLine(msg);
        if (line == null) return false;

        String s = line.trim().toLowerCase(java.util.Locale.ROOT);
        return s.equals("y") || s.equals("yes");
    }





    /** struct 只生成 .h */
    private boolean createStruct(Logger logger, Map<String, String> map) {
        return this.createFile("struct.h", logger, map);
    }

    /** enum 只生成 .h */
    private boolean createEnum(Logger logger, Map<String, String> map) {
        return this.createFile("enum.h", logger, map);
    }

    /** interface 只生成 .h */
    private boolean createInterface(Logger logger, Map<String, String> map) {
        return this.createFile("interface.h", logger, map);
    }

    /**
     * 實際生成檔案：
     * 1) 讀取 template（customFile）
     * 2) 根據 namespace 計算資料夾
     * 3) filename = className + template 的副檔名（.h/.cpp）
     * 4) 替換模板變數
     * 5) 寫檔
     */
    private boolean createFile(String temp, Logger logger, Map<String, String> map) {

        String header;
        try {
            header = Application.getInstance().getConfigLoader().getCustomFile(temp);
        } catch (Throwable e) {
            logger.warning("load template failed: " + temp + " - " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }

        if (header == null || header.isBlank()) {
            logger.warning("template not found/empty: " + temp);
            return false;
        }

        File folder = this.getFolder(map.get("$NAMESPACE$"), logger);
        if (folder == null) return false;

        String className = map.get("$CLASSNAME$");
        if (className == null || className.isBlank()) {
            logger.warning("file name is required.");
            return false;
        }

        File file = new File(folder, className + temp.substring(temp.indexOf(".")));

        if (this.dryRun) {
            boolean exists = file.exists();
            String action = exists ? (this.force ? "overwrite" : "skip(exists)") : "create";
            logger.info(String.format("[preview] " + NL + NL + " would %s:" + NL + TAB + " %s " + NL + TAB + "(template=%s)", action, file, temp));
            return !exists || this.force;
        }

        // ✅ 確保資料夾存在（不然常常寫不進去）
        try {
            Files.createDirectories(folder.toPath());
        } catch (Throwable e) {
            logger.warning("create folder failed: " + folder + " - " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }

        header = fileReplace(header, map);

        if (!writeFile(file, header, logger)) {
            // ✅ 別再固定說「同名檔」，用情境分開提示
            if (!this.force && file.exists()) {
                logger.warning("file already exists: " + file + " (use --force to overwrite)");
            } else {
                logger.warning("write file failed: " + file);
            }
            return false;
        }

        logger.info(String.format("make new file %s.", file));
        return true;
    }

    private static Path stageWrite(Path target, String content, Charset charset) throws IOException {
        Files.createDirectories(target.getParent());
        String tmpName = target.getFileName() + ".staging." + UUID.randomUUID();
        Path tmp = target.resolveSibling(tmpName);
        Files.writeString(tmp, content, charset, StandardOpenOption.CREATE_NEW);
        return tmp;
    }

    private static void commitMove(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void quietDelete(Path p) {
        if (p == null) return;
        try { Files.deleteIfExists(p); } catch (Throwable ignored) {}
    }

    private boolean atomicWritePair(Path hTarget, String hContent,
                                    Path cppTarget, String cppContent,
                                    Charset charset, boolean force) throws IOException {

        // parent 目錄（你的 atomicWriteString 也是這樣）
        Files.createDirectories(hTarget.getParent());
        Files.createDirectories(cppTarget.getParent());

        // force=false 時：任一存在就整體拒絕（一致性）
        if (!force && (Files.exists(hTarget) || Files.exists(cppTarget))) {
            throw new FileAlreadyExistsException(
                    (Files.exists(hTarget) ? hTarget : cppTarget).toString()
            );
        }

        Path hTmp = null;
        Path cppTmp = null;

        try {
            // 1) stage：兩份都先寫 tmp
            hTmp = stageWrite(hTarget, hContent, charset);
            cppTmp = stageWrite(cppTarget, cppContent, charset);

            // 2) commit：再搬成正式檔（盡量 atomic move）
            commitMove(hTmp, hTarget);
            hTmp = null; // 已經 commit，不用 cleanup

            commitMove(cppTmp, cppTarget);
            cppTmp = null;

            return true;
        } finally {
            // 任一步失敗：清 staging 垃圾
            quietDelete(hTmp);
            quietDelete(cppTmp);
        }
    }




}
