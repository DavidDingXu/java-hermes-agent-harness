package com.ading.ai.hermes.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureDependencyTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java");
    private static final String ROOT_PACKAGE = "com.ading.ai.hermes.";
    private static final Pattern PROJECT_IMPORT = Pattern.compile(
            "^import\\s+(?:static\\s+)?(com\\.ading\\.ai\\.hermes\\.[\\w.]+);",
            Pattern.MULTILINE
    );

    @Test
    void coreHasNoDependencyOnOuterRuntimePackages() throws Exception {
        assertImportsLimitedTo("core", Set.of("core"));
    }

    @Test
    void modelDependsOnlyOnCoreAndItsOwnContracts() throws Exception {
        assertImportsLimitedTo("model", Set.of("core", "model"));
    }

    @Test
    void promptDependsOnlyOnCoreAndModelContracts() throws Exception {
        assertImportsLimitedTo("prompt", Set.of("core", "model", "prompt"));
    }

    @Test
    void reusableRuntimeLayersDoNotDependOnEntryAdapters() throws Exception {
        Set<String> forbidden = Set.of("cli", "web", "gateway", "acp");
        List<String> violations = new ArrayList<>();
        for (String layer : List.of(
                "core", "model", "prompt", "context", "session", "memory", "tool", "security"
        )) {
            for (ProjectImport projectImport : importsIn(layer)) {
                if (forbidden.contains(projectImport.targetLayer())) {
                    violations.add(projectImport.description());
                }
            }
        }
        assertTrue(violations.isEmpty(), () -> "entry adapter dependency leaked inward:\n"
                + String.join("\n", violations));
    }

    private static void assertImportsLimitedTo(String layer, Set<String> allowed) throws Exception {
        List<String> violations = importsIn(layer).stream()
                .filter(projectImport -> !allowed.contains(projectImport.targetLayer()))
                .map(ProjectImport::description)
                .toList();
        assertTrue(violations.isEmpty(), () -> layer + " dependency boundary was crossed:\n"
                + String.join("\n", violations));
    }

    private static List<ProjectImport> importsIn(String layer) throws IOException {
        Path directory = SOURCE_ROOT.resolve(ROOT_PACKAGE.replace('.', '/')).resolve(layer);
        List<ProjectImport> imports = new ArrayList<>();
        try (var files = Files.walk(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = PROJECT_IMPORT.matcher(Files.readString(file));
                while (matcher.find()) {
                    String imported = matcher.group(1);
                    String remainder = imported.substring(ROOT_PACKAGE.length());
                    imports.add(new ProjectImport(
                            SOURCE_ROOT.relativize(file),
                            remainder.substring(0, remainder.indexOf('.')),
                            imported
                    ));
                }
            }
        }
        return imports;
    }

    private record ProjectImport(Path source, String targetLayer, String importedType) {

        String description() {
            return source + " imports " + importedType;
        }
    }
}
