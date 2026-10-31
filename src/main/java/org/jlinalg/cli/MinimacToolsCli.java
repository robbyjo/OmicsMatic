/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.FollowupSupport.*;

/** Typed, bounded original Minimac operations with native input and result audits. */
final class MinimacToolsCli {
    private MinimacToolsCli() { }
    private static final Set<String> ALLOWED=Set.of("recipe","home","out","timeout","options-file");
    static int run(String[] args,PrintStream out,PrintStream err){
        if(args.length==1&&args[0].equals("--help")){out.println(help());return 0;}
        try{
            Map<String,String> options=parse(args);required(options,"out");int[] code={0};
            externalTransaction(options,"original-minimac-typed-audited-operation",(directory,manifest)->{
                var session=new LinkedHashMap<String,Object>();var warnings=new ArrayList<String>();
                session.put("tool","Minimac");session.put("requested_options",options);
                try{
                    int timeout=integer(options,"timeout",3600,1,86400);verifyOptions(options);
                    var spec=MinimacRecipe.read(Path.of(required(options,"recipe")));
                    session.put("operation",spec.operation());session.put("profile",spec.profile());
                    session.put("recipe_sha256",spec.recipeSha256());session.put("recipe",spec.source());
                    boolean needsRuntime=!spec.operation().equals("inspect-reference")||spec.reference().kind().equals("msav");
                    MinimacRuntime.Context runtime=null;
                    if(needsRuntime){
                        String home=options.get("home");
                        if(home==null||home.isBlank())throw new MinimacRuntime.Unavailable("No admitted original Minimac --home supplied; analysis never installs dependencies",null);
                        runtime=MinimacRuntime.open(Path.of(home),spec.profile());MinimacRuntime.provenance(runtime,session);
                    }else if(options.containsKey("home"))throw new IOException("Text-only native reference inspection does not consume --home; remove the unused option");
                    var input=MinimacInputs.prepare(spec,directory,runtime,timeout,session);warnings.addAll(input.warnings());
                    var immutable=new java.util.TreeMap<String,String>();
                    input.snapshots().originalSha256().forEach((path,sha)->immutable.put(path.toString(),sha));
                    session.put("immutable_original_sha256",immutable);
                    Map<String,Object> results=MinimacOperations.run(input,runtime,directory,timeout,session,warnings);
                    input.verify();verifyOptions(options);
                    MinimacInputs.writeJson(directory.resolve("results.json"),results,input.budget());
                    warnings.add("SCIENTIFIC REVIEW REQUIRED: read the supplied recipe, input-snapshots/, reference/target audit ledgers, variant-join.tsv and original process diagnostics. Required action: confirm assembly, literal contig/region, eligibility and independence, normalization, REF/ALT, phasing, explicit person/copy identities, ploidy and the intended parameters/QC. Native checks preserve original values and do not establish biological suitability, empirical accuracy, calibration or historical/server/full-paper reproduction. No allele flip, build conversion, heterozygous phasing, participant selection or version substitution is inferred.");
                    if(spec.operation().equals("check"))warnings.add(0,"INPUT CHECK ONLY: no imputation process was run. Required action: inspect the complete admission ledgers; choose an explicit impute recipe and a new output directory when ready.");
                    if(spec.operation().equals("inspect-reference"))warnings.add(0,"REFERENCE INSPECTION ONLY: the complete reference was audited; no imputation or parameter fitting was run. Required action: review reference-audit.json and reference-selected.tsv before using this panel.");
                    if(runtime!=null&&runtime.emulated())warnings.add("EXPLICIT LEGACY EMULATION: this profile uses its pinned private QEMU runtime. Required action: retain the recorded version/host/performance limitations; no global kernel configuration or current-version substitution was made.");
                    manifest.put("analysis_status","completed-"+spec.operation()+"-with-review-warnings");
                }catch(MinimacRuntime.Unavailable|MinimacRecipe.UnavailableCapability unavailable){
                    code[0]=3;manifest.put("analysis_status","skipped-unavailable-dependency-or-capability");
                    session.put("error",unavailable.toString());RnaToolRuntime.quarantine(directory);session.put("retained_files_location","partial/");
                    warnings.add(0,"ANALYSIS SKIPPED: "+unavailable.getMessage()+". Required action: correct the named dependency/capability or skip the dependent analysis; inspect the recovery below and rerun to new paths. Retained partial files are not completed results.");
                }catch(Exception failure){
                    code[0]=2;manifest.put("analysis_status","failed");session.put("error",failure.toString());
                    RnaToolRuntime.quarantine(directory);session.put("retained_files_location","partial/");
                    warnings.add(0,"ANALYSIS FAILED: "+failure.getMessage()+". Required action: inspect session.json and partial/ logs, correct the named input, resource, process or numerical cause, and rerun to a new output directory. No partial result table is admitted.");
                }
                session.put("status",manifest.get("analysis_status"));session.put("warnings",warnings);
                Files.writeString(directory.resolve("session.json"),MinimacJson.json(session)+"\n");
                Files.writeString(directory.resolve("analysis-status.json"),MinimacJson.json(Map.of("status",manifest.get("analysis_status")))+"\n");
                Files.writeString(directory.resolve("WARNINGS.md"),"# Minimac "+(code[0]==0?"WARNINGS REQUIRE REVIEW":code[0]==3?"ANALYSIS SKIPPED":"ANALYSIS FAILED")
                        +"\n\n"+String.join("\n\n",warnings)+"\n\n"+MinimacRuntime.recovery()+"\n");
            });
            err.println((code[0]==0?"ANALYSIS WARNINGS: ":code[0]==3?"ANALYSIS SKIPPED: ":"ANALYSIS FAILED: ")+Path.of(options.get("out"),"WARNINGS.md"));
            if(code[0]==0)out.println("Minimac operation and complete native audit completed: "+options.get("out"));return code[0];
        }catch(Exception failure){err.println("omicsmatic: "+failure.getMessage()+"\n"+MinimacRuntime.recovery());return 2;}
    }
    private static Map<String,String> parse(String[] args)throws Exception{
        var direct=options(args,ALLOWED);if(!direct.containsKey("options-file"))return direct;
        Path path=Path.of(direct.get("options-file")).toAbsolutePath().normalize();
        byte[] raw=AdmixtureInputs.raw(path,1048576);Map<?,?> source=AdmixtureInputs.decode(raw);var result=new LinkedHashMap<String,String>();
        for(var e:source.entrySet()){
            String key=e.getKey().toString();
            if(!ALLOWED.contains(key)||key.equals("options-file")||!(e.getValue() instanceof String value))
                throw new IOException("Unknown, recursive or non-string Minimac CLI option");
            result.put(key,value);
        }
        result.putAll(direct);result.put("options-sha256",AdmixtureInputs.digest(raw));return result;
    }
    private static void verifyOptions(Map<String,String> options)throws IOException{
        if(options.containsKey("options-file")&&!hash(Path.of(options.get("options-file"))).equals(options.get("options-sha256")))
            throw new IOException("Minimac options file changed during execution");
    }
    static String help(){return """
        minimac-tools --recipe TYPED_RECIPE.json [--home ADMITTED_PRIVATE_HOME]
          --out NEW_DIRECTORY [--timeout 3600] [--options-file ASCII_UTF8_OPTIONS.json]
        Typed prepare-reference / convert-reference / inspect-reference / check /
        impute operations. Explicit original current Min4 4.1.6, historical Min4
        1.0.2 with private QEMU, Min4 1.0.0 with private GOMP, or Min3 2.0.1.
        Original processes need Linux x86_64 Java; on Windows use Java in WSL.
        Text reference inspection is native Java; binary MSAV inspection needs
        its admitted original exporter. Check validates inputs without fitting.
        Recipes declare exact assembly, contig/region, review, identity/copy TSVs,
        column semantics, phasing/missing policies, allele-key exclusions,
        ID and supplied-call/phase-change policies, upstream defaults or explicit
        version-specific overrides, source-R2 post-QC and resource limits.
        Complete original output is checked before explicit post-QC; raw numeric
        strings stay unchanged. No phase reconstruction from legacy GP.
        Exit0 completed with review warnings; exit3 unavailable/forbidden runtime
        or capability skipped; exit2 input/process/resource/audit failure.
        Read WARNINGS.md and Required action. Failed/skipped partials are retained
        under partial/. Existing outputs/runtimes are never replaced.
        Analysis never installs. Setup: python3 tools/literature/install_minimac.py
        --help. Cell Ranger is excluded. UTF8 string-options JSON supports Unicode
        paths; direct flags override. Use a new output directory for each attempt.
        """;}
}

