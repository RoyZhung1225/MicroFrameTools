package org.example.cli_core.buffer;


import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

public class CommandExecutor {
    protected final Map<String, CommandHandler> commandHandlerMap;

    public CommandExecutor(){
        this.commandHandlerMap = new LinkedHashMap<>() {
            @Override
            public CommandHandler put(String key, CommandHandler value) {
                return super.put(key.toLowerCase(), value);
            }

            public CommandHandler replace(String key, CommandHandler value) {
                return super.replace(key.toLowerCase(), value);
            }
        };
    }

    public Map<String, CommandHandler> getCommandMap(){
        return this.commandHandlerMap;
    }

    public boolean commandExecute(StringBuff args, Logger logger){
        if(args.remaining() <= 0)
            return false;

        CommandHandler commandHandler = this.commandHandlerMap.get(args.get().toLowerCase());

        if(commandHandler == null)
            return false;

        return commandHandler.onCommand(args, logger);
    }
}
