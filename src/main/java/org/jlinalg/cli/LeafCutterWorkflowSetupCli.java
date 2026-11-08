/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.*;
import java.util.*;

/** Export exact original sources, source archives, locks and standalone private setup; export installs nothing. */
public final class LeafCutterWorkflowSetupCli {
    private LeafCutterWorkflowSetupCli() { }
    public static void main(String[] args){try{export(args);}catch(Exception failure){System.err.println("LEAFCUTTER SETUP EXPORT FAIL2: "+failure+"\nRequired action: preserve partial exports and retry a NEW canonical writable output. Use no kit without setup-kit.json. No analysis or installation was performed.");System.exit(2);}}
    private static void export(String[] args)throws Exception {
        if(args.length==1&&args[0].equals("--help")){System.out.println("--export-runtime-setup NEW_DIRECTORY\nExport exact original LeafCutter source at2c9907ef66ad, GPL/Apache notices, source addons,239archive dependency locks and standalone Linux setup. CAPTURE reads existing original dependency/library build receipts; INSTALL requires --allow-install, a supplied permitted micromamba and a NEW prefix. Missing archives require --allow-download or an exact archive cache. Missing/forbidden software keeps dependent analysis skipped. Export runs no analysis and installs no software.");return;}
        if(args.length!=2||!args[0].equals("--export-runtime-setup"))throw new IllegalArgumentException("Use --export-runtime-setup NEW_DIRECTORY or --help");Path out=Path.of(args[1]).toAbsolutePath().normalize();if(Files.exists(out,LinkOption.NOFOLLOW_LINKS)||!out.getParent().toRealPath().equals(out.getParent()))throw new IllegalArgumentException("Require NEW output under a canonical existing writable parent");Files.createDirectory(out);var files=new TreeMap<String,Object>();var profile=LeafCutterWorkflowRuntime.profile();
        for(String name:Monocle3WorkflowRequest.object(profile.get("files")).keySet()){byte[] raw=LeafCutterWorkflowRuntime.resource(name);Path file=out.resolve(NextflowFiles.member(name));Files.createDirectories(file.getParent());Files.write(file,raw,StandardOpenOption.CREATE_NEW);files.put(name,Map.of("bytes",raw.length,"sha256",NextflowFiles.sha(raw)));}byte[] raw=LeafCutterWorkflowRuntime.resource("profiles.json");Files.write(out.resolve("profiles.json"),raw,StandardOpenOption.CREATE_NEW);files.put("profiles.json",Map.of("bytes",raw.length,"sha256",NextflowFiles.sha(raw)));NextflowFiles.writeJson(out.resolve("setup-kit.json"),Map.of("schema_version",1,"files",files,"software_installed",false,"analysis_executed",false));System.out.println("Exported exact LeafCutter runtime setup: "+out+"\nRun python3 -I -B "+out.resolve("setup/prepare.py")+" --help.\nRead WARNINGS.md after preparation. Require a PASS_BOUND_ORIGINAL_LEAFCUTTER_WORKFLOW_RUNTIME_ONLY binding before RUN. Forbidden or missing required installation keeps dependent work skipped.");
    }
}
