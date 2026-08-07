package com.dingxu.ai.hermes.context;

import com.dingxu.ai.hermes.core.AgentEvent;
import com.dingxu.ai.hermes.core.AgentEventKind;
import com.dingxu.ai.hermes.core.AgentState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ContextCompactor {

    private static final String SUMMARY_HEADER = """
            REFERENCE ONLY
            This summary preserves older runtime evidence. The latest user message wins when it conflicts with this summary.
            """;

    private final ContextCompactionPolicy policy;

    public ContextCompactor(ContextCompactionPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    public ContextCompactionResult compact(AgentState state) {
        Objects.requireNonNull(state, "state must not be null");
        int originalCharacters = countCharacters(state.events());
        if (originalCharacters <= policy.maxCharacters() || state.events().isEmpty()) {
            return new ContextCompactionResult(state, false, new ContextCompactionReport(
                    state.events().size(),
                    state.events().size(),
                    0,
                    originalCharacters,
                    originalCharacters
            ));
        }

        int headEnd = Math.min(policy.keepFirstEvents(), state.events().size());
        int tailStart = tailStart(state.events(), headEnd);
        if (tailStart <= headEnd) {
            return new ContextCompactionResult(state, false, new ContextCompactionReport(
                    state.events().size(),
                    state.events().size(),
                    0,
                    originalCharacters,
                    originalCharacters
            ));
        }

        List<AgentEvent> head = state.events().subList(0, headEnd);
        List<AgentEvent> middle = state.events().subList(headEnd, tailStart);
        List<AgentEvent> tail = state.events().subList(tailStart, state.events().size());

        List<AgentEvent> compactedEvents = new ArrayList<>();
        compactedEvents.addAll(head);
        compactedEvents.add(AgentEvent.contextSummary(buildSummary(middle)));
        compactedEvents.addAll(tail);

        AgentState compactedState = new AgentState(compactedEvents, state.turnsUsed());
        return new ContextCompactionResult(compactedState, true, new ContextCompactionReport(
                state.events().size(),
                compactedEvents.size(),
                middle.size(),
                originalCharacters,
                countCharacters(compactedEvents)
        ));
    }

    private int tailStart(List<AgentEvent> events, int headEnd) {
        int tailStart = Math.max(headEnd, events.size() - policy.keepLastEvents());
        if (tailStart < events.size() && isToolEvent(events.get(tailStart))) {
            while (tailStart > headEnd && isToolEvent(events.get(tailStart - 1))) {
                tailStart--;
            }
        }
        return tailStart;
    }

    private boolean isToolEvent(AgentEvent event) {
        return event.kind() == AgentEventKind.TOOL_REQUESTED
                || event.kind() == AgentEventKind.TOOL_OBSERVED;
    }

    private String buildSummary(List<AgentEvent> events) {
        StringBuilder summary = new StringBuilder(SUMMARY_HEADER);
        summary.append("Summarized events: ").append(events.size()).append('\n');
        for (int index = 0; index < events.size(); index++) {
            summary.append("- ").append(index + 1).append(". ").append(describe(events.get(index))).append('\n');
            if (summary.length() >= policy.summaryMaxCharacters()) {
                return truncate(summary.toString(), policy.summaryMaxCharacters());
            }
        }
        return truncate(summary.toString(), policy.summaryMaxCharacters());
    }

    private String describe(AgentEvent event) {
        return switch (event.kind()) {
            case USER_MESSAGE -> "user: " + preview(event.text());
            case CONTEXT_SUMMARY -> "context summary: " + preview(event.text());
            case ERROR_RECOVERED -> "error recovered: " + preview(event.text());
            case RUN_INTERRUPTED -> "run interrupted: " + preview(event.text());
            case MODEL_FINAL_ANSWER -> "model final answer: " + preview(event.text());
            case TOOL_REQUESTED -> "tool requested: "
                    + event.toolRequest().name()
                    + " "
                    + preview(String.valueOf(event.toolRequest().arguments()));
            case TOOL_OBSERVED -> "tool observed: "
                    + event.toolObservation().callId()
                    + " success="
                    + event.toolObservation().success()
                    + " "
                    + preview(event.toolObservation().content());
        };
    }

    private String preview(String text) {
        return truncate(text.replace('\n', ' '), policy.eventPreviewMaxCharacters());
    }

    private String truncate(String text, int maxCharacters) {
        if (text.length() <= maxCharacters) {
            return text;
        }
        if (maxCharacters <= 3) {
            return text.substring(0, maxCharacters);
        }
        return text.substring(0, maxCharacters - 3) + "...";
    }

    private int countCharacters(List<AgentEvent> events) {
        int characters = 0;
        for (AgentEvent event : events) {
            characters += describe(event).length();
        }
        return characters;
    }
}
