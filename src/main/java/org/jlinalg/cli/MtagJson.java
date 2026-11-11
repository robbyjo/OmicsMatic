/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict owned MTAG metadata; nonfinite scientific values use explicit hexadecimal/text fields. */
final class MtagJson {
    private MtagJson() { }
    static void need(boolean condition,String cause)throws IOException {if(!condition)throw new IOException(cause);}
    static Object read(Path path,int maximum)throws IOException {
        need(Files.isRegularFile(path)&&Files.size(path)<=maximum,"Complete MTAG JSON capacity/path exceeded");
        byte[] raw=Files.readAllBytes(path);need(raw.length<=maximum,"MTAG JSON grew beyond capacity");
        return SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(raw)).toString());
    }
    static Map<?,?> object(Object value)throws IOException {need(value instanceof Map<?,?>,"Explicit MTAG JSON object required");return (Map<?,?>)value;}
    static List<?> list(Object value)throws IOException {need(value instanceof List<?>,"Explicit MTAG JSON array required");return (List<?>)value;}
    static String text(Object value)throws IOException {need(value instanceof String,"Explicit MTAG JSON text required");return (String)value;}
    static boolean bool(Object value)throws IOException {need(value instanceof Boolean,"Explicit MTAG JSON boolean required");return (Boolean)value;}
    static void keys(Map<?,?> map,String... expected)throws IOException {need(map.keySet().equals(Set.of(expected)),"Unknown/missing MTAG fields: "+map.keySet());}
    static double number(Object value)throws IOException {need(value instanceof Number,"Explicit MTAG numeric value required");double d=((Number)value).doubleValue();need(Double.isFinite(d),"Finite explicit MTAG numeric value required");return d;}
    static long integer(Object value,long min,long max)throws IOException {
        double d=number(value);need(d>=min&&d<=max&&d==Math.rint(d),"MTAG integer is outside declared range");
        try{return new BigDecimal(value.toString()).longValueExact();}catch(ArithmeticException error){throw new IOException("Exact MTAG integer required",error);}
    }
    static Map<String,Object> map(Object... pairs) {
        var result=new LinkedHashMap<String,Object>();
        for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return result;
    }
    static void write(Path path,Object value)throws IOException {Files.writeString(path,encode(value)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
    static String encode(Object value)throws IOException {
        if(value==null)return "null";
        if(value instanceof String s)return quote(s);
        if(value instanceof Path p)return quote(p.toString());
        if(value instanceof Boolean)return value.toString();
        if(value instanceof Number n){need(Double.isFinite(n.doubleValue()),"Cannot serialize a nonfinite MTAG diagnostic number");return n.toString();}
        if(value instanceof Map<?,?> m){var parts=new ArrayList<String>();for(var e:m.entrySet())parts.add(quote(text(e.getKey()))+":"+encode(e.getValue()));return "{"+String.join(",",parts)+"}";}
        if(value instanceof Iterable<?> seq){var parts=new ArrayList<String>();for(Object x:seq)parts.add(encode(x));return "["+String.join(",",parts)+"]";}
        throw new IOException("Unsupported MTAG diagnostic type: "+value.getClass().getName());
    }
    private static String quote(String s) {
        var result=new StringBuilder("\"");
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            switch(c){case '"'->result.append("\\\"");case '\\'->result.append("\\\\");case '\n'->result.append("\\n");case '\r'->result.append("\\r");case '\t'->result.append("\\t");default->{if(c<32||Character.isSurrogate(c))result.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)c));else result.append(c);}}
        }
        return result.append('"').toString();
    }
}
