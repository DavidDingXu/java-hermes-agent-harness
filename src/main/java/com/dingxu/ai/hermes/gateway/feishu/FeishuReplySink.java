package com.dingxu.ai.hermes.gateway.feishu;

@FunctionalInterface
public interface FeishuReplySink {

    void send(FeishuReply reply);
}
