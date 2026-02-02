package org.example;

import org.jline.reader.Candidate;

import java.util.List;

public interface CompletableCommand {
     void complete(CompletionRequest req, List<String> out);
}
