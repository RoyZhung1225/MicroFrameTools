package org.example.config;

import lombok.Data;
import org.example.app.Application;
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

    public static final class ReloadResult {
        public final boolean ok;
        public final String message; // ok=false 才會有原因

        private ReloadResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        public static ReloadResult ok() { return new ReloadResult(true, ""); }
        public static ReloadResult fail(String msg) { return new ReloadResult(false, msg == null ? "" : msg); }
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

    public ReloadResult reload(String source) {
        if (source == null) {
            return ReloadResult.fail("config.yml is empty/null");
        }

        try {
            Yaml yaml = new Yaml();
            Map<String, Object> map = yaml.load(source);
            if (map == null) {
                return ReloadResult.fail("config.yml parsed to null map");
            }

            // 逐項讀：不存在就跳過，不覆蓋
            applyString(map, "namespace", this::setNamespace);
            applyString(map, "guard", this::setGuard);
            applyString(map, "path", this::setPath);
            applyString(map, "test", this::setTest);
            applyString(map, "debug", this::setDebug);

            // rootMode 若之後你要支援，也可以加：
            // applyBoolean(map, "rootMode", this::setRootMode);

            return ReloadResult.ok();

        } catch (Exception e) {
            return ReloadResult.fail("config.yml format error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void applyString(Map<String, Object> map, String key, java.util.function.Consumer<String> setter) {
        Object obj = map.get(key);
        if (obj == null) return;

        if (obj instanceof String s) {
            setter.accept(s);
            return;
        }

        // 允許 YAML 不是字串但可轉字串（例如數字）
        setter.accept(String.valueOf(obj));
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
