package com.ading.ai.hermes.model;

import com.ading.ai.hermes.core.ToolRequest;
import com.ading.ai.hermes.core.ModelTurnKind;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAiCompatibleModelProviderTest {

    @Test
    void sendsChatCompletionRequestAndParsesTextResponse() throws Exception {
        RecordingTransport transport = new RecordingTransport(new OpenAiHttpResponse(200, """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "README has been inspected."
                      }
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 12,
                    "completion_tokens": 5
                  }
                }
                """));
        OpenAiCompatibleModelProvider provider = provider(transport, "https://example.com");

        ChatResponse response = provider.complete(new ChatRequest(
                List.of(ChatMessage.system("You are a runtime."), ChatMessage.user("inspect README")),
                List.of(new ToolSpec("read_file", "Read a file", Map.of("path", "string"))),
                new ModelOptions("test-model", 0.0)
        ));

        assertEquals(URI.create("https://example.com/v1/chat/completions"), transport.request().uri());
        assertEquals(Duration.ofSeconds(5), transport.request().timeout());
        assertEquals("Bearer test-key", transport.request().authorizationHeader());
        var json = new ObjectMapper().readTree(transport.request().body());
        assertEquals("test-model", json.path("model").asText());
        assertEquals("system", json.path("messages").path(0).path("role").asText());
        assertEquals("read_file", json.path("tools").path(0).path("function").path("name").asText());
        assertEquals("string", json.path("tools").path(0).path("function")
                .path("parameters").path("properties").path("path").path("type").asText());
        assertEquals(ModelTurnKind.FINAL_ANSWER, response.turn().kind());
        assertEquals("README has been inspected.", response.turn().finalAnswer());
        assertEquals(new Usage(12, 5), response.usage());
        assertEquals("openai-compatible", response.provider());
    }

    @Test
    void serializesAssistantToolCallsAndMatchingToolResults() throws Exception {
        OpenAiCompatibleModelProvider provider = provider(
                new RecordingTransport(new OpenAiHttpResponse(200, """
                        {"choices":[{"message":{"role":"assistant","content":"done"}}]}
                        """)),
                "https://example.com"
        );
        ToolRequest request = new ToolRequest(
                "call-1", "read_file", Map.of("path", "README.md")
        );

        var json = new ObjectMapper().readTree(provider.toRequestJson(new ChatRequest(
                List.of(
                        ChatMessage.user("read README"),
                        ChatMessage.assistantToolCalls(List.of(request)),
                        ChatMessage.toolResult("call-1", "README content")
                ),
                List.of(),
                new ModelOptions("test-model", 0.0)
        )));

        var assistant = json.path("messages").path(1);
        assertEquals("assistant", assistant.path("role").asText());
        assertEquals("call-1", assistant.path("tool_calls").path(0).path("id").asText());
        assertEquals("read_file", assistant.path("tool_calls").path(0)
                .path("function").path("name").asText());
        assertEquals("README.md", new ObjectMapper().readTree(assistant.path("tool_calls").path(0)
                .path("function").path("arguments").asText()).path("path").asText());

        var tool = json.path("messages").path(2);
        assertEquals("tool", tool.path("role").asText());
        assertEquals("call-1", tool.path("tool_call_id").asText());
        assertEquals("README content", tool.path("content").asText());
    }

    @Test
    void parsesFunctionToolCallIntoToolRequest() throws Exception {
        OpenAiCompatibleModelProvider provider = new OpenAiCompatibleModelProvider(
                new OpenAiCompatibleOptions(URI.create("http://127.0.0.1:1"), "test-key", Duration.ofSeconds(5))
        );

        ChatResponse response = provider.parseResponse("""
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "tool_calls": [
                          {
                            "id": "call-1",
                            "type": "function",
                            "function": {
                              "name": "read_file",
                              "arguments": "{\\"path\\":\\"README.md\\"}"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
                """);

        assertEquals(ModelTurnKind.TOOL_REQUEST, response.turn().kind());
        assertEquals("call-1", response.turn().toolRequest().callId());
        assertEquals("read_file", response.turn().toolRequest().name());
        assertEquals("README.md", response.turn().toolRequest().arguments().get("path"));
    }

    @Test
    void preservesEveryToolCallReturnedInOneAssistantTurn() throws Exception {
        OpenAiCompatibleModelProvider provider = new OpenAiCompatibleModelProvider(
                new OpenAiCompatibleOptions(URI.create("http://127.0.0.1:1"), "test-key", Duration.ofSeconds(5))
        );

        ChatResponse response = provider.parseResponse("""
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "tool_calls": [
                        {
                          "id": "call-1",
                          "type": "function",
                          "function": {"name": "read_file", "arguments": "{\\\"path\\\":\\\"README.md\\\"}"}
                        },
                        {
                          "id": "call-2",
                          "type": "function",
                          "function": {"name": "read_file", "arguments": "{\\\"path\\\":\\\"pom.xml\\\"}"}
                        }
                      ]
                    }
                  }]
                }
                """);

        assertEquals(List.of("call-1", "call-2"), response.turn().toolRequests().stream()
                .map(request -> request.callId())
                .toList());
    }

    @Test
    void preservesProviderReasoningAsSeparateResponseEvidence() throws Exception {
        OpenAiCompatibleModelProvider provider = new OpenAiCompatibleModelProvider(
                new OpenAiCompatibleOptions(URI.create("http://127.0.0.1:1"), "test-key", Duration.ofSeconds(5))
        );

        ChatResponse response = provider.parseResponse("""
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "Use the focused test.",
                        "reasoning_content": "The full suite is unnecessary for this change."
                      }
                    }
                  ]
                }
                """);

        assertEquals("Use the focused test.", response.turn().finalAnswer());
        assertEquals("The full suite is unnecessary for this change.", response.reasoning());
    }

    @Test
    void failsFastOnProviderHttpError() throws Exception {
        OpenAiCompatibleModelProvider provider = provider(
                new RecordingTransport(new OpenAiHttpResponse(429, "{\"error\":{\"message\":\"rate limited\"}}")),
                "https://example.com"
        );

        ModelProviderException error = assertThrows(ModelProviderException.class, () -> provider.complete(
                new ChatRequest(
                        List.of(ChatMessage.user("hello")),
                        List.of(),
                        new ModelOptions("test-model", 0.0)
                )
        ));

        assertEquals("model provider returned HTTP 429", error.getMessage());
    }

    private OpenAiCompatibleModelProvider provider(RecordingTransport transport, String baseUrl) {
        return new OpenAiCompatibleModelProvider(
                new OpenAiCompatibleOptions(URI.create(baseUrl), "test-key", Duration.ofSeconds(5)),
                transport,
                new ObjectMapper(),
                new ToolCallParser()
        );
    }

    private static final class RecordingTransport implements OpenAiHttpTransport {

        private final OpenAiHttpResponse response;
        private final AtomicReference<OpenAiHttpRequest> request = new AtomicReference<>();

        private RecordingTransport(OpenAiHttpResponse response) {
            this.response = response;
        }

        @Override
        public OpenAiHttpResponse send(OpenAiHttpRequest request) throws IOException {
            this.request.set(request);
            return response;
        }

        private OpenAiHttpRequest request() {
            return request.get();
        }
    }
}
