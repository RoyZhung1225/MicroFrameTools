package org.example.cli_core.buffer;

import java.util.logging.Logger;

public class CommandIndex extends CommandExecutor implements CommandHandler {
    private final String description;
    private final CommandHelp commandHelp;

    public CommandIndex(){
        this("no description.");
    }

    public CommandIndex(String description){
        super();
        if(description == null)
            description = "no description.";

        this.description = description;
        this.commandHelp = new CommandHelp(this.commandHandlerMap);
        super.getCommandMap().put("help", this.commandHelp);
    }

    @Override
    public boolean onCommand(StringBuff args, Logger logger) {
        if(args == null)
            throw new NullPointerException();

        if(args.remaining() <= 0)
            return this.commandHelp.onCommand(args, logger);

        return this.commandExecute(args, logger);
    }

    @Override
    public String getDescription() {
        return this.description;
    }

}
