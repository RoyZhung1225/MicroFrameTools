package org.example.module_packageinfo;

import lombok.Getter;
import org.example.app.Application;
import org.example.runtime.IgnoreList;


import java.io.File;
import java.util.logging.Logger;

public class PackageInfo {

    @Getter
    private IncludeList includeList;
    public Logger getLogger(){
        return Application.getInstance().getLogger();
    }

    public IgnoreList getIgnoreFolder(){
        return Application.getInstance().getGlobal().getIgnoreList();
    }

    public File getWorkFolder(){
        return new File(Application.getInstance().getGlobal().getProgram().getWorkFolder(), Application.getInstance().getGlobal().getConfig().getPath());
    }

    public void update(){
        this.includeList = new IncludeList(this.getWorkFolder(), this.getIgnoreFolder());
    }
}
