package org.example.completion;

public final class CompletionBootstrap {

    private CompletionBootstrap() {}

    public static void run(String[] args) {
        // 保險：關掉 java.util.logging（避免任何 handler 輸出）
        java.util.logging.LogManager.getLogManager().reset();

        String line = "";
        int cursor = Integer.MAX_VALUE;

        // args[0] 是 __complete，從 1 開始解析
        for (int i = 1; i < args.length; i++) {
            String t = args[i];

            if ("--line".equalsIgnoreCase(t) && i + 1 < args.length) {
                line = args[++i];
                continue;
            }

            if ("--cursor".equalsIgnoreCase(t) && i + 1 < args.length) {
                try { cursor = Integer.parseInt(args[++i]); } catch (Throwable ignore) {}
            }
        }

        // 先用純靜態候選驗證管線乾淨（之後再換成你的 CompletionEngine）
        for (String c : baseCandidates(line, cursor)) {
            System.out.println(c);
        }
    }

    private static java.util.List<String> baseCandidates(String line, int cursor) {
        // 先固定候選，確保 stdout 乾淨
        return java.util.List.of("create", "reload", "setting", "info", "package", "exit");
    }
}
