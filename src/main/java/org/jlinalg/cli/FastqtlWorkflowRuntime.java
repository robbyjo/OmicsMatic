/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.ScenicWorkflowFiles.*;

/** Pinned distribution executable with a closed, physically captured private ELF dependency bundle. */
final class FastqtlWorkflowRuntime {
    private FastqtlWorkflowRuntime() { }
    static final class Unavailable extends IOException {private static final long serialVersionUID=1L;Unavailable(String s){super(s);}Unavailable(String s,Throwable e){super(s,e);}}
    static final String BINARY="707d47265f6c5ae722a098ac212a0755f9d2095481f76731d4f6313ef10b86c7";
    static final String COPYRIGHT="40a6b2d085c44edc6a42da8b1d85162463af888c0b5841eafd7fb9d4b381ba7a";
    static final Set<String> LIBRARIES=Set.of("ld-linux-x86-64.so.2","libRmath.so.1","libhts.so.3","libboost_iostreams.so.1.83.0","libboost_program_options.so.1.83.0","libgsl.so.27","libblas.so.3","libstdc++.so.6","libm.so.6","libgcc_s.so.1","libc.so.6","libdeflate.so.0","libhtscodecs.so.2","liblzma.so.5","libbz2.so.1.0","libz.so.1","libzstd.so.1","libgslcblas.so.0");
    static Map<String,Object> identity(Map<String,Object> request)throws IOException {
        if(!"Linux".equals(System.getProperty("os.name"))||!Set.of("amd64","x86_64").contains(System.getProperty("os.arch")))throw new Unavailable("This ELF profile requires Linux x86_64 Java; on Windows run the complete project JAR in WSL. Dependent analysis skipped");
        Path home=Path.of(text(request.get("runtime_home"),"runtime_home"));var files=map();
        for(String name:List.of("bin/fastQTL","COPYRIGHT")) {
            Path file=home.resolve(name);if(!Files.isRegularFile(file))throw new Unavailable("Missing "+file+"; stage the named Ubuntu package and private dependencies using the documented installer");
            physical(home,file);var pin=pin(file);if(!(name.startsWith("bin/")?BINARY:COPYRIGHT).equals(pin.get("sha256")))throw new Unavailable("Wrong pinned Ubuntu FastQTL "+name+"; no edition substitution or hash refresh");files.put(name,pin);
        }
        Path lib=home.resolve("lib");if(!Files.isDirectory(lib))throw new Unavailable("Missing private ELF library directory: "+lib);
        var actual=new HashSet<String>();try(var members=Files.list(lib)){for(Path path:members.toList()){String name=path.getFileName().toString();physical(home,path);require(LIBRARIES.contains(name),"Unexpected private ELF library: "+name);actual.add(name);files.put("lib/"+name,pin(path));}}
        if(!actual.equals(LIBRARIES))throw new Unavailable("Incomplete private ELF bundle; restore these SONAME files: "+difference(LIBRARIES,actual));
        if(!Files.isExecutable(lib.resolve("ld-linux-x86-64.so.2"))||!Files.isExecutable(home.resolve("bin/fastQTL")))throw new Unavailable("Restore permitted executable permissions on the private loader and FastQTL binary");
        return map("profile",FastqtlWorkflowContracts.PROFILE,"files",files,"kernel",System.getProperty("os.version"),"os",System.getProperty("os.name"),"arch",System.getProperty("os.arch"),"source_commit","d7396fc4ac16e8c0e6686c4988f1c331a41a9dc6","distribution_patches_apply",true,"help_is_health_probe",false);
    }
    private static Set<String> difference(Set<String> a,Set<String> b){var c=new TreeSet<>(a);c.removeAll(b);return c;}
    private static void physical(Path home,Path file)throws IOException {require(home.toRealPath().equals(home)&&Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)&&file.toRealPath().equals(file),"Runtime must use physical regular files without symlink/junction aliases: "+file);}
    static List<String> command(Path root,Map<String,Object> request)throws IOException {
        Path home=Path.of(text(request.get("runtime_home"),"runtime_home"));var o=object(request.get("options"));
        var cmd=new ArrayList<>(List.of(home.resolve("lib/ld-linux-x86-64.so.2").toString(),"--inhibit-cache","--library-path",home.resolve("lib").toString(),home.resolve("bin/fastQTL").toString(),"--vcf",FastqtlWorkflowContracts.input(root,"vcf").toString(),"--bed",FastqtlWorkflowContracts.input(root,"phenotypes").toString(),"--region",text(request.get("region"),"region"),"--out",root.resolve("worker/result.txt").toString(),"--log",root.resolve("worker/original.log").toString(),"--seed",literal(o.get("seed")),"--window",literal(o.get("window")),"--maf-threshold",literal(o.get("maf_threshold")),"--ma-sample-threshold",literal(o.get("ma_sample_threshold")),"--global-af-threshold","0"));
        if(object(request.get("inputs")).containsKey("covariates"))cmd.addAll(List.of("--cov",FastqtlWorkflowContracts.input(root,"covariates").toString()));
        if(Boolean.TRUE.equals(o.get("normal")))cmd.add("--normal");
        if("NOMINAL".equals(request.get("operation")))cmd.addAll(List.of("--threshold",literal(o.get("threshold"))));else {cmd.add("--permute");for(Object v:(List<?>)o.get("permutations"))cmd.add(literal(v));}
        return List.copyOf(cmd);
    }
    static void execute(Path root,Map<String,Object> request,FastqtlWorkflowProcess.Check check)throws Exception {
        var before=identity(request);write(root.resolve("runtime-before.json"),before);var process=map();int exit;
        var environment=Map.of("LC_ALL","C","LANG","C","TZ","UTC","PATH","/usr/bin:/bin");process.put("environment",environment);
        try{exit=FastqtlWorkflowProcess.execute(command(root,request),environment,root,request,process,check);}finally{write(root.resolve("worker-process.json"),process);}
        var after=identity(request);write(root.resolve("runtime-after.json"),after);require(same(before,after),"Runtime changed during original operation");
        require(exit==0,"Original FastQTL returned "+exit+"; retain worker logs and partial output");
        String stdout=new String(bytes(root.resolve("worker/stdout.log"),67108864),java.nio.charset.StandardCharsets.UTF_8);require(stdout.contains("Version : v2.184_gtex")&&stdout.contains("Running time:")&&!stdout.contains("ERROR"),"Original invocation lacks the expected version/completion or reports ERROR (upstream can return zero on error)");
        require(Files.isRegularFile(root.resolve("worker/result.txt"),LinkOption.NOFOLLOW_LINKS),"Original process returned no scientific output");
    }
}
