package com.ading.ai.hermes.verification;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class JavaProjectVerificationDetector {

    public Optional<ProjectVerificationRecipe> detect(Path projectRoot) {
        Path root = Objects.requireNonNull(
                projectRoot,
                "projectRoot must not be null"
        ).toAbsolutePath().normalize();
        if (Files.isRegularFile(root.resolve("pom.xml"))) {
            return Optional.of(new ProjectVerificationRecipe(
                    "Maven",
                    root,
                    List.of(new ProjectVerificationCommand("test", mavenCommand(root))),
                    List.of("Detected pom.xml")
            ));
        }
        if (Files.isRegularFile(root.resolve("settings.gradle"))
                || Files.isRegularFile(root.resolve("settings.gradle.kts"))
                || Files.isRegularFile(root.resolve("build.gradle"))
                || Files.isRegularFile(root.resolve("build.gradle.kts"))) {
            return Optional.of(new ProjectVerificationRecipe(
                    "Gradle",
                    root,
                    List.of(new ProjectVerificationCommand("test", gradleCommand(root))),
                    List.of("Detected Gradle build")
            ));
        }
        return Optional.empty();
    }

    private static List<String> mavenCommand(Path root) {
        if (isWindows() && Files.isRegularFile(root.resolve("mvnw.cmd"))) {
            return List.of("mvnw.cmd", "test");
        }
        if (!isWindows() && Files.isExecutable(root.resolve("mvnw"))) {
            return List.of("./mvnw", "test");
        }
        return List.of("mvn", "test");
    }

    private static List<String> gradleCommand(Path root) {
        if (isWindows() && Files.isRegularFile(root.resolve("gradlew.bat"))) {
            return List.of("gradlew.bat", "test");
        }
        if (!isWindows() && Files.isExecutable(root.resolve("gradlew"))) {
            return List.of("./gradlew", "test");
        }
        return List.of("gradle", "test");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }
}
