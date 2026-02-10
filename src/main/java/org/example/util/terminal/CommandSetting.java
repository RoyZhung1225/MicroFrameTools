package org.example.util.terminal;

import org.example.app.Application;
import org.example.cli_core.buffer.CommandHandler;
import org.example.cli_core.buffer.StringBuff;

import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class CommandSetting implements CommandHandler {


    @Override
    public boolean onCommand(StringBuff stringBuff, Logger logger) {
        // 範例：setting completion.list on/off


        Application.getInstance().getGlobal().update(this.buffToArray(stringBuff));
        return true;
    }

    @Override
    public String getDescription() {
        return this.getSettingHelpList();
    }



    private String getSettingHelpList(){
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Usage: setting <command>\n");
        stringBuilder.append("--------------------------setting commandList-------------------------\n");
        Map<String, Consumer<String>> optionMap = Application.getInstance().getGlobal().getOptionMap();
        for(Map.Entry<String, Consumer<String>> entry : optionMap.entrySet()){
            stringBuilder.append(entry.getKey());
            stringBuilder.append('\n');
        }
        stringBuilder.append("----------------------------------------------------------------------\n");
        return stringBuilder.toString();
    }

    private String[] buffToArray(StringBuff stringBuff){
        String[] result = new String[stringBuff.remaining()];
        for(int i=0; i< result.length; ++i){
            result[i] = stringBuff.get();
        }
        return result;
    }
}
