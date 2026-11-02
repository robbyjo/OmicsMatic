/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Closed owned-record JSON; no input-selected reflected class. */
final class Pal2NalJson {
    private Pal2NalJson() { }
    static String json(Object object)throws IOException{return RegenieRuntime.json(value(object));}
    static byte[] bytes(Object object)throws IOException{return (json(object)+"\n").getBytes(StandardCharsets.UTF_8);}
    static void write(Path path,Object object)throws IOException{Files.write(path,bytes(object),StandardOpenOption.CREATE_NEW);}
    static Map<?,?> object(byte[] raw)throws IOException {
        String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
        if(!(SimpleJson.parse(text) instanceof Map<?,?> map))throw new IOException("Require a complete PAL2NAL UTF-8 JSON object");return map;
    }
    static Map<?,?> object(Path path,long maximum)throws IOException {
        IqtreeFiles.identity(path,maximum,()->{});byte[] raw;
        try(var in=Files.newInputStream(path)){raw=in.readNBytes(Math.toIntExact(maximum)+1);}
        if(raw.length>maximum)throw new IOException("PAL2NAL JSON byte capacity exceeded");return object(raw);
    }
    static Object value(Object source)throws IOException {
        if(source==null||source instanceof String||source instanceof Boolean)return source;
        if(source instanceof Number n){
            if(!(n instanceof Byte||n instanceof Short||n instanceof Integer||n instanceof Long||n instanceof Double||n instanceof Float)
                    ||n instanceof Double d&&!Double.isFinite(d)||n instanceof Float f&&!Float.isFinite(f))throw new IOException("Require finite supported PAL2NAL numbers");return n;
        }
        if(source instanceof Enum<?> e)return e.name();if(source instanceof Path p)return p.toString();
        if(source instanceof Map<?,?> map){var values=new TreeMap<String,Object>();for(var entry:map.entrySet()){if(!(entry.getKey() instanceof String s))throw new IOException("PAL2NAL JSON keys must be strings");values.put(s,value(entry.getValue()));}return values;}
        if(source instanceof Collection<?> list){var values=new ArrayList<Object>();for(Object item:list)values.add(value(item));if(source instanceof Set<?>)values.sort(Comparator.comparing(RegenieRuntime::json));return values;}
        String name=source.getClass().getName();
        if(source.getClass().isRecord()&&(name.startsWith("org.jlinalg.cli.Pal2Nal")||name.startsWith("org.jlinalg.sequence.Pal2Nal")||name.startsWith("org.jlinalg.sequence.MafftSequences$")||name.startsWith("org.jlinalg.cli.IqtreeFiles$"))){
            var values=new TreeMap<String,Object>();try{for(var part:source.getClass().getRecordComponents())values.put(part.getName(),value(part.getAccessor().invoke(source)));}
            catch(IllegalAccessException|InvocationTargetException e){throw new IOException("Cannot encode an owned PAL2NAL record",e);}return values;
        }
        throw new IOException("Unsupported PAL2NAL record type: "+name);
    }
}
