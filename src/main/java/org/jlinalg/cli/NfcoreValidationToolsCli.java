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

/** Typed user-workflow command; exact upstream runtime and complete declared outputs. */
public final class NfcoreValidationToolsCli {
    private NfcoreValidationToolsCli() { }
    static final String ACTION="This bridge admits only the complete original nf-validation1.1.3 plugin with explicitly selected Nextflow24.04.4. Capture its installed parent directory as resource plugins; analyses never install plugins or contact the plugin registry. This component alone does not qualify nf-core/rnasplice or any paper recipe. Required action: inspect the latest attempt's WARNINGS.md, FAILURE.json when present, original nextflow.log, trace.tsv and process logs. Require COMPLETED.json and successful --verify before downstream use. SKIP3 means the exact runtime, permitted dependency or requested task capability is unavailable: skip dependent analysis and complete permitted setup first. Export setup separately with --export-runtime-setup; downloads/installations require explicit installer flags and are never attempted during analysis. FAIL2 means invalid input, changed evidence, process, capacity, cancellation or finalization failure: no result is admitted. Preserve every attempt, correct the named cause and use NEW paths if the immutable request/capacities must change. --resume uses the same captured recipe, inputs, working files and original Nextflow cache; editing originals is not a request to change the captured analysis. The guard tracks owned local Linux processes, not remote scheduler/service lifecycles. Portable verification checks complete captured bytes, declared input/output contracts, exact command/parameters and original completion/trace consistency; it does not rerun biology, readmit the producer host or authenticate execution history. Hashes establish integrity, not authenticity. A complete declared workflow result does not certify biological accuracy or reproduce unresolved paper settings. Cell Ranger remains excluded.";
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    static int run(String[] args,PrintStream out,PrintStream err){
        Path root=null;boolean fresh=false;Runnable check=()->{if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Nextflow interrupted");};
        try{
            if(args.length==1&&args[0].equals("--help")){emit(out,help());return 0;}
            if(args.length==1&&args[0].equals("--runtime-profiles")){emit(out,NextflowJson.json(java.util.List.of(NfcoreValidationPlugin.ENGINE)));return 0;}
            if(args.length==12&&args[0].equals("--install-engine")){
                var setup=new LinkedHashMap<String,String>();for(int i=0;i<args.length;i+=2)if(!Set.of("--install-engine","--one-jar","--home","--setup-report","--out","--packaging").contains(args[i])||setup.putIfAbsent(args[i],args[i+1])!=null)throw new IOException("Require unique exact engine setup value options");
                if(!setup.keySet().equals(Set.of("--install-engine","--one-jar","--home","--setup-report","--out","--packaging"))||!"one".equals(setup.get("--packaging")))throw new IOException("Declare all standard engine setup paths and --packaging one");
                Path engine=NfcoreValidationEngine.install(Path.of(setup.get("--install-engine")).toAbsolutePath().normalize(),Path.of(setup.get("--one-jar")).toAbsolutePath().normalize(),Path.of(setup.get("--home")).toAbsolutePath().normalize(),Path.of(setup.get("--setup-report")).toAbsolutePath().normalize(),Path.of(setup.get("--out")).toAbsolutePath().normalize());emit(out,"Original standard Nextflow24.04.4 setup completed: "+engine+". Capture this directory as resource engine. No analysis executed.\n"+ACTION);return 0;
            }
            if(args.length==4&&args[0].equals("--install-plugin")&&args[2].equals("--out")){Path plugins=NfcoreValidationSetup.install(Path.of(args[1]),Path.of(args[3]).toAbsolutePath().normalize());emit(out,"Original nf-validation1.1.3 installed separately at "+plugins+". No analysis executed. Capture this directory as resource plugins.\n"+ACTION);return 0;}
            if(args.length==2&&args[0].equals("--export-runtime-setup")){NextflowSetup.export(Path.of(args[1]));emit(out,"Nextflow setup exported; no installation or analysis performed.\n"+ACTION);return 0;}
            if(args.length==2&&args[0].equals("--verify")){var result=NfcoreValidationBundle.verify(Path.of(args[1]),check);emit(out,"Nextflow complete declared result verified: "+result.attempt()+"; "+result.outputFiles()+" output files, "+result.outputBytes()+" bytes; manifest SHA-256 "+result.manifest().sha256()+".\n"+ACTION);return 0;}
            var options=new LinkedHashMap<String,String>();for(int i=0;i<args.length;i+=2)if(i+1>=args.length||!Set.of("--recipe","--home","--setup-report","--out","--resume").contains(args[i])||options.putIfAbsent(args[i],args[i+1])!=null)throw new IOException("Require unique typed Nextflow value options; see --help");
            boolean resume=options.containsKey("--resume");if(resume&&(options.containsKey("--recipe")||options.containsKey("--out")))throw new IOException("Resume uses its saved recipe/session; omit --recipe and --out");
            if(!resume&&(!options.containsKey("--recipe")||!options.containsKey("--out")))throw new IOException("New Nextflow execution requires --recipe and --out");
            root=Path.of(options.get(resume?"--resume":"--out")).toAbsolutePath().normalize();if(!resume){NfcoreValidationWorkflow.create(root);fresh=true;}
            if(!options.containsKey("--home")||!options.containsKey("--setup-report"))throw new NextflowRuntime.Unavailable("Exact managed --home and completed --setup-report are required; skip analysis until permitted setup completes");
            Path home=Path.of(options.get("--home")).toAbsolutePath().normalize(),report=Path.of(options.get("--setup-report")).toAbsolutePath().normalize();
            NfcoreValidationWorkflow.Result result=resume?NfcoreValidationWorkflow.attempt(root,home,report,true,check):NfcoreValidationWorkflow.start(root,Path.of(options.get("--recipe")).toAbsolutePath().normalize(),home,report,check);
            emit(out,"Nextflow original workflow and complete declared output audit completed: "+result.attempt()+"; "+result.outputs().files()+" output files.\n"+ACTION);
            for(String line:NextflowFiles.utf8(NextflowFiles.bytes(result.attempt().resolve("WARNINGS.md"),1048576)).lines().toList())if(line.startsWith("WARNING:"))emit(err,"Nextflow "+line);
            return 0;
        }catch(Exception failure){
            int code=failure instanceof NextflowRuntime.Unavailable||failure instanceof NextflowProcess.Unavailable?3:2;String label=code==3?"SKIP3 - ANALYSIS SKIPPED":"FAIL2 - NO RESULT ADMITTED",detail=detail(failure);emit(err,"Nextflow "+label+": "+detail+"\n"+ACTION);
            if(fresh&&root!=null&&!Files.exists(root.resolve("latest.json")))try{
                NextflowFiles.writeJson(root.resolve("FAILURE.json"),Map.of("status",label,"exit_code",code,"error",detail,"required_action",ACTION));Files.writeString(root.resolve("WARNINGS.md"),"# Nextflow "+label+"\n\n"+detail+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            }catch(IOException diagnostic){emit(err,"Nextflow diagnostic write failed: "+diagnostic+". Preserve the new session directory.");}
            return code;
        }
    }
    static String detail(Exception failure){String text=failure.toString();return text.length()>16384?text.substring(0,16384)+" [diagnostic bounded; original inputs and raw logs remain complete]":text;}
    private static void emit(PrintStream stream,String text){stream.writeBytes((text+"\n").getBytes(StandardCharsets.UTF_8));}
    static String help(){return """
        nfcore-validation-tools --recipe RECIPE.json --home EXACT_HOME --setup-report COMPLETED_SETUP --out NEW_SESSION
        nfcore-validation-tools --resume SESSION --home EXACT_HOME --setup-report COMPLETED_SETUP
        nfcore-validation-tools --verify SESSION_OR_STANDALONE_ATTEMPT
        nfcore-validation-tools --export-runtime-setup NEW_DIRECTORY
        nfcore-validation-tools --runtime-profiles | --help
        nfcore-validation-tools --install-plugin ORIGINAL_RELEASE.zip --out NEW_SETUP
        nfcore-validation-tools --install-engine ORIGINAL_BOOTSTRAP --one-jar ORIGINAL_ONE.jar --home MANAGED_HOME --setup-report REPORT --packaging one --out NEW_SETUP
        Exact original engine for this bridge: Linux x86_64 Nextflow24.04.4.
        Download the exact nf-validation1.1.3 release separately where permitted;
        --install-plugin checks the complete archive before local installation.
        Capture the separate exact standard packaging as resource engine; the
        managed home/report retain the exact Java11 and baseline runtime proof.
        Supply the complete workflow directory, explicit configuration files/profiles,
        declared data roots, lossless parameters, required executables and outputs.
        Completed standalone attempt directories include all referenced immutable blobs.
        """+ACTION;}
}
