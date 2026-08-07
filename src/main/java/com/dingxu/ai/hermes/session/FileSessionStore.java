package com.dingxu.ai.hermes.session;

import com.dingxu.ai.hermes.core.AgentEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class FileSessionStore {

    private final Path sessionsDir;
    private final ObjectMapper objectMapper;

    public FileSessionStore(Path sessionsDir) {
        this(sessionsDir, new ObjectMapper());
    }

    FileSessionStore(Path sessionsDir, ObjectMapper objectMapper) {
        this.sessionsDir = Objects.requireNonNull(sessionsDir, "sessionsDir must not be null");
        this.objectMapper = objectMapper;
    }

    public void append(SessionId sessionId, AgentEvent event) {
        try {
            Files.createDirectories(sessionsDir);
            String json = objectMapper.writeValueAsString(event);
            Files.writeString(
                    sessionPath(sessionId),
                    json + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException error) {
            throw new SessionStoreException("failed to append session event", error);
        }
    }

    public SessionRecord load(SessionId sessionId) {
        Path path = sessionPath(sessionId);
        if (!Files.exists(path)) {
            return new SessionRecord(sessionId, List.of());
        }

        try {
            List<AgentEvent> events = new ArrayList<>();
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    events.add(objectMapper.readValue(line, AgentEvent.class));
                }
            }
            return new SessionRecord(sessionId, events);
        } catch (IOException error) {
            throw new SessionStoreException("failed to load session", error);
        }
    }

    private Path sessionPath(SessionId sessionId) {
        return sessionsDir.resolve(sessionId.value() + ".jsonl").normalize();
    }
}
