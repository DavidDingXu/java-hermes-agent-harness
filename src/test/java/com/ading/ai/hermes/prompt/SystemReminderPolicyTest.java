package com.ading.ai.hermes.prompt;

import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.core.ToolObservation;
import com.ading.ai.hermes.core.ToolRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemReminderPolicyTest {

    @Test
    void derivesRemindersFromTheCurrentRunStructuredState() {
        AgentState state = new AgentState(List.of(
                AgentEvent.userMessage("update file"),
                AgentEvent.toolRequested(new ToolRequest("read-1", "read_file", Map.of())),
                AgentEvent.toolObserved(ToolObservation.failure("read-1", "path not found"))
        ), 1);

        List<SystemReminder> reminders = SystemReminderPolicy.standard().remindersFor(state);

        assertEquals(List.of("tool-failure"), reminders.stream().map(SystemReminder::code).toList());
    }
}
