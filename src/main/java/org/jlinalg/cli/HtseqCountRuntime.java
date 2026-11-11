/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import static org.jlinalg.cli.HtseqFileContract.*;

/** Exact scientific sources plus fresh stable package/interpreter identities; no host authentication. */
final class HtseqCountRuntime {
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String reason){super(reason);}
        Unavailable(String reason,Throwable cause){super(reason,cause);}
    }
    private final Path home,python,site;
    private final Map<String,Object> profile,snapshot;
    private static final Map<String,String> ENV=Map.of("PATH","/usr/bin:/bin","LC_ALL","C.UTF-8","LANG","C.UTF-8","PYTHONNOUSERSITE","1",
        "PYTHONDONTWRITEBYTECODE","1","PYTHONHASHSEED","0","OPENBLAS_NUM_THREADS","1","OMP_NUM_THREADS","1","MKL_NUM_THREADS","1");
    HtseqCountRuntime(Path home,String version)throws IOException{
        if(!System.getProperty("os.name").equals("Linux"))throw new Unavailable("HTSeq original counting requires Linux Java; on Windows run the development JAR under a permitted WSL/Linux Java environment. Java-only VERIFY is portable.");
        this.home=home;profile=profile(version);site=home.resolve(text(profile.get("site")));python=home.resolve("bin/python");
        try{
            if(!Files.exists(home,LinkOption.NOFOLLOW_LINKS)||!Files.exists(python))throw new Unavailable("Missing exact private HTSeq runtime: "+home);
            if(!Files.isDirectory(home,LinkOption.NOFOLLOW_LINKS)||!home.toRealPath().equals(home))throw new IOException("HTSeq runtime_home must be a canonical unlinked directory");
            if(!Files.isExecutable(python))throw new Unavailable("HTSeq Python execution is unavailable/forbidden: "+python);
            for(String pkg:List.of("HTSeq","numpy","pysam"))if(!Files.isDirectory(site.resolve(pkg),LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Missing required HTSeq runtime package: "+pkg+" at "+site);
            snapshot=capture();sources(snapshot,profile);
        }catch(AccessDeniedException|NoSuchFileException denied){throw new Unavailable("Required HTSeq runtime/package access is unavailable or forbidden; skip dependent analysis",denied);}
    }
    private Map<String,Object> capture()throws IOException{
        var all=new TreeMap<String,Object>();long total=0;int count=0;
        for(String name:List.of("HTSeq","numpy","pysam","HTSeq.libs","numpy.libs","pysam.libs")){
            Path root=site.resolve(name);if(!Files.exists(root,LinkOption.NOFOLLOW_LINKS))continue;
            for(var entry:tree(root,1073741824,20000).entrySet()){
                if(++count>20000)throw new IOException("HTSeq package inventory exceeds20000files");
                var value=object(entry.getValue());total=Math.addExact(total,(Long)value.get("bytes"));if(total>1073741824)throw new IOException("HTSeq complete package inventory exceeds1GiB");
                all.put(name+"/"+entry.getKey(),entry.getValue());
            }
        }
        Path real=python.toRealPath();
        return Map.of("package_files",all,"python_target",real.toString(),"python_image",pin(real,134217728),
            "python_link_target",Files.isSymbolicLink(python)?Files.readSymbolicLink(python).toString():"regular-file",
            "base_standard_library_and_system_DLLs_guarded",false,"execution_authenticated",false);
    }
    static void sources(Map<String,Object> snapshot,Map<String,Object> profile)throws IOException{
        var installed=object(snapshot.get("package_files"));var expected=object(profile.get("scientific_sources"));
        var actual=new HashSet<String>();var generated=new HashSet<>((List<?>)profile.get("generated_source_exceptions"));
        for(var entry:installed.entrySet())if(entry.getKey().startsWith("HTSeq/")&&entry.getKey().endsWith(".py")&&!generated.contains(entry.getKey())){
            actual.add(entry.getKey());if(!entry.getValue().equals(expected.get(entry.getKey())))throw new IOException("Installed HTSeq scientific Python differs from the exact original edition: "+entry.getKey());
        }
        if(!actual.equals(expected.keySet()))throw new IOException("Installed HTSeq scientific modules are missing/added; no release substitution is admitted");
    }
    void verify()throws IOException{CHECK.run();if(!snapshot.equals(capture()))throw new IOException("HTSeq package/interpreter identity changed during analysis");}
    Map<String,Object> document(){return Map.of("runtime_home",home.toString(),"site",site.toString(),"version",profile.get("version"),
        "snapshot",snapshot,"scientific_source_profile",profile,"scope","Fresh complete named-package and interpreter snapshot, including bytecode/compiled/generated files. Stable before/after execution; not whole-host, base-standard-library, loader/system-DLL or execution authentication.");}
    RsemProcess.Result execute(String mode,Path prepared,Path work,HtseqFileContract.Limits limits,RsemProcess.Check inputs)throws IOException{
        var args=new java.util.ArrayList<>(List.of(python.toString(),"-s","-B",prepared.resolve("bridge.py").toString(),mode));
        if(mode.equals("count"))args.add(prepared.resolve("arguments.json").toString());
        try{return RsemProcess.executeOwned(args,ENV,work,limits.process(),()->{verify();inputs.verify();},CHECK,List.of(prepared));}
        catch(RsemProcess.UnavailableDependency missing){throw new Unavailable("Required HTSeq process supervision helper is missing or forbidden: "+missing.getMessage(),missing);}
        catch(IOException failed){throw new IOException("Original HTSeq "+mode+" failed: "+String.valueOf(failed.getMessage()).replace("RSEM","HTSeq")+". Read "+work.resolve("process.stderr")+" and process.json; no result is admitted.",failed);}
    }
    void probe(Map<String,Object> value)throws IOException{
        for(String name:List.of("HTSeq","python","numpy","pysam")){
            String wanted=text(profile.get(name.equals("HTSeq")?"version":name));
            if(!wanted.equals(value.get(name)))throw new IOException("HTSeq runtime version probe differs: "+name+" requires "+wanted+", observed "+value.get(name));
        }
        for(String pkg:List.of("HTSeq","numpy","pysam")){
            Path observed=Path.of(text(value.get(pkg+"_file"))).getParent();if(!observed.equals(site.resolve(pkg)))throw new IOException("HTSeq imported a package outside the declared private site: "+pkg);
        }
        if(!text(value.get("executable")).equals(snapshot.get("python_target")))throw new IOException("HTSeq probe executable differs from the guarded interpreter");
    }
}
