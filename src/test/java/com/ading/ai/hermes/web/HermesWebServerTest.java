package com.ading.ai.hermes.web;

import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.core.FinishReason;
import com.ading.ai.hermes.core.ToolObservation;
import com.ading.ai.hermes.core.ToolRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HermesWebServerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @TempDir
    Path workspace;

    private HermesWebServer server;
    private URI baseUri;
    private AtomicReference<WebRuntimeConfig> configured;
    private AtomicReference<WebRuntimeSettings> configuredSettings;

    @BeforeEach
    void startServer() {
        configured = new AtomicReference<>();
        configuredSettings = new AtomicReference<>();
        server = new HermesWebServer(
                new InetSocketAddress("127.0.0.1", 0),
                workspace,
                null,
                (config, settings) -> {
                    configured.set(config);
                    configuredSettings.set(settings);
                    return request -> new AgentRunResult(
                            FinishReason.FINAL_ANSWER,
                            "README inspected",
                            new AgentState(List.of(
                                    AgentEvent.userMessage(request.userMessage()),
                                    AgentEvent.toolRequested(new ToolRequest(
                                            "call-1", "read_file", Map.of("path", "README.md")
                                    )),
                                    AgentEvent.toolObserved(ToolObservation.success(
                                            "call-1", "# Hermes"
                                    )),
                                    AgentEvent.modelFinalAnswer("README inspected")
                            ), 2)
                    );
                }
        );
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.port());
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void servesTheConsoleAndReportsUnconfiguredState() throws Exception {
        HttpResponse<String> page = get("/");
        HttpResponse<String> config = get("/api/config");
        JsonNode runtimeConfig = objectMapper.readTree(get("/api/runtime-config").body());

        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("Hermes Web Console"));
        assertEquals(200, config.statusCode());
        assertFalse(objectMapper.readTree(config.body()).path("configured").asBoolean());
        assertTrue(runtimeConfig.path("skillsEnabled").asBoolean());
        assertTrue(runtimeConfig.path("fileEditingEnabled").asBoolean());
    }

    @Test
    void configuresRuntimeWithoutReturningTheApiKeyAndRunsATask() throws Exception {
        String secret = "reader-secret-value";
        HttpResponse<String> configuredResponse = post("/api/config", Map.of(
                "baseUrl", "https://models.example/v1",
                "apiKey", secret,
                "model", "hermes-model",
                "workspace", workspace.toString()
        ));

        assertEquals(200, configuredResponse.statusCode());
        assertFalse(configuredResponse.body().contains(secret));
        assertEquals(secret, configured.get().apiKey());

        HttpResponse<String> publicConfig = get("/api/config");
        assertFalse(publicConfig.body().contains(secret));
        assertTrue(objectMapper.readTree(publicConfig.body()).path("apiKeyConfigured").asBoolean());

        Path skillDirectory = workspace.resolve(".hermes/skills/reader-summary");
        Files.createDirectories(skillDirectory);
        Files.writeString(skillDirectory.resolve("SKILL.md"), """
                ---
                name: reader-summary
                description: Summarize reader material
                triggers: [summarize]
                ---

                Answer with the conclusion first.
                """);
        JsonNode runtimeConfig = objectMapper.readTree(post("/api/runtime-config", Map.of(
                "systemPromptAppendix", "Reply in Chinese.",
                "projectMemory", "Project uses Java 21.",
                "userMemory", "User prefers concise answers.",
                "skillsDirectory", workspace.resolve(".hermes/skills").toString(),
                "skillsEnabled", true,
                "fileEditingEnabled", false
        )).body());

        assertEquals(1, runtimeConfig.path("loadedSkills").size());
        assertEquals("reader-summary", runtimeConfig.path("loadedSkills").get(0).path("name").asText());
        assertFalse(configuredSettings.get().fileEditingEnabled());
        assertEquals("Project uses Java 21.", configuredSettings.get().projectMemory());

        HttpResponse<String> runResponse = post("/api/runs", Map.of(
                "prompt", "inspect README",
                "maxTurns", 4
        ));
        JsonNode run = objectMapper.readTree(runResponse.body());

        assertEquals(200, runResponse.statusCode());
        assertEquals("FINAL_ANSWER", run.path("finishReason").asText());
        assertEquals("README inspected", run.path("finalAnswer").asText());
        assertEquals(4, run.path("events").size());
        assertEquals("read_file", run.path("events").get(1).path("toolName").asText());

        JsonNode latest = objectMapper.readTree(get("/api/runs/latest").body());
        assertTrue(latest.path("available").asBoolean());
        assertEquals(run.path("conversationId").asText(), latest.path("conversationId").asText());

        post("/api/config", Map.of(
                "baseUrl", "https://models.example/v1",
                "apiKey", secret,
                "model", "another-model",
                "workspace", workspace.toString()
        ));
        assertFalse(objectMapper.readTree(get("/api/runs/latest").body()).path("available").asBoolean());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(baseUri.resolve(path)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> post(String path, Object body) throws Exception {
        return client.send(
                HttpRequest.newBuilder(baseUri.resolve(path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }
}
