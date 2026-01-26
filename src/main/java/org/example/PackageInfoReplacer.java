package org.example;

public class PackageInfoReplacer {

    public enum Result { REPLACED, SECTION_NOT_FOUND }

    public static final class ReplaceResult {
        public final Result result;
        public final String content;
        public ReplaceResult(Result result, String content) {
            this.result = result;
            this.content = content;
        }
    }

    public ReplaceResult replace(String original, String includeFolderText, String includeText) {
        // 用 LF 做統一處理，避免 CRLF/LF 造成定位失敗
        String text = normalizeToLf(original);

        String includeFolderHeader = header("Include folder");
        String includeHeader = header("Include");
        String endHeader = header("End of file");

        ReplaceResult r1 = replaceBetweenHeaders(text, includeFolderHeader, includeHeader, includeFolderText);
        if (r1.result != Result.REPLACED) return new ReplaceResult(Result.SECTION_NOT_FOUND, original);

        ReplaceResult r2 = replaceBetweenHeaders(r1.content, includeHeader, endHeader, includeText);
        if (r2.result != Result.REPLACED) return new ReplaceResult(Result.SECTION_NOT_FOUND, original);

        // 寫回時維持原檔換行風格
        String out = restoreNewlinesLikeOriginal(original, r2.content);
        return new ReplaceResult(Result.REPLACED, out);
    }

    private ReplaceResult replaceBetweenHeaders(String textLf, String startHeaderLf, String endHeaderLf, String body) {
        int start = textLf.indexOf(startHeaderLf);
        int end = textLf.indexOf(endHeaderLf);
        if (start < 0 || end < 0 || end <= start) {
            return new ReplaceResult(Result.SECTION_NOT_FOUND, textLf);
        }

        int insertPos = start + startHeaderLf.length();

        String before = textLf.substring(0, insertPos);
        String after = textLf.substring(end);

        String bodyLf = normalizeToLf(body).trim();
        String insert = "\n\n" + (bodyLf.isEmpty() ? "" : bodyLf + "\n");

        return new ReplaceResult(Result.REPLACED, before + insert + after);
    }

    private String header(String title) {
        // 以你模板的章節格式組成 header（用 LF）
        return "/* ***************************************************************************************\n" +
                " * " + title + "\n" +
                " */";
    }

    private String normalizeToLf(String s) {
        return s.replace("\r\n", "\n").replace("\r", "\n");
    }

    private String restoreNewlinesLikeOriginal(String original, String lfText) {
        // 如果原檔包含 CRLF，就輸出 CRLF；否則輸出 LF
        if (original.contains("\r\n")) {
            return lfText.replace("\n", "\r\n");
        }
        return lfText;
    }
}
