package it.fabio.transport.api;

import java.util.Map;
import java.util.stream.Collectors;

/** Small JSON response encoder; request bodies use standard URL-encoded forms. */
final class Json {
    private Json() {}

    static String encode(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) return map.entrySet().stream()
                .map(e -> encode(e.getKey().toString()) + ":" + encode(e.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
        if (value instanceof Iterable<?> values) {
            var result = new java.util.StringJoiner(",", "[", "]");
            values.forEach(v -> result.add(encode(v)));
            return result.toString();
        }
        var result = new StringBuilder("\"");
        for (char c : value.toString().toCharArray()) {
            switch (c) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> { if (c < 32 || Character.isSurrogate(c)) result.append(String.format("\\u%04x", (int) c)); else result.append(c); }
            }
        }
        return result.append('"').toString();
    }
}
