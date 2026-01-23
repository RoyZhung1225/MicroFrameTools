package org.example.command;

import net.kitsu.lib.util.buffer.StringBuff;
import net.kitsu.lib.util.terminal.CommandHandler;
import org.example.Application;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.logging.Logger;

public class CommandCreate implements CommandHandler {
    private final Map<String, BiPredicate<Logger, Map<String, String>>> option;

    private String getGuard() {
        String result = (Application.getInstance().getGlobal().getConfig().getGuard() + UUID.randomUUID()).toUpperCase();
        return result.replace("-", "_");
    }

    public CommandCreate() {
        this.option = new HashMap<>();
        this.option.put("class", this::createClass);
        this.option.put("-c", this::createClass);
        this.option.put("enum", this::createEnum);
        this.option.put("-e", this::createEnum);
        this.option.put("struct", this::createStruct);
        this.option.put("-s", this::createStruct);
        this.option.put("interface", this::createInterface);
        this.option.put("-i", this::createInterface);
    }

    private File getWorkFolder() {
        return Application.getInstance().getGlobal().getProgram().getWorkFolder();
    }

    private File getFolder(String path) {
        path = Application.getInstance().getGlobal().getConfig().getPath() + path.replace("::", "\\");
        return new File(this.getWorkFolder(), path);
    }

    @Override
    public boolean onCommand(StringBuff stringBuff, Logger logger) {
        String key = "";
        String name = "";
        String path = Application.getInstance().getGlobal().getConfig().getNamespace();

        if (stringBuff.remaining() > 0)
            key = stringBuff.get();

        if (stringBuff.remaining() > 0)
            name = stringBuff.get();

        if (stringBuff.remaining() > 0)
            path = stringBuff.get();

        if (key == null) {
            logger.info("no key");
            return true;
        }

        BiPredicate<Logger, Map<String, String>> func = this.option.get(key);
        if (func == null) {
            logger.info("key not found");
            return true;
        }

        Map<String, String> map = new HashMap<>();
        map.put("$GUARD$", this.getGuard());
        map.put("$NAMESPACE$", path);
        map.put("$CLASSNAME$", name);

        if (func.test(logger, map))
            logger.info("successful");

        return true;
    }

    @Override
    public String getDescription() {
        return "create new file";
    }

    private static String fileReplace(String source, Map<String, String> map) {
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String value = entry.getValue();
            if (value == null)
                value = "";

            source = source.replace(entry.getKey(), value);
        }

        return source;
    }

    private static boolean writeFile(File file, String source) {
        if (file.exists())
            return false;

        try {
            file.getParentFile().mkdirs();
            file.createNewFile();
            Files.writeString(file.toPath(), source, StandardCharsets.UTF_8);
        } catch (Throwable ignore) {
            return false;
        }
        return true;
    }

    private boolean createClass(Logger logger, Map<String, String> map) {
        if (this.createFile("class.h", logger, map)) {
            return this.createFile("class.cpp", logger, map);
        }

        return false;
    }

    private boolean createStruct(Logger logger, Map<String, String> map) {
        return this.createFile("struct.h", logger, map);
    }

    private boolean createEnum(Logger logger, Map<String, String> map) {
        return this.createFile("enum.h", logger, map);
    }

    private boolean createInterface(Logger logger, Map<String, String> map) {
        return this.createFile("interface.h", logger, map);
    }

    private boolean createFile(String temp, Logger logger, Map<String, String> map) {
        String header = Application.getInstance().getConfigLoader().getCustomFile(temp);

        File folder = this.getFolder(map.get("$NAMESPACE$"));

        String className = map.get("$CLASSNAME$");
        if (className == null) {
            logger.warning("file name not found.");
            return false;
        }

        File file = new File(folder, className + temp.substring(temp.indexOf(".")));

        header = fileReplace(header, map);


        if (!writeFile(file, header)) {
            logger.warning(String.format("make %s failure.", file));
            return false;
        }

        logger.info(String.format("make new file %s.", file));

        return true;
    }
}