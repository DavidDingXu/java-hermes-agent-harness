package com.ading.ai.hermes.skill;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public record SkillProvenance(
        SkillSourceKind sourceKind,
        String sourceId,
        String contentHash
) {

    public SkillProvenance {
        Objects.requireNonNull(sourceKind, "sourceKind must not be null");
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(contentHash, "contentHash must not be null");
        sourceId = sourceId.trim();
        contentHash = contentHash.trim();
        if (sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        if (contentHash.isBlank()) {
            throw new IllegalArgumentException("contentHash must not be blank");
        }
    }

    public static SkillProvenance fromContent(
            SkillSourceKind sourceKind,
            String sourceId,
            String name,
            String version,
            String instructions
    ) {
        String canonical = String.join("\n",
                nullToEmpty(name).trim(),
                nullToEmpty(version).trim(),
                nullToEmpty(instructions).trim()
        );
        return new SkillProvenance(sourceKind, sourceId, sha256(canonical));
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is not available", error);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
