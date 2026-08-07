package com.dingxu.ai.hermes.core;

public final class ManualStopSignal implements StopSignal {

    private boolean stopRequested;
    private String reason = "";

    public void requestStop(String reason) {
        stopRequested = true;
        this.reason = reason == null ? "" : reason;
    }

    @Override
    public boolean stopRequested() {
        return stopRequested;
    }

    @Override
    public String reason() {
        return reason;
    }
}
