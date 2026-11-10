/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.KingRequest.need;
import static org.jlinalg.cli.KingAudit.map;

/** Supervised exact original KING2.3.2 execution; no original software is bundled. */
public final class KingToolsCli {
    private KingToolsCli() { }
    static final String SCOPE = "Original KING2.3.2 kinship, unrelated selection, duplicate screening and Linux IBD coordinates on complete caller-selected coordinate-sorted diploid autosomal A/C/G/T BED data. Original reports are retained byte-for-byte. Complete native hard-call counts verify reported kinship/concordance fields; segment endpoints and reported fractions are checked against captured BIM coordinates. Pedigree values, segment placement, candidate-screen completeness, inference labels and unrelated selection are not independently refitted. Rounded source values cannot establish exact paper thresholds. Missing reports or unreported pairs do not establish biological absence or unrelatedness. This is not PLINK2-hosted KING, SNPRelate, historical KING2.0/2.2.7, arbitrary multi-file merging or a paper reproduction. Capacities are logical checks, not hard OS quotas. File hashes are integrity checks, not producer authentication. Windows system DLLs are not independently pinned.";
    static final String ACTION = "Require exit0, result_admitted=true and successful complete --verify; read WARNINGS.md, provenance.json and original stdout/stderr. SKIP3: skip the dependent analysis, supply the named exact permitted private research-use KING runtime with the exported setup helper, allowing download only where permitted. If installation is forbidden or prerequisites cannot be supplied, keep analysis skipped. For ibdseg explicitly select Linux Java and its original Linux runtime. FAIL2: admit no output; preserve the attempt, inspect the named cause and original logs, correct inputs/options explicitly and retry NEW paths. Never silently regenerate genotypes, rename IDs, substitute versions or accept a source fallback to another method. Source-zero coefficients with zero heterozygosity are unassessable. Original KING programs/source/archives must stay private under their author terms.";
    private static final Set<String> LIBRARIES = Set.of("libz.so.1","libquadmath.so.0","libm.so.6","libgomp.so.1","libpthread.so.0","libc.so.6","ld-linux-x86-64.so.2");
    private record Runtime(String platform, Path executable, Map<Path,Pin> files, Map<String,String> lddPaths, byte[] raw) { }
    private static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message) { super(message); }
    }
    public static void main(String[] args) { System.exit(run(args,System.out,System.err)); }
    public static int run(String[] args,PrintStream output,PrintStream error) {
        Path out=null;int exit=2;var warnings=new ArrayList<String>();warnings.add(SCOPE);
        var state=map("status","FAIL2_NO_KING_RESULT_ADMITTED","result_admitted",false);
        try {
            if(args.length==1&&args[0].equals("--help")) {
                output.println("king-tools --request JSON | --verify BUNDLE | --export-runtime-setup NEW_DIRECTORY\n"+SCOPE+"\n"+ACTION);return 0;
            }
            if(args.length==2&&args[0].equals("--export-runtime-setup")) {
                Path target=Path.of(args[1]).toAbsolutePath().normalize();
                need(!Files.exists(target,LinkOption.NOFOLLOW_LINKS),"Setup target exists; choose NEW paths");
                byte[] source;
                try(var stream=KingToolsCli.class.getResourceAsStream("/org/jlinalg/omics/king/setup_king_runtime.py")) {
                    need(stream!=null,"Missing packaged setup helper");source=stream.readAllBytes();
                }
                Files.createDirectories(target.getParent());Files.createDirectory(target);
                Files.write(target.resolve("setup_king_runtime.py"),source,StandardOpenOption.CREATE_NEW);
                Files.writeString(target.resolve("WARNINGS.md"),SCOPE+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                output.println("Exported runtime-only helper; no download, installation or analysis performed.\n"+ACTION);return 0;
            }
            if(args.length==2&&args[0].equals("--verify")) {
                verify(Path.of(args[1]).toAbsolutePath().normalize());
                output.println("PASS complete original KING bundle and native transport replay; no upstream execution or producer authentication.");return 0;
            }
            need(args.length==2&&args[0].equals("--request"),"Require --request JSON, --verify BUNDLE or --help");
            Path source=Path.of(args[1]).toAbsolutePath().normalize().toRealPath();
            byte[] raw=bytes(source,1_048_576);
            KingRequest.Request request=KingRequest.parse(NextflowParameters.parse(utf8(raw),1_048_576,100_000),source.getParent());
            Path target=resolved(request.out());
            need(!Files.exists(target,LinkOption.NOFOLLOW_LINKS),"Output exists; preserve it and choose NEW paths");
            for(Path path:List.of(source,request.profile(),bed(request,"bed"),bed(request,"bim"),bed(request,"fam")))separate(target,path);
            Files.createDirectories(target.getParent());out=Files.createDirectory(target);
            Files.write(out.resolve("source-request.json"),raw,StandardOpenOption.CREATE_NEW);
            Files.writeString(out.resolve("WARNINGS.initial.md"),"# PROVISIONAL: NO KING RESULT ADMITTED\n\n"+SCOPE+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            long start=System.nanoTime();Runnable check=()->{
                if(Thread.currentThread().isInterrupted()||System.nanoTime()-start>Duration.ofSeconds(request.limits().wallSeconds()).toNanos())
                    throw new java.util.concurrent.CancellationException("Complete KING wall-time/interruption capacity exceeded");
            };
            Runtime runtime=runtime(request.profile());
            if(request.mode().equals("ibdseg")&&runtime.platform().equals("windows"))
                throw new Unavailable("Tested Windows2.3.2 lacks coordinate-level IBD output. Run this explicit ibdseg request with Linux Java and the original Linux profile; no automatic switch performed.");
            for(Path path:runtime.files().keySet())separate(out,path);
            Files.write(out.resolve("runtime-profile.json"),runtime.raw(),StandardOpenOption.CREATE_NEW);
            runtimePins(runtime,check);
            Files.createDirectory(out.resolve("inputs"));
            var original=new LinkedHashMap<Path,Pin>();long total=0;
            for(String extension:List.of("bed","bim","fam")) {
                Path input=bed(request,extension).toRealPath(),copy=out.resolve("inputs/cohort."+extension);
                Pin before=pin(input,request.limits().inputBytes(),check);total=Math.addExact(total,before.bytes());
                need(total<=request.limits().inputBytes(),"Complete BED/BIM/FAM input byte capacity exceeded");
                try(var in=Files.newInputStream(input);var dest=Files.newOutputStream(copy,StandardOpenOption.CREATE_NEW)) {
                    byte[] buffer=new byte[65_536];long count=0;
                    for(int n;(n=in.read(buffer))!=-1;) {check.run();count=Math.addExact(count,n);need(count<=before.bytes(),"Input grew during complete capture");dest.write(buffer,0,n);}
                }
                need(before.equals(pin(input,before.bytes(),check))&&before.equals(pin(copy,before.bytes(),check)),"Input changed during complete capture");
                original.put(input,before);
            }
            // Validate the entire data/operation capacity before launching any analysis process.
            KingAudit.inputs(out,request,check);
            Files.createDirectory(out.resolve("worker"));var processes=new ArrayList<Map<String,Object>>();
            if(runtime.platform().equals("linux"))dependencies(runtime,out,request,check,"dependencies-before",processes);
            List<String> command=command(runtime.platform(),runtime.executable().toString(),out.toString(),request);
            Map<String,Object> process=execute(out,"analysis",command,runtime.platform(),request,check);processes.add(process);
            need(((Number)process.get("exit_code")).intValue()==0,"Original KING failed with exit"+process.get("exit_code")+"; inspect retained unadmitted-worker/analysis.stdout and analysis.stderr");
            if(runtime.platform().equals("linux"))dependencies(runtime,out,request,check,"dependencies-after",processes);
            for(var entry:original.entrySet()) {
                need(entry.getValue().equals(pin(entry.getKey(),entry.getValue().bytes(),check)),"Original input changed during KING execution");
                String name=entry.getKey().toString();String extension=name.substring(name.lastIndexOf('.')+1);
                need(entry.getValue().equals(pin(out.resolve("inputs/cohort."+extension),entry.getValue().bytes(),check)),"Captured original input changed during KING execution");
            }
            runtimePins(runtime,check);
            need(sha(raw).equals(pin(source,raw.length,check).sha256()),"Request changed during execution");
            need(sha(runtime.raw()).equals(pin(request.profile().toRealPath(),runtime.raw().length,check).sha256()),"Runtime profile changed during execution");
            Map<String,Object> audit=KingAudit.verify(out,out.resolve("worker"),request,runtime.platform(),true,check);
            writeJson(out.resolve("provenance.json"),audit);
            var sources=new LinkedHashMap<String,Object>();for(var entry:original.entrySet())sources.put(entry.getKey().toString(),entry.getValue());
            writeJson(out.resolve("execution.json"),map("platform",runtime.platform(),"bundle_at_execution",out.toString(),
                    "executable_at_execution",runtime.executable().toString(),"original_inputs",sources,"steps",processes));
            Files.move(out.resolve("worker"),out.resolve("results"));
            warnings.addAll(scientificWarnings(audit,request));bounds(out,request.limits(),check);
            state.put("status","completed-original-king-with-warnings");state.put("result_admitted",true);exit=0;
        } catch(Unavailable failure) {
            exit=3;state.put("status","SKIP3_KING_DEPENDENCY_UNAVAILABLE");state.put("cause",failure.toString());warnings.add("SKIP3 DEPENDENT ANALYSIS SKIPPED: "+failure.getMessage());
        } catch(Exception failure) {
            state.put("cause",failure.toString());warnings.add("FAIL2 NO RESULT ADMITTED: "+failure.getMessage());
        } catch(OutOfMemoryError failure) {
            state.put("cause","Java heap exhausted");warnings.add("FAIL2 NO RESULT ADMITTED: Complete native census exhausted heap; preserve attempt and retry with permitted heap/capacities.");
        } finally {
            if(out!=null)try {
                if(exit!=0)for(String name:List.of("worker","results"))if(Files.exists(out.resolve(name))) {
                    try{Files.move(out.resolve(name),out.resolve("unadmitted-"+name));}
                    catch(IOException failure){warnings.add("FAIL2: Unadmitted "+name+" remains in place; quarantine failed: "+failure);}
                }
                state.put("required_action",ACTION);writeJson(out.resolve("analysis-status.json"),state);
                Files.writeString(out.resolve("WARNINGS.md"),"# "+state.get("status")+"\n\n"+String.join("\n\n",warnings)+"\n\nREQUIRED ACTION: "+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                var completed=new LinkedHashMap<String,Object>(state);completed.put("scope",SCOPE);completed.put("files",inventory(out));
                writeJson(out.resolve("COMPLETED.pending.json"),completed);
                Files.move(out.resolve("COMPLETED.pending.json"),out.resolve("COMPLETED.json"),java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch(Exception failure) {exit=2;error.println("FAIL2: Could not seal KING attempt; admit no results: "+failure);}
        }
        PrintStream stream=exit==0?output:error;
        stream.println(state.get("status")+"\n"+String.join("\n",warnings)+"\nREQUIRED ACTION: "+ACTION+(out==null?"":"\nEvidence: "+out));return exit;
    }
    private static Path bed(KingRequest.Request request,String extension) {return Path.of(request.bfile()+"."+extension);}
    private static Path resolved(Path path) throws IOException {
        Path parent=path.getParent();need(parent!=null,"Output requires a parent directory");
        var missing=new ArrayList<Path>();while(!Files.exists(parent,LinkOption.NOFOLLOW_LINKS)){missing.add(parent.getFileName());parent=parent.getParent();need(parent!=null,"No canonical output ancestor");}
        parent=parent.toRealPath();for(int i=missing.size()-1;i>=0;i--)parent=parent.resolve(missing.get(i));return parent.resolve(path.getFileName());
    }
    private static void separate(Path out,Path input) throws IOException {
        Path path=Files.exists(input,LinkOption.NOFOLLOW_LINKS)?input.toRealPath():resolved(input);
        need(!path.startsWith(out)&&!out.startsWith(path),"KING output overlaps an original input/runtime/request");
    }
    private static Pin expected(String platform) throws IOException {
        return switch(platform) {
            case "windows" -> new Pin(1263104,"ae4ce40a753504ba6199bf9b46ba64b3df8492191a79aa16518892ab997cc616");
            case "linux" -> new Pin(3284088,"dc2ed86a82471754bbc9ed53e44764d24a593eb3e4b14fde9d30eb6608f74718");
            default -> throw new IOException("Unsupported KING runtime platform");
        };
    }
    private static Map<?,?> profile(byte[] raw) throws IOException {
        Map<?,?> p=object(NextflowParameters.parse(utf8(raw),4_194_304,100_000));
        keys(p,"schema_version","tool","version","intended_use","platform","executable","executable_pin","dependencies","dependency_names","ldd_paths","loader_files","windows_system_DLLs_independently_pinned");
        integer(p.get("schema_version"),1,1);
        need(p.get("tool").equals("KING")&&p.get("version").equals("2.3.2")&&p.get("intended_use").equals("research"),"Exact research-use KING2.3.2 profile required");
        String platform=text(p.get("platform"));need(expected(platform).equals(fingerprint(p.get("executable_pin"),10_000_000)),"Exact original executable pin differs");
        need(Boolean.FALSE.equals(p.get("windows_system_DLLs_independently_pinned")),"Unsupported Windows DLL closure claim");
        Map<?,?> dependencies=object(p.get("dependencies")),names=object(p.get("dependency_names")),paths=object(p.get("ldd_paths")),loader=object(p.get("loader_files"));
        if(platform.equals("windows"))need(dependencies.isEmpty()&&names.isEmpty()&&paths.isEmpty()&&loader.isEmpty(),"Unsupported Windows runtime dependencies");
        else {
            need(names.keySet().equals(LIBRARIES)&&paths.keySet().equals(LIBRARIES),"Incomplete Linux dependency-name population");
            var required=new HashSet<String>();required.add("/usr/bin/ldd");
            for(Object value:names.values())required.add(text(value));
            need(dependencies.keySet().equals(required),"Incomplete/extraneous Linux dependency population");
            need(loader.containsKey("/etc/ld.so.cache")&&loader.containsKey("/etc/ld.so.conf"),"Incomplete Linux loader metadata");
            for(Object key:loader.keySet())need(text(key).equals("/etc/ld.so.cache")||text(key).equals("/etc/ld.so.conf")||text(key).startsWith("/etc/ld.so.conf.d/"),"Unsupported loader metadata path");
        }
        return p;
    }
    private static Runtime runtime(Path file) throws IOException {
        if(!Files.isRegularFile(file))throw new Unavailable("Missing runtime-profile.json; export setup and supply a permitted original runtime");
        byte[] raw=bytes(file.toRealPath(),4_194_304);Map<?,?> p=profile(raw);String platform=text(p.get("platform"));
        String host=System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if(!(platform.equals("windows")&&host.contains("windows")||platform.equals("linux")&&host.contains("linux")))throw new Unavailable("KING profile requires explicitly selected "+platform+" Java");
        if(!Set.of("amd64","x86_64").contains(System.getProperty("os.arch").toLowerCase(Locale.ROOT)))throw new Unavailable("KING tested profile requires x86_64 Java");
        Path executable=Path.of(text(p.get("executable")));
        if(!Files.isRegularFile(executable)||!Files.isExecutable(executable))throw new Unavailable("Required exact original executable missing/nonexecutable: "+executable);
        var files=new LinkedHashMap<Path,Pin>();files.put(executable,expected(platform));
        for(String key:List.of("dependencies","loader_files"))for(var entry:object(p.get(key)).entrySet()) {
            Path path=Path.of(text(entry.getKey()));
            if(!Files.isRegularFile(path))throw new Unavailable("Missing original runtime prerequisite: "+path);
            files.put(path,fingerprint(entry.getValue(),1_000_000_000));
        }
        var paths=new TreeMap<String,String>();for(var entry:object(p.get("ldd_paths")).entrySet())paths.put(text(entry.getKey()),text(entry.getValue()));
        return new Runtime(platform,executable,Map.copyOf(files),Map.copyOf(paths),raw);
    }
    private static void runtimePins(Runtime runtime,Runnable check) throws IOException {
        for(var entry:runtime.files().entrySet())need(entry.getValue().equals(pin(entry.getKey(),entry.getValue().bytes(),check)),"Original runtime/dependency changed or differs: "+entry.getKey());
        if(runtime.platform().equals("linux")) {
            need(!Files.exists(Path.of("/etc/ld.so.preload"),LinkOption.NOFOLLOW_LINKS),"Unsupported loader preload configuration");
            var actual=new HashSet<Path>();actual.add(Path.of("/etc/ld.so.cache"));actual.add(Path.of("/etc/ld.so.conf"));
            try(var files=Files.list(Path.of("/etc/ld.so.conf.d"))){files.forEach(actual::add);}
            var saved=new HashSet<Path>();for(Object key:object(profile(runtime.raw()).get("loader_files")).keySet())saved.add(Path.of(text(key)));
            need(actual.equals(saved),"Linux loader configuration population changed");
        }
    }
    private static List<String> command(String platform,String executable,String root,KingRequest.Request request) {
        String separator=platform.equals("windows")?"\\":"/";
        var result=new ArrayList<String>(List.of(executable,"-b",root+separator+"inputs"+separator+"cohort.bed","--"+request.mode()));
        if(request.degree()!=0)result.addAll(List.of("--degree",Integer.toString(request.degree())));
        result.addAll(List.of("--cpus",Integer.toString(request.threads()),"--prefix",root+separator+"worker"+separator+"original"));return List.copyOf(result);
    }
    private static Map<String,String> lddPaths(String text) throws IOException {
        var paths=new TreeMap<String,String>();
        for(String raw:text.split("\\R")) {
            String line=raw.strip();if(line.isEmpty()||line.startsWith("linux-vdso.so.1 "))continue;
            var mapped=java.util.regex.Pattern.compile("([^ ]+) => (/[^ ]+) \\(0x[0-9a-f]+\\)").matcher(line);
            String name,path;
            if(mapped.matches()){name=mapped.group(1);path=mapped.group(2);}
            else {var loader=java.util.regex.Pattern.compile("(/[^ ]+) \\(0x[0-9a-f]+\\)").matcher(line);need(loader.matches(),"Missing/unknown Linux dynamic prerequisite: "+line);path=loader.group(1);name=path.substring(path.lastIndexOf('/')+1);}
            need(paths.putIfAbsent(name,path)==null,"Duplicate Linux dynamic prerequisite");
        }
        need(paths.keySet().equals(LIBRARIES),"Linux dynamic prerequisite population differs");return paths;
    }
    private static void dependencies(Runtime runtime,Path out,KingRequest.Request request,Runnable check,String id,List<Map<String,Object>> processes) throws Exception {
        Map<String,Object> process=execute(out,id,List.of("/usr/bin/ldd",runtime.executable().toString()),"linux",request,check);processes.add(process);
        need(((Number)process.get("exit_code")).intValue()==0,"Linux dynamic dependency inspection failed");
        need(lddPaths(utf8(bytes(out.resolve("worker/"+id+".stdout"),1_048_576))).equals(runtime.lddPaths()),"Runtime library mapping differs from profile");
        Map<?,?> names=object(profile(runtime.raw()).get("dependency_names"));
        for(var entry:runtime.lddPaths().entrySet())need(Path.of(entry.getValue()).toRealPath().toString().equals(names.get(entry.getKey())),"Runtime library alias resolved differently");
    }
    private static void bounds(Path root,KingRequest.Limits limits,Runnable check) throws IOException {
        // Running logs are expected to grow. Only count current sizes here; stable hashing occurs after termination.
        long[] total={0};int[] count={0};
        Files.walkFileTree(root,new java.nio.file.SimpleFileVisitor<>() {
            @Override public java.nio.file.FileVisitResult preVisitDirectory(Path path,java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                check.run();need(++count[0]<=limits.outputFiles()&&!attrs.isSymbolicLink(),"KING output directory/link capacity exceeded");
                return java.nio.file.FileVisitResult.CONTINUE;
            }
            @Override public java.nio.file.FileVisitResult visitFile(Path path,java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                check.run();need(++count[0]<=limits.outputFiles()&&attrs.isRegularFile()&&!attrs.isSymbolicLink(),"KING output file/link capacity exceeded");
                total[0]=Math.addExact(total[0],attrs.size());need(total[0]<=limits.outputBytes(),"KING complete output byte capacity exceeded");
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }
    private static Map<String,Object> execute(Path out,String id,List<String> command,String platform,KingRequest.Request request,Runnable check) throws Exception {
        Path worker=out.resolve("worker");var builder=new ProcessBuilder(command).directory(worker.toFile());
        Map<String,String> inherited=new HashMap<>(builder.environment());builder.environment().clear();
        if(platform.equals("linux"))builder.environment().putAll(Map.of("PATH","/usr/bin:/bin","LANG","C","LC_ALL","C","OMP_NUM_THREADS",Integer.toString(request.threads())));
        else for(String name:List.of("SystemRoot","WINDIR","TEMP","TMP"))if(inherited.containsKey(name))builder.environment().put(name,inherited.get(name));
        builder.redirectOutput(worker.resolve(id+".stdout").toFile());builder.redirectError(worker.resolve(id+".stderr").toFile());
        Process process=builder.start();process.getOutputStream().close();var observed=new LinkedHashMap<Long,ProcessHandle>();observed.put(process.pid(),process.toHandle());
        boolean succeeded=false;
        try {
            while(true) {
                process.descendants().forEach(h->observed.put(h.pid(),h));check.run();bounds(out,request.limits(),check);
                if(process.waitFor(100,TimeUnit.MILLISECONDS))break;
            }
            process.descendants().forEach(h->observed.put(h.pid(),h));
            need(observed.values().stream().noneMatch(ProcessHandle::isAlive),"Observed original worker process remains live");succeeded=true;
            Map<String,Object> record=map("id",id,"command",command,"exit_code",process.exitValue(),"observed_owned_processes_terminal",true,"observed_pids",new ArrayList<>(observed.keySet()));
            writeJson(worker.resolve(id+".process.json"),record);return record;
        } finally {
            if(!succeeded) {
                process.descendants().forEach(h->observed.put(h.pid(),h));
                for(ProcessHandle h:observed.values())if(h.isAlive())h.destroy();
                for(ProcessHandle h:observed.values())if(h.isAlive())h.destroyForcibly();
                process.waitFor(5,TimeUnit.SECONDS);
                writeJson(worker.resolve(id+".process.json"),map("id",id,"command",command,"result_admitted",false,
                        "observed_owned_processes_terminal",observed.values().stream().noneMatch(ProcessHandle::isAlive),"observed_pids",new ArrayList<>(observed.keySet())));
            }
        }
    }
    private static Map<String,Object> inventory(Path root) throws IOException {
        var result=new TreeMap<String,Object>();
        for(var entry:tree(root,100_000,200_000_000_000L,100_000_000_000L,()->{}).entrySet()) {
            need(entry.getValue().kind().equals("file"),"Linked/nonregular KING bundle member");
            result.put(entry.getKey(),map("kind","file","pin",entry.getValue().pin()));
        }
        return result;
    }
    private static List<String> scientificWarnings(Map<?,?> audit,KingRequest.Request request) throws IOException {
        var result=new ArrayList<String>();
        if(integer(audit.get("zero_smaller_heterozygosity_pairs_unassessable"),0,Long.MAX_VALUE)>0)result.add("UNASSESSABLE PAIRS: The complete census contains zero smaller-heterozygosity denominators. Original KING may emit a zero coefficient; it is not evidence of unrelatedness. Read native-pair-counts.tsv before downstream use. Its explicitly named native between-family minimum-heterozygosity profile also lists within-family pairs for count diagnostics; those diagnostic coefficients do not replace original within-family estimates.");
        if(request.mode().equals("unrelated"))result.add("ORIGINAL SELECTION ONLY: keep/remove lists are an exact original-sample partition. Selection, degree interpretation and tie handling remain the original KING algorithm; no independent optimality or biological unrelatedness claim is made.");
        if(request.mode().equals("ibdseg"))result.add("IBD FILTERS: Original KING omits pairs without a long segment >10Mb and omits short segments <3Mb. PropIBD uses the source's quantized IBD1/2 fractions. Absence is not proof of no shared DNA. Native checks validate complete returned coordinates/fractions, not segment placement.");
        if(request.mode().equals("duplicate"))result.add("DUPLICATE SCREEN: Original KING screens heterozygote concordance at its default80% threshold. Returned pair counts are checked; screen completeness and absence are not independently established.");
        return result;
    }
    private static String stable(Object value) throws IOException {
        if(value instanceof Map<?,?> values){var result=new TreeMap<String,Object>();for(var e:values.entrySet())result.put(text(e.getKey()),normal(e.getValue()));return NextflowJson.json(result);}return NextflowJson.json(normal(value));
    }
    private static Object normal(Object value) throws IOException {
        if(value instanceof Pin pin)return normal(map("bytes",pin.bytes(),"sha256",pin.sha256()));
        if(value instanceof Map<?,?> values){var result=new TreeMap<String,Object>();for(var e:values.entrySet())result.put(text(e.getKey()),normal(e.getValue()));return result;}
        if(value instanceof List<?> values){var result=new ArrayList<Object>();for(Object v:values)result.add(normal(v));return result;}
        if(value instanceof NextflowParameters.NumberToken n)return new java.math.BigDecimal(n.text()).stripTrailingZeros();
        if(value instanceof Number n)return new java.math.BigDecimal(n.toString()).stripTrailingZeros();return value;
    }
    private static void verify(Path root) throws Exception {
        directory(root);Map<?,?> done=object(json(root.resolve("COMPLETED.json"),64_000_000));
        need(Boolean.TRUE.equals(done.get("result_admitted"))&&done.get("status").equals("completed-original-king-with-warnings"),"No admitted complete original KING analysis");
        var actual=new TreeMap<String,Object>(inventory(root));actual.remove("COMPLETED.json");
        need(stable(actual).equals(stable(done.get("files"))),"Complete KING file population/hash differs");
        need(SCOPE.equals(done.get("scope"))&&ACTION.equals(done.get("required_action")),"KING scope/action protocol differs");
        KingRequest.Request request=KingRequest.parse(NextflowParameters.parse(utf8(bytes(root.resolve("source-request.json"),1_048_576)),1_048_576,100_000),root);
        Map<?,?> p=profile(bytes(root.resolve("runtime-profile.json"),4_194_304));String platform=text(p.get("platform"));
        need(!request.mode().equals("ibdseg")||platform.equals("linux"),"Unsupported Windows IBD-coordinate admission");
        Map<?,?> state=object(json(root.resolve("analysis-status.json"),1_048_576));
        need(Boolean.TRUE.equals(state.get("result_admitted"))&&state.get("status").equals(done.get("status"))&&ACTION.equals(state.get("required_action")),"KING admission state differs");
        Map<?,?> execution=object(json(root.resolve("execution.json"),16_777_216));
        need(platform.equals(execution.get("platform"))&&p.get("executable").equals(execution.get("executable_at_execution")),"KING execution/runtime platform/executable differs");
        Map<?,?> original=object(execution.get("original_inputs"));need(original.size()==3,"Incomplete original input bindings");
        var extensions=new HashSet<String>();
        for(var entry:original.entrySet()) {
            String name=text(entry.getKey());String extension=name.substring(name.lastIndexOf('.')+1);
            need(Set.of("bed","bim","fam").contains(extension)&&extensions.add(extension),"Unexpected/duplicate original input binding");
            Pin value=fingerprint(entry.getValue(),request.limits().inputBytes());
            need(value.equals(pin(root.resolve("inputs/cohort."+extension),value.bytes(),()->{})),"Captured/original input binding differs");
        }
        List<?> steps=list(execution.get("steps"));int expected=platform.equals("linux")?3:1;
        need(steps.size()==expected,"Incomplete original process population");
        var ids=platform.equals("linux")?List.of("dependencies-before","analysis","dependencies-after"):List.of("analysis");
        for(int i=0;i<ids.size();i++) {
            String id=ids.get(i);Map<?,?> step=object(steps.get(i));
            List<String> planned=id.equals("analysis")?command(platform,text(p.get("executable")),text(execution.get("bundle_at_execution")),request):List.of("/usr/bin/ldd",text(p.get("executable")));
            need(id.equals(step.get("id"))&&strings(step.get("command")).equals(planned)&&integer(step.get("exit_code"),0,0)==0&&Boolean.TRUE.equals(step.get("observed_owned_processes_terminal")),"Original KING process plan/disposition differs");
            need(stable(step).equals(stable(json(root.resolve("results/"+id+".process.json"),1_048_576))),"Original KING process record differs");
            if(!id.equals("analysis"))need(stable(lddPaths(utf8(bytes(root.resolve("results/"+id+".stdout"),1_048_576)))).equals(stable(p.get("ldd_paths"))),"Saved Linux dependency mapping differs");
        }
        Map<String,Object> audit=KingAudit.verify(root,root.resolve("results"),request,platform,false,()->{});
        need(stable(audit).equals(stable(json(root.resolve("provenance.json"),16_777_216))),"Complete native KING replay differs");
        String warnings=utf8(bytes(root.resolve("WARNINGS.md"),16_777_216));
        need(warnings.startsWith("# "+done.get("status"))&&warnings.contains(SCOPE)&&warnings.contains(ACTION),"Prominent KING warning/action protocol missing");
        for(String warning:scientificWarnings(audit,request))need(warnings.contains(warning),"Material KING warning missing");
    }
}
