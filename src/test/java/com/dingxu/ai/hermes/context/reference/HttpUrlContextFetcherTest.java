package com.dingxu.ai.hermes.context.reference;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpUrlContextFetcherTest {

    @Test
    void rejectsHostsOutsideTheExplicitAllowlistBeforeNetworkAccess() {
        AtomicBoolean sent = new AtomicBoolean();
        HttpUrlContextFetcher fetcher = new HttpUrlContextFetcher(
                Set.of("docs.example.com"),
                100,
                host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")},
                uri -> {
                    sent.set(true);
                    return response("unused");
                }
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> fetcher.fetch("https://other.example.com/guide")
        );
        assertFalse(sent.get());
    }

    @Test
    void rejectsAllowlistedHostWhenItResolvesToLoopback() {
        HttpUrlContextFetcher fetcher = new HttpUrlContextFetcher(
                Set.of("docs.example.com"),
                100,
                host -> new InetAddress[]{InetAddress.getByName("127.0.0.1")},
                uri -> response("unused")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> fetcher.fetch("https://docs.example.com/guide")
        );
    }

    @Test
    void refusesResponseBeforeReadingBeyondTheConfiguredByteLimit() {
        HttpUrlContextFetcher fetcher = new HttpUrlContextFetcher(
                Set.of("docs.example.com"),
                4,
                host -> new InetAddress[]{InetAddress.getByName("93.184.216.34")},
                uri -> response("12345")
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> fetcher.fetch("https://docs.example.com/guide")
        );
        assertEquals("URL response exceeds byte limit 4", error.getMessage());
    }

    private static UrlHttpResponse response(String body) {
        return new UrlHttpResponse(
                200,
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8))
        );
    }
}
