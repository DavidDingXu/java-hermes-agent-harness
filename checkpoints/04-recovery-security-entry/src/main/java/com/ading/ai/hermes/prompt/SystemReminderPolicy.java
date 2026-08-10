package com.ading.ai.hermes.prompt;

import com.ading.ai.hermes.core.AgentState;
import java.util.List;

@FunctionalInterface
public interface SystemReminderPolicy {

    List<SystemReminder> remindersFor(AgentState state);

    static SystemReminderPolicy standard() {
        return StructuredStateSystemReminderPolicy.INSTANCE;
    }
}
