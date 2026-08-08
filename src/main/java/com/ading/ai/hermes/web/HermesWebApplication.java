package com.ading.ai.hermes.web;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Map;

public final class HermesWebApplication {

    private static final int DEFAULT_PORT = 8080;

    private HermesWebApplication() {
    }

    public static void main(String[] args) {
        Map<String, String> environment = System.getenv();
        int port = parsePort(environment.get("HERMES_WEB_PORT"));
        Path workspace = Path.of(environment.getOrDefault("HERMES_WORKSPACE", ""));
        WebRuntimeConfig initialConfig = initialConfig(environment, workspace);

        HermesWebServer server = HermesWebServer.production(
                new InetSocketAddress("127.0.0.1", port),
                workspace,
                initialConfig
        );
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "hermes-web-shutdown"));
        System.out.printf("Hermes Web Console: http://127.0.0.1:%d%n", server.port());
    }

    private static int parsePort(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65_535) {
                throw new IllegalArgumentException("HERMES_WEB_PORT must be between 1 and 65535");
            }
            return port;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("HERMES_WEB_PORT must be an integer", error);
        }
    }

    private static WebRuntimeConfig initialConfig(Map<String, String> environment, Path workspace) {
        String baseUrl = environment.get("OPENAI_BASE_URL");
        String apiKey = environment.get("OPENAI_API_KEY");
        String model = environment.get("OPENAI_MODEL");
        if (hasText(baseUrl) && hasText(apiKey) && hasText(model)) {
            return new WebRuntimeConfig(baseUrl, apiKey, model, workspace);
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
