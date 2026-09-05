package com.ipuuuuu.agentops.core;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small JDK-only JSON helpers for the deliberately limited API surface. */
public final class Json {
    private Json() {}

    private static String quoted(String key) {
        return "\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"";
    }

    /** Returns a decoded JSON string field, or an empty string when absent/not a string. */
    public static String value(String body, String key) {
        Matcher matcher = Pattern.compile(quoted(key), Pattern.DOTALL).matcher(body == null ? "" : body);
        return matcher.find() ? unescape(matcher.group(1)) : "";
    }

    public static boolean looksLikeObject(String body) {
        if (body == null) return false;
        String trimmed = body.trim();
        return trimmed.startsWith("{") && trimmed.endsWith("}");
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                switch (c) {
                    case '"', '\\', '/' -> out.append(c);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (i + 4 >= value.length()) return "";
                        try { out.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16)); }
                        catch (NumberFormatException e) { return ""; }
                        i += 4;
                    }
                    default -> out.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                out.append(c);
            }
        }
        if (escaped) return "";
        return out.toString();
    }

    public static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\b", "\\b").replace("\f", "\\f").replace("\n", "\\n")
                .replace("\r", "\\r").replace("\t", "\\t");
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
