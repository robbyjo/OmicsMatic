/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/** Owned compilation of exact original archives; setup does not admit a scientific result. */
final class RsemBuild {
    private RsemBuild() { }
    static final Set<String> GENERATED=Set.of("samtools-1.3/config.h","samtools-1.3/config.mk","samtools-1.3/htslib-1.3/config.h");
    static final long ARCHIVE_BYTES=67108864, SOURCE_BYTES=2147483648L;
    record Tools(Path cc,Path cxx,Path make,Path ar,Path ranlib,List<Path> roots,List<Path> includes,List<Path> libraries) {
        Tools {roots=List.copyOf(roots);includes=List.copyOf(includes);libraries=List.copyOf(libraries);}
        List<Path> programs(){return List.of(cc,cxx,make,ar,ranlib);}
    }
    record Limits(RsemFileGraph.Limits compiler,RsemProcess.Limits process,RsemHost.Limits host,long wholeMillis,int jobs) {
        Limits {if(wholeMillis<1||wholeMillis>86400000||jobs<1||jobs>32)throw new IllegalArgumentException("Require explicit whole-build deadline and compiler parallelism");}
        static Limits defaults(){return new Limits(new RsemFileGraph.Limits(536870912,34359738368L,300000,64,268435456),
                new RsemProcess.Limits(900000,8589934592L,900,1073741824,1024,8589934592L,300000,64,256,4294967296L,25),
                new RsemHost.Limits(new RepeatHostFiles.Limits(536870912,8589934592L,100000,128,67108864),
                        new RsemProcess.Limits(60000,1073741824,60,16777216,256,1073741824,100000,8,64,536870912,25),1024),3600000,2);}
    }
    record SourceFile(long bytes,String sha256) { }
    record Context(Path home,RsemPayload.Profile profile,Path source,Path decoder,List<Path> libraries,
            String recordSha256,RsemFileGraph.Snapshot sourceGraph,Map<String,String> evidence) {
        Context {libraries=List.copyOf(libraries);evidence=Collections.unmodifiableMap(new TreeMap<>(evidence));}
        void verify(Runnable check)throws IOException{
            RsemPayload.profiles();sourceGraph.verify(check);
            if(!recordSha256.equals(hash(home.resolve("build.json"),67108864,check)))throw new IOException("RSEM source-build receipt changed");
            for(var entry:evidence.entrySet())if(!hash(home.resolve(entry.getKey()),536870912,check).equals(entry.getValue()))
                throw new IOException("RSEM source-build evidence changed: "+entry.getKey());
        }
    }
    private record Compiler(Path home,Tools tools,Map<String,String> files,List<Path> nativePrograms,String lockSha256,
            RsemFileGraph.Snapshot prefix,RsemFileGraph.Snapshot owned) implements RsemProvider {
        Compiler {files=Collections.unmodifiableMap(new TreeMap<>(files));nativePrograms=List.copyOf(nativePrograms);}
        public String profile(){return "rsem-original-source-compiler-v1";}
        public List<Path> libraryDirectories(){return tools.libraries();}
        public List<Path> providerRoots(){var result=new ArrayList<>(tools.roots());result.add(home);return List.copyOf(result);}
        public List<Path> executablePaths(){var result=new ArrayList<>(nativePrograms);result.addAll(files.keySet().stream().filter(n->n.startsWith("bin/")).map(home::resolve).toList());return List.copyOf(result);}
        public Map<String,String> environment(Path work)throws IOException{
            var env=new LinkedHashMap<String,String>();var paths=new java.util.LinkedHashSet<String>();paths.add(home.resolve("bin").toString());tools.programs().forEach(p->paths.add(p.getParent().toString()));paths.add("/usr/bin");paths.add("/bin");
            env.put("PATH",String.join(":",paths));env.put("LANG","C");env.put("LC_ALL","C");env.put("TZ","UTC");env.put("SHELL","/bin/sh");
            env.put("CC",home.resolve("bin/gcc").toString());env.put("CXX",home.resolve("bin/g++").toString());env.put("AR",home.resolve("bin/ar").toString());env.put("RANLIB",home.resolve("bin/ranlib").toString());
            String include=String.join(" ",tools.includes().stream().map(p->"-I"+p).toList());
            env.put("CFLAGS","-O2 -fcommon"+(include.isEmpty()?"":" "+include));env.put("CPPFLAGS",include);
            env.put("LDFLAGS",String.join(" ",tools.libraries().stream().map(p->"-L"+p+" -Wl,-rpath,"+p).toList()));
            if(!tools.includes().isEmpty()){String dirs=String.join(":",tools.includes().stream().map(Path::toString).toList());env.put("C_INCLUDE_PATH",dirs);env.put("CPLUS_INCLUDE_PATH",dirs);}
            if(!tools.libraries().isEmpty()){String dirs=String.join(":",tools.libraries().stream().map(Path::toString).toList());env.put("LIBRARY_PATH",dirs);env.put("LD_LIBRARY_PATH",dirs);}
            env.put("OMP_NUM_THREADS","1");env.put("OPENBLAS_NUM_THREADS","1");env.put("MKL_NUM_THREADS","1");return Map.copyOf(env);
        }
        public void verify(Runnable check)throws IOException{owned.verify(check);prefix.verify(check);}
    }
    static Context build(String version,Path suppliedArchive,Path cache,boolean allowDownload,boolean allowInstall,Tools tools,
            Path suppliedHome,Limits limits,Runnable cancellation)throws IOException{
        if(!allowInstall)throw unavailable("Installation was not permitted");
        if(!"Linux".equals(System.getProperty("os.name"))||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw unavailable("Source setup requires Linux x86_64 Java");
        RsemPayload.Profile profile=RsemPayload.profiles().get(version);if(profile==null)throw new IOException("Unknown exact RSEM source release;do not substitute another edition");
        validateTools(tools);Path home=suppliedHome.toAbsolutePath().normalize();RsemPayload.originalPath(home);
        if(home.getParent()==null||!home.getParent().toRealPath().equals(home.getParent()))throw new IOException("Source setup requires an existing canonical parent and NEW home");
        for(Path root:tools.roots())if(home.startsWith(root)||root.startsWith(home))throw new IOException("RSEM build home must be disjoint from every read-only compiler root");
        Runnable check=deadline(limits.wholeMillis(),cancellation);
        Path archive=archive(profile,suppliedArchive,cache,allowDownload,check);Files.createDirectory(home);
        try{
            Path metadata=Files.createDirectory(home.resolve("metadata")),inputs=Files.createDirectory(home.resolve("inputs")),outputs=Files.createDirectory(home.resolve("outputs")),archiveCopy=inputs.resolve("original-source.tar.gz");
            Files.copy(archive,archiveCopy);if(!hash(archiveCopy,ARCHIVE_BYTES,check).equals(profile.archiveSha256()))throw new IOException("Source archive changed during owned copy");
            Path source=home.resolve("RSEM-"+profile.commit());var original=extract(archiveCopy,home,"RSEM-"+profile.commit(),check);
            write(metadata.resolve("original-source.json"),original);Path decoderSource=inputs.resolve("decoder.cpp");
            try(var stream=RsemBuild.class.getResourceAsStream("/org/jlinalg/omics/rsem-setup/decode.cpp")){
                if(stream==null)throw new IOException("Missing pinned source-linked decoder source");byte[] raw=stream.readNBytes(1048577);
                if(raw.length>1048576||!AdmixtureInputs.digest(raw).equals("f6cb8e71fc5172374f0cf860a5b6524134c0337048a0ef5e0460c435cde239e7"))throw new IOException("RSEM source-linked decoder source differs");
                Files.write(decoderSource,raw,StandardOpenOption.CREATE_NEW);
            }
            Compiler compiler=compiler(home.resolve("compiler"),tools,metadata,limits,version.equals("1.2.28"),check);
            RsemHost.Result host=RsemHost.capture(compiler,home.resolve("build-host"),limits.host(),check);
            var phases=new ArrayList<RsemProcess.Result>();
            // env --chdir preserves the original configure working directory while logs remain in a NEW owned process directory.
            phases.add(run(home,"cwd-check",List.of("/usr/bin/env","--chdir="+source,"/usr/bin/true"),compiler,host,limits,check,List.of()));
            if(version.equals("1.2.28"))phases.add(run(home,"configure",List.of("/usr/bin/env","--chdir="+source.resolve("samtools-1.3"),"/bin/sh","./configure","--without-curses"),compiler,host,limits,check,List.of(source.resolve("samtools-1.3"))));
            var configured=checkOriginal(source,original,version.equals("1.2.28")?GENERATED:Set.of(),check);write(metadata.resolve("generated-configuration-changes.json"),configured);
            var makeCommand=new ArrayList<String>(List.of(tools.make().toString(),"--jobserver-style=pipe","-C",source.toString(),"-j"+limits.jobs()));
            if(!version.equals("1.2.28")){
                Map<String,String> environment=compiler.environment(home.resolve("make"));
                for(String key:List.of("CXX","CC","AR","RANLIB","CPPFLAGS","LDFLAGS"))makeCommand.add(key+"="+environment.get(key));
            }
            phases.add(run(home,"make",makeCommand,compiler,host,limits,check,List.of(source.resolve("samtools-1.3"))));
            var generated=checkOriginal(source,original,version.equals("1.2.28")?GENERATED:Set.of(),check);
            write(metadata.resolve("generated-configuration-changes-after-build.json"),generated);
            for(String name:RsemPayload.PROGRAMS){Path program=source.resolve(name);if(!Files.isExecutable(program)||RsemElf.image(program,536870912,check)==null)throw new IOException("Original build did not produce the required complete executable: "+name);}
            Path decoder=outputs.resolve("omicsmatic-rsem-decode");var command=new ArrayList<String>(List.of(tools.cxx().toString(),"-std=gnu++98","-O2","-I"+source.resolve("samtools-1.3/htslib-1.3"),decoderSource.toString(),source.resolve("samtools-1.3/htslib-1.3/libhts.a").toString()));
            tools.libraries().forEach(p->{command.add("-L"+p);command.add("-Wl,-rpath,"+p);});command.addAll(List.of("-lz","-lpthread","-lm","-o",decoder.toString()));
            phases.add(run(home,"decoder",command,compiler,host,limits,check,List.of()));
            compiler.verify(check);RsemHost.verifyFiles(host.snapshot(),compiler,limits.host(),check);
            var compilerAfter=RsemFileGraph.capture(compiler.prefix().roots().stream().map(Path::of).toList(),compiler.prefix().files().stream().map(Path::of).toList(),limits.compiler(),metadata.resolve("compiler-prefix-after.jsonl"),check);
            if(!compiler.prefix().equals(compilerAfter))throw new IOException("Complete compiler input graph changed across original source compilation");
            var sourceGraph=RsemFileGraph.capture(List.of(source),List.of(decoder,decoderSource,archiveCopy),limits.compiler(),metadata.resolve("built-source.jsonl"),check);
            var evidence=new TreeMap<String,String>();
            try(var files=Files.walk(home)){
                for(Path path:files.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)).toList())if(!path.startsWith(source))evidence.put(home.relativize(path).toString().replace('\\','/'),hash(path,536870912,check));
            }
            var record=new LinkedHashMap<String,Object>();record.put("schema_version",1);record.put("status","COMPLETED_EXACT_ORIGINAL_SOURCE_BUILD");record.put("profile",profile);record.put("source_profile_lock_sha256",RsemPayload.LOCK);
            record.put("tools",tools);record.put("compiler_prefix",compiler.prefix());record.put("compiler_prefix_after",compilerAfter);record.put("build_host_snapshot_sha256",host.snapshotSha256());record.put("source_graph",sourceGraph);record.put("original_source_files",original.size());
            record.put("configuration_changes_before_build",configured);record.put("generated_configuration_changes",generated);record.put("phases",phases.stream().map(p->Map.of("ledger",p.ledger(),"exit",p.exitCode(),"owned_live_after_cleanup",p.ownedLiveAfterCleanup())).toList());record.put("evidence",evidence);
            record.put("source_to_build_provenance_verified",true);record.put("scientific_runtime_admitted",false);record.put("public_fitting_workflow_available",false);
            record.put("scope","Actual owned compilation from the exact pinned original archive with before/after declared compiler inputs,host providers,commands,zero exits and terminal process groups. Only the explicit three legacy generated configurations may differ. Receipt integrity is not a signature or OS sandbox;original fixed scientific health and scientific input/output admission remain separate.");
            byte[] raw=RsemJson.bytes(record);if(raw.length>67108864)throw new IOException("Complete RSEM build receipt exceeds metadata capacity");
            var used=RepeatBundle.inventory(home,new RepeatBundle.Limits(limits.process().workspaceBytes(),limits.process().fileCount()),false,check);
            if(used.bytes()+raw.length+65536>limits.process().workspaceBytes()||used.members()+8>limits.process().fileCount())throw new IOException("Source setup receipt/failure reserve exceeds aggregate owned capacity");
            Files.write(home.resolve("build.json"),raw,StandardOpenOption.CREATE_NEW);
            Context result=new Context(home,profile,source,decoder,tools.libraries(),AdmixtureInputs.digest(raw),sourceGraph,evidence);result.verify(check);return result;
        }catch(IOException|RuntimeException failure){
            try{String message=failure.toString();if(message.length()>8192)message=message.substring(0,8192);Files.writeString(home.resolve("BUILD_FAILED.txt"),"RSEM SOURCE SETUP FAILED: no runtime or fit admitted.\n"+message+"\n"+recovery()+"\n",StandardOpenOption.CREATE_NEW);}
            catch(IOException diagnostic){failure.addSuppressed(diagnostic);}throw failure;
        }
    }
    private static Compiler compiler(Path home,Tools tools,Path metadata,Limits limits,boolean legacy,Runnable check)throws IOException{
        Files.createDirectory(home);Path bin=Files.createDirectory(home.resolve("bin"));var names=List.of("gcc","g++","make","ar","ranlib");var targets=List.of(tools.cc(),tools.cxx(),tools.make(),tools.ar(),tools.ranlib());var files=new TreeMap<String,String>();
        for(int i=0;i<names.size();i++){
            String name=names.get(i),flags=legacy?(name.equals("g++")?" -std=gnu++98":name.equals("gcc")?" -fcommon":""):"";
            Path script=bin.resolve(name);Files.writeString(script,"#!/bin/sh\nexec '"+targets.get(i)+"'"+flags+" \"$@\"\n",StandardOpenOption.CREATE_NEW);Files.setPosixFilePermissions(script,permissions(0755));files.put("bin/"+name,hash(script,1048576,check));
        }
        byte[] plan=RsemJson.bytes(Map.of("tools",tools,"legacy_gnucxx98_and_common_wrappers",legacy));Files.write(home.resolve("tools.json"),plan,StandardOpenOption.CREATE_NEW);Files.write(home.resolve("empty-input"),new byte[0],StandardOpenOption.CREATE_NEW);
        var prefix=RsemFileGraph.capture(tools.roots(),tools.programs(),limits.compiler(),metadata.resolve("compiler-prefix-discovery.jsonl"),check);
        var owned=RsemFileGraph.capture(List.of(home),List.of(),limits.compiler(),metadata.resolve("compiler-wrappers.jsonl"),check);
        var preliminary=new Compiler(home,tools,files,tools.programs(),AdmixtureInputs.digest(plan),prefix,owned);
        var programs=new java.util.LinkedHashSet<Path>(tools.programs());var candidates=new java.util.LinkedHashSet<Path>(tools.programs());var discovery=new LinkedHashMap<String,Object>();
        for(String name:List.of("cc1","cc1plus","collect2","as","ld","lto-wrapper","lto1")){
            Path driver=name.equals("cc1plus")||name.equals("collect2")?tools.cxx():tools.cc();
            var result=query(home,"program-"+name,List.of(driver.toString(),"-print-prog-name="+name),preliminary,limits,check);
            String reported=singleLine(result.stdout());Path selected=compilerProgram(reported,preliminary,candidates);programs.add(selected);discovery.put(name,Map.of("reported",reported,"selected",selected));
        }
        // GCC installs this linker plugin in its executable-search prefix, not
        // necessarily in the library/start-file search used by -print-file-name.
        var plugin=query(home,"link-plugin",List.of(tools.cxx().toString(),"-print-prog-name=liblto_plugin.so"),preliminary,limits,check);
        String pluginName=singleLine(plugin.stdout());Path pluginPath=compilerProgram(pluginName,preliminary,candidates);programs.add(pluginPath);discovery.put("liblto_plugin",Map.of("reported",pluginName,"selected",pluginPath));
        for(String language:List.of("c","c++")){
            Path driver=language.equals("c")?tools.cc():tools.cxx();var result=query(home,"includes-"+(language.equals("c")?"c":"cxx"),
                    List.of(driver.toString(),"-E","-x",language,"-v",home.resolve("empty-input").toString()),preliminary,limits,check);
            List<String> includes=includeSearch(AdmixtureInputs.raw(result.stderr(),1048576));
            for(String include:includes)declaredCompilerLocation(include,tools,candidates);discovery.put("include_search_"+language,includes);
        }
        var search=query(home,"search-dirs",List.of(tools.cxx().toString(),"-print-search-dirs"),preliminary,limits,check);
        List<String> searchPaths=librarySearch(AdmixtureInputs.raw(search.stdout(),1048576));
        for(String location:searchPaths)declaredCompilerLocation(location,tools,candidates);discovery.put("library_search",searchPaths);
        write(metadata.resolve("compiler-discovery.json"),discovery);
        prefix.verify(check);
        var complete=RsemFileGraph.capture(tools.roots(),List.copyOf(candidates),limits.compiler(),metadata.resolve("compiler-prefix-before.jsonl"),check);
        return new Compiler(home,tools,files,List.copyOf(programs),AdmixtureInputs.digest(plan),complete,owned);
    }
    private static RsemProcess.Result query(Path compilerHome,String name,List<String> command,Compiler compiler,Limits limits,Runnable check)throws IOException{
        Path base=compilerHome.getParent(),work=base.resolve("compiler-query-"+name);var roots=new ArrayList<Path>();
        try(var entries=Files.newDirectoryStream(base)){for(Path path:entries)if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))roots.add(path);}
        return RsemProcess.executeOwned(command,compiler.environment(work),work,limits.process(),()->compiler.verify(check),check,roots);
    }
    private static Path compilerProgram(String reported,Compiler compiler,Set<Path> candidates)throws IOException{
        if(reported.isEmpty()||reported.chars().anyMatch(c->c<33||c==127))throw new IOException("Invalid compiler subprocess path: "+reported);
        Path path=Path.of(reported);
        if(!path.isAbsolute()){
            if(reported.contains("/"))throw new IOException("Compiler reported a relative subprocess path: "+reported);Path selected=null;
            for(String directory:compiler.environment(compiler.home()).get("PATH").split(":")){
                Path candidate=Path.of(directory).resolve(reported);candidates.add(candidate);
                if(selected==null&&Files.isRegularFile(candidate)&&Files.isExecutable(candidate))selected=candidate;
            }
            if(selected==null)throw unavailable("Compiler subprocess/plugin is unavailable: "+reported);path=selected;
        }
        Path real=path.toRealPath();if(!Files.isRegularFile(real,LinkOption.NOFOLLOW_LINKS)||compiler.tools().roots().stream().noneMatch(real::startsWith))
            throw new IOException("Compiler subprocess/plugin is outside declared complete compiler roots: "+reported);
        candidates.add(real);return real;
    }
    private static String singleLine(Path path)throws IOException{
        String text=new String(AdmixtureInputs.raw(path,65536),StandardCharsets.UTF_8);
        if(!text.endsWith("\n")||text.substring(0,text.length()-1).contains("\n"))throw new IOException("Compiler query is incomplete or has unexpected multiple lines");return text.strip();
    }
    static List<String> includeSearch(byte[] raw)throws IOException{
        String text=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(raw)).toString();if(raw.length>1048576||!text.endsWith("\n"))throw new IOException("Incomplete compiler include-search diagnostic");
        var result=new ArrayList<String>();boolean active=false,ended=false;
        for(String line:text.split("\n")){
            if(line.equals("#include <...> search starts here:")){if(active||ended)throw new IOException("Repeated compiler include-search block");active=true;}
            else if(line.equals("End of search list.")){if(!active||ended)throw new IOException("Invalid compiler include-search terminator");active=false;ended=true;}
            else if(active){if(!line.startsWith(" ")||!line.strip().startsWith("/"))throw new IOException("Unreviewed compiler include-search entry: "+line);result.add(line.strip());}
            else if(line.startsWith("ignoring nonexistent directory \"")){
                if(!line.endsWith("\""))throw new IOException("Malformed missing compiler include directory");result.add(line.substring("ignoring nonexistent directory \"".length(),line.length()-1));
            }
        }
        if(!ended||result.isEmpty()||result.size()>128)throw new IOException("Incomplete/over-capacity compiler include-search population");return List.copyOf(result);
    }
    static List<String> librarySearch(byte[] raw)throws IOException{
        String text=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(raw)).toString();if(raw.length>1048576||!text.endsWith("\n"))throw new IOException("Incomplete compiler library-search query");
        List<String> result=null;for(String line:text.split("\n"))if(line.startsWith("libraries: =")){
            if(result!=null)throw new IOException("Repeated compiler library-search declaration");result=List.of(line.substring("libraries: =".length()).split(":",-1));
        }
        if(result==null||result.isEmpty()||result.size()>256||result.stream().anyMatch(String::isEmpty))throw new IOException("Incomplete/over-capacity compiler library-search population");return result;
    }
    private static void declaredCompilerLocation(String reported,Tools tools,Set<Path> candidates)throws IOException{
        Path original=Path.of(reported);if(!original.isAbsolute()||reported.chars().anyMatch(c->c<33||c==127))throw new IOException("Unreviewed compiler search location: "+reported);
        Path actual;
        try{actual=original.toRealPath();}catch(java.nio.file.NoSuchFileException absent){
            if(tools.roots().stream().noneMatch(original::startsWith))throw new IOException("Missing compiler search directory is outside declared roots: "+reported);actual=original.normalize();
        }
        if(tools.roots().stream().noneMatch(actual::startsWith))throw new IOException("Compiler search location is outside declared complete roots: "+reported);
        candidates.add(actual);
    }
    private static RsemProcess.Result run(Path home,String name,List<String> command,Compiler compiler,RsemHost.Result host,Limits limits,Runnable check,List<Path> probeRoots)throws IOException{
        RsemProcess.Check integrity=()->{compiler.verify(check);RsemHost.verifyFiles(host.snapshot(),compiler,limits.host(),check);};
        var complete=new ArrayList<Path>();try(var entries=Files.newDirectoryStream(home)){
            for(Path path:entries)if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))complete.add(path);
        }
        return probeRoots.isEmpty()?RsemProcess.executeOwned(command,compiler.environment(home.resolve(name)),home.resolve(name),limits.process(),integrity,check,complete)
                :RsemProcess.executeConfiguration(command,compiler.environment(home.resolve(name)),home.resolve(name),limits.process(),integrity,check,complete,probeRoots);
    }
    static Map<String,SourceFile> extract(Path archive,Path home,String prefix,Runnable check)throws IOException{
        if(!home.isAbsolute()||!home.toRealPath().equals(home)||!prefix.matches("[A-Za-z0-9_-]+"))throw new IOException("Source extraction requires a canonical parent and fixed source prefix");
        Files.createDirectory(home.resolve(prefix));var result=new TreeMap<String,SourceFile>();var seen=new java.util.HashSet<String>();long[] bytes={0};int entries=0;
        try(var compressed=Files.newInputStream(archive);var gzip=new GZIPInputStream(compressed);var bounded=new FilterInputStream(gzip){
            @Override public int read(byte[] b,int off,int len)throws IOException{int n=super.read(b,off,len);if(n>0){bytes[0]+=n;if(bytes[0]>SOURCE_BYTES)throw new IOException("Complete decompressed source archive exceeds capacity");check.run();}return n;}
            @Override public int read()throws IOException{byte[] b=new byte[1];return read(b,0,1)<0?-1:Byte.toUnsignedInt(b[0]);}
            @Override public long skip(long n)throws IOException{long skipped=0;byte[] b=new byte[65536];while(skipped<n){int read=read(b,0,(int)Math.min(b.length,n-skipped));if(read<0)break;skipped+=read;}return skipped;}
        };var tar=new TarArchiveInputStream(bounded)){
            TarArchiveEntry entry;while((entry=tar.getNextEntry())!=null){
                check.run();if(++entries>100000)throw new IOException("Original source archive member capacity exceeded");String name=entry.getName();
                while(name.endsWith("/"))name=name.substring(0,name.length()-1);
                if(!name.equals(prefix)&&!name.startsWith(prefix+"/")||name.startsWith("/")||name.contains("\\")||java.util.Arrays.stream(name.split("/",-1)).anyMatch(p->p.isEmpty()||p.equals(".")||p.equals(".."))||name.length()>4096)
                    throw new IOException("Unsafe or unexpected original source archive member: "+name);
                Path path=home.resolve(name);if(!seen.add(name)||!path.normalize().startsWith(home)||!(entry.isFile()||entry.isDirectory())||entry.isSymbolicLink()||entry.isLink()||entry.isSparse()||name.equals(prefix)&&!entry.isDirectory())throw new IOException("Repeated,linked or nonregular original source member: "+name);
                if(entry.isDirectory()){if(entry.getSize()!=0)throw new IOException("Directory source member carries unexpected data: "+name);Files.createDirectories(path);continue;}
                if(entry.getSize()<0||entry.getSize()>536870912)throw new IOException("Original source member size exceeds capacity: "+name);Files.createDirectories(path.getParent());
                if(!path.getParent().toRealPath().equals(path.getParent()))throw new IOException("Source extraction parent became linked: "+name);
                long copied;try(var output=Files.newOutputStream(path,StandardOpenOption.CREATE_NEW,LinkOption.NOFOLLOW_LINKS)){copied=tar.transferTo(output);}
                if(copied!=entry.getSize())throw new IOException("Truncated original source member: "+name);Files.setPosixFilePermissions(path,permissions(entry.getMode()&0777));
                // Preserve distributed generated-file dependencies. Fresh extraction
                // timestamps can otherwise trigger an unintended autoreconfiguration.
                Files.setLastModifiedTime(path,entry.getLastModifiedTime());
                String relative=name.substring(prefix.length()+1);if(result.put(relative,new SourceFile(copied,hash(path,536870912,check)))!=null)throw new IOException("Repeated original source member: "+name);
                try(var input=Files.newInputStream(path)){byte[] magic=input.readNBytes(4);if(magic.length==4&&magic[0]==127&&magic[1]=='E'&&magic[2]=='L'&&magic[3]=='F')throw new IOException("Source archive unexpectedly contains a precompiled ELF image: "+name);}
            }
        }if(result.isEmpty())throw new IOException("Empty original source archive");return Collections.unmodifiableMap(result);
    }
    private static Map<String,Object> checkOriginal(Path source,Map<String,SourceFile> original,Set<String> allowed,Runnable check)throws IOException{
        var changes=new TreeMap<String,Object>();for(var entry:original.entrySet()){
            Path path=source.resolve(entry.getKey());String actual=hash(path,536870912,check);
            if(!actual.equals(entry.getValue().sha256())){
                if(!allowed.contains(entry.getKey()))throw new IOException("Original scientific/source file changed during setup: "+entry.getKey());
                changes.put(entry.getKey(),Map.of("before_sha256",entry.getValue().sha256(),"after_sha256",actual));
            }
        }return changes;
    }
    private static void validateTools(Tools tools)throws IOException{
        if(tools.roots().isEmpty()||tools.roots().size()>32||tools.includes().size()>16||tools.libraries().size()>8)throw new IOException("Require bounded explicit compiler roots/includes/libraries");
        for(Path path:tools.programs()){
            RsemPayload.originalPath(path);if(!Files.isRegularFile(path)||!Files.isExecutable(path))throw unavailable("Required compiler tool is missing or forbidden: "+path);
            Path real=path.toRealPath();if(tools.roots().stream().noneMatch(real::startsWith))throw new IOException("Compiler executable is outside the declared full compiler roots: "+path);
        }
        var directories=new ArrayList<Path>(tools.roots());directories.addAll(tools.includes());directories.addAll(tools.libraries());
        for(Path path:directories){RsemPayload.originalPath(path);if(!Files.isDirectory(path)||!path.toRealPath().equals(path))throw unavailable("Declared compiler directory is missing,linked or forbidden: "+path);
            if(tools.roots().stream().noneMatch(path::startsWith))throw new IOException("Compiler include/library directory is outside its declared complete roots: "+path);}
    }
    private static Path archive(RsemPayload.Profile profile,Path supplied,Path cache,boolean allowDownload,Runnable check)throws IOException{
        Path path=supplied;
        if(path==null){
            if(cache==null)throw unavailable("Supply an offline source archive or an explicit private archive cache");
            cache=cache.toAbsolutePath().normalize();if(!Files.isDirectory(cache)||!cache.toRealPath().equals(cache))throw new IOException("Require a canonical existing private archive cache");
            path=cache.resolve("RSEM-"+profile.version()+"-"+profile.commit()+".tar.gz");
            if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS)){
                if(!allowDownload)throw unavailable("Pinned RSEM archive is absent and downloads were not permitted: "+path);
                Path part=path.resolveSibling(path.getFileName()+".partial");
                download(URI.create(profile.sourceUrl()),part,check);
                if(!hash(part,ARCHIVE_BYTES,check).equals(profile.archiveSha256()))throw new IOException("Downloaded original archive checksum differs;partial retained");Files.move(part,path);
            }
        }
        path=path.toAbsolutePath().normalize();if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw unavailable("Missing original source archive: "+path);
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!path.toRealPath().equals(path)||!hash(path,ARCHIVE_BYTES,check).equals(profile.archiveSha256()))throw new IOException("Original archive is linked,oversized or has the wrong exact source checksum: "+path);
        return path;
    }
    private static void download(URI initial,Path part,Runnable check)throws IOException{
        URI uri=initial;
        for(int redirects=0;redirects<=5;redirects++){
            check.run();if(!uri.getScheme().equals("https")||uri.getUserInfo()!=null||!Set.of("codeload.github.com","github.com","objects.githubusercontent.com").contains(uri.getHost()))
                throw new IOException("Unreviewed original source download redirect: "+uri);
            var connection=(HttpURLConnection)uri.toURL().openConnection();connection.setConnectTimeout(30000);connection.setReadTimeout(30000);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("User-Agent","OmicsMatic-RSEM-source-setup");
            try{
                int status=connection.getResponseCode();
                if(Set.of(301,302,303,307,308).contains(status)){String location=connection.getHeaderField("Location");if(location==null)throw new IOException("Original source redirect has no location");uri=uri.resolve(location);continue;}
                if(status!=200)throw unavailable("Official original source archive is unavailable,HTTP "+status+";supply a permitted exact offline archive");
                try(var input=connection.getInputStream();var output=Files.newOutputStream(part,StandardOpenOption.CREATE_NEW)){
                    long size=0;byte[] buffer=new byte[65536];int n;
                    while((n=input.read(buffer))!=-1){check.run();if((size+=n)>ARCHIVE_BYTES)throw new IOException("Source archive download exceeds capacity");output.write(buffer,0,n);}
                }return;
            }finally{connection.disconnect();}
        }throw new IOException("Original source download redirect capacity exceeded");
    }
    private static Set<PosixFilePermission> permissions(int mode){var result=java.util.EnumSet.noneOf(PosixFilePermission.class);var values=PosixFilePermission.values();for(int i=0;i<9;i++)if((mode&(1<<(8-i)))!=0)result.add(values[i]);return result;}
    private static String hash(Path path,long maximum,Runnable check)throws IOException{return RepeatInputs.hash(path,maximum,check);}
    private static void write(Path path,Object value)throws IOException{Files.write(path,RsemJson.bytes(value),StandardOpenOption.CREATE_NEW);}
    private static Runnable deadline(long millis,Runnable cancellation){long started=System.nanoTime();return ()->{cancellation.run();if(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)>millis)throw new CancellationException("Whole original RSEM source setup exceeded its declared wall capacity");};}
    private static RsemProcess.UnavailableDependency unavailable(String cause){return new RsemProcess.UnavailableDependency(cause+". "+recovery());}
    static String recovery(){return "Required action: use a permitted Linux x86_64 Java host,exact source archive and declared working compiler/headers/library roots. Permit installation/download explicitly or supply offline archives. If required installation or execution is forbidden/unavailable,SKIP dependent fitting. Preserve partial downloads,failed homes and all logs;correct the named cause and choose NEW paths. Setup alone admits no scientific result.";}
}
