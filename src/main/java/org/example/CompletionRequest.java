package org.example;


import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CompletionRequest {
    private String commandName;
    private List<String> tokens;
    private int wordIndex;
    private String prefix;
    private String workDir;
}
