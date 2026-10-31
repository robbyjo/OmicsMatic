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

/** Separate original RefSeq RNA admission; other HOMER profiles do not qualify. */
final class HomerRnaRuntime {
    private HomerRnaRuntime() { }
    static final String MARKER=".jlinalg-homer-rna.json";
    private record Lock(Map<?,?> data,String hash) { }
    private static final Lock LOCK=load();
    private static final Set<String> UTILITIES=Set.of("/usr/bin/perl","/bin/sh","/usr/bin/env",
            "/usr/bin/rm","/usr/bin/mv","/usr/bin/cat","/usr/bin/mkdir","/usr/bin/ldd");
    private static final Set<String> PROGRAMS=Set.of("makeTagDirectory","getPeakTags","analyzeRepeats.pl");
    private static final Set<String> RELOCATED=Set.of("bin/analyzeRepeats.pl","bin/HomerConfig.pm",
            "bin/addGeneAnnotation.pl","bin/convertIDs.pl");

    record Context(Path home,Path resources,String profile,String markerHash,
                   Map<String,String> files,Map<String,String> resourceHashes,
                   Map<String,HomerRuntime.External> external,Map<?,?> build) {
        Context {
            files=Map.copyOf(files);resourceHashes=Map.copyOf(resourceHashes);
            external=Map.copyOf(external);build=Map.copyOf(build);
        }
        void verify()throws IOException {
            try { if(!equals(open(home,profile)))throw new IOException("RNA runtime or references changed during analysis"); }
            catch(HomerRuntime.Unavailable failure){throw new IOException("RNA dependency changed; preserve partials and re-admit a NEW home",failure);}
        }
        void markerCheck()throws IOException {
            if(!markerHash.equals(FollowupSupport.hash(home.resolve(MARKER))))throw new IOException("RNA runtime marker changed during analysis");
        }
        Path genomeFile(String genome,String suffix)throws IOException {
            if(!Set.of("hg38","mm10").contains(genome)||!Set.of(genome+".rna","chrom.sizes").contains(suffix))throw new IOException("Unsupported admitted RNA genome file");
            return resources.resolve("data/genomes/"+genome+"/"+suffix);
        }
    }

