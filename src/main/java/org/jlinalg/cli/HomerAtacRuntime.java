/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Separate original tag/region/coverage/converter admission; no other HOMER home qualifies. */
final class HomerAtacRuntime {
    private HomerAtacRuntime() { }
    static final String MARKER=".jlinalg-homer-atac.json";
    private record Lock(Map<?,?> data,String hash) { }
    private static final Lock LOCK=load();
    private static final Set<String> UTILITIES=Set.of("/bin/sh","/usr/bin/env","/usr/bin/mkdir","/usr/bin/mv","/usr/bin/ldd");
    private static final Set<String> PROGRAMS=Set.of("makeTagDirectory","findPeaks","makeUCSCfile","bedGraphToBigWig");
    record Context(Path home,String profile,String markerHash,Map<String,String> files,
                   Map<String,HomerRuntime.External> external,Map<?,?> build) {
        Context {files=Map.copyOf(files);external=Map.copyOf(external);build=Map.copyOf(build);}
        void verify()throws IOException {try{if(!equals(open(home,profile)))throw new IOException("ATAC runtime changed during analysis");}catch(HomerRuntime.Unavailable failure){throw new IOException("ATAC runtime changed during analysis; preserve partials and re-admit a NEW home",failure);}}
        void markerCheck()throws IOException {if(!markerHash.equals(FollowupSupport.hash(home.resolve(MARKER))))throw new IOException("ATAC runtime marker changed during analysis");}
    }
    private static Lock load(){
        try(var in=HomerAtacRuntime.class.getResourceAsStream("/org/jlinalg/omics/homer-atac-lock.json")){
            if(in==null)throw new IOException("Missing ATAC lock");byte[] raw=in.readNBytes(1048577);if(raw.length>1048576)throw new IOException("ATAC lock too large");return new Lock(AdmixtureInputs.decode(raw),AdmixtureInputs.digest(raw));
        }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
    static String profile(){return LOCK.data.get("profile").toString();}
    static Context open(Path supplied,String profile)throws HomerRuntime.Unavailable {
        try {
            if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch").toLowerCase(Locale.ROOT)))throw new IOException("Original ATAC profile needs Linux x86_64 Java; use Linux Java inside WSL on Windows");
            Path home=supplied.toAbsolutePath().normalize();for(Path p=home;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Linked ATAC path/ancestor");
            if(!home.toString().matches("/[A-Za-z0-9_./-]+")||!Files.isDirectory(home,LinkOption.NOFOLLOW_LINKS)||!home.toRealPath().equals(home))throw new IOException("Missing,linked or unsafe private ATAC home");
            Path marker=home.resolve(MARKER);HomerFasta.regular(marker,1048576);byte[] raw=AdmixtureInputs.raw(marker,1048576);Map<?,?> meta=AdmixtureInputs.decode(raw);
            if(AdmixtureInputs.whole(meta,"schema_version",1,1)!=1||!LOCK.data.get("tool").equals(meta.get("tool"))||!profile().equals(profile)||!profile.equals(meta.get("profile"))||!"5.1".equals(meta.get("version"))||!home.toString().equals(meta.get("home"))||!LOCK.hash.equals(meta.get("lock_sha256")))throw new IOException("ATAC version/profile/path/lock differs; annotation/genomic homes do not admit ATAC");
            Map<?,?> host=AdmixtureInputs.map(meta.get("host"));if(!"Linux".equals(host.get("os"))||!"x86_64".equals(host.get("architecture"))||!System.getProperty("os.version").equals(host.get("kernel")))throw new IOException("ATAC host/kernel binding changed");
            Map<?,?> members=AdmixtureInputs.map(LOCK.data.get("members")),converter=AdmixtureInputs.map(LOCK.data.get("converter")),recorded=AdmixtureInputs.map(meta.get("files"));var expected=new HashSet<String>();for(Object name:members.keySet())expected.add(name.toString());expected.addAll(Set.of("bin/makeTagDirectory","bin/bedGraphToBigWig","share/UCSC_LICENSE","config.txt"));
            if(!expected.equals(recorded.keySet()))throw new IOException("ATAC file inventory differs");var files=new TreeMap<String,String>();
            for(String name:expected){
                Path path=home.resolve(name);HomerFasta.regular(path,67108864);if(name.startsWith("bin/")&&!Files.isExecutable(path))throw new IOException("ATAC helper cannot execute: "+name);String hash=FollowupSupport.hash(path);if(!hash.equals(recorded.get(name)))throw new IOException("ATAC member changed: "+name);files.put(name,hash);
                if(members.containsKey(name)){Map<?,?> pin=AdmixtureInputs.map(members.get(name));if(!hash.equals(pin.get("sha256"))||Files.size(path)!=AdmixtureInputs.whole(pin,"bytes",1,67108864))throw new IOException("Original ATAC helper differs: "+name);}
            }
            if(!files.get("bin/bedGraphToBigWig").equals(converter.get("sha256"))||Files.size(home.resolve("bin/bedGraphToBigWig"))!=AdmixtureInputs.whole(converter,"bytes",1,67108864)||!files.get("share/UCSC_LICENSE").equals(converter.get("license_sha256"))||!Files.readString(home.resolve("config.txt"),StandardCharsets.UTF_8).equals(LOCK.data.get("generated_config")))throw new IOException("ATAC converter/license/configuration differs");
            Map<?,?> build=AdmixtureInputs.map(meta.get("build"));var originalSources=new TreeMap<String,String>();for(var source:AdmixtureInputs.map(LOCK.data.get("build_sources")).entrySet())originalSources.put(source.getKey().toString(),AdmixtureInputs.text(AdmixtureInputs.map(source.getValue()),"sha256"));
            if(!"built-original5.1-tag-builder-installation-prefix-only".equals(build.get("status"))||!originalSources.equals(build.get("original_sources"))||!LOCK.data.get("build_recipe").equals(build.get("recipe"))||!files.get("bin/makeTagDirectory").equals(build.get("binary_sha256"))||AdmixtureInputs.whole(AdmixtureInputs.map(build.get("compile")),"exit_code",0,0)!=0||AdmixtureInputs.whole(AdmixtureInputs.map(build.get("link")),"exit_code",0,0)!=0)throw new IOException("Original configured tag-builder build proof differs");
            verifyBuild(build,home);
            var found=new HashSet<String>();try(var walk=Files.walk(home)){int count=0;for(var it=walk.iterator();it.hasNext();){Path path=it.next();if(++count>32||Files.isSymbolicLink(path))throw new IOException("Extra/linked ATAC home member");if(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))found.add(home.relativize(path).toString());else if(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Nonregular ATAC home member");}}expected.add(MARKER);if(!found.equals(expected))throw new IOException("ATAC home contains extra/missing files");
            Map<?,?> closure=AdmixtureInputs.map(meta.get("external_files"));if(!closure.keySet().containsAll(UTILITIES)||closure.size()<5||closure.size()>256)throw new IOException("ATAC host/library closure incomplete");var external=new TreeMap<String,HomerRuntime.External>();
            for(var binding:closure.entrySet()){
                String name=binding.getKey().toString();if(!name.matches("/(usr|lib|lib64|bin)/[A-Za-z0-9_./+-]+"))throw new IOException("Unsupported ATAC host binding");Map<?,?> pin=AdmixtureInputs.map(binding.getValue());Path real=Path.of(name).toRealPath();HomerFasta.regular(real,100000000);String hash=FollowupSupport.hash(real);long bytes=Files.size(real);
                if(!real.toString().equals(pin.get("real_path"))||bytes!=AdmixtureInputs.whole(pin,"bytes",1,100000000)||!hash.equals(pin.get("sha256")))throw new IOException("ATAC host/library binding changed: "+name);external.put(name,new HomerRuntime.External(real.toString(),bytes,hash));
            }
            Map<?,?> health=AdmixtureInputs.map(meta.get("health")),healthPin=AdmixtureInputs.map(LOCK.data.get("health"));if(!LOCK.data.get("health_status").equals(health.get("status"))||AdmixtureInputs.whole(health,"replicates",2,2)!=2||AdmixtureInputs.whole(health,"merged_tag_mass",2900,2900)!=2900||!healthPin.get("merged_tag_sha256").equals(health.get("merged_tag_sha256"))||!healthPin.get("peak_rows_sha256").equals(health.get("peak_rows_sha256"))||AdmixtureInputs.whole(health,"peak_count",3,3)!=3||!healthPin.get("coverage_sha256").equals(health.get("coverage_sha256"))||!healthPin.get("coverage_rows").equals(health.get("coverage_rows")))throw new IOException("Original ATAC tag/region/coverage health incomplete");
            Map<?,?> bigwig=AdmixtureInputs.map(health.get("bigwig"));if(!"passed-every-raw-interval-float32".equals(bigwig.get("status"))||!healthPin.get("coverage_rows").equals(bigwig.get("rows")))throw new IOException("Original BigWig raw health incomplete");
            if(!(health.get("calls") instanceof List<?> calls)||calls.size()!=6)throw new IOException("Six original ATAC scientific health calls required");for(Object call:calls)if(AdmixtureInputs.whole(AdmixtureInputs.map(call),"exit_code",0,0)!=0)throw new IOException("Original ATAC health operation failed");
            if(!AdmixtureInputs.digest(raw).equals(FollowupSupport.hash(marker)))throw new IOException("ATAC marker changed during verification");return new Context(home,profile,AdmixtureInputs.digest(raw),files,external,build);
        }catch(Exception failure){throw new HomerRuntime.Unavailable("Original HOMER ATAC dependency unavailable: "+failure.getMessage(),failure);}
    }
    private static void verifyBuild(Map<?,?> build,Path home)throws IOException {
        Map<?,?> tools=AdmixtureInputs.map(build.get("tool_executables"));String compiler=null,make=null;
        for(var entry:tools.entrySet()){
            String name=entry.getKey().toString();Map<?,?> value=AdmixtureInputs.map(entry.getValue());if(!name.matches("/[A-Za-z0-9_./+-]+")||!AdmixtureInputs.text(value,"real_path").matches("/[A-Za-z0-9_./+-]+")||!AdmixtureInputs.text(value,"sha256").matches("[0-9a-f]{64}"))throw new IOException("ATAC historical build-tool identity differs");
            if(Path.of(name).getFileName().toString().endsWith("g++")){if(compiler!=null)throw new IOException("Multiple ATAC compiler identities");compiler=name;}if(Path.of(name).getFileName().toString().equals("make")){if(make!=null)throw new IOException("Multiple ATAC make identities");make=name;}
        }
        if(tools.size()!=2||compiler==null||make==null)throw new IOException("ATAC build-tool identities incomplete");Map<?,?> recipe=AdmixtureInputs.map(LOCK.data.get("build_recipe"));if(!(recipe.get("objects") instanceof List<?> objects)||!(recipe.get("link_flags") instanceof List<?> flags)||!(AdmixtureInputs.map(build.get("link")).get("command") instanceof List<?> command)||command.size()!=objects.size()+flags.size()+3)throw new IOException("ATAC link proof incomplete");
        Path source=Path.of(command.get(1).toString()).getParent();var link=new ArrayList<String>();link.add(compiler);for(Object object:objects)link.add(source.resolve(object.toString()).toString());for(Object flag:flags)link.add(flag.toString());link.addAll(List.of("-o",home.resolve("bin/makeTagDirectory").toString()));
        var compile=new ArrayList<String>(List.of(make,"-B","COMPILER="+compiler,"DEBUG="+AdmixtureInputs.text(recipe,"compile_flags")));for(Object object:objects)compile.add(object.toString());if(!link.equals(command)||!compile.equals(AdmixtureInputs.map(build.get("compile")).get("command")))throw new IOException("ATAC configured build command/home differs");
        for(var pair:List.of(Map.entry("compiler_version",compiler),Map.entry("make_version",make))){Map<?,?> version=AdmixtureInputs.map(build.get(pair.getKey()));if(!List.of(pair.getValue(),"--version").equals(version.get("command"))||AdmixtureInputs.whole(version,"exit_code",0,0)!=0)throw new IOException("ATAC historical build version proof incomplete");}
        var original=new TreeMap<String,String>();for(var entry:AdmixtureInputs.map(LOCK.data.get("build_sources")).entrySet())original.put(Path.of(entry.getKey().toString()).getFileName().toString(),AdmixtureInputs.text(AdmixtureInputs.map(entry.getValue()),"sha256"));Map<?,?> configured=AdmixtureInputs.map(build.get("configured_sources"));
        if(!configured.keySet().equals(original.keySet())||!AdmixtureInputs.text(configured,"SeqTag.cpp").matches("[0-9a-f]{64}"))throw new IOException("ATAC configured source inventory differs");for(var entry:original.entrySet())if(!entry.getKey().equals("SeqTag.cpp")&&!entry.getValue().equals(configured.get(entry.getKey())))throw new IOException("Original ATAC scientific source differs");
    }
    static int execute(Context runtime,String program,List<String> arguments,Path work,String label,int timeout,long bytes,long rss,Map<String,Object> session)throws Exception {
        work(work);if(!PROGRAMS.contains(program))throw new IOException("Unsupported admitted ATAC executable");var command=new ArrayList<String>();command.add(runtime.home.resolve("bin/"+program).toString());command.addAll(arguments);
        session.put("runtime_profile",runtime.profile);session.put("runtime_home",runtime.home.toString());session.put("runtime_lock_sha256",LOCK.hash);session.put("runtime_marker_sha256",runtime.markerHash);session.put("runtime_files",runtime.files);session.put("runtime_build",runtime.build);session.put("external_file_count",runtime.external.size());
        return HomerRuntime.executeCommand(command,work,label,timeout,bytes,rss,Map.of("PATH",runtime.home.resolve("bin")+":/usr/bin:/bin"),runtime::verify,runtime::markerCheck,session);
    }
    static void work(Path work)throws HomerRuntime.Unavailable {
        try{Path path=work.toAbsolutePath().normalize();if(!path.toString().matches("/[A-Za-z0-9_./-]+")||!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)||!path.toRealPath().equals(path))throw new IOException("Original C++ tag creation uses shell utilities and comma-sensitive filename parsing; work paths must be real private Linux ASCII paths containing only letters,digits,underscore,dot,slash or hyphen");}
        catch(IOException failure){throw new HomerRuntime.Unavailable("ATAC output path capability unavailable: "+failure.getMessage()+". Required action: choose a NEW output under a real Linux/WSL parent with only the permitted ASCII path characters; source SAM paths may remain Unicode because they are copied to safe aliases",failure);}
    }
    static String recovery(){return "Required action: use Linux x86_64 Java, inside WSL on Windows, and the separate linux-x86_64-atac-5.1 profile. Where permitted run python3 tools/literature/install_homer_atac.py --home NEW_PRIVATE_ATAC_HOME --cache ARCHIVE_CACHE --report NEW_REPORT --allow-install --compiler /PATH/TO/g++ --make /PATH/TO/make --archive-dir VERIFIED_ARCHIVES --converter-path PINNED_BEDGRAPHTOBIGWIG; alternatively explicitly allow downloads. Exact original5.1 archive and originalUCSC2.10 content pins are in homer-atac-lock.json. Setup needs C++11 headers, pthread, math and static C++/GCC runtime libraries; no OS package is installed. Missing, forbidden, changed or unsupported dependencies SKIP this analysis, exit3. Other failures exit2 retain partial/. Preserve homes/reports, correct the named cause and rerun to NEW paths. Analysis never installs, registers or substitutes versions. Read WARNINGS.md and stderr. No named genome, alignment, deduplication, RNA or Cell Ranger capability is admitted.";}
}
