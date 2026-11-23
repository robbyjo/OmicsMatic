/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.SquidpyWorkflowFiles.*;

/** Local input capture and a source/runtime fingerprint before the public call. */
final class SquidpyWorkflowRuntime {
    private SquidpyWorkflowRuntime() { }
    static final String RESOURCE="/org/jlinalg/omics/squidpy-workflow/";
    static final List<String> SOURCE_FILES=List.of("worker.py","operations.py","SOURCE-PROFILE.json");
    static byte[] resource(String name)throws IOException {
        try(var stream=SquidpyWorkflowRuntime.class.getResourceAsStream(RESOURCE+name)){
            require(stream!=null,"Restore packaged original Squidpy worker/profile: "+name);return stream.readAllBytes();
        }
    }
    static Map<String,Object> profile()throws IOException{return object(GprofilerJson.read(resource("SOURCE-PROFILE.json")));}
    static void sources(Path root)throws IOException {
        Files.createDirectory(root.resolve("source"));var inventory=map();
        for(String name:SOURCE_FILES){byte[] raw=resource(name);Files.write(root.resolve("source/"+name),raw,StandardOpenOption.CREATE_NEW);inventory.put(name,pin(raw));}
        write(root.resolve("source-inventory.json"),inventory);
    }
    static Map<String,Object> workerRequest(Map<String,Object> request)throws IOException {
        var worker=map("schema_version",1,"operation",request.get("operation"),"expected_python",request.get("expected_python"),
                "expected_squidpy",request.get("expected_squidpy"),"expected_packages",request.get("expected_packages"),"options",request.get("options"));
        var limits=map();for(String name:List.of("maximum_cells","maximum_features","maximum_entries","maximum_dense_entries","maximum_working_bytes","maximum_work","threads"))limits.put(name,object(request.get("limits")).get(name));
        worker.put("limits",limits);worker.put("ack_timeout_seconds",object(request.get("limits")).get("ack_timeout_seconds"));
        if(request.containsKey("input")){
            var input=new LinkedHashMap<>(object(request.get("input")));String format=input.get("format").toString();
            if(format.equals("matrix_market")){
                String source=input.get("matrix").toString();input.put("matrix","../inputs/matrix.mtx"+(source.endsWith(".gz")?".gz":""));
                input.put("cells","../inputs/cells.tsv");input.put("features","../inputs/features.tsv");
            }else input.put("file","../inputs/data."+(format.equals("h5ad")?"h5ad":"json"));
            worker.put("input",input);
        }
        if(request.containsKey("interactions_file"))worker.put("interactions_file","../inputs/interactions.json");
        if(request.containsKey("nanostring_directory"))worker.put("nanostring_directory","../inputs/nanostring");
        return worker;
    }
    private static void copy(Path root,Map<String,Object> request,Path parent,Map<String,Object> captures,String target,Path source)throws IOException {
        relative(target);capture(parent==null?source:parent.resolve(target),root.resolve(target),request,captures,target);
    }
    static void stage(Path root,Map<String,Object> request,Path parent)throws IOException {
        var captures=map();var directories=new TreeSet<String>();
        if(request.containsKey("input")){
            var input=object(request.get("input"));String format=input.get("format").toString();
            if(format.equals("matrix_market")){
                String source=input.get("matrix").toString();copy(root,request,parent,captures,"inputs/matrix.mtx"+(source.endsWith(".gz")?".gz":""),Path.of(source));
                for(String name:List.of("cells","features"))copy(root,request,parent,captures,"inputs/"+name+".tsv",Path.of(input.get(name).toString()));
            }else copy(root,request,parent,captures,"inputs/data."+(format.equals("h5ad")?"h5ad":"json"),Path.of(input.get("file").toString()));
        }
        if(request.containsKey("interactions_file"))copy(root,request,parent,captures,"inputs/interactions.json",Path.of(request.get("interactions_file").toString()));
        if(request.containsKey("nanostring_directory")){
            Path original=Path.of(request.get("nanostring_directory").toString());
            if(parent==null&&!Files.isDirectory(original))throw new SquidpyWorkflowContracts.Unavailable("Provide permitted original Nanostring directory: "+original);
            directories.add("inputs/nanostring");Files.createDirectory(root.resolve("inputs/nanostring"));
            var options=object(request.get("options"));
            for(String key:List.of("counts_file","meta_file","fov_file"))if(options.get(key)!=null){String name=relative(options.get(key).toString());copy(root,request,parent,captures,"inputs/nanostring/"+name,original.resolve(name));}
            for(String name:List.of("CellComposite","CellLabels")){
                String target="inputs/nanostring/"+name;Path input=parent==null?original.resolve(name):parent.resolve(target);
                if(!Files.exists(input,LinkOption.NOFOLLOW_LINKS))continue;
                require(Files.isDirectory(input,LinkOption.NOFOLLOW_LINKS)&&input.toRealPath().equals(input.toAbsolutePath().normalize()),"Require physical captured Nanostring image directory");
                directories.add(target);Files.createDirectories(root.resolve(target));
                try(var paths=Files.list(input)){
                    for(Path file:paths.toList()){
                        require(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS),"Nanostring image directory has a nonregular/nested entry; preserve it and provide the intended self-contained bundle");
                        String child=relative(file.getFileName().toString());copy(root,request,parent,captures,target+"/"+child,file);
                    }
                }
            }
        }
        write(root.resolve("input-inventory.json"),captures);write(root.resolve("input-layout.json"),map("directories",new ArrayList<>(directories)));
    }
    static int execute(Path root,Map<String,Object> request,Map<String,Object> manifest,Path parent)throws Exception {
        Path python=Path.of(request.get("python").toString());
        if(!Files.isRegularFile(python)||!Files.isExecutable(python))throw new SquidpyWorkflowContracts.Unavailable("Declared permitted Python is missing/nonexecutable: "+python+"; use an explicitly installed local runtime and matching host paths");
        stage(root,request,parent);write(root.resolve("worker/request.json"),workerRequest(request));
        var environment=new HashMap<>(System.getenv());
        environment.keySet().removeIf(k->k.startsWith("PYTHON")||k.startsWith("LC_")||Set.of("LANGUAGE","LD_PRELOAD","LD_LIBRARY_PATH","DYLD_INSERT_LIBRARIES","DYLD_LIBRARY_PATH").contains(k));
        environment.put("LC_ALL","C.UTF-8");environment.put("LANG","C.UTF-8");environment.put("PYTHONHASHSEED","0");
        String threads=Long.toString(SquidpyWorkflowContracts.cap(request,"threads"));
        for(String name:List.of("OMP_NUM_THREADS","OPENBLAS_NUM_THREADS","MKL_NUM_THREADS","NUMBA_NUM_THREADS"))environment.put(name,threads);
        Path cache=root.resolve("worker/cache");Files.createDirectory(cache);
        environment.put("NUMBA_CACHE_DIR",cache.resolve("numba").toString());environment.put("MPLCONFIGDIR",cache.resolve("matplotlib").toString());environment.put("XDG_CACHE_HOME",cache.resolve("xdg").toString());environment.put("MPLBACKEND","Agg");
        var command=List.of(python.toString(),"-s","-B","-X","utf8","-X","pycache_prefix="+cache.resolve("pycache"),"../source/worker.py",root.toString());
        var preflight=new Preflight(root,request);int code;
        try{code=SquidpyWorkflowProcess.execute(command,environment,root,request,manifest,preflight::check);}
        finally{write(root.resolve("worker-process.json"),new LinkedHashMap<>(manifest));}
        Path reportFile=root.resolve("worker/report.json");require(Files.isRegularFile(reportFile),"Original Python worker ended without a report; inspect retained stdout/stderr");
        var report=read(reportFile);
        if(code==3&&"SKIP3_DEPENDENT_ANALYSIS_SKIPPED".equals(report.get("status")))throw new SquidpyWorkflowContracts.Unavailable(text(report.get("cause"),"prerequisite failure"));
        require(code==0&&"ORIGINAL_RETURNED_PENDING_JAVA_AUDIT".equals(report.get("status"))&&Boolean.TRUE.equals(report.get("original_call_returned"))&&!Boolean.TRUE.equals(report.get("results_admitted")),"Original Squidpy worker did not return a complete auditable output: "+report.get("cause"));
        require(preflight.acknowledged,"Original analysis lacks its verified pre-call handshake");preflight.check();
        var after=read(root.resolve("worker/runtime-after-info.json"));preflight.checkInfo(after);
        require(same(preflight.before,runtimeFiles(preflight.roots)),"Declared Python/package/runtime closure changed during original analysis");
        write(root.resolve("runtime-after.json"),preflight.before);manifest.put("runtime_fingerprint_scope","Declared Python, complete recorded distribution locations and Python stdlib roots; resolved aliases retained; only __pycache__ directories excluded and a fresh private bytecode-cache prefix used. Source wheel members checked before import. System loader dependencies are not enumerated. No host authentication, hard RAM quota or guarantee against transient changes restored between observations.");
        return code;
    }
    private static final class Preflight {
        final Path root;final Map<String,Object> request;boolean acknowledged;Map<String,Object> before;List<Path> roots=List.of();final Map<Path,String> stamps=new LinkedHashMap<>();
        final long started=System.nanoTime();
        Preflight(Path root,Map<String,Object> request){this.root=root;this.request=request;}
        void check()throws IOException {
            for(var item:stamps.entrySet())require(item.getValue().equals(stamp(item.getKey())),"Immutable captured input/source metadata changed during execution: "+item.getKey());
            if(acknowledged||!Files.isRegularFile(root.resolve("worker/READY")))return;
            require(Arrays.equals(bytes(root.resolve("worker/READY"),128),"SQUIDPY_PREFLIGHT_V1\n".getBytes(StandardCharsets.US_ASCII)),"Invalid Squidpy preflight marker");
            var info=read(root.resolve("worker/runtime.json"));
            var paths=new ArrayList<Path>();for(Object value:list(info.get("runtime_roots")))paths.add(Path.of(text(value,"runtime root")));roots=List.copyOf(paths);
            require(!roots.isEmpty()&&roots.size()<=1024,"Invalid runtime root population");before=runtimeFiles(roots);checkInfo(info);
            for(String name:SOURCE_FILES)require(Arrays.equals(resource(name),bytes(root.resolve("source/"+name),67108864)),"Worker source differs from producer resource");
            var captured=read(root.resolve("input-inventory.json"));
            for(var entry:captured.entrySet()){
                String name=relative(entry.getKey());require(same(pin(root.resolve(name)),entry.getValue()),"Captured input changed before original call");remember(root.resolve(name));
            }
            for(String name:List.of("canonical-request.json","source-request.json","request-binding.json","producer.json","source-inventory.json","input-inventory.json","input-layout.json","worker/request.json"))if(Files.exists(root.resolve(name)))remember(root.resolve(name));
            for(String name:SOURCE_FILES)remember(root.resolve("source/"+name));
            require(System.nanoTime()-started<=java.util.concurrent.TimeUnit.SECONDS.toNanos(SquidpyWorkflowContracts.cap(request,"timeout_seconds")),"Worker deadline expired during preflight fingerprinting; no original call is acknowledged");
            require(!Files.exists(root.resolve("worker/report.json")),"Worker ended before fingerprint acknowledgement; inspect its retained handshake/prerequisite error and retry NEW output");
            write(root.resolve("runtime-before.json"),before);text(root.resolve("worker/ACK.pending"),"SQUIDPY_ACK_V1\n");Files.move(root.resolve("worker/ACK.pending"),root.resolve("worker/ACK"),StandardCopyOption.ATOMIC_MOVE);acknowledged=true;
        }
        void checkInfo(Map<String,Object> info)throws IOException {
            require(request.get("expected_python").equals(info.get("python"))&&request.get("expected_squidpy").equals(info.get("squidpy"))&&SquidpyWorkflowContracts.PROFILE.equals(info.get("profile")),"Resolved Python/Squidpy profile differs from request");
            require("0".equals(info.get("python_hash_seed")),"Original Python hash seed differs");
            var packages=map();for(Object value:list(info.get("packages"))){var p=object(value);require(packages.put(text(p.get("name"),"package name"),p.get("version"))==null,"Duplicate installed distribution name");}
            for(var expected:object(request.get("expected_packages")).entrySet()){
                Object actual=packages.get(expected.getKey());
                if(actual==null)for(var p:packages.entrySet())if(SquidpyWorkflowAudit.packageKey(p.getKey()).equals(SquidpyWorkflowAudit.packageKey(expected.getKey())))actual=p.getValue();
                require(expected.getValue().equals(actual),"Loaded dependency differs: "+expected.getKey());
            }
            var edition=object(object(profile().get("editions")).get(request.get("expected_squidpy")));
            require(same(edition.get("members"),info.get("original_source_files")),"Original source member closure differs");
            var files=object(before.get("files"));
            require(files.containsKey(Path.of(text(info.get("python_executable"),"actual Python")).toRealPath().toString()),"Actual Python is outside the recorded runtime closure");
            for(Object value:list(info.get("loaded_module_files"))){Path path=Path.of(text(value,"loaded module")).toRealPath();
                boolean source=SOURCE_FILES.stream().anyMatch(name->path.equals(root.resolve("source/"+name)));
                require(source||files.containsKey(path.toString()),"Loaded module is outside pre-call runtime/source closure: "+path);
            }
        }
        void remember(Path path)throws IOException{stamps.put(path,stamp(path));}
        String stamp(Path path)throws IOException{var a=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);require(a.isRegularFile()&&!a.isSymbolicLink(),"Immutable input/source must be regular");return a.size()+"/"+a.lastModifiedTime()+"/"+a.fileKey();}
    }
}
