/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Exact original APBS3.5.0 Windows profile; explicit installation is separate. */
final class ApbsRuntime {
    private ApbsRuntime() { }
    private static final String MARKER=".jlinalg-apbs.json";
    private record Lock(Map<?,?> data,String sha256) { }
    private static final Lock LOCK=loadLock();
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String text){super(text);}
        Unavailable(String text,Throwable cause){super(text,cause);}
    }
    record Context(Path home,Path executable,String markerSha256,Map<String,String> files) {
        Context { files=Map.copyOf(files); }
        void verify()throws Unavailable {
            Context current=open(home);
            if(!current.markerSha256.equals(markerSha256)||!current.files.equals(files))throw new Unavailable("Original APBS runtime changed during analysis;results cannot be admitted");
        }
    }
    private static Lock loadLock() {
        try(var input=ApbsRuntime.class.getResourceAsStream("/org/jlinalg/omics/apbs-runtime-lock.json")) {
            if(input==null)throw new IOException("Missing APBS runtime lock");byte[] raw=input.readNBytes(1048577);if(raw.length>1048576)throw new IOException("Oversized APBS lock");
            Object value=SimpleJson.parse(new String(raw,StandardCharsets.UTF_8));if(!(value instanceof Map<?,?> map))throw new IOException("Invalid APBS lock");return new Lock(map,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
    static Context open(Path supplied)throws Unavailable {
        try {
            if(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch").toLowerCase(Locale.ROOT)))throw new Unavailable("This exact original APBS profile requires a Windows x86_64 host;the executable itself is32bit x86");
            Path home=supplied.toAbsolutePath().normalize();if(!Files.isDirectory(home)||Files.isSymbolicLink(home)||!home.toRealPath().equals(home))throw new Unavailable("APBS private home is missing,linked or aliased");
            Path marker=home.resolve(MARKER);Map<?,?> record=readMap(marker);String markerHash=FollowupSupport.hash(marker);
            for(String key:List.of("tool","version","profile","archive_sha256"))if(!LOCK.data.get(key).equals(record.get(key)))throw new Unavailable("APBS admission profile mismatch:"+key);
            if(!LOCK.sha256.equals(record.get("runtime_lock_sha256")))throw new Unavailable("APBS runtime lock differs from this project");
            if(!(LOCK.data.get("reference_files") instanceof Map<?,?> original)||!(record.get("files") instanceof Map<?,?> marked))throw new Unavailable("Invalid APBS member map");
            var expected=new LinkedHashMap<String,String>();
            for(var entry:original.entrySet())expected.put((String)entry.getKey(),(String)entry.getValue());expected.put((String)LOCK.data.get("license_file"),(String)LOCK.data.get("license_sha256"));
            if(!expected.equals(marked))throw new Unavailable("APBS marker member map differs from authenticated original files");
            for(var entry:expected.entrySet())if(!entry.getValue().equals(FollowupSupport.hash(member(home,entry.getKey()))))throw new Unavailable("APBS original member changed:"+entry.getKey());
            var found=new java.util.HashSet<String>();int count=0;
            try(var walk=Files.walk(home)) {
                var paths=walk.iterator();while(paths.hasNext()) {
                    Path path=paths.next();if(++count>4096||Files.isSymbolicLink(path)||!path.toRealPath().equals(path.toAbsolutePath().normalize()))throw new Unavailable("APBS home contains linked,aliased or excessive members");
                    if(Files.isRegularFile(path)&&!path.equals(marker))found.add(home.relativize(path).toString().replace('\\','/'));
                }
            }
            if(!found.equals(expected.keySet()))throw new Unavailable("APBS runtime contains missing or unlisted files");
            if(!markerHash.equals(FollowupSupport.hash(marker)))throw new Unavailable("APBS marker changed during verification");
            windowsRoot();return new Context(home,member(home,(String)LOCK.data.get("executable")),markerHash,expected);
        }catch(Unavailable failure){throw failure;}
        catch(Exception failure){throw new Unavailable("Original APBS dependency verification failed:"+failure.getMessage(),failure);}
    }
    private static Path member(Path home,String name)throws IOException {
        if(name.startsWith("/")||name.contains("\\")||name.contains(":")||java.util.Arrays.stream(name.split("/",-1)).anyMatch(p->p.isEmpty()||p.equals(".")||p.equals("..")))throw new IOException("Unsafe APBS member name");
        Path path=home.resolve(name).normalize();if(!path.startsWith(home)||!Files.isRegularFile(path)||Files.isSymbolicLink(path)||!path.toRealPath().equals(path))throw new IOException("Missing/linked APBS member:"+name);return path;
    }
    private static Map<?,?> readMap(Path path)throws IOException {
        if(!Files.isRegularFile(path)||Files.isSymbolicLink(path)||Files.size(path)>1048576)throw new IOException("Missing/oversized APBS metadata");Object parsed=SimpleJson.parse(Files.readString(path,StandardCharsets.UTF_8));if(!(parsed instanceof Map<?,?> map))throw new IOException("Require an APBS JSON object");return map;
    }
    private static Path windowsRoot()throws Unavailable {
        String text=System.getenv("SystemRoot");if(text==null)text=System.getenv("windir");if(text==null||!Files.isRegularFile(Path.of(text).resolve("System32/kernel32.dll")))throw new Unavailable("Windows SystemRoot/System32 host dependency is unavailable");return Path.of(text);
    }
    static void provenance(Context runtime,Map<String,Object> session) {
        session.put("upstream_tool","APBS");session.put("upstream_version",LOCK.data.get("version"));session.put("runtime_profile",LOCK.data.get("profile"));session.put("runtime_lock_sha256",LOCK.sha256);session.put("runtime_marker_sha256",runtime.markerSha256);session.put("runtime_home",runtime.home.toString());session.put("runtime_members_sha256",runtime.files);session.put("runtime_scope",LOCK.data.get("scope"));session.put("license_url",LOCK.data.get("license_url"));
    }
    static int execute(Context runtime,List<String> arguments,Path directory,String name,int seconds,Map<String,Object> session)throws Exception {
        var command=new ArrayList<String>();command.add(runtime.executable.toString());command.addAll(arguments);
        var builder=new ProcessBuilder(command).directory(directory.toFile()).redirectOutput(directory.resolve(name+".stdout").toFile()).redirectError(directory.resolve(name+".stderr").toFile());
        Path windows=windowsRoot();var environment=builder.environment();for(String key:new ArrayList<>(environment.keySet())){String upper=key.toUpperCase(Locale.ROOT);if(upper.startsWith("APBS")||upper.startsWith("PYTHON")||upper.startsWith("LD_")||upper.startsWith("DYLD_")||Set.of("CYGWIN","MSYSTEM","LIBRARY_PATH").contains(upper))environment.remove(key);}
        environment.put("PATH",runtime.home.resolve("bin")+";"+windows.resolve("System32")+";"+windows);environment.put("LC_ALL","C");for(String key:List.of("OMP_NUM_THREADS","OPENBLAS_NUM_THREADS","MKL_NUM_THREADS"))environment.put(key,"1");
        var call=new LinkedHashMap<String,Object>();call.put("command",command);call.put("timeout_seconds",seconds);call.put("stdout",name+".stdout");call.put("stderr",name+".stderr");var processes=new ArrayList<Object>();if(session.get("processes") instanceof List<?> list)processes.addAll(list);processes.add(call);session.put("processes",processes);
        long started=System.nanoTime();Process process;
        try{process=builder.start();}catch(IOException failure){call.put("launch_error",failure.toString());throw new Unavailable("Original APBS cannot start;inspect its original runtime/dependency closure",failure);}
        call.put("pid",process.pid());
        try {
            if(!process.waitFor(seconds,TimeUnit.SECONDS)){call.put("timed_out",true);throw new IOException("Original APBS exceeded --timeout;no completed result is admitted");}
            int code=process.exitValue();call.put("exit_code",code);if(code==-1073741515||code==-1073741701)throw new Unavailable("Windows could not load original APBS or its DLL dependency (exit"+code+")");return code;
        }finally {
            call.put("seconds",(System.nanoTime()-started)/1e9);
            if(process.isAlive()){call.put("termination_requested",true);process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();if(!process.waitFor(10,TimeUnit.SECONDS))throw new IOException("Owned APBS process could not terminate;inspect PID"+process.pid());}
            call.put("alive_after_cleanup",process.isAlive());
        }
    }
    static void version(Context runtime,Path directory,int timeout,Map<String,Object> session)throws Exception {
        int code=execute(runtime,List.of("--version"),directory,"version",Math.min(timeout,30),session);String banner=Files.readString(directory.resolve("version.stdout"))+Files.readString(directory.resolve("version.stderr"));
        if(code!=13||!java.util.regex.Pattern.compile("\\bAPBS\\s+3\\.5\\.0\\b").matcher(banner).find())throw new Unavailable("APBS informational probe did not match original3.5.0/exit13 protocol");
    }
    static String recovery(){return "Required action: on Windows x86_64 run python tools/literature/install_apbs.py --home NEW_PRIVATE_HOME --cache ARCHIVE_CACHE --allow-install (add --allow-download only when permitted),then supply --home NEW_PRIVATE_HOME. If the exact admitted original runtime is absent,changed or installation is forbidden,SKIP the APBS analysis. Preserve failed bundles and changed homes;review input charges/radii,grid coverage,parameters and original logs before rerunning to a new output directory. Native electrostatic-grid can inspect an existing complete finite DX field using Java alone. This profile does not admit alternate operating systems/builds or run PDB2PQR automatically.";}
}
