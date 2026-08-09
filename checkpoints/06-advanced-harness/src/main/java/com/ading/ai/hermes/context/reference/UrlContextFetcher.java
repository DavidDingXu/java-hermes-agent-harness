package com.ading.ai.hermes.context.reference;

@FunctionalInterface
public interface UrlContextFetcher {

    String fetch(String url) throws Exception;

    static UrlContextFetcher disabled() {
        return url -> {
            throw new IllegalStateException("URL references are disabled");
        };
    }
}
