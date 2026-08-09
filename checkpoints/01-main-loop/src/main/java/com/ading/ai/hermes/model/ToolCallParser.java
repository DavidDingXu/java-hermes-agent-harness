package com.ading.ai.hermes.model;

import com.ading.ai.hermes.core.ToolRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ToolCallParser {

    private static final TypeReference<Map<String, Object>> ARGUMENTS_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public ToolCallParser() {
        this(new ObjectMapper());
    }

    ToolCallParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ToolCallParseReport parse(List<RawToolCall> rawCalls) {
        List<ToolRequest> requests = new ArrayList<>();
        List<ToolCallRepair> repairs = new ArrayList<>();
        List<ToolCallParseError> errors = new ArrayList<>();
        Map<String, Integer> callIdCounts = new HashMap<>();
        int generatedId = 1;

        for (RawToolCall rawCall : rawCalls) {
            String callId = rawCall.callId();
            if (callId.isBlank()) {
                callId = "generated-call-" + generatedId++;
                repairs.add(new ToolCallRepair(
                        ToolCallRepairKind.GENERATED_MISSING_CALL_ID,
                        rawCall.callId(),
                        callId,
                        "missing call id was generated"
                ));
            }

            int currentCount = callIdCounts.merge(callId, 1, Integer::sum);
            if (currentCount > 1) {
                String repaired = callId + "-" + currentCount;
                repairs.add(new ToolCallRepair(
                        ToolCallRepairKind.RENAMED_DUPLICATE_CALL_ID,
                        callId,
                        repaired,
                        "duplicate call id was renamed"
                ));
                callId = repaired;
                callIdCounts.put(callId, 1);
            }

            Map<String, Object> arguments;
            try {
                arguments = objectMapper.readValue(rawCall.argumentsJson(), ARGUMENTS_TYPE);
            } catch (IOException error) {
                errors.add(new ToolCallParseError(
                        ToolCallParseErrorKind.MALFORMED_ARGUMENTS_JSON,
                        callId,
                        rawCall.name(),
                        "argumentsJson is not valid JSON object"
                ));
                continue;
            }

            requests.add(new ToolRequest(callId, rawCall.name(), arguments));
        }

        return new ToolCallParseReport(requests, repairs, errors);
    }
}
