package org.example.util.format;
import java.time.format.DateTimeFormatter;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class LoggerFormat extends Formatter {
    private final DateTimeFormatter dateTimeFormatter;

    public LoggerFormat(){
        this.dateTimeFormatter = DateTimeFormat.getDataTimeFormatter();
    }
    @Override
    public String format(LogRecord record) {
        return String.format("[%s][%s] %s\n",
                this.dateTimeFormatter.format(record.getInstant()),
                record.getLevel().toString(),
                record.getMessage());
    }
}
