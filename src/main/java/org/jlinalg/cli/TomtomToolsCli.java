/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Set;

/** Typed original motif comparison; runtime health and complete user workflow gates are separate. */
public final class TomtomToolsCli {
    private TomtomToolsCli() { }
    /** @param args unique typed command value options */
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    static int run(String[] args,PrintStream out,PrintStream err){
        Path root=null;Runnable check=()->{if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Tomtom interrupted");};
        try{
            if(args.length==1&&args[0].equals("--help")){emit(out,help());return 0;}
            if(args.length==1&&args[0].equals("--runtime-profiles")){emit(out,NextflowJson.json(java.util.List.of(TomtomRecipe.PROFILE)));return 0;}
            if(args.length==2&&args[0].equals("--export-runtime-setup")){TomtomSetupExport.export(absolute(args[1]));emit(out,"Exported standalone original source setup kit; no installation or analysis was run. Read RUN-SETUP.txt and UPSTREAM-COPYING; use only permitted explicit build/download flags.");return 0;}
            if(args.length==2&&args[0].equals("--verify")){report(TomtomWorkflow.verify(absolute(args[1]),check),true,out,err);return 0;}
            var options=new LinkedHashMap<String,String>();for(int i=0;i<args.length;i+=2)if(i+1>=args.length||!Set.of("--recipe","--home","--build-evidence","--out").contains(args[i])||options.putIfAbsent(args[i],args[i+1])!=null)throw new IOException("Require unique typed Tomtom value options; use --help");
            if(!options.containsKey("--recipe")||!options.containsKey("--out"))throw new IOException("Require --recipe REQUEST.json and --out NEW_DIRECTORY");root=absolute(options.get("--out"));report(TomtomWorkflow.start(absolute(options.get("--recipe")),options.containsKey("--home")?absolute(options.get("--home")):null,options.containsKey("--build-evidence")?absolute(options.get("--build-evidence")):null,root,check),false,out,err);return 0;
        }catch(Exception failure){boolean skip=failure instanceof TomtomRuntime.Unavailable||failure instanceof org.jlinalg.sequence.TomtomResults.UnsupportedInput||failure instanceof org.jlinalg.sequence.MemeMotifInputs.UnsupportedInput;emit(err,"Tomtom "+(skip?"SKIP3 — ANALYSIS SKIPPED":"FAIL2 — NO RESULT ADMITTED")+": "+failure+"\n"+TomtomWorkflow.ACTION);if(root!=null&&Files.exists(root.resolve("FAILURE.json")))emit(err,"Retained attempt: "+root+". Read its prominent WARNINGS.md and named failed stage.");return skip?3:2;}
    }
    private static void report(TomtomWorkflow.Result result,boolean verified,PrintStream out,PrintStream err)throws IOException {emit(out,(verified?"Verified captured":"Completed original")+" Tomtom "+result.request().mode()+": "+result.output().matches().size()+" reported matches; "+result.output().pairs().size()+" complete declared pairs. "+result.root()+"\n"+TomtomWorkflow.ACTION);for(String line:NextflowFiles.utf8(NextflowFiles.bytes(result.root().resolve("WARNINGS.md"),1048576)).lines().toList())if(line.startsWith("WARNING:"))emit(err,"Tomtom "+line);}
    private static Path absolute(String text){return Path.of(text).toAbsolutePath().normalize();}
    private static void emit(PrintStream stream,String text){stream.writeBytes((text+"\n").getBytes(StandardCharsets.UTF_8));}
    static String help(){return """
        tomtom-tools --recipe REQUEST.json --home EXACT_HOME --build-evidence COMPLETED_BUILD_REPORT --out NEW_DIRECTORY
        tomtom-tools --verify COMPLETE_ANALYSIS
        tomtom-tools --export-runtime-setup NEW_DIRECTORY
        tomtom-tools --runtime-profiles | --help
        Original 5.5.9 DNA motif comparisons; TEXT and FULL_REPORT are explicit modes.
        All scientific settings/background/input roles and complete capacities use the typed JSON recipe.
        Execution requires permitted Linux x86_64 Java, such as Linux Java inside WSL on Windows.
        Complete captured verification also works on Windows and never opens recorded producer paths.
        Native source/build evidence and fresh host admission are mandatory; analysis never installs.
        """+TomtomWorkflow.ACTION;}
}
