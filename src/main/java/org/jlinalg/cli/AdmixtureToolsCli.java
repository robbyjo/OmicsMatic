/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.FollowupSupport.*;

/** Typed original ADMIXTURE fitting, supervision and fixed-model projection. */
final class AdmixtureToolsCli {
    private AdmixtureToolsCli() { }
    private static final Set<String> ALLOWED=Set.of("recipe","home","out","timeout","options-file");
    static int run(String[] args,PrintStream out,PrintStream err) {
        if(args.length==1&&args[0].equals("--help")){out.println(help());return 0;}
        try {
            Map<String,String> options=parse(args);required(options,"out");int[] code={0};
            externalTransaction(options,"original-admixture-typed-audited-operation",(directory,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();session.put("tool","ADMIXTURE");session.put("requested_options",options);
                try {
                    int timeout=integer(options,"timeout",3600,1,86400);AdmixtureInputs.Spec spec=AdmixtureInputs.read(Path.of(required(options,"recipe")));verifyOptions(options);
                    Files.copy(spec.recipe(),directory.resolve("supplied-recipe.json"));if(!hash(directory.resolve("supplied-recipe.json")).equals(spec.recipeHash()))throw new IOException("ADMIXTURE recipe changed during snapshot");session.put("operation",spec.operation());session.put("version",spec.version());session.put("recipe_sha256",spec.recipeHash());session.put("recipe",spec.source());
                    String supplied=options.get("home");if(supplied==null||supplied.isBlank())throw new AdmixtureRuntime.Unavailable("No admitted original ADMIXTURE --home supplied; analysis never installs dependencies");
                    AdmixtureRuntime.Context runtime=AdmixtureRuntime.open(Path.of(supplied),spec.version());AdmixtureRuntime.provenance(runtime,session);
                    AdmixtureInputs.Prepared inputs=AdmixtureInputs.prepare(spec,directory);var sources=new TreeMap<String,String>();inputs.immutable().forEach((p,h)->sources.put(p.toString(),h));session.put("immutable_input_sha256",sources);inputs.verify();runtime.verify();
                    int exit=AdmixtureRuntime.execute(runtime,AdmixtureInputs.command(inputs),directory,spec.threads(),timeout,spec.limits().output(),session);inputs.verify();runtime.verify();verifyOptions(options);
                    if(exit!=0)throw new IOException("Original ADMIXTURE returned "+exit+"; inspect fit.stdout/fit.stderr. A failed point estimate or bootstrap is not a completed analysis");
                    Map<String,Object> audit=AdmixtureOutputs.audit(inputs,directory);inputs.verify();runtime.verify();verifyOptions(options);Files.writeString(directory.resolve("native-audit.json"),RegenieRuntime.json(audit)+"\n");
                    var results=new LinkedHashMap<String,Object>();results.put("tool","ADMIXTURE");results.put("operation",spec.operation());results.put("version",spec.version());results.put("k",spec.k());results.put("samples",inputs.samples().size());results.put("markers",inputs.markers().size());results.put("native_audit_sha256",hash(directory.resolve("native-audit.json")));if(audit.containsKey("model_sha256"))results.put("model_sha256",audit.get("model_sha256"));Files.writeString(directory.resolve("results.json"),RegenieRuntime.json(results)+"\n");
                    if(spec.major().kind().equals("iterations"))warnings.add("FIXED ITERATION RESULT: the point fit stopped after the explicitly requested "+spec.major().argument()+" iterations. Original 'Converged' wording does not certify numerical convergence. Required action: assess the retained objective/delta history and run an explicitly chosen convergence/sensitivity analysis before interpreting the fitted components.");
                    warnings.add("SCIENTIFIC REVIEW REQUIRED: original ADMIXTURE fits the requested model. Native Java checks the complete sample/marker family, counted BIM allele2 orientation, model ploidy, supplied Q/P likelihood bounds and declared penalty. Q/P are original six-decimal values; the central reconstructed likelihood is diagnostic and Q rows are never renormalized. Required action: inspect input_review, sample-ledger.tsv, marker-ledger.tsv, native-audit.json and original fit.stdout/fit.stderr. Confirm assembly, allele/order harmonization, marker and unrelated/reference eligibility, LD pruning, missingness, genotype coding, K and seed suitability. These checks do not establish a global optimum, model calibration, historical/full-paper reproduction or social/geographic identity.");
                    if(spec.ploidy()==1||!spec.coding().equals("diploid-hardcalls"))warnings.add("EXPLICIT PLOIDY MODEL: "+spec.coding()+", model ploidy="+spec.ploidy()+". Homozygously coded haploid calls receive one model allele; pseudohaploid input analyzed as diploid receives twice that weight. Required action: verify source genotype coding and the intended biological model; no sex-specific ploidy or crop-dosage conversion is inferred.");
                    if(spec.operation().equals("supervised"))warnings.add("CALLER-SUPPLIED REFERENCES: labels are joined by exact FID/IID and staged with safe aliases. Component order is recovered from all reference Q rows, including the original1e-5 numerical floor. Required action: review component-labels.tsv and reference eligibility; a supplied label is not an inferred social identity.");
                    if(spec.operation().equals("project"))warnings.add("FIXED-MODEL PROJECTION: the completed training model's P, exact BIM bytes, assembly, version and component order are verified; new target samples are allowed. The declared projection ploidy policy is "+spec.projectionPloidy()+"; explicit-target-model permits separately reviewed target ploidy/coding without changing the learned allele frequencies or converting genotype calls. Required action: assess training-panel suitability,target/reference relationships and target likelihood weighting. The fixed P matrix is preserved exactly; this operation does not learn a novel ancestral population.");
                    if(spec.cv()>0)warnings.add("ORIGINAL CROSS-VALIDATION: the recorded scalar is original "+spec.cv()+"-fold held-out-genotype error, not sample-level cross-validation or an independently reconstructed prediction score. Required action: compare explicitly planned K/seed runs and document the selection/aggregation rule; this command does not silently choose K or select a favorable replicate.");
                    if(spec.boot()>0)warnings.add("ORIGINAL BOOTSTRAP UNCERTAINTY: all "+spec.boot()+" replicates completed with explicit block length "+spec.block()+" markers. Q-standard-errors.tsv and signed Q-bias.tsv retain upstream values; native checks verify complete finite matrices but do not certify uncertainty calibration. Required action: assess ordered-marker linkage, block length, replicate count and minor stopping rule. Oversized default blocks can crash upstream; no scientific parameter is silently retuned after failure.");
                    if(((Number)audit.get("precision_unbounded_cells")).longValue()>0)warnings.add(0,"UNBOUNDED PRINTED-PARAMETER PRECISION: "+audit.get("precision_unbounded_cells")+" observed cells have a negative-infinite lower likelihood bound. Retained Q/P are rounded original parameters, not a precise likelihood reconstruction. Required action: review the explicitly permitted unbounded interval and do not treat the central likelihood as exact or as a calibrated p value.");
                    if(runtime.emulated())warnings.add("UNSUPPORTED LEGACY VERSION / EXPLICIT EMULATION: original1.3.0 runs through pinned private QEMU8.2.2 with CPUqemu64. Direct execution failed on the validated WSL host because legacy vsyscall is disabled. Required action: retain this profile and its performance/compatibility limits; no current-version substitution or global kernel configuration change was made. This does not establish reported1.23 or exact1.3 historical parity.");
                    for(String name:List.of("fit.stderr","fit.stdout")){
                        var notices=new StringBuilder();long found=0;try(var reader=Files.newBufferedReader(directory.resolve(name))){for(String line;(line=AdmixtureInputs.line(reader,spec.limits().line()))!=null;){if(name.equals("fit.stderr")&&!line.isBlank()||line.toLowerCase(Locale.ROOT).contains("warning")){found++;if(notices.length()<8192){String next=(notices.isEmpty()?"":" | ")+line;notices.append(next,0,Math.min(next.length(),8192-notices.length()));}}}}
                        if(found>0)warnings.add("ORIGINAL NOTICES in "+name+" ("+found+" lines; bounded excerpt): "+notices+". Required action: read the complete retained original log before using this result.");
                    }
                    manifest.put("analysis_status","completed-with-review-warnings");
                }catch(AdmixtureRuntime.Unavailable unavailable){code[0]=3;manifest.put("analysis_status","skipped-unavailable-dependency");session.put("error",unavailable.getMessage());RnaToolRuntime.quarantine(directory);session.put("retained_files_location","partial/");warnings.add(0,"ANALYSIS SKIPPED: "+unavailable.getMessage()+". Required action: follow the runtime recovery below; retained files under partial/ are not completed analysis results.");}
                catch(Exception failure){code[0]=2;manifest.put("analysis_status","failed");session.put("error",failure.toString());RnaToolRuntime.quarantine(directory);session.put("retained_files_location","partial/");warnings.add(0,"ANALYSIS FAILED: "+failure.getMessage()+". Required action: inspect session.json and partial/ logs, correct the named input/runtime/process/numerical cause, and rerun to a new output directory. No successful partial table is admitted.");}
                session.put("status",manifest.get("analysis_status"));session.put("warnings",warnings);Files.writeString(directory.resolve("session.json"),RegenieRuntime.json(session)+"\n");Files.writeString(directory.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",manifest.get("analysis_status")))+"\n");Files.writeString(directory.resolve("WARNINGS.md"),"# ADMIXTURE "+(code[0]==0?"WARNINGS REQUIRE REVIEW":code[0]==3?"ANALYSIS SKIPPED":"ANALYSIS FAILED")+"\n\n"+String.join("\n\n",warnings)+"\n\n"+AdmixtureRuntime.recovery()+"\n");
            });
            err.println((code[0]==0?"ANALYSIS WARNINGS: ":code[0]==3?"ANALYSIS SKIPPED: ":"ANALYSIS FAILED: ")+Path.of(options.get("out"),"WARNINGS.md"));if(code[0]==0)out.println("Original ADMIXTURE operation and native audit completed: "+options.get("out"));return code[0];
        }catch(Exception failure){err.println("jlinalg: "+failure.getMessage()+"\n"+AdmixtureRuntime.recovery());return 2;}
    }
    private static Map<String,String> parse(String[] args)throws Exception {
        var direct=options(args,ALLOWED);if(!direct.containsKey("options-file"))return direct;Path path=Path.of(direct.get("options-file")).toAbsolutePath().normalize();byte[] raw=AdmixtureInputs.raw(path,1048576);Map<?,?> source=AdmixtureInputs.decode(raw);var result=new LinkedHashMap<String,String>();
        for(var e:source.entrySet()){String key=e.getKey().toString();if(!ALLOWED.contains(key)||key.equals("options-file")||!(e.getValue() instanceof String value))throw new IOException("Unknown,recursive or non-string ADMIXTURE CLI option");result.put(key,value);}result.putAll(direct);result.put("options-sha256",AdmixtureInputs.digest(raw));return result;
    }
    private static void verifyOptions(Map<String,String> options)throws IOException {if(options.containsKey("options-file")&&!hash(Path.of(options.get("options-file"))).equals(options.get("options-sha256")))throw new IOException("ADMIXTURE options file changed during execution");}
    static String help(){return """
        admixture-tools --recipe TYPED_RECIPE.json --home ADMITTED_PRIVATE_HOME
          --out NEW_DIRECTORY [--timeout 3600] [--options-file ASCII_UTF8_OPTIONS.json]
        Original ADMIXTURE1.4.0 native Linux x86_64, or explicit unsupported1.3.0
        through pinned private QEMU8.2.2/qemu64. On Windows use Linux Java in WSL.
        Typed fit/supervised/project operations use immutable variant-major PLINK
        BED/BIM/FAM. Declare exact version,assembly,input review,K,seed,threads,
        global ploidy/coding,method,acceleration,major/minor stopping rules,
        CV folds,bootstrap replicates/explicit block,penalty and resource limits.
        Supervision joins an explicit fid/iid/population TSV; '-' means unknown.
        Projection consumes a completed typed fitted model with exact BIM/P and
        declared assembly,version,ploidy/component identities; new targets allowed.
        Native likelihood audits count BIM allele2 and retain printed precision.
        No hidden K/seed choice,replicate alignment,ploidy/allele conversion or
        ancestry identity inference. Original CV/bootstrap calibration is upstream.
        Exit0 completed with review warnings; exit3 unavailable/forbidden dependency
        skips; exit2 input/process/timeout/audit failure. Read WARNINGS.md and its
        Required action. Failed/skipped partials are quarantined; outputs preserved.
        Analysis never installs. python3 tools/literature/install_admixture.py --help.
        ASCII-named UTF8 string-options JSON supports Unicode paths; flags override.
        """;}
}
