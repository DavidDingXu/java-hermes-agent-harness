package com.dingxu.ai.hermes.scheduler;

@FunctionalInterface
public interface CronDeliverySink {

    void deliver(CronRunRecord runRecord);
}
