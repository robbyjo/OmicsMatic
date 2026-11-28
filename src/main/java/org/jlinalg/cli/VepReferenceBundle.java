/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Entire byte-bound original human cache/FASTA, never a relabeled or reduced cache. */
final class VepReferenceBundle {
    private final Path root;private final Map<String,NextflowFiles.Pin> files;private final long maximum;private final Map<String,Object> profile;
    private VepReferenceBundle(Path root,Map<String,NextflowFiles.Pin> files,long maximum,Map<String,Object> profile){this.root=root;this.files=Map.copyOf(files);this.maximum=maximum;this.profile=profile;}
    static VepReferenceBundle bind(Path supplied,long maximum)throws IOException {
        Path root=supplied.toAbsolutePath().normalize();if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))throw new VepToolsRuntime.Unavailable("Entire exact original VEP105 human reference bundle unavailable: "+root+". Prepare its complete permitted archives/index or retain skip");
        byte[] reference=VepToolsRuntime.resource("REFERENCE.json");if(!Arrays.equals(reference,NextflowFiles.bytes(root.resolve("PROFILE.json"),67108864)))throw new IOException("Original human VEP reference profile differs; no cache/release/assembly substitution");var profile=object(SimpleJson.parse(new String(reference,StandardCharsets.UTF_8)),"reference profile");if(!"homo_sapiens-Ensembl105-GRCh38-complete-original-cache-and-primary-FASTA".equals(profile.get("profile")))throw new IOException("Unexpected original reference edition");
        Map<String,NextflowFiles.Pin> expected=new TreeMap<>();for(var entry:object(profile.get("files"),"reference files").entrySet())expected.put(NextflowFiles.member(entry.getKey()),NextflowFiles.fingerprint(entry.getValue(),maximum));expected.put("PROFILE.json",new NextflowFiles.Pin(reference.length,NextflowFiles.sha(reference)));var bundle=new VepReferenceBundle(root,expected,maximum,profile);bundle.verify();return bundle;
    }
    void verify()throws IOException {
        var actual=NextflowFiles.tree(root,1000000,maximum,maximum,()->{if(Thread.currentThread().isInterrupted())throw new IllegalStateException("VEP reference integrity inspection interrupted");});if(!actual.keySet().equals(files.keySet()))throw new IOException("Complete original VEP reference file population changed");for(var entry:actual.entrySet())if(!entry.getValue().kind().equals("file")||!entry.getValue().pin().equals(files.get(entry.getKey())))throw new IOException("Entire original reference bytes changed: "+entry.getKey());
    }
    void capture(Path path)throws IOException{var result=new LinkedHashMap<String,Object>();result.put("profile",profile.get("profile"));result.put("root",root.toString());result.put("source_archives",profile.get("source_archives"));Map<String,Object> identities=new TreeMap<>();files.forEach((name,pin)->identities.put(name,Map.of("bytes",pin.bytes(),"sha256",pin.sha256())));result.put("complete_file_identities",identities);result.put("subset_or_version_substitution",false);json(path,result);}
    long bytes(){long bytes=0;for(var pin:files.values())bytes=Math.addExact(bytes,pin.bytes());return bytes;}
    Path root(){return root;}Path cache(){return root.resolve("cache");}Path fasta(){return root.resolve("genome.fa");}Path fai(){return root.resolve("genome.fa.fai");}
}
