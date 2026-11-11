/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.sequence.TomtomResults;

/** Complete requested original output family and unreported-pair ledger. */
final class TomtomOutput {
    private TomtomOutput() { }
    record Result(List<TomtomResults.Match> matches,List<TomtomResults.Pair> pairs,List<String> warnings,Map<String,Object> document) {
        Result {matches=List.copyOf(matches);pairs=List.copyOf(pairs);warnings=List.copyOf(warnings);document=Map.copyOf(document);}
    }
    static Result read(Path process,TomtomCapturedInputs.Context inputs,List<String> command,Runnable check)throws IOException {
        var request=inputs.request();var expected=inputs.expected(command);var limits=new MemeRuntimeFiles.Limits(request.capacity("file_bytes"),request.capacity("workspace_bytes"),(int)request.capacity("files"));var before=MemeRuntimeFiles.tree(process,limits,check);
        Set<String> base=Set.of(".","process.stdout","process.stderr","process.json","launch.json","WARNINGS.md");var required=new java.util.HashSet<>(base);
        if(request.mode()==TomtomRecipe.Mode.FULL_REPORT)required.addAll(Set.of("result","result/tomtom.xml","result/tomtom.tsv","result/tomtom.html"));require(before.keySet().equals(required),"Requested original Tomtom output/process family is incomplete or has unknown members");
        List<TomtomResults.Match> matches;List<TomtomResults.Pair> pairs;List<String> warnings;Map<String,Object> html=Map.of();NextflowFiles.Pin table;
        if(request.mode()==TomtomRecipe.Mode.TEXT){var audit=TomtomResults.readText(process.resolve("process.stdout"),expected,request.outputLimits(),check);matches=audit.matches();pairs=audit.pairs();warnings=audit.warnings();table=new NextflowFiles.Pin(audit.bytes(),audit.sha256());}
        else{var audit=TomtomResults.read(process.resolve("result/tomtom.xml"),process.resolve("result/tomtom.tsv"),expected,request.outputLimits(),check);matches=audit.matches();pairs=audit.pairs();warnings=audit.warnings();
            int nodes=(int)Math.min(50000000L,1000000L+16*request.capacity("xml_elements")+20*request.capacity("reported_rows"));html=TomtomHtml.read(process.resolve("result/tomtom.html"),audit,(int)Math.min(request.capacity("file_bytes"),268435456L),nodes,check).document();table=NextflowFiles.pin(process.resolve("result/tomtom.tsv"),request.capacity("table_bytes"),check);
        }
        require(before.equals(MemeRuntimeFiles.tree(process,limits,check)),"Complete original Tomtom output family changed during audit");var document=MemeRuntimeFiles.object("output_mode",request.mode().name(),"original_table",MemeRuntimeFiles.pin(table),"complete_reported_matches",matches.size(),"complete_declared_pairs",pairs.size(),"unreported_pairs",pairs.size()-matches.size(),"HTML",html,"warnings",warnings,"scientific_output_contract_complete",true,"biological_accuracy_certified",false,"scope","Original fitted results retained without replacement; unreported numeric values remain unavailable. Final workflow admission/verification is separate.");return new Result(matches,pairs,warnings,document);
    }
    private record Key(int qdb,String qid,int tdb,String tid) { }
    static void writePairs(Path path,Result result,Runnable check)throws IOException {
        var reported=new HashMap<Key,TomtomResults.Match>();for(var match:result.matches())reported.put(new Key(match.queryDatabase(),match.queryId(),match.targetDatabase(),match.targetId()),match);
        try(var out=Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){
            out.write("query_database\tquery_id\ttarget_database\ttarget_id\treported\tp_value\tE_value\tq_value\toffset\toverlap\torientation\n");
            for(var pair:result.pairs()){check.run();var match=reported.get(new Key(pair.queryDatabase(),pair.queryId(),pair.targetDatabase(),pair.targetId()));require(pair.reported()==(match!=null),"Complete Tomtom pair/report disposition differs");out.write(pair.queryDatabase()+"\t"+pair.queryId()+"\t"+pair.targetDatabase()+"\t"+pair.targetId()+"\t"+pair.reported()+"\t");
                if(match==null)out.write("NA\tNA\tNA\tNA\tNA\tNA\n");else out.write(match.pvalue().token()+"\t"+match.evalue().token()+"\t"+match.qvalue().token()+"\t"+match.offset()+"\t"+match.overlap()+"\t"+(match.reverseTarget()?"-":"+")+"\n");}
        }
    }
    static void verifyPairs(Path path,Result result,Runnable check)throws IOException {
        try(var channel=Files.newByteChannel(path)){long size=channel.size();require(size>0,"Empty complete Tomtom pair ledger");channel.position(size-1);var last=java.nio.ByteBuffer.allocate(1);require(channel.read(last)==1&&last.array()[0]=='\n',"Complete Tomtom pair ledger requires its terminal newline");}
        var reported=new HashMap<Key,TomtomResults.Match>();for(var match:result.matches())reported.put(new Key(match.queryDatabase(),match.queryId(),match.targetDatabase(),match.targetId()),match);
        try(var in=Files.newBufferedReader(path,StandardCharsets.UTF_8)){
            require("query_database\tquery_id\ttarget_database\ttarget_id\treported\tp_value\tE_value\tq_value\toffset\toverlap\torientation".equals(in.readLine()),"Invalid complete Tomtom pair ledger header");
            for(var pair:result.pairs()){check.run();var match=reported.get(new Key(pair.queryDatabase(),pair.queryId(),pair.targetDatabase(),pair.targetId()));String expected=pair.queryDatabase()+"\t"+pair.queryId()+"\t"+pair.targetDatabase()+"\t"+pair.targetId()+"\t"+pair.reported()+"\t"+(match==null?"NA\tNA\tNA\tNA\tNA\tNA":match.pvalue().token()+"\t"+match.evalue().token()+"\t"+match.qvalue().token()+"\t"+match.offset()+"\t"+match.overlap()+"\t"+(match.reverseTarget()?"-":"+"));require(expected.equals(in.readLine()),"Changed, missing or zero-filled original pair disposition");}
            require(in.readLine()==null,"Late original Tomtom pair ledger rows");
        }
    }
    private static void require(boolean value,String message)throws IOException {MemeRuntimeFiles.require(value,message);}
}
