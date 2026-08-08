package com.ading.ai.hermes.web;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HermesWebApplicationTest {

    @TempDir
    Path workspace;

    @Test
    void startsUnconfiguredWhenModelEnvironmentIsMissing() {
        assertNull(HermesWebApplication.initialConfig(Map.of(), workspace));
        assertNull(HermesWebApplication.initialConfig(Map.of(
                "OPENAI_BASE_URL", "https://models.example",
                "OPENAI_MODEL", "hermes-model"
        ), workspace));
    }

    @Test
    void preloadsModelOnlyWhenAllEnvironmentValuesArePresent() {
        WebRuntimeConfig config = HermesWebApplication.initialConfig(Map.of(
                "OPENAI_BASE_URL", "https://models.example",
                "OPENAI_API_KEY", "secret",
                "OPENAI_MODEL", "hermes-model"
        ), workspace);

        assertEquals("https://models.example", config.baseUrl());
        assertEquals("hermes-model", config.model());
    }

    @Test
    void fallsBackToAnAvailablePortWhenDefaultPortIsOccupied() throws Exception {
        try (ServerSocket occupied = occupiedPort();
             HermesWebServer server = HermesWebApplication.createServer(
                     occupied.getLocalPort(),
                     false,
                     workspace,
                     null
             )) {
            assertNotEquals(occupied.getLocalPort(), server.port());
        }
    }

    @Test
    void reportsConfiguredPortCollisionClearly() throws Exception {
        try (ServerSocket occupied = occupiedPort()) {
            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> HermesWebApplication.createServer(
                            occupied.getLocalPort(),
                            true,
                            workspace,
                            null
                    )
            );

            assertTrue(error.getMessage().contains("已被占用"));
            assertTrue(error.getMessage().contains("hermes.web.port"));
        }
    }

    private static ServerSocket occupiedPort() throws Exception {
        ServerSocket socket = new ServerSocket();
        socket.bind(new InetSocketAddress("127.0.0.1", 0));
        return socket;
    }
}