    private static Lock load() {
        try(var in=HomerRnaRuntime.class.getResourceAsStream("/org/jlinalg/omics/homer-rna-lock.json")) {
            if(in==null)throw new IOException("Missing RNA lock");
            byte[] raw=in.readNBytes(1048577);if(raw.length>1048576)throw new IOException("RNA lock too large");
            return new Lock(AdmixtureInputs.decode(raw),AdmixtureInputs.digest(raw));
        } catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
    static String profile(){return LOCK.data.get("profile").toString();}
    static String lockHash(){return LOCK.hash;}

    static Context open(Path supplied,String profile)throws HomerRuntime.Unavailable {
        try {
            if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch").toLowerCase(Locale.ROOT))) {
                throw new IOException("Original RNA requires Linux x86_64 Java; use Linux Java inside WSL on Windows");
            }
            Path home=safe(supplied);Path marker=home.resolve(MARKER);HomerFasta.regular(marker,1048576);
            byte[] raw=AdmixtureInputs.raw(marker,1048576);Map<?,?> meta=AdmixtureInputs.decode(raw);
            Path resources=safe(Path.of(AdmixtureInputs.text(meta,"resource_root")));
            if(AdmixtureInputs.whole(meta,"schema_version",1,1)!=1||!LOCK.data.get("tool").equals(meta.get("tool"))
                    ||!"5.1".equals(meta.get("version"))||!profile().equals(profile)||!profile.equals(meta.get("profile"))
                    ||!home.toString().equals(meta.get("home"))||!LOCK.hash.equals(meta.get("lock_sha256"))) {
                throw new IOException("RNA profile/version/path/lock differs; named and ATAC homes do not admit RNA");
            }
            if(home.equals(resources)||home.startsWith(resources)||resources.startsWith(home))throw new IOException("RNA runtime and resource roots must be nonnested");
            Map<?,?> host=AdmixtureInputs.map(meta.get("host"));
            if(!"Linux".equals(host.get("os"))||!"x86_64".equals(host.get("architecture"))||!System.getProperty("os.version").equals(host.get("kernel")))throw new IOException("RNA host/kernel binding differs");
            verifyHealth(AdmixtureInputs.map(meta.get("health")),home);
            var files=new TreeMap<String,String>();Map<?,?> members=AdmixtureInputs.map(LOCK.data.get("members"));
            for(var entry:members.entrySet()) {
                String name=entry.getKey().toString();Map<?,?> pin=AdmixtureInputs.map(entry.getValue());Path path=home.resolve(name);noLinks(path);HomerFasta.regular(path,67108864);
                if(name.startsWith("bin/")&&!Files.isExecutable(path))throw new IOException("RNA helper cannot execute: "+name);
                String hash=FollowupSupport.hash(path);
                if(Boolean.TRUE.equals(pin.get("relocate"))) {
                    if(!RELOCATED.contains(name))throw new IOException("Unexpected relocated RNA member");
                    byte[] body=AdmixtureInputs.raw(path,2097152),prefix=prefix(home),old=Base64.getDecoder().decode(AdmixtureInputs.text(pin,"original_prefix_base64"));
                    if(body.length<prefix.length||!java.util.Arrays.equals(prefix,java.util.Arrays.copyOf(body,prefix.length)))throw new IOException("RNA installation prefix differs");
                    byte[] original=new byte[old.length+body.length-prefix.length];System.arraycopy(old,0,original,0,old.length);System.arraycopy(body,prefix.length,original,old.length,body.length-prefix.length);
                    if(original.length!=AdmixtureInputs.whole(pin,"bytes",1,2097152)||!AdmixtureInputs.digest(original).equals(pin.get("sha256")))throw new IOException("Original RNA scientific body differs: "+name);
                } else if(Files.size(path)!=AdmixtureInputs.whole(pin,"bytes",1,67108864)||!hash.equals(pin.get("sha256")))throw new IOException("Original RNA helper bytes differ: "+name);
                files.put(name,hash);
            }
            Path config=home.resolve("config.txt");HomerFasta.regular(config,8192);
            if(!Files.readString(config,StandardCharsets.UTF_8).equals(config(home,resources)))throw new IOException("RNA genome/organism configuration differs");
            files.put("config.txt",FollowupSupport.hash(config));
            var resourceHashes=new TreeMap<String,String>();Map<?,?> pins=AdmixtureInputs.map(LOCK.data.get("resources"));
            for(var entry:pins.entrySet()) {
                String name=entry.getKey().toString();Map<?,?> pin=AdmixtureInputs.map(entry.getValue());Path path=resources.resolve(name);noLinks(path);HomerFasta.regular(path,268435456);
                String hash=FollowupSupport.hash(path);long size=Files.size(path);
                if(size!=AdmixtureInputs.whole(pin,"bytes",1,268435456)||!hash.equals(pin.get("sha256")))throw new IOException("Original RNA reference changed: "+name);
                resourceHashes.put(name,hash);
                if(name.startsWith("data/accession/")) {
                    Path copy=home.resolve(name);noLinks(copy);HomerFasta.regular(copy,268435456);
                    if(Files.size(copy)!=size||!FollowupSupport.hash(copy).equals(hash))throw new IOException("RNA accession copy differs: "+name);
                    files.put(name,hash);
                }
            }
            Path binary=home.resolve("bin/makeTagDirectory");HomerFasta.regular(binary,67108864);
            if(!Files.isExecutable(binary))throw new IOException("Configured original RNA tag helper cannot execute");
            files.put("bin/makeTagDirectory",FollowupSupport.hash(binary));Map<?,?> build=AdmixtureInputs.map(meta.get("build"));
            var originalSources=new TreeMap<String,String>();for(var entry:AdmixtureInputs.map(LOCK.data.get("build_sources")).entrySet())originalSources.put(entry.getKey().toString(),AdmixtureInputs.text(AdmixtureInputs.map(entry.getValue()),"sha256"));
            if(!"built-original5.1-tag-builder-installation-prefix-only".equals(build.get("status"))||!originalSources.equals(build.get("original_sources"))
                    ||!LOCK.data.get("build_recipe").equals(build.get("recipe"))||!files.get("bin/makeTagDirectory").equals(build.get("binary_sha256"))
                    ||AdmixtureInputs.whole(AdmixtureInputs.map(build.get("compile")),"exit_code",0,0)!=0||AdmixtureInputs.whole(AdmixtureInputs.map(build.get("link")),"exit_code",0,0)!=0)throw new IOException("Original RNA configured-source build proof differs");
            verifyBuild(build,home);
            if(!files.equals(AdmixtureInputs.map(meta.get("files")))||!resourceHashes.equals(AdmixtureInputs.map(meta.get("resources"))))throw new IOException("RNA runtime/reference hash inventory differs");
            var found=new HashSet<String>();try(var walk=Files.walk(home)){int count=0;for(var it=walk.iterator();it.hasNext();) {
                Path path=it.next();if(++count>128||Files.isSymbolicLink(path))throw new IOException("Extra/linked RNA home entry");
                if(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))found.add(home.relativize(path).toString());else if(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Nonregular RNA home member");
            }}
            var allowed=new HashSet<>(files.keySet());allowed.add(MARKER);if(!found.equals(allowed))throw new IOException("RNA home has extra/missing members");
            Map<?,?> closure=AdmixtureInputs.map(meta.get("external_files"));
            if(closure.size()<10||closure.size()>256||!closure.keySet().containsAll(UTILITIES))throw new IOException("RNA Perl/POSIX/ELF/utility closure incomplete");
            var external=new TreeMap<String,HomerRuntime.External>();
            for(var entry:closure.entrySet()) {
                String name=entry.getKey().toString();if(!name.matches("/(usr|lib|lib64|bin)/[A-Za-z0-9_./+-]+"))throw new IOException("Unsupported RNA external binding");
                Map<?,?> pin=AdmixtureInputs.map(entry.getValue());Path real=Path.of(name).toRealPath();HomerFasta.regular(real,100000000);String hash=FollowupSupport.hash(real);long bytes=Files.size(real);
                if(!real.toString().equals(pin.get("real_path"))||bytes!=AdmixtureInputs.whole(pin,"bytes",1,100000000)||!hash.equals(pin.get("sha256")))throw new IOException("RNA host/library binding changed: "+name);
                external.put(name,new HomerRuntime.External(real.toString(),bytes,hash));
            }
            if(!AdmixtureInputs.digest(raw).equals(FollowupSupport.hash(marker)))throw new IOException("RNA marker changed during verification");
            return new Context(home,resources,profile,AdmixtureInputs.digest(raw),files,resourceHashes,external,build);
        } catch(Exception failure){throw new HomerRuntime.Unavailable("Original HOMER RNA dependency unavailable: "+failure.getMessage(),failure);}
    }

