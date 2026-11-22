/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/** Admitted unmodified original HOMER core; analysis never installs software. */
final class HomerRuntime {
    private HomerRuntime() { }
    private static final String MARKER=".jlinalg-homer.json";
    private static final String HEALTH="passed-original-fixed-scan-known-two-statistics-and-discovery-health";
    private record Lock(Map<?,?> data,String sha256) { }
    private static final Lock LOCK=load();
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
        Unavailable(String message,Throwable cause){super(message,cause);}
    }
    record External(String realPath,long bytes,String sha256) { }
    record Context(Path home,String profile,String version,String markerHash,
            Map<String,String> files,Map<String,External> external) {
        Context {files=Map.copyOf(files);external=Map.copyOf(external);}
        void verify()throws IOException {
            try {if(!equals(open(home,profile)))throw new IOException("HOMER runtime admission changed during analysis");}
            catch(Unavailable failure){throw new IOException("HOMER runtime changed during analysis; no completed result is admitted",failure);}
        }
    }
    private static Lock load(){
        try(var in=HomerRuntime.class.getResourceAsStream("/org/jlinalg/omics/homer-runtime-lock.json")){
            if(in==null)throw new IOException("Missing bundled HOMER runtime lock");
            byte[] raw=in.readNBytes(1048577);if(raw.length>1048576)throw new IOException("Oversized HOMER runtime lock");
            return new Lock(AdmixtureInputs.decode(raw),AdmixtureInputs.digest(raw));
        }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
    static Context open(Path supplied,String profile)throws Unavailable {
        try {
            if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch").toLowerCase(Locale.ROOT)))
                throw new IOException("Original HOMER core requires Linux x86_64 Java; on Windows use Linux Java inside WSL");
            Path home=supplied.toAbsolutePath().normalize();
            if(!Files.isDirectory(home,LinkOption.NOFOLLOW_LINKS)||!home.toRealPath().equals(home))throw new IOException("Missing or linked private HOMER runtime home");
            Path marker=home.resolve(MARKER);HomerFasta.regular(marker,1048576);byte[] raw=AdmixtureInputs.raw(marker,1048576);
            String markerHash=AdmixtureInputs.digest(raw);Map<?,?> meta=AdmixtureInputs.decode(raw);
            Map<?,?> pin=AdmixtureInputs.map(AdmixtureInputs.map(LOCK.data.get("profiles")).get(profile));
            String version=AdmixtureInputs.text(pin,"version");
            if(AdmixtureInputs.whole(meta,"schema_version",1,1)!=1||!"HOMER".equals(meta.get("tool"))||!profile.equals(meta.get("profile"))||!version.equals(meta.get("version"))||!LOCK.sha256.equals(meta.get("lock_sha256")))
                throw new IOException("Requested HOMER version/profile or bundled lock differs; no version substitution is allowed");
            Map<?,?> host=AdmixtureInputs.map(meta.get("host")),health=AdmixtureInputs.map(meta.get("health"));
            if(!"Linux".equals(host.get("os"))||!"x86_64".equals(host.get("architecture"))||!System.getProperty("os.version").equals(host.get("kernel"))||!HEALTH.equals(health.get("status"))
                    ||AdmixtureInputs.whole(health,"targets",40,40)!=40||AdmixtureInputs.whole(health,"background",40,40)!=40||AdmixtureInputs.whole(health,"fixed_motif_hits",1,1000)<1)
                throw new IOException("HOMER host/kernel or actual scientific health differs; help output is insufficient");
            if(!(health.get("calls") instanceof List<?> calls)||calls.size()!=4)throw new IOException("HOMER four-workflow health record is incomplete");
            for(Object call:calls)if(AdmixtureInputs.whole(AdmixtureInputs.map(call),"exit_code",0,0)!=0)throw new IOException("Original HOMER health operation failed");
            Map<?,?> members=AdmixtureInputs.map(pin.get("members")),recorded=AdmixtureInputs.map(meta.get("files"));
            if(!members.keySet().equals(recorded.keySet()))throw new IOException("HOMER runtime member inventory differs");
            var files=new TreeMap<String,String>();
            for(var entry:members.entrySet()){
                String name=entry.getKey().toString();if(!Set.of("bin/homer2","share/COPYING").contains(name))throw new IOException("Unexpected locked HOMER runtime member");
                Map<?,?> expected=AdmixtureInputs.map(entry.getValue());Path path=home.resolve(name);HomerFasta.regular(path,100000000);
                String digest=FollowupSupport.hash(path);
                if(Files.size(path)!=AdmixtureInputs.whole(expected,"bytes",1,100000000)||!digest.equals(expected.get("sha256"))||!digest.equals(recorded.get(name))||name.startsWith("bin/")&&!Files.isExecutable(path))
                    throw new IOException("Original HOMER member changed or cannot execute: "+name);
                files.put(name,digest);
            }
            var found=new HashSet<String>();int count=0;
            try(var walk=Files.walk(home)){
                for(var it=walk.iterator();it.hasNext();){Path path=it.next();if(++count>16||Files.isSymbolicLink(path))throw new IOException("Unexpected or linked HOMER runtime family");
                    if(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))found.add(home.relativize(path).toString().replace('\\','/'));
                    else if(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Nonregular HOMER runtime member");}
            }
            var expectedFiles=new HashSet<>(files.keySet());expectedFiles.add(MARKER);if(!found.equals(expectedFiles))throw new IOException("HOMER runtime contains extra or missing files");
            Map<?,?> closure=AdmixtureInputs.map(meta.get("closure")),externals=AdmixtureInputs.map(closure.get("external_files"));
            if(!(pin.get("external_library_names") instanceof List<?> libraryNames)||!(closure.get("external_libraries") instanceof List<?> libraryPaths)||!"bin/homer2".equals(closure.get("binary")))throw new IOException("HOMER external library declaration is missing");
            var names=new HashSet<String>();var paths=new HashSet<String>();
            for(Object value:libraryPaths){String name=value.toString();paths.add(name);names.add(Path.of(name).getFileName().toString());}
            if(!names.equals(new HashSet<>(libraryNames))||names.size()!=libraryPaths.size()||paths.size()!=libraryPaths.size())throw new IOException("HOMER dynamic-library binding family differs");
            paths.add("/usr/bin/ldd");if(!paths.equals(externals.keySet()))throw new IOException("HOMER host-library closure is incomplete or contains unexpected entries");
            var external=new TreeMap<String,External>();
            for(var entry:externals.entrySet()){
                Path path=Path.of(entry.getKey().toString());Map<?,?> expected=AdmixtureInputs.map(entry.getValue());
                if(!path.isAbsolute()||!Files.isRegularFile(path))throw new IOException("HOMER host dependency is absent: "+path);
                Path real=path.toRealPath();long bytes=AdmixtureInputs.whole(expected,"bytes",1,100000000);String digest=FollowupSupport.hash(real);
                if(!real.toString().equals(expected.get("real_path"))||Files.size(real)!=bytes||!digest.equals(expected.get("sha256")))throw new IOException("HOMER host dependency changed: "+path);
                external.put(path.toString(),new External(real.toString(),bytes,digest));
            }
            if(!markerHash.equals(FollowupSupport.hash(marker)))throw new IOException("HOMER marker changed during admission");
            return new Context(home,profile,version,markerHash,files,external);
        }catch(Exception failure){throw new Unavailable("Original HOMER dependency unavailable: "+failure.getMessage(),failure);}
    }
    static void provenance(Context runtime,Map<String,Object> session){
        session.put("runtime_home",runtime.home.toString());session.put("runtime_profile",runtime.profile);session.put("runtime_version",runtime.version);
        session.put("runtime_marker_sha256",runtime.markerHash);session.put("runtime_lock_sha256",LOCK.sha256);session.put("runtime_files",runtime.files);
        var external=new TreeMap<String,Object>();runtime.external.forEach((name,e)->external.put(name,Map.of("real_path",e.realPath,"bytes",e.bytes,"sha256",e.sha256)));session.put("runtime_external_files",external);
        session.put("runtime_scope",LOCK.data.get("scope"));session.put("runtime_transport_warning",LOCK.data.get("transport_warning"));
    }
    static int execute(Context runtime,List<String> arguments,Path work,String label,int timeoutSeconds,long byteBound,Map<String,Object> session)throws Exception {
        return execute(runtime,arguments,work,label,timeoutSeconds,byteBound,0,session);
    }
    static int execute(Context runtime,List<String> arguments,Path work,String label,int timeoutSeconds,long byteBound,long rssBound,Map<String,Object> session)throws Exception {
        var command=new ArrayList<String>();command.add(runtime.home.resolve("bin/homer2").toString());command.addAll(arguments);
        return executeCommand(command,work,label,timeoutSeconds,byteBound,rssBound,Map.of(),runtime::verify,
                ()->{if(!runtime.markerHash.equals(FollowupSupport.hash(runtime.home.resolve(MARKER))))throw new IOException("HOMER runtime marker changed during execution");},session);
    }
    @FunctionalInterface interface Verify {void run()throws IOException;}
    static int executeCommand(List<String> command,Path work,String label,int timeoutSeconds,long byteBound,long rssBound,
                              Map<String,String> extraEnvironment,Verify verify,Verify markerCheck,Map<String,Object> session)throws Exception {
        verify.run();if(!label.matches("[a-z0-9-]+"))throw new IOException("Unsafe original operation label");
        Path home=work.resolve(label+"-home"),temp=work.resolve(label+"-tmp");Files.createDirectory(home);Files.createDirectory(temp);
        var builder=new ProcessBuilder(command).directory(work.toFile()).redirectOutput(work.resolve(label+".stdout").toFile()).redirectError(work.resolve(label+".stderr").toFile());
        var env=builder.environment();env.clear();env.put("PATH","/usr/bin:/bin");env.put("HOME",home.toString());env.put("TMPDIR",temp.toString());env.put("LANG","C");env.put("LC_ALL","C");env.put("OMP_NUM_THREADS","1");env.putAll(extraEnvironment);
        var call=new LinkedHashMap<String,Object>();call.put("command",command);call.put("timeout_seconds",timeoutSeconds);call.put("byte_bound",byteBound);if(rssBound>0)call.put("rss_bound_bytes",rssBound);session.put("process_"+label,call);
        long started=System.nanoTime();Process child;
        try{child=builder.start();}catch(IOException failure){call.put("launch_error",failure.toString());throw new Unavailable("Verified original HOMER executable could not start on this host/mount",failure);}
        call.put("pid",child.pid());var descendants=new LinkedHashSet<ProcessHandle>();boolean interrupted=false;Exception primary=null;
        try{
            long nextMarkerCheck=started;
            for(;;){
                child.descendants().forEach(descendants::add);boolean done=child.waitFor(100,TimeUnit.MILLISECONDS);bound(work,byteBound);
                if(rssBound>0){
                    long resident=resident(child.toHandle());for(ProcessHandle descendant:descendants)resident=Math.addExact(resident,resident(descendant));
                    long peak=((Number)call.getOrDefault("maximum_observed_rss_bytes",0L)).longValue();call.put("maximum_observed_rss_bytes",Math.max(peak,resident));
                    if(resident>rssBound)throw new IOException("Original HOMER resident-memory monitor exceeded maximum-upstream-memory-bytes");
                }
                if(System.nanoTime()>=nextMarkerCheck){markerCheck.run();nextMarkerCheck=System.nanoTime()+1000000000L;}
                if(done){verify.run();call.put("exit_code",child.exitValue());return child.exitValue();}
                if(System.nanoTime()-started>timeoutSeconds*1000000000L){call.put("timed_out",true);throw new IOException("Original HOMER exceeded timeout; provisional outputs are not completed results");}
            }
        }catch(InterruptedException failure){interrupted=true;primary=failure;throw failure;}
        catch(Exception failure){primary=failure;throw failure;}
        finally{
            call.put("seconds",(System.nanoTime()-started)/1e9);if(interrupted)Thread.interrupted();
            try{
                child.descendants().forEach(descendants::add);for(ProcessHandle handle:descendants)if(handle.isAlive())handle.destroyForcibly();if(child.isAlive())child.destroyForcibly();
                if(!child.waitFor(10,TimeUnit.SECONDS))throw new IOException("Owned HOMER process did not terminate: PID "+child.pid());
                long deadline=System.nanoTime()+5000000000L;boolean alive;
                do {alive=child.isAlive();for(ProcessHandle handle:descendants)if(!terminal(handle))alive=true;
                    if(alive)Thread.sleep(20);
                }while(alive&&System.nanoTime()<deadline);
                call.put("alive_after_cleanup",alive);call.put("owned_descendants_seen",descendants.stream().map(ProcessHandle::pid).toList());
                if(alive)throw new IOException("Owned HOMER descendant remains alive;inspect recorded PIDs");
            }catch(Exception cleanup){
                call.put("cleanup_error",cleanup.toString());if(primary==null)throw cleanup;primary.addSuppressed(cleanup);
            }finally{if(interrupted)Thread.currentThread().interrupt();}
        }
    }
    private static boolean terminal(ProcessHandle child)throws IOException{
        if(!child.isAlive())return true;
        if(!System.getProperty("os.name").equals("Linux"))return false;
        try{return terminalStatus(status(child));}
        catch(java.nio.file.AccessDeniedException denied){throw new IOException("Cannot verify owned-process terminal state;inspect recorded PIDs",denied);}
        catch(IOException ended){return !child.isAlive();}
    }
    static boolean terminalStatus(String text){
        for(String line:text.split("\n"))if(line.startsWith("State:")){
            String state=line.substring(6).trim();return state.startsWith("Z")||state.startsWith("X");
        }
        return false;
    }
    private static String status(ProcessHandle child)throws IOException{
        try(var input=Files.newInputStream(Path.of("/proc",Long.toString(child.pid()),"status"))){
            byte[] raw=input.readNBytes(65537);if(raw.length>65536)throw new Unavailable("Owned-process /proc status exceeds capacity");
            return new String(raw,java.nio.charset.StandardCharsets.US_ASCII);
        }
    }
    @FunctionalInterface interface StatusReader {String read(ProcessHandle child)throws IOException;}
    private static long resident(ProcessHandle child)throws IOException{return resident(child,HomerRuntime::status);}
    static long resident(ProcessHandle child,StatusReader source)throws IOException{return resident(child,source,HomerRuntime::memoryStat);}
    static long resident(ProcessHandle child,StatusReader source,StatusReader statSource)throws IOException{
        Unavailable last=null;
        for(int attempt=0;attempt<6;attempt++){
            if(!child.isAlive())return 0;
            String text=null;
            try{text=source.read(child);}
            catch(java.nio.file.AccessDeniedException denied){throw new Unavailable("Operating environment forbids required owned-process resident-memory monitoring",denied);}
            catch(IOException transientRead){
                // procfs may report ESRCH from read(), not just ENOENT from open(),
                // when a short-lived helper vanishes between those operations.
                if(!child.isAlive())return 0;
                last=new Unavailable("Owned-process memory-status read failed for PID "+child.pid()+";inspect the retained cause",transientRead);
            }
            if(text!=null){
                if(!child.isAlive())return 0;
                try{return residentStatus(text);}
                catch(Unavailable transientState){
                    if(!transientState.getMessage().contains("nonterminal"))throw transientState;
                    try{if(exitingWithoutAddressSpace(statSource.read(child)))return 0;}
                    catch(java.nio.file.AccessDeniedException denied){throw new Unavailable("Operating environment forbids required owned-process memory-stat monitoring",denied);}
                    catch(IOException changed){if(!child.isAlive())return 0;if(changed instanceof Unavailable)throw changed;}
                    last=transientState;
                }
            }
            java.util.concurrent.locks.LockSupport.parkNanos(5000000L);
        }
        if(!child.isAlive())return 0;throw last==null?new Unavailable("Owned-process memory monitor unavailable"):last;
    }
    private static String memoryStat(ProcessHandle child)throws IOException{
        try(var input=Files.newInputStream(Path.of("/proc",Long.toString(child.pid()),"stat"))){byte[] raw=input.readNBytes(65537);if(raw.length>65536)throw new Unavailable("Owned-process memory stat exceeds capacity");return new String(raw,java.nio.charset.StandardCharsets.US_ASCII);}
    }
    static boolean exitingWithoutAddressSpace(String text)throws Unavailable{
        // Linux exit_mm removes task->mm before exit reaches Z/X. Require the
        // PF_EXITING flag and zero stat vsize/RSS, not merely a missing VmRSS.
        try{int close=text.lastIndexOf(')');if(close<1)throw new NumberFormatException();String[] f=text.substring(close+1).trim().split("\\s+");if(f.length<22)throw new NumberFormatException();long flags=Long.parseLong(f[6]),virtual=Long.parseLong(f[20]),rss=Long.parseLong(f[21]);if(flags<0||virtual<0||rss<0)throw new NumberFormatException();return (flags&4)!=0&&virtual==0&&rss==0;}
        catch(NumberFormatException failure){throw new Unavailable("Malformed owned-process memory stat",failure);}
    }
    static long residentStatus(String text)throws Unavailable{
        String[] lines=text.split("\n");
        if(terminalStatus(text))return 0;
        for(String line:lines)if(line.startsWith("VmRSS:")){
            String[] fields=line.trim().split("\\s+");if(fields.length!=3||!fields[2].equals("kB"))throw new Unavailable("Owned-process /proc memory units differ");
            try{long kb=Long.parseLong(fields[1]);if(kb<0)throw new NumberFormatException();return Math.multiplyExact(kb,1024);}
            catch(NumberFormatException|ArithmeticException failure){throw new Unavailable("Invalid owned-process resident-memory field",failure);}
        }
        throw new Unavailable("Owned-process resident-memory monitoring is unavailable for a nonterminal task on this Linux host");
    }
    static void bound(Path root,long maximum)throws IOException{
        long[] bytes={0};int[] count={0};
        Files.walkFileTree(root,new SimpleFileVisitor<>(){
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException{
                if(++count[0]>4096||Files.isSymbolicLink(path))throw new IOException("HOMER staged member-count/link bound exceeded");return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException{
                if(!attrs.isRegularFile()||++count[0]>4096)throw new IOException("HOMER output has nonregular/linked or excessive members");
                bytes[0]=Math.addExact(bytes[0],attrs.size());if(bytes[0]>maximum)throw new IOException("HOMER staged/output workspace byte bound exceeded");return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path path,IOException failure)throws IOException{
                if(failure instanceof NoSuchFileException)return FileVisitResult.CONTINUE;throw failure;
            }
        });
    }
    static String recovery(){return "Required action: use Linux x86_64 Java and a same-host admitted original HOMER core. On Windows use Linux Java inside WSL. Where permitted run python3 tools/literature/install_homer.py --profile linux-x86_64-5.1 --home NEW_PRIVATE_HOME --cache ARCHIVE_CACHE --report NEW_REPORT --allow-install, choosing linux-x86_64-4.11 or linux-x86_64-4.11.1 explicitly when needed. Add --allow-download only where permitted, or supply --archive-dir OFFLINE_ARCHIVE_DIRECTORY. Missing, forbidden, unsupported or changed dependencies must SKIP the affected analysis with the named cause; analysis never installs or substitutes versions. Preserve changed homes and partial outputs, correct the cause and use NEW paths. Official HTTP content pins are not independently authenticated signatures. Core runtime admission does not admit genome/Perl annotation, peak, coverage or RNA workflows. Native homer-scan is a separate method, not an enrichment/discovery substitute. Cell Ranger remains excluded.";}
}
