/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeMap;

/** Canonical JSON data for owned typed audit records; no input-selected reflection or code execution. */
final class MinimacJson {
    private MinimacJson() { }
    static String json(Object value)throws IOException{return RegenieRuntime.json(value(value));}
    static Object value(Object source)throws IOException{
        if(source instanceof Double d&&!Double.isFinite(d)||source instanceof Float f&&!Float.isFinite(f))throw new IOException("Nonfinite Minimac JSON number must retain explicit missing/diagnostic semantics");
        if(source==null||source instanceof String||source instanceof Number||source instanceof Boolean)return source;
        if(source instanceof Enum<?> e)return e.name();
        if(source instanceof Path path)return path.toString();
        if(source instanceof OptionalDouble optional)return optional.isPresent()?optional.getAsDouble():null;
        if(source instanceof java.util.Optional<?> optional)return optional.isPresent()?value(optional.get()):null;
        if(source instanceof Map<?,?> map){var result=new TreeMap<String,Object>();for(var e:map.entrySet())result.put(e.getKey().toString(),value(e.getValue()));return result;}
        if(source instanceof Collection<?> list){var result=new ArrayList<Object>();for(Object item:list)result.add(value(item));return result;}
        if(source.getClass().isRecord()){
            String name=source.getClass().getName();
            if(!name.startsWith("org.jlinalg.cli.Minimac")&&!name.startsWith("org.jlinalg.genetics.Minimac"))
                throw new IOException("Unsupported owned audit-record type");
            var result=new TreeMap<String,Object>();
            try {for(var part:source.getClass().getRecordComponents())result.put(part.getName(),value(part.getAccessor().invoke(source)));}
            catch(IllegalAccessException|InvocationTargetException failure){throw new IOException("Cannot encode owned Minimac audit record",failure);}
            return result;
        }
        throw new IOException("Unsupported Minimac audit JSON value: "+source.getClass().getName());
    }
}

