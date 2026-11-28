/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;

/** All original source files/Git modes and every original module/import image alias. */
final class NfcoreHic210Profile {
    private NfcoreHic210Profile(){ }
    static final String ID="nf-core/hic-2.1.0",ENGINE="linux-x86_64-nextflow-22.10.6",RESOURCE="/org/jlinalg/omics/nfcore-hic210/";
    static final Pin SOURCE=new Pin(34330,"5685f27bfb3e4ffcafb94158bd811ae6d716cdc4a67982d730eb5d0c0e3a233b");
    static final Pin IMAGES=new Pin(10104,"1b1cf58637b4dc57a3eb26f412469d41db9a1e4c97cbf3d5d05120f7529a79ef");
    static final Pin EXPORTER=new Pin(2988,"c5225f0392632911737ca845b284ebb967f55f19b99f764746ccac988c0ffb47");
    static final Pin SETUP=new Pin(7569,"3098a1f35814412a643c980c821fd2ecf3ca72eed85d0227913cfa587da08b83");
    static final String COMMIT="fe4ac656317d24c37e81e7940a526ed9ea812f8e";
    static byte[] resource(String name,Pin expected)throws IOException{
        try(var stream=NfcoreHic210Profile.class.getResourceAsStream(RESOURCE+name)){
            if(stream==null)throw new NextflowProcess.Unavailable("Required packaged original Hi-C profile is missing; dependent analysis skipped");byte[] raw=stream.readNBytes(Math.toIntExact(expected.bytes()+1));if(!new Pin(raw.length,sha(raw)).equals(expected))throw new IOException("Changed source-bound Hi-C profile: "+name);return raw;
        }
    }
    static Map<?,?> objectResource(String name,Pin expected)throws IOException{return object(NextflowParameters.parse(utf8(resource(name,expected)),1048576,100000));}
    static void exportSetup(Path supplied)throws IOException{byte[] profile=resource("IMAGES.json",IMAGES),script=resource("install_images.py",SETUP);Path root=supplied.toAbsolutePath().normalize();if(root.getParent()==null||!root.getParent().toRealPath().equals(root.getParent()))throw new IOException("Use a new image-setup export directory with canonical existing parent");Files.createDirectory(root);Files.write(root.resolve("IMAGES.json"),profile,StandardOpenOption.CREATE_NEW);Files.write(root.resolve("setup.py"),script,StandardOpenOption.CREATE_NEW);Files.writeString(root.resolve("README.txt"),"Original HiC2.1.0 source images only. No analysis executed.\n\npython3 setup.py --home NEW_PRIVATE_HOME --report NEW_REPORT --cache PRIVATE_CACHE --allow-install --allow-download\n\nUse Linux/WSL Python3.11+. Omit --allow-download for exact offline SHA256-named cache files. Omit both flags to check an existing completed home. Corrupt caches and partials remain retained. Namespace execution is checked by the typed workflow. SKIP3: dependent analysis skipped until exact permitted prerequisites are ready; FAIL2: preserve diagnostics and correct integrity/input/capacity errors using NEW paths. The generated declaration.json is the typed request image_set.\n",java.nio.charset.StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
    static Map<String,Map<?,?>> sourceFiles()throws IOException{
        var p=objectResource("SOURCE-PROFILE.json",SOURCE);if(!ID.equals(p.get("pipeline_profile"))||!COMMIT.equals(p.get("source_commit")))throw new IOException("Original Hi-C source edition differs");var result=new TreeMap<String,Map<?,?>>();int executable=0;
        for(var entry:object(p.get("files")).entrySet()){
            String name=member(text(entry.getKey()));var row=object(entry.getValue());keys(row,"bytes","sha256","git_mode","git_blob_sha1","executable");fingerprint(row,67108864);boolean marked=Boolean.TRUE.equals(row.get("executable"));if(!(row.get("executable")instanceof Boolean)||!Set.of("100644","100755").contains(row.get("git_mode"))||marked!=row.get("git_mode").equals("100755")||!text(row.get("git_blob_sha1")).matches("[0-9a-f]{40}"))throw new IOException("Original Hi-C Git source declaration differs");if(marked)executable++;result.put(name,row);
        }
        if(result.size()!=126||executable!=17)throw new IOException("Incomplete original126-file Hi-C source or17 executable Git modes");return result;
    }
    static Map<?,?> images()throws IOException{
        var p=objectResource("IMAGES.json",IMAGES);if(!ID.equals(p.get("pipeline_profile"))||!COMMIT.equals(p.get("source_commit")))throw new IOException("Original Hi-C image edition differs");var images=object(p.get("images"));var aliases=object(p.get("aliases"));var seen=new HashSet<String>();
        for(var entry:images.entrySet()){
            String uri=text(entry.getKey());var row=object(entry.getValue());keys(row,"pin","modules","process_names");fingerprint(row.get("pin"),5000000000L);for(String name:strings(row.get("process_names")))if(!name.matches("[A-Za-z_][A-Za-z_0-9]*")||!seen.add(name)||!uri.equals(aliases.get(name)))throw new IOException("Original Hi-C process/image import alias differs");
        }
        if(images.size()!=14||aliases.size()!=30||!seen.equals(aliases.keySet()))throw new IOException("Incomplete original14-image/30-alias Hi-C closure");return images;
    }
    static void copySource(Path source,Path target)throws IOException{
        if(!Files.exists(source))throw new NextflowProcess.Unavailable("Exact original Hi-C source is missing; use nfcore-tools source setup for nf-core/hic-2.1.0 or retain the skip");directory(source);var expected=sourceFiles();var files=new HashSet<String>();var directories=new HashSet<String>();for(String name:expected.keySet())for(int at=name.lastIndexOf('/');at>0;at=name.lastIndexOf('/',at-1))directories.add(name.substring(0,at));
        try(var stream=Files.walk(source)){
            for(var iterator=stream.iterator();iterator.hasNext();){Path p=iterator.next();if(p.equals(source))continue;String name=source.relativize(p).toString().replace('\\','/');if(Files.isSymbolicLink(p))throw new IOException("Linked original Hi-C source member");if(Files.isDirectory(p)){directory(p);if(!directories.contains(name))throw new IOException("Extra original source directory: "+name);continue;}var row=expected.get(name);if(row==null||!fingerprint(row,67108864).equals(pin(p,67108864,()->{})))throw new IOException("Changed/extra original Hi-C source: "+name);files.add(name);}
        }
        if(!files.equals(expected.keySet()))throw new NextflowProcess.Unavailable("Incomplete original Hi-C source; complete exact permitted source setup or retain skipped analysis");Files.createDirectory(target);
        for(var entry:expected.entrySet()){
            Path from=source.resolve(entry.getKey()),to=target.resolve(entry.getKey());Files.createDirectories(to.getParent());Files.copy(from,to);var expectedPin=fingerprint(entry.getValue(),67108864);if(!pin(from,67108864,()->{}).equals(expectedPin)||!pin(to,67108864,()->{}).equals(expectedPin))throw new IOException("Original Hi-C source changed during capture");
            try{int wanted=Boolean.TRUE.equals(entry.getValue().get("executable"))?0755:0644;Files.setPosixFilePermissions(to,PosixFilePermissions.fromString(wanted==0755?"rwxr-xr-x":"rw-r--r--"));if(mode(to)!=wanted)throw new NextflowProcess.Unavailable("Filesystem did not preserve original Hi-C Git modes; use native Linux storage and NEW paths");}catch(UnsupportedOperationException e){throw new NextflowProcess.Unavailable("Original Hi-C Git execution modes require a permitted POSIX filesystem; use native Linux/WSL storage and NEW paths");}
        }
    }
    static String overlay(String engine,String captured,int cpus,long memory)throws IOException{
        NfcoreHic210Request.path(engine);NfcoreHic210Request.path(captured);var result=new StringBuilder("env.MPLCONFIGDIR = '.matplotlib'\nprocess.executor = 'local'\nexecutor.cpus = ").append(cpus).append("\nexecutor.memory = '").append(memory).append(" B'\nsingularity.autoMounts = true\nsingularity.runOptions = '--userns --no-home --pid'\nsingularity.cacheDir = '").append(engine).append("/workspace/execution/container-cache'\nprocess {\n");
        for(var entry:new TreeMap<>(images()).entrySet()){var row=object(entry.getValue());String hash=fingerprint(row.get("pin"),5000000000L).sha256();for(String name:strings(row.get("process_names")))result.append("  withName: '").append(name).append("' { container = '").append(captured).append("/images/").append(hash).append(".sif' }\n");}return result.append("}\n").toString();
    }
}
