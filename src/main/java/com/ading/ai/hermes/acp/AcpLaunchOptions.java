package com.ading.ai.hermes.acp;

import com.ading.ai.hermes.config.LocalApplicationConfiguration;
import java.nio.file.Path;
import java.util.Objects;

record AcpLaunchOptions(Path configurationFile, boolean explicitConfiguration) {

    static AcpLaunchOptions parse(String[] args, Path launchDirectory) {
        Objects.requireNonNull(args, "args must not be null");
        Path normalizedLaunchDirectory = Objects.requireNonNull(
                launchDirectory,
                "launchDirectory must not be null"
        ).toAbsolutePath().normalize();
        if (args.length == 0) {
            return new AcpLaunchOptions(
                    normalizedLaunchDirectory.resolve(LocalApplicationConfiguration.DEFAULT_FILE),
                    false
            );
        }
        if (args.length != 2 || !"--config".equals(args[0])
                || args[1] == null || args[1].isBlank()) {
            throw new IllegalArgumentException(
                    "用法: AcpApplication [--config <hermes.local.properties 路径>]"
            );
        }
        Path configured = Path.of(args[1].trim());
        Path resolved = configured.isAbsolute()
                ? configured.toAbsolutePath().normalize()
                : normalizedLaunchDirectory.resolve(configured).normalize();
        return new AcpLaunchOptions(resolved, true);
    }
}
