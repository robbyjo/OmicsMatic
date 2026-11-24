/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Exact source setup/inspection; typed pipeline execution is a separate milestone. */
public final class NfcoreSourceToolsCli {
    private NfcoreSourceToolsCli() { }
    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    static int run(String[] args,PrintStream out,PrintStream err){
        try{
            if(Arrays.equals(args,new String[]{"--help"})){out.println(help());return out.checkError()?2:0;}
            if(Arrays.equals(args,new String[]{"--source-profiles"})){
                var rows=new TreeMap<String,Object>();
                for(var entry:NfcoreSources.profiles().entrySet()){
                    var p=NextflowFiles.object(entry.getValue());var row=new LinkedHashMap<String,Object>();
                    for(String field:List.of("pipeline","version","commit","license","nextflow_constraint_literal","analysis_admitted"))row.put(field,p.get(field));
                    row.put("source_files",NextflowFiles.object(p.get("files")).size());rows.put(NextflowFiles.text(entry.getKey()),row);
                }
                out.println(NextflowJson.json(rows));return out.checkError()?2:0;
            }
            if(args.length==2&&args[0].equals("--export-source-setup")){NfcoreSources.export(Path.of(args[1]));out.println("EXPORTED_NFCORE_SOURCE_SETUP_NOT_RUN. "+NfcoreSources.ACTION);return out.checkError()?2:0;}
            if(args.length==3&&args[0].equals("--inspect-source")){out.println(NextflowJson.json(NfcoreSources.inspect(args[1],Path.of(args[2]))));out.println("Source verification only; no pipeline analysis was run.");return out.checkError()?2:0;}
            throw new IOException("Use --source-profiles, --inspect-source PROFILE HOME, --export-source-setup NEWDIR or --help. Pipeline execution is not available in this source-only milestone.");
        }catch(NfcoreSources.Unavailable|NoSuchFileException|AccessDeniedException unavailable){err.println("SKIP3: dependent source verification and pipeline analysis skipped. "+unavailable.getMessage()+". "+NfcoreSources.ACTION);return 3;
        }catch(IOException|RuntimeException failure){err.println("FAIL2: no complete source verification or analysis. "+failure+". "+NfcoreSources.ACTION);return 2;}
    }
    static String help(){return """
        nfcore-tools --source-profiles
        nfcore-tools --inspect-source PROFILE SOURCE_HOME
        nfcore-tools --export-source-setup NEWDIR
        Seven exact MIT source profiles: eager 2.3.4/2.4.4, chipseq 1.2.1/2.0.0,
        atacseq 1.2.1, hic 2.1.0 and rnasplice 1.0.4. Complete source namespaces
        and bytes are checked; SHA-256 provides integrity, not authentication.
        No pipeline execution, biological result or catalog-family admission.
        Export never installs. The exported Python 3.11+ helper requires explicit
        --allow-install and, for a missing cache, --allow-download.
        Exit0: source-only success;3: missing/prohibited prerequisite, analysis
        skipped;2: integrity/input failure, no complete result. Read WARNINGS.md.
        Guide: docs/literature/NFCORE_SOURCE_WORKFLOWS.md
        """+NfcoreSources.ACTION;}
}
