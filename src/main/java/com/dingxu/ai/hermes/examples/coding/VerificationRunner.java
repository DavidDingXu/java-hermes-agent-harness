package com.dingxu.ai.hermes.examples.coding;

import java.nio.file.Path;

@FunctionalInterface
public interface VerificationRunner {

    VerificationResult run(String command, Path workspaceRoot);
}
