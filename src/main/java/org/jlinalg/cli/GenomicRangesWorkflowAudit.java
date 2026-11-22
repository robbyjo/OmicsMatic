/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.GenomicRangesWorkflowContracts.require;

/** Core export and capture relationships; offline verification does not parse/refit RDS. */
final class GenomicRangesWorkflowAudit {
    private GenomicRangesWorkflowAudit() { }
    static long number(String value,long minimum,long maximum)throws IOException {
        require(value.matches("0|-?[1-9][0-9]*"),"Invalid literal integer in source export");try{long n=Long.parseLong(value);require(n>=minimum&&n<=maximum,"Source export integer outside valid range");return n;}catch(NumberFormatException e){throw new IOException("Source export integer overflow",e);}
    }
    static Map<String,Object> outcome(Path root)throws IOException {
        var rows=new ArrayList<String[]>();List<String> header=GenomicRangesWorkflowFiles.rows(root.resolve("worker/status.tsv"),65536,8,1,(i,r)->rows.add(r));
        require(header.equals(List.of("status","detail_hex","calls_completed","source_functions_checked","result_kind","result_length","query_length","subject_length"))&&rows.size()==1,"Invalid worker outcome structure");String[] row=rows.get(0);
        require(Set.of("COMPLETED_WITH_WARNINGS","SKIP3_DEPENDENT_ANALYSIS_SKIPPED","FAIL2_NO_COMPLETE_RESULT").contains(row[0]),"Unknown original worker status");
        var result=new LinkedHashMap<String,Object>();result.put("status",row[0]);result.put("detail",GenomicRangesWorkflowRuntime.unhex(row[1]));result.put("calls_completed",number(row[2],0,1));result.put("source_functions_checked",number(row[3],0,160));result.put("result_kind",row[4]);
        for(int i=5;i<8;i++)result.put(header.get(i),row[i].equals("NA")?null:number(row[i],0,2147483647));return result;
    }
    static Map<String,Object> result(Path root,Map<String,Object> request,Path output)throws IOException {
        var state=outcome(root);require(state.get("status").equals("COMPLETED_WITH_WARNINGS")&&state.get("calls_completed").equals(1L)&&state.get("source_functions_checked").equals(160L),"Original operation lacks a complete source-checked outcome");
        long query=GenomicRangesWorkflowContracts.integer(state.get("query_length"),0,GenomicRangesWorkflowFiles.cap(request,"maximum_rows")),subject=GenomicRangesWorkflowContracts.integer(state.get("subject_length"),0,GenomicRangesWorkflowFiles.cap(request,"maximum_rows")),length=GenomicRangesWorkflowContracts.integer(state.get("result_length"),0,GenomicRangesWorkflowFiles.cap(request,"maximum_rows"));
        String op=request.get("operation").toString(),kind=state.get("result_kind").toString();var opts=GenomicRangesWorkflowFiles.map(request.get("options"));
        String expected=Set.of("promoters","subset-overlaps","reduce","disjoin").contains(op)?"GRANGES":op.equals("out-of-bounds")?"INTEGER_INDICES":op.equals("distance-to-nearest")||"all".equals(opts.get("select"))?"HITS":"INTEGER_SELECTION";
        boolean logical=kind.equals("LOGICAL_SELECTION")&&op.equals("nearest")&&"arbitrary".equals(opts.get("select"))&&subject==0;
        require(kind.equals(expected)||logical,"Original result kind differs from the declared operation/options");
        if(!GenomicRangesWorkflowContracts.BINARY.contains(op))require(subject==0,"Unary operation has a subject population");
        else{if(request.get("subject_mode").equals("self"))require(subject==query,"Self operation node counts differ");require(Math.multiplyExact(query,subject)<=GenomicRangesWorkflowFiles.cap(request,"maximum_pairs"),"Captured candidate-pair capacity exceeded");}
        require(!op.equals("promoters")||length==query,"Promoter output length differs from query");require(!op.equals("subset-overlaps")||length<=query,"Subset result exceeds query population");
        var expectedMembers=new HashSet<>(Set.of("rows.tsv","result.rds"));if(kind.equals("GRANGES"))expectedMembers.add("sequences.tsv");
        try(var paths=Files.list(output)){require(paths.map(p->p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()).equals(expectedMembers),"Missing/extra original result exports");}
        for(String name:expectedMembers)require(Files.isRegularFile(output.resolve(name),LinkOption.NOFOLLOW_LINKS),"Nonregular original result member");require(Files.size(output.resolve("result.rds"))>0,"Missing serialized full original result");
        var seqlevels=new HashSet<String>();long maximumBytes=GenomicRangesWorkflowFiles.cap(request,"maximum_member_bytes");
        if(kind.equals("GRANGES")) {
            var header=GenomicRangesWorkflowFiles.rows(output.resolve("sequences.tsv"),maximumBytes,5,Math.toIntExact(GenomicRangesWorkflowFiles.cap(request,"maximum_rows"))+100000,(i,r)->{
                require(seqlevels.add(GenomicRangesWorkflowRuntime.unhex(r[0])),"Duplicate literal sequence level");if(!r[1].equals("NA"))number(r[1],0,Integer.MAX_VALUE);require(Set.of("TRUE","FALSE","NA").contains(r[2]),"Invalid source circular flag");require(Set.of("NA","VALUE").contains(r[3]),"Invalid source genome-name state");if(r[3].equals("VALUE"))GenomicRangesWorkflowRuntime.unhex(r[4]);else require(r[4].equals("-"),"Missing genome name has a payload");
            },true);require(header.equals(List.of("seqname_hex","length","circular","genome_state","genome_hex")),"Invalid sequence metadata export header");
        }
        final long[] previous={0},count={0};
        List<String> header=GenomicRangesWorkflowFiles.rows(output.resolve("rows.tsv"),maximumBytes,8,Math.toIntExact(GenomicRangesWorkflowFiles.cap(request,"maximum_rows")),(i,r)->{
            count[0]++;require(number(r[0],1,Integer.MAX_VALUE)==i,"Source row order/count changed");
            if(kind.equals("HITS")){
                require(r.length==4,"Invalid hit export width");number(r[1],1,query);number(r[2],1,subject);if(!r[3].equals("NA"))number(r[3],0,Integer.MAX_VALUE);if(!op.equals("distance-to-nearest"))require(r[3].equals("NA"),"Unexpected distance on a non-distance operation");
            }else if(kind.equals("GRANGES")){
                require(r.length==8&&seqlevels.contains(GenomicRangesWorkflowRuntime.unhex(r[1])),"Range references an absent literal sequence level");long start=number(r[2],-2147483647L,Integer.MAX_VALUE),end=number(r[3],-2147483647L,Integer.MAX_VALUE),width=number(r[4],0,Integer.MAX_VALUE);require(end-start+1==width,"Source endpoint/width relationship differs");require(Set.of("+","-","*").contains(r[5])&&Set.of("NULL","NA","VALUE").contains(r[6]),"Invalid original strand/name state");if(r[6].equals("VALUE"))GenomicRangesWorkflowRuntime.unhex(r[7]);else require(r[7].equals("-"),"Absent name has a payload");
            }else{
                require(r.length==2,"Invalid integer/logical result export width");if(logical)require(r[1].equals("NA"),"Logical source selection must retain missing values");else if(kind.equals("INTEGER_SELECTION")){if(!r[1].equals("NA"))number(r[1],1,subject);}else{long index=number(r[1],1,query);require(index>previous[0],"Out-of-bound row indices reordered or duplicated");previous[0]=index;}
            }
        },true);
        require(header.equals(kind.equals("HITS")?List.of("row","query_index","subject_index","distance"):kind.equals("GRANGES")?List.of("row","seqname_hex","start","end","width","strand","name_state","name_hex"):List.of("row","index")),"Invalid original core export header");require(count[0]==length,"Source result length differs from complete core export");require(!(kind.equals("INTEGER_SELECTION")||logical)||length==query,"Selection vector length differs from query");require(!kind.equals("INTEGER_INDICES")||length<=query,"Boundary indices exceed query population");return state;
    }
    static Map<String,Object> inventory(Path root,Map<String,Object> request)throws IOException {
        GenomicRangesWorkflowProcess.footprint(root,request);var entries=new java.util.TreeMap<String,Object>();
        try(var walk=Files.walk(root)){for(Path path:walk.toList())if(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)){
            require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)&&path.toRealPath().equals(path.toAbsolutePath().normalize()),"Nonregular/aliased captured node");String name=root.relativize(path).toString().replace('\\','/');if(!name.equals("seal.json"))entries.put(name,GenomicRangesWorkflowFiles.pin(path));
        }}return entries;
    }
    static Map<String,Object> verify(Path root)throws IOException {
        require(root.toRealPath().equals(root.toAbsolutePath().normalize()),"Capture path resolves through an alias");var request=GenomicRangesWorkflowContracts.validate(GenomicRangesWorkflowFiles.read(root.resolve("request.json"),16777216),root);
        var seal=GenomicRangesWorkflowFiles.read(root.resolve("seal.json"),67108864);require(seal.keySet().equals(Set.of("schema_version","files"))&&GenomicRangesWorkflowContracts.integer(seal.get("schema_version"),1,1)==1,"Invalid capture seal schema");var expected=GenomicRangesWorkflowFiles.map(seal.get("files"));var actual=inventory(root,request);require(expected.keySet().equals(actual.keySet()),"Missing/extra sealed capture members");for(String name:actual.keySet())GenomicRangesWorkflowRuntime.matches(GenomicRangesWorkflowFiles.map(expected.get(name)),GenomicRangesWorkflowFiles.map(actual.get(name)));
        GenomicRangesWorkflowRuntime.captureSources(root,request.get("profile").toString(),true);GenomicRangesWorkflowRuntime.controls(root,request,true);
        var manifest=GenomicRangesWorkflowFiles.read(root.resolve("manifest.json"),16777216);GenomicRangesWorkflowContracts.integer(manifest.get("schema_version"),1,1);require(Set.of("RUN","REPLAY").contains(manifest.get("action")),"Unknown capture action");require(manifest.get("results_admitted") instanceof Boolean&&manifest.get("complete") instanceof Boolean,"Admission/completion fields require literal booleans");require(manifest.get("workflow").equals("genomicranges-prepared-v1")&&manifest.get("profile").equals(request.get("profile"))&&manifest.get("operation").equals(request.get("operation")),"Manifest/request operation identity differs");
        if(manifest.get("action").equals("REPLAY")){Path parent=Path.of(GenomicRangesWorkflowContracts.string(manifest.get("replay_parent")));require(parent.isAbsolute()&&Path.of(request.get("input_rds").toString()).equals(parent.resolve("inputs/objects.rds")),"Replay input does not identify its recorded parent capture");var parentSeal=GenomicRangesWorkflowFiles.map(manifest.get("replay_parent_seal"));GenomicRangesWorkflowRuntime.matches(parentSeal,parentSeal);}else require(!manifest.containsKey("replay_parent")&&!manifest.containsKey("replay_parent_seal"),"RUN capture contains contradictory replay provenance");
        boolean admitted=Boolean.TRUE.equals(manifest.get("results_admitted"));
        if(admitted){require(Boolean.TRUE.equals(manifest.get("complete"))&&GenomicRangesWorkflowContracts.integer(manifest.get("exit_code"),0,0)==0&&manifest.get("status").equals("COMPLETED_WITH_WARNINGS")&&!manifest.containsKey("cleanup_error"),"Contradictory admission/cleanup status");var state=result(root,request,root.resolve("admitted"));var recorded=GenomicRangesWorkflowFiles.map(manifest.get("outcome"));require(state.keySet().equals(recorded.keySet()),"Manifest outcome population differs");for(String key:state.keySet()){Object a=state.get(key),b=recorded.get(key);require(a instanceof Number&&b instanceof Number?((Number)a).longValue()==((Number)b).longValue():java.util.Objects.equals(a,b),"Manifest/original outcome differs: "+key);}
            var before=GenomicRangesWorkflowFiles.read(root.resolve("runtime-files-before.json"),16777216);var after=GenomicRangesWorkflowFiles.read(root.resolve("runtime-files-after.json"),16777216);require(before.keySet().equals(after.keySet())&&!before.isEmpty(),"Incomplete runtime identity population");for(String key:before.keySet())GenomicRangesWorkflowRuntime.matches(GenomicRangesWorkflowFiles.map(before.get(key)),GenomicRangesWorkflowFiles.map(after.get(key)));
            GenomicRangesWorkflowRuntime.savedHandshake(root,request,manifest,before);
            try(var paths=Files.list(root.resolve("admitted"))){for(Path path:paths.toList())GenomicRangesWorkflowRuntime.matches(GenomicRangesWorkflowFiles.pin(root.resolve("worker/raw-result").resolve(path.getFileName())),GenomicRangesWorkflowFiles.pin(path));}
        }else {require(!Files.exists(root.resolve("admitted"),LinkOption.NOFOLLOW_LINKS)&&!Boolean.TRUE.equals(manifest.get("complete"))&&Set.of("SKIP3_DEPENDENT_ANALYSIS_SKIPPED","FAIL2_NO_COMPLETE_RESULT").contains(manifest.get("status")),"Failed/skipped capture contains admitted results or contradictory completion");GenomicRangesWorkflowContracts.integer(manifest.get("exit_code"),manifest.get("status").equals("SKIP3_DEPENDENT_ANALYSIS_SKIPPED")?3:2,manifest.get("status").equals("SKIP3_DEPENDENT_ANALYSIS_SKIPPED")?3:2);}
        return manifest;
    }
}
