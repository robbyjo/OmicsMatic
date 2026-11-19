/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichr;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exact numeric tokens, including Enrichr's bare Infinity extension. */
final class EnrichrJson {
    record Numeric(String token) { }
    private final String text;
    private int at, depth, nodes;
    private EnrichrJson(String text) { this.text = text; }

    static Object read(byte[] bytes) throws IOException {
        String text = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString();
        EnrichrJson parser = new EnrichrJson(text);
        Object value = parser.value();
        parser.space();
        if (parser.at != text.length()) throw parser.error("trailing content");
        return value;
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
        if (token.length() > 128 || (!List.of("NaN", "Infinity", "-Infinity").contains(token)
                && !token.matches("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")))
            throw error("invalid numeric token");
        return new Numeric(token);
    }

    private boolean take(char c) {
        if (at < text.length() && text.charAt(at) == c) { at++; return true; }
        return false;
    }
    private void space() { while (at < text.length() && " \r\n\t".indexOf(text.charAt(at)) >= 0) at++; }
    private IOException error(String message) { return new IOException("Enrichr JSON offset " + at + ": " + message); }
}
