package org.example.runtime;

import lombok.Getter;
import org.example.ConfigLoader;
import org.example.ProgramConfig;
import org.example.config.CustomConfig;
import org.example.app.Application;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class GlobalVariable {
    @Getter
    private final Map<String, Consumer<String>> optionMap;
    @Getter
    private final CustomConfig config;
    @Getter
    private final ProgramConfig program;
    @Getter
    private final IgnoreList ignoreList;

    private Logger getLogger(){
        return Application.getInstance().getLogger();
    }


    public GlobalVariable(){
        this.optionMap = new LinkedHashMap<>();
        optionMap.put("-completion.list", (v) -> {
            boolean on = v != null && (
                    "on".equalsIgnoreCase(v) ||
                            "true".equalsIgnoreCase(v) ||
                            "1".equals(v) ||
                            "yes".equalsIgnoreCase(v) ||
                            "y".equalsIgnoreCase(v)
            );

            // 預設關閉：false；輸入 on/true/1/yes/y 才會開
            getProgram().setCompletionList(on);

            // (可選) 如果你想回饋訊息，這裡可以 logger.info，但注意你 Global 目前拿 logger 的方式
        });

        this.optionMap.put("-p", this::updateWorkFolder);
        this.optionMap.put("-path", this::updateWorkFolder);
        this.optionMap.put("-g", this::updateGuard);
        this.optionMap.put("-guard", this::updateGuard);
        this.optionMap.put("-n", this::updateNamespace);
        this.optionMap.put("-namespace", this::updateNamespace);

        this.config = new CustomConfig();
        this.program = new ProgramConfig();
        this.ignoreList = new IgnoreList();
    }

    public boolean reload(ConfigLoader loader, Logger logger) {
        boolean ok = true;

        var r1 = this.config.reload(loader.getConfig());
        if (!r1.ok) { ok = false; if (logger != null) logger.warning(r1.message); }

        var r2 = this.ignoreList.reloadFile(loader.getIgnoreFile());
        if (!r2.ok) { ok = false; if (logger != null) logger.warning(r2.message); }

        var r3 = this.ignoreList.reloadFolder(loader.getIgnoreFolder());
        if (!r3.ok) { ok = false; if (logger != null) logger.warning(r3.message); }

        return ok;
    }



//    // Global.java
//    private final ProgramRuntime runtime = new ProgramRuntime();
//    public ProgramRuntime runtime() { return runtime; }
//
//
//    @Setter
//    @Getter
//    public static final class ProgramRuntime {
//        private volatile boolean completionList = false; // ✅ 預設關閉
//
//    }

    public void update(String[] args) {
        if (args == null || args.length == 0) {
            Application.getInstance().getLogger().warning("usage: setting -<command> <value>");
            return;
        }

        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if (key == null || key.isBlank()) continue;

            if (key.charAt(0) != '-') continue;

            Consumer<String> method = optionMap.get(key);
            if (method == null) continue;

            // 下一個 token 必須是 value
            i++;
            if (i >= args.length) {
                this.getLogger().warning("no setting value.");
                break;
            }

            method.accept(args[i]);
        }
    }



    private void updateWorkFolder(String var){
        if(!Application.getInstance().getGlobal().getConfig().isRootMode()){
            Application.getInstance().getLogger().warning("rootMode is not open.");
            return;
        }
        this.program.setWorkFolder(new File(var));
    }

    private void updateGuard(String var){
        this.config.setGuard(var);
    }

    private void updateNamespace(String var){
        this.config.setNamespace(var);
    }

    @Override
    public String toString(){
        return this.program.toString() +
                "\r\n" +
                this.config.toString() +
                "\r\n" +
                this.ignoreList.toString();
    }
}
