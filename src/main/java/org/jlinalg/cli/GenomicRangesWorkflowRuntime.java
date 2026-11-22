/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
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
import static org.jlinalg.cli.GenomicRangesWorkflowContracts.require;

/** Original R source checks, explicit runtime identity and pre-call fingerprint ACK. */
final class GenomicRangesWorkflowRuntime {
    private GenomicRangesWorkflowRuntime() { }
    static final String RESOURCE="/org/jlinalg/omics/genomicranges-workflow/";
    static final String FILE_SCOPE="Package execution/data/identity files,R binaries and loaded native libraries. Top-level package help/html/doc/demo/tests/unitTests directories are excluded;full official download archive hashes are retained separately. No native-source or host attestation.";
    static final String SOURCE_SHA="7eda1e55a9277fd737c88b64995e74ce2543076a74ad28224a2d037f6016b71d";
    static byte[] resource(String name)throws IOException {
        require(!name.startsWith("/")&&!name.contains("..")&&!name.contains("\\"),"Unsafe packaged source path");
        try(var stream=GenomicRangesWorkflowRuntime.class.getResourceAsStream(RESOURCE+name)) {require(stream!=null,"Missing packaged GenomicRanges source member: "+name);return stream.readAllBytes();}
    }
    static Map<String,Object> descriptor()throws IOException {
        byte[] raw=resource("SOURCE.json");require(GenomicRangesWorkflowFiles.hash(raw).equals(SOURCE_SHA),"Changed packaged source descriptor");return GenomicRangesWorkflowFiles.map(SimpleJson.parse(GenomicRangesWorkflowFiles.utf8(raw)));
    }
    static Map<String,Object> profile(String id)throws IOException {
        var all=GenomicRangesWorkflowFiles.map(descriptor().get("profiles"));require(all.containsKey(id),"Unknown declared exact GenomicRanges profile");return GenomicRangesWorkflowFiles.map(all.get(id));
    }
    static String hex(String value){return HexFormat.of().formatHex(value.getBytes(StandardCharsets.UTF_8));}
    static String unhex(String value)throws IOException {
        require(value.matches("(?:[0-9a-f]{2})*"),"Invalid UTF-8 hexadecimal field");return GenomicRangesWorkflowFiles.utf8(HexFormat.of().parseHex(value));
    }
    static void matches(Map<String,Object> expected,Map<String,Object> actual)throws IOException {
        require(expected.keySet().equals(Set.of("bytes","sha256"))&&actual.keySet().equals(expected.keySet()),"Invalid file fingerprint schema");
        long first=GenomicRangesWorkflowContracts.integer(expected.get("bytes"),0,Long.MAX_VALUE),second=GenomicRangesWorkflowContracts.integer(actual.get("bytes"),0,Long.MAX_VALUE);
        require(expected.get("sha256") instanceof String&&((String)expected.get("sha256")).matches("[0-9a-f]{64}")&&actual.get("sha256") instanceof String&&((String)actual.get("sha256")).matches("[0-9a-f]{64}"),"Invalid selected SHA-256 digest");
        require(first==second&&expected.get("sha256").equals(actual.get("sha256")),"Changed selected file fingerprint");
    }
    static Map<String,byte[]> sources(String id)throws IOException {
        var d=descriptor();var p=profile(id);var result=new LinkedHashMap<String,byte[]>();result.put("SOURCE.json",resource("SOURCE.json"));
        for(var item:Map.of("worker","genomicranges_worker.R","notice","NOTICE.txt","license","LICENSE-Artistic-2.0.txt").entrySet()) {
            byte[] raw=resource(item.getValue());matches(GenomicRangesWorkflowFiles.map(d.get(item.getKey())),GenomicRangesWorkflowFiles.pin(raw));result.put(item.getValue().equals("genomicranges_worker.R")?"worker.R":item.getValue(),raw);
        }
        for(var item:GenomicRangesWorkflowFiles.map(p.get("source_files")).entrySet()) {
            byte[] raw=resource("profiles/"+p.get("version")+"/"+item.getKey());matches(GenomicRangesWorkflowFiles.map(item.getValue()),GenomicRangesWorkflowFiles.pin(raw));result.put(item.getKey(),raw);
        }
        return result;
    }
    static void captureSources(Path root,String id,boolean compare)throws IOException {
        var expected=sources(id);var classes=classes();Path fingerprints=root.resolve("source/java-classes.json");
        if(compare){var saved=GenomicRangesWorkflowFiles.read(fingerprints,4194304);require(saved.keySet().equals(classes.keySet()),"Changed producer class population");for(String name:classes.keySet())matches(GenomicRangesWorkflowFiles.map(saved.get(name)),GenomicRangesWorkflowFiles.map(classes.get(name)));}
        else Files.write(fingerprints,RsemJson.bytes(classes),StandardOpenOption.CREATE_NEW);
        for(var entry:expected.entrySet()) {
            Path p=root.resolve("source").resolve(entry.getKey());
            if(compare)require(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&Arrays.equals(entry.getValue(),GenomicRangesWorkflowFiles.bytes(p,entry.getValue().length)),"Changed captured producer/source member: "+entry.getKey());
            else Files.write(p,entry.getValue(),StandardOpenOption.CREATE_NEW);
        }
        var names=new HashSet<>(expected.keySet());names.add("java-classes.json");try(var walk=Files.list(root.resolve("source"))) {require(walk.map(p->p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()).equals(names),"Missing/extra captured source members");}
    }
    static Map<String,Object> classes()throws IOException {
        var types=new TreeMap<String,Class<?>>();
        for(Class<?> type:List.of(GenomicRangesWorkflowRuntime.class,GenomicRangesWorkflowContracts.class,GenomicRangesWorkflowFiles.class,GenomicRangesWorkflowProcess.class,OwnedProcesses.class,GenomicRangesWorkflowAudit.class,GenomicRangesToolsCli.class,SimpleJson.class,RsemJson.class,SeuratDifferentialContracts.class))collect(type,types);
        var result=new LinkedHashMap<String,Object>();
        for(var item:types.entrySet())try(var input=item.getValue().getResourceAsStream("/"+item.getKey().replace('.','/')+".class")){require(input!=null,"Missing executing producer class bytes");result.put(item.getKey(),GenomicRangesWorkflowFiles.pin(input.readAllBytes()));}
        return result;
    }
    private static void collect(Class<?> type,Map<String,Class<?>> types){if(types.putIfAbsent(type.getName(),type)!=null)return;for(Class<?> child:type.getDeclaredClasses())collect(child,types);}
    static Map<String,String> environment() {
        var result=new LinkedHashMap<>(System.getenv());var remove=Set.of("LANG","LANGUAGE","R_LIBS","R_LIBS_USER","R_LIBS_SITE","R_PROFILE","R_PROFILE_USER","R_ENVIRON","R_ENVIRON_USER","R_DEFAULT_PACKAGES","R_TESTS","R_HOME");
        result.keySet().removeIf(k->k.toUpperCase(Locale.ROOT).startsWith("LC_")||remove.contains(k.toUpperCase(Locale.ROOT)));
        result.putAll(Map.of("LANG","C","LC_ALL","C","OMP_NUM_THREADS","1","OPENBLAS_NUM_THREADS","1","MKL_NUM_THREADS","1"));return result;
    }
    static void controls(Path root,Map<String,Object> request,boolean compare)throws IOException {
        Map<String,Object> p=profile(request.get("profile").toString()),limits=GenomicRangesWorkflowFiles.map(request.get("limits"));
        var controls=new LinkedHashMap<String,String>();controls.put("operation",request.get("operation").toString());controls.put("profile",request.get("profile").toString());controls.put("r_version",p.get("R").toString());controls.put("package_version",p.get("version").toString());controls.put("subject_mode",request.get("subject_mode").toString());controls.put("maximum_rows",limits.get("maximum_rows").toString());controls.put("maximum_pairs",limits.get("maximum_pairs").toString());controls.put("ready_timeout_seconds",Long.toString(Math.min(3600,((Number)limits.get("timeout_seconds")).longValue())));
        StringBuilder table=new StringBuilder("key\tvalue_hex\n");for(var c:controls.entrySet())table.append(c.getKey()).append('\t').append(hex(c.getValue())).append('\n');control(root.resolve("control.tsv"),table,compare);
        table=new StringBuilder("path_hex\n");for(Object path:(List<?>)request.get("r_libraries"))table.append(hex(path.toString())).append('\n');control(root.resolve("libraries.tsv"),table,compare);
        table=new StringBuilder("key\ttype\tvalue_hex\n");
        for(var c:GenomicRangesWorkflowFiles.map(request.get("options")).entrySet()) {
            Object value=c.getValue();String type,valueText;
            if(GenomicRangesWorkflowContracts.OPTIONS.get(request.get("operation").toString()).get(c.getKey()).equals("number-vector")){type="numeric";valueText=value instanceof List<?> values?values.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(",")):value.toString();}
            else if(value instanceof Boolean flag){type="boolean";valueText=flag?"TRUE":"FALSE";}
            else if(value instanceof Number){type="integer";valueText=value.toString();}
            else if(value instanceof List<?> values){type="integer";valueText=values.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(","));}
            else{type="string";valueText=value.toString();}
            table.append(c.getKey()).append('\t').append(type).append('\t').append(hex(valueText)).append('\n');
        }
        control(root.resolve("options.tsv"),table,compare);
    }
    private static void control(Path path,StringBuilder value,boolean compare)throws IOException {
        byte[] raw=value.toString().getBytes(StandardCharsets.UTF_8);if(compare)require(Arrays.equals(GenomicRangesWorkflowFiles.bytes(path,raw.length),raw),"Operation control differs from validated request: "+path.getFileName());else Files.write(path,raw,StandardOpenOption.CREATE_NEW);
    }
    static Map<String,Object> fingerprintRoots(List<Path> roots)throws IOException {
        var result=new TreeMap<String,Object>();
        for(Path root:roots) {
            if(Files.isRegularFile(root,LinkOption.NOFOLLOW_LINKS)){result.put(root.toRealPath().toString(),GenomicRangesWorkflowFiles.pin(root));continue;}
            require(Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS),"Missing/nonregular declared runtime root: "+root);
            Files.walkFileTree(root,new java.nio.file.SimpleFileVisitor<Path>(){
                @Override public java.nio.file.FileVisitResult preVisitDirectory(Path directory,java.nio.file.attribute.BasicFileAttributes attributes)throws IOException {
                    require(!attributes.isSymbolicLink()&&directory.toRealPath().equals(directory.toAbsolutePath().normalize()),"Aliased runtime directory: "+directory);
                    if(directory.getParent()!=null&&directory.getParent().equals(root)&&Set.of("help","html","doc","demo","tests","unitTests").contains(directory.getFileName().toString()))return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
                @Override public java.nio.file.FileVisitResult visitFile(Path file,java.nio.file.attribute.BasicFileAttributes attributes)throws IOException {
                    require(attributes.isRegularFile()&&!attributes.isSymbolicLink()&&file.toRealPath().equals(file.toAbsolutePath().normalize()),"Nonregular/aliased runtime member: "+file);
                    result.put(file.toRealPath().toString(),GenomicRangesWorkflowFiles.pin(file));require(result.size()<=50000,"Runtime file fingerprint capacity exceeded");return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        }
        long total=0;for(Object value:result.values()){total=Math.addExact(total,((Number)GenomicRangesWorkflowFiles.map(value).get("bytes")).longValue());require(total<=4294967296L,"Runtime fingerprint byte capacity exceeded");}return result;
    }
    static Map<String,String> textRows(Path path,String header)throws IOException {
        List<String> lines=Files.readAllLines(path,StandardCharsets.UTF_8);require(!lines.isEmpty()&&lines.get(0).equals(header),"Invalid runtime marker header");var result=new LinkedHashMap<String,String>();
        for(String row:lines.subList(1,lines.size())){String[] cells=row.split("\t",-1);require(cells.length==2&&result.putIfAbsent(cells[0],cells[1])==null,"Invalid duplicate runtime marker row");}return result;
    }
    static void savedHandshake(Path root,Map<String,Object> request,Map<String,Object> manifest,Map<String,Object> recordedFiles)throws IOException {
        require(FILE_SCOPE.equals(manifest.get("runtime_fingerprint_scope")),"Recorded runtime fingerprint scope differs from this producer");
        String id=request.get("profile").toString();
        require(Files.readAllLines(root.resolve("worker/READY")).equals(List.of("GENOMICRANGES_PREFLIGHT_V1",id))&&Files.readAllLines(root.resolve("worker/ACK")).equals(List.of("GENOMICRANGES_ACK_V1",id)),"Recorded pre-call handshake differs from declared profile");
        var expected=GenomicRangesWorkflowFiles.map(profile(id).get("dependencies"));var seen=new HashSet<String>();List<String> lines=Files.readAllLines(root.resolve("worker/runtime-packages.tsv"),StandardCharsets.UTF_8);require(!lines.isEmpty()&&lines.get(0).equals("package\tversion\tpath_hex"),"Invalid recorded runtime package header");
        for(String line:lines.subList(1,lines.size())){String[] r=line.split("\t",-1);require(r.length==3&&seen.add(r[0])&&expected.containsKey(r[0])&&expected.get(r[0]).equals(r[1])&&Path.of(unhex(r[2])).isAbsolute(),"Recorded package edition/path differs from exact profile");}
        require(seen.equals(expected.keySet()),"Recorded runtime dependency population differs");
        Object commandValue=manifest.get("command");require(commandValue instanceof List<?>,"Missing original process command");List<?> command=(List<?>)commandValue;require(command.size()==4&&command.get(1).equals("--vanilla")&&Path.of(command.get(0).toString()).equals(Path.of(request.get("rscript").toString())),"Recorded original process command/executable differs");
        require(recordedFiles.keySet().stream().anyMatch(p->Path.of(p).equals(Path.of(command.get(0).toString()))),"Recorded runtime fingerprints omit actual Rscript");
        GenomicRangesWorkflowContracts.integer(manifest.get("process_exit_code"),0,0);GenomicRangesWorkflowContracts.integer(manifest.get("worker_elapsed_millis"),0,Long.MAX_VALUE);
        require(manifest.get("owned_processes") instanceof List<?>,"Missing owned-process cleanup population");List<?> processes=(List<?>)manifest.get("owned_processes");require(!processes.isEmpty(),"Missing original worker process identity");for(Object process:processes){var row=GenomicRangesWorkflowFiles.map(process);require(row.keySet().equals(Set.of("pid","start","same_identity_alive"))&&Boolean.FALSE.equals(row.get("same_identity_alive")),"Recorded original worker/observed child remains live");GenomicRangesWorkflowContracts.integer(row.get("pid"),1,Long.MAX_VALUE);try{java.time.Instant.parse(row.get("start").toString());}catch(java.time.format.DateTimeParseException e){throw new IOException("Invalid owned process start identity",e);}}
    }
    private static List<Path> runtimeRoots(Path root,Map<String,Object> request)throws IOException {
        var expected=GenomicRangesWorkflowFiles.map(profile(request.get("profile").toString()).get("dependencies"));var seen=new HashSet<String>();var paths=new ArrayList<Path>();Path base=null;
        List<String> lines=Files.readAllLines(root.resolve("worker/runtime-packages.tsv"),StandardCharsets.UTF_8);require(lines.get(0).equals("package\tversion\tpath_hex"),"Invalid runtime package handshake");
        for(String line:lines.subList(1,lines.size())){
            String[] row=line.split("\t",-1);require(row.length==3&&seen.add(row[0])&&expected.containsKey(row[0])&&expected.get(row[0]).equals(row[1]),"Wrong/missing runtime package identity");Path path=Path.of(unhex(row[2])).toAbsolutePath().normalize();if(!Files.isDirectory(path))throw new GenomicRangesWorkflowContracts.Unavailable("Resolved package path is unavailable: "+row[0]);paths.add(path);if(row[0].equals("base"))base=path;
        }
        require(seen.equals(expected.keySet())&&base!=null,"Runtime namespace population differs from declared exact dependency profile");
        Path home=base.getParent().getParent();paths.add(home.resolve("bin"));paths.add(Path.of(request.get("rscript").toString()));
        for(var item:textRows(root.resolve("worker/runtime-dlls.tsv"),"name_hex\tpath_hex").entrySet()) {
            String name=unhex(item.getKey()),dll=unhex(item.getValue());if(name.equals("base")&&dll.equals("base"))continue;Path path=Path.of(dll);require(path.isAbsolute(),"Unresolved native runtime path");paths.add(path);
        }
        return List.copyOf(paths);
    }
    static int execute(Path root,Map<String,Object> request,Map<String,Object> manifest)throws Exception {
        manifest.put("runtime_fingerprint_scope",FILE_SCOPE);
        Path executable=Path.of(request.get("rscript").toString());if(!Files.isRegularFile(executable)||!Files.isExecutable(executable))throw new GenomicRangesWorkflowContracts.Unavailable("Supply exact permitted profile Rscript: "+executable);
        for(Object path:(List<?>)request.get("r_libraries"))if(!Files.isDirectory(Path.of(path.toString())))throw new GenomicRangesWorkflowContracts.Unavailable("Supply/install exact permitted private R library: "+path);
        var immutable=new TreeMap<Path,Map<String,Object>>();for(String dir:List.of("source","inputs"))try(var stream=Files.walk(root.resolve(dir))){for(Path path:stream.filter(Files::isRegularFile).toList())immutable.put(path,GenomicRangesWorkflowFiles.pin(path));}
        for(String name:List.of("request.json","control.tsv","options.tsv","libraries.tsv")){Path p=root.resolve(name);immutable.put(p,GenomicRangesWorkflowFiles.pin(p));}
        var stamps=new LinkedHashMap<Path,java.nio.file.attribute.FileTime>();for(Path p:immutable.keySet())stamps.put(p,Files.getLastModifiedTime(p));
        final class Handshake implements GenomicRangesWorkflowProcess.Check {
            List<Path> roots;Map<String,Object> before;
            public void verify()throws IOException {
                for(var entry:stamps.entrySet())require(Files.isRegularFile(entry.getKey(),LinkOption.NOFOLLOW_LINKS)&&Files.getLastModifiedTime(entry.getKey()).equals(entry.getValue())&&Files.size(entry.getKey())==((Number)immutable.get(entry.getKey()).get("bytes")).longValue(),"Immutable captured input/source changed during original worker");
                Path ready=root.resolve("worker/READY");if(before!=null||!Files.exists(ready))return;
                require(Files.readAllLines(ready).equals(List.of("GENOMICRANGES_PREFLIGHT_V1",request.get("profile").toString())),"Invalid original runtime-ready marker");roots=runtimeRoots(root,request);before=fingerprintRoots(roots);GenomicRangesWorkflowFiles.json(root.resolve("runtime-files-before.json"),before);
                Path ack=root.resolve("worker/ACK.pending");Files.writeString(ack,"GENOMICRANGES_ACK_V1\n"+request.get("profile")+"\n",StandardOpenOption.CREATE_NEW);Files.move(ack,root.resolve("worker/ACK"));
            }
        }
        Handshake handshake=new Handshake();int code=GenomicRangesWorkflowProcess.execute(List.of(executable.toRealPath().toString(),"--vanilla",root.resolve("source/worker.R").toString(),root.toString()),environment(),root,request,manifest,handshake);
        for(var item:immutable.entrySet())require(item.getValue().equals(GenomicRangesWorkflowFiles.pin(item.getKey())),"Captured input/source changed before/after original operation");
        if(handshake.before!=null){var after=fingerprintRoots(handshake.roots);GenomicRangesWorkflowFiles.json(root.resolve("runtime-files-after.json"),after);require(after.equals(handshake.before),"Selected runtime files changed before/after original operation");}
        if(code==0)require(handshake.before!=null,"Original operation lacks a completed runtime fingerprint handshake");return code;
    }
}
