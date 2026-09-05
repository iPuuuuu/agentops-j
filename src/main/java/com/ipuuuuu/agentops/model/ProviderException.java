package com.ipuuuuu.agentops.model;

public final class ProviderException extends RuntimeException {
    public enum Kind { TRANSIENT, RATE_LIMITED, AUTHENTICATION, INVALID_REQUEST, SERVER, NETWORK, CIRCUIT_OPEN, UNKNOWN }
    private final Kind kind;
    private final int statusCode;

    public ProviderException(Kind kind, String message) { this(kind, message, 0, null); }
    public ProviderException(Kind kind, String message, Throwable cause) { this(kind, message, 0, cause); }
    public ProviderException(Kind kind, String message, int statusCode) { this(kind, message, statusCode, null); }
    public ProviderException(Kind kind, String message, int statusCode, Throwable cause) {
        super(message, cause); this.kind = kind; this.statusCode = statusCode;
    }
    public Kind kind() { return kind; }
    public int statusCode() { return statusCode; }
    public boolean retryable() { return kind == Kind.TRANSIENT || kind == Kind.RATE_LIMITED || kind == Kind.SERVER || kind == Kind.NETWORK; }
}
