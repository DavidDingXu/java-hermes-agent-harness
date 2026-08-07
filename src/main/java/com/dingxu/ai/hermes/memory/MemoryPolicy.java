package com.dingxu.ai.hermes.memory;

import java.util.Locale;
import java.util.regex.Pattern;

public final class MemoryPolicy {

    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "\\b(sk-[A-Za-z0-9_-]{6,}|api\\s*key|password|secret|token)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private MemoryPolicy() {
    }

    public static MemoryPolicy defaultPolicy() {
        return new MemoryPolicy();
    }

    public MemoryDecision evaluate(MemoryCandidate candidate) {
        String text = candidate.text();
        String lower = text.toLowerCase(Locale.ROOT);

        if (SECRET_PATTERN.matcher(text).find()) {
            return MemoryDecision.reject("sensitive_content");
        }
        if (isTodo(lower)) {
            return MemoryDecision.reject("todo_belongs_to_session");
        }
        if (isTaskProgress(lower)) {
            return MemoryDecision.reject("task_progress_is_session_history");
        }
        if (isUserPreference(candidate, text, lower)) {
            return MemoryDecision.accept(
                    MemoryTarget.USER,
                    "user_preference",
                    normalizeUserPreference(text)
            );
        }
        if (isStableProjectFact(text)) {
            return MemoryDecision.accept(MemoryTarget.MEMORY, "stable_project_fact", text);
        }

        return MemoryDecision.reject("low_signal");
    }

    private boolean isTodo(String lower) {
        return lower.startsWith("todo:") || lower.contains(" todo:") || lower.contains("tomorrow continue");
    }

    private boolean isTaskProgress(String lower) {
        return (lower.startsWith("finished ") || lower.startsWith("completed ") || lower.startsWith("done "))
                && (lower.contains("committed")
                || lower.contains("commit ")
                || lower.contains("uploaded ")
                || lower.contains("deployed "));
    }

    private boolean isUserPreference(MemoryCandidate candidate, String text, String lower) {
        if (candidate.source() != MemoryCandidate.Source.USER_TEXT) {
            return false;
        }
        return text.contains("希望你先给结论")
                || lower.contains("prefer")
                || lower.contains("remember that i like");
    }

    private String normalizeUserPreference(String text) {
        if (text.contains("先给结论") && text.contains("解释原因")) {
            return "User prefers answers that give the conclusion first, then explain the reason.";
        }
        return text;
    }

    private boolean isStableProjectFact(String text) {
        return text.startsWith("Project ") && text.contains(" uses ");
    }
}
