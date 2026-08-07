package com.dingxu.ai.hermes.context.reference;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class HttpUrlContextFetcher implements UrlContextFetcher {

    private final Set<String> allowedHosts;
    private final int maxResponseBytes;
    private final HostResolver hostResolver;
    private final UrlHttpTransport transport;

    public HttpUrlContextFetcher(Set<String> allowedHosts, int maxResponseBytes) {
        this(
                allowedHosts,
                maxResponseBytes,
                InetAddress::getAllByName,
                new JdkUrlHttpTransport(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build())
        );
    }

    HttpUrlContextFetcher(
            Set<String> allowedHosts,
            int maxResponseBytes,
            HostResolver hostResolver,
            UrlHttpTransport transport
    ) {
        if (allowedHosts == null || allowedHosts.isEmpty()) {
            throw new IllegalArgumentException("allowedHosts must not be empty");
        }
        if (maxResponseBytes < 1) {
            throw new IllegalArgumentException("maxResponseBytes must be positive");
        }
        this.allowedHosts = allowedHosts.stream()
                .map(host -> host.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.maxResponseBytes = maxResponseBytes;
        this.hostResolver = Objects.requireNonNull(hostResolver, "hostResolver must not be null");
        this.transport = Objects.requireNonNull(transport, "transport must not be null");
    }

    @Override
    public String fetch(String url) throws Exception {
        URI uri = validate(url);
        UrlHttpResponse response = transport.send(uri);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            close(response.body());
            throw new IllegalStateException("URL returned HTTP " + response.statusCode());
        }
        try (InputStream body = response.body()) {
            byte[] bytes = body.readNBytes(maxResponseBytes + 1);
            if (bytes.length > maxResponseBytes) {
                throw new IllegalStateException(
                        "URL response exceeds byte limit " + maxResponseBytes
                );
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private URI validate(String url) throws Exception {
        URI uri = URI.create(url);
        if (!("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("only http and https URLs are supported");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("URL must contain a host and no user info");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!allowedHosts.contains(host)) {
            throw new IllegalArgumentException("URL host is not allowlisted: " + host);
        }
        InetAddress[] addresses = hostResolver.resolve(host);
        if (addresses.length == 0 || Arrays.stream(addresses).anyMatch(HttpUrlContextFetcher::isNonPublic)) {
            throw new IllegalArgumentException("URL host resolves to a non-public address");
        }
        return uri;
    }

    private static boolean isNonPublic(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet6Address) {
            byte first = address.getAddress()[0];
            return (first & 0xfe) == 0xfc;
        }
        byte[] bytes = address.getAddress();
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        return first == 0
                || first >= 224
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 192 && second == 0);
    }

    private static void close(InputStream body) {
        try {
            body.close();
        } catch (IOException ignored) {
            // The request has already failed; closing is best effort.
        }
    }
}

@FunctionalInterface
interface HostResolver {
    InetAddress[] resolve(String host) throws Exception;
}

@FunctionalInterface
interface UrlHttpTransport {
    UrlHttpResponse send(URI uri) throws IOException, InterruptedException;
}

record UrlHttpResponse(int statusCode, InputStream body) {
    UrlHttpResponse {
        Objects.requireNonNull(body, "body must not be null");
    }
}

final class JdkUrlHttpTransport implements UrlHttpTransport {

    private final HttpClient client;

    JdkUrlHttpTransport(HttpClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    @Override
    public UrlHttpResponse send(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "text/plain,text/markdown,text/html")
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(
                request, HttpResponse.BodyHandlers.ofInputStream()
        );
        return new UrlHttpResponse(response.statusCode(), response.body());
    }
}
