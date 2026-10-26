/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import static org.jlinalg.cli.FollowupSupport.*;

/** Private original MAGMA application; upstream binaries/source are never included in this JAR. */
final class MagmaRuntime {
    private static final Map<String,String> FILES=Map.of("magma.exe","e05e3d963215a973064852ec33640118d65745884cc800a5119ca7cffe6cb859","manual_v1.10.pdf","2528587cb151177ab693aacc274bb5c528974e3ceee77f4cb6b60e2e9b4d4ffe","readme.txt","0ee7257e5e8e87642a853c63ba24a3b7a4c12b6690416d7914a3016f60d4419a","changelog.txt","cf2a5221bce26abf5ccf4553a525b815690965ee87a09739aea26c0cc48d89d9");
    static final class Unavailable extends IOException {private static final long serialVersionUID=1L;Unavailable(String message,Exception cause){super(message,cause);}}
    private final Path home;private final Map<Path,String> protectedFiles=new LinkedHashMap<>();private final Map<String,Object> session;
    MagmaRuntime(Path supplied,Path dir,Map<String,Object> session)throws Exception{
        this.session=session;home=supplied.toAbsolutePath().normalize();
        try{
            if(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")||!List.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw new IOException("The pinned MAGMA profile requires Windows x86_64");Path marker=home.resolve(".jlinalg-magma.json");Map<?,?> m=RegenieRuntime.object(marker);if(!"1.10".equals(m.get("version"))||!"windows-x86_64-static".equals(m.get("profile"))||!"953a2a88eb3a7047b2963712a5fce5d74ab82ca63c9ac32bc3184b3055020507".equals(m.get("archive_sha256"))||!FILES.equals(m.get("files")))throw new IOException("Pinned private MAGMA marker differs");
            for(var e:FILES.entrySet()){Path p=home.resolve(e.getKey());if(!Files.isRegularFile(p)||!hash(p).equals(e.getValue()))throw new IOException("Pinned runtime member changed: "+e.getKey());protectedFiles.put(p,e.getValue());}protectedFiles.put(marker,hash(marker));execute(List.of("--version"),dir,"version",60);if(!Files.readString(dir.resolve("version.log")).strip().equals("MAGMA version: v1.10 (win/s)"))throw new IOException("Actual MAGMA version differs from pinned application");session.put("runtime_version","1.10 (win/s)");session.put("runtime_executable_sha256",hash(home.resolve("magma.exe")));session.put("runtime_terms","Unmodified private upstream download; redistribution/modification prohibited. No upstream program/source bundled in JLinAlg.");
        }catch(IOException|IllegalArgumentException e){throw new Unavailable("MAGMA runtime unavailable/changed: "+e.getMessage(),e);}
    }
    void execute(List<String> options,Path dir,String stage,int timeout)throws Exception{
        var command=new ArrayList<String>();command.add(home.resolve("magma.exe").toString());command.addAll(options);session.put(stage+"_command",command);var builder=new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).redirectOutput(dir.resolve(stage+".log").toFile());builder.environment().put("LC_ALL","C");Process process;
        try{process=builder.start();}catch(IOException e){throw new Unavailable("Cannot launch private MAGMA: "+e.getMessage(),e);}
        try{if(!process.waitFor(timeout,TimeUnit.SECONDS)){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();process.waitFor(30,TimeUnit.SECONDS);throw new IOException("MAGMA timed out; retained native logs and partial files");}}catch(InterruptedException e){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();Thread.currentThread().interrupt();throw e;}
        session.put(stage+"_exit_code",process.exitValue());String log=Files.readString(dir.resolve(stage+".log"));if(process.exitValue()!=0||java.util.regex.Pattern.compile("(?im)^\\s*(ERROR|FATAL)\\b").matcher(log).find())throw new IOException("MAGMA failed; inspect "+stage+".log (exit "+process.exitValue()+")");verify();
    }
    void verify()throws IOException{RegenieRuntime.verify(protectedFiles);}
}
