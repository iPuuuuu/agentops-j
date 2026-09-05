package com.ipuuuuu.agentops.api;

import com.ipuuuuu.agentops.observability.MetricsRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
final class MetricsFilter extends OncePerRequestFilter {
    private final MetricsRegistry metrics;

    MetricsFilter(MetricsRegistry metrics) {
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        metrics.httpRequest();
        long startedAt = System.nanoTime();
        boolean exceptionRecorded = false;
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException error) {
            exceptionRecorded = true;
            metrics.httpError();
            throw error;
        } finally {
            if (!exceptionRecorded && response.getStatus() >= 400) metrics.httpError();
            metrics.httpLatency((System.nanoTime() - startedAt) / 1_000_000L);
        }
    }
}
