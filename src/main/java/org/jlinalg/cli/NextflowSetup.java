/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeMap;

/** Exact standalone setup export; exporting never installs or admits an analysis. */
final class NextflowSetup {
    private NextflowSetup() { }
    static void export(Path supplied)throws IOException {
        Path root=supplied.toAbsolutePath().normalize();if(root.getParent()==null||!root.getParent().toRealPath().equals(root.getParent()))throw new IOException("Require a canonical parent for a new Nextflow setup export");
        Files.createDirectory(root);
        try{
            var files=new TreeMap<String,NextflowFiles.Pin>();
            for(var row:Map.of("install_nextflow.py",NextflowRuntime.INSTALLER,"nextflow-managed-runtime-lock.json",NextflowRuntime.LOCK).entrySet()){
                byte[] raw=NextflowRuntime.resource(row.getKey(),row.getValue());Files.write(root.resolve(row.getKey()),raw,StandardOpenOption.CREATE_NEW);files.put(row.getKey(),row.getValue());
            }
            String text="Nextflow EXACT RUNTIME SETUP ONLY\n\nNo software was installed and no user analysis was run by this export.\nOn permitted Linux x86_64 Python3.11+ (Linux Python in WSL on Windows), from this directory:\n\npython3 install_nextflow.py --profile linux-x86_64-nextflow-24.04.4 --home NEW_PRIVATE_HOME --report NEW_SETUP_REPORT --cache CACHE --allow-install --allow-download\n\nSupported exact engine editions:21.04.1,22.10.6,24.04.4. Choose the requested engine edition; a pipeline release is not an engine version. Omit --allow-download for a complete cache, or add --archive-dir EXACT_OFFLINE_ARCHIVES. Recheck an existing complete home using a NEW report without install/download flags. Require exit0, COMPLETED.json and unchanged complete hashes.\n\nMissing/forbidden required installation, download, execution or helper: SKIP3; skip dependent analysis. Corrupt/incomplete data, integrity, process, cancellation or capacity failure: FAIL2; preserve partials/diagnostics, correct the named cause and use NEW paths. Read WARNINGS.md and the full permission_mode_departures audit; use a filesystem supporting exact POSIX modes if their preservation is required. Setup health is synthetic and does not admit a user workflow or biological result. Cell Ranger remains excluded.\n";
            Files.writeString(root.resolve("README.txt"),text,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);files.put("README.txt",NextflowFiles.pin(root.resolve("README.txt"),65536,()->{}));
            NextflowFiles.writeJson(root.resolve("export.json"),Map.of("schema_version",1,"status","EXPORTED_SETUP_NOT_RUN","user_analysis_admitted",false,"files",files));
        }catch(IOException|RuntimeException failure){
            try{Files.writeString(root.resolve("FAILURE.txt"),"Nextflow setup export FAIL2; no analysis admitted.\n"+failure+"\nRequired action: preserve this directory, correct the named write/resource mismatch and export to a new path.\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}catch(IOException diagnostic){failure.addSuppressed(diagnostic);}throw failure;
        }
    }
}
