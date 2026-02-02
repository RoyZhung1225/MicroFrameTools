package org.example.util.terminal;

import org.example.Application;
import org.example.PackageInfo;
import org.example.util.buffer.StringBuff;

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
