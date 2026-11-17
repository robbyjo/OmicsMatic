/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import static org.jlinalg.cli.SnakemakeOriginalFiles.*;

/** Original local Snakemake execution/replay and portable evidence verification. */
public final class SnakemakeToolsCli {
    private static final String NOTICE="""
        # Snakemake workflow warnings and required actions

        Original Snakemake 9.27.1, local executor, complete explicit target DAG.
        Execution/replay require qualified Linux x86-64 software and a native
        POSIX output filesystem. Use schema 1 for explicitly preinstalled software
        or schema 2 for Conda/Apptainer with explicit provider/cache declarations.
        allow_install in the recipe is the installation permission: false blocks
        missing/incomplete original deployments; true uses NEW owned cache paths.
        Selected providers are Conda 26.9.4 and Apptainer 1.5.5. No PATH/version
        substitution or installed-Conda-prefix relocation is performed.

        Run trusted workflow code only. No network/security sandbox, identical
        author environment, biological validation or whole-paper reproduction is
        claimed. Declare complete input roots, captured parameter bindings and
        the complete intended output family. Shadow staging is explicitly owned.
        Literal software/cache links and device metadata are never followed as
        host paths by portable verification. Other dynamically loaded external
        dependencies are not sealed. Fresh dependency resolution need not produce
        identical environment bytes; both software trees remain recorded.

        verify checks a complete directory or standalone ZIP without executing
        an engine or accessing producer paths. replay runs the original engine
        again, preserving declared settings and captured inputs; exact outputs
        must match byte for byte. Benchmark measurements retain fresh timing/RSS
        values and compare the declared schema/repeat family. A prefix-bearing
        exact output can legitimately fail comparison after fresh installation;
        preserve both results and choose a NEW workflow contract if path-independent
        scientific outputs are required. Never repair an existing parent bundle.

        SKIP3: skip the dependent analysis when prerequisites are missing/forbidden.
        Read FAILURE_WARNINGS.md and FAILURE.json, restore the named permitted
        qualified runtime/provider/cache or use permitted setup, then retry NEW
        output. For an unsupported output filesystem use a native Linux location.
        FAIL2: no completed workflow/comparison result is admitted. Preserve all
        inputs, partial outputs, source stderr/events and failed attempts, correct
        the named discrepancy and retry NEW output. A nested earlier success does
        not replace a failed outer replay. Use the frozen matching reader for an
        older code edition; never refresh historical fingerprints.

        Recorded limits cover their stated scopes; sampled workspace/RSS checks
        are not OS disk or aggregate JVM quotas. Hashes establish integrity and
        consistency, not signatures or trusted-host authentication. This command
        alone does not establish paper-specific catalog coverage. The existing
        benchmark operation remains separate. Build current source; released
        v0.4.0 does not contain these workflow commands.
        """;
    private SnakemakeToolsCli() { }
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    public static int run(String[] args,PrintStream output,PrintStream error){
        Path verification=null;
        var completed=new SnakemakeOriginalBundle.Verified[1];
        try {
            if(args.length==0||args.length==1&&args[0].equals("--help")){output.print(help());return 0;}
            if(args[0].equals("benchmark"))return SnakemakeBenchmarkCli.run(args,output,error);
            if(args[0].equals("--request")||args[0].equals("--verify"))return SnakemakeBenchmarkCli.run(args,output,error);
            String operation=args[0];if(!Set.of("run","verify","replay").contains(operation))throw new IllegalArgumentException("Unknown Snakemake operation: "+operation+". Use --help.");
            if(args.length==2&&args[1].equals("--help")){output.print(help());return 0;}
            Map<String,String> options=options(Arrays.copyOfRange(args,1,args.length));
            Set<String> expected=operation.equals("run")?Set.of("--request","--runtime","--out"):operation.equals("replay")?Set.of("--bundle","--runtime","--out"):Set.of("--bundle","--out");
            if(!options.keySet().equals(expected))throw new IllegalArgumentException("Require exactly "+expected+"; use snakemake-tools --help.");
            Path destination=Path.of(options.get("--out")).toAbsolutePath().normalize();if(destination.getParent()==null)throw new IOException("Require a NEW named output directory");directory(destination.getParent());
            if(Files.exists(destination,LinkOption.NOFOLLOW_LINKS))throw new IOException("Output exists: "+destination+". Preserve it and choose a NEW directory.");
            Path source=Path.of(options.get(operation.equals("run")?"--request":"--bundle")).toAbsolutePath().normalize().toRealPath();
            if(destination.startsWith(source)||source.startsWith(destination))throw new IOException("NEW output must not overlap the supplied request/bundle");
            Runnable check=()->{if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Snakemake command interrupted; retain partial evidence");};
            error.println("WARNING: original local workflow evidence; read all retained WARNINGS.md and source diagnostics. "+(operation.equals("verify")?"Portable verification executes no engine.":"Missing/forbidden prerequisites skip dependent analysis; installation follows the explicit recipe permission."));error.flush();
            if(error.checkError())throw new IOException("Warning delivery failed");
            if(operation.equals("verify")){
                Files.createDirectory(destination);verification=destination;Files.writeString(destination.resolve("WARNINGS.md"),NOTICE,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                Map<String,Object> report=SnakemakeOriginalArchive.read(source,root->{var verified=SnakemakeOriginalBundle.verify(root,check);return report("verified-declared-workflow-evidence",source,verified);},check);
                writeJson(destination.resolve("VERIFIED.json"),report);writeJson(destination.resolve("COMPLETED.new.json"),Map.of("status","PASS_PORTABLE_WORKFLOW_VERIFICATION","verification",pin(destination.resolve("VERIFIED.json"),1_048_576,check),"engine_executed",false,"biological_accuracy_certified",false));
                Files.move(destination.resolve("COMPLETED.new.json"),destination.resolve("COMPLETED.json"),StandardCopyOption.ATOMIC_MOVE);output.println("PASS Snakemake portable verification: "+destination);output.flush();if(output.checkError())throw new IOException("Verification success delivery failed");return 0;
            }
            Path runtime=Path.of(options.get("--runtime")).toAbsolutePath().normalize();
            var verified=operation.equals("run")?SnakemakeOriginalWorkflow.run(source,runtime,destination,check):SnakemakeOriginalArchive.read(source,root->{var result=SnakemakeOriginalReplay.execute(root,runtime,destination,check);completed[0]=result;return result;},check);completed[0]=verified;
            output.println("PASS Snakemake "+operation+" jobs="+verified.events().jobs().size()+" declared_outputs="+verified.outputs().size()+" bundle="+verified.root());output.flush();if(output.checkError())throw new IOException("Success delivery failed; retained bundle remains available for independent verification");return 0;
        }catch(IOException|RuntimeException failure){
            int status=(failure instanceof SnakemakeOriginalRuntime.Unavailable||failure instanceof SnakemakeOriginalProcess.Unavailable)&&failure.getSuppressed().length==0?3:2;
            if(completed[0]!=null)try{
                Path bundle=completed[0].root(),owner=bundle.getParent();directory(owner);
                if(Files.exists(bundle.resolve("COMPLETED.json")))Files.move(bundle.resolve("COMPLETED.json"),bundle.resolve("INVALIDATED-COMPLETED.json"));
                writeJson(owner.resolve("FAILURE.json"),Map.of("status","FAIL2-public-command-not-admitted","exit_code",2,"error",failure.toString(),"required_action","Retain the completed scientific evidence and invalidated marker. Resolve the public-command/archive-integrity failure and retry NEW output; no success is admitted for this failed outer command."));
                Files.writeString(owner.resolve("FAILURE_WARNINGS.md"),"# FAIL2 outer command not admitted\n\n"+failure+"\n\n"+NOTICE,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            }catch(IOException recording){failure.addSuppressed(recording);}
            if(verification!=null)try{
                Path complete=verification.resolve("COMPLETED.json");if(Files.exists(complete))Files.move(complete,verification.resolve("INVALIDATED-COMPLETED.json"));
                writeJson(verification.resolve("FAILURE.json"),Map.of("status","FAIL2-verification-not-admitted","exit_code",2,"error",failure.toString(),"required_action","Preserve the original and this verification attempt. Resolve the named bytes/census/edition/semantic discrepancy and retry NEW output; never refresh old fingerprints."));Files.writeString(verification.resolve("FAILURE_WARNINGS.md"),"# FAIL2 verification not admitted\n\n"+failure+"\n\n"+NOTICE,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            }catch(IOException recording){failure.addSuppressed(recording);}
            error.println((status==3?"SKIP3_SNAKEMAKE_ANALYSIS_SKIPPED ":"FAIL2_SNAKEMAKE_RESULT_NOT_ADMITTED ")+failure+"\n"+NOTICE);error.flush();return status;
        }
    }
    private static Map<String,Object> report(String status,Path source,SnakemakeOriginalBundle.Verified verified){
        var result=new TreeMap<String,Object>();result.put("schema_version",1);result.put("tool","Snakemake");result.put("status",status);result.put("supplied_bundle",source.toString());result.put("manifest",verified.manifest());result.put("producer_root",verified.producer());result.put("deployment",verified.request().execution().deployment());result.put("logical_jobs",verified.events().jobs().size());result.put("declared_outputs",verified.outputs().size());result.put("original_warning_error_records",verified.events().diagnostics().size());result.put("engine_executed",false);result.put("biological_accuracy_certified",false);result.put("whole_paper_reproduced",false);return result;
    }
    private static Map<String,String> options(String[] args){
        var result=new TreeMap<String,String>();
        for(int i=0;i<args.length;i+=2){if(i+1>=args.length||!Set.of("--request","--runtime","--bundle","--out").contains(args[i])||args[i+1].isEmpty()||args[i+1].chars().anyMatch(c->c<32||c==127)||result.putIfAbsent(args[i],args[i+1])!=null)throw new IllegalArgumentException("Use unique option/value pairs; see snakemake-tools --help.");}
        return result;
    }
    private static String help(){return """
        snakemake-tools run --request RECIPE.json --runtime ADOPTION --out NEW
        snakemake-tools verify --bundle DIRECTORY_OR_ZIP --out NEW
        snakemake-tools replay --bundle DIRECTORY_OR_ZIP --runtime ADOPTION --out NEW
        snakemake-tools benchmark --help

        Every workflow recipe explicitly declares targets, inputs, output contract,
        parameters, limits and installation permission. See SNAKEMAKE_WORKFLOWS.md.

        """+NOTICE;}
}
