package org.example.util.terminal;

import org.jline.reader.LineReader;

import java.util.concurrent.atomic.AtomicReference;

public final class ConsoleSink {
    private static final AtomicReference<LineReader> READER = new AtomicReference<>();

    private ConsoleSink() {}

    public static void bind(LineReader reader) {
        READER.set(reader);
    }

    public static void unbind() {
        READER.set(null);
    }

    public static void printAbove(String s) {
        LineReader r = READER.get();
        if (r != null) {
            r.printAbove(s);
        } else {
            // fallback（非 REPL 或尚未 bind）
            System.out.println(s);
        }
    }
}
