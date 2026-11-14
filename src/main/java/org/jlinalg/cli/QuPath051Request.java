/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Exact caller-script requests; never infer a QuPath version, ROI, stain or classifier. */
final class QuPath051Request {
    private QuPath051Request() { }
    record Request(Path script,Path provider,Path out,Map<String,Path> inputs,List<String> classes,
                   Map<String,Long> outputs,String provenance,String population,String expectedReturn,
                   boolean execute,boolean trusted,int seconds,int heap,long inputBytes,long outputBytes,int files) { }
    static Request parse(Map<String,Object> m,Path parent)throws IOException {
        allowed(m,"schema_version","version","mode","script","provider_directory","inputs","required_classes","expected_outputs",
                "input_provenance","analysis_population","expected_return","execution_policy","trusted_code_policy","out",
                "timeout_seconds","heap_megabytes","maximum_input_bytes","maximum_output_bytes","maximum_files");
        NextflowFiles.integer(m.get("schema_version"),1,1);
        require(text(m.get("version")).equals("0.5.1")&&text(m.get("mode")).equals("SCRIPT"),"Require explicit QuPath0.5.1 SCRIPT; other editions are separate");
        var inputs=new LinkedHashMap<String,Path>();var folded=new HashSet<String>();
        for(var e:object(m.get("inputs")).entrySet()) {
            String n=member(e.getKey());require(inputs.size()<4096&&folded.add(n.toLowerCase(Locale.ROOT)),"Duplicate portable logical input or excessive population");inputs.put(n,path(parent,e.getValue()));
        }
        noAncestors(inputs.keySet());var classes=new ArrayList<String>();var seen=new HashSet<String>();
        for(Object value:NextflowFiles.list(m.get("required_classes"))) {
            String n=label(value);require(n.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")&&seen.add(n)&&classes.size()<4096,"Invalid/duplicate/excessive required original class");classes.add(n);
        }
        var outputs=new LinkedHashMap<String,Long>();folded.clear();
        for(var e:object(m.get("expected_outputs")).entrySet()) {
            String n=member(e.getKey());require(outputs.size()<4096&&folded.add(n.toLowerCase(Locale.ROOT)),"Duplicate portable expected output or excessive population");
            var spec=object(e.getValue());allowed(spec,"minimum_bytes");outputs.put(n,number(spec,"minimum_bytes",1,0,137438953472L));
        }
        require(!outputs.isEmpty(),"Declare every expected original result file");noAncestors(outputs.keySet());
        String marker=label(m.get("expected_return"));require(marker.length()<=4096,"Complete original return marker exceeds capacity");
        return new Request(path(parent,m.get("script")),path(parent,m.get("provider_directory")),path(parent,m.get("out")),
                Collections.unmodifiableMap(inputs),List.copyOf(classes),Collections.unmodifiableMap(outputs),label(m.get("input_provenance")),
                label(m.get("analysis_population")),marker,policy(m,"execution_policy"),policy(m,"trusted_code_policy"),
                (int)number(m,"timeout_seconds",900,1,604800),(int)number(m,"heap_megabytes",2048,64,1048576),
                number(m,"maximum_input_bytes",1073741824L,1,137438953472L),number(m,"maximum_output_bytes",2147483648L,1,137438953472L),
                (int)number(m,"maximum_files",20000,32,1000000));
    }
    static Map<String,Object> object(Object value)throws IOException {var result=new LinkedHashMap<String,Object>();for(var e:NextflowFiles.object(value).entrySet())result.put(text(e.getKey()),e.getValue());return result;}
    static String text(Object value)throws IOException{return NextflowFiles.text(value);}
    static String label(Object value)throws IOException {String s=text(value);require(!s.isEmpty()&&s.length()<=32768&&s.chars().noneMatch(c->c<32||c==127),"Require a nonempty literal label without control characters");return s;}
    static Path path(Path parent,Object value)throws IOException {Path p=Path.of(label(value));return (p.isAbsolute()?p:parent.resolve(p)).toAbsolutePath().normalize();}
    static long number(Map<String,Object> m,String key,long fallback,long min,long max)throws IOException{return m.containsKey(key)?NextflowFiles.integer(m.get(key),min,max):fallback;}
    static String member(String name)throws IOException {NextflowFiles.member(name);require(name.length()<=1024&&name.matches("[A-Za-z0-9][A-Za-z0-9_.-]*(/[A-Za-z0-9][A-Za-z0-9_.-]*)*"),"Use portable relative logical input/output file names; identifiers inside files remain literal");return name;}
    private static void noAncestors(Set<String> names)throws IOException {for(String n:names)for(String other:names)require(n.equals(other)||!other.toLowerCase(Locale.ROOT).startsWith(n.toLowerCase(Locale.ROOT)+"/"),"Logical files overlap as directories");}
    private static boolean policy(Map<String,Object> m,String key)throws IOException {String p=text(m.get(key));require(Set.of("ALLOW","FORBID").contains(p),"Declare ALLOW or FORBID: "+key);return p.equals("ALLOW");}
    static void allowed(Map<String,Object> m,String... keys)throws IOException {var valid=Set.of(keys);for(String key:m.keySet())require(valid.contains(key),"Unknown request field: "+key);}
    static void require(boolean value,String message)throws IOException {if(!value)throw new IOException(message);}
}
