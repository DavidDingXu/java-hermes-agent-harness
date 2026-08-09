package com.ading.ai.hermes.gateway;

import com.ading.ai.hermes.core.AgentRunRequest;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentRuntime;
import com.ading.ai.hermes.core.IterationBudget;
import com.ading.ai.hermes.control.AdmissionDecision;
import com.ading.ai.hermes.control.NewWorkPolicy;

import java.util.Map;
import java.util.Objects;

public final class HttpGatewayHandler {

    public static final String TURN_PATH = "/v1/turns";
    public static final String SESSION_KEY_HEADER = "X-Hermes-Session-Key";

    private final AgentRuntime runtime;
    private final IterationBudget defaultBudget;
    private final NewWorkPolicy newWorkPolicy;

    public HttpGatewayHandler(AgentRuntime runtime) {
        this(runtime, IterationBudget.maxTurns(6), NewWorkPolicy.allowAll());
    }

    public HttpGatewayHandler(AgentRuntime runtime, IterationBudget defaultBudget) {
        this(runtime, defaultBudget, NewWorkPolicy.allowAll());
    }

    public HttpGatewayHandler(
            AgentRuntime runtime,
            IterationBudget defaultBudget,
            NewWorkPolicy newWorkPolicy
    ) {
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
        this.defaultBudget = Objects.requireNonNull(defaultBudget, "defaultBudget must not be null");
        this.newWorkPolicy = Objects.requireNonNull(newWorkPolicy, "newWorkPolicy must not be null");
    }

    public HttpGatewayResponse handle(HttpGatewayRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (!"POST".equals(request.method())) {
            return error(405, "only POST is supported");
        }
        if (!TURN_PATH.equals(request.path())) {
            return error(404, "unknown gateway path: " + request.path());
        }

        GatewayTurnRequest turnRequest = request.turnRequest();
        String validationError = validate(turnRequest, request.header(SESSION_KEY_HEADER));
        if (!validationError.isBlank()) {
            return error(400, validationError);
        }
        AdmissionDecision admission = Objects.requireNonNull(
                newWorkPolicy.evaluate(),
                "newWorkPolicy result must not be null"
        );
        if (!admission.allowed()) {
            return error(503, admission.reason());
        }

        AgentRunResult result = runtime.run(AgentRunRequest.from(
                turnRequest.source(),
                turnRequest.conversationId(),
                turnRequest.userMessage(),
                defaultBudget,
                turnRequest.metadata()
        ));
        GatewayTurnResponse body = new GatewayTurnResponse(
                turnRequest.conversationId(),
                request.header(SESSION_KEY_HEADER),
                result.finalAnswer(),
                result.finishReason(),
                result.state().events(),
                Map.of(
                        "source", turnRequest.source(),
                        "turnsUsed", Integer.toString(result.state().turnsUsed())
                )
        );
        return new HttpGatewayResponse(200, body, "");
    }

    private static String validate(GatewayTurnRequest request, String sessionKey) {
        if (request == null) {
            return "turn request is required";
        }
        if (request.source().isBlank()) {
            return "source must not be blank";
        }
        if (request.conversationId().isBlank()) {
            return "conversationId must not be blank";
        }
        if (request.userMessage().isBlank()) {
            return "userMessage must not be blank";
        }
        if (hasControlCharacter(sessionKey)) {
            return SESSION_KEY_HEADER + " must not contain control characters";
        }
        if (sessionKey.length() > 256) {
            return SESSION_KEY_HEADER + " must be at most 256 characters";
        }
        return "";
    }

    private static boolean hasControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static HttpGatewayResponse error(int status, String message) {
        return new HttpGatewayResponse(status, null, message);
    }
}
