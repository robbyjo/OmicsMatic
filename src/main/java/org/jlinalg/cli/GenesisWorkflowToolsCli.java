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

/** Supervised original GENESIS2.42.0 workflows with explicit typed inputs and native admission checks. */
public final class GenesisWorkflowToolsCli {
    private GenesisWorkflowToolsCli() { }
    static final String SCOPE="Explicit original GENESIS2.42.0 on a pinned private Windows R4.6.1/longdouble64 runtime: PC-AiR, supplied-PC PC-Relate, Gaussian/binomial/Poisson null models, Gaussian two-stage rank normalisation and single-variant Score/Score.SPA/BinomiRare/CMP dispatch or one-covariate original GxE Wald testing. Native Java checks complete hard calls and FID/IID transport, ordered matrix partitions, unrelated-population PCA QC/orthogonality, scaled covariance connected components, phenotype/covariance order, model design/convergence/effective engine, original association sample selection, rank/normal-quantile/rescaling fields and association algebra. BinomiRare includes a native Poisson-binomial PMF comparison and preserves the original FFT density-ordering tail rule. Additional original first-stage and Score controls are explicitly recorded; no upstream algorithm or output is repaired. Fitting, PCA/projection, kinship estimation and saddlepoint calculations execute upstream. Saved verification repeats the stated native contracts without R; it does not authenticate software, establish biological validity, independently fit model estimators or certify every source field. Finite complete autosomal biallelic hard calls and explicit model inputs only; no silent phenotype drops, ID repair or genotype imputation in original inputs. Original malformed/unassessable results and source-method errors admit no result. Effective GLM/test fallbacks are prominently disclosed. Logical capacities are not OS quotas; external Windows system DLLs are not independently pinned. No whole-package, historical-paper or whole-goal completion claim.";
    static final String ACTION="Require exit0, result_admitted=true and successful complete --verify before using a bundle. Read WARNINGS.md, scientific-audit.json, native association variant dispositions when present, and original stdout/stderr/messages. SKIP3: SKIP dependent original GENESIS analysis; supply the exact permitted private runtime using the exported helper. If installation/downloads are unavailable or forbidden, leave that analysis skipped. FAIL2: NO RESULT ADMITTED, including partial source output. Preserve the full attempt, correct the named input/process/capacity/scientific cause and retry NEW paths. Never repair IDs, calls, source NaN/NA/Inf or partition dimensions, silently reorder covariance inputs, suppress source fallback or substitute software/versions/settings to hide a failure. Historical paper versions and settings are not inferred or reproduced by this profile.";
    private record Runtime(Path script,Path library,Path rHome,Map<Path,Pin> files,byte[] raw) { }
    private static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String cause){super(cause);}
    }
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    public static int run(String[] args,PrintStream output,PrintStream error) {
        Path out=null;int code=2;Runtime runtime=null;GenesisRequest.Request request=null;boolean runtimeValidated=false;boolean[] processLive={false};
        var protectedInputs=new LinkedHashMap<Path,Pin>();var warnings=new ArrayList<String>();warnings.add(SCOPE);
        var state=map("status","FAIL2_NO_GENESIS_RESULT_ADMITTED","result_admitted",false,"original_analysis_executed",false);
        try {
            if(args.length==1&&args[0].equals("--help")) {output.println("genesis-tools --request JSON | --verify BUNDLE | --export-runtime-helper NEW_DIRECTORY\n"+SCOPE+"\n"+ACTION);return 0;}
            if(args.length==2&&args[0].equals("--export-runtime-helper")) {
                Path target=Path.of(args[1]).toAbsolutePath().normalize();need(!Files.exists(target,LinkOption.NOFOLLOW_LINKS),"Runtime helper target exists; choose NEW paths");
                Files.createDirectories(target.getParent());Files.createDirectory(target);Files.write(target.resolve("prepare_genesis_runtime.py"),resource("prepare_genesis_runtime.py"),StandardOpenOption.CREATE_NEW);
                Files.writeString(target.resolve("WARNINGS.md"),SCOPE+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                output.println("Exported runtime-profile helper only; no download, installation or analysis.\n"+ACTION);return 0;
            }
            if(args.length==2&&args[0].equals("--verify")) {verify(Path.of(args[1]).toAbsolutePath().normalize());output.println("PASS complete saved GENESIS bundle and stated native scientific contracts; no upstream analysis, independent model refit or producer authentication.");return 0;}
            need(args.length==2&&args[0].equals("--request"),"Require --request JSON, --verify BUNDLE or --help");
            Path source=Path.of(args[1]).toAbsolutePath().normalize().toRealPath();byte[] raw=bytes(source,1_048_576);
            request=GenesisRequest.parse(NextflowParameters.parse(utf8(raw),1_048_576,100_000),source.getParent());
            Path target=resolved(request.out());need(!Files.exists(target,LinkOption.NOFOLLOW_LINKS),"Output exists; preserve previous attempts and choose NEW paths");
            separate(target,source);separate(target,request.profile());for(Path path:GenesisInputs.sources(request).values())separate(target,path);
            Files.createDirectories(target.getParent());out=Files.createDirectory(target);
            Files.write(out.resolve("source-request.json"),raw,StandardOpenOption.CREATE_NEW);
            Files.writeString(out.resolve("WARNINGS.initial.md"),"# PROVISIONAL: NO GENESIS RESULT ADMITTED\n\n"+SCOPE+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            long start=System.nanoTime(),maximumNanos=Duration.ofSeconds(request.limits().seconds()).toNanos();
            Runnable check=()->{if(Thread.currentThread().isInterrupted()||System.nanoTime()-start>maximumNanos)throw new java.util.concurrent.CancellationException("Complete GENESIS wall-time/interruption capacity exceeded");};
            protectedInputs.put(source,pin(source,1_048_576,check));need(java.util.Arrays.equals(raw,bytes(source,1_048_576)),"Original request changed during capture");
            runtime=runtime(request.profile());protectedInputs.put(request.profile().toRealPath(),pin(request.profile().toRealPath(),16_777_216,check));
            for(Path file:runtime.files().keySet())separate(out,file);runtimePins(runtime,check);runtimeValidated=true;
            optionalPrerequisites(runtime,request);
            Files.write(out.resolve("runtime-profile.json"),runtime.raw(),StandardOpenOption.CREATE_NEW);
            Files.createDirectory(out.resolve("inputs"));long inputBytes=0;
            for(var input:GenesisInputs.sources(request).entrySet()) {
                Path original=input.getValue().toRealPath();Pin before=pin(original,request.limits().inputBytes(),check);
                inputBytes=Math.addExact(inputBytes,before.bytes());need(inputBytes<=request.limits().inputBytes(),"Complete original GENESIS input byte capacity exceeded");
                Path captured=out.resolve("inputs").resolve(input.getKey());copy(original,captured,before,check);protectedInputs.put(original,before);protectedInputs.put(captured,before);
            }
            var data=GenesisInputs.read(out,request,check);GenesisInputs.prepare(out,request,data,check);
            Path parameters=out.resolve("parameters.tsv");
            Files.writeString(parameters,parameterText(request),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);Files.write(out.resolve("worker.R"),resource("run_genesis_workflows.R"),StandardOpenOption.CREATE_NEW);
            Files.createDirectory(out.resolve("worker"));Files.createDirectory(out.resolve("tmp"));
            List<String> command=List.of(runtime.script().toString(),"--vanilla",out.resolve("worker.R").toString(),runtime.library().toString(),
                out.toString(),out.resolve("worker").toString(),parameters.toString());
            var process=execute(out,command,runtime,request,check,state,processLive);
            writeJson(out.resolve("execution.json"),map("platform","windows","bundle_at_execution",out.toString(),"process",process));
            if(((Number)process.get("exit_code")).intValue()!=0) {
                String details="Inspect retained analysis.stdout, analysis.stderr and original-error.txt";
                for(String name:List.of("original-error.txt","original-close-error.txt"))if(Files.isRegularFile(out.resolve("worker").resolve(name)))details+="\n"+utf8(bytes(out.resolve("worker").resolve(name),1_048_576));
                throw new IOException("Original GENESIS failed; NO RESULT ADMITTED. "+details);
            }
            for(var e:protectedInputs.entrySet())need(e.getValue().equals(pin(e.getKey(),e.getValue().bytes(),check)),"Original/captured input changed during GENESIS execution");
            var audit=GenesisWorkflowAudit.outputs(out,request,data,check,true);writeJson(out.resolve("scientific-audit.json"),audit);
            need(Files.readString(out.resolve("worker/analysis.stdout"),StandardCharsets.UTF_8).contains("ORIGINAL_GENESIS_EXPLICIT_WORKFLOW_COMPLETE"),"Incomplete original GENESIS completion marker");
            if(Boolean.TRUE.equals(audit.get("original_GLM_fallback")))warnings.add("WARNING: GENESIS estimated all variance components as zero and used ordinary GLM. effective_mixed_model=false. Do not report a fitted positive kinship variance or mixed-model association for this attempt; inspect original messages and scientific-audit.json.");
            if(Boolean.TRUE.equals(audit.get("source_CMP_to_BinomiRare_fallback")))warnings.add("WARNING: the requested CMP test executed GENESIS's BinomiRare path because the effective null model is ordinary regression. effective_association_test=BinomiRare. Do not label its P values as an executed CMP approximation; the original dispatch and output are retained.");
            if(Boolean.TRUE.equals(audit.get("source_SPA_to_Score_fallback")))warnings.add("WARNING: GENESIS used ordinary Score for the requested Score.SPA test because the requested outcome family is not binomial. effective_association_test=Score. Do not describe these P values as saddlepoint approximations.");
            if(audit.get("source_PMF_density_ordering_roundoff_disagreements") instanceof Number count&&count.longValue()>0)warnings.add("WARNING: original FFT and native Poisson-binomial PMFs differ in density ordering within the recorded floating-point tolerance. The original FFT values and strict smaller-density tail rule were retained. Read the discrepancy count; this result is not an independently substituted native P value.");
            if(audit.get("negative_source_FFT_PMF_values_retained") instanceof Number count&&count.longValue()>0)warnings.add("WARNING: the original FFT PMF has "+count+" small negative entries within the recorded2e-12 absolute comparison tolerance. Those auxiliary values remain unchanged. Read original-poibin-control-pmfs.tsv and the scientific audit; no clipping or native P-value substitution was performed.");
            if(audit.get("SPA_not_converged_retaining_original_Score_P") instanceof Number count&&count.longValue()>0)warnings.add("WARNING: original SPA did not converge for "+count+" variants and retained their original Score P values. Read the per-variant original SPA.converged flags and scientific audit; do not label those variants as converged SPA calculations.");
            String sourceWarnings=Files.readString(out.resolve("worker/original-warnings.txt"),StandardCharsets.UTF_8);if(!sourceWarnings.isBlank())warnings.add("ORIGINAL WARNINGS:\n"+sourceWarnings);
            String sourceMessages=Files.readString(out.resolve("worker/original-messages.txt"),StandardCharsets.UTF_8);if(!sourceMessages.isBlank())warnings.add("ORIGINAL MESSAGES:\n"+sourceMessages);
            state.putAll(map("status","completed-original-GENESIS-with-explicit-warnings","result_admitted",true,"downstream_eligible",audit.get("downstream_eligible"),"scientific_audit",audit));code=0;
        } catch(Unavailable unavailable) {
            state.putAll(map("status","SKIP3_EXACT_GENESIS_RUNTIME_UNAVAILABLE","cause",unavailable.getMessage()));warnings.add("SKIP3: "+unavailable.getMessage());code=3;
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
                    if(code==2)state.put("status","FAIL2_NO_GENESIS_RESULT_ADMITTED");
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
                code=2;state.putAll(map("status","FAIL2_NO_GENESIS_RESULT_ADMITTED","result_admitted",false,"downstream_eligible",false));
                warnings.add("FAIL2: attempt could not be sealed; admit no result: "+failure);error.println(warnings.get(warnings.size()-1));
                try{Files.writeString(out.resolve("UNSEALED-FAIL2.txt"),String.join("\n",warnings)+"\n\nREQUIRED ACTION: "+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}catch(IOException ignored){/* Existing/raw outputs remain preserved; stderr and exit2 are authoritative. */}
            }
        }
        (code==0?output:error).println(state.get("status")+"\n"+String.join("\n",warnings)+"\nREQUIRED ACTION: "+ACTION+(out==null?"":"\nEvidence: "+out));return code;
    }
    private static byte[] resource(String name)throws IOException {
        try(var stream=GenesisWorkflowToolsCli.class.getResourceAsStream("/org/jlinalg/omics/genesis/"+name)){need(stream!=null,"Missing packaged GENESIS resource: "+name);return stream.readAllBytes();}
    }
    private static String parameterText(GenesisRequest.Request request) {
        var text=new StringBuilder("key\tvalue\n");for(var e:GenesisRequest.parameters(request).entrySet())text.append(e.getKey()).append('\t').append(e.getValue()).append('\n');return text.toString();
    }
    private static Path resolved(Path path)throws IOException {
        Path parent=path.getParent();need(parent!=null,"Output requires a parent directory");var missing=new ArrayList<Path>();
        while(!Files.exists(parent,LinkOption.NOFOLLOW_LINKS)){missing.add(parent.getFileName());parent=parent.getParent();need(parent!=null,"No existing output ancestor");}
        parent=parent.toRealPath();for(int i=missing.size()-1;i>=0;i--)parent=parent.resolve(missing.get(i));return parent.resolve(path.getFileName());
    }
    private static void separate(Path out,Path input)throws IOException {Path p=Files.exists(input,LinkOption.NOFOLLOW_LINKS)?input.toRealPath():resolved(input);need(!p.startsWith(out)&&!out.startsWith(p),"GENESIS output overlaps an input, request or runtime");}
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
        keys(p,"schema_version","tool","version","platform","r_version","r_longdouble_significand_bits","rscript","r_home","private_library","files","windows_system_DLLs_independently_pinned");
        integer(p.get("schema_version"),1,1);integer(p.get("r_longdouble_significand_bits"),64,64);need(p.get("tool").equals("GENESIS")&&p.get("version").equals("2.42.0")&&p.get("r_version").equals("4.6.1")&&p.get("platform").equals("windows"),"Require exact original private GENESIS2.42/R4.6.1 Windows profile");
        need(Boolean.FALSE.equals(p.get("windows_system_DLLs_independently_pinned")),"Unsupported external Windows DLL closure claim");
        need(!object(p.get("files")).isEmpty()&&object(p.get("files")).size()<=40000,"Runtime closure file capacity exceeded");return p;
    }
    private static void optionalPrerequisites(Runtime runtime,GenesisRequest.Request request)throws IOException {
        if(!request.mode().equals("association")||request.model().interaction()!=null)return;
        var required=new LinkedHashMap<String,String>();String test=request.model().test();
        if(test.equals("Score.SPA")&&request.model().family().equals("binomial"))required.put("SPAtest","3.1.2");
        if(test.equals("BinomiRare"))required.put("poibin","1.6");
        if(test.equals("CMP")) {required.put("poibin","1.6");if(!request.inputs().covariance().isEmpty())required.put("COMPoissonReg","0.8.2");}
        for(var packageVersion:required.entrySet()) {
            Path description=runtime.library().resolve(packageVersion.getKey()).resolve("DESCRIPTION");
            if(!runtime.files().containsKey(description)||!Files.isRegularFile(description))throw new Unavailable("The selected original test requires private "+packageVersion.getKey()+packageVersion.getValue()+". Skip this dependent association. Where permitted, prepare a NEW library/profile with tools/literature/extend_genesis_runtime.py --allow-download --allow-install; otherwise leave it skipped.");
            String version=null;for(String line:Files.readAllLines(description,StandardCharsets.UTF_8))if(line.startsWith("Version:")){need(version==null,"Duplicate optional package version field");version=line.substring(8).strip();}
            if(!packageVersion.getValue().equals(version))throw new Unavailable("Selected original test requires the validated private "+packageVersion.getKey()+packageVersion.getValue()+"; supplied version is "+version+". Skip that test rather than silently substituting another version.");
        }
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
    private static void capacity(Path root,GenesisRequest.Request r)throws IOException {
        capacity(root,r,0,0);
    }
    private static void capacity(Path root,GenesisRequest.Request r,long extraBytes,int extraFiles)throws IOException {
        long bytes=extraBytes;int files=extraFiles;try(var paths=Files.walk(root)) {
            var iterator=paths.iterator();while(iterator.hasNext()) {
                Path p=iterator.next();need(!Files.isSymbolicLink(p),"Linked attempt output is unsupported");
                if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)){bytes=Math.addExact(bytes,Files.size(p));need(++files<=r.limits().outputFiles()&&bytes<=r.limits().outputBytes(),"Complete attempt output byte/file capacity exceeded");}
            }
        }
    }
    private static Map<String,Object> execute(Path root,List<String> command,Runtime runtime,GenesisRequest.Request r,Runnable check,Map<String,Object> state,boolean[] live)throws Exception {
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
        need(Boolean.TRUE.equals(done.get("result_admitted"))&&Boolean.TRUE.equals(done.get("original_analysis_executed"))&&text(done.get("status")).equals("completed-original-GENESIS-with-explicit-warnings"),"Saved attempt admits no original GENESIS result");
        var expected=object(done.get("files"));var actual=inventory(root);actual.remove("COMPLETED.json");
        need(NextflowJson.json(actual).equals(NextflowJson.json(expected)),"Complete saved bundle byte inventory differs");
        byte[] raw=bytes(root.resolve("source-request.json"),1_048_576);var r=GenesisRequest.parse(NextflowParameters.parse(utf8(raw),1_048_576,100_000),root);
        need(Files.readString(root.resolve("parameters.tsv"),StandardCharsets.UTF_8).equals(parameterText(r)),"Saved typed parameter file differs from the explicit request");
        capacity(root,r);byte[] savedWorker=bytes(root.resolve("worker.R"),1_048_576),currentWorker=resource("run_genesis_workflows.R");
        need(java.util.Arrays.equals(savedWorker,currentWorker),"Saved GENESIS execution worker differs from this validated producer; preserve the matching development JAR");
        Map<?,?> runtime=profile(bytes(root.resolve("runtime-profile.json"),16_777_216));var execution=object(json(root.resolve("execution.json"),1_048_576));
        need(text(execution.get("platform")).equals("windows"),"Unexpected saved original execution platform");String original=text(execution.get("bundle_at_execution"));
        var process=object(execution.get("process"));need(integer(process.get("exit_code"),0,0)==0&&Boolean.FALSE.equals(process.get("timeout")),"Original source process did not complete successfully");
        need(NextflowJson.json(process).equals(NextflowJson.json(json(root.resolve("worker/analysis.process.json"),1_048_576))),"Original source process records disagree");
        need(Files.readString(root.resolve("worker/analysis.stdout"),StandardCharsets.UTF_8).contains("ORIGINAL_GENESIS_EXPLICIT_WORKFLOW_COMPLETE"),"Saved original source completion marker missing");
        List<String> command=List.of(text(runtime.get("rscript")),"--vanilla",original+"\\worker.R",text(runtime.get("private_library")),
            original,original+"\\worker",original+"\\parameters.tsv");
        need(strings(process.get("command")).equals(command),"Saved original command differs from typed execution");
        var data=GenesisInputs.read(root,r,()->{});var audit=GenesisWorkflowAudit.outputs(root,r,data,()->{},false);
        need(NextflowJson.json(audit).equals(NextflowJson.json(json(root.resolve("scientific-audit.json"),1_048_576)))&&NextflowJson.json(audit).equals(NextflowJson.json(done.get("scientific_audit"))),"Saved scientific audit differs from complete native replay");
        need(Boolean.valueOf(audit.get("downstream_eligible").toString()).equals(done.get("downstream_eligible")),"Saved downstream eligibility differs");
        var savedState=new LinkedHashMap<String,Object>();for(var e:done.entrySet())if(!e.getKey().equals("files"))savedState.put(text(e.getKey()),e.getValue());
        need(NextflowJson.json(savedState).equals(NextflowJson.json(json(root.resolve("analysis-status.json"),16_777_216))),"Saved completion and analysis-status records disagree");
        need(text(done.get("required_action")).equals(ACTION)&&text(done.get("scope")).equals(SCOPE),"Saved scope/required actions differ from the matching development command");
        String warningText=Files.readString(root.resolve("WARNINGS.md"),StandardCharsets.UTF_8);
        need(warningText.contains("REQUIRED ACTION: "+ACTION)&&warningText.contains(SCOPE),"Saved prominent warnings/required actions are missing");
        for(String field:List.of("warnings","messages")) {
            String originalLog=Files.readString(root.resolve("worker/original-"+field+".txt"),StandardCharsets.UTF_8);
            need(originalLog.isBlank()||warningText.contains(originalLog),"Original "+field+" were omitted from prominent warnings");
        }
        if(Boolean.TRUE.equals(audit.get("original_GLM_fallback")))need(warningText.contains("effective_mixed_model=false"),"Original GLM fallback is missing from prominent warnings");
    }
}
