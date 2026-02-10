package org.example.cli_jline;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public final class JLineLogHandler extends Handler {
    private final Formatter formatter;

    public JLineLogHandler(Formatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public void publish(LogRecord record) {
        if (record == null || !isLoggable(record)) return;

        String msg;
        try {
            msg = (formatter != null) ? formatter.format(record) : safeMessage(record);
        } catch (Throwable t) {
            msg = safeMessage(record);
        }

        if (msg == null) msg = "";
        msg = msg.replace("\r", ""); // 避免回車破壞排版

        // 如果有 thrown，把 stack trace 接在後面
        Throwable thrown = record.getThrown();
        if (thrown != null) {
            msg = msg + "\n" + stackTrace(thrown);
        }

        // 用 JLine 協調輸出
        ConsoleSink.printAbove(msg);
    }

    private static String safeMessage(LogRecord record) {
        String level = record.getLevel() == null ? "INFO" : record.getLevel().getName();
        String m = record.getMessage();
        if (m == null) m = "";
        return "[" + level + "] " + m;
    }

    private static String stackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    @Override public void flush() {}
    @Override public void close() throws SecurityException {}
}
