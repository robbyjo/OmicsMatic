/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Typed exact-edition original fastp execution with complete population/report audit. */
final class FastpToolsCli {
    private FastpToolsCli() { }
    static final String ACTION="Required action: inspect top-level WARNINGS.md,recipe-request/replay.json,processing/input-statistics.json,audit/output-audit.json,audit/read-dispositions.tsv and original reports. Require valid COMPLETED.json plus fastp-tools --verify DIRECTORY before downstream use. The admitted scope is complete populations,source report statistics and conservative transformation/conservation envelopes;upstream adapter/poly-tail/overlap/Bloom choices and biological accuracy are not independently reproduced. Missing paired reads can reflect failure of both mates or duplicate suppression. Original insert-size histograms sample thread0;0.22.0 duplicate buffers/counters are non-atomic. Exact supported Linux x86_64 runtime,completed matching setup report and permitted helpers are required for execution;portable verification uses Java and bounded scratch only. Install/download only where permitted using explicit installer flags. SKIP3 means unavailable/forbidden runtime,capability or requested duplicate-memory reservation: skip dependent analysis and resolve the named cause/use an adequate supported host. Accuracy6 needs24GiB Bloom buffer plus overhead;never silently lower requested accuracy or guess a missing edition,barcode position or paper option. FAIL2 means invalid input,integrity,process,capacity,cancellation or finalization failure:no result is admitted. Preserve raw/partial outputs and diagnostics,correct the named cause and retry to NEW output paths. Increase explicit capacities and Java heap only where resources permit;never truncate a population. Setup-only completion,exit zero and provisional stage records do not admit user results. Portable verification does not rerun upstream processing,readmit the producer host or authenticate execution history. Hashes establish integrity,not authenticity. Cell Ranger remains excluded.";
    static int run(String[] args,PrintStream out,PrintStream err){
        Path root=null;boolean owns=false;Runnable check=()->{if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("fastp interrupted");};
        try{
            if(args.length==1&&args[0].equals("--help")){emit(out,help());return 0;}
            if(args.length==1&&args[0].equals("--example")){emit(out,FastpJson.json(FastpRecipe.example()));return 0;}
            if(args.length==1&&args[0].equals("--runtime-profiles")){emit(out,FastpJson.json(FastpRuntime.profiles()));return 0;}
            if(args.length==2&&args[0].equals("--export-runtime-setup")){FastpSetup.export(Path.of(args[1]));emit(out,"fastp runtime setup files exported;no installation or analysis was performed.\n"+ACTION);return 0;}
            if(args.length==2&&args[0].equals("--verify")){var result=FastpBundle.verify(Path.of(args[1]),"COMPLETED.json",check);emit(out,"fastp complete scoped result verified by native population/report/envelope replay;manifest SHA-256 "+result.sha256()+". Upstream inference and biological accuracy are not reproduced.");return 0;}
            var options=new LinkedHashMap<String,String>();for(int i=0;i<args.length;i+=2)if(i+1>=args.length||!Set.of("--recipe","--home","--setup-report","--out").contains(args[i])||options.putIfAbsent(args[i],args[i+1])!=null)throw new IOException("Require unique fastp --recipe,--home,--setup-report and --out value options");
            if(!options.containsKey("--recipe")||!options.containsKey("--out"))throw new IOException("fastp requires --recipe and --out");root=Path.of(options.get("--out")).toAbsolutePath().normalize();
            for(String name:java.util.List.of("--home","--setup-report"))if(options.containsKey(name)){Path source=Path.of(options.get(name)).toAbsolutePath().normalize();if(source.startsWith(root)||root.startsWith(source))throw new IOException("fastp output and runtime/setup trees must be disjoint");}
            FastpBundle.create(root);owns=true;
            if(!options.containsKey("--home")||!options.containsKey("--setup-report"))throw new FastpRuntime.Unavailable("Exact managed --home and completed matching --setup-report are required;skip dependent analysis until permitted setup is complete");
            var result=FastpWorkflow.run(root,Path.of(options.get("--recipe")).toAbsolutePath().normalize(),Path.of(options.get("--home")),Path.of(options.get("--setup-report")),check);
            emit(out,"fastp original processing and complete scoped audit completed: "+root+"\n"+ACTION);for(String warning:result.warnings())emit(err,"fastp WARNING: "+warning);if(FastpWorkflow.mainReads(result)==0)emit(err,"fastp WARNING NO_MAIN_PASSING_READS: skip dependent main-read analyses;review failed/orphan populations and filters.");return 0;
        }catch(Exception failure){
            int code=failure instanceof FastpRuntime.Unavailable?3:2;String label=code==3?"SKIP3 — ANALYSIS SKIPPED":"FAIL2 — NO RESULT ADMITTED",detail=failure.toString();if(detail.length()>16384)detail=detail.substring(0,16384)+" [diagnostic bounded;raw inputs remain complete]";emit(err,"fastp "+label+": "+detail+"\n"+ACTION);
            if(owns)try{
                if(Files.exists(root.resolve("COMPLETED.json")))Files.delete(root.resolve("COMPLETED.json"));
                FastpFiles.writeJson(root.resolve("FAILURE.json"),Map.of("schema_version",1,"tool",FastpBundle.TOOL,"exit_code",code,"status",label,"error",detail,"scoped_result_admitted",false,"required_action",ACTION));
                Files.writeString(root.resolve("FAILURE.md"),"# fastp "+label+"\n\n"+detail+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            }catch(IOException diagnostic){emit(err,"fastp failure diagnostics could not be fully written: "+diagnostic+"\n"+ACTION);}return code;
        }
    }
    private static void emit(PrintStream stream,String text){stream.writeBytes((text+"\n").getBytes(StandardCharsets.UTF_8));}
    static String help(){return """
        fastp-tools --recipe RECIPE.json --home EXACT_MANAGED_HOME --setup-report COMPLETED_SETUP_REPORT --out NEW_DIRECTORY
        fastp-tools --verify DIRECTORY
        fastp-tools --export-runtime-setup NEW_DIRECTORY
        fastp-tools --example | --runtime-profiles | --help
        Exact original editions:0.22.0,0.23.2,0.25.0,1.0.1;execution requires Linux x86_64 Java.
        Complete unwrapped four-line plain/gzip single/paired FASTQ;explicit quality/alphabet/plus/pair policies.
        Typed adapter,global/quality cuts,filters,poly-G/X,correction,duplication and orphan choices.
        Complete bounded report/population audit and portable Java replay;all raw inputs/results retained.
        """+ACTION;}
}
