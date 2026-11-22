/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;
import static org.jlinalg.cli.SkatWorkflowContracts.require;

/** Exact original source, declared R bindings and a pre-fit runtime fingerprint handshake. */
final class SkatWorkflowRuntime {
    private SkatWorkflowRuntime() { }
    static final String SOURCE_SHA="5caa7feef9969466e85624820b7f57001f90c1c3d43a15229add232a855a3166";
    static final String WORKER_SHA="85335f8e649a7854836e5f0865975a749ec91aae1162ec18ed73422c39923e0c";
    static final String RESOURCE="/org/jlinalg/omics/skat-workflow/";
    static final String SCOPE="Original SKAT2.2.5 function bodies/formals and declared R4.6.1/package versions; "
        +"recorded runtime/package/native-file bytes before the first fit and after the process. "
        +"Not full native-source authentication, host attestation, continuous file monitoring, an independent R fit or a complete convergence certificate.";

    record Outcome(String status,String detail,int nullFits,int setCalls,int diagnostics) {
        Map<String,Object> asMap(){return Map.of("status",status,"detail",detail,"original_null_fits",nullFits,"original_set_calls",setCalls,"genotype_diagnostic_calls",diagnostics);}
    }
    static byte[] resource(String name)throws IOException {
        require(!name.startsWith("/")&&!name.contains("\\")&&!name.contains(".."),"Unsafe packaged SKAT resource path");
        try(var input=SkatWorkflowRuntime.class.getResourceAsStream(RESOURCE+name)) {
            require(input!=null,"Missing required packaged SKAT source: "+name);return input.readAllBytes();
        }
    }
    static void matches(Map<String,Object> expected,Map<String,Object> actual,String label)throws IOException {
        require(expected.keySet().equals(Set.of("bytes","sha256"))&&actual.keySet().equals(expected.keySet()),"Invalid file fingerprint: "+label);
        require(expected.get("bytes") instanceof Number&&actual.get("bytes") instanceof Number,"Invalid fingerprint length: "+label);
        Number wanted=(Number)expected.get("bytes"),observed=(Number)actual.get("bytes");
        require(wanted.longValue()>=0&&wanted.doubleValue()==wanted.longValue()&&observed.longValue()>=0&&observed.doubleValue()==observed.longValue(),"Nonintegral/negative fingerprint length: "+label);
        require(expected.get("sha256") instanceof String&&actual.get("sha256") instanceof String,"Invalid fingerprint digest");
        require(wanted.longValue()==observed.longValue()&&expected.get("sha256").equals(actual.get("sha256")),"Changed fingerprint: "+label);
    }
    static Map<String,Object> classFingerprints()throws IOException {
        var classes=new TreeMap<String,Class<?>>();
        for(Class<?> type:List.of(SkatWorkflowRuntime.class,SkatWorkflowContracts.class,SkatWorkflowFiles.class,SkatWorkflowProcess.class,OwnedProcesses.class,SkatWorkflowAudit.class,SkatWorkflowCli.class,JLinAlgCli.class,org.jlinalg.settest.RSourceColumnSum.class,SeuratDifferentialContracts.class,RsemJson.class,SimpleJson.class,RegenieRuntime.class))collect(type,classes);
        try{collect(Class.forName("org.jlinalg.settest.RLongDoubleSum"),classes);}catch(ClassNotFoundException error){throw new IOException("Missing selected R column accumulator",error);}
        var result=new LinkedHashMap<String,Object>();
        for(var entry:classes.entrySet()) {
            String name="/"+entry.getKey().replace('.','/')+".class";
            try(var input=entry.getValue().getResourceAsStream(name)) {require(input!=null,"Missing executing class bytes: "+name);result.put(entry.getKey(),SkatWorkflowFiles.pin(input.readAllBytes()));}
        }
        return result;
    }
    private static void collect(Class<?> type,Map<String,Class<?>> result) {
        if(result.putIfAbsent(type.getName(),type)!=null)return;
        for(Class<?> nested:type.getDeclaredClasses())collect(nested,result);
    }
    static void sources(Path root,boolean compare)throws IOException {
        byte[] descriptor=resource("SOURCE.json"),worker=resource("skat_worker.R");
        require(SkatWorkflowFiles.hash(descriptor).equals(SOURCE_SHA),"Changed packaged original SKAT source descriptor");
        require(SkatWorkflowFiles.hash(worker).equals(WORKER_SHA),"Changed packaged original SKAT worker");
        Map<String,Object> profile=SkatWorkflowFiles.map(SimpleJson.parse(SkatWorkflowFiles.utf8(descriptor)));
        require(profile.get("profile").equals(SkatWorkflowContracts.PROFILE)&&((Number)profile.get("namespace_functions")).intValue()==239,"Changed original SKAT source profile");
        var expected=new HashSet<String>();
        for(var entry:SkatWorkflowFiles.map(profile.get("packaged_reference_files")).entrySet()) {
            String name=entry.getKey();Path relative=Path.of(name);
            require(name.startsWith("reference/")&&!relative.isAbsolute()&&relative.normalize().equals(relative)&&!name.contains("..")&&!name.contains("\\"),"Unsafe original reference path");
            byte[] bytes=resource(name);matches(SkatWorkflowFiles.map(entry.getValue()),SkatWorkflowFiles.pin(bytes),name);source(root.resolve("source/"+name),bytes,compare);expected.add(name);
        }
        require(expected.size()==302,"Changed original SKAT reference population");
        for(var entry:Map.of("SOURCE.json",descriptor,"skat_worker.R",worker).entrySet()){source(root.resolve("source/"+entry.getKey()),entry.getValue(),compare);expected.add(entry.getKey());}
        Path fingerprints=root.resolve("source/java-class-fingerprints.json");var classes=classFingerprints();
        if(compare) {
            require(Files.isRegularFile(fingerprints,LinkOption.NOFOLLOW_LINKS),"Missing captured Java producer fingerprints");
            var recorded=SkatWorkflowFiles.read(fingerprints,4_194_304);require(recorded.keySet().equals(classes.keySet()),"Changed Java producer class population");
            for(String name:classes.keySet())matches(SkatWorkflowFiles.map(recorded.get(name)),SkatWorkflowFiles.map(classes.get(name)),name);
        }else{Files.write(fingerprints,RsemJson.bytes(classes),StandardOpenOption.CREATE_NEW);}
        expected.add("java-class-fingerprints.json");
        try(var stream=Files.walk(root.resolve("source"))) {
            var actual=new HashSet<String>();
            for(Path path:stream.toList()) {
                require(!Files.isSymbolicLink(path)&&path.toRealPath().equals(path.toAbsolutePath().normalize()),"Aliased source capture node");
                if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))continue;require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Nonregular source capture node");
                actual.add(root.resolve("source").relativize(path).toString().replace('\\','/'));
            }
            require(actual.equals(expected),"Missing or extra captured original SKAT source members");
        }
    }
    private static void source(Path path,byte[] bytes,boolean compare)throws IOException {
        if(compare){require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Missing captured original source: "+path);require(Arrays.equals(bytes,SkatWorkflowFiles.bytes(path,Math.max(1,bytes.length))),"Changed captured original source: "+path);}
        else{Files.createDirectories(path.getParent());Files.write(path,bytes,StandardOpenOption.CREATE_NEW);}
    }
    static String unhex(String value)throws IOException {
        require(value.matches("(?:[0-9a-f]{2})*"),"Invalid original UTF-8 hexadecimal field");
        try{return SkatWorkflowFiles.utf8(HexFormat.of().parseHex(value));}catch(IllegalArgumentException error){throw new IOException("Invalid original hexadecimal field",error);}
    }
    static Map<String,String> environment() {
        var result=new LinkedHashMap<>(System.getenv());
        Set<String> remove=Set.of("LANG","LANGUAGE","R_LIBS","R_LIBS_USER","R_LIBS_SITE","R_PROFILE","R_PROFILE_USER","R_ENVIRON","R_ENVIRON_USER","R_DEFAULT_PACKAGES","R_TESTS","R_HOME");
        result.keySet().removeIf(k->k.toUpperCase(Locale.ROOT).startsWith("LC_")||remove.contains(k.toUpperCase(Locale.ROOT)));
        result.putAll(Map.of("LANG","C","LC_ALL","C","OMP_NUM_THREADS","1","OPENBLAS_NUM_THREADS","1","MKL_NUM_THREADS","1"));return result;
    }
    static Outcome outcome(Path root)throws IOException {
        var rows=new ArrayList<String[]>();List<String> header;
        try{header=SkatWorkflowFiles.rows(root.resolve("worker/worker-status.tsv"),65_536,5,1,(i,v)->rows.add(v));}
        catch(IOException error){throw new IOException("Original SKAT worker did not retain a valid final status;read raw stdout/stderr and preserve this partial attempt",error);}
        require(header.equals(List.of("status","detail_hex","original_null_fits","original_set_calls","genotype_diagnostic_calls"))&&rows.size()==1,"Invalid original worker status structure");
        String[] row=rows.get(0);require(Set.of("COMPLETED_WITH_WARNINGS","SKIP3_DEPENDENT_ANALYSIS_SKIPPED","FAIL2_NO_COMPLETE_RESULT").contains(row[0]),"Unknown original worker status");
        return new Outcome(row[0],unhex(row[1]),count(row[2]),count(row[3]),count(row[4]));
    }
    private static int count(String text)throws IOException {
        require(text.matches("0|[1-9][0-9]{0,9}"),"Invalid original call count");
        try{return Integer.parseInt(text);}catch(NumberFormatException error){throw new IOException("Original call count overflow",error);}
    }
    static Outcome run(Path root,Path requestDirectory,Map<String,Object> request,Map<String,Object> manifest)throws Exception {
        sources(root,true);Path declared=SkatWorkflowContracts.resolvePath(requestDirectory,request,"rscript");
        if(!Files.isRegularFile(declared)||!Files.isExecutable(declared))throw new SkatWorkflowContracts.Unavailable("Supply exact permitted R4.6.1 Rscript executable: "+declared);
        Path rscript=declared.toRealPath();var executable=SkatWorkflowFiles.pin(rscript);var libraries=new ArrayList<Path>();var libraryRows=new ArrayList<Map<String,Object>>();
        for(Object value:list(request.get("r_libraries"),"r_libraries")) {
            Path path=resolve(requestDirectory,value.toString());if(!Files.isDirectory(path))throw new SkatWorkflowContracts.Unavailable("Supply exact permitted declared R library directory: "+path);
            Path real=path.toRealPath();require(!libraries.contains(real),"Duplicate resolved R library directory");libraries.add(real);libraryRows.add(Map.of("declared",path.toString(),"resolved",real.toString()));
        }
        Path worker=root.resolve("worker");Files.writeString(worker.resolve("runtime-paths.txt"),String.join("\n",libraries.stream().map(Path::toString).toList())+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        String nonce=UUID.randomUUID().toString();Files.writeString(worker.resolve("launch-nonce.txt"),nonce+"\n",StandardCharsets.US_ASCII,StandardOpenOption.CREATE_NEW);
        var bindings=new LinkedHashMap<String,Object>();bindings.put("profile",SkatWorkflowContracts.PROFILE);bindings.put("request_directory",requestDirectory.toString());bindings.put("rscript",Map.of("declared",declared.toString(),"resolved",rscript.toString(),"fingerprint",executable));bindings.put("libraries",libraryRows);bindings.put("runtime_admission_scope",SCOPE);
        Files.write(worker.resolve("runtime-bindings.json"),RsemJson.bytes(bindings),StandardOpenOption.CREATE_NEW);manifest.put("runtime_admission_scope",SCOPE);
        Probe probe=new Probe(root,request,libraries,nonce,manifest);int code;
        try{code=SkatWorkflowProcess.execute(List.of(rscript.toString(),"--vanilla","../source/skat_worker.R"),environment(),root,request,manifest,probe::check);}
        finally{sources(root,true);matches(executable,SkatWorkflowFiles.pin(rscript),"declared Rscript executable");probe.recheck();}
        Outcome result=outcome(root);manifest.put("original_worker_status",result.asMap());
        if(code==3&&result.status().equals("SKIP3_DEPENDENT_ANALYSIS_SKIPPED")) {
            require(result.nullFits()==0&&result.setCalls()==0,"A dependency skip followed original model execution;cannot report an unexecuted analysis");
            throw new SkatWorkflowContracts.Unavailable(result.detail()+". Supply the exact permitted R/package profile named above;otherwise retain SKIP3 and skip the dependent analysis.");
        }
        require(code==0&&result.status().equals("COMPLETED_WITH_WARNINGS"),"Original SKAT failed;no complete result is admitted: "+result.detail());
        require(probe.acknowledged,"Original worker completed without the pre-fit fingerprint handshake");verifyNamespace(root,"namespace-after");return result;
    }
    static void verifyNamespace(Path root,String name)throws IOException {
        var expected=new HashSet<String>();var rows=new ArrayList<String[]>();
        List<String> header=SkatWorkflowFiles.rows(root.resolve("source/reference/functions.tsv"),1_048_576,2,239,(i,v)->rows.add(v));
        require(header.equals(List.of("function","file"))&&rows.size()==239,"Invalid original function reference index");
        for(String[] row:rows) {
            String file=row[0]+".txt";require(!file.contains("/")&&!file.contains("\\")&&expected.add(file),"Unsafe or duplicated function snapshot");
            Path path=root.resolve("worker/"+name+"/"+file);matches(SkatWorkflowFiles.pin(root.resolve("source/"+row[1])),SkatWorkflowFiles.pin(path),"original function "+row[0]+" "+name);
        }
        try(var stream=Files.list(root.resolve("worker/"+name))) {
            Set<String> actual=new HashSet<>();for(Path p:stream.toList()){require(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS),"Nonregular namespace snapshot");actual.add(p.getFileName().toString());}require(actual.equals(expected),"Changed namespace snapshot file population");
        }
    }
    private static final class Probe {
        private final Path root;private final Map<String,Object> request;private final List<Path> libraries;private final String nonce;private final Map<String,Object> manifest;
        private final Map<Path,Map<String,Object>> fingerprints=new LinkedHashMap<>();private boolean acknowledged;private int readyReadRetries;
        Probe(Path root,Map<String,Object> request,List<Path> libraries,String nonce,Map<String,Object> manifest){this.root=root;this.request=request;this.libraries=List.copyOf(libraries);this.nonce=nonce;this.manifest=manifest;}
        void check()throws IOException {
            if(acknowledged)return;Path ready=root.resolve("worker/runtime-ready.txt");if(!Files.exists(ready,LinkOption.NOFOLLOW_LINKS))return;
            require(Files.isRegularFile(ready,LinkOption.NOFOLLOW_LINKS)&&!Files.isSymbolicLink(ready),"Invalid owned runtime readiness marker");
            byte[] marker;
            try{marker=SkatWorkflowFiles.bytes(ready,64);}
            catch(FileSystemException error) {
                if(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")){readyReadRetries++;manifest.put("runtime_ready_transient_read_retries",readyReadRetries);return;}
                throw error;
            }
            require(SkatWorkflowFiles.utf8(marker).equals(nonce+"\n")||SkatWorkflowFiles.utf8(marker).equals(nonce+"\r\n"),"Changed runtime readiness nonce");
            sources(root,true);verifyNamespace(root,"namespace-before");
            for(String name:List.of("runtime-info.tsv","runtime-packages.tsv","runtime-files.tsv"))require(Files.isRegularFile(root.resolve("worker/"+name),LinkOption.NOFOLLOW_LINKS),"Ready worker omitted required runtime metadata: "+name);
            Map<String,String> info=keyValues(root.resolve("worker/runtime-info.tsv"));require(info.keySet().equals(Set.of("R_version","R_home","R_platform","namespace_functions"))&&info.get("R_version").equals("4.6.1")&&info.get("namespace_functions").equals("239"),"Changed reported R/function profile");
            if(!info.get("R_platform").startsWith("x86_64"))throw new SkatWorkflowContracts.Unavailable("The selected R4.6.1 column-sum audit requires an x86_64 R build;no model fit was acknowledged. Supply a permitted x86_64 runtime or retain this skipped analysis.");
            Path home=Path.of(SkatWorkflowContracts.pathText(info.get("R_home"))).toRealPath();require(Files.isDirectory(home),"Invalid reported R home");
            var allowed=new ArrayList<>(libraries);allowed.add(home);var packages=packageRows(root,request,libraries,home);var mandatory=new HashSet<Path>();
            for(Map<String,Object> row:packages)mandatory.addAll(requiredPackageFiles(Path.of(row.get("resolved").toString())));
            var requiredPackages=new HashSet<>(mandatory);mandatory.addAll(requiredRFiles(home));var raw=new ArrayList<String[]>();
            List<String> header=SkatWorkflowFiles.rows(root.resolve("worker/runtime-files.tsv"),SkatWorkflowFiles.cap(request,"maximum_member_bytes"),2,20_000,(i,v)->{require(v[0].equals(Integer.toString(i)),"Changed runtime file index");raw.add(v);});
            require(header.equals(List.of("index","path")),"Invalid runtime file table header");var seen=new HashSet<String>();var resolvedFiles=new HashSet<Path>();var records=new ArrayList<Map<String,Object>>();
            for(String[] row:raw) {
                require(seen.add(row[1]),"Duplicate declared runtime file");Path literal=Path.of(SkatWorkflowContracts.pathText(row[1]));require(literal.isAbsolute(),"Runtime file must be absolute");Path real=literal.toRealPath();require(allowed.stream().anyMatch(real::startsWith),"Runtime file is outside declared R/libraries: "+real);
                var fingerprint=SkatWorkflowFiles.pin(real);fingerprints.put(real,fingerprint);resolvedFiles.add(real);records.add(Map.of("origin","original-worker-table","reported",row[1],"resolved",real.toString(),"fingerprint",fingerprint));
            }
            require(resolvedFiles.containsAll(requiredPackages),"Reported runtime fingerprint population omits required installed package files");
            // R.home("bin") can identify bin/x64, while Java also sees the parent launchers.
            // Preserve the unmodified original table and explicitly add Java-discovered R files.
            for(Path real:mandatory)if(resolvedFiles.add(real)) {
                var fingerprint=SkatWorkflowFiles.pin(real);fingerprints.put(real,fingerprint);records.add(Map.of("origin","java-discovered-required-R-file","resolved",real.toString(),"fingerprint",fingerprint));
            }
            for(String name:List.of("runtime-info.tsv","runtime-packages.tsv","runtime-files.tsv","runtime-ready.txt")){Path path=root.resolve("worker/"+name);fingerprints.put(path,SkatWorkflowFiles.pin(path));}
            var report=new LinkedHashMap<String,Object>();report.put("runtime_info",info);report.put("packages",packages);report.put("files",records);report.put("mandatory_files",mandatory.stream().map(Path::toString).sorted().toList());report.put("scope",SCOPE);report.put("ready_transient_read_retries",readyReadRetries);
            Files.write(root.resolve("worker/runtime-fingerprints.json"),RsemJson.bytes(report),StandardOpenOption.CREATE_NEW);
            Path pending=root.resolve("worker/runtime-ack.pending"),ack=root.resolve("worker/runtime-ack.txt");require(!Files.exists(ack,LinkOption.NOFOLLOW_LINKS),"Unexpected preexisting fingerprint acknowledgment");Files.writeString(pending,nonce+"\n",StandardCharsets.US_ASCII,StandardOpenOption.CREATE_NEW);
            try{Files.move(pending,ack,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException error){throw new IOException("Cannot atomically publish the pre-fit fingerprint acknowledgment;analysis is unadmitted",error);}
            fingerprints.put(ack,SkatWorkflowFiles.pin(ack));manifest.put("runtime_fingerprinted_files",records.size());manifest.put("original_reported_runtime_files",raw.size());manifest.put("resolved_runtime_fingerprinted_files",resolvedFiles.size());manifest.put("runtime_package_built_warnings",packages.stream().filter(r->!r.get("built").toString().startsWith("R 4.6.1;")).map(r->r.get("package")+": "+r.get("built")).toList());acknowledged=true;
        }
        void recheck()throws IOException {for(var entry:fingerprints.entrySet())matches(entry.getValue(),SkatWorkflowFiles.pin(entry.getKey()),entry.getKey().toString());manifest.put("runtime_pre_fit_acknowledged",acknowledged);manifest.put("runtime_fingerprints_rechecked",!fingerprints.isEmpty());}
    }
    static Map<String,String> keyValues(Path path)throws IOException {
        var result=new LinkedHashMap<String,String>();List<String> header=SkatWorkflowFiles.rows(path,1_048_576,2,64,(i,v)->require(result.putIfAbsent(v[0],v[1])==null,"Duplicate runtime information key"));require(header.equals(List.of("key","value")),"Invalid runtime information header");return result;
    }
    private static List<Map<String,Object>> packageRows(Path root,Map<String,Object> request,List<Path> libraries,Path home)throws IOException {
        var expected=new LinkedHashMap<String,String>();List<String> expectedHeader=SkatWorkflowFiles.rows(root.resolve("source/reference/packages.tsv"),1_048_576,2,10,(i,v)->require(expected.putIfAbsent(v[0],v[1])==null,"Duplicate source package"));require(expectedHeader.equals(List.of("package","version"))&&expected.size()==10,"Invalid exact package reference");
        var result=new ArrayList<Map<String,Object>>();var names=new ArrayList<>(expected.keySet());var roots=new ArrayList<>(libraries);roots.add(home.resolve("library").toRealPath());
        List<String> header=SkatWorkflowFiles.rows(root.resolve("worker/runtime-packages.tsv"),SkatWorkflowFiles.cap(request,"maximum_member_bytes"),5,10,(i,v)->{
            require(i<=names.size()&&v[0].equals(names.get(i-1))&&v[1].equals(expected.get(v[0]))&&v[2].equals(v[1].replace('-','.')),"Changed exact package/version/order");
            Path path=Path.of(SkatWorkflowContracts.pathText(v[4]));require(path.isAbsolute(),"Reported package path must be absolute");Path real=path.toRealPath();require(roots.stream().anyMatch(p->real.equals(p.resolve(v[0]))),"Package lies outside exact declared library search roots");
            String built=unhex(v[3]);result.add(Map.of("package",v[0],"version",v[1],"canonical_version",v[2],"built",built,"reported",v[4],"resolved",real.toString()));
        });require(header.equals(List.of("package","version","canonical_version","built_hex","path"))&&result.size()==10,"Incomplete runtime package metadata");return result;
    }
    private static Set<Path> requiredPackageFiles(Path directory)throws IOException {
        var result=new HashSet<Path>();try(var stream=Files.walk(directory)){
            for(Path path:stream.toList())if(Files.isRegularFile(path)){String name=directory.relativize(path).toString().replace('\\','/');if(name.equals("DESCRIPTION")||name.equals("NAMESPACE")||name.matches("R/[^/]+\\.(rdb|rdx)")||name.matches("libs/.*\\.(dll|so)")||name.matches("Meta/[^/]+\\.rds"))result.add(path.toRealPath());}
        }
        require(result.contains(directory.resolve("DESCRIPTION").toRealPath())&&result.contains(directory.resolve("NAMESPACE").toRealPath()),"Missing required installed package metadata");return result;
    }
    private static Set<Path> requiredRFiles(Path home)throws IOException {
        var result=new HashSet<Path>();for(String part:List.of("bin","lib")){Path directory=home.resolve(part);if(!Files.isDirectory(directory))continue;try(var stream=Files.walk(directory)){
            for(Path path:stream.toList())if(Files.isRegularFile(path)){String name=path.getFileName().toString();if(part.equals("bin")&&name.matches(".*\\.(dll|exe)")||part.equals("lib")&&name.matches(".*\\.so(?:\\.[0-9.]+)?"))result.add(path.toRealPath());}
        }}return result;
    }
}
