package com.dingxu.ai.hermes.model;

import com.dingxu.ai.hermes.core.ModelTurnKind;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OpenAiCompatibleModelProviderIntegrationTest {

    @Test
    void canCallRealOpenAiCompatibleEndpointWhenEnvironmentIsConfigured() {
        String baseUrl = System.getenv("OPENAI_BASE_URL");
        String apiKey = System.getenv("OPENAI_API_KEY");
        String model = System.getenv("OPENAI_MODEL");
        assumeTrue(hasText(baseUrl) && hasText(apiKey) && hasText(model),
                "set OPENAI_BASE_URL, OPENAI_API_KEY and OPENAI_MODEL to run this test");

        OpenAiCompatibleModelProvider provider = new OpenAiCompatibleModelProvider(
                OpenAiCompatibleOptions.of(baseUrl, apiKey)
        );

        ChatResponse response = provider.complete(new ChatRequest(
                List.of(
                        ChatMessage.system("Return a short plain text answer."),
                        ChatMessage.user("Reply with exactly: hermes-ok")
                ),
                List.of(),
                new ModelOptions(model, 0.0)
        ));

        assertEquals(ModelTurnKind.FINAL_ANSWER, response.turn().kind());
        assertFalse(response.turn().finalAnswer().isBlank());
        assertEquals("openai-compatible", response.provider());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
