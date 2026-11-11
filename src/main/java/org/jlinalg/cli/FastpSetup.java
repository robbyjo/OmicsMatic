/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeMap;

/** Export exact existing installer resources; installation remains an explicit separate action. */
final class FastpSetup {
    private FastpSetup() { }
    static void export(Path supplied)throws IOException {
        Path root=supplied.toAbsolutePath().normalize();FastpBundle.create(root);
        try{
            var files=new TreeMap<String,FastpFiles.Pin>();
            resource(root,"tools/literature/install_fastp.py","fastp-runtime-setup/install_fastp.py","e77621208a1fb878a41e1f6054f4623865da5607c4c3cbd172b5063fcd9af84b",files);
            resource(root,"tools/literature/fastp-runtime-lock.json","fastp-runtime-lock.json",FastpRuntime.LOCK_SHA256,files);
            resource(root,"src/main/resources/org/jlinalg/omics/fastp-MIT-NOTICE.txt","fastp-MIT-NOTICE.txt","52e18866a994ee9839aa6d15696bc1fe425e6466631d3a57a75c089dd84ae88e",files);
            Files.writeString(root.resolve("README.txt"),"fastp original runtime SETUP ONLY: no user analysis was run or admitted.\nFrom this exported directory on an allowed Linux x86_64 host with Python3.11+:\npython3 tools/literature/install_fastp.py --profile linux-x86_64-fastp-0.23.2 --home NEW_PRIVATE_HOME --report NEW_SETUP_REPORT --cache CACHE --allow-install --allow-download\nOmit --allow-download for a complete exact cache (CACHE/fastp.VERSION). Select the exact reported supported edition; never infer a missing patch. Require successful setup COMPLETED.json and full hashes. Use the separate fastp-tools --recipe/--home/--setup-report/--out workflow for user analysis.\n"+FastpToolsCli.ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            files.put("README.txt",FastpFiles.pin(root.resolve("README.txt"),65536,()->{}));FastpFiles.writeJson(root.resolve("export.json"),Map.of("schema_version",1,"status","EXPORTED_SETUP_NOT_RUN","user_analysis_admitted",false,"files",files));
        }catch(IOException|RuntimeException failure){try{Files.writeString(root.resolve("FAILURE.md"),"# fastp setup export FAIL2\n\n"+failure+"\n\n"+FastpToolsCli.ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}catch(IOException diagnostic){failure.addSuppressed(diagnostic);}throw failure;}
    }
    private static void resource(Path root,String target,String source,String sha,Map<String,FastpFiles.Pin> files)throws IOException {
        byte[] raw;try(var input=FastpSetup.class.getResourceAsStream("/org/jlinalg/omics/"+source)){if(input==null)throw new IOException("Missing fastp setup resource: "+source);raw=input.readNBytes(1_048_577);}
        if(raw.length>1_048_576||!FastpFiles.sha(raw).equals(sha))throw new IOException("Changed fastp setup resource: "+source);Path destination=root.resolve(target);Files.createDirectories(destination.getParent());Files.write(destination,raw,StandardOpenOption.CREATE_NEW);files.put(target,new FastpFiles.Pin(raw.length,sha));
    }
}
