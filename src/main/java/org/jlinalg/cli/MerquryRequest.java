/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;

final class MerquryRequest {
    private MerquryRequest() { }
    record Input(String role,Path path,String kind) { }
    record Capacity(long inputBytes,long decodedBytes,long bases,long records,int recordBases,int lineCharacters,int distinctKmers,long outputBytes,int files,long wallMillis) { }
    record Settings(int k,int threads,Integer memoryGib,Boolean filterParents,boolean falseDuplications) { }
    record Spec(Map<String,Object> value,String edition,String mode,String preparation,Path runtime,List<Input> inputs,Settings settings,Capacity capacity,NextflowProcess.Limits process) { }
    static Path absolute(Object raw)throws IOException {
        String text=AllcoolsPosteriorRequest.scalar(raw,32768);Path p=Path.of(text);require(p.isAbsolute()&&p.normalize().equals(p),"Canonical absolute Merqury input/runtime path required");return p;
    }
    static Spec read(Path path)throws IOException {return parse(json(path,1048576));}
    static Spec parse(Object raw)throws IOException {
        var v=object(raw);keys(v,"schema_version","edition","mode","preparation","runtime","inputs","settings","capacity","process");integer(v.get("schema_version"),1,1);
        String edition=text(v.get("edition")),mode=text(v.get("mode"));require(Set.of("1.1","1.3","1.4.1").contains(edition),"Select an explicit original source edition 1.1, 1.3 or 1.4.1 and its exact named runtime profile; do not infer an author version or a native source-modification choice");
        require(Set.of("evaluate","count-kmers","build-hapmers").contains(mode),"Declare evaluate, count-kmers or build-hapmers");
        var inputs=new ArrayList<Input>();
        for(Object item:list(v.get("inputs"))) {
            var i=object(item);keys(i,"role","path","kind");String role=text(i.get("role")),kind=text(i.get("kind"));require(Set.of("meryl","fasta","fastx").contains(kind),"Declare input kind meryl, fasta or fastx");inputs.add(new Input(role,absolute(i.get("path")),kind));
        }
        require(!inputs.isEmpty(),"Complete declared input population required");var roles=inputs.stream().map(Input::role).toList();
        if(mode.equals("count-kmers"))require(inputs.stream().allMatch(i->i.role().equals("reads")&&i.kind().equals("fastx")),"Count inputs must all be explicit FASTX read sets; repeated files/identities are not deduplicated");
        else if(mode.equals("evaluate")) {
            require(roles.equals(List.of("reads","assembly1"))||roles.equals(List.of("reads","assembly1","assembly2")),"Evaluate requires ordered reads, assembly1, optional assembly2");
            require(inputs.get(0).kind().equals("meryl")&&inputs.subList(1,inputs.size()).stream().allMatch(i->i.kind().equals("fasta")),"Evaluate requires a complete read Meryl database and complete assembly FASTAs");
        }else require((roles.equals(List.of("mat","pat"))||roles.equals(List.of("mat","pat","child")))&&inputs.stream().allMatch(i->i.kind().equals("meryl")),"Hapmers requires ordered complete mat, pat and optional child Meryl databases");
        var s=object(v.get("settings"));keys(s,"k","threads","count_memory_gib","filter_parents","false_duplications");
        int k=Math.toIntExact(integer(s.get("k"),1,64)),threads=Math.toIntExact(integer(s.get("threads"),1,256));
        Integer memory=s.get("count_memory_gib")==null?null:Math.toIntExact(integer(s.get("count_memory_gib"),1,1024));Boolean filter=s.get("filter_parents")==null?null:bool(s.get("filter_parents"));boolean duplicate=bool(s.get("false_duplications"));
        require((memory!=null)==mode.equals("count-kmers")&&(filter!=null)==mode.equals("build-hapmers")&&(!duplicate||mode.equals("evaluate")),"Declare only settings used by the selected mode; irrelevant memory/filter values must be null");
        require(!mode.equals("build-hapmers")||!edition.equals("1.1")||Boolean.TRUE.equals(filter),"Merqury1.1 has no -no-filt parent option; request its original filtered behavior");
        var c=object(v.get("capacity"));keys(c,"input_bytes","decoded_bytes","bases","records","record_bases","line_characters","distinct_kmers","output_bytes","files","wall_millis");
        var cap=new Capacity(integer(c.get("input_bytes"),1,1000000000000L),integer(c.get("decoded_bytes"),1,1000000000000L),integer(c.get("bases"),1,1000000000000L),integer(c.get("records"),1,Integer.MAX_VALUE-8),Math.toIntExact(integer(c.get("record_bases"),1,Integer.MAX_VALUE-8)),Math.toIntExact(integer(c.get("line_characters"),1,Integer.MAX_VALUE-8)),Math.toIntExact(integer(c.get("distinct_kmers"),1,Integer.MAX_VALUE-8)),integer(c.get("output_bytes"),1,1000000000000L),Math.toIntExact(integer(c.get("files"),16,1000000)),integer(c.get("wall_millis"),1,604800000));
        var p=object(v.get("process"));keys(p,"wall_millis","address_space_bytes","cpu_seconds","file_bytes","open_files","workspace_bytes","file_count","processes","threads","sampled_rss_bytes","monitor_millis");
        var limits=new NextflowProcess.Limits(integer(p.get("wall_millis"),1,cap.wallMillis()),integer(p.get("address_space_bytes"),16777216,1099511627776L),integer(p.get("cpu_seconds"),1,604800),integer(p.get("file_bytes"),1,cap.outputBytes()),Math.toIntExact(integer(p.get("open_files"),8,1048576)),integer(p.get("workspace_bytes"),1,1000000000000L),Math.toIntExact(integer(p.get("file_count"),4,cap.files())),Math.toIntExact(integer(p.get("processes"),1,4096)),Math.toIntExact(integer(p.get("threads"),1,1048576)),integer(p.get("sampled_rss_bytes"),1,1099511627776L),Math.toIntExact(integer(p.get("monitor_millis"),1,1000)));
        require(limits.threads()>=threads,"Owned thread capacity must cover requested Meryl worker threads");
        require(!mode.equals("count-kmers")||inputs.size()<2||limits.openFiles()>=32000,"Original union_sum.sh explicitly raises the soft open-file limit to32000 for multiple read inputs; declare process.open_files >=32000 rather than overriding its source behavior");
        var copy=new LinkedHashMap<String,Object>();v.forEach((key,value)->copy.put(key.toString(),value));
        return new Spec(Collections.unmodifiableMap(copy),edition,mode,AllcoolsPosteriorRequest.scalar(v.get("preparation"),8192),absolute(v.get("runtime")),List.copyOf(inputs),new Settings(k,threads,memory,filter,duplicate),cap,limits);
    }
    private static boolean bool(Object value)throws IOException {require(value instanceof Boolean,"Explicit boolean required");return(Boolean)value;}
}
