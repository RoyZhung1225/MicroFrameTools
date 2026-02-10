package org.example.completion;

import java.util.List;

public interface CompletableCommand {
     void complete(CompletionRequest req, List<String> out);
}
