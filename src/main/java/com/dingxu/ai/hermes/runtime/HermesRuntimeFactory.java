package com.dingxu.ai.hermes.runtime;

import com.dingxu.ai.hermes.checkpoint.FileWorkspaceCheckpointStore;
import com.dingxu.ai.hermes.context.reference.ContextReferenceResolver;
import com.dingxu.ai.hermes.context.reference.ProcessGitContextReader;
import com.dingxu.ai.hermes.context.reference.UrlContextFetcher;
import com.dingxu.ai.hermes.core.InterruptibleAgentLoop;
import com.dingxu.ai.hermes.gateway.feishu.FeishuEventHandler;
import com.dingxu.ai.hermes.gateway.feishu.FeishuReplySink;
import com.dingxu.ai.hermes.gateway.local.FeishuLocalService;
import com.dingxu.ai.hermes.gateway.local.LocalServiceRegistry;
import com.dingxu.ai.hermes.harness.AgentHarness;
import com.dingxu.ai.hermes.hook.RuntimeHookChain;
import com.dingxu.ai.hermes.model.ModelOptions;
import com.dingxu.ai.hermes.model.ModelProvider;
import com.dingxu.ai.hermes.model.ModelProviderDriver;
import com.dingxu.ai.hermes.prompt.PromptBuilder;
import com.dingxu.ai.hermes.prompt.PromptPolicy;
import com.dingxu.ai.hermes.run.InMemoryRunCoordinator;
import com.dingxu.ai.hermes.tool.ToolRegistry;
import com.dingxu.ai.hermes.tool.ToolBatchRunner;
import com.dingxu.ai.hermes.tools.basic.WorkspaceEditTool;
import com.dingxu.ai.hermes.tools.basic.WorkspaceFileTools;
import java.nio.file.Path;
import java.util.Objects;

public final class HermesRuntimeFactory {

    private static final int MAX_FILE_CHARACTERS = 40_000;
    private static final int MAX_REFERENCED_CONTEXT_CHARACTERS = 100_000;

    private HermesRuntimeFactory() {
    }

    public static HermesRuntimeAssembly create(
            Path workspace,
            ModelProvider provider,
            ModelOptions modelOptions,
            FeishuReplySink feishuReplySink
    ) {
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(modelOptions, "modelOptions must not be null");
        Objects.requireNonNull(feishuReplySink, "feishuReplySink must not be null");

        ToolRegistry tools = new WorkspaceFileTools(workspace, MAX_FILE_CHARACTERS)
                .registerInto(ToolRegistry.empty());
        tools = new WorkspaceEditTool(workspace).registerInto(tools);
        PromptBuilder promptBuilder = new PromptBuilder(
                PromptPolicy.hermesDefault(),
                tools.specs(),
                modelOptions
        );
        ModelProviderDriver modelDriver = new ModelProviderDriver(provider, promptBuilder);
        ToolBatchRunner toolRunner = new ToolBatchRunner(
                tools,
                4,
                request -> request.name().equals("read_file")
                        || request.name().equals("list_directory")
        );
        InMemoryRunCoordinator runs = new InMemoryRunCoordinator();
        AgentHarness harness = new AgentHarness(
                (request, stopSignal) -> new InterruptibleAgentLoop(
                        modelDriver, toolRunner, stopSignal
                ).run(request),
                new ContextReferenceResolver(
                        workspace,
                        MAX_REFERENCED_CONTEXT_CHARACTERS,
                        UrlContextFetcher.disabled(),
                        new ProcessGitContextReader(workspace)
                ),
                new FileWorkspaceCheckpointStore(workspace),
                runs,
                RuntimeHookChain.empty()
        );
        HarnessAgentRuntime runtime = new HarnessAgentRuntime(harness);
        LocalServiceRegistry localServices = FeishuLocalService.register(
                LocalServiceRegistry.empty(),
                new FeishuEventHandler(runtime, feishuReplySink)
        );
        return new HermesRuntimeAssembly(runtime, harness, runs, tools, localServices);
    }
}
