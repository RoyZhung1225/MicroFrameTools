package org.example.util.terminal;


import org.example.Application;
import org.example.util.buffer.StringBuff;

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
