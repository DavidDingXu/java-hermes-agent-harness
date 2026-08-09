package com.ading.ai.hermes.observability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public Map<String, Object> redactMap(Map<String, ?> values) {
        Map<String, Object> redacted = new LinkedHashMap<>();
        values.forEach((key, value) -> redacted.put(
                key,
                isSensitiveField(key) ? "[REDACTED]" : redactValue(value)
        ));
        return Map.copyOf(redacted);
    }

    private Object redactValue(Object value) {
        if (value instanceof String text) {
            return redact(text);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> redacted = new LinkedHashMap<>();
            map.forEach((key, nested) -> {
                String field = String.valueOf(key);
                redacted.put(
                        field,
                        isSensitiveField(field) ? "[REDACTED]" : redactValue(nested)
                );
            });
            return Map.copyOf(redacted);
        }
        if (value instanceof List<?> list) {
            List<Object> redacted = new ArrayList<>(list.size());
            list.forEach(item -> redacted.add(redactValue(item)));
            return List.copyOf(redacted);
        }
        return value;
    }

    private boolean isSensitiveField(String field) {
        String normalized = field.replaceAll("[^A-Za-z0-9]", "").toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("authorization")
                || normalized.equals("cookie")
                || normalized.equals("setcookie")
                || normalized.equals("apikey")
                || normalized.endsWith("token")
                || normalized.endsWith("password")
                || normalized.endsWith("secret");
    }
}
