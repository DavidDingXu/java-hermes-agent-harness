package com.ading.ai.hermes.terminal;

@FunctionalInterface
public interface TerminalBackend {

    TerminalResult execute(TerminalCommand command);
}
