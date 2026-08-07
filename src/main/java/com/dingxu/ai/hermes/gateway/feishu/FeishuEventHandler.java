package com.dingxu.ai.hermes.gateway.feishu;

import com.dingxu.ai.hermes.core.AgentRunRequest;
import com.dingxu.ai.hermes.core.AgentRuntime;
import com.dingxu.ai.hermes.core.IterationBudget;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class FeishuEventHandler {

    private final AgentRuntime runtime;
    private final FeishuReplySink replySink;
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    public FeishuEventHandler(AgentRuntime runtime, FeishuReplySink replySink) {
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
        this.replySink = Objects.requireNonNull(replySink, "replySink must not be null");
    }

    public FeishuHandleResult handle(FeishuEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        if (event.kind() == FeishuEventKind.CHALLENGE) {
            if (event.challenge().isBlank()) {
                return FeishuHandleResult.rejected("challenge must not be blank");
            }
            return FeishuHandleResult.challenge(event.challenge());
        }
        String validationError = validateTextEvent(event);
        if (!validationError.isBlank()) {
            return FeishuHandleResult.rejected(validationError);
        }
        if (!processedEventIds.add(event.eventId())) {
            return FeishuHandleResult.duplicate();
        }

        try {
            var result = runtime.run(AgentRunRequest.from(
                    "feishu",
                    event.chatId(),
                    event.text(),
                    IterationBudget.maxTurns(8),
                    Map.of("eventId", event.eventId(), "senderId", event.senderId())
            ));
            replySink.send(new FeishuReply(event.chatId(), result.finalAnswer()));
            return FeishuHandleResult.processed();
        } catch (RuntimeException error) {
            processedEventIds.remove(event.eventId());
            throw error;
        }
    }

    private static String validateTextEvent(FeishuEvent event) {
        if (event.eventId().isBlank()) {
            return "eventId must not be blank";
        }
        if (event.chatId().isBlank()) {
            return "chatId must not be blank";
        }
        if (event.senderId().isBlank()) {
            return "senderId must not be blank";
        }
        if (event.text().isBlank()) {
            return "text must not be blank";
        }
        return "";
    }
}
