package com.ading.ai.hermes.checkpoint;

import com.ading.ai.hermes.core.ModelTurn;
import com.ading.ai.hermes.metrics.InMemoryMetricsHealth;
import com.ading.ai.hermes.metrics.MeteredModelProvider;
import com.ading.ai.hermes.model.ChatMessage;
import com.ading.ai.hermes.model.ChatRequest;
import com.ading.ai.hermes.model.ChatResponse;
import com.ading.ai.hermes.model.ModelOptions;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CheckpointContractTest {

    @Test
    void metricsFailureCannotReplaceASuccessfulModelResponse() {
        ChatResponse response = ChatResponse.of(ModelTurn.finalAnswer("done"));
        InMemoryMetricsHealth health = new InMemoryMetricsHealth();
        MeteredModelProvider provider = new MeteredModelProvider(
                request -> response,
                metric -> {
                    throw new IllegalStateException("metrics unavailable");
                },
                () -> 0L,
                health
        );

        ChatResponse actual = provider.complete(new ChatRequest(
                List.of(ChatMessage.user("hello")),
                List.of(),
                new ModelOptions("test-model", 0.0)
        ));

        assertSame(response, actual);
        assertEquals(1, health.droppedCount());
    }
}
