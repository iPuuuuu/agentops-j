package com.ipuuuuu.agentops.model;

import java.util.List;

/** OpenAI-compatible request details understood by the provider abstraction. */
public record ModelRequest(String model, List<Message> messages, Double temperature, Integer maxTokens) {
    public ModelRequest {
        model = model == null ? "" : model;
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public ModelRequest(String model, String prompt) {
        this(model, List.of(new Message("user", prompt)), null, null);
    }

    /** Compatibility view used by deterministic providers and token estimates. */
    public String prompt() {
        for (int index = messages.size() - 1; index >= 0; index--) {
            Message message = messages.get(index);
            if ("user".equals(message.role())) return message.content();
        }
        return messages.isEmpty() ? "" : messages.get(messages.size() - 1).content();
    }

    public record Message(String role, String content) {
        public Message {
            role = role == null ? "" : role;
            content = content == null ? "" : content;
        }
    }
}
