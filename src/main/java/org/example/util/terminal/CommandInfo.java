package org.example.util.terminal;


import org.example.Application;
import org.example.CompletableCommand;
import org.example.CompletionRequest;
import org.example.util.buffer.StringBuff;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class CommandInfo implements CommandHandler, CompletableCommand {
    private final Map<String, Consumer<Logger>> optionMap;

    public CommandInfo() {
        this.optionMap = new HashMap<>();
        this.register();
    }

    private void register() {
        this.optionMap.put("config", CommandInfo::showConfig);
        this.optionMap.put("ignore", CommandInfo::showIgnore);
        this.optionMap.put("workspace", CommandInfo::getWorkSpace);
        this.optionMap.put("version", CommandInfo::getVersion);
    }

    private static final List<String> INFO_ITEMS = List.of(
            "commands", "config", "workspace", "version", "help"
    );

    private static final List<String> OPTIONS = List.of("--help", "-h");

    @Override
    public void complete(CompletionRequest req, List<String> out) {
        List<String> tokens = req.getTokens();
        String prefix = req.getPrefix();
        int idx = req.getWordIndex();

        if (prefix == null) prefix = "";

        // idx==0 外層 completer 已補 command 名
        if (idx == 0) return;

        // info 只吃第一個參數作為 option，所以只在「第一個 positional」補全
        int pos = countInfoPositionals(tokens, idx);
        if (pos != 0) return;

        // 來源：optionMap 支援的可查詢項目
        for (String k : this.optionMap.keySet()) {
            if (k == null || k.isBlank()) continue;

            // 你 onCommand 會 toLowerCase，所以補全也統一小寫輸出
            String item = k.toLowerCase();

            if (prefix.isEmpty() || item.startsWith(prefix)) {
                out.add(item);
            }
        }
    }

    private static int countInfoPositionals(List<String> tokens, int wordIndex) {
        // tokens: ["info", ...]
        if (tokens == null || tokens.size() <= 1) return 0;

        int endExclusive = Math.min(wordIndex, tokens.size());

        int count = 0;
        for (int i = 1; i < endExclusive; i++) { // i=1 skip "info"
            String t = tokens.get(i);
            if (t == null || t.isBlank()) continue;

            // 如果未來你加 -h/--help，可在這裡把它當 option 跳過
            if (t.startsWith("-")) continue;

            count++;
        }
        return count;
    }



    @Override
    public boolean onCommand(StringBuff stringBuff, Logger logger) {
        String option = "config";
        if(stringBuff.remaining() > 0)
            option = stringBuff.get().toLowerCase();

        Consumer<Logger> accept = this.optionMap.get(option);
        if(accept == null){
            logger.info("option not found");
            return true;
        }

        accept.accept(logger);
        return true;
    }

    @Override
    public String getDescription() {
        return "show arguments";
    }


    private static void showConfig(Logger logger) {
        logger.info("\r\n" + Application.getInstance().getGlobal().toString());
    }

    private static void showIgnore(Logger logger) {
        logger.info("\r\n" + Application.getInstance().getGlobal().getIgnoreList().toString());
    }

    private static void getWorkSpace(Logger logger) {
        File work = Application.getInstance().getGlobal().getProgram().getWorkFolder();
        if (work == null) {
            logger.warning("workspace: (null)");
            return;
        }

        String abs = work.getAbsolutePath();

        String canon;
        try {
            canon = work.getCanonicalPath();
        } catch (Exception e) {
            canon = abs; // fallback
        }

        logger.info("workspace: " + abs);
        if (!canon.equals(abs)) {
            logger.info("workspace(canonical): " + canon);
        }

        logger.info("exists: " + work.exists());
        logger.info("isDirectory: " + work.isDirectory());
        logger.info("readable: " + work.canRead());
        logger.info("writable: " + work.canWrite());
    }


    private static void getVersion(Logger logger) {
        // 1) 最常用：從 MANIFEST.MF 讀 Implementation-Version
        Package p = Application.class.getPackage();
        String impl = (p != null) ? p.getImplementationVersion() : null;

        // 2) fallback：有些 build 會放 Specification-Version
        String spec = (p != null) ? p.getSpecificationVersion() : null;

        // 3) 最後 fallback：unknown
        String ver = (impl != null && !impl.isBlank()) ? impl
                : (spec != null && !spec.isBlank()) ? spec
                : "unknown";

        logger.info("version: " + ver);

        // 額外資訊（可選但很實用）
        logger.info("java: " + System.getProperty("java.version"));
        logger.info("os: " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " (" + System.getProperty("os.arch") + ")");
    }

}