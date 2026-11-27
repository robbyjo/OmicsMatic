/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.jlinalg.sequence.WaspRemapAudit;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

/** Immutable original inputs, original mapping stages, complete native audit and fresh saved-input replay. */
final class WaspBundle {
    private WaspBundle() { }
    private record Inputs(Map<Path,Map<String,Object>> files,Map<String,Object> effective) { }
    static Runnable bounded(WaspRequest.Spec spec,Runnable cancellation) {
        long start=System.nanoTime();return()->{cancellation.run();if(Thread.currentThread().isInterrupted()||(System.nanoTime()-start)/1000000>spec.wallMillis())throw new IllegalStateException("WASP total wall capacity/cancellation exceeded; no complete result");};
    }
    private static String input(Path path,String name,Map<Path,Map<String,Object>> rows,WaspRequest.Spec spec,long[] total,Runnable check)throws IOException {
        member(name);if(!Files.isReadable(path)||!Files.isRegularFile(path))throw new WaspRuntime.Unavailable("Complete permitted WASP input unavailable: "+path+"; supply it before retrying. Dependent analysis skipped");
        require(!rows.containsKey(path),"Original input roles must be distinct files");Pin value=pin(path,spec.inputBytes(),check);total[0]=Math.addExact(total[0],value.bytes());require(total[0]<=spec.inputBytes(),"Complete original input byte capacity exceeded");
        rows.put(path,map("original",path.toString(),"captured",name,"bytes",value.bytes(),"sha256",value.sha256()));return name;
    }
    private static Inputs inputs(WaspRequest.Spec spec,Runnable check)throws IOException {
        var rows=new LinkedHashMap<Path,Map<String,Object>>();long[] total={0};String bam=input(spec.bam(),"alignment.bam",rows,spec,total,check),reference=input(spec.reference(),spec.gzipReference()?"reference.fa.gz":"reference.fa",rows,spec,total,check);String alt=null;
        Object selected=object(spec.value().get("reference")).get("alt_contigs");if(selected!=null)alt=input(WaspRequest.absolute(selected),"alternative-contigs.alt",rows,spec,total,check);
        var variants=new LinkedHashMap<String,Object>();variants.put("mode",spec.mode());
        if(spec.mode().equals("text")) {
            Path dir=WaspRequest.absolute(spec.variants().get("directory"));if(!Files.isDirectory(dir))throw new WaspRuntime.Unavailable("Required permitted text SNP directory unavailable: "+dir);
            var tree=tree(dir,spec.files(),spec.inputBytes(),spec.inputBytes(),check);require(!tree.isEmpty(),"Text SNP directory is empty; provide explicit complete per-contig gzip files");
            for(String name:new TreeSet<>(tree.keySet())){require(tree.get(name).kind().equals("file")&&!name.contains("/")&&name.endsWith(".snps.txt.gz"),"Original text variants require flat regular <contig>.snps.txt.gz files");input(dir.resolve(name),"variants/text/"+name,rows,spec,total,check);}
            variants.put("directory","variants/text");
        }else if(spec.mode().equals("hdf5")) {
            variants.put("snp_table",input(WaspRequest.absolute(spec.variants().get("snp_table")),"variants/snp-table.h5",rows,spec,total,check));
            variants.put("snp_index",input(WaspRequest.absolute(spec.variants().get("snp_index")),"variants/snp-index.h5",rows,spec,total,check));
            variants.put("haplotypes",input(WaspRequest.absolute(spec.variants().get("haplotypes")),"variants/haplotypes.h5",rows,spec,total,check));
        }else {
            variants.put("chromosomes",input(WaspRequest.absolute(spec.variants().get("chromosomes")),"variants/chromInfo.txt",rows,spec,total,check));var vcfs=new ArrayList<String>();
            for(Object value:list(spec.variants().get("vcfs"))){Path path=WaspRequest.absolute(value);vcfs.add(input(path,"variants/vcf/"+path.getFileName(),rows,spec,total,check));}variants.put("vcfs",vcfs);
        }
        require(rows.size()<spec.files(),"Complete original input file population exceeds capacity");return new Inputs(rows,map("bam",bam,"reference",reference,"alt_contigs",alt,"variants",variants));
    }
    static Path capture(Path request,Path supplied,Runnable cancellation)throws IOException {
        WaspRuntime.platform();if(!Files.isReadable(request))throw new WaspRuntime.Unavailable("WASP analysis request unavailable: "+request);byte[] raw=bytes(request,16777216);Pin requestPin=pin(request,16777216,cancellation);require(requestPin.equals(new Pin(raw.length,sha(raw))),"Original request changed while reading");
        var spec=WaspRequest.parse(SimpleJson.parse(utf8(raw)));Runnable check=bounded(spec,cancellation);Pin producing= WaspRuntime.producer();var runtime=WaspRuntime.open(spec.runtime(),spec.profile(),check);if(spec.mode().equals("vcf-to-hdf5"))runtime.program("snp2h5");
        Inputs inputs=inputs(spec,check);Path root=WaspRuntime.newRoot(supplied);runtime.separate(root);for(Path path:inputs.files.keySet())require(!path.startsWith(root)&&!root.startsWith(path),"WASP input/output paths overlap");Files.createDirectory(root);
        try {
            writeJson(root.resolve("INCOMPLETE.json"),map("filtering_admitted",false,"paper_recipe_admitted",false));Files.write(root.resolve("request.json"),raw,StandardOpenOption.CREATE_NEW);writeJson(root.resolve("source-inputs.json"),new ArrayList<>(inputs.files.values()));writeJson(root.resolve("runtime-binding.json"),map("path",spec.runtime().toString(),"seal",runtime.seal()));
            prepare(root,spec,inputs.effective,check);Path copied=root.resolve("engine/inputs");
            for(var row:inputs.files.entrySet()){check.run();Path target=copied.resolve(text(row.getValue().get("captured")));Files.createDirectories(target.getParent());Files.copy(row.getKey(),target);require(pin(target,spec.inputBytes(),check).equals(fingerprint(row.getValue(),spec.inputBytes())),"Captured original WASP input differs");}
            var captured=tree(copied,spec.files(),spec.inputBytes(),spec.inputBytes(),check);var expectedNames=new HashSet<String>();for(var row:inputs.files.values()){String name=text(row.get("captured"));expectedNames.add(name);require(captured.containsKey(name)&&captured.get(name).pin().equals(fingerprint(row,spec.inputBytes())),"Copied WASP population differs from complete original input roles");}require(captured.keySet().equals(expectedNames),"Unexpected file in complete captured WASP inputs");var capturedManifest=manifest(captured);writeJson(root.resolve("captured-inputs.json"),capturedManifest);
            runtime=runtime.anchored(anchors(root,producing,requestPin,new ArrayList<>(inputs.files.values()),map("path",spec.runtime().toString(),"seal",runtime.seal()),capturedManifest,inputs.effective));
            execute(root,spec,runtime,check);
            for(var row:inputs.files.entrySet())require(pin(row.getKey(),spec.inputBytes(),check).equals(fingerprint(row.getValue(),spec.inputBytes())),"Original WASP source input changed during analysis");
            if(spec.mode().equals("text")){var now=tree(WaspRequest.absolute(spec.variants().get("directory")),spec.files(),spec.inputBytes(),spec.inputBytes(),check);var names=new HashSet<String>();for(var row:inputs.files.values()){String name=text(row.get("captured"));if(name.startsWith("variants/text/"))names.add(name.substring("variants/text/".length()));}require(now.keySet().equals(names),"Original text SNP directory population changed during analysis");}
            require(requestPin.equals(pin(request,16777216,check))&&producing.equals(WaspRuntime.producer()),"Original WASP request or producing JAR changed");finish(root,spec,runtime,producing,check);return root;
        }catch(IOException|RuntimeException|Error e){failure(root,e);throw e;}
    }
    private static void prepare(Path root,WaspRequest.Spec spec,Map<?,?> effective,Runnable check)throws IOException {
        Path engine=Files.createDirectory(root.resolve("engine"));Files.createDirectory(engine.resolve("inputs"));Files.write(engine.resolve("worker.py"),WaspRuntime.resource("worker.py"),StandardOpenOption.CREATE_NEW);writeJson(engine.resolve("profile.json"),WaspRuntime.profile(spec.profile()));writeJson(engine.resolve("effective.json"),effective);
        Files.writeString(root.resolve("WARNINGS.md"),warnings(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);check.run();
    }
    private static Pin jsonPin(Object value)throws IOException {byte[] raw=(NextflowJson.json(value)+"\n").getBytes(StandardCharsets.UTF_8);return new Pin(raw.length,sha(raw));}
    private static Map<Path,Pin> anchors(Path root,Pin producer,Pin request,Object sources,Object runtime,Object captured,Object effective)throws IOException {
        return Map.of(WaspRuntime.producerPath(),producer,root.resolve("request.json"),request,root.resolve("source-inputs.json"),jsonPin(sources),root.resolve("runtime-binding.json"),jsonPin(runtime),root.resolve("captured-inputs.json"),jsonPin(captured),root.resolve("engine/effective.json"),jsonPin(effective));
    }
    private static void immutable(Path root,WaspRequest.Spec spec,WaspRuntime.Context runtime,Runnable check)throws IOException {
        check.run();runtime.verify(check);byte[] worker=WaspRuntime.resource("worker.py");require(pin(root.resolve("engine/worker.py"),16777216,check).equals(new Pin(worker.length,sha(worker))),"Owned WASP worker changed");
        require(NextflowJson.json(WaspRuntime.profile(spec.profile())).equals(NextflowJson.json(json(root.resolve("engine/profile.json"),16777216))),"Captured WASP source profile changed");
        require(NextflowJson.json(manifest(tree(root.resolve("engine/inputs"),spec.files(),spec.inputBytes(),spec.inputBytes(),check))).equals(NextflowJson.json(json(root.resolve("captured-inputs.json"),134217728))),"Complete captured original WASP input population changed");
    }
    private static NextflowProcess.Result process(Path root,String name,List<String> command,WaspRequest.Spec spec,WaspRuntime.Context runtime,Runnable check)throws IOException {
        try{return NextflowProcess.executeSession(command,runtime.environment(spec.hashSeed()),root.resolve("engine/"+name),root.resolve("engine"),root,spec.process(),()->immutable(root,spec,runtime,check),check);}
        catch(IOException e){throw new IOException("Original WASP step "+name+" failed. Inspect "+root.resolve("engine/"+name)+". "+e.getMessage(),e);}
    }
    private static List<String> worker(Path root,WaspRequest.Spec spec,WaspRuntime.Context runtime,String operation,String... arguments)throws IOException {
        var command=new ArrayList<String>(List.of(runtime.program("python"),"-P","-B","-s",root.resolve("engine/worker.py").toString(),operation));command.addAll(List.of(arguments));return command;
    }
    private static List<String> original(Path root,WaspRequest.Spec spec,WaspRuntime.Context runtime,String script,String... arguments)throws IOException {
        var command=new ArrayList<>(worker(root,spec,runtime,"original",runtime.mappingSource().toString(),script,Long.toString(spec.randomSeed())));command.addAll(List.of(arguments));return command;
    }
    private static void sort(Path root,String label,Path input,Path output,WaspRequest.Spec spec,WaspRuntime.Context runtime,Runnable check)throws IOException {
        process(root,label,List.of(runtime.program("samtools"),"sort","--no-PG","-@",Integer.toString(spec.threads()),"-o",output.toString(),input.toString()),spec,runtime,check);
    }
    private static void index(Path root,String label,Path input,WaspRequest.Spec spec,WaspRuntime.Context runtime,Runnable check)throws IOException {
        process(root,label,List.of(runtime.program("samtools"),"index",spec.csi()?"-c":"-b",input.toString()),spec,runtime,check);
    }
    private static void merge(Path root,String label,Path output,List<Path> inputs,WaspRequest.Spec spec,WaspRuntime.Context runtime,Runnable check)throws IOException {
        var command=new ArrayList<>(List.of(runtime.program("samtools"),"merge","--no-PG","-c","-p","-o",output.toString()));for(Path path:inputs)command.add(path.toString());process(root,label,command,spec,runtime,check);
    }
    private static void execute(Path root,WaspRequest.Spec spec,WaspRuntime.Context runtime,Runnable check)throws IOException {
        immutable(root,spec,runtime,check);Path engine=root.resolve("engine"),inputs=engine.resolve("inputs");var effective=object(json(engine.resolve("effective.json"),16777216));var input=WaspInputs.audit(inputs,effective,spec,check);var inputAudit=new LinkedHashMap<>(input.audit());var dynamicWarnings=new ArrayList<>(input.warnings());
        var variants=object(effective.get("variants"));Path table=null,snpIndex=null,haplotypes=null;Map<String,Object> vcfAudit=null;
        if(spec.mode().equals("vcf-to-hdf5")) {
            vcfAudit=WaspInputs.vcf(inputs,variants,input,spec,check);inputAudit.put("VCF",vcfAudit);dynamicWarnings.addAll(strings(vcfAudit.get("warnings")));
            Path hdf=Files.createDirectory(engine.resolve("hdf"));table=hdf.resolve("snp-table.h5");snpIndex=hdf.resolve("snp-index.h5");haplotypes=hdf.resolve("haplotypes.h5");
            var command=new ArrayList<>(List.of(runtime.program("snp2h5"),"--chrom",inputs.resolve(member(text(variants.get("chromosomes")))).toString(),"--format","vcf","--snp_tab",table.toString(),"--snp_index",snpIndex.toString(),"--haplotype",haplotypes.toString()));for(String file:strings(variants.get("vcfs")))command.add(inputs.resolve(member(file)).toString());process(root,"convert-original-HDF5",command,spec,runtime,check);
        }else if(spec.mode().equals("hdf5")) {table=inputs.resolve(member(text(variants.get("snp_table"))));snpIndex=inputs.resolve(member(text(variants.get("snp_index"))));haplotypes=inputs.resolve(member(text(variants.get("haplotypes"))));}
        if(table!=null) {
            process(root,"HDF5-content-census",worker(root,spec,runtime,"hdf-census",engine.resolve("HDF5-CENSUS.json").toString(),Long.toString(spec.hdfCells()),table.toString(),snpIndex.toString(),haplotypes.toString()),spec,runtime,check);
            var census=json(engine.resolve("HDF5-CENSUS.json"),268435456);if(vcfAudit!=null)WaspInputs.convertedHdf(census,vcfAudit);
            var hdf=WaspInputs.hdf(census,input,spec,check);inputAudit.put("HDF5",hdf);dynamicWarnings.addAll(strings(hdf.get("warnings")));
        }
        writeJson(root.resolve("INPUT-AUDIT.json"),inputAudit);
        Path sorted=engine.resolve("sorted-input.bam");sort(root,"sort-source-BAM",inputs.resolve(member(text(effective.get("bam")))),sorted,spec,runtime,check);index(root,"index-source-BAM",sorted,spec,runtime,check);
        Path reference=Files.createDirectory(engine.resolve("reference")).resolve("index");process(root,"BWA-index",List.of(runtime.program("bwa"),"index","-p",reference.toString(),inputs.resolve(member(text(effective.get("reference")))).toString()),spec,runtime,check);
        if(effective.get("alt_contigs")!=null){Files.copy(inputs.resolve(member(text(effective.get("alt_contigs")))),Path.of(reference+".alt"));dynamicWarnings.add("Explicit original BWA ALT-contig metadata was supplied. Only the provided ALT definitions and reference are used; no alternate mappings were inferred.");}
        Path find=Files.createDirectory(engine.resolve("find"));var findCommand=new ArrayList<>(original(root,spec,runtime,"find_intersecting_snps.py","--is_sorted","--max_seqs",textNumber(spec.settings().get("max_sequences")),"--max_snps",textNumber(spec.settings().get("max_snps")),"--output_dir",find.toString()));if(spec.paired())findCommand.add("--is_paired_end");
        if(table==null){findCommand.add("--snp_dir");findCommand.add(inputs.resolve("variants/text").toString());}
        else {findCommand.addAll(List.of("--snp_tab",table.toString(),"--snp_index",snpIndex.toString(),"--haplotype",haplotypes.toString()));if(spec.variants().get("samples")!=null){Path names=engine.resolve("selected-samples.txt");Files.writeString(names,String.join("\n",strings(spec.variants().get("samples")))+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);findCommand.add("--samples");findCommand.add(names.toString());}}
        findCommand.add(sorted.toString());process(root,"original-find",findCommand,spec,runtime,check);
        Path candidates=find.resolve("sorted-input.to.remap.bam"),single=find.resolve(spec.paired()?"sorted-input.remap.single.fq.gz":"sorted-input.remap.fq.gz"),first=spec.paired()?find.resolve("sorted-input.remap.fq1.gz"):null,second=spec.paired()?find.resolve("sorted-input.remap.fq2.gz"):null;
        var fastq=WaspFastqAudit.audit(candidates,single,first,second,spec,check);writeJson(root.resolve("FASTQ-AUDIT.json"),fastq.audit());dynamicWarnings.addAll(fastq.warnings());
        var remaps=new ArrayList<Path>();
        if(spec.paired())remaps.add(remap(root,"paired",List.of(first,second),reference,spec,runtime,check));remaps.add(remap(root,"single",List.of(single),reference,spec,runtime,check));Path remapped=engine.resolve("remapped.bam");merge(root,"merge-remapped",remapped,remaps,spec,runtime,check);index(root,"index-remapped",remapped,spec,runtime,check);
        var mapping=WaspFastqAudit.mapped(remapped,fastq,spec,check);writeJson(root.resolve("REMAPPING-AUDIT.json"),mapping);
        Path filtered=engine.resolve("filtered.bam");process(root,"original-remap-filter",original(root,spec,runtime,"filter_remapped_reads.py",candidates.toString(),remapped.toString(),filtered.toString()),spec,runtime,check);
        Path immediate=find.resolve("sorted-input.keep.bam"),keepSorted=engine.resolve("immediate.sorted.bam"),filteredSorted=engine.resolve("filtered.sorted.bam");sort(root,"sort-immediate-keep",immediate,keepSorted,spec,runtime,check);sort(root,"sort-filtered-keep",filtered,filteredSorted,spec,runtime,check);
        Path merged=engine.resolve("merged.bam");merge(root,"merge-retained-originals",merged,List.of(keepSorted,filteredSorted),spec,runtime,check);Path retained=merged;
        if(spec.dedup()){index(root,"index-before-original-paired-dedup",merged,spec,runtime,check);retained=engine.resolve("deduplicated.bam");process(root,"original-random-paired-dedup",original(root,spec,runtime,"rmdup_pe.py",merged.toString(),retained.toString()),spec,runtime,check);dynamicWarnings.add("Original rmdup_pe used explicit Python random seed "+spec.randomSeed()+"; single reads are discarded by that paired source step. The original printed mate-unmapped statistic repeats the improper-pair counter; use the separately named complete native record census for actual record accounting.");}
        Path finalBam=engine.resolve("final.bam");sort(root,"sort-final",retained,finalBam,spec,runtime,check);index(root,"index-final",finalBam,spec,runtime,check);
        var report=audit(root,spec,check);filterCounters(engine.resolve("original-remap-filter/process.stderr"),report.originalFilterCounters());require(report.recordPopulations().get("final_records")>=integer(spec.settings().get("minimum_final_records"),0,Integer.MAX_VALUE),"Final retained records fail the explicit minimum_final_records QC; no complete analysis admitted");dynamicWarnings.addAll(report.warnings());
        if(report.recordPopulations().get("final_records")==0)dynamicWarnings.add("EMPTY FILTERED POPULATION: the workflow retained zero reads. Downstream allele-specific inference is SKIPPED; a completed filter census does not establish a biological finding.");
        var sourceDiagnostics=sourceDiagnostics(root,spec,check);dynamicWarnings.addAll(sourceDiagnostics);writeJson(root.resolve("OUTPUT-AUDIT.json"),report(report,dynamicWarnings));Files.writeString(root.resolve("WARNINGS.md"),"\n"+String.join("\n",dynamicWarnings)+"\n",StandardCharsets.UTF_8,StandardOpenOption.APPEND);
        writeJson(root.resolve("canonical.json"),canonical(root,spec,report,check));immutable(root,spec,runtime,check);
    }
    private static Path remap(Path root,String role,List<Path> fastqs,Path reference,WaspRequest.Spec spec,WaspRuntime.Context runtime,Runnable check)throws IOException {
        var command=new ArrayList<String>();command.add(runtime.program("bwa"));command.addAll(spec.bwaArguments());command.add(reference.toString());for(Path path:fastqs)command.add(path.toString());var process=process(root,"BWA-remap-"+role,command,spec,runtime,check);Path output=root.resolve("engine/remapped-"+role+".bam");sort(root,"sort-remapped-"+role,process.stdout(),output,spec,runtime,check);return output;
    }
    private static String textNumber(Object value)throws IOException{return Long.toString(integer(value,0,Integer.MAX_VALUE));}
    private static WaspRemapAudit.Report audit(Path root,WaspRequest.Spec spec,Runnable check)throws IOException {
        Path engine=root.resolve("engine"),find=engine.resolve("find");return WaspRemapAudit.audit(engine.resolve("inputs/alignment.bam"),engine.resolve("sorted-input.bam"),find.resolve("sorted-input.keep.bam"),find.resolve("sorted-input.to.remap.bam"),engine.resolve("remapped.bam"),engine.resolve("filtered.bam"),engine.resolve("merged.bam"),engine.resolve("final.bam"),spec.paired(),spec.dedup(),new WaspRemapAudit.Limits(spec.records(),spec.nativeCharacters()),check);
    }
    private static Map<String,Object> report(WaspRemapAudit.Report report,List<String> warnings){return map("status","PASS_COMPLETE_NATIVE_WASP_ORIGINAL_READ_RETENTION_AND_INDEX_AUDIT","original_filter_counters",report.originalFilterCounters(),"record_populations",report.recordPopulations(),"complete_stage_canonical_record_SHA256",report.stageCanonicalRecordSha256(),"warnings",warnings,"paper_recipe_admitted",false,"CHT_admitted",false,"Hornet_admitted",false,"STARsolo_admitted",false);}
    private static void filterCounters(Path log,Map<String,Long> expected)throws IOException {
        var patterns=Map.of("keep","keep reads: ([0-9]+)","bad","bad reads: ([0-9]+)","not_present","not present reads: ([0-9]+)","discard","discard reads: ([0-9]+)","cigar_mismatch","  CIGAR mismatch ([0-9]+)","cigar_missing","  CIGAR missing or multiple: ([0-9]+)","pair_missing","  mate pair missing: ([0-9]+)");
        var actual=new HashMap<String,Long>();try(var reader=Files.newBufferedReader(log,StandardCharsets.UTF_8)){for(String line;(line=reader.readLine())!=null;)for(var entry:patterns.entrySet()){var match=Pattern.compile(entry.getValue()).matcher(line);if(match.matches())require(actual.put(entry.getKey(),Long.parseLong(match.group(1)))==null,"Repeated original WASP filter counter");}}
        require(actual.equals(expected),"Original printed WASP filter counters differ from complete independent native source-rule counts");
    }
    private static List<String> sourceDiagnostics(Path root,WaspRequest.Spec spec,Runnable check)throws IOException {
        var result=new ArrayList<String>();Pattern diagnostic=Pattern.compile(".*(?:WARNING|Warning|ERROR|Error|\\[E::).*"),optional=Pattern.compile("\\[E::idx_find_and_load] Could not retrieve index file for '.*sorted-input\\.to\\.remap\\.bam'");
        try(var directories=Files.newDirectoryStream(root.resolve("engine"))){for(Path dir:directories){Path log=dir.resolve("process.stderr");if(!Files.isDirectory(dir)||!Files.isRegularFile(log))continue;check.run();pin(log,Math.min(spec.outputBytes(),268435456),check);for(String line:utf8(bytes(log,(int)Math.min(spec.outputBytes(),268435456))).split("\\r?\\n",-1))if(diagnostic.matcher(line).matches()){if(line.contains("[E::")&&!optional.matcher(line).matches())throw new IOException("Unexpected original HTSlib error; inspect "+log+": "+line);result.add("Original "+dir.getFileName()+": "+line);}}}
        Collections.sort(result);return result;
    }
    private static Map<String,Object> canonical(Path root,WaspRequest.Spec spec,WaspRemapAudit.Report report,Runnable check)throws IOException {
        Path engine=root.resolve("engine");var bwa=new TreeMap<String,Object>();for(String suffix:List.of(".amb",".ann",".bwt",".pac",".sa"))bwa.put(suffix,pin(Path.of(engine.resolve("reference/index")+suffix),spec.outputBytes(),check));if(Files.exists(engine.resolve("reference/index.alt")))bwa.put(".alt",pin(engine.resolve("reference/index.alt"),spec.inputBytes(),check));
        var data=map("profile",spec.profile(),"record_populations",report.recordPopulations(),"original_filter_counters",report.originalFilterCounters(),"complete_stage_canonical_record_SHA256",report.stageCanonicalRecordSha256(),"BWA_index_files",bwa,"INPUT",json(root.resolve("INPUT-AUDIT.json"),268435456),"FASTQ",json(root.resolve("FASTQ-AUDIT.json"),268435456),"remapping",json(root.resolve("REMAPPING-AUDIT.json"),1048576),"final_BAM",pin(engine.resolve("final.bam"),spec.outputBytes(),check),"final_index",pin(Path.of(engine.resolve("final.bam")+(spec.csi()?".csi":".bai")),spec.outputBytes(),check));
        if(Files.exists(engine.resolve("HDF5-CENSUS.json")))data.put("HDF5",json(engine.resolve("HDF5-CENSUS.json"),268435456));return data;
    }
    private static void finish(Path root,WaspRequest.Spec spec,WaspRuntime.Context runtime,Pin producer,Runnable check)throws IOException {
        immutable(root,spec,runtime,check);require(producer.equals(WaspRuntime.producer()),"Producing WASP JAR changed");var files=new TreeMap<>(manifest(tree(root,spec.files(),spec.process().workspaceBytes(),Math.max(spec.inputBytes(),spec.outputBytes()),check)));files.remove("INCOMPLETE.json");
        writeJson(root.resolve("manifest.json"),map("schema_version",1,"profile",spec.profile(),"producer",producer,"runtime_seal",runtime.seal(),"files",files,"paper_recipe_admitted",false));
        writeJson(root.resolve("COMPLETED.json"),map("status","COMPLETE_WASP_MAPPING_FILTER_WORKFLOW","manifest",pin(root.resolve("manifest.json"),134217728,check),"filtering_admitted",true,"paper_recipe_admitted",false));verifySaved(root,true,check);Files.delete(root.resolve("INCOMPLETE.json"));
    }
    static WaspRequest.Spec verifySaved(Path supplied,boolean precommit,Runnable cancellation)throws IOException {
        Path root=supplied.toAbsolutePath().normalize();directory(root);require(!Files.exists(root.resolve("FAILURE.json"))&&Files.exists(root.resolve("INCOMPLETE.json"))==precommit,"Failed/incomplete WASP attempt is not admitted");var done=object(json(root.resolve("COMPLETED.json"),1048576));keys(done,"status","manifest","filtering_admitted","paper_recipe_admitted");require("COMPLETE_WASP_MAPPING_FILTER_WORKFLOW".equals(done.get("status"))&&Boolean.TRUE.equals(done.get("filtering_admitted"))&&Boolean.FALSE.equals(done.get("paper_recipe_admitted")),"Missing or expanded WASP workflow admission");
        require(pin(root.resolve("manifest.json"),134217728,cancellation).equals(fingerprint(done.get("manifest"),134217728)),"Saved WASP manifest changed");var saved=object(json(root.resolve("manifest.json"),134217728));keys(saved,"schema_version","profile","producer","runtime_seal","files","paper_recipe_admitted");integer(saved.get("schema_version"),1,1);require(WaspRuntime.producer().equals(fingerprint(saved.get("producer"),1000000000)),"Restore the exact WASP producing JAR; do not refresh old seals");var spec=WaspRequest.parse(json(root.resolve("request.json"),16777216));Runnable check=bounded(spec,cancellation);require(spec.profile().equals(saved.get("profile"))&&Boolean.FALSE.equals(saved.get("paper_recipe_admitted")),"Different WASP profile/admission");
        var runtime=WaspRuntime.open(spec.runtime(),spec.profile(),check);require(runtime.seal().equals(fingerprint(saved.get("runtime_seal"),1048576)),"Runtime binding seal differs");var actual=new TreeMap<>(manifest(tree(root,spec.files(),spec.process().workspaceBytes(),Math.max(spec.inputBytes(),spec.outputBytes()),check)));actual.remove("manifest.json");actual.remove("COMPLETED.json");actual.remove("INCOMPLETE.json");require(NextflowJson.json(actual).equals(NextflowJson.json(saved.get("files"))),"Complete saved WASP file population changed");immutable(root,spec,runtime,check);
        var binding=object(json(root.resolve("runtime-binding.json"),1048576));keys(binding,"path","seal");require(spec.runtime().toString().equals(binding.get("path"))&&runtime.seal().equals(fingerprint(binding.get("seal"),1048576)),"Saved WASP runtime pointer differs from request and binding seal");
        var report=audit(root,spec,check);filterCounters(root.resolve("engine/original-remap-filter/process.stderr"),report.originalFilterCounters());require(NextflowJson.json(canonical(root,spec,report,check)).equals(NextflowJson.json(json(root.resolve("canonical.json"),268435456))),"Saved complete native WASP result/canonical population differs");return spec;
    }
    static void verify(Path supplied,Path scratchSupplied,Runnable cancellation)throws IOException {
        Path original=supplied.toAbsolutePath().normalize();var spec=verifySaved(original,false,cancellation);Runnable check=bounded(spec,cancellation);var runtime=WaspRuntime.open(spec.runtime(),spec.profile(),check);Path scratch=WaspRuntime.newRoot(scratchSupplied);runtime.separate(scratch);require(!scratch.startsWith(original)&&!original.startsWith(scratch),"Replay source and NEW scratch paths overlap");Files.createDirectory(scratch);
        try {
            writeJson(scratch.resolve("INCOMPLETE.json"),map("filtering_admitted",false,"paper_recipe_admitted",false));for(String name:List.of("request.json","source-inputs.json","runtime-binding.json","captured-inputs.json"))Files.copy(original.resolve(name),scratch.resolve(name));
            var effective=object(json(original.resolve("engine/effective.json"),16777216));prepare(scratch,spec,effective,check);Path inputs=scratch.resolve("engine/inputs");
            for(Object key:object(json(original.resolve("captured-inputs.json"),134217728)).keySet()){String name=member(text(key));Path output=inputs.resolve(name);Files.createDirectories(output.getParent());Files.copy(original.resolve("engine/inputs").resolve(name),output);}
            runtime=runtime.anchored(anchors(scratch,WaspRuntime.producer(),pin(original.resolve("request.json"),16777216,check),json(original.resolve("source-inputs.json"),134217728),json(original.resolve("runtime-binding.json"),1048576),json(original.resolve("captured-inputs.json"),134217728),effective));immutable(scratch,spec,runtime,check);execute(scratch,spec,runtime,check);
            require(NextflowJson.json(json(original.resolve("canonical.json"),268435456)).equals(NextflowJson.json(json(scratch.resolve("canonical.json"),268435456))),"Fresh original WASP workflow replay differs in complete FASTQ/HDF/read/record/BAM/index populations");
            verifySaved(original,false,check);writeJson(scratch.resolve("REPLAY.json"),map("status","PASS_COMPLETE_FRESH_WASP_MAPPING_REPLAY","original_completion",pin(original.resolve("COMPLETED.json"),1048576,check),"final_BAM_and_index_byte_parity",true,"all_canonical_populations_equal",true,"paper_recipe_admitted",false));finish(scratch,spec,runtime,WaspRuntime.producer(),check);verifySaved(original,false,check);
        }catch(IOException|RuntimeException|Error e){failure(scratch,e);throw e;}
    }
    private static void failure(Path root,Throwable e)throws IOException {Files.deleteIfExists(root.resolve("COMPLETED.json"));if(!Files.exists(root.resolve("FAILURE.json")))writeJson(root.resolve("FAILURE.json"),map("status",e instanceof WaspRuntime.Unavailable?"SKIP3_DEPENDENT_WASP_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_WASP_WORKFLOW","error",e.toString(),"required_action",WaspRuntime.ACTION,"filtering_admitted",false));}
    private static String warnings(){return "WASP MAPPING WORKFLOW: WARNINGS AND REQUIRED ACTION\n\n"+WaspRuntime.ACTION+"\nInputs begin with an existing aligned BAM and the caller's actual reference/variant preparation. Original alignment, upstream sample QC, reference build and author recipe are not inferred. The BAM is sorted in owned storage. Text, existing phased HDF5 and original VCF-to-HDF5 conversion are separate explicit modes. Original find, BWA remapping, original CIGAR-aware filtering, original-record merge, optional seeded original paired duplicate removal and final sorting/indexing are retained with complete native input/FQ/record/index audits.\n\nPython3.11 -P -B -s uses an explicit startup hash seed and disables user-site/unsafe-path imports; bound source is added explicitly. No original scientific source is patched or re-ordered. Containers may contain different HDF5/gzip creation metadata, so replay compares every decoded FASTQ byte and HDF fixed-type column/array population plus complete final BAM/index bytes and canonical original records. All raw files, original logs and failures remain retained.\n\nOriginal HDF chr-prefix fallback is allowed only when explicitly selected. Missing requested samples cannot silently bypass filtering. Intentional missing-variant contigs must be explicit; contigs whose placed reads are all source-ineligible are accounted separately. Text source uppercasing/hyphen removal is reported; lossy S10 truncation, duplicate index overwrite and unsorted variant rows fail admission. No identifier, genotype, allele or coordinate repair is performed by the adapter.\n\nOriginal source FASTQ quality/orientation rules and unordered paired-CIGAR comparison are retained and reported. A retained wrong-chromosome coordinate coincidence fails native QC without changing original output. Original single-read rmdup omits its final position group and is not admitted; paired rmdup is an explicit optional policy and drops single reads. No CHT, Hornet or STARsolo coverage is established.\n\nEvery required prerequisite must be permitted and present. Binding and analysis commands do not install software. Complete input/output, decoded/row/cell/native-character and process capacities are explicit; native caps do not reserve JVM memory. Missing dependencies skip dependent work, and capacity/cancellation/changed-byte failures admit no complete result. Preserve the exact producing JAR, binding and all attempts.\n";}
}
