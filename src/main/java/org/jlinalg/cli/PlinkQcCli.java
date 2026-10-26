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

/** Typed original autosomal hard-call QC; no default scientific thresholds or populations. */
final class PlinkQcCli {
    private PlinkQcCli() { }
    static final Set<String> ALLOWED=Set.of("task","engine","home","bfile","samples","out","options-file","genome-build",
        "qc-order","chromosome-scope","unresolved-alleles","maximum-sample-missing","maximum-variant-missing","minimum-maf","minimum-mac","frequency-population",
        "hwe-mode","hwe-p","hwe-k","hwe-keep-fewhet","hwe-population","hwe-founders","hwe-unreportable","final-check",
        "threads","memory-mib","timeout-seconds","maximum-samples","maximum-variants","maximum-cells","maximum-metadata-mib","maximum-retained-mib","maximum-line-bytes","maximum-bed-mib","maximum-samples-mib","maximum-report-mib");
    private static final String RECOVERY="Required action: inspect WARNINGS.md, session.yaml, sample-decisions.tsv, variant-decisions.tsv, final-threshold-violations.tsv and retained upstream logs. Correct the named input, explicit reference population, threshold, operation order or resource bound and rerun to a new output directory. No threshold or statistic is relaxed automatically. Failed filtered genotype files are quarantined under partial/. "+PlinkRuntime.setup();
    static int run(PlinkToolsCli.Parsed parsed,PrintStream output,PrintStream error) {
        var options=parsed.options();try {
            PlinkToolsCli.validateOptions(options,ALLOWED);required(options,"out");required(options,"bfile");required(options,"samples");
            String engine=choice(options,"engine",Set.of("plink1","plink2")),order=choice(options,"qc-order",Set.of("samples-then-variants","variants-then-samples")),unresolved=choice(options,"unresolved-alleles",Set.of("error","exclude","retain-with-warning"));
            choice(options,"chromosome-scope",Set.of("autosomes"));String build=choice(options,"genome-build",Set.of("GRCh37","GRCh38","unspecified"));
            double mind=range(options,"maximum-sample-missing",0,1),geno=range(options,"maximum-variant-missing",0,1),maf=range(options,"minimum-maf",0,.5);long mac=Long.parseLong(required(options,"minimum-mac"));if(mac<0)throw new IllegalArgumentException("--minimum-mac must be a nonnegative integer");
            String frequency=choice(options,"frequency-population",Set.of("founders","all")),hweMode=choice(options,"hwe-mode",Set.of("off","exact","midp")),finalCheck=choice(options,"final-check",Set.of("fail","warn"));
            double hweP=Double.NaN,hweK=0;boolean keepFew=false;String hwePopulation="not-requested",hweFounders="not-requested",unreportable="not-requested";
            var hweOptions=Set.of("hwe-p","hwe-k","hwe-keep-fewhet","hwe-population","hwe-founders","hwe-unreportable");
            if(hweMode.equals("off")){for(String key:hweOptions)if(options.containsKey(key))throw new IllegalArgumentException("Unused --"+key+" is forbidden when --hwe-mode off");}
            else {hweP=range(options,"hwe-p",0,1);if(hweP==0||hweP==1)throw new IllegalArgumentException("--hwe-p must be strictly between0and1 in this QC subset;use --hwe-mode off to disable HWE explicitly");hwePopulation=choice(options,"hwe-population",Set.of("all","controls"));hweFounders=choice(options,"hwe-founders",Set.of("founders","all"));unreportable=choice(options,"hwe-unreportable",Set.of("error","exclude","retain-with-warning"));
                if(engine.equals("plink2")){hweK=range(options,"hwe-k",0,Double.MAX_VALUE);keepFew=choice(options,"hwe-keep-fewhet",Set.of("yes","no")).equals("yes");}
                else if(options.containsKey("hwe-k")||options.containsKey("hwe-keep-fewhet"))throw new IllegalArgumentException("PLINK1 has no k/keep-fewhet HWE policy;choose the engine explicitly");
            }
            int threads=integer(options,"threads",1),memory=integer(options,"memory-mib",engine.equals("plink1")?128:640),timeout=integer(options,"timeout-seconds",3600);if(threads>256||timeout>86400||memory<(engine.equals("plink1")?64:640))throw new IllegalArgumentException("Invalid threads,timeout or engine-specific memory bound");
            var bounds=new PlinkGlmInputs.Bounds(integer(options,"maximum-samples",100000),integer(options,"maximum-variants",2000000),positive(options,"maximum-cells",500000000),mib(options,"maximum-metadata-mib",512),mib(options,"maximum-retained-mib",512),integer(options,"maximum-line-bytes",16384),mib(options,"maximum-bed-mib",65536),mib(options,"maximum-samples-mib",64));if(bounds.line()<64)throw new IllegalArgumentException("--maximum-line-bytes must be at least64");long reportBytes=mib(options,"maximum-report-mib",4096);
            String configuredHome=options.getOrDefault("home",System.getenv("JLINALG_PLINK_HOME"));if(configuredHome!=null&&!configuredHome.isBlank()){Path home=PlinkToolsCli.resolved(Path.of(configuredHome)),destination=PlinkToolsCli.resolved(Path.of(options.get("out")));if(destination.startsWith(home)||home.startsWith(destination))throw new IllegalArgumentException("QC output must be outside the sealed runtime home");}
            var settings=new PlinkQcPipeline.Settings(engine,order,mind,geno,maf,mac,frequency,hweMode,hweP,hweK,keepFew,hwePopulation,hweFounders,unreportable,finalCheck,threads,memory,timeout,reportBytes);
            int[] code={0};boolean[] warned={false};
            externalTransaction(options,"plink-original-autosomal-hardcall-qc",(directory,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();session.put("scope","Original pinned autosomal BED hard-call QC with explicit reference populations and one-pass order. No clinical/sample identity/ancestry/relatedness/caller certification,genome-build validation,imputation or whole-paper replay. Final retained calls are checked exactly against source input before export.");session.put("qc_policy",new LinkedHashMap<>(options));
                try {
                    if(configuredHome==null||configuredHome.isBlank())throw new PlinkRuntime.Unavailable("No --home or JLINALG_PLINK_HOME supplied",null);Path upstream=directory.resolve("upstream");Files.createDirectory(upstream);var runtime=new PlinkRuntime(Path.of(configuredHome),engine,upstream,session);
                    var inputs=PlinkQcInputs.prepare(Path.of(options.get("bfile")).toAbsolutePath().normalize(),Path.of(options.get("samples")).toAbsolutePath().normalize(),upstream,unresolved,bounds);manifest.put("input_sha256",inputs.inputHashes());session.put("staged_input_sha256",inputs.stagedHashes());session.put("source_samples",inputs.samples().size());session.put("source_variants",inputs.variants().size());session.put("initially_included_samples",inputs.initialSamples().size());session.put("initially_included_autosomal_variants",inputs.initialVariants().size());
                    session.put("population_contract","Frequency and HWE populations are explicit keep-file subsets. The original --nonfounders flag includes every individual of each already-selected reference subset. Founder selection uses recorded FAM parents both0;control selection uses only the explicit QC sample-table group. HWE/MAF reference subsets never remove cases/nonfounders from the final cohort by themselves. Staged FAM phenotypes are -9 and ignored;source FAM phenotype,parents and sex are restored unchanged in the final export.");
                    session.put("staging_contract","Deterministic ASCII surrogate IDs;full source identity mapping in decision tables. Native packed-reader admission permits missing allele labels only for QC;scoring/GLM default resolved-allele contracts remain unchanged. FAM/BIM/whole BED input and staged hashes are rechecked after the pipeline;each small stage keep/extract mask is rechecked immediately.");
                    if(options.containsKey("options-file")){Path recipe=Path.of(options.get("options-file"));if(!parsed.recipeSha256().equals(hash(recipe)))throw new IOException("QC recipe changed after parsing");Files.copy(recipe,directory.resolve("options-input.json"));if(!parsed.recipeSha256().equals(hash(directory.resolve("options-input.json"))))throw new IOException("QC recipe snapshot differs");}
                    new PlinkQcPipeline(inputs,runtime,upstream,settings,session,warnings).execute(directory);runtime.verify();
                    if(options.containsKey("options-file")&&!parsed.recipeSha256().equals(hash(Path.of(options.get("options-file")))))throw new IOException("QC recipe changed during analysis");
                    if(build.equals("unspecified"))warnings.add("Genome build is explicitly unspecified. Required action: establish assembly/coordinate provenance before genomic interpretation;this workflow validates genotype/identity preservation,not a reference assembly.");
                    long excludedSamples=inputs.samples().size()-inputs.initialSamples().size(),excludedVariants=inputs.variants().size()-inputs.initialVariants().size();if(excludedSamples>0||excludedVariants>0)warnings.add("Initial policy excluded "+excludedSamples+" samples and "+excludedVariants+" variants before genotype QC. Required action: inspect the explicit sample include flags,autosome scope and unresolved-allele policy in the decision tables;these exclusions are not downstream HWE/MAF failures.");
                    try(var paths=Files.list(upstream)){for(Path path:paths.filter(p->p.getFileName().toString().endsWith(".stdout")||p.getFileName().toString().endsWith(".stderr")||p.getFileName().toString().endsWith(".log")).toList()){try(var lines=Files.lines(path)){if(lines.anyMatch(line->line.stripLeading().toLowerCase(java.util.Locale.ROOT).startsWith("warning:")))warnings.add("Original "+engine+" emitted warnings in upstream/"+path.getFileName()+". Required action: read that complete retained file before downstream use.");}}}
                    warned[0]=!warnings.isEmpty();String state=warned[0]?"completed-with-qc-warnings":"completed";manifest.put("analysis_status",state);status(directory,state);if(warned[0])Files.writeString(directory.resolve("WARNINGS.md"),"# ORIGINAL GENOTYPE QC WARNINGS\n\n"+String.join("\n\n",warnings)+"\n");
                }catch(Exception failure){code[0]=failure instanceof PlinkRuntime.Unavailable?3:2;String state=code[0]==3?"skipped-dependency-unavailable":"failed";manifest.put("analysis_status",state);manifest.put("error",failure.toString());session.put("error",failure.toString());status(directory,state);for(String name:List.of("genotypes.bed","genotypes.bim","genotypes.fam","qc-samples-retained.tsv"))if(Files.exists(directory.resolve(name))){Files.createDirectories(directory.resolve("partial"));Files.move(directory.resolve(name),directory.resolve("partial").resolve(name));}Files.writeString(directory.resolve("WARNINGS.md"),"# ORIGINAL GENOTYPE QC "+(code[0]==3?"SKIPPED":"FAILED")+"\n\n"+failure+"\n\n"+String.join("\n\n",warnings)+"\n\n"+RECOVERY+"\n");}
                Files.writeString(directory.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));
            });
            if(code[0]!=0)error.println("ORIGINAL GENOTYPE QC "+(code[0]==3?"SKIPPED":"FAILED")+": see "+Path.of(options.get("out")).resolve("WARNINGS.md")+" for the cause and required action.");else{output.println("Original genotype QC completed: "+options.get("out"));if(warned[0])error.println("GENOTYPE QC WARNINGS: review "+Path.of(options.get("out")).resolve("WARNINGS.md")+" before downstream use.");}return code[0];
        }catch(Exception failure){error.println("ORIGINAL GENOTYPE QC NOT STARTED: "+failure.getMessage()+". "+RECOVERY+" Review plink-tools --task qc --help.");return 2;}
    }
    private static void status(Path directory,String state) throws IOException {Files.writeString(directory.resolve("analysis-status.json"),"{\"status\":\""+state+"\"}\n");}
    private static String choice(Map<String,String> options,String key,Set<String> choices){String value=required(options,key);if(!choices.contains(value))throw new IllegalArgumentException("--"+key+" must be one of "+choices);return value;}
    private static double range(Map<String,String> options,String key,double low,double high){double value=Double.parseDouble(required(options,key));if(!Double.isFinite(value)||value<low||value>high)throw new IllegalArgumentException("--"+key+" must be finite in ["+low+","+high+"]");return value;}
    private static long positive(Map<String,String> options,String key,long fallback){long value=Long.parseLong(options.getOrDefault(key,Long.toString(fallback)));if(value<1)throw new IllegalArgumentException("--"+key+" must be positive");return value;}
    private static int integer(Map<String,String> options,String key,int fallback){return Math.toIntExact(positive(options,key,fallback));}
    private static long mib(Map<String,String> options,String key,long fallback){return Math.multiplyExact(positive(options,key,fallback),1048576);}
    static String help(){return """
        plink-tools --task qc --engine plink1|plink2 --home SEALED_PRIVATE_HOME
          --bfile BED_PREFIX --samples QC_SAMPLES.tsv --out NEW_DIRECTORY
          --genome-build GRCh37|GRCh38|unspecified --chromosome-scope autosomes
          --qc-order samples-then-variants|variants-then-samples
          --unresolved-alleles error|exclude|retain-with-warning
          --maximum-sample-missing FRACTION --maximum-variant-missing FRACTION
          --minimum-maf FRACTION --minimum-mac COUNT --frequency-population founders|all
          --hwe-mode off|exact|midp --final-check fail|warn
          [if HWE enabled: --hwe-p POSITIVE_P --hwe-population all|controls
           --hwe-founders founders|all --hwe-unreportable error|exclude|retain-with-warning]
          [PLINK2 HWE additionally requires: --hwe-k NONNEGATIVE --hwe-keep-fewhet yes|no]
          [--options-file UTF8_OPTIONS.json] [--threads 1] [--memory-mib 128|640]
          [--timeout-seconds 3600] [--maximum-samples 100000] [--maximum-variants 2000000]
          [--maximum-cells 500000000] [--maximum-metadata-mib 512] [--maximum-retained-mib 512]
          [--maximum-line-bytes 16384] [--maximum-bed-mib 65536] [--maximum-samples-mib 64]
          [--maximum-report-mib 4096]
        QC sample TSV header: fid, iid, include, group (tabs). Name every FAM pair once;
        include=yes|no, group=control|case|unassigned. FAM phenotype is ignored for QC.
        Scientific thresholds,order and reference populations are explicit. Recorded
        founders have both FAM parents0. Controls are selected only from this table.
        Reference-only sample subsets screen variants;remaining cases/nonfounders remain
        in the final cohort. Native initial policies exclude nonautosomes and optionally
        unresolved allele labels before any missingness calculation. Original decision
        masks are authoritative;rounded report fractions/P-values never decide filters.
        Explicit minimum-maf0 disables that filter by omitting --maf (PLINK1 rejects0);
        minimum-mac0 is passed explicitly. Enabled HWE thresholds are strictly below1.
        Source HWE P=1 with zero calls is unreportable. Explicit policy is required.
        Monomorphic/singleton conditional tests are flagged as degenerate. PLINK2 k is
        never selected implicitly. One-pass order can change final criteria;final-check
        fail prohibits publishing a set that fails successful final checks,while warn
        retains flagged violations. Any original-process nonzero exit still fails QC.
        Final genotypes.bed/bim/fam restore original FID/IID,parents,sex,phenotype,variant
        IDs and coordinates. Upstream allele orientation is preserved and every retained
        call is checked against the source. Decision tables and raw reports are retained.
        Analysis never installs software. Missing/changed/forbidden dependencies skip
        with exit3 and WARNINGS.md. Input/scientific failures use exit2;read exact recovery
        actions. Failed filtered genotype files are quarantined under partial/.
        """;}
}
