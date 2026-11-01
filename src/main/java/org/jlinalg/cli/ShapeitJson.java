/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

/** JSON for owned SHAPEIT records; data cannot select a reflected class. */
final class ShapeitJson {
    private ShapeitJson() { }
    static String json(Object value) throws IOException { return encode(value,0); }
    private static String encode(Object value,int depth) throws IOException {
        if(depth>128)throw new IOException("SHAPEIT diagnostic nesting capacity exceeded");
        if(value==null)return "null";
        if(value instanceof Boolean)return value.toString();
        if(value instanceof Number number){
            if(!(number instanceof Byte||number instanceof Short||number instanceof Integer||number instanceof Long||number instanceof Float||number instanceof Double)
                    ||!Double.isFinite(number.doubleValue()))throw new IOException("Unsupported/nonfinite SHAPEIT diagnostic number");
            return number.toString();
        }
        if(value instanceof String text)return quote(text);
        if(value instanceof Path||value instanceof Enum<?>)return quote(value.toString());
        if(value instanceof Map<?,?> map){
            var sorted=new TreeMap<String,Object>();for(var entry:map.entrySet()){
                if(!(entry.getKey() instanceof String key)||sorted.containsKey(key))throw new IOException("Require unique SHAPEIT diagnostic string keys");sorted.put(key,entry.getValue());
            }
            var parts=new ArrayList<String>();for(var entry:sorted.entrySet())parts.add(quote(entry.getKey())+":"+encode(entry.getValue(),depth+1));return "{"+String.join(",",parts)+"}";
        }
        if(value instanceof Iterable<?> list){var parts=new ArrayList<String>();for(Object item:list)parts.add(encode(item,depth+1));return "["+String.join(",",parts)+"]";}
        String name=value.getClass().getName();
        if(value.getClass().isRecord()&&(name.startsWith("org.jlinalg.cli.Shapeit")||name.startsWith("org.jlinalg.genetics.Shapeit"))){
            var map=new TreeMap<String,Object>();
            try{for(var component:value.getClass().getRecordComponents())map.put(component.getName(),component.getAccessor().invoke(value));}
            catch(IllegalAccessException|InvocationTargetException invalid){throw new IOException("Cannot encode owned SHAPEIT record",invalid);}
            return encode(map,depth+1);
        }
        throw new IOException("Unsupported SHAPEIT diagnostic class: "+name);
    }
    private static String quote(String text){
        var out=new StringBuilder("\"");for(int i=0;i<text.length();i++){
            char c=text.charAt(i);if(c=='"'||c=='\\')out.append('\\').append(c);
            else if(c<32)out.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)c));else out.append(c);
        }
        return out.append('"').toString();
    }
}
