package com.ading.ai.hermes.core;

public interface StopSignal {

    boolean stopRequested();

    String reason();

    static StopSignal none() {
        return new StopSignal() {
            @Override
            public boolean stopRequested() {
                return false;
            }

            @Override
            public String reason() {
                return "";
            }
        };
    }
}
