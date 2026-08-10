package com.ading.ai.hermes.prompt;

import com.ading.ai.hermes.model.PromptCacheDescriptor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

public final class PromptPlan {

    private final List<PromptSection> sections;
    private final String systemPrompt;
    private final String stablePrefix;
    private final PromptCacheDescriptor cacheDescriptor;

    public PromptPlan(List<PromptSection> sections) {
        this.sections = List.copyOf(sections);
        if (this.sections.isEmpty()) {
            throw new IllegalArgumentException("sections must not be empty");
        }
        verifyTierOrder(this.sections);
        systemPrompt = render(this.sections);
        stablePrefix = render(this.sections.stream()
                .filter(section -> section.tier() == PromptTier.STABLE)
                .toList());
        cacheDescriptor = stablePrefix.isEmpty()
                ? PromptCacheDescriptor.none()
                : new PromptCacheDescriptor(fingerprint(stablePrefix), stablePrefix.length());
    }

    public static PromptPlan fromPolicy(PromptPolicy policy) {
        return new PromptPlan(List.of(new PromptSection(
                PromptTier.STABLE,
                "Runtime policy",
                policy.systemPrompt()
        )));
    }

    public List<PromptSection> sections() {
        return sections;
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public String stablePrefix() {
        return stablePrefix;
    }

    public PromptCacheDescriptor cacheDescriptor() {
        return cacheDescriptor;
    }

    private static void verifyTierOrder(List<PromptSection> sections) {
        PromptTier previous = PromptTier.STABLE;
        for (PromptSection section : sections) {
            if (section.tier().ordinal() < previous.ordinal()) {
                throw new IllegalArgumentException(
                        "prompt sections must be ordered STABLE, CONTEXT, then VOLATILE"
                );
            }
            previous = section.tier();
        }
    }

    private static String render(List<PromptSection> sections) {
        return sections.stream()
                .map(section -> "## " + section.name() + "\n" + section.content())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");
    }

    private static String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
