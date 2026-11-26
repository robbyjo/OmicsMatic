/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;

/** Complete prepared-input capture with exact producer and portable full numerical replay. */
final class AllcoolsPosteriorBundle {
    static final String PROFILE="native-allcools-prepared-posterior-v1";
    static final String ACTION="Required action: inspect WARNINGS.md, OUTPUT-AUDIT.json and outputs/priors.tsv, especially zero coverage, nonfinite fitted priors, NaN/Inf values and xarray NaN-to-clip replacements. Require COMPLETED.json and allcools-posterior --verify using the exact producing JAR. Keep complete inputs, producers and failed attempts. Correct named malformed input, dimension, capacity, cancellation or consistency failures and retry NEW paths. For absent or forbidden prerequisites, SKIP3 the dependent analysis. This command needs no upstream runtime; raw ALLC/Zarr preparation, clustering, label transfer and DMR testing remain separate. Do not repair degenerate priors, impute counts, change representations or refresh old seals.";
    static final class Unavailable extends IOException {private static final long serialVersionUID=1L;Unavailable(String value){super(value);}}
    private AllcoolsPosteriorBundle() {}
    static Pin producer()throws IOException {
        try {
            Path p=Path.of(AllcoolsPosteriorBundle.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            if(!p.getFileName().toString().endsWith(".jar"))throw new Unavailable("Build and retain the complete executable producing JAR; class-directory capture is unavailable");
            return pin(p,1000000000L,()->{});
        }catch(java.net.URISyntaxException|NullPointerException e){throw new Unavailable("Exact producing JAR unavailable; restore it before dependent analysis");}
    }
    private static Pin strictPin(Object raw,long maximum)throws IOException { keys(object(raw),"bytes","sha256");return fingerprint(raw,maximum); }
    static Runnable bounded(AllcoolsPosteriorRequest.Spec s,Runnable cancellation) {
        long start=System.nanoTime();return ()->{cancellation.run();if(Thread.currentThread().isInterrupted()||(System.nanoTime()-start)/1000000>s.capacity().wallMillis())throw new IllegalStateException("Cooperative wall limit/cancellation exceeded; no complete posterior result");};
    }
    static Path capture(Path supplied,AllcoolsPosteriorRequest.Spec s,Runnable cancellation)throws IOException {
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
            stage="COMPLETE_PREPARED_POSTERIOR";writeJson(root.resolve("OUTPUT-AUDIT.json"),AllcoolsPosteriorCompute.run(root,s,false,check));
            unchanged(originals,pins,s,producer,check);stage="FINALIZE_AND_REPLAY";var files=files(root,s,check);files.remove("INCOMPLETE.json");
            writeJson(root.resolve("manifest.json"),Map.of("schema_version",1,"profile",PROFILE,"producer",producer,"files",files,"analysis_admitted",true,"whole_package_admitted",false));
            writeJson(root.resolve("COMPLETED.json"),Map.of("schema_version",1,"status","COMPLETE_NATIVE_ALLCOOLS_PREPARED_POSTERIOR","manifest",pin(root.resolve("manifest.json"),1048576,check),"analysis_admitted",true,"whole_package_admitted",false));
            verify(root,true,check);unchanged(originals,pins,s,producer,check);Files.delete(root.resolve("INCOMPLETE.json"));return root;
        }catch(IOException|RuntimeException|Error e){if(created)failure(root,stage,e);throw e;}
    }
    private static void unchanged(Map<String,Path> originals,Map<String,Pin> pins,AllcoolsPosteriorRequest.Spec s,Pin producer,Runnable check)throws IOException {
        for(var entry:originals.entrySet())require(pins.get(entry.getKey()).equals(pin(entry.getValue(),s.capacity().inputBytes(),check)),"Original input changed before admission");
        require(producer.equals(producer()),"Producing JAR changed during calculation");
    }
    static void verify(Path supplied,boolean precommit,Runnable cancellation)throws IOException {
        Path root=supplied.toAbsolutePath().normalize();directory(root);
        require(!Files.exists(root.resolve("FAILURE.json"),LinkOption.NOFOLLOW_LINKS)&&Files.exists(root.resolve("INCOMPLETE.json"),LinkOption.NOFOLLOW_LINKS)==precommit,"Failed/incomplete posterior capture is not admitted");
        var done=object(json(root.resolve("COMPLETED.json"),1048576));keys(done,"schema_version","status","manifest","analysis_admitted","whole_package_admitted");integer(done.get("schema_version"),1,1);
        require("COMPLETE_NATIVE_ALLCOOLS_PREPARED_POSTERIOR".equals(done.get("status"))&&Boolean.TRUE.equals(done.get("analysis_admitted"))&&Boolean.FALSE.equals(done.get("whole_package_admitted")),"Missing or expanded native analysis admission");
        Pin m=pin(root.resolve("manifest.json"),1048576,cancellation);require(m.equals(strictPin(done.get("manifest"),1048576)),"Saved manifest changed");
        var saved=object(json(root.resolve("manifest.json"),1048576));keys(saved,"schema_version","profile","producer","files","analysis_admitted","whole_package_admitted");integer(saved.get("schema_version"),1,1);
        require(PROFILE.equals(saved.get("profile"))&&Boolean.TRUE.equals(saved.get("analysis_admitted"))&&Boolean.FALSE.equals(saved.get("whole_package_admitted")),"Different or expanded native profile");
        require(producer().equals(strictPin(saved.get("producer"),1000000000L)),"Restore exact producing JAR; do not reseal another executable");
        var s=AllcoolsPosteriorRequest.read(root.resolve("request.json"),false);Runnable check=bounded(s,cancellation);inventory(root,s,saved.get("files"),precommit,check);
        var input=object(json(root.resolve("source-inputs.json"),1048576));keys(input,"original_counts","pins","complete_original_bytes");
        require(s.counts().equals(input.get("original_counts"))&&Boolean.TRUE.equals(input.get("complete_original_bytes")),"Complete original provenance differs");
        var pins=object(input.get("pins"));keys(pins,"counts.tsv");long total=0;
        for(String name:List.of("counts.tsv")) {Pin identity=pin(root.resolve("inputs/"+name),s.capacity().inputBytes(),check);require(identity.equals(strictPin(pins.get(name),s.capacity().inputBytes())),"Complete original input bytes differ");total=Math.addExact(total,identity.bytes());}
        require(total<=s.capacity().inputBytes(),"Combined saved input bytes exceed capacity");
        require(Files.readString(root.resolve("WARNINGS.md"),StandardCharsets.UTF_8).equals(warnings()),"Saved warning/action text differs");
        require(NextflowJson.json(AllcoolsPosteriorCompute.run(root,s,true,check)).equals(NextflowJson.json(json(root.resolve("OUTPUT-AUDIT.json"),1048576))),"Saved complete numerical audit differs");
        inventory(root,s,saved.get("files"),precommit,check);require(m.equals(pin(root.resolve("manifest.json"),1048576,check))&&producer().equals(strictPin(saved.get("producer"),1000000000L)),"Manifest or producer changed during replay");
    }
    static Map<String,Object> files(Path root,AllcoolsPosteriorRequest.Spec s,Runnable check)throws IOException {
        var files=new TreeMap<String,Object>();long reserve=16777216L,total=Math.addExact(Math.addExact(s.capacity().inputBytes(),s.capacity().outputBytes()),reserve),generated=0;
        for(var row:tree(root,32,total,Math.max(s.capacity().inputBytes(),s.capacity().outputBytes())+reserve,check).entrySet()) {
            require(row.getValue().kind().equals("file"),"Linked capture member refused");Pin p=row.getValue().pin();
            if(!row.getKey().startsWith("inputs/")){generated=Math.addExact(generated,p.bytes());require(generated<=s.capacity().outputBytes()+reserve,"Generated files exceed output capacity plus metadata reserve");}
            files.put(row.getKey(),Map.of("bytes",p.bytes(),"sha256",p.sha256()));
        }
        return files;
    }
    private static void inventory(Path root,AllcoolsPosteriorRequest.Spec s,Object declared,boolean precommit,Runnable check)throws IOException {
        var directories=new HashSet<>(Set.of("","inputs","outputs"));
        try(var paths=Files.walk(root)) {
            var all=paths.limit(65).toList();require(all.size()<=64,"Capture namespace exceeds bounded population");
            for(Path p:all){check.run();if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))require(directories.remove(root.relativize(p).toString().replace('\\','/')),"Extra capture directory");}
        }
        require(directories.isEmpty(),"Missing capture directory");var actual=files(root,s,check);
        var expected=new HashSet<>(Set.of("request.json","WARNINGS.md","inputs/counts.tsv","source-inputs.json","outputs/values.tsv","outputs/priors.tsv","OUTPUT-AUDIT.json","manifest.json","COMPLETED.json"));
        if(precommit)expected.add("INCOMPLETE.json");require(actual.keySet().equals(expected),"Missing/extra complete capture file");
        actual.remove("manifest.json");actual.remove("COMPLETED.json");actual.remove("INCOMPLETE.json");require(NextflowJson.json(actual).equals(NextflowJson.json(declared)),"Complete saved capture byte population differs");
    }
    private static void failure(Path root,String stage,Throwable error) {
        try {Files.deleteIfExists(root.resolve("COMPLETED.json"));if(!Files.exists(root.resolve("FAILURE.json")))writeJson(root.resolve("FAILURE.json"),Map.of("status","FAIL2_NO_COMPLETE_NATIVE_ALLCOOLS_POSTERIOR","stage",stage,"error",error.toString(),"required_action",ACTION,"analysis_admitted",false));}
        catch(IOException suppressed){error.addSuppressed(suppressed);}
    }
    static String warnings() { return "ALLCOOLS PREPARED POSTERIOR: WARNINGS AND REQUIRED ACTION\n\nThis native profile implements ALLCools 1.0.8 calculate_posterior_mc_frac and the numerical transform in MCDS.add_m_value for complete prepared counts. It uses float64 contiguous feature-axis reductions for each cell and methylation type. It does not infer an author version, layout, reference, region set, count preparation or settings. NumPy and xarray are explicit comparison branches for prepared two-dimensional per-type arrays; arbitrary xarray tensor layouts, broadcasting, Dask reduction trees and lazy NetCDF writing are separate.\n\nThe TSV header is cell, mc_type, feature, mc, cov. Every declared cell contains the same ordered methylation types; every group contains the same complete ordered unique features. All cells and count records are consumed in declared rectangular block order. Counts must be literal uint32 integers with 0 <= mc <= cov. This is an explicit prepared-count subset of the upstream API. No sorting, intersection, filtering, count saturation, renaming, imputation, pseudocount fitting or dropping is performed. UTF-8 is strict, with an optional header BOM and CR/LF record delimiters. Quoting is literal. Blank records, unknown fields, invalid UTF-8, missing/extra rows and noncanonical integer spellings fail. Exact original bytes are captured.\n\nRaw 0/0 fractions are NaN and excluded by nanmean/nanvar with population variance (ddof=0). Moment-based alpha and beta are used without repairs, including zero/negative/nonfinite parameters. A zero-coverage region gives normalized fraction 1 only when the fitted prior supports that algebra; this is not guaranteed for degenerate priors. Source-defined NaN/Inf markers remain in raw/posterior/prior outputs and are counted explicitly. A complete calculation with such markers does not establish that its fitted priors are scientifically usable.\n\nNormalization of posterior fractions divides by alpha/(alpha+beta). Clipping is applied only when normalization is enabled. NumPy replaces values > clip and preserves NaN. Xarray uses where(value < clip, clip): NaN becomes the clipping value. These replacements are separately counted. A clipped finite value can therefore originate from a NaN posterior; inspect both columns and the priors. M values first use unnormalized, unclipped posterior fractions, then log2((fraction+alpha)/(1-fraction+alpha)); optional per-cell normalization divides by the NaN-skipping mean of M values. The M regularization alpha is explicit and distinct from the fitted Beta alpha.\n\nCapacity limits bound complete input/output bytes, declared dimensions and cooperative wall/cancellation checks; they are not heap or immediate interruption guarantees. Each group needs its complete feature axis in memory. Resource or cancellation failure admits no complete result. StrictMath-based native log2 may differ by rounding from NumPy. Normalized M values are refused when a finite mean is too close to zero: abs(mean_M) must exceed 32*ulp(max_abs_finite_M)/1e-11. This conservative admission guard prevents observed rounding amplification and does not repair or rescale the source result. Source NaN/Inf cases outside that finite-divisor guard remain explicit markers. Choose unnormalized M values explicitly if scientifically appropriate; no automatic fallback occurs. Source comparisons preserve all reference values and declare both matches and numerical refusals. Saved replay recomputes every value and prior and requires byte-identical canonical derived outputs using the exact producing JAR. Original paths are provenance only during replay. Integrity hashes do not establish authenticity or biological validity.\n\nNo upstream runtime is downloaded, installed or executed by this command. Dataset construction, ALLC/Tabix/Zarr IO, feature selection, clustering, consensus, pseudobulk merging, SeuratIntegration, RNA/MERFISH transfer, BaseDS and DMS/DMR workflows are separate. Whole-package, whole-paper and full-author-workflow reproduction is not admitted.\n\n"+ACTION+"\n"; }
}
