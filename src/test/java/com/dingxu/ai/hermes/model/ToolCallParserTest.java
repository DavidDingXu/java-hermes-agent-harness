package com.dingxu.ai.hermes.model;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCallParserTest {

    @Test
    void parsesStandardToolCallIntoToolRequest() {
        ToolCallParser parser = new ToolCallParser();

        ToolCallParseReport report = parser.parse(List.of(
                new RawToolCall("call-1", "read_file", "{\"path\":\"README.md\"}")
        ));

        assertEquals(1, report.requests().size());
        assertTrue(report.repairs().isEmpty());
        assertTrue(report.errors().isEmpty());
        assertEquals("call-1", report.requests().get(0).callId());
        assertEquals("read_file", report.requests().get(0).name());
        assertEquals("README.md", report.requests().get(0).arguments().get("path"));
    }

    @Test
    void reportsMalformedJsonWithoutCreatingRequest() {
        ToolCallParser parser = new ToolCallParser();

        ToolCallParseReport report = parser.parse(List.of(
                new RawToolCall("call-1", "read_file", "{\"path\":\"README.md\"")
        ));

        assertTrue(report.requests().isEmpty());
        assertEquals(1, report.errors().size());
        assertEquals("call-1", report.errors().get(0).callId());
        assertEquals(ToolCallParseErrorKind.MALFORMED_ARGUMENTS_JSON, report.errors().get(0).kind());
    }

    @Test
    void repairsMissingCallIdWithDeterministicGeneratedId() {
        ToolCallParser parser = new ToolCallParser();

        ToolCallParseReport report = parser.parse(List.of(
                new RawToolCall("", "search", "{\"query\":\"Hermes\"}")
        ));

        assertEquals(1, report.requests().size());
        assertEquals("generated-call-1", report.requests().get(0).callId());
        assertEquals(1, report.repairs().size());
        assertEquals(ToolCallRepairKind.GENERATED_MISSING_CALL_ID, report.repairs().get(0).kind());
    }

    @Test
    void repairsDuplicateCallIdsWithStableSuffixes() {
        ToolCallParser parser = new ToolCallParser();

        ToolCallParseReport report = parser.parse(List.of(
                new RawToolCall("call-1", "read_file", "{\"path\":\"a.txt\"}"),
                new RawToolCall("call-1", "read_file", "{\"path\":\"b.txt\"}")
        ));

        assertEquals(2, report.requests().size());
        assertEquals("call-1", report.requests().get(0).callId());
        assertEquals("call-1-2", report.requests().get(1).callId());
        assertEquals(1, report.repairs().size());
        assertEquals(ToolCallRepairKind.RENAMED_DUPLICATE_CALL_ID, report.repairs().get(0).kind());
    }
}
