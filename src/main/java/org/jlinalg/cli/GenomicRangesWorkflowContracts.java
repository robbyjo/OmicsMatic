/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Closed requests for original prepared-GRanges operations, without implicit recipes. */
final class GenomicRangesWorkflowContracts {
    private GenomicRangesWorkflowContracts() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
        Unavailable(String message,Throwable cause){super(message,cause);}
    }
    static void require(boolean value,String message)throws IOException {if(!value)throw new IOException(message);}
    static Map<String,Object> map(Object value)throws IOException {return GenomicRangesWorkflowFiles.map(value);}
    static String identifier(String value)throws IOException {
        require(value!=null&&!value.isEmpty()&&value.indexOf('\0')<0&&value.indexOf('\n')<0&&value.indexOf('\r')<0&&value.indexOf('\t')<0&&java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value),"Require a literal nonempty UTF-8 identifier without control delimiters");return value;
    }
    static String string(Object value)throws IOException {require(value instanceof String,"Require an explicit literal string");return identifier((String)value);}
    static long integer(Object value,long minimum,long maximum)throws IOException {
        require(value instanceof Number,"Require an explicit integer");Number n=(Number)value;long l=n.longValue();
        require(Double.isFinite(n.doubleValue())&&n.doubleValue()==l&&l>=minimum&&l<=maximum,"Integer is nonintegral or outside declared capacity");return l;
    }
    static Path path(Path base,Object value)throws IOException {return base.resolve(string(value)).toAbsolutePath().normalize();}
    static final Set<String> BINARY=Set.of("find-overlaps","subset-overlaps","nearest","distance-to-nearest");
    static final Map<String,Map<String,String>> OPTIONS=Map.of(
        "find-overlaps",Map.of("maxgap","integer","minoverlap","integer","type","string","select","string","ignore.strand","boolean"),
        "subset-overlaps",Map.of("maxgap","integer","minoverlap","integer","type","string","ignore.strand","boolean","invert","boolean"),
        "nearest",Map.of("select","string","ignore.strand","boolean"),
        "distance-to-nearest",Map.of("select","string","ignore.strand","boolean"),
        "promoters",Map.of("upstream","number-vector","downstream","number-vector","use.names","boolean"),
        "out-of-bounds",Map.of(),
        "reduce",Map.of("drop.empty.ranges","boolean","min.gapwidth","integer","with.revmap","boolean","with.inframe.attrib","boolean","ignore.strand","boolean"),
        "disjoin",Map.of("with.revmap","boolean","ignore.strand","boolean"));
    static final Set<String> LIMITS=Set.of("maximum_input_bytes","maximum_member_bytes","maximum_output_bytes","maximum_files","maximum_rows","maximum_pairs","timeout_seconds");

    static Map<String,Object> validate(Map<String,Object> supplied,Path base)throws IOException {
        require(supplied.keySet().equals(Set.of("schema_version","operation","profile","rscript","r_libraries","input_rds","subject_mode","options","limits")),"Unknown or missing GenomicRanges request fields");
        integer(supplied.get("schema_version"),1,1);String op=string(supplied.get("operation"));require(OPTIONS.containsKey(op),"Unsupported explicit GenomicRanges operation");
        String profile=string(supplied.get("profile"));GenomicRangesWorkflowRuntime.profile(profile);
        String mode=string(supplied.get("subject_mode"));require(BINARY.contains(op)?Set.of("provided","self").contains(mode):mode.equals("unused"),"Declare provided/self subject for binary operations,unused for unary operations");
        Map<String,Object> limits=map(supplied.get("limits"));require(limits.keySet().equals(LIMITS),"Unknown or missing explicit capacity limits");
        var normalizedLimits=new LinkedHashMap<String,Object>();
        for(String key:new java.util.TreeSet<>(LIMITS)){long max=switch(key){case "timeout_seconds"->86400;case "maximum_files"->100000;case "maximum_rows"->100000000;case "maximum_pairs"->1000000000000L;default->17179869184L;};normalizedLimits.put(key,integer(limits.get(key),Set.of("maximum_rows","maximum_pairs").contains(key)?0:1,max));}
        require(((Number)normalizedLimits.get("maximum_member_bytes")).longValue()<=((Number)normalizedLimits.get("maximum_output_bytes")).longValue(),"Member capacity exceeds total output capacity");
        var options=map(supplied.get("options"));require(options.keySet().equals(OPTIONS.get(op).keySet()),"Every operation option must be explicit;no extra options or inferred defaults");
        var normalizedOptions=new LinkedHashMap<String,Object>();
        for(var entry:new java.util.TreeMap<>(OPTIONS.get(op)).entrySet()) {
            String key=entry.getKey(),type=entry.getValue();Object value=options.get(key);
            if(type.equals("boolean")){require(value instanceof Boolean,"Require explicit boolean option: "+key);normalizedOptions.put(key,value);}
            else if(type.equals("string")){String text=string(value);Set<String> allowed=key.equals("type")?Set.of("any","start","end","within","equal"):op.equals("find-overlaps")?Set.of("all","first","last","arbitrary"):Set.of("arbitrary","all");require(allowed.contains(text),"Unknown literal operation option: "+key);normalizedOptions.put(key,text);}
            else if(type.equals("number-vector")){
                if(value instanceof List<?> items){require(items.size()<=1000000,"Promoter vector option exceeds transport capacity");var values=new ArrayList<Number>();for(Object item:items){require(item instanceof Number&&Double.isFinite(((Number)item).doubleValue()),"Promoter distances require explicit finite numeric values");values.add((Number)item);}normalizedOptions.put(key,List.copyOf(values));}
                else{require(value instanceof Number&&Double.isFinite(((Number)value).doubleValue()),"Promoter distances require explicit finite numeric values");normalizedOptions.put(key,value);}
            }
            else normalizedOptions.put(key,integer(value,-2147483647L,2147483647L));
        }
        Object libraryValue=supplied.get("r_libraries");require(libraryValue instanceof List<?>,"Require explicit ordered private R libraries");var libraries=new ArrayList<String>();var seen=new HashSet<Path>();
        for(Object item:(List<?>)libraryValue){Path p=path(base,item);require(seen.add(p),"Duplicate declared R library");libraries.add(p.toString());}
        require(!libraries.isEmpty()&&libraries.size()<=32,"Supply one to32 ordered permitted private R libraries");
        var result=new LinkedHashMap<String,Object>();result.put("schema_version",1);result.put("operation",op);result.put("profile",profile);result.put("rscript",path(base,supplied.get("rscript")).toString());result.put("r_libraries",List.copyOf(libraries));result.put("input_rds",path(base,supplied.get("input_rds")).toString());result.put("subject_mode",mode);result.put("options",normalizedOptions);result.put("limits",normalizedLimits);return result;
    }
}
