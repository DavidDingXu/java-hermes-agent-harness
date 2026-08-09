package com.ading.ai.hermes.runtime;

import com.ading.ai.hermes.acp.HermesAcpAgent;
import com.ading.ai.hermes.checkpoint.FileWorkspaceCheckpointStore;
import com.ading.ai.hermes.context.CompactingContextEngine;
import com.ading.ai.hermes.context.ContextCompactionPolicy;
import com.ading.ai.hermes.context.ContextCompactor;
import com.ading.ai.hermes.context.reference.ContextReferenceResolver;
import com.ading.ai.hermes.context.reference.ProcessGitContextReader;
import com.ading.ai.hermes.context.reference.UrlContextFetcher;
import com.ading.ai.hermes.control.FileEmergencyStop;
import com.ading.ai.hermes.core.InterruptibleAgentLoop;
import com.ading.ai.hermes.core.ErrorRecoveryPolicy;
import com.ading.ai.hermes.core.AgentEventKind;
import com.ading.ai.hermes.core.ModelDriver;
import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.gateway.feishu.FeishuEventHandler;
import com.ading.ai.hermes.gateway.feishu.FeishuReplySink;
import com.ading.ai.hermes.gateway.local.FeishuLocalService;
import com.ading.ai.hermes.gateway.local.LocalServiceRegistry;
import com.ading.ai.hermes.harness.AgentHarness;
import com.ading.ai.hermes.harness.HarnessRuntime;
import com.ading.ai.hermes.hook.RuntimeHookChain;
import com.ading.ai.hermes.memory.MemoryTarget;
import com.ading.ai.hermes.metrics.MeteredModelProvider;
import com.ading.ai.hermes.model.ChatRequestFactory;
import com.ading.ai.hermes.model.ModelOptions;
import com.ading.ai.hermes.model.ModelProvider;
import com.ading.ai.hermes.model.ModelProviderDriver;
import com.ading.ai.hermes.prompt.PromptBuilder;
import com.ading.ai.hermes.run.InMemoryRunCoordinator;
import com.ading.ai.hermes.security.GuardedToolDriver;
import com.ading.ai.hermes.security.ToolPolicy;
import com.ading.ai.hermes.tool.ToolRegistry;
import com.ading.ai.hermes.tool.ToolBatchRunner;
import com.ading.ai.hermes.tools.basic.WorkspaceEditTool;
import com.ading.ai.hermes.tools.basic.WorkspaceFileTools;
import com.ading.ai.hermes.verification.CompletionGate;
import com.ading.ai.hermes.verification.CompletionVerifier;
import com.ading.ai.hermes.verification.WorkspaceCompletionVerifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;

public final class HermesRuntimeFactory {

    private HermesRuntimeFactory() {
    }

    public static HermesRuntimeAssembly create(
            Path workspace,
            ModelProvider provider,
            ModelOptions modelOptions,
            FeishuReplySink feishuReplySink
    ) {
        return create(
                workspace,
                provider,
                modelOptions,
                feishuReplySink,
                HermesRuntimeOptions.defaults()
        );
    }

    public static HermesRuntimeAssembly create(
            Path workspace,
            ModelProvider provider,
            ModelOptions modelOptions,
            FeishuReplySink feishuReplySink,
            HermesRuntimeOptions runtimeOptions
    ) {
        return create(
                workspace,
                provider,
                modelOptions,
                feishuReplySink,
                runtimeOptions,
                new WorkspaceCompletionVerifier(workspace)
        );
    }

