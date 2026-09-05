package com.ipuuuuu.agentops.model;

/** Compatibility alias for deterministic test providers. */
public final class MockModelProvider implements ModelProvider {
    private final DeterministicModelProvider delegate;
    public MockModelProvider(String providerName) { this(providerName, false); }
    public MockModelProvider(String providerName, boolean failFirstBalanced) { delegate = new DeterministicModelProvider(providerName, failFirstBalanced); }
    @Override public String name() { return delegate.name(); }
    @Override public ModelResponse complete(ModelRequest request) { return delegate.complete(request); }
}
