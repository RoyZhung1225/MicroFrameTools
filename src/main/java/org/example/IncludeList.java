package org.example;

import lombok.Data;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

@Data
public class IncludeList {
    private final File baseFolder;
    private List<String> fileList;
    private List<IncludeList> folderList;

    public IncludeList(File folder, IgnoreList ignoreList){
        this.baseFolder = folder;
        this.fileList = new LinkedList<>();
        this.folderList = new LinkedList<>();

        File[] files = folder.listFiles(PackageFileFilter.getInstance());


        if (files != null) {
            for(File f : files){
                if(ignoreList != null){
                    if(ignoreList.isIgnoreFile(f.getName()))
                        continue;
                }
                this.fileList.add(f.getName());
            }
        }

        File[] folders = folder.listFiles(PackageFolderFilter.getInstance());

        if (folders != null) {
            for(File f : folders){
                if(ignoreList != null){
                    if(ignoreList.isIgnoreFolder(f.getName()))
                        continue;
                }

                IncludeList list = new IncludeList(f, ignoreList);
                if(!list.isEmpty())
                    this.folderList.add(list);
            }
        }
    }

    public boolean isEmpty(){
        return (this.fileList.isEmpty() && this.folderList.isEmpty());
    }

    @Override
    public String toString(){
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("folder : ");
        stringBuilder.append(this.baseFolder.toString());
        stringBuilder.append("\r\n");
        stringBuilder.append("----------------------------------------\r\n");
        stringBuilder.append("include folder list:\r\n");
        stringBuilder.append(this.getIncludeFolders());
        stringBuilder.append("include list:\r\n");
        stringBuilder.append(this.getIncludeFiles());

        for(IncludeList i : this.folderList){
            stringBuilder.append(i.toString());
        }

        return stringBuilder.toString();
    }

    public String getIncludeFiles(){
        StringBuilder stringBuilder = new StringBuilder();
        for(String s : this.fileList){
            stringBuilder.append("#include \"./");
            stringBuilder.append(s);
            stringBuilder.append("\"\r\n");
        }
        return stringBuilder.toString();
    }

    public String getIncludeFolders(){
        StringBuilder stringBuilder = new StringBuilder();
        for(IncludeList i : this.folderList){
            stringBuilder.append("#include \"./");
            stringBuilder.append(i.getFolderName());
            stringBuilder.append("/package-info.h\"\r\n");
        }
        return stringBuilder.toString();
    }

    public String getFolderName(){
        return this.baseFolder.getName();
    }

    public String generator(String format){
        format = format.replace("$INCLUDE_FOLDER$", this.getIncludeFolders());
        format = format.replace("$INCLUDE$", this.getIncludeFiles());
        format = format.replace("$DEFINE$", "");
        return format;
    }

    public void write(String format){
        File pf = new File(this.baseFolder, "package-info.h");

        try {
            if(pf.exists())
                pf.delete();

            pf.createNewFile();
            Files.writeString(pf.toPath(), this.generator(format), StandardCharsets.UTF_8);
            this.getLogger().info("write file : " + pf);
        } catch (IOException e) {
            this.getLogger().warning("write file fail : " + pf);
        }
    }

    public void writeAll(String format){
        this.write(format);
        for(IncludeList i : this.folderList){
            i.writeAll(format);
        }
    }

    private Logger getLogger(){
        return Application.getInstance().getLogger();
    }
}
