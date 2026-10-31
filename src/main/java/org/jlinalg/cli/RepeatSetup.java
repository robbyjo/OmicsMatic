/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Export the standalone, pinned private-payload setup without installing anything. */
final class RepeatSetup {
    private RepeatSetup() { }
    static final List<String> RESOURCES=List.of("repeat-runtime-lock.json",
            "repeat-runtime-setup/install_repeat_payload.py","repeat-runtime-setup/repeat_python_bootstrap.py",
            "repeat-runtime-setup/notices/LICENSE-libgomp-GPL3.txt","repeat-runtime-setup/notices/LICENSE-libgomp-exception.txt");
    static void export(Path supplied,Runnable check)throws IOException{
        Path root=supplied.toAbsolutePath().normalize();
        if(root.getParent()==null||!root.getParent().toRealPath().equals(root.getParent()))throw new IOException("Require a canonical existing parent and NEW RepeatMasker setup export directory");
        var files=new TreeMap<String,byte[]>();
        for(String name:RESOURCES){
            check.run();try(var input=RepeatSetup.class.getResourceAsStream("/org/jlinalg/omics/"+name)){
                if(input==null)throw new IOException("Missing bundled RepeatMasker setup resource: "+name);
                byte[] raw=input.readNBytes(2097153);if(raw.length>2097152)throw new IOException("Bundled RepeatMasker setup resource exceeds its capacity");files.put(name,raw);
            }
        }
        files.put("README.md",README.getBytes(StandardCharsets.UTF_8));
        var fingerprints=new TreeMap<String,Object>();
        for(var entry:files.entrySet())fingerprints.put(entry.getKey(),Map.of("bytes",entry.getValue().length,"sha256",AdmixtureInputs.digest(entry.getValue())));
        files.put("setup.json",RepeatBundle.json(Map.of("schema_version",1,"tool","RepeatMasker","status","EXPORTED_NOT_INSTALLED_OR_SEARCH_ADMITTED",
                "scope","Pristine private payload setup only;scientific runtime admission and public original SEARCH remain unfinished","resources",fingerprints)));
        var sums=new StringBuilder();for(var entry:files.entrySet())sums.append(AdmixtureInputs.digest(entry.getValue())).append("  ").append(entry.getKey()).append('\n');
        files.put("SHA256SUMS",sums.toString().getBytes(StandardCharsets.UTF_8));
        long bytes=0;for(byte[] value:files.values())bytes=Math.addExact(bytes,value.length);
        if(bytes>4194304)throw new IOException("Complete RepeatMasker setup export exceeds4MiB");
        Files.createDirectory(root);
        try{
            for(var entry:files.entrySet()){
                check.run();Path target=root.resolve(entry.getKey());Files.createDirectories(target.getParent());
                Files.write(target,entry.getValue(),StandardOpenOption.CREATE_NEW);
                if(!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS)||!target.toRealPath().equals(target)
                        ||!RepeatInputs.hash(target,entry.getValue().length,check).equals(AdmixtureInputs.digest(entry.getValue())))throw new IOException("RepeatMasker setup export differs: "+entry.getKey());
            }
            RepeatBundle.inventory(root,new RepeatBundle.Limits(4194304,64),false,check);
        }catch(IOException|RuntimeException failure){
            try{Files.writeString(root.resolve("SETUP_EXPORT_FAILED.txt"),"FAIL2 incomplete setup export. Retain these partials and retry to a NEW directory.\n"+failure+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
            catch(IOException diagnostic){failure.addSuppressed(diagnostic);}throw failure;
        }
    }
    private static final String README="""
        # RepeatMasker private payload setup

        **Payload setup only. Original SEARCH is not admitted by this export.**
        Verify every SHA256SUMS entry before using this exported copy. Checksums
        check integrity, not authenticity. Keep the original JAR and its provenance.
        Exporting performs no downloads, installation or scientific analysis.

        Setup needs Linux x86_64 Python3.11+ and an existing libzstd. On Windows
        use WSL. Run the helper with -I -S -B to exclude Python site startup files
        and bytecode writes. See its complete options and required recovery:

        ```sh
        python3 -I -S -B repeat-runtime-setup/install_repeat_payload.py --help
        python3 -I -S -B repeat-runtime-setup/install_repeat_payload.py \\
          --profile linux-x86_64-repeatmasker-4.2.6-custom-rmblast \\
          --home /ABS/NEW-repeat-payload --report /ABS/NEW-setup-report \\
          --cache /ABS/private-archive-cache --allow-install --allow-download
        ```

        Replace /ABS with a canonical existing real parent. Home, report and
        cache must be disjoint. Original runtime paths use the ASCII subset
        /[A-Za-z0-9_./+-]+. Existing homes/reports are never replaced. Downloads
        need separate --allow-download; offline use omits it and supplies one or
        more --offline-archives DIRECTORY values containing the pinned archives.
        --allow-install is required only for this explicit setup command.

        The historical profile linux-x86_64-repeatmasker-4.1.5-dfam3.7-rmblast
        additionally requires --python /ABS/exact-python3.12 matching the bundled
        lock. It uses Dfam3.7 and isolated pinned h5py/NumPy wheels, preserves
        metadata/licenses and never modifies a global Python environment.
        Unreported paper resource editions remain unresolved; do not substitute
        profiles or claim exact paper reconstruction.

        Read installation.json and WARNINGS.md on every attempt. Missing or
        forbidden dependencies/permissions mean SKIP3: leave dependent analyses
        skipped. Invalid inputs, changed files, extraction or capacity failures
        mean FAIL2: retain all partials, correct the named cause and use NEW
        home/report paths. The helper never installs OS packages or performs a
        scientific search. Success is PAYLOAD_INSTALLED_SEARCH_NOT_ADMITTED;
        full host/dependency closure and scientific runtime admission remain
        separate requirements. Cell Ranger remains excluded.
        """;
}
