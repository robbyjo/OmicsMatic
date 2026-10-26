/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Authenticated original FIMO runtime; installation is always explicit and separate. */
final class FimoRuntime {
    private FimoRuntime() { }
    private static final String MARKER = ".jlinalg-fimo.json", DECLARATION = "provenance/intended-use.json";
    private static final List<String> REMOVED = List.of("LD_PRELOAD","LD_LIBRARY_PATH","CYGWIN","MSYSTEM","LIBRARY_PATH","MEME_ETC_DIR","MEME_BIN_DIR","MEME_DIR");
    private static final Lock LOCK = loadLock();
    static final class Unavailable extends IOException {
        private static final long serialVersionUID = 1L;
        Unavailable(String message) { super(message); }
        Unavailable(String message, Throwable cause) { super(message,cause); }
    }
    private record Lock(Map<?,?> data, String sha256) { }
    record Context(Path home, Path executable, String markerSha256, Map<String,String> files) {
        Context { files=Map.copyOf(files); }
        void verify() throws Unavailable {
            Context now = open(home);
            if (!now.markerSha256.equals(markerSha256) || !now.files.equals(files)) throw new Unavailable("Original FIMO runtime changed during analysis; outputs cannot be admitted");
        }
    }
    private static Lock loadLock() {
        try (var in=FimoRuntime.class.getResourceAsStream("/org/jlinalg/omics/fimo-runtime-lock.json")) {
            if (in==null) throw new IOException("Missing bundled FIMO lock");
            byte[] bytes=in.readNBytes(131073);if(bytes.length>131072)throw new IOException("Bundled FIMO lock exceeds limit");
            Object value=SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString());
            if(!(value instanceof Map<?,?> map))throw new IOException("Invalid bundled FIMO lock");
            return new Lock(map,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch(Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
    static Context open(Path supplied) throws Unavailable {
        try {
            if (!System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win") || !Set.of("amd64","x86_64").contains(System.getProperty("os.arch").toLowerCase(java.util.Locale.ROOT)))
                throw new Unavailable("This authenticated original FIMO profile is only validated on Windows x86_64");
            Path home=supplied.toAbsolutePath().normalize();
            if(!Files.isDirectory(home) || Files.isSymbolicLink(home))throw new Unavailable("Private FIMO home is absent or symbolic: "+home);
            Path marker=home.resolve(MARKER);Map<?,?> record=readMap(marker,1_048_576);String markerHash=FollowupSupport.hash(marker);
            for(String field:List.of("tool","version","profile","eligibility","executable"))
                if(!LOCK.data.get(field).equals(record.get(field)))throw new Unavailable("FIMO profile mismatch: "+field);
            if(!LOCK.sha256.equals(record.get("runtime_lock_sha256")))throw new Unavailable("FIMO runtime lock differs from the current project");
            if(!(record.get("files") instanceof Map<?,?> files) || !(LOCK.data.get("reference_files") instanceof Map<?,?> expected))throw new Unavailable("FIMO runtime file map is invalid");
            var names=new java.util.HashSet<Object>(expected.keySet());names.add(DECLARATION);
            if(!files.keySet().equals(names))throw new Unavailable("FIMO runtime member map is incomplete or contains unexpected entries");
            for(var entry:expected.entrySet())if(!entry.getValue().equals(files.get(entry.getKey())))throw new Unavailable("Authenticated FIMO runtime member hash changed: "+entry.getKey());
            var verified=new LinkedHashMap<String,String>();
            for(var entry:files.entrySet()) {
                if(!(entry.getKey() instanceof String name) || !(entry.getValue() instanceof String digest) || !digest.matches("[0-9a-f]{64}"))throw new Unavailable("Invalid FIMO member hash entry");
                Path member=member(home,name);
                if(!digest.equals(FollowupSupport.hash(member)))throw new Unavailable("FIMO runtime integrity failed: "+name);
                verified.put(name,digest);
            }
            Map<?,?> declaration=readMap(member(home,DECLARATION),65536);
            if(!LOCK.data.get("eligibility").equals(declaration.get("intended_use")))throw new Unavailable("FIMO educational/research/nonprofit intended-use declaration is missing");
            var found=new java.util.HashSet<String>();int paths=0;
            try(var walk=Files.walk(home)) {
                var iterator=walk.iterator();while(iterator.hasNext()) {
                    Path path=iterator.next();if(++paths>128 || Files.isSymbolicLink(path))throw new Unavailable("FIMO runtime contains symbolic or excessive members");
                    if(Files.isRegularFile(path) && !path.equals(marker))found.add(home.relativize(path).toString().replace('\\','/'));
                }
            }
            if(!found.equals(verified.keySet()))throw new Unavailable("FIMO runtime contains unlisted files; its dependency closure is not admitted");
            if(!markerHash.equals(FollowupSupport.hash(marker)))throw new Unavailable("FIMO marker changed during integrity verification");
            return new Context(home,member(home,(String)LOCK.data.get("executable")),markerHash,verified);
        } catch(Unavailable failure) { throw failure; }
        catch(Exception failure) { throw new Unavailable("Original FIMO runtime is unavailable or failed verification: "+failure.getMessage(),failure); }
    }
    private static Path member(Path home,String name) throws IOException {
        if(!name.matches("[A-Za-z0-9._/-]+") || name.startsWith("/") || java.util.Arrays.stream(name.split("/",-1)).anyMatch(s->s.isEmpty() || s.equals(".") || s.equals("..")))throw new IOException("Unsafe FIMO member path");
        Path path=home;
        for(String part:name.split("/")) { path=path.resolve(part);if(Files.isSymbolicLink(path))throw new IOException("Symbolic FIMO member is unsupported"); }
        if(!Files.isRegularFile(path) || !path.toRealPath().startsWith(home.toRealPath()))throw new IOException("Missing or escaping FIMO member: "+name);
        return path;
    }
    private static Map<?,?> readMap(Path path,int maximum) throws IOException {
        if(!Files.isRegularFile(path) || Files.size(path)>maximum)throw new IOException("Missing or oversized FIMO metadata: "+path);
        Object value=SimpleJson.parse(Files.readString(path,StandardCharsets.UTF_8));
        if(!(value instanceof Map<?,?> map))throw new IOException("Require a FIMO JSON object: "+path);return map;
    }
    static String path(Path path) throws IOException {
        String value=path.toAbsolutePath().normalize().toString().replace('\\','/');
        if(value.length()<3 || !Character.isLetter(value.charAt(0)) || !value.substring(1,3).equals(":/"))throw new IOException("This Cygwin FIMO profile requires local drive-letter paths; UNC paths are unsupported");
        return "/cygdrive/"+Character.toLowerCase(value.charAt(0))+"/"+value.substring(3);
    }
    static void provenance(Context runtime, Map<String,Object> session) {
        session.put("upstream_tool","FIMO");session.put("upstream_version","5.5.9");session.put("runtime_profile",LOCK.data.get("profile"));
        session.put("runtime_home",runtime.home.toString());session.put("runtime_marker_sha256",runtime.markerSha256);session.put("runtime_lock_sha256",LOCK.sha256);session.put("runtime_members_sha256",runtime.files);
        session.put("license_eligibility",LOCK.data.get("eligibility"));session.put("license_url",LOCK.data.get("license_url"));session.put("runtime_scope",LOCK.data.get("reference_scope"));
        session.put("host_boundary","Windows KERNEL32/ntdll and Cygwin path interpretation. PATH is restricted to the private runtime bin and Windows System32; LC_ALL=C.");
    }
    static int execute(Context runtime,List<String> arguments,Path directory,String name,int seconds,Map<String,Object> session) throws Exception {
        var command=new ArrayList<String>();command.add(runtime.executable.toString());command.addAll(arguments);
        var builder=new ProcessBuilder(command).directory(directory.toFile()).redirectOutput(directory.resolve(name+".stdout").toFile()).redirectError(directory.resolve(name+".stderr").toFile());
        String windows=System.getenv("SystemRoot");if(windows==null || !Files.isDirectory(Path.of(windows).resolve("System32")))throw new Unavailable("Windows SystemRoot/System32 host dependency is unavailable");
        var env=builder.environment();env.put("PATH",runtime.home.resolve("bin")+";"+Path.of(windows).resolve("System32"));env.put("LC_ALL","C");for(String key:REMOVED)env.remove(key);
        var call=new LinkedHashMap<String,Object>();call.put("command",command);call.put("timeout_seconds",seconds);call.put("stdout",name+".stdout");call.put("stderr",name+".stderr");
        Object existing=session.get("processes");List<Object> processes;
        if(existing instanceof List<?> list) { processes=new ArrayList<>(list); } else processes=new ArrayList<>();processes.add(call);session.put("processes",processes);
        long started=System.nanoTime();Process process;
        try { process=builder.start(); } catch(IOException failure) { call.put("launch_error",failure.toString());throw new Unavailable("Original FIMO process cannot start; inspect the runtime/dependency closure",failure); }
        call.put("pid",process.pid());
        try {
            if(!process.waitFor(seconds,TimeUnit.SECONDS)) { call.put("timed_out",true);throw new IOException("Original FIMO exceeded --timeout; no completed analysis is published"); }
            int code=process.exitValue();call.put("exit_code",code);
            if(code==-1073741515 || code==-1073741701)throw new Unavailable("Windows could not load the original FIMO runtime or DLL dependency (exit "+code+")");
            return code;
        } finally {
            call.put("seconds",(System.nanoTime()-started)/1e9);
            if(process.isAlive()) {
                call.put("termination_requested",true);process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();
                if(!process.waitFor(10,TimeUnit.SECONDS))throw new IOException("Original FIMO could not be terminated after failure; inspect owned process PID "+process.pid()+" and release its handles before rerunning");
            }
            call.put("alive_after_cleanup",process.isAlive());
        }
    }
    static String recovery() { return "Required action: where permitted, run python tools/literature/install_fimo.py --home NEW_PRIVATE_HOME --reference-home build/omics-fimo-5.5.9-gnurx-reference --cache build/literature-review/fimo-runtime --intended-use educational-research-nonprofit --allow-install (add --allow-download only when archive download is permitted), then supply --home NEW_PRIVATE_HOME. This installer admits the exact tested local Windows x86_64 reference build, retaining licenses and source/backend provenance; it does not build or validate an alternate Linux/WSL/Docker runtime. If the reference/profile is absent or installation is forbidden, SKIP the upstream analysis. Use native motif-scan only for its documented P-value subset. Preserve changed profiles, diagnostics and all failed attempts; correct the named cause and rerun to a new output directory. No implicit installation or commercial permission is inferred."; }
}
