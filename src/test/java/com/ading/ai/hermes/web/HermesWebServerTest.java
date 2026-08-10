package com.ading.ai.hermes.web;

import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.core.FinishReason;
import com.ading.ai.hermes.core.ToolObservation;
import com.ading.ai.hermes.core.ToolRequest;
import com.ading.ai.hermes.model.ChatResponse;
import com.ading.ai.hermes.model.ModelOptions;
import com.ading.ai.hermes.runtime.HermesRuntimeAssembly;
import com.ading.ai.hermes.runtime.HermesRuntimeFactory;
import com.ading.ai.hermes.skill.SkillCandidate;
import com.ading.ai.hermes.skill.SkillProvenance;
import com.ading.ai.hermes.skill.SkillSourceKind;
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
import java.util.concurrent.atomic.AtomicInteger;
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
    private AtomicReference<HermesRuntimeAssembly> configuredAssembly;

    @BeforeEach
    void startServer() throws Exception {
        configured = new AtomicReference<>();
        configuredSettings = new AtomicReference<>();
        configuredAssembly = new AtomicReference<>();
        Files.writeString(workspace.resolve("README.md"), "# Hermes");
        server = new HermesWebServer(
                new InetSocketAddress("127.0.0.1", 0),
                workspace,
                null,
                (config, settings) -> {
                    configured.set(config);
                    configuredSettings.set(settings);
                    AtomicInteger calls = new AtomicInteger();
                    HermesRuntimeAssembly assembly = HermesRuntimeFactory.create(
                            config.workspace(),
                            request -> calls.getAndIncrement() == 0
                                    ? ChatResponse.of(com.ading.ai.hermes.core.ModelTurn.toolRequest(
                                            new ToolRequest(
                                                    "call-1",
                                                    "read_file",
                                                    Map.of("path", "README.md")
                                            )
                                    ))
                                    : ChatResponse.of(com.ading.ai.hermes.core.ModelTurn.finalAnswer(
                                            "README inspected"
                                    )),
                            new ModelOptions(config.model(), 0.0),
                            reply -> { },
                            settings.toRuntimeOptions()
                    );
                    configuredAssembly.set(assembly);
                    return assembly;
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
        assertFalse(runtimeConfig.path("fileEditingEnabled").asBoolean());
    }

    @Test
    void keepsFileEditingDisabledWhenTheRuntimeRequestOmitsTheSwitch() throws Exception {
        JsonNode runtimeConfig = objectMapper.readTree(post("/api/runtime-config", Map.of(
                "skillsEnabled", true
        )).body());

        assertFalse(runtimeConfig.path("fileEditingEnabled").asBoolean());
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
                "fileEditingEnabled", false,
                "profile", "reader"
        )).body());

        assertEquals(1, runtimeConfig.path("loadedSkills").size());
        assertEquals("reader-summary", runtimeConfig.path("loadedSkills").get(0).path("name").asText());
        assertFalse(configuredSettings.get().fileEditingEnabled());
        assertEquals("Project uses Java 21.", configuredSettings.get().projectMemory());
        assertEquals("reader", configuredSettings.get().profile());

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

    @Test
    void exposesPersistedRuntimeEvidenceAndSkillApproval() throws Exception {
        post("/api/config", Map.of(
                "baseUrl", "https://models.example/v1",
                "apiKey", "reader-secret-value",
                "model", "hermes-model",
                "workspace", workspace.toString()
        ));
        post("/api/runs", Map.of("prompt", "inspect README", "maxTurns", 4));

        JsonNode operations = objectMapper.readTree(get("/api/operations").body());
        JsonNode sessions = objectMapper.readTree(get("/api/sessions/search?q=README").body());

        assertEquals(2, operations.path("modelCalls").asInt());
        assertEquals(1, operations.path("trajectoryRecords").asInt());
        assertTrue(operations.path("latestTrajectoryAvailable").asBoolean());
        assertTrue(sessions.path("hits").size() >= 1);

        SkillCandidate candidate = new SkillCandidate(
                "reader-summary",
                "Summarize technical material",
                List.of("summary"),
                "Lead with the conclusion.",
                SkillProvenance.fromContent(
                        SkillSourceKind.AGENT_CREATED,
                        "review/web-test",
                        "reader-summary",
                        "candidate",
                        "Lead with the conclusion."
                )
        );
        String candidateId = configuredAssembly.get().skillApprovals().submit(candidate).id();

        JsonNode pending = objectMapper.readTree(get("/api/skills/pending").body());
        assertEquals(candidateId, pending.path("candidates").get(0).path("id").asText());

        JsonNode approved = objectMapper.readTree(post(
                "/api/skills/" + candidateId + "/approve",
                Map.of()
        ).body());
        assertEquals("reader-summary", approved.path("skill").path("name").asText());
        assertEquals(0, objectMapper.readTree(get("/api/skills/pending").body())
                .path("candidates").size());
    }

    @Test
    void redactsSecretsFromRunAndLatestRunResponses() throws Exception {
        String secret = "sk-demo-secret";
        Files.writeString(workspace.resolve("README.md"), "api_key=" + secret);
        post("/api/config", Map.of(
                "baseUrl", "https://models.example/v1",
                "apiKey", "provider-secret",
                "model", "hermes-model",
                "workspace", workspace.toString()
        ));

        HttpResponse<String> run = post("/api/runs", Map.of(
                "prompt", "inspect README",
                "maxTurns", 4
        ));
        HttpResponse<String> latest = get("/api/runs/latest");

        assertEquals(200, run.statusCode());
        assertFalse(run.body().contains(secret));
        assertFalse(latest.body().contains(secret));
        assertTrue(run.body().contains("[REDACTED]"));
    }

    @Test
    void continuesAnExplicitWebConversationAndReturnsNonSuccessFinishReasonsHonestly()
            throws Exception {
        post("/api/config", Map.of(
                "baseUrl", "https://models.example/v1",
                "apiKey", "reader-secret-value",
                "model", "hermes-model",
                "workspace", workspace.toString()
        ));

        JsonNode limited = objectMapper.readTree(post("/api/runs", Map.of(
                "prompt", "inspect README",
                "maxTurns", 1,
                "conversationId", "web-reader"
        )).body());
        JsonNode continued = objectMapper.readTree(post("/api/runs", Map.of(
                "prompt", "continue from the same session",
                "maxTurns", 2,
                "conversationId", "web-reader"
        )).body());

        assertEquals("web-reader", limited.path("conversationId").asText());
        assertEquals("ITERATION_LIMIT", limited.path("finishReason").asText());
        assertEquals("web-reader", continued.path("conversationId").asText());
        assertEquals("FINAL_ANSWER", continued.path("finishReason").asText());
        assertEquals(5, configuredAssembly.get().sessions()
                .load(new com.ading.ai.hermes.session.SessionId("web-reader"))
                .events()
                .size());
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
