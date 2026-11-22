/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Three exact original builds,explicit Linux/WSL transport and owned-group cleanup. */
final class QtltoolsWorkflowRuntime {
    private QtltoolsWorkflowRuntime() { }
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
        Unavailable(String message,Throwable error){super(message,error);}
    }
    static byte[] resource(String name)throws IOException {
        try(var input=QtltoolsWorkflowRuntime.class.getResourceAsStream("/org/jlinalg/omics/qtltools-workflow/"+name)){if(input==null)throw new IOException("Missing source-bound QTLtools resource: "+name);return input.readAllBytes();}
    }
    static Map<String,Object> profiles()throws IOException {return object(SimpleJson.parse(new String(resource("PROFILES.json"),StandardCharsets.UTF_8)),"profiles");}
    static void sources(Path root,boolean verify)throws IOException {
        var profiles=profiles();if(!NextflowFiles.sha(resource("owned_worker.py")).equals(text(profiles,"worker_sha256")))throw new IOException("Owned worker differs from its source profile");
        for(String name:List.of("PROFILES.json","owned_worker.py")) {
            Path target=root.resolve("source/"+name);byte[] raw=resource(name);
            if(verify){if(!java.util.Arrays.equals(Files.readAllBytes(target),raw))throw new IOException("Changed captured QTLtools source resource");}
            else Files.write(target,raw,StandardOpenOption.CREATE_NEW);
        }
    }
    static String path(Path source,boolean wsl)throws IOException {
        String value=source.toAbsolutePath().normalize().toString().replace('\\','/');
        if(!wsl)return value;
        if(!System.getProperty("os.name").startsWith("Windows")||!value.matches("[A-Za-z]:/.*"))throw new Unavailable("WSL requires Windows drive-backed paths;use a host-native Linux runtime on Linux");
        return "/mnt/"+Character.toLowerCase(value.charAt(0))+value.substring(2);
    }
    static Map<String,Object> absoluteRuntime(Path parent,Map<String,Object> raw)throws IOException {
        var result=object(raw.get("runtime"),"runtime");result.put("executable",resolve(parent,text(result,"executable")).toString());
        var paths=new ArrayList<String>();if(result.containsKey("library_directories"))for(Object value:list(result.get("library_directories"),"library_directories"))paths.add(resolve(parent,value.toString()).toString());result.put("library_directories",paths);return result;
    }
    static List<String> arguments(Map<String,Object> r)throws IOException {
        String operation=text(r,"operation"),edition=text(r,"edition");var p=object(r.get("parameters"),"parameters");var files=new LinkedHashMap<String,String>();for(var input:QtltoolsWorkflowContracts.inputs(r))files.put(input.key(),"../"+input.captured());
        var args=new ArrayList<String>();args.add(operation.startsWith("CIS_")?"cis":operation.startsWith("TRANS_")?"trans":operation.toLowerCase(java.util.Locale.ROOT));
        for(var pair:new java.util.TreeMap<>(Map.of("genotypes","vcf","phenotypes","bed","covariates","cov","alignment","bam","gtf","gtf","qtl","qtl","annotations","bed")).entrySet())if(files.containsKey(pair.getKey()))Collections.addAll(args,"--"+pair.getValue(),files.get(pair.getKey()));
        if(operation.equals("QUAN")) {
            args.add("--bam");for(var input:QtltoolsWorkflowContracts.inputs(r))if(input.key().matches("alignment[0-9]+"))args.add(files.get(input.key()));args.add(edition.equals("1.1")?"--samples":"--sample");for(Object name:list(p.get("sample_names"),"sample_names"))args.add(name.toString());
            Collections.addAll(args,"--out-prefix","../outputs/result");
            for(String key:List.of("rpkm","tpm","proper_pairs","consistency","no_merge","legacy_options","failed_qc","remove_duplicates"))if(Boolean.TRUE.equals(p.get(key)))args.add(switch(key){case "proper_pairs"->"--check-proper-pairing";case "consistency"->"--check-consistency";case "failed_qc"->"--filter-failed-qc";case "remove_duplicates"->"--filter-remove-duplicates";default->"--"+key.replace('_','-');});
            for(var entry:new java.util.TreeMap<>(Map.of("mapping_quality","filter-mapping-quality","minimum_exon","filter-min-exon","mismatch","filter-mismatch","mismatch_total","filter-mismatch-total")).entrySet())Collections.addAll(args,"--"+entry.getValue(),p.get(entry.getKey()).toString());
            if(p.containsKey("gene_types")){args.add("--gene-types");for(Object type:list(p.get("gene_types"),"gene_types"))args.add(type.toString());}
            if(p.containsKey("maximum_read_length"))Collections.addAll(args,"--max-read-length",p.get("maximum_read_length").toString());
            if(p.get("filename_hash").equals("none"))args.add("--no-hash");else if(p.get("filename_hash").equals("content"))args.add("--xxhash");
        }else Collections.addAll(args,"--out","../outputs/result");
        if(operation.startsWith("CIS_")||operation.startsWith("TRANS_")) {
            if(Boolean.TRUE.equals(p.get("normal")))args.add("--normal");Collections.addAll(args,"--window",p.get("window").toString());
            for(String key:QtltoolsWorkflowContracts.FILTERS.stream().sorted().toList())if(files.containsKey(key))Collections.addAll(args,"--"+key.replace('_','-'),files.get(key));
            if(p.containsKey("chunk")){args.add("--chunk");for(Object value:list(p.get("chunk"),"chunk"))args.add(value.toString());}
        }
        if(operation.startsWith("CIS_")) {
            String group=p.get("grouping").toString();if(!group.equals("none"))args.add("--grp-"+group);if(Boolean.TRUE.equals(p.get("standard_error")))args.add("--std-err");
            switch(operation){case "CIS_NOMINAL"->Collections.addAll(args,"--nominal",p.get("nominal_p").toString());case "CIS_PERMUTE"->Collections.addAll(args,"--permute",p.get("permutations").toString());case "CIS_CONDITIONAL"->Collections.addAll(args,"--mapping",files.get("thresholds"));default->throw new IOException("Unsupported cis operation");}
        }
        if(operation.startsWith("TRANS_")) {
            Collections.addAll(args,"--threshold",p.get("threshold").toString(),"--bins",p.get("bins").toString());
            switch(operation){case "TRANS_NOMINAL"->args.add("--nominal");case "TRANS_PERMUTE"->args.add("--permute");case "TRANS_SAMPLE"->Collections.addAll(args,"--sample",p.get("sample").toString());case "TRANS_ADJUST"->Collections.addAll(args,"--adjust",files.get("adjustment"));default->throw new IOException("Unsupported trans operation");}
        }
        if(operation.equals("FDENSITY"))Collections.addAll(args,"--window",p.get("window").toString(),"--bin",p.get("bin").toString());
        if(operation.equals("MBV")) {
            for(var entry:new java.util.TreeMap<>(Map.of("mapping_quality","filter-mapping-quality","base_quality","filter-base-quality","minimal_coverage","filter-minimal-coverage","binomial_p","filter-binomial-pvalue","imputation_quality","filter-imputation-qual","imputation_probability","filter-imputation-prob")).entrySet())Collections.addAll(args,"--"+entry.getValue(),p.get(entry.getKey()).toString());
            if(Boolean.TRUE.equals(p.get("keep_duplicates")))args.add("--filter-keep-duplicates");if(p.containsKey("region"))Collections.addAll(args,"--reg",p.get("region").toString());
        }else if(p.containsKey("region"))Collections.addAll(args,"--region",p.get("region").toString());
        Collections.addAll(args,"--seed",p.get("seed").toString());return List.copyOf(args);
    }
    static void run(Path root,Map<String,Object> r,Map<String,Object> manifest)throws Exception {
        var binding=object(r.get("runtime"),"runtime");boolean wsl=binding.containsKey("wsl_distribution");Path executable=Path.of(text(binding,"executable"));
        if(!wsl&&!System.getProperty("os.name").equals("Linux"))throw new Unavailable("The exact original builds require host-native Linux or explicit Windows/WSL transport");
        if(!Files.isRegularFile(executable,LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Supply the exact permitted executable: "+executable);
        var profile=object(object(profiles().get("profiles"),"profiles").get(text(r,"edition")),"edition profile");
        if(!FollowupSupport.hash(executable).equals(text(profile,"sha256"))||Files.size(executable)!=((Number)profile.get("bytes")).longValue())throw new Unavailable("Exact declared edition/build is unavailable;required SHA256 "+text(profile,"sha256")+". Do not substitute editions");
        var original=new ArrayList<String>();original.add(path(executable,wsl));original.addAll(arguments(r));var libraries=new ArrayList<String>();for(Object value:list(binding.get("library_directories"),"library_directories"))libraries.add(path(Path.of(value.toString()),wsl));
        String token=UUID.randomUUID().toString();var spec=Map.of("command",original,"profile",profile,"library_directories",libraries,"worker",path(root.resolve("worker"),wsl),"working_directory",path(root.resolve("worker"),wsl),"token",token);
        json(root.resolve("worker/spec.json"),spec);json(root.resolve("worker/runtime-bindings.json"),binding);json(root.resolve("worker/command.json"),Map.of("original_arguments",original,"edition",r.get("edition"),"operation",r.get("operation"),"relative_capture_paths",true));
        var command=new ArrayList<String>();if(wsl)command.addAll(List.of("wsl.exe","-d",text(binding,"wsl_distribution"),"--"));Collections.addAll(command,"python3",path(root.resolve("source/owned_worker.py"),wsl),"launch","--spec",path(root.resolve("worker/spec.json"),wsl));
        var builder=new ProcessBuilder(command).directory(root.resolve("worker").toFile()).redirectOutput(root.resolve("worker/stdout.log").toFile()).redirectError(root.resolve("worker/stderr.log").toFile());
        var baseline=PeerWorkflowRuntime.footprint(root);AtomicReference<Process> owned=new AtomicReference<>();Thread hook=new Thread(()->{Process process=owned.get();if(process!=null){try{cleanup(root,binding,wsl,token);}catch(Exception ignored){/* Exact ledger retained for recovery. */}process.destroyForcibly();}},"qtltools-owned-cleanup");Runtime.getRuntime().addShutdownHook(hook);Process process=null;long start=System.nanoTime();
        try {
            try{process=builder.start();}catch(IOException missing){throw new Unavailable("Python3/Linux/WSL transport cannot launch;provide the permitted environment",missing);}
            owned.set(process);process.getOutputStream().close();manifest.put("launcher_pid",process.pid());
            while(!process.waitFor(100,TimeUnit.MILLISECONDS)){if(System.nanoTime()-start>TimeUnit.SECONDS.toNanos(QtltoolsWorkflowContracts.limit(r,"timeout_seconds")))throw new IOException("Original QTLtools timed out;owned group cleanup requested");capacity(root,baseline,QtltoolsWorkflowContracts.limit(r,"maximum_output_bytes"));}
            capacity(root,baseline,QtltoolsWorkflowContracts.limit(r,"maximum_output_bytes"));manifest.put("process_exit_code",process.exitValue());
            if(process.exitValue()==3||process.exitValue()==127)throw new Unavailable("Original dependency or execution environment unavailable;read worker/unavailable.json and stderr.log");
            if(process.exitValue()!=0)throw new IOException("Original QTLtools failed;retain all raw outputs and logs");
            if(!Boolean.TRUE.equals(read(root.resolve("worker/owned.closed.json")).get("complete_cleanup")))throw new IOException("Owned process group did not close");
            if(!Boolean.TRUE.equals(read(root.resolve("worker/runtime-after.json")).get("unchanged")))throw new IOException("Runtime identity changed");
        }finally {
            boolean closed=Files.isRegularFile(root.resolve("worker/owned.closed.json"),LinkOption.NOFOLLOW_LINKS)&&Boolean.TRUE.equals(read(root.resolve("worker/owned.closed.json")).get("complete_cleanup"));
            if(process!=null&&(process.isAlive()||Thread.currentThread().isInterrupted()||!closed)) {
                boolean interrupted=Thread.interrupted();try{manifest.put("owned_cleanup",cleanup(root,binding,wsl,token));}catch(Exception error){manifest.put("owned_cleanup_error",error.toString());}finally{process.destroyForcibly();if(interrupted)Thread.currentThread().interrupt();}
            }
            owned.set(null);try{Runtime.getRuntime().removeShutdownHook(hook);}catch(IllegalStateException ignored){/* Shutdown hook owns cleanup. */}
        }
        if(!FollowupSupport.hash(executable).equals(text(profile,"sha256")))throw new IOException("Executable changed during source execution");
    }
    private static Map<String,Object> cleanup(Path root,Map<String,Object> binding,boolean wsl,String token)throws Exception {
        if(!Files.isRegularFile(root.resolve("worker/owned.json"),LinkOption.NOFOLLOW_LINKS))return Map.of("complete_cleanup",true,"original_group_started",false);
        var args=new ArrayList<String>();if(wsl)args.addAll(List.of("wsl.exe","-d",text(binding,"wsl_distribution"),"--"));Collections.addAll(args,"python3",path(root.resolve("source/owned_worker.py"),wsl),"stop","--ledger",path(root.resolve("worker/owned.json"),wsl),"--token",token);
        Process process=new ProcessBuilder(args).redirectOutput(root.resolve("worker/cleanup.json").toFile()).redirectError(root.resolve("worker/cleanup.stderr").toFile()).start();process.getOutputStream().close();if(!process.waitFor(20,TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException("Owned cleanup timed out;inspect only its ledger PID/group/token");}var result=read(root.resolve("worker/cleanup.json"));if(process.exitValue()!=0||!Boolean.TRUE.equals(result.get("complete_cleanup")))throw new IOException("Owned group cleanup incomplete");return result;
    }
    private static void capacity(Path root,Map<String,Long> baseline,long maximum)throws IOException {long bytes=0;for(var entry:PeerWorkflowRuntime.footprint(root).entrySet()){long growth=Math.max(0,entry.getValue()-baseline.getOrDefault(entry.getKey(),0L));if(growth>maximum-bytes)throw new IOException("maximum_output_bytes exceeded");bytes+=growth;}}
}
