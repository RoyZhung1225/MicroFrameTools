package org.example;

import lombok.Getter;
import net.kitsu.lib.util.format.LoggerFormat;
import net.kitsu.lib.util.terminal.Terminal;
import org.example.command.*;

import java.util.Scanner;
import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;

public class Application extends Terminal implements Runnable {
    @Getter
    public static Application instance;
    @Getter
    private final ConfigLoader configLoader;
    @Getter
    private final GlobalVariable global;
    @Getter
    private final Logger logger;
    private final Scanner scanner;
    private boolean start;

    public Application(String[] args){
        Application.instance = this;
        this.logger = this.setupLogger();
        this.scanner = new Scanner(System.in);
        this.global = new GlobalVariable();
        this.configLoader = new ConfigLoader();
        this.start = true;
        this.register();
    }
    public void stop(){
        this.start = false;
    }

    public void run(){
        while (this.start){
            this.logger.info("> ");
            this.execute(this.scanner.nextLine(), this.logger);
        }
    }

    public void register(){
        this.commandHandlerMap.put("create", new CommandCreate());
        this.commandHandlerMap.put("package", new CommandPackage());
        this.commandHandlerMap.put("exit", new CommandExit());
        this.commandHandlerMap.put("info", new CommandInfo());
        this.commandHandlerMap.put("setting", new CommandSetting());
        this.commandHandlerMap.put("reload", new CommandReload());
        this.commandHandlerMap.put("decreate", new CommandCreateDebug());
    }

    private Logger setupLogger(){
        Logger result = Logger.getGlobal();
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new LoggerFormat());
        result.setUseParentHandlers(false);
        result.addHandler(consoleHandler);
        return result;
    }

}
