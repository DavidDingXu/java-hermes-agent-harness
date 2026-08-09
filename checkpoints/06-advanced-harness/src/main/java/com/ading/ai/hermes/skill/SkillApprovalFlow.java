package com.ading.ai.hermes.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class SkillApprovalFlow {

    private final List<PendingSkillCandidate> pending = new ArrayList<>();
    private final List<SkillManifest> approved = new ArrayList<>();
    private final Path skillsDirectory;
    private final Path pendingDirectory;
    private final ObjectMapper objectMapper;
    private int nextId = 1;

    public SkillApprovalFlow() {
        this(null, new ObjectMapper());
    }

    public SkillApprovalFlow(Path skillsDirectory) {
        this(skillsDirectory, new ObjectMapper());
    }

    SkillApprovalFlow(Path skillsDirectory, ObjectMapper objectMapper) {
        this.skillsDirectory = skillsDirectory == null
                ? null
                : skillsDirectory.toAbsolutePath().normalize();
        this.pendingDirectory = this.skillsDirectory == null
                ? null
                : this.skillsDirectory.resolve(".pending");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        load();
    }

    public synchronized PendingSkillCandidate submit(SkillCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        PendingSkillCandidate pendingCandidate = new PendingSkillCandidate("skill-candidate-" + nextId++, candidate);
        pending.add(pendingCandidate);
        persistPending(pendingCandidate);
        return pendingCandidate;
    }

    public synchronized SkillManifest approve(String id) {
        PendingSkillCandidate candidate = requirePending(id);
        SkillManifest skill = candidate.candidate().approveAsSkill("approved/" + candidate.id());
        persistApproved(candidate.id(), skill);
        pending.remove(candidate);
        deletePending(candidate.id());
        approved.add(skill);
        return skill;
    }

    public synchronized void reject(String id) {
        PendingSkillCandidate candidate = requirePending(id);
        pending.remove(candidate);
        deletePending(candidate.id());
    }

    public synchronized List<PendingSkillCandidate> pending() {
        return List.copyOf(pending);
    }

    public synchronized List<SkillManifest> approvedSkills() {
        return List.copyOf(approved);
    }

    private PendingSkillCandidate requirePending(String id) {
        Objects.requireNonNull(id, "id must not be null");
        for (PendingSkillCandidate candidate : pending) {
            if (candidate.id().equals(id)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("pending skill candidate not found: " + id);
    }

    private void load() {
        if (skillsDirectory == null) {
            return;
        }
        try {
            Files.createDirectories(pendingDirectory);
            approved.addAll(new SkillLoader().loadAll(skillsDirectory));
            try (var files = Files.list(pendingDirectory)) {
                files.filter(path -> path.getFileName().toString().endsWith(".json"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .map(this::readPending)
                        .forEach(pending::add);
            }
            int pendingMax = pending.stream()
                    .map(PendingSkillCandidate::id)
                    .mapToInt(this::candidateNumber)
                    .max()
                    .orElse(0);
            nextId = Math.max(pendingMax, highestApprovedCandidateNumber()) + 1;
        } catch (IOException error) {
            throw new IllegalStateException("failed to load skill approvals", error);
        }
    }

    private PendingSkillCandidate readPending(Path file) {
        try {
            return objectMapper.readValue(file.toFile(), PendingSkillCandidate.class);
        } catch (IOException error) {
            throw new IllegalStateException("failed to read pending skill candidate: " + file, error);
        }
    }

    private void persistPending(PendingSkillCandidate candidate) {
        if (pendingDirectory == null) {
            return;
        }
        writeAtomically(pendingDirectory.resolve(candidate.id() + ".json"), temporary ->
                objectMapper.writeValue(temporary.toFile(), candidate));
    }

    private void persistApproved(String id, SkillManifest skill) {
        if (skillsDirectory == null) {
            return;
        }
        Path directory = skillsDirectory.resolve("approved-" + id);
        try {
            Files.createDirectories(directory);
        } catch (IOException error) {
            throw new IllegalStateException("failed to create approved skill directory", error);
        }
        String frontmatter = """
                ---
                name: %s
                description: %s
                version: %s
                enabled: true
                triggers: [%s]
                ---

                %s
                """.formatted(
                singleLine(skill.name()),
                singleLine(skill.description()),
                singleLine(skill.version()),
                skill.triggers().stream().map(this::singleLine).collect(java.util.stream.Collectors.joining(", ")),
                skill.instructions()
        );
        writeAtomically(directory.resolve("SKILL.md"), temporary ->
                Files.writeString(temporary, frontmatter, StandardCharsets.UTF_8));
    }

    private void deletePending(String id) {
        if (pendingDirectory == null) {
            return;
        }
        try {
            Files.deleteIfExists(pendingDirectory.resolve(id + ".json"));
        } catch (IOException error) {
            throw new IllegalStateException("failed to delete pending skill candidate", error);
        }
    }

    private void writeAtomically(Path target, FileWriter writer) {
        Path temporary = null;
        try {
            temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
            writer.write(temporary);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            throw new IllegalStateException("failed to persist skill approval", error);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The persistence exception above remains the actionable failure.
                }
            }
        }
    }

    private int candidateNumber(String id) {
        try {
            return Integer.parseInt(id.substring("skill-candidate-".length()));
        } catch (RuntimeException error) {
            return 0;
        }
    }

    private int highestApprovedCandidateNumber() throws IOException {
        try (var directories = Files.list(skillsDirectory)) {
            return directories
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("approved-skill-candidate-"))
                    .map(name -> name.substring("approved-".length()))
                    .mapToInt(this::candidateNumber)
                    .max()
                    .orElse(0);
        }
    }

    private String singleLine(String value) {
        return value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    @FunctionalInterface
    private interface FileWriter {
        void write(Path file) throws IOException;
    }
}
