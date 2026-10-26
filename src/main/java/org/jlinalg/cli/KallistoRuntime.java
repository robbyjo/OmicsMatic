/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import static org.jlinalg.cli.FollowupSupport.*;

/** Original private 0.48.0 Windows program; analysis commands never install it. */
final class KallistoRuntime {
    static final String VERSION="0.48.0",MARKER=".jlinalg-kallisto.json";
    static final class Unavailable extends IOException {private static final long serialVersionUID=1L;Unavailable(String message,Exception cause){super(message,cause);}}
    private final Path home;private final Map<Path,String> hashes=new LinkedHashMap<>();private final Map<String,Object> session;
    KallistoRuntime(Path supplied,Path dir,Map<String,Object> session)throws Exception {
        this.session=session;home=supplied.toAbsolutePath().normalize();
        try {
            if(!RegenieRuntime.windows()||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw new IOException("The pinned official profile requires Windows x86_64");
            Map<?,?> lock;
            try(var input=KallistoRuntime.class.getResourceAsStream("/org/jlinalg/omics/kallisto-runtime-lock.json")) {
                if(input==null)throw new IOException("Bundled runtime identity missing");Object value=SimpleJson.parse(new String(input.readAllBytes(),StandardCharsets.UTF_8));if(!(value instanceof Map<?,?> map))throw new IOException("Invalid bundled runtime identity");lock=map;
            }
            Path marker=home.resolve(MARKER);var metadata=RegenieRuntime.object(marker);
            for(String key:List.of("version","profile","archive_sha256","files"))if(!Objects.equals(metadata.get(key),lock.get(key)))throw new IOException("Pinned runtime identity differs: "+key);
            if(!(lock.get("files") instanceof Map<?,?> files)||files.size()!=12)throw new IOException("Runtime file inventory differs");
            var observed=new HashSet<String>();try(var stream=Files.walk(home)){for(Path path:stream.filter(Files::isRegularFile).toList())if(!path.equals(marker))observed.add(home.relativize(path).toString().replace('\\','/'));}
            if(!observed.equals(files.keySet()))throw new IOException("Runtime contains missing/extra members");
            for(var entry:files.entrySet()) {
                Path path=RegenieRuntime.contained(home,String.valueOf(entry.getKey()));String expected=String.valueOf(entry.getValue());
                if(!Files.isRegularFile(path)||Files.isSymbolicLink(path)||!expected.equals(hash(path)))throw new IOException("Runtime member changed: "+entry.getKey());hashes.put(path,expected);
            }
            hashes.put(marker,hash(marker));session.put("runtime_marker_sha256",hash(marker));session.put("runtime_executable_sha256",hash(home.resolve("kallisto.exe")));session.put("runtime_version",VERSION);session.put("runtime_profile","original-official-windows-x86_64");
            execute(List.of("version"),dir,"version",30);
            if(!Files.readString(dir.resolve("version.log")).strip().equals("kallisto, version "+VERSION))throw new IOException("Actual executable version differs");
        }catch(IOException|IllegalArgumentException failure){throw new Unavailable("kallisto dependency unavailable or changed: "+failure.getMessage(),failure);}
    }
    static String path(Path path){return path.toAbsolutePath().normalize().toString().replace('\\','/');}
    void execute(List<String> arguments,Path dir,String stage,int timeout)throws Exception {
        verify();var command=new ArrayList<String>();command.add(path(home.resolve("kallisto.exe")));command.addAll(arguments);session.put(stage+"_command",List.copyOf(command));
        var builder=new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).redirectOutput(dir.resolve(stage+".log").toFile());
        builder.environment().put("LC_ALL","C");builder.environment().put("OMP_NUM_THREADS","1");String system=System.getenv("SystemRoot");if(system==null)throw new IOException("Windows SystemRoot unavailable");builder.environment().put("PATH",home+java.io.File.pathSeparator+Path.of(system,"System32")+java.io.File.pathSeparator+system);
        Process child;try{child=builder.start();}catch(IOException failure){throw new Unavailable("Cannot launch original kallisto: "+failure.getMessage(),failure);}
        try {
            if(!child.waitFor(timeout,TimeUnit.SECONDS)){child.descendants().forEach(ProcessHandle::destroyForcibly);child.destroyForcibly();child.waitFor(30,TimeUnit.SECONDS);throw new IOException("kallisto "+stage+" timed out; inspect the retained log and resource/reference choices");}
        }catch(InterruptedException failure){child.descendants().forEach(ProcessHandle::destroyForcibly);child.destroyForcibly();Thread.currentThread().interrupt();throw failure;}
        session.put(stage+"_exit_code",child.exitValue());verify();
        if(child.exitValue()!=0||Files.readString(dir.resolve(stage+".log")).matches("(?s).*(?m)^\\s*[Ee][Rr][Rr][Oo][Rr]:.*"))throw new IOException("Original kallisto "+stage+" failed (exit "+child.exitValue()+"); inspect "+stage+".log");
    }
    void verify()throws IOException{RegenieRuntime.verify(hashes);}
    Map<?,?> runInfo(Path original,Path audited)throws IOException {
        if(!Files.isRegularFile(original)||Files.size(original)>64*1024*1024)throw new IOException("Original run_info.json missing/oversized");
        Object recorded=session.get("quant_command");if(!(recorded instanceof List<?> command)||command.isEmpty())throw new IOException("Quant command identity missing");
        String expectedForward=String.join(" ",command.stream().map(String::valueOf).toList());var windowsCommand=new ArrayList<String>();windowsCommand.add(home.resolve("kallisto.exe").toString());for(int i=1;i<command.size();i++)windowsCommand.add(String.valueOf(command.get(i)));String expectedWindows=String.join(" ",windowsCommand);
        String raw=Files.readString(original);Map<?,?> metadata=null;boolean repaired=false;
        try {Object parsed=SimpleJson.parse(raw);if(parsed instanceof Map<?,?> map&&(expectedForward.equals(map.get("call"))||expectedWindows.equals(map.get("call"))))metadata=map;}catch(IOException invalidKnownFormat){/* Only the exact known call-field defect can be recovered below. */}
        if(metadata==null) {
            var field=Pattern.compile("(?m)^\\s*\"call\"\\s*:\\s*\"([^\\r\\n]*)\"\\s*$").matcher(raw);
            if(!field.find())throw new IOException("Original run_info.json is invalid outside the known Windows call-field defect");
            String literal=field.group(1);int start=field.start(1)-1,end=field.end(1)+1;
            if(field.find()||!literal.equals(expectedForward)&&!literal.equals(expectedWindows))throw new IOException("Original metadata call differs from the exact launched command");
            Object parsed=SimpleJson.parse(raw.substring(0,start)+RegenieRuntime.json(literal)+raw.substring(end));
            if(!(parsed instanceof Map<?,?> map))throw new IOException("Original run_info.json is not an object");metadata=map;repaired=true;
        }
        var result=new LinkedHashMap<String,Object>();for(var entry:metadata.entrySet())result.put(String.valueOf(entry.getKey()),entry.getValue());
        result.put("jlinalg_metadata_compatibility",repaired?"Known literal Windows call re-escaped in this derived copy; original bytes preserved":"Original strict JSON call matches launched arguments");result.put("jlinalg_original_run_info_sha256",hash(original));result.put("jlinalg_launched_arguments",command);Files.writeString(audited,RegenieRuntime.json(result)+"\n");session.put("run_info_call_field_reescaped",repaired);return result;
    }
    static String setup(){return "Where permitted run python tools/literature/install_kallisto.py --allow-download and supply --home build/omics-kallisto-0.48.0. If unavailable/forbidden,skip until this exact profile is supplied. Preserve changed homes/logs; never substitute the failed0.52.0 source build. Version checks alone do not validate analysis.";}
}
