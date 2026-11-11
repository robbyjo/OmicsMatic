/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;

/** Lossless JSON for this adapter's owned records; never reflects a type selected by input JSON. */
final class HifiasmJson {
    private HifiasmJson(){ }
    static Object value(Object source)throws IOException{
        if(source==null||source instanceof String||source instanceof Boolean||source instanceof Number||source instanceof NextflowParameters.NumberToken)return source;
        if(source instanceof Character character)return character.toString();
        if(source instanceof OptionalLong optional)return optional.isPresent()?optional.getAsLong():null;
        if(source instanceof Map<?,?> map){var result=new LinkedHashMap<String,Object>();for(var e:map.entrySet()){if(!(e.getKey() instanceof String key))throw new IOException("Hifiasm JSON requires string object keys");result.put(key,value(e.getValue()));}return result;}
        if(source instanceof Iterable<?> list){var result=new ArrayList<Object>();for(Object entry:list)result.add(value(entry));return result;}
        String name=source.getClass().getName();if(source.getClass().isRecord()&&(name.startsWith("org.jlinalg.assembly.Hifiasm")||name.startsWith("org.jlinalg.cli.Hifiasm")||name.startsWith("org.jlinalg.cli.RsemElf$")||name.startsWith("org.jlinalg.cli.RsemProcess$"))){var result=new LinkedHashMap<String,Object>();try{for(var component:source.getClass().getRecordComponents())result.put(component.getName(),value(component.getAccessor().invoke(source)));}catch(IllegalAccessException|InvocationTargetException failure){throw new IOException("Cannot encode owned hifiasm record",failure);}return result;}
        throw new IOException("Unsupported hifiasm audit class: "+name);
    }
    static Map<String,Object> document(Object value)throws IOException{return Monocle3WorkflowRequest.object(NextflowParameters.parse(NextflowJson.json(value(value)),33554432,2000000));}
    static byte[] bytes(Object value)throws IOException{return (NextflowJson.json(value(value))+"\n").getBytes(StandardCharsets.UTF_8);}
}
