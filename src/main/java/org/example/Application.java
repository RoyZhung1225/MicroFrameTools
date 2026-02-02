package org.example;

import lombok.Getter;

import org.example.util.format.LoggerFormat;
import org.example.util.terminal.*;


import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;

public final class Application extends FrameTerminal{
    @Getter
    private static Application instance;
    @Getter
    private final GlobalVariable global;
    @Getter
    private final Logger logger;
    @Getter
    private final ConfigLoader configLoader;
    @Getter
    private boolean start;

    public Application(String[] args) throws IOException {

        Application.instance = this;
        this.logger = this.setupLogger();
        this.global = new GlobalVariable();
        this.configLoader = new ConfigLoader();
        this.global.reload();

        this.commandHandlerMap.put("package", new CommandPackage());
        this.commandHandlerMap.put("exit", new CommandExit());
        this.commandHandlerMap.put("info", new CommandInfo());
        this.commandHandlerMap.put("setting", new CommandSetting());
        this.commandHandlerMap.put("reload", new CommandReload());
        this.commandHandlerMap.put("create", new CommandCreate());
        this.commandHandlerMap.put("guard", new CommandGuardPlan());

        Path work = global.getProgram().getWorkFolder().toPath();
        WorkspaceGuard.checkWorkspace(work, logger);

        this.start = true;
    }



    public void stop() {
        this.start = false;
    }

    public void executeLine(String line){
        this.execute(line, this.logger);
    }

    public List<String> getCommandNames(){
        List<String> commandList = new LinkedList<>();
        for (Map.Entry<String, CommandHandler> entry : this.commandHandlerMap.entrySet()){
            commandList.add(entry.getKey());
        }
        return commandList;
    }

    public CommandHandler getHandler(String name) {
        return this.commandHandlerMap.get(name);
    }

    private Logger setupLogger() {
        Logger result = Logger.getGlobal();
        result.setUseParentHandlers(false);

        // 清掉既有 handler，避免重複輸出（尤其你會重建 Application）
        for (var h : result.getHandlers()) {
            result.removeHandler(h);
        }

        // 改成 JLine-aware handler：走 reader.printAbove，避免破壞輸入行/候選清單
        result.addHandler(new org.example.util.terminal.JLineLogHandler(new LoggerFormat()));
        return result;
    }

}
