package com.ading.ai.hermes.control;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class FileEmergencyStop implements NewWorkPolicy {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path sentinel;

    public FileEmergencyStop(Path sentinel) {
        this.sentinel = Objects.requireNonNull(
                sentinel,
                "sentinel must not be null"
        ).toAbsolutePath().normalize();
    }

    public synchronized EmergencyStopState engage(String reason) {
        EmergencyStopState state = new EmergencyStopState(reason, Instant.now().toString());
        Path parent = sentinel.getParent();
        Path temporary = parent.resolve(sentinel.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            Files.createDirectories(parent);
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("engagedAt", state.engagedAt());
            payload.put("reason", state.reason());
            JSON.writeValue(temporary.toFile(), payload);
            moveIntoPlace(temporary);
            return state;
        } catch (IOException exception) {
            throw new IllegalStateException("failed to engage emergency stop", exception);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The sentinel move already decided whether admission is paused.
            }
        }
    }

    public synchronized boolean resume() {
        try {
            return Files.deleteIfExists(sentinel);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to resume new work", exception);
        }
    }

    public boolean engaged() {
        return Files.exists(sentinel);
    }

    public Optional<EmergencyStopState> state() {
        if (!engaged()) {
            return Optional.empty();
        }
        try {
            Map<String, String> payload = JSON.readValue(
                    sentinel.toFile(),
                    new TypeReference<>() { }
            );
            return Optional.of(new EmergencyStopState(
                    payload.get("reason"),
                    payload.get("engagedAt")
            ));
        } catch (IOException | RuntimeException exception) {
            return Optional.of(new EmergencyStopState("", ""));
        }
    }

    @Override
    public AdmissionDecision evaluate() {
        return state()
                .map(value -> AdmissionDecision.reject(
                        value.reason().isBlank()
                                ? "new work is paused by the emergency stop"
                                : "new work is paused: " + value.reason()
                ))
                .orElseGet(AdmissionDecision::allow);
    }

    private void moveIntoPlace(Path temporary) throws IOException {
        try {
            Files.move(
                    temporary,
                    sentinel,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, sentinel, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
