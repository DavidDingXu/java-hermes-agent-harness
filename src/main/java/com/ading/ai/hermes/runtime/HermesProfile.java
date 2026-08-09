package com.ading.ai.hermes.runtime;

import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

public record HermesProfile(String name) {

    private static final String DEFAULT_NAME = "default";
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    public HermesProfile {
        if (name == null || !SAFE_NAME.matcher(name.trim()).matches()) {
            throw new IllegalArgumentException(
                    "profile name must use 1-64 letters, digits, dots, underscores, or hyphens"
            );
        }
        name = name.trim();
    }

    public static HermesProfile defaultProfile() {
        return new HermesProfile(DEFAULT_NAME);
    }

    public Path stateDirectory(Path workspace) {
        Path root = Objects.requireNonNull(workspace, "workspace must not be null")
                .toAbsolutePath()
                .normalize()
                .resolve(".hermes");
        return DEFAULT_NAME.equals(name) ? root : root.resolve("profiles").resolve(name);
    }
}
