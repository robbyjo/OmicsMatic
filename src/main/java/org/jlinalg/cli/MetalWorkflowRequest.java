/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.MetalWorkflowFiles.*;

/** Typed declarations; no inferred paper settings and no free-form interpreter commands. */
final class MetalWorkflowRequest {
    static final Set<String> KEYS=Set.of("executable","executable_sha256","notice","release","execution","studies","scheme","allele_mode","strand","frequency","overlap","z_cutoff","heterogeneity","output_probability","post_genomic_control","warning_policy","hypothesis","max_studies","max_rows","max_metadata_bytes","max_bytes","max_files","max_line_bytes","wall_ms");
    record Config(Map<String,String> values){
        Config{values=Map.copyOf(values);}
        String get(String key){return values.get(key);}
        long number(String key){return Long.parseLong(get(key));}
        int integer(String key){return Math.toIntExact(number(key));}
        boolean on(String key){return get(key).equals("ON");}
        boolean stderr(){return get("scheme").equals("STDERR");}
        boolean post(){return !get("post_genomic_control").equals("OFF");}
    }
    private MetalWorkflowRequest(){ }
    static Config read(Path path)throws IOException {return parse(map(path));}
    static Config parse(Map<String,String> values)throws IOException {
        require(values.keySet().equals(KEYS),"Request requires exactly: "+new TreeSet<>(KEYS));
        for(var e:values.entrySet())require(!e.getValue().isBlank(),"Blank request value: "+e.getKey());
        choice(values,"scheme","STDERR","SAMPLESIZE");choice(values,"execution","allow","forbid");choice(values,"allele_mode","paired","none");
        for(String key:List.of("strand","frequency","overlap","heterogeneity"))choice(values,key,"ON","OFF");
        choice(values,"output_probability","ordinary","log10");choice(values,"warning_policy","forbid","allow-recorded");
        require(values.get("executable_sha256").matches("[0-9a-f]{64}"),"Require explicit lowercase executable SHA-256");
        require(!values.get("hypothesis").isBlank(),"An explicit same-hypothesis declaration is required");
        Config c=new Config(values);require(!c.on("overlap")||!c.stderr(),"Original OVERLAP requires SAMPLESIZE; no substitute STDERR overlap correction");
        require(!c.get("allele_mode").equals("none")||(!c.on("strand")&&!c.on("frequency")),"Gene/non-allelic mode requires strand=OFF and frequency=OFF");
        if(c.on("overlap"))require(number(c.get("z_cutoff"),"z_cutoff")>0,"Require positive overlap Z cutoff");else require(c.get("z_cutoff").equals("-"),"Without overlap z_cutoff must be '-'");
        gc(c.get("post_genomic_control"));
        limit(c,"max_studies",1,1024);limit(c,"max_rows",1,1_000_000_000_000L);limit(c,"max_metadata_bytes",1,1L<<40);limit(c,"max_bytes",1,1L<<40);limit(c,"max_files",1,1_000_000);limit(c,"max_line_bytes",64,16L<<20);limit(c,"wall_ms",1,86_400_000);
        return c;
    }
    static void gc(String raw)throws IOException {if(!Set.of("ON","OFF").contains(raw))require(number(raw,"genomic_control")>0,"Genomic-control value must be positive");}
    private static void choice(Map<String,String> p,String key,String... choices)throws IOException {require(Set.of(choices).contains(p.get(key)),key+" must be one of "+List.of(choices));}
    private static void limit(Config c,String key,long min,long max)throws IOException {String raw=c.get(key);require(raw.matches("[1-9][0-9]*"),"Require a positive integer "+key);long x=Long.parseLong(raw);require(x>=min&&x<=max,"Out-of-range "+key);}
}
