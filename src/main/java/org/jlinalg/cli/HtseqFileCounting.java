/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import static org.jlinalg.cli.HtseqFileContract.*;

/** Transactional original SAM/BAM-GTF counting and portable integrity/schema verification. */
final class HtseqFileCounting {
    private HtseqFileCounting() { }
    private static final String WARNING="# HTSEQ COUNTING WARNINGS AND REQUIRED ACTION\n\nOriginal SAM/BAM-GTF counting uses the exact declared edition and explicit options. The original parser decides single-read versus paired-read units (original-auto); no unit, strand, annotation or genome is inferred from a paper. Declared genome/annotation labels are user assertions, not reference identity validation. Raw IDs and source decimal strings are preserved. Summary categories may overlap under ALL/FRACTION and need not sum to gene counts or units. FRACTION divides by assigned-feature count, not NH. A minimum MAPQ21 is needed for strict MAPQ>20. No normalization, differential model, proper-pair prefilter, UMI/cell/barcode counting or custom ATAC script is supplied.\n\nHistorical differences remain:0.5.3 default MAPQ was0 (this workflow always declares it explicitly), summary names are unprefixed and unknown chromosomes warn/skip without incrementing no_feature.0.5.3/0.6.0 score secondary/supplementary records and reject tested header-only SAM and =/X CIGAR; unsupported options are refused.0.11.1 lacks FRACTION. Random assignment is not admitted by this workflow. An unspecified edition or0.13 without a patch is not silently assigned a profile.\n\nFresh package/interpreter snapshots include bytecode, compiled/generated files and adjacent package libraries. Original scientific Python modules must match packaged release-source hashes. Exact Python/NumPy/pysam versions are checked by a real isolated import. Package and interpreter identities, original inputs and prepared copies are checked before/after processes. Base standard library, loader/system DLLs and whole-host/execution authenticity are outside this guard. This is not whole historical environment, paper-exact or biological equivalence.\n\nVERIFY needs Java only. It checks the complete committed bundle, source profile, zero-exit/cleanup records, literal original table and exact derived count/summary tables. It does not independently parse/pair the user's SAM/BAM-GTF, re-execute HTSeq, replay original statistical/RNG behavior, authenticate execution or establish biological accuracy. Empty/zero counts are not negative biological findings. Capacity limits are logical; process AS/CPU/file/wall guards and sampled aggregate RSS have their documented supervisor scope.\n\nInspect count/process.stderr and probe/process.stderr, even after success; original warnings remain retained. A source exit/input failure admits no partial count table. Native prepared-read counting is a separate checked API, not an independent native parse of these files. Use the separate development JAR. Cell Ranger remains excluded.\n\n"+ACTION+"\n";
    static void count(Request r)throws Exception{
        prepareOut(r.out());
        try{
            write(r.out().resolve("INCOMPLETE.json"),Map.of("status","INCOMPLETE_HTSEQ_COUNT","result_admitted",false));
            Path home=r.path("runtime_home"),alignment=r.path("alignment"),annotation=r.path("annotation");
            for(Path input:List.of(home,alignment,annotation,r.source()))if(r.out().startsWith(input)||input.startsWith(r.out()))throw new IOException("HTSeq output must be disjoint from runtime, original inputs and request");
            Path prepared=Files.createDirectory(r.out().resolve("prepared"));
            Files.write(prepared.resolve("bridge.py"),resource("bridge.py"),StandardOpenOption.CREATE_NEW);
            Files.write(prepared.resolve("request.json"),bytes(r.source(),1048576),StandardOpenOption.CREATE_NEW);
            var runtime=new HtseqCountRuntime(home,r.text("version"));
            var originals=Map.of("alignment",pin(alignment,r.limits().input()),"annotation",pin(annotation,r.limits().input()),"request",pin(r.source(),1048576));
            Path localAlignment=prepared.resolve("alignment."+r.text("format")),localAnnotation=prepared.resolve("annotation.gtf");
            write(r.out().resolve("resolved.json"),Map.of("out",r.out().toString(),"prepared",prepared.toString(),"alignment_copy",localAlignment.toString(),"annotation_copy",localAnnotation.toString(),"runtime_home",home.toString()));
            copy(alignment,localAlignment,originals.get("alignment"),r.limits().input());copy(annotation,localAnnotation,originals.get("annotation"),r.limits().input());
            if(!originals.get("alignment").equals(pin(localAlignment,r.limits().input()))||!originals.get("annotation").equals(pin(localAnnotation,r.limits().input())))throw new IOException("HTSeq input copy differs from original complete bytes");
            formats(localAlignment,r.text("format"),localAnnotation);
            write(prepared.resolve("arguments.json"),arguments(r,localAlignment,localAnnotation));
            var ready=tree(prepared,r.limits().process().workspaceBytes(),64);
            RsemProcess.Check guard=()->{
                if(!originals.get("alignment").equals(pin(alignment,r.limits().input()))||!originals.get("annotation").equals(pin(annotation,r.limits().input()))||!originals.get("request").equals(pin(r.source(),1048576))||!ready.equals(tree(prepared,r.limits().process().workspaceBytes(),64)))throw new IOException("HTSeq original/prepared input or fixed bridge/argument identity changed");
            };
            var probe=runtime.execute("probe",prepared,r.out().resolve("probe"),r.limits(),guard);
            var imported=json(probe.stdout(),1048576);runtime.probe(imported);
            write(r.out().resolve("runtime.json"),Map.of("runtime",runtime.document(),"actual_import_probe",imported));
            var process=runtime.execute("count",prepared,r.out().resolve("count"),r.limits(),guard);
            var table=HtseqCountTable.read(process.stdout(),r.text("version"),r.text("nonunique"),r.limits().output(),r.limits().rows());
            Files.writeString(r.out().resolve("counts.tsv"),table.genesTsv(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            Files.writeString(r.out().resolve("summary.tsv"),table.summaryTsv(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            write(r.out().resolve("report.json"),Map.of("schema_version",1,"version",r.text("version"),"nonunique",r.text("nonunique"),
                "feature_rows",table.genes().size(),"summary_rows",5,"original_inputs",originals,"source_count_table",pin(process.stdout(),r.limits().output()),
                "independent_native_SAM_GTF_parse_or_pairing",false,"execution_authenticated",false,"biological_accuracy_certified",false));
            Files.writeString(r.out().resolve("WARNINGS.md"),WARNING,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            write(r.out().resolve("analysis-status.json"),Map.of("status","COMPLETED_ORIGINAL_HTSEQ_COUNT_WITH_WARNINGS","result_admitted",true));
            var inventory=tree(r.out(),r.limits().process().workspaceBytes(),512);inventory.remove("INCOMPLETE.json");
            write(r.out().resolve("manifest.json"),Map.of("schema_version",1,"kind","original-HTSeq-count-bundle","version",r.text("version"),"limits",r.limits().document(),"files",inventory));
            write(r.out().resolve("COMPLETED.json"),Map.of("status","COMPLETED_HTSEQ_COUNT","manifest",pin(r.out().resolve("manifest.json"),16777216)));
            verifyRun(r.out(),true);runtime.verify();guard.verify();
            Files.delete(r.out().resolve("INCOMPLETE.json"));
        }catch(Exception failure){failure(r.out(),failure);throw failure;}
    }
    private static void prepareOut(Path out)throws IOException{
        CHECK.run();if(Files.exists(out,LinkOption.NOFOLLOW_LINKS)||out.getParent()==null||!Files.isDirectory(out.getParent(),LinkOption.NOFOLLOW_LINKS)||!out.getParent().toRealPath().equals(out.getParent()))throw new IOException("HTSeq requires a NEW output directory with an existing canonical parent");Files.createDirectory(out);
    }
    private static void failure(Path out,Exception error)throws IOException{
        boolean unavailable=error instanceof HtseqCountRuntime.Unavailable;
        Path partial=out.resolve("partial");
        for(String name:List.of("counts.tsv","summary.tsv","verification.json","report.json","COMPLETED.json","analysis-status.json","WARNINGS.md"))if(Files.exists(out.resolve(name),LinkOption.NOFOLLOW_LINKS)){
            if(!Files.exists(partial))Files.createDirectory(partial);Files.move(out.resolve(name),partial.resolve(name));
        }
        Files.writeString(out.resolve("WARNINGS.md"),"# "+(unavailable?"SKIP3: HTSEQ ANALYSIS SKIPPED":"FAIL2: NO HTSEQ RESULT ADMITTED")+"\n\n"+error+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        write(out.resolve("analysis-status.json"),Map.of("status",unavailable?"SKIP3_DEPENDENCY_UNAVAILABLE":"FAIL2_NO_RESULT_ADMITTED","result_admitted",false,"error",error.toString()));
        write(out.resolve("FAILURE.json"),Map.of("error",error.toString(),"required_action",ACTION,"result_admitted",false));
    }
    static Map<String,Object> verifyRun(Path run,boolean internal)throws IOException{
        if(Files.exists(run.resolve("INCOMPLETE.json"),LinkOption.NOFOLLOW_LINKS)&&!internal)throw new IOException("HTSeq run retains an INCOMPLETE guard; no result is admitted");
        if(Files.exists(run.resolve("FAILURE.json"),LinkOption.NOFOLLOW_LINKS))throw new IOException("Failed/skipped HTSeq attempt cannot be verified as an admitted result");
        var manifest=json(run.resolve("manifest.json"),16777216);integer(manifest.get("schema_version"),1,1);
        if(!"original-HTSeq-count-bundle".equals(manifest.get("kind")))throw new IOException("Not an HTSeq original count bundle");
        var completed=json(run.resolve("COMPLETED.json"),1048576);
        if(!"COMPLETED_HTSEQ_COUNT".equals(completed.get("status"))||!pin(run.resolve("manifest.json"),16777216).equals(completed.get("manifest")))throw new IOException("HTSeq completion/manifest identity differs");
        var request=request(run.resolve("prepared/request.json"));if(!request.operation().equals("COUNT")||!request.text("version").equals(manifest.get("version")))throw new IOException("HTSeq saved request/profile differs");
        if(!RsemJson.json(request.limits().document()).equals(RsemJson.json(manifest.get("limits"))))throw new IOException("HTSeq saved request/capacities differ");
        var inventory=tree(run,request.limits().process().workspaceBytes(),512);
        inventory.remove("INCOMPLETE.json");inventory.remove("manifest.json");inventory.remove("COMPLETED.json");
        if(!inventory.equals(manifest.get("files")))throw new IOException("HTSeq complete bundle inventory differs, including missing/extra files");
        if(!pin(run.resolve("prepared/bridge.py"),1048576).equals(bytePin(resource("bridge.py"))))throw new IOException("HTSeq fixed packaged bridge differs");
        var resolved=json(run.resolve("resolved.json"),1048576);String savedOut=text(resolved.get("out")),prepared=text(resolved.get("prepared")),home=text(resolved.get("runtime_home"));
        if(!savedOut.startsWith("/")||!prepared.equals(savedOut+"/prepared")||!text(resolved.get("alignment_copy")).equals(prepared+"/alignment."+request.text("format"))||!text(resolved.get("annotation_copy")).equals(prepared+"/annotation.gtf"))throw new IOException("HTSeq recorded original preparation paths are inconsistent");
        Object recordedArguments=SimpleJson.parse(utf8(bytes(run.resolve("prepared/arguments.json"),1048576)));
        if(!arguments(request,text(resolved.get("alignment_copy")),text(resolved.get("annotation_copy"))).equals(recordedArguments))throw new IOException("HTSeq original count arguments differ from the explicit saved request");
        var runtime=json(run.resolve("runtime.json"),16777216);var details=object(runtime.get("runtime"));var profile=profile(request.text("version"));
        if(!profile.equals(details.get("scientific_source_profile")))throw new IOException("HTSeq saved original scientific-source profile differs");
        HtseqCountRuntime.sources(object(details.get("snapshot")),profile);
        var imported=object(runtime.get("actual_import_probe"));
        if(!imported.equals(json(run.resolve("probe/process.stdout"),1048576)))throw new IOException("HTSeq recorded import profile differs from actual probe stdout");
        if(!home.equals(details.get("runtime_home"))||!text(details.get("site")).equals(home+"/"+profile.get("site"))||!imported.get("executable").equals(object(details.get("snapshot")).get("python_target")))throw new IOException("HTSeq import/package/interpreter paths differ from the recorded runtime");
        for(String name:List.of("HTSeq","python","numpy","pysam"))if(!profile.get(name.equals("HTSeq")?"version":name).equals(imported.get(name)))throw new IOException("Saved HTSeq actual import edition/dependency differs: "+name);
        for(String pkg:List.of("HTSeq","numpy","pysam")){
            String file=text(imported.get(pkg+"_file"));int slash=file.lastIndexOf('/');
            if(slash<0||!file.substring(0,slash).equals(text(details.get("site"))+"/"+pkg))throw new IOException("Saved HTSeq import escaped the declared package directory: "+pkg);
        }
        for(String name:List.of("probe","count")){
            var process=json(run.resolve(name+"/process.json"),16777216);
            if(!Long.valueOf(0).equals(process.get("exit_code"))||!"completed-process-only".equals(process.get("status"))||!(process.get("owned_live_after_cleanup") instanceof List<?> live)||!live.isEmpty())throw new IOException("HTSeq zero-exit/owned-process cleanup record is not complete: "+name);
            var expected=new java.util.ArrayList<>(List.of("/usr/bin/setsid","/usr/bin/prlimit","--as="+request.limits().memory(),"--cpu="+request.limits().seconds(),"--fsize="+request.limits().output(),"--nofile=256","--core=0","--",home+"/bin/python","-s","-B",prepared+"/bridge.py",name));
            if(name.equals("count"))expected.add(prepared+"/arguments.json");
            if(!expected.equals(process.get("command"))||!expected.equals(json(run.resolve(name+"/launch.json"),16777216).get("command"))||!RsemJson.json(request.limits().process()).equals(RsemJson.json(process.get("limits")))||!List.of(prepared).equals(process.get("accounted_roots"))||!"strict-zero-exit-process".equals(process.get("process_purpose")))throw new IOException("HTSeq recorded fixed process command/capacities differ: "+name);
        }
        var table=HtseqCountTable.read(run.resolve("count/process.stdout"),request.text("version"),request.text("nonunique"),request.limits().output(),request.limits().rows());
        if(!utf8(bytes(run.resolve("counts.tsv"),request.limits().output())).equals(table.genesTsv())||!utf8(bytes(run.resolve("summary.tsv"),request.limits().output())).equals(table.summaryTsv()))throw new IOException("HTSeq derived tables differ from literal original count table");
        var report=json(run.resolve("report.json"),16777216);
        if(!Long.valueOf(table.genes().size()).equals(report.get("feature_rows"))||!Long.valueOf(5).equals(report.get("summary_rows"))||!request.text("version").equals(report.get("version"))||!request.text("nonunique").equals(report.get("nonunique"))||!pin(run.resolve("count/process.stdout"),request.limits().output()).equals(report.get("source_count_table")))throw new IOException("HTSeq count report differs from original table/request");
        var original=object(report.get("original_inputs"));
        if(!pin(run.resolve("prepared/alignment."+request.text("format")),request.limits().input()).equals(original.get("alignment"))||!pin(run.resolve("prepared/annotation.gtf"),request.limits().input()).equals(original.get("annotation"))||!pin(run.resolve("prepared/request.json"),1048576).equals(original.get("request")))throw new IOException("HTSeq original input identities differ from prepared snapshots");
        var status=json(run.resolve("analysis-status.json"),1048576);
        if(!"COMPLETED_ORIGINAL_HTSEQ_COUNT_WITH_WARNINGS".equals(status.get("status"))||!Boolean.TRUE.equals(status.get("result_admitted"))||!utf8(bytes(run.resolve("WARNINGS.md"),1048576)).equals(WARNING))throw new IOException("HTSeq committed status or required scope/recovery warnings differ");
        return Map.of("status","VERIFIED_HTSEQ_COUNT_BUNDLE_INTEGRITY_AND_TABLE_SCHEMA","version",request.text("version"),"feature_rows",table.genes().size(),
            "independent_native_SAM_GTF_parse_or_pairing",false,"execution_authenticated",false,"biological_accuracy_certified",false);
    }
    static void verify(Request r)throws Exception{
        Path run=r.path("run");if(r.out().startsWith(run)||run.startsWith(r.out()))throw new IOException("HTSeq VERIFY output and source run must be disjoint");
        prepareOut(r.out());
        try{
            write(r.out().resolve("INCOMPLETE.json"),Map.of("status","INCOMPLETE_HTSEQ_VERIFY","result_admitted",false));
            var before=tree(run,1000000000000L,512);var verified=verifyRun(run,false);
            write(r.out().resolve("verification.json"),verified);
            Files.writeString(r.out().resolve("WARNINGS.md"),WARNING,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            if(!before.equals(tree(run,1000000000000L,512)))throw new IOException("HTSeq source bundle changed during portable verification");
            write(r.out().resolve("COMPLETED.json"),Map.of("status","COMPLETED_HTSEQ_VERIFY","verification",pin(r.out().resolve("verification.json"),1048576)));
            Files.delete(r.out().resolve("INCOMPLETE.json"));
        }catch(Exception failure){failure(r.out(),failure);throw failure;}
    }
}
