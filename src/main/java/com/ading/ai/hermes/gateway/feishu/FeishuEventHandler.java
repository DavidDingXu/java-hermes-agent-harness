package com.ading.ai.hermes.gateway.feishu;

import com.ading.ai.hermes.core.AgentRunRequest;
import com.ading.ai.hermes.core.AgentRuntime;
import com.ading.ai.hermes.core.IterationBudget;
import com.ading.ai.hermes.control.AdmissionDecision;
import com.ading.ai.hermes.control.NewWorkPolicy;
import com.ading.ai.hermes.gateway.GatewayAccessPolicy;
import com.ading.ai.hermes.gateway.GatewayAuthorizationPolicy;
import com.ading.ai.hermes.gateway.GatewayIdentity;
import com.ading.ai.hermes.gateway.GatewaySessionRouter;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class FeishuEventHandler {

    private final AgentRuntime runtime;
    private final FeishuReplySink replySink;
    private final NewWorkPolicy newWorkPolicy;
    private final GatewayAuthorizationPolicy authorizationPolicy;
    private final GatewaySessionRouter sessionRouter;
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    public FeishuEventHandler(AgentRuntime runtime, FeishuReplySink replySink) {
        this(runtime, replySink, NewWorkPolicy.allowAll(), GatewayAccessPolicy.channelAllowAll());
    }

    public FeishuEventHandler(
            AgentRuntime runtime,
            FeishuReplySink replySink,
            NewWorkPolicy newWorkPolicy
    ) {
        this(runtime, replySink, newWorkPolicy, GatewayAccessPolicy.channelAllowAll());
    }

    public FeishuEventHandler(
            AgentRuntime runtime,
            FeishuReplySink replySink,
            NewWorkPolicy newWorkPolicy,
            GatewayAuthorizationPolicy authorizationPolicy
    ) {
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
        this.replySink = Objects.requireNonNull(replySink, "replySink must not be null");
        this.newWorkPolicy = Objects.requireNonNull(newWorkPolicy, "newWorkPolicy must not be null");
        this.authorizationPolicy = Objects.requireNonNull(
                authorizationPolicy,
                "authorizationPolicy must not be null"
        );
        this.sessionRouter = new GatewaySessionRouter();
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
        GatewayIdentity identity = new GatewayIdentity(
                "feishu",
                event.chatType(),
                event.chatId(),
                event.senderId()
        );
        var authorization = Objects.requireNonNull(
                authorizationPolicy.evaluate(identity),
                "authorizationPolicy result must not be null"
        );
        if (!authorization.allowed()) {
            return FeishuHandleResult.rejected(authorization.reason());
        }
        AdmissionDecision admission = Objects.requireNonNull(
                newWorkPolicy.evaluate(),
                "newWorkPolicy result must not be null"
        );
        if (!admission.allowed()) {
            return FeishuHandleResult.rejected(admission.reason());
        }
        if (!processedEventIds.add(event.eventId())) {
            return FeishuHandleResult.duplicate();
        }

        try {
            String sessionKey = sessionRouter.sessionKey(identity);
            var result = runtime.run(AgentRunRequest.from(
                    "feishu",
                    sessionKey,
                    event.text(),
                    IterationBudget.maxTurns(8),
                    Map.of(
                            "eventId", event.eventId(),
                            "senderId", event.senderId(),
                            "chatType", event.chatType(),
                            "sessionKey", sessionKey
                    )
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
        if (event.chatType().isBlank()) {
            return "chatType must not be blank";
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
