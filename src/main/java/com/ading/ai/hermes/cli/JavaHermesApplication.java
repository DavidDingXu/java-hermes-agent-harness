package com.ading.ai.hermes.cli;

import com.ading.ai.hermes.model.ModelOptions;
import com.ading.ai.hermes.model.OpenAiCompatibleModelProvider;
import com.ading.ai.hermes.model.OpenAiCompatibleOptions;
import com.ading.ai.hermes.runtime.HermesRuntimeFactory;
import java.nio.file.Path;
import java.util.Map;

public final class JavaHermesApplication {

    private JavaHermesApplication() {
    }

    public static void main(String[] args) {
        if (JavaHermesCli.isHelpRequest(args)) {
            JavaHermesCli.printUsage(System.out);
            return;
        }
        int exitCode;
        try {
            var environment = System.getenv();
            String baseUrl = required(environment, "OPENAI_BASE_URL");
            String apiKey = required(environment, "OPENAI_API_KEY");
            String model = required(environment, "OPENAI_MODEL");
            Path workspace = Path.of("").toAbsolutePath().normalize();

            var provider = new OpenAiCompatibleModelProvider(OpenAiCompatibleOptions.of(baseUrl, apiKey));
            var assembly = HermesRuntimeFactory.create(
                    workspace,
                    provider,
                    new ModelOptions(model, 0.0),
                    reply -> System.out.println(reply.text())
            );
            exitCode = new JavaHermesCli(assembly.runtime(), System.out, System.err).run(args);
        } catch (IllegalArgumentException error) {
            System.err.println(error.getMessage());
            exitCode = 2;
        }
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
