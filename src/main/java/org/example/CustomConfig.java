package org.example;

import lombok.Data;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;

@Data
public class CustomConfig {
    private String guard = "";
    private String namespace = "";
    private String path = "";
    private String test = "";
    private String debug = "";

    private boolean rootMode = false;
    public CustomConfig(){
    }

    public void reload(String source){
        Yaml yaml = new Yaml();

        Map<String, Object> map = yaml.load(source);

        if(map == null)
            return;

        Object obj;

        obj = map.get("guard");

        if(obj != null)
            this.guard = (String)obj;

        obj = map.get("namespace");

        if(obj != null)
            this.namespace = (String)obj;

        obj = map.get("rootMode");

        if(obj != null)
            this.rootMode = (Boolean)obj;

        obj = map.get("path");

        if(obj != null)
            this.path = (String)obj;

        obj = map.get("test");

        if(obj != null)
            this.test = (String)obj;

        obj = map.get("debug");

        if(obj != null)
            this.debug = (String)obj;

    }

    @Override
    public String toString(){
        return "config.guard = " +
                this.guard +
                "\r\nconfig.namespace = " +
                this.namespace +
                "\r\nconfig.rootMode = " +
                this.rootMode +
                "\r\nconfig.path = " +
                this.path +
                "\r\nconfig.test = " +
                this.test +
                "\r\nconfig.debug = " +
                this.debug;
    }

}
