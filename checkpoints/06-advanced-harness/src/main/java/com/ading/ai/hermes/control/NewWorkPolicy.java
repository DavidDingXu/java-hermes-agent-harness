package com.ading.ai.hermes.control;

@FunctionalInterface
public interface NewWorkPolicy {

    AdmissionDecision evaluate();

    static NewWorkPolicy allowAll() {
        return AdmissionDecision::allow;
    }
}
