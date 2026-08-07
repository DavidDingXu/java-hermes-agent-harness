package com.ading.ai.hermes.skill;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.StringJoiner;

public final class SkillCandidateGenerator {

    public Optional<SkillCandidate> generate(TaskReview review) {
        if (!review.hasReusableWorkflow()) {
            return Optional.empty();
        }

        String name = skillName(review.task());
        String description = "Recovered workflow for " + normalizeDescriptionSubject(review.task());
        List<String> triggers = triggers(review.task());
        String instructions = instructions(review);
        return Optional.of(new SkillCandidate(
                name,
                description,
                triggers,
                instructions,
                SkillProvenance.fromContent(
                        SkillSourceKind.AGENT_CREATED,
                        "review/" + review.sessionId(),
                        name,
                        "candidate",
                        instructions
                )
        ));
    }

    private String skillName(String task) {
        String normalized = task.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (normalized.startsWith("fix-")) {
            normalized = normalized.substring(4);
        }
        if (reviewedFailureName(normalized)) {
            normalized = normalized.substring(0, normalized.length() - "-failure".length()) + "-recovery";
        }
        if (normalized.isBlank()) {
            return "task-recovery";
        }
        return normalized;
    }

    private boolean reviewedFailureName(String normalized) {
        return normalized.endsWith("-failure");
    }

    private String normalizeDescriptionSubject(String task) {
        String text = task.trim();
        if (text.regionMatches(true, 0, "fix ", 0, 4)) {
            text = text.substring(4);
        }
        if (text.isBlank()) {
            return "task";
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private List<String> triggers(String task) {
        String normalized = task.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        List<String> words = normalized.isBlank() ? List.of("task") : List.of(normalized.split("\\s+"));
        return words.stream()
                .filter(word -> !"fix".equals(word))
                .map(word -> "failure".equals(word) ? "recovery" : word)
                .distinct()
                .toList();
    }

    private String instructions(TaskReview review) {
        StringJoiner text = new StringJoiner("\n");
        text.add("## Working Path");
        for (String step : review.successfulSteps()) {
            text.add("- " + step);
        }
        if (!review.failureSignals().isEmpty()) {
            text.add("");
            text.add("## Failure Signals");
            for (String signal : review.failureSignals()) {
                text.add("- " + signal);
            }
        }
        text.add("");
        text.add("## Reuse Boundary");
        for (String lesson : review.reusableLessons()) {
            text.add("- " + lesson);
        }
        return text.toString();
    }
}
