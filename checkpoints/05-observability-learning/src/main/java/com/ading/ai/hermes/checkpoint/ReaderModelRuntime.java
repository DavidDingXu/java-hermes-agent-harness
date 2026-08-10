package com.ading.ai.hermes.checkpoint;

import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.AgentEventKind;
import com.ading.ai.hermes.core.AgentLoop;
import com.ading.ai.hermes.core.AgentRunRequest;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.core.ErrorRecoveringAgentLoop;
import com.ading.ai.hermes.core.ErrorRecoveryPolicy;
import com.ading.ai.hermes.core.IterationBudget;
import com.ading.ai.hermes.core.ToolDriver;
import com.ading.ai.hermes.core.ToolRequest;
import com.ading.ai.hermes.model.ChatMessage;
import com.ading.ai.hermes.model.ChatRequest;
import com.ading.ai.hermes.model.ModelOptions;
import com.ading.ai.hermes.model.ModelProvider;
import com.ading.ai.hermes.model.ModelProviderDriver;
import com.ading.ai.hermes.model.OpenAiCompatibleModelProvider;
import com.ading.ai.hermes.model.OpenAiCompatibleOptions;
import com.ading.ai.hermes.model.ToolSpec;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

final class ReaderModelRuntime {

    private static final Path LOCAL_CONFIGURATION = Path.of("config", "hermes.local.properties");
    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是 Hermes Agent Harness 的阶段验收模型。
            必须遵守工具 Schema；需要文件证据时先调用工具，看到 Observation 后再回答。
            最终回答使用简洁中文，不编造没有观察到的事实。
            """;

    private final ModelProvider provider;
    private final ModelOptions options;
    private final Path configurationFile;

    private ReaderModelRuntime(ModelProvider provider, ModelOptions options, Path configurationFile) {
        this.provider = provider;
        this.options = options;
        this.configurationFile = configurationFile;
    }

    static ReaderModelRuntime fromLocalConfiguration() {
        Path file = findConfigurationFile();
        Properties properties = read(file);
        String baseUrl = required(properties, "openai.base-url", file);
        String apiKey = required(properties, "openai.api-key", file);
        String model = required(properties, "openai.model", file);
        rejectPlaceholder(baseUrl, "openai.base-url", file);
        rejectPlaceholder(apiKey, "openai.api-key", file);
        rejectPlaceholder(model, "openai.model", file);
        return new ReaderModelRuntime(
                new OpenAiCompatibleModelProvider(OpenAiCompatibleOptions.of(baseUrl, apiKey)),
                new ModelOptions(model, 0.0),
                file
        );
    }

    AgentRunResult runText(String task) {
        return run(DEFAULT_SYSTEM_PROMPT, task, request ->
                com.ading.ai.hermes.core.ToolObservation.failure(
                        request.callId(), "当前阶段没有向模型开放工具"
                ), List.of(), 4);
    }

    AgentRunResult run(String task, ToolDriver tools, List<ToolSpec> specs, int maxTurns) {
        return run(DEFAULT_SYSTEM_PROMPT, task, tools, specs, maxTurns);
    }

    AgentRunResult run(
            String systemPrompt,
            String task,
            ToolDriver tools,
            List<ToolSpec> specs,
            int maxTurns
    ) {
        return run(systemPrompt, task, tools, specs, maxTurns, new AgentState(List.of(), 0));
    }

    AgentRunResult run(
            String systemPrompt,
            String task,
            ToolDriver tools,
            List<ToolSpec> specs,
            int maxTurns,
            AgentState history
    ) {
        ModelProviderDriver model = new ModelProviderDriver(
                provider,
                state -> new ChatRequest(toMessages(systemPrompt, state), specs, options)
        );
        return new AgentLoop(model, tools).run(
                AgentRunRequest.start(task, IterationBudget.maxTurns(maxTurns)),
                history
        );
    }

    AgentRunResult runRecovering(
            ModelProvider executionProvider,
            String systemPrompt,
            String task,
            ToolDriver tools,
            List<ToolSpec> specs,
            int maxTurns
    ) {
        ModelProviderDriver model = new ModelProviderDriver(
                executionProvider,
                state -> new ChatRequest(toMessages(systemPrompt, state), specs, options)
        );
        return new ErrorRecoveringAgentLoop(
                model,
                tools,
                ErrorRecoveryPolicy.maxRecoveries(2)
        ).run(AgentRunRequest.start(task, IterationBudget.maxTurns(maxTurns)));
    }

    ModelProvider provider() {
        return provider;
    }

    ModelOptions options() {
        return options;
    }

    String model() {
        return options.model();
    }

    Path configurationFile() {
        return configurationFile;
    }

    static String preview(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 180) + "...";
    }

    private static List<ChatMessage> toMessages(String systemPrompt, AgentState state) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt));
        List<ToolRequest> pendingToolRequests = new ArrayList<>();
        for (AgentEvent event : state.events()) {
            if (event.kind() == AgentEventKind.TOOL_REQUESTED) {
                pendingToolRequests.add(event.toolRequest());
                continue;
            }
            flushToolRequests(messages, pendingToolRequests);
            switch (event.kind()) {
                case USER_MESSAGE -> messages.add(ChatMessage.user(event.text()));
                case CONTEXT_SUMMARY, ERROR_RECOVERED, COMPLETION_REJECTED ->
                        messages.add(ChatMessage.system(event.text()));
                case MODEL_FINAL_ANSWER -> messages.add(ChatMessage.assistant(event.text()));
                case TOOL_OBSERVED -> messages.add(ChatMessage.toolResult(
                        event.toolObservation().callId(),
                        event.toolObservation().content()
                ));
                case RUN_INTERRUPTED, TOOL_REQUESTED -> {
                    // 中断事件不会进入新的模型请求，Tool Request 已在上方合并。
                }
            }
        }
        flushToolRequests(messages, pendingToolRequests);
        return List.copyOf(messages);
    }

    private static void flushToolRequests(List<ChatMessage> messages, List<ToolRequest> pending) {
        if (!pending.isEmpty()) {
            messages.add(ChatMessage.assistantToolCalls(List.copyOf(pending)));
            pending.clear();
        }
    }

    private static Path findConfigurationFile() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(LOCAL_CONFIGURATION);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException(
                "未找到本地模型配置。请在项目 config/hermes.local.properties 中填写 "
                        + "openai.base-url、openai.api-key 和 openai.model 后重新运行当前 Main。"
        );
    }

    private static Properties read(Path file) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (Exception error) {
            throw new IllegalStateException("读取本地模型配置失败: " + file, error);
        }
    }

    private static String required(Properties properties, String name, Path file) {
        String value = properties.getProperty(name, "").trim();
        if (value.isBlank()) {
            throw new IllegalStateException("本地模型配置缺少 " + name + ": " + file);
        }
        return value;
    }

    private static void rejectPlaceholder(String value, String name, Path file) {
        String normalized = value.toLowerCase();
        if (normalized.contains("replace-with")
                || normalized.contains("your-openai")
                || normalized.contains("你的_api")) {
            throw new IllegalStateException("请把示例值替换为真实的 " + name + ": " + file);
        }
    }
}
