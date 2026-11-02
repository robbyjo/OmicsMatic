/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Closed, explicit original fastp recipes and portable captured-input replays. */
final class FastpRecipe {
    private FastpRecipe() { }
    static final int MAXIMUM_BYTES=2_097_152;
    record Limits(FastpOriginalStage.Limits stage,FastpOutputAudit.Limits audit) { }
    record Spec(Path source,String profile,Path first,Path second,FastpFastq.Encoding encoding,FastpFastq.Alphabet alphabet,
                FastpFastq.Plus plus,FastpFastq.Pairing pairing,FastpParameters.Options options,Limits limits) { }
    static Limits defaults(){return new Limits(new FastpOriginalStage.Limits(FastpFastq.Limits.defaults(),new FastpProcess.Limits(3_600_000,8L<<30,3600,4L<<30,256,16L<<30,1024,16,64,6L<<30,100),32L<<30,10000),new FastpOutputAudit.Limits(new FastpMetrics.Limits(1_000_000,16_777_216),new FastpSort.Limits(16_777_216,16_000_000,100_000_000,100000),new FastpJoin.Limits(4096,16_777_216,1_000_000_000_000L,100_000_000),16_777_216));}
    static Spec parse(byte[] raw,Path source)throws IOException {
        try{
            Map<?,?> r=FastpRuntime.object(SimpleJson.parse(FastpFiles.utf8(raw)));
            for(String unsupported:List.of("merge","umi","split","stdin","adapterFasta","indexFilter"))if(r.containsKey(unsupported))throw new FastpRuntime.Unavailable("Requested fastp capability is outside this typed contract: "+unsupported+";skip dependent analysis rather than silently dropping it");
            keys(r,Set.of("schema_version","profile","inputs","policies","options","limits"),Set.of("limits"));
            if(FastpRuntime.integer(r.get("schema_version"),1,Integer.MAX_VALUE)!=1)throw new FastpRuntime.Unavailable("Unsupported fastp recipe schema;no fallback selected");
            String profile=FastpChoices.text(r,"profile");FastpRuntime.locked(profile);
            Map<?,?> inputs=FastpRuntime.object(r.get("inputs")),policies=FastpChoices.map(r.get("policies"),Set.of("encoding","alphabet","plus","pairing"));keys(inputs,Set.of("read1","read2"),Set.of("read2"));
            Path first=resolve(source,pathText(inputs,"read1")),second=inputs.containsKey("read2")?resolve(source,pathText(inputs,"read2")):null;
            var encoding=enumeration(FastpFastq.Encoding.class,policies,"encoding");var alphabet=enumeration(FastpFastq.Alphabet.class,policies,"alphabet");var plus=enumeration(FastpFastq.Plus.class,policies,"plus");var pairing=enumeration(FastpFastq.Pairing.class,policies,"pairing");
            var options=FastpChoices.options(r.get("options"));var limits=r.containsKey("limits")?limits(r.get("limits")):defaults();
            String version=FastpChoices.text(FastpRuntime.locked(profile),"version");FastpParameters.arguments(version,second!=null,encoding,options,"/captured/read1",second==null?null:"/captured/read2");
            return new Spec(source.toAbsolutePath().normalize(),profile,first,second,encoding,alphabet,plus,pairing,options,limits);
        }catch(IllegalArgumentException invalid){throw new IOException("Invalid fastp recipe path,enum or capacity",invalid);}
    }
    static Limits limits(Object value)throws IOException {
        try{
            Map<?,?> r=FastpChoices.map(value,Set.of("stage","audit")),a=FastpChoices.map(r.get("audit"),Set.of("statistics","sorting","matching","jsonBytes"));
            Map<?,?> s=FastpChoices.map(a.get("statistics"),Set.of("cycles","maximumResidentBytes")),o=FastpChoices.map(a.get("sorting"),Set.of("memoryBytes","lineCharacters","records","maximumParts")),m=FastpChoices.map(a.get("matching"),Set.of("familyRecords","familyBytes","matchingOperations","solverSteps"));
            return new Limits(FastpChoices.stage(r.get("stage")),new FastpOutputAudit.Limits(new FastpMetrics.Limits(FastpChoices.i(s,"cycles"),FastpChoices.n(s,"maximumResidentBytes")),new FastpSort.Limits(FastpChoices.n(o,"memoryBytes"),FastpChoices.i(o,"lineCharacters"),FastpChoices.n(o,"records"),FastpChoices.i(o,"maximumParts")),new FastpJoin.Limits(FastpChoices.i(m,"familyRecords"),FastpChoices.n(m,"familyBytes"),FastpChoices.n(m,"matchingOperations"),FastpChoices.n(m,"solverSteps")),FastpChoices.i(a,"jsonBytes")));
        }catch(IllegalArgumentException invalid){throw new IOException("Invalid explicit fastp workflow capacities",invalid);}
    }
    static byte[] replay(byte[] raw,Spec spec,List<FastpFiles.Capture> captures)throws IOException {
        var r=new LinkedHashMap<String,Object>();for(var e:FastpRuntime.object(SimpleJson.parse(FastpFiles.utf8(raw))).entrySet())r.put(FastpRuntime.text(e.getKey()),e.getValue());
        var inputs=new LinkedHashMap<String,Object>();for(int i=0;i<captures.size();i++)inputs.put("read"+(i+1),"processing/"+FastpFiles.member(captures.get(i).snapshot()));r.put("inputs",inputs);r.put("limits",spec.limits());return (FastpJson.json(r)+"\n").getBytes(StandardCharsets.UTF_8);
    }
    static Map<String,Object> example(){return Map.of("schema_version",1,"profile","linux-x86_64-fastp-0.23.2","inputs",Map.of("read1","read1.fastq","read2","read2.fastq"),"policies",Map.of("encoding","PHRED33","alphabet","UPPER_ACGTN","plus","EMPTY_ONLY","pairing","TERMINAL_MATE_SUFFIX"),"options",FastpParameters.Options.defaults(1),"limits",defaults());}
    static String pathText(Map<?,?> r,String key)throws IOException {String text=FastpChoices.text(r,key);if(text.isBlank()||text.indexOf(0)>=0)throw new IOException("Require nonblank fastp path");return text;}
    static Path resolve(Path source,String value){Path p=Path.of(value);return(p.isAbsolute()?p:source.toAbsolutePath().getParent().resolve(p)).toAbsolutePath().normalize();}
    static <E extends Enum<E>> E enumeration(Class<E> type,Map<?,?> map,String key)throws IOException {try{return Enum.valueOf(type,FastpChoices.text(map,key));}catch(IllegalArgumentException invalid){throw new IOException("Unknown fastp "+key,invalid);}}
    static void keys(Map<?,?> map,Set<String> all,Set<String> optional)throws IOException {if(!all.containsAll(map.keySet())||!map.keySet().containsAll(all.stream().filter(k->!optional.contains(k)).toList()))throw new IOException("Unknown or missing fastp recipe/record fields: "+map.keySet());}
    static String originalInput(String recipe,String input)throws IOException {
        if(!recipe.startsWith("/"))throw new IOException("Recorded fastp producer recipe must be an absolute Linux path");String source=input.startsWith("/")?input:recipe.substring(0,recipe.lastIndexOf('/')+1)+input;var parts=new java.util.ArrayList<String>();
        for(String part:source.split("/")){if(part.isEmpty()||part.equals("."))continue;if(part.equals("..")){if(!parts.isEmpty())parts.remove(parts.size()-1);}else parts.add(part);}return "/"+String.join("/",parts);
    }
}
