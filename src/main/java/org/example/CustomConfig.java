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

    private String loadConfig(String config, String source){

        Yaml yaml = new Yaml();

        Map<String, Object> map = yaml.load(source);

        if(map == null)
            return null;

        Object obj;
        obj = map.get(config);
        String load = "";
        if (obj == null) {
            Application.getInstance().getLogger().info(String.format("Config <%s> loading error! Please check.", config));
        }

        if(obj != null)
            load = (String)obj;

        return load;
    }

    public void reload(String source) {

        this.namespace = this.loadConfig("namespace", source);
        this.guard = this.loadConfig("guard", source);
        this.path = this.loadConfig("path", source);
        this.test = this.loadConfig("test", source);
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
