/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.gprofiler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strict UTF-8 JSON with exact numeric tokens; adapted from the project Enrichr reader. */
public final class GprofilerJson {
    public record Numeric(String token) {
        public Numeric { if (token == null || token.length() > 128 || !token.matches("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?"))
            throw new IllegalArgumentException("Require a finite JSON numeric token"); }
    }
    private final String text;
    private int at, depth, nodes;
    private GprofilerJson(String text) { this.text = text; }

    public static Object read(byte[] bytes) throws IOException {
        String text = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString();
        GprofilerJson parser = new GprofilerJson(text);
        Object value = parser.value();
        parser.space();
        if (parser.at != text.length()) throw parser.error("trailing content");
        return value;
    }

    /** Encode without changing identifier order or numeric tokens. */
    public static byte[] write(Object value, int maximumBytes) throws IOException {
        if (maximumBytes < 1 || maximumBytes > 67_108_864) throw new IOException("Invalid JSON byte capacity");
        StringBuilder out = new StringBuilder();
        encode(value, out, 0, new int[1], maximumBytes);
        byte[] bytes = out.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > maximumBytes) throw new IOException("JSON byte capacity exceeded");
        return bytes;
    }

    private static void encode(Object value, StringBuilder out, int depth, int[] nodes, int capacity) throws IOException {
        if (++nodes[0] > 1_000_000 || depth >= 64) throw new IOException("JSON structure capacity exceeded");
        if (value == null) out.append("null");
        else if (value instanceof Boolean b) out.append(b);
        else if (value instanceof String s) quote(s, out, capacity);
        else if (value instanceof Numeric n) out.append(n.token());
        else if (value instanceof Number n) {
            try { out.append(new Numeric(n.toString()).token()); }
            catch (IllegalArgumentException e) { throw new IOException("Nonfinite or invalid JSON number", e); }
        } else if (value instanceof Map<?,?> map) {
            out.append('{'); boolean first = true;
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw new IOException("JSON object keys must be strings");
                if (!first) out.append(','); first = false;
                quote(key, out, capacity); out.append(':'); encode(entry.getValue(), out, depth + 1, nodes, capacity);
            }
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('['); boolean first = true;
            for (Object entry : list) {
                if (!first) out.append(','); first = false;
                encode(entry, out, depth + 1, nodes, capacity);
            }
            out.append(']');
        } else throw new IOException("Unsupported JSON value type: " + value.getClass().getName());
        if (out.length() > capacity) throw new IOException("JSON character capacity exceeded");
    }

    private static void quote(String value, StringBuilder out, int capacity) throws IOException {
        if (value.length() > capacity) throw new IOException("JSON string capacity exceeded");
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 == value.length() || !Character.isLowSurrogate(value.charAt(i + 1)))
                    throw new IOException("Unpaired Unicode surrogate");
                out.append(c).append(value.charAt(++i));
            } else if (Character.isLowSurrogate(c)) throw new IOException("Unpaired Unicode surrogate");
            else switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int)c));
                    else out.append(c);
                }
            }
            if (out.length() > capacity) throw new IOException("JSON character capacity exceeded");
        }
        out.append('"');
    }

    private Object value() throws IOException {
        if (++nodes > 1_000_000 || ++depth > 64) throw error("JSON capacity exceeded");
        try {
            space();
            if (at == text.length()) throw error("missing value");
            return switch (text.charAt(at)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> numeric();
            };
        } finally { depth--; }
    }

    private Map<String,Object> object() throws IOException {
        at++;
        Map<String,Object> values = new LinkedHashMap<>();
        space();
        if (take('}')) return values;
        while (true) {
            space();
            if (at == text.length() || text.charAt(at) != '"') throw error("object key required");
            String key = string();
            if (values.containsKey(key)) throw error("duplicate object key");
            space();
            if (!take(':')) throw error("colon required");
            values.put(key, value());
            space();
            if (take('}')) return values;
            if (!take(',')) throw error("comma required");
        }
    }

    private List<Object> array() throws IOException {
        at++;
        List<Object> values = new ArrayList<>();
        space();
        if (take(']')) return values;
        while (true) {
            values.add(value());
            space();
            if (take(']')) return values;
            if (!take(',')) throw error("comma required");
        }
    }

    private String string() throws IOException {
        at++;
        StringBuilder value = new StringBuilder();
        while (at < text.length()) {
            char c = text.charAt(at++);
            if (c == '"') {
                for (int i = 0; i < value.length(); i++) {
                    char u = value.charAt(i);
                    if (Character.isHighSurrogate(u)) {
                        if (++i == value.length() || !Character.isLowSurrogate(value.charAt(i)))
                            throw error("unpaired surrogate");
                    } else if (Character.isLowSurrogate(u)) throw error("unpaired surrogate");
                }
                return value.toString();
            }
            if (c < 0x20) throw error("unescaped control character");
            if (c != '\\') { value.append(c); continue; }
            if (at == text.length()) throw error("incomplete escape");
            char escaped = text.charAt(at++);
            switch (escaped) {
                case '"', '\\', '/' -> value.append(escaped);
                case 'b' -> value.append('\b');
                case 'f' -> value.append('\f');
                case 'n' -> value.append('\n');
                case 'r' -> value.append('\r');
                case 't' -> value.append('\t');
                case 'u' -> {
                    if (at + 4 > text.length()) throw error("incomplete Unicode escape");
                    if (!text.substring(at, at + 4).matches("[0-9a-fA-F]{4}")) throw error("invalid Unicode escape");
                    try { value.append((char)Integer.parseInt(text.substring(at, at + 4), 16)); }
                    catch (NumberFormatException e) { throw error("invalid Unicode escape"); }
                    at += 4;
                }
                default -> throw error("invalid escape");
            }
        }
        throw error("unterminated string");
    }

    private Object literal(String token, Object value) throws IOException {
        if (!text.startsWith(token, at)) throw error("invalid literal");
        at += token.length();
        return value;
    }

    private Numeric numeric() throws IOException {
        int start = at;
        while (at < text.length() && ",]} \t\r\n".indexOf(text.charAt(at)) < 0) at++;
        String token = text.substring(start, at);
        if (token.length() > 128 || !token.matches("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?"))
            throw error("invalid numeric token");
        return new Numeric(token);
    }

    private boolean take(char c) {
        if (at < text.length() && text.charAt(at) == c) { at++; return true; }
        return false;
    }
    private void space() { while (at < text.length() && " \r\n\t".indexOf(text.charAt(at)) >= 0) at++; }
    private IOException error(String message) { return new IOException("g:Profiler JSON offset " + at + ": " + message); }
}
