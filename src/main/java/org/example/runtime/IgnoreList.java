package org.example.runtime;

import lombok.Getter;
import org.example.app.Application;

import java.util.*;

public class IgnoreList {
    @Getter
    private final Map<String, Object> ignoreFolderMap;
    private final Map<String, Object> ignoreFileMap;

    public IgnoreList() {
        this.ignoreFolderMap = new HashMap<>();
        this.ignoreFileMap = new HashMap<>();
    }

    public static final class ReloadResult {
        public final boolean ok;
        public final String message;

        private ReloadResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message == null ? "" : message;
        }

        public static ReloadResult ok() { return new ReloadResult(true, ""); }
        public static ReloadResult fail(String msg) { return new ReloadResult(false, msg); }
    }


    public boolean isIgnoreFile(String folderName) {
        return this.ignoreFileMap.containsKey(folderName);
    }

    public List<String> getIgnoreFileList() {
        return new ArrayList<>(this.ignoreFileMap.keySet());
    }

    public boolean isIgnoreFolder(String folderName) {
        return this.ignoreFolderMap.containsKey(folderName);
    }

    public List<String> getIgnoreFolderList() {
        return new ArrayList<>(this.ignoreFolderMap.keySet());
    }

    public ReloadResult reloadFolder(String source) {
        this.ignoreFolderMap.clear();

        if (source == null) {
            return ReloadResult.fail("ignore-list.txt is empty/null");
        }

        try {
            String[] lines = source.split("\\r?\\n");
            for (String raw : lines) {
                if (raw == null) continue;
                String name = raw.trim();
                if (name.isEmpty()) continue;
                if (name.startsWith("#")) continue;

                this.ignoreFolderMap.put(name, null);
            }
            return ReloadResult.ok();
        } catch (Throwable t) {
            return ReloadResult.fail("load ignoreFolder error: " + t.getMessage());
        }
    }


    public ReloadResult reloadFile(String source) {
        this.ignoreFileMap.clear();
        this.ignoreFileMap.put("package-info.h", null); // keep your default

        if (source == null) {
            return ReloadResult.fail("ignore-file.txt is empty/null");
        }

        try {
            String[] lines = source.split("\\r?\\n");
            for (String raw : lines) {
                if (raw == null) continue;
                String name = raw.trim();
                if (name.isEmpty()) continue;
                if (name.startsWith("#")) continue;

                this.ignoreFileMap.put(name, null);
            }
            return ReloadResult.ok();
        } catch (Throwable t) {
            return ReloadResult.fail("load ignoreFile error: " + t.getMessage());
        }
    }


//    public void reload(String folder, String file){
//        this.reloadFile(file);
//        this.reloadFolder(folder);
//    }

    @Override
    public String toString(){
        List<String> folderList = this.getIgnoreFolderList();
        List<String> fileList = this.getIgnoreFileList();
        StringBuilder stringBuilder = new StringBuilder("\r\n");

        if(folderList.size() != 0){
            int i=0;
            stringBuilder.append("------------------------------------------\r\nfolder list:\r\n");
            for(String s : folderList){
                stringBuilder.append(i);
                stringBuilder.append(") ");
                stringBuilder.append(s);
                stringBuilder.append("\r\n");
                ++i;
            }
        }else{
            stringBuilder.append("------------------------------------------\r\nfolder ignore is empty\r\n");
        }

        if(fileList.size() != 0){
            int i=0;
            stringBuilder.append("------------------------------------------\r\nfile list:\r\n");
            for(String s : fileList){
                stringBuilder.append(i);
                stringBuilder.append(") ");
                stringBuilder.append(s);
                stringBuilder.append("\r\n");
                ++i;
            }
        }else{
            stringBuilder.append("------------------------------------------\r\nfile ignore is empty\r\n");
        }

        return stringBuilder.toString();
    }
}
