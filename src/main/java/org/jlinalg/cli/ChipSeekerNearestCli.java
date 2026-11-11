/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.ChipSeekerNearestFeatures;
import org.jlinalg.genetics.ChipSeekerNearestFeatures.*;

/** Native prepared-file nearest-feature workflow; no R runtime or package installation. */
public final class ChipSeekerNearestCli {
    private ChipSeekerNearestCli() { }
    private static final Set<String> ALLOWED=Set.of("input","features","reference-build","coordinates","overlap","ignore-overlap","ignore-upstream","ignore-downstream",
        "max-peaks","max-features","max-operations","max-input-bytes","out");
    private static final String ACTION="Preserve this attempt and read WARNINGS.md, analysis-status.json and any partial directory. Correct the named input, preparation or capacity cause and retry into a NEW output directory. Do not clip coordinates, relabel ambiguous strands, replace source dummy/NaN/Inf states or infer biological absence to avoid a scope gap. This Java component needs no R installation; full genomic annotation requires a separately validated upstream workflow.";

    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    public static int run(String[] args,PrintStream output,PrintStream error){
        try{
            if(args.length==1 && args[0].equals("--help")){
                output.println("Native ChIPseeker prepared unstranded-peak nearest-feature subset. No R installation.\n"
                    +"--input PEAKS.tsv --features FEATURES.tsv --reference-build LABEL --coordinates one-based-inclusive --out NEW_DIRECTORY\n"
                    +"Peaks header: peak_id<TAB>contig<TAB>start<TAB>end. Features header: feature_id<TAB>contig<TAB>start<TAB>end<TAB>strand.\n"
                    +"Options: --overlap TSS|ALL (TSS), --ignore-overlap true|false, --ignore-upstream true|false, --ignore-downstream true|false;\n"
                    +"--max-peaks (100000), --max-features (100000), --max-operations (50000000), --max-input-bytes (67108864 per file).\n"
                    +"Unicode paths: --request ASCII_PATH.json with {\"schema_version\":1,\"options\":{...}}. Options have string values; input/features/out resolve against that JSON.\n"
                    +"Exact caller-supplied reference/contigs and +/- features; unstranded peaks only. Duplicate biological IDs remain ordered.\n"
                    +"Read WARNINGS.md; source-dropped queries and N+1/Inf boundary dummies remain explicit. No TxDb, feature-class or whole-package parity.");return 0;
            }
            var invocation=invocation(args);var options=invocation.options();FollowupSupport.required(options,"input");FollowupSupport.required(options,"features");
            String build=FollowupSupport.required(options,"reference-build");require(build.length()<=256 && build.codePoints().noneMatch(Character::isISOControl),"Invalid declared shared reference label");
            require(FollowupSupport.required(options,"coordinates").equals("one-based-inclusive"),"Declare --coordinates one-based-inclusive; no implicit BED conversion");
            int[] exit={0};
            // This shared publisher retains failed publication attempts and retries
            // Windows handles. The native workflow launches no external processes.
            FollowupSupport.externalTransaction(options,"chipseeker-nearest",(directory,manifest)->{
                try{
                    invocation.verify();
                    int maxBytes=FollowupSupport.integer(options,"max-input-bytes",67108864,1,268435456);
                    int maxPeaks=FollowupSupport.integer(options,"max-peaks",100000,1,1000000),maxFeatures=FollowupSupport.integer(options,"max-features",100000,1,1000000);
                    long maxOperations=Long.parseLong(options.getOrDefault("max-operations","50000000"));require(maxOperations>=1 && maxOperations<=10000000000L,"Native operation capacity must be in [1,10000000000]");
                    var settings=new Options(FollowupSupport.bool(options,"ignore-overlap",false),FollowupSupport.bool(options,"ignore-upstream",false),FollowupSupport.bool(options,"ignore-downstream",false),Overlap.valueOf(options.getOrDefault("overlap","TSS")));
                    Path captured=Files.createDirectory(directory.resolve("inputs"));
                    if(invocation.request()!=null){
                        Files.write(captured.resolve("options-request.json"),invocation.raw(),StandardOpenOption.CREATE_NEW);
                        manifest.put("options_request_sha256",invocation.pin().sha256());
                    }
                    var peakFile=capture(Path.of(options.get("input")),captured.resolve("peaks.tsv"),maxBytes);
                    var featureFile=capture(Path.of(options.get("features")),captured.resolve("features.tsv"),maxBytes);
                    var initialHashes=NextflowFiles.object(manifest.get("input_sha256"));
                    require(peakFile.pin().sha256().equals(initialHashes.get("input")) && featureFile.pin().sha256().equals(initialHashes.get("features")),"Input identity changed after the transaction's initial provenance capture");
                    var peakRows=table(peakFile.raw(),"peak_id\tcontig\tstart\tend",maxPeaks);
                    var featureRows=table(featureFile.raw(),"feature_id\tcontig\tstart\tend\tstrand",maxFeatures);
                    var peaks=new ArrayList<Peak>();var features=new ArrayList<Feature>();
                    for(var row:peakRows)peaks.add(new Peak(row[1],coordinate(row[2]),coordinate(row[3])));
                    for(var row:featureRows){
                        if(row[4].equals("*"))throw new ChipSeekerNearestFeatures.UnsupportedInput("Wildcard feature strands require their own original-source contract; skip this native subset without relabeling the feature");
                        require(row[4].equals("+") || row[4].equals("-"),"Feature strand must be literal + or -");
                        features.add(new Feature(row[1],coordinate(row[2]),coordinate(row[3]),row[4].charAt(0)));
                    }
                    Runnable check=()->{if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Native nearest-feature computation cancelled");};
                    var result=ChipSeekerNearestFeatures.annotate(peaks,features,settings,new Limits(maxPeaks,maxFeatures,maxOperations),check);
                    peakFile.verify();featureFile.verify();invocation.verify();
                    var header=List.of("input_ordinal","peak_id","contig","start","end","disposition","source_feature_ordinal","feature_id","feature_contig","feature_start","feature_end","feature_strand","distance_to_tss");
                    var ledger=new ArrayList<String[]>();var retained=new ArrayList<String[]>();int matched=0,missing=0,dummies=0;
                    for(var row:result.rows()){
                        var p=peakRows.get(row.inputIndex());boolean real=row.disposition()==Disposition.MATCHED;
                        String[] f=real?featureRows.get(row.sourceFeatureIndex()-1):new String[]{"","","","",""};
                        String distance=Double.isNaN(row.distanceToTss())?"NaN":Double.isInfinite(row.distanceToTss())?"Inf":Long.toString((long)row.distanceToTss());
                        String[] values={Integer.toString(row.inputIndex()+1),p[0],p[1],p[2],p[3],row.disposition().name(),Integer.toString(row.sourceFeatureIndex()),f[0],f[1],f[2],f[3],f[4],distance};
                        ledger.add(values);if(row.disposition()!=Disposition.NO_FEATURE)retained.add(values);
                        switch(row.disposition()){case MATCHED->matched++;case NO_FEATURE->missing++;case BOUNDARY_DUMMY->dummies++;}
                    }
                    var report=new LinkedHashMap<String,Object>();report.put("method","CHIPSEEKER_PREPARED_UNSTRANDED_NEAREST_V1");report.put("reference_build_declared_for_both_inputs",build);
                    report.put("input_peaks",peaks.size());report.put("input_features",features.size());report.put("matched",matched);report.put("source_dropped_queries_retained_in_ledger",missing);
                    report.put("source_boundary_dummies",dummies);report.put("source_retained_rows",retained.size());report.put("operations",result.operations());report.put("coordinate_system","one-based-inclusive");
                    report.put("settings",Map.of("same_strand",false,"overlap",settings.overlap().name(),"ignore_overlap",settings.ignoreOverlap(),"ignore_upstream",settings.ignoreUpstream(),"ignore_downstream",settings.ignoreDownstream()));
                    report.put("capacities",Map.of("input_bytes_per_file",maxBytes,"peaks",maxPeaks,"features",maxFeatures,"operations",maxOperations));
                    report.put("java_runtime",System.getProperty("java.runtime.version"));
                    report.put("original_reference_source_versions",List.of("1.22.1","1.26.2","1.34.1","1.48.1"));
                    report.put("original_reference_component_sha256","599e14b879a34de1581b8e576a7b572b2e23f99c414dab9d049c498442b630cf");
                    report.put("original_reference_GenomicRanges_version","1.64.0");report.put("R_runtime_used_by_this_analysis",false);
                    report.put("input_pins",Map.of("peaks",pinMap(peakFile.pin()),"features",pinMap(featureFile.pin())));
                    report.put("scope","Prepared unstranded-peak/sameStrand=FALSE nearest-feature component. Caller supplies matching ordered ranges/reference; no TxDb, feature classes, gene symbols, historical dependency environment, whole package/paper or biological certification.");
                    var warnings=new ArrayList<String>(result.warnings());warnings.add("The shared reference label is a caller declaration; these tables do not prove the underlying genome/annotation identity. Coordinate/contig/ID values and duplicate IDs are preserved. No BED conversion, alias repair or feature deduplication is performed.");
                    String warningText="# NATIVE COMPONENT WARNINGS\n\n"+String.join("\n\n",warnings)+"\n\nDo not treat NaN, Inf, unmapped queries or dummy boundaries as gene assignments or negative biology. Read analysis-status.json and manifest.yaml before downstream use.\n";
                    FollowupSupport.table(directory.resolve("annotations.tsv"),header,ledger);FollowupSupport.table(directory.resolve("source-retained.tsv"),header,retained);
                    NextflowFiles.writeJson(directory.resolve("report.json"),report);Files.writeString(directory.resolve("WARNINGS.md"),warningText,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                    NextflowFiles.writeJson(directory.resolve("analysis-status.json"),Map.of("status","completed","result_admitted",true,"scope","prepared native nearest-feature subset"));
                    peakFile.verify();featureFile.verify();invocation.verify();
                    if(invocation.request()!=null)require(invocation.pin().equals(NextflowFiles.pin(captured.resolve("options-request.json"),invocation.pin().bytes(),()->{})),"Captured native request changed");
                    manifest.put("analysis_status","completed");manifest.put("native_report",report);
                    error.println("WARNING: prepared nearest-feature subset only; "+missing+" source-dropped queries retained and "+dummies+" source boundary dummies. Read WARNINGS.md. Dummy/NaN/Inf states are not gene assignments.");
                }catch(Exception failure){
                    exit[0]=failure instanceof ChipSeekerNearestFeatures.UnsupportedInput?3:2;
                    for(String name:List.of("annotations.tsv","source-retained.tsv","report.json","analysis-status.json","WARNINGS.md")){
                        Path file=directory.resolve(name);if(Files.exists(file)){Path partial=directory.resolve("partial");Files.createDirectories(partial);Files.move(file,partial.resolve(name));}
                    }
                    String status=exit[0]==3?"SKIP3_ANALYSIS_SKIPPED":"FAIL2_NO_RESULT_ADMITTED";
                    manifest.put("analysis_status",status);manifest.put("error",failure.toString());
                    NextflowFiles.writeJson(directory.resolve("analysis-status.json"),Map.of("status",status,"result_admitted",false,"cause",failure.toString(),"required_action",ACTION));
                    Files.writeString(directory.resolve("WARNINGS.md"),"# "+status+"\n\n"+failure+"\n\nREQUIRED ACTION: "+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                    error.println(status+": "+failure+"\nREQUIRED ACTION: "+ACTION);
                }
            });
            if(exit[0]==0)output.println("Native nearest-feature component completed: "+options.get("out"));
            return exit[0];
        }catch(Exception failure){error.println("FAIL2_NO_RESULT_ADMITTED: "+failure+"\nREQUIRED ACTION: "+ACTION);return 2;}
    }
    private record Invocation(Map<String,String> options,Path request,NextflowFiles.Pin pin,byte[] raw){
        Invocation {options=Map.copyOf(options);}
        void verify()throws IOException{if(request!=null)require(pin.equals(NextflowFiles.pin(request,1048576,()->{})),"Original native options request changed");}
    }
    private static Invocation invocation(String[] args)throws IOException{
        if(args.length!=2 || !args[0].equals("--request"))return new Invocation(FollowupSupport.options(args,ALLOWED),null,null,null);
        Path request=Path.of(args[1]).toAbsolutePath().normalize();var pin=NextflowFiles.pin(request,1048576,()->{});byte[] bytes=NextflowFiles.bytes(request,1048576);
        require(bytes.length==pin.bytes() && NextflowFiles.sha(bytes).equals(pin.sha256()),"Native options request changed during intake");
        var raw=NextflowFiles.object(NextflowParameters.parse(NextflowFiles.utf8(bytes),1048576,10000));NextflowFiles.keys(raw,"schema_version","options");NextflowFiles.integer(raw.get("schema_version"),1,1);
        var supplied=NextflowFiles.object(raw.get("options"));var options=new LinkedHashMap<String,String>();
        for(var entry:supplied.entrySet()){
            String key=NextflowFiles.text(entry.getKey());require(ALLOWED.contains(key),"Unknown native options request key: "+key);
            String value=NextflowFiles.text(entry.getValue());
            if(Set.of("input","features","out").contains(key)){
                Path path=Path.of(value);value=(path.isAbsolute()?path:request.getParent().resolve(path)).toAbsolutePath().normalize().toString();
            }
            options.put(key,value);
        }
        var result=new Invocation(options,request,pin,bytes);result.verify();return result;
    }
    private record Captured(Path original,Path copy,NextflowFiles.Pin pin,byte[] raw){
        void verify()throws IOException{require(pin.equals(NextflowFiles.pin(original,pin.bytes(),()->{})) && pin.equals(NextflowFiles.pin(copy,pin.bytes(),()->{})),"Original or captured native annotation input changed");}
    }
    private static Captured capture(Path source,Path destination,int maximum)throws IOException{
        var pin=NextflowFiles.pin(source,maximum,()->{});byte[] raw=NextflowFiles.bytes(source,maximum);
        require(raw.length==pin.bytes() && NextflowFiles.sha(raw).equals(pin.sha256()),"Native annotation input changed during capture");
        Files.write(destination,raw,StandardOpenOption.CREATE_NEW);var value=new Captured(source,destination,pin,raw);value.verify();return value;
    }
    private static List<String[]> table(byte[] raw,String header,int maximum)throws IOException{
        String text=NextflowFiles.utf8(raw).replace("\r\n","\n");require(!text.contains("\r"),"Native annotation tables require LF or CRLF records");
        var lines=text.lines().iterator();require(lines.hasNext() && lines.next().equals(header),"Require exact tab-separated header: "+header);
        int columns=header.split("\t",-1).length;var result=new ArrayList<String[]>();
        while(lines.hasNext()){
            String line=lines.next();require(line.length()<=32768 && !line.isEmpty(),"Blank or excessive native annotation row");String[] fields=line.split("\t",-1);
            require(fields.length==columns && !fields[0].isEmpty() && !fields[1].isEmpty(),"Incomplete native annotation row; IDs/contigs remain literal");
            for(String value:fields)require(value.codePoints().noneMatch(Character::isISOControl),"Control characters in native annotation field");
            require(result.size()<maximum,"Complete native annotation row population exceeds capacity");result.add(fields);
        }
        return List.copyOf(result);
    }
    private static int coordinate(String token)throws IOException{
        require(token.matches("[1-9][0-9]{0,9}"),"Coordinates must be literal positive one-based integers");
        try{return Integer.parseInt(token);}catch(NumberFormatException failure){throw new IOException("Coordinate exceeds supported 32-bit range",failure);}
    }
    private static Map<String,Object> pinMap(NextflowFiles.Pin pin){return Map.of("bytes",pin.bytes(),"sha256",pin.sha256());}
    private static void require(boolean value,String message)throws IOException{if(!value)throw new IOException(message);}
}
