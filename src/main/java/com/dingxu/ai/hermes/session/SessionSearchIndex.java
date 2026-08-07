package com.dingxu.ai.hermes.session;

import com.dingxu.ai.hermes.core.AgentEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class SessionSearchIndex {

    private final List<SessionRecord> records = new ArrayList<>();

    public void add(SessionRecord record) {
        records.add(Objects.requireNonNull(record, "record must not be null"));
    }

    public List<SessionSearchHit> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }

        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        List<SessionSearchHit> hits = new ArrayList<>();
        for (SessionRecord record : records) {
            for (int index = 0; index < record.events().size(); index++) {
                AgentEvent event = record.events().get(index);
                String searchableText = searchableText(event);
                if (searchableText.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                    hits.add(new SessionSearchHit(record.sessionId(), index, event.kind(), searchableText));
                    if (hits.size() == limit) {
                        return List.copyOf(hits);
                    }
                }
            }
        }
        return List.copyOf(hits);
    }

    private String searchableText(AgentEvent event) {
        return switch (event.kind()) {
            case USER_MESSAGE, CONTEXT_SUMMARY, ERROR_RECOVERED, RUN_INTERRUPTED, MODEL_FINAL_ANSWER -> event.text();
            case TOOL_REQUESTED -> "tool "
                    + event.toolRequest().name()
                    + " "
                    + event.toolRequest().arguments();
            case TOOL_OBSERVED -> "tool result "
                    + event.toolObservation().callId()
                    + " success="
                    + event.toolObservation().success()
                    + " "
                    + event.toolObservation().content();
        };
    }
}
