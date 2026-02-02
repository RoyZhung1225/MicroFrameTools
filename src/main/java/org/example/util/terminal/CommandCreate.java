package org.example.util.terminal;

import org.example.Application;
import org.example.CompletableCommand;
import org.example.CompletionRequest;
import org.example.SafeFiles;
import org.example.util.buffer.StringBuff;
import org.jline.reader.Candidate;

import java.nio.file.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.logging.Logger;

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
    private static final List<String> OPTIONS = List.of("--dry-run", "--force", "--namespace", "--ns", "--help", "-h");
    private static final List<String> NAMESPACES = List.of("my::core", "model::entity");

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

        // 2) 再處理「補 option value」：--namespace/--ns 的 value
        String prev = prevToken(w, idx);
        if ("--namespace".equalsIgnoreCase(prev) || "--ns".equalsIgnoreCase(prev)) {
            addStartsWith(out, NAMESPACES, prefix);
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

        // pos>=2：type + name 都有了 → 補 namespace（可選）
        addStartsWith(out, NAMESPACES, prefix);
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
        boolean dryRun;
        boolean force;
        String type;            // class/struct/enum/interface
        String name;            // Foo
        String namespace;       // my::core
    }

    private ParsedArgs parseArgs(StringBuff sb) {
        ParsedArgs a = new ParsedArgs();
        a.namespace = Application.getInstance().getGlobal().getConfig().getNamespace();

        // 收集所有 token（注意：這會把 sb 消耗完）
        java.util.ArrayList<String> tokens = new java.util.ArrayList<>();
        while (sb.remaining() > 0) {
            String t = sb.get();
            if (t != null && !t.isBlank()) tokens.add(t);
        }

        // 先解析 option（允許任意順序）
        java.util.ArrayList<String> positional = new java.util.ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);

            if ("--dry-run".equalsIgnoreCase(t) || "-n".equalsIgnoreCase(t)) {
                a.dryRun = true;
                continue;
            }

            if ("--force".equalsIgnoreCase(t) || "-f".equalsIgnoreCase(t)) {
                a.force = true;
                continue;
            }

            // 帶值選項：--namespace <value>
            if ("--namespace".equalsIgnoreCase(t) || "--ns".equalsIgnoreCase(t)) {
                if (i + 1 < tokens.size()) {
                    a.namespace = tokens.get(++i);
                }
                continue;
            }

            // 不是 option 的都當位置參數
            positional.add(t);
        }

        // 位置參數映射：type name [namespace]
        if (positional.size() > 0) a.type = positional.get(0);
        if (positional.size() > 1) a.name = positional.get(1);
        if (positional.size() > 2 && (a.namespace == null || a.namespace.isBlank()
                || a.namespace.equals(Application.getInstance().getGlobal().getConfig().getNamespace()))) {
            a.namespace = positional.get(2);
        }
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
        this.dryRun = a.dryRun;
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

        if (func.test(logger, map)) logger.info("success");
        return true;
    }


    @Override
    public String getDescription() {
        return this.getCreateHelpList();
    }

    private String getCreateHelpList(){
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("usage: create <class|struct|enum|interface> <Name> [Namespace]\n");
        stringBuilder.append("--------------------------Create commandList--------------------------\n");
        for(Map.Entry<String, BiPredicate<Logger, Map<String, String>>> entry : this.option.entrySet()){
            stringBuilder.append(entry.getKey());
            stringBuilder.append('\n');
        }
        stringBuilder.append("----------------------------------------------------------------------\n");
        return stringBuilder.toString();
    }

    /** 顯示 usage（目前訊息沒有包含 namespace 參數） */
    public void sendUsage(Logger logger) {
        logger.info("usage: create <class|struct|enum|interface> <Name> [Namespace]");
        logger.info("example:");
        logger.info("  create class Foo");
        logger.info("  create class Foo my::core");
        logger.info("  create -s Data model::entity");
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
        if (this.createFile("class.h", logger, map)) {
            return this.createFile("class.cpp", logger, map);
        }
        return false;
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
        // 讀取模板內容（如果回傳 null，後續 replace 會 NPE）
        String header = Application.getInstance().getConfigLoader().getCustomFile(temp);

        // 根據 namespace 找到輸出資料夾
        File folder = this.getFolder(map.get("$NAMESPACE$"), logger);
        if (folder == null) return false;


        // 檔名（類名）
        String className = map.get("$CLASSNAME$");
        if (className == null) {
            logger.warning("file name not found.");
            return false;
        }

        // 產生目標檔案：<className>.<ext>
        File file = new File(folder, className + temp.substring(temp.indexOf(".")));

        if (this.dryRun) {
            boolean exists = file.exists();

            String action;
            if (exists) {
                action = this.force ? "overwrite" : "skip(exists)";
            } else {
                action = "create";
            }

            logger.info(String.format(
                    "[dry-run] would %s: %s (template=%s)",
                    action, file, temp
            ));

            // 模擬真實結果：
            // - 檔案不存在 → 成功
            // - 檔案存在 + force → 成功（會覆蓋）
            // - 檔案存在 + 無 force → 失敗
            return !exists || this.force;
        }



        // 套用模板替換
        header = fileReplace(header, map);

        // 寫檔失敗就回報
        if (!writeFile(file, header, logger)) {
            logger.warning(String.format("make %s failure.", file));
            return false;
        }



        logger.info(String.format("make new file %s.", file));
        return true;
    }

}
