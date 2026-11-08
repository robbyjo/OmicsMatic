/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.file.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Portable export of the pinned offline-capable Linux runtime preparation kit. */
public final class CellPhoneDbSetupCli {
    private CellPhoneDbSetupCli() { }
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    public static int run(String[] args,PrintStream out,PrintStream err){
        if(args.length==1&&args[0].equals("--help")){out.println("java -cp DEVELOPMENT.jar org.jlinalg.cli.CellPhoneDbSetupCli --export NEW_DIRECTORY\nExport verifies and writes the complete original5.0.1 source, exact dependency locks, MIT notice, setup and safe worker. Export installs nothing. On Linux x86_64, run the exported setup/prepare.py using system Python3.11+ with NEW --out and --prefix, exact --micromamba and optional repeatable --archive-cache directories. Installation requires --allow-install; uncached downloads also require --allow-download. If permissions or dependencies are unavailable, retain SKIP3 and skip analysis. Read the exported SETUP.md and setup WARNINGS.md. RUN never installs or substitutes software.");return 0;}
        try{require(args.length==2&&args[0].equals("--export"),"Use --export NEW_DIRECTORY or --help");Path target=Path.of(args[1]).toAbsolutePath().normalize();require(!Files.exists(target,LinkOption.NOFOLLOW_LINKS)&&Files.isDirectory(target.getParent(),LinkOption.NOFOLLOW_LINKS)&&target.getParent().toRealPath().equals(target.getParent()),"Export requires NEW directory under an existing canonical unlinked parent");CellPhoneDbWorkflowRuntime.sources(target);CellPhoneDbWorkflowRuntime.verifySources(target);out.println("PASS_EXPORTED_EXACT_CELLPHONEDB_RUNTIME_KIT_ONLY: "+target+"\nRead "+target.resolve("SETUP.md")+". No software was installed and no analysis was run.");return 0;}
        catch(Exception failure){err.println("CELLPHONEDB SETUP FAIL2: "+failure+"\nPreserve the exported/provisional directory, resolve the named path/source problem, and retry NEW paths. Never use an incomplete kit or refresh old profile hashes.");return 2;}
    }
}
