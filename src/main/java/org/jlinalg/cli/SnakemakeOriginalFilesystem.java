/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import static org.jlinalg.cli.SnakemakeOriginalFiles.*;
import static org.jlinalg.cli.SnakemakeOriginalRecorded.*;

/** Real output-filesystem prerequisites, before any workflow/deployer starts. */
final class SnakemakeOriginalFilesystem {
    private static final String DATA="OmicsMatic Snakemake filesystem preflight\n";
    private static final String CHECKS="case-distinct regular files; exact POSIX modes; internal hardlink identity; literal relative/absolute symlinks; same-directory atomic move";
    private SnakemakeOriginalFilesystem() { }

    static void prepare(Path root,Runnable check)throws IOException {
        if(!System.getProperty("os.name").equals("Linux")||!root.getFileSystem().supportedFileAttributeViews().contains("unix"))throw unavailable(root,"Native Linux POSIX filesystem metadata is unavailable",null);
        Path probe=Files.createDirectory(root.resolve("filesystem-preflight"));
        try {
            directory(probe);String store=Files.getFileStore(probe).type();
            if(List.of("9p","drvfs","ntfs","fuseblk","cifs","smbfs").contains(store.toLowerCase(java.util.Locale.ROOT)))throw new IOException("Output uses an unqualified mounted filesystem: "+store);
            byte[] payload=DATA.getBytes(StandardCharsets.UTF_8);Path upper=probe.resolve("A"),lower=probe.resolve("a"),hard=probe.resolve("hard"),relative=probe.resolve("relative"),absolute=probe.resolve("absolute"),moved=probe.resolve("moved");
            Files.write(upper,payload,StandardOpenOption.CREATE_NEW);Files.setPosixFilePermissions(upper,PosixFilePermissions.fromString("rw-r-----"));
            Identity initial=identity(upper,4096,check);if(initial.mode()!=0640||initial.fileKey().equals("null"))throw new IOException("Exact POSIX file mode/identity is unavailable");
            Files.writeString(lower,"distinct case\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);if(Files.isSameFile(upper,lower))throw new IOException("Case-distinct names refer to the same file");
            Files.createLink(hard,upper);Identity linked=identity(hard,4096,check);if(!Files.isSameFile(hard,upper)||!linked.fileKey().equals(initial.fileKey())||linked.links()!=2||!linked.pin().equals(initial.pin()))throw new IOException("Internal hardlink identity/topology is not preserved");
            Files.createSymbolicLink(relative,Path.of("A"));Files.createSymbolicLink(absolute,upper);
            if(!Files.readSymbolicLink(relative).toString().equals("A")||!Files.readSymbolicLink(absolute).equals(upper)||!relative.toRealPath().equals(upper)||!absolute.toRealPath().equals(upper))throw new IOException("Literal relative/absolute symlinks are unavailable");
            Files.setPosixFilePermissions(hard,PosixFilePermissions.fromString("rw-------"));Identity changed=identity(upper,4096,check);if(changed.mode()!=0600||!changed.fileKey().equals(initial.fileKey()))throw new IOException("Hardlink mode changes are not shared");
            Files.move(hard,moved,StandardCopyOption.ATOMIC_MOVE);Identity renamed=identity(moved,4096,check);if(!renamed.equals(changed))throw new IOException("Atomic move changed file bytes/identity/mode/link metadata");
            var result=new TreeMap<String,Object>();result.put("schema_version",1);result.put("status","PASS_NATIVE_LINUX_OUTPUT_FILESYSTEM");result.put("producer_root",root.toString());result.put("filesystem",store);result.put("checks",CHECKS);result.put("initial",initial);result.put("linked",linked);result.put("changed",changed);result.put("renamed",renamed);result.put("case_pin",pin(lower,4096,check));result.put("relative_target","A");result.put("absolute_target",upper.toString());
            // These names were just created under this canonical owned probe.
            // Delete link entries, never resolve their targets for deletion.
            for(Path path:List.of(relative,absolute,moved,upper,lower))Files.delete(path);
            Files.delete(probe);writeJson(root.resolve("FILESYSTEM.json"),result);
        }catch(IOException|UnsupportedOperationException failure){throw unavailable(root,failure.toString(),failure);}
    }

    static void audit(Path root,String producer,Runnable check)throws IOException {
        Map<?,?> record=object(json(root.resolve("FILESYSTEM.json"),1_048_576));keys(record,"schema_version","status","producer_root","filesystem","checks","initial","linked","changed","renamed","case_pin","relative_target","absolute_target");integer(record.get("schema_version"),1,1);
        if(!"PASS_NATIVE_LINUX_OUTPUT_FILESYSTEM".equals(record.get("status"))||!absolute(producer).equals(record.get("producer_root"))||!CHECKS.equals(record.get("checks"))||text(record.get("filesystem")).isBlank()||!"A".equals(record.get("relative_target"))||!(producer+"/filesystem-preflight/A").equals(record.get("absolute_target")))throw new IOException("Recorded filesystem prerequisites differ");
        Pin expected=new Pin(DATA.getBytes(StandardCharsets.UTF_8).length,sha(DATA.getBytes(StandardCharsets.UTF_8)));String key=null;
        for(String name:List.of("initial","linked","changed","renamed")){
            Map<?,?> value=object(record.get(name));keys(value,"pin","fileKey","modified","mode","links");
            if(!expected.equals(fingerprint(value.get("pin"),4096))||integer(value.get("mode"),0,0777)!=(name.equals("initial")||name.equals("linked")?0640:0600)||integer(value.get("links"),1,2)!=(name.equals("initial")?1:2))throw new IOException("Recorded filesystem file/link/mode proof differs");
            String actual=text(value.get("fileKey"));if(actual.equals("null")||key!=null&&!key.equals(actual))throw new IOException("Recorded filesystem hardlink identity differs");key=actual;text(value.get("modified"));
        }
        equal(record.get("changed"),record.get("renamed"),"filesystem atomic move identity");
        byte[] lower="distinct case\n".getBytes(StandardCharsets.UTF_8);if(!new Pin(lower.length,sha(lower)).equals(fingerprint(record.get("case_pin"),4096)))throw new IOException("Recorded case-distinction proof differs");check.run();
    }

    private static SnakemakeOriginalRuntime.Unavailable unavailable(Path root,String cause,Throwable source){
        var failure=new SnakemakeOriginalRuntime.Unavailable("SKIP3 output filesystem prerequisite: "+cause+". Skip the dependent analysis; use a NEW output directory on a permitted native Linux filesystem with POSIX modes, case distinction, hardlinks, symlinks and atomic moves. Keep any retained filesystem-preflight directory at "+root+"; no workflow/deployer was started.");
        if(source!=null)failure.initCause(source);return failure;
    }
}
