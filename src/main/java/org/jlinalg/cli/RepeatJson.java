/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Canonical owned audit records; no reflected class is selected by an input document. */
final class RepeatJson {
    private RepeatJson() { }
    static String json(Object source) throws IOException { return RegenieRuntime.json(value(source)); }
    private static Object value(Object source) throws IOException {
        if (source == null || source instanceof String || source instanceof Boolean) return source;
        if (source instanceof Number number) {
            if (!(number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long
                    || number instanceof BigInteger || number instanceof BigDecimal || number instanceof Double || number instanceof Float)
                    || number instanceof Double d && !Double.isFinite(d) || number instanceof Float f && !Float.isFinite(f))
                throw new IOException("RepeatMasker audit needs a supported exact or finite numeric value");
            return number;
        }
        if (source instanceof Enum<?> enumeration) return enumeration.name();
        if (source instanceof Path path) return path.toString();
        if (source instanceof Map<?,?> map) {
            var result = new TreeMap<String,Object>();
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw new IOException("RepeatMasker JSON object keys must be strings");
                result.put(key, value(entry.getValue()));
            }
            return result;
        }
        if (source instanceof Collection<?> collection) {
            var result = new ArrayList<Object>(); for (Object entry : collection) result.add(value(entry));
            if (source instanceof Set<?>) result.sort(Comparator.comparing(RegenieRuntime::json));
            return result;
        }
        String name = source.getClass().getName();
        if (source.getClass().isRecord() && (name.startsWith("org.jlinalg.cli.Repeat") || name.startsWith("org.jlinalg.genetics.RepeatAnnotations$"))) {
            var result = new TreeMap<String,Object>();
            try { for (var part : source.getClass().getRecordComponents()) result.put(part.getName(), value(part.getAccessor().invoke(source))); }
            catch (IllegalAccessException | InvocationTargetException invalid) { throw new IOException("Cannot encode an owned RepeatMasker audit record", invalid); }
            return result;
        }
        throw new IOException("Unsupported RepeatMasker audit value: " + name);
    }
}