    private static void verifyHealth(Map<?,?> health,Path home)throws IOException {
        if(!LOCK.data.get("health_status").equals(health.get("status")))throw new IOException("Original RNA health status differs");
        Map<?,?> genomes=AdmixtureInputs.map(health.get("genomes")),pins=AdmixtureInputs.map(AdmixtureInputs.map(LOCK.data.get("health")).get("genomes"));
        if(!genomes.keySet().equals(pins.keySet()))throw new IOException("Both complete RNA reference health runs required");
        for(var entry:pins.entrySet()) {
            Map<?,?> pin=AdmixtureInputs.map(entry.getValue()),value=AdmixtureInputs.map(genomes.get(entry.getKey()));
            if(!(pin.get("replicates") instanceof List<?> replicates)||replicates.size()!=2)throw new IOException("RNA health replicate lock incomplete");
            var weights=new ArrayList<Object>();var hashes=new ArrayList<Object>();for(Object raw:replicates){Map<?,?> p=AdmixtureInputs.map(raw);weights.add(p.get("weight"));hashes.add(p.get("chr1_tag_sha256"));}
            if(AdmixtureInputs.whole(value,"replicates",2,2)!=2||!weights.equals(value.get("weights"))||!hashes.equals(value.get("tag_sha256"))
                    ||!pin.get("expanded_rows").equals(value.get("expanded_rows"))||!pin.get("expanded_body_sha256").equals(value.get("expanded_body_sha256"))
                    ||!pin.get("matrices").equals(value.get("matrices")))throw new IOException("Complete RNA health tag/feature/value populations differ");
            if(!(value.get("calls") instanceof List<?> calls)||calls.size()!=7)throw new IOException("Seven original RNA health calls required per reference");
            for(Object raw:calls) {
                Map<?,?> call=AdmixtureInputs.map(raw);if(AdmixtureInputs.whole(call,"exit_code",0,0)!=0||!Boolean.TRUE.equals(call.get("owned_group_empty_after_wait"))||!(call.get("command") instanceof List<?> command)||command.isEmpty())throw new IOException("Original RNA health operation incomplete");
                String program=command.get(0).toString();boolean direct=Set.of(home.resolve("bin/makeTagDirectory").toString(),home.resolve("bin/getPeakTags").toString()).contains(program);
                boolean perl=program.equals("/usr/bin/perl")&&command.size()>1&&command.get(1).equals(home.resolve("bin/analyzeRepeats.pl").toString());
                if(!direct&&!perl)throw new IOException("RNA historical health command/home differs");
            }
        }
    }

