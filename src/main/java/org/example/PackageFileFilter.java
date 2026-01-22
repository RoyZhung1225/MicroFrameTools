package org.example;

import java.io.File;
import java.io.FileFilter;

public class PackageFileFilter implements FileFilter {
    private static PackageFileFilter instance = new PackageFileFilter();

    public static PackageFileFilter getInstance(){
        return PackageFileFilter.instance;
    }

    private PackageFileFilter(){
    }

    @Override
    public boolean accept(File pathname) {
        if(!pathname.isFile())
            return false;

        String fileName = pathname.getName();
        int index = fileName.lastIndexOf('.');
        if(index == -1)
            return false;

        String fileExtension = fileName.substring(1+index);

        if(fileExtension.equalsIgnoreCase("package-info.h"))
            return false;

        if(fileExtension.equalsIgnoreCase("h"))
            return true;

        if(fileExtension.equalsIgnoreCase("hpp"))
            return true;

        return false;
    }
}
