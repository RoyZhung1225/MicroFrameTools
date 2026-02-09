package org.example.util.terminal;

import org.example.util.buffer.StringBuff;

import java.util.logging.Logger;
import java.util.*;


public class CommandComplete implements CommandHandler {

    @Override
    public String getDescription() {
        return ""; // 不建議出現在 help
    }

    @Override
    public boolean onCommand(StringBuff sb, Logger logger) {
        // completion 模式：不要印 logger，避免污染 stdout
        Parsed p = parse(sb);

        String line = p.line == null ? "" : p.line;
        int cursor = clamp(p.cursor, 0, line.length());

        // 只分析 cursor 左側（PowerShell 傳進來的 cursor 就是這個語意）
        String left = line.substring(0, cursor);

        // tokens：用你現有的 StringBuff 規則（空白分隔、支援 "..."）
        List<String> tokens = tokenize(left);

        // prefix：如果左側不是以空白結尾，prefix = 最後一個 token；否則 prefix=""
        String prefix = "";
        if (!left.isEmpty() && !Character.isWhitespace(left.charAt(left.length() - 1))) {
            if (!tokens.isEmpty()) prefix = tokens.get(tokens.size() - 1);
        }

        // commandAst.ToString() 通常包含 "mytool ..."，所以 tokens[0] 多半是程式名
        // 你要找「真正的 command」，用這個方法比較穩
        CompletionContext ctx = CompletionContext.fromTokens(tokens);

        // 產生候選
        List<Candidate> cands = CompletionEngine.complete(ctx, prefix);

        // 輸出：每行一個候選（只輸出文字，別加其他東西）
        for (Candidate c : cands) {
            System.out.println(c.text);
        }

        return true;
    }

    // ----------------- parsing -----------------

    private static final class Parsed {
        String line;
        int cursor;
    }

    private static Parsed parse(StringBuff sb) {
        Parsed p = new Parsed();
        p.cursor = Integer.MAX_VALUE;

        while (sb.remaining() > 0) {
            String t = sb.get();
            if (t == null) continue;

            if ("--line".equalsIgnoreCase(t) && sb.remaining() > 0) {
                p.line = sb.get();
                continue;
            }

            if ("--cursor".equalsIgnoreCase(t) && sb.remaining() > 0) {
                String v = sb.get();
                try {
                    p.cursor = Integer.parseInt(v);
                } catch (Throwable ignore) {
                }
            }
        }
        return p;
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    // 模擬你 Terminal tokenize 行為：空白切、雙引號包起來算一個 token（不含引號）
    private static List<String> tokenize(String s) {
        ArrayList<String> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;

        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;

        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);

            if (ch == '"') {
                inQuote = !inQuote;
                continue;
            }

            if (!inQuote && Character.isWhitespace(ch)) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                continue;
            }

