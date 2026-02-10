package org.example.completion;

import org.example.app.Application;
import org.example.cli_core.buffer.CommandHandler;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.ArrayList;
import java.util.List;

public class CommandNameCompleter implements Completer {
    Application application;

    public CommandNameCompleter(Application application) {

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