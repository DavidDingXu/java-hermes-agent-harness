package com.ading.ai.hermes.tools.basic;

public final class UniqueTextEdit {

    private UniqueTextEdit() {
    }

    public static UniqueTextEditResult apply(String content, String expected, String replacement) {
        if (expected == null || expected.isEmpty()) {
            return UniqueTextEditResult.failure(content, "expected text must not be empty");
        }
        int first = content.indexOf(expected);
        if (first < 0) {
            return UniqueTextEditResult.failure(content, "expected text has 0 matches");
        }
        int second = content.indexOf(expected, first + expected.length());
        if (second >= 0) {
            int matches = 2;
            int cursor = second + expected.length();
            while ((cursor = content.indexOf(expected, cursor)) >= 0) {
                matches++;
                cursor += expected.length();
            }
            return UniqueTextEditResult.failure(content, "expected text has " + matches + " matches");
        }
        String updated = content.substring(0, first) + replacement + content.substring(first + expected.length());
        return UniqueTextEditResult.success(updated);
    }
}
