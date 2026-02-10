package org.example;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;

@Data
public class ProgramConfig {
    private File workFolder;
    private File programFolder;

    @Getter
    @Setter
    private boolean completionList = false; // 預設關閉

    public ProgramConfig() {
        this.workFolder = new File(System.getProperty("user.dir"));
        this.programFolder = detectProgramFolderFallback();
    }

    private static File detectProgramFolderFallback() {
        // 1) 最準：從 class 的 CodeSource 推位置（IDE=target/classes，jar=jar所在資料夾）
        try {
            URL url = ProgramConfig.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation();
            if (url != null) {
                URI uri = url.toURI();
                Path p = Path.of(uri).toAbsolutePath().normalize();

                // IDE: .../target/classes -> parent parent = project root/target (依你要)
                // 這裡定義 "programFolder" = 該 location 的 parent folder
                // - jar: .../app.jar -> parent = jar所在資料夾
                // - dir: .../target/classes/ -> parent = .../target/classes 的 parent = .../target
                Path parent = p.getParent();
                if (parent != null) return parent.toFile();
            }
        } catch (Throwable ignore) {}

        // 2) fallback：至少別炸，回 user.dir
        return new File(System.getProperty("user.dir"));
    }

    @Override
    public String toString() {
        return "program.jar = " + this.programFolder + "\r\n" +
                "program.work = " + this.workFolder;
    }
}
