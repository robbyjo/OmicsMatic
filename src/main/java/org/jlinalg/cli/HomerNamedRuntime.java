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

/** Separate original named-annotation admission with pinned complete resource files. */
final class HomerNamedRuntime {
    private HomerNamedRuntime() { }
    static final String MARKER=".jlinalg-homer-named.json";
    private record Lock(Map<?,?> data,String hash) { }
    private static final Lock LOCK=load();
    record Context(Path home,Path resources,String profile,String markerHash,Map<String,String> files,
                   Map<String,String> resourceHashes,Map<String,HomerRuntime.External> external) {
        Context {files=Map.copyOf(files);resourceHashes=Map.copyOf(resourceHashes);external=Map.copyOf(external);}
        void verify()throws IOException {try{if(!equals(open(home,profile)))throw new IOException("Named runtime or resources changed during analysis");}catch(HomerRuntime.Unavailable changed){throw new IOException("Named dependency changed during analysis; preserve diagnostics and re-admit a new home",changed);}}
        void markerCheck()throws IOException {if(!markerHash.equals(FollowupSupport.hash(home.resolve(MARKER))))throw new IOException("Named runtime marker changed during analysis");}
        Path genomeFile(String genome,String suffix){return resources.resolve("data/genomes/"+genome+"/"+suffix);}
    }
    private static Lock load(){
        try(var in=HomerNamedRuntime.class.getResourceAsStream("/org/jlinalg/omics/homer-named-lock.json")){
            if(in==null)throw new IOException("Missing named annotation lock");byte[] raw=in.readNBytes(1048577);if(raw.length>1048576)throw new IOException("Named lock too large");return new Lock(AdmixtureInputs.decode(raw),AdmixtureInputs.digest(raw));
        }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
    static String profile(){return LOCK.data.get("profile").toString();}
    static Context open(Path supplied,String profile)throws HomerRuntime.Unavailable {
        try{
            if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch").toLowerCase(Locale.ROOT)))throw new IOException("Original named annotation requires Linux x86_64 Java; use Linux Java inside WSL on Windows");
            Path home=safe(supplied);HomerFasta.regular(home.resolve(MARKER),1048576);byte[] raw=AdmixtureInputs.raw(home.resolve(MARKER),1048576);Map<?,?> meta=AdmixtureInputs.decode(raw);Path resources=safe(Path.of(AdmixtureInputs.text(meta,"resource_root")));
            if(AdmixtureInputs.whole(meta,"schema_version",1,1)!=1||!LOCK.data.get("tool").equals(meta.get("tool"))||!profile().equals(profile)||!profile.equals(meta.get("profile"))||!"5.1".equals(meta.get("version"))||!home.toString().equals(meta.get("home"))||!LOCK.hash.equals(meta.get("lock_sha256")))throw new IOException("Named profile/version/path/lock differs; other HOMER admissions do not admit named annotation");
            Map<?,?> host=AdmixtureInputs.map(meta.get("host")),health=AdmixtureInputs.map(meta.get("health"));
            if(!"Linux".equals(host.get("os"))||!"x86_64".equals(host.get("architecture"))||!System.getProperty("os.version").equals(host.get("kernel"))||!LOCK.data.get("health_status").equals(health.get("status")))throw new IOException("Named host/kernel or actual scientific health differs");
            Map<?,?> hg=AdmixtureInputs.map(health.get("genomes"));if(!hg.keySet().equals(Set.of("hg38","mm10")))throw new IOException("Both named-resource health runs are required");
            for(String genome:List.of("hg38","mm10")){
                Map<?,?> h=AdmixtureInputs.map(hg.get(genome));
                if(AdmixtureInputs.whole(h,"rows",7,7)!=7||AdmixtureInputs.whole(h,"columns",19,19)!=19||AdmixtureInputs.whole(AdmixtureInputs.map(h.get("call")),"exit_code",0,0)!=0||!healthFixtureDigest(genome).equals(h.get("peaks_sha256")))throw new IOException("Named scientific health is incomplete");
            }
            var files=new TreeMap<String,String>();Map<?,?> members=AdmixtureInputs.map(LOCK.data.get("members")),recorded=AdmixtureInputs.map(meta.get("files"));
            for(var member:members.entrySet()){
                String name=member.getKey().toString();Map<?,?> expected=AdmixtureInputs.map(member.getValue());Path path=home.resolve(name);HomerFasta.regular(path,50000000);
                if(!Files.isExecutable(path))throw new IOException("Named helper cannot execute: "+name);String digest=FollowupSupport.hash(path);
                if(Boolean.TRUE.equals(expected.get("relocate"))){
                    if(!Set.of("bin/annotatePeaks.pl","bin/HomerConfig.pm").contains(name))throw new IOException("Unexpected named relocated member");
                    byte[] body=AdmixtureInputs.raw(path,2097152),prefix=prefix(home),old=Base64.getDecoder().decode(AdmixtureInputs.text(expected,"original_prefix_base64"));
                    if(body.length<prefix.length||!java.util.Arrays.equals(prefix,java.util.Arrays.copyOf(body,prefix.length)))throw new IOException("Named installation prefix differs");
                    byte[] original=new byte[old.length+body.length-prefix.length];System.arraycopy(old,0,original,0,old.length);System.arraycopy(body,prefix.length,original,old.length,body.length-prefix.length);
                    if(original.length!=AdmixtureInputs.whole(expected,"bytes",1,2097152)||!AdmixtureInputs.digest(original).equals(expected.get("sha256")))throw new IOException("Original named scientific body differs");
                }else if(Files.size(path)!=AdmixtureInputs.whole(expected,"bytes",1,50000000)||!digest.equals(expected.get("sha256")))throw new IOException("Original named helper bytes differ");
                files.put(name,digest);
            }
            Path config=home.resolve("config.txt");HomerFasta.regular(config,8192);if(!Files.readString(config,StandardCharsets.UTF_8).equals(config(home,resources)))throw new IOException("Named configuration differs");files.put("config.txt",FollowupSupport.hash(config));
            var resourceHashes=new TreeMap<String,String>();Map<?,?> resourcePins=AdmixtureInputs.map(LOCK.data.get("resources"));
            for(var entry:resourcePins.entrySet()){
                String name=entry.getKey().toString();Map<?,?> pin=AdmixtureInputs.map(entry.getValue());Path path=resources.resolve(name);noLinks(path);HomerFasta.regular(path,2147483648L);String digest=FollowupSupport.hash(path);
                if(Files.size(path)!=AdmixtureInputs.whole(pin,"bytes",1,2147483648L)||!digest.equals(pin.get("sha256")))throw new IOException("Original named resource changed: "+name);resourceHashes.put(name,digest);
                if(name.startsWith("data/accession/")){Path copy=home.resolve(name);noLinks(copy);HomerFasta.regular(copy,268435456);if(Files.size(copy)!=Files.size(path)||!FollowupSupport.hash(copy).equals(digest))throw new IOException("Named organism accession copy differs: "+name);files.put(name,digest);}
            }
            if(!files.equals(recorded)||!resourceHashes.equals(AdmixtureInputs.map(meta.get("resources"))))throw new IOException("Named runtime/resource hash inventories differ");
            var found=new HashSet<String>();try(var walk=Files.walk(home)){int count=0;for(var it=walk.iterator();it.hasNext();){Path path=it.next();if(++count>32||Files.isSymbolicLink(path))throw new IOException("Extra/linked named member");if(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))found.add(home.relativize(path).toString());else if(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Nonregular named member");}}
            var permitted=new HashSet<>(files.keySet());permitted.add(MARKER);if(!permitted.equals(found))throw new IOException("Named home has extra/missing files");
            Map<?,?> closure=AdmixtureInputs.map(meta.get("external_files"));if(closure.size()<10||closure.size()>256||!closure.keySet().containsAll(Set.of("/usr/bin/perl","/bin/sh","/usr/bin/env","/usr/bin/rm","/usr/bin/mv","/usr/bin/cat","/usr/bin/ldd")))throw new IOException("Named Perl/host/library closure missing");
            var external=new TreeMap<String,HomerRuntime.External>();for(var entry:closure.entrySet()){
                String name=entry.getKey().toString();if(!name.matches("/(usr|lib|lib64|bin)/[A-Za-z0-9_./+-]+"))throw new IOException("Unsupported named external binding");Map<?,?> pin=AdmixtureInputs.map(entry.getValue());Path real=Path.of(name).toRealPath();HomerFasta.regular(real,100000000);String digest=FollowupSupport.hash(real);long bytes=Files.size(real);
                if(!real.toString().equals(pin.get("real_path"))||bytes!=AdmixtureInputs.whole(pin,"bytes",1,100000000)||!digest.equals(pin.get("sha256")))throw new IOException("Named host/Perl/library binding changed: "+name);external.put(name,new HomerRuntime.External(real.toString(),bytes,digest));
            }
            return new Context(home,resources,profile,AdmixtureInputs.digest(raw),files,resourceHashes,external);
        }catch(Exception failure){throw new HomerRuntime.Unavailable("Original HOMER named-annotation dependency unavailable: "+failure.getMessage(),failure);}
    }
    static String healthFixtureDigest(String genome)throws IOException{
        Map<?,?> pin=AdmixtureInputs.map(AdmixtureInputs.map(LOCK.data.get("health")).get(genome));Object raw=pin.get("peaks");if(!(raw instanceof String peaks)||peaks.isEmpty())throw new IOException("Named health fixture text missing");return AdmixtureInputs.digest(peaks.getBytes(StandardCharsets.UTF_8));
    }
    private static Path safe(Path path)throws IOException{Path result=path.toAbsolutePath().normalize();noLinks(result);if(!result.toString().matches("/[A-Za-z0-9_./-]+")||!Files.isDirectory(result,LinkOption.NOFOLLOW_LINKS)||!result.toRealPath().equals(result))throw new IOException("Named home/resource root missing, linked or unsafe");return result;}
    private static void noLinks(Path path)throws IOException{for(Path p=path;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Linked named path/ancestor");}
    private static byte[] prefix(Path home){return ("#!/usr/bin/env perl\nuse warnings;\nuse lib \""+home+"/bin\";\nmy $homeDir = \""+home+"/\";\n").getBytes(StandardCharsets.US_ASCII);}
    private static String config(Path home,Path resources)throws IOException{
        var lines=new ArrayList<>(List.of("# Explicit pinned HOMER named annotation; no ontology or sequence operation.","SOFTWARE","ORGANISMS"));Map<?,?> entries=AdmixtureInputs.map(LOCK.data.get("config_entries"));
        for(String name:List.of("human","mouse"))lines.add(join(entries.get(name),null));lines.addAll(List.of("PROMOTERS","GENOMES"));for(String name:List.of("hg38","mm10"))lines.add(join(entries.get(name),home.relativize(resources.resolve("data/genomes/"+name))+"/"));lines.add("SETTINGS");return String.join("\n",lines)+"\n";
    }
    private static String join(Object raw,String path)throws IOException{if(!(raw instanceof List<?> row)||row.size()!=6)throw new IOException("Invalid named config lock row");var result=new ArrayList<String>();for(Object field:row)result.add(field.toString());if(path!=null)result.set(4,path);return String.join("\t",result);}
    static void memory(long rss)throws HomerRuntime.Unavailable {
        long minimum=((Number)LOCK.data.get("minimum_upstream_memory_bytes")).longValue();try{
            if(rss<minimum)throw new IOException("Original named annotation needs at least6GiB allowed upstream RSS");long available=-1;for(String line:Files.readAllLines(Path.of("/proc/meminfo"),StandardCharsets.US_ASCII))if(line.startsWith("MemAvailable:")){String[] fields=line.trim().split("\\s+");if(fields.length!=3||!fields[2].equals("kB"))throw new IOException("Invalid available-memory units");available=Math.multiplyExact(Long.parseLong(fields[1]),1024);}
            if(available<minimum)throw new IOException("At least6GiB available upstream memory is required before launch");
        }catch(Exception failure){throw new HomerRuntime.Unavailable("Named annotation memory capability unavailable: "+failure.getMessage(),failure);}
    }
    static int execute(Context runtime,String genome,boolean statistics,Path work,int timeout,long bytes,long rss,Map<String,Object> session)throws Exception {
        memory(rss);var command=new ArrayList<>(List.of("/usr/bin/perl",runtime.home.resolve("bin/annotatePeaks.pl").toString(),"stage-peaks.pos",genome,"-size","given"));if(statistics)command.addAll(List.of("-annStats","original-annotation-stats.tsv"));
        session.put("runtime_profile",runtime.profile);session.put("runtime_home",runtime.home.toString());session.put("runtime_marker_sha256",runtime.markerHash);session.put("runtime_lock_sha256",LOCK.hash);session.put("runtime_members",runtime.files);session.put("resource_root",runtime.resources.toString());session.put("resource_sha256",runtime.resourceHashes);session.put("external_file_count",runtime.external.size());
        return HomerRuntime.executeCommand(command,work,"original",timeout,bytes,rss,Map.of("PATH",runtime.home.resolve("bin")+":/usr/bin:/bin","PERL_HASH_SEED","0","PERL_PERTURB_KEYS","0"),runtime::verify,runtime::markerCheck,session);
    }
    static String recovery(){return "Required action: use Linux x86_64 Java, inside WSL on Windows, and the separate linux-x86_64-named-annotation-5.1-v7.0 profile. Where permitted run python3 tools/literature/install_homer_named.py --annotation-home ADMITTED_5_1_ANNOTATION_HOME --home NEW_PRIVATE_NAMED_HOME --resource-root PINNED_RESOURCE_ROOT --report NEW_REPORT --allow-install. The twelve required original v7.0 files are listed in homer-named-lock.json. Allow at least6GiB upstream RSS and available RAM; set Java heap and input/audit capacities separately. Missing, forbidden, changed or unsupported dependencies SKIP this analysis, exit3; other failures exit2. Preserve failed reports and homes, correct the named cause and rerun to NEW paths. Analysis never installs, downloads, registers or substitutes versions. Default named region annotation does not admit sequence/GC, motif, peak, coverage, RNA or GO operations. Review WARNINGS.md and original.stderr. Cell Ranger remains excluded.";}
}
