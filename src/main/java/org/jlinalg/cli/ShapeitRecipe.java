/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Closed common-stage schema; arbitrary program paths, extra engine flags and reflected classes are not accepted. */
final class ShapeitRecipe {
    private ShapeitRecipe() { }
    record Spec(Path source,ShapeitRawIndex.Identity identity,ShapeitCommonPhasing.Request request) {
        void verify()throws IOException{if(!identity.equals(ShapeitRawIndex.identity(source,2097152,()->{})))throw new IOException("SHAPEIT recipe changed; no public result is admitted");}
    }
    static Spec read(Path supplied,Path home)throws IOException{
        Path source=supplied.toAbsolutePath().normalize();var identity=ShapeitRawIndex.identity(source,2097152,()->{});
        byte[] raw;try(var input=Files.newInputStream(source)){raw=input.readNBytes(2097153);}if(raw.length>2097152)throw new IOException("SHAPEIT recipe exceeds byte capacity");
        Map<?,?> value=object(SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString()));
        keys(value,Set.of("schema_version","profile","target","reference","scaffold","map","contig","region","parameters","limits"));
        if(integer(value.get("schema_version"))!=1||!ShapeitRuntime.PROFILE.equals(text(value,"profile")))throw new ShapeitRuntime.Unavailable("Unsupported SHAPEIT schema/profile; no historical build is substituted");
        Map<?,?> parameters=object(value.get("parameters"));keys(parameters,Set.of("snps_only","minimum_maf","seed","threads","count_policy","missing_policy"));
        Object snps=parameters.get("snps_only");if(!(snps instanceof Boolean))throw new IOException("Require boolean SHAPEIT parameters.snps_only");
        Double maf=null;if(parameters.containsKey("minimum_maf")){if(!(parameters.get("minimum_maf") instanceof Number n)||!Double.isFinite(n.doubleValue()))throw new IOException("Require finite numeric SHAPEIT minimum_maf");maf=n.doubleValue();}
        ShapeitCommonPhasing.CountPolicy counts;ShapeitCommonPhasing.MissingPolicy missing;
        try{counts=ShapeitCommonPhasing.CountPolicy.valueOf(text(parameters,"count_policy"));missing=ShapeitCommonPhasing.MissingPolicy.valueOf(text(parameters,"missing_policy"));}
        catch(IllegalArgumentException invalid){throw new IOException("Unknown explicit SHAPEIT count/missing policy",invalid);}
        var limits=ShapeitCommonPhasing.Limits.defaults();if(value.containsKey("limits"))limits=(ShapeitCommonPhasing.Limits)override(limits,value.get("limits"));
        var request=new ShapeitCommonPhasing.Request(home,resolve(source,value,"target",true),resolve(source,value,"reference",false),resolve(source,value,"scaffold",false),resolve(source,value,"map",false),text(value,"contig"),text(value,"region"),(Boolean)snps,maf,Math.toIntExact(integer(parameters.get("seed"))),Math.toIntExact(integer(parameters.get("threads"))),counts,missing,limits);
        var spec=new Spec(source,identity,request);spec.verify();return spec;
    }
    private static Object override(Object defaults,Object supplied)throws IOException{
        Class<?> type=defaults.getClass();if(!type.isRecord()||!(type.getName().startsWith("org.jlinalg.cli.Shapeit")||type.getName().startsWith("org.jlinalg.genetics.Shapeit")))throw new IOException("Unsupported SHAPEIT capacity record");
        Map<?,?> values=object(supplied);var fields=type.getRecordComponents();var allowed=new HashSet<String>();for(var field:fields)allowed.add(field.getName());keys(values,allowed);
        Object[] arguments=new Object[fields.length];Class<?>[] types=new Class<?>[fields.length];
        try{
            for(int i=0;i<fields.length;i++){
                var field=fields[i];types[i]=field.getType();Object base=field.getAccessor().invoke(defaults);
                if(!values.containsKey(field.getName()))arguments[i]=base;
                else if(field.getType().isRecord())arguments[i]=override(base,values.get(field.getName()));
                else if(field.getType()==int.class)arguments[i]=Math.toIntExact(integer(values.get(field.getName())));
                else if(field.getType()==long.class)arguments[i]=integer(values.get(field.getName()));
                else throw new IOException("Unsupported SHAPEIT capacity field type");
            }
            return type.getDeclaredConstructor(types).newInstance(arguments);
        }catch(ReflectiveOperationException|ArithmeticException invalid){Throwable cause=invalid instanceof InvocationTargetException nested?nested.getCause():invalid;throw new IOException("Invalid complete SHAPEIT capacities: "+cause,cause);}
    }
    private static Path resolve(Path recipe,Map<?,?> values,String key,boolean required)throws IOException{
        if(!values.containsKey(key)){if(required)throw new IOException("Missing SHAPEIT recipe input: "+key);return null;}String name=text(values,key);Path path=Path.of(name);return (path.isAbsolute()?path:recipe.getParent().resolve(path)).toAbsolutePath().normalize();
    }
    private static Map<?,?> object(Object value)throws IOException{if(!(value instanceof Map<?,?> map))throw new IOException("Require SHAPEIT JSON object");return map;}
    private static String text(Map<?,?> value,String key)throws IOException{if(!(value.get(key) instanceof String text)||text.isBlank()||text.codePoints().anyMatch(Character::isISOControl))throw new IOException("Missing/invalid SHAPEIT recipe text: "+key);return text;}
    private static void keys(Map<?,?> map,Set<String> allowed)throws IOException{for(Object key:map.keySet())if(!(key instanceof String)||!allowed.contains(key))throw new IOException("Unknown SHAPEIT schema field: "+key);}
    private static long integer(Object value)throws IOException{if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=n.longValue()||Math.abs(n.doubleValue())>9007199254740991L)throw new IOException("Require an exact bounded SHAPEIT integer");return n.longValue();}
}
