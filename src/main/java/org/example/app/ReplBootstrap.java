package org.example.app;

import org.example.completion.CommandNameCompleter;
import org.example.completion.CompletableCommand;
import org.example.completion.CompletionRequest;
import org.jline.keymap.KeyMap;
import org.jline.reader.*;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class ReplBootstrap {
    public static void main(String[] args) throws IOException {
        Application application = new Application(args);
        new ReplBootstrap().run(application);
    }


    private static void installSmartTab(LineReader reader,
                                        Terminal terminal,
                                        Application application) {

        reader.getWidgets().put("smart-tab", () -> {
            boolean listOn = application.getGlobal()
                    .getProgram()
                    .isCompletionList();

            if (listOn) {
                reader.setOpt(LineReader.Option.AUTO_LIST);
                reader.setOpt(LineReader.Option.LIST_AMBIGUOUS);
                reader.setVariable(LineReader.LIST_MAX, 100);
            } else {
                reader.unsetOpt(LineReader.Option.AUTO_LIST);
                reader.unsetOpt(LineReader.Option.LIST_AMBIGUOUS);
                reader.setVariable(LineReader.LIST_MAX, 0);
            }

            reader.callWidget(listOn ? "complete" : "menu-complete");
            return true;
        });

        // MAIN keymap
        reader.getKeyMaps()
                .get(LineReader.MAIN)
                .bind(new Reference("smart-tab"), KeyMap.ctrl('I'));

// EMACS keymap
        reader.getKeyMaps()
                .get(LineReader.EMACS)
                .bind(new Reference("smart-tab"), KeyMap.ctrl('I'));

    }




    public void run(Application application) {
        try (Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .jna(true)
                .build()) {

            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(new CommandNameCompleter(application))
                    .build();

            installSmartTab(reader, terminal, application);

            application.setPrompter(prompt -> {
                try {
                    return reader.readLine(prompt);
                } catch (UserInterruptException e) {
                    return null; // 視為取消
                } catch (EndOfFileException e) {
                    return null; // 視為取消/退出
                }
            });

            printAbove(reader, "Program ready. Ctrl+C=cancel line, Ctrl+D=exit, type 'exit' to quit.");


            while (application.isStart()) {
                String line;
                try {
                    line = reader.readLine("> ");

                } catch (UserInterruptException e) {
                    // Ctrl+C：取消當前輸入，不退出
                    terminal.writer().println(); // 乾淨換行（可選但推薦）
                    terminal.flush();
                    continue;
                } catch (EndOfFileException e) {
                    printAbove(reader, "close Program");
                    printAbove(reader, "see you next time");
                    application.stop();
                    break;
                }

                if (line == null) { // defensive
                    printAbove(reader, "close Program");
                    printAbove(reader, "see you next time");
                    application.stop();
                    break;
                }

                String trimmed = line.trim();

                if (trimmed.isEmpty()) continue;

                try {
                    application.executeLine(trimmed);
                } catch (Exception e) {
                    printAbove(reader, "executeLine Error" + e.getMessage());
                }
            }
        } catch (IOException ioException) {
            throw new RuntimeException("Failed to init terminal", ioException);
        }
    }

    private static void printAbove(LineReader reader, String s) {
        reader.printAbove(s);
    }

}
