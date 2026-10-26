/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.FollowupSupport.*;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.PlinkAssociations;
import org.jlinalg.genetics.PlinkConfidenceIntervals;

/** Explicit original PLINK association, autosomal QC, LD pruning and PCA. */
final class PlinkToolsCli {
    private PlinkToolsCli() { }
    private static final Set<String> GLM_ALLOWED=Set.of("task","engine","home","bfile","cohort","model","binary-coding","covariates","missing","genome-build","out","options-file","confidence-level",
        "threads","memory-mib","timeout-seconds","maximum-samples","maximum-variants","maximum-cells","maximum-metadata-mib","maximum-retained-mib","maximum-line-bytes","maximum-bed-mib","maximum-cohort-mib","maximum-report-mib");
    private static final String RECOVERY="Required action: inspect WARNINGS.md, sample-ledger.tsv, session.yaml and retained upstream logs. Correct the named inputs, model/coding, covariates or resource limit, then rerun to a new output directory. Original ERRCODE/NA rows are unreportable; finite provisional estimates never override an error. Do not treat blank estimates or P-values as zero. No population structure, relatedness or other confounding adjustment is inferred. "+PlinkRuntime.setup();
    record Parsed(Map<String,String> options,String recipeSha256) { }

    static int run(String[] args,PrintStream output,PrintStream error) {
        if (args.length==1 && args[0].equals("--help")) { output.println(help()+"\n"+PlinkQcCli.help()+"\n"+PlinkPruneCli.help()+"\n"+PlinkPcaCli.help());return 0; }
        if(args.length==3&&args[0].equals("--task")&&args[2].equals("--help")&&args[1].equals("qc")){output.println(PlinkQcCli.help());return 0;}
        if(args.length==3&&args[0].equals("--task")&&args[2].equals("--help")&&args[1].equals("prune")){output.println(PlinkPruneCli.help());return 0;}
        if(args.length==3&&args[0].equals("--task")&&args[2].equals("--help")&&args[1].equals("pca")){output.println(PlinkPcaCli.help());return 0;}
        try {
            var parsed=parse(args);var options=parsed.options();if("qc".equals(options.get("task")))return PlinkQcCli.run(parsed,output,error);if("prune".equals(options.get("task")))return PlinkPruneCli.run(parsed,output,error);if("pca".equals(options.get("task")))return PlinkPcaCli.run(parsed,output,error);validateOptions(options,GLM_ALLOWED);required(options,"out");
            required(options,"bfile");required(options,"cohort");
            if (!required(options,"task").equals("glm")) throw new IllegalArgumentException("--task must be glm, qc, prune or pca; other PLINK operations are not exposed by this command yet");
            String engine=required(options,"engine"),model=required(options,"model"),missing=required(options,"missing"),build=required(options,"genome-build");
            if (!Set.of("plink1","plink2").contains(engine) || !Set.of("linear","logistic","firth","firth-fallback").contains(model)) throw new IllegalArgumentException("Declare plink1|plink2 and linear|logistic|firth|firth-fallback");
            if (engine.equals("plink1") && (model.equals("firth") || model.equals("firth-fallback"))) throw new IllegalArgumentException("Original PLINK1 does not support this Firth workflow; select plink2 explicitly");
            String coding=model.equals("linear")?"quantitative":required(options,"binary-coding");
            if (model.equals("linear") && options.containsKey("binary-coding")) throw new IllegalArgumentException("--binary-coding is only for binary models");
            if (!model.equals("linear") && !Set.of("0/1","1/2").contains(coding)) throw new IllegalArgumentException("--binary-coding must be 0/1 or 1/2");
            if (!Set.of("error","exclude").contains(missing) || !Set.of("GRCh37","GRCh38","unspecified").contains(build)) throw new IllegalArgumentException("Declare --missing error|exclude and --genome-build GRCh37|GRCh38|unspecified");
            var covariates=PlinkGlmInputs.covariates(required(options,"covariates"));
            int threads=positiveInt(options,"threads",1),memory=positiveInt(options,"memory-mib",engine.equals("plink1")?128:640),timeout=positiveInt(options,"timeout-seconds",3600);
            if (threads>256 || timeout>86400 || memory<(engine.equals("plink1")?64:640)) throw new IllegalArgumentException("Require threads<=256, timeout<=86400, and engine memory at least64MiB (PLINK1) or640MiB (PLINK2)");
            var bounds=new PlinkGlmInputs.Bounds(positiveInt(options,"maximum-samples",100000),positiveInt(options,"maximum-variants",2000000),positiveLong(options,"maximum-cells",500000000),mib(options,"maximum-metadata-mib",512),mib(options,"maximum-retained-mib",512),positiveInt(options,"maximum-line-bytes",16384),mib(options,"maximum-bed-mib",65536),mib(options,"maximum-cohort-mib",64));
            long reportLimit=mib(options,"maximum-report-mib",4096);
            double confidence=options.containsKey("confidence-level")?Double.parseDouble(options.get("confidence-level")):Double.NaN;
            if (options.containsKey("confidence-level")) PlinkConfidenceIntervals.validateLevel(confidence);
            if (bounds.line()<64) throw new IllegalArgumentException("--maximum-line-bytes must be at least64");
            String configuredHome=options.getOrDefault("home",System.getenv("JLINALG_PLINK_HOME"));
            if (configuredHome!=null && !configuredHome.isBlank()) {
                Path runtimeHome=resolved(Path.of(configuredHome)),destination=resolved(Path.of(options.get("out")));
                if (destination.startsWith(runtimeHome) || runtimeHome.startsWith(destination)) throw new IllegalArgumentException("Output must be outside the sealed runtime home and its ancestors; choose a separate new output directory");
            }
            int[] code={0};boolean[] warned={false};
            externalTransaction(options,"plink-original-additive-glm",(directory,manifest)-> {
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();
                session.put("scope","Original pinned PLINK additive hard-call, diploid human autosomal biallelic DNA regression. No native regression solver, genome-build validation, strand inference, implicit covariates, QC filters, imputation, multiplicity correction or historical-paper version equivalence.");
                session.put("model",model);session.put("input_phenotype_coding",coding);session.put("upstream_binary_coding","1=control,2=case");session.put("missing_policy",missing);session.put("covariates",covariates);session.put("genome_build_declared",build);
                session.put("confidence_level_requested",Double.isNaN(confidence)?"not-requested":confidence);
                try {
                    // Admit dependencies before copying or scanning potentially large scientific inputs.
                    if (configuredHome==null || configuredHome.isBlank()) throw new PlinkRuntime.Unavailable("No --home or JLINALG_PLINK_HOME was supplied",null);
                    Path upstream=directory.resolve("upstream");Files.createDirectory(upstream);
                    var runtime=new PlinkRuntime(Path.of(configuredHome),engine,upstream,session);
                    Path prefix=Path.of(required(options,"bfile")).toAbsolutePath().normalize(),cohort=Path.of(required(options,"cohort")).toAbsolutePath().normalize();
                    var prepared=PlinkGlmInputs.prepare(prefix,cohort,upstream,model,coding,missing,covariates,bounds);
                    manifest.put("input_sha256",prepared.inputHashes());session.put("staged_input_sha256",prepared.stagedHashes());
                    session.put("input_samples",prepared.samples());session.put("eligible_samples",prepared.eligible());session.put("excluded_samples",prepared.excluded());session.put("eligible_cases",prepared.cases());session.put("eligible_controls",prepared.controls());session.put("variants",prepared.variants().size());
                    session.put("staging","Exact FID/IID pairs and variant IDs map to deterministic ASCII surrogates. FAM founder/parent/sex status is preserved; FAM phenotype is ignored in favor of the explicit cohort. Genetic distance is staged as0 and is unused by this GLM. Binary0/1 is explicitly mapped to1/2; quantitative/covariate values are parsed and serialized as finite Java doubles. Raw original reports use surrogates; metadata tables map every ID back.");
                    session.put("unused_numeric_missing_sentinel",prepared.missingSentinel());
                    if (options.containsKey("options-file")) { Path recipe=Path.of(options.get("options-file"));if (!parsed.recipeSha256().equals(hash(recipe))) throw new IOException("Options recipe changed after parsing");Files.copy(recipe,directory.resolve("options-input.json"));if (!parsed.recipeSha256().equals(hash(directory.resolve("options-input.json")))) throw new IOException("Options recipe snapshot differs");session.put("options_file_sha256",parsed.recipeSha256()); }
                    Files.copy(upstream.resolve("sample-ledger.tsv"),directory.resolve("sample-ledger.tsv"));Files.copy(upstream.resolve("variant-metadata.tsv"),directory.resolve("variant-metadata.tsv"));
                    var command=new ArrayList<>(List.of("--bfile","input","--keep","eligible.tsv","--pheno","phenotypes.tsv","--pheno-name","TRAIT","--threads",Integer.toString(threads),"--memory",Integer.toString(memory)));
                    if (engine.equals("plink1")) command.addAll(List.of("--allow-no-sex","--keep-allele-order","--missing-phenotype",Integer.toString(prepared.missingSentinel())));
                    else command.addAll(List.of("--no-psam-pheno","--no-categorical","--no-input-missing-phenotype"));
                    if (!covariates.isEmpty()) {
                        command.addAll(List.of("--covar","covariates.tsv","--covar-name"));var names=new ArrayList<String>();for (int i=0;i<covariates.size();i++) names.add("C"+(i+1));command.add(String.join(",",names));
                    }
                    if (engine.equals("plink1")) command.addAll(List.of(model.equals("linear")?"--linear":"--logistic","hide-covar"));
                    else {
                        command.addAll(List.of("--glm","hide-covar"));if (covariates.isEmpty()) command.add("allow-no-covars");
                        if (!model.equals("linear")) command.add(switch (model) {case "logistic"->"no-firth";case "firth"->"firth";default->"firth-fallback";});
                        command.add("pheno-ids");
                    }
                    if (!Double.isNaN(confidence)) command.addAll(List.of("--ci",Double.toString(confidence)));
                    command.addAll(List.of("--out","result"));runtime.execute(command,upstream,"glm",timeout);
                    PlinkGlmInputs.verify(prepared.inputs(),prepared.inputHashes());PlinkGlmInputs.verifyStaged(upstream,prepared);
                    String suffix=engine.equals("plink1")?".assoc."+model:".TRAIT.glm."+switch (model) {case "linear"->"linear";case "logistic"->"logistic";case "firth"->"firth";default->"logistic.hybrid";};
                    Path report=upstream.resolve("result"+suffix);session.put("original_report",directory.relativize(report).toString());session.put("original_report_sha256",hash(report));
                    var sourceEngine=PlinkAssociations.Engine.valueOf(engine.toUpperCase(Locale.ROOT));var sourceModel=PlinkAssociations.Model.valueOf(model.toUpperCase(Locale.ROOT).replace('-','_'));
                    var statusCounts=new LinkedHashMap<String,Long>();var ciCounts=new LinkedHashMap<String,Long>();PlinkAssociations.Counts counts;
                    try (var writer=Files.newBufferedWriter(directory.resolve("results.tsv"))) {
                        writer.write("variant\tchromosome\tposition\tea\toa\teffect_is_bim_a1\tn\tfirth\tbeta\todds_ratio\tstandard_error\tstatistic\tp\tnegative_log10_p\tstatus\tupstream_error\tupstream_variant\tconfidence_level_requested\tci_scale\tci_lower\tci_upper\tci_status\n");
                        counts=PlinkAssociations.scan(report,sourceEngine,sourceModel,prepared.alleles(),prepared.eligible(),new PlinkAssociations.Limits(bounds.variants(),reportLimit,bounds.line()),row->{
                            var variant=prepared.variants().get(row.variant());
                            if (row.effectReportable() && row.observedSamples()!=variant.observed()) throw new IllegalArgumentException("Original observed count differs from the exact eligible nonmissing-genotype count at "+variant.original());
                            statusCounts.merge(row.status().toString(),1L,Long::sum);
                            try {
                                String limits="\t\t\t\t\tNOT_REQUESTED";
                                if (!Double.isNaN(confidence)) {
                                    var interval=PlinkConfidenceIntervals.read(row,sourceEngine,sourceModel,confidence);ciCounts.merge(interval.status().toString(),1L,Long::sum);
                                    limits="\t"+confidence+"\t"+interval.scale()+"\t"+number(interval.lower())+"\t"+number(interval.upper())+"\t"+interval.status();
                                }
                                writer.write(variant.original()+"\t"+variant.chromosome()+"\t"+variant.position()+"\t"+row.effectAllele()+"\t"+row.otherAllele()+"\t"+row.effectIsBimA1()+"\t"+row.observedSamples()+"\t"+row.firth()+"\t"+number(row.beta())+"\t"+number(row.oddsRatio())+"\t"+number(row.standardError())+"\t"+number(row.statistic())+"\t"+number(row.pValue())+"\t"+number(row.negativeLog10PValue())+"\t"+row.status()+"\t"+row.upstreamError()+"\t"+row.variant()+limits+"\n");
                            }
                            catch (IOException failure) { throw new UncheckedIOException(failure); }
                        });
                    }
                    session.put("report_status_counts",statusCounts);session.put("effect_reportable_rows",counts.effectReportable());session.put("p_reportable_rows",counts.pReportable());
                    session.put("confidence_status_counts",ciCounts);
                    if (ciCounts.containsKey("UNREPORTABLE_BOUNDS")) warnings.add(ciCounts.get("UNREPORTABLE_BOUNDS")+" original confidence intervals have unavailable, nonfinite, reversed or unresolved bounds; both canonical bounds are blank. Required action: review the original limits and requested level; obtain reportable original limits before interval-based inference. Effect/SE reportability is assessed separately.");
                    if (prepared.excluded()>0) warnings.add(prepared.excluded()+" samples were explicitly excluded for missing phenotype/covariates; inspect sample-ledger.tsv. Required action: assess selection bias and cohort eligibility. Per-variant missing genotypes further reduce n without imputation.");
                    if (counts.upstreamErrors()+counts.missingFields()>0) warnings.add((counts.upstreamErrors()+counts.missingFields())+" regression rows are UNREPORTABLE. Canonical numeric estimates are blank; raw original fields remain in "+directory.relativize(report)+". Required action: inspect upstream_error/NA, convergence, genotype counts, separation and model rank before any downstream inference.");
                    if (counts.unresolvedZeroP()>0) warnings.add(counts.unresolvedZeroP()+" original P-values were printed as zero and have no resolved log P-value. Required action: obtain an original report with adequate log-P resolution; do not assign a floor or infer significance from an invented zero.");
                    if (statusCounts.containsKey("VALID_LOG_P_ONLY")) warnings.add(statusCounts.get("VALID_LOG_P_ONLY")+" rows retain P-value resolution only in negative_log10_p; p is blank. Required action: use the log field and original textual report for comparisons; never turn the blank into zero.");
                    if (statusCounts.containsKey("VALID_WITHOUT_REPORTED_SE")) warnings.add("Original PLINK1 did not report standard errors for "+statusCounts.get("VALID_WITHOUT_REPORTED_SE")+" rows. Required action: request --confidence-level (for example 0.95) to obtain original reported SE before SE-dependent meta-analysis; no SE is reconstructed from rounded beta/statistic fields.");
                    for (String name:List.of("glm.stdout","glm.stderr","result.log")) {
                        Path path=upstream.resolve(name);if (Files.isRegularFile(path)) try (var lines=Files.lines(path)) { if (lines.anyMatch(line->line.stripLeading().toLowerCase(Locale.ROOT).startsWith("warning:"))) warnings.add("Original "+engine+" emitted warnings in upstream/"+name+". Required action: read that complete retained file before interpreting the analysis."); }
                    }
                    if (build.equals("unspecified")) warnings.add("Genome build was explicitly unspecified. Required action: establish assembly and variant provenance before genomic interpretation; this workflow does not validate the reference genome.");
                    if (covariates.isEmpty()) warnings.add("Only an intercept and additive genotype were fitted. Required action: justify the explicit --covariates none choice and assess population structure, relatedness and confounding before inference.");
                    if (counts.effectReportable()==0 || counts.pReportable()==0) throw new IOException("No association has both a reportable effect and resolved P-value");
                    if (options.containsKey("options-file") && !parsed.recipeSha256().equals(hash(Path.of(options.get("options-file"))))) throw new IOException("Options recipe changed during original association");
                    runtime.verify();warned[0]=!warnings.isEmpty();String state=warned[0]?"completed-with-qc-warnings":"completed";manifest.put("analysis_status",state);status(directory,state);
                    if (warned[0]) Files.writeString(directory.resolve("WARNINGS.md"),"# ORIGINAL PLINK ASSOCIATION WARNINGS\n\n"+String.join("\n\n",warnings)+"\n");
                } catch (Exception failure) {
                    code[0]=failure instanceof PlinkRuntime.Unavailable?3:2;String state=code[0]==3?"skipped-dependency-unavailable":"failed";manifest.put("analysis_status",state);manifest.put("error",failure.toString());session.put("error",failure.toString());status(directory,state);
                    if (Files.exists(directory.resolve("results.tsv"))) { Files.createDirectories(directory.resolve("partial"));Files.move(directory.resolve("results.tsv"),directory.resolve("partial/results.tsv")); }
                    Files.writeString(directory.resolve("WARNINGS.md"),"# ORIGINAL PLINK ASSOCIATION "+(code[0]==3?"SKIPPED":"FAILED")+"\n\n"+failure+"\n\n"+String.join("\n\n",warnings)+"\n\n"+RECOVERY+"\n");
                }
                Files.writeString(directory.resolve("session.yaml"),new org.yaml.snakeyaml.Yaml().dump(session));
            });
            if (code[0]!=0) error.println("ORIGINAL PLINK ASSOCIATION "+(code[0]==3?"SKIPPED":"FAILED")+": see "+Path.of(options.get("out")).resolve("WARNINGS.md")+" for cause and required action.");
            else { output.println("Original PLINK association completed: "+options.get("out"));if (warned[0]) error.println("ORIGINAL PLINK ASSOCIATION WARNINGS: review "+Path.of(options.get("out")).resolve("WARNINGS.md")+" before downstream use."); }return code[0];
        } catch (Exception failure) { error.println("ORIGINAL PLINK ASSOCIATION NOT STARTED: "+failure.getMessage()+". "+RECOVERY+" Review plink-tools --help.");return 2; }
    }
    private static void status(Path directory,String state) throws IOException { Files.writeString(directory.resolve("analysis-status.json"),"{\"status\":\""+state+"\"}\n"); }
    static Path resolved(Path path) throws IOException {
        Path absolute=path.toAbsolutePath().normalize(),ancestor=absolute;
        while (!Files.exists(ancestor)) { ancestor=ancestor.getParent();if (ancestor==null) throw new IOException("No existing ancestor for "+path); }
        return ancestor.toRealPath().resolve(ancestor.relativize(absolute)).normalize();
    }
    private static String number(double value) { return Double.isNaN(value)?"":Double.toString(value); }
    private static long positiveLong(Map<String,String> options,String key,long fallback) { long value=Long.parseLong(options.getOrDefault(key,Long.toString(fallback)));if (value<1) throw new IllegalArgumentException("--"+key+" must be positive");return value; }
    private static int positiveInt(Map<String,String> options,String key,int fallback) { return Math.toIntExact(positiveLong(options,key,fallback)); }
    private static long mib(Map<String,String> options,String key,long fallback) { return Math.multiplyExact(positiveLong(options,key,fallback),1048576); }
    private static Parsed parse(String[] args) throws Exception {
        var allowed=new java.util.HashSet<>(GLM_ALLOWED);allowed.addAll(PlinkQcCli.ALLOWED);allowed.addAll(PlinkPruneCli.ALLOWED);allowed.addAll(PlinkPcaCli.ALLOWED);
        var direct=options(args,allowed);if (!direct.containsKey("options-file")) return new Parsed(direct,null);Path path=Path.of(direct.get("options-file"));if (!Files.isRegularFile(path) || Files.size(path)>1048576) throw new IOException("Require a UTF-8 options JSON file <=1MiB");
        byte[] raw;try (var stream=Files.newInputStream(path)) { raw=stream.readNBytes(1048577); }if (raw.length>1048576) throw new IOException("Options JSON grew beyond1MiB");
        String digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw));
        Object parsed=SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString());if (!(parsed instanceof Map<?,?> values)) throw new IllegalArgumentException("Require a JSON object of option:string values");
        var result=new LinkedHashMap<String,String>();for (var entry:values.entrySet()) { String key=String.valueOf(entry.getKey());if (!allowed.contains(key) || key.equals("options-file") || !(entry.getValue() instanceof String text)) throw new IllegalArgumentException("Unknown, recursive or non-string recipe option: "+key);result.put(key,text); }result.putAll(direct);return new Parsed(result,digest);
    }
    static void validateOptions(Map<String,String> options,Set<String> allowed){for(String key:options.keySet())if(!allowed.contains(key))throw new IllegalArgumentException("Option --"+key+" does not belong to the selected PLINK task");}
    static String help() { return """
        plink-tools --task glm --engine plink1|plink2 --home SEALED_PRIVATE_HOME
          --bfile BED_PREFIX --cohort COHORT.tsv --model linear|logistic|firth|firth-fallback
          [--binary-coding 0/1|1/2] --covariates none|NAME1,NAME2 --missing error|exclude
          --genome-build GRCh37|GRCh38|unspecified --out NEW_DIRECTORY
          [--options-file UTF8_OPTIONS.json] [--threads 1] [--memory-mib 128|640]
          [--timeout-seconds 3600] [--maximum-samples 100000] [--maximum-variants 2000000]
          [--maximum-cells 500000000] [--maximum-metadata-mib 512] [--maximum-retained-mib 512]
          [--maximum-line-bytes 16384] [--maximum-bed-mib 65536] [--maximum-cohort-mib 64]
          [--maximum-report-mib 4096]
          [--confidence-level LEVEL_IN_0.01_TO_BELOW_1]
        Original pinned Windows x86_64 PLINK additive, diploid human autosomal hard-call
        association subset. Firth modes require plink2. --home can be supplied through
        JLINALG_PLINK_HOME. Analysis never installs software; unavailable dependency =>
        SKIPPED, exit3, WARNINGS.md with exact setup action. Scientific/input failure =>exit2.
        Cohort: exact TSV header fid, iid, phenotype, then declared numeric covariates in
        order; name every FAM pair exactly once. Only literal NA denotes missing values.
        Binary coding and missing-value exclusion require explicit choices. FAM phenotypes
        are ignored; sex is not implicitly added on these autosomes. No QC filter, imputation,
        population adjustment, strand inference or multiplicity correction is inferred.
        Require canonical chromosomes1..22 in contiguous blocks and resolved uppercase
        DNA alleles (1..1000 bases). Linear traits containing only0/1/2 are rejected because
        original auto-detection is ambiguous. Exact identities map through ASCII surrogates;
        sample-ledger.tsv and variant-metadata.tsv retain the original identifiers.
        ERR/NA rows have blank canonical estimates. Original printed zero is unresolved;
        positive P below double range retains negative_log10_p. Binary beta=log(reported OR).
        --confidence-level requests original reported SE/limits, with the invocation level
        retained separately from rounded/truncated column labels. ci_scale is BETA for linear
        models and ODDS_RATIO for binary models; standard_error is beta/log-odds SE. Original
        limits are normal-based, including linear models with Student-t P-values. No limits
        are recomputed from rounded output. ERR/NA effects suppress their interval bounds.
        Review WARNINGS.md and raw upstream reports before downstream use. Failed canonical
        streams are quarantined under partial/. This command does not expose the full PLINK suite.
        """; }
}
