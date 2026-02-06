package org.example.util.terminal;


import org.example.Application;
import org.example.util.buffer.StringBuff;

import java.util.logging.Logger;

public class CommandReload implements CommandHandler {
    @Override
    public boolean onCommand(StringBuff stringBuff, Logger logger) {
        if(!Application.getInstance().getGlobal().reload()){
            return true;
        }else {
            Application.getInstance().getLogger().info("reload success");
        }
        return true;
    }

    @Override
    public String getDescription() {
        return "reload ignore file";
    }


}
