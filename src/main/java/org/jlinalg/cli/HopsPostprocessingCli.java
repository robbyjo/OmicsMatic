/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.metagenomics.HopsAssessmentAudit;
import org.jlinalg.metagenomics.HopsEvidence;

/** Original AMPS complete plots/workspace after verified original MaltExtract output. */
public final class HopsPostprocessingCli {
    private HopsPostprocessingCli() { }
    private static final String STATUS="complete-captured-original-hops035-postprocessing-only";
    private static final String ACTION="Required action: retain complete inputs, original output, process diagnostics and every failed attempt. Supply a verified complete original MaltExtract1.7 bundle, its exact1.7 reader JAR, a permitted Linux x86_64 public R4.0.5 provider from tools/literature/hops035_workflow/setup.py, and a NEW native-Linux output directory. If download, installation or execution is unavailable or forbidden, skip dependent analysis. Resolve the named input, identity, capacity, source or output discrepancy before retrying; do not repair names or nonfinite statistics, substitute versions, truncate populations or infer biological absence. The unchanged original default-only script is known to fail; failed or empty plotting output admits no result. This stage preserves original PDF/RData and compares scientific fields with native Java. It does not run MALT, reproduce author settings, automate manual biological assessment, authenticate a producer or qualify whole HOPS/the250-paper goal.";
    private record Caps(long fileBytes,long bundleBytes,int files,long rows,long cells,int samples,int taxa,int threads,long wall) {
        HopsEvidence.Limits tables(){return new HopsEvidence.Limits(fileBytes,rows,cells,samples,taxa);}
        NextflowProcess.Limits process(){return new NextflowProcess.Limits(wall*1000,17179869184L,Math.max(1800,wall*4),fileBytes,4096,Math.max(40000000000L,bundleBytes*4),files*3,64,256,8589934592L,1000);}
        Map<String,Object> document(){var m=new TreeMap<String,Object>();m.put("file_bytes",fileBytes);m.put("bundle_bytes",bundleBytes);m.put("files",files);m.put("rows",rows);m.put("cells",cells);m.put("samples",samples);m.put("taxa",taxa);m.put("threads",threads);m.put("wall_seconds",wall);return m;}
    }
    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    static int run(String[] args,PrintStream console,PrintStream error) {
        Path out=null;boolean owned=false;
        try {
            if(args.length==1&&args[0].equals("--help")){console.println(help());return 0;}
            Map<String,String> o=options(args);Path provider=path(required(o,"--post-runtime")),reader=path(required(o,"--reader-runtime"));out=path(required(o,"--out"));
            if(Files.exists(out,LinkOption.NOFOLLOW_LINKS))throw new IOException("Output must be NEW; prior attempts remain unchanged");
            NextflowFiles.directory(out.getParent());
            Path source=path(required(o,o.containsKey("--verify")?"--verify":"--maltextract-bundle"));
            for(Path p:List.of(provider,reader,source))if(p.startsWith(out)||out.startsWith(p))throw new IOException("Inputs/runtime and owned output overlap");
            Files.createDirectory(out);owned=true;
            if(o.containsKey("--verify")) {
                if(!o.keySet().equals(Set.of("--verify","--post-runtime","--reader-runtime","--out")))throw new IOException("Saved verification accepts only its bundle, providers and NEW output");
                verify(source,reader,provider,out,console,error);console.println("PASS COMPLETE SAVED ORIGINAL HOPS POSTPROCESSING; no original fitting/plotting repeated; producer not authenticated");return 0;
            }
            Caps caps=caps(o);String filter=required(o,"--filter"),json=required(o,"--emit-json");
            if(!Set.of("default","def_anc").contains(filter)||!Set.of("true","false").contains(json))throw new IOException("Explicit original filter default/def_anc and emit-json true/false required");
            var snapshot=HopsPostprocessingProvider.inspect(provider);nativeFilesystem(out);
            checkExtraction(source,reader,out.resolve("source-check.stdout"),out.resolve("source-check.stderr"));
            Path bundle=Files.createDirectory(out.resolve("bundle")),extraction=bundle.resolve("extraction");copyTree(source,extraction,caps);
            var extractionPins=inventory(extraction,caps,null);checkExtraction(extraction,reader,out.resolve("captured-check.stdout"),out.resolve("captured-check.stderr"));
            var index=NextflowFiles.object(NextflowFiles.json(extraction.resolve("original-files.json"),67108864));Path original=Files.createDirectory(out.resolve("original"));materialize(extraction,index,original,caps);
            var originalInputs=inventory(original,caps,null);Path nodes=extraction.resolve("inputs/targets.txt");List<String> literalNodes=nodes(nodes,caps);
            Path program=Files.createDirectory(bundle.resolve("program"));Path exportScript=program.resolve("export_assessment.R");Files.write(exportScript,HopsPostprocessingProvider.resource("export_assessment.R"),StandardOpenOption.CREATE_NEW);
            Files.createDirectory(out.resolve("work"));var environment=environment(provider.toString());
            var command=command(provider.toString(),original.toString(),nodes.toString(),filter,Boolean.parseBoolean(json),caps);
            var invocation=Map.of("producer_root",out.toString(),"provider_root",provider.toString(),"filter",filter,"emit_json",Boolean.parseBoolean(json),"command",command,"environment",environment,"process_limits",caps.process());
            NextflowFiles.writeJson(bundle.resolve("invocation.json"),invocation);
            Path output=out;NextflowProcess.Check integrity=()->{HopsPostprocessingProvider.unchanged(provider,snapshot);same(extractionPins,inventory(extraction,caps,null),"Complete captured MaltExtract input bundle changed");checkSubset(original,originalInputs,caps);};
            execute(command,environment,out.resolve("post-process"),out,caps,integrity);errors(out.resolve("post-process/process.stderr"));
            Path scientific=bundle.resolve("scientific-export.json");
            execute(exportCommand(provider,exportScript,original.resolve("analysis.RData"),scientific),environment,out.resolve("export-process"),out,caps,integrity);errors(out.resolve("export-process/process.stderr"));
            var nativeResult=HopsEvidence.inspect(original,literalNodes,caps.tables());var audit=audit(original,scientific,nativeResult,Boolean.parseBoolean(json),caps);
            flatten(original,bundle,caps);copyTree(out.resolve("post-process"),bundle.resolve("post-process"),caps);copyTree(out.resolve("export-process"),bundle.resolve("export-process"),caps);
            var report=new TreeMap<String,Object>();report.put("status",STATUS);report.put("capacities",caps.document());report.put("provider_setup_receipt",snapshot.receipt());report.put("filter",filter);report.put("emit_json",Boolean.parseBoolean(json));report.put("native_statistics",nativeResult.document());report.put("scientific_audit",audit);report.put("producer_root",out.toString());report.put("provider_root",provider.toString());report.put("new_tool_credit",0);report.put("whole_hops_qualified",false);report.put("producer_authenticated",false);
            NextflowFiles.writeJson(bundle.resolve("audit.json"),report);Files.writeString(bundle.resolve("WARNINGS.md"),warnings(nativeResult),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            integrity.verify();NextflowFiles.writeJson(bundle.resolve("COMPONENT_COMPLETED.json"),Map.of("status",STATUS,"files",inventory(bundle,caps,"COMPONENT_COMPLETED.json"),"new_tool_credit",0));
            Path finalCheck=Files.createDirectory(out.resolve("final-verification"));verify(bundle,reader,provider,finalCheck,console,error);
            console.println("COMPLETE CAPTURED ORIGINAL HOPS POSTPROCESSING: "+output+"/bundle; inspect WARNINGS.md and original plots/workspace. Whole-HOPS credit remains0.");return 0;
        }catch(Exception failure) {
            int code=failure instanceof HopsPostprocessingProvider.Unavailable||failure instanceof NextflowProcess.Unavailable?3:2;
            String status=code==3?"SKIP3 DEPENDENT ANALYSIS SKIPPED":"FAIL2 NO RESULT ADMITTED";error.println("HOPS POSTPROCESSING "+status+": "+failure+"\n"+ACTION);
            if(owned&&out!=null)try{NextflowFiles.writeJson(out.resolve("FAILURE.json"),Map.of("status",status,"error",failure.toString(),"required_action",ACTION));Files.writeString(out.resolve("WARNINGS_FAILURE.md"),"# "+status+"\n\n"+failure+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}catch(IOException diagnostic){error.println("Retain failed output; diagnostic write failed: "+diagnostic);}
            return code;
        }
    }
    private static void verify(Path bundle,Path reader,Path provider,Path out,PrintStream console,PrintStream error)throws IOException {
        nativeFilesystem(out);NextflowFiles.directory(bundle);
        if(Files.exists(bundle.resolve("FAILURE.json"))||Files.exists(bundle.getParent().resolve("FAILURE.json"))||Files.exists(bundle.resolve("WARNINGS_FAILURE.md")))throw new IOException("Failed/provisional HOPS output cannot be admitted");
        var report=NextflowFiles.object(NextflowFiles.json(bundle.resolve("audit.json"),67108864));
        NextflowFiles.keys(report,"status","capacities","provider_setup_receipt","filter","emit_json","native_statistics","scientific_audit","producer_root","provider_root","new_tool_credit","whole_hops_qualified","producer_authenticated");
        if(!STATUS.equals(report.get("status"))||NextflowFiles.integer(report.get("new_tool_credit"),0,0)!=0||!Boolean.FALSE.equals(report.get("whole_hops_qualified"))||!Boolean.FALSE.equals(report.get("producer_authenticated"))||!"def_anc".equals(report.get("filter"))||!(report.get("emit_json") instanceof Boolean))throw new IOException("Contradictory saved HOPS scope/profile claim");
        Caps caps=savedCaps(NextflowFiles.object(report.get("capacities")));var complete=NextflowFiles.object(NextflowFiles.json(bundle.resolve("COMPONENT_COMPLETED.json"),67108864));NextflowFiles.keys(complete,"status","files","new_tool_credit");
        if(!STATUS.equals(complete.get("status"))||NextflowFiles.integer(complete.get("new_tool_credit"),0,0)!=0)throw new IOException("Saved completion scope differs");
        var expected=pins(complete.get("files"),caps);same(expected,inventory(bundle,caps,"COMPONENT_COMPLETED.json"),"Complete saved HOPS bundle inventory differs");
        checkExtraction(bundle.resolve("extraction"),reader,out.resolve("extraction-check.stdout"),out.resolve("extraction-check.stderr"));
        var snapshot=HopsPostprocessingProvider.inspect(provider);Path original=Files.createDirectory(out.resolve("original"));
        var index=NextflowFiles.object(NextflowFiles.json(bundle.resolve("original-files.json"),67108864));materialize(bundle,index,original,caps);
        var actualMembers=new TreeSet<String>();for(Object value:index.values())actualMembers.add(NextflowFiles.text(NextflowFiles.object(value).get("member")));
        var savedMembers=new TreeSet<String>();for(String name:expected.keySet())if(name.startsWith("original-content/"))savedMembers.add(name);
        if(!actualMembers.equals(savedMembers))throw new IOException("Complete original HOPS flat member population differs");
        var extractionIndex=NextflowFiles.object(NextflowFiles.json(bundle.resolve("extraction/original-files.json"),67108864));
        for(var entry:extractionIndex.entrySet()) {
            String name=literalMember(NextflowFiles.text(entry.getKey()));var value=NextflowFiles.object(entry.getValue());
            if(!NextflowFiles.pin(original.resolve(name),caps.fileBytes(),()->{}).equals(NextflowFiles.fingerprint(value,caps.fileBytes())))throw new IOException("Original MaltExtract input was changed by postprocessing: "+name);
        }
        byte[] exportSource=HopsPostprocessingProvider.resource("export_assessment.R");Path script=bundle.resolve("program/export_assessment.R");
        if(!Arrays.equals(exportSource,NextflowFiles.bytes(script,2097152)))throw new IOException("Saved workspace-export source differs");
        var nativeResult=HopsEvidence.inspect(original,nodes(bundle.resolve("extraction/inputs/targets.txt"),caps),caps.tables());boolean json=(Boolean)report.get("emit_json");
        var savedAudit=audit(original,bundle.resolve("scientific-export.json"),nativeResult,json,caps);same(nativeResult.document(),report.get("native_statistics"),"Saved native statistics differ");same(savedAudit,report.get("scientific_audit"),"Saved complete scientific audit differs");
        String producer=NextflowFiles.text(report.get("producer_root")),oldProvider=NextflowFiles.text(report.get("provider_root"));
        if(!producer.startsWith("/")||producer.endsWith("/")||!oldProvider.startsWith("/")||oldProvider.endsWith("/"))throw new IOException("Invalid original canonical producer/provider metadata");
        NextflowFiles.fingerprint(report.get("provider_setup_receipt"),8388608);
        var originalCommand=command(oldProvider,producer+"/original",producer+"/bundle/extraction/inputs/targets.txt","def_anc",json,caps);
        var originalEnvironment=environment(oldProvider);var invocation=Map.of("producer_root",producer,"provider_root",oldProvider,"filter","def_anc","emit_json",json,"command",originalCommand,"environment",originalEnvironment,"process_limits",caps.process());
        same(invocation,NextflowFiles.json(bundle.resolve("invocation.json"),16777216),"Original HOPS invocation differs");
        receipt(bundle.resolve("post-process"),originalCommand,producer,caps);errors(bundle.resolve("post-process/process.stderr"));
        var originalExport=exportCommand(Path.of(oldProvider),Path.of(producer+"/bundle/program/export_assessment.R"),Path.of(producer+"/original/analysis.RData"),Path.of(producer+"/bundle/scientific-export.json"));
        receipt(bundle.resolve("export-process"),originalExport,producer,caps);errors(bundle.resolve("export-process/process.stderr"));
        if(!Files.readString(bundle.resolve("WARNINGS.md"),StandardCharsets.UTF_8).equals(warnings(nativeResult)))throw new IOException("Saved prominent warnings differ");
        Files.createDirectory(out.resolve("work"));Path exported=out.resolve("workspace-reexport.json");
        NextflowProcess.Check integrity=()->{HopsPostprocessingProvider.unchanged(provider,snapshot);same(expected,inventory(bundle,caps,"COMPONENT_COMPLETED.json"),"Read-only saved HOPS bundle changed during verification");};
        execute(exportCommand(provider,script,original.resolve("analysis.RData"),exported),environment(provider.toString()),out.resolve("reexport-process"),out,caps,integrity);errors(out.resolve("reexport-process/process.stderr"));
        same(NextflowFiles.json(bundle.resolve("scientific-export.json"),67108864),NextflowFiles.json(exported,67108864),"Fresh original RData export differs from saved complete scientific fields");
        audit(original,exported,nativeResult,json,caps);integrity.verify();
        NextflowFiles.writeJson(out.resolve("VERIFIED.json"),Map.of("status","pass-complete-original-hops-postprocessing-saved-verification","pairs",nativeResult.pairs().size(),"rdata_freshly_decoded",true,"original_fitting_repeated",false,"original_plotting_repeated",false,"producer_authenticated",false,"new_tool_credit",0));
    }
    private static Map<String,Object> audit(Path root,Path scientific,HopsEvidence.Inspection nativeResult,boolean json,Caps caps)throws IOException {
        try {
            var document=NextflowFiles.object(NextflowFiles.json(scientific,67108864));Map<String,Map<String,List<Integer>>> decoded=null;
            if(json) {
                decoded=new LinkedHashMap<>();for(var column:NextflowFiles.object(NextflowFiles.json(root.resolve("heatmap_overview_Wevid.json"),67108864)).entrySet()) {
                    var cells=new LinkedHashMap<String,List<Integer>>();for(var cell:NextflowFiles.object(column.getValue()).entrySet()) {
                        var values=new ArrayList<Integer>();for(Object n:NextflowFiles.list(cell.getValue()))values.add((int)NextflowFiles.integer(n,1,4));cells.put(NextflowFiles.text(cell.getKey()),values);
                    }decoded.put(NextflowFiles.text(column.getKey()),cells);
                }
            }else if(Files.exists(root.resolve("heatmap_overview_Wevid.json")))throw new IOException("Undeclared original JSON output exists");
            var report=HopsAssessmentAudit.audit(nativeResult,document,NextflowFiles.utf8(NextflowFiles.bytes(root.resolve("heatmap_overview_Wevid.tsv"),67108864)),decoded);
            var expected=new TreeSet<>(HopsAssessmentAudit.expectedPdfs(nativeResult));var actual=new TreeSet<String>();
            try(var paths=Files.walk(root)){for(Path p:paths.filter(p->!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)).toList())if(p.getFileName().toString().endsWith(".pdf"))actual.add(root.relativize(p).toString());}
            if(!expected.equals(actual))throw new IOException("Complete original candidate/heatmap PDF population differs");
            for(String name:expected)pdf(root.resolve(name),caps);
            return report;
        }catch(IllegalArgumentException failure){throw new IOException("Original AMPS scientific output disagrees with native rules: "+failure,failure);}
    }
    private static void pdf(Path path,Caps caps)throws IOException {
        var pin=NextflowFiles.pin(path,caps.fileBytes(),()->{});if(pin.bytes()<100)throw new IOException("Truncated original PDF");
        try(var channel=Files.newByteChannel(path)) {
            var header=ByteBuffer.allocate(8);while(header.hasRemaining())if(channel.read(header)<0)throw new IOException("Incomplete PDF header");
            if(!new String(header.array(),StandardCharsets.US_ASCII).startsWith("%PDF-1."))throw new IOException("Original PDF header differs");
            int count=(int)Math.min(pin.bytes(),2048);channel.position(pin.bytes()-count);var tail=ByteBuffer.allocate(count);while(tail.hasRemaining())if(channel.read(tail)<0)throw new IOException("Incomplete PDF trailer");
            if(!new String(tail.array(),StandardCharsets.ISO_8859_1).stripTrailing().endsWith("%%EOF"))throw new IOException("Original PDF trailer is absent");
        }
    }
    private static void receipt(Path dir,List<String> command,String producer,Caps caps)throws IOException {
        var process=NextflowFiles.object(NextflowFiles.json(dir.resolve("process.json"),16777216));var launch=NextflowFiles.object(NextflowFiles.json(dir.resolve("launch.json"),16777216));
        var hard=new ArrayList<>(List.of("/usr/bin/setsid","/usr/bin/prlimit","--as="+caps.process().addressSpaceBytes(),"--cpu="+caps.process().cpuSeconds(),"--fsize="+caps.process().fileBytes(),"--nofile="+caps.process().openFiles(),"--"));hard.addAll(command);
        same(hard,process.get("command"),"Original supervised command differs");same(caps.process(),process.get("limits"),"Original process limits differ");
        if(!"completed-process-only".equals(process.get("status"))||NextflowFiles.integer(process.get("exit_code"),0,0)!=0||!NextflowFiles.list(process.get("owned_live_after_cleanup")).isEmpty()||NextflowFiles.list(process.get("owned_processes")).isEmpty()||!(producer+"/work").equals(process.get("working_directory"))||!producer.equals(process.get("workspace_root"))||process.containsKey("error")||process.containsKey("suppressed"))throw new IOException("Original process receipt does not prove complete controlled execution");
        for(String key:List.of("command","limits","working_directory","workspace_root","owned_launcher_pid"))same(process.get(key),launch.get(key),"Original launch/final receipts disagree: "+key);
        if(!"running".equals(launch.get("status")))throw new IOException("Original launch receipt lacks its running state");
    }
    private static void execute(List<String> command,Map<String,String> env,Path record,Path root,Caps caps,NextflowProcess.Check integrity)throws IOException {
        var result=NfcoreProcess.executeSession(command,env,record,root.resolve("work"),root,caps.process(),integrity,()->{if(Thread.currentThread().isInterrupted())throw new IllegalStateException("HOPS processing interrupted");});
        if(result.exitCode()!=0||!result.ownedLiveAfterCleanup().isEmpty())throw new IOException("Original R process failed or owned workers remain live; inspect "+record);
    }
    private static List<String> command(String provider,String original,String nodes,String filter,boolean json,Caps caps) {
        var c=new ArrayList<>(List.of(provider+"/r/bin/Rscript","--vanilla",provider+"/program/postprocessing.AMPS.r","-r",original,"-m",filter,"-t",Integer.toString(caps.threads()),"-n",nodes));if(json)c.add("-j");return List.copyOf(c);
    }
    private static List<String> exportCommand(Path provider,Path script,Path rdata,Path target){return List.of(provider+"/r/bin/Rscript","--vanilla",script.toString(),rdata.toString(),target.toString());}
    private static Map<String,String> environment(String provider){return Map.of("PATH",provider+"/r/bin:/usr/bin:/bin","LC_ALL","C.UTF-8","LANG","C.UTF-8","TZ","UTC","OMP_NUM_THREADS","1","OPENBLAS_NUM_THREADS","1","R_ENVIRON_USER","/dev/null","R_PROFILE_USER","/dev/null","R_LIBS_USER","/nonexistent-omicsmatic-hops-r-user-library");}
    private static void errors(Path stderr)throws IOException {String s=NextflowFiles.utf8(NextflowFiles.bytes(stderr,33554432));if(java.util.regex.Pattern.compile("(?m)^(?:Error(?: in|:)|Execution halted|Fatal error|SEVERE:)").matcher(s).find())throw new IOException("Original R reported an error even if its launcher returned0; inspect "+stderr);}
    private static void checkExtraction(Path source,Path reader,Path stdout,Path stderr)throws IOException {
        try(var so=new PrintStream(Files.newOutputStream(stdout,StandardOpenOption.CREATE_NEW),false,StandardCharsets.UTF_8);var se=new PrintStream(Files.newOutputStream(stderr,StandardOpenOption.CREATE_NEW),false,StandardCharsets.UTF_8)) {
            int code=MaltExtractWorkflowCli.run(new String[]{"--verify",source.toString(),"--runtime",reader.toString()},so,se);
            if(code==3)throw new HopsPostprocessingProvider.Unavailable("MaltExtract reader unavailable; see "+stderr);
            if(code!=0)throw new IOException("Complete MaltExtract evidence refused; inspect "+stderr);
        }
    }
    private static List<String> nodes(Path file,Caps caps)throws IOException {
        var nodes=new LinkedHashSet<String>();for(String line:NextflowFiles.utf8(NextflowFiles.bytes(file,16777216)).lines().toList()) {
            if(!line.matches("[A-Za-z0-9_. -]*"))throw new IOException("Unqualified/unsafe original R scan or unquoted plot-directory identity; retain the literal input without renaming");
            String name=line.strip().replace(' ','_');if(name.isEmpty())continue;
            if(name.equals(".")||name.equals("..")||!name.matches("[A-Za-z0-9_.-]+"))throw new IOException("Original plot identity cannot be represented safely");nodes.add(name);
        }
        if(nodes.isEmpty()||nodes.size()>caps.taxa())throw new IOException("Complete original target population is empty or exceeds capacity");return List.copyOf(nodes);
    }
    private static void nativeFilesystem(Path p)throws IOException {if(!System.getProperty("os.name").equals("Linux")||Set.of("9p","drvfs","cifs","smbfs").contains(Files.getFileStore(p).type()))throw new HopsPostprocessingProvider.Unavailable("Original materialization/execution requires a native Linux filesystem");}
    private static void copyTree(Path source,Path target,Caps caps)throws IOException {
        var pins=inventory(source,caps,null);Files.createDirectory(target);
        for(var entry:pins.entrySet()) {Path destination=target.resolve(entry.getKey());Files.createDirectories(destination.getParent());Files.copy(source.resolve(entry.getKey()),destination);}
        same(pins,inventory(target,caps,null),"Complete capture differs");same(pins,inventory(source,caps,null),"Complete source changed during capture");
    }
    private static Map<String,NextflowFiles.Pin> inventory(Path root,Caps caps,String omitted)throws IOException {
        NextflowFiles.directory(root);var result=new TreeMap<String,NextflowFiles.Pin>();long total=0;
        try(var paths=Files.walk(root)){for(Path p:paths.filter(p->!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)).toList()) {
            String name=NextflowFiles.member(root.relativize(p).toString().replace('\\','/'));if(name.equals(omitted))continue;
            if(result.size()>=caps.files())throw new IOException("Complete bundle member capacity exceeded");var pin=NextflowFiles.pin(p,caps.fileBytes(),()->{});total=Math.addExact(total,pin.bytes());if(total>caps.bundleBytes())throw new IOException("Complete bundle byte capacity exceeded");result.put(name,pin);
        }}return result;
    }
    private static Map<String,NextflowFiles.Pin> pins(Object value,Caps caps)throws IOException {var result=new TreeMap<String,NextflowFiles.Pin>();for(var e:NextflowFiles.object(value).entrySet())result.put(NextflowFiles.member(NextflowFiles.text(e.getKey())),NextflowFiles.fingerprint(e.getValue(),caps.fileBytes()));if(result.size()>caps.files())throw new IOException("Saved member capacity exceeded");return result;}
    private static void checkSubset(Path root,Map<String,NextflowFiles.Pin> pins,Caps caps)throws IOException {for(var e:pins.entrySet())if(!NextflowFiles.pin(root.resolve(e.getKey()),caps.fileBytes(),()->{}).equals(e.getValue()))throw new IOException("Original supplied table changed: "+e.getKey());}
    private static String literalMember(String name)throws IOException {if(name.isEmpty()||name.startsWith("/")||name.chars().anyMatch(c->c<32||c==127))throw new IOException("Unsafe literal original member");for(String p:name.split("/",-1))if(Set.of("",".","..").contains(p))throw new IOException("Unsafe literal original member");return name;}
    private static void materialize(Path source,Map<?,?> index,Path target,Caps caps)throws IOException {
        if(index.size()>caps.files())throw new IOException("Complete original output member capacity exceeded");long total=0;var members=new HashSet<String>();
        for(var entry:index.entrySet()) {
            String name=literalMember(NextflowFiles.text(entry.getKey()));var value=NextflowFiles.object(entry.getValue());NextflowFiles.keys(value,"member","bytes","sha256");String member=NextflowFiles.member(NextflowFiles.text(value.get("member")));
            if(!members.add(member))throw new IOException("Original flat members are not one-to-one");var expected=NextflowFiles.fingerprint(value,caps.fileBytes());total=Math.addExact(total,expected.bytes());if(total>caps.bundleBytes())throw new IOException("Complete original output byte capacity exceeded");
            Path input=source.resolve(member),output=target.resolve(name);if(!NextflowFiles.pin(input,caps.fileBytes(),()->{}).equals(expected))throw new IOException("Original flat input differs");Files.createDirectories(output.getParent());Files.copy(input,output);if(!NextflowFiles.pin(output,caps.fileBytes(),()->{}).equals(expected))throw new IOException("Literal materialization differs");
        }
    }
    private static void flatten(Path original,Path bundle,Caps caps)throws IOException {
        Path flat=Files.createDirectory(bundle.resolve("original-content"));var index=new TreeMap<String,Object>();int n=0;long bytes=0;
        try(var rows=Files.walk(original)){for(Path p:rows.filter(p->!Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)).sorted().toList()) {
            String name=literalMember(original.relativize(p).toString());if(n>=caps.files())throw new IOException("Complete original member capacity exceeded");var pin=NextflowFiles.pin(p,caps.fileBytes(),()->{});bytes=Math.addExact(bytes,pin.bytes());if(bytes>caps.bundleBytes())throw new IOException("Complete original bytes exceed capacity");String member=String.format(Locale.ROOT,"original-content/f%06d.bin",++n);Files.copy(p,bundle.resolve(member));index.put(name,Map.of("member",member,"bytes",pin.bytes(),"sha256",pin.sha256()));
        }}NextflowFiles.writeJson(bundle.resolve("original-files.json"),index);
    }
    private static Caps caps(Map<String,String> o)throws IOException {return new Caps(number(o,"--maximum-file-bytes",1000000000,1000000000),number(o,"--maximum-bundle-bytes",8000000000L,100000000000L),(int)number(o,"--maximum-files",100000,300000),number(o,"--maximum-table-rows",1000000,10000000),number(o,"--maximum-table-cells",100000000,1000000000),(int)number(o,"--maximum-samples",100,10000),(int)number(o,"--maximum-taxa",10000,100000),(int)number(o,"--threads",2,32),number(o,"--wall-seconds",600,151200));}
    private static Caps savedCaps(Map<?,?> o)throws IOException {NextflowFiles.keys(o,"file_bytes","bundle_bytes","files","rows","cells","samples","taxa","threads","wall_seconds");return new Caps(NextflowFiles.integer(o.get("file_bytes"),1,1000000000),NextflowFiles.integer(o.get("bundle_bytes"),1,100000000000L),(int)NextflowFiles.integer(o.get("files"),1,300000),NextflowFiles.integer(o.get("rows"),1,10000000),NextflowFiles.integer(o.get("cells"),1,1000000000),(int)NextflowFiles.integer(o.get("samples"),1,10000),(int)NextflowFiles.integer(o.get("taxa"),1,100000),(int)NextflowFiles.integer(o.get("threads"),1,32),NextflowFiles.integer(o.get("wall_seconds"),1,151200));}
    private static long number(Map<String,String> o,String key,long fallback,long maximum)throws IOException {String s=o.getOrDefault(key,Long.toString(fallback));if(!s.matches("[1-9][0-9]*"))throw new IOException("Positive exact integer required: "+key);try{long n=Long.parseLong(s);if(n>maximum)throw new IOException("Capacity exceeds supported range: "+key);return n;}catch(NumberFormatException e){throw new IOException("Capacity integer overflow",e);}}
    private static Map<String,String> options(String[] args)throws IOException {var allowed=Set.of("--verify","--maltextract-bundle","--reader-runtime","--post-runtime","--out","--filter","--emit-json","--maximum-file-bytes","--maximum-bundle-bytes","--maximum-files","--maximum-table-rows","--maximum-table-cells","--maximum-samples","--maximum-taxa","--threads","--wall-seconds");var o=new TreeMap<String,String>();for(int i=0;i<args.length;i+=2)if(i+1>=args.length||!allowed.contains(args[i])||o.putIfAbsent(args[i],args[i+1])!=null)throw new IOException("Require unique complete known options; see --help");return o;}
    private static String required(Map<String,String> o,String key)throws IOException {String s=o.get(key);if(s==null||s.isEmpty())throw new IOException("Missing explicit option: "+key);return s;}
    private static Path path(String s)throws IOException {Path p=Path.of(s).toAbsolutePath().normalize();Path e=Files.exists(p,LinkOption.NOFOLLOW_LINKS)?p:p.getParent();if(e==null||!e.toRealPath().equals(e))throw new IOException("Canonical existing path/parent required");return p;}
    private static void same(Object a,Object b,String cause)throws IOException {if(!NextflowJson.json(a).equals(NextflowJson.json(b)))throw new IOException(cause);}
    private static String warnings(HopsEvidence.Inspection result){return "# ORIGINAL HOPS POSTPROCESSING WARNINGS AND REQUIRED ACTIONS\n\n**This is an output-producing postprocessing stage. Whole HOPS/MALT qualification, manual biological assessment and the250-paper implementation remain incomplete.**\n\nAll original input tables, plots, RData, TSV/optional JSON and process logs are retained unchanged. RData is freshly decoded with exact R4.0.5 during saved verification, then complete scientific cells and class matrices are compared with native Java. PDF checks cover complete expected file identities, headers/trailers and retained bytes; arbitrary plot graphics are produced by the unchanged upstream script, not independently reimplemented. Runtime setup inventories and SHA256 establish recorded integrity, not distributor signatures or producer authenticity.\n\n"+String.join("\n\n",result.warnings())+"\n\nRead all original process stderr for R warnings. Sampled aggregate RSS is not a hard memory bound. Literal target names currently require qualified ASCII letters/digits/spaces/underscore/dot/hyphen because the unchanged source uses an unquoted shell mkdir. Unsupported names are refused without renaming.\n\n"+ACTION+"\n";}
    private static String help(){return "--maltextract-bundle COMPLETE_BUNDLE --reader-runtime EXACT_17_READER --post-runtime PUBLIC_R405_PROVIDER --filter def_anc --emit-json true|false --out NEW_NATIVE_LINUX_DIRECTORY\n--verify COMPLETE_HOPS_BUNDLE --reader-runtime EXACT_17_READER --post-runtime PUBLIC_R405_PROVIDER --out NEW_NATIVE_LINUX_VERIFICATION\nOriginal default mode may be requested for retained diagnostics but its known upstream failure is never admitted. Optional positive capacities: --maximum-file-bytes, --maximum-bundle-bytes, --maximum-files, --maximum-table-rows, --maximum-table-cells, --maximum-samples, --maximum-taxa, --threads, --wall-seconds. No automatic install or silent version/method substitution.\n"+ACTION;}
}
