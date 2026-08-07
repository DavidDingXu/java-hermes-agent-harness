package com.dingxu.ai.hermes.examples.coding;

import java.util.List;
import java.util.Objects;

public record CodingAgentPolicy(List<String> allowedVerificationPrefixes, int maxContextCharsPerFile) {

    public CodingAgentPolicy {
        Objects.requireNonNull(allowedVerificationPrefixes, "allowedVerificationPrefixes must not be null");
        if (maxContextCharsPerFile <= 0) {
            throw new IllegalArgumentException("maxContextCharsPerFile must be positive");
        }
        allowedVerificationPrefixes = allowedVerificationPrefixes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(prefix -> !prefix.isBlank())
                .toList();
        if (allowedVerificationPrefixes.isEmpty()) {
            throw new IllegalArgumentException("allowedVerificationPrefixes must not be empty");
        }
    }

    public static CodingAgentPolicy defaults() {
        return new CodingAgentPolicy(List.of("mvn test", "mvn -Dtest="), 12_000);
    }

    public boolean allowsVerificationCommand(String command) {
        String normalized = command == null ? "" : command.trim();
        return allowedVerificationPrefixes.stream().anyMatch(normalized::startsWith);
    }
}
