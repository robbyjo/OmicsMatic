/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.jlinalg.cli.FollowupSupport.hash;

/** Exact private original Beagle/JVM boundary; analysis never installs software. */
final class BeagleRuntime {
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message,Exception cause){super(message,cause);}
    }
    private final Path home,jar,javaExecutable;
    private final String family;
    private final Map<Path,String> hashes=new LinkedHashMap<>();
    private final Map<String,Object> session;

    BeagleRuntime(Path supplied,String profile,Path javaExecutable,Path directory,Map<String,Object> session)throws Exception {
        home=supplied.toAbsolutePath().normalize();this.javaExecutable=javaExecutable.toAbsolutePath().normalize();this.session=session;
        try {
            byte[] raw;try(var input=BeagleRuntime.class.getResourceAsStream("/org/jlinalg/omics/beagle-runtime-lock.json")){if(input==null)throw new IOException("Bundled Beagle identity missing");raw=input.readAllBytes();}
            if(!(SimpleJson.parse(new String(raw,StandardCharsets.UTF_8)) instanceof Map<?,?> lock)||!(lock.get("profiles") instanceof Map<?,?> profiles)||!(profiles.get(profile) instanceof Map<?,?> selected)||!(lock.get("files") instanceof Map<?,?> files)||files.size()!=5)throw new IOException("Unknown/malformed original Beagle profile");
            family=String.valueOf(selected.get("family"));jar=home.resolve(String.valueOf(selected.get("jar")));
            if(!home.equals(home.toRealPath())||Files.isSymbolicLink(home))throw new IOException("Linked Beagle home/ancestor is unsupported;select a real private path");
            Path marker=member(".jlinalg-beagle.json");if(Files.size(marker)>65536)throw new IOException("Beagle marker exceeds64KiB");
            String lockSha=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw));
            if(!(SimpleJson.parse(Files.readString(marker)) instanceof Map<?,?> metadata)||!lockSha.equals(metadata.get("runtime_lock_sha256"))||!lock.get("profile").equals(metadata.get("profile"))||!files.equals(metadata.get("files")))throw new IOException("Beagle marker differs from bundled identity");
            var observed=new HashSet<String>();try(var entries=Files.list(home)){var iterator=entries.iterator();int count=0;while(iterator.hasNext()){Path p=iterator.next();if(++count>6||Files.isSymbolicLink(p)||!Files.isRegularFile(p))throw new IOException("Extra/linked Beagle runtime member");if(!p.equals(marker))observed.add(p.getFileName().toString());}}
            if(!observed.equals(files.keySet()))throw new IOException("Beagle runtime inventory differs");
            for(var entry:files.entrySet()){Path path=member(String.valueOf(entry.getKey()));String expected=String.valueOf(entry.getValue());if(!expected.matches("[a-f0-9]{64}")||!hash(path).equals(expected))throw new IOException("Original Beagle runtime changed: "+entry.getKey());hashes.put(path,expected);}
            hashes.put(marker,hash(marker));
            if(!Files.isRegularFile(this.javaExecutable))throw new IOException("Java executable unavailable");hashes.put(this.javaExecutable,hash(this.javaExecutable));
            session.put("runtime_home",home.toString());session.put("runtime_profile",lock.get("profile"));session.put("runtime_lock_sha256",lockSha);session.put("selected_profile",profile);session.put("upstream_family",family);session.put("original_jar_sha256",hash(jar));session.put("java_executable_sha256",hash(this.javaExecutable));
            execute(List.of(this.javaExecutable.toString(),"-version"),directory,"java-version",30);
            String version=Files.readString(directory.resolve("java-version.stdout"))+Files.readString(directory.resolve("java-version.stderr"));var matcher=java.util.regex.Pattern.compile("version\\s+\"(?:1\\.)?(\\d+)").matcher(version);
            if(!matcher.find()||Integer.parseInt(matcher.group(1))<8)throw new IOException("Original Beagle requires a permitted Java8+ runtime");session.put("java_version_text",version.strip());
            execute(base(128,5),directory,"version",30);
            if(!Files.readString(directory.resolve("version.stdout")).lines().findFirst().orElse("").equals(selected.get("version_line"))||Files.size(directory.resolve("version.stderr"))!=0)throw new IOException("Original Beagle usage/version probe differs");
            session.put("upstream_version",selected.get("version_line"));
        }catch(IOException|IllegalArgumentException failure){throw new Unavailable("Original Beagle dependency unavailable or changed: "+failure.getMessage(),failure);}
    }
    String family(){return family;}
    List<String> base(int memoryMiB,int stackMiB){return new ArrayList<>(List.of(javaExecutable.toString(),"-Dfile.encoding=UTF-8","-Duser.language=en","-Duser.country=US","-Xss"+stackMiB+"m","-Xmx"+memoryMiB+"m","-jar",jar.toString()));}
    private Path member(String name)throws IOException {
        Path p=home.resolve(name).normalize();if(!p.startsWith(home)||!Files.isRegularFile(p)||Files.isSymbolicLink(p)||!p.toRealPath().startsWith(home.toRealPath()))throw new IOException("Missing/linked/escaping Beagle member: "+name);return p;
    }
    void verify()throws Unavailable {
        try {
            if(!home.equals(home.toRealPath()))throw new IOException("Beagle home acquired a link");
            var observed=new HashSet<Path>();try(var entries=Files.list(home)){var iterator=entries.iterator();int count=0;while(iterator.hasNext()){Path p=iterator.next();if(++count>6||Files.isSymbolicLink(p)||!Files.isRegularFile(p))throw new IOException("Runtime inventory changed");observed.add(p);}}
            var expected=new HashSet<>(hashes.keySet());expected.remove(javaExecutable);if(!observed.equals(expected))throw new IOException("Runtime file family changed");
            for(var entry:hashes.entrySet())if(!Files.isRegularFile(entry.getKey())||!hash(entry.getKey()).equals(entry.getValue()))throw new IOException("Runtime/Java file changed: "+entry.getKey());
        }catch(IOException failure){throw new Unavailable(failure.getMessage(),failure);}
    }
    void execute(List<String> command,Path directory,String stage,int timeout)throws Exception {
        if(!stage.matches("[A-Za-z0-9-]+")||timeout<1||timeout>86400)throw new IllegalArgumentException("Invalid Beagle process stage/timeout");
        verify();session.put(stage+"_command",List.copyOf(command));Path stdout=directory.resolve(stage+".stdout"),stderr=directory.resolve(stage+".stderr");
        var builder=new ProcessBuilder(command).directory(directory.toFile()).redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
        for(String name:List.of("JAVA_TOOL_OPTIONS","_JAVA_OPTIONS","JDK_JAVA_OPTIONS","CLASSPATH"))builder.environment().remove(name);
        builder.environment().put("LC_ALL","C");Process child;
        try{child=builder.start();}catch(IOException failure){throw new Unavailable("Cannot launch original Beagle/JVM: "+failure.getMessage(),failure);}
        session.put(stage+"_owned_pid",child.pid());
        boolean timedOut;
        try{timedOut=!child.waitFor(timeout,TimeUnit.SECONDS);if(timedOut)terminate(child);}
        catch(InterruptedException failure){try{terminate(child);}finally{Thread.currentThread().interrupt();}throw failure;}
        session.put(stage+"_exit_code",child.exitValue());session.put(stage+"_stdout_sha256",hash(stdout));session.put(stage+"_stderr_sha256",hash(stderr));verify();
        if(timedOut){session.put(stage+"_timed_out",true);throw new IOException("Original Beagle "+stage+" exceeded timeout; owned process terminated");}
        if(child.exitValue()!=0)throw new IOException("Original Beagle "+stage+" failed with exit "+child.exitValue()+";inspect retained stdout/stderr and upstream log");
    }
    private static void terminate(Process child)throws Exception {for(var handle:child.descendants().toList())if(handle.isAlive())handle.destroyForcibly();child.destroyForcibly();if(!child.waitFor(30,TimeUnit.SECONDS))throw new IOException("Owned Beagle JVM did not terminate");}
    static String setup(){return " Where permitted, run python tools/literature/install_beagle.py --home NEW_PRIVATE_HOME --java JAVA_EXECUTABLE --allow-install; add --allow-download only for permitted missing official downloads. Supply that sealed home with --home or JLINALG_BEAGLE_HOME. If software/Java is unavailable, changed or forbidden, skip this analysis (exit 3). Preserve changed homes/caches and use a new path; never re-seal changed binaries.";}
}
