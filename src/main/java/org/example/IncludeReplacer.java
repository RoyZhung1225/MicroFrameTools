package org.example;


public class IncludeReplacer {

    private static final String INCLUDE_FOLDER_HEADER =
            "/* ***************************************************************************************\r\n" +
                    " * Include folder\r\n" +
                    " */";

    private static final String INCLUDE_HEADER =
            "/* ***************************************************************************************\r\n" +
                    " * Include\r\n" +
                    " */";

    private static final String END_HEADER =
            "/* ***************************************************************************************\r\n" +
                    " * End of file\r\n" +
                    " */";

    /**
     * 將 package-info.h 內容中的區塊替換為新產生的 include 清單
     */
    public String replace(String original, String includeFolderText, String includeText) {
        String result = original;

        // 替換 Include folder 區塊（到 Include 標題前）
        result = replaceBlockBetweenHeaders(
                result,
                INCLUDE_FOLDER_HEADER,
                INCLUDE_HEADER,
                includeFolderText
        );

        // 替換 Include 區塊（到 End of file 標題前）
        result = replaceBlockBetweenHeaders(
                result,
                INCLUDE_HEADER,
                END_HEADER,
                includeText
        );

        return result;
    }

    /**
     * 將 startHeader 與 endHeader 之間的內容替換為 newBody
     * 替換範圍：startHeader 結尾到 endHeader 開始之前
     */
    private String replaceBlockBetweenHeaders(String text, String startHeader, String endHeader, String newBody) {
        int start = text.indexOf(startHeader);
        int end = text.indexOf(endHeader);

        if (start < 0 || end < 0 || end <= start)
            return text;

        int insertPos = start + startHeader.length();
        String before = text.substring(0, insertPos);
        String after = text.substring(end);

        return before + "\r\n\r\n" + newBody + "\r\n" + after;
    }
}
