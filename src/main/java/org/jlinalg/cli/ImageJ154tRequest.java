/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Literal full-macro requests; no inferred image axes, image units or Fiji edition. */
final class ImageJ154tRequest {
    private ImageJ154tRequest() { }
    record Request(Path macro,Path out,Map<String,Path> inputs,Path plugins,String pluginProvenance,
                   List<String> commands,Map<String,Object> outputs,String provenance,String population,
                   boolean execute,boolean display,int threads,int seconds,int heap,long inputBytes,
                   long outputBytes,long pixels,int files,String expectedReturn) { }
    static Request parse(Map<String,Object> m,Path parent)throws IOException {
        allowed(m,"schema_version","version","mode","macro","inputs","plugins_directory","plugins_provenance","required_commands","expected_outputs","input_provenance","analysis_population","execution_policy","display_policy","out","threads","timeout_seconds","heap_megabytes","maximum_input_bytes","maximum_output_bytes","maximum_decoded_pixels","maximum_files","expected_return");
        NextflowFiles.integer(m.get("schema_version"),1,1);require(text(m.get("version")).equals("1.54t")&&text(m.get("mode")).equals("MACRO"),"Require explicit original ImageJ core 1.54t and MACRO; Fiji/ImageJ2 versions are separate identities");
        var inputs=new LinkedHashMap<String,Path>();var folded=new HashSet<String>();
        for(var e:object(m.get("inputs")).entrySet()){
            String n=member(e.getKey());require(inputs.size()<4096&&folded.add(n.toLowerCase(Locale.ROOT)),"Duplicate portable input name or excessive inputs");inputs.put(n,path(parent,e.getValue()));
        }
        noAncestors(inputs.keySet());
        Path plugins=m.get("plugins_directory")==null?null:path(parent,m.get("plugins_directory"));
        String pluginProvenance=plugins==null?"No external plugins declared":label(m.get("plugins_provenance"));
        var commands=new ArrayList<String>();var seen=new HashSet<String>();for(Object x:NextflowFiles.list(m.get("required_commands"))){String n=label(x);require(commands.size()<4096&&seen.add(n),"Duplicate/excessive required command");commands.add(n);}
        var outputs=new LinkedHashMap<String,Object>();folded.clear();
        for(var e:object(m.get("expected_outputs")).entrySet()){
            String n=member(e.getKey());require(outputs.size()<4096&&folded.add(n.toLowerCase(Locale.ROOT)),"Duplicate portable output name or excessive outputs");var spec=object(e.getValue());allowed(spec,"kind","minimum_bytes");String kind=text(spec.get("kind"));require(Set.of("TIFF","FILE").contains(kind),"Declare TIFF for complete decoded-pixel auditing, or FILE for byte-preserved non-image output");long minimum=number(spec,"minimum_bytes",1,1,137438953472L);outputs.put(n,Map.of("kind",kind,"minimum_bytes",minimum));
        }
        require(!outputs.isEmpty(),"Declare every expected analysis output");noAncestors(outputs.keySet());
        return new Request(path(parent,m.get("macro")),path(parent,m.get("out")),Collections.unmodifiableMap(inputs),plugins,pluginProvenance,List.copyOf(commands),Collections.unmodifiableMap(outputs),label(m.get("input_provenance")),label(m.get("analysis_population")),policy(m,"execution_policy"),policy(m,"display_policy"),(int)number(m,"threads",1,1,256),(int)number(m,"timeout_seconds",900,1,604800),(int)number(m,"heap_megabytes",2048,64,1048576),number(m,"maximum_input_bytes",1073741824,1,137438953472L),number(m,"maximum_output_bytes",2147483648L,1,137438953472L),number(m,"maximum_decoded_pixels",268435456,1,1000000000000L),(int)number(m,"maximum_files",20000,32,1000000),m.containsKey("expected_return")?text(m.get("expected_return")):null);
    }
    static Map<String,Object> object(Object x)throws IOException {var result=new LinkedHashMap<String,Object>();for(var e:NextflowFiles.object(x).entrySet())result.put(text(e.getKey()),e.getValue());return result;}
    static String text(Object x)throws IOException{return NextflowFiles.text(x);}
    static String label(Object x)throws IOException {String s=text(x);require(!s.isEmpty()&&s.length()<=32768&&s.chars().noneMatch(c->c<32||c==127),"Require a nonempty literal label");return s;}
    static Path path(Path parent,Object x)throws IOException {Path p=Path.of(label(x));return (p.isAbsolute()?p:parent.resolve(p)).toAbsolutePath().normalize();}
    static long number(Map<String,Object> m,String key,long fallback,long min,long max)throws IOException{return m.containsKey(key)?NextflowFiles.integer(m.get(key),min,max):fallback;}
    static String member(String n)throws IOException {NextflowFiles.member(n);require(n.length()<=1024&&n.matches("[A-Za-z0-9][A-Za-z0-9_.-]*(/[A-Za-z0-9][A-Za-z0-9_.-]*)*"),"Use portable relative logical input/output names");return n;}
    private static void noAncestors(Set<String> names)throws IOException {for(String n:names)for(String other:names)require(n.equals(other)||!other.toLowerCase(Locale.ROOT).startsWith(n.toLowerCase(Locale.ROOT)+"/"),"File names overlap as directories");}
    private static boolean policy(Map<String,Object> m,String key)throws IOException {String p=text(m.get(key));require(Set.of("ALLOW","FORBID").contains(p),"Declare ALLOW or FORBID: "+key);return p.equals("ALLOW");}
    static void allowed(Map<String,Object> m,String... keys)throws IOException {var valid=Set.of(keys);for(String k:m.keySet())require(valid.contains(k),"Unknown field: "+k);}
    static void require(boolean value,String message)throws IOException {if(!value)throw new IOException(message);}
}
