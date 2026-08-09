package com.ading.ai.hermes.skill;

import com.ading.ai.hermes.memory.MemoryCandidate;
import com.ading.ai.hermes.observability.TraceEvent;
import com.ading.ai.hermes.observability.TraceEventKind;
import com.ading.ai.hermes.observability.TrajectoryRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class TrajectorySelfImprovementReviewer {

    private final SkillCandidateGenerator skillCandidateGenerator;

    public TrajectorySelfImprovementReviewer(SkillCandidateGenerator skillCandidateGenerator) {
        this.skillCandidateGenerator = Objects.requireNonNull(
                skillCandidateGenerator,
                "skillCandidateGenerator must not be null"
        );
    }

    public SelfImprovementReview review(TrajectoryRecord trajectory) {
        Objects.requireNonNull(trajectory, "trajectory must not be null");
        List<TraceEvent> events = trajectory.events();
        List<MemoryCandidate> memoryCandidates = memoryCandidates(events);
        TaskReview taskReview = taskReview(trajectory.sessionId(), events);
        Optional<SkillCandidate> skillCandidate = taskReview == null
                ? Optional.empty()
                : skillCandidateGenerator.generate(taskReview);
        return new SelfImprovementReview(
                trajectory.sessionId(),
                taskReview != null && taskReview.recoveredFailure(),
                memoryCandidates,
                skillCandidate
        );
    }

    private List<MemoryCandidate> memoryCandidates(List<TraceEvent> events) {
        List<MemoryCandidate> candidates = new ArrayList<>();
        for (TraceEvent event : events) {
            if (event.kind() != TraceEventKind.USER_MESSAGE) {
                continue;
            }
            String text = attr(event, "text");
            if (looksLikeUserPreference(text)) {
                candidates.add(MemoryCandidate.fromUserText(text));
            }
        }
        return candidates;
    }

    private TaskReview taskReview(String sessionId, List<TraceEvent> events) {
        String task = firstUserMessage(events);
        List<String> failureSignals = failureSignals(events);
        List<String> successfulSteps = successfulSteps(events);
        boolean finished = finished(events);

        if (!finished || failureSignals.isEmpty() || successfulSteps.size() < 2) {
            return null;
        }

        return new TaskReview(
                sessionId,
                task,
                true,
                successfulSteps,
                failureSignals,
                List.of("Reuse only when the new task has the same failure signal and tool boundary.")
        );
    }

    private String firstUserMessage(List<TraceEvent> events) {
        for (TraceEvent event : events) {
            if (event.kind() == TraceEventKind.USER_MESSAGE) {
                String text = attr(event, "text");
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "Recovered task";
    }

    private List<String> failureSignals(List<TraceEvent> events) {
        List<String> signals = new ArrayList<>();
        for (TraceEvent event : events) {
            if (event.kind() == TraceEventKind.ERROR_RECOVERED) {
                String message = attr(event, "message");
                if (!message.isBlank()) {
                    signals.add(message);
                }
            }
        }
        return signals;
    }

    private List<String> successfulSteps(List<TraceEvent> events) {
        List<String> steps = new ArrayList<>();
        for (TraceEvent event : events) {
            if (event.kind() == TraceEventKind.TOOL_REQUESTED) {
                String toolName = attr(event, "toolName");
                String arguments = attr(event, "arguments");
                if (!toolName.isBlank()) {
                    steps.add(arguments.isBlank() ? "Run tool " + toolName + "." : "Run tool " + toolName + " with " + arguments + ".");
                }
            }
            if (event.kind() == TraceEventKind.TOOL_OBSERVED && "true".equalsIgnoreCase(attr(event, "success"))) {
                String content = attr(event, "content");
                if (!content.isBlank()) {
                    steps.add("Use successful observation: " + content);
                }
            }
        }
        return steps;
    }

    private boolean finished(List<TraceEvent> events) {
        for (TraceEvent event : events) {
            if (event.kind() == TraceEventKind.RUN_FINISHED
                    && "FINAL_ANSWER".equals(attr(event, "finishReason"))) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeUserPreference(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return text.contains("希望你先给结论")
                || lower.contains("prefer")
                || lower.contains("remember that i like")
                || lower.contains("don't format")
                || lower.contains("stop doing");
    }

    private String attr(TraceEvent event, String key) {
        Map<String, String> attributes = event.attributes();
        return attributes.getOrDefault(key, "").trim();
    }
}
