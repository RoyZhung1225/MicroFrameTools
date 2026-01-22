package org.example;

import lombok.Getter;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class GlobalVariable {
    private final Map<String, Consumer<String>> optionMap;
    @Getter
    private final ProgramConfig program;
    @Getter
    private final CustomConfig customConfig;
    @Getter
    private final IgnoreList ignoreList;

    public GlobalVariable(){
        this.optionMap = new HashMap<>();

        this.register();

        this.program = new ProgramConfig();
        this.customConfig = new CustomConfig();
        this.ignoreList = new IgnoreList();
    }
    public void reload(){
        this.customConfig.reload(Application.getInstance().getConfigLoader().getConfig());
        this.ignoreList.reloadFile(Application.getInstance().getConfigLoader().getIgnoreFile());
        this.ignoreList.reloadFolder(Application.getInstance().getConfigLoader().getIgnoreFolder());
    }

    public void register(){
        this.optionMap.put("-p", this::updateWorkFolder);
        this.optionMap.put("-path", this::updateWorkFolder);
        this.optionMap.put("-g", this::updateGuard);
        this.optionMap.put("-guard", this::updateGuard);
        this.optionMap.put("-n", this::updateNamespace);
        this.optionMap.put("-namespace", this::updateNamespace);
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

    public void updateWorkFolder(String var){this.program.setWorkFolder(new File(var));}
    private void updateGuard(String var){
        this.customConfig.setGuard(var);
    }
    private void updateNamespace(String var){
        this.customConfig.setNamespace(var);
    }

    @Override
    public String toString(){
        return this.program.toString() +
                "\r\n" +
                this.customConfig.toString() +
                "\r\n" +
                this.ignoreList.toString();
    }
}
