package com.ipuuuuu.agentops.model;

/** Minimal request understood by the provider abstraction. */
public record ModelRequest(String model, String prompt) {
    public ModelRequest {
        model = model == null ? "" : model;
        prompt = prompt == null ? "" : prompt;
    }
}
