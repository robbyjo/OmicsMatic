/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import org.jlinalg.sequence.TomtomResults;

/** Exact original current-host Tomtom admission; a runtime ticket never admits a user fit. */
final class TomtomRuntime {
    private TomtomRuntime() { }
    static final String ACTION="Required action: inspect WARNINGS.md, FAILURE.json when present, metadata/host.json, every retained probe/process stdout/stderr/ledger and the complete source/build evidence. "
        +"Missing or forbidden dependencies/capabilities require a named SKIP3 and instructions to supply them on a permitted host; analysis never installs. Correct failures and retry into NEW private canonical Linux paths. "
        +"A copied receipt is not a current-host runtime ticket. User input, requested settings, supervised execution, complete requested outputs and final bundle verification remain separate gates.";
    static final class Unavailable extends IOException {
        private static final long serialVersionUID=1L;
        Unavailable(String message){super(message);}
        Unavailable(String message,Throwable cause){super(message,cause);}
    }
    record Limits(RepeatHostFiles.Limits host,MemeRuntimeFiles.Limits evidence,RsemProcess.Limits process,int images,long admissionMillis) {
        Limits {java.util.Objects.requireNonNull(host);java.util.Objects.requireNonNull(evidence);java.util.Objects.requireNonNull(process);if(images<1||images>512||admissionMillis<1||admissionMillis>604800000L)throw new IllegalArgumentException("Invalid whole Tomtom runtime admission capacities");}
        static Limits defaults(){return new Limits(new RepeatHostFiles.Limits(268435456,4294967296L,100000,128,67108864),new MemeRuntimeFiles.Limits(67108864,268435456,10000),new RsemProcess.Limits(120000,2147483648L,120,67108864,1024,1073741824,10000,32,256,1073741824,50),128,1200000);}
    }
    static final class Admitted {
        private final Context context;private final Path work;private final Map<String,Object> evidence;
        private Admitted(Context context,Path work,Map<String,Object> evidence){this.context=context;this.work=work;this.evidence=Map.copyOf(evidence);}
        TomtomRecipe.Mode mode(){return context.mode;}
        Path home(){return context.home;}
        Path work(){return work;}
        boolean userAnalysisAdmitted(){return false;}
        void verify(Runnable check)throws IOException {context.verify(check);require(evidence.equals(MemeRuntimeFiles.tree(work,context.evidenceLimits(),check)),"Completed native Tomtom runtime evidence changed");}
        RsemProcess.Result execute(TomtomRecipe.Request request,TomtomInputs.Context inputs,Path newProcess,Runnable check)throws IOException {
            return execute(request,inputs,newProcess,null,()->{},check);
        }
        RsemProcess.Result execute(TomtomRecipe.Request request,TomtomInputs.Context inputs,Path newProcess,Path accountedPreparation,RsemProcess.Check extraIntegrity,Runnable check)throws IOException {
            require(request.equals(inputs.request()),"Tomtom recipe and complete captured input binding differ");
            if(request.mode()!=mode())throw new Unavailable("Admitted Tomtom runtime capability differs from the explicitly requested output mode. Obtain a fresh matching runtime ticket; do not change output mode automatically.");
            TomtomRecipe.nativePath(newProcess.toString());require(!overlap(newProcess,work)&&!overlap(newProcess,home())&&!overlap(newProcess,inputs.root())&&!overlap(newProcess,context.report.root()),"Native Tomtom process, runtime, captured inputs and evidence must be disjoint");
            String output=mode()==TomtomRecipe.Mode.TEXT?null:newProcess.resolve("result").toString();var command=request.arguments(home().resolve("bin/tomtom").toString(),inputs.query().toString(),inputs.targets(),inputs.backgroundFile(),output);inputs.expected(command);
            List<Path> accounting;
            if(accountedPreparation==null)accounting=List.of(work,inputs.root());
            else{NextflowFiles.directory(accountedPreparation);require(work.startsWith(accountedPreparation)&&inputs.root().startsWith(accountedPreparation)&&context.report.root().startsWith(accountedPreparation)&&!overlap(accountedPreparation,home())&&!overlap(accountedPreparation,newProcess),"Complete prepared Tomtom accounting root does not bind runtime, inputs and source evidence disjointly from execution/home");accounting=List.of(accountedPreparation);}
            return RsemProcess.executeOwned(command,context.environment,newProcess,request.processLimits(),()->{verify(check);inputs.verify(true,check);extraIntegrity.verify();},check,accounting);
        }
    }
    private static final List<String> HELPERS=List.of("/usr/bin/setsid","/usr/bin/prlimit","/usr/bin/ldd","/bin/bash");
    private static final List<String> CONFIG=List.of("/etc/ld.so.cache","/etc/ld.so.conf","/etc/ld.so.preload","/etc/nsswitch.conf","/etc/hosts","/etc/resolv.conf","/etc/gai.conf","/etc/host.conf","/etc/localtime","/etc/os-release","/usr/share/zoneinfo/UTC","/lib/ld-linux.so.2","/lib64/ld-linux-x86-64.so.2","/libx32/ld-linux-x32.so.2");
    private static final Set<Path> SYSTEM=Set.of(Path.of("/usr/lib"),Path.of("/usr/lib64"),Path.of("/usr/libx32"),Path.of("/lib"),Path.of("/lib64"),Path.of("/libx32"),Path.of("/usr/bin"),Path.of("/bin"),Path.of("/etc"),Path.of("/usr/share"),Path.of("/usr/local/lib"),Path.of("/usr/local/share"),Path.of("/run/systemd/resolve"),Path.of("/run/resolvconf"),Path.of("/mnt/wsl"));

