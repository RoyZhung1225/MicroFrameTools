package org.example.util.terminal;


import org.example.app.Application;
import org.example.cli_core.buffer.CommandHandler;
import org.example.cli_core.buffer.StringBuff;

import java.util.logging.Logger;

public class CommandExit implements CommandHandler {
    @Override
    public boolean onCommand(StringBuff stringBuff, Logger logger) {
        Application.getInstance().stop();
        logger.info("see you next time.");
        return true;
    }

    @Override
    public String getDescription() {
        return "exit program.";
    }


}
