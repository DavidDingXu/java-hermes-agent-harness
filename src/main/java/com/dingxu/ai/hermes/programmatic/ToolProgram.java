package com.dingxu.ai.hermes.programmatic;

@FunctionalInterface
public interface ToolProgram {

    String run(ProgrammaticToolContext context) throws Exception;
}
