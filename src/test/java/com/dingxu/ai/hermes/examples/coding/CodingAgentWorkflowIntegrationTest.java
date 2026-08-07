package com.dingxu.ai.hermes.examples.coding;

import com.dingxu.ai.hermes.model.OpenAiCompatibleModelProvider;
import com.dingxu.ai.hermes.model.OpenAiCompatibleOptions;
import com.dingxu.ai.hermes.model.ModelOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CodingAgentWorkflowIntegrationTest {

    @TempDir
    Path workspace;

    @Test
    void canUseRealOpenAiCompatibleEndpointForCodingPlanWhenEnvironmentIsConfigured() throws Exception {
        String baseUrl = System.getenv("OPENAI_BASE_URL");
        String apiKey = System.getenv("OPENAI_API_KEY");
        String model = System.getenv("OPENAI_MODEL");
        assumeTrue(hasText(baseUrl) && hasText(apiKey) && hasText(model),
                "set OPENAI_BASE_URL, OPENAI_API_KEY and OPENAI_MODEL to run this test");

        Files.writeString(workspace.resolve("Calculator.java"), """
                final class Calculator {
                    int add(int a, int b) {
                        return a - b;
                    }
                }
                """, StandardCharsets.UTF_8);

        OpenAiCompatibleModelProvider provider = new OpenAiCompatibleModelProvider(
                OpenAiCompatibleOptions.of(baseUrl, apiKey)
        );
        RecordingVerifier verifier = new RecordingVerifier();
        CodingAgentWorkflow workflow = new CodingAgentWorkflow(
                workspace,
                provider,
                verifier,
                new ObjectMapper(),
                CodingAgentPolicy.defaults(),
                new ModelOptions(model, 0.0)
        );

        CodingAgentRunResult result = workflow.run(new CodingTask(
                "Change Calculator.java so add returns a + b. Use verification command: mvn -Dtest=CalculatorTest test",
                List.of("Calculator.java")
        ));

        assertTrue(result.success(), result.message());
        assertTrue(Files.readString(workspace.resolve("Calculator.java")).contains("return a + b;"));
        assertTrue(verifier.commands().stream().anyMatch(command -> command.startsWith("mvn -Dtest=")));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class RecordingVerifier implements VerificationRunner {
        private final List<String> commands = new java.util.ArrayList<>();

        @Override
        public VerificationResult run(String command, Path workspaceRoot) {
            commands.add(command);
            return new VerificationResult(command, true, "real model coding plan accepted");
        }

        List<String> commands() {
            return List.copyOf(commands);
        }
    }
}
