package com.ading.ai.hermes.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MemoryStore {

    private final MemoryPolicy policy;
    private final int memoryLimit;
    private final int userLimit;
    private final Path storageDirectory;
    private final ObjectMapper objectMapper;
    private final List<String> memoryEntries = new ArrayList<>();
    private final List<String> userEntries = new ArrayList<>();

    public MemoryStore(MemoryPolicy policy, int memoryLimit, int userLimit) {
        this(policy, memoryLimit, userLimit, null, new ObjectMapper());
    }

    public MemoryStore(MemoryPolicy policy, int memoryLimit, int userLimit, Path storageDirectory) {
        this(policy, memoryLimit, userLimit, storageDirectory, new ObjectMapper());
    }

    MemoryStore(
            MemoryPolicy policy,
            int memoryLimit,
            int userLimit,
            Path storageDirectory,
            ObjectMapper objectMapper
    ) {
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        if (memoryLimit <= 0) {
            throw new IllegalArgumentException("memoryLimit must be positive");
        }
        if (userLimit <= 0) {
            throw new IllegalArgumentException("userLimit must be positive");
        }
        this.memoryLimit = memoryLimit;
        this.userLimit = userLimit;
        this.storageDirectory = storageDirectory == null
                ? null
                : storageDirectory.toAbsolutePath().normalize();
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        loadPersistedEntries();
    }

    public synchronized MemoryWriteResult consider(MemoryCandidate candidate) {
        MemoryDecision decision = policy.evaluate(candidate);
        if (decision.kind() == MemoryDecisionKind.REJECT) {
            return new MemoryWriteResult(false, decision);
        }

        List<String> targetEntries = mutableEntries(decision.target());
        if (targetEntries.contains(decision.normalizedContent())) {
            return new MemoryWriteResult(false, MemoryDecision.reject("duplicate"));
        }
        if (wouldExceedLimit(targetEntries, decision)) {
            return new MemoryWriteResult(false, MemoryDecision.reject(limitReason(decision.target())));
        }

        targetEntries.add(decision.normalizedContent());
        persist(decision.target(), targetEntries);
        return new MemoryWriteResult(true, decision);
    }

    public synchronized List<String> entries(MemoryTarget target) {
        return List.copyOf(mutableEntries(target));
    }

    private void loadPersistedEntries() {
        if (storageDirectory == null) {
            return;
        }
        try {
            Files.createDirectories(storageDirectory);
            memoryEntries.addAll(read(MemoryTarget.MEMORY));
            userEntries.addAll(read(MemoryTarget.USER));
            if (characters(memoryEntries) > memoryLimit || characters(userEntries) > userLimit) {
                throw new IllegalStateException("persisted memory exceeds configured limit");
            }
        } catch (IOException error) {
            throw new IllegalStateException("failed to load persisted memory", error);
        }
    }

    private List<String> read(MemoryTarget target) throws IOException {
        Path file = file(target);
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        List<String> entries = objectMapper.readValue(file.toFile(), new TypeReference<>() { });
        return entries.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(entry -> !entry.isBlank())
                .toList();
    }

    private void persist(MemoryTarget target, List<String> entries) {
        if (storageDirectory == null) {
            return;
        }
        Path targetFile = file(target);
        Path temporary = null;
        try {
            temporary = Files.createTempFile(storageDirectory, target.name().toLowerCase(), ".tmp");
            objectMapper.writeValue(temporary.toFile(), entries);
            try {
                Files.move(
                        temporary,
                        targetFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            throw new IllegalStateException("failed to persist memory", error);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The target write already failed with the actionable exception.
                }
            }
        }
    }

    private Path file(MemoryTarget target) {
        return storageDirectory.resolve(target == MemoryTarget.MEMORY
                ? "project-memory.json"
                : "user-memory.json");
    }

    private int characters(List<String> entries) {
        return entries.stream().mapToInt(String::length).sum()
                + Math.max(0, entries.size() - 1);
    }

    private List<String> mutableEntries(MemoryTarget target) {
        Objects.requireNonNull(target, "target must not be null");
        return switch (target) {
            case MEMORY -> memoryEntries;
            case USER -> userEntries;
        };
    }

    private boolean wouldExceedLimit(List<String> targetEntries, MemoryDecision decision) {
        int currentCharacters = 0;
        for (String entry : targetEntries) {
            currentCharacters += entry.length();
        }
        int separatorCharacters = targetEntries.isEmpty() ? 0 : 1;
        return currentCharacters + separatorCharacters + decision.normalizedContent().length() > limit(decision.target());
    }

    private int limit(MemoryTarget target) {
        return switch (target) {
            case MEMORY -> memoryLimit;
            case USER -> userLimit;
        };
    }

    private String limitReason(MemoryTarget target) {
        return switch (target) {
            case MEMORY -> "memory_limit_exceeded";
            case USER -> "user_limit_exceeded";
        };
    }
}