    static HermesRuntimeAssembly create(
            Path workspace,
            ModelProvider provider,
            ModelOptions modelOptions,
            FeishuReplySink feishuReplySink,
            HermesRuntimeOptions runtimeOptions,
            CompletionVerifier completionVerifier
    ) {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(modelOptions, "modelOptions must not be null");
        Objects.requireNonNull(feishuReplySink, "feishuReplySink must not be null");
        Objects.requireNonNull(runtimeOptions, "runtimeOptions must not be null");
        Objects.requireNonNull(completionVerifier, "completionVerifier must not be null");

        HermesRuntimeState runtimeState = new HermesRuntimeState(workspace, runtimeOptions.profile());
        ToolRegistry tools = new WorkspaceFileTools(workspace, runtimeOptions.maxFileCharacters())
                .registerInto(ToolRegistry.empty());
        if (runtimeOptions.fileEditingEnabled()) {
            tools = new WorkspaceEditTool(workspace).registerInto(tools);
        }
        ToolRegistry configuredTools = tools;
        MeteredModelProvider meteredProvider = new MeteredModelProvider(
                provider,
                runtimeState.metrics()
        );
        var contextEngine = new CompactingContextEngine(new ContextCompactor(
                new ContextCompactionPolicy(60_000, 2, 12, 12_000, 1_000)
        ));
        ToolBatchRunner toolRunner = new ToolBatchRunner(
                new GuardedToolDriver(
                        tools,
                        java.util.List.of(ToolPolicy.workspaceRelativePath("path"))
                ),
                4,
                request -> request.name().equals("read_file")
                        || request.name().equals("list_directory")
        );
        InMemoryRunCoordinator runs = new InMemoryRunCoordinator();
        HarnessRuntime instrumentedRuntime = (request, stopSignal) -> {
            ModelOptions activeModel = modelOptionsFor(request, modelOptions);
            ChatRequestFactory requestFactory = agentState -> new PromptBuilder(
                    runtimeOptions.promptPlanFor(
                            latestUserMessage(agentState),
                            runtimeState.memories().entries(MemoryTarget.MEMORY),
                            runtimeState.memories().entries(MemoryTarget.USER),
                            runtimeState.skillApprovals().approvedSkills()
                    ),
                    configuredTools.specs(),
                    activeModel
            ).create(agentState);
            ModelDriver modelDriver = new ModelProviderDriver(meteredProvider, requestFactory);
            ModelDriver contextAwareModel = state -> modelDriver.next(
                    contextEngine.select(state).state()
            );
            var result = new InterruptibleAgentLoop(
                    contextAwareModel,
                    toolRunner,
                    stopSignal,
                    ErrorRecoveryPolicy.maxRecoveries(2)
            ).run(request, runtimeState.restoreConversation(request.conversationId()));
            result = applyCompletionGate(result, completionVerifier);
            contextEngine.onTurnCompleted(result);
            runtimeState.recordRun(request, result);
            return result;
        };
        AgentHarness harness = new AgentHarness(
                instrumentedRuntime,
                new ContextReferenceResolver(
                        workspace,
                        runtimeOptions.maxReferencedContextCharacters(),
                        UrlContextFetcher.disabled(),
                        new ProcessGitContextReader(workspace)
                ),
                new FileWorkspaceCheckpointStore(workspace),
                runs,
                RuntimeHookChain.empty()
        );
        HarnessAgentRuntime runtime = new HarnessAgentRuntime(harness);
        FileEmergencyStop emergencyStop = new FileEmergencyStop(
                runtimeOptions.profile().stateDirectory(workspace).resolve("ESTOP")
        );
        LocalServiceRegistry localServices = FeishuLocalService.register(
                LocalServiceRegistry.empty(),
                new FeishuEventHandler(runtime, feishuReplySink, emergencyStop)
        );
        HermesAcpAgent acp = new HermesAcpAgent(
                runtime,
                sessionId -> runs.activeRunForSession(sessionId).ifPresent(run ->
                        runs.submitBusyInput(
                                run.runId(),
                                "ACP client requested cancellation",
                                com.ading.ai.hermes.run.BusyInputMode.INTERRUPT
                        )
                ),
                runtimeState.sessions(),
                workspace,
                modelOptions.model()
        );
        return new HermesRuntimeAssembly(
                runtime,
                harness,
                runs,
                tools,
                localServices,
                emergencyStop,
                runtimeState,
                acp
        );
    }

    private static ModelOptions modelOptionsFor(
            com.ading.ai.hermes.core.AgentRunRequest request,
            ModelOptions defaults
    ) {
        String configured = request.metadata().get("model");
        return configured == null || configured.isBlank()
                ? defaults
                : new ModelOptions(configured.trim(), defaults.temperature());
    }

    private static String latestUserMessage(com.ading.ai.hermes.core.AgentState state) {
        return state.events().stream()
                .filter(event -> event.kind() == AgentEventKind.USER_MESSAGE)
                .reduce((first, second) -> second)
                .map(event -> event.text())
                .orElseThrow(() -> new IllegalStateException("agent state has no user message"));
    }

    private static AgentRunResult applyCompletionGate(
            AgentRunResult result,
            CompletionVerifier verifier
    ) {
        var decision = new CompletionGate(verifier).evaluate(result);
        if (!decision.eligible() || decision.accepted()) {
            return result;
        }
        var events = new ArrayList<>(result.state().events());
        events.add(com.ading.ai.hermes.core.AgentEvent.completionRejected(decision.detail()));
        return new AgentRunResult(
                com.ading.ai.hermes.core.FinishReason.VERIFICATION_FAILED,
                result.finalAnswer(),
                new AgentState(events, result.state().turnsUsed())
        );
    }
}
