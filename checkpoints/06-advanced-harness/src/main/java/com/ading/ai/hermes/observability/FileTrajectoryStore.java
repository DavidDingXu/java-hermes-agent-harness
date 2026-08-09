package com.ading.ai.hermes.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class FileTrajectoryStore {

    private final Path file;
    private final ObjectMapper objectMapper;

    public FileTrajectoryStore(Path file, ObjectMapper objectMapper) {
        this.file = Objects.requireNonNull(file, "file must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public synchronized void append(TrajectoryRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String line = objectMapper.writeValueAsString(record) + System.lineSeparator();
            Files.writeString(
                    file,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException error) {
            throw new IllegalStateException("failed to append trajectory record: " + file, error);
        }
    }

    public synchronized List<TrajectoryRecord> records() {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            List<TrajectoryRecord> records = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    records.add(objectMapper.readValue(line, TrajectoryRecord.class));
                }
            }
            return List.copyOf(records);
        } catch (IOException error) {
            throw new IllegalStateException("failed to read trajectory records: " + file, error);
        }
    }
}
