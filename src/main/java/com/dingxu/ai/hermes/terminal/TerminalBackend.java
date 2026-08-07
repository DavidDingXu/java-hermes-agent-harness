package com.dingxu.ai.hermes.terminal;

@FunctionalInterface
public interface TerminalBackend {

    TerminalResult execute(TerminalCommand command);
}
