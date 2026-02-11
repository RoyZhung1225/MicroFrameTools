package org.example.cli_core.buffer;

import java.util.List;

public interface HelpableCommand {
    HelpDoc help();
    record HelpDoc(
            String summary,          // 一句話（列在 help 總覽）
            String usage,            // usage block
            List<String> options,    // 每行一個 option 描述
            List<String> examples,   // examples
            String detail            // 其他補充（可空）
    ) {}
}
