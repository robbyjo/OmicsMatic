/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Fixed original script profiles and a new, immutable execution-payload snapshot.
 * Source-to-build provenance and scientific runtime admission are separate gates. */
final class RsemPayload {
    private RsemPayload() { }
    static final String LOCK = "c7fcb6272bb0ee9f34207d4e221e68c017af1e6174b9492310ada68c1aa55fb7";
    static final String RESOURCE = "/org/jlinalg/omics/rsem-source-profiles.json";
    static final long FILE_BYTES = 536870912;
    static final RepeatHostFiles.Limits LIMITS = new RepeatHostFiles.Limits(FILE_BYTES,2147483648L,1024,4,4194304);
    static final List<String> PROGRAMS = List.of("rsem-extract-reference-transcripts","rsem-synthesis-reference-transcripts",
            "rsem-preref","rsem-parse-alignments","rsem-build-read-index","rsem-run-em","rsem-run-gibbs",
            "rsem-calculate-credibility-intervals","rsem-sam-validator","samtools-1.3/samtools");
    record Profile(String version,String commit,String archiveSha256,String sourceUrl,String reportedVersion,Map<String,String> scripts) {
        Profile { scripts=Collections.unmodifiableMap(new TreeMap<>(scripts)); }
    }
    record Member(long bytes,String sha256) { }
    record Context(Path home,Profile source,List<Path> libraryDirectories,Map<String,Member> files,
            String manifestSha256,RepeatHostFiles.Snapshot tree,RsemBuild.Context sourceBuild) implements RsemProvider {
        Context { libraryDirectories=List.copyOf(libraryDirectories);files=Collections.unmodifiableMap(new TreeMap<>(files)); }
        public String profile(){return "rsem-original-"+source.version()+"-linux-x86_64";}
        public String lockSha256(){return LOCK;}
        public List<Path> providerRoots(){var result=new ArrayList<>(libraryDirectories);result.add(home);return List.copyOf(result);}
        public List<Path> executablePaths(){return files.keySet().stream().filter(n->n.startsWith("bin/")).map(home::resolve).toList();}
        public Map<String,String> environment(Path ownedWork) throws IOException {
            originalPath(ownedWork);
            var env=new LinkedHashMap<String,String>();
            env.put("PATH",home.resolve("bin")+":"+home.resolve("bin/samtools-1.3")+":/usr/bin:/bin");
            if(!libraryDirectories.isEmpty())env.put("LD_LIBRARY_PATH",String.join(":",libraryDirectories.stream().map(Path::toString).toList()));
            env.put("LANG","C");env.put("LC_ALL","C");env.put("TZ","UTC");
            env.put("OMP_NUM_THREADS","1");env.put("OPENBLAS_NUM_THREADS","1");env.put("MKL_NUM_THREADS","1");
            env.put("REF_PATH","");env.put("REF_CACHE","");
            return Collections.unmodifiableMap(env);
        }
        public void verify(Runnable check)throws IOException {
            profiles();check.run();
            if(sourceBuild!=null)sourceBuild.verify(check);
            RepeatHostFiles.verify(tree,Set.of(home),Set.of(home),LIMITS,check);
            if(!manifestSha256.equals(RepeatInputs.hash(home.resolve("payload.json"),4194304,check)))
                throw new IOException("RSEM payload metadata changed");
        }
    }
    static Map<String,Profile> profiles()throws IOException {
        byte[] raw;
        try(var input=RsemPayload.class.getResourceAsStream(RESOURCE)){
            if(input==null)throw new IOException("Missing exact RSEM source-profile lock");
            raw=input.readNBytes(65537);
        }
        if(raw.length>65536||!AdmixtureInputs.digest(raw).equals(LOCK))throw new IOException("RSEM source-profile lock changed");
        Map<?,?> lock=RepeatBundle.object(raw);
        if(!Long.valueOf(1).equals(lock.get("schema_version"))||!(lock.get("profiles") instanceof List<?> rows)||rows.size()!=5)
            throw new IOException("Invalid RSEM source-profile population");
        var profiles=new TreeMap<String,Profile>();
        for(Object item:rows){
            if(!(item instanceof Map<?,?> row)||!(row.get("assets") instanceof Map<?,?> assets))throw new IOException("Invalid RSEM source profile");
            var scripts=new TreeMap<String,String>();
            for(var entry:assets.entrySet()){
                if(!(entry.getKey() instanceof String name)||!(entry.getValue() instanceof String hash)||!hash.matches("[0-9a-f]{64}"))
                    throw new IOException("Invalid original RSEM script pin");
                scripts.put(name,hash);
            }
            if(!scripts.keySet().equals(Set.of("rsem-calculate-expression","rsem-prepare-reference","rsem_perl_utils.pm")))
                throw new IOException("Incomplete original RSEM script population");
            String version=text(row,"version"),commit=text(row,"commit"),archive=text(row,"source_archive_sha256"),url=text(row,"source_url");
            if(!commit.matches("[0-9a-f]{40}")||!archive.matches("[0-9a-f]{64}")||!url.equals("https://codeload.github.com/deweylab/RSEM/tar.gz/"+commit))
                throw new IOException("Invalid original RSEM source archive identity");
            if(profiles.put(version,new Profile(version,commit,archive,url,text(row,"reported_version"),scripts))!=null)
                throw new IOException("Repeated RSEM source profile");
        }
        if(!profiles.keySet().equals(Set.of("1.2.28","1.3.0","1.3.1","1.3.2","1.3.3")))throw new IOException("Unexpected RSEM source release");
        return Collections.unmodifiableMap(profiles);
    }
    /** Copies only declared programs into NEW paths; a successful copy is not scientific admission. */
    static Context stage(Path suppliedSource,String version,Path suppliedDecoder,List<Path> suppliedLibraries,Path suppliedHome,Runnable check)throws IOException {
        return stage(suppliedSource,version,suppliedDecoder,suppliedLibraries,suppliedHome,null,check);
    }
    static Context stageBuilt(RsemBuild.Context built,List<Path> runtimeLibraries,Path suppliedHome,Runnable check)throws IOException{
        built.verify(check);
        if(!runtimeLibraries.containsAll(built.libraries()))throw new IOException("Runtime library declaration omits original source-build provider roots");
        return stage(built.source(),built.profile().version(),built.decoder(),runtimeLibraries,suppliedHome,built,check);
    }
    private static Context stage(Path suppliedSource,String version,Path suppliedDecoder,List<Path> suppliedLibraries,Path suppliedHome,RsemBuild.Context built,Runnable check)throws IOException {
        if(!"Linux".equals(System.getProperty("os.name")))throw new RsemProcess.UnavailableDependency("RSEM original execution requires Linux Java; skip dependent fitting on this host");
        Profile profile=profiles().get(version);if(profile==null)throw new IOException("Unsupported RSEM source release; do not substitute a newer release");
        Path source=suppliedSource.toAbsolutePath().normalize(),home=suppliedHome.toAbsolutePath().normalize(),decoder=suppliedDecoder.toAbsolutePath().normalize();
        for(Path p:List.of(source,home,decoder))originalPath(p);
        if(!Files.isDirectory(source,LinkOption.NOFOLLOW_LINKS)||!source.toRealPath().equals(source))throw new IOException("RSEM source build requires a canonical existing directory");
        if(home.getParent()==null||!home.getParent().toRealPath().equals(home.getParent())||home.startsWith(source)||source.startsWith(home))
            throw new IOException("RSEM payload requires a canonical NEW home disjoint from its source build");
        var libraries=new ArrayList<Path>();
        if(suppliedLibraries.size()>8)throw new IOException("RSEM explicit library-directory capacity exceeded");
        for(Path input:suppliedLibraries){
            Path path=input.toAbsolutePath().normalize();originalPath(path);
            if(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)||!path.toRealPath().equals(path))throw new IOException("Declared RSEM library directory is missing or linked: "+path);
            if(path.startsWith(home)||home.startsWith(path)||libraries.stream().anyMatch(p->p.startsWith(path)||path.startsWith(p)))
                throw new IOException("RSEM library roots must be unique,disjoint and outside the new payload");
            libraries.add(path);
        }
        var inputs=new TreeMap<String,Path>();
        profile.scripts().keySet().forEach(name->inputs.put("bin/"+name,source.resolve(name)));
        PROGRAMS.forEach(name->inputs.put("bin/"+name,source.resolve(name)));
        inputs.put("bin/omicsmatic-rsem-decode",decoder);
        var expected=new TreeMap<String,Member>();
        for(var entry:inputs.entrySet()){
            check.run();Path input=entry.getValue();regular(input);
            String name=entry.getKey().substring(4),hash=RepeatInputs.hash(input,FILE_BYTES,check);
            if(profile.scripts().containsKey(name)){
                if(!hash.equals(profile.scripts().get(name)))throw new IOException("Original RSEM script differs from exact "+version+" source: "+name);
            }else if(!Files.isExecutable(input)||RepeatHostElf.image(input,FILE_BYTES,check)==null)
                throw new IOException("RSEM executable must be a complete supported ELF image: "+input);
            expected.put(entry.getKey(),new Member(Files.size(input),hash));
        }
        Files.createDirectory(home);
        try{
            for(var entry:inputs.entrySet()){
                check.run();Path output=home.resolve(entry.getKey());Files.createDirectories(output.getParent());Files.copy(entry.getValue(),output,StandardCopyOption.COPY_ATTRIBUTES);
                Member pin=expected.get(entry.getKey());
                if(Files.size(output)!=pin.bytes()||!RepeatInputs.hash(output,FILE_BYTES,check).equals(pin.sha256())
                        ||!RepeatInputs.hash(entry.getValue(),FILE_BYTES,check).equals(pin.sha256()))throw new IOException("RSEM program changed across payload copy: "+entry.getKey());
            }
            var record=new LinkedHashMap<String,Object>();record.put("schema_version",1);record.put("status","STAGED_SOURCE_PROFILE_AND_BINARY_SNAPSHOT_ONLY");
            record.put("source",profile);record.put("original_build_home",source);record.put("source_profile_lock_sha256",LOCK);record.put("library_directories",libraries);
            record.put("files",expected);record.put("source_to_build_provenance_verified",built!=null);record.put("scientific_runtime_admitted",false);
            if(built!=null){record.put("source_build_home",built.home());record.put("source_build_receipt_sha256",built.recordSha256());}
            record.put("scope","Exact pinned original scripts,complete declared copied ELF files and source profile. Executable hashes bind this payload;they are not signatures or proof of source-to-binary compilation. Host closure and original fixed scientific health are separate required gates. No public fitting workflow is admitted by staging.");
            byte[] metadata=RsemJson.bytes(record);if(metadata.length>4194304)throw new IOException("RSEM payload metadata exceeds capacity");
            Files.write(home.resolve("payload.json"),metadata,StandardOpenOption.CREATE_NEW);
            var tree=RepeatHostFiles.capture(List.of(home),List.of(),Set.of(home),Set.of(home),LIMITS,check);
            Context result=new Context(home,profile,libraries,expected,AdmixtureInputs.digest(metadata),tree,built);result.verify(check);return result;
        }catch(IOException|RuntimeException failure){
            try{Files.writeString(home.resolve("PAYLOAD_FAILED.txt"),"RSEM PAYLOAD FAILED: no runtime or fit admitted.\n"+failure+"\nRequired action: retain this directory and correct the named source,capacity or permission problem. Rerun to NEW paths. If installation is forbidden,skip dependent fitting.\n",StandardOpenOption.CREATE_NEW);}
            catch(IOException diagnostic){failure.addSuppressed(diagnostic);}throw failure;
        }
    }
    static void originalPath(Path path)throws IOException {
        if(!path.isAbsolute()||!path.normalize().equals(path)||!path.toString().matches("/[A-Za-z0-9_./+\\-]+"))
            throw new IOException("Original RSEM shell dispatch requires canonical absolute paths using letters,digits,underscore,dot,slash,plus or minus;copy inputs to permitted NEW paths before fitting");
    }
    private static void regular(Path path)throws IOException {
        if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS))throw new RsemProcess.UnavailableDependency("Missing original RSEM program: "+path+". Required action: supply the exact permitted original build and decoder,or skip dependent fitting when installation is forbidden.");
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!path.toRealPath().equals(path))throw new IOException("Missing,linked or noncanonical RSEM program: "+path);
    }
    private static String text(Map<?,?> map,String key)throws IOException {
        if(!(map.get(key) instanceof String value)||value.isBlank())throw new IOException("Missing RSEM profile field: "+key);return value;
    }
}
