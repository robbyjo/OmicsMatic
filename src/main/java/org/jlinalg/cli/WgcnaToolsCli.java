/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.network.Wgcna1721;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Prepared WGCNA operations with native recomputation or original-package execution. */
public final class WgcnaToolsCli {
    private WgcnaToolsCli() { }
    static final String ACTION="Read WARNINGS.md, analysis-status.json, worker/report.json and original logs before downstream use. SKIP3 (exit3): dependent analysis skipped; restore the named exact permitted input/runtime/package or retain the skip. FAIL2 (exit2): no complete result; retain partials and diagnostics, correct the named data/model/integrity/capacity failure and retry NEW output. Never substitute versions, q-values, missingness rules, samples or models. Native rankPvalue explicitly uses calculateQvalue=FALSE; original R supports its original qvalue calls and errors. Original networks use one declared complete block, explicit power and original defaults recorded in the source profile; no manual module edits, PyWGCNA, hdWGCNA, robust rWGCNA or author recipe is inferred. VERIFY recomputes native results and checks original result structure/integrity without refitting R or reading binary RDS. Hashes establish integrity, not authenticity or coordinated replacement detection. Capacities are preflight estimates/monitored limits, not OS quotas. No whole-paper or biological replication claim.";
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    static int run(String[] args,PrintStream out,PrintStream err) {
        Path root=null;boolean owned=false;Map<String,Object> execution=new LinkedHashMap<>();
        try {
            if(args.length==1&&args[0].equals("--help")){out.println("wgcna-tools --request UTF8_JSON | --verify BUNDLE\nRUN: NATIVE RANK_PVALUE/CONNECTIVITY or source-bound ORIGINAL_R RANK_PVALUE/CONNECTIVITY/NETWORK/SIGNED_KME. See docs/wgcna-tools.md for literal TSVs, exact runtime, capacities and replay.\n"+ACTION);return 0;}
            if(args.length==2&&args[0].equals("--verify")){verify(Path.of(args[1]));out.println("Verified saved WGCNA bundle. "+ACTION);return 0;}
            if(args.length!=2||!args[0].equals("--request"))throw new IOException("Require --request JSON, --verify BUNDLE or --help");
            Path source=Path.of(args[1]).toAbsolutePath().normalize();byte[] original=NextflowFiles.bytes(source,2000000);var raw=object(SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(original)).toString()),"request");var r=WgcnaToolsRequest.validate(raw);Path parent=source.getParent();root=resolve(parent,text(r,"out"));Files.createDirectories(root.getParent());Files.createDirectory(root);owned=true;
            json(root.resolve("analysis-status.json"),Map.of("status","RUNNING","results_admitted",false));Files.write(root.resolve("source-request.json"),original,StandardOpenOption.CREATE_NEW);
            for(String directory:List.of("inputs","results","source","worker"))Files.createDirectory(root.resolve(directory));
            var captured=new LinkedHashMap<String,Object>(raw);captured.put("out",".");long remaining=WgcnaToolsRequest.number(r,"maximum_input_bytes");
            for(String key:List.of("matrix","modules","eigengenes"))if(raw.containsKey(key)){remaining-=capture(resolve(parent,text(raw,key)),root.resolve("inputs/"+key),remaining);captured.put(key,"inputs/"+key);}
            json(root.resolve("request.json"),captured);var effective=WgcnaToolsRequest.validate(captured);json(root.resolve("effective-request.json"),effective);WgcnaToolsRuntime.sources(root,false);
            var matrix=intake(root,effective);var manifest=execution;manifest.put("backend",r.get("backend"));manifest.put("operation",r.get("operation"));
            var before=inputHashes(root);
            if(text(r,"backend").equals("NATIVE")){for(var e:nativeResults(root,effective,matrix).entrySet())Files.write(root.resolve("results/"+e.getKey()),e.getValue(),StandardOpenOption.CREATE_NEW);}
            else WgcnaToolsRuntime.run(root,parent,r,manifest);
            if(!before.equals(inputHashes(root)))throw new IOException("Captured input changed during computation");
            WgcnaToolsRuntime.sources(root,true);intake(root,effective);audit(root,effective,matrix);
            json(root.resolve("execution.json"),manifest);Files.writeString(root.resolve("WARNINGS.md"),warning(effective,matrix),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            json(root.resolve("analysis-status.json"),Map.of("status","COMPLETE_SCOPED_WGCNA","results_admitted",true,"whole_paper_reproduced",false));SvaWorkflowCli.seal(root);verify(root);
            out.println("Completed scoped WGCNA operation: "+root);err.println("WARNINGS / REQUIRED ACTIONS: "+root.resolve("WARNINGS.md")+"\n"+ACTION);return 0;
        }catch(Exception|LinkageError|OutOfMemoryError failure) {
            int code=failure instanceof SvaWorkflowRuntime.Unavailable?3:2;String state=code==3?"SKIP3_DEPENDENT_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_RESULT";err.println(state+": "+failure+"\n"+ACTION);
            if(failure instanceof InterruptedException||failure instanceof InterruptedIOException)Thread.currentThread().interrupt();
            if(owned&&root!=null)try{json(root.resolve("execution.json"),execution);try(var trace=new PrintWriter(Files.newBufferedWriter(root.resolve("failure-stack.txt"),StandardCharsets.UTF_8))){failure.printStackTrace(trace);}json(root.resolve("analysis-status.json"),Map.of("status",state,"results_admitted",false,"cause",failure.toString(),"required_action",ACTION));Files.writeString(root.resolve("WARNINGS.md"),"# "+state+"\n\nCAUSE: "+failure+"\n\nREQUIRED ACTION: "+ACTION+"\n",StandardCharsets.UTF_8);SvaWorkflowCli.seal(root);}catch(IOException e){err.println("Could not finish diagnostic seal: "+e+"; preserve output directory");}return code;
        }
    }
    static WgcnaToolsRequest.Table intake(Path root,Map<String,Object> r)throws IOException {
        var t=WgcnaToolsRequest.table(root.resolve("inputs/matrix"),r);WgcnaToolsRequest.dimensions(t,r);String op=text(r,"operation");
        if(op.equals("CONNECTIVITY"))WgcnaToolsRequest.modules(root.resolve("inputs/modules"),t);
        if(op.equals("SIGNED_KME")){var me=WgcnaToolsRequest.table(root.resolve("inputs/eigengenes"),r);if(!me.rows().equals(t.rows()))throw new IOException("Eigengene sample identities/order differ");if((long)t.columns().size()*me.columns().size()>WgcnaToolsRequest.number(r,"maximum_entries"))throw new IOException("Membership result entry capacity exceeded");}
        return t;
    }
    static Map<String,byte[]> nativeResults(Path root,Map<String,Object> r,WgcnaToolsRequest.Table t)throws IOException {
        List<String> columns;double[][] values;String file;
        if(text(r,"operation").equals("RANK_PVALUE")) {
            double[] weights=null;if(r.containsKey("weights")){var v=list(r.get("weights"),"weights");weights=new double[v.size()];for(int i=0;i<weights.length;i++)weights[i]=((Number)v.get(i)).doubleValue();}
            var result=Wgcna1721.rankPvalue(t.values(),weights,Wgcna1721.Ties.valueOf(text(r,"ties").toUpperCase(Locale.ROOT)),Wgcna1721.Missing.valueOf(text(r,"na_last").toUpperCase(Locale.ROOT)),Wgcna1721.Method.valueOf(text(r,"p_value_method").toUpperCase(Locale.ROOT)));columns=result.columns();values=result.values();file="rank-pvalues.tsv";
        }else {columns=List.of("kTotal","kWithin","kOut","kDiff");values=Wgcna1721.intramodularConnectivity(t.values(),WgcnaToolsRequest.modules(root.resolve("inputs/modules"),t).toArray(String[]::new),bool(r,"scale_by_max",false));file="connectivity.tsv";}
        StringBuilder output=new StringBuilder("id\t"+String.join("\t",columns)+"\n");long missing=0,infinite=0;
        for(int i=0;i<values.length;i++){output.append(t.rows().get(i));for(double value:values[i]){output.append('\t').append(Double.isNaN(value)?"NA":value==Double.POSITIVE_INFINITY?"Inf":value==Double.NEGATIVE_INFINITY?"-Inf":Double.toString(value));if(Double.isNaN(value))missing++;else if(!Double.isFinite(value))infinite++;}output.append('\n');}
        byte[] rendered=output.toString().getBytes(StandardCharsets.UTF_8);if(rendered.length>WgcnaToolsRequest.number(r,"maximum_output_bytes"))throw new IOException("Native result exceeds output capacity; no truncation");
        var summary=new LinkedHashMap<String,Object>();summary.put("backend","NATIVE");summary.put("operation",r.get("operation"));summary.put("source_semantics","WGCNA 1.72-1");summary.put("input_rows",t.rows().size());summary.put("input_columns",t.columns().size());summary.put("input_missing",t.missing());summary.put("input_infinite",t.infinite());summary.put("output_missing",missing);summary.put("output_infinite",infinite);summary.put("qvalues_computed",false);summary.put("whole_tool_reimplemented",false);summary.put("whole_paper_reproduced",false);
        byte[] report=RsemJson.bytes(summary);if((long)rendered.length+report.length>WgcnaToolsRequest.number(r,"maximum_output_bytes"))throw new IOException("Native complete result exceeds output capacity");return Map.of(file,rendered,"summary.json",report);
    }
    static void verify(Path supplied)throws IOException {
        Path root=supplied.toAbsolutePath().normalize();var seal=read(root.resolve("capture-files.json"),16000000);keys(seal,Set.of("schema_version","authentication","files"));if(!Boolean.FALSE.equals(seal.get("authentication"))||integer(seal,"schema_version",0,1,1)!=1)throw new IOException("Invalid WGCNA seal");
        var expected=object(seal.get("files"),"files");var actual=new TreeMap<String,String>();for(String name:SvaWorkflowRuntime.footprint(root).keySet())if(!name.equals("capture-files.json"))actual.put(name,FollowupSupport.hash(root.resolve(name)));if(!actual.equals(expected))throw new IOException("Saved WGCNA capture population or bytes changed");
        var state=read(root.resolve("analysis-status.json"));if(!Boolean.TRUE.equals(state.get("results_admitted"))||!"COMPLETE_SCOPED_WGCNA".equals(state.get("status")))throw new IOException("Failed/skipped WGCNA capture is not a complete result");
        var saved=read(root.resolve("request.json"));var r=WgcnaToolsRequest.validate(saved);if(!same(r,read(root.resolve("effective-request.json"))))throw new IOException("Effective request changed");
        for(String key:List.of("matrix","modules","eigengenes"))if(saved.containsKey(key)&&!saved.get(key).equals("inputs/"+key))throw new IOException("Saved input path escaped capture");if(!".".equals(saved.get("out")))throw new IOException("Saved output path changed");WgcnaToolsRuntime.sources(root,true);var t=intake(root,r);
        if(text(r,"backend").equals("NATIVE")){for(var row:nativeResults(root,r,t).entrySet())if(!Arrays.equals(row.getValue(),Files.readAllBytes(root.resolve("results/"+row.getKey()))))throw new IOException("Native semantic recomputation differs: "+row.getKey());}
        audit(root,r,t);if(!Files.readString(root.resolve("WARNINGS.md")).equals(warning(r,t)))throw new IOException("Warnings/source scope changed");
    }
    private static void audit(Path root,Map<String,Object> r,WgcnaToolsRequest.Table t)throws IOException {
        String op=text(r,"operation");List<String> files=switch(op){case "RANK_PVALUE"->List.of("rank-pvalues.tsv");case "CONNECTIVITY"->List.of("connectivity.tsv");case "SIGNED_KME"->List.of("membership.tsv");default->List.of("eigengenes.tsv","membership.tsv","connectivity.tsv","adjacency.tsv");};
        var limits=new LinkedHashMap<String,Object>(r);limits.put("maximum_input_bytes",r.get("maximum_output_bytes"));limits.put("maximum_rows",Math.max(t.rows().size(),t.columns().size()));limits.put("maximum_columns",Math.max(12,Math.max(t.rows().size(),t.columns().size())));limits.put("maximum_entries",100000000);
        for(String name:files){var result=WgcnaToolsRequest.table(root.resolve("results/"+name),limits);var rows=name.equals("eigengenes.tsv")?t.rows():op.equals("NETWORK")||op.equals("SIGNED_KME")?t.columns():t.rows();if(!rows.equals(result.rows()))throw new IOException("Original/native result population/order changed: "+name);if(name.equals("adjacency.tsv")&&!result.columns().equals(t.columns()))throw new IOException("Adjacency feature population differs");if(op.equals("RANK_PVALUE"))for(double[] row:result.values())for(double v:row)if(!Double.isNaN(v)&&(!Double.isFinite(v)||v<0||v>1))throw new IOException("Invalid original/native probability");if(name.equals("membership.tsv")||name.equals("eigengenes.tsv"))for(double[] row:result.values())for(double v:row)if(!Double.isNaN(v)&&(!Double.isFinite(v)||Math.abs(v)>1.00000001))throw new IOException("Invalid original membership/eigengene value");}
        if(op.equals("NETWORK")){List<String> lines=Files.readAllLines(root.resolve("results/modules.tsv"),StandardCharsets.UTF_8);if(lines.size()!=t.columns().size()+1||!lines.get(0).equals("id\tlabel\tmodule"))throw new IOException("Original module table shape differs");for(int i=0;i<t.columns().size();i++){String[] cells=lines.get(i+1).split("\t",-1);if(cells.length!=3||!cells[0].equals(t.columns().get(i)))throw new IOException("Original module identities/order differ");identity(cells[2]);WgcnaToolsRequest.numeric(cells[1]);}}
        if(text(r,"backend").equals("ORIGINAL_R")){var report=read(root.resolve("worker/report.json"));if(!WgcnaToolsRequest.PROFILE.equals(report.get("profile"))||!op.equals(report.get("operation"))||!Boolean.FALSE.equals(report.get("whole_paper_reproduced")))throw new IOException("Original report binding differs");if(!Files.isRegularFile(root.resolve("worker/objects.rds"),LinkOption.NOFOLLOW_LINKS)||Files.size(root.resolve("worker/objects.rds"))==0)throw new IOException("Original RDS missing");}
    }
    private static String warning(Map<String,Object> r,WgcnaToolsRequest.Table t){return "# WGCNA — WARNINGS AND REQUIRED ACTIONS\n\n"+ACTION+"\n\nBackend: "+r.get("backend")+"; operation: "+r.get("operation")+". Input missing tokens: "+t.missing()+"; input infinite tokens: "+t.infinite()+". Retain every source missing/nonfinite result; no automatic row/feature/module removal or numeric repair is performed. Constant scaled columns and modules with fewer than three nodes follow original rules. Original qvalue may fail rather than supply q-values. Exact source/profile guards cover the scope declared in source/SOURCE-PROFILE.json. Review retained original warnings even when execution completes.\n";}
    private static long capture(Path source,Path destination,long cap)throws IOException {if(!Files.exists(source)||!Files.isReadable(source))throw new SvaWorkflowRuntime.Unavailable("Required permitted input unavailable: "+source);if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)||Files.size(source)>cap)throw new IOException("Input nonregular or exceeds aggregate capacity");String before=FollowupSupport.hash(source);long size=0;try(var input=Files.newInputStream(source);var output=Files.newOutputStream(destination,StandardOpenOption.CREATE_NEW)){byte[] buffer=new byte[65536];int n;while((n=input.read(buffer))!=-1){if(n>cap-size)throw new IOException("Input grew beyond capacity");output.write(buffer,0,n);size+=n;}}if(!before.equals(FollowupSupport.hash(source))||!before.equals(FollowupSupport.hash(destination)))throw new IOException("Input changed during capture");return size;}
    private static Map<String,String> inputHashes(Path root)throws IOException {Map<String,String> values=new TreeMap<>();try(var stream=Files.list(root.resolve("inputs"))){for(Path p:stream.toList())values.put(p.getFileName().toString(),FollowupSupport.hash(p));}return values;}
}
