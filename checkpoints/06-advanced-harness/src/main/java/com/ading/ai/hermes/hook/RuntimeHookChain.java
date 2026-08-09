package com.ading.ai.hermes.hook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class RuntimeHookChain {

    private final List<Registration> registrations;

    private RuntimeHookChain(List<Registration> registrations) {
        this.registrations = List.copyOf(registrations);
    }

    public static RuntimeHookChain empty() {
        return new RuntimeHookChain(List.of());
    }

    public RuntimeHookChain register(
            String name,
            RuntimeHookPoint point,
            int priority,
            HookFailureMode failureMode,
            RuntimeHook hook
    ) {
        List<Registration> next = new ArrayList<>(registrations);
        next.add(new Registration(name, point, priority, failureMode, hook));
        next.sort(Comparator.comparingInt(Registration::priority));
        return new RuntimeHookChain(next);
    }

    public RuntimeHookDecision invoke(RuntimeHookEvent event) {
        Map<String, Object> payload = event.payload();
        List<String> warnings = new ArrayList<>();
        for (Registration registration : registrations) {
            if (registration.point() != event.point()) {
                continue;
            }
            try {
                RuntimeHookDecision decision = registration.hook().handle(event.withPayload(payload));
                warnings.addAll(decision.warnings());
                payload = decision.payload();
                if (!decision.allowed()) {
                    return new RuntimeHookDecision(false, payload, decision.reason(), warnings);
                }
            } catch (Exception exception) {
                String message = "hook '" + registration.name() + "' failed: " + exception.getMessage();
                if (registration.failureMode() == HookFailureMode.FAIL_CLOSED) {
                    return new RuntimeHookDecision(false, payload, message, warnings);
                }
                warnings.add(message);
            }
        }
        return new RuntimeHookDecision(true, payload, "", warnings);
    }

    private record Registration(
            String name,
            RuntimeHookPoint point,
            int priority,
            HookFailureMode failureMode,
            RuntimeHook hook
    ) {
    }
}
