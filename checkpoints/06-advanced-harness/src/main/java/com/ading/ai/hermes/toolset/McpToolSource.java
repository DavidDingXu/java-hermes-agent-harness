package com.ading.ai.hermes.toolset;

import java.util.List;

@FunctionalInterface
public interface McpToolSource {

    List<McpToolDescriptor> discover() throws Exception;
}
