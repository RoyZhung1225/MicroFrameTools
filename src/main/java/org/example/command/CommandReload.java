package org.example.command;

import net.kitsu.lib.util.buffer.StringBuff;
import net.kitsu.lib.util.terminal.CommandHandler;
import org.example.Application;

import java.util.logging.Logger;

public class CommandReload implements CommandHandler {
    @Override
    public boolean onCommand(StringBuff stringBuff, Logger logger) {
        Application.getInstance().getGlobal().reload();
        logger.info("reload success.");
        return true;
    }

    @Override
    public String getDescription() {
        return "reload ignore file";
    }
}
