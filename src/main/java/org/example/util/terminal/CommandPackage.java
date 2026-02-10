package org.example.util.terminal;

import org.example.app.Application;
import org.example.cli_core.buffer.CommandHandler;
import org.example.module_packageinfo.PackageInfo;
import org.example.cli_core.buffer.StringBuff;

import java.util.logging.Logger;

public class CommandPackage implements CommandHandler {
    private final PackageInfo packageInfo;

    public CommandPackage(){
        this.packageInfo = new PackageInfo();
    }

    @Override
    public boolean onCommand(StringBuff stringBuff, Logger logger) {
        this.packageInfo.update();
        this.packageInfo.getIncludeList().writeAll(Application.getInstance().getConfigLoader().getPackageFormatFile());
        return true;
    }

    @Override
    public String getDescription() {
        return "package header file.";
    }


}
