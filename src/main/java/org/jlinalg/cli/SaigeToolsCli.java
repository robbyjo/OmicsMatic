/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.FollowupSupport.*;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Typed original SAIGE operations with immutable inputs and native output audits. */
final class SaigeToolsCli {
    private SaigeToolsCli() { }
    private static final Set<String> ALLOWED=Set.of("recipe","home","out","timeout","options-file");

    static int run(String[] args,PrintStream out,PrintStream err) {
        if(args.length==1&&args[0].equals("--help")){out.println(help());return 0;}
        try {
            Map<String,String> options=parse(args);required(options,"out");int[] code={0};
            externalTransaction(options,"original-saige-typed-audited-operation",(directory,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();
                session.put("requested_options",options);
                try {
                    int timeout=integer(options,"timeout",3600,1,86400);
                    Path recipe=Path.of(required(options,"recipe")).toAbsolutePath().normalize();
                    String operation=SaigeGrmInputs.text(SaigeNullInputs.json(recipe,2*1048576),"operation");
                    if(!Set.of("sparse-grm","fit-null","single").contains(operation))throw new IOException("Supported typed operations are sparse-grm,fit-null and single;conditional/gene/LD integration remains separate work");
                    SaigeSingleInputs.Spec singleSpec=operation.equals("single")?SaigeSingleInputs.read(recipe):null;
                    SaigeNullInputs.Spec nullSpec=operation.equals("fit-null")?SaigeNullInputs.read(recipe):null;
                    SaigeGrmInputs.Spec spec=singleSpec!=null?null:nullSpec==null?SaigeGrmInputs.read(recipe):nullSpec.genotypes();
                    String recipeHash=singleSpec!=null?singleSpec.recipeSha256():spec.recipeSha256();Map<?,?> recipeSource=singleSpec!=null?singleSpec.source():spec.source();
                    Files.copy(recipe,directory.resolve("supplied-recipe.json"));
                    if(!hash(directory.resolve("supplied-recipe.json")).equals(recipeHash))throw new IOException("SAIGE recipe changed during snapshot");
                    session.put("operation",operation);session.put("recipe_sha256",recipeHash);session.put("recipe",recipeSource);
                    String supplied=options.get("home");
                    if(supplied==null||supplied.isBlank())throw new SaigeRuntime.Unavailable("No admitted original SAIGE --home supplied;analysis never installs dependencies");
                    SaigeRuntime.Context runtime=SaigeRuntime.open(Path.of(supplied));
                    SaigeRuntime.provenance(runtime,session);SaigeRuntime.probe(runtime,directory,timeout,session);
                    var sources=new TreeMap<String,String>();Map<String,Object> audit;List<String> stages;Verifier verify;
                    if(singleSpec!=null) {
                        SaigeSingleInputs.Prepared inputs=SaigeSingleInputs.prepare(singleSpec,directory,runtime);inputs.model().immutable().forEach((p,h)->sources.put(p.toString(),h));inputs.immutable().forEach((p,h)->sources.put(p.toString(),h));session.put("immutable_input_sha256",sources);verify=inputs::verify;inputs.verify();
                        int exit=SaigeRuntime.execute(runtime,SaigeRuntime.program(runtime,"step2_SPAtests.R"),SaigeSingleInputs.command(inputs,directory),directory,"single",timeout,session);
                        inputs.verify();runtime.verify();if(exit!=0)throw new IOException("Original SAIGE association returned "+exit+";inspect single.stdout and single.stderr");
                        audit=SaigeSingleAudit.audit(inputs,directory);stages=List.of("runtime-probe","single");
                        warnings.add("SCIENTIFIC REVIEW REQUIRED — ASSOCIATION: read variant-ledger.tsv, genotype-sample-ledger.tsv and association-audit.tsv. Every model IID must be present; extra genotype participants are analyzed only if in the model. PLINK Allele1/Allele2 labels follow the explicitly declared order; biological REF/ALT, strand, ancestry and ploidy are not inferred. Both original initial QC and post-imputation MAF/MAC checks are retained, including the exact floating threshold arithmetic and explicit small-dosage cleanup. Required action: review all exclusions, allele orientation, imputation/cleanup and model/cohort choices before scientific use.");
                        warnings.add("SCIENTIFIC REVIEW REQUIRED — ORIGINAL ESTIMATORS: native checks cover the complete eligible variant set, counts/frequencies, permitted centered/projected score paths, applicable nonsparse variances and propagated printed normal-P/effect/SE intervals. Final printed P does not independently establish which fast-retest branch ran. Original sparse-PCG, SPA, Firth and efficient resampling remain upstream algorithms; this is not an independent re-fit or historical/full-paper reproduction. Required action: review original single.stdout/stderr, convergence and calibration; inspect the recorded variance-ratio bins, resampling eligibility and numerical bounds.");
                        long unreportable=((Number)audit.get("unreportable_rows_statistics_withheld")).longValue();if(unreportable>0)warnings.add(0,"UNREPORTABLE ASSOCIATIONS: "+unreportable+" returned rows have numerical/estimability or Firth-convergence limitations. Their canonical statistical fields are withheld and named in association-audit.tsv; original fields remain unmodified in original-associations.tsv. Required action: investigate the named reasons and do not interpret these rows as estimable null effects or use their original printed P as an admitted association.");
                        if(((Number)audit.get("efficient_resampling_eligible_rows")).longValue()>0)warnings.add("EFFICIENT RESAMPLING: low-MAC rows use the explicitly requested original resampling path. Required action: assess original resampling precision and calibration; the wrapper does not infer a historical random seed or independently reconstruct this estimator.");
                        if(((Number)audit.get("single_common_ratio_applied_below_MAC20_rows")).longValue()>0)warnings.add("VARIANCE-RATIO EXTRAPOLATION: a single ratio calibrated from original MAC>=20 markers is applied to lower-MAC rows. Required action: assess calibration or fit explicitly suitable categorical MAC ratios; a finite P does not establish calibrated rare-variant inference.");
                        if(inputs.model().sparseRatio())warnings.add("SPARSE-GRM REVIEW: the reused fit uses candidate-screened sparse relatedness. Required action: assess its marker/cutoff/cohort suitability and do not infer complete above-cutoff pair coverage from the matrix.");
                        if(inputs.model().sparseRatio())warnings.add("ORIGINAL STEP-2 SPARSE STORAGE: the admitted1.5.2 program passes the supplied stored MatrixMarket coordinates to its Sigma constructor without reflecting a symmetric file's triangle. A controlled original export retained5122entries rather than9244reflected entries;native conditional replay confirmed asymmetric conditioning covariance. This is source-compatible execution,not a covariance-validity guarantee. Required action: assess this original profile's sparse numerical behavior with a qualified analyst,or use a suitable full-GRM null/variance-ratio workflow that does not request Step2 sparse Sigma. The wrapper preserves the original program and does not silently repair its matrix.");
                        if(((Number)audit.get("original_PCG_nonconvergence_messages")).longValue()>0)warnings.add(0,"ORIGINAL PCG NONCONVERGENCE: the source reported an iteration-limit failure without identifying each affected marker. All potentially affected sparse-path statistics are unreportable under explicit retention;otherwise the analysis fails. Required action: inspect original logs and the audit ledger,resolve model/GRM/numerical suitability,and rerun to a new directory. Do not use retained raw statistics as converged estimates.");

                    }else if(nullSpec==null) {
                        SaigeGrmInputs.Prepared inputs=SaigeGrmInputs.prepare(spec,directory);inputs.immutable().forEach((p,h)->sources.put(p.toString(),h));
                        session.put("immutable_input_sha256",sources);verify=inputs::verify;inputs.verify();
                        int exit=SaigeRuntime.execute(runtime,SaigeRuntime.program(runtime,"createSparseGRM.R"),SaigeGrmInputs.command(inputs,directory),directory,"sparse-grm",timeout,session);
                        inputs.verify();runtime.verify();if(exit!=0)throw new IOException("Original SAIGE returned "+exit+";inspect sparse-grm.stdout and sparse-grm.stderr");
                        audit=SaigeGrmInputs.audit(inputs,directory);stages=List.of("runtime-probe","sparse-grm");
                    }else {
                        SaigeNullInputs.Prepared inputs=SaigeNullInputs.prepare(nullSpec,directory);inputs.genotypes().immutable().forEach((p,h)->sources.put(p.toString(),h));inputs.immutable().forEach((p,h)->sources.put(p.toString(),h));
                        session.put("immutable_input_sha256",sources);verify=inputs::verify;inputs.verify();session.put("phenotype_admission",inputs.admission());
                        Files.writeString(directory.resolve("phenotype-admission.json"),RegenieRuntime.json(inputs.admission())+"\n",StandardCharsets.UTF_8);
                        int exit=SaigeRuntime.execute(runtime,SaigeRuntime.program(runtime,"step1_fitNULLGLMM.R"),SaigeNullInputs.command(inputs,directory),directory,"fit-null",timeout,session);
                        inputs.verify();runtime.verify();if(exit!=0)throw new IOException("Original SAIGE null fit returned "+exit+";inspect fit-null.stdout and fit-null.stderr");
                        audit=SaigeNullAudit.inspect(inputs,runtime,directory,timeout,session);stages=List.of("runtime-probe","fit-null","model-audit");
                        warnings.add("SCIENTIFIC REVIEW REQUIRED — NULL MODEL: read cohort-ledger.tsv and phenotype-admission.json for every inclusion and exclusion. Declared categorical levels are coded as native dummy variables with the first level as baseline; safe aliases prevent source names from becoming R formulas. Native checks cover the complete cohort, coded or average-rank IRNT outcome, declared covariate column space, finite converged variance components, variance-ratio bins and all base/LOCO residual identities. Mixed-model and variance-ratio estimation remain original SAIGE. Required action: review convergence diagnostics, separation, covariate choices, ancestry, ascertainment, sparse/full GRM suitability and variance-ratio calibration before association testing. This fit is not an independently re-fitted validation of the original estimator.");
                        if(nullSpec.sparse()||nullSpec.sparseRatio())warnings.add("SCIENTIFIC REVIEW REQUIRED — SPARSE GRM: the supplied typed GRM bundle uses candidate screening and does not certify complete above-cutoff pair coverage. Required action: establish that this GRM and its generation/fit cutoffs are suitable for the intended model; this fit does not certify complete graph coverage for unrelated selection.");
                    }
                    session.put("immutable_input_sha256",sources);
                    Files.writeString(directory.resolve("native-audit.json"),RegenieRuntime.json(audit)+"\n",StandardCharsets.UTF_8);
                    if(operation.equals("sparse-grm")) {
                    warnings.add("SCIENTIFIC REVIEW REQUIRED — INCOMPLETE PAIR COVERAGE: original SAIGE screens candidate pairs using random_markers. The resulting sparse GRM does not certify complete coverage above relatedness_cutoff. Do not use it as an asserted complete graph for unrelated-select without independently establishing coverage. Required action: review the marker source, missingness, MAF, random-marker count, cutoff and sample universe; establish suitable graph coverage for the intended selection or modeling analysis.");
                    warnings.add("SCIENTIFIC REVIEW REQUIRED — MATRIX AND CODING: observed diagonal values are retained; unstored entries are zero. No unit diagonal or positive definiteness is inferred. Diploid autosomal PLINK hard calls and the assembly label are caller declarations; this operation does not establish biological REF/ALT, ancestry, sex/ploidy coding or historical paper parity. Original R fixes seed=1. Native input QC counts use exact observed hard calls; upstream float arithmetic can affect boundary decisions. Required action: inspect native-audit.json, the supplied recipe and original logs before use. Association fitting, SPA/Firth, gene tests and LD operations are not exposed by this recipe.");
                    Map<?,?> matrix=(Map<?,?>)audit.get("matrix");
                    if(((Number)matrix.get("total_zero_diagonals")).longValue()>0)warnings.add("ZERO GRM DIAGONALS: "+matrix.get("total_zero_diagonals")+" samples have zero diagonal entries, including "+matrix.get("unstored_zero_diagonals")+" unstored entries. Required action: investigate genotype variation and the intended model; this output is not certified positive definite.");
                    }
                    for(String stage:stages) {
                        Path log=directory.resolve(stage+".stderr");
                        if(Files.size(log)>8L*1048576)throw new IOException("Original SAIGE diagnostic log exceeds 8MiB");
                        if(Files.size(log)>0)warnings.add("ORIGINAL R/SAIGE DIAGNOSTICS: inspect "+stage+".stderr and "+stage+".stdout. Original import-replacement and packages-built-under-R4.5.3 notices under admitted R4.5.2 are retained; an exit code of zero alone does not admit results. Required action: review all original warnings and the native audit for this run.");
                    }
                    verifyOptions(options);verify.verify();runtime.verify();
                    var results=new LinkedHashMap<String,Object>();results.put("status","completed-with-review-warnings");results.put("operation",operation);results.put("native_audit_sha256",hash(directory.resolve("native-audit.json")));
                    if(singleSpec!=null){results.put("associations","associations.tsv");for(String key:List.of("associations_sha256","original_output_sha256","audit_ledger_sha256","variant_ledger_sha256","sample_ledger_sha256","returned_variants","unreportable_rows_statistics_withheld"))results.put(key,audit.get(key));long reportable=((Number)audit.get("returned_variants")).longValue()-((Number)audit.get("unreportable_rows_statistics_withheld")).longValue();results.put("reportable_variants",reportable);results.put("scientific_outcome",reportable==0?"no-reportable-associations":"reportable-associations");}
                    else if(nullSpec==null){results.put("grm","grm.mtx");results.put("sample_ids","sample-ids.txt");results.put("grm_sha256",audit.get("grm_sha256"));results.put("sample_ids_sha256",audit.get("sample_ids_sha256"));results.put("pair_coverage",audit.get("pair_coverage"));}
                    else {results.put("null_model","null-model.rda");results.put("variance_ratios","variance-ratios.tsv");for(String key:List.of("null_model_sha256","variance_ratios_sha256","original_model_export_sha256"))results.put(key,audit.get(key));results.put("recipe_sha256",spec.recipeSha256());results.put("assembly_declaration",spec.assembly());results.put("trait_type",nullSpec.trait());results.put("loco",nullSpec.loco());results.put("sparse_fit",nullSpec.sparse());results.put("sparse_variance_ratio",nullSpec.sparseRatio());results.put("cohort_ledger_sha256",hash(directory.resolve("cohort-ledger.tsv")));results.put("phenotype_admission_sha256",hash(directory.resolve("phenotype-admission.json")));}
                    Files.writeString(directory.resolve("results.json"),RegenieRuntime.json(results)+"\n",StandardCharsets.UTF_8);session.put("results",results);manifest.put("analysis_status","completed-with-review-warnings");
                }catch(SaigeRuntime.Unavailable unavailable) {
                    code[0]=3;manifest.put("analysis_status","skipped-unavailable-dependency");session.put("error",unavailable.getMessage());
                    RnaToolRuntime.quarantine(directory);session.put("retained_files_location","partial/");warnings.add(0,"ANALYSIS SKIPPED: "+unavailable.getMessage()+". Required action: follow the runtime recovery below; any prior files are retained under partial/ and are not admitted analysis results.");
                }catch(Exception failure) {
                    code[0]=2;manifest.put("analysis_status","failed");session.put("error",failure.toString());
                    RnaToolRuntime.quarantine(directory);session.put("retained_files_location","partial/");warnings.add(0,"ANALYSIS FAILED: "+failure.getMessage()+". Required action: inspect session.json and the original logs under partial/, correct the named input/runtime/process/audit cause, and rerun to a new output directory. Retained partial outputs are not admitted results.");
                }
                session.put("status",manifest.get("analysis_status"));session.put("warnings",warnings);
                Files.writeString(directory.resolve("session.json"),RegenieRuntime.json(session)+"\n",StandardCharsets.UTF_8);
                Files.writeString(directory.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status")))+"\n",StandardCharsets.UTF_8);
                Files.writeString(directory.resolve("WARNINGS.md"),"# SAIGE "+(code[0]==0?"WARNINGS REQUIRE REVIEW":code[0]==3?"ANALYSIS SKIPPED":"ANALYSIS FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\n"+SaigeRuntime.recovery()+"\n",StandardCharsets.UTF_8);
            });
            err.println((code[0]==0?"ANALYSIS WARNINGS: ":code[0]==3?"ANALYSIS SKIPPED: ":"ANALYSIS FAILED: ")+Path.of(options.get("out"),"WARNINGS.md"));
            if(code[0]==0)out.println("Original SAIGE operation and native audit completed: "+options.get("out"));
            return code[0];
        }catch(Exception failure){err.println("jlinalg: "+failure.getMessage()+"\n"+SaigeRuntime.recovery());return 2;}
    }
    @FunctionalInterface private interface Verifier {void verify()throws Exception;}
    private static Map<String,String> parse(String[] args)throws Exception {
        var direct=options(args,ALLOWED);if(!direct.containsKey("options-file"))return direct;
        Path path=Path.of(direct.get("options-file")).toAbsolutePath().normalize();
        if(!Files.isRegularFile(path)||Files.size(path)>1048576)throw new IOException("Require a UTF8 JSON options object at most 1MiB");
        byte[] raw=Files.readAllBytes(path);Object value=SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString());
        if(!(value instanceof Map<?,?> map))throw new IOException("Options must be a JSON object");
        var result=new LinkedHashMap<String,String>();
        for(var entry:map.entrySet()) {
            String key=String.valueOf(entry.getKey());
            if(!ALLOWED.contains(key)||key.equals("options-file")||!(entry.getValue() instanceof String text))throw new IOException("Unknown, recursive or non-string SAIGE option");
            result.put(key,text);
        }
        result.putAll(direct);result.put("options-sha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw)));
        return result;
    }
    private static void verifyOptions(Map<String,String> options)throws IOException {
        if(options.containsKey("options-file")&&!hash(Path.of(options.get("options-file"))).equals(options.get("options-sha256")))throw new IOException("SAIGE CLI options file changed during execution");
    }
    static String help(){return """
        saige-tools --recipe TYPED_RECIPE.json --home ADMITTED_PRIVATE_SAIGE_HOME
          --out NEW_DIRECTORY [--timeout 3600] [--options-file ASCII_UTF8_OPTIONS.json]
        Original SAIGE1.5.2/R4.5.2 on Ubuntu24.04 Linux x86_64, including Linux
        Java in WSL. Typed operation=sparse-grm uses immutable diploid autosomal
        PLINK BED/BIM/FAM snapshots. Declare assembly, random_markers, cutoff,
        min_maf, max_missing, diagonal=observed, seed=1 and resource limits.
        Every output sample and stored matrix entry is audited; canonical matrix
        and ID bytes match the retained original outputs. Random-marker candidate
        screening does not certify complete above-cutoff pair coverage. Typed
        operation=fit-null declares binary/quantitative coding, covariates/category
        levels, complete-case policy, IRNT, full/sparse GRM, LOCO, variance-ratio bins
        and numerical controls. A sparse fit consumes a completed typed sparse-grm
        bundle. Every cohort/outcome/design span and base/LOCO residual is audited.
        operation=single reuses a completed hash-bound typed null-model bundle and
        declares exact chromosome, PLINK allele order, extra-sample policy, initial
        and post-imputation QC, dosage cleanup, SPA/Firth/fast/resampling choices.
        Complete variants/counts/score paths/normal-P and effect intervals audited;
        original sparse-PCG/SPA/Firth/ER estimators retained. Explicit unreportable
        policy withholds canonical statistics and retains original rows/ledgers.
        BGEN/VCF, conditional/gene/LD integration remains separate active work.
        Exit0 completed with scientific review warnings; exit3 absent/changed
        dependencies SKIP analysis; exit2 invalid input/process/timeout/audit failure.
        Read WARNINGS.md and its Required action. Failed/skipped partials retained.
        Analysis never installs; python3 tools/literature/install_saige.py --help.
        Existing outputs are preserved. ASCII-named UTF8 JSON string-options files
        support Unicode paths; direct flags override their values.
        """;}
}
