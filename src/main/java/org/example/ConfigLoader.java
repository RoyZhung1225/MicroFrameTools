package org.example;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class ConfigLoader {


    public String getConfig() {
        return this.loadString("config.yml");
    }

    public String getCustomFile(String filename) {
        return this.loadString(filename);
    }

    public String getPackageFormatFile() {
        return this.loadString("package-info.h");
    }
    public String getIgnoreFolder(){
        return this.loadString("ignore-list.txt");
    }

    public String getIgnoreFile(){return this.loadString("ignore-file.txt");}

    private File getWorkFolder() {
        return Application.getInstance().getGlobal().getProgram().getWorkFolder();
    }

    private File getProgramFolder() {
        return Application.getInstance().getGlobal().getProgram().getProgramFolder();
    }

    private String loadString(String fileName) {
        File file = this.getFile(fileName);
        if (file == null)
            return "";

        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }


    private File getFile(String fileName) {
        fileName = ".kitconfig\\" + fileName;
        File result = new File(this.getWorkFolder(), fileName);
        if (result.isFile())
            return result;

        result = new File(this.getProgramFolder(), fileName);
        if (result.isFile())
            return result;

        return null;
    }
}
