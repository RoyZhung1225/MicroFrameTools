package org.example.module_packageinfo;

import java.io.File;
import java.io.FileFilter;

public class PackageFolderFilter implements FileFilter {

    private static final PackageFolderFilter instance = new PackageFolderFilter();

    public static PackageFolderFilter getInstance(){
        return PackageFolderFilter.instance;
    }

    private PackageFolderFilter(){

    }
    @Override
    public boolean accept(File pathname) {
        return !pathname.isFile();
    }
}
