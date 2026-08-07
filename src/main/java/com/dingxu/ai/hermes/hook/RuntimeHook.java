package com.dingxu.ai.hermes.hook;

@FunctionalInterface
public interface RuntimeHook {

    RuntimeHookDecision handle(RuntimeHookEvent event) throws Exception;
}
