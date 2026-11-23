/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.ScenicWorkflowFiles.*;

/** Acknowledged unchanged-runtime execution using the existing owned-process guard. */
final class ScenicPlusPrepareRuntime {
    private ScenicPlusPrepareRuntime(){ }
    static void execute(Path root,Map<String,Object> request,Map<String,Object> process)throws Exception{
        Path python=Path.of(text(request.get("python"),"python"));
        if(!Files.isRegularFile(python)||!Files.isExecutable(python))throw new ScenicWorkflowContracts.Unavailable("Missing/nonexecutable permitted private Python: "+python);
        var environment=new HashMap<>(System.getenv());
        environment.keySet().removeIf(k->k.startsWith("PYTHON")||k.startsWith("LC_")||List.of("OMP_","OPENBLAS_","MKL_","NUMBA_","NUMEXPR_","VECLIB_","BLIS_","ARROW_").stream().anyMatch(k::startsWith)||k.startsWith("HDF5_")||Set.of("LANGUAGE","LD_PRELOAD","LD_LIBRARY_PATH","DYLD_INSERT_LIBRARIES","DYLD_LIBRARY_PATH").contains(k));
        environment.put("LC_ALL","C.UTF-8");environment.put("LANG","C.UTF-8");environment.put("PYTHONHASHSEED","0");environment.put("MPLBACKEND","Agg");
        for(String name:List.of("OMP_NUM_THREADS","OPENBLAS_NUM_THREADS","MKL_NUM_THREADS","NUMBA_NUM_THREADS","NUMEXPR_NUM_THREADS","VECLIB_MAXIMUM_THREADS","BLIS_NUM_THREADS"))environment.put(name,"1");
        Path cache=root.resolve("worker/cache");Files.createDirectory(cache);environment.put("NUMBA_CACHE_DIR",cache.resolve("numba").toString());environment.put("MPLCONFIGDIR",cache.resolve("matplotlib").toString());environment.put("XDG_CACHE_HOME",cache.resolve("xdg").toString());
        var check=new Preflight(root,request);
        int code;
        try{code=ScenicWorkflowProcess.execute(List.of(python.toString(),"-s","-B","-X","utf8","-X","pycache_prefix="+cache.resolve("pycache"),"../source/worker.py",root.toString()),environment,root,request,process,check::check);}
        finally{write(root.resolve("worker-process-attempt.json"),process);}
        var report=read(root.resolve("worker/report.json"));
        if(code==3&&"SKIP3_DEPENDENT_ANALYSIS_SKIPPED".equals(report.get("status")))throw new ScenicWorkflowContracts.Unavailable(text(report.get("cause"),"runtime prerequisite"));
        require(code==0&&"ORIGINAL_RETURNED_PENDING_JAVA_AUDIT".equals(report.get("status"))&&Boolean.TRUE.equals(report.get("original_call_returned"))&&Boolean.FALSE.equals(report.get("results_admitted")),"Original SCENIC+ preparation did not return auditable output; inspect PRIVATE worker report/stdout/stderr: "+report.get("cause"));
        require(check.acknowledged,"Original SCENIC+ preparation lacks Java fingerprint acknowledgement");check.check();check.info(read(root.resolve("worker/runtime-after-info.json")));
        require(same(check.before,runtimeFiles(check.roots)),"Python/package/stdlib closure changed during original SCENIC+ preparation");write(root.resolve("runtime-after.json"),check.before);
        for(var entry:read(root.resolve("input-inventory.json")).entrySet())require(same(entry.getValue(),pin(root.resolve(relative(entry.getKey())))),"Captured scientific input changed");
        process.put("runtime_scope","Complete recorded distribution locations, Python executable and stdlib; resolved aliases retained; __pycache__ excluded. No OS loader closure, hard RAM/job quota or authenticity claim. Original source population checked before/after; capture and vendor traces stay private.");
    }
    private static final class Preflight {
        final Path root;final Map<String,Object> request;final Map<Path,String> stamps=new LinkedHashMap<>();boolean acknowledged;final long started=System.nanoTime();
        List<Path> roots=List.of();Map<String,Object> before;
        Preflight(Path root,Map<String,Object> request){this.root=root;this.request=request;}
        void check()throws IOException{
            for(var entry:stamps.entrySet())require(entry.getValue().equals(stamp(entry.getKey())),"Immutable captured input/source metadata changed: "+entry.getKey());
            if(acknowledged||!Files.isRegularFile(root.resolve("worker/READY")))return;
            require(Arrays.equals(bytes(root.resolve("worker/READY"),128),"SCENICPLUS_PREFLIGHT_V1\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII)),"Invalid SCENIC+ preflight marker");
            var info=read(root.resolve("worker/runtime.json"));var paths=new ArrayList<Path>();for(Object value:list(info.get("runtime_roots")))paths.add(Path.of(text(value,"runtime root")));roots=List.copyOf(paths);
            require(!roots.isEmpty()&&roots.size()<=1024,"Unexpected runtime root population");before=runtimeFiles(roots);info(info);
            for(String name:ScenicPlusPrepareCli.SOURCES){Path path=root.resolve("source/"+name);require(Arrays.equals(ScenicPlusPrepareCli.resource(name),bytes(path,67108864)),"Changed packaged caller/profile");remember(path);}
            for(var entry:read(root.resolve("input-inventory.json")).entrySet()){Path path=root.resolve(relative(entry.getKey()));require(same(entry.getValue(),pin(path)),"Changed captured input");remember(path);}
            for(String name:List.of("canonical-request.json","source-request.json","producer.json","input-inventory.json","source-inventory.json","worker/request.json"))remember(root.resolve(name));
            require(!Files.exists(root.resolve("worker/report.json")),"Worker ended before Java acknowledgement; inspect retained private report");
            require(System.nanoTime()-started<=java.util.concurrent.TimeUnit.SECONDS.toNanos(ScenicWorkflowContracts.cap(request,"timeout_seconds")),"Deadline expired during runtime fingerprinting; original SCENIC+ preparation is not acknowledged");
            write(root.resolve("runtime-before.json"),before);ScenicWorkflowFiles.text(root.resolve("worker/ACK.pending"),"SCENICPLUS_ACK_V1\n");Files.move(root.resolve("worker/ACK.pending"),root.resolve("worker/ACK"),StandardCopyOption.ATOMIC_MOVE);acknowledged=true;
        }
        void info(Map<String,Object> info)throws IOException{
            var profile=object(org.jlinalg.gprofiler.GprofilerJson.read(ScenicPlusPrepareCli.resource("SOURCE-PROFILE.json")));
            require(ScenicPlusPrepareCli.PROFILE.equals(info.get("profile"))&&profile.get("python").equals(info.get("python"))&&"0".equals(info.get("python_hash_seed")),"Resolved runtime/profile/hash seed differs");
            require(same(profile.get("loaded_distributions"),info.get("packages"))&&same(profile.get("original_python_sources"),info.get("original_python_sources"))&&same(profile.get("pinned_git_python_sources"),info.get("pinned_git_python_sources")),"Resolved original metadata origins/source differ");
            var files=object(before.get("files"));require(files.containsKey(Path.of(text(info.get("python_executable"),"Python executable")).toRealPath().toString()),"Actual interpreter outside runtime closure");
            for(Object value:list(info.get("loaded_module_files"))){Path path=Path.of(text(value,"loaded module")).toRealPath();require(ScenicPlusPrepareCli.SOURCES.stream().anyMatch(name->path.equals(root.resolve("source/"+name)))||files.containsKey(path.toString()),"Loaded module outside runtime/caller closure: "+path);}
        }
        void remember(Path path)throws IOException{stamps.put(path,stamp(path));}
        String stamp(Path path)throws IOException{var a=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);require(a.isRegularFile()&&!a.isSymbolicLink(),"Immutable captured member must be regular");return a.size()+"/"+a.lastModifiedTime()+"/"+a.fileKey();}
    }
}
