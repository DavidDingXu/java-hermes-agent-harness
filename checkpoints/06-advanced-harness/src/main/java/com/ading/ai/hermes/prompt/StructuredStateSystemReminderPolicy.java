package com.ading.ai.hermes.prompt;

import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.AgentEventKind;
import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.core.ToolObservation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class StructuredStateSystemReminderPolicy implements SystemReminderPolicy {

    static final StructuredStateSystemReminderPolicy INSTANCE =
            new StructuredStateSystemReminderPolicy();

    private StructuredStateSystemReminderPolicy() {
    }

    @Override
    public List<SystemReminder> remindersFor(AgentState state) {
        List<AgentEvent> events = Objects.requireNonNull(
                state,
                "state must not be null"
        ).events();
        int currentRunStart = latestUserMessage(events);
        Map<String, String> toolsByCallId = new LinkedHashMap<>();
        ToolObservation latestObservation = null;
        boolean workspaceChanged = false;
        for (int index = currentRunStart; index < events.size(); index++) {
            AgentEvent event = events.get(index);
            if (event.kind() == AgentEventKind.TOOL_REQUESTED) {
                toolsByCallId.put(event.toolRequest().callId(), event.toolRequest().name());
            } else if (event.kind() == AgentEventKind.TOOL_OBSERVED) {
                latestObservation = event.toolObservation();
                if (latestObservation.success()
                        && "edit_file".equals(toolsByCallId.get(latestObservation.callId()))) {
                    workspaceChanged = true;
                }
            }
        }

        List<SystemReminder> reminders = new ArrayList<>();
        if (latestObservation != null && !latestObservation.success()) {
            reminders.add(new SystemReminder(
                    "tool-failure",
                    "根据失败原因修正下一步；不要原样重复请求，也不要把失败动作描述为成功。"
            ));
        }
        if (workspaceChanged) {
            reminders.add(new SystemReminder(
                    "workspace-changed",
                    "工作区已经发生修改。只陈述当前事件能够证明的结果；"
                            + "在 Runtime 返回完成证据前，不要声称项目验证已经通过。"
            ));
        }
        return List.copyOf(reminders);
    }

    private static int latestUserMessage(List<AgentEvent> events) {
        for (int index = events.size() - 1; index >= 0; index--) {
            if (events.get(index).kind() == AgentEventKind.USER_MESSAGE) {
                return index;
            }
        }
        return 0;
    }
}
