package com.dingxu.ai.hermes.run;

import java.time.Instant;

public record RunEvent(long index, RunEventType type, String payload, Instant occurredAt) {
}
