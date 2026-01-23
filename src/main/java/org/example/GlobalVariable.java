package org.example;

import lombok.Getter;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class GlobalVariable {
    private final Map<String, Consumer<String>> optionMap;

    @Getter
    private final CustomConfig config;
    @Getter
    private final ProgramConfig program;
    @Getter
    private final IgnoreList ignoreList;

    private Logger getLogger(){
        return Application.getInstance().getLogger();
    }


    public GlobalVariable(){
        this.optionMap = new HashMap<>();
        this.optionMap.put("-p", this::updateWorkFolder);
        this.optionMap.put("-path", this::updateWorkFolder);
        this.optionMap.put("-g", this::updateGuard);
        this.optionMap.put("-guard", this::updateGuard);
        this.optionMap.put("-n", this::updateNamespace);
        this.optionMap.put("-namespace", this::updateNamespace);

        this.config = new CustomConfig();
        this.program = new ProgramConfig();
        this.ignoreList = new IgnoreList();
    }

    public void reload(){
        this.config.reload(Application.getInstance().getConfigLoader().getConfig());
        this.ignoreList.reloadFile(Application.getInstance().getConfigLoader().getIgnoreFile());
        this.ignoreList.reloadFolder(Application.getInstance().getConfigLoader().getIgnoreFolder());
    }

    public void update(String[] args){
        for(int i=0; i<args.length; ++i){
            if(args[i].charAt(0) == '-'){
                Consumer<String> method = optionMap.get(args[i]);
                if(method == null)
                    continue;

                ++i;
                if(i>args.length)
                    break;

                method.accept(args[i]);
            }
        }
    }


    private void updateWorkFolder(String var){
        this.program.setWorkFolder(new File(var));
    }

    private void updateGuard(String var){
        this.config.setGuard(var);
    }

    private void updateNamespace(String var){
        this.config.setNamespace(var);
    }

    @Override
    public String toString(){
        return this.program.toString() +
                "\r\n" +
                this.config.toString() +
                "\r\n" +
                this.ignoreList.toString();
    }
}
