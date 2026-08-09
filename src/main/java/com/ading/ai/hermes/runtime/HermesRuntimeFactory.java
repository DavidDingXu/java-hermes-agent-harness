package com.ading.ai.hermes.runtime;

import com.ading.ai.hermes.checkpoint.FileWorkspaceCheckpointStore;
import com.ading.ai.hermes.context.reference.ContextReferenceResolver;
import com.ading.ai.hermes.context.reference.ProcessGitContextReader;
import com.ading.ai.hermes.context.reference.UrlContextFetcher;
import com.ading.ai.hermes.control.FileEmergencyStop;
import com.ading.ai.hermes.core.InterruptibleAgentLoop;
import com.ading.ai.hermes.core.ErrorRecoveryPolicy;
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
import com.ading.ai.hermes.prompt.PromptPolicy;
import com.ading.ai.hermes.run.InMemoryRunCoordinator;
import com.ading.ai.hermes.security.GuardedToolDriver;
import com.ading.ai.hermes.security.ToolPolicy;
import com.ading.ai.hermes.tool.ToolRegistry;
import com.ading.ai.hermes.tool.ToolBatchRunner;
import com.ading.ai.hermes.tools.basic.WorkspaceEditTool;
import com.ading.ai.hermes.tools.basic.WorkspaceFileTools;
import java.nio.file.Path;
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
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(modelOptions, "modelOptions must not be null");
        Objects.requireNonNull(feishuReplySink, "feishuReplySink must not be null");
        Objects.requireNonNull(runtimeOptions, "runtimeOptions must not be null");

        HermesRuntimeState runtimeState = new HermesRuntimeState(workspace);
        ToolRegistry tools = new WorkspaceFileTools(workspace, runtimeOptions.maxFileCharacters())
                .registerInto(ToolRegistry.empty());
        if (runtimeOptions.fileEditingEnabled()) {
            tools = new WorkspaceEditTool(workspace).registerInto(tools);
        }
        ToolRegistry configuredTools = tools;
        ChatRequestFactory requestFactory = agentState -> new PromptBuilder(
                new PromptPolicy(runtimeOptions.systemPromptFor(
                        agentState.events().getFirst().text(),
                        runtimeState.memories().entries(MemoryTarget.MEMORY),
                        runtimeState.memories().entries(MemoryTarget.USER),
                        runtimeState.skillApprovals().approvedSkills()
                )),
                configuredTools.specs(),
                modelOptions
        ).create(agentState);
        ModelProviderDriver modelDriver = new ModelProviderDriver(
                new MeteredModelProvider(provider, runtimeState.metrics()),
                requestFactory
        );
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
            var result = new InterruptibleAgentLoop(
                    modelDriver,
                    toolRunner,
                    stopSignal,
                    ErrorRecoveryPolicy.maxRecoveries(2)
            ).run(request);
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
                workspace.resolve(".hermes").resolve("ESTOP")
        );
        LocalServiceRegistry localServices = FeishuLocalService.register(
                LocalServiceRegistry.empty(),
                new FeishuEventHandler(runtime, feishuReplySink, emergencyStop)
        );
        return new HermesRuntimeAssembly(
                runtime,
                harness,
                runs,
                tools,
                localServices,
                emergencyStop,
                runtimeState
        );
    }
}
