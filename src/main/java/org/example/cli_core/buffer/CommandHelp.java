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
        String target = null;
        if (args != null && args.remaining() > 0) {
            target = args.get();
            if (target != null) target = target.trim().toLowerCase();
        }

        if (target == null || target.isBlank()) {
            showCommandList(logger);
            logger.info("usage: help <command>");
            return true;
        }

        CommandHandler h = commandHandlerMap.get(target);
        if (h == null) {
            logger.warning("unknown command: " + target);
            logger.info("use: help");
            return true;
        }

        // 如果 command 有結構化 help，用它
        if (h instanceof HelpableCommand hc) {
            printHelpDoc(target, hc.help(), logger);
            return true;
        }

        // fallback：舊的 getDescription
        logger.info("[" + target + "]");
        logger.info(h.getDescription());
        return true;
    }


    private String[] buffToArray(StringBuff stringBuff){
        String[] result = new String[stringBuff.remaining()];
        for(int i=0; i< result.length; ++i){
            result[i] = stringBuff.get();
        }
        return result;
    }

    private void showCommandList(Logger logger) {
        StringBuilder sb = new StringBuilder();
        sb.append(BOLD).append("COMMANDS").append(RESET).append(NL);

        for (var e : commandHandlerMap.entrySet()) {
            String name = e.getKey();
            CommandHandler h = e.getValue();

            String summary = "";
            if (h instanceof HelpableCommand hc) {
                summary = hc.help().summary();
            } else {
                summary = oneLine(h.getDescription());
            }

            sb.append(TAB).append(name).append(" - ").append(summary).append(NL);
        }

        sb.append(NL).append("usage: help <command>").append(NL);
        logger.info(sb.toString());
    }

    private static String oneLine(String s) {
        if (s == null) return "";
        String t = s.replace("\r", "").replace("\n", " ").trim();
        return t.length() > 80 ? t.substring(0, 77) + "..." : t;
    }

    private static void printHelpDoc(String name, HelpableCommand.HelpDoc doc, Logger logger) {
        StringBuilder sb = new StringBuilder(512);

        sb.append(BOLD).append(name).append(RESET).append(NL);

        if (doc.summary() != null && !doc.summary().isBlank()) {
            sb.append(TAB).append(doc.summary().trim()).append(NL);
        }

        if (doc.usage() != null && !doc.usage().isBlank()) {
            sb.append(NL).append(BOLD).append("USAGE").append(RESET).append(NL);
            sb.append(TAB).append(doc.usage().trim()).append(NL);
        }

        if (doc.options() != null && !doc.options().isEmpty()) {
            sb.append(NL).append(BOLD).append("OPTIONS").append(RESET).append(NL);
            for (String opt : doc.options()) {
                sb.append(TAB).append(opt).append(NL);
            }
        }

        if (doc.examples() != null && !doc.examples().isEmpty()) {
            sb.append(NL).append(BOLD).append("EXAMPLES").append(RESET).append(NL);
            for (String ex : doc.examples()) {
                sb.append(TAB).append(ex).append(NL);
            }
        }

        if (doc.detail() != null && !doc.detail().isBlank()) {
            sb.append(NL).append(BOLD).append("NOTES").append(RESET).append(NL);
            sb.append(TAB).append(doc.detail().trim()).append(NL);
        }

        logger.info(sb.toString());
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




    @Override
    public String getDescription() {
        return "show commandList." + NL;
    }


}
