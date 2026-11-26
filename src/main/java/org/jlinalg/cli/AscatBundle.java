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

/** Complete immutable inputs, original ASCAT execution and fresh saved-input replay. */
final class AscatBundle {
    private AscatBundle() { }
    static Runnable bounded(AscatRequest.Spec spec,Runnable cancellation){long start=System.nanoTime();return ()->{cancellation.run();if(Thread.currentThread().isInterrupted()||(System.nanoTime()-start)/1000000>spec.wallMillis())throw new IllegalStateException("ASCAT total wall capacity/cancellation exceeded");};}
    private static String input(Path path,String kind,Map<Path,Map<String,Object>> rows,AscatRequest.Spec spec,long[] total,Runnable check,String chosen)throws IOException {
        if(path==null)return null;
        if(rows.containsKey(path)){var row=rows.get(path);require(kind.equals(row.get("kind"))&&(chosen==null||chosen.equals(row.get("captured"))),"Conflicting captured input roles/index names");return "inputs/"+text(row.get("captured"));}
        if(!Files.isRegularFile(path)||!Files.isReadable(path))throw new AscatRuntime.Unavailable("Complete permitted ASCAT input unavailable: "+path+". Supply the named file and existing indexes before retrying");
        Pin p=pin(path,spec.inputBytes(),check);total[0]=Math.addExact(total[0],p.bytes());require(total[0]<=spec.inputBytes()&&rows.size()<spec.files(),"Complete input bytes/file capacity exceeded");
        String name=chosen==null?String.format(Locale.ROOT,"file-%06d.%s",rows.size()+1,kind):chosen;rows.put(path,map("original",path.toString(),"captured",name,"kind",kind,"bytes",p.bytes(),"sha256",p.sha256()));return "inputs/"+name;
    }
    private static String hts(Path path,Map<Path,Map<String,Object>> rows,AscatRequest.Spec spec,long[] total,Runnable check)throws IOException {
        if(path==null)return null;boolean bam=path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bam");String name=input(path,bam?"bam":"cram",rows,spec,total,check,null);
        input(Path.of(path+(bam?".bai":".crai")),bam?"bai":"crai",rows,spec,total,check,name.substring(7)+(bam?".bai":".crai"));return name;
    }
    private static Map<String,Object> effective(AscatRequest.Spec spec,Map<Path,Map<String,Object>> rows,long[] total,Runnable check,String counter)throws IOException {
        var matrices=new LinkedHashMap<String,Object>();for(var entry:spec.matrices().entrySet())matrices.put(entry.getKey(),input(entry.getValue(),"matrix",rows,spec,total,check,null));
        var samples=new ArrayList<Object>();for(var sample:spec.samples())samples.add(map("id",sample.id(),"sex",sample.sex(),"tumor",hts(sample.tumor(),rows,spec,total,check),"normal",hts(sample.normal(),rows,spec,total,check)));
        var references=new ArrayList<Object>();for(var ref:spec.references())references.add(map("chromosome",ref.chromosome(),"alleles",input(ref.alleles(),"alleles",rows,spec,total,check,null),"loci",input(ref.loci(),"loci",rows,spec,total,check,null)));
        var panel=new ArrayList<Object>();for(var sample:spec.panel())panel.add(map("patient_id",sample.patient(),"id",sample.id(),"sex",sample.sex(),"hts",hts(sample.hts(),rows,spec,total,check)));
        String fasta=input(spec.fasta(),"fasta",rows,spec,total,check,null);if(fasta!=null)input(Path.of(spec.fasta()+".fai"),"fai",rows,spec,total,check,fasta.substring(7)+".fai");
        var correction=map("mode",spec.correction().get("mode"),"gc",input(optionalPath(spec.correction().get("gc")),"correction",rows,spec,total,check,null),"replication",input(optionalPath(spec.correction().get("replication")),"correction",rows,spec,total,check,null));
        return map("schema_version",1,"mode",spec.mode(),"samples",samples,"matrices",spec.mode().equals("matrices")?matrices:null,"references",references,"normal_panel",panel,"bed",input(spec.bed(),"bed",rows,spec,total,check,null),"problem_loci",input(spec.problemLoci(),"problem-loci",rows,spec,total,check,null),"reference_fasta",fasta,"correction",correction,"settings",spec.settings(),"allele_counter",counter);
    }
    private static Path optionalPath(Object value)throws IOException{return value==null?null:AscatRequest.absolute(value);}
    static Path capture(Path supplied,AscatRequest.Spec spec,Runnable cancellation)throws IOException {
        AscatRuntime.platform();Runnable check=bounded(spec,cancellation);var runtime=AscatRuntime.open(spec.runtime(),spec.profile(),check);Path root=AscatRuntime.newRoot(supplied);
        require(!root.startsWith(spec.runtime())&&!spec.runtime().startsWith(root),"Capture/runtime paths overlap");
        boolean sequencing=!spec.mode().equals("matrices");if(sequencing)require(root.toString().matches("[A-Za-z0-9_./-]+"),"Original ASCAT shell preparation requires a capture path without spaces/metacharacters; original input paths are copied unchanged to safe names");
        var originals=new LinkedHashMap<Path,Map<String,Object>>();var effective=effective(spec,originals,new long[]{0},check,sequencing?runtime.alleleCounter():null);
        for(Path original:originals.keySet())require(!root.startsWith(original)&&!original.startsWith(root),"Capture/input paths overlap");Files.createDirectory(root);
        try {
            Files.writeString(root.resolve("WARNINGS.md"),AscatRuntime.ACTION,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);writeJson(root.resolve("request.json"),spec.value());writeJson(root.resolve("source-inputs.json"),new ArrayList<>(originals.values()));
            Files.write(root.resolve("worker.R"),AscatRuntime.resource("worker.R"),StandardOpenOption.CREATE_NEW);Path engine=Files.createDirectory(root.resolve("engine")),inputs=Files.createDirectory(engine.resolve("inputs"));
            for(var row:originals.entrySet()){check.run();Files.copy(row.getKey(),inputs.resolve(text(row.getValue().get("captured"))));}
            writeJson(engine.resolve("effective.json"),effective);writeJson(engine.resolve("profile.json"),AscatRuntime.profile(spec.profile()));
            writeJson(root.resolve("captured-inputs.json"),manifest(tree(inputs,spec.files(),spec.inputBytes(),spec.inputBytes(),check)));writeJson(root.resolve("runtime-binding.json"),map("path",spec.runtime().toString(),"seal",runtime.seal()));
            writeJson(root.resolve("INPUT-AUDIT.json"),auditInputs(engine,spec,check));run(root,engine,spec,runtime,effective,check);finish(engine,spec,runtime,check);
            writeJson(root.resolve("OUTPUT-AUDIT.json"),AscatOutputs.audit(engine,spec,check));for(var row:originals.entrySet())require(pin(row.getKey(),spec.inputBytes(),check).equals(fingerprint(row.getValue(),spec.inputBytes())),"Original input changed during complete ASCAT workflow");
            runtime.verify(check);writeJson(root.resolve("canonical.json"),AscatOutputs.canonical(engine,spec,check));
            writeJson(root.resolve("manifest.json"),map("producer",AscatRuntime.producer(),"files",manifest(tree(root,spec.files(),spec.process().workspaceBytes(),Math.max(spec.inputBytes(),spec.outputBytes()),check)),"whole_package_admitted",false));
            writeJson(root.resolve("COMPLETED.json"),map("status","COMPLETE_ASCAT_SCOPED_WORKFLOW","manifest",pin(root.resolve("manifest.json"),134217728,check),"analysis_admitted",true,"fresh_replay_required",true,"whole_package_admitted",false));return root;
        }catch(IOException|RuntimeException|Error e){failure(root,e);throw e;}
    }
    private static void finish(Path engine,AscatRequest.Spec spec,AscatRuntime.Context runtime,Runnable check)throws IOException {
        runtime.loadedMaps(Files.readAllLines(engine.resolve("R-MAPS.txt"),StandardCharsets.UTF_8));runtime.loadedMaps(Files.readAllLines(Path.of("/proc/self/maps"),StandardCharsets.UTF_8));check.run();
    }
    private static void run(Path root,Path engine,AscatRequest.Spec spec,AscatRuntime.Context runtime,Object effective,Runnable check)throws IOException {
        var expected=object(json(root.resolve("captured-inputs.json"),134217728));Pin producer=AscatRuntime.producer();byte[] worker=AscatRuntime.resource("worker.R");var env=new HashMap<>(runtime.executionEnvironment());env.put("XDG_CACHE_HOME",engine.resolve("cache").toString());
        NextflowProcess.Check integrity=()->{check.run();require(producer.equals(AscatRuntime.producer()),"Producing JAR changed");require(pin(root.resolve("worker.R"),16777216,check).equals(new Pin(worker.length,sha(worker))),"Captured worker differs from producing JAR");require(NextflowJson.json(expected).equals(NextflowJson.json(manifest(tree(engine.resolve("inputs"),spec.files(),spec.inputBytes(),spec.inputBytes(),check)))),"Complete captured inputs changed");require(NextflowJson.json(effective).equals(NextflowJson.json(json(engine.resolve("effective.json"),16777216))),"Effective source settings changed");require(NextflowJson.json(AscatRuntime.profile(spec.profile())).equals(NextflowJson.json(json(engine.resolve("profile.json"),16777216))),"Original source profile changed");};
        NextflowProcess.executeSessionEnvironment(List.of(text(runtime.programs().get("Rscript")),"--vanilla",root.resolve("worker.R").toString()),env,engine.resolve("process"),engine,root,spec.process(),integrity,check);
    }
    static Map<String,Object> auditInputs(Path engine,AscatRequest.Spec spec,Runnable check)throws IOException {
        var effective=object(json(engine.resolve("effective.json"),16777216));var audit=new TreeMap<String,Object>();
        if(spec.mode().equals("matrices")) {
            var matrices=object(effective.get("matrices"));Map<String,String> coordinates=null;var chromosomes=new HashSet<>(strings(spec.settings().get("chromosomes")));
            for(Object key:matrices.keySet())if(matrices.get(key)!=null){var values=matrix(engine.resolve(text(matrices.get(key))),spec,check);if(coordinates==null)coordinates=values;else require(coordinates.equals(values),"Complete original matrices differ in source locus IDs or coordinates");audit.put(text(key),map("rows",values.size(),"declared_chromosome_rows",values.values().stream().filter(x->chromosomes.contains(x.split("\t",-1)[0])).count()));}
        }
        try(var stream=Files.newDirectoryStream(engine.resolve("inputs"))){for(Path path:stream){check.run();String name=path.getFileName().toString();if(name.endsWith(".bam")){var a=SambambaStreamingBamAudit.inspectInput(path,spec.bamLimits());audit.put(name,map("records",a.records(),"bytes",a.input().bytes(),"sha256",a.input().sha256(),"scope","complete BGZF framing/CRC, BAM header and every record; original alleleCounter performs indexed locus counting"));}else if(name.endsWith(".cram")){try(var in=Files.newInputStream(path)){require(Arrays.equals(in.readNBytes(4),new byte[]{'C','R','A','M'}),"Invalid CRAM magic");}audit.put(name,map("scope","complete byte identity and CRAM magic; original alleleCounter decodes requested indexed loci, not an independent every-record CRAM audit"));}}}
        return map("status","PASS_COMPLETE_CAPTURED_ASCAT_INPUT_IDENTITIES_AND_DECLARED_MATRIX_POPULATIONS","inputs",audit,"chromosome_selection","Original ASCAT explicitly selects declared chromosomes and sorts loci; all source file bytes, including excluded chromosomes, remain captured");
    }
    private static Map<String,String> matrix(Path path,AscatRequest.Spec spec,Runnable check)throws IOException {
        var coordinates=new HashMap<String,String>();try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)){
            String line=reader.readLine();require(line!=null,"Empty original matrix");String[] header=line.split("\t",-1);require(header.length==spec.samples().size()+3,"Declare every original matrix sample, in its source column order");for(int i=0;i<spec.samples().size();i++)require(header[i+3].equals(spec.samples().get(i).id()),"Original matrix sample identities/order differ");
            for(line=reader.readLine();line!=null;line=reader.readLine()){check.run();String[] fields=line.split("\t",-1);require(fields.length==header.length&&!fields[0].isEmpty()&&!fields[1].isEmpty(),"Invalid original matrix width or source locus ID");try{require(Long.parseLong(fields[2])>0,"Invalid source coordinate");}catch(NumberFormatException e){throw new IOException("Invalid source coordinate",e);}require(coordinates.put(fields[0],fields[1]+"\t"+fields[2])==null,"Duplicate original matrix locus ID");for(int i=3;i<fields.length;i++)require(fields[i].matches("NA|NaN|Inf|-Inf|[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"),"Original numerical token is outside the explicit input domain; no coercion/repair is performed");}
        }require(!coordinates.isEmpty(),"Empty original matrix locus population");return coordinates;
    }
    static void verify(Path supplied,Path scratchSupplied,Runnable cancellation)throws IOException {
        AscatRuntime.platform();Path root=directory(supplied.toAbsolutePath().normalize());var spec=AscatRequest.read(root.resolve("request.json"));Runnable check=bounded(spec,cancellation);
        var done=object(json(root.resolve("COMPLETED.json"),1048576));require("COMPLETE_ASCAT_SCOPED_WORKFLOW".equals(done.get("status"))&&!Files.exists(root.resolve("FAILURE.json")),"Only completed ASCAT captures are verifiable");require(pin(root.resolve("manifest.json"),134217728,check).equals(fingerprint(done.get("manifest"),134217728)),"Saved manifest changed");var saved=object(json(root.resolve("manifest.json"),134217728));require(AscatRuntime.producer().equals(fingerprint(saved.get("producer"),1000000000L)),"Restore exact producing JAR; never refresh old hashes");
        var actual=new TreeMap<>(manifest(tree(root,spec.files(),spec.process().workspaceBytes(),Math.max(spec.inputBytes(),spec.outputBytes()),check)));actual.remove("manifest.json");actual.remove("COMPLETED.json");require(NextflowJson.json(actual).equals(NextflowJson.json(saved.get("files"))),"Complete saved ASCAT bundle population/bytes changed");
        var runtime=AscatRuntime.open(spec.runtime(),spec.profile(),check);var binding=object(json(root.resolve("runtime-binding.json"),1048576));require(spec.runtime().toString().equals(binding.get("path"))&&runtime.seal().equals(fingerprint(binding.get("seal"),1048576)),"Saved runtime binding changed");require(NextflowJson.json(AscatOutputs.audit(root.resolve("engine"),spec,check)).equals(NextflowJson.json(json(root.resolve("OUTPUT-AUDIT.json"),134217728))),"Saved output audit does not recompute");
        Path scratch=AscatRuntime.newRoot(scratchSupplied);require(!scratch.startsWith(root)&&!root.startsWith(scratch)&&!scratch.startsWith(spec.runtime())&&!spec.runtime().startsWith(scratch),"Replay must be separate from evidence/runtime");if(!spec.mode().equals("matrices"))require(scratch.toString().matches("[A-Za-z0-9_./-]+"),"Original shell preparation requires a replay path without spaces/metacharacters");Files.createDirectory(scratch);
        try {
            Files.writeString(scratch.resolve("WARNINGS.md"),AscatRuntime.ACTION,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);Files.copy(root.resolve("worker.R"),scratch.resolve("worker.R"));Files.copy(root.resolve("captured-inputs.json"),scratch.resolve("captured-inputs.json"));Path engine=Files.createDirectory(scratch.resolve("engine")),inputs=Files.createDirectory(engine.resolve("inputs"));
            try(var children=Files.newDirectoryStream(root.resolve("engine/inputs"))){for(Path path:children){check.run();Files.copy(path,inputs.resolve(path.getFileName()));}}for(String name:List.of("effective.json","profile.json"))Files.copy(root.resolve("engine/"+name),engine.resolve(name));
            require(NextflowJson.json(auditInputs(engine,spec,check)).equals(NextflowJson.json(json(root.resolve("INPUT-AUDIT.json"),134217728))),"Saved native input audit does not recompute");Object effective=json(engine.resolve("effective.json"),16777216);run(scratch,engine,spec,runtime,effective,check);finish(engine,spec,runtime,check);writeJson(scratch.resolve("OUTPUT-AUDIT.json"),AscatOutputs.audit(engine,spec,check));var canonical=AscatOutputs.canonical(engine,spec,check);writeJson(scratch.resolve("canonical.json"),canonical);require(NextflowJson.json(canonical).equals(NextflowJson.json(json(root.resolve("canonical.json"),134217728))),"Fresh complete ASCAT workflow differs in original model states, complete tables, QC or plot bytes");runtime.verify(check);writeJson(scratch.resolve("COMPLETED.json"),map("status","PASS_COMPLETE_ASCAT_BINARY_AND_NUMERICAL_REPLAY","original_capture",root.toString(),"canonical_outputs",canonical.size(),"whole_package_admitted",false));
        }catch(IOException|RuntimeException|Error e){failure(scratch,e);throw e;}
    }
    private static void failure(Path root,Throwable e)throws IOException {writeJson(root.resolve("FAILURE.json"),map("status",e instanceof AscatRuntime.Unavailable||e instanceof NextflowProcess.Unavailable?"SKIP3_DEPENDENT_ASCAT_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_ASCAT_WORKFLOW","error",e.toString(),"analysis_admitted",false,"required_action",AscatRuntime.ACTION));}
}
