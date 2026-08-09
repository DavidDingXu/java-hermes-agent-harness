package com.ading.ai.hermes.web;

import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.AgentRunRequest;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.IterationBudget;
import com.ading.ai.hermes.memory.MemoryTarget;
import com.ading.ai.hermes.model.ModelOptions;
import com.ading.ai.hermes.model.OpenAiCompatibleModelProvider;
import com.ading.ai.hermes.model.OpenAiCompatibleOptions;
import com.ading.ai.hermes.observability.TraceRedactor;
import com.ading.ai.hermes.runtime.HermesRuntimeFactory;
import com.ading.ai.hermes.runtime.HermesRuntimeAssembly;
import com.ading.ai.hermes.session.SessionSearchHit;
import com.ading.ai.hermes.skill.PendingSkillCandidate;
import com.ading.ai.hermes.skill.SkillManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public final class HermesWebServer implements AutoCloseable {

    private static final int MAX_REQUEST_BYTES = 1_000_000;
    private static final int MAX_PROMPT_CHARACTERS = 20_000;
    private static final int MAX_EVENT_CONTENT_CHARACTERS = 4_000;

    private final HttpServer server;
    private final ExecutorService executor;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TraceRedactor redactor = new TraceRedactor();
    private final Path defaultWorkspace;
    private final RuntimeFactory runtimeFactory;
    private final AtomicReference<RuntimeSession> session = new AtomicReference<>();
    private final AtomicReference<WebRuntimeSettings> runtimeSettings = new AtomicReference<>();
    private final AtomicReference<Map<String, Object>> latestRun = new AtomicReference<>();

    public static HermesWebServer production(
            InetSocketAddress address,
            Path defaultWorkspace,
            WebRuntimeConfig initialConfig
    ) {
        return production(
                address,
                defaultWorkspace,
                initialConfig,
                WebRuntimeSettings.defaults(defaultWorkspace)
        );
    }

    public static HermesWebServer production(
            InetSocketAddress address,
            Path defaultWorkspace,
            WebRuntimeConfig initialConfig,
            WebRuntimeSettings initialSettings
    ) {
        RuntimeFactory factory = (config, settings) -> HermesRuntimeFactory.create(
                config.workspace(),
                new OpenAiCompatibleModelProvider(
                        OpenAiCompatibleOptions.of(config.baseUrl(), config.apiKey())
                ),
                new ModelOptions(config.model(), 0.0),
                reply -> { },
                settings.toRuntimeOptions()
        );
        return new HermesWebServer(address, defaultWorkspace, initialConfig, initialSettings, factory);
    }

    HermesWebServer(
            InetSocketAddress address,
            Path defaultWorkspace,
            WebRuntimeConfig initialConfig,
            RuntimeFactory runtimeFactory
    ) {
        this(
                address,
                defaultWorkspace,
                initialConfig,
                WebRuntimeSettings.defaults(defaultWorkspace),
                runtimeFactory
        );
    }

    HermesWebServer(
            InetSocketAddress address,
            Path defaultWorkspace,
            WebRuntimeConfig initialConfig,
            WebRuntimeSettings initialSettings,
            RuntimeFactory runtimeFactory
    ) {
        try {
            this.server = HttpServer.create(address, 0);
        } catch (IOException error) {
            throw new IllegalStateException("failed to create Hermes web server", error);
        }
        this.defaultWorkspace = defaultWorkspace.toAbsolutePath().normalize();
        if (!Files.isDirectory(this.defaultWorkspace)) {
            throw new IllegalArgumentException("default workspace must be an existing directory");
        }
        this.runtimeFactory = runtimeFactory;
        this.runtimeSettings.set(Objects.requireNonNull(
                initialSettings,
                "initialSettings must not be null"
        ));
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.server.setExecutor(executor);
        this.server.createContext("/", this::handle);
        if (initialConfig != null) {
            session.set(new RuntimeSession(
                    initialConfig,
                    runtimeFactory.create(initialConfig, initialSettings)
            ));
        }
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.close();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            addCommonHeaders(exchange);
            String path = exchange.getRequestURI().getPath();
            if ("/api/health".equals(path)) {
                requireMethod(exchange, "GET");
                writeJson(exchange, 200, Map.of("status", "ok", "configured", session.get() != null));
            } else if ("/api/config".equals(path)) {
                handleConfig(exchange);
            } else if ("/api/runtime-config".equals(path)) {
                handleRuntimeConfig(exchange);
            } else if ("/api/runs".equals(path)) {
                handleRun(exchange);
            } else if ("/api/runs/latest".equals(path)) {
                handleLatestRun(exchange);
            } else if ("/api/operations".equals(path)) {
                handleOperations(exchange);
            } else if ("/api/sessions/search".equals(path)) {
                handleSessionSearch(exchange);
            } else if ("/api/skills/pending".equals(path)) {
                handlePendingSkills(exchange);
            } else if (path.startsWith("/api/skills/")) {
                handleSkillDecision(exchange, path);
            } else {
                handleStatic(exchange, path);
            }
        } catch (HttpError error) {
            writeJson(exchange, error.status, Map.of("error", error.getMessage()));
        } catch (IllegalArgumentException error) {
            writeJson(exchange, 400, Map.of("error", error.getMessage()));
        } catch (RuntimeException error) {
            writeJson(exchange, 500, Map.of("error", safeMessage(error)));
        } finally {
            exchange.close();
        }
    }

    private synchronized void handleConfig(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            RuntimeSession current = session.get();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("configured", current != null);
            response.put("baseUrl", current == null ? "" : current.config.baseUrl());
            response.put("model", current == null ? "" : current.config.model());
            response.put("workspace", current == null
                    ? defaultWorkspace.toString()
                    : current.config.workspace().toString());
            response.put("apiKeyConfigured", current != null);
            response.put("configurationSource", current == null
                    ? ""
                    : current.config.source().displayName());
            response.put("configurationNotices", current == null
                    ? List.of()
                    : current.config.notices());
            writeJson(exchange, 200, response);
            return;
        }
        requireMethod(exchange, "POST");
        ConfigRequest request = readJson(exchange, ConfigRequest.class);
        RuntimeSession current = session.get();
        String apiKey = hasText(request.apiKey)
                ? request.apiKey
                : current == null ? "" : current.config.apiKey();
        Path workspace = hasText(request.workspace)
                ? Path.of(request.workspace)
                : defaultWorkspace;
        WebRuntimeConfig config = new WebRuntimeConfig(
                request.baseUrl,
                apiKey,
                request.model,
                workspace
        );
        WebRuntimeSettings settings = runtimeSettings.get();
        if (!settings.workspace().equals(config.workspace())) {
            settings = WebRuntimeSettings.defaults(config.workspace());
        }
        HermesRuntimeAssembly assembly = runtimeFactory.create(config, settings);
        runtimeSettings.set(settings);
        session.set(new RuntimeSession(config, assembly));
        latestRun.set(null);
        writeJson(exchange, 200, Map.of(
                "configured", true,
                "baseUrl", config.baseUrl(),
                "model", config.model(),
                "workspace", config.workspace().toString(),
                "apiKeyConfigured", true,
                "configurationSource", config.source().displayName(),
                "configurationNotices", config.notices()
        ));
    }

    private synchronized void handleRuntimeConfig(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {
            writeJson(exchange, 200, runtimeSettingsView(runtimeSettings.get()));
            return;
        }
        requireMethod(exchange, "POST");
        RuntimeSettingsRequest request = readJson(exchange, RuntimeSettingsRequest.class);
        RuntimeSession current = session.get();
        Path workspace = current == null ? defaultWorkspace : current.config.workspace();
        WebRuntimeSettings settings = new WebRuntimeSettings(
                workspace,
                request.systemPromptAppendix,
                request.projectMemory,
                request.userMemory,
                hasText(request.skillsDirectory) ? Path.of(request.skillsDirectory) : null,
                request.skillsEnabled == null || request.skillsEnabled,
                request.fileEditingEnabled == null || request.fileEditingEnabled,
                hasText(request.profile) ? request.profile : "default"
        );
        HermesRuntimeAssembly assembly = current == null
                ? null
                : runtimeFactory.create(current.config, settings);
        runtimeSettings.set(settings);
        if (current != null) {
            session.set(new RuntimeSession(current.config, assembly));
        }
        latestRun.set(null);
        writeJson(exchange, 200, runtimeSettingsView(settings));
    }

    private Map<String, Object> runtimeSettingsView(WebRuntimeSettings settings) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("systemPromptAppendix", settings.systemPromptAppendix());
        response.put("projectMemory", settings.projectMemory());
        response.put("userMemory", settings.userMemory());
        response.put("skillsDirectory", settings.workspace()
                .relativize(settings.skillsDirectory())
                .toString()
                .replace('\\', '/'));
        response.put("skillsEnabled", settings.skillsEnabled());
        response.put("fileEditingEnabled", settings.fileEditingEnabled());
        response.put("profile", settings.profile());
        response.put("loadedSkills", settings.loadedSkills().stream()
                .map(skill -> Map.of(
                        "name", skill.name(),
                        "version", skill.version(),
                        "description", skill.description()
                ))
                .toList());
        return response;
    }

    private void handleRun(HttpExchange exchange) throws IOException {
        requireMethod(exchange, "POST");
        RuntimeSession current = session.get();
        if (current == null) {
            throw new HttpError(409, "configure the model before running a task");
        }
        RunRequest request = readJson(exchange, RunRequest.class);
        String prompt = request.prompt == null ? "" : request.prompt.trim();
        if (prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        if (prompt.length() > MAX_PROMPT_CHARACTERS) {
            throw new IllegalArgumentException(
                    "prompt must be at most " + MAX_PROMPT_CHARACTERS + " characters"
            );
        }
        int maxTurns = request.maxTurns == null ? 8 : request.maxTurns;
        if (maxTurns < 1 || maxTurns > 30) {
            throw new IllegalArgumentException("maxTurns must be between 1 and 30");
        }

        String conversationId = hasText(request.conversationId)
                ? request.conversationId.trim()
                : "web-" + UUID.randomUUID();
        if (conversationId.length() > 128) {
            throw new IllegalArgumentException("conversationId must be at most 128 characters");
        }
        if (hasControlCharacter(conversationId)) {
            throw new IllegalArgumentException("conversationId must not contain control characters");
        }
        AgentRunResult result = current.assembly.runtime().run(AgentRunRequest.from(
                "web-console",
                conversationId,
                prompt,
                IterationBudget.maxTurns(maxTurns),
                Map.of("workspace", current.config.workspace().toString())
        ));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("conversationId", conversationId);
        response.put("finishReason", result.finishReason().name());
        response.put("finalAnswer", redactor.redact(result.finalAnswer()));
        response.put("turnsUsed", result.state().turnsUsed());
        response.put("workspace", current.config.workspace().toString());
        response.put("events", result.state().events().stream().map(this::eventView).toList());
        latestRun.set(Map.copyOf(response));
        writeJson(exchange, 200, response);
    }

    private void handleLatestRun(HttpExchange exchange) throws IOException {
        requireMethod(exchange, "GET");
        Map<String, Object> run = latestRun.get();
        if (run == null) {
            writeJson(exchange, 200, Map.of("available", false));
            return;
        }
        Map<String, Object> response = new LinkedHashMap<>(run);
        response.put("available", true);
        writeJson(exchange, 200, response);
    }

    private void handleOperations(HttpExchange exchange) throws IOException {
        requireMethod(exchange, "GET");
        RuntimeSession current = session.get();
        if (current == null) {
            writeJson(exchange, 200, Map.of(
                    "configured", false,
                    "modelCalls", 0,
                    "inputTokens", 0,
                    "outputTokens", 0,
                    "trajectoryRecords", 0,
                    "latestTrajectoryAvailable", false,
                    "projectMemory", List.of(),
                    "userMemory", List.of(),
                    "pendingSkills", 0,
                    "approvedSkills", 0
            ));
            return;
        }
        HermesRuntimeAssembly assembly = current.assembly;
        var calls = assembly.metrics().calls();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("configured", true);
        response.put("modelCalls", calls.size());
        response.put("inputTokens", calls.stream()
                .mapToInt(call -> call.usage().inputTokens()).sum());
        response.put("outputTokens", calls.stream()
                .mapToInt(call -> call.usage().outputTokens()).sum());
        response.put("trajectoryRecords", assembly.trajectories().records().size());
        response.put("latestTrajectoryAvailable", assembly.state().latest().isPresent());
        response.put("projectMemory", assembly.memories().entries(MemoryTarget.MEMORY));
        response.put("userMemory", assembly.memories().entries(MemoryTarget.USER));
        response.put("pendingSkills", assembly.skillApprovals().pending().size());
        response.put("approvedSkills", assembly.skillApprovals().approvedSkills().size());
        writeJson(exchange, 200, response);
    }

    private void handleSessionSearch(HttpExchange exchange) throws IOException {
        requireMethod(exchange, "GET");
        RuntimeSession current = requireRuntimeSession();
        String query = queryParameter(exchange, "q");
        if (!hasText(query)) {
            throw new IllegalArgumentException("q must not be blank");
        }
        List<Map<String, Object>> hits = current.assembly.sessions().search(query, 20).stream()
                .map(this::sessionHitView)
                .toList();
        writeJson(exchange, 200, Map.of("query", query, "hits", hits));
    }

    private void handlePendingSkills(HttpExchange exchange) throws IOException {
        requireMethod(exchange, "GET");
        RuntimeSession current = requireRuntimeSession();
        writeJson(exchange, 200, Map.of(
                "candidates",
                current.assembly.skillApprovals().pending().stream()
                        .map(this::pendingSkillView)
                        .toList()
        ));
    }

    private synchronized void handleSkillDecision(HttpExchange exchange, String path) throws IOException {
        requireMethod(exchange, "POST");
        RuntimeSession current = requireRuntimeSession();
        String[] segments = path.split("/");
        if (segments.length != 5 || !"skills".equals(segments[2])) {
            throw new HttpError(404, "skill action not found");
        }
        String id = segments[3];
        String action = segments[4];
        if ("approve".equals(action)) {
            SkillManifest skill = current.assembly.skillApprovals().approve(id);
            writeJson(exchange, 200, Map.of("status", "approved", "skill", skillView(skill)));
            return;
        }
        if ("reject".equals(action)) {
            current.assembly.skillApprovals().reject(id);
            writeJson(exchange, 200, Map.of("status", "rejected", "id", id));
            return;
        }
        throw new HttpError(404, "skill action not found");
    }

    private RuntimeSession requireRuntimeSession() {
        RuntimeSession current = session.get();
        if (current == null) {
            throw new HttpError(409, "configure the model before reading runtime state");
        }
        return current;
    }

    private Map<String, Object> sessionHitView(SessionSearchHit hit) {
        return Map.of(
                "sessionId", hit.sessionId().value(),
                "eventIndex", hit.eventIndex(),
                "kind", hit.kind().name(),
                "snippet", abbreviate(hit.snippet())
        );
    }

    private Map<String, Object> pendingSkillView(PendingSkillCandidate pending) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", pending.id());
        view.put("name", pending.candidate().name());
        view.put("description", pending.candidate().description());
        view.put("triggers", pending.candidate().triggers());
        view.put("instructions", pending.candidate().instructions());
        view.put("sourceId", pending.candidate().provenance().sourceId());
        return view;
    }

    private Map<String, Object> skillView(SkillManifest skill) {
        return Map.of(
                "name", skill.name(),
                "version", skill.version(),
                "description", skill.description(),
                "triggers", skill.triggers()
        );
    }

    private Map<String, Object> eventView(AgentEvent event) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("kind", event.kind().name());
        view.put("text", abbreviate(redactor.redact(event.text())));
        if (event.toolRequest() != null) {
            view.put("callId", event.toolRequest().callId());
            view.put("toolName", event.toolRequest().name());
            view.put("arguments", redactor.redactMap(event.toolRequest().arguments()));
        }
        if (event.toolObservation() != null) {
            view.put("callId", event.toolObservation().callId());
            view.put("success", event.toolObservation().success());
            view.put("content", abbreviate(redactor.redact(event.toolObservation().content())));
        }
        return view;
    }

    private void handleStatic(HttpExchange exchange, String path) throws IOException {
        requireMethod(exchange, "GET");
        String resource = switch (path) {
            case "/", "/index.html" -> "/web-console/index.html";
            case "/styles.css" -> "/web-console/styles.css";
            case "/app.js" -> "/web-console/app.js";
            default -> throw new HttpError(404, "resource not found");
        };
        try (InputStream input = HermesWebServer.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new HttpError(404, "resource not found");
            }
            byte[] body = input.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType(resource));
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        }
    }

    private <T> T readJson(HttpExchange exchange, Class<T> type) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            throw new HttpError(415, "Content-Type must be application/json");
        }
        byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            throw new HttpError(413, "request body is too large");
        }
        try {
            return objectMapper.readValue(body, type);
        } catch (IOException error) {
            throw new HttpError(400, "request body must be valid JSON");
        }
    }

    private void writeJson(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] body = objectMapper.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private static void addCommonHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Content-Security-Policy",
                "default-src 'self'; style-src 'self'; script-src 'self'; connect-src 'self'; "
                        + "img-src 'self' data:; base-uri 'none'; frame-ancestors 'none'");
    }

    private static void requireMethod(HttpExchange exchange, String expected) {
        if (!expected.equals(exchange.getRequestMethod())) {
            throw new HttpError(405, "method not allowed");
        }
    }

    private static String contentType(String resource) {
        if (resource.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (resource.endsWith(".js")) {
            return "text/javascript; charset=utf-8";
        }
        return "text/html; charset=utf-8";
    }

    private static String abbreviate(String value) {
        if (value == null || value.length() <= MAX_EVENT_CONTENT_CHARACTERS) {
            return value == null ? "" : value;
        }
        return value.substring(0, MAX_EVENT_CONTENT_CHARACTERS) + "...";
    }

    private static String safeMessage(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean hasControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    private String queryParameter(HttpExchange exchange, String name) {
        String rawQuery = exchange.getRequestURI().getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            if (decode(parts[0]).equals(name)) {
                return parts.length == 2 ? decode(parts[1]).trim() : "";
            }
        }
        return "";
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    interface RuntimeFactory {
        HermesRuntimeAssembly create(WebRuntimeConfig config, WebRuntimeSettings settings);
    }

    private record RuntimeSession(WebRuntimeConfig config, HermesRuntimeAssembly assembly) {
    }

    private record ConfigRequest(String baseUrl, String apiKey, String model, String workspace) {
    }

    private record RuntimeSettingsRequest(
            String systemPromptAppendix,
            String projectMemory,
            String userMemory,
            String skillsDirectory,
            Boolean skillsEnabled,
            Boolean fileEditingEnabled,
            String profile
    ) {
    }

    private record RunRequest(String prompt, Integer maxTurns, String conversationId) {
    }

    private static final class HttpError extends RuntimeException {
        private final int status;

        private HttpError(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}
