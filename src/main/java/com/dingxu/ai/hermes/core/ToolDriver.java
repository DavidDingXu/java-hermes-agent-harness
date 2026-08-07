package com.dingxu.ai.hermes.core;

import java.util.ArrayList;
import java.util.List;

@FunctionalInterface
public interface ToolDriver {

    ToolObservation execute(ToolRequest request);

    default List<ToolObservation> executeBatch(List<ToolRequest> requests) {
        List<ToolObservation> observations = new ArrayList<>(requests.size());
        for (ToolRequest request : requests) {
            observations.add(execute(request));
        }
        return List.copyOf(observations);
    }
}
