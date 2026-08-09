package com.ading.ai.hermes.acp;

import com.ading.ai.hermes.config.LocalApplicationConfiguration;
import com.ading.ai.hermes.model.ModelOptions;
import com.ading.ai.hermes.model.OpenAiCompatibleModelProvider;
import com.ading.ai.hermes.model.OpenAiCompatibleOptions;
import com.ading.ai.hermes.runtime.HermesProfile;
import com.ading.ai.hermes.runtime.HermesRuntimeFactory;
import com.ading.ai.hermes.runtime.HermesRuntimeOptions;
import java.nio.file.Path;
import java.util.Map;

public final class AcpApplication {

    private AcpApplication() {
    }

    public static void main(String[] args) {
        try {
            Path launchDirectory = Path.of("").toAbsolutePath().normalize();
            var loaded = LocalApplicationConfiguration.loadResolved(
                    launchDirectory,
                    System.getenv()
            );
            loaded.notices().forEach(System.err::println);
            Map<String, String> config = loaded.values();
            String baseUrl = required(config, "OPENAI_BASE_URL");
            String apiKey = required(config, "OPENAI_API_KEY");
            String model = required(config, "OPENAI_MODEL");
            Path workspace = resolveWorkspace(launchDirectory, config.get("HERMES_WORKSPACE"));
            var assembly = HermesRuntimeFactory.create(
                    workspace,
                    new OpenAiCompatibleModelProvider(OpenAiCompatibleOptions.of(baseUrl, apiKey)),
                    new ModelOptions(model, 0.0),
                    reply -> { },
                    HermesRuntimeOptions.defaults().withProfile(new HermesProfile(
                            config.getOrDefault("HERMES_PROFILE", "default")
                    ))
            );
            assembly.acp().runStdio();
        } catch (IllegalArgumentException | IllegalStateException error) {
            System.err.println(error.getMessage());
            System.exit(2);
        }
    }

    private static String required(Map<String, String> config, String key) {
        String value = config.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    key + " 未配置；请先填写 config/hermes.local.properties"
            );
        }
        return value.trim();
    }

    private static Path resolveWorkspace(Path launchDirectory, String configured) {
        if (configured == null || configured.isBlank()) {
            return launchDirectory;
        }
        Path path = Path.of(configured);
        return path.isAbsolute() ? path.normalize() : launchDirectory.resolve(path).normalize();
    }
}
