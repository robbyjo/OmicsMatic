/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-only */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.FusionWorkflowFiles.*;

/** Original source capture, input staging and a fingerprinted pre-call ACK. */
final class FusionWorkflowRuntime {
    private FusionWorkflowRuntime() { }
    static final String RESOURCE="/org/jlinalg/omics/fusion-9346c122/";
    static Map<String,Object> profile()throws IOException {
        try(var in=FusionWorkflowRuntime.class.getResourceAsStream(RESOURCE+"PROFILE.json")){
            if(in==null)throw new FusionWorkflowContracts.Unavailable("Restore packaged FUSION source profile");return object(GprofilerJson.read(in.readAllBytes()));
        }
    }
    static void sources(Path root)throws IOException {
        var p=profile();write(root.resolve("source-profile.json"),p);
        for(var member:object(p.get("members")).entrySet()){
            String name=relative(member.getKey());var value=object(member.getValue());
            try(var in=FusionWorkflowRuntime.class.getResourceAsStream(RESOURCE+text(value.get("resource"),"source resource"))){
                require(in!=null,"Missing source member "+name);byte[] raw=in.readAllBytes();require(same(pin(raw),map("bytes",value.get("bytes"),"sha256",value.get("sha256"))),"Packaged original source differs: "+name);
                Path destination=root.resolve("worker").resolve(name);Files.createDirectories(destination.getParent());Files.write(destination,raw,StandardOpenOption.CREATE_NEW);
            }
        }
        text(root.resolve("worker/.here"),"");
    }
    static Map<String,Object> workerRequest(Map<String,Object> request)throws IOException {
        var result=new LinkedHashMap<>(request);var source=object(request.get("inputs"));var input=map();String operation=request.get("operation").toString();
        if(operation.equals("TRAIN")){
            input.put("bfile","workdata/train");if(source.containsKey("phenotype"))input.put("phenotype","workdata/phenotype");if(source.containsKey("covariates"))input.put("covariates","workdata/covariates");
        }else{
            input.put("sumstats","../inputs/sumstats");input.put("ref_ld_prefix","../inputs/reference.");input.put("chromosome",source.get("chromosome"));
            if(operation.equals("ASSOCIATE")){input.put("weights","../inputs/weights.pos");input.put("weights_dir","../inputs/weights");if(source.containsKey("panel_sizes"))input.put("panel_sizes","../inputs/panel-sizes");}
            else{input.put("association","../inputs/association");input.put("weights_base",source.get("weights_base"));if(source.containsKey("gene_annotation"))input.put("gene_annotation","glist-hg19");}
        }
        result.put("inputs",input);result.put("input_origins",source);return result;
    }
    static List<String> arguments(Map<String,Object> request)throws IOException {
        var worker=workerRequest(request);var input=object(worker.get("inputs"));var args=new ArrayList<String>();String operation=request.get("operation").toString();
        if(operation.equals("TRAIN")){
            option(args,"bfile",input.get("bfile"));option(args,"tmp","tmp/train");
            for(var pair:Map.of("phenotype","pheno","covariates","covar").entrySet())if(input.containsKey(pair.getKey()))option(args,pair.getValue(),input.get(pair.getKey()));
            for(var entry:object(request.get("binaries")).entrySet()){
                String path=entry.getValue().toString().replace('\\','/');require(path.matches("[A-Za-z0-9_./:+-]+"),"Original training shell invocation requires a literal tool path without spaces/metacharacters; install/copy the tool into a private safe path");option(args,"PATH_"+entry.getKey(),path);
            }
            if(Boolean.TRUE.equals(object(request.get("runtime_compatibility")).get("gcta_no_analysis_probe_exit1"))){int at=args.indexOf("--PATH_gcta");require(at>=0,"Missing original GCTA binding");args.set(at+1,"./gcta-entry.sh");}
        }else{
            option(args,"sumstats",input.get("sumstats"));option(args,"ref_ld_chr",input.get("ref_ld_prefix"));option(args,"chr",input.get("chromosome"));
            if(operation.equals("ASSOCIATE")){option(args,"weights",input.get("weights"));option(args,"weights_dir",input.get("weights_dir"));if(input.containsKey("panel_sizes"))option(args,"PANELN",input.get("panel_sizes"));}
            else option(args,"input","post-input.tsv");
        }
        option(args,"out","out/result");
        for(var entry:object(request.get("options")).entrySet()){
            if(entry.getValue() instanceof Boolean flag){if(flag)args.add("--"+entry.getKey());}
            else option(args,entry.getKey(),literal(entry.getValue()));
        }
        return List.copyOf(args);
    }
    private static void option(List<String> args,String name,Object value){args.add("--"+name);args.add(value.toString());}
    static void stage(Path root,Map<String,Object> request,Path parent,Map<String,Object> captures)throws IOException {
        var inputs=object(request.get("inputs"));String operation=request.get("operation").toString();
        if(operation.equals("TRAIN")){
            for(String extension:List.of(".bed",".bim",".fam"))copy(root,request,parent,captures,"inputs/train"+extension,Path.of(inputs.get("bfile")+extension));
            for(String name:List.of("phenotype","covariates"))if(inputs.containsKey(name))copy(root,request,parent,captures,"inputs/"+name,Path.of(inputs.get(name).toString()));
            Files.createDirectories(root.resolve("worker/workdata"));
            try(var files=Files.list(root.resolve("inputs"))){for(Path file:files.toList())Files.copy(file,root.resolve("worker/workdata").resolve(file.getFileName()));}
        }else{
            copy(root,request,parent,captures,"inputs/sumstats",Path.of(inputs.get("sumstats").toString()));
            String prefix=inputs.get("ref_ld_prefix").toString()+inputs.get("chromosome");
            for(String extension:List.of(".bed",".bim",".fam"))copy(root,request,parent,captures,"inputs/reference."+inputs.get("chromosome")+extension,Path.of(prefix+extension));
            if(operation.equals("ASSOCIATE")){
                copy(root,request,parent,captures,"inputs/weights.pos",Path.of(inputs.get("weights").toString()));if(inputs.containsKey("panel_sizes"))copy(root,request,parent,captures,"inputs/panel-sizes",Path.of(inputs.get("panel_sizes").toString()));
            }else{
                copy(root,request,parent,captures,"inputs/association",Path.of(inputs.get("association").toString()));
                if(inputs.containsKey("gene_annotation")){
                    copy(root,request,parent,captures,"inputs/gene-annotation",Path.of(inputs.get("gene_annotation").toString()));Files.copy(root.resolve("inputs/gene-annotation"),root.resolve("worker/glist-hg19"));
                }
            }
        }
    }
    private static void copy(Path root,Map<String,Object> request,Path parent,Map<String,Object> captures,String name,Path original)throws IOException {
        relative(name);Path source=parent==null?original:parent.resolve(name);capture(source,root.resolve(name),request,captures,name);
    }
    static int execute(Path root,Map<String,Object> request,Map<String,Object> manifest,Path parent)throws Exception {
        Path rscript=Path.of(request.get("rscript").toString());if(!Files.isRegularFile(rscript))throw new FusionWorkflowContracts.Unavailable("Restore exact permitted Rscript: "+rscript);
        var libraries=new ArrayList<String>();for(Object value:list(request.get("r_libraries"))){Path path=Path.of(value.toString());if(!Files.isDirectory(path))throw new FusionWorkflowContracts.Unavailable("Restore declared private/existing R library: "+path);libraries.add(value.toString());}
        for(Object value:object(request.get("binaries")).values())if(!Files.isRegularFile(Path.of(value.toString())))throw new FusionWorkflowContracts.Unavailable("Restore declared original executable: "+value);
        var captures=map();stage(root,request,parent,captures);write(root.resolve("worker/request.json"),workerRequest(request));
        if(Boolean.TRUE.equals(object(request.get("runtime_compatibility")).get("gcta_no_analysis_probe_exit1"))){
            if(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"))throw new FusionWorkflowContracts.Unavailable("This declared GCTA probe adapter requires a POSIX runtime; use the tested Linux path or retain the skipped analysis");
            text(root.resolve("worker/gcta-program.txt"),object(request.get("binaries")).get("gcta").toString()+"\n");Files.setPosixFilePermissions(root.resolve("worker/gcta-entry.sh"),java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        }
        text(root.resolve("worker/libraries.hex"),String.join("\n",libraries.stream().map(FusionWorkflowFiles::hex).toList())+"\n");
        var command=new ArrayList<String>(List.of(rscript.toString(),"--vanilla","driver.R"));command.addAll(arguments(request));
        var environment=new HashMap<>(System.getenv());environment.keySet().removeIf(k->k.startsWith("LC_")||Set.of("R_PROFILE","R_PROFILE_USER","R_ENVIRON","R_ENVIRON_USER","R_LIBS","R_LIBS_SITE","R_LIBS_USER","LD_PRELOAD","DYLD_INSERT_LIBRARIES","PYTHONPATH","PYTHONHOME").contains(k));
        environment.put("LC_ALL","C");environment.put("LANG","C");environment.put("R_LIBS_USER",String.join(File.pathSeparator,libraries));environment.put("R_LIBS_SITE","");
        for(String name:List.of("OMP_NUM_THREADS","OPENBLAS_NUM_THREADS","MKL_NUM_THREADS"))environment.put(name,"1");
        var guard=new Preflight(root,request,parent,captures);int code=FusionWorkflowProcess.execute(command,environment,root,request,manifest,guard::check);
        var outcome=outcome(root);if(code==3&&"SKIP3".equals(outcome.get("status")))throw new FusionWorkflowContracts.Unavailable(outcome.get("detail").toString());
        require(code==0&&"COMPLETED".equals(outcome.get("status")),"Original FUSION worker did not complete: "+outcome.get("detail"));
        require(guard.acknowledged,"Original source analysis lacks its pre-call ACK");guard.check();require(same(guard.runtimeBefore,runtimeFiles(guard.roots)),"Declared/loaded runtime files changed during analysis");
        write(root.resolve("runtime-after.json"),guard.runtimeBefore);manifest.put("runtime_fingerprint_scope","Rscript; declared tools; R home bin/lib; resolved loaded namespace roots and DLL paths; links resolved/recorded; docs/help/tests excluded. System loader dependencies and changes restored between observations are not attested.");return code;
    }
    static Map<String,Object> outcome(Path root)throws IOException {
        Path file=root.resolve("worker/outcome.tsv");if(!Files.isRegularFile(file))return map("status","FAIL2","detail","Worker exited without a completion outcome; inspect retained stdout/stderr");
        var rows=Files.readAllLines(file,StandardCharsets.UTF_8);require(rows.size()==2&&rows.get(0).equals("status\tdetail_hex"),"Invalid worker outcome");String[] values=rows.get(1).split("\t",-1);require(values.length==2,"Invalid worker outcome fields");return map("status",values[0],"detail",unhex(values[1]));
    }
    private static final class Preflight {
        final Path root,parent;final Map<String,Object> request,captures;boolean acknowledged;Map<String,Object> runtimeBefore;List<Path> roots=List.of();final Map<Path,String> attributes=new LinkedHashMap<>();
        Preflight(Path root,Map<String,Object> request,Path parent,Map<String,Object> captures){this.root=root;this.request=request;this.parent=parent;this.captures=captures;}
        void check()throws IOException {
            for(var entry:attributes.entrySet())require(entry.getValue().equals(stamp(entry.getKey())),"Captured immutable input/source metadata changed during execution: "+entry.getKey());
            if(acknowledged||!Files.isRegularFile(root.resolve("worker/READY")))return;
            require(Files.readAllLines(root.resolve("worker/READY")).equals(List.of("FUSION_PREFLIGHT_9346C122_V1")),"Invalid original preflight marker");
            var runtime=read(root.resolve("worker/runtime.json"));require(request.get("expected_R").equals(runtime.get("R")),"Resolved R differs from request");
            var packages=map();for(Object value:list(runtime.get("packages"))){var p=object(value);packages.put(p.get("name").toString(),p.get("version"));}
            for(var expected:object(request.get("expected_packages")).entrySet())require(expected.getValue().equals(packages.get(expected.getKey())),"Resolved package edition differs: "+expected.getKey());
            Object neededValue=GprofilerJson.read(bytes(root.resolve("worker/needed-files.json"),67108864));var needed=list(neededValue);require(needed.size()<=FusionWorkflowContracts.cap(request,"maximum_features"),"Selected feature capacity exceeded");
            for(Object value:needed){var file=object(value);String target=relative(text(file.get("target"),"weight target"));require(target.startsWith("inputs/weights/"),"Unexpected weight capture target");copy(root,request,parent,captures,target,Path.of(text(file.get("source"),"weight source")));}
            write(root.resolve("input-inventory.json"),captures);
            var runtimeRoots=new ArrayList<Path>();runtimeRoots.add(Path.of(request.get("rscript").toString()));Path home=Path.of(runtime.get("R_home").toString());runtimeRoots.add(home.resolve("bin"));if(Files.isDirectory(home.resolve("lib")))runtimeRoots.add(home.resolve("lib"));
            for(Object value:list(runtime.get("packages")))runtimeRoots.add(Path.of(object(value).get("path").toString()));
            for(Object value:object(runtime.get("dlls")).values()){String path=object(value).get("path").toString();if(!path.equals("base"))runtimeRoots.add(Path.of(path));}
            for(Object value:object(request.get("binaries")).values())runtimeRoots.add(Path.of(value.toString()));roots=List.copyOf(runtimeRoots);runtimeBefore=runtimeFiles(roots);write(root.resolve("runtime-before.json"),runtimeBefore);
            for(String captured:captures.keySet())remember(root.resolve(captured));
            for(String name:List.of("canonical-request.json","source-request.json","worker/request.json","worker/libraries.hex","worker/.here"))remember(root.resolve(name));
            for(String name:object(profile().get("members")).keySet())remember(root.resolve("worker").resolve(name));
            if(Files.exists(root.resolve("worker/gcta-program.txt")))remember(root.resolve("worker/gcta-program.txt"));
            text(root.resolve("worker/ACK.pending"),"FUSION_ACK_9346C122_V1\n");Files.move(root.resolve("worker/ACK.pending"),root.resolve("worker/ACK"),StandardCopyOption.ATOMIC_MOVE);acknowledged=true;
        }
        void remember(Path file)throws IOException {attributes.put(file,stamp(file));}
        String stamp(Path file)throws IOException {var a=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);require(a.isRegularFile()&&!a.isSymbolicLink(),"Immutable captured input/source is not a regular file");return a.size()+"/"+a.lastModifiedTime()+"/"+a.fileKey();}
    }
}
