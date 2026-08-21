package com.ipuuuuu.agentops.core;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Json {
    private Json() {}

    public static String value(String body, String key) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(body == null ? "" : body);
        return matcher.find() ? matcher.group(1) : "";
    }

    public static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    public static String object(Map<String, String> values) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) json.append(',');
            first = false;
            json.append('"').append(escape(entry.getKey())).append("\":\"")
                    .append(escape(entry.getValue())).append('"');
        }
        return json.append('}').toString();
    }
}
