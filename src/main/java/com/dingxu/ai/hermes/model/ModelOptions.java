package com.dingxu.ai.hermes.model;

public record ModelOptions(String model, double temperature) {

    public ModelOptions {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        if (temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("temperature must be between 0.0 and 2.0");
        }
    }
}
