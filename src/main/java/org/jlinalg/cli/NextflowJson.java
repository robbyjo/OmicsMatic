/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Owned diagnostic records and lossless parameter JSON; no user-selected types. */
final class NextflowJson {
    private NextflowJson() { }
    static String json(Object source)throws IOException{return encode(source,true,0);}
    static String parameters(Object source)throws IOException{return encode(source,false,0);}
    private static String encode(Object value,boolean canonical,int depth)throws IOException {
        if(depth>80)throw new IOException("Nextflow JSON nesting capacity exceeded");
        if(value==null)return "null";
        if(value instanceof NextflowParameters.NumberToken token)return token.text();
        if(value instanceof String text)return quote(text);
        if(value instanceof Path||value instanceof Enum<?>)return quote(value.toString());
        if(value instanceof Boolean)return value.toString();
        if(value instanceof Number number){
            if(!(number instanceof Byte||number instanceof Short||number instanceof Integer||number instanceof Long||number instanceof Float||number instanceof Double||number instanceof BigInteger||number instanceof BigDecimal))throw new IOException("Unsupported Nextflow numeric class");
            if(number instanceof Double d&&!Double.isFinite(d)||number instanceof Float f&&!Float.isFinite(f))throw new IOException("Nonfinite Nextflow JSON number");
            return number.toString();
        }
        if(value instanceof Map<?,?> map){
            Map<String,Object> checked=canonical?new TreeMap<>():new LinkedHashMap<>();
            for(var entry:map.entrySet()){if(!(entry.getKey() instanceof String key)||checked.containsKey(key))throw new IOException("Nextflow JSON requires unique string keys");checked.put(key,entry.getValue());}
            var out=new StringBuilder("{");for(var entry:checked.entrySet()){if(out.length()>1)out.append(',');out.append(quote(entry.getKey())).append(':').append(encode(entry.getValue(),canonical,depth+1));}return out.append('}').toString();
        }
        if(value instanceof Iterable<?> list){var out=new StringBuilder("[");for(Object item:list){if(out.length()>1)out.append(',');out.append(encode(item,canonical,depth+1));}return out.append(']').toString();}
        if(value.getClass().isArray()){var out=new StringBuilder("[");for(int i=0;i<Array.getLength(value);i++){if(i>0)out.append(',');out.append(encode(Array.get(value,i),canonical,depth+1));}return out.append(']').toString();}
        String name=value.getClass().getName();
        if(value.getClass().isRecord()&&(name.startsWith("org.jlinalg.cli.Nextflow")||name.startsWith("org.jlinalg.workflow.Nextflow"))){
            var fields=new LinkedHashMap<String,Object>();
            try{for(var field:value.getClass().getRecordComponents())fields.put(field.getName(),field.getAccessor().invoke(value));}
            catch(IllegalAccessException|InvocationTargetException invalid){throw new IOException("Cannot encode owned Nextflow record",invalid);}
            return encode(fields,canonical,depth+1);
        }
        throw new IOException("Unsupported Nextflow JSON class: "+name);
    }
    private static String quote(String value)throws IOException {
        NextflowParameters.unicode(value);var out=new StringBuilder("\"");
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c=='"'||c=='\\')out.append('\\').append(c);else if(c<32)out.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)c));else out.append(c);}
        return out.append('"').toString();
    }
}
