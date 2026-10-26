/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.jlinalg.cli.FollowupSupport.*;

/** Verified private REGENIE process boundary; never installs from an analysis command. */
final class RegenieRuntime {
    static final String VERSION="4.1.3";
    private final Path home;
    private final Path executable;
    private final Map<Path,String> hashes=new LinkedHashMap<>();
    private final Map<String,Object> session;
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message,Exception cause){super(message,cause);}
    }
    RegenieRuntime(Path supplied,Path directory,Map<String,Object> session)throws Exception {
        home=supplied.toAbsolutePath().normalize();executable=home.resolve("bin/"+(windows()?"regenie.exe":"regenie"));this.session=session;
        try{
            Path marker=home.resolve(".jlinalg-regenie.json");Map<?,?> metadata=object(marker);
            if(!VERSION.equals(metadata.get("version"))||!(metadata.get("files") instanceof Map<?,?> files)||files.isEmpty())throw new IOException("Managed runtime marker lacks pinned version/file identities");
            String profile=String.valueOf(metadata.get("profile"));if(windows()&&!profile.equals("windows-cygwin-source")||!windows()&&!List.of("linux-x86_64","macos-x86_64").contains(profile))throw new IOException("Managed runtime profile differs from this host");
            for(var e:files.entrySet()){
                Path path=contained(home,String.valueOf(e.getKey()));String expected=String.valueOf(e.getValue());
                if(!expected.matches("[0-9a-f]{64}")||!Files.isRegularFile(path)||!expected.equals(hash(path)))throw new IOException("Runtime file integrity failed: "+path);hashes.put(path,expected);
            }
            if(!hashes.containsKey(executable)||windows()&&!hashes.containsKey(home.resolve("bin/cygpath.exe")))throw new IOException("Runtime executable/path converter is absent from managed file identities");
            hashes.put(marker,hash(marker));session.put("runtime_profile",profile);session.put("runtime_marker_sha256",hash(marker));session.put("executable_sha256",hash(executable));
            execute(List.of(executable.toString(),"--version"),directory,"version",60);
            if(!Files.readString(directory.resolve("version.log")).strip().equals("v"+VERSION))throw new IOException("Runtime version differs from REGENIE "+VERSION);
        }catch(IOException|IllegalArgumentException failure){throw new Unavailable("REGENIE dependency unavailable or changed: "+failure.getMessage(),failure);}
    }
    String executable(){return executable.toString();}
    String path(Path path,Path directory)throws Exception {
        if(!windows())return path.toAbsolutePath().normalize().toString();
        String name="path-"+((Number)session.getOrDefault("path_conversions",0)).intValue();session.put("path_conversions",((Number)session.getOrDefault("path_conversions",0)).intValue()+1);
        execute(List.of(home.resolve("bin/cygpath.exe").toString(),"-u","-a",path.toAbsolutePath().normalize().toString()),directory,name,30);
        String result=Files.readString(directory.resolve(name+".log")).strip();if(!result.startsWith("/")||result.contains("\n"))throw new IOException("Path conversion did not produce one absolute POSIX path");return result;
    }
    void execute(List<String> command,Path directory,String stage,int timeout)throws Exception {
        session.put(stage+"_command",List.copyOf(command));var builder=new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).redirectOutput(directory.resolve(stage+".log").toFile());
        var env=builder.environment();env.put("LC_ALL","C");env.put("OMP_NUM_THREADS","1");env.put("OPENBLAS_NUM_THREADS","1");
        if(windows()){
            String system=System.getenv("SystemRoot");if(system==null)throw new IOException("Windows SystemRoot is unavailable");
            env.put("PATH",home.resolve("bin")+java.io.File.pathSeparator+Path.of(system,"System32")+java.io.File.pathSeparator+system);
        }
        Process child=builder.start();try{
            if(!child.waitFor(timeout,TimeUnit.SECONDS)){child.descendants().forEach(ProcessHandle::destroyForcibly);child.destroyForcibly();child.waitFor(30,TimeUnit.SECONDS);throw new IOException("REGENIE stage "+stage+" timed out; inspect "+stage+".log");}
            session.put(stage+"_exit_code",child.exitValue());if(child.exitValue()!=0)throw new IOException("REGENIE stage "+stage+" exited "+child.exitValue()+"; inspect "+stage+".log");
        }catch(InterruptedException failure){child.descendants().forEach(ProcessHandle::destroyForcibly);child.destroyForcibly();Thread.currentThread().interrupt();throw failure;}
    }
    void verify()throws IOException {verify(hashes);}
    static void verify(Map<Path,String> hashes)throws IOException {for(var e:hashes.entrySet())if(!Files.isRegularFile(e.getKey())||!e.getValue().equals(hash(e.getKey())))throw new IOException("Input/runtime changed during analysis: "+e.getKey());}
    static Path contained(Path directory,String relative)throws IOException {
        Path path=directory.resolve(relative).normalize();if(Path.of(relative).isAbsolute()||!path.startsWith(directory)||path.equals(directory)||Files.exists(path)&&!path.toRealPath().startsWith(directory.toRealPath()))throw new IOException("Manifest path escapes its bundle: "+relative);return path;
    }
    static Map<?,?> object(Path path)throws IOException {
        if(!Files.isRegularFile(path)||Files.size(path)>64*1024*1024)throw new IOException("Missing/oversized JSON manifest: "+path);
        Object value=SimpleJson.parse(Files.readString(path));if(!(value instanceof Map<?,?> map))throw new IOException("Expected JSON object: "+path);return map;
    }
    static String json(Object value){
        if(value==null)return"null";if(value instanceof Number||value instanceof Boolean)return value.toString();
        if(value instanceof Map<?,?> map){var entries=new ArrayList<String>();for(var e:map.entrySet())entries.add(json(e.getKey().toString())+":"+json(e.getValue()));return"{"+String.join(",",entries)+"}";}
        if(value instanceof Iterable<?> iterable){var entries=new ArrayList<String>();for(Object v:iterable)entries.add(json(v));return"["+String.join(",",entries)+"]";}
        var b=new StringBuilder("\"");for(char c:value.toString().toCharArray())switch(c){case'"'->b.append("\\\"");case'\\'->b.append("\\\\");default->{if(c<32)b.append(String.format("\\u%04x",(int)c));else b.append(c);}}return b.append('"').toString();
    }
    static boolean windows(){return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");}
}
