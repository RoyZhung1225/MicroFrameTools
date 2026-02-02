package org.example.util.terminal;
import org.example.util.buffer.StringBuff;

import java.util.logging.Logger;

public class FrameTerminal extends CommandIndex {
    private String unknownCommand;
    public FrameTerminal(){
        super();
        this.unknownCommand = "unknown command please try 'help'.";
    }

    public void setUnknownCommand(String text){
        if(text == null)
            text = "unknown command please try 'help'.";

        this.unknownCommand = text;
    }

    public boolean execute(String line, Logger logger){
        if(line.isEmpty())
            return false;

        StringBuff stringBuff = new StringBuff(128);
        int index = 0;

        while (true){
            int next;

            try{
                while (line.charAt(index) == ' ')
                    ++index;


                if(line.charAt(index) == '"'){
                    ++index;
                    next = line.indexOf('"', index);
                }else{
                    next = line.indexOf(' ', index);
                }
            }catch (StringIndexOutOfBoundsException ignore){
                break;
            }


            if(next == -1){
                stringBuff.put(line.substring(index));
                break;
            }


            stringBuff.put(line.substring(index, next));
            index = ++next;
        }

        stringBuff.flip();

        if(this.commandExecute(stringBuff, logger))
            return true;

        logger.info(this.unknownCommand);
        return false;
    }
}
