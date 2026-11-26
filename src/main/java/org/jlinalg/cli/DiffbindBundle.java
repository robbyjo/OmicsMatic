/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.alignment.SambambaStreamingBamAudit;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

/** Full input capture and fresh original-workflow binary/numerical replay. */
final class DiffbindBundle {
    private DiffbindBundle() { }
    static final String ACTION="WARNINGS AND REQUIRED ACTION: inspect WARNINGS.md, OUTPUT-AUDIT.json, SOURCE-WARNINGS.json and original process stdout/stderr. Require COMPLETED.json and a successful diffbind-tools --verify CAPTURE --scratch NEW_DIRECTORY using the exact producing JAR and unchanged bound runtime. Missing or forbidden prerequisites produce SKIP3 and skip dependent analysis; install only where permitted, then bind a NEW runtime. Changed bytes, invalid input/model, nonconvergence, child failure, cancellation or resource exhaustion produce FAIL2 with no complete result. Retain failed attempts and original inputs, correct the named cause and retry NEW paths. This scoped workflow uses original DiffBind3.24.0/R4.6.2, DESeq2/edgeR, additive source-factor designs and BAM/BED inputs. Author versions/designs/defaults are never inferred. test_fold changes the source hypothesis test AND report filter; it is not a view-only fold-change filter. Duplicate report column names remain in their original positions. Native Java projects fitted DESeq2 results; original upstream code performs model fitting and shrinkage. No whole-package or biological validation is claimed.\n";
    static Runnable bounded(DiffbindRequest.Spec spec,Runnable cancellation){long start=System.nanoTime();return ()->{cancellation.run();if(Thread.currentThread().isInterrupted()||(System.nanoTime()-start)/1000000>spec.wallMillis())throw new IllegalStateException("DiffBind total wall capacity/cancellation exceeded");};}
    static Path capture(Path supplied,DiffbindRequest.Spec spec,Runnable cancellation)throws IOException {
        DiffbindRuntime.platform();Runnable check=bounded(spec,cancellation);var runtime=DiffbindRuntime.open(spec.runtime(),check);Path root=DiffbindRuntime.newRoot(supplied);
        require(!root.startsWith(spec.runtime())&&!spec.runtime().startsWith(root),"Capture/runtime paths overlap");
        var originals=new LinkedHashMap<Path,Map<String,Object>>();var sampleRows=new ArrayList<Map<String,Object>>();long[] total={0};
        for(var sample:spec.samples()) {
            String bam=input(sample.bam(),"bam",originals,spec,total,check),peaks=input(sample.peaks(),"bed",originals,spec,total,check);
            input(Path.of(sample.bam()+".bai"),"bai",originals,spec,total,check,bam+".bai");
            String control=null;if(sample.control()!=null){control=input(sample.control(),"bam",originals,spec,total,check);input(Path.of(sample.control()+".bai"),"bai",originals,spec,total,check,control+".bai");}
            sampleRows.add(map("id",sample.id(),"attributes",sample.attributes(),"bam",bam,"peaks",peaks,"control",control,"control_id",sample.controlId()));
        }
        for(Path original:originals.keySet())require(!root.startsWith(original)&&!original.startsWith(root),"Capture/input paths overlap");
        Files.createDirectory(root);
        try {
            Files.writeString(root.resolve("WARNINGS.md"),ACTION,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);writeJson(root.resolve("request.json"),spec.value());writeJson(root.resolve("source-inputs.json"),new ArrayList<>(originals.values()));
            Path engine=Files.createDirectory(root.resolve("engine"));Path inputs=Files.createDirectory(engine.resolve("inputs"));
            for(var row:originals.entrySet()){check.run();Files.copy(row.getKey(),inputs.resolve(text(row.getValue().get("captured"))));}
            Files.writeString(inputs.resolve("samples.csv"),csv(sampleRows,spec.samples().get(0).attributes().keySet()),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            writeJson(engine.resolve("effective.json"),map("schema_version",1,"settings",spec.settings()));
            Files.write(root.resolve("worker.R"),DiffbindRuntime.resource("worker.R"),StandardOpenOption.CREATE_NEW);
            writeJson(root.resolve("runtime-binding.json"),map("path",spec.runtime().toString(),"seal",runtime.seal()));
            writeJson(root.resolve("captured-inputs.json"),manifest(tree(inputs,spec.files(),spec.inputBytes()+16777216L,spec.inputBytes(),check)));
            writeJson(root.resolve("INPUT-AUDIT.json"),auditInputs(inputs,spec,check));
            run(root,engine,spec,runtime,check);
            writeJson(root.resolve("OUTPUT-AUDIT.json"),DiffbindOutputs.audit(engine,spec,check));
            runtime.loadedMaps(Files.readAllLines(engine.resolve("R-MAPS.txt"),StandardCharsets.UTF_8));runtime.loadedMaps(Files.readAllLines(Path.of("/proc/self/maps"),StandardCharsets.UTF_8));
            for(var row:originals.entrySet())require(pin(row.getKey(),spec.inputBytes(),check).equals(fingerprint(row.getValue(),spec.inputBytes())),"Original input changed during complete workflow");
            runtime.verify(check);writeJson(root.resolve("canonical.json"),DiffbindOutputs.canonical(engine,spec,check));
            writeJson(root.resolve("manifest.json"),map("producer",DiffbindRuntime.producer(),"files",manifest(tree(root,spec.files(),spec.process().workspaceBytes(),spec.outputBytes(),check)),"whole_package_admitted",false));
            writeJson(root.resolve("COMPLETED.json"),map("status","COMPLETE_DIFFBIND_SCOPED_WORKFLOW","manifest",pin(root.resolve("manifest.json"),134217728,check),"analysis_admitted",true,"fresh_replay_required",true,"whole_package_admitted",false));return root;
        }catch(IOException|RuntimeException|Error e){failure(root,e);throw e;}
    }
    private static String input(Path path,String kind,Map<Path,Map<String,Object>> rows,DiffbindRequest.Spec spec,long[] total,Runnable check)throws IOException {return input(path,kind,rows,spec,total,check,null);}
    private static String input(Path path,String kind,Map<Path,Map<String,Object>> rows,DiffbindRequest.Spec spec,long[] total,Runnable check,String chosen)throws IOException {
        if(rows.containsKey(path)){var row=rows.get(path);require(kind.equals(row.get("kind"))&&(chosen==null||chosen.equals(row.get("captured"))),"Conflicting captured input roles/index paths");return text(row.get("captured"));}
        if(!Files.isRegularFile(path)||!Files.isReadable(path))throw new DiffbindRuntime.Unavailable("Complete permitted input unavailable: "+path+". Require each BAM index at BAM_PATH.bai; prepare it before retrying");
        Pin p=pin(path,spec.inputBytes(),check);total[0]=Math.addExact(total[0],p.bytes());require(total[0]<=spec.inputBytes()&&rows.size()<spec.files(),"Complete input bytes/file capacity exceeded");
        String name=chosen==null?String.format(Locale.ROOT,"file-%06d.%s",rows.size()+1,kind):chosen;rows.put(path,map("original",path.toString(),"captured",name,"kind",kind,"bytes",p.bytes(),"sha256",p.sha256()));return name;
    }
    private static String csv(List<Map<String,Object>> samples,Set<String> attributes)throws IOException {
        var columns=new ArrayList<>(List.of("SampleID"));columns.addAll(new TreeSet<>(attributes));columns.addAll(List.of("bamReads","Peaks","PeakCaller"));boolean controls=samples.stream().anyMatch(x->x.get("control")!=null);if(controls)columns.addAll(List.of("bamControl","ControlID"));
        var result=new StringBuilder();csvLine(result,columns);
        for(var sample:samples){var a=object(sample.get("attributes"));var values=new ArrayList<String>();for(String c:columns)values.add(switch(c){case "SampleID"->text(sample.get("id"));case "bamReads"->text(sample.get("bam"));case "Peaks"->text(sample.get("peaks"));case "PeakCaller"->"bed";case "bamControl"->sample.get("control")==null?"":text(sample.get("control"));case "ControlID"->sample.get("control_id")==null?"":text(sample.get("control_id"));default->String.valueOf(a.get(c));});csvLine(result,values);}return result.toString();
    }
    private static void csvLine(StringBuilder s,List<String> values){s.append(String.join(",",values.stream().map(v->"\""+v.replace("\"","\"\"")+"\"").toList())).append('\n');}
    private static Map<String,Object> auditInputs(Path inputs,DiffbindRequest.Spec spec,Runnable check)throws IOException {
        var audit=new TreeMap<String,Object>();try(var stream=Files.newDirectoryStream(inputs)){
            for(Path path:stream){check.run();String name=path.getFileName().toString();
                if(name.endsWith(".bam")){var a=SambambaStreamingBamAudit.inspectInput(path,spec.bamLimits());audit.put(name,map("records",a.records(),"bytes",a.input().bytes(),"sha256",a.input().sha256(),"scope","complete BGZF framing/CRC, BAM header and every record; no independent peak-count classification or BAI qualification"));}
                else if(name.endsWith(".bed")){long rows=0;try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){for(String line=reader.readLine();line!=null;line=reader.readLine()){check.run();String[] f=line.split("\t",-1);require(f.length>=3&&f.length<=12&&!f[0].isEmpty(),"Require plain complete BED3..12 records; no header/comment repairs");try{long start=Long.parseLong(f[1]),end=Long.parseLong(f[2]);require(start>=0&&end>start&&end<=Integer.MAX_VALUE,"Invalid source BED interval");}catch(NumberFormatException e){throw new IOException("Invalid BED coordinate",e);}rows++;}}require(rows>0,"Empty peak input");audit.put(name,map("rows",rows,"scope","complete input intervals; original source controls consensus/count semantics"));}
            }
        }return map("status","PASS_COMPLETE_CAPTURED_BAM_AND_BED_INPUTS","inputs",audit);
    }
    private static void run(Path root,Path engine,DiffbindRequest.Spec spec,DiffbindRuntime.Context runtime,Runnable check)throws IOException {
        var expectedInputs=object(json(root.resolve("captured-inputs.json"),134217728));Pin producer=DiffbindRuntime.producer();byte[] worker=DiffbindRuntime.resource("worker.R");
        var env=new HashMap<>(runtime.executionEnvironment());env.put("XDG_CACHE_HOME",engine.resolve("cache").toString());
        NextflowProcess.Check integrity=()->{
            check.run();require(producer.equals(DiffbindRuntime.producer()),"Producing JAR changed");
            require(pin(root.resolve("worker.R"),16777216,check).equals(new Pin(worker.length,sha(worker))),"Captured worker differs from producer");
            require(NextflowJson.json(expectedInputs).equals(NextflowJson.json(manifest(tree(engine.resolve("inputs"),spec.files(),spec.inputBytes()+16777216L,spec.inputBytes(),check)))),"Complete captured input bytes changed");
            require(NextflowJson.json(object(json(engine.resolve("effective.json"),16777216))).equals(NextflowJson.json(map("schema_version",1,"settings",spec.settings()))),"Effective settings changed");
        };
        NextflowProcess.executeSessionEnvironment(List.of(text(runtime.programs().get("Rscript")),"--vanilla",root.resolve("worker.R").toString()),env,engine.resolve("process"),engine,root,spec.process(),integrity,check);
    }
    static void verify(Path supplied,Path scratchSupplied,Runnable cancellation)throws IOException {
        DiffbindRuntime.platform();Path root=directory(supplied.toAbsolutePath().normalize());var spec=DiffbindRequest.read(root.resolve("request.json"));Runnable check=bounded(spec,cancellation);
        var done=object(json(root.resolve("COMPLETED.json"),1048576));require("COMPLETE_DIFFBIND_SCOPED_WORKFLOW".equals(done.get("status"))&&!Files.exists(root.resolve("FAILURE.json")),"Only completed original captures are verifiable");
        require(pin(root.resolve("manifest.json"),134217728,check).equals(fingerprint(done.get("manifest"),134217728)),"Saved manifest changed");var manifest=object(json(root.resolve("manifest.json"),134217728));require(DiffbindRuntime.producer().equals(fingerprint(manifest.get("producer"),1000000000L)),"Restore exact producing JAR; never refresh old hashes");
        var files=new TreeMap<>(NextflowFiles.manifest(tree(root,spec.files(),spec.process().workspaceBytes(),spec.outputBytes(),check)));files.remove("manifest.json");files.remove("COMPLETED.json");require(NextflowJson.json(files).equals(NextflowJson.json(manifest.get("files"))),"Complete saved bundle bytes/population changed");
        var runtime=DiffbindRuntime.open(spec.runtime(),check);var binding=object(json(root.resolve("runtime-binding.json"),1048576));require(spec.runtime().toString().equals(binding.get("path"))&&runtime.seal().equals(fingerprint(binding.get("seal"),1048576)),"Saved runtime binding changed");
        require(NextflowJson.json(DiffbindOutputs.audit(root.resolve("engine"),spec,check)).equals(NextflowJson.json(json(root.resolve("OUTPUT-AUDIT.json"),16777216))),"Saved output audit does not recompute");
        Path scratch=DiffbindRuntime.newRoot(scratchSupplied);require(!scratch.startsWith(root)&&!root.startsWith(scratch)&&!scratch.startsWith(spec.runtime()),"Replay directory must be separate from evidence/runtime");Files.createDirectory(scratch);
        try {
            Files.writeString(scratch.resolve("WARNINGS.md"),ACTION,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);Files.copy(root.resolve("worker.R"),scratch.resolve("worker.R"));Files.copy(root.resolve("captured-inputs.json"),scratch.resolve("captured-inputs.json"));
            Path engine=Files.createDirectory(scratch.resolve("engine")),inputs=Files.createDirectory(engine.resolve("inputs"));
            try(var children=Files.newDirectoryStream(root.resolve("engine/inputs"))){for(Path p:children)Files.copy(p,inputs.resolve(p.getFileName()));}Files.copy(root.resolve("engine/effective.json"),engine.resolve("effective.json"));
            require(NextflowJson.json(auditInputs(inputs,spec,check)).equals(NextflowJson.json(json(root.resolve("INPUT-AUDIT.json"),16777216))),"Saved native input audit does not recompute");
            run(scratch,engine,spec,runtime,check);writeJson(scratch.resolve("OUTPUT-AUDIT.json"),DiffbindOutputs.audit(engine,spec,check));
            runtime.loadedMaps(Files.readAllLines(engine.resolve("R-MAPS.txt"),StandardCharsets.UTF_8));runtime.loadedMaps(Files.readAllLines(Path.of("/proc/self/maps"),StandardCharsets.UTF_8));
            var canonical=DiffbindOutputs.canonical(engine,spec,check);writeJson(scratch.resolve("canonical.json"),canonical);require(NextflowJson.json(canonical).equals(NextflowJson.json(json(root.resolve("canonical.json"),134217728))),"Fresh complete workflow differs in model, counts, reports, projections or plot bytes");
            runtime.verify(check);writeJson(scratch.resolve("COMPLETED.json"),map("status","PASS_COMPLETE_DIFFBIND_BINARY_AND_NUMERICAL_REPLAY","original_capture",root.toString(),"canonical_outputs",canonical.size(),"whole_package_admitted",false));
        }catch(IOException|RuntimeException|Error e){failure(scratch,e);throw e;}
    }
    private static void failure(Path root,Throwable e)throws IOException {writeJson(root.resolve("FAILURE.json"),map("status",e instanceof DiffbindRuntime.Unavailable?"SKIP3_DEPENDENT_DIFFBIND_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_DIFFBIND_WORKFLOW","error",e.toString(),"analysis_admitted",false,"required_action",ACTION));}
}
