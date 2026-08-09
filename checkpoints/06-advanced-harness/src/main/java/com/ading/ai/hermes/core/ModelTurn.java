package com.ading.ai.hermes.core;

import java.util.List;
import java.util.Objects;

public record ModelTurn(ModelTurnKind kind, String finalAnswer, List<ToolRequest> toolRequests) {

    public ModelTurn {
        Objects.requireNonNull(kind, "kind must not be null");
        finalAnswer = finalAnswer == null ? "" : finalAnswer;
        toolRequests = toolRequests == null ? List.of() : List.copyOf(toolRequests);
        if (kind == ModelTurnKind.TOOL_REQUEST && toolRequests.isEmpty()) {
            throw new IllegalArgumentException("tool request turn must contain at least one request");
        }
    }

    public static ModelTurn finalAnswer(String text) {
        return new ModelTurn(ModelTurnKind.FINAL_ANSWER, text, List.of());
    }

    public static ModelTurn toolRequest(ToolRequest request) {
        return toolRequests(List.of(Objects.requireNonNull(request, "request must not be null")));
    }

    public static ModelTurn toolRequests(List<ToolRequest> requests) {
        return new ModelTurn(ModelTurnKind.TOOL_REQUEST, "", requests);
    }

    public ToolRequest toolRequest() {
        return toolRequests.isEmpty() ? null : toolRequests.getFirst();
    }
}
