/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/** Canonical owned Eagle audit values; input cannot select a reflected type. */
final class EagleJson {
    private EagleJson() { }
    static String json(Object source)throws IOException{return RegenieRuntime.json(value(source));}
    private static Object value(Object source)throws IOException {
        if(source instanceof Double d&&!Double.isFinite(d)||source instanceof Float f&&!Float.isFinite(f))throw new IOException("Nonfinite Eagle numbers require explicit missing/diagnostic semantics");
        if(source==null||source instanceof String||source instanceof Number||source instanceof Boolean)return source;
        if(source instanceof Enum<?> e)return e.name();if(source instanceof Path p)return p.toString();
        if(source instanceof Map<?,?> map){var result=new TreeMap<String,Object>();for(var entry:map.entrySet())result.put(entry.getKey().toString(),value(entry.getValue()));return result;}
        if(source instanceof Collection<?> list){var result=new ArrayList<Object>();for(Object entry:list)result.add(value(entry));return result;}
        String name=source.getClass().getName();
        if(source.getClass().isRecord()&&(name.startsWith("org.jlinalg.cli.Eagle")||name.startsWith("org.jlinalg.genetics.Eagle")||source instanceof MinimacVcf.Limits)) {
            var result=new TreeMap<String,Object>();
            try{for(var part:source.getClass().getRecordComponents())result.put(part.getName(),value(part.getAccessor().invoke(source)));}
            catch(IllegalAccessException|InvocationTargetException invalid){throw new IOException("Cannot encode an owned Eagle audit record",invalid);}return result;
        }
        throw new IOException("Unsupported Eagle audit value: "+name);
    }
}
