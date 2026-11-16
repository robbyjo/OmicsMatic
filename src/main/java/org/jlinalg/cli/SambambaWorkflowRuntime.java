/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import static org.jlinalg.cli.SambambaWorkflowFiles.*;

/** Exact original ELF, execution helpers and observed Linux dependency identities. */
final class SambambaWorkflowRuntime {
    static final List<String> PROBES=List.of("version","ldd-source","ldd-setpriv","ldd-prlimit","ldd-bash");
    private static final Pattern LIBRARY=Pattern.compile("(/[^\\s]+)\\s+\\(0x[0-9a-fA-F]+\\)");
    private SambambaWorkflowRuntime(){ }
    static Properties inspect(Path binary,Path out,Config c)throws Exception {
        linux();binary(binary,c.get("edition"));
        Properties record=new Properties();record.setProperty("profile",PROFILE);record.setProperty("edition",c.get("edition"));
        record.setProperty("binary_path",binary.toString());record.setProperty("binary_sha256",sha(binary));
        record.setProperty("header_version",c.get("edition").equals("0.8.2")?"1.0":"0.6.6");
        record.setProperty("java_runtime",System.getProperty("java.runtime.version"));record.setProperty("java_vendor",System.getProperty("java.vendor"));
        record.setProperty("os_version",System.getProperty("os.version"));
        add(record,binary);
        for(String name:List.of("/usr/bin/setpriv","/usr/bin/prlimit","/usr/bin/ldd","/usr/bin/bash")){
            Path file=Path.of(name);if(!Files.isExecutable(file))throw new Prerequisite("Required permitted Linux helper unavailable: "+file);add(record,file);
        }
        for(String name:List.of("/etc/ld.so.cache","/etc/ld.so.preload"))if(Files.exists(Path.of(name)))add(record,Path.of(name));
        var version=SambambaWorkflowProcess.execute(List.of(binary.toString(),"--version"),out,"version",c,10_000,512L<<20);
        if(version.exitCode()!=0)throw new Prerequisite("Original version command could not run; read version.stderr. Supply the permitted matching Linux runtime and helpers; no dependent analysis ran.");
        String text=console(out,"version");var matches=Pattern.compile("(?m)^sambamba ([0-9.]+)\\s*$").matcher(text);
        Set<String> observed=new TreeSet<>();while(matches.find())observed.add(matches.group(1));
        if(!observed.equals(Set.of(c.get("edition"))))throw new IOException("Complete observed Sambamba version differs: "+observed);
        List<Path> executables=List.of(binary,Path.of("/usr/bin/setpriv"),Path.of("/usr/bin/prlimit"),Path.of("/usr/bin/bash"));
        for(int i=0;i<executables.size();i++){
            String phase=PROBES.get(i+1);var result=SambambaWorkflowProcess.execute(List.of("/usr/bin/ldd",executables.get(i).toString()),out,phase,c,10_000,512L<<20);
            String message=console(out,phase);boolean isStatic=message.contains("not a dynamic executable");
            if(i==0&&c.get("edition").equals("0.8.2")){
                if(result.exitCode()!=1||!isStatic)throw new IOException("Selected original0.8.2 ELF no longer has the recorded static runtime identity");
                record.setProperty("original_linkage","static");
            }else{
                if(result.exitCode()!=0||isStatic||message.contains("not found"))throw new Prerequisite("Missing/unassessable permitted Linux dependency; read "+phase+".stdout/.stderr and supply the named runtime. Skip dependent analysis.");
                var libs=LIBRARY.matcher(message);int count=0;
                while(libs.find()){add(record,Path.of(libs.group(1)));count++;}
                if(count==0)throw new IOException("No complete resolved library identities were observed for "+phase);
                if(i==0)record.setProperty("original_linkage","dynamic");
            }
            verify(record);
        }
        return record;
    }
    static String console(Path out,String phase)throws IOException {
        Path a=out.resolve(phase+".stdout"),b=out.resolve(phase+".stderr");
        if(Files.size(a)>65536||Files.size(b)>65536)throw new IOException("Runtime probe console exceeds 64 KiB");
        return Files.readString(a)+"\n"+Files.readString(b);
    }
    private static void add(Properties p,Path requested)throws Exception {
        if(!Files.isRegularFile(requested))throw new Prerequisite("Resolved runtime file unavailable: "+requested);
        Path path=requested.toRealPath();String hash=sha(path),key="file_sha256."+path;
        if(p.containsKey(key)&&!hash.equals(p.getProperty(key)))throw new IOException("Runtime changed during dependency inspection: "+path);
        p.setProperty(key,hash);p.setProperty("file_bytes."+path,Long.toString(Files.size(path)));
    }
    static void verify(Properties before)throws Exception {
        for(String key:before.stringPropertyNames())if(key.startsWith("file_sha256.")){
            Path path=Path.of(key.substring("file_sha256.".length()));
            if(!Files.isRegularFile(path))throw new Prerequisite("Exact recorded runtime file is unavailable: "+path+". Supply the permitted original runtime; do not substitute editions.");
            if(!sha(path).equals(before.getProperty(key))||!Long.toString(Files.size(path)).equals(before.getProperty("file_bytes."+path)))
                throw new IOException("Exact source/helper/runtime bytes changed: "+path);
        }
    }
    static void replayCoherence(Properties original,Properties current)throws IOException {
        // JVM/OS versions are retained observations. Exact implementation classes
        // and the original executable/helpers/libraries remain required identities.
        var a=new Properties();a.putAll(original);var b=new Properties();b.putAll(current);
        for(String key:List.of("java_runtime","java_vendor","os_version")){a.remove(key);b.remove(key);}
        if(!a.equals(b))throw new IOException("Original/replay runtime identities differ; use the exact permitted recorded runtime or qualify a separate new analysis");
    }
}
