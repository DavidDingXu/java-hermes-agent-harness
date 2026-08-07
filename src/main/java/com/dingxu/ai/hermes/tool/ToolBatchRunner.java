package com.dingxu.ai.hermes.tool;

import com.dingxu.ai.hermes.core.ToolDriver;
import com.dingxu.ai.hermes.core.ToolObservation;
import com.dingxu.ai.hermes.core.ToolRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;

public final class ToolBatchRunner implements ToolDriver {

    private final ToolDriver toolDriver;
    private final int maxConcurrency;
    private final Predicate<ToolRequest> parallelSafe;

    public ToolBatchRunner(ToolDriver toolDriver, int maxConcurrency) {
        this(toolDriver, maxConcurrency, request -> true);
    }

    public ToolBatchRunner(
            ToolDriver toolDriver,
            int maxConcurrency,
            Predicate<ToolRequest> parallelSafe
    ) {
        this.toolDriver = Objects.requireNonNull(toolDriver, "toolDriver must not be null");
        if (maxConcurrency <= 0) {
            throw new IllegalArgumentException("maxConcurrency must be positive");
        }
        this.maxConcurrency = maxConcurrency;
        this.parallelSafe = Objects.requireNonNull(parallelSafe, "parallelSafe must not be null");
    }

    public List<ToolObservation> execute(List<ToolRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }
        if (!requests.stream().allMatch(parallelSafe)) {
            return requests.stream().map(this::executeOne).toList();
        }

        try (ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(maxConcurrency, requests.size())
        )) {
            List<Future<ToolObservation>> futures = new ArrayList<>();
            for (ToolRequest request : requests) {
                futures.add(executor.submit(() -> executeOne(request)));
            }

            List<ToolObservation> observations = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                observations.add(await(futures.get(i), requests.get(i)));
            }
            return List.copyOf(observations);
        }
    }

    @Override
    public ToolObservation execute(ToolRequest request) {
        return executeOne(request);
    }

    @Override
    public List<ToolObservation> executeBatch(List<ToolRequest> requests) {
        return execute(requests);
    }

    private ToolObservation executeOne(ToolRequest request) {
        try {
            return toolDriver.execute(request);
        } catch (RuntimeException error) {
            return ToolObservation.failure(request.callId(), "tool execution failed: " + error.getMessage());
        }
    }

    private ToolObservation await(Future<ToolObservation> future, ToolRequest request) {
        try {
            return future.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return ToolObservation.failure(request.callId(), "tool execution interrupted");
        } catch (ExecutionException error) {
            return ToolObservation.failure(request.callId(), "tool execution failed");
        }
    }
}