    private static void verifyBuild(Map<?,?> build,Path home)throws IOException {
        Map<?,?> tools=AdmixtureInputs.map(build.get("tool_executables"));String compiler=null,make=null;
        for(var entry:tools.entrySet()) {
            String name=entry.getKey().toString();Map<?,?> value=AdmixtureInputs.map(entry.getValue());
            if(!name.matches("/[A-Za-z0-9_./+-]+")||!AdmixtureInputs.text(value,"real_path").matches("/[A-Za-z0-9_./+-]+")||!AdmixtureInputs.text(value,"sha256").matches("[0-9a-f]{64}"))throw new IOException("RNA historical build-tool identity differs");
            if(Path.of(name).getFileName().toString().endsWith("g++")){if(compiler!=null)throw new IOException("Multiple RNA compiler identities");compiler=name;}
            if(Path.of(name).getFileName().toString().equals("make")){if(make!=null)throw new IOException("Multiple RNA make identities");make=name;}
        }
        if(tools.size()!=2||compiler==null||make==null)throw new IOException("RNA historical build-tool identities incomplete");
        Map<?,?> recipe=AdmixtureInputs.map(LOCK.data.get("build_recipe"));
        if(!(recipe.get("objects") instanceof List<?> objects)||!(recipe.get("link_flags") instanceof List<?> flags)||!(AdmixtureInputs.map(build.get("link")).get("command") instanceof List<?> command)||command.size()!=objects.size()+flags.size()+3)throw new IOException("RNA original link proof incomplete");
        Path source=Path.of(command.get(1).toString()).getParent();var link=new ArrayList<String>();link.add(compiler);for(Object object:objects)link.add(source.resolve(object.toString()).toString());for(Object flag:flags)link.add(flag.toString());link.addAll(List.of("-o",home.resolve("bin/makeTagDirectory").toString()));
        var compile=new ArrayList<String>(List.of(make,"-B","COMPILER="+compiler,"DEBUG="+AdmixtureInputs.text(recipe,"compile_flags")));for(Object object:objects)compile.add(object.toString());
        if(!link.equals(command)||!compile.equals(AdmixtureInputs.map(build.get("compile")).get("command")))throw new IOException("RNA configured build command/home differs");
        for(var pair:List.of(Map.entry("compiler_version",compiler),Map.entry("make_version",make))){Map<?,?> version=AdmixtureInputs.map(build.get(pair.getKey()));if(!List.of(pair.getValue(),"--version").equals(version.get("command"))||AdmixtureInputs.whole(version,"exit_code",0,0)!=0)throw new IOException("RNA historical build version proof incomplete");}
        var original=new TreeMap<String,String>();for(var entry:AdmixtureInputs.map(LOCK.data.get("build_sources")).entrySet())original.put(Path.of(entry.getKey().toString()).getFileName().toString(),AdmixtureInputs.text(AdmixtureInputs.map(entry.getValue()),"sha256"));Map<?,?> configured=AdmixtureInputs.map(build.get("configured_sources"));
        if(!configured.keySet().equals(original.keySet())||!AdmixtureInputs.text(configured,"SeqTag.cpp").matches("[0-9a-f]{64}"))throw new IOException("RNA configured source inventory differs");
        for(var entry:original.entrySet())if(!entry.getKey().equals("SeqTag.cpp")&&!entry.getValue().equals(configured.get(entry.getKey())))throw new IOException("Original RNA scientific source differs");
    }

