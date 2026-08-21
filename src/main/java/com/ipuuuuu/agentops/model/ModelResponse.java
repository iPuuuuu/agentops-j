package com.ipuuuuu.agentops.model;

public record ModelResponse(String provider, String content, int attempts, boolean fallback) {}
