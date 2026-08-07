package com.ading.ai.hermes.runtime;

import com.ading.ai.hermes.core.AgentRunRequest;
import com.ading.ai.hermes.core.FinishReason;
import com.ading.ai.hermes.core.IterationBudget;
import com.ading.ai.hermes.model.ModelOptions;
import com.ading.ai.hermes.model.OpenAiCompatibleModelProvider;
import com.ading.ai.hermes.model.OpenAiCompatibleOptions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HermesRuntimeFactoryIntegrationTest {

    @TempDir
    Path workspace;

    @Test
    void realModelCanReadAndEditAFileThroughTheHarnessToolLoop() throws Exception {
        String baseUrl = System.getenv("OPENAI_BASE_URL");
        String apiKey = System.getenv("OPENAI_API_KEY");
        String model = System.getenv("OPENAI_MODEL");
        assumeTrue(hasText(baseUrl) && hasText(apiKey) && hasText(model),
                "set OPENAI_BASE_URL, OPENAI_API_KEY and OPENAI_MODEL to run this test");
        Files.writeString(workspace.resolve("status.txt"), "state=before\n");

        HermesRuntimeAssembly assembly = HermesRuntimeFactory.create(
                workspace,
                new OpenAiCompatibleModelProvider(OpenAiCompatibleOptions.of(baseUrl, apiKey)),
                new ModelOptions(model, 0.0),
                reply -> { }
        );

        var result = assembly.runtime().run(AgentRunRequest.from(
                "integration-test",
                "real-tool-loop",
                "Use read_file to inspect status.txt. Then use edit_file to replace the exact text "
                        + "state=before with state=after. Do not claim completion until the edit tool succeeds.",
                IterationBudget.maxTurns(8),
                Map.of()
        ));

        assertEquals(FinishReason.FINAL_ANSWER, result.finishReason());
        assertEquals("state=after\n", Files.readString(workspace.resolve("status.txt")));
        assertTrue(result.state().events().stream()
                .anyMatch(event -> event.toolRequest() != null
                        && event.toolRequest().name().equals("read_file")));
        assertTrue(result.state().events().stream()
                .anyMatch(event -> event.toolRequest() != null
                        && event.toolRequest().name().equals("edit_file")));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
