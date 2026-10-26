/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.FollowupSupport.*;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit original exact/randomized PCA, with separate AF and fitted populations. */
final class PlinkPcaCli {
    private PlinkPcaCli() { }
    static final Set<String> ALLOWED=Set.of("task","engine","home","bfile","samples","variants","out","options-file","genome-build","chromosome-scope","unresolved-alleles",
        "algorithm","pca-missing","components","seed","missing-genotypes","minimum-reference-calls","maximum-relative-eigen-residual","maximum-quality-products",
        "threads","memory-mib","timeout-seconds","maximum-samples","maximum-variants","maximum-cells","maximum-metadata-mib","maximum-retained-mib","maximum-line-bytes","maximum-bed-mib","maximum-samples-mib","maximum-report-mib","maximum-components");
    private static final List<String> CANONICAL=List.of("eigenvalues.tsv","eigenvectors.tsv","variant-weights.tsv");
    private static final String RECOVERY="Required action: read WARNINGS.md,session.yaml,variant-reference.tsv,eigen-quality.tsv when present,and retained upstream logs. Correct the named panel,independent sample masks,component count,explicit approximation tolerance or resource limits;rerun to a new directory. Review low-frequency markers with a declared QC/panel selection;this command never adds --grm-maf yes-really/--bad-freqs,changes the algorithm or silently removes markers. "+PlinkRuntime.setup();

