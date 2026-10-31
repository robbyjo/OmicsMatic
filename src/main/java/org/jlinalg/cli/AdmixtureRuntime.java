/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Hash-bound original Linux binaries; installation is a separate explicit action. */
final class AdmixtureRuntime {
    private AdmixtureRuntime() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String text){super(text);}
        Unavailable(String text,Throwable cause){super(text,cause);}
    }
    private record Lock(Map<?,?> data,String hash) { }
    private static final Lock LOCK=load();
    private static final String MARKER=".jlinalg-admixture.json";
    record Context(Path home,String profile,String version,String markerHash,Map<String,String> files,boolean emulated) {
        Context {files=Map.copyOf(files);}
        void verify()throws IOException {try{Context c=open(home,version);if(!profile.equals(c.profile)||!markerHash.equals(c.markerHash)||!files.equals(c.files))throw new IOException("ADMIXTURE admitted runtime changed during analysis");}catch(Unavailable e){throw new IOException("ADMIXTURE dependency changed during analysis; no result is admitted",e);}}
    }
    private static Lock load(){try(var in=AdmixtureRuntime.class.getResourceAsStream("/org/jlinalg/omics/admixture-runtime-lock.json")){if(in==null)throw new IOException("Missing bundled ADMIXTURE lock");byte[] raw=in.readNBytes(1048577);if(raw.length>1048576)throw new IOException("Oversized ADMIXTURE lock");return new Lock(AdmixtureInputs.map(SimpleJson.parse(new String(raw,StandardCharsets.UTF_8))),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    static Context open(Path supplied,String version)throws Unavailable {
        try {
            String arch=System.getProperty("os.arch").toLowerCase(Locale.ROOT);if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(arch))throw new IOException("This original runtime needs Linux x86_64; on Windows use Linux Java inside WSL");
            Path home=supplied.toAbsolutePath().normalize();if(!Files.isDirectory(home,LinkOption.NOFOLLOW_LINKS)||!home.toRealPath().equals(home))throw new IOException("Missing or linked ADMIXTURE runtime home");
            Path marker=home.resolve(MARKER);byte[] raw=AdmixtureInputs.raw(marker,1048576);String markerHash=AdmixtureInputs.digest(raw);Map<?,?> meta=AdmixtureInputs.decode(raw);String profile=AdmixtureInputs.text(meta,"profile");Map<?,?> profiles=AdmixtureInputs.map(LOCK.data.get("profiles")),pin=AdmixtureInputs.map(profiles.get(profile));
            if(!LOCK.hash.equals(meta.get("lock_sha256"))||!version.equals(pin.get("version"))||!version.equals(meta.get("version"))||AdmixtureInputs.whole(meta,"schema_version",1,1)!=1)throw new IOException("ADMIXTURE requested version,profile or bundled lock differs; historical versions are never silently upgraded");
            Map<?,?> host=AdmixtureInputs.map(meta.get("host")),health=AdmixtureInputs.map(meta.get("health"));if(!"Linux".equals(host.get("os"))||!"x86_64".equals(host.get("architecture"))||!System.getProperty("os.version").equals(host.get("kernel"))||!"passed-original-K1-likelihood-and-matrix-health".equals(health.get("status")))throw new IOException("Runtime host/kernel or actual scientific health admission differs; help output is insufficient");
            Map<?,?> members=AdmixtureInputs.map(pin.get("members")),recorded=AdmixtureInputs.map(meta.get("files"));if(!recorded.keySet().equals(members.keySet()))throw new IOException("Runtime member inventory differs");var hashes=new TreeMap<String,String>();
            for(var e:members.entrySet()){String name=e.getKey().toString();if(!name.matches("(?:bin|share)/[A-Za-z0-9._-]+"))throw new IOException("Unsafe locked runtime member");Map<?,?> entry=AdmixtureInputs.map(e.getValue());Path p=home.resolve(name);AdmixtureInputs.regular(p,100000000);if(!p.toRealPath().equals(p)||Files.size(p)!=((Number)entry.get("bytes")).longValue())throw new IOException("Runtime member path/size differs: "+name);String h=FollowupSupport.hash(p);if(!h.equals(entry.get("sha256"))||!h.equals(recorded.get(name)))throw new IOException("Runtime member changed: "+name);if(name.startsWith("bin/")&&!Files.isExecutable(p))throw new IOException("Runtime executable permission unavailable: "+name);hashes.put(name,h);}
            var found=new HashSet<String>();int count=0;try(var walk=Files.walk(home)){for(var it=walk.iterator();it.hasNext();){Path p=it.next();if(++count>32||Files.isSymbolicLink(p))throw new IOException("Unexpected/linked runtime family");if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))found.add(home.relativize(p).toString().replace('\\','/'));else if(!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))throw new IOException("Nonregular runtime member");}}
            var expected=new HashSet<>(hashes.keySet());expected.add(MARKER);if(!found.equals(expected)||!markerHash.equals(FollowupSupport.hash(marker)))throw new IOException("Runtime contains changed,missing or unmanaged files");return new Context(home,profile,version,markerHash,hashes,Boolean.TRUE.equals(pin.get("emulated")));
        }catch(Exception e){throw new Unavailable("Original ADMIXTURE dependency unavailable: "+e.getMessage(),e);}
    }
    static void provenance(Context r,Map<String,Object> session){session.put("runtime_home",r.home.toString());session.put("runtime_profile",r.profile);session.put("runtime_marker_sha256",r.markerHash);session.put("runtime_lock_sha256",LOCK.hash);session.put("runtime_files",r.files);session.put("legacy_emulation",r.emulated);}
    static int execute(Context r,List<String> arguments,Path work,int threads,int timeout,long outputBytes,Map<String,Object> session)throws Exception {
        var cmd=new ArrayList<String>();if(r.emulated)cmd.addAll(List.of(r.home.resolve("bin/qemu-x86_64-static").toString(),"-cpu","qemu64"));cmd.add(r.home.resolve("bin/admixture").toString());cmd.addAll(arguments);
        Path home=work.resolve("runtime-home"),temp=work.resolve("runtime-tmp");Files.createDirectory(home);Files.createDirectory(temp);ProcessBuilder builder=new ProcessBuilder(cmd).directory(work.toFile()).redirectOutput(work.resolve("fit.stdout").toFile()).redirectError(work.resolve("fit.stderr").toFile());var env=builder.environment();env.clear();env.put("PATH","/usr/bin:/bin");env.put("HOME",home.toString());env.put("TMPDIR",temp.toString());env.put("LANG","C");env.put("LC_ALL","C");for(String k:List.of("OMP_NUM_THREADS","OPENBLAS_NUM_THREADS","MKL_NUM_THREADS"))env.put(k,Integer.toString(threads));
        var call=new LinkedHashMap<String,Object>();call.put("command",cmd);call.put("timeout_seconds",timeout);session.put("process",call);long start=System.nanoTime();Process child;
        try{child=builder.start();}catch(IOException e){call.put("launch_error",e.toString());throw new Unavailable("Verified ADMIXTURE executable could not start on this host/mount",e);}
        call.put("pid",child.pid());var descendants=new LinkedHashSet<ProcessHandle>();boolean interrupted=false;
        try {
            for(;;){child.descendants().forEach(descendants::add);boolean done=child.waitFor(100,TimeUnit.MILLISECONDS);long size=0;int files=0;try(var walk=Files.walk(work)){for(var it=walk.iterator();it.hasNext();){Path p=it.next();if(++files>512)throw new IOException("ADMIXTURE work file-count bound exceeded");if(Files.isRegularFile(p)){size=Math.addExact(size,Files.size(p));if(size>outputBytes)throw new IOException("ADMIXTURE staged/output byte bound exceeded");}}}if(done){call.put("exit_code",child.exitValue());return child.exitValue();}if(System.nanoTime()-start>timeout*1000000000L){call.put("timed_out",true);throw new IOException("Original ADMIXTURE exceeded timeout; incomplete outputs are not admitted");}}
        }catch(InterruptedException e){interrupted=true;throw e;}
        finally {
            call.put("seconds",(System.nanoTime()-start)/1e9);if(interrupted)Thread.interrupted();try{child.descendants().forEach(descendants::add);for(ProcessHandle handle:descendants)if(handle.isAlive())handle.destroyForcibly();if(child.isAlive())child.destroyForcibly();if(!child.waitFor(10,TimeUnit.SECONDS))throw new IOException("Owned ADMIXTURE process did not terminate: PID "+child.pid());boolean alive=child.isAlive()||descendants.stream().anyMatch(ProcessHandle::isAlive);call.put("alive_after_cleanup",alive);call.put("owned_descendants_seen",descendants.stream().map(ProcessHandle::pid).toList());if(alive)throw new IOException("Owned ADMIXTURE descendant remains alive; inspect recorded PIDs");}finally{if(interrupted)Thread.currentThread().interrupt();}
        }
    }
    static String recovery(){return "Required action: use Linux x86_64 Java and an admitted private original ADMIXTURE runtime. On Windows run Linux Java inside WSL. Where installation is permitted, run python3 tools/literature/install_admixture.py --profile linux-x86_64-1.4.0 --home NEW_PRIVATE_HOME --cache ARCHIVE_CACHE --report NEW_REPORT --allow-install; add --allow-download only when permitted. Legacy1.3.0 uses the explicit linux-x86_64-1.3.0-qemu8.2.2 profile and an additional pinned private QEMU dependency; it never changes host kernel settings. Keep the requested original version explicit. If installation/execution is forbidden,unavailable,unsupported or changed, SKIP the dependent analysis and preserve its named cause. Preserve changed homes and failed outputs; correct inputs/runtime or permissions and rerun to a new output directory. Analysis never installs software. Native AncestryMixtureLikelihood needs no upstream runtime.";}
}