    static Admitted admit(String profile,TomtomRecipe.Mode mode,Path home,Path buildEvidence,Path newWork,Limits limits,Runnable cancellation)throws IOException {
        MemeNativeHost.read();java.util.Objects.requireNonNull(mode);java.util.Objects.requireNonNull(limits);java.util.Objects.requireNonNull(cancellation);require(TomtomRecipe.PROFILE.equals(profile),"Unsupported exact original Tomtom profile; no automatic edition replacement");
        privatePath(home);privatePath(newWork);NextflowFiles.directory(buildEvidence);require(newWork.getParent()!=null&&newWork.getParent().toRealPath().equals(newWork.getParent())&&!Files.exists(newWork,LinkOption.NOFOLLOW_LINKS),"Require a NEW canonical native Tomtom admission directory");
        require(!overlap(home,newWork)&&!overlap(buildEvidence,newWork)&&!overlap(home,buildEvidence),"Native Tomtom home, build evidence and admission work must be disjoint");
        long start=System.nanoTime();Runnable check=()->{cancellation.run();if(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)>limits.admissionMillis())throw new java.util.concurrent.CancellationException("Complete Tomtom admission wall capacity exceeded");};boolean fresh=false;
        try{
            if(!newWork.getFileSystem().supportedFileAttributeViews().contains("posix"))throw new Unavailable("Native Tomtom admission requires a filesystem preserving private Linux modes; use a permitted native Linux filesystem");
            Files.createDirectory(newWork,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));fresh=true;require(NextflowFiles.mode(newWork)==0700,"Native Tomtom work did not preserve mode0700");
            for(String name:List.of("metadata","probes","inputs","health"))Files.createDirectory(newWork.resolve(name));
            MemeRuntimeFiles.write(newWork.resolve("metadata/request.json"),Map.of("profile",profile,"output_mode",mode.name(),"home",home.toString(),"build_evidence",buildEvidence.toString(),"installation_performed",false,"user_analysis_admitted",false));
            Context context=new Context(mode,home,buildEvidence,limits,check);context.discover(newWork,check);var health=context.health(newWork,check);context.verify(check);
            MemeRuntimeFiles.write(newWork.resolve("metadata/host.json"),context.document());MemeRuntimeFiles.write(newWork.resolve("metadata/health.json"),Map.of("status","PASSED_FRESH_ORIGINAL_FIXED_REFERENCE_HEALTH","fits",health,"user_analysis_admitted",false));
            Files.writeString(newWork.resolve("WARNINGS.md"),"# Native Tomtom runtime health only; no user analysis admitted\n\nTwo fresh synthetic9-pair health fits match all ten original reference columns. Tiny databases do not certify P-value calibration or biological accuracy. "
                +"Text capability does not require Perl/report imports; full-report capability binds those separately. Source/build hashes establish provenance and integrity, not authenticity or historical edition equivalence.\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            var inventory=MemeRuntimeFiles.tree(newWork,context.evidenceLimits(),check);MemeRuntimeFiles.write(newWork.resolve("manifest.json"),Map.of("schema_version",1,"status","NATIVE_RUNTIME_HEALTH_ONLY","profile",profile,"output_mode",mode.name(),"home",home.toString(),"files",inventory,"user_analysis_admitted",false));
            MemeRuntimeFiles.write(newWork.resolve("COMPLETED.json"),Map.of("status","COMPLETE_NATIVE_TOMTOM_RUNTIME_HEALTH_ONLY","manifest",MemeRuntimeFiles.pin(NextflowFiles.pin(newWork.resolve("manifest.json"),limits.host().metadataBytes(),check)),"user_analysis_admitted",false));
            var completed=MemeRuntimeFiles.tree(newWork,context.evidenceLimits(),check);context.verify(check);require(completed.equals(MemeRuntimeFiles.tree(newWork,context.evidenceLimits(),check)),"Native Tomtom runtime evidence changed during finalization");return new Admitted(context,newWork,completed);
        }catch(IOException|RuntimeException failure){
            IOException typed=failure instanceof Unavailable u?u:failure instanceof RsemProcess.UnavailableDependency||failure instanceof AccessDeniedException?new Unavailable("Required permitted native Tomtom dependency/capability is unavailable: "+failure.getMessage(),failure):failure instanceof IOException io?io:new IOException("Native Tomtom admission cancelled or invalid; no runtime or analysis admitted",failure);
            if(fresh)failure(newWork,typed);throw typed;
        }
    }

    private static final class Context {
        final TomtomRecipe.Mode mode;final Path home;final Limits limits;final MemeBuildEvidence.Context report;final Map<String,String> platform,environment;
        final Set<Path> allowed=new HashSet<>(SYSTEM),roots=new HashSet<>();final TreeSet<Path> files=new TreeSet<>(),nodes=new TreeSet<>();
        final Map<String,RsemElf.Image> images=new TreeMap<>();final Map<String,RsemElf.Report> bindings=new TreeMap<>();final List<Path> completedProbes=new ArrayList<>();
        RepeatHostFiles.Snapshot host;MemePerl.Receipt perl;
        Context(TomtomRecipe.Mode mode,Path home,Path evidence,Limits limits,Runnable check)throws IOException {
            this.mode=mode;this.home=home;this.limits=limits;platform=MemeNativeHost.read();report=MemeBuildEvidence.read(evidence,limits.evidence(),check);
            if(!Files.isDirectory(home,LinkOption.NOFOLLOW_LINKS))throw new Unavailable("Exact original MEME/Tomtom home is absent. Complete explicit permitted setup, or skip dependent analysis.");
            NextflowFiles.directory(home);require((NextflowFiles.mode(home)&0022)==0,"Original Tomtom runtime home must not be writable by group or others");MemeBuildEvidence.verifyPayload(report,home,check);
            allowed.add(home);addRoot(home);addRoot(Path.of("/etc/ld.so.conf.d"));environment=Map.of("PATH",home.resolve("bin")+":"+home.resolve("libexec/meme-5.5.9")+":/usr/bin:/bin","LANG","C","LC_ALL","C","TZ","UTC");
            for(String name:HELPERS)addExecutable(Path.of(name));for(String name:CONFIG)files.add(Path.of(name));for(String name:List.of("/bin","/lib","/lib64","/usr/bin","/usr/lib","/usr/lib64","/usr/lib/x86_64-linux-gnu"))nodes.add(Path.of(name));
            if(mode==TomtomRecipe.Mode.FULL_REPORT){addExecutable(Path.of("/bin/sh"));addExecutable(Path.of("/usr/bin/perl"));
                Path helper=home.resolve("libexec/meme-5.5.9/tomtom_xml_to_html");require(Files.isExecutable(helper),"Requested Tomtom report helper is not executable");String script=NextflowFiles.utf8(NextflowFiles.bytes(helper,2097152));require(script.startsWith("#!/usr/bin/perl\n"),"Unsupported original Tomtom report helper interpreter");}
            host=capture(check);
        }
        MemeRuntimeFiles.Limits evidenceLimits(){return new MemeRuntimeFiles.Limits(limits.process().fileBytes(),limits.process().workspaceBytes(),limits.process().fileCount());}
        void addExecutable(Path path)throws IOException {
            if(!Files.isRegularFile(path)||!Files.isReadable(path)||!Files.isExecutable(path))throw new Unavailable("Required existing permitted Tomtom helper is missing or execution is forbidden: "+path);
            Path real=path.toRealPath();allow(real);files.add(path);files.add(real);
        }
        void discover(Path work,Runnable check)throws IOException {
            var pending=new TreeSet<Path>();pending.add(home.resolve("bin/tomtom"));for(String helper:List.of("/usr/bin/setsid","/usr/bin/prlimit","/bin/bash"))pending.add(Path.of(helper).toRealPath());
            if(mode==TomtomRecipe.Mode.FULL_REPORT){pending.add(Path.of("/bin/sh").toRealPath());pending.add(Path.of("/usr/bin/perl").toRealPath());}
            discoverImages(pending,work,check);
            if(mode==TomtomRecipe.Mode.FULL_REPORT){
                RsemProcess.Result probe;try{probe=run(MemePerl.arguments(home),"perl-imports",work,check);}catch(IOException failed){
                    verify(check);Path stderr=work.resolve("probes/perl-imports/process.stderr");
                    if(Files.isRegularFile(stderr,LinkOption.NOFOLLOW_LINKS)&&MemePerl.unavailableImport(failed,NextflowFiles.bytes(stderr,2097152)))throw new Unavailable("Required permitted Perl/XML::Parser::Expat/report import dependency is unavailable. Supply it on a permitted host or skip this full-report analysis; inspect probes/perl-imports.",failed);
                    throw failed;
                }
                perl=MemePerl.parse(NextflowFiles.bytes(probe.stdout(),2097152),NextflowFiles.bytes(probe.stderr(),2097152));
                for(String name:perl.roots())addRoot(Path.of(name));
                for(String name:perl.modules().values()){Path path=Path.of(name);allow(path);files.add(path);files.add(path.toRealPath());}
                for(String name:perl.dynamicImages()){Path path=Path.of(name);allow(path);files.add(path);files.add(path.toRealPath());pending.add(path.toRealPath());}
                expand(check);discoverImages(pending,work,check);
                var repeated=run(MemePerl.arguments(home),"perl-imports-recheck",work,check);require(perl.equals(MemePerl.parse(NextflowFiles.bytes(repeated.stdout(),2097152),NextflowFiles.bytes(repeated.stderr(),2097152))),"Original Perl imports/search roots changed after dependency closure");
            }
            require(images.containsKey(home.resolve("bin/tomtom").toString()),"Original Tomtom executable omitted from ELF dependency closure");verify(check);
            var version=run(List.of(home.resolve("bin/tomtom").toString(),"-version"),"version",work,check);require(NextflowFiles.utf8(NextflowFiles.bytes(version.stdout(),1048576)).strip().equals("5.5.9")&&Files.size(version.stderr())==0,"Fresh original exact Tomtom version differs");
        }
        void discoverImages(TreeSet<Path> pending,Path work,Runnable check)throws IOException {
            while(!pending.isEmpty()){
                Path path=pending.pollFirst().toRealPath();if(images.containsKey(path.toString()))continue;check.run();require(images.size()<limits.images(),"Complete native Tomtom ELF image capacity exceeded");
                var image=RsemElf.image(path,limits.host().fileBytes(),check);require(image!=null,"Non-ELF image in native Tomtom dependency closure: "+path);images.put(path.toString(),image);files.add(path);
                for(Path directory:RsemElf.searchDirectories(path,image))addRoot(directory);expand(check);
                Path probe=work.resolve("probes/libraries-"+bindings.size());RsemProcess.Result result=RsemProcess.inspectLibraries(path,environment,probe,limits.process(),()->verify(check),check,accounting(work));completedProbes.add(probe);
                byte[] stdout=NextflowFiles.bytes(result.stdout(),2097152),stderr=NextflowFiles.bytes(result.stderr(),2097152);if((NextflowFiles.utf8(stdout)+NextflowFiles.utf8(stderr)).contains("not found"))throw new Unavailable("Required native Tomtom ELF library is unavailable; inspect "+probe+" and supply it on a permitted host or skip dependent execution");
                var report=RsemElf.bindings(image,result.exitCode(),stdout,stderr);bindings.put(path.toString(),report);
                if(path.equals(home.resolve("bin/tomtom")))require(image.interpreter()!=null&&report.virtualDso()&&!report.noLibraryBindings(),"Original Tomtom executable lacks expected dynamic loader/bindings");
                for(var binding:report.bindings()){
                    Path reported=Path.of(binding.path()).normalize();allow(reported);Path real=reported.toRealPath();allow(real);files.add(reported);files.add(real);
                    if(!Files.isReadable(real))throw new Unavailable("Reported native Tomtom library is unreadable or forbidden: "+reported);
                    require(RsemElf.image(real,limits.host().fileBytes(),check)!=null,"Reported Tomtom library is not an ELF image");if(!images.containsKey(real.toString()))pending.add(real);
                    if(binding.loader()&&image.interpreter()!=null)require(Path.of(image.interpreter()).toRealPath().equals(real),"Tomtom ELF interpreter and reported loader differ");
                }
                expand(check);
            }
        }
        List<Map<String,Object>> health(Path work,Runnable check)throws IOException {
            var lock=MemeRuntimeFiles.lock();var health=NextflowFiles.object(SimpleJson.parse(NextflowFiles.utf8(MemeRuntimeFiles.resource(MemeRuntimeFiles.RESOURCE+"health-reference.json",NextflowFiles.fingerprint(lock.get("health_reference"),2097152)))));
            byte[] raw=MemeRuntimeFiles.resource(MemeRuntimeFiles.RESOURCE+"health-motifs.meme",NextflowFiles.fingerprint(lock.get("health_motifs"),2097152));Path motifs=work.resolve("inputs/health-motifs.meme");Files.write(motifs,raw,StandardOpenOption.CREATE_NEW);var references=new ArrayList<Map<String,Object>>();
            for(Object value:NextflowFiles.list(health.get("cases"))){var spec=NextflowFiles.object(value);String label=NextflowFiles.text(spec.get("label"));require(Set.of("ed-forward","kullback-both").contains(label),"Unknown original Tomtom health protocol");
                Path folder=work.resolve("health").resolve(label);Files.createDirectory(folder);Path source=folder.resolve("request.json");MemeRuntimeFiles.write(source,healthRecipe(motifs,spec));var request=TomtomRecipe.read(source,check);var inputs=TomtomInputs.capture(folder.resolve("prepared"),request,check);
                String probe="health-"+label;Path process=work.resolve("probes").resolve(probe);String output=mode==TomtomRecipe.Mode.TEXT?null:process.resolve("result").toString();var command=request.arguments(home.resolve("bin/tomtom").toString(),inputs.query().toString(),inputs.targets(),inputs.backgroundFile(),output);var expected=inputs.expected(command);
                var result=RsemProcess.executeOwned(command,environment,process,limits.process(),()->{verify(check);inputs.verify(true,check);},check,accounting(work));completedProbes.add(process);Path table=output==null?result.stdout():Path.of(output).resolve("tomtom.tsv");int pairs,rows;
                if(mode==TomtomRecipe.Mode.TEXT){var audit=TomtomResults.readText(table,expected,request.outputLimits(),check);pairs=audit.pairs().size();rows=audit.matches().size();}
                else{var audit=TomtomResults.read(Path.of(output).resolve("tomtom.xml"),table,expected,request.outputLimits(),check);pairs=audit.pairs().size();rows=audit.matches().size();String html=NextflowFiles.utf8(NextflowFiles.bytes(Path.of(output).resolve("tomtom.html"),8388608));require(html.startsWith("<!DOCTYPE HTML>")&&html.contains("@JSON_VAR data")&&html.contains("var data ="),"Requested original Tomtom health HTML is incomplete");}
                var actual=new ArrayList<List<String>>();for(String line:NextflowFiles.utf8(NextflowFiles.bytes(table,8388608)).split("\n"))if(!line.isBlank()&&!line.startsWith("#")&&!line.startsWith("Query_ID\t"))actual.add(List.of(line.split("\t",-1)));
                var reference=new ArrayList<List<String>>();for(Object row:NextflowFiles.list(spec.get("rows")))reference.add(NextflowFiles.strings(row));require(rows==9&&pairs==9&&actual.equals(reference),"Fresh original Tomtom health differs from the complete fixed ten-column source reference; preserve the discrepancy and do not weaken the reference gate");
                inputs.verify(true,check);verify(check);references.add(Map.of("label",label,"output_mode",mode.name(),"complete_declared_pairs",pairs,"complete_reported_rows",rows,"all_ten_columns_exact_original_match",true,"table",MemeRuntimeFiles.pin(NextflowFiles.pin(table,8388608,check)),"owned_live_after_cleanup",result.ownedLiveAfterCleanup(),"user_analysis_admitted",false));
            }
            require(references.size()==2,"Incomplete fresh original Tomtom health family");return List.copyOf(references);
        }
        Map<String,Object> healthRecipe(Path motifs,Map<?,?> spec)throws IOException {
            return MemeRuntimeFiles.object("schema_version",1,"profile",TomtomRecipe.PROFILE,"query",motifs.toString(),"targets",List.of(motifs.toString()),"distance",NextflowFiles.text(spec.get("distance")),"reverse_complement",spec.get("reverse_complement"),"pseudocount",new java.math.BigDecimal("0.1"),"minimum_overlap",1,"internal",false,"complete_scores",true,"threshold",Map.of("kind","QVALUE","value",1),"background",MemeRuntimeFiles.object("source","QUERY","path",null),"output_mode",mode.name(),"limits",Map.of("input_bytes",8388608,"decoded_characters",8388608,"matrix_values",1000000,"table_bytes",8388608));
        }
        RsemProcess.Result run(List<String> command,String label,Path work,Runnable check)throws IOException {Path probe=work.resolve("probes").resolve(label);var result=RsemProcess.executeOwned(command,environment,probe,limits.process(),()->verify(check),check,accounting(work));completedProbes.add(probe);return result;}
        List<Path> accounting(Path work){var paths=new ArrayList<>(List.of(work.resolve("metadata"),work.resolve("inputs"),work.resolve("health")));paths.addAll(completedProbes);return List.copyOf(paths);}
        RepeatHostFiles.Snapshot capture(Runnable check)throws IOException {return RepeatHostFiles.capture(roots.stream().sorted().toList(),List.copyOf(files),List.copyOf(nodes),roots,allowed,limits.host(),check);}
        void expand(Runnable check)throws IOException {verify(check);var expanded=capture(check);verify(check);host=expanded;}
        void verify(Runnable check)throws IOException {check.run();report.verify(check);require(platform.equals(MemeNativeHost.read()),"Native Tomtom process/CPU/kernel identity changed");RepeatHostFiles.verify(host,roots,allowed,limits.host(),check);}
        void allow(Path path)throws IOException {if(allowed.stream().noneMatch(path::startsWith))throw new Unavailable("Unreviewed native Tomtom dependency/search root: "+path+". Preserve diagnostics and use a supported permitted host; no automatic host change was made.");}
        void addRoot(Path path)throws IOException {
            allow(path);roots.add(path);nodes.add(path);
            if(Files.exists(path)){Path real=path.toRealPath();allow(real);require(Files.isDirectory(real,LinkOption.NOFOLLOW_LINKS),"Declared native Tomtom search root is not a directory");roots.add(real);nodes.add(real);}
        }
        Map<String,Object> document()throws IOException {return MemeRuntimeFiles.object("profile",TomtomRecipe.PROFILE,"output_mode",mode.name(),"home",home.toString(),"platform",platform,"environment_without_owned_HOME_TMPDIR",environment,"complete_host_files",SimpleJson.parse(NextflowFiles.utf8(RepeatHostFiles.bytes(host,limits.host()))),"ELF_images",SimpleJson.parse(RsemJson.json(images)),"ELF_bindings",SimpleJson.parse(RsemJson.json(bindings)),"Perl_report_capability",perl==null?null:perl.document(),"build_completion",MemeRuntimeFiles.pin(report.completion()),"scope","Exact original source/build/payload; complete declared current ELF/helper/library/configuration and consumed RPATH trees, plus full configured Perl roots/imported modules/native XML libraries only for requested full reports. Fresh fixed-reference health is separate. This is not a signature, arbitrary plugin/network policy, biological certification or user fit.");}
    }
    private static void failure(Path root,IOException failure){
        try{Path marker=root.resolve("COMPLETED.json");if(Files.exists(marker,LinkOption.NOFOLLOW_LINKS))Files.move(marker,root.resolve("UNADMITTED-COMPLETED.json"));MemeRuntimeFiles.write(root.resolve("FAILURE.json"),Map.of("status",failure instanceof Unavailable?"SKIP3_NATIVE_TOMTOM_DEPENDENCY_UNAVAILABLE":"FAIL2_NATIVE_TOMTOM_NOT_ADMITTED","error",failure.toString(),"required_action",ACTION,"user_analysis_admitted",false));Files.writeString(root.resolve("WARNINGS.md"),"\n\n# NO RUNTIME OR USER ANALYSIS ADMITTED\n\n"+failure+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(IOException diagnostics){failure.addSuppressed(diagnostics);}
    }
    private static void privatePath(Path path)throws IOException {require(path!=null&&path.isAbsolute()&&path.normalize().equals(path),"Require canonical absolute native Tomtom paths");TomtomRecipe.nativePath(path.toString());}
    private static boolean overlap(Path a,Path b){return a.startsWith(b)||b.startsWith(a);}
    private static void require(boolean condition,String message)throws IOException {MemeRuntimeFiles.require(condition,message);}
}
