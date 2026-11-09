/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Encodes explicit JSON values and closed, internally constructed audit records. */
final class SnifflesWorkflowJson {
    private SnifflesWorkflowJson() { }
    static String encode(Object value) throws IOException { return SnifflesJson.encode(convert(value)); }
    static byte[] bytes(Object value) throws IOException { return (encode(value) + "\n").getBytes(StandardCharsets.UTF_8); }
    private static Object convert(Object value) throws IOException {
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Long || value instanceof Integer) return value;
        if (value instanceof Path || value instanceof Enum<?>) return value.toString();
        if (value instanceof Map<?, ?> map) {
            var result = new TreeMap<String, Object>();
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw new IOException("Require text audit keys");
                result.put(key, convert(entry.getValue()));
            }
            return result;
        }
        if (value instanceof Collection<?> values) {
            var result = new ArrayList<Object>();
            for (Object item : values) result.add(convert(item));
            if (value instanceof Set<?>) result.sort(java.util.Comparator.comparing(item -> {
                try { return SnifflesJson.encode(item); } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }));
            return result;
        }
        String name = value.getClass().getName();
        if (value.getClass().isRecord() && (name.startsWith("org.jlinalg.cli.SnifflesProcess$")
                || name.startsWith("org.jlinalg.cli.SnifflesHostFiles$") || name.startsWith("org.jlinalg.cli.SnifflesElf$")
                || name.startsWith("org.jlinalg.cli.SnifflesWorkflowFiles$"))) {
            var result = new TreeMap<String, Object>();
            try {
                for (var field : value.getClass().getRecordComponents()) result.put(field.getName(), convert(field.getAccessor().invoke(value)));
            } catch (IllegalAccessException | InvocationTargetException failure) { throw new IOException("Cannot encode internal audit record", failure); }
            return result;
        }
        throw new IOException("Unsupported audit value: " + name);
    }
}
