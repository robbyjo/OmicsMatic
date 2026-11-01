/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.jlinalg.phylogeny.JukesCantorLikelihood;
import org.jlinalg.phylogeny.NewickTree;
import org.jlinalg.phylogeny.PhylogeneticAlignment;

/** Exact original static-program admission. This class launches no process or user analysis. */
final class IqtreeRuntime {
    private IqtreeRuntime() { }
    static final String MARKER=".omicsmatic-iqtree.json";
    private static final String ROOT="/org/jlinalg/omics/";
    private static final String LOCK_SHA="2d80b924a35c9ad6dad675ca9905c4595dc1ccf8e2166116f8a001dc36a0a4f7";
    private static final List<String> VERSIONS=List.of("1.6.12","2.1.2","2.1.4-beta","2.2.0.3","2.3.1","2.3.5","2.3.6","2.4.0");
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message) { super(message); }
        Unavailable(String message,Throwable cause) { super(message,cause); }
    }
    record Fingerprint(long bytes,String sha256) { }
    record External(String requested,String realPath,Fingerprint fingerprint) { }
    record Segment(long type,long flags,long offset,long fileBytes,long memoryBytes,long alignment) { }
    record Context(Path home,String profile,String version,String program,Fingerprint marker,
                   Map<String,Fingerprint> files,Map<String,External> helpers,Path setupReport,
                   Map<String,Fingerprint> setupFiles,Fingerprint setupCompletion) {
        Context { files=Map.copyOf(files);helpers=Map.copyOf(helpers);setupFiles=Map.copyOf(setupFiles); }
        List<String> command() { return List.of(home.resolve(program).toString()); }
        Map<String,String> environment(int threads) {
            if(threads<1 || threads>256)throw new IllegalArgumentException("IQ-TREE requires a declared 1..256 thread count");
            return Map.of("PATH","/usr/bin:/bin","LANG","C","LC_ALL","C","OMP_NUM_THREADS",String.valueOf(threads),"OPENBLAS_NUM_THREADS","1","MKL_NUM_THREADS","1");
        }
        void verify() throws IOException {
            try { if(!equals(open(home,profile)))throw new IOException("IQ-TREE admitted runtime or setup evidence changed during analysis"); }
            catch(Unavailable unavailable) { throw new IOException("Previously admitted IQ-TREE prerequisite became unavailable; no result is admitted",unavailable); }
        }
    }
    private record Lock(Map<?,?> value,String installerSha256) { }

    static Set<String> profiles() { var result=new HashSet<String>();for(String version:VERSIONS)result.add("linux-x86_64-iqtree-"+version);return Set.copyOf(result); }

    private static Lock lock() throws IOException {
        byte[] raw=resource("iqtree-runtime-lock.json");
        if(!digest(raw).equals(LOCK_SHA))throw new IOException("Bundled IQ-TREE runtime lock differs from its pinned source");
        Map<?,?> value=object(SimpleJson.parse(utf8(raw)));
        if(integer(value.get("schema_version"),1,1)!=1 || !"IQ-TREE".equals(value.get("tool")) || !object(value.get("profiles")).keySet().equals(profiles()))
            throw new IOException("Unsupported IQ-TREE lock schema/profile set");
        for(var entry:object(value.get("bundled")).entrySet()) {
            String name=member(entry.getKey());byte[] bytes=resource("iqtree-runtime-setup/iqtree-runtime/"+name);
            requireFingerprint(bytes,fingerprint(object(entry.getValue())));
        }
        return new Lock(value,digest(resource("iqtree-runtime-setup/install_iqtree.py")));
    }

    static Context open(Path supplied,String requestedProfile) throws IOException {
        String architecture=System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        if(!System.getProperty("os.name").equals("Linux") || !Set.of("amd64","x86_64").contains(architecture))
            throw new Unavailable("IQ-TREE original runtimes require Linux x86_64 Java; use Linux Java inside WSL on Windows");
        if(!profiles().contains(requestedProfile))throw new Unavailable("Requested exact IQ-TREE edition is unavailable; no alternative edition is substituted");
        if(Files.exists(Path.of("/etc/ld.so.preload"),LinkOption.NOFOLLOW_LINKS))throw new Unavailable("OS loader preloads are outside the admitted helper environment");
        Path home=supplied.toAbsolutePath().normalize();
        if(!Files.exists(home,LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Exact IQ-TREE home is absent; provision the permitted pinned profile before dependent analysis");
        try {
            canonical(home);if(!Files.isDirectory(home,LinkOption.NOFOLLOW_LINKS))throw new IOException("IQ-TREE home must be a private directory");
            Lock lock=lock();Map<?,?> pin=object(object(lock.value().get("profiles")).get(requestedProfile));
            Path markerPath=home.resolve(MARKER);Fingerprint markerIdentity=identity(markerPath,262144);Map<?,?> marker=json(markerPath,262144);
            integer(marker.get("schema_version"),1,1);
            for(var entry:Map.of("tool","IQ-TREE","status","installed-smoke-tested-runtime-only","profile",requestedProfile,"version",text(pin.get("version")),"home",home.toString(),"lock_sha256",LOCK_SHA,"installer_sha256",lock.installerSha256()).entrySet())
                if(!entry.getValue().equals(marker.get(entry.getKey())))throw new IOException("IQ-TREE runtime binding differs: "+entry.getKey());
            if(!Boolean.FALSE.equals(marker.get("scientific_analysis_admitted")))throw new IOException("Runtime installation cannot claim scientific analysis admission");
            Map<?,?> host=object(marker.get("host"));
            if(!"Linux".equals(host.get("os")) || !"x86_64".equals(host.get("architecture")))throw new IOException("IQ-TREE setup host identity differs");
            if(!System.getProperty("os.version").equals(host.get("kernel")))throw new Unavailable("IQ-TREE admitted kernel changed; preserve this home and provision a fresh admission on the current host");
            Map<?,?> expected=object(pin.get("files")),recorded=object(marker.get("files"));
            if(expected.size()!=8 || !recorded.keySet().equals(expected.keySet()))throw new IOException("Complete IQ-TREE runtime file inventory differs");
            String program=member(pin.get("program"));var files=new TreeMap<String,Fingerprint>();
            for(var entry:expected.entrySet()) {
                String name=member(entry.getKey());Fingerprint wanted=fingerprint(object(entry.getValue()));Map<?,?> saved=object(recorded.get(name));
                if(!fingerprint(saved).equals(wanted) || !(name.equals(program)?"0555":"0444").equals(saved.get("mode")))throw new IOException("IQ-TREE runtime member binding differs: "+name);
                if(!identity(home.resolve(name),wanted.bytes()).equals(wanted))throw new IOException("IQ-TREE runtime bytes changed: "+name);
                requireMode(home.resolve(name),name.equals(program)?0555:0444);files.put(name,wanted);
            }
            requireMode(markerPath,0444);inventory(home,files.keySet(),true,67108864,64);
            if(!Files.isExecutable(home.resolve(program)))throw new Unavailable("IQ-TREE executable permission is unavailable; skip dependent analysis");
            List<Segment> elf=staticElf(raw(home.resolve(program),33554432));checkElfRecord(object(marker.get("elf")),elf);
            var helpers=new TreeMap<String,External>();Map<?,?> savedHelpers=object(host.get("helpers"));
            if(!savedHelpers.keySet().equals(Set.of("/usr/bin/prlimit","/usr/bin/setsid")))throw new IOException("IQ-TREE OS helper census differs");
            for(var entry:savedHelpers.entrySet()) {
                String requested=text(entry.getKey());Map<?,?> saved=object(entry.getValue());Path real=Path.of(requested).toRealPath();
                if(!real.toString().equals(saved.get("real_path")))throw new IOException("IQ-TREE OS helper target changed");Fingerprint wanted=fingerprint(saved);
                if(!identity(real,wanted.bytes()).equals(wanted))throw new IOException("IQ-TREE OS helper bytes changed");
                if(!Files.isExecutable(real))throw new Unavailable("IQ-TREE OS helper execution is forbidden: "+requested);
                helpers.put(requested,new External(requested,real.toString(),wanted));
            }
            Path report=Path.of(text(marker.get("setup_report_directory")));canonical(report);
            if(report.startsWith(home)||home.startsWith(report))throw new IOException("IQ-TREE setup report and runtime home overlap");
            Path completion=report.resolve("COMPLETED.json");Fingerprint completionIdentity=identity(completion,262144);Map<?,?> completed=json(completion,262144);
            if(!"completed-runtime-setup-only".equals(completed.get("status")) || !home.toString().equals(completed.get("home")) || !requestedProfile.equals(completed.get("profile")) || !markerIdentity.sha256().equals(completed.get("runtime_marker_sha256")) || !Boolean.FALSE.equals(completed.get("scientific_analysis_admitted")))
                throw new IOException("IQ-TREE runtime setup is incomplete or completion binding differs");
            Map<?,?> savedFiles=object(completed.get("files"));var setupFiles=new TreeMap<String,Fingerprint>();
            if(!savedFiles.keySet().containsAll(Set.of("installation.json","WARNINGS.md","health/process.json","version/process.json")))throw new IOException("Incomplete IQ-TREE setup evidence census");
            for(var entry:savedFiles.entrySet()) {
                String name=member(entry.getKey());Fingerprint wanted=fingerprint(object(entry.getValue()));
                if(!identity(report.resolve(name),wanted.bytes()).equals(wanted))throw new IOException("IQ-TREE setup evidence changed: "+name);setupFiles.put(name,wanted);
            }
            inventory(report,setupFiles.keySet(),false,8388608,128);
            Map<?,?> installation=json(report.resolve("installation.json"),262144);
            if(integer(installation.get("exit_code"),0,0)!=0 || !Boolean.FALSE.equals(installation.get("scientific_analysis_admitted")) || !markerIdentity.sha256().equals(installation.get("runtime_marker_sha256")))throw new IOException("IQ-TREE setup has no completed successful installation report");
            checkProcess(report.resolve("version/process.json"),versionCommand(home,program));
            checkProcess(report.resolve("health/process.json"),healthCommand(home,program,report));
            String versionText=utf8(raw(report.resolve("version/process.stdout"),1048576));
            if(!versionText.lines().findFirst().orElse("").equals(pin.get("version_first_line")))throw new IOException("Original IQ-TREE version banner differs");
            replayHealth(home,report,lock.value(),object(marker.get("health")));
            if(!markerIdentity.equals(identity(markerPath,262144)) || !completionIdentity.equals(identity(completion,262144)))throw new IOException("IQ-TREE admission metadata changed during verification");
            return new Context(home,requestedProfile,text(pin.get("version")),program,markerIdentity,files,helpers,report,setupFiles,completionIdentity);
        } catch(AccessDeniedException denied) { throw new Unavailable("IQ-TREE runtime access is forbidden; skip dependent analysis",denied); }
        catch(IllegalArgumentException invalid) { throw new IOException("Invalid IQ-TREE runtime metadata",invalid); }
    }

    private static void replayHealth(Path home,Path report,Map<?,?> lock,Map<?,?> saved) throws IOException {
        Map<?,?> expected=object(lock.get("health"));
        if(!expected.get("status").equals(saved.get("status")) || integer(saved.get("sequences"),4,4)!=4 || integer(saved.get("sites"),12,12)!=12 || integer(saved.get("all_site_likelihoods_checked"),12,12)!=12 || !List.of().equals(saved.get("owned_live_after_cleanup")))
            throw new IOException("IQ-TREE complete synthetic health receipt differs");
        for(String name:List.of("alignment.fasta","fixed.nwk"))if(!identity(report.resolve("health/"+name),4096).equals(identity(home.resolve("share/health/"+name),4096)))throw new IOException("IQ-TREE health inputs differ from pinned originals");
        Path treeFile=report.resolve("health/result.treefile");if(!identity(treeFile,4096).equals(fingerprint(object(expected.get("treefile")))))throw new IOException("IQ-TREE complete fixed-tree output differs");
        var data=PhylogeneticAlignment.readFasta(report.resolve("health/alignment.fasta"),PhylogeneticAlignment.Kind.DNA,PhylogeneticAlignment.HeaderPolicy.WHOLE_HEADER,new PhylogeneticAlignment.Limits(4,12,48,4096,128,32));
        if(data.rows().size()!=4 || data.columns()!=12)throw new IOException("Incomplete IQ-TREE health alignment");
        var tree=NewickTree.parse(utf8(raw(treeFile,4096)));var requested=NewickTree.parse(utf8(raw(report.resolve("health/fixed.nwk"),4096)));
        if(!tree.taxa().equals(data.taxa()) || !tree.bipartitions(1000).equals(requested.bipartitions(1000)))throw new IOException("IQ-TREE fixed health topology/taxa differ");
        for(String a:data.taxa())for(String b:data.taxa())if(Math.abs(tree.distance(a,b)-requested.distance(a,b))>1e-15)throw new IOException("IQ-TREE fixed health branch metric differs");
        var nativeResult=JukesCantorLikelihood.evaluate(tree,data.jc69Sequences());
        if(Math.abs(nativeResult.logLikelihood()-finite(expected.get("total_log_likelihood")))>2e-12)throw new IOException("Native IQ-TREE health replay differs from independent enumeration");
        String[] sites=utf8(raw(report.resolve("health/result.sitelh"),16384)).strip().split("\\s+");
        if(sites.length!=15 || !sites[0].equals("1") || !sites[1].equals("12") || !sites[2].equals("Site_Lh"))throw new IOException("Incomplete IQ-TREE health site-likelihood output");
        List<?> oracle=list(expected.get("site_log_likelihoods"));if(oracle.size()!=12)throw new IOException("Incomplete IQ-TREE health oracle");double[] actual=nativeResult.siteLogLikelihoods();
        for(int i=0;i<12;i++) { if(Math.abs(actual[i]-finite(oracle.get(i)))>2e-13)throw new IOException("Native IQ-TREE health site differs from independent enumeration");rounded(actual[i],sites[i+3]); }
        String model=utf8(raw(report.resolve("health/result.iqtree"),1048576));
        if(!one(model,"(?m)^Model of substitution: ([^\\r\\n]+)").equals("JC") || !model.contains("Number of free parameters (#branches + #model parameters): 0"))throw new IOException("IQ-TREE health fixed-model report differs");
        rounded(nativeResult.logLikelihood(),one(model,"(?m)^Log-likelihood of the tree: ([^ ]+)"));
    }

    private static void checkProcess(Path path,List<String> expected) throws IOException {
        Map<?,?> process=json(path,262144);
        if(!"completed-process-only".equals(process.get("status")) || integer(process.get("exit_code"),0,0)!=0 || !List.of().equals(process.get("owned_live_after_cleanup")) || !Boolean.TRUE.equals(process.get("leader_pid_reserved_until_cleanup")) || !expected.equals(process.get("command")))
            throw new IOException("IQ-TREE setup process receipt or cleanup differs");
    }
    private static List<String> limited(List<String> arguments) { var result=new ArrayList<>(List.of("/usr/bin/prlimit","--as=1073741824","--cpu=15","--fsize=1048576","--nofile=128","--core=0","--"));result.addAll(arguments);return List.copyOf(result); }
    private static List<String> versionCommand(Path home,String program) { return limited(List.of(home.resolve(program).toString(),"--version")); }
    private static List<String> healthCommand(Path home,String program,Path report) {
        return limited(List.of(home.resolve(program).toString(),"-s",report.resolve("health/alignment.fasta").toString(),"-st","DNA","-m","JC","-te",report.resolve("health/fixed.nwk").toString(),"-blfix","-wsl","-seed","1729","-nt","1","-pre",report.resolve("health/result").toString()));
    }

    static List<Segment> staticElf(byte[] raw) throws IOException {
        if(raw.length<64 || raw[0]!=127 || raw[1]!='E' || raw[2]!='L' || raw[3]!='F' || raw[4]!=2 || raw[5]!=1 || raw[6]!=1)throw new IOException("Require original ELF64 little-endian executable");
        var bytes=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        if(bytes.getShort(16)!=2 || bytes.getShort(18)!=62 || bytes.getInt(20)!=1 || bytes.getShort(52)!=64)throw new IOException("IQ-TREE ELF architecture/type/header differs");
        long offset=bytes.getLong(32);int size=Short.toUnsignedInt(bytes.getShort(54)),count=Short.toUnsignedInt(bytes.getShort(56));
        if(offset<64 || size!=56 || count<1 || count>64 || offset>raw.length-(long)size*count)throw new IOException("Invalid complete IQ-TREE ELF header dimensions");
        var result=new ArrayList<Segment>();
        for(int i=0;i<count;i++) {
            int at=Math.toIntExact(offset+(long)i*size);long type=Integer.toUnsignedLong(bytes.getInt(at)),flags=Integer.toUnsignedLong(bytes.getInt(at+4));
            long start=bytes.getLong(at+8),file=bytes.getLong(at+32),memory=bytes.getLong(at+40),alignment=bytes.getLong(at+48);
            if(type==2 || type==3)throw new IOException("Dynamic/interpreter ELF segment is outside the static IQ-TREE profile");
            if(start<0 || file<0 || memory<0 || alignment<0 || file>raw.length || start>raw.length-file || type==1 && memory<file || type==0x6474e551L && (flags&1)!=0)throw new IOException("Invalid IQ-TREE ELF segment or executable stack");
            result.add(new Segment(type,flags,start,file,memory,alignment));
        }
        if(result.stream().noneMatch(segment->segment.type()==1))throw new IOException("IQ-TREE ELF has no loadable segment");return List.copyOf(result);
    }
    private static void checkElfRecord(Map<?,?> saved,List<Segment> actual) throws IOException {
        if(!Boolean.TRUE.equals(saved.get("PT_INTERP_absent")) || !Boolean.TRUE.equals(saved.get("PT_DYNAMIC_absent")))throw new IOException("IQ-TREE static ELF receipt differs");
        List<?> headers=list(saved.get("headers"));if(headers.size()!=actual.size())throw new IOException("IQ-TREE ELF header census differs");
        for(int i=0;i<headers.size();i++) {
            Map<?,?> item=object(headers.get(i));Segment segment=actual.get(i);
            for(var entry:Map.of("type",segment.type(),"flags",segment.flags(),"offset",segment.offset(),"file_bytes",segment.fileBytes(),"memory_bytes",segment.memoryBytes(),"alignment",segment.alignment()).entrySet())
                if(integer(item.get(entry.getKey()),0,Long.MAX_VALUE)!=entry.getValue())throw new IOException("IQ-TREE ELF receipt field differs");
        }
    }

    static Map<String,Fingerprint> exportSetup(Path supplied) throws IOException {
        Lock lock=lock();Path root=supplied.toAbsolutePath().normalize();canonical(root.getParent());Files.createDirectory(root);var files=new TreeMap<String,Fingerprint>();
        var resources=new TreeMap<String,String>();resources.put("iqtree-runtime-lock.json","iqtree-runtime-lock.json");resources.put("install_iqtree.py","iqtree-runtime-setup/install_iqtree.py");
        for(Object key:object(lock.value().get("bundled")).keySet()) { String name=member(key);resources.put("iqtree-runtime/"+name,"iqtree-runtime-setup/iqtree-runtime/"+name); }
        for(var entry:resources.entrySet()) { byte[] raw=resource(entry.getValue());Path target=root.resolve(entry.getKey());Files.createDirectories(target.getParent());Files.write(target,raw,StandardOpenOption.CREATE_NEW);files.put(entry.getKey(),new Fingerprint(raw.length,digest(raw))); }
        Files.writeString(root.resolve("README.txt"),"IQ-TREE private runtime setup only. No compiled upstream program is bundled.\nUse Linux x86_64 Python 3.11+, existing permitted prlimit/setsid, a fresh home and report, and separate cache paths whose parents exist.\npython3 install_iqtree.py --profile linux-x86_64-iqtree-2.3.6 --home NEW_PRIVATE_HOME --cache ARCHIVE_CACHE --report NEW_REPORT_DIRECTORY --allow-install --allow-download\nDownloads/install require permission; use --archive-dir for exact existing archives. Reverification of a complete home needs only a new report, without install/download flags. Require COMPLETED.json and matching hashes. Missing/forbidden prerequisites or editions require SKIP3 and skipped analysis. Integrity, execution, capacity or finalization failures require FAIL2; preserve all failed files and use fresh paths.\nSetup performs only the complete four-taxon fixed-JC69 smoke check and admits no user analysis or whole-paper result. The typed public IQ-TREE analysis adapter remains unfinished.\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        return Map.copyOf(files);
    }

    static Fingerprint identity(Path path,long maximum) throws IOException {
        canonical(path);BasicFileAttributes before=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!before.isRegularFile() || before.size()>maximum || before.size()>67108864 || ((Number)Files.getAttribute(path,"unix:nlink",LinkOption.NOFOLLOW_LINKS)).longValue()!=1)throw new IOException("Nonregular, linked or oversized IQ-TREE file: "+path);
        MessageDigest digest=digestInstance();long count=0;
        try(var input=Files.newInputStream(path)) { byte[] block=new byte[65536];for(int n=input.read(block);n>=0;n=input.read(block)) { count=Math.addExact(count,n);if(count>maximum)throw new IOException("IQ-TREE file grew beyond its complete byte capacity");digest.update(block,0,n); } }
        BasicFileAttributes after=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(count!=before.size() || count!=after.size() || !before.lastModifiedTime().equals(after.lastModifiedTime()) || !java.util.Objects.equals(before.fileKey(),after.fileKey()))throw new IOException("IQ-TREE file changed while hashing");
        return new Fingerprint(count,HexFormat.of().formatHex(digest.digest()));
    }
    private static void inventory(Path root,Set<String> names,boolean home,long maximum,int fileCount) throws IOException {
        var expected=new HashSet<>(names);expected.add(home?MARKER:"COMPLETED.json");var seen=new HashSet<String>();long[] total={0};int[] count={0};
        var directories=new HashSet<String>();directories.add("");
        for(String name:names)for(Path parent=Path.of(name).getParent();parent!=null;parent=parent.getParent())directories.add(parent.toString().replace('\\','/'));
        Files.walkFileTree(root,new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attributes) throws IOException { canonical(path);if(++count[0]>fileCount || home && !directories.contains(root.relativize(path).toString().replace('\\','/')))throw new IOException("IQ-TREE complete directory/file capacity exceeded or unknown directory");if(home && !path.equals(root))requireMode(path,0700);return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attributes) throws IOException {
                canonical(path);String name=root.relativize(path).toString().replace('\\','/');
                if(++count[0]>fileCount || !attributes.isRegularFile() || !expected.contains(name) || !seen.add(name))throw new IOException("Extra, linked or unknown IQ-TREE runtime/setup member");total[0]=Math.addExact(total[0],attributes.size());if(total[0]>maximum)throw new IOException("Complete IQ-TREE runtime/setup byte capacity exceeded");return FileVisitResult.CONTINUE;
            }
        });if(!seen.equals(expected))throw new IOException("Incomplete IQ-TREE runtime/setup file census");
    }
    private static void requireMode(Path path,int mode) throws IOException {
        int observed=((Number)Files.getAttribute(path,"unix:mode",LinkOption.NOFOLLOW_LINKS)).intValue()&07777;
        if(observed!=mode)throw new IOException("IQ-TREE private mode changed: "+path);
    }
    private static String member(Object value) throws IOException { String name=text(value);if(name.isEmpty() || name.startsWith("/") || name.indexOf('\\')>=0 || name.codePoints().anyMatch(Character::isISOControl))throw new IOException("Unsafe IQ-TREE member path");for(String part:name.split("/",-1))if(part.isEmpty() || part.equals(".") || part.equals(".."))throw new IOException("Unsafe IQ-TREE member path");return name; }
    private static void canonical(Path path) throws IOException { if(path==null || !path.isAbsolute() || !path.normalize().equals(path) || !path.toRealPath().equals(path))throw new IOException("IQ-TREE path must be canonical and unlinked: "+path); }
    private static Map<?,?> json(Path path,int maximum) throws IOException { return object(SimpleJson.parse(utf8(raw(path,maximum)))); }
    private static Map<?,?> object(Object value) throws IOException { if(!(value instanceof Map<?,?> map))throw new IOException("Require IQ-TREE JSON object");return map; }
    private static List<?> list(Object value) throws IOException { if(!(value instanceof List<?> list))throw new IOException("Require IQ-TREE JSON array");return list; }
    private static String text(Object value) throws IOException { if(!(value instanceof String text))throw new IOException("Require IQ-TREE JSON text");return text; }
    private static long integer(Object value,long minimum,long maximum) throws IOException { if(!(value instanceof Long number) || number<minimum || number>maximum)throw new IOException("Invalid IQ-TREE integer metadata");return number; }
    private static double finite(Object value) throws IOException { if(!(value instanceof Number number) || !Double.isFinite(number.doubleValue()))throw new IOException("Invalid IQ-TREE finite numeric metadata");return number.doubleValue(); }
    private static Fingerprint fingerprint(Map<?,?> value) throws IOException { long bytes=integer(value.get("bytes"),0,67108864);String hash=text(value.get("sha256"));if(!hash.matches("[a-f0-9]{64}"))throw new IOException("Invalid IQ-TREE SHA256 metadata");return new Fingerprint(bytes,hash); }
    private static void requireFingerprint(byte[] raw,Fingerprint expected) throws IOException { if(raw.length!=expected.bytes() || !digest(raw).equals(expected.sha256()))throw new IOException("Bundled IQ-TREE input bytes differ from the lock"); }
    private static byte[] raw(Path path,int maximum) throws IOException { try(var input=Files.newInputStream(path)) { byte[] raw=input.readNBytes(maximum+1);if(raw.length>maximum)throw new IOException("Complete IQ-TREE metadata byte capacity exceeded");return raw; } }
    private static byte[] resource(String name) throws IOException { try(var input=IqtreeRuntime.class.getResourceAsStream(ROOT+name)) { if(input==null)throw new IOException("Missing bundled IQ-TREE setup resource: "+name);byte[] raw=input.readNBytes(1048577);if(raw.length>1048576)throw new IOException("Oversized bundled IQ-TREE setup resource");return raw; } }
    private static String utf8(byte[] raw) throws IOException { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString(); }
    private static String digest(byte[] raw) { return HexFormat.of().formatHex(digestInstance().digest(raw)); }
    private static MessageDigest digestInstance() { try { return MessageDigest.getInstance("SHA-256"); }catch(NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); } }
    private static String one(String source,String regex) throws IOException { var match=Pattern.compile(regex).matcher(source);if(!match.find())throw new IOException("Missing IQ-TREE health report field");String value=match.group(1);if(match.find())throw new IOException("Repeated IQ-TREE health report field");return value; }
    private static void rounded(double actual,String displayed) throws IOException { try { double source=Double.parseDouble(displayed);if(!Double.isFinite(source) || Math.abs(actual-source)>.5*new BigDecimal(displayed).ulp().doubleValue()+2e-12)throw new IOException("IQ-TREE source likelihood differs beyond its printed precision"); }catch(NumberFormatException invalid) { throw new IOException("Invalid original IQ-TREE likelihood token",invalid); } }
}
