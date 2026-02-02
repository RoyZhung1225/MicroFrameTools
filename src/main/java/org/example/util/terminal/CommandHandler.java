package org.example.util.terminal;


import org.example.util.buffer.StringBuff;

import java.util.logging.Logger;

public interface CommandHandler {
    boolean onCommand(StringBuff args, Logger logger);

    String getDescription();

}
