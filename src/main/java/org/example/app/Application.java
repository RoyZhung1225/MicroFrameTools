package org.example.app;

import lombok.Getter;

import lombok.Setter;
import org.example.config.ConfigLoader;
import org.example.cli_core.buffer.CommandHandler;

import org.example.runtime.GlobalVariable;
import org.example.runtime.Prompter;
import org.example.cli_core.buffer.FrameTerminal;
import org.example.cli_jline.JLineLogHandler;
import org.example.cli_jline.format.LoggerFormat;
import org.example.util.terminal.*;
import org.example.workspace.WorkspaceGuard;


import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.*;

public final class Application extends FrameTerminal {
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

        WorkspaceGuard.ensureWorkspaceOrExit(global.getProgram(), logger);

        Path work = global.getProgram().getWorkFolder().toPath();
        Path program = global.getProgram().getProgramFolder().toPath();
        this.configLoader = new ConfigLoader(work, program);

        this.commandHandlerMap.put("package", new CommandPackage());
        this.commandHandlerMap.put("exit", new CommandExit());
        this.commandHandlerMap.put("info", new CommandInfo());
        this.commandHandlerMap.put("setting", new CommandSetting());
        this.commandHandlerMap.put("reload", new CommandReload());
        this.commandHandlerMap.put("create", new CommandCreate());
        this.commandHandlerMap.put("guard", new CommandGuardPlan());

        WorkspaceGuard.checkWorkspace(work, logger);
        this.global.reload(this.configLoader, logger);
        this.start = true;
    }

    @Getter
    @Setter
    private Prompter prompter;

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


    private static volatile boolean LOG_INITED = false;

    private Logger setupLogger() {
        Logger global = Logger.getGlobal();

        if (LOG_INITED) return global;
        LOG_INITED = true;

        // 斷根：移除 root/global 既有 handler（含 IDE / 預設 ConsoleHandler）
        LogManager.getLogManager().reset();

        global.setUseParentHandlers(false);

        Handler h = new JLineLogHandler(new LoggerFormat());
        h.setLevel(Level.ALL);
        global.addHandler(h);

        global.setLevel(Level.ALL);
        return global;
    }
}
