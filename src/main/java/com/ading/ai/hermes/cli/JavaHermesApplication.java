package com.ading.ai.hermes.cli;

import com.ading.ai.hermes.config.LocalApplicationConfiguration;
import com.ading.ai.hermes.model.ModelOptions;
import com.ading.ai.hermes.model.OpenAiCompatibleModelProvider;
import com.ading.ai.hermes.model.OpenAiCompatibleOptions;
import com.ading.ai.hermes.runtime.HermesRuntimeFactory;
import java.nio.file.Path;

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
            Path launchDirectory = Path.of("").toAbsolutePath().normalize();
            var configuration = LocalApplicationConfiguration.load(launchDirectory, System.getenv());
            var input = SystemPromptInput.standard();
            var modelConfig = CliStartupConfiguration.resolveModel(configuration, input);
            String[] effectiveArgs = CliStartupConfiguration.addPromptWhenMissing(args, input);
            Path workspace = workspace(launchDirectory, configuration.get("HERMES_WORKSPACE"));

            var provider = new OpenAiCompatibleModelProvider(OpenAiCompatibleOptions.of(
                    modelConfig.baseUrl(),
                    modelConfig.apiKey()
            ));
            var assembly = HermesRuntimeFactory.create(
                    workspace,
                    provider,
                    new ModelOptions(modelConfig.model(), 0.0),
                    reply -> System.out.println(reply.text())
            );
            exitCode = new JavaHermesCli(assembly.runtime(), System.out, System.err).run(effectiveArgs);
        } catch (IllegalArgumentException | IllegalStateException error) {
            System.err.println(error.getMessage());
            exitCode = 2;
        }
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static Path workspace(Path launchDirectory, String configured) {
        if (configured == null || configured.isBlank()) {
            return launchDirectory;
        }
        Path path = Path.of(configured);
        return path.isAbsolute() ? path.normalize() : launchDirectory.resolve(path).normalize();
    }
}
