package com.ipuuuuu.agentops.model;

/** Vendor-neutral model provider contract. Implementations may be local, mocked, or remote. */
public interface ModelProvider {
    String name();

    ModelResponse complete(ModelRequest request);

    default ModelResponse complete(String model, String prompt) {
        return complete(new ModelRequest(model, prompt));
    }
}
