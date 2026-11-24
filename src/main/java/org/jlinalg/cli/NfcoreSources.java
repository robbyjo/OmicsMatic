/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;

/** Full original source verification. This class never executes pipeline software. */
final class NfcoreSources {
    private NfcoreSources() { }
    static final String STATUS="VERIFIED_NFCORE_SOURCE_ONLY_NO_ANALYSIS";
    static final String MARKER=".omicsmatic-nfcore-source.json";
    static final String RESOURCE="/org/jlinalg/cli/nfcore-source-setup/";
    static final Pin LOCK=new Pin(210075,"41ead51194221d7515e21b2bae7c1ae647e1824b0167e28cd36b95348722fdde");
    static final Pin SETUP=new Pin(11153,"140e66a57a848d94257cf5cc5419300f3e761b7f3cf468e6a1688fd915137b30");
    static final String ACTION="Source setup does not run an nf-core analysis. Qualify the exact Nextflow engine, process dependencies or immutable containers, remote configurations, references, sample sheet and complete outputs before using the separate workflow adapter. Do not infer an unreported paper edition. Preserve partials and diagnostics; correct the named cause and use NEW paths.";
    static final class Unavailable extends IOException { private static final long serialVersionUID=1L; Unavailable(String message){super(message);} }

    static byte[] resource(String name,Pin expected)throws IOException {
        try(var stream=NfcoreSources.class.getResourceAsStream(RESOURCE+name)){
            if(stream==null)throw new Unavailable("Missing packaged nf-core source setup resource: "+name);
            byte[] raw=stream.readNBytes(Math.toIntExact(expected.bytes()+1));
            if(raw.length!=expected.bytes()||!sha(raw).equals(expected.sha256()))throw new IOException("Changed pinned nf-core setup resource: "+name);
            return raw;
        }
    }
    static Map<?,?> profiles()throws IOException {
        var root=object(NextflowParameters.parse(utf8(resource("source-lock.json",LOCK)),1048576,100000));
        keys(root,"schema_version","status","profiles","whole_goal_complete");integer(root.get("schema_version"),1,1);
        if(!"PINNED_ORIGINAL_NFCORE_SOURCES_ONLY".equals(root.get("status"))||!Boolean.FALSE.equals(root.get("whole_goal_complete")))throw new IOException("Changed nf-core source-only scope");
        var profiles=object(root.get("profiles"));
        if(profiles.size()!=7)throw new IOException("Incomplete exact source profile population");
        return profiles;
    }
    static Map<?,?> profile(String id)throws IOException {
        var value=profiles().get(id);
        if(value==null)throw new Unavailable("Unsupported exact source profile "+id+"; select --source-profiles without guessing an edition");
        return object(value);
    }
    static Map<String,Object> inspect(String id,Path supplied)throws IOException {
        var profile=profile(id);Path home=supplied.toAbsolutePath().normalize();
        if(!Files.isDirectory(home,LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Missing permitted source home "+home+"; export source setup and install it where permitted");
        directory(home);Path source=home.resolve("source"),marker=home.resolve(MARKER);
        if(!Files.isRegularFile(marker,LinkOption.NOFOLLOW_LINKS))throw new Unavailable("No completed source-only marker in "+home);
        var state=object(json(marker,65536));keys(state,"schema_version","status","profile","source_lock_sha256","analysis_admitted");integer(state.get("schema_version"),1,1);
        if(!STATUS.equals(state.get("status"))||!id.equals(state.get("profile"))||!LOCK.sha256().equals(state.get("source_lock_sha256"))||!Boolean.FALSE.equals(state.get("analysis_admitted")))throw new IOException("Changed source-only installation identity");
        var top=new TreeSet<String>();try(var entries=Files.list(home)){for(Path p:entries.toList())top.add(p.getFileName().toString());}
        if(!top.equals(Set.of("source",MARKER)))throw new IOException("Unexpected or missing source-home entry");
        directory(source);var expected=object(profile.get("files"));var expectedDirs=new TreeSet<String>();
        for(Object value:expected.keySet()){
            String name=member(text(value));
            for(int at=name.lastIndexOf('/');at>0;at=name.lastIndexOf('/',at-1))expectedDirs.add(name.substring(0,at));
        }
        var found=new TreeSet<String>();var dirs=new TreeSet<String>();var modeDepartures=new ArrayList<String>();long[] total={0};
        Files.walkFileTree(source,new SimpleFileVisitor<>(){
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attributes)throws IOException{
                directory(path);if(!path.equals(source)){
                    String name=source.relativize(path).toString().replace('\\','/');
                    if(!expectedDirs.contains(name))throw new IOException("Unexpected source directory: "+name);dirs.add(name);
                }
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attributes)throws IOException{
                String name=source.relativize(path).toString().replace('\\','/');Object row=expected.get(name);
                if(row==null||!attributes.isRegularFile())throw new IOException("Unexpected/nonregular source member: "+name);
                var original=object(row);keys(original,"bytes","sha256","executable");var identity=fingerprint(original,67108864);
                if(!identity.equals(pin(path,identity.bytes(),()->{})))throw new IOException("Changed original source member: "+name);
                if(!(original.get("executable")instanceof Boolean executable))throw new IOException("Invalid source execution-mode declaration");
                int actualMode=mode(path);if(actualMode>=0&&((actualMode&0111)!=0)!=executable)modeDepartures.add(name);
                found.add(name);total[0]=Math.addExact(total[0],identity.bytes());return FileVisitResult.CONTINUE;
            }
        });
        if(!found.equals(expected.keySet())||!dirs.equals(expectedDirs))throw new IOException("Incomplete original source namespace");
        Collections.sort(modeDepartures);var result=new LinkedHashMap<String,Object>();result.put("status",STATUS);result.put("profile",id);
        for(String field:List.of("pipeline","version","commit"))result.put(field,profile.get(field));
        result.put("source_files",found.size());result.put("source_bytes",total[0]);result.put("permission_mode_departures",modeDepartures);
        result.put("exact_POSIX_modes_claimed",modeDepartures.isEmpty()&&source.getFileSystem().supportedFileAttributeViews().contains("unix"));
        result.put("analysis_admitted",false);result.put("required_next_steps",profile.get("required_next_steps"));return result;
    }
    static void export(Path supplied)throws IOException {
        Path root=supplied.toAbsolutePath().normalize();
        if(root.getParent()==null||!root.getParent().toRealPath().equals(root.getParent()))throw new IOException("Use a canonical parent for a NEW source setup export");
        // Validate both bundled payloads before creating output.
        byte[] lock=resource("source-lock.json",LOCK),setup=resource("setup.py",SETUP);
        Files.createDirectory(root);
        try{
            Files.write(root.resolve("source-lock.json"),lock,StandardOpenOption.CREATE_NEW);Files.write(root.resolve("setup.py"),setup,StandardOpenOption.CREATE_NEW);
            Files.writeString(root.resolve("README.txt"),"NF-CORE SOURCE SETUP ONLY\n\n"+ACTION+"\n\npython setup.py --profile nf-core/chipseq-1.2.1 --home NEW_SOURCE_HOME --report NEW_REPORT --cache PRIVATE_CACHE --allow-install --allow-download\n\nOmit --allow-download for an exact offline cache. Omit both flags to check an existing source home using a NEW report. This helper installs MIT pipeline source only; it installs no engine, process tool, container or reference and executes no upstream code. Exit0 means source-only verification, never an analysis. SKIP3 means unavailable/prohibited source setup; dependent pipeline analysis must be skipped. FAIL2 means integrity/input failure, no complete source result. Read the report's WARNINGS.md, including the eager GATK 3.5 dependency restriction.\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        }catch(IOException|RuntimeException failure){
            try{Files.writeString(root.resolve("FAILURE.txt"),"FAIL2: incomplete source setup export; preserve files and use a NEW output after correcting the cause. "+failure,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}catch(IOException diagnostic){failure.addSuppressed(diagnostic);}throw failure;
        }
    }
}
