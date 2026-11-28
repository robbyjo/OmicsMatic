/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import static org.jlinalg.cli.NextflowFiles.*;

/** Complete source bytes, original Git executable flags and all source-image aliases. */
final class NfcoreRnasplice104Profile {
    private NfcoreRnasplice104Profile() { }
    static final String ID="nf-core/rnasplice-1.0.4";
    static final String RESOURCE="/org/jlinalg/omics/nfcore-rnasplice104/";
    static final Pin SOURCE=new Pin(57108,"4600d3d18e00d9257170e67c822aeab58a49322607d330242aa77d4c39545d05");
    static final Pin IMAGES=new Pin(21817,"50eca1d73b5146456b72599d75c09b663ac9281009b5d576696b16e993d4f1fa");
    static final String COMMIT="1d0494ae3402d1a46e0adadad24f81a0ff855c77";
    static Map<?,?> resource(String name,Pin expected)throws IOException {
        try(var stream=NfcoreRnasplice104Profile.class.getResourceAsStream(RESOURCE+name)){
            if(stream==null)throw new NextflowProcess.Unavailable("Missing packaged complete RNA source/image profile; dependent analysis skipped");
            byte[] raw=stream.readNBytes(Math.toIntExact(expected.bytes()+1));if(!new Pin(raw.length,sha(raw)).equals(expected))throw new IOException("Changed complete RNA source/image profile: "+name);return object(NextflowParameters.parse(utf8(raw),1048576,100000));
        }
    }
    static Map<String,Map<?,?>> sourceFiles()throws IOException {
        Map<?,?> profile=resource("SOURCE-PROFILE.json",SOURCE);if(!ID.equals(profile.get("pipeline_profile"))||!COMMIT.equals(profile.get("source_commit")))throw new IOException("RNA source edition differs");Map<?,?> rows=object(profile.get("files"));if(rows.size()!=206)throw new IOException("Incomplete original RNA source population");var result=new TreeMap<String,Map<?,?>>();int executable=0;
        for(var row:rows.entrySet()){String name=member(text(row.getKey()));Map<?,?> value=object(row.getValue());keys(value,"bytes","sha256","git_mode","executable","git_blob_sha1");sourcePin(value);boolean marked=Boolean.TRUE.equals(value.get("executable"));if(!(value.get("executable")instanceof Boolean)||!Set.of("100644","100755").contains(value.get("git_mode"))||marked!=value.get("git_mode").equals("100755")||!text(value.get("git_blob_sha1")).matches("[0-9a-f]{40}"))throw new IOException("Invalid original Git source identity: "+name);if(marked)executable++;result.put(name,value);}
        if(executable!=29)throw new IOException("Original RNA executable-file census differs");return Map.copyOf(result);
    }
    static Pin sourcePin(Map<?,?> row)throws IOException{return new Pin(integer(row.get("bytes"),0,67108864),text(row.get("sha256")));}
    static Map<?,?> images()throws IOException {
        Map<?,?> profile=resource("IMAGES.json",IMAGES);if(!ID.equals(profile.get("pipeline_profile"))||!COMMIT.equals(profile.get("source_commit")))throw new IOException("RNA source-image edition differs");Map<?,?> images=object(profile.get("images")),aliases=object(profile.get("aliases"));if(images.size()!=31||aliases.size()!=72)throw new IOException("Incomplete original RNA image/import graph");var seen=new HashSet<String>();
        for(var image:images.entrySet()){String uri=text(image.getKey());Map<?,?> value=object(image.getValue());keys(value,"pin","modules","process_names");fingerprint(value.get("pin"),5000000000L);for(String name:strings(value.get("process_names")))if(!name.matches("[A-Za-z_][A-Za-z0-9_]*")||!seen.add(name)||!uri.equals(aliases.get(name)))throw new IOException("Ambiguous RNA source process/image alias");}
        if(!seen.equals(aliases.keySet()))throw new IOException("Incomplete RNA source process/image alias closure");return images;
    }
    /** Copy every original member without changing the supplied source or its modes. */
    static void copySource(Path supplied,Path target)throws IOException {
        if(!Files.exists(supplied))throw new NextflowProcess.Unavailable("Complete original RNA source directory is missing; complete permitted source setup or retain the skipped analysis");directory(supplied);var expected=sourceFiles();var found=new HashSet<String>();var directories=new HashSet<String>();for(String name:expected.keySet())for(int at=name.lastIndexOf('/');at>0;at=name.lastIndexOf('/',at-1))directories.add(name.substring(0,at));
        try(var paths=Files.walk(supplied)){var iterator=paths.iterator();while(iterator.hasNext()){Path path=iterator.next();if(path.equals(supplied))continue;if(Files.isSymbolicLink(path))throw new IOException("Linked original RNA source member: "+path);String name=supplied.relativize(path).toString().replace('\\','/');if(Files.isDirectory(path)){directory(path);if(!directories.contains(name))throw new IOException("Unexpected original RNA source directory: "+name);continue;}Map<?,?> row=expected.get(name);if(row==null||!sourcePin(row).equals(pin(path,67108864,()->{})))throw new IOException("Unexpected/changed original RNA source member: "+name);found.add(name);}}
        if(!found.equals(expected.keySet()))throw new NextflowProcess.Unavailable("Incomplete original RNA source prerequisite; complete exact permitted source setup before dependent analysis");Files.createDirectory(target);
        for(var row:new TreeMap<>(expected).entrySet()){Path source=supplied.resolve(row.getKey()),destination=target.resolve(row.getKey());Files.createDirectories(destination.getParent());Files.copy(source,destination);if(!sourcePin(row.getValue()).equals(pin(destination,67108864,()->{}))||!sourcePin(row.getValue()).equals(pin(source,67108864,()->{})))throw new IOException("RNA source changed during complete capture: "+row.getKey());
            try{boolean executable=Boolean.TRUE.equals(row.getValue().get("executable"));Files.setPosixFilePermissions(destination,PosixFilePermissions.fromString(executable?"rwxr-xr-x":"rw-r--r--"));if(mode(destination)!=(executable?0755:0644))throw new NextflowProcess.Unavailable("Filesystem does not retain original RNA Git source modes; use a permitted native Linux filesystem and retain the skipped analysis");}catch(UnsupportedOperationException failure){throw new NextflowProcess.Unavailable("Original RNA Git executable modes require a permitted POSIX filesystem, including a native Linux/WSL filesystem; dependent analysis skipped");}
        }
    }
    static String overlay(String engine,String capturedBundle,int cpus,long memoryBytes)throws IOException {
        NfcoreRnasplice104Request.path(engine);NfcoreRnasplice104Request.path(capturedBundle);if(cpus<1||cpus>1024||memoryBytes<16777216||memoryBytes>1099511627776L)throw new IOException("Invalid explicit local executor capacities");var out=new StringBuilder("process.executor = 'local'\nexecutor.cpus = ").append(cpus).append("\nexecutor.memory = '").append(memoryBytes).append(" B'\nsingularity.autoMounts = true\nsingularity.runOptions = '--userns --no-home --pid'\nsingularity.cacheDir = ").append(quote(engine+"/workspace/execution/container-cache")).append("\nprocess {\n");
        for(var row:new TreeMap<>(images()).entrySet()){Map<?,?> value=object(row.getValue());String hash=fingerprint(value.get("pin"),5000000000L).sha256();for(String name:strings(value.get("process_names")))out.append("  withName: ").append(quote(name)).append(" { container = ").append(quote(capturedBundle+"/images/"+hash+".sif")).append(" }\n");}
        return out.append("}\n").toString();
    }
    private static String quote(String value){return "'"+value.replace("\\","\\\\").replace("'","\\'")+"'";}
}
