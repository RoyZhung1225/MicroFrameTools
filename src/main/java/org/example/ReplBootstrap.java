package org.example;

import lombok.SneakyThrows;
import org.example.util.terminal.CommandHandler;
import org.jline.reader.*;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class ReplBootstrap{
    public static void main(String[] args) throws IOException {
        Application application = new Application(args);
        new ReplBootstrap().run(application);
    }
    public static final class CommandNameCompleter implements Completer{
        Application application;

        public CommandNameCompleter(Application application){

            this.application = application;
        }

        @Override
        public void complete(LineReader lineReader, ParsedLine parsedLine, List<Candidate> candidates) {
            int wordIndex = parsedLine.wordIndex();

            String prefix = parsedLine.word();
            if (prefix == null) prefix = "";

            List<String> tokens = parsedLine.words();
            if (tokens == null) tokens = List.of();

            // 1) 第 0 token：補 command 名
            if (wordIndex == 0) {
                String finalPrefix = prefix;
                application.getCommandNames().stream()
                        .filter(n -> n != null && !n.isBlank())
                        .sorted()
                        .filter(n -> finalPrefix.isEmpty() || n.startsWith(finalPrefix))
                        .forEach(n -> candidates.add(new Candidate(n)));
                return;
            }

            // 2) 參數補全：routing 到 handler.complete
            if (tokens.isEmpty()) return;

            String cmd = tokens.get(0);
            if (cmd == null || cmd.isBlank()) return;

            CommandHandler handler = application.getHandler(cmd);
            if (!(handler instanceof CompletableCommand completable)) {
                return;
            }

            // 建 CompletionRequest（用 setter）
            CompletionRequest req = new CompletionRequest();
            req.setCommandName(cmd);               // 先用你現有欄位
            req.setTokens(tokens);
            req.setWordIndex(wordIndex);
            req.setPrefix(prefix);

            // workDir 用字串：跟你目前的 model 對齊
            String workDir = application.getGlobal().getProgram().getWorkFolder().getAbsolutePath();
            req.setWorkDir(workDir);

            List<String> out = new ArrayList<>();
            completable.complete(req, out);

            out.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .distinct()
                    .sorted()
                    .forEach(s -> candidates.add(new Candidate(s)));
        }



    }




    public void run(Application application) {
        try(Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .jna(true)
                .build()) {

            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(new CommandNameCompleter(application))
                    .build();

            printAbove(reader, "REPL ready. Ctrl+C=cancel line, Ctrl+D=exit, type 'exit' to quit.");



            while (application.isStart()){
                String line;
                try {
                    line = reader.readLine("> ");

                }catch (UserInterruptException e){
                    continue;
                }catch (EndOfFileException e){
                    printAbove(reader, "bye");
                    application.stop();
                    break;
                }

                if (line == null) { // defensive
                    printAbove(reader, "bye");
                    application.stop();
                    break;
                }

                String trimmed = line.trim();

                if(trimmed.isEmpty()) continue;

                if("exit".equalsIgnoreCase(trimmed)){
                    printAbove(reader, "bye");
                    application.stop();
                    break;
                }

                try {
                    application.executeLine(trimmed);
                }catch (Exception e){
                    printAbove(reader, "executeLine Error" + e.getMessage());
                }
            }
        }catch (IOException ioException){
            throw new RuntimeException("Failed to init terminal", ioException);
        }
    }

    private static void printAbove(LineReader reader, String s) {
        reader.printAbove(s);
    }

}
