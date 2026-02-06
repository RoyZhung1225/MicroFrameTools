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

    public CustomConfig() {
    }

    private String loadConfig(String config, Map<String, Object> map){

        String load;
        Object obj;
        try {

            if(map == null)
                return null;

            obj = map.get(config);

            if (obj == null) {
                Application.getInstance().getLogger().info(String.format("Config <%s> loading error! Please check.", config));
                return null;
            }

            load = (String)obj;

            return load;
        }catch (ClassCastException e){
            Application.getInstance().getLogger().warning("config.yml loading error!");
            return null;
        }

    }

    public boolean reload(String source) {
        String v;
        Map<String, Object> map;
        try {
            Yaml yaml = new Yaml();
            map = yaml.load(source);
            v = loadConfig("namespace", map);
            if (v != null) { this.namespace = v;}

            v = loadConfig("guard", map);
            if (v != null) { this.guard = v;}

            v = loadConfig("path", map);
            if (v != null) { this.path = v;}

            v = loadConfig("test", map);
            if (v != null) { this.test = v;}

            return true;
        }catch (Exception e){
            Application.getInstance().getLogger().warning("config.yml format error!");
            return false;
        }
    }

    @Override
    public String toString() {
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