    private static Path safe(Path path)throws IOException {
        Path result=path.toAbsolutePath().normalize();noLinks(result);
        if(!result.toString().matches("/[A-Za-z0-9_./-]+")||!Files.isDirectory(result,LinkOption.NOFOLLOW_LINKS)||!result.toRealPath().equals(result))throw new IOException("RNA home/resource root missing,linked or unsafe");
        return result;
    }
    private static void noLinks(Path path)throws IOException {for(Path p=path;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Linked RNA path/ancestor");}
    private static byte[] prefix(Path home){return ("#!/usr/bin/env perl\nuse warnings;\nuse lib \""+home+"/bin\";\nmy $homeDir = \""+home+"/\";\n").getBytes(StandardCharsets.US_ASCII);}
    private static String config(Path home,Path root)throws IOException {
        var lines=new ArrayList<>(List.of("# Explicit pinned HOMER RNA; no sequence or ontology operation.","SOFTWARE","ORGANISMS"));Map<?,?> entries=AdmixtureInputs.map(LOCK.data.get("config_entries"));
        for(String name:List.of("human","mouse"))lines.add(join(entries.get(name),null));lines.addAll(List.of("PROMOTERS","GENOMES"));
        for(String name:List.of("hg38","mm10"))lines.add(join(entries.get(name),home.relativize(root.resolve("data/genomes/"+name))+"/"));lines.add("SETTINGS");return String.join("\n",lines)+"\n";
    }
    private static String join(Object raw,String path)throws IOException {
        if(!(raw instanceof List<?> row)||row.size()!=6)throw new IOException("Invalid RNA config lock row");var fields=new ArrayList<String>();for(Object value:row)fields.add(value.toString());if(path!=null)fields.set(4,path);return String.join("\t",fields);
    }
    static void memory(long rss)throws HomerRuntime.Unavailable {
        try {
            long minimum=((Number)LOCK.data.get("minimum_upstream_memory_bytes")).longValue(),required=((Number)LOCK.data.get("minimum_available_memory_bytes")).longValue();
            if(rss<minimum)throw new IOException("Original RNA needs at least4GiB allowed upstream RSS");long available=-1;
            for(String line:Files.readAllLines(Path.of("/proc/meminfo"),StandardCharsets.US_ASCII))if(line.startsWith("MemAvailable:")){String[] f=line.trim().split("\\s+");if(f.length!=3||!f[2].equals("kB"))throw new IOException("Invalid RNA available-memory units");available=Math.multiplyExact(Long.parseLong(f[1]),1024);}
            if(available<required)throw new IOException("At least6GiB available memory required before RNA launch");
        }catch(Exception failure){throw new HomerRuntime.Unavailable("RNA memory capability unavailable: "+failure.getMessage(),failure);}
    }
    static void work(Path work)throws HomerRuntime.Unavailable {
        try {Path path=work.toAbsolutePath().normalize();noLinks(path);if(!path.toString().matches("/[A-Za-z0-9_./-]+")||!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)||!path.toRealPath().equals(path))throw new IOException("Original RNA work needs a real private ASCII Linux path with only letters,digits,underscore,dot,slash or hyphen");}
        catch(IOException failure){throw new HomerRuntime.Unavailable("RNA work path capability unavailable: "+failure.getMessage(),failure);}
    }
    static int execute(Context runtime,String program,List<String> arguments,Path work,String label,int timeout,long bytes,long rss,Map<String,Object> session)throws Exception {
        work(work);memory(rss);if(!PROGRAMS.contains(program))throw new IOException("Unsupported admitted RNA executable");
        var command=new ArrayList<String>();if(program.endsWith(".pl"))command.add("/usr/bin/perl");command.add(runtime.home.resolve("bin/"+program).toString());command.addAll(arguments);
        session.put("runtime_profile",runtime.profile);session.put("runtime_home",runtime.home.toString());session.put("runtime_marker_sha256",runtime.markerHash);session.put("runtime_lock_sha256",LOCK.hash);session.put("runtime_files",runtime.files);session.put("runtime_build",runtime.build);session.put("resource_root",runtime.resources.toString());session.put("resource_sha256",runtime.resourceHashes);session.put("external_file_count",runtime.external.size());
        return HomerRuntime.executeCommand(command,work,label,timeout,bytes,rss,Map.of("PATH",runtime.home.resolve("bin")+":/usr/bin:/bin","PERL_HASH_SEED","0","PERL_PERTURB_KEYS","0"),runtime::verify,runtime::markerCheck,session);
    }
    static String recovery(){return "Required action: use Linux x86_64 Java inside WSL on Windows and the separate linux-x86_64-rna-5.1-v7.0 profile. Prepare eight exact files with prepare_homer_rna_resources.py; where permitted run install_homer_rna.py --home NEW_PRIVATE_RNA_HOME --resource-root PINNED_RNA_RESOURCE_ROOT --cache SOFTWARE_CACHE --report NEW_REPORT --allow-install --archive-dir VERIFIED_SOFTWARE_ARCHIVES --compiler /PATH/TO/g++ --make /PATH/TO/make, or explicitly permit downloads. Allow at least4GiB upstream RSS and6GiB available memory; Java heap is separate. Missing/forbidden/unsupported/changed dependencies SKIP this analysis, exit3; other failures exit2 retain partials. Inspect WARNINGS.md,session.json and original logs,correct the named cause and rerun to NEW paths. Analysis never installs,registers or substitutes editions. No alignment,deduplication,DESeq2,R,GO or Cell Ranger capability is admitted.";}
}
