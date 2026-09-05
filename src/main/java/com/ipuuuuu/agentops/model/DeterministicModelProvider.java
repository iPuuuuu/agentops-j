package com.ipuuuuu.agentops.model;

import java.util.concurrent.atomic.AtomicInteger;

/** No-network provider suitable for tests and local development. */
public final class DeterministicModelProvider implements ModelProvider {
    private final String providerName;
    private final boolean failFirstBalanced;
    private final AtomicInteger balancedCalls = new AtomicInteger();

    public DeterministicModelProvider(String providerName) { this(providerName, false); }
    public DeterministicModelProvider(String providerName, boolean failFirstBalanced) {
        this.providerName = providerName; this.failFirstBalanced = failFirstBalanced;
    }
    @Override public String name() { return providerName; }
    @Override public ModelResponse complete(ModelRequest request) {
        if (failFirstBalanced && "balanced".equals(request.model()) && balancedCalls.getAndIncrement() == 0)
            throw new ProviderException(ProviderException.Kind.TRANSIENT, "deterministic primary failure");
        return new ModelResponse(providerName, "simulated response from " + providerName, 1, false);
    }
}
