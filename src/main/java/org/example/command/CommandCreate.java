package org.example.command;

import net.kitsu.lib.util.buffer.StringBuff;
import net.kitsu.lib.util.terminal.CommandHandler;
import org.example.Application;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.logging.Logger;

public class CommandCreate implements CommandHandler {
    private final Map<String, BiPredicate<Logger, Map<String, String>>> option;

    private String getGuard() {
        String result = (Application.getInstance().getGlobal().getCustomConfig().getGuard() + UUID.randomUUID()).toUpperCase();
        return result.replace("-", "_");
    }

    public CommandCreate() {
        this.option = new HashMap<>();
        this.register();
    }

    private File getWorkFolder() {
        return Application.getInstance().getGlobal().getProgram().getWorkFolder();
    }
    private File getFolder(String path) {
        path = Application.getInstance().getGlobal().getCustomConfig().getPath() + path.replace("::", "\\");
        return new File(this.getWorkFolder(), path);
    }

    private void register(){
        this.option.put("class", this::createClass);
        this.option.put("-c", this::createClass);
        this.option.put("enum", this::createEnum);
        this.option.put("-e", this::createEnum);
        this.option.put("struct", this::createStruct);
        this.option.put("-s", this::createStruct);
        this.option.put("interface", this::createInterface);
        this.option.put("-i", this::createInterface);
    }


    @Override
    public boolean onCommand(StringBuff stringBuff, Logger logger) {
        String key = "";
        String name = "";
        String path = Application.getInstance().getGlobal().getCustomConfig().getNamespace();

        if(stringBuff.remaining() > 0)
            key = stringBuff.get();

        if(stringBuff.remaining() > 0)
            name = stringBuff.get();

        if(stringBuff.remaining() > 0)
            path = stringBuff.get();

        if(key == null){
            logger.info("key does not exist");
            return true;
        }

        BiPredicate<Logger, Map<String, String>> func = this.option.get(key);
        if(func == null){
            logger.info("key not found");
            return true;
        }

        Map<String, String> map = new HashMap<>();
        map.put("$GUARD$", this.getGuard());
        map.put("$NAMESPACE$", path);
        map.put("$CLASSNAME$", name);

        if(func.test(logger, map))
            logger.info("succeed");

        return true;
    }

    private boolean createClass(Logger logger, Map<String, String> map) {
        if (this.createFile("class.h", logger, map)) {
            return this.createFile("class.cpp", logger, map);
        }

        return false;
    }

    private boolean createEnum(Logger logger, Map<String, String> map) {
        return this.createFile("enum.h", logger, map);
    }

    private boolean createInterface(Logger logger, Map<String, String> map) {
        return this.createFile("interface.h", logger, map);
    }

    private boolean createStruct(Logger logger, Map<String, String> map){
        return this.createFile("struct.h", logger, map);
    }

    private boolean createFile(String temp, Logger logger, Map<String, String> map) {
        String header = Application.getInstance().getConfigLoader().getCustomFile(temp);
        logger.info(header);
        return true;
    }

    @Override
    public String getDescription() {return "create new file";}

}
