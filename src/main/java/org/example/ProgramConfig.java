package org.example;

import lombok.Data;

import java.io.File;

@Data
public class ProgramConfig {

    private File workFolder;
    private File programFolder;

    public ProgramConfig(){
        this.workFolder = new File(System.getProperty("user.dir"));
        //this.programFolder = new File(System.getProperty("user.dir"));
        this.programFolder = new File(System.getProperty("java.class.path")).getParentFile();
    }


    @Override
    public String toString(){
        return "program.jar = " +
                this.programFolder +
                "\r\n" +
                "program.work = " +
                this.workFolder;
    }
}
