package com.dingxu.ai.hermes.context.reference;

@FunctionalInterface
public interface GitContextReader {

    String read(String reference) throws Exception;
}
