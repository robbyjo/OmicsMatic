/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.jlinalg.sequence.MafftSequences;
import org.jlinalg.sequence.MafftAddMap;

/** Read-only complete MAFFT payload/setup checks. No process or user analysis is launched.
 * Full dynamic host/library/Perl capture and public analysis admission remain separate gates. */
final class MafftRuntime {
    private MafftRuntime() { }
    static final String MARKER=".omicsmatic-mafft.json";
    private static final String ROOT="/org/jlinalg/omics/";
    private static final String LOCK_SHA="e66f242f6f3ee4dfe34bc971869bd301ac4d4eec1ef629d786062472764cffcb";
    private static final List<String> VERSIONS=List.of("7.313","7.475","7.487","7.490","7.505","7.526");
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
        Unavailable(String message,Throwable cause){super(message,cause);}
    }
    record Fingerprint(long bytes,String sha256) { }
    record External(String requested,String realPath,Fingerprint fingerprint,int mode,long links) { }
    record Segment(long type,long flags,long offset,long fileBytes,long memoryBytes,long alignment) { }
    record Context(Path home,String profile,String version,String program,Fingerprint marker,
                   Map<String,Fingerprint> files,Map<String,External> helpers,Path setupReport,
                   Map<String,Fingerprint> setupFiles,Fingerprint setupCompletion,String knownWarning) {
        Context {files=Map.copyOf(files);helpers=Map.copyOf(helpers);setupFiles=Map.copyOf(setupFiles);}
        List<String> command(){return List.of("/bin/bash",home.resolve(program).toString());}
        Map<String,String> environment(Path work,int threads)throws IOException {
            canonical(work);asciiOwned(work);
            if(threads<1||threads>256)throw new IllegalArgumentException("Declare 1..256 MAFFT threads");
            return Map.ofEntries(Map.entry("PATH","/usr/bin:/bin"),Map.entry("HOME",work.toString()),Map.entry("TMPDIR",work.toString()),
                Map.entry("MAFFT_TMPDIR",work.toString()),Map.entry("LANG","C"),Map.entry("LC_ALL","C"),
                Map.entry("OMP_NUM_THREADS",Integer.toString(threads)),Map.entry("OPENBLAS_NUM_THREADS","1"),Map.entry("MKL_NUM_THREADS","1"));
        }
        void verify()throws IOException {
            try{if(!equals(open(home,profile)))throw new IOException("MAFFT admitted payload or setup evidence changed");}
            catch(Unavailable absent){throw new IOException("Previously admitted MAFFT prerequisite became unavailable; no result admitted",absent);}
        }
    }
    private record Lock(Map<?,?> value,String installerSha256) { }
    static Set<String> profiles(){var result=new HashSet<String>();for(String v:VERSIONS)result.add("linux-x86_64-mafft-"+v);return Set.copyOf(result);}
    /** Portable check of the recorded edition/payload; does not re-admit a live host. */
    static void verifyRecordedPayload(String profile,Map<?,?> recorded)throws IOException {
        Map<?,?> pin=object(object(lock().value().get("profiles")).get(profile));
        for(String key:List.of("profile","version","program")) {
            Object expected=key.equals("profile")?profile:pin.get(key);
            if(!Objects.equals(expected,recorded.get(key)))throw new IOException("Recorded MAFFT edition/program differs: "+key);
        }
        if(!Objects.equals(pin.get("known_warning"),recorded.get("knownWarning")))throw new IOException("Recorded MAFFT original-release warning differs");
        var expected=new LinkedHashMap<String,Fingerprint>();
        for(var entry:object(pin.get("files")).entrySet())expected.put(member(entry.getKey()),fingerprint(object(entry.getValue())));
        for(var entry:object(lock().value().get("bundled")).entrySet())expected.put("share/"+member(entry.getKey()),fingerprint(object(entry.getValue())));
        Map<?,?> files=object(recorded.get("files"));if(!files.keySet().equals(expected.keySet()))throw new IOException("Recorded MAFFT payload family differs");
        for(var entry:expected.entrySet())if(!entry.getValue().equals(fingerprint(object(files.get(entry.getKey())))))throw new IOException("Recorded MAFFT payload bytes differ: "+entry.getKey());
    }
    static String lockSha256(){return LOCK_SHA;}
    private static Lock lock()throws IOException {
        byte[] raw=resource("mafft-runtime-lock.json");
        if(!digest(raw).equals(LOCK_SHA))throw new IOException("Bundled MAFFT lock differs from its pinned source");
        Map<?,?> value=object(SimpleJson.parse(utf8(raw)));
        integer(value.get("schema_version"),1,1);
        if(!"MAFFT".equals(value.get("tool"))||!object(value.get("profiles")).keySet().equals(profiles()))throw new IOException("Unsupported MAFFT profile/schema");
        for(var entry:object(value.get("bundled")).entrySet())requireFingerprint(resource("mafft-runtime-setup/mafft-runtime/"+member(entry.getKey())),fingerprint(object(entry.getValue())));
        return new Lock(value,digest(resource("mafft-runtime-setup/install_mafft.py")));
    }
    private static Map<String,Object> expectedFiles(Map<?,?> pin,Map<?,?> lock)throws IOException {
        var expected=new TreeMap<String,Object>();
        for(var entry:object(pin.get("files")).entrySet())expected.put(member(entry.getKey()),entry.getValue());
        for(var entry:object(lock.get("bundled")).entrySet()){
            Map<?,?> item=object(entry.getValue());expected.put("share/"+member(entry.getKey()),Map.of("bytes",item.get("bytes"),"sha256",item.get("sha256"),"mode","0444"));
        }
        return Collections.unmodifiableMap(expected);
    }
    static Context open(Path supplied,String requestedProfile)throws IOException {
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch").toLowerCase(Locale.ROOT)))
            throw new Unavailable("Original MAFFT admission requires Linux x86_64 Java; use Linux Java inside WSL on Windows");
        if(!profiles().contains(requestedProfile))throw new Unavailable("Exact MAFFT edition/profile unavailable; no other edition is substituted");
        if(Files.exists(Path.of("/etc/ld.so.preload"),LinkOption.NOFOLLOW_LINKS))throw new Unavailable("OS loader preloads are outside this declared helper profile");
        Path home=supplied.toAbsolutePath().normalize();
        if(!Files.exists(home,LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Exact managed MAFFT home is absent; provision the permitted profile first");
        canonical(home);asciiOwned(home);requireMode(home,0700);
        if(!Files.exists(home.resolve(MARKER),LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Unmanaged/provisional MAFFT home; preserve it and use a new private home");
        try {
            Lock lock=lock();Map<?,?> pin=object(object(lock.value().get("profiles")).get(requestedProfile));
            Path markerPath=home.resolve(MARKER);Fingerprint markerId=identity(markerPath,262144);Map<?,?> marker=json(markerPath,262144);
            for(var entry:Map.of("tool","MAFFT","status","installed-smoke-tested-runtime-only","profile",requestedProfile,"version",text(pin.get("version")),"home",home.toString(),"lock_sha256",LOCK_SHA,"installer_sha256",lock.installerSha256()).entrySet())
                if(!entry.getValue().equals(marker.get(entry.getKey())))throw new IOException("MAFFT managed identity differs: "+entry.getKey());
            integer(marker.get("schema_version"),1,1);
            if(!Boolean.FALSE.equals(marker.get("scientific_analysis_admitted")))throw new IOException("Setup cannot claim user scientific admission");
            Map<?,?> host=object(marker.get("host"));
            if(!"Linux".equals(host.get("os"))||!"x86_64".equals(host.get("architecture")))throw new IOException("MAFFT setup host differs");
            if(!System.getProperty("os.version").equals(host.get("kernel")))throw new Unavailable("MAFFT admitted kernel changed; preserve this home and provision fresh admission");
            var expected=expectedFiles(pin,lock.value());Map<?,?> recorded=object(marker.get("files"));
            if(!expected.equals(recorded))throw new IOException("Complete MAFFT runtime member binding differs");
            var files=new TreeMap<String,Fingerprint>();var elves=new HashSet<String>();Map<?,?> savedElves=object(marker.get("elf"));
            for(var entry:expected.entrySet()) {
                String name=member(entry.getKey());Map<?,?> item=object(entry.getValue());Fingerprint wanted=fingerprint(item);
                if(!identity(home.resolve(name),wanted.bytes()).equals(wanted))throw new IOException("Original MAFFT runtime bytes differ: "+name);
                int mode=mode(item.get("mode"));requireMode(home.resolve(name),mode);files.put(name,wanted);
                if(object(pin.get("static_elf_programs")).containsKey(name)) {
                    elves.add(name);checkElfRecord(object(savedElves.get(name)),staticElf(raw(home.resolve(name),33554432)));
                }
            }
            if(!savedElves.keySet().equals(elves))throw new IOException("Complete MAFFT ELF program receipt census differs");
            requireMode(markerPath,0444);inventory(home,files.keySet(),true,67108864,128);
            if(!Files.isExecutable(home.resolve(text(pin.get("program")))))throw new Unavailable("MAFFT original launcher execution is forbidden");
            var helpers=new TreeMap<String,External>();Map<?,?> savedHelpers=object(host.get("helpers"));
            var helperNames=new HashSet<String>();for(Object value:list(lock.value().get("host_helpers")))helperNames.add(text(value));
            if(!helperNames.equals(savedHelpers.keySet()))throw new IOException("Complete MAFFT declared helper census differs");
            for(String name:helperNames) {
                Map<?,?> saved=object(savedHelpers.get(name));Path requested=Path.of(name),real=requested.toRealPath();
                if(!real.toString().equals(saved.get("real_path"))||!Files.isExecutable(requested))throw new Unavailable("Declared MAFFT helper path/execution changed: "+name);
                Fingerprint wanted=fingerprint(saved);long links=integer(saved.get("links"),1,1000000);int wantedMode=(int)integer(saved.get("mode"),0,07777);
                if(!identity(real,wanted.bytes(),links).equals(wanted))throw new IOException("Declared MAFFT host helper bytes changed: "+name);
                requireMode(real,wantedMode);helpers.put(name,new External(name,real.toString(),wanted,wantedMode,links));
            }
            Path report=Path.of(text(marker.get("setup_report_directory")));canonical(report);asciiOwned(report);requireMode(report,0700);
            if(report.startsWith(home)||home.startsWith(report))throw new IOException("MAFFT setup report must be separate from the runtime");
            Path completePath=report.resolve("COMPLETED.json");Fingerprint completeId=identity(completePath,262144);Map<?,?> completed=json(completePath,262144);
            for(var entry:Map.of("status","completed-runtime-setup-only","home",home.toString(),"profile",requestedProfile,"runtime_marker_sha256",markerId.sha256()).entrySet())
                if(!entry.getValue().equals(completed.get(entry.getKey())))throw new IOException("MAFFT setup completion binding differs");
            if(!Boolean.FALSE.equals(completed.get("scientific_analysis_admitted")))throw new IOException("Setup completion cannot claim scientific admission");
            var reportFiles=new TreeMap<String,Fingerprint>();
            for(var entry:object(completed.get("files")).entrySet()) {
                String name=member(entry.getKey());Fingerprint wanted=fingerprint(object(entry.getValue()));
                if(!identity(report.resolve(name),wanted.bytes()).equals(wanted))throw new IOException("MAFFT setup evidence bytes changed: "+name);
                reportFiles.put(name,wanted);
            }
            var names=new HashSet<>(List.of("installation.json","WARNINGS.md"));var dirs=new TreeSet<String>();dirs.add("version");
            for(String name:List.of("process.stdout","process.stderr","process.json","running.json"))names.add("version/"+name);
            for(Object rawControl:list(lock.value().get("setup_health"))) {
                Map<?,?> control=object(rawControl);String name=member(control.get("name"));dirs.add(name);
                for(String member:List.of("process.stdout","process.stderr","process.json","running.json"))names.add(name+"/"+member);
                for(Object member:object(control.get("inputs")).keySet())names.add(name+"/"+member(member));
                if(name.equals("AA-add-keep-map"))names.add(name+"/added.fasta.map");
            }
            if(!reportFiles.keySet().equals(names)||!list(completed.get("directories")).equals(new ArrayList<>(dirs)))throw new IOException("MAFFT setup evidence file/directory inventory differs");
            inventory(report,reportFiles.keySet(),false,16777216,128);
            try(var paths=Files.walk(report)) {
                var actualDirs=new TreeSet<String>();
                for(Path p:paths.filter(Files::isDirectory).toList()) { requireMode(p,0700);if(!p.equals(report))actualDirs.add(report.relativize(p).toString().replace('\\','/')); }
                if(!actualDirs.equals(dirs))throw new IOException("Extra or missing MAFFT setup directory");
            }
            Map<?,?> setup=json(report.resolve("installation.json"),262144);
            for(var entry:Map.of("status","installed-smoke-tested-runtime-only","profile",requestedProfile,"home",home.toString(),"version",text(pin.get("version")),"lock_sha256",LOCK_SHA,"installer_sha256",lock.installerSha256(),"runtime_marker_sha256",markerId.sha256()).entrySet())
                if(!entry.getValue().equals(setup.get(entry.getKey())))throw new IOException("MAFFT original setup report binding differs");
            integer(setup.get("exit_code"),0,0);
            if(!Boolean.FALSE.equals(setup.get("scientific_analysis_admitted"))||!host.equals(setup.get("host"))||!recorded.equals(setup.get("files"))||!marker.get("health").equals(setup.get("health")))throw new IOException("MAFFT setup evidence scope/census differs");
            checkHealth(home,report,pin,lock.value());
            if(!identity(markerPath,262144).equals(markerId)||!identity(completePath,262144).equals(completeId))throw new IOException("MAFFT marker or completion changed during admission");
            String warning=pin.get("known_warning")==null?null:text(pin.get("known_warning"));
            return new Context(home,requestedProfile,text(pin.get("version")),text(pin.get("program")),markerId,files,helpers,report,reportFiles,completeId,warning);
        }catch(AccessDeniedException denied){throw new Unavailable("MAFFT runtime/setup files are not readable; skip dependent analysis",denied);}
    }
    private static void checkHealth(Path home,Path report,Map<?,?> pin,Map<?,?> lock)throws IOException {
        List<String> base=List.of("/bin/bash",home.resolve(text(pin.get("program"))).toString());var command=new ArrayList<>(base);command.add("--version");
        checkProcess(report.resolve("version/process.json"),limited(command));
        String banner=utf8(raw(report.resolve("version/process.stdout"),1048576))+utf8(raw(report.resolve("version/process.stderr"),1048576));
        if(!java.util.regex.Pattern.compile("(?<![A-Za-z0-9.])v"+java.util.regex.Pattern.quote(text(pin.get("version")))+"(?![0-9.])").matcher(banner).find())throw new IOException("Original MAFFT version banner differs");
        var references=new HashMap<String,Map<?,?>>();for(Object value:list(pin.get("health_references"))) {Map<?,?> r=object(value);references.put(text(r.get("original_control")),object(r.get("reference_output")));}
        for(Object value:list(lock.get("setup_health"))) {
            Map<?,?> control=object(value);String name=member(control.get("name"));Path work=report.resolve(name);command=new ArrayList<>(base);
            for(Object argument:list(control.get("arguments")))command.add(text(argument));checkProcess(work.resolve("process.json"),limited(command));
            for(var entry:object(control.get("inputs")).entrySet()) {
                Fingerprint expected=fingerprint(object(object(lock.get("bundled")).get(entry.getValue())));
                if(!identity(work.resolve(member(entry.getKey())),expected.bytes()).equals(expected))throw new IOException("MAFFT synthetic health input differs");
            }
            for(var entry:references.get(name).entrySet())if(!entry.getKey().equals("process.stderr")) {
                Fingerprint expected=fingerprint(object(entry.getValue()));
                if(!identity(work.resolve(member(entry.getKey())),expected.bytes()).equals(expected))throw new IOException("MAFFT complete original health output differs");
            }
            var alphabet=name.startsWith("DNA")?MafftSequences.Alphabet.NUCLEOTIDE:MafftSequences.Alphabet.PROTEIN;
            boolean add=name.equals("AA-add-keep-map");var input=data(work.resolve("input.fasta"),alphabet,add?MafftSequences.Layout.ALIGNMENT:MafftSequences.Layout.SEQUENCES);
            var aligned=data(work.resolve("process.stdout"),alphabet,MafftSequences.Layout.ALIGNMENT);
            var added=add?data(work.resolve("added.fasta"),alphabet,MafftSequences.Layout.SEQUENCES):null;
            var population=add?MafftSequences.combine(input,added,MafftSequences.Limits.defaults()):input;
            var identities=new LinkedHashMap<String,String>();for(String id:population.taxa())identities.put(id,id);
            MafftSequences.checkIdentities(population,aligned,identities,MafftSequences.OutputOrder.INPUT);
            if(add) {
                MafftSequences.auditBackbone(input,aligned,identities,MafftSequences.Backbone.KEEP_LENGTH);
                var map=MafftAddMap.read(work.resolve("added.fasta.map"),added,aligned,identities,MafftAddMap.Limits.defaults(),row->{},()->{});
                if(map.residues()!=12||map.mappedResidues()!=7||map.deletedResidues()!=5)throw new IOException("MAFFT complete synthetic deletion-map family differs");
            }else MafftSequences.audit(input,aligned,identities,MafftSequences.OutputOrder.INPUT);
        }
    }
    private static MafftSequences.Data data(Path file,MafftSequences.Alphabet alphabet,MafftSequences.Layout layout)throws IOException {
        return MafftSequences.readFasta(file,alphabet,MafftSequences.Symbols.BIOLOGICAL,MafftSequences.Headers.WHOLE_HEADER,layout,MafftSequences.Gaps.ALLOW,MafftSequences.Limits.defaults());
    }
    private static void checkProcess(Path file,List<String> command)throws IOException {
        Map<?,?> value=json(file,262144);
        if(!"completed-process-only".equals(value.get("status"))||integer(value.get("exit_code"),0,0)!=0||!List.of().equals(value.get("owned_live_after_cleanup"))||!Boolean.TRUE.equals(value.get("leader_pid_reserved_until_cleanup"))||!command.equals(value.get("command")))throw new IOException("Incomplete/failed MAFFT setup process or owned cleanup");
    }
    private static List<String> limited(List<String> command){var result=new ArrayList<>(List.of("/usr/bin/prlimit","--as=1073741824","--cpu=15","--fsize=1048576","--nofile=128","--core=0","--"));result.addAll(command);return List.copyOf(result);}
    private static void asciiOwned(Path path)throws IOException {if(!path.toString().matches("/[A-Za-z0-9/._+-]+"))throw new IOException("Original MAFFT managed paths require ASCII without whitespace or metacharacters");}
    private static int mode(Object value)throws IOException {String text=text(value);if(!Set.of("0444","0555").contains(text))throw new IOException("Unsupported MAFFT private file mode");return Integer.parseInt(text,8);}
    static Map<String,Fingerprint> exportSetup(Path supplied)throws IOException {
        Lock lock=lock();Path root=supplied.toAbsolutePath().normalize();canonical(root.getParent());Files.createDirectory(root);var files=new TreeMap<String,Fingerprint>();
        var resources=new TreeMap<String,String>();resources.put("mafft-runtime-lock.json","mafft-runtime-lock.json");resources.put("install_mafft.py","mafft-runtime-setup/install_mafft.py");
        for(Object key:object(lock.value().get("bundled")).keySet()){String name=member(key);resources.put("mafft-runtime/"+name,"mafft-runtime-setup/mafft-runtime/"+name);}
        for(var entry:resources.entrySet()){byte[] raw=resource(entry.getValue());Path target=root.resolve(entry.getKey());Files.createDirectories(target.getParent());Files.write(target,raw,StandardOpenOption.CREATE_NEW);files.put(entry.getKey(),new Fingerprint(raw.length,digest(raw)));}
        byte[] readme=("MAFFT private runtime setup only. Use current-source mafft-tools for separately admitted user-data alignment; broader capabilities remain unfinished.\nUse Linux x86_64 Python3.11+, existing permitted shell/coreutils/Perl/prlimit/setsid, and NEW canonical ASCII home/report paths whose parents exist.\npython3 install_mafft.py --profile linux-x86_64-mafft-7.526 --home NEW_PRIVATE_HOME --cache ARCHIVE_CACHE --report NEW_REPORT_DIRECTORY --allow-install --allow-download\nSelect the exact reported edition; major-only v7 in P052 is unresolved. Install/download flags are independent. Use --archive-dir for complete offline archives. Complete homes can be rechecked with a NEW report without either flag. Require COMPLETED.json and full hashes. Missing/forbidden prerequisites: SKIP3 and skipped dependent analysis; invalid data/integrity/process/capacity/finalization: FAIL2, no admitted result. Preserve failed/partial files and correct the named cause before retrying to new paths.\nSetup checks three full tiny synthetic alignments; it is not an alignment-accuracy, full host/library/Perl admission or whole-paper certificate. Exact7.475 retains the maintainer FFT-NS-i/auto memory-bug warning. Read MAFFT_DEVELOPMENT.md. Cell Ranger remains excluded.\n").getBytes(StandardCharsets.UTF_8);
        Files.write(root.resolve("README.txt"),readme,StandardOpenOption.CREATE_NEW);files.put("README.txt",new Fingerprint(readme.length,digest(readme)));return Map.copyOf(files);
    }
    static List<Segment> staticElf(byte[] raw) throws IOException {
        if(raw.length<64 || raw[0]!=127 || raw[1]!='E' || raw[2]!='L' || raw[3]!='F' || raw[4]!=2 || raw[5]!=1 || raw[6]!=1)throw new IOException("Require original ELF64 little-endian executable");
        var bytes=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        if(bytes.getShort(16)!=2 || bytes.getShort(18)!=62 || bytes.getInt(20)!=1 || bytes.getShort(52)!=64)throw new IOException("MAFFT ELF architecture/type/header differs");
        long offset=bytes.getLong(32);int size=Short.toUnsignedInt(bytes.getShort(54)),count=Short.toUnsignedInt(bytes.getShort(56));
        if(offset<64 || size!=56 || count<1 || count>64 || offset>raw.length-(long)size*count)throw new IOException("Invalid complete MAFFT ELF header dimensions");
        var result=new ArrayList<Segment>();
        for(int i=0;i<count;i++) {
            int at=Math.toIntExact(offset+(long)i*size);long type=Integer.toUnsignedLong(bytes.getInt(at)),flags=Integer.toUnsignedLong(bytes.getInt(at+4));
            long start=bytes.getLong(at+8),file=bytes.getLong(at+32),memory=bytes.getLong(at+40),alignment=bytes.getLong(at+48);
            if(type==2 || type==3)throw new IOException("Dynamic/interpreter ELF segment is outside the static MAFFT profile");
            if(start<0 || file<0 || memory<0 || alignment<0 || file>raw.length || start>raw.length-file || type==1 && memory<file || type==0x6474e551L && (flags&1)!=0)throw new IOException("Invalid MAFFT ELF segment or executable stack");
            result.add(new Segment(type,flags,start,file,memory,alignment));
        }
        if(result.stream().noneMatch(segment->segment.type()==1))throw new IOException("MAFFT ELF has no loadable segment");return List.copyOf(result);
    }
    private static void checkElfRecord(Map<?,?> saved,List<Segment> actual) throws IOException {
        if(!Boolean.TRUE.equals(saved.get("PT_INTERP_absent")) || !Boolean.TRUE.equals(saved.get("PT_DYNAMIC_absent")))throw new IOException("MAFFT static ELF receipt differs");
        List<?> headers=list(saved.get("headers"));if(headers.size()!=actual.size())throw new IOException("MAFFT ELF header census differs");
        for(int i=0;i<headers.size();i++) {
            Map<?,?> item=object(headers.get(i));Segment segment=actual.get(i);
            for(var entry:Map.of("type",segment.type(),"flags",segment.flags(),"offset",segment.offset(),"file_bytes",segment.fileBytes(),"memory_bytes",segment.memoryBytes(),"alignment",segment.alignment()).entrySet())
                if(integer(item.get(entry.getKey()),0,Long.MAX_VALUE)!=entry.getValue())throw new IOException("MAFFT ELF receipt field differs");
        }
    }

    static Fingerprint identity(Path path,long maximum) throws IOException { return identity(path,maximum,1); }
    private static Fingerprint identity(Path path,long maximum,long links) throws IOException {
        canonical(path);BasicFileAttributes before=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!before.isRegularFile() || before.size()>maximum || before.size()>67108864 || ((Number)Files.getAttribute(path,"unix:nlink",LinkOption.NOFOLLOW_LINKS)).longValue()!=links)throw new IOException("Nonregular, linked or oversized MAFFT file: "+path);
        MessageDigest digest=digestInstance();long count=0;
        try(var input=Files.newInputStream(path)) { byte[] block=new byte[65536];for(int n=input.read(block);n>=0;n=input.read(block)) { count=Math.addExact(count,n);if(count>maximum)throw new IOException("MAFFT file grew beyond its complete byte capacity");digest.update(block,0,n); } }
        BasicFileAttributes after=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(count!=before.size() || count!=after.size() || !before.lastModifiedTime().equals(after.lastModifiedTime()) || !java.util.Objects.equals(before.fileKey(),after.fileKey()))throw new IOException("MAFFT file changed while hashing");
        return new Fingerprint(count,HexFormat.of().formatHex(digest.digest()));
    }
    private static void inventory(Path root,Set<String> names,boolean home,long maximum,int fileCount) throws IOException {
        var expected=new HashSet<>(names);expected.add(home?MARKER:"COMPLETED.json");var seen=new HashSet<String>();long[] total={0};int[] count={0};
        var directories=new HashSet<String>();directories.add("");
        for(String name:names)for(Path parent=Path.of(name).getParent();parent!=null;parent=parent.getParent())directories.add(parent.toString().replace('\\','/'));
        Files.walkFileTree(root,new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attributes) throws IOException { canonical(path);if(++count[0]>fileCount || home && !directories.contains(root.relativize(path).toString().replace('\\','/')))throw new IOException("MAFFT complete directory/file capacity exceeded or unknown directory");if(home && !path.equals(root))requireMode(path,0700);return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attributes) throws IOException {
                canonical(path);String name=root.relativize(path).toString().replace('\\','/');
                if(++count[0]>fileCount || !attributes.isRegularFile() || !expected.contains(name) || !seen.add(name))throw new IOException("Extra, linked or unknown MAFFT runtime/setup member");total[0]=Math.addExact(total[0],attributes.size());if(total[0]>maximum)throw new IOException("Complete MAFFT runtime/setup byte capacity exceeded");return FileVisitResult.CONTINUE;
            }
        });if(!seen.equals(expected))throw new IOException("Incomplete MAFFT runtime/setup file census");
    }
    private static void requireMode(Path path,int mode) throws IOException {
        int observed=((Number)Files.getAttribute(path,"unix:mode",LinkOption.NOFOLLOW_LINKS)).intValue()&07777;
        if(observed!=mode)throw new IOException("MAFFT private mode changed: "+path);
    }
    private static String member(Object value) throws IOException { String name=text(value);if(name.isEmpty() || name.startsWith("/") || name.indexOf('\\')>=0 || name.codePoints().anyMatch(Character::isISOControl))throw new IOException("Unsafe MAFFT member path");for(String part:name.split("/",-1))if(part.isEmpty() || part.equals(".") || part.equals(".."))throw new IOException("Unsafe MAFFT member path");return name; }
    private static void canonical(Path path) throws IOException { if(path==null || !path.isAbsolute() || !path.normalize().equals(path) || !path.toRealPath().equals(path))throw new IOException("MAFFT path must be canonical and unlinked: "+path); }
    private static Map<?,?> json(Path path,int maximum) throws IOException { return object(SimpleJson.parse(utf8(raw(path,maximum)))); }
    private static Map<?,?> object(Object value) throws IOException { if(!(value instanceof Map<?,?> map))throw new IOException("Require MAFFT JSON object");return map; }
    private static List<?> list(Object value) throws IOException { if(!(value instanceof List<?> list))throw new IOException("Require MAFFT JSON array");return list; }
    private static String text(Object value) throws IOException { if(!(value instanceof String text))throw new IOException("Require MAFFT JSON text");return text; }
    private static long integer(Object value,long minimum,long maximum) throws IOException { if(!(value instanceof Long number) || number<minimum || number>maximum)throw new IOException("Invalid MAFFT integer metadata");return number; }
    private static double finite(Object value) throws IOException { if(!(value instanceof Number number) || !Double.isFinite(number.doubleValue()))throw new IOException("Invalid MAFFT finite numeric metadata");return number.doubleValue(); }
    private static Fingerprint fingerprint(Map<?,?> value) throws IOException { long bytes=integer(value.get("bytes"),0,67108864);String hash=text(value.get("sha256"));if(!hash.matches("[a-f0-9]{64}"))throw new IOException("Invalid MAFFT SHA256 metadata");return new Fingerprint(bytes,hash); }
    private static void requireFingerprint(byte[] raw,Fingerprint expected) throws IOException { if(raw.length!=expected.bytes() || !digest(raw).equals(expected.sha256()))throw new IOException("Bundled MAFFT input bytes differ from the lock"); }
    private static byte[] raw(Path path,int maximum) throws IOException { try(var input=Files.newInputStream(path)) { byte[] raw=input.readNBytes(maximum+1);if(raw.length>maximum)throw new IOException("Complete MAFFT metadata byte capacity exceeded");return raw; } }
    private static byte[] resource(String name) throws IOException { try(var input=MafftRuntime.class.getResourceAsStream(ROOT+name)) { if(input==null)throw new IOException("Missing bundled MAFFT setup resource: "+name);byte[] raw=input.readNBytes(1048577);if(raw.length>1048576)throw new IOException("Oversized bundled MAFFT setup resource");return raw; } }
    private static String utf8(byte[] raw) throws IOException { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString(); }
    private static String digest(byte[] raw) { return HexFormat.of().formatHex(digestInstance().digest(raw)); }
    private static MessageDigest digestInstance() { try { return MessageDigest.getInstance("SHA-256"); }catch(NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); } }
}
