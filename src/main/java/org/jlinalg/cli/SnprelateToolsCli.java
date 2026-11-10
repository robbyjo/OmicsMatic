/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.SnprelateRequest.need;
import static org.jlinalg.cli.SnprelateAudit.map;

/** Supervised original SNPRelate1.46 KING, LD pruning and GRM workflows with complete native transport/QC checks. */
public final class SnprelateToolsCli {
    private SnprelateToolsCli() { }
    static final String SCOPE="Explicit original SNPRelate1.46.0 on a private Windows R4.6.1 runtime. Complete BED hard calls, original FID/IID/variant sidecars, explicit numeric row-index GDS transport, physical layout, QC, family context, LD method/window/start/seed, GRM method or exact/randomized PCA settings are retained. Native Java independently verifies complete genotypes, QC populations and KING fields. PCA preserves original eigenvector signs, all reported eigenvalues and uncomputed DSPEVX NaN placeholders; native checks verify dimensions, orthogonality and exact-algorithm eigen equations without refitting. Original LD/GRM estimators execute upstream and are not independently refitted by saved verification. No inferred paper settings, ancestry selection, input sorting, genotype/ID repair, PLINK substitution, biological validity, software authenticity or production-cohort performance claim. Logical storage/time/row capacities are not hard OS quotas; Windows system DLLs outside the supplied R distribution are not independently pinned.";
    static final String ACTION="Require exit0, result_admitted=true and complete --verify before using a bundle. Read WARNINGS.md, scientific-audit.json, native-variant-qc.tsv and original stdout/stderr. SKIP3: skip dependent original SNPRelate analysis; supply the named exact permitted private runtime using the exported helper. If downloads/installation are unavailable or forbidden, keep that analysis skipped. FAIL2: admit no result; preserve the full attempt and resolve its stated input/process/capacity/scientific discrepancy before a new explicit request. Never change IDs, calls, thresholds, seed, method, layout or versions to suppress failure. Empty SNP populations are rejected. Unassessable source values remain unchanged and make downstream_eligible=false; do not fill them or use them in downstream PCA/PC-AiR.";
    private record Runtime(Path script,Path library,Path rHome,Map<Path,Pin> files,byte[] raw) { }
    private static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String cause){super(cause);}
    }
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    public static int run(String[] args,PrintStream output,PrintStream error) {
        Path out=null;int code=2;Runtime runtime=null;SnprelateRequest.Request request=null;boolean runtimeValidated=false;boolean[] processLive={false};
        var protectedInputs=new LinkedHashMap<Path,Pin>();var warnings=new ArrayList<String>();warnings.add(SCOPE);
        var state=map("status","FAIL2_NO_SNPRELATE_RESULT_ADMITTED","result_admitted",false,"original_analysis_executed",false);
        try {
            if(args.length==1&&args[0].equals("--help")) {output.println("snprelate-tools --request JSON | --verify BUNDLE | --export-runtime-helper NEW_DIRECTORY\n"+SCOPE+"\n"+ACTION);return 0;}
            if(args.length==2&&args[0].equals("--export-runtime-helper")) {
                Path target=Path.of(args[1]).toAbsolutePath().normalize();need(!Files.exists(target,LinkOption.NOFOLLOW_LINKS),"Runtime helper target exists; choose NEW paths");
                Files.createDirectories(target.getParent());Files.createDirectory(target);Files.write(target.resolve("prepare_snprelate_runtime.py"),resource("prepare_snprelate_runtime.py"),StandardOpenOption.CREATE_NEW);
                Files.writeString(target.resolve("WARNINGS.md"),SCOPE+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                output.println("Exported runtime-profile helper only; no download, installation or analysis.\n"+ACTION);return 0;
            }
            if(args.length==2&&args[0].equals("--verify")) {verify(Path.of(args[1]).toAbsolutePath().normalize());output.println("PASS complete original SNPRelate saved bundle and native transport/QC/KING replay; no upstream analysis or producer authentication.");return 0;}
            need(args.length==2&&args[0].equals("--request"),"Require --request JSON, --verify BUNDLE or --help");
            Path source=Path.of(args[1]).toAbsolutePath().normalize().toRealPath();byte[] raw=bytes(source,1_048_576);
            request=SnprelateRequest.parse(NextflowParameters.parse(utf8(raw),1_048_576,100_000),source.getParent());
            Path target=resolved(request.out());need(!Files.exists(target,LinkOption.NOFOLLOW_LINKS),"Output exists; preserve previous attempts and choose NEW paths");
            for(Path path:List.of(source,request.profile(),bed(request,"bed"),bed(request,"bim"),bed(request,"fam")))separate(target,path);
            Files.createDirectories(target.getParent());out=Files.createDirectory(target);
            Files.write(out.resolve("source-request.json"),raw,StandardOpenOption.CREATE_NEW);
            Files.writeString(out.resolve("WARNINGS.initial.md"),"# PROVISIONAL: NO SNPRELATE RESULT ADMITTED\n\n"+SCOPE+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            long start=System.nanoTime(),maximumNanos=Duration.ofSeconds(request.limits().seconds()).toNanos();
            Runnable check=()->{if(Thread.currentThread().isInterrupted()||System.nanoTime()-start>maximumNanos)throw new java.util.concurrent.CancellationException("Complete SNPRelate wall-time/interruption capacity exceeded");};
            runtime=runtime(request.profile());for(Path file:runtime.files().keySet())separate(out,file);runtimePins(runtime,check);runtimeValidated=true;
            Files.write(out.resolve("runtime-profile.json"),runtime.raw(),StandardOpenOption.CREATE_NEW);
            Files.createDirectory(out.resolve("inputs"));long inputBytes=0;
            for(String ext:List.of("bed","bim","fam")) {
                Path original=bed(request,ext).toRealPath();Pin before=pin(original,request.limits().inputBytes(),check);
                inputBytes=Math.addExact(inputBytes,before.bytes());need(inputBytes<=request.limits().inputBytes(),"Complete original BED/BIM/FAM byte capacity exceeded");
                Path captured=out.resolve("inputs/cohort."+ext);copy(original,captured,before,check);protectedInputs.put(original,before);protectedInputs.put(captured,before);
            }
            var data=SnprelateAudit.inputs(out,request,check);SnprelateAudit.writeInputs(out,data,request,check);
            need(!data.qc().isEmpty(),"SCIENTIFIC DISCREPANCY: complete QC population is empty; skip dependent original analysis and preserve native-variant-qc.tsv");
            Path parameters=out.resolve("parameters.tsv");
            Files.writeString(parameters,parameterText(request),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);Files.write(out.resolve("worker.R"),resource("run_snprelate.R"),StandardOpenOption.CREATE_NEW);
            Files.createDirectory(out.resolve("worker"));Files.createDirectory(out.resolve("tmp"));
            List<String> command=List.of(runtime.script().toString(),"--vanilla",out.resolve("worker.R").toString(),runtime.library().toString(),
                out.resolve("inputs/cohort").toString(),out.resolve("worker").toString(),parameters.toString());
            var process=execute(out,command,runtime,request,check,state,processLive);
            writeJson(out.resolve("execution.json"),map("platform","windows","bundle_at_execution",out.toString(),"process",process));
            need(((Number)process.get("exit_code")).intValue()==0,"Original SNPRelate failed; inspect retained analysis.stdout, analysis.stderr and original-error.txt");
            for(var e:protectedInputs.entrySet())need(e.getValue().equals(pin(e.getKey(),e.getValue().bytes(),check)),"Original/captured input changed during SNPRelate execution");
            var audit=SnprelateAudit.outputs(out,out.resolve("worker"),request,data,check);writeJson(out.resolve("scientific-audit.json"),audit);
            need(Files.readString(out.resolve("worker/analysis.stdout"),StandardCharsets.UTF_8).contains("ORIGINAL_SNPRELATE_EXPLICIT_WORKFLOW_COMPLETE"),"Incomplete original SNPRelate completion marker");
            if(Boolean.FALSE.equals(audit.get("downstream_eligible")))warnings.add("WARNING: Unassessable original off-diagonal values or duplicate original variant IDs retained. downstream_eligible=false; skip dependent PCA/PC-AiR, ID-based extraction or models that require complete finite inputs and unambiguous identifiers. Read the named counts/flags in scientific-audit.json.");
            String sourceWarnings=Files.readString(out.resolve("worker/original-warnings.txt"),StandardCharsets.UTF_8);if(!sourceWarnings.isBlank())warnings.add("ORIGINAL WARNINGS:\n"+sourceWarnings);
            state.putAll(map("status","completed-original-SNPRelate-with-explicit-warnings","result_admitted",true,"downstream_eligible",audit.get("downstream_eligible"),"scientific_audit",audit));code=0;
        } catch(Unavailable unavailable) {
            state.putAll(map("status","SKIP3_EXACT_SNPRELATE_RUNTIME_UNAVAILABLE","cause",unavailable.getMessage()));warnings.add("SKIP3: "+unavailable.getMessage());code=3;
        } catch(Exception failure) {
            state.put("cause",failure.toString());warnings.add("FAIL2 NO RESULT ADMITTED: "+failure.getMessage());
        } catch(OutOfMemoryError failure) {
            state.put("cause","Java heap exhausted");warnings.add("FAIL2 NO RESULT ADMITTED: complete native audit exhausted heap; retry only with permitted heap/capacities and the same explicit scientific settings.");
        } finally {
            if(out!=null)try {
                need(!processLive[0],"Original process termination is unconfirmed; leave attempt unsealed and do not hash live outputs");
                if(runtimeValidated)try {runtimePins(runtime,()->{});}catch(Exception changed){code=2;state.put("runtime_preservation_failure",changed.toString());warnings.add("FAIL2: original runtime changed or disappeared; admit no result.");}
                for(var e:protectedInputs.entrySet())try{need(e.getValue().equals(pin(e.getKey(),e.getValue().bytes(),()->{})),"Protected input changed");}catch(Exception changed){code=2;state.put("input_preservation_failure",changed.toString());warnings.add("FAIL2: original/captured input changed; admit no result.");}
                if(code!=0) {
                    state.put("result_admitted",false);state.put("downstream_eligible",false);
                    if(code==2)state.put("status","FAIL2_NO_SNPRELATE_RESULT_ADMITTED");
                    if(Files.exists(out.resolve("worker")))Files.move(out.resolve("worker"),out.resolve("unadmitted-worker"));
                }
                state.put("required_action",ACTION);state.put("scope",SCOPE);writeJson(out.resolve("analysis-status.json"),state);
                Files.writeString(out.resolve("WARNINGS.md"),"# "+state.get("status")+"\n\n"+String.join("\n\n",warnings)+"\n\nREQUIRED ACTION: "+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                if(request!=null)capacity(out,request);
                var done=new LinkedHashMap<String,Object>(state);done.put("files",inventory(out));byte[] completion=(NextflowJson.json(done)+"\n").getBytes(StandardCharsets.UTF_8);
                if(request!=null)capacity(out,request,completion.length,1);
                Files.write(out.resolve("COMPLETED.pending.json"),completion,StandardOpenOption.CREATE_NEW);
                Files.move(out.resolve("COMPLETED.pending.json"),out.resolve("COMPLETED.json"),StandardCopyOption.ATOMIC_MOVE);
            } catch(Exception failure){
                code=2;state.putAll(map("status","FAIL2_NO_SNPRELATE_RESULT_ADMITTED","result_admitted",false,"downstream_eligible",false));
                warnings.add("FAIL2: attempt could not be sealed; admit no result: "+failure);error.println(warnings.get(warnings.size()-1));
                try{Files.writeString(out.resolve("UNSEALED-FAIL2.txt"),String.join("\n",warnings)+"\n\nREQUIRED ACTION: "+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}catch(IOException ignored){/* Existing/raw outputs remain preserved; stderr and exit2 are authoritative. */}
            }
        }
        (code==0?output:error).println(state.get("status")+"\n"+String.join("\n",warnings)+"\nREQUIRED ACTION: "+ACTION+(out==null?"":"\nEvidence: "+out));return code;
    }
    private static byte[] resource(String name)throws IOException {
        try(var stream=SnprelateToolsCli.class.getResourceAsStream("/org/jlinalg/omics/snprelate/"+name)){need(stream!=null,"Missing packaged SNPRelate resource: "+name);return stream.readAllBytes();}
    }
    private static String parameterText(SnprelateRequest.Request request) {
        var text=new StringBuilder("parameter\tvalue\n");for(var e:SnprelateRequest.parameters(request).entrySet())text.append(e.getKey()).append('\t').append(e.getValue()).append('\n');return text.toString();
    }
    private static Path bed(SnprelateRequest.Request r,String ext){return Path.of(r.bfile()+"."+ext);}
    private static Path resolved(Path path)throws IOException {
        Path parent=path.getParent();need(parent!=null,"Output requires a parent directory");var missing=new ArrayList<Path>();
        while(!Files.exists(parent,LinkOption.NOFOLLOW_LINKS)){missing.add(parent.getFileName());parent=parent.getParent();need(parent!=null,"No existing output ancestor");}
        parent=parent.toRealPath();for(int i=missing.size()-1;i>=0;i--)parent=parent.resolve(missing.get(i));return parent.resolve(path.getFileName());
    }
    private static void separate(Path out,Path input)throws IOException {Path p=Files.exists(input,LinkOption.NOFOLLOW_LINKS)?input.toRealPath():resolved(input);need(!p.startsWith(out)&&!out.startsWith(p),"SNPRelate output overlaps an input, request or runtime");}
    private static Runtime runtime(Path file)throws IOException {
        if(!Files.isRegularFile(file))throw new Unavailable("Missing exact private runtime-profile.json; export helper and supply permitted prerequisites");
        byte[] raw=bytes(file.toRealPath(),16_777_216);Map<?,?> p=profile(raw);
        if(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch").toLowerCase(Locale.ROOT)))throw new Unavailable("This exact runtime profile requires explicitly selected Windows x86_64 Java; Linux can verify saved evidence without R");
        Path script=Path.of(text(p.get("rscript"))),library=Path.of(text(p.get("private_library"))),rHome=Path.of(text(p.get("r_home")));
        need(script.isAbsolute()&&library.isAbsolute()&&rHome.isAbsolute()&&script.equals(rHome.resolve("bin/Rscript.exe")),"Invalid explicit R runtime roots");
        var files=new TreeMap<Path,Pin>();for(var e:object(p.get("files")).entrySet()) {
            Path name=Path.of(text(e.getKey()));need(name.isAbsolute()&&(name.startsWith(library)||name.startsWith(rHome)),"Runtime closure member escapes its declared roots");
            files.put(name,fingerprint(e.getValue(),2_000_000_000));
        }
        need(files.containsKey(script),"Runtime closure omits the required Rscript executable");return new Runtime(script,library,rHome,Map.copyOf(files),raw);
    }
    private static Map<?,?> profile(byte[] raw)throws IOException {
        Map<?,?> p=object(NextflowParameters.parse(utf8(raw),16_777_216,200_000));
        keys(p,"schema_version","tool","version","platform","r_version","rscript","r_home","private_library","files","windows_system_DLLs_independently_pinned");
        integer(p.get("schema_version"),1,1);need(p.get("tool").equals("SNPRelate")&&p.get("version").equals("1.46.0")&&p.get("r_version").equals("4.6.1")&&p.get("platform").equals("windows"),"Require exact original private SNPRelate1.46/R4.6.1 Windows profile");
        need(Boolean.FALSE.equals(p.get("windows_system_DLLs_independently_pinned")),"Unsupported external Windows DLL closure claim");
        need(!object(p.get("files")).isEmpty()&&object(p.get("files")).size()<=20000,"Runtime closure file capacity exceeded");return p;
    }
    private static void runtimePins(Runtime runtime,Runnable check)throws IOException {
        var found=new HashMap<Path,Pin>();long total=0;
        for(Path root:List.of(runtime.rHome(),runtime.library())) {
            if(!Files.isDirectory(root))throw new Unavailable("Missing exact R distribution/private library: "+root);
            try(var paths=Files.walk(root)) {
                var iterator=paths.iterator();while(iterator.hasNext()) {
                    check.run();Path file=iterator.next();need(!Files.isSymbolicLink(file),"Linked runtime closure member is unsupported");
                    if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) {
                        if(!runtime.files().containsKey(file))throw new IOException("Unexpected runtime closure member: "+file);
                        Pin value=pin(file,runtime.files().get(file).bytes(),check);found.put(file,value);total=Math.addExact(total,value.bytes());need(total<=2_000_000_000,"Runtime closure byte capacity exceeded");
                    }
                }
            }
        }
        if(!found.keySet().equals(runtime.files().keySet()))throw new Unavailable("Required exact R/private package closure member is missing; regenerate a permitted exact profile");
        need(found.equals(runtime.files()),"Exact private runtime bytes changed; do not substitute versions or suppress integrity failures");
    }
    private static void copy(Path source,Path target,Pin expected,Runnable check)throws IOException {
        try(var in=Files.newInputStream(source);var output=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)) {
            long size=0;byte[] buffer=new byte[65536];for(int n;(n=in.read(buffer))!=-1;){check.run();size=Math.addExact(size,n);need(size<=expected.bytes(),"Original input grew during capture");output.write(buffer,0,n);}
        }
        need(expected.equals(pin(source,expected.bytes(),check))&&expected.equals(pin(target,expected.bytes(),check)),"Original input changed during complete capture");
    }
    private static void capacity(Path root,SnprelateRequest.Request r)throws IOException {
        capacity(root,r,0,0);
    }
    private static void capacity(Path root,SnprelateRequest.Request r,long extraBytes,int extraFiles)throws IOException {
        long bytes=extraBytes;int files=extraFiles;try(var paths=Files.walk(root)) {
            var iterator=paths.iterator();while(iterator.hasNext()) {
                Path p=iterator.next();need(!Files.isSymbolicLink(p),"Linked attempt output is unsupported");
                if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)){bytes=Math.addExact(bytes,Files.size(p));need(++files<=r.limits().outputFiles()&&bytes<=r.limits().outputBytes(),"Complete attempt output byte/file capacity exceeded");}
            }
        }
    }
    private static Map<String,Object> execute(Path root,List<String> command,Runtime runtime,SnprelateRequest.Request r,Runnable check,Map<String,Object> state,boolean[] live)throws Exception {
        var builder=new ProcessBuilder(command).directory(root.toFile()).redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(root.resolve("worker/analysis.stdout").toFile()).redirectError(root.resolve("worker/analysis.stderr").toFile());
        var environment=builder.environment();environment.putAll(Map.of("LC_ALL","C","LANG","C","R_DEFAULT_PACKAGES","base","R_LIBS_USER",runtime.library().toString(),
            "R_LIBS_SITE","","R_HOME",runtime.rHome().toString(),"TMPDIR",root.resolve("tmp").toString(),"OMP_NUM_THREADS","1","OPENBLAS_NUM_THREADS","1","MKL_NUM_THREADS","1"));
        long start=System.nanoTime();Process process;
        try{process=builder.start();}catch(IOException launch){throw new Unavailable("Required original R runtime could not be launched; skip dependent analysis: "+launch);}
        live[0]=true;state.put("original_analysis_executed",true);process.getOutputStream().close();var descendants=new LinkedHashMap<Long,ProcessHandle>();
        var record=map("command",command,"timeout",false);
        try {
            while(!process.waitFor(100,TimeUnit.MILLISECONDS)){process.descendants().forEach(h->descendants.put(h.pid(),h));check.run();capacity(root,r);}
            check.run();capacity(root,r);return record;
        } catch(Exception failure) {
            record.put("cause",failure.toString());record.put("timeout",failure instanceof java.util.concurrent.CancellationException);throw failure;
        } finally {
            process.descendants().forEach(h->descendants.put(h.pid(),h));for(var child:descendants.values())if(child.isAlive())child.destroyForcibly();
            if(process.isAlive())process.destroyForcibly();need(process.waitFor(10,TimeUnit.SECONDS),"Original R process did not terminate; attempt remains unsealed");
            for(var child:descendants.values())if(child.isAlive())child.onExit().get(10,TimeUnit.SECONDS);
            live[0]=false;
            record.put("exit_code",process.exitValue());record.put("elapsed_milliseconds",(System.nanoTime()-start)/1_000_000);
            writeJson(root.resolve("worker/analysis.process.json"),record);
        }
    }
    private static Map<String,Object> inventory(Path root)throws IOException {
        var result=new TreeMap<String,Object>();try(var paths=Files.walk(root)) {
            var iterator=paths.iterator();while(iterator.hasNext()) {
                Path p=iterator.next();need(!Files.isSymbolicLink(p),"Linked bundle member unsupported");
                if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)) {Pin value=pin(p,Long.MAX_VALUE,()->{});result.put(root.relativize(p).toString().replace('\\','/'),map("bytes",value.bytes(),"sha256",value.sha256()));}
            }
        }return result;
    }
    private static void writeJson(Path path,Object value)throws IOException {Files.writeString(path,NextflowJson.json(value)+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
    private static void verify(Path root)throws Exception {
        root=root.toRealPath();Map<?,?> done=object(json(root.resolve("COMPLETED.json"),16_777_216));
        need(Boolean.TRUE.equals(done.get("result_admitted"))&&Boolean.TRUE.equals(done.get("original_analysis_executed"))&&text(done.get("status")).equals("completed-original-SNPRelate-with-explicit-warnings"),"Saved attempt admits no original SNPRelate result");
        var expected=object(done.get("files"));var actual=inventory(root);actual.remove("COMPLETED.json");
        need(NextflowJson.json(actual).equals(NextflowJson.json(expected)),"Complete saved bundle byte inventory differs");
        byte[] raw=bytes(root.resolve("source-request.json"),1_048_576);var r=SnprelateRequest.parse(NextflowParameters.parse(utf8(raw),1_048_576,100_000),root);
        need(Files.readString(root.resolve("parameters.tsv"),StandardCharsets.UTF_8).equals(parameterText(r)),"Saved typed parameter file differs from the explicit request");
        capacity(root,r);byte[] savedWorker=bytes(root.resolve("worker.R"),1_048_576),currentWorker=resource("run_snprelate.R");
        if(!java.util.Arrays.equals(savedWorker,currentWorker)) {
            byte[] previous=resource("run_snprelate_146_three_modes.R");
            need(r.pca()==null&&sha(previous).equals("8c0c02a632d4b9c3484167ff381e116910098be69321717e7a375e7c0040acb9")&&java.util.Arrays.equals(savedWorker,previous),"Saved execution worker is neither the current source nor the exact validated preceding three-workflow producer");
        }
        Map<?,?> runtime=profile(bytes(root.resolve("runtime-profile.json"),16_777_216));var execution=object(json(root.resolve("execution.json"),1_048_576));
        need(text(execution.get("platform")).equals("windows"),"Unexpected saved original execution platform");String original=text(execution.get("bundle_at_execution"));
        var process=object(execution.get("process"));need(integer(process.get("exit_code"),0,0)==0&&Boolean.FALSE.equals(process.get("timeout")),"Original source process did not complete successfully");
        need(NextflowJson.json(process).equals(NextflowJson.json(json(root.resolve("worker/analysis.process.json"),1_048_576))),"Original source process records disagree");
        need(Files.readString(root.resolve("worker/analysis.stdout"),StandardCharsets.UTF_8).contains("ORIGINAL_SNPRELATE_EXPLICIT_WORKFLOW_COMPLETE"),"Saved original source completion marker missing");
        List<String> command=List.of(text(runtime.get("rscript")),"--vanilla",original+"\\worker.R",text(runtime.get("private_library")),
            original+"\\inputs\\cohort",original+"\\worker",original+"\\parameters.tsv");
        need(strings(process.get("command")).equals(command),"Saved original command differs from typed execution");
        var data=SnprelateAudit.inputs(root,r,()->{});var audit=SnprelateAudit.outputs(root,root.resolve("worker"),r,data,()->{});
        need(NextflowJson.json(audit).equals(NextflowJson.json(json(root.resolve("scientific-audit.json"),1_048_576)))&&NextflowJson.json(audit).equals(NextflowJson.json(done.get("scientific_audit"))),"Saved scientific audit differs from complete native replay");
        need(Boolean.valueOf(audit.get("downstream_eligible").toString()).equals(done.get("downstream_eligible")),"Saved downstream eligibility differs");
    }
}
