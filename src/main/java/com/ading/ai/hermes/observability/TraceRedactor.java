package com.ading.ai.hermes.observability;

import java.util.regex.Pattern;

public final class TraceRedactor {

    private static final Pattern SECRET_KEY = Pattern.compile("(?i)(api[_-]?key|token|password)=([^,}\\s]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[^\\s,}]+");
    private static final Pattern SK_TOKEN = Pattern.compile("sk-[A-Za-z0-9_-]+");

    public String redact(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String redacted = SECRET_KEY.matcher(value).replaceAll("$1=[REDACTED]");
        redacted = BEARER.matcher(redacted).replaceAll("Bearer [REDACTED]");
        redacted = SK_TOKEN.matcher(redacted).replaceAll("[REDACTED]");
        return redacted;
    }
}
