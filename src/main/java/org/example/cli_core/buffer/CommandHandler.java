package org.example.cli_core.buffer;


import java.util.logging.Logger;

public interface CommandHandler {
    boolean onCommand(StringBuff args, Logger logger);

    String getDescription();

}
