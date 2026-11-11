/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

/** JSON for owned native mash records; input cannot choose a reflected class. */
final class MashJson {
    private MashJson() { }
    static String json(Object value)throws IOException {return encode(value,0);}
    private static String encode(Object value,int depth)throws IOException {
        if(depth>64)throw new IOException("Mash diagnostic nesting capacity exceeded");
        if(value==null)return "null";if(value instanceof Boolean)return value.toString();
        if(value instanceof Number number){if(!(number instanceof Byte||number instanceof Short||number instanceof Integer||number instanceof Long||number instanceof Double||number instanceof Float)||!Double.isFinite(number.doubleValue()))throw new IOException("Unsupported/nonfinite mash diagnostic number");return number.toString();}
        if(value instanceof String text)return quote(text);
        if(value instanceof Map<?,?> map){var sorted=new TreeMap<String,Object>();for(var entry:map.entrySet()){if(!(entry.getKey() instanceof String key)||sorted.containsKey(key))throw new IOException("Require unique mash JSON string keys");sorted.put(key,entry.getValue());}var parts=new ArrayList<String>();for(var entry:sorted.entrySet())parts.add(quote(entry.getKey())+":"+encode(entry.getValue(),depth+1));return "{"+String.join(",",parts)+"}";}
        if(value instanceof Iterable<?> list){var parts=new ArrayList<String>();for(Object item:list)parts.add(encode(item,depth+1));return "["+String.join(",",parts)+"]";}
        String name=value.getClass().getName();
        if(value.getClass().isRecord()&&(name.startsWith("org.jlinalg.cli.Mash")||name.startsWith("org.jlinalg.shrinkage.Mash"))){var map=new TreeMap<String,Object>();try{for(var field:value.getClass().getRecordComponents())map.put(field.getName(),field.getAccessor().invoke(value));}catch(InvocationTargetException|IllegalAccessException error){throw new IOException("Cannot encode owned mash record",error);}return encode(map,depth+1);}
        throw new IOException("Unsupported mash JSON diagnostic class");
    }
    private static String quote(String value){StringBuilder text=new StringBuilder("\"");for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c=='"'||c=='\\')text.append('\\').append(c);else if(c<32)text.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)c));else text.append(c);}return text.append('"').toString();}
}
