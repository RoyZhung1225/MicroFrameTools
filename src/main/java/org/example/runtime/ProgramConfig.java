package org.example;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.example.app.Application;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
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
        this.programFolder = detectProgramFolder();
    }

    private static File detectProgramFolder() {
        try {
            var uri = Application.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI();
            Path p = Path.of(uri).toAbsolutePath().normalize();

            // 如果是 jar 檔，取 parent；如果是資料夾（classes），就用它本身
            if (p.toString().toLowerCase().endsWith(".jar")) {
                return p.getParent().toFile();
            }
            return p.toFile();
        } catch (URISyntaxException e) {
            // fallback：至少不炸
            return new File(System.getProperty("user.dir"));
        }
    }


    @Override
    public String toString() {
        return "program.jar = " + this.programFolder + "\r\n" +
                "program.work = " + this.workFolder;
    }
}
