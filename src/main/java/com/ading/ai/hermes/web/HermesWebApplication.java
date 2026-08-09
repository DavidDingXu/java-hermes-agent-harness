package com.ading.ai.hermes.web;

import com.ading.ai.hermes.config.LocalApplicationConfiguration;
import com.ading.ai.hermes.config.LoadedApplicationConfiguration;
import com.ading.ai.hermes.config.ConfigurationSource;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Map;

public final class HermesWebApplication {

    private static final int DEFAULT_PORT = 8080;

    private HermesWebApplication() {
    }

    public static void main(String[] args) {
        try {
            start();
        } catch (IllegalArgumentException | IllegalStateException error) {
            System.err.println(error.getMessage());
            System.exit(2);
        }
    }

    private static void start() {
        Path launchDirectory = Path.of("").toAbsolutePath().normalize();
        LoadedApplicationConfiguration loaded = LocalApplicationConfiguration.loadResolved(
                launchDirectory,
                System.getenv()
        );
        Map<String, String> configuration = loaded.values();
        boolean portConfigured = configuration.containsKey("HERMES_WEB_PORT");
        int port = parsePort(configuration.get("HERMES_WEB_PORT"));
        Path workspace = workspace(launchDirectory, configuration.get("HERMES_WORKSPACE"));
        String profile = configuration.getOrDefault("HERMES_PROFILE", "default");
        WebRuntimeConfig initialConfig = initialConfig(loaded, workspace);

        loaded.notices().forEach(System.err::println);

        HermesWebServer server = createServer(port, portConfigured, workspace, initialConfig, profile);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "hermes-web-shutdown"));
        System.out.printf("Hermes Web Console: http://127.0.0.1:%d%n", server.port());
        if (initialConfig == null) {
            System.out.println("模型尚未配置，请在页面的“模型配置”中填写后再运行任务。");
        } else {
            System.out.println("模型配置来源：" + initialConfig.source().displayName());
        }
    }

    static HermesWebServer createServer(
            int port,
            boolean portConfigured,
            Path workspace,
            WebRuntimeConfig initialConfig
    ) {
        return createServer(port, portConfigured, workspace, initialConfig, "default");
    }

    static HermesWebServer createServer(
            int port,
            boolean portConfigured,
            Path workspace,
            WebRuntimeConfig initialConfig,
            String profile
    ) {
        try {
            return productionServer(port, workspace, initialConfig, profile);
        } catch (IllegalStateException error) {
            if (!causedByBindException(error)) {
                throw error;
            }
            if (portConfigured) {
                throw new IllegalStateException(
                        "Web 端口 " + port + " 已被占用，请修改 config/hermes.local.properties 中的 "
                                + "hermes.web.port，或设置 HERMES_WEB_PORT。",
                        error
                );
            }
            System.err.printf("默认端口 %d 已被占用，正在选择空闲端口。%n", port);
            return productionServer(0, workspace, initialConfig, profile);
        }
    }

    private static HermesWebServer productionServer(
            int port,
            Path workspace,
            WebRuntimeConfig initialConfig,
            String profile
    ) {
        return HermesWebServer.production(
                new InetSocketAddress("127.0.0.1", port),
                workspace,
                initialConfig,
                new WebRuntimeSettings(
                        workspace,
                        "",
                        "",
                        "",
                        null,
                        true,
                        true,
                        profile
                )
        );
    }

    private static boolean causedByBindException(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof BindException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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

    static WebRuntimeConfig initialConfig(Map<String, String> environment, Path workspace) {
        Map<String, ConfigurationSource> sources = new java.util.LinkedHashMap<>();
        environment.keySet().forEach(name -> sources.put(name, ConfigurationSource.ENVIRONMENT));
        return initialConfig(
                new LoadedApplicationConfiguration(environment, sources, java.util.List.of()),
                workspace
        );
    }

    static WebRuntimeConfig initialConfig(
            LoadedApplicationConfiguration configuration,
            Path workspace
    ) {
        String baseUrl = configuration.values().get("OPENAI_BASE_URL");
        String apiKey = configuration.values().get("OPENAI_API_KEY");
        String model = configuration.values().get("OPENAI_MODEL");
        if (hasText(baseUrl) && hasText(apiKey) && hasText(model)) {
            return new WebRuntimeConfig(
                    baseUrl,
                    apiKey,
                    model,
                    workspace,
                    configuration.modelSource(),
                    configuration.notices()
            );
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static Path workspace(Path launchDirectory, String configured) {
        if (!hasText(configured)) {
            return launchDirectory;
        }
        Path path = Path.of(configured);
        return path.isAbsolute() ? path.normalize() : launchDirectory.resolve(path).normalize();
    }
}
