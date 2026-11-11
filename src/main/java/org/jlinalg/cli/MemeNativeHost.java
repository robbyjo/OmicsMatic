/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Current native process/CPU identity, excluding volatile counters and clock values. */
final class MemeNativeHost {
    private MemeNativeHost() { }
    static Map<String,String> read()throws IOException {
        if(!System.getProperty("os.name").equals("Linux")||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch").toLowerCase(Locale.ROOT)))throw new TomtomRuntime.Unavailable("Exact original Tomtom runtime admission requires Linux x86_64 Java. On Windows use a permitted Linux Java host such as WSL; otherwise skip dependent execution.");
        var result=new TreeMap<String,String>();result.put("os","Linux");result.put("architecture","x86_64");result.put("kernel",System.getProperty("os.version"));String status=proc(Path.of("/proc/self/status"),65536),cpu=proc(Path.of("/proc/cpuinfo"),4194304);
        for(String line:status.split("\n"))for(String name:List.of("Uid:","Gid:","Groups:","Cpus_allowed_list:"))if(line.startsWith(name))require(result.putIfAbsent(name,line.substring(name.length()).strip().replaceAll("[ \\t]+"," "))==null,"Duplicate native MEME process identity field");
        require(result.keySet().containsAll(List.of("Uid:","Gid:","Groups:","Cpus_allowed_list:")),"Incomplete native MEME process identity");var lines=new TreeSet<String>();
        for(String raw:cpu.split("\n")){int colon=raw.indexOf(':');if(colon<0)continue;String name=raw.substring(0,colon).strip();if(Set.of("vendor_id","cpu family","model","model name","stepping","microcode","flags").contains(name))lines.add(name+"="+raw.substring(colon+1).strip().replaceAll("[ \\t]+"," "));}
        require(lines.stream().anyMatch(s->s.startsWith("flags="))&&lines.stream().anyMatch(s->s.startsWith("model name=")),"Incomplete native MEME CPU identity/features");result.put("cpu_profile_sha256",NextflowFiles.sha(String.join("\n",lines).getBytes(java.nio.charset.StandardCharsets.UTF_8)));return Map.copyOf(result);
    }
    private static String proc(Path path,int capacity)throws IOException {
        try(var in=Files.newInputStream(path)){byte[] raw=in.readNBytes(capacity+1);if(raw.length>capacity)throw new IOException("Native MEME host identity exceeds capacity");return NextflowFiles.utf8(raw);}
        catch(IOException missing){throw new TomtomRuntime.Unavailable("Required readable native procfs identity is unavailable; supply a permitted supported host or skip dependent execution",missing);}
    }
    private static void require(boolean condition,String message)throws IOException {MemeRuntimeFiles.require(condition,message);}
}
