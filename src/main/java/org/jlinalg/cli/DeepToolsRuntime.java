/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import static org.jlinalg.cli.FollowupSupport.*;

/** Sealed original deepTools process boundary; analysis never installs software. */
final class DeepToolsRuntime {
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message,Exception cause){super(message,cause);}
    }
    private final Path home,directory,bridge,inspector;
    private final Map<Path,String> protectedFiles=new LinkedHashMap<>();
    private final Map<String,Object> session;
    private final List<String> prefix;
    private final Map<?,?> lock,profile;
    private final boolean cygwin;
    private final Path fontCache;
    DeepToolsRuntime(Path supplied,Path directory,Map<String,Object> session)throws Exception {
        this.directory=directory;this.session=session;home=supplied.toAbsolutePath().normalize();
        try {
            bridge=resource("deeptools_bridge.py");inspector=resource("deeptools_inspect.py");
            Path sourceLock=resource("deeptools-runtime-lock.json"),marker=home.resolve(".jlinalg-deeptools.json");
            lock=RegenieRuntime.object(sourceLock);profile=RegenieRuntime.object(marker);
            if(!"jlinalg-deeptools-runtime-v1".equals(profile.get("schema"))||!"3.5.6".equals(profile.get("version"))||!hash(sourceLock).equals(profile.get("lock_sha256")))throw new IOException("Managed profile differs from pinned deepTools3.5.6/source lock");
            cygwin="cygwin".equals(profile.get("kind"));if(!cygwin&&!"native".equals(profile.get("kind"))||cygwin&&!RegenieRuntime.windows())throw new IOException("Runtime profile is incompatible with this host");
            if(!(profile.get("files") instanceof Map<?,?> files)||files.isEmpty())throw new IOException("Managed runtime lacks sealed file identities");
            for(var entry:files.entrySet()){
                Path path=Path.of(String.valueOf(entry.getKey())).toAbsolutePath().normalize();String digest=String.valueOf(entry.getValue());
                if(!Path.of(String.valueOf(entry.getKey())).isAbsolute()||!digest.matches("[0-9a-f]{64}"))throw new IOException("Runtime seal contains an invalid absolute path/hash");protectedFiles.put(path,digest);
            }
            protectedFiles.put(marker,hash(marker));verify();
            Path managedBridge=home.resolve("deeptools_bridge.py");if(!hash(bridge).equals(protectedFiles.get(managedBridge)))throw new IOException("Runtime bridge differs from this executable; prepare a new managed runtime with its matching installer");
            if(!(profile.get("python_command") instanceof List<?> command)||command.isEmpty())throw new IOException("Managed interpreter command is absent");
            prefix=command.stream().map(String::valueOf).toList();Path executable=Path.of(prefix.get(0)).toRealPath();if(!protectedFiles.containsKey(executable))throw new IOException("Interpreter/shell is not sealed");
            if(cygwin&&(!prefix.equals(List.of(executable.toString(),"--noprofile","--norc",path(home.resolve("launch.sh"))))||!protectedFiles.containsKey(home.resolve("launch.sh"))))throw new IOException("Cygwin launcher must be the sealed fixed-argument managed script");
            if(!cygwin&&(prefix.size()!=1||!executable.toRealPath().equals(Path.of(String.valueOf(profile.get("python_executable"))).toRealPath())))throw new IOException("Native profile must select one sealed Python interpreter");
            fontCache=directory.resolve("mpl-cache");Files.createDirectory(fontCache);
            if(cygwin){
                Path template=Path.of(String.valueOf(profile.get("font_cache_template"))).toAbsolutePath().normalize();if(!template.equals(home.resolve("font-cache-template")))throw new IOException("Cygwin font-cache template is not the managed local template");
                try(var paths=Files.list(template)){var templates=paths.filter(p->p.getFileName().toString().matches("fontlist.*\\.json")).toList();if(templates.size()!=1)throw new IOException("Validated v390 font-cache template unavailable");for(Path p:templates){Object version=RegenieRuntime.object(p).get("_version");if(!protectedFiles.containsKey(p)||!(version instanceof Number number)||number.intValue()!=390)throw new IOException("Font-cache template/version is unsealed or incompatible");Files.copy(p,fontCache.resolve(p.getFileName()));}}
            }
            session.put("runtime_marker_sha256",hash(marker));session.put("runtime_files_verified",protectedFiles.size()-1);session.put("runtime_profile",profile.get("kind"));session.put("upstream_version","3.5.6");session.put("runtime_bridge_sha256",hash(bridge));
            Map<?,?> probe=execute(bridge,List.of(),"runtime-probe",180);validate(probe,"probe-completed");if(!"passed".equals(probe.get("four_loaded_runtime_forks_and_eigenvalues")))throw new IOException("Loaded runtime did not complete its four subprocess probes");
        }catch(IOException|IllegalArgumentException failure){throw new Unavailable("deepTools dependency unavailable/changed: "+failure.getMessage(),failure);}
    }
    private Path resource(String name)throws IOException {
        Path target=directory.resolve(name);try(InputStream stream=getClass().getResourceAsStream("/org/jlinalg/omics/"+name)){if(stream==null)throw new IOException("Missing bundled deepTools support: "+name);Files.copy(stream,target);}return target;
    }
    String path(Path value)throws IOException {
        String text=value.toAbsolutePath().normalize().toString();if(!cygwin)return text;text=text.replace('\\','/');if(text.length()<3||text.charAt(1)!=':'||text.charAt(2)!='/')throw new IOException("Cygwin profile requires local absolute drive paths; UNC input is unsupported: "+text);return "/cygdrive/"+Character.toLowerCase(text.charAt(0))+text.substring(2);
    }
    Map<?,?> audit(Map<String,Object> request,String stage,int timeout)throws Exception {
        Path source=directory.resolve(stage+"-request.json");Files.writeString(source,RegenieRuntime.json(request)+"\n");Map<?,?> result=execute(inspector,List.of("--bootstrap",path(bridge),"--request",path(source)),stage,timeout);validate(result,"audit-completed");return result;
    }
    void run(String module,List<String> arguments,String stage,int timeout)throws Exception {
        var options=new ArrayList<String>(List.of("--module",module,"--"));options.addAll(arguments);validate(execute(bridge,options,stage,timeout),"original-call-completed");
    }
    private Map<?,?> execute(Path script,List<String> options,String stage,int timeout)throws Exception {
        Path report=directory.resolve(stage+"-execution.json");var command=new ArrayList<>(prefix);command.add(path(script));command.addAll(List.of("--report",path(report)));command.addAll(options);session.put(stage+"_command",List.copyOf(command));
        var builder=new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).redirectOutput(directory.resolve(stage+".log").toFile());var environment=builder.environment();
        for(String key:List.of("PYTHONHOME","PYTHONPATH","MATPLOTLIBRC"))environment.remove(key);
        environment.put("PYTHONNOUSERSITE","1");environment.put("PYTHONDONTWRITEBYTECODE","1");environment.put("MPLBACKEND","Agg");environment.put("MPLCONFIGDIR",path(fontCache));environment.put("OPENBLAS_NUM_THREADS","1");environment.put("OMP_NUM_THREADS","1");
        Process child=builder.start();try{
            if(!child.waitFor(timeout,TimeUnit.SECONDS)){child.descendants().forEach(ProcessHandle::destroyForcibly);child.destroyForcibly();child.waitFor(30,TimeUnit.SECONDS);throw new IOException("deepTools "+stage+" timed out; inspect retained log");}
            session.put(stage+"_exit_code",child.exitValue());if(child.exitValue()!=0){String detail=Files.isRegularFile(report)?String.valueOf(RegenieRuntime.object(report).get("error")):"No execution marker";throw new IOException("deepTools "+stage+" failed with exit"+child.exitValue()+"; inspect "+stage+".log and execution marker. "+detail.substring(Math.max(0,detail.length()-1800)));}
        }catch(InterruptedException failure){child.descendants().forEach(ProcessHandle::destroyForcibly);child.destroyForcibly();Thread.currentThread().interrupt();throw failure;}
        if(!Files.isRegularFile(report))throw new IOException("deepTools exited without a completion marker; exit0 alone is not success");return RegenieRuntime.object(report);
    }
    private void validate(Map<?,?> result,String expected)throws IOException {
        if(!expected.equals(result.get("status")))throw new IOException("deepTools did not complete "+expected+"; inspect execution marker: "+result.get("error"));
        if(!lock.get("critical_versions").equals(result.get("versions"))||!lock.get("source_modules_sha256").equals(result.get("source_sha256")))throw new IOException("Actual original program/dependency identities differ from the pinned source lock");
        Path python=Path.of(String.valueOf(profile.get("python_executable"))).toAbsolutePath().normalize();if(!Objects.equals(protectedFiles.get(python),result.get("executable_sha256")))throw new IOException("Actual interpreter differs from the sealed executable");
        if(profile.get("expected_prefix")!=null&&!profile.get("expected_prefix").equals(result.get("prefix")))throw new IOException("Actual interpreter prefix differs");
        if(cygwin&&String.valueOf(result.get("python_core_dll_maps")).lines().filter(s->!s.isBlank()).count()!=1)throw new IOException("Cygwin must load exactly one private Python core DLL");
    }
    void verify()throws IOException {
        long started=System.nanoTime();var executor=Executors.newFixedThreadPool(4);int verified=0;
        try(var log=Files.newBufferedWriter(directory.resolve("runtime-integrity.log"),java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND)){
            var entries=new ArrayList<>(protectedFiles.entrySet());log.write("START files="+entries.size()+" workers=4\n");log.flush();
            for(int offset=0;offset<entries.size();offset+=256){
                var tasks=new ArrayList<Callable<Void>>();for(var entry:entries.subList(offset,Math.min(offset+256,entries.size())))tasks.add(()->{if(!Files.isRegularFile(entry.getKey())||!entry.getValue().equals(hash(entry.getKey())))throw new IOException("Input/runtime changed during analysis: "+entry.getKey());return null;});
                for(var future:executor.invokeAll(tasks))try{future.get();}catch(ExecutionException failure){throw new IOException(String.valueOf(failure.getCause().getMessage()),failure.getCause());}
                verified+=tasks.size();log.write("VERIFIED "+verified+"/"+entries.size()+"\n");log.flush();
            }
            log.write("PASSED files="+verified+" elapsed_ms="+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)+"\n");
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IOException("Interrupted during full runtime integrity verification",interrupted);}
        finally{executor.shutdownNow();}
    }
}
