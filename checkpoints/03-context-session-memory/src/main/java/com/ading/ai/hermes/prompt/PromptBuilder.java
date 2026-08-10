package com.ading.ai.hermes.prompt;

import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.model.ChatMessage;
import com.ading.ai.hermes.model.ChatRequest;
import com.ading.ai.hermes.model.ChatRequestFactory;
import com.ading.ai.hermes.model.ModelOptions;
import com.ading.ai.hermes.model.ToolSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PromptBuilder implements ChatRequestFactory {

    private final PromptPlan plan;
    private final List<ToolSpec> tools;
    private final ModelOptions options;

    public PromptBuilder(PromptPolicy policy, List<ToolSpec> tools, ModelOptions options) {
        this(PromptPlan.fromPolicy(Objects.requireNonNull(policy, "policy must not be null")), tools, options);
    }

    public PromptBuilder(PromptPlan plan, List<ToolSpec> tools, ModelOptions options) {
        this.plan = Objects.requireNonNull(plan, "plan must not be null");
        this.tools = List.copyOf(tools);
        this.options = Objects.requireNonNull(options, "options must not be null");
    }

    @Override
    public ChatRequest create(AgentState state) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(plan.systemPrompt()));
        for (int index = 0; index < state.events().size(); index++) {
            AgentEvent event = state.events().get(index);
            if (event.kind() == com.ading.ai.hermes.core.AgentEventKind.TOOL_REQUESTED) {
                List<com.ading.ai.hermes.core.ToolRequest> requests = new ArrayList<>();
                while (index < state.events().size()
                        && state.events().get(index).kind()
                        == com.ading.ai.hermes.core.AgentEventKind.TOOL_REQUESTED) {
                    requests.add(state.events().get(index).toolRequest());
                    index++;
                }
                index--;
                messages.add(ChatMessage.assistantToolCalls(requests));
            } else {
                messages.add(toMessage(event));
            }
        }
        return new ChatRequest(messages, tools, options, plan.cacheDescriptor());
    }

    private ChatMessage toMessage(AgentEvent event) {
        return switch (event.kind()) {
            case USER_MESSAGE -> ChatMessage.user(event.text());
            case CONTEXT_SUMMARY -> ChatMessage.system("context summary\n" + event.text());
            case ERROR_RECOVERED -> ChatMessage.system("error recovered\n" + event.text());
            case RUN_INTERRUPTED -> ChatMessage.system("run interrupted\n" + event.text());
            case MODEL_FINAL_ANSWER -> ChatMessage.assistant(event.text());
            case TOOL_REQUESTED -> throw new IllegalStateException("tool requests must be grouped");
            case TOOL_OBSERVED -> ChatMessage.toolResult(
                    event.toolObservation().callId(),
                    event.toolObservation().success()
                            ? event.toolObservation().content()
                            : "tool request failed: " + event.toolObservation().content()
            );
        };
    }
}
