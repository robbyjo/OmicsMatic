/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;
import static org.jlinalg.cli.GctaWorkflowFiles.number;

/** Explicit source-binary execution or native GRM. Never selects a fallback engine. */
final class GctaWorkflowRuntime {
    static final String SOURCE="6da64e9d8be838d01c34efb974e69c72e861f4c2";
    static final String WINDOWS="2529bccb2f685332f115726c3cb2bf8cab91f12ae3344a991e3a52d751bdb07e";
    static final String LINUX="dbb2b5e5d51e278328ff297a403d3dd8ee5f5280f0b6d3d8db74b3e7fead7b49";
    static final String LIBOMP="ee9ce7570217bbccf87d569eb06e71fe9ec246899d2b2fe4fd918215e29e1c79";
    private GctaWorkflowRuntime() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
    }

    static void run(Path root,Path requestDirectory,Map<String,Object> r,GctaWorkflowFiles.Prepared prepared,Map<String,Object> manifest) throws Exception {
        if(!GctaWorkflowContracts.PROFILE.equals(r.get("profile")))
            throw new Unavailable("The exact declared edition "+r.get("profile")+" is not qualified. Supply its permitted original source/runtime and qualify it; do not substitute 1.94.1 or a beta/adjacent release");
        if(text(r,"engine").equals("native")){nativeRun(root,r,prepared,manifest);return;}
        Path runtime=resolve(requestDirectory,text(r,"runtime"));
        boolean windows=System.getProperty("os.name").startsWith("Windows"),linux=System.getProperty("os.name").equals("Linux");
        if(!windows&&!linux)throw new Unavailable("Only exact Windows/Linux x86_64 GCTA1.94.1 binaries have been qualified");
        if(!Files.isRegularFile(runtime,LinkOption.NOFOLLOW_LINKS)||!Files.isReadable(runtime)||!Files.isExecutable(runtime))
            throw new Unavailable("Supply a readable executable original GCTA1.94.1 runtime at "+runtime+"; install/download or set execution permission only where permitted");
        String wanted=windows?WINDOWS:LINUX;
        if(!FollowupSupport.hash(runtime).equals(wanted))throw new Unavailable("The selected file is not the exact qualified "+(windows?"Windows":"Linux")+" runtime. Supply SHA256 "+wanted+"; no edition substitution");
        var runtimePins=new LinkedHashMap<String,Object>();runtimePins.put(runtime.toString(),wanted);
        if(windows) {
            Path dll=runtime.getParent().resolve("libomp.dll");
            if(!Files.isRegularFile(dll,LinkOption.NOFOLLOW_LINKS)||!FollowupSupport.hash(dll).equals(LIBOMP))
                throw new Unavailable("Supply the exact adjacent libomp.dll SHA256 "+LIBOMP+" from the original Windows release");
            runtimePins.put(dll.toString(),LIBOMP);
        }
        manifest.put("runtime_flavor",windows?"GCTA1.94.1-Windows-x86_64":"GCTA1.94.1-Linux-x86_64");
        manifest.put("runtime_sha256",runtimePins);manifest.put("upstream_source_commit",SOURCE);
        manifest.put("runtime_admission_scope","Exact EXE/ELF and Windows adjacent libomp identities, not authentication of all OS libraries. Source stochastic calibration may differ across platforms at the same seed.");
        json(root.resolve("worker/runtime-bindings.json"),Map.of("runtime",runtime.toString(),"runtime_sha256",runtimePins));
        String model=text(r,"model");var steps=new ArrayList<Map<String,Object>>();long started=System.nanoTime();
        manifest.put("original_steps",steps);
        if(model.equals("FASTGWA_BINARY")) {
            Files.createDirectory(root.resolve("worker/null-model"));Files.createDirectory(root.resolve("worker/association"));
            runStep(root,r,runtime,command(r,false),"null-model",started,steps);
            Path prefix=root.resolve("worker/null-model/result.fastGWA");
            for(String suffix:List.of(".mdl.id",".mdl.bin2"))if(!Files.isRegularFile(Path.of(prefix+suffix),LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Original binary null model did not export its complete model files: "+suffix);
            runStep(root,r,runtime,command(r,true),"association",started,steps);
        } else runStep(root,r,runtime,command(r,false),"analysis",started,steps);
        for(var entry:runtimePins.entrySet())if(!FollowupSupport.hash(Path.of(entry.getKey())).equals(entry.getValue()))
            throw new IOException("Runtime identity changed during original execution");
        manifest.put("original_steps",steps);
    }

    static List<String> command(Map<String,Object> r,boolean loadBinary) throws IOException {
        String model=text(r,"model");var args=new ArrayList<String>();
        if(r.containsKey("genotypes"))Collections.addAll(args,"--bfile","../inputs/genotypes","--autosome-num",Long.toString(number(r,"autosome_number")));
        if(loadBinary) {
            Collections.addAll(args,"--load-model","null-model/result.fastGWA","--thread-num","1","--out","association/result");return args;
        }
        switch(model) {
            case "GRM" -> Collections.addAll(args,"--make-grm","--make-grm-alg",Long.toString(number(r,"grm_algorithm")));
            case "PCA" -> Collections.addAll(args,"--grm","../inputs/grm0","--pca",Long.toString(number(r,"pca_components")));
            case "COJO_SELECT","COJO_JOINT","COJO_CONDITION" -> {
                Collections.addAll(args,"--cojo-file","../inputs/summary","--cojo-wind",Long.toString(number(r,"cojo_window_kb")),"--cojo-collinear",r.get("cojo_collinear").toString(),"--diff-freq",r.get("diff_frequency").toString());
                if(model.equals("COJO_SELECT"))Collections.addAll(args,"--cojo-slct","--cojo-p",r.get("cojo_p").toString());
                else if(model.equals("COJO_JOINT"))args.add("--cojo-joint");
                else Collections.addAll(args,"--cojo-cond","../inputs/condition");
            }
            case "REML" -> {
                if(list(r.get("grms"),"grms").size()==1)Collections.addAll(args,"--grm","../inputs/grm0");
                else Collections.addAll(args,"--mgrm","grms.list");
                Collections.addAll(args,"--reml","--reml-maxit",Long.toString(number(r,"reml_max_iterations")),"--reml-est-fix","--reml-pred-rand");
                if(Boolean.TRUE.equals(r.get("reml_unconstrained")))args.add("--reml-no-constrain");
            }
            case "LD_SCORE","LD_SCORE_REGION" -> {
                if(model.equals("LD_SCORE"))args.add("--ld-score");else Collections.addAll(args,"--ld-score-region",Long.toString(number(r,"ld_region_kb")));
                Collections.addAll(args,"--ld-wind",r.get("ld_window_kb").toString(),"--ld-rsq-cutoff",r.get("ld_rsq_cutoff").toString());
                if(Boolean.TRUE.equals(r.get("ld_adjusted")))args.add("--ld-score-adj");
            }
            case "SPARSE_GRM" -> Collections.addAll(args,"--grm","../inputs/grm0","--make-bK-sparse",r.get("sparse_cutoff").toString());
            case "FASTGWA_BINARY" -> Collections.addAll(args,"--grm-sparse","../inputs/sparse","--fastGWA-mlm-binary","--model-only","--seed",Long.toString(number(r,"seed")),"--num-vec",Long.toString(number(r,"random_vectors")));
            default -> throw new IOException("Unsupported GCTA model");
        }
        for(String key:List.of("phenotype","qcovar","covar","extract"))if(r.containsKey(key))
            Collections.addAll(args,switch(key){case "phenotype"->"--pheno";case "qcovar"->"--qcovar";case "covar"->"--covar";default->"--extract";},"../inputs/"+key);
        Collections.addAll(args,"--thread-num","1","--out",model.equals("FASTGWA_BINARY")?"null-model/result":"result");return args;
    }

    private static void runStep(Path root,Map<String,Object> r,Path runtime,List<String> arguments,String step,long started,List<Map<String,Object>> records) throws Exception {
        Path worker=root.resolve("worker");var command=new ArrayList<String>();command.add(runtime.toString());command.addAll(arguments);
        var record=new LinkedHashMap<String,Object>();record.put("step",step);record.put("command",command);record.put("started_at",Instant.now().toString());records.add(record);
        json(worker.resolve(step+"-command.json"),record);
        var builder=new ProcessBuilder(command).directory(worker.toFile()).redirectOutput(worker.resolve(step+".stdout").toFile()).redirectError(worker.resolve(step+".stderr").toFile());
        builder.environment().keySet().removeIf(k->k.startsWith("LC_")||Set.of("LD_PRELOAD","LD_LIBRARY_PATH","DYLD_INSERT_LIBRARIES","DYLD_LIBRARY_PATH","LANG","LANGUAGE").contains(k));
        builder.environment().putAll(Map.of("LANG","C","LC_ALL","C","OMP_NUM_THREADS","1","MKL_NUM_THREADS","1","OPENBLAS_NUM_THREADS","1"));
        AtomicReference<Process> owned=new AtomicReference<>();Thread hook=new Thread(()->{Process p=owned.get();if(p!=null)stop(p,new ArrayList<>());},"gcta-owned-worker-cleanup");
        Runtime.getRuntime().addShutdownHook(hook);Process process=null;var descendants=new ArrayList<ProcessHandle>();
        try {
            try{process=builder.start();}catch(IOException error){throw new Unavailable("Exact GCTA runtime could not launch: "+error);}
            owned.set(process);record.put("pid",process.pid());json(worker.resolve(step+"-command.json"),record);process.getOutputStream().close();
            while(!process.waitFor(100,TimeUnit.MILLISECONDS)) {
                process.descendants().forEach(p->{if(descendants.stream().noneMatch(old->old.pid()==p.pid()))descendants.add(p);});
                if(System.nanoTime()-started>TimeUnit.SECONDS.toNanos(number(r,"timeout_seconds")))throw new IOException("Original GCTA workflow timed out; no complete result");
                checkOutput(worker,number(r,"maximum_output_bytes"));
            }
            checkOutput(worker,number(r,"maximum_output_bytes"));record.put("exit_code",process.exitValue());json(worker.resolve(step+"-command.json"),record);
            if(process.exitValue()!=0)throw new IOException("Original GCTA step "+step+" failed; inspect retained stdout/stderr/log/model/partial files");
        } finally {
            if(process!=null&&(process.isAlive()||descendants.stream().anyMatch(ProcessHandle::isAlive)||Thread.currentThread().isInterrupted()))record.put("owned_process_cleanup",stop(process,descendants));
            owned.set(null);try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){/* JVM shutdown owns cleanup. */}
            json(worker.resolve(step+"-command.json"),record);
        }
    }
    static Map<String,Long> footprint(Path root) throws IOException {return SusieWorkflowRuntime.footprint(root);}
    static void checkOutput(Path worker,long cap) throws IOException {
        long size=0;for(long bytes:footprint(worker).values()){if(bytes>cap-size)throw new IOException("maximum_output_bytes exceeded; all available partials retained");size+=bytes;}
    }
    private static List<Map<String,Object>> stop(Process process,List<ProcessHandle> children) {
        boolean interrupted=Thread.interrupted();process.descendants().forEach(p->{if(children.stream().noneMatch(old->old.pid()==p.pid()))children.add(p);});
        var handles=new ArrayList<>(children);Collections.reverse(handles);handles.add(process.toHandle());
        for(var handle:handles)if(handle.isAlive())handle.destroy();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(handles.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<deadline)try{Thread.sleep(25);}catch(InterruptedException e){interrupted=true;}
        for(var handle:handles)if(handle.isAlive())handle.destroyForcibly();deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(handles.stream().anyMatch(ProcessHandle::isAlive)&&System.nanoTime()<deadline)try{Thread.sleep(25);}catch(InterruptedException e){interrupted=true;}
        var result=new ArrayList<Map<String,Object>>();for(var handle:handles)result.add(Map.of("pid",handle.pid(),"alive_after_cleanup",handle.isAlive()));
        if(interrupted)Thread.currentThread().interrupt();return result;
    }

    private static void nativeRun(Path root,Map<String,Object> r,GctaWorkflowFiles.Prepared prepared,Map<String,Object> manifest) throws Exception {
        if(text(r,"model").equals("SPARSE_GRM")){nativeSparse(root,r,prepared,manifest);return;}
        long pairs=GctaWorkflowFiles.pairs(prepared.samples().size());
        if(pairs>number(r,"maximum_result_rows")||prepared.variants().size()>number(r,"maximum_result_rows"))throw new IOException("maximum_result_rows exceeded before native GRM fitting");
        if(pairs>number(r,"maximum_output_bytes")/8)throw new IOException("Native source GRM/count binary outputs exceed maximum_output_bytes before fitting");
        var result=GctaWorkflowFiles.nativeGrm(root,r,prepared);Path worker=root.resolve("worker");
        try(var grm=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(worker.resolve("result.grm.bin"),StandardOpenOption.CREATE_NEW)));
                var counts=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(worker.resolve("result.grm.N.bin"),StandardOpenOption.CREATE_NEW)));
                var exact=Files.newBufferedWriter(worker.resolve("result.grm.N.exact.tsv"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            exact.write("row\tcolumn\tjointly_called_markers\n");
            for(int i=0;i<prepared.samples().size();i++)for(int j=0;j<=i;j++) {
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Native result export interrupted");
                grm.writeInt(Integer.reverseBytes(Float.floatToRawIntBits(result.relationship(i,j))));
                counts.writeInt(Integer.reverseBytes(Float.floatToRawIntBits((float)result.jointlyCalledMarkers(i,j))));
                exact.write(i+"\t"+j+"\t"+result.jointlyCalledMarkers(i,j)+"\n");
            }
        }
        try(var ids=Files.newBufferedWriter(worker.resolve("result.grm.id"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            for(var id:result.samples())ids.write(id.familyId()+"\t"+id.individualId()+"\n");
        }
        try(var markers=Files.newBufferedWriter(worker.resolve("result.markers.tsv"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            markers.write("marker\tcalled_samples\tsupplied_allele_mean\thwe_variance\tdisposition\n");
            for(var marker:result.markers())markers.write(marker.id()+"\t"+marker.calledSamples()+"\t"+(marker.suppliedAlleleMean()==null?"NA":marker.suppliedAlleleMean())+"\t"+(marker.hweVariance()==null?"NA":marker.hweVariance())+"\t"+marker.disposition()+"\n");
        }
        manifest.put("native_source_profile",org.jlinalg.genetics.GctaHardcallGrm.SOURCE_PROFILE);
        manifest.put("native_source_output_weight",result.sourceOutputWeight());manifest.put("native_included_markers",result.includedMarkers());
        manifest.put("native_java_version",System.getProperty("java.version"));checkOutput(worker,number(r,"maximum_output_bytes"));
    }
    private static void nativeSparse(Path root,Map<String,Object> r,GctaWorkflowFiles.Prepared input,Map<String,Object> manifest) throws IOException {
        int n=input.samples().size();long pairs=GctaWorkflowFiles.pairs(n);
        if(pairs>number(r,"maximum_work"))throw new IOException("maximum_work exceeded before native sparse conversion");
        float cutoff=((Number)r.get("sparse_cutoff")).floatValue();long kept=0;
        try(var matrix=new DataInputStream(new BufferedInputStream(Files.newInputStream(root.resolve("inputs/grm0.grm.bin"))));
                var output=Files.newBufferedWriter(root.resolve("worker/result.grm.sp"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            for(int i=0;i<n;i++)for(int j=0;j<=i;j++) {
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Native sparse conversion interrupted");
                float value=Float.intBitsToFloat(Integer.reverseBytes(matrix.readInt()));
                if(value>cutoff) {
                    if(++kept>number(r,"maximum_result_rows"))throw new IOException("maximum_result_rows exceeded; sparse partial retained");
                    output.write(i+"\t"+j+"\t"+sourceFloat8(value)+"\n");
                }
            }
        }
        try(var ids=Files.newBufferedWriter(root.resolve("worker/result.grm.id"),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            for(var id:input.samples())ids.write(id.familyId()+"\t"+id.individualId()+"\n");
        }
        manifest.put("native_source_profile","GCTA1.94.1-float-threshold-sparse-GRM");
        manifest.put("source_effective_float32_cutoff",(double)cutoff);manifest.put("sparse_entries",kept);
        manifest.put("native_java_version",System.getProperty("java.version"));checkOutput(root.resolve("worker"),number(r,"maximum_output_bytes"));
    }
    /** C++ defaultfloat at numeric_limits<float>::digits10+2 (eight significant digits). */
    static String sourceFloat8(float value) {
        if(Float.floatToRawIntBits(value)==Integer.MIN_VALUE)return "-0";
        var rounded=new java.math.BigDecimal((double)value).round(new java.math.MathContext(8,java.math.RoundingMode.HALF_EVEN)).stripTrailingZeros();
        if(rounded.signum()==0)return "0";
        int exponent=rounded.precision()-rounded.scale()-1;
        if(exponent < -4 || exponent >= 8)
            return rounded.scaleByPowerOfTen(-exponent).toPlainString()+"e"+(exponent<0?"-":"+")+String.format(Locale.ROOT,"%02d",Math.abs(exponent));
        return rounded.toPlainString();
    }
}
