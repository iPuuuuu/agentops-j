package com.ipuuuuu.agentops;

import com.ipuuuuu.agentops.api.RequestHandler;
import com.ipuuuuu.agentops.model.ModelRouter;
import com.ipuuuuu.agentops.observability.MetricsRegistry;
import com.ipuuuuu.agentops.observability.TraceStore;
import com.ipuuuuu.agentops.tools.ToolExecutor;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;

public final class AgentOpsApplication {
    private AgentOpsApplication() {}

    public static void main(String[] args) throws Exception {
        MetricsRegistry metrics = new MetricsRegistry();
        TraceStore traces = new TraceStore();
        RequestHandler handler = new RequestHandler(new ModelRouter(metrics), new ToolExecutor(metrics), metrics, traces);
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", handler);
        server.start();
        System.out.println("AgentOps-J listening on http://localhost:8080");
    }
}
