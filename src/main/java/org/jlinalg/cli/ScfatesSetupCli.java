/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.file.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Portable export of the pinned offline-capable Linux runtime preparation kit. */
public final class ScfatesSetupCli {
    private ScfatesSetupCli() { }
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    public static int run(String[] args,PrintStream out,PrintStream err){
        if(args.length==1&&args[0].equals("--help")){out.println("java -cp DEVELOPMENT.jar org.jlinalg.cli.ScfatesSetupCli --export NEW_DIRECTORY\nExports the exact scFates1.2.5/SimplePPT1.1.7 original wheels, pinned dependencies, source notices and Linux setup/worker. No installation or analysis occurs. Read exported SETUP.md. Setup requires exact read-only project Python3.11.17 base receipt, NEW paths, --allow-install and --allow-download only for uncached pinned archives. Forbidden/missing dependencies retain SKIP3 and skip analysis.");return 0;}
        try{require(args.length==2&&args[0].equals("--export"),"Use --export NEW_DIRECTORY or --help");Path target=Path.of(args[1]).toAbsolutePath().normalize();require(!Files.exists(target,LinkOption.NOFOLLOW_LINKS)&&Files.isDirectory(target.getParent(),LinkOption.NOFOLLOW_LINKS)&&target.getParent().toRealPath().equals(target.getParent()),"Export requires NEW directory under an existing canonical unlinked parent");ScfatesWorkflowRuntime.sources(target);ScfatesWorkflowRuntime.verifySources(target);out.println("PASS_EXPORTED_EXACT_SCFATES_RUNTIME_KIT_ONLY: "+target+"\nRead "+target.resolve("SETUP.md")+". No software was installed and no analysis was run.");return 0;}
        catch(Exception failure){err.println("SCFATES SETUP FAIL2: "+failure+"\nPreserve the exported/provisional directory, resolve the named path/source problem, and retry NEW paths. Never use an incomplete kit or refresh old profile hashes.");return 2;}
    }
}
