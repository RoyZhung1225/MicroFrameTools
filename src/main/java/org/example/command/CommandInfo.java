package org.example.command;

import net.kitsu.lib.util.buffer.StringBuff;
import net.kitsu.lib.util.terminal.CommandHandler;
import org.example.Application;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class CommandInfo implements CommandHandler {
    private final Map<String, Consumer<Logger>> optionMap;

    public CommandInfo() {
        this.optionMap = new HashMap<>();
        this.register();
    }

    private void register() {
        this.optionMap.put("config", CommandInfo::showConfig);
        this.optionMap.put("ignore", CommandInfo::showIgnore);
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
}
