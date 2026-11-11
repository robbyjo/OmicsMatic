/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.*;
import java.util.*;

/** Portable byte-preserving setup-kit export. Installation/capture is a separate explicit Linux invocation. */
public final class HailWorkflowSetupCli {
    private HailWorkflowSetupCli() { }
    public static void main(String[] args){try{export(args);}catch(Exception failure){System.err.println("HAIL SETUP EXPORT FAIL2: "+failure+"\nRequired action: preserve the partial export; choose a NEW directory under an existing canonical writable parent and repeat. Do not use a kit without a complete setup-kit.json. No software installation or analysis was performed.");System.exit(2);}}
    private static void export(String[] args)throws Exception {
        if(args.length==1&&args[0].equals("--help")){System.out.println("--export-runtime-setup NEW_DIRECTORY\nExport pinned Hail0.2.139/0.2.130 source profiles, MIT notices, exact dependency archive locks and private preparation code.\nLinux setup/prepare.py --mode CAPTURE reads an existing permitted prefix. INSTALL requires --allow-install and a NEW prefix; missing archives require --allow-download. No analysis runs or software installs during export.");return;}
        if(args.length!=2||!args[0].equals("--export-runtime-setup"))throw new IllegalArgumentException("Use --export-runtime-setup NEW_DIRECTORY or --help");Path out=Path.of(args[1]).toAbsolutePath().normalize();if(Files.exists(out,LinkOption.NOFOLLOW_LINKS)||!out.getParent().toRealPath().equals(out.getParent()))throw new IllegalArgumentException("Require a NEW setup directory under a canonical existing parent");Files.createDirectory(out);var files=new TreeMap<String,Object>();
        for(String name:List.of("profiles.json","0.2.139/MIT.txt","0.2.130/MIT.txt","setup/prepare.py","setup/inventory.py","setup/dependency-locks.json")){
            byte[] raw=HailWorkflowRuntime.resource(name);Path path=out.resolve(name);Files.createDirectories(path.getParent());Files.write(path,raw,StandardOpenOption.CREATE_NEW);files.put(name,Map.of("bytes",raw.length,"sha256",NextflowFiles.sha(raw)));
        }
        NextflowFiles.writeJson(out.resolve("setup-kit.json"),Map.of("schema_version",1,"files",files,"analysis_executed",false,"software_installed",false));System.out.println("Exported exact Hail setup kit: "+out+"\nRead CAPTURE/INSTALL help: python3 -I -B "+out.resolve("setup/prepare.py")+" --help\nIf required installation/download is unavailable or forbidden, keep dependent analysis skipped and read WARNINGS.md. Preparation uses NEW paths and preserves failed attempts.");
    }
}
