/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;

/** Complete prepared-input capture with exact producer and portable full numerical replay. */
final class AllcoolsRegionBundle {
    static final String PROFILE="native-allcools-region-quantification-v1";
    static final String ACTION="Required action: inspect WARNINGS.md, OUTPUT-AUDIT.json and outputs/pools.tsv. Zero-coverage regions have hyper-score 1; uint32 count saturation can change ratios, while scores use raw counts. Require COMPLETED.json and allcools-region-quantify --verify with the exact producing JAR. Keep complete inputs, producer and failed attempts. Correct named invalid input, overflow, dimensions, resource, cancellation or consistency failures and retry NEW paths. Missing or forbidden prerequisites give SKIP3 and skip dependent analyses. No upstream software is needed for these prepared-count calculations. ALLC/Tabix/Zarr construction and full author workflows remain pending; do not refresh old seals.";
    static final class Unavailable extends IOException {private static final long serialVersionUID=1L;Unavailable(String value){super(value);}}
    private AllcoolsRegionBundle() {}
    static Pin producer()throws IOException {
        try {
            Path p=Path.of(AllcoolsRegionBundle.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            if(!p.getFileName().toString().endsWith(".jar"))throw new Unavailable("Build and retain the complete executable producing JAR; class-directory capture is unavailable");
            return pin(p,1000000000L,()->{});
        }catch(java.net.URISyntaxException|NullPointerException e){throw new Unavailable("Exact producing JAR unavailable; restore it before dependent analysis");}
    }
    private static Pin strictPin(Object raw,long maximum)throws IOException { keys(object(raw),"bytes","sha256");return fingerprint(raw,maximum); }
    static Runnable bounded(AllcoolsRegionRequest.Spec s,Runnable cancellation) {
        long start=System.nanoTime();return ()->{cancellation.run();if(Thread.currentThread().isInterrupted()||(System.nanoTime()-start)/1000000>s.capacity().wallMillis())throw new IllegalStateException("Cooperative wall limit/cancellation exceeded; no complete region quantification result");};
    }
    static Path capture(Path supplied,AllcoolsRegionRequest.Spec s,Runnable cancellation)throws IOException {
        Path root=supplied.toAbsolutePath().normalize();require(supplied.toAbsolutePath().equals(root)&&root.getParent()!=null,"Require a NEW canonical capture path");
        directory(root.getParent());require(!Files.exists(root,LinkOption.NOFOLLOW_LINKS),"Capture path exists; use a NEW path");
        Runnable check=bounded(s,cancellation);var originals=Map.of("counts.tsv",Path.of(s.counts()));var pins=new TreeMap<String,Pin>();long total=0;
        for(var entry:originals.entrySet()) {
            Path p=entry.getValue();if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS)||!Files.isReadable(p))throw new Unavailable("Complete permitted input unavailable: "+p);
            Pin identity=pin(p,s.capacity().inputBytes(),check);pins.put(entry.getKey(),identity);total=Math.addExact(total,identity.bytes());
        }
        require(total<=s.capacity().inputBytes(),"Combined complete input bytes exceed capacity");Pin producer=producer();boolean created=false;String stage="CREATE_CAPTURE";
        try {
            Files.createDirectory(root);created=true;Files.createDirectory(root.resolve("inputs"));Files.createDirectory(root.resolve("outputs"));
            writeJson(root.resolve("INCOMPLETE.json"),Map.of("analysis_admitted",false));writeJson(root.resolve("request.json"),s.value());
            Files.writeString(root.resolve("WARNINGS.md"),warnings(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            stage="CAPTURE_COMPLETE_INPUTS";
            for(var entry:originals.entrySet()) {
                Files.copy(entry.getValue(),root.resolve("inputs/"+entry.getKey()));
                require(pins.get(entry.getKey()).equals(pin(entry.getValue(),s.capacity().inputBytes(),check))&&pins.get(entry.getKey()).equals(pin(root.resolve("inputs/"+entry.getKey()),s.capacity().inputBytes(),check)),"Original input changed during capture");
            }
            writeJson(root.resolve("source-inputs.json"),Map.of("original_counts",s.counts(),"pins",pins,"complete_original_bytes",true));
            stage="COMPLETE_REGION_QUANTIFICATION";writeJson(root.resolve("OUTPUT-AUDIT.json"),AllcoolsRegionCompute.run(root,s,false,check));
            unchanged(originals,pins,s,producer,check);stage="FINALIZE_AND_REPLAY";var files=files(root,s,check);files.remove("INCOMPLETE.json");
            writeJson(root.resolve("manifest.json"),Map.of("schema_version",1,"profile",PROFILE,"producer",producer,"files",files,"analysis_admitted",true,"whole_package_admitted",false));
            writeJson(root.resolve("COMPLETED.json"),Map.of("schema_version",1,"status","COMPLETE_NATIVE_ALLCOOLS_REGION_QUANTIFICATION","manifest",pin(root.resolve("manifest.json"),1048576,check),"analysis_admitted",true,"whole_package_admitted",false));
            verify(root,true,check);unchanged(originals,pins,s,producer,check);Files.delete(root.resolve("INCOMPLETE.json"));return root;
        }catch(IOException|RuntimeException|Error e){if(created)failure(root,stage,e);throw e;}
    }
    private static void unchanged(Map<String,Path> originals,Map<String,Pin> pins,AllcoolsRegionRequest.Spec s,Pin producer,Runnable check)throws IOException {
        for(var entry:originals.entrySet())require(pins.get(entry.getKey()).equals(pin(entry.getValue(),s.capacity().inputBytes(),check)),"Original input changed before admission");
        require(producer.equals(producer()),"Producing JAR changed during calculation");
    }
    static void verify(Path supplied,boolean precommit,Runnable cancellation)throws IOException {
        Path root=supplied.toAbsolutePath().normalize();directory(root);
        require(!Files.exists(root.resolve("FAILURE.json"),LinkOption.NOFOLLOW_LINKS)&&Files.exists(root.resolve("INCOMPLETE.json"),LinkOption.NOFOLLOW_LINKS)==precommit,"Failed/incomplete region quantification capture is not admitted");
        var done=object(json(root.resolve("COMPLETED.json"),1048576));keys(done,"schema_version","status","manifest","analysis_admitted","whole_package_admitted");integer(done.get("schema_version"),1,1);
        require("COMPLETE_NATIVE_ALLCOOLS_REGION_QUANTIFICATION".equals(done.get("status"))&&Boolean.TRUE.equals(done.get("analysis_admitted"))&&Boolean.FALSE.equals(done.get("whole_package_admitted")),"Missing or expanded native analysis admission");
        Pin m=pin(root.resolve("manifest.json"),1048576,cancellation);require(m.equals(strictPin(done.get("manifest"),1048576)),"Saved manifest changed");
        var saved=object(json(root.resolve("manifest.json"),1048576));keys(saved,"schema_version","profile","producer","files","analysis_admitted","whole_package_admitted");integer(saved.get("schema_version"),1,1);
        require(PROFILE.equals(saved.get("profile"))&&Boolean.TRUE.equals(saved.get("analysis_admitted"))&&Boolean.FALSE.equals(saved.get("whole_package_admitted")),"Different or expanded native profile");
        require(producer().equals(strictPin(saved.get("producer"),1000000000L)),"Restore exact producing JAR; do not reseal another executable");
        var s=AllcoolsRegionRequest.read(root.resolve("request.json"),false);Runnable check=bounded(s,cancellation);inventory(root,s,saved.get("files"),precommit,check);
        var input=object(json(root.resolve("source-inputs.json"),1048576));keys(input,"original_counts","pins","complete_original_bytes");
        require(s.counts().equals(input.get("original_counts"))&&Boolean.TRUE.equals(input.get("complete_original_bytes")),"Complete original provenance differs");
        var pins=object(input.get("pins"));keys(pins,"counts.tsv");long total=0;
        for(String name:List.of("counts.tsv")) {Pin identity=pin(root.resolve("inputs/"+name),s.capacity().inputBytes(),check);require(identity.equals(strictPin(pins.get(name),s.capacity().inputBytes())),"Complete original input bytes differ");total=Math.addExact(total,identity.bytes());}
        require(total<=s.capacity().inputBytes(),"Combined saved input bytes exceed capacity");
        require(Files.readString(root.resolve("WARNINGS.md"),StandardCharsets.UTF_8).equals(warnings()),"Saved warning/action text differs");
        require(NextflowJson.json(AllcoolsRegionCompute.run(root,s,true,check)).equals(NextflowJson.json(json(root.resolve("OUTPUT-AUDIT.json"),1048576))),"Saved complete numerical audit differs");
        inventory(root,s,saved.get("files"),precommit,check);require(m.equals(pin(root.resolve("manifest.json"),1048576,check))&&producer().equals(strictPin(saved.get("producer"),1000000000L)),"Manifest or producer changed during replay");
    }
    static Map<String,Object> files(Path root,AllcoolsRegionRequest.Spec s,Runnable check)throws IOException {
        var files=new TreeMap<String,Object>();long reserve=16777216L,total=Math.addExact(Math.addExact(s.capacity().inputBytes(),s.capacity().outputBytes()),reserve),generated=0;
        for(var row:tree(root,32,total,Math.max(s.capacity().inputBytes(),s.capacity().outputBytes())+reserve,check).entrySet()) {
            require(row.getValue().kind().equals("file"),"Linked capture member refused");Pin p=row.getValue().pin();
            if(!row.getKey().startsWith("inputs/")){generated=Math.addExact(generated,p.bytes());require(generated<=s.capacity().outputBytes()+reserve,"Generated files exceed output capacity plus metadata reserve");}
            files.put(row.getKey(),Map.of("bytes",p.bytes(),"sha256",p.sha256()));
        }
        return files;
    }
    private static void inventory(Path root,AllcoolsRegionRequest.Spec s,Object declared,boolean precommit,Runnable check)throws IOException {
        var directories=new HashSet<>(Set.of("","inputs","outputs"));
        try(var paths=Files.walk(root)) {
            var all=paths.limit(65).toList();require(all.size()<=64,"Capture namespace exceeds bounded population");
            for(Path p:all){check.run();if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))require(directories.remove(root.relativize(p).toString().replace('\\','/')),"Extra capture directory");}
        }
        require(directories.isEmpty(),"Missing capture directory");var actual=files(root,s,check);
        var expected=new HashSet<>(Set.of("request.json","WARNINGS.md","inputs/counts.tsv","source-inputs.json","outputs/values.tsv","outputs/pools.tsv","outputs/posterior-counts.tsv","OUTPUT-AUDIT.json","manifest.json","COMPLETED.json"));
        if(precommit)expected.add("INCOMPLETE.json");require(actual.keySet().equals(expected),"Missing/extra complete capture file");
        actual.remove("manifest.json");actual.remove("COMPLETED.json");actual.remove("INCOMPLETE.json");require(NextflowJson.json(actual).equals(NextflowJson.json(declared)),"Complete saved capture byte population differs");
    }
    private static void failure(Path root,String stage,Throwable error) {
        try {Files.deleteIfExists(root.resolve("COMPLETED.json"));if(!Files.exists(root.resolve("FAILURE.json")))writeJson(root.resolve("FAILURE.json"),Map.of("status","FAIL2_NO_COMPLETE_NATIVE_ALLCOOLS_REGION_QUANTIFICATION","stage",stage,"error",error.toString(),"required_action",ACTION,"analysis_admitted",false));}
        catch(IOException suppressed){error.addSuppressed(suppressed);}
    }
    static String warnings() { return "ALLCOOLS REGION QUANTIFICATION: WARNINGS AND REQUIRED ACTION\n\nThis native profile quantifies complete PREPARED raw region counts for one rectangular cell/type/feature dataset. Original ALLCools 1.0.8 count_matrix/dataset.py supplies the strict binomial survival and binary16 score semantics. It does not read ALLC, Tabix or Zarr or construct regions. Each input record is already aggregated for the declared region and methylation pattern. Overlapping regions/patterns may legitimately count a call more than once; they must not be deduplicated or interpreted as a genomic mass balance. Methylation pattern strings are literal axis labels here, not executed IUPAC queries. Supply real preparation provenance and scientifically chosen region axes/cutoffs; paper versions or defaults are not inferred.\n\nThe exact TSV header is cell, mc_type, feature, mc, cov. Every cell contains the same ordered unique methylation types and every group the same complete ordered unique features. Entire axes are consumed in rectangular block order, with no sorting, intersection, renaming, dropped rows or imputation. UTF-8 is strict with optional header BOM and CR/LF record delimiters. Fields are literal, without CSV quoting. Counts are canonical nonnegative int64 integers with mc <= cov. Per-group integer summation overflow fails explicitly; NumPy int64 overflow is not reproduced or repaired. Capacity or cancellation failures admit no complete result. Each complete region axis must fit in memory. Wall checks are cooperative; this is not a heap or immediate interruption guarantee.\n\nCount outputs saturate mc and cov INDEPENDENTLY at 4294967295 (uint32 maximum). This can change a methylation ratio, including making a formerly partial region appear fully methylated. Exact raw counts remain in inputs/counts.tsv and outputs/values.tsv, and saturation counters are recorded. outputs/posterior-counts.tsv contains the SATURATED counts in the exact five-column format accepted by allcools-posterior. Scores are calculated from RAW UNSATURATED counts. Do not recompute scores from the saturated count table. Posterior inference requires its own explicit complete native request and settings, and may have degenerate priors; inspect its separate audit and warnings.\n\nFor each cell/type, p=sum(raw_mc)/(sum(raw_cov)+1e-6). The hypo-score before thresholding is P[Binomial(raw_cov,p) > raw_mc], except raw_cov <= raw_mc gives zero, including 0/0. This is strict survival with no one-count continuity adjustment. The survival probability is rounded to binary16 first. Hyper-scores then compute 1 minus that rounded value in binary16. Each cutoff is also rounded to binary16 under NumPy 1.23.5 weak scalar promotion on [0,1]; values below it become zero. Equality is retained. Hyper is computed before hypo cutoff, not from the thresholded hypo output. Uncovered regions therefore have hypo-score 0 and hyper-score 1 even with positive cutoffs; this is source behavior, not evidence of hypermethylation. Zero coverage and hyper-one counts are prominent in OUTPUT-AUDIT.json.\n\nBinomial probabilities use JDistlib, independently compared with the original SciPy-based functions on retained synthetic complete axes. The stored score grid is binary16, emitted as exact decimal binary64 representations. Native and SciPy probability evaluation may differ near rounding boundaries or for untested extremes; this profile is not a universal bitwise equivalence claim. Nonfinite/out-of-domain native probabilities fail rather than being replaced. Raw integer values and sums remain exact until source probability conversion to float64; counts beyond 2^53 lose resolution in that conversion, as in the original. No p-value rescaling, pseudocount fitting, or probability repair is applied.\n\nSaved verification checks all original bytes, exact file/directory population, producing JAR, warnings, and every count/score/pool/export value through full numerical replay. Original absolute paths are provenance and need not exist on the replay host. Hashes establish integrity, not authenticity or biological validity. Native prepared quantification and the existing posterior method do not establish an ALLC dataset adapter, clustering, integration, consensus, pseudobulk merge, BaseDS or DMR workflow. Whole-package and whole-paper reproduction remain unadmitted.\nRequired action: inspect WARNINGS.md, OUTPUT-AUDIT.json and outputs/pools.tsv. Zero-coverage regions have hyper-score 1; uint32 count saturation can change ratios, while scores use raw counts. Require COMPLETED.json and allcools-region-quantify --verify with the exact producing JAR. Keep complete inputs, producer and failed attempts. Correct named invalid input, overflow, dimensions, resource, cancellation or consistency failures and retry NEW paths. Missing or forbidden prerequisites give SKIP3 and skip dependent analyses. No upstream software is needed for these prepared-count calculations. ALLC/Tabix/Zarr construction and full author workflows remain pending; do not refresh old seals.\n"; }
}
