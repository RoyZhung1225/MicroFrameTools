package org.example.cli_core.buffer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class CommandHelp implements CommandHandler {
    public static final String BOLD = "\u001B[1m";
    public static final String RESET = "\u001B[0m";
    public static final String NL = System.lineSeparator();
    public static final String TAB = "    ";

    private final Map<String, CommandHandler> commandHandlerMap;

    private final Map<String, Consumer<String>> optionMap;

    public CommandHelp(Map<String, CommandHandler> commandHandlerMap){
        if(commandHandlerMap == null)
            throw new NullPointerException();

        this.commandHandlerMap = commandHandlerMap;
        this.optionMap = new LinkedHashMap<>();
    }

    @Override
    public boolean onCommand(StringBuff args, Logger logger) {
        this.showCommandList(logger);

        this.argsParser(this.buffToArray(args));

        return true;
    }

    private String[] buffToArray(StringBuff stringBuff){
        String[] result = new String[stringBuff.remaining()];
        for(int i=0; i< result.length; ++i){
            result[i] = stringBuff.get();
        }
        return result;
    }

    public void argsParser(String[] args){

        for(int i=0; i<args.length; ++i){

            if(args[i].charAt(0) == '-'){
                Consumer<String> method = optionMap.get(args[i]);
                if(method == null)
                    continue;

                ++i;
                if(i>args.length)
                    break;

                if(i == args.length){
                    break;
                }

                method.accept(args[i]);
            }
        }
    }



    public void showCommandList(Logger logger){
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(BOLD+ "Help list:" + RESET).append(NL);
        for(Map.Entry<String, CommandHandler> entry : this.commandHandlerMap.entrySet()){
            CommandHandler commandHandler = entry.getValue();
            stringBuilder.append(BOLD).append("NAME").append(RESET).append(NL);
            stringBuilder.append(TAB).append(entry.getKey());
            stringBuilder.append(" - ").append(NL);
            stringBuilder.append(BOLD).append("DESCRIPTION").append(RESET).append(NL);
            stringBuilder.append(TAB).append(commandHandler.getDescription()).append(NL);
        }
        logger.info(stringBuilder.toString());
    }



    @Override
    public String getDescription() {
        return "show commandList." + NL;
    }


}
