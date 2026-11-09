/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Explicit JSON values only; never reflect or select classes from user data. */
final class SnifflesJson {
    private SnifflesJson() { }
    static String encode(Object value) throws IOException {
        StringBuilder out = new StringBuilder(); append(out, value, 0); return out.toString();
    }
    private static void append(StringBuilder out, Object value, int depth) throws IOException {
        if (depth > 64) throw new IOException("Sniffles JSON depth capacity exceeded");
        if (value == null) { out.append("null"); return; }
        if (value instanceof String text) { quote(out, text); return; }
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long) { out.append(value); return; }
        if (value instanceof Map<?, ?> map) {
            out.append('{'); boolean first = true;
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw new IOException("Require text JSON keys");
                if (!first) out.append(','); first = false; quote(out, key); out.append(':'); append(out, entry.getValue(), depth + 1);
            }
            out.append('}'); return;
        }
        if (value instanceof List<?> list) {
            out.append('['); boolean first = true;
            for (Object item : list) { if (!first) out.append(','); first = false; append(out, item, depth + 1); }
            out.append(']'); return;
        }
        throw new IOException("Unsupported Sniffles JSON value: " + value.getClass().getName());
    }
    private static void quote(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b"); case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
                default -> { if (ch < 32) out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) ch)); else out.append(ch); }
            }
        }
        out.append('"');
    }
}
