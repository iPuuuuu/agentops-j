package com.ipuuuuu.agentops.tools;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Registry of tools and the tenants permitted to invoke them. */
public final class ToolRegistry {
    private final Map<String, ToolDefinition> definitions = new ConcurrentHashMap<>();

    public ToolRegistry() {}

    public static ToolRegistry defaultRegistry() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ToolDefinition("send_email", Set.of(), Set.of("*")));
        return registry;
    }

    public void register(ToolDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        definitions.put(definition.name(), definition);
    }

    public void register(String name, Set<String> requiredParameters, Set<String> allowedTenants) {
        register(new ToolDefinition(name, requiredParameters, allowedTenants));
    }

    public ToolDefinition find(String name) {
        return definitions.get(name);
    }

    public boolean isAllowed(String tenant, String tool) {
        ToolDefinition definition = find(tool);
        return definition != null && definition.allowsTenant(tenant);
    }

    public void validate(String tenant, String tool) {
        if (tenant == null || tenant.isBlank()) throw new IllegalArgumentException("tenant is required");
        if (tool == null || tool.isBlank()) throw new IllegalArgumentException("tool is required");
        ToolDefinition definition = find(tool);
        if (definition == null) throw new IllegalArgumentException("unknown tool: " + tool);
        if (!definition.allowsTenant(tenant)) throw new SecurityException("tenant is not allowed to invoke tool");
    }

    public record ToolDefinition(String name, Set<String> requiredParameters, Set<String> allowedTenants) {
        public ToolDefinition {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("tool name is required");
            requiredParameters = immutableSet(requiredParameters);
            allowedTenants = immutableSet(allowedTenants);
        }

        public boolean allowsTenant(String tenant) {
            return tenant != null && (allowedTenants.contains("*") || allowedTenants.contains(tenant));
        }

        private static Set<String> immutableSet(Set<String> values) {
            if (values == null) return Set.of();
            LinkedHashSet<String> copy = new LinkedHashSet<>();
            for (String value : values) {
                if (value == null || value.isBlank()) throw new IllegalArgumentException("set values must be nonblank");
                copy.add(value);
            }
            return Collections.unmodifiableSet(copy);
        }
    }
}
