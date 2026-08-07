package com.dingxu.ai.hermes.model;

import com.dingxu.ai.hermes.core.ModelTurn;
import com.dingxu.ai.hermes.core.ToolRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;

public final class OpenAiCompatibleModelProvider implements ModelProvider {

    private static final String PROVIDER_NAME = "openai-compatible";

    private final OpenAiCompatibleOptions options;
    private final OpenAiHttpTransport transport;
    private final ObjectMapper objectMapper;
    private final ToolCallParser toolCallParser;

    public OpenAiCompatibleModelProvider(OpenAiCompatibleOptions options) {
        this(options, new JdkOpenAiHttpTransport(HttpClient.newHttpClient()), new ObjectMapper(), new ToolCallParser());
    }

    OpenAiCompatibleModelProvider(
            OpenAiCompatibleOptions options,
            OpenAiHttpTransport transport,
            ObjectMapper objectMapper,
            ToolCallParser toolCallParser
    ) {
        this.options = options;
        this.transport = transport;
        this.objectMapper = objectMapper;
        this.toolCallParser = toolCallParser;
    }

    @Override
    public ChatResponse complete(ChatRequest request) {
        try {
            OpenAiHttpResponse response = transport.send(new OpenAiHttpRequest(
                    options.chatCompletionsUri(),
                    options.timeout(),
                    "Bearer " + options.apiKey(),
                    toRequestJson(request)
            ));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ModelProviderException("model provider returned HTTP " + response.statusCode());
            }
            return parseResponse(response.body());
        } catch (IOException error) {
            throw new ModelProviderException("failed to call model provider", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ModelProviderException("model provider call was interrupted", error);
        }
    }

    String toRequestJson(ChatRequest request) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", request.options().model());
        root.put("temperature", request.options().temperature());

        ArrayNode messages = root.putArray("messages");
        for (ChatMessage message : request.messages()) {
            ObjectNode item = messages.addObject();
            item.put("role", toOpenAiRole(message.role()));
            if (!message.content().isBlank() || message.role() == ChatRole.TOOL) {
                item.put("content", message.content());
            }
            if (!message.toolRequests().isEmpty()) {
                ArrayNode toolCalls = item.putArray("tool_calls");
                for (ToolRequest toolRequest : message.toolRequests()) {
                    ObjectNode call = toolCalls.addObject();
                    call.put("id", toolRequest.callId());
                    call.put("type", "function");
                    ObjectNode function = call.putObject("function");
                    function.put("name", toolRequest.name());
                    function.put("arguments", objectMapper.writeValueAsString(toolRequest.arguments()));
                }
            }
            if (!message.toolCallId().isBlank()) {
                item.put("tool_call_id", message.toolCallId());
            }
        }

        if (!request.tools().isEmpty()) {
            ArrayNode tools = root.putArray("tools");
            for (ToolSpec tool : request.tools()) {
                ObjectNode toolNode = tools.addObject();
                toolNode.put("type", "function");
                ObjectNode function = toolNode.putObject("function");
                function.put("name", tool.name());
                function.put("description", tool.description());
                function.set("parameters", toParametersSchema(tool));
            }
        }

        return objectMapper.writeValueAsString(root);
    }

    ChatResponse parseResponse(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode choice = root.path("choices").path(0);
        if (choice.isMissingNode()) {
            throw new ModelProviderException("model provider response has no choices");
        }

        JsonNode message = choice.path("message");
        ModelTurn turn = parseModelTurn(message);
        Usage usage = parseUsage(root.path("usage"));
        return new ChatResponse(turn, usage, PROVIDER_NAME, reasoning(message));
    }

    private String reasoning(JsonNode message) {
        String reasoning = message.path("reasoning_content").asText("");
        if (reasoning.isBlank()) {
            reasoning = message.path("reasoning").asText("");
        }
        return reasoning;
    }

    private ModelTurn parseModelTurn(JsonNode message) {
        JsonNode toolCalls = message.path("tool_calls");
        if (toolCalls.isArray() && !toolCalls.isEmpty()) {
            ToolCallParseReport report = toolCallParser.parse(toRawToolCalls(toolCalls));
            if (!report.errors().isEmpty()) {
                throw new ModelProviderException("model provider returned malformed tool call arguments");
            }
            if (report.requests().isEmpty()) {
                throw new ModelProviderException("model provider returned no usable tool calls");
            }
            return ModelTurn.toolRequests(report.requests());
        }

        String content = message.path("content").asText("");
        if (content.isBlank()) {
            throw new ModelProviderException("model provider response has no content");
        }
        return ModelTurn.finalAnswer(content);
    }

    private List<RawToolCall> toRawToolCalls(JsonNode toolCalls) {
        List<RawToolCall> rawCalls = new ArrayList<>();
        for (JsonNode toolCall : toolCalls) {
            JsonNode function = toolCall.path("function");
            rawCalls.add(new RawToolCall(
                    toolCall.path("id").asText(""),
                    function.path("name").asText(""),
                    function.path("arguments").asText("{}")
            ));
        }
        return rawCalls;
    }

    private Usage parseUsage(JsonNode usage) {
        if (usage.isMissingNode() || usage.isNull()) {
            return Usage.empty();
        }
        return new Usage(
                usage.path("prompt_tokens").asInt(0),
                usage.path("completion_tokens").asInt(0)
        );
    }

    private ObjectNode toParametersSchema(ToolSpec tool) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");

        for (var entry : tool.parameters().entrySet()) {
            ObjectNode property = properties.putObject(entry.getKey());
            property.put("type", entry.getValue());
            required.add(entry.getKey());
        }

        return schema;
    }

    private String toOpenAiRole(ChatRole role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
        };
    }
}

@FunctionalInterface
interface OpenAiHttpTransport {

    OpenAiHttpResponse send(OpenAiHttpRequest request) throws IOException, InterruptedException;
}

record OpenAiHttpRequest(java.net.URI uri, java.time.Duration timeout, String authorizationHeader, String body) {
}

record OpenAiHttpResponse(int statusCode, String body) {
}

final class JdkOpenAiHttpTransport implements OpenAiHttpTransport {

    private final HttpClient httpClient;

    JdkOpenAiHttpTransport(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public OpenAiHttpResponse send(OpenAiHttpRequest request) throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder(request.uri())
                .timeout(request.timeout())
                .header("Authorization", request.authorizationHeader())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(request.body()))
                .build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        return new OpenAiHttpResponse(response.statusCode(), response.body());
    }
}
