/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.genetics.ChipSeekerNearestFeatures;
import org.jlinalg.genetics.ChipSeekerNearestFeatures.*;
import static org.jlinalg.cli.ChipSeekerAnnotationRequest.*;

/** Original annotatePeak plus native nearest audit; genomic labels stay version-specific upstream results. */
public final class ChipSeekerAnnotationCli {
    private ChipSeekerAnnotationCli() { }
    static final String ACTION="Preserve this attempt. Read WARNINGS.md, analysis-status.json, worker/worker-report.json, each worker/cases/ID/source-status.json and original stdout/stderr. SKIP3: supply exact R4.6.1, requested original ChIPseeker edition and SOURCE-PROFILE.json dependencies in declared private libraries where permitted; if installation is forbidden, skip the dependent analysis. This command installs nothing. FAIL2: no result from this atomic batch is admitted, including completed or partial R tables. Correct the named input, original error, source or capacity cause and retry into NEW paths. Do not drop queries, clip coordinates, repair IDs, change directions or substitute modern annotations to force success. Read CHIPSEEKER_NEAREST_DEVELOPMENT.md for reference setup. There is no portable VERIFY command for this development interface.";
    static final String SCOPE="Original version-specific annotatePeak on an explicit TxDb SQLite and peak population, current declared R dependencies, and a separately scoped native nearest audit. Genomic labels/distributions remain upstream. The reference-build label is a caller declaration; no assembly, TxDb, identifier or enhancer rule is inferred. Raw original geneChr/geneStrand may be numeric codes: use the separate literal feature ledger. No historical dependency environment, whole-package/paper reproduction, native-DLL source authentication or biological certification is claimed. Logical input/output/operation bounds and polling are not OS memory or disk quotas.";
    private record Captured(Path original,Path copy,NextflowFiles.Pin pin){void verify()throws IOException{require(pin.equals(NextflowFiles.pin(original,pin.bytes(),()->{}))&&pin.equals(NextflowFiles.pin(copy,pin.bytes(),()->{})),"Original or captured annotation input changed: "+original);}}
    private record PeakRow(String id,String contig,int start,int end,String strand){Map<String,Object> json(){return Map.of("peak_id",id,"contig",contig,"start",start,"end",end,"strand",strand);}}
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    public static int run(String[] args,PrintStream output,PrintStream error){
        try{
            if(args.length==1&&args[0].equals("--help")){
                emit(output,"ChipSeekerAnnotationCli --request ASCII_PATH.json\nMode ANNOTATE; exact version 1.22.1/1.26.2/1.34.1/1.48.1; one-based-inclusive peak TSV, explicit TxDb SQLite, reference_build, rscript/r_libraries and NEW out.\nPeaks header: peak_id<TAB>contig<TAB>start<TAB>end, optionally followed by <TAB>strand (+/-/*).\nOptional analyses array contains named annotation configurations; defaults are captured. Any failed configuration rejects the entire atomic batch.\nUTF-8 JSON paths resolve against the request file. On Windows keep JAR and outer request paths ASCII; internal paths/IDs are UTF-8.\n"+SCOPE+"\n"+ACTION);return 0;
            }
            require(args.length==2&&args[0].equals("--request"),"Require --request UTF8_JSON or --help");
            Path source=Path.of(args[1]).toAbsolutePath().normalize();var requestPin=NextflowFiles.pin(source,1048576,()->{});byte[] raw=NextflowFiles.bytes(source,1048576);
            require(raw.length==requestPin.bytes()&&NextflowFiles.sha(raw).equals(requestPin.sha256()),"Annotation request changed during intake");
            Request request=parse(object(NextflowParameters.parse(NextflowFiles.utf8(raw),1048576,100000)),source.getParent());int[] exit={0};
            FollowupSupport.externalTransaction(Map.of("out",request.out().toString()),"original-chipseeker-annotation-development",(stage,manifest)->{
                var warnings=new ArrayList<String>();warnings.add(SCOPE);var state=new LinkedHashMap<String,Object>();state.put("status","FAIL2_NO_RESULT_ADMITTED");state.put("result_admitted",false);
                try{
                    verifyRequest(source,requestPin);Files.write(stage.resolve("source-request.json"),raw,StandardOpenOption.CREATE_NEW);NextflowFiles.writeJson(stage.resolve("effective-request.json"),request.effective());
                    Path inputs=Files.createDirectory(stage.resolve("inputs"));var peaks=capture(request.peaks(),inputs.resolve("peaks.tsv"),Math.min(request.inputBytes(),request.peakBytes()));
                    var txdb=capture(request.txdb(),inputs.resolve("txdb.sqlite"),request.inputBytes()-peaks.pin().bytes());
                    require(request.expectedTxdb()==null||request.expectedTxdb().equals(txdb.pin().sha256()),"Caller-declared TxDb SHA-256 does not match the supplied database");
                    manifest.put("captured_inputs",Map.of("peaks",fingerprint(peaks.pin()),"txdb",fingerprint(txdb.pin())));manifest.put("source_request_sha256",requestPin.sha256());
                    List<PeakRow> rows=peaks(peaks.copy(),request);Path worker=Files.createDirectory(stage.resolve("worker"));
                    NextflowFiles.writeJson(worker.resolve("peaks.json"),rows.stream().map(PeakRow::json).toList());NextflowFiles.writeJson(worker.resolve("parameters.json"),request.parameters());
                    ChipSeekerAnnotationRuntime.sources(stage,request.version(),false);ChipSeekerAnnotationRuntime.run(stage,request,manifest);
                    var workerReport=read(worker.resolve("worker-report.json"),request.jsonBytes());
                    for(Object warning:list(workerReport.get("warnings")))warnings.add("ORIGINAL R WARNING: "+text(warning));
                    peaks.verify();txdb.verify();verifyRequest(source,requestPin);ChipSeekerAnnotationRuntime.sources(stage,request.version(),true);
                    var reports=new ArrayList<Map<String,Object>>();
                    for(Analysis analysis:request.analyses())reports.add(audit(worker.resolve("cases").resolve(analysis.id()),analysis,rows,request,warnings));
                    Path results=Files.createDirectory(stage.resolve("results"));
                    for(int i=0;i<request.analyses().size();i++){
                        Analysis analysis=request.analyses().get(i);Path from=worker.resolve("cases").resolve(analysis.id()),to=Files.createDirectory(results.resolve(analysis.id()));
                        for(String name:List.of("original-annotations.tsv","original-detail.tsv","original-distribution.tsv","original-output.json","original-cell-kinds.json","original-csAnno.rds","original-nearest.rds","original-ordered-features.rds","original-nearest.json","query-ledger.tsv","selected-query-ordinals.tsv","source-status.json"))
                            Files.copy(from.resolve(name),to.resolve(name));
                        NextflowFiles.writeJson(to.resolve("analysis-status.json"),Map.of("status","completed-with-warnings","result_admitted",true,"report",reports.get(i)));
                    }
                    peaks.verify();txdb.verify();verifyRequest(source,requestPin);ChipSeekerAnnotationRuntime.sources(stage,request.version(),true);
                    var report=new LinkedHashMap<String,Object>();report.put("version",request.version());report.put("reference_build_declared",request.build());report.put("coordinate_system","one-based-inclusive");
                    report.put("input_peaks",rows.size());report.put("analyses",reports);report.put("scope",SCOPE);report.put("analysis_installs_software",false);report.put("historical_dependency_environment_reproduced",false);
                    NextflowFiles.writeJson(stage.resolve("report.json"),report);state.put("status","completed-with-warnings");state.put("result_admitted",true);state.put("analyses",reports.size());
                    warnings.add("The original TxDb sequence metadata is retained in worker/txdb-metadata.tsv and worker/txdb-sequence-info.json. Compare it and the captured SHA-256 to the intended reference. A declaration alone does not prove assembly identity.");
                }catch(ChipSeekerAnnotationRuntime.Unavailable unavailable){exit[0]=3;state.put("status","SKIP3_DEPENDENT_ANALYSIS_SKIPPED");state.put("cause",unavailable.toString());warnings.add("SKIP3: "+unavailable);}
                catch(Exception failure){exit[0]=2;state.put("cause",failure.toString());warnings.add("FAIL2: "+failure);}
                catch(OutOfMemoryError failure){exit[0]=2;state.put("cause","Java heap exhausted");warnings.add("FAIL2: Java heap exhausted. Use suitable capacities/heap and NEW output paths; partial results are unadmitted.");}
                if(exit[0]!=0){
                    Path results=stage.resolve("results"),partial=stage.resolve("unadmitted-partial-results");
                    if(Files.exists(results)){require(results.toRealPath().startsWith(stage.toRealPath())&&partial.toAbsolutePath().normalize().getParent().equals(stage.toAbsolutePath().normalize()),"Refuse an out-of-stage partial-results move");Files.move(results,partial);}
                    Path report=stage.resolve("report.json");if(Files.exists(report))Files.move(report,stage.resolve("unadmitted-report.json"));
                    appendOriginalErrors(stage,request,warnings);
                }
                state.put("required_action",ACTION);state.put("version",request.version());state.put("atomic_batch",true);
                NextflowFiles.writeJson(stage.resolve("analysis-status.json"),state);Files.writeString(stage.resolve("WARNINGS.md"),"# "+state.get("status")+"\n\n"+String.join("\n\n",warnings)+"\n\nREQUIRED ACTION: "+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                manifest.put("analysis_status",state.get("status"));manifest.put("result_admitted",state.get("result_admitted"));manifest.put("version",request.version());manifest.put("scope",SCOPE);
            });
            emit(exit[0]==0?output:error,(exit[0]==0?"Original ChIPseeker annotation completed: ":exit[0]==3?"SKIP3 DEPENDENT ANALYSIS SKIPPED: ":"FAIL2 NO RESULT ADMITTED: ")+request.out());
            emit(error,"WARNINGS / REQUIRED ACTIONS: "+request.out().resolve("WARNINGS.md")+"\n"+ACTION);return exit[0];
        }catch(Exception failure){emit(error,"FAIL2 NO RESULT ADMITTED: "+failure+"\n"+ACTION);return 2;}
        catch(OutOfMemoryError failure){emit(error,"FAIL2 NO RESULT ADMITTED: Java heap exhausted. "+ACTION);return 2;}
    }
    private static void verifyRequest(Path source,NextflowFiles.Pin pin)throws IOException{require(pin.equals(NextflowFiles.pin(source,1048576,()->{})),"Original annotation request changed");}
    private static Map<String,Object> fingerprint(NextflowFiles.Pin p){return Map.of("bytes",p.bytes(),"sha256",p.sha256());}
    private static Captured capture(Path source,Path copy,long maximum)throws IOException{
        require(maximum>=0,"Complete input population exceeds maximum_input_bytes");var pin=NextflowFiles.pin(source,maximum,()->{});long count=0;
        try(var input=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS);var output=Files.newOutputStream(copy,StandardOpenOption.CREATE_NEW)){
            byte[] buffer=new byte[65536];for(int n=input.read(buffer);n>=0;n=input.read(buffer)){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Input capture cancelled");require(n<=maximum-count,"Input grew beyond capture capacity");output.write(buffer,0,n);count+=n;}
        }
        var result=new Captured(source,copy,pin);require(count==pin.bytes(),"Input changed during complete capture");result.verify();return result;
    }
    private static List<PeakRow> peaks(Path path,Request request)throws IOException{
        String value=NextflowFiles.utf8(NextflowFiles.bytes(path,request.peakBytes())).replace("\r\n","\n");require(!value.contains("\r"),"Peak records must use LF or CRLF");
        var lines=value.lines().iterator();require(lines.hasNext(),"Missing peak header");String header=lines.next();boolean stranded=header.equals("peak_id\tcontig\tstart\tend\tstrand");require(stranded||header.equals("peak_id\tcontig\tstart\tend"),"Require literal peak TSV header, optionally with strand");
        var result=new ArrayList<PeakRow>();while(lines.hasNext()){
            String line=lines.next();require(!line.isEmpty()&&line.length()<=65536&&result.size()<request.maximumPeaks(),"Blank/excessive peak row or maximum_peaks exceeded");
            String[] f=line.split("\t",-1);require(f.length==(stranded?5:4),"Ragged peak row");label(f[0],32768);label(f[1],4096);
            int start=coordinate(f[2]),end=coordinate(f[3]);require(start<=end,"Reversed peak interval");String strand=stranded?f[4]:"*";require(Set.of("+","-","*").contains(strand),"Invalid peak strand");result.add(new PeakRow(f[0],f[1],start,end,strand));
        }require(!result.isEmpty(),"Original annotation requires a nonempty peak population; no empty successful result is inferred");return List.copyOf(result);
    }
    private static int coordinate(String s)throws IOException{require(s.matches("[1-9][0-9]{0,9}"),"Coordinates must be literal positive one-based integers");try{return Integer.parseInt(s);}catch(NumberFormatException error){throw new IOException("Coordinate exceeds the source 32-bit range",error);}}
    private static Map<String,Object> read(Path p,int cap)throws IOException{return object(NextflowParameters.parse(NextflowFiles.utf8(NextflowFiles.bytes(p,cap)),cap,10000000));}
    private static Map<String,Object> audit(Path folder,Analysis analysis,List<PeakRow> peaks,Request request,List<String> warnings)throws IOException{
        var status=read(folder.resolve("source-status.json"),request.jsonBytes());require("original-returned".equals(status.get("status"))&&status.get("original_error")==null,"Original configuration did not return successfully: "+analysis.id());
        for(Object warning:list(status.get("warnings")))warnings.add(analysis.id()+": ORIGINAL WARNING: "+text(warning));
        var output=read(folder.resolve("original-output.json"),request.jsonBytes());var nearest=read(folder.resolve("original-nearest.json"),request.jsonBytes());var kinds=read(folder.resolve("original-cell-kinds.json"),request.jsonBytes());
        var rows=list(output.get("annotations"));var detail=list(output.get("detail"));var indices=list(nearest.get("retained_input_ordinals"));var featureIndices=list(nearest.get("index_tokens"));var distances=list(nearest.get("distance_tokens"));var features=list(nearest.get("features"));
        require(rows.size()==indices.size()&&rows.size()==detail.size()&&rows.size()==featureIndices.size()&&rows.size()==distances.size(),"Original output/nearest/detail population mismatch");
        require(list(output.get("tss_region")).stream().map(Object::toString).toList().size()==2,"Invalid original promoter metadata");
        var actualWindow=list(output.get("tss_region"));for(int i=0;i<2;i++)require(integer(actualWindow.get(i),-1000000,1000000)==analysis.tssRegion().get(i),"Original promoter window changed");
        require(list(output.get("level")).equals(List.of(analysis.level()))&&integer(list(output.get("peak_num")).get(0),1,request.maximumPeaks())==peaks.size(),"Original level or peak population changed");
        var byInput=new LinkedHashMap<Integer,Integer>();int previous=0;
        for(int i=0;i<rows.size();i++){
            int ordinal=(int)integer(indices.get(i),1,peaks.size());require(ordinal>previous,"Original retained query order/identity differs");previous=ordinal;byInput.put(ordinal,i);
            var row=object(rows.get(i));var peak=peaks.get(ordinal-1);
            require(integer(row.get("input_ordinal"),1,peaks.size())==ordinal&&text(row.get("peak_id")).equals(peak.id())&&text(row.get("seqnames")).equals(peak.contig())&&integer(row.get("start"),1,Integer.MAX_VALUE)==peak.start()&&integer(row.get("end"),1,Integer.MAX_VALUE)==peak.end()&&text(row.get("strand")).equals(peak.strand()),"Original query identity/coordinates changed");
            require(sameDistance(row.get("distanceToTSS"),text(distances.get(i))),"Original annotation/nearest distances changed");
        }
        String nativeStatus="passed",skip=null;
        if(peaks.size()>1000000||features.size()>1000000)skip="Original result exceeds the native API's one-million-query/feature population contract; no partial native audit is admitted.";
        if(analysis.sameStrand()||peaks.stream().anyMatch(p->!p.strand().equals("*")))skip="Native nearest contract covers unstranded peaks with sameStrand=false; original source result remains distinct.";
        if(peaks.stream().anyMatch(p->p.contig().equals("chrNA"))||features.stream().anyMatch(v->{try{var f=object(v);return text(f.get("seqnames")).equals("chrNA")||!Set.of("+","-").contains(text(f.get("strand")));}catch(IOException e){return false;}}))skip="Native reserved-contig/wildcard-feature domain is unsupported; no native result is admitted.";
        if(skip==null){
            try{
                var nativePeaks=new ArrayList<Peak>();for(var p:peaks)nativePeaks.add(new Peak(p.contig(),p.start(),p.end()));
                var nativeFeatures=new ArrayList<Feature>();for(Object value:features){var f=object(value);nativeFeatures.add(new Feature(text(f.get("seqnames")),(int)integer(f.get("start"),1,Integer.MAX_VALUE),(int)integer(f.get("end"),1,Integer.MAX_VALUE),text(f.get("strand")).charAt(0)));}
                var nativeResult=ChipSeekerNearestFeatures.annotate(nativePeaks,nativeFeatures,new Options(analysis.ignoreOverlap(),analysis.ignoreUpstream(),analysis.ignoreDownstream(),analysis.overlap().equals("TSS")?Overlap.TSS:Overlap.ALL),new Limits(Math.max(1,peaks.size()),Math.max(1,features.size()),request.nativeOperations()),()->{if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Native audit cancelled");});
                var retained=nativeResult.sourceRetained();require(retained.size()==rows.size(),"Native/source nearest retained population differs");
                for(int i=0;i<retained.size();i++){var r=retained.get(i);require(r.inputIndex()+1==integer(indices.get(i),1,peaks.size())&&Integer.toString(r.sourceFeatureIndex()).equals(text(featureIndices.get(i)))&&sameDistance(r.distanceToTss(),text(distances.get(i))),"Native/source nearest assignment differs for "+analysis.id());}
                warnings.addAll(nativeResult.warnings().stream().map(w->analysis.id()+": NATIVE AUDIT: "+w).toList());
            }catch(ChipSeekerNearestFeatures.UnsupportedInput unsupported){skip=unsupported.getMessage();}
            catch(IllegalArgumentException capacity){if(capacity.getMessage().equals("Complete native nearest-feature operation capacity exceeded; no partial result is admitted"))skip=capacity.getMessage();else throw capacity;}
        }
        if(skip!=null){nativeStatus="SKIPPED_NO_NATIVE_RESULT";warnings.add(analysis.id()+": NATIVE AUDIT SKIPPED: "+skip+" Original genomic annotation is an upstream result; do not claim native verification for this configuration.");}
        var ledger=new ArrayList<List<String>>();var selected=new ArrayList<List<String>>();int dummies=0;
        var names=list(nearest.get("original_feature_names"));var annotationKinds=list(object(kinds.get("annotations")).get("annotation"));require(annotationKinds.size()==rows.size(),"Original missing-value kind population differs");
        for(int ordinal=1;ordinal<=peaks.size();ordinal++){
            PeakRow peak=peaks.get(ordinal-1);Integer at=byInput.get(ordinal);String disposition="SOURCE_DROPPED",index="",contig="",start="",end="",strand="",name="",distance="",annotation="",kind="absent-source-row";
            if(at!=null){
                var row=object(rows.get(at));index=text(featureIndices.get(at));distance=text(distances.get(at));annotation=text(row.get("annotation"));kind=text(annotationKinds.get(at));
                int feature;try{feature=Integer.parseInt(index);}catch(NumberFormatException invalid){throw new IOException("Unsupported original nearest feature token; preserved raw source admits no project result: "+index,invalid);}
                require(feature>=1&&feature<=features.size()+1,"Original nearest index outside complete feature/dummy population");
                disposition=feature==features.size()+1?"SOURCE_BOUNDARY_DUMMY":"SOURCE_MATCHED";
                if(feature<=features.size()){var f=object(features.get(feature-1));contig=text(f.get("seqnames"));start=Long.toString(integer(f.get("start"),1,Integer.MAX_VALUE));end=Long.toString(integer(f.get("end"),1,Integer.MAX_VALUE));strand=text(f.get("strand"));name=names.isEmpty()?"":text(names.get(feature-1));}else dummies++;
                if(analysis.selection()==null||(kind.equals("value")&&analysis.selection().stream().anyMatch(annotation::startsWith)))selected.add(List.of(Integer.toString(ordinal),peak.id(),annotation,kind));
            }
            ledger.add(List.of(Integer.toString(ordinal),peak.id(),peak.contig(),Integer.toString(peak.start()),Integer.toString(peak.end()),peak.strand(),disposition,index,contig,start,end,strand,name,distance,annotation,kind));
        }
        table(folder.resolve("query-ledger.tsv"),List.of("input_ordinal","peak_id","contig","start","end","strand","disposition","source_feature_ordinal","literal_feature_contig","literal_feature_start","literal_feature_end","literal_feature_strand","original_feature_name","distance_to_tss_token","original_annotation","annotation_kind"),ledger);
        table(folder.resolve("selected-query-ordinals.tsv"),List.of("input_ordinal","peak_id","original_annotation","annotation_kind"),selected);
        warnings.add(analysis.id()+": "+(peaks.size()-rows.size())+" source-dropped queries and "+dummies+" boundary dummies remain explicit in query-ledger.tsv. Original numeric geneChr/geneStrand fields are retained without repair. No enhancer definition is inferred; selection uses only the caller's literal prefixes.");
        var report=new LinkedHashMap<String,Object>();report.put("id",analysis.id());report.put("source_returned",rows.size());report.put("source_dropped",peaks.size()-rows.size());report.put("source_boundary_dummies",dummies);report.put("features",features.size());report.put("selected_rows",selected.size());report.put("native_nearest_audit",nativeStatus);if(skip!=null)report.put("native_audit_skip_reason",skip);report.put("effective_settings",analysis.json());return report;
    }
    private static boolean sameDistance(Object x,String expected)throws IOException{
        String actual=x instanceof NextflowParameters.NumberToken n?n.text():x instanceof Number?x.toString():text(x);
        if(Set.of("NA","NaN","Inf","-Inf").contains(expected))return actual.equals(expected)||(expected.equals("Inf")&&actual.equals("Infinity"))||(expected.equals("-Inf")&&actual.equals("-Infinity"));
        try{return Double.parseDouble(actual)==Double.parseDouble(expected);}catch(NumberFormatException invalid){throw new IOException("Invalid original distance token",invalid);}
    }
    private static void appendOriginalErrors(Path stage,Request request,List<String> warnings){
        Path report=stage.resolve("worker/worker-report.json");if(!Files.isRegularFile(report))return;
        try{var value=read(report,request.jsonBytes());for(Object warning:list(value.get("warnings")))warnings.add("ORIGINAL R WARNING: "+text(warning));for(Object item:list(value.get("cases"))){var c=object(item);if(c.get("original_error")!=null)warnings.add("ORIGINAL CONFIGURATION ERROR "+text(c.get("id"))+": "+text(object(c.get("original_error")).get("message"))+". This batch admits no result.");}}catch(IOException failure){warnings.add("Could not summarize original errors: "+failure+". Raw worker logs/reports are retained; no result admitted.");}
    }
    private static void table(Path path,List<String> header,List<List<String>> rows)throws IOException{
        try(var out=Files.newBufferedWriter(path,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){line(out,header);for(var row:rows)line(out,row);}
    }
    private static void line(Writer out,List<String> row)throws IOException{boolean first=true;for(String s:row){if(!first)out.write('\t');first=false;if(s.indexOf('"')>=0||s.indexOf('\t')>=0||s.indexOf('\n')>=0||s.indexOf('\r')>=0)out.write('"'+s.replace("\"","\"\"")+'"');else out.write(s);}out.write('\n');}
    private static void emit(PrintStream out,String s){out.writeBytes((s+"\n").getBytes(StandardCharsets.UTF_8));}
}
