/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Separate admission for original supplied-GTF Perl annotation; no installation during analysis. */
final class HomerAnnotationRuntime {
    private HomerAnnotationRuntime() { }
    private static final String MARKER=".jlinalg-homer-annotation.json";
    private static final String HEALTH="passed-original-supplied-GTF-annotation-health";
    private record Lock(Map<?,?> data,String hash) { }
    private static final Lock LOCK=load();
    record Context(Path home,String profile,String version,String markerHash,Map<String,String> files,
                   Map<String,HomerRuntime.External> external) {
        Context {files=Map.copyOf(files);external=Map.copyOf(external);}
        void verify()throws IOException {try{if(!equals(open(home,profile)))throw new IOException("Annotation runtime changed during analysis");}catch(HomerRuntime.Unavailable changed){throw new IOException("Annotation runtime changed during analysis;preserve diagnostics and re-admit a new home",changed);}}
    }
    private static Lock load(){
        try(var in=HomerAnnotationRuntime.class.getResourceAsStream("/org/jlinalg/omics/homer-annotation-lock.json")){
            if(in==null)throw new IOException("Missing original annotation lock");byte[] raw=in.readNBytes(1048577);if(raw.length>1048576)throw new IOException("Annotation lock too large");
            return new Lock(AdmixtureInputs.decode(raw),AdmixtureInputs.digest(raw));
        }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
    static Context open(Path supplied,String profile)throws HomerRuntime.Unavailable {
        try{
            if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch").toLowerCase(Locale.ROOT)))throw new IOException("Original annotation requires Linux x86_64 Java;on Windows use Linux Java inside WSL");
            Path home=supplied.toAbsolutePath().normalize();if(!home.toString().matches("/[A-Za-z0-9_./-]+")||!Files.isDirectory(home,LinkOption.NOFOLLOW_LINKS)||!home.toRealPath().equals(home))throw new IOException("Annotation home missing,linked or not a shell/Perl-safe ASCII installation path");
            Path marker=home.resolve(MARKER);HomerFasta.regular(marker,1048576);byte[] raw=AdmixtureInputs.raw(marker,1048576);Map<?,?> meta=AdmixtureInputs.decode(raw);
            Map<?,?> pin=AdmixtureInputs.map(AdmixtureInputs.map(LOCK.data.get("profiles")).get(profile));String version=AdmixtureInputs.text(pin,"version");
            if(AdmixtureInputs.whole(meta,"schema_version",1,1)!=1||!"HOMER annotation".equals(meta.get("tool"))||!home.toString().equals(meta.get("home"))||!profile.equals(meta.get("profile"))||!version.equals(meta.get("version"))||!LOCK.hash.equals(meta.get("lock_sha256")))throw new IOException("Annotation profile/version/path/lock differs;core-only profiles do not admit Perl annotation");
            Map<?,?> host=AdmixtureInputs.map(meta.get("host")),health=AdmixtureInputs.map(meta.get("health"));
            if(!"Linux".equals(host.get("os"))||!"x86_64".equals(host.get("architecture"))||!System.getProperty("os.version").equals(host.get("kernel"))||!HEALTH.equals(health.get("status")))throw new IOException("Annotation host/kernel or actual scientific health differs");
            if(AdmixtureInputs.whole(health,"rows",9,9)!=9||AdmixtureInputs.whole(health,"known_tss_rows",8,8)!=8||AdmixtureInputs.whole(health,"unknown_contig_rows",1,1)!=1||AdmixtureInputs.whole(health,"gtf_exons",4,4)!=4||AdmixtureInputs.whole(AdmixtureInputs.map(health.get("call")),"exit_code",0,0)!=0)throw new IOException("Annotation scientific health is incomplete");
            Map<?,?> members=AdmixtureInputs.map(pin.get("members")),recorded=AdmixtureInputs.map(meta.get("files"));var expectedNames=new HashSet<Object>(members.keySet());expectedNames.add("config.txt");if(!expectedNames.equals(recorded.keySet()))throw new IOException("Annotation member inventory differs");
            var files=new TreeMap<String,String>();
            for(var member:members.entrySet()){
                String name=member.getKey().toString();Map<?,?> expected=AdmixtureInputs.map(member.getValue());Path path=home.resolve(name);HomerFasta.regular(path,50000000);
                if(name.startsWith("bin/")&&!Files.isExecutable(path))throw new IOException("Annotation helper cannot execute: "+name);
                String digest=FollowupSupport.hash(path);if(!digest.equals(recorded.get(name)))throw new IOException("Annotation member changed: "+name);
                if(Boolean.TRUE.equals(expected.get("relocate"))){
                    if(!Set.of("bin/annotatePeaks.pl","bin/HomerConfig.pm").contains(name))throw new IOException("Unexpected relocated annotation member");
                    byte[] body=AdmixtureInputs.raw(path,2097152);byte[] prefix=prefix(home),old=Base64.getDecoder().decode(AdmixtureInputs.text(expected,"original_prefix_base64"));
                    if(body.length<prefix.length||!java.util.Arrays.equals(prefix,java.util.Arrays.copyOf(body,prefix.length)))throw new IOException("Annotation installation prefix differs");
                    byte[] original=new byte[old.length+body.length-prefix.length];System.arraycopy(old,0,original,0,old.length);System.arraycopy(body,prefix.length,original,old.length,body.length-prefix.length);
                    if(original.length!=AdmixtureInputs.whole(expected,"bytes",1,2097152)||!AdmixtureInputs.digest(original).equals(expected.get("sha256")))throw new IOException("Original annotation scientific body differs");
                }else if(Files.size(path)!=AdmixtureInputs.whole(expected,"bytes",1,50000000)||!digest.equals(expected.get("sha256")))throw new IOException("Original annotation member bytes differ");
                files.put(name,digest);
            }
            Path config=home.resolve("config.txt");HomerFasta.regular(config,8192);if(!Files.readString(config,StandardCharsets.UTF_8).equals(LOCK.data.get("generated_config")))throw new IOException("Annotation named-genome/organism configuration changed");files.put("config.txt",FollowupSupport.hash(config));if(!files.get("config.txt").equals(recorded.get("config.txt")))throw new IOException("Annotation configuration hash differs");
            var found=new HashSet<String>();try(var walk=Files.walk(home)){
                int count=0;for(var it=walk.iterator();it.hasNext();){Path path=it.next();if(++count>20||Files.isSymbolicLink(path))throw new IOException("Extra/linked annotation runtime member");if(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))found.add(home.relativize(path).toString().replace('\\','/'));else if(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Nonregular annotation runtime member");}
            }
            var permitted=new HashSet<>(files.keySet());permitted.add(MARKER);if(!permitted.equals(found))throw new IOException("Annotation runtime has extra/missing files");
            Map<?,?> closure=AdmixtureInputs.map(meta.get("external_files"));if(closure.size()<10||closure.size()>256||!closure.keySet().containsAll(Set.of("/usr/bin/perl","/bin/sh","/usr/bin/env","/usr/bin/rm","/usr/bin/mv","/usr/bin/cat","/usr/bin/ldd")))throw new IOException("Annotation Perl/host/library closure missing");
            var external=new TreeMap<String,HomerRuntime.External>();
            for(var entry:closure.entrySet()){
                String name=entry.getKey().toString();if(!name.matches("/(usr|lib|lib64|bin)/[A-Za-z0-9_./+-]+"))throw new IOException("Unsupported external annotation binding");
                Map<?,?> expected=AdmixtureInputs.map(entry.getValue());Path path=Path.of(name),real=path.toRealPath();HomerFasta.regular(real,100000000);String digest=FollowupSupport.hash(real);long bytes=Files.size(real);
                if(!real.toString().equals(expected.get("real_path"))||bytes!=AdmixtureInputs.whole(expected,"bytes",1,100000000)||!digest.equals(expected.get("sha256")))throw new IOException("Annotation host/Perl/library file changed: "+name);
                external.put(name,new HomerRuntime.External(real.toString(),bytes,digest));
            }
            return new Context(home,profile,version,AdmixtureInputs.digest(raw),files,external);
        }catch(Exception failure){throw new HomerRuntime.Unavailable("Original HOMER annotation dependency unavailable: "+failure.getMessage(),failure);}
    }
    private static byte[] prefix(Path home){return ("#!/usr/bin/env perl\nuse warnings;\nuse lib \""+home+"/bin\";\nmy $homeDir = \""+home+"/\";\n").getBytes(StandardCharsets.US_ASCII);}
    static int execute(Context runtime,List<String> arguments,Path work,int timeout,long bytes,long rss,Map<String,Object> session)throws Exception{
        var command=new ArrayList<>(List.of("/usr/bin/perl",runtime.home.resolve("bin/annotatePeaks.pl").toString()));command.addAll(arguments);
        session.put("runtime_profile",runtime.profile);session.put("runtime_version",runtime.version);session.put("runtime_home",runtime.home.toString());session.put("runtime_marker_sha256",runtime.markerHash);session.put("runtime_lock_sha256",LOCK.hash);session.put("runtime_members",runtime.files);session.put("external_file_count",runtime.external.size());
        return HomerRuntime.executeCommand(command,work,"original",timeout,bytes,rss,Map.of("PATH",runtime.home.resolve("bin")+":/usr/bin:/bin","PERL_HASH_SEED","0","PERL_PERTURB_KEYS","0"),runtime::verify,
                ()->{if(!runtime.markerHash.equals(FollowupSupport.hash(runtime.home.resolve(MARKER))))throw new IOException("HOMER annotation runtime marker changed during execution");},session);
    }
    static String recovery(){return "Required action: use Linux x86_64 Java,inside WSL on Windows,and a separate admitted annotation profile. Where permitted run python3 tools/literature/install_homer_annotation.py --profile linux-x86_64-annotation-5.1 --home NEW_PRIVATE_HOME --cache ARCHIVE_CACHE --report NEW_REPORT --allow-install. Choose annotation-4.11 or annotation-4.11.1 explicitly when needed;add --allow-download only where permitted,or supply --archive-dir VERIFIED_OFFLINE_ARCHIVES. Missing,forbidden,unsupported or changed dependencies SKIP this analysis,exit3;preserve old homes and failed reports,correct the cause and use new paths. Core-only profiles do not admit Perl annotation. Analysis never installs,substitutes versions or downloads genome data. Cell Ranger excluded.";}
}