            cur.append(ch);
        }

        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    // ----------------- completion engine (minimal) -----------------

    static final class Candidate {
        final String text;

        Candidate(String text) {
            this.text = text;
        }
    }

    static final class CompletionContext {
        final List<String> tokens;      // cursor 左側 tokens
        final String command;           // create/help/...（若沒有就空）
        final String activeCommand;     // 同 command，命名清楚
        final boolean expectOptionValueNamespace;

        private CompletionContext(List<String> tokens, String command, boolean expectNsValue) {
            this.tokens = tokens;
            this.command = command;
            this.activeCommand = command;
            this.expectOptionValueNamespace = expectNsValue;
        }

        static CompletionContext fromTokens(List<String> tokens) {
            // tokens[0] 通常是程式名 mytool
            String cmd = tokens.size() >= 2 ? tokens.get(1) : "";

            // 判斷是否在補 --namespace 的 value：最後一個「已完整輸入的 token」是 --namespace/--ns
            // 若 cursor 位在 value 開始前，left 會以空白結尾，此時 prefix=""
            String last = tokens.isEmpty() ? "" : tokens.get(tokens.size() - 1);
            boolean expectNsValue = "--namespace".equalsIgnoreCase(last) || "--ns".equalsIgnoreCase(last);

            return new CompletionContext(tokens, cmd, expectNsValue);
        }

        int createPositionalCount() {
            // create 語法：create [options] <type> <name> [namespace]
            // 這裡粗略計算「非 option 的 token」當作 positional（排除程式名與 command）
            // tokens: [mytool, create, ...]
            if (!"create".equalsIgnoreCase(command)) return 0;

            ArrayList<String> nonOpt = new ArrayList<>();
            for (int i = 2; i < tokens.size(); i++) {
                String t = tokens.get(i);
                if (t.startsWith("-")) {
                    // 跳過 option 與它的 value（這裡只處理 namespace 這種帶值選項）
                    if ("--namespace".equalsIgnoreCase(t) || "--ns".equalsIgnoreCase(t)) {
                        i++; // skip value if exists
                    }
                    continue;
                }
                nonOpt.add(t);
            }
            return nonOpt.size(); // 0:type 還沒, 1:已有 type, 2:已有 name, 3:已有 namespace
        }
    }

    static final class CompletionEngine {

        // 靜態字典（先跑起來；後續再接 workspace/config provider）
        private static final List<String> COMMANDS = List.of(
                "create", "reload", "setting", "info", "exit", "package"
        );
        private static final List<String> GLOBAL_OPTIONS = List.of(
                "--help", "-h"
        );
        private static final List<String> CREATE_OPTIONS = List.of(
                "--dry-run", "-n", "--force", "-f", "--namespace", "--ns"
        );
        private static final List<String> CREATE_TYPES = List.of(
                "class", "struct", "enum", "interface", "-c", "-s", "-e", "-i"
        );

        static List<Candidate> complete(CompletionContext ctx, String prefix) {
            boolean wantOptionsOnly = prefix != null && prefix.startsWith("-");

            // 1) 如果正在補 --namespace 的 value → 回 namespace 候選
            if ("create".equalsIgnoreCase(ctx.activeCommand) && ctx.expectOptionValueNamespace) {
                return filter(prefix, namespaceCandidates());
            }

            // 2) command 還沒選：補 commands 或 options（看 prefix）
            if (ctx.activeCommand == null || ctx.activeCommand.isBlank()) {
                if (wantOptionsOnly) {
                    return filter(prefix, concat(GLOBAL_OPTIONS));
                }
                return filter(prefix, concat(COMMANDS));
            }

            // 3) create：依位置補 type / namespace / options
            if ("create".equalsIgnoreCase(ctx.activeCommand)) {
                if (wantOptionsOnly) {
                    return filter(prefix, concat(CREATE_OPTIONS, GLOBAL_OPTIONS));
                }

                int pos = ctx.createPositionalCount();

                // pos==0 → 期待 type
                if (pos == 0) {
                    return filter(prefix, concat(CREATE_TYPES, CREATE_OPTIONS, GLOBAL_OPTIONS));
                }

                // pos==2 → 期待 namespace（type/name 都有了）
                if (pos >= 2) {
                    // 這裡讓 namespace + options 都可補
                    return filter(prefix, concat(namespaceCandidates(), CREATE_OPTIONS, GLOBAL_OPTIONS));
                }

                // pos==1（正在輸入 name）：通常不補，但仍可補 options
                return filter(prefix, concat(CREATE_OPTIONS, GLOBAL_OPTIONS));
            }

            // 4) 其他 command：先只補 global options
            if (wantOptionsOnly) return filter(prefix, concat(GLOBAL_OPTIONS));
            return filter(prefix, concat(GLOBAL_OPTIONS));
        }

        private static List<Candidate> filter(String prefix, List<String> raw) {
            String p = prefix == null ? "" : prefix;
            ArrayList<Candidate> out = new ArrayList<>();
            HashSet<String> seen = new HashSet<>();

            for (String s : raw) {
                if (s == null || s.isBlank()) continue;
                if (!p.isEmpty() && !s.startsWith(p)) continue;
                if (seen.add(s)) out.add(new Candidate(s));
            }

            out.sort(Comparator.comparing(a -> a.text));
            return out;
        }

        private static List<String> concat(List<String>... parts) {
            ArrayList<String> out = new ArrayList<>();
            for (List<String> p : parts) out.addAll(p);
            return out;
        }

        // 智慧補全第一步：namespace 候選（先用 config + 之後再加掃資料夾）
        private static List<String> namespaceCandidates() {
            ArrayList<String> out = new ArrayList<>();

            // config default namespace
            //String def = Application.getInstance().getGlobal().getConfig().getNamespace();
            String def = null;
            if (def != null && !def.isBlank()) out.add(def);

            // TODO：加 workspace 掃描（workFolder + config.path 下的資料夾 → a::b）
            // 先提供幾個常用 placeholder（你也可拿 config 設定）
            out.add("my::core");
            out.add("model::entity");

            return out;
        }
    }
}

