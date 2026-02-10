package org.example.cli_jline.format;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DateTimeFormat {
    public static DateTimeFormatter getDataTimeFormatter(){
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").
                withZone(ZoneId.systemDefault());
    }
}
