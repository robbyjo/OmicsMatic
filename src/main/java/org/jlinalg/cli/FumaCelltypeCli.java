/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.enrichment.FumaCelltypeAnalysis;
import org.jlinalg.enrichment.FumaDegEnrichment;
import static org.jlinalg.cli.FollowupSupport.*;

/** Typed classic FUMA cell-type gene-property workflow using original MAGMA fits. */
final class FumaCelltypeCli {
    private FumaCelltypeCli() { }
    private static final Set<String> ALLOWED=Set.of("method","celltype-options-file","home","runtime-profile","gene-bundle","datasets","build","expression-id","correction","step2","step3","gene-coverage","missing-values","max-bytes","max-genes","max-datasets","max-cells","max-values","max-models","max-calls","max-ledger-rows","max-operations","max-output-bytes","timeout","deadline","out");
    private static final Set<String> PATHS=Set.of("home","gene-bundle","datasets","out");
    private static final String RECOVERY="Run fitting on Windows x86_64 Java with an admitted original MAGMA 1.10 windows-x86_64-static home. Where permitted run python tools/literature/install_magma.py --allow-download --environment NEW_WINDOWS_HOME, then supply --home NEW_WINDOWS_HOME. If installation is unavailable or forbidden, skip this fitting analysis until the pinned runtime is supplied on a permitted supported host. A Linux MAGMA profile has not been validated for this command. Preserve existing homes and failed attempts; choose a NEW analysis output after correcting the named cause.";
    private static final String SCOPE="CLASSIC SUPPLIED-RESOURCE CELL-TYPE GENE-PROPERTY WORKFLOW. Original MAGMA 1.10 fits condition on supplied Average expression; independent Java code corrects complete per-dataset/global families and interprets optional within/cross-dataset models. This does not compute expression, Average values, orthologues, gene IDs or gene-level GWAS results. EWCE, CELLEX, CEPO, MAGMA.Celltyping, gsMAP and full hosted/historical FUMA are separate. Required action: declare exact resources, build, correction, missingness and step choices; inspect all gene/fit/join ledgers, native logs and effective NGENES. Cell Ranger remains excluded.";
    static int run(String[] args,PrintStream out,PrintStream err) {
        if(Arrays.asList(args).contains("--help")){out.println(help());return 0;}
        try {
            var o=parse(args);if(o.containsKey("method")&&!o.get("method").equals("cell-type"))throw new IOException("--method must be cell-type");
            for(String key:List.of("home","runtime-profile","gene-bundle","datasets","build","expression-id","correction","step2","step3","gene-coverage","missing-values","out"))required(o,key);
            int[] code={0};externalTransaction(o,"fuma-celltype",(directory,manifest)->{
                var warnings=new ArrayList<String>();warnings.add(SCOPE);var session=new LinkedHashMap<String,Object>();var sources=new LinkedHashMap<Path,String>();var preparedHashes=new LinkedHashMap<Path,String>();session.put("source_commit","bdfbf06b5dd24037ec3c7497d773fad3c2656150");session.put("execution_staging_prefix",directory.toString());session.put("published_prefix",Path.of(o.get("out")).toAbsolutePath().normalize().toString());
                for(String key:List.of("build","expression-id","correction","step2","step3","gene-coverage","missing-values","runtime-profile"))session.put(key,o.get(key));
                try {
                    if(!o.get("runtime-profile").equals("windows-x86_64-static"))throw new IOException("--runtime-profile must be the explicit admitted windows-x86_64-static MAGMA1.10 profile");
                    for(String key:List.of("build","expression-id"))FumaCelltypeInputs.identity(o.get(key));if(!o.get("build").matches("[A-Za-z0-9_.-]{1,80}"))throw new IOException("Declare a simple explicit build identity; no build inference or liftover");
                    if(directory.toString().codePoints().anyMatch(c->c<32||c>126))throw new IOException("Original Windows MAGMA fitting requires an ASCII output parent path; choose an ASCII parent for --out and place Unicode input paths/final directory names in the UTF-8 options carrier");
                    boolean step2=explicitBoolean(o,"step2"),step3=explicitBoolean(o,"step3");if(step3&&!step2)throw new IOException("--step3 true requires --step2 true; the original nests cross-dataset analysis inside within-dataset selection");
                    FumaCelltypeAnalysis.Correction correction=switch(o.get("correction")){case "bonferroni"->FumaCelltypeAnalysis.Correction.BONFERRONI;case "BH"->FumaCelltypeAnalysis.Correction.BH;case "BY"->FumaCelltypeAnalysis.Correction.BY;case "holm"->FumaCelltypeAnalysis.Correction.HOLM;case "hochberg"->FumaCelltypeAnalysis.Correction.HOCHBERG;case "hommel"->FumaCelltypeAnalysis.Correction.HOMMEL;default->throw new IOException("--correction must be bonferroni, BH, BY, holm, hochberg or hommel");};
                    boolean coverage=sourcePolicy(o,"gene-coverage"),missing=sourcePolicy(o,"missing-values");int bytes=integer(o,"max-bytes",268435456,1,1073741824),genes=integer(o,"max-genes",1000000,2,2000000),datasets=integer(o,"max-datasets",64,1,256),cells=integer(o,"max-cells",10000,1,1000000),values=integer(o,"max-values",20000000,1,100000000),models=integer(o,"max-models",100000,1,1000000),calls=integer(o,"max-calls",256,1,10000),ledger=integer(o,"max-ledger-rows",2000000,1,20000000),timeout=integer(o,"timeout",3600,1,86400),deadline=integer(o,"deadline",7200,1,86400);
                    long operations=positiveLong(o,"max-operations",100000000L,10000000000L),output=positiveLong(o,"max-output-bytes",1073741824L,1099511627776L),started=System.nanoTime();FumaDegEnrichment.Check check=()->{if(Thread.currentThread().isInterrupted()||System.nanoTime()-started>deadline*1000000000L)throw new IllegalStateException("Cell-type analysis interrupted or deadline exceeded; correct the cause or adjust --deadline and rerun to a NEW output");};
                    var inputLimits=new FumaCelltypeInputs.Limits(bytes,genes,datasets,cells,values);if(o.containsKey("celltype-options-file"))FumaCelltypeInputs.raw(Path.of(o.get("celltype-options-file")),sources,1048576);
                    var bundle=FumaCelltypeInputs.bundle(Path.of(o.get("gene-bundle")),o.get("build"),sources,inputLimits);var resources=FumaCelltypeInputs.datasets(Path.of(o.get("datasets")),sources,inputLimits,missing,check);long coverageRows=FumaCelltypeInputs.coverage(resources,bundle.genes(),coverage,directory.resolve("resource-coverage.tsv"),warnings,ledger,check);if(resources.size()>calls)throw new IOException("Complete marginal call plan exceeds --max-calls");long requested=resources.stream().mapToLong(d->d.cells().size()).sum();if(requested>models)throw new IOException("Complete marginal model population exceeds --max-models");
                    Path prepared=directory.resolve("prepared");Files.createDirectory(prepared);Path raw=prepared.resolve("genes.raw");Files.copy(bundle.raw(),raw,StandardCopyOption.COPY_ATTRIBUTES);preparedHashes.put(raw,hash(raw));var tables=new LinkedHashMap<String,Path>();var resourceLedger=new ArrayList<String[]>();int index=0;for(var dataset:resources){check.run();Path path=prepared.resolve(String.format(java.util.Locale.ROOT,"d%04d.properties.tsv",++index));FumaCelltypeInputs.write(path,dataset.columns(),dataset.rows());preparedHashes.put(path,hash(path));tables.put(dataset.id(),path);resourceLedger.add(new String[]{dataset.id(),dataset.resourceId(),dataset.source().toString(),sources.get(dataset.source()),directory.relativize(path).toString(),""+dataset.rows().size(),""+dataset.cells().size()});}
                    table(directory.resolve("datasets.tsv"),List.of("dataset","resource_id","source","sha256","prepared_path","genes","tested_cells"),resourceLedger);table(directory.resolve("gene-analysis-universe.tsv"),List.of("GENE"),bundle.genes().stream().map(g->new String[]{g}).toList());session.put("gene_bundle_metadata",bundle.metadata());session.put("gene_universe_size",bundle.genes().size());session.put("complete_resource_datasets",resources.size());session.put("resource_coverage_rows",coverageRows);FumaCelltypeInputs.verify(sources);
                    var runtime=new MagmaRuntime(Path.of(o.get("home")),directory,session);var runner=new FumaCelltypeRun(directory,raw,runtime,preparedHashes,warnings,session,new FumaCelltypeAnalysis.Limits(cells,models,operations),check,timeout,calls,output,bundle.genes().size(),ledger,coverageRows);runner.run(resources,tables,correction,step2,step3);runtime.verify();FumaCelltypeInputs.verify(sources);FumaCelltypeInputs.verify(preparedHashes);session.put("status","completed-with-review-warnings");manifest.put("analysis_status","completed-with-review-warnings");
                    warnings.add("SOURCE AND NUMERICAL REVIEW: globally adjusted P < 0.05 controls conditional selection. Current source form defaults are not inferred for a historical paper. Source step2 branch order is retained, including an inequality that differs from tutorial introductory prose. Direct source P values may underflow; lost log values are not invented. Step3 matrices use fifteen-significant-digit numeric serialization to follow the original R write stage. Extra gene-info/abbreviate=0 settings only expand original reports. Required action: inspect source differences, raw fits, effective populations and all conditional states before biological interpretation.");
                }catch(MagmaRuntime.Unavailable unavailable){code[0]=3;session.put("status","skipped-dependency-unavailable");session.put("cause",unavailable.toString());manifest.put("analysis_status","skipped-dependency-unavailable");RnaToolRuntime.quarantine(directory);session.put("retained_files_location","partial/");warnings.add(0,"ANALYSIS SKIPPED: "+unavailable.getMessage()+". Required action: "+RECOVERY);}
                catch(Exception failure){code[0]=2;session.put("status","failed");session.put("cause",failure.toString());manifest.put("analysis_status","failed");RnaToolRuntime.quarantine(directory);session.put("retained_files_location","partial/");warnings.add(0,"ANALYSIS FAILED: "+failure.getMessage()+". Required action: inspect partial/ and the model/coverage/fit diagnostics, correct the named input, policy, identity or capacity cause, then rerun to a NEW output. Retained tables are partial diagnostics and are not an admitted analysis.");}
                var identities=new LinkedHashMap<String,String>();sources.forEach((p,h)->identities.put(p.toString(),h));session.put("input_sha256",identities);manifest.put("input_sha256",identities);session.put("warnings",warnings);Files.writeString(directory.resolve("session.json"),RegenieRuntime.json(session)+"\n");Files.writeString(directory.resolve("analysis-status.json"),RegenieRuntime.json(Map.of("status",session.get("status"),"exit_code",code[0]))+"\n");Files.writeString(directory.resolve("WARNINGS.md"),(code[0]==0?"# ANALYSIS WARNINGS\n\n":code[0]==3?"# ANALYSIS SKIPPED - MAGMA RUNTIME UNAVAILABLE\n\n":"# ANALYSIS FAILED\n\n")+String.join("\n\n",warnings)+"\n");
            });
            err.println((code[0]==0?"CELL-TYPE WARNINGS: ":code[0]==3?"CELL-TYPE ANALYSIS SKIPPED: ":"CELL-TYPE ANALYSIS FAILED: ")+Path.of(o.get("out")).toAbsolutePath().resolve("WARNINGS.md"));if(code[0]==0)out.println("Classic cell-type workflow completed: "+o.get("out"));return code[0];
        }catch(Exception failure){err.println("CELL-TYPE ANALYSIS FAILED: "+failure.getMessage()+"\nRequired action: correct the named option, file or output path and rerun to a NEW directory. "+RECOVERY);return 2;}
    }
    private static boolean explicitBoolean(Map<String,String> options,String key)throws IOException {String value=required(options,key);if(!Set.of("true","false").contains(value))throw new IOException("--"+key+" must be explicit true or false");return value.equals("true");}
    private static boolean sourcePolicy(Map<String,String> options,String key)throws IOException {String value=required(options,key);if(!Set.of("error","source-default").contains(value))throw new IOException("--"+key+" must be error or source-default");return value.equals("source-default");}
    private static long positiveLong(Map<String,String> options,String key,long fallback,long maximum)throws IOException {long value=Long.parseLong(options.getOrDefault(key,""+fallback));if(value<1||value>maximum)throw new IOException("--"+key+" outside 1.."+maximum);return value;}
    private static Map<String,String> parse(String[] args)throws Exception {var direct=options(args,ALLOWED);if(!direct.containsKey("celltype-options-file"))return direct;Path path=Path.of(direct.get("celltype-options-file")).toAbsolutePath().normalize();byte[] raw=AdmixtureInputs.raw(path,1048576);var result=new LinkedHashMap<String,String>();for(var entry:AdmixtureInputs.decode(raw).entrySet()){String key=entry.getKey().toString();if(!ALLOWED.contains(key)||key.equals("celltype-options-file")||!(entry.getValue() instanceof String value))throw new IOException("Unknown, recursive or non-string cell-type option: "+key);result.put(key,PATHS.contains(key)?path.getParent().resolve(value).normalize().toString():value);}result.putAll(direct);result.put("options-sha256",AdmixtureInputs.digest(raw));return result;}
    static String help(){return """
        fuma-tools --method cell-type --celltype-options-file OPTIONS.json
        Explicit required options:
          --runtime-profile windows-x86_64-static --home ADMITTED_MAGMA_1_10_HOME
          --gene-bundle SEALED_MAGMA_GENE_BUNDLE --datasets DATASETS.tsv
          --build BUILD --expression-id RELEASE --correction bonferroni|BH|BY|holm|hochberg|hommel
          --step2 true|false --step3 true|false --gene-coverage error|source-default
          --missing-values error|source-default --out NEW_DIRECTORY_IN_ASCII_PARENT
        Step3 requires step2. Dataset TSV header: dataset/resource_id/path, tab separated;
          property paths resolve relative to that manifest. Each property TSV requires
          GENE, distinct ASCII cell labels and exact precomputed Average. Rows have exact
          unique ASCII gene IDs and finite decimal values or explicitly permitted NA.
          Source-default missingness stays upstream; Java never imputes expression.
        Both correction families use every marginal test. Global adjusted P < .05 selects
          optional conditional pairs. All resource, original-fit and join rows are retained.
        Optional capacities: --max-bytes 268435456 --max-genes 1000000 --max-datasets 64
          --max-cells 10000 --max-values 20000000 --max-models 100000 --max-calls 256
          --max-ledger-rows 2000000 --max-operations 100000000 --max-output-bytes 1073741824
          --timeout 3600 per original call --deadline 7200 seconds for the workflow.
        Capacities do not reserve JVM heap; output bytes are checked between completed calls.
        Options-file values are strings and paths resolve relative to that file. Unicode
          input paths and final directory names belong in an ASCII UTF-8 carrier;
          the parent of --out must be ASCII because original fitting uses that parent.
        Read WARNINGS.md, session.json, analysis-status.json and every complete fit/coverage
          ledger. Exit0 completed with warnings;3 dependency unavailable;2 failed. Failed or
          skipped attempts retain diagnostics under partial/. Existing outputs are preserved.
        Original calls use a temporary staging prefix recorded in session.json; all relative
          bundle files move together to published_prefix. Logs keep actual execution paths.
        """+SCOPE+"\nRequired action: "+RECOVERY;}
}