    static int run(PlinkToolsCli.Parsed parsed,PrintStream output,PrintStream error) {
        var options=parsed.options();try {
            PlinkToolsCli.validateOptions(options,ALLOWED);if(!required(options,"task").equals("pca"))throw new IllegalArgumentException("Require --task pca");
            for(String key:List.of("bfile","samples","variants","out"))required(options,key);
            String engine=choice(options,"engine",Set.of("plink1","plink2")),algorithm=choice(options,"algorithm",Set.of("exact","approx")),missing=choice(options,"pca-missing",Set.of("pairwise","mean"));
            choice(options,"chromosome-scope",Set.of("autosomes"));choice(options,"genome-build",Set.of("GRCh37","GRCh38","unspecified"));
            String unresolved=choice(options,"unresolved-alleles",Set.of("error","exclude")),calls=choice(options,"missing-genotypes",Set.of("error","upstream"));
            if(engine.equals("plink1")&&(!algorithm.equals("exact")||!missing.equals("pairwise")))throw new IllegalArgumentException("This PLINK1 profile supports exact pairwise PCA only;select PLINK2 explicitly for mean-imputed/randomized PCA");
            if(algorithm.equals("approx")&&!missing.equals("mean"))throw new IllegalArgumentException("Original randomized PCA always mean-imputes;declare --pca-missing mean");
            int seed=algorithm.equals("approx")?integer(options,"seed",null):0;
            if(algorithm.equals("exact")&&options.containsKey("seed"))throw new IllegalArgumentException("--seed is only admitted for explicitly randomized PCA");
            double tolerance=missing.equals("mean")?Double.parseDouble(required(options,"maximum-relative-eigen-residual")):Double.NaN;
            if(missing.equals("pairwise")&&options.containsKey("maximum-relative-eigen-residual"))throw new IllegalArgumentException("The streaming mean-GRM residual gate does not apply to pairwise PCA;omit the incompatible tolerance");
            if(missing.equals("mean")&&(!Double.isFinite(tolerance)||tolerance<=0||tolerance>=1))throw new IllegalArgumentException("Require finite --maximum-relative-eigen-residual in (0,1)");
            int components=integer(options,"components",null),maximumComponents=integer(options,"maximum-components",100),minimum=integer(options,"minimum-reference-calls",null);
            if(minimum<2||components>maximumComponents||maximumComponents>1000)throw new IllegalArgumentException("Require minimum-reference-calls>=2 and components<=maximum-components<=1000");
            int threads=integer(options,"threads",1),memory=integer(options,"memory-mib",engine.equals("plink1")?128:640),timeout=integer(options,"timeout-seconds",3600);
            if(threads>256||timeout>86400||memory<(engine.equals("plink1")?64:640))throw new IllegalArgumentException("Invalid threads/timeout or engine-specific memory bound");
            var bounds=new PlinkGlmInputs.Bounds(integer(options,"maximum-samples",100000),integer(options,"maximum-variants",2000000),positive(options,"maximum-cells",500000000L),mib(options,"maximum-metadata-mib",512),mib(options,"maximum-retained-mib",512),integer(options,"maximum-line-bytes",16384),mib(options,"maximum-bed-mib",65536),mib(options,"maximum-samples-mib",64));
            if(bounds.line()<64)throw new IllegalArgumentException("--maximum-line-bytes must be at least64");
            long reportBytes=mib(options,"maximum-report-mib",4096),qualityProducts=positive(options,"maximum-quality-products",2000000000L);
            var reportLimits=new PlinkPcaReports.Limits(bounds.samples(),bounds.variants(),maximumComponents,bounds.retained(),reportBytes,bounds.line());
            String home=options.getOrDefault("home",System.getenv("JLINALG_PLINK_HOME"));
            if(home!=null&&!home.isBlank()){Path h=PlinkToolsCli.resolved(Path.of(home)),d=PlinkToolsCli.resolved(Path.of(options.get("out")));if(d.startsWith(h)||h.startsWith(d))throw new IllegalArgumentException("PCA output must be outside the sealed runtime home");}
            int[] code={0};
            externalTransaction(options,"plink-original-genetic-PCA",(directory,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();session.put("scope","Original pinned biallelic autosomal hard-call PCA on an explicit panel and independent fitted/frequency sample masks. No automatic QC,LD pruning,unrelatedness/ancestry certification,projection of new samples,whole-tool equivalence or historical-paper replay.");session.put("PCA_policy",new LinkedHashMap<>(options));
                try {
                    if(home==null||home.isBlank())throw new PlinkRuntime.Unavailable("No --home or JLINALG_PLINK_HOME supplied",null);
                    Path upstream=directory.resolve("upstream");Files.createDirectory(upstream);var runtime=new PlinkRuntime(Path.of(home),engine,upstream,session);
                    var prepared=PlinkPcaInputs.prepare(Path.of(options.get("bfile")).toAbsolutePath().normalize(),Path.of(options.get("samples")).toAbsolutePath().normalize(),Path.of(options.get("variants")).toAbsolutePath().normalize(),upstream,unresolved,calls,minimum,bounds);
                    var hashes=new LinkedHashMap<>(prepared.bed().inputHashes());hashes.put("PCA_samples",prepared.sampleHash());hashes.put("PCA_panel",prepared.panelHash());manifest.put("input_sha256",hashes);session.put("staged_input_sha256",prepared.bed().stagedHashes());session.put("staged_mask_sha256",prepared.maskHashes());
                    if(components>prepared.basis().size())throw new IOException("Requested PCA components exceed fitted sample count");
                    if(missing.equals("mean"))PlinkPcaQuality.admit(prepared.basis().size(),prepared.panel().size(),components,qualityProducts,bounds.retained());
                    long workspace=workspace(prepared,components,algorithm);session.put("upstream_primary_allocation_estimate_bytes",workspace);session.put("memory_scope","Primary allocation estimate only;--memory is original main workspace,not a strict process RSS limit.Native-library/operating-system overhead is additional.");
                    if(workspace>Math.multiplyExact((long)memory,1048576))throw new IOException("PCA primary allocation estimate exceeds declared --memory-mib;review components/panel/basis or increase explicit memory");
                    Files.copy(upstream.resolve("variant-reference.tsv"),directory.resolve("variant-reference.tsv"));samples(prepared,directory);
                    snapshotRecipe(parsed,directory);
                    var frequency=command(engine,threads,memory,"pca-frequency.keep");frequency.addAll(List.of("--nonfounders","--freq","counts","--out","frequency"));runtime.execute(frequency,upstream,"frequency",timeout);
                    Path frequencyPath=upstream.resolve("frequency"+(engine.equals("plink1")?".frq.counts":".acount"));
                    var frequencies=PlinkQcReports.frequencies(frequencyPath,prepared.panel(),bounds.variants(),reportBytes,bounds.line());
                    for(String id:prepared.panel()) {
                        var row=frequencies.get(id);var variant=prepared.bed().variants().get(id);var known=prepared.calls().get(id);
                        long first=row.first().equals(variant.a1())?row.firstCount():row.second().equals(variant.a1())?row.secondCount():-1;
                        if(!Set.of(row.first(),row.second()).equals(Set.of(variant.a1(),variant.a2()))||first!=known.countedAlleles()||row.observations()!=2L*known.referenceCalled())throw new IOException("Original frequency counts/alleles differ from explicit reference hard calls at "+variant.original());
                    }
                    String frequencySha=hash(frequencyPath);session.put("frequency_report_sha256",frequencySha);PlinkPcaInputs.verify(prepared,upstream);
                    var pca=command(engine,threads,memory,"pca-basis.keep");pca.addAll(List.of("--read-freq",frequencyPath.getFileName().toString(),"--pca",Integer.toString(components)));
                    if(engine.equals("plink1"))pca.addAll(List.of("header","tabs","var-wts"));else{if(algorithm.equals("approx"))pca.add("approx");else if(missing.equals("mean"))pca.add("meanimpute");pca.addAll(List.of("allele-wts","scols=fid","vcols=chrom,ref,alt"));}
                    if(algorithm.equals("approx"))pca.addAll(List.of("--seed",Integer.toString(seed)));pca.addAll(List.of("--out","pca"));runtime.execute(pca,upstream,"pca",timeout);
                    if(!frequencySha.equals(hash(frequencyPath)))throw new IOException("Loaded PCA frequency report changed during execution");PlinkPcaInputs.verify(prepared,upstream);
                    var basis=PlinkPcaReports.basis(upstream.resolve("pca.eigenval"),upstream.resolve("pca.eigenvec"),prepared.basis(),components,reportLimits);
                    session.put("maximum_printed_orthogonality_error",basis.maximumOrthogonalityError());session.put("fitted_samples",prepared.basis().size());session.put("frequency_reference_samples",prepared.reference().size());session.put("panel_markers",prepared.panel().size());
                    if(missing.equals("mean")) {
                        var quality=PlinkPcaQuality.mean(prepared,basis,qualityProducts,bounds.retained(),tolerance);session.put("maximum_observed_relative_eigen_residual",quality.maximumRelativeResidual());session.put("mean_GRM_trace",quality.trace());
                        try(var writer=Files.newBufferedWriter(directory.resolve("eigen-quality.tsv"))) {
                            writer.write("component\tobserved_relative_residual\tprinted_value_uncertainty_envelope\trequested_maximum_relative_residual\taccepted\n");
                            for(var row:quality.components())writer.write(row.ordinal()+"\t"+row.relativeResidual()+"\t"+row.printedValueEnvelope()+"\t"+tolerance+"\t"+row.accepted()+"\n");
                        }
                        if(!quality.accepted())throw new IOException("PCA mean-GRM eigen-residual exceeds the explicit accuracy tolerance;review eigen-quality.tsv.No exact/alternate-seed fallback was attempted");
                        warnings.add("The requested relative Ritz-residual tolerance was applied directly to the reported PCs.Printed-value uncertainty is diagnostic only and never widens the threshold.This check does not establish exact top-K subspace recovery,an unreported eigengap or biological ancestry. Required action: review eigen-quality.tsv,tolerance,PC stability and study design before use.");
                    } else warnings.add("Pairwise missingness can yield an indefinite GRM.Report dimensions,positive requested eigenvalues and printed orthogonality were checked;no streaming mean-GRM residual or unreported component-boundary gap is claimed.Required action: review original logs,missingness and PC stability;do not substitute an ordinary SVD or treat linear projection as identical fitted coordinates.");
                    publish(prepared,basis,engine,components,reportLimits,upstream,directory);
                    PlinkPcaInputs.verify(prepared,upstream);runtime.verify();verifyRecipe(parsed);
                    warnings.add("Original-program signs/scales and actual allele rows are retained.PLINK1 rows are A1/A2 variant weights;PLINK2 rows are REF/ALT allele weights with A1 identifying the counted allele.Native GeneticPrincipalComponents projection coefficients use different conventions.Required action: preserve engine,allele columns and scaling together;do not interchange weight formats.");
                    warnings.add("Fitted and frequency samples are exact independent FID/IID masks.Pedigree founders need not be unrelated;no PCA covariates,LD panel,QC or biological population labels were inferred.Required action: review sample-reference.tsv,variant-reference.tsv and all upstream logs.Only fitted samples have reported coordinates;new-sample projection is a separate workflow.");
                    session.put("report_sha256",Map.of("eigenval",hash(upstream.resolve("pca.eigenval")),"eigenvec",hash(upstream.resolve("pca.eigenvec")),"weights",hash(upstream.resolve(engine.equals("plink1")?"pca.eigenvec.var":"pca.eigenvec.allele"))));
                    manifest.put("analysis_status","passed-with-review-warnings");status(directory,"passed-with-review-warnings");Files.writeString(directory.resolve("WARNINGS.md"),"# ORIGINAL PCA — REVIEW BEFORE USE\n\n"+String.join("\n\n",warnings)+"\n\n"+RECOVERY+"\n");
                } catch(Exception failure) {
                    code[0]=failure instanceof PlinkRuntime.Unavailable?3:2;String state=code[0]==3?"skipped-dependency-unavailable":"failed";manifest.put("analysis_status",state);manifest.put("error",failure.toString());session.put("error",failure.toString());status(directory,state);
                    for(String name:CANONICAL)if(Files.exists(directory.resolve(name))){Files.createDirectories(directory.resolve("partial"));Files.move(directory.resolve(name),directory.resolve("partial").resolve(name));}
                    Files.writeString(directory.resolve("WARNINGS.md"),"# ORIGINAL PCA "+(code[0]==3?"SKIPPED":"FAILED — DO NOT USE")+"\n\n"+failure+"\n\n"+String.join("\n\n",warnings)+"\n\n"+RECOVERY+"\n");
                }
                Files.writeString(directory.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));
            });
            if(code[0]!=0)error.println("ORIGINAL PCA "+(code[0]==3?"SKIPPED":"FAILED")+": see "+Path.of(options.get("out")).resolve("WARNINGS.md")+" for the cause and required action.");
            else{output.println("Original PCA completed: "+options.get("out"));error.println("PCA WARNINGS: read "+Path.of(options.get("out")).resolve("WARNINGS.md")+" before use.");}return code[0];
        } catch(Exception failure){error.println("ORIGINAL PCA NOT STARTED: "+failure.getMessage()+". "+RECOVERY+" Review plink-tools --task pca --help.");return 2;}
    }
    private static ArrayList<String> command(String engine,int threads,int memory,String keep) {
        var result=new ArrayList<>(List.of("--bfile","input","--keep",keep,"--extract","pca-panel.extract","--autosome","--threads",Integer.toString(threads),"--memory",Integer.toString(memory)));
        if(engine.equals("plink1"))result.addAll(List.of("--keep-allele-order","--allow-no-sex"));else result.add("--no-psam-pheno");return result;
    }
    private static long workspace(PlinkPcaInputs.Prepared p,int k,String algorithm) {
        long n=p.basis().size(),m=p.panel().size();if(algorithm.equals("exact"))return Math.multiplyExact(Math.multiplyExact(n,n),8L);
        long factor=Math.multiplyExact(Math.multiplyExact((long)k,k+1L),16L),samples=Math.multiplyExact(n,factor),variants=Math.multiplyExact(m,factor);
        return Math.addExact(Math.addExact(Math.addExact(samples,variants),Math.max(samples,variants)),Math.multiplyExact(5760L,n));
    }
    private static void publish(PlinkPcaInputs.Prepared p,PlinkPcaReports.Basis basis,String engine,int k,PlinkPcaReports.Limits limits,Path upstream,Path out) throws IOException {
        String suffix=engine.equals("plink1")?"pca.eigenvec.var":"pca.eigenvec.allele";
        try(var writer=Files.newBufferedWriter(out.resolve("variant-weights.tsv"))) {
            writer.write("variant\tupstream_variant\tchromosome\tallele_order_convention\tfirst_allele\tsecond_allele\tcounted_allele");for(int c=1;c<=k;c++)writer.write("\tPC"+c);writer.write("\n");
            PlinkPcaReports.weights(upstream.resolve(suffix),engine,PlinkPcaInputs.variants(p),k,limits,row->{writer.write(p.bed().variants().get(row.variant()).original()+"\t"+row.variant()+"\t"+row.chromosome()+"\t"+(engine.equals("plink1")?"PLINK1_A1_A2":"PLINK2_REF_ALT")+"\t"+row.firstAllele()+"\t"+row.secondAllele()+"\t"+row.countedAllele());for(var value:row.components())writer.write("\t"+value.literal());writer.write("\n");});
        }
        try(var writer=Files.newBufferedWriter(out.resolve("eigenvectors.tsv"))) {
            writer.write("fid\tiid");for(int c=1;c<=k;c++)writer.write("\tPC"+c);writer.write("\n");
            for(var row:basis.samples().values()){var id=p.bed().samples().get(row.id()).original();writer.write(id.fid()+"\t"+id.iid());for(var value:row.components())writer.write("\t"+value.literal());writer.write("\n");}
        }
        try(var writer=Files.newBufferedWriter(out.resolve("eigenvalues.tsv"))){writer.write("component\teigenvalue\n");for(int c=0;c<k;c++)writer.write((c+1)+"\t"+basis.eigenvalues().get(c).literal()+"\n");}
    }
    private static void samples(PlinkPcaInputs.Prepared p,Path out) throws IOException {
        try(var writer=Files.newBufferedWriter(out.resolve("sample-reference.tsv"))){writer.write("fid\tiid\tupstream_fid\tupstream_iid\trecorded_FAM_founder\tfitted\tfrequency_reference\n");for(var s:p.bed().samples().values())writer.write(s.original().fid()+"\t"+s.original().iid()+"\t"+s.staged().fid()+"\t"+s.staged().iid()+"\t"+s.founder()+"\t"+p.basis().contains(s.staged())+"\t"+p.reference().contains(s.staged())+"\n");}
    }
    private static void verifyRecipe(PlinkToolsCli.Parsed p)throws Exception {if(p.options().containsKey("options-file")&&!p.recipeSha256().equals(hash(Path.of(p.options().get("options-file")))))throw new IOException("PCA recipe changed during analysis");}
    private static void snapshotRecipe(PlinkToolsCli.Parsed p,Path out)throws Exception {verifyRecipe(p);if(p.options().containsKey("options-file")){Files.copy(Path.of(p.options().get("options-file")),out.resolve("options-input.json"));if(!p.recipeSha256().equals(hash(out.resolve("options-input.json"))))throw new IOException("PCA recipe snapshot differs from parsed input");}}
    private static void status(Path out,String value)throws IOException{Files.writeString(out.resolve("analysis-status.json"),"{\"status\":\""+value+"\"}\n");}
    private static String choice(Map<String,String> o,String key,Set<String> allowed){String value=required(o,key);if(!allowed.contains(value))throw new IllegalArgumentException("--"+key+" must be one of "+allowed);return value;}
    private static long positive(Map<String,String> o,String key,Long fallback){long value=Long.parseLong(o.containsKey(key)?o.get(key):fallback==null?required(o,key):fallback.toString());if(value<1)throw new IllegalArgumentException("--"+key+" must be positive");return value;}
    private static int integer(Map<String,String> o,String key,Integer fallback){return Math.toIntExact(positive(o,key,fallback==null?null:fallback.longValue()));}
    private static long mib(Map<String,String> o,String key,long fallback){return Math.multiplyExact(positive(o,key,fallback),1048576);}
    static String help(){return """
        plink-tools --task pca --engine plink1|plink2 --home SEALED_PRIVATE_HOME
          --bfile BED_PREFIX --samples PCA_SAMPLES.tsv --variants DECLARED_PANEL.txt
          --out NEW_DIRECTORY --genome-build GRCh37|GRCh38|unspecified
          --chromosome-scope autosomes --unresolved-alleles error|exclude
          --algorithm exact|approx --pca-missing pairwise|mean --components INTEGER
          --missing-genotypes error|upstream --minimum-reference-calls INTEGER_AT_LEAST_2
        PCA sample TSV: fid,iid,fit,frequency_reference (tabs);every source FAM pair
        exactly once,independent yes/no roles.No founder/unrelatedness selection inferred.
        Panel:one exact unique original ID per line;only resolved autosomal markers.
        PLINK1 supports exact/pairwise;PLINK2 exact/pairwise,exact/mean,approx/mean.
        Approx requires explicit positive --seed;exact forbids it.Mean modes require
        --maximum-relative-eigen-residual FRACTION_IN_0_1 and stream declared-GRM
        residuals with --maximum-quality-products (default2billion) and retained-byte
        bounds.Pairwise mode checks reported orthogonality/format,not a mean-GRM
        residual or unreported component-boundary gap.No algorithm/seed/QC fallback.
        Default upstream GRM low-MAF guard is preserved;no override/bad-freqs inferred.
        Original eigenvectors and allele weights preserve raw signs/scales;only fitted
        samples get coordinates.New-sample projection is a separate workflow.
        Review WARNINGS.md/session.yaml and diagnostic/reference tables before use.
        Unavailable/changed/forbidden runtime=>skip exit3;input/process/quality=>exit2.
        Failed canonical results are quarantined under partial/.Analysis never installs.
        Common runtime/resource/options-file flags match plink-tools QC.
        """;}
}
