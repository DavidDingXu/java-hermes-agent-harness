package com.dingxu.ai.hermes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeIdentityTest {

    @Test
    void describesTheInitialRuntimeBoundary() {
        RuntimeIdentity identity = RuntimeIdentity.initial();

        assertEquals("java-hermes-agent-harness", identity.projectName());
        assertEquals("Hermes-style Agent Runtime in Java", identity.runtimeBoundary());
    }
}
