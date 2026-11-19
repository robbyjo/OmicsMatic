/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.PlinkRelatednessSupport.*;

/** Captured original PLINK KING/IBD workflows with portable native pair audits. */
public final class PlinkRelatednessCli {
    private PlinkRelatednessCli() { }
    static final String STATUS="completed-with-warnings";
    static final String SCOPE="SCOPED ORIGINAL PLINK RELATEDNESS: pinned Windows PLINK2 KING tables, triangular matrices and cutoff selection, or PLINK1.9 GENOME IBD. Inputs are already prepared, sorted, biallelic diploid human autosomal SNP BED/BIM/FAM files. No QC, LD pruning, ancestry restriction, imputation, ID repair or participant removal is inferred. Native counts/kinship, count-derived PPC arithmetic and rounded-field relations do not independently refit IBD frequencies/Z estimates, PPC thinning, pedigree inference, selection optimality or biological relationships. Original unbounded estimates remain unbounded. No historical paper, whole-tool, Windows DLL or producer authentication claim.";
    static final String ACTION="SKIP3: the exact permitted original runtime/platform is unavailable; supply the named sealed private profile using the exported setup helper, or leave dependent analysis skipped. RUN never installs software. FAIL2: no complete result admitted; retain source inputs, partial outputs and original logs, fix the named input/model/numerical/integrity/process/capacity cause and retry NEW paths. Read analysis-status.json, diagnostics.json, WARNINGS.md and worker/result.log/stdout/stderr. A zero original exit is insufficient. Never turn NaN/Inf/NA into zero, clip unbounded IBD estimates, equate KING kinship with PI_HAT, infer optimal selection or apply exact cutoffs to rounded printed values. VERIFY checks captured consistency/native arithmetic without original software; hashes do not authenticate coordinated fabricated captures. Logical/observed limits are not hard OS containment or memory quotas.";
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    static int run(String[] args,PrintStream out,PrintStream err){
        try {
            if(args.length==1&&args[0].equals("--help")){emit(out,"PlinkRelatednessCli --request UTF8_JSON\nModes RUN, VERIFY and REPLAY. Operations KING_TABLE, KING_MATRIX, KING_CUTOFF and GENOME.\nPlinkRelatednessCli --export-runtime-setup NEW_DIRECTORY\n"+SCOPE+"\n"+ACTION);return 0;}
            if(args.length==2&&args[0].equals("--export-runtime-setup")){exportSetup(Path.of(args[1]));emit(out,"Exported explicit-path setup helper. Use --home, --archives, --report and explicit permission flags; setup performs no analysis.");return 0;}
            require(args.length==2&&args[0].equals("--request"),"Require --request UTF8_JSON, --help or --export-runtime-setup NEW_DIRECTORY");
            Path source=Path.of(args[1]).toAbsolutePath().normalize();var raw=read(source);String mode=text(raw,"mode");
            if(mode.equals("VERIFY")){
                keys(raw,"schema_version","mode","analysis","wall_seconds");integer(raw,"schema_version",1,1);
                verify(resolve(source.getParent(),text(raw,"analysis")),(int)integer(raw,"wall_seconds",1,Integer.MAX_VALUE));emit(out,"Verified captured original PLINK reports and declared native arithmetic without an original runtime. "+ACTION);return 0;
            }
            if(mode.equals("REPLAY"))return replay(source,raw,out,err);
            return execute(source,raw,out,err);
        }catch(OutOfMemoryError e){emit(err,"FAIL2: Java heap exhausted; no complete result admitted. Supply suitable heap/capacities and NEW output. "+ACTION);return 2;}
        catch(Exception e){emit(err,"FAIL2: no complete result admitted: "+e+"\n"+ACTION);return 2;}
    }
    private static int execute(Path source,Map<String,Object> raw,PrintStream out,PrintStream err)throws Exception {
        var r=PlinkRelatednessContracts.validate(raw);var l=r.limits();var clock=new Clock(l.wallSeconds());
        Path destination=resolve(source.getParent(),r.out()),home=resolve(source.getParent(),r.runtimeHome()),input=resolve(source.getParent(),r.bfile());
        require(!Files.exists(destination,LinkOption.NOFOLLOW_LINKS),"Output already exists; choose NEW paths");
        destination=PlinkToolsCli.resolved(destination);Path realHome=PlinkToolsCli.resolved(home);
        require(!destination.startsWith(realHome)&&!realHome.startsWith(destination),"Output and runtime home must not contain one another, including through existing directory aliases");
        for(String suffix:List.of(".bed",".bim",".fam"))require(!PlinkToolsCli.resolved(Path.of(input+suffix)).startsWith(destination),"Output must not contain original inputs");
        Files.createDirectories(destination.getParent());Path parent=destination.getParent().toRealPath();destination=parent.resolve(destination.getFileName());
        Path stage=Files.createTempDirectory(parent,".omicsmatic-plink-relatedness-");int code=2;String error=null;boolean restoreInterrupt=false;
        var manifest=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();PlinkRelatednessReports.Result report=null;
        try {
            copy(source,stage.resolve("source-request.json"),1_048_576,clock);require(same(raw,read(stage.resolve("source-request.json"))),"Source request changed during admission");
            manifest.put("source_request_sha256",hash(stage.resolve("source-request.json"),clock));
            var captured=new LinkedHashMap<String,Object>(raw);captured.put("bfile","inputs/cohort");captured.put("out",".");json(stage.resolve("request.json"),captured);
            var runtime=new PlinkRelatednessRuntime(home,stage,r,clock);
            Files.createDirectory(stage.resolve("inputs"));var inputPins=new TreeMap<String,String>();var originalPins=new TreeMap<String,String>();
            long declaredInputBytes=0;
            for(String suffix:List.of(".fam",".bim",".bed")){
                Path original=Path.of(input+suffix);require(Files.isRegularFile(original,LinkOption.NOFOLLOW_LINKS),"Missing/linked original genotype input: "+original);
                declaredInputBytes=Math.addExact(declaredInputBytes,Files.size(original));
                require(declaredInputBytes<=l.inputBytes(),"Combined original input_bytes capacity exceeded before hashing/capture");
            }
            for(String suffix:List.of(".fam",".bim",".bed"))originalPins.put(suffix,hash(Path.of(input+suffix),clock));
            long remaining=l.inputBytes();for(String suffix:List.of(".fam",".bim",".bed")){Path target=stage.resolve("inputs/cohort"+suffix);remaining-=copy(Path.of(input+suffix),target,remaining,clock);String digest=hash(target,clock);require(digest.equals(originalPins.get(suffix)),"Original input changed before the complete snapshot");inputPins.put("inputs/cohort"+suffix,digest);}
            manifest.put("input_sha256",inputPins);
            var prepared=PlinkRelatednessInputs.prepare(stage,r,clock);
            runtime.execute(PlinkRelatednessContracts.arguments(r),"analysis");
            report=PlinkRelatednessReports.publish(stage,r,prepared,false,clock);
            for(String suffix:originalPins.keySet())require(originalPins.get(suffix).equals(hash(Path.of(input+suffix),clock)),"Original input changed during analysis: "+suffix);
            for(var e:inputPins.entrySet())require(e.getValue().equals(hash(stage.resolve(e.getKey()),clock)),"Captured genotype input changed during analysis");
            require(manifest.get("source_request_sha256").equals(hash(source,clock)),"Original request changed during analysis");runtime.verify();
            warnings.addAll(report.warnings());warnings.addAll(sourceWarnings(stage,r,clock));
            writeMetadata(stage,r,manifest,report,STATUS,true,warnings,null);
            checkInventory(stage,r,false,clock);
            seal(stage,l,clock);verifySeal(stage,l,clock);code=0;
        }catch(PlinkRelatednessRuntime.Unavailable e){code=3;error=e.toString();}
        catch(OutOfMemoryError e){code=2;error="Java heap exhausted during relatedness work";}
        catch(Exception e){code=2;error=e.toString();restoreInterrupt=Thread.interrupted()||e instanceof InterruptedIOException;}
        if(code!=0){
            String status=code==3?"SKIP3_DEPENDENT_ANALYSIS_SKIPPED":"FAIL2_NO_RESULT_ADMITTED";
            for(String name:List.of("pairs.tsv","selection.tsv"))if(Files.exists(stage.resolve(name))){Files.createDirectories(stage.resolve("partial"));Files.move(stage.resolve(name),stage.resolve("partial/"+name));}
            warnings.add(status+": "+error+". Raw worker outputs and partial exports are retained, not admitted.");
            writeMetadata(stage,r,manifest,report,status,false,warnings,error);
            try{seal(stage,l,new Clock(30));}catch(Exception|OutOfMemoryError failure){emit(err,"Failed attempt retained without a complete seal: "+failure);}
        }
        require(stage.toRealPath().getParent().equals(parent)&&destination.getParent().equals(parent),"Owned publication paths changed");
        try{Files.move(stage,destination);}catch(IOException e){
            warnings.add("FAIL2 output publication failed: "+e+". Release the named file lock or choose a writable NEW destination; retain this staging folder.");
            writeMetadata(stage,r,manifest,report,"FAIL2_OUTPUT_PUBLICATION_FAILED",false,warnings,e.toString());
            emit(err,"FAIL2: publication failed; retained complete attempt at "+stage+". "+ACTION);return 2;
        }
        emit(code==0?out:err,(code==0?"Completed captured original PLINK relatedness: ":code==3?"SKIP3: dependent original analysis skipped: ":"FAIL2: no result admitted: ")+destination);
        emit(err,"WARNINGS / REQUIRED ACTIONS: "+destination.resolve("WARNINGS.md")+"\n"+ACTION);if(restoreInterrupt)Thread.currentThread().interrupt();return code;
    }
    private static void writeMetadata(Path stage,PlinkRelatednessContracts.Request r,Map<String,Object> manifest,PlinkRelatednessReports.Result report,
            String status,boolean admitted,List<String> warnings,String error)throws IOException {
        var summary=report==null?Map.<String,Object>of():report.summary();
        var state=new LinkedHashMap<String,Object>();state.put("schema_version",1);state.put("status",status);state.put("results_admitted",admitted);state.put("required_action",ACTION);state.put("summary",summary);state.put("engine",r.engine());state.put("profile",PlinkRelatednessContracts.PROFILE);if(error!=null)state.put("error",error);
        manifest.put("schema_version",1);manifest.put("engine",r.engine());manifest.put("profile",PlinkRelatednessContracts.PROFILE);manifest.put("analysis_status",status);manifest.put("results_admitted",admitted);manifest.put("scope",SCOPE);manifest.put("summary",summary);manifest.put("independent_complete_model_refit",false);manifest.put("installed_DLL_source_authentication",false);manifest.put("whole_paper_reproduced",false);
        json(stage.resolve("analysis-status.json"),state);json(stage.resolve("manifest.json"),manifest);json(stage.resolve("diagnostics.json"),summary);
        Files.writeString(stage.resolve("WARNINGS.md"),warningText(status,warnings),StandardCharsets.UTF_8);
        json(stage.resolve("verify-request.json"),Map.of("schema_version",1,"mode","VERIFY","analysis",".","wall_seconds",r.limits().wallSeconds()));
    }
    private static String warningText(String status,List<String> warnings){return "# "+status+"\n\n"+SCOPE+"\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: "+ACTION+"\n";}
    static void verify(Path root,int seconds)throws IOException {
        root=root.toAbsolutePath().normalize();require(Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)&&!Files.isSymbolicLink(root),"Capture must be a real directory");
        var state=read(root.resolve("analysis-status.json"));require(STATUS.equals(state.get("status"))&&Boolean.TRUE.equals(state.get("results_admitted")),"Failed/skipped/incomplete captures are not admitted");
        var clock=new Clock(seconds);var captured=read(root.resolve("request.json"));var r=PlinkRelatednessContracts.validate(captured);
        require(r.bfile().equals("inputs/cohort")&&r.out().equals("."),"Captured input/output bindings changed");verifySeal(root,r.limits(),clock);checkInventory(root,r,true,clock);
        keys(state,"schema_version","status","results_admitted","required_action","summary","engine","profile");
        require(integer(state,"schema_version",1,1)==1&&text(state,"status").equals(STATUS)&&bool(state,"results_admitted")&&text(state,"required_action").equals(ACTION)&&text(state,"engine").equals(r.engine())&&text(state,"profile").equals(PlinkRelatednessContracts.PROFILE),"Failed/skipped/incomplete captures are not admitted");
        var manifest=read(root.resolve("manifest.json"));keys(manifest,"schema_version","engine","profile","analysis_status","results_admitted","scope","source_request_sha256","input_sha256","summary","independent_complete_model_refit","installed_DLL_source_authentication","whole_paper_reproduced");
        require(integer(manifest,"schema_version",1,1)==1&&text(manifest,"engine").equals(r.engine())&&text(manifest,"profile").equals(PlinkRelatednessContracts.PROFILE)&&text(manifest,"analysis_status").equals(STATUS)&&bool(manifest,"results_admitted")&&text(manifest,"scope").equals(SCOPE),"Admitted engine/status/scope differs");
        for(String key:List.of("independent_complete_model_refit","installed_DLL_source_authentication","whole_paper_reproduced"))require(!bool(manifest,key),"Unsupported refit/authentication/reproduction claim");
        var original=read(root.resolve("source-request.json"));PlinkRelatednessContracts.validate(original);require(hash(root.resolve("source-request.json"),clock).equals(text(manifest,"source_request_sha256")),"Original request hash differs");
        original.put("bfile","inputs/cohort");original.put("out",".");require(same(original,captured),"Captured settings differ from original request");
        var pins=object(manifest.get("input_sha256"),"captured input hashes");require(pins.keySet().equals(Set.of("inputs/cohort.bed","inputs/cohort.bim","inputs/cohort.fam")),"Input hash population differs");
        for(var e:pins.entrySet())require(hash(root.resolve(e.getKey()),clock).equals(e.getValue()),"Captured input hash differs");
        PlinkRelatednessRuntime.validateCapture(root,r,clock);var prepared=PlinkRelatednessInputs.prepare(root,r,clock);var report=PlinkRelatednessReports.publish(root,r,prepared,true,clock);
        require(same(report.summary(),state.get("summary"))&&same(report.summary(),manifest.get("summary"))&&same(report.summary(),read(root.resolve("diagnostics.json"))),"Published scientific summary differs");
        var warnings=new ArrayList<>(report.warnings());warnings.addAll(sourceWarnings(root,r,clock));require(Files.readString(root.resolve("WARNINGS.md")).equals(warningText(STATUS,warnings)),"Warnings/recovery instructions differ");
        var verify=read(root.resolve("verify-request.json"));require(same(verify,Map.of("schema_version",1,"mode","VERIFY","analysis",".","wall_seconds",r.limits().wallSeconds())),"Saved portable verify request differs");
    }
    private static List<String> sourceWarnings(Path root,PlinkRelatednessContracts.Request r,Clock clock)throws IOException {
        var result=new ArrayList<String>();
        for(String name:List.of("analysis.stdout","analysis.stderr","result.log")){
            boolean warning=false;try(var in=new Lines(root.resolve("worker/"+name),r.limits().lineBytes(),clock)){String line;while((line=in.next())!=null)if(line.stripLeading().toLowerCase(Locale.ROOT).startsWith("warning"))warning=true;}
            if(warning)result.add("Original "+r.engine()+" emitted warnings in worker/"+name+". Read that complete retained file and resolve the named cause before interpreting relatedness.");
        }
        return result;
    }
    private static void checkInventory(Path root,PlinkRelatednessContracts.Request r,boolean sealed,Clock clock)throws IOException {
        var names=new HashSet<>(Set.of("source-request.json","request.json","runtime-bindings.json","source/runtime-lock.json","source/runtime-marker.json","inputs/cohort.bed","inputs/cohort.bim","inputs/cohort.fam","worker/version.stdout","worker/version.stderr","worker/version-process.json","worker/analysis.stdout","worker/analysis.stderr","worker/analysis-process.json","worker/result.log","analysis-status.json","manifest.json","diagnostics.json","WARNINGS.md","verify-request.json"));
        switch(r.operation()){
            case KING_TABLE->names.addAll(List.of("worker/result.kin0","pairs.tsv"));
            case KING_MATRIX->names.addAll(List.of("worker/result.king.bin","worker/result.king.id","pairs.tsv"));
            case KING_CUTOFF->{names.addAll(List.of("worker/result.king.cutoff.in.id","worker/result.king.cutoff.out.id","selection.tsv"));if(bool(r.settings(),"audit_retained_pairs"))names.add("pairs.tsv");}
            case GENOME->names.addAll(List.of("worker/result.genome","pairs.tsv"));
        }
        if(Files.exists(root.resolve("worker/result.nosex")))names.add("worker/result.nosex");if(sealed)names.add("capture-files.json");
        require(footprint(root,r.limits().files(),r.limits().diskBytes(),clock).keySet().equals(names),"Capture has missing/extra files outside the declared original-profile protocol");
    }
    private static int replay(Path source,Map<String,Object> raw,PrintStream out,PrintStream err)throws Exception {
        keys(raw,"schema_version","mode","analysis","out","runtime_home","wall_seconds");integer(raw,"schema_version",1,1);int seconds=(int)integer(raw,"wall_seconds",1,Integer.MAX_VALUE);
        Path prior=resolve(source.getParent(),text(raw,"analysis"));verify(prior,seconds);var request=read(prior.resolve("request.json"));request.put("bfile",prior.resolve("inputs/cohort").toString());request.put("out",resolve(source.getParent(),text(raw,"out")).toString());request.put("runtime_home",resolve(source.getParent(),text(raw,"runtime_home")).toString());
        var limits=object(request.get("limits"),"limits");limits.put("wall_seconds",seconds);request.put("limits",limits);
        Path destination=Path.of(text(request,"out"));require(!Files.exists(destination),"Replay output exists");Files.createDirectories(destination.getParent());
        Path protocol=Files.createTempFile(destination.getParent(),"plink-relatedness-replay-",".json");json(protocol,request);
        // Retain the explicit new replay request; the original capture is never altered.
        return execute(protocol,request,out,err);
    }
    private static void exportSetup(Path requested)throws IOException {
        Path target=requested.toAbsolutePath().normalize();require(!Files.exists(target,LinkOption.NOFOLLOW_LINKS),"Setup export destination exists");Files.createDirectories(target.getParent());Files.createDirectory(target);
        for(String name:List.of("install_plink.py","plink-runtime-lock.json","setup_plink_relatedness.py"))Files.write(target.resolve(name),PlinkRelatednessRuntime.resource(name),StandardOpenOption.CREATE_NEW);
        Files.writeString(target.resolve("README.txt"),"EXACT PRIVATE PLINK SETUP ONLY; NO ANALYSIS\nRun Python3.11+ setup_plink_relatedness.py --home NEW_PRIVATE_HOME --archives PRIVATE_ARCHIVES --report NEW_REPORT --allow-install\nAdd --allow-download only where downloading is permitted. The programs and GPL text remain private. No OS/PATH changes. Missing/forbidden prerequisites => SKIP3 and dependent analysis skipped. If Windows reports an access-denied publication after successful version probes, retain the attempt, release the named lock and retry NEW paths; never overwrite/re-seal a changed home.\n",StandardCharsets.UTF_8);
    }
    private static void emit(PrintStream stream,String value){stream.writeBytes((value+"\n").getBytes(StandardCharsets.UTF_8));}
}
