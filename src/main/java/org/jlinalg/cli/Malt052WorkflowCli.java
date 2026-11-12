/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Complete owned DNA FASTA -> explicit mapped index -> original BlastN workflow. */
public final class Malt052WorkflowCli {
    private Malt052WorkflowCli() { }
    private static final String STATUS="complete-owned-malt052-build-and-blastn-native-mapping-alternative";
    private static final String ACTION="Required action: retain the complete workflow, original inputs, unclassified native/index and separately mapped derived/index, and both stages' diagnostics. Supply all FASTA references, the complete declared accession mapping, complete unique-name FASTQ and a permitted pinned Linux x86_64 MALT0.5.2/Java14 provider. Declare --method native-accession-alternative and --profile CUSTOM052|STANDALONE explicitly. Install only where permitted; if a required runtime/install/execution is unavailable or forbidden, skip dependent analysis. Correct the named input, resource or integrity issue before retrying a NEW directory. Saved verification requires only the exact separate MALT0.5.2 reader provider and modern Java, not original Java14 execution. Historical reference databases and manual biological interpretation remain separate requirements.";
    private static final Set<String> BUILD=Set.of("--fasta","--fasta-list","--mapping","--format","--namespace","--method","--threads","--heap-mib","--gc-threads","--maximum-wall-seconds","--maximum-file-bytes","--maximum-input-bytes","--maximum-fasta-files","--maximum-references","--maximum-reference-bases","--maximum-line-characters","--maximum-mapping-rows");
    private static final Set<String> ALIGN=Set.of("--query","--profile","--alignment-type","--strand","--memory-mode","--threads","--include-unaligned","--minimum-score","--maximum-expected","--minimum-identity","--maximum-alignments","--maximum-alignments-per-reference","--top-percent","--minimum-support","--minimum-support-percent","--gap-open","--gap-extend","--maximum-file-bytes","--maximum-input-bytes","--maximum-queries","--maximum-query-bases","--maximum-output-alignments","--maximum-wall-seconds","--heap-mib","--gc-threads");
    private static final class Skipped extends IOException {
        private static final long serialVersionUID=1L;
        Skipped(String message){super(message);}
    }
    record WorkflowLimits(int files,long bytes,long fileBytes) { }
    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    static int run(String[] args,PrintStream stdout,PrintStream stderr) {
        Path out=null;boolean owned=false;String stage="input validation";
        try {
            if(args.length==1&&args[0].equals("--help")){stdout.println(help());return 0;}
            var o=options(args);out=canonical(required(o,"--out"),false);
            if(Files.exists(out,LinkOption.NOFOLLOW_LINKS))throw new IOException("Output must be NEW; previous attempts remain unchanged");
            if(o.containsKey("--verify")) {
                if(!o.keySet().equals(Set.of("--verify","--reader-runtime","--out")))throw new IOException("Saved workflow verification accepts only complete root, reader runtime and NEW output");
                Path root=canonical(o.get("--verify"),true),reader=Path.of(required(o,"--reader-runtime")).toAbsolutePath().normalize();
                disjoint(out,List.of(root,reader));Files.createDirectory(out);owned=true;stage="complete saved workflow verification";verify(root,reader,out);
                stdout.println("PASS COMPLETE SAVED MALT052 BUILD AND BLASTN WORKFLOW; no original fitting repeated");return 0;
            }
            validateFit(o);var caps=workflowLimits(o);var buildCaps=Malt052BuildCli.limits(o);
            Path runtime=Path.of(required(o,"--runtime")).toAbsolutePath().normalize();
            var originals=originals(o,buildCaps.files(),buildCaps.fileBytes());disjoint(out,originals);disjoint(out,List.of(runtime));
            var pins=new TreeMap<Path,NextflowFiles.Pin>();
            for(Path p:originals)pins.put(p,NextflowFiles.pin(p,buildCaps.fileBytes(),()->{}));
            Files.createDirectory(out);owned=true;
            var invocation=new TreeMap<String,Object>();invocation.put("schema","MALT052_BUILD_BLASTN_WORKFLOW_V1");invocation.put("producer_root",out.toString());invocation.put("working_directory",Path.of("").toAbsolutePath().normalize().toString());invocation.put("options",o);
            NextflowFiles.writeJson(out.resolve("workflow-invocation.json"),invocation);
            stage="original seed construction and explicit native accession mapping";
            var build=arguments(o,BUILD,"--runtime",runtime.toString(),"--out",out.resolve("index-build").toString());
            requireStage(call(true,build,out.resolve("index-build.stdout"),out.resolve("index-build.stderr")),stage,out.resolve("index-build.stderr"));
            stage="complete built-index verification";
            requireStage(call(true,List.of("--verify",out.resolve("index-build").toString()),out.resolve("index-build-verify.stdout"),out.resolve("index-build-verify.stderr")),stage,out.resolve("index-build-verify.stderr"));
            stage="original complete-query BlastN alignment";
            var align=arguments(o,ALIGN,"--runtime",runtime.toString(),"--database",out.resolve("index-build/derived/index").toString(),"--index-provenance","native-alternative","--out",out.resolve("alignment").toString());
            requireStage(call(false,align,out.resolve("alignment.stdout"),out.resolve("alignment.stderr")),stage,out.resolve("alignment.stderr"));
            stage="complete alignment verification";
            requireStage(call(false,List.of("--verify",out.resolve("alignment").toString(),"--runtime",runtime.toString()),out.resolve("alignment-verify.stdout"),out.resolve("alignment-verify.stderr")),stage,out.resolve("alignment-verify.stderr"));
            stage="complete cross-stage and original-input binding";
            var index=json(out.resolve("index-build/audit.json"));var alignment=json(out.resolve("alignment/audit.json"));
            var sources=sourceBindings(out,invocation,caps.fileBytes());
            for(var row:sources){Path original=Path.of(NextflowFiles.text(row.get("location")));same(pins.get(original),row.get("pin"),"Workflow captured source differs from original input: "+original);}
            for(var row:pins.entrySet())same(row.getValue(),NextflowFiles.pin(row.getKey(),buildCaps.fileBytes(),()->{}),"Original input changed during complete workflow: "+row.getKey());
            NextflowFiles.writeJson(out.resolve("source-bindings.json"),sources);
            var report=new TreeMap<String,Object>();report.put("status",STATUS);report.put("producer_root",out.toString());report.put("method",o.get("--method"));report.put("profile",o.get("--profile"));report.put("index_build",index);report.put("alignment",alignment);report.put("new_tool_credit",0);report.put("whole_malt_qualified",false);report.put("biological_result_admitted",false);report.put("producer_authenticated",false);
            NextflowFiles.writeJson(out.resolve("workflow-audit.json"),report);Files.writeString(out.resolve("WARNINGS.md"),warnings(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            bind(out,invocation,report,caps);
            NextflowFiles.writeJson(out.resolve("COMPONENT_COMPLETED.json"),Map.of("status",STATUS,"files",inventory(out,"COMPONENT_COMPLETED.json",caps),"new_tool_credit",0));
            stdout.println("COMPLETE OWNED MALT052 BUILD AND BLASTN: "+out+"; read WARNINGS.md and both stages' original stderr. Catalog/whole-tool qualification remains separate.");return 0;
        }catch(Exception failure) {
            int code=failure instanceof Skipped?3:2;String status=code==3?"SKIP3 DEPENDENT ANALYSIS SKIPPED":"FAIL2 NO RESULT ADMITTED";
            stderr.println("MALT052 WORKFLOW "+status+" at "+stage+": "+failure+"\n"+ACTION);
            if(owned&&out!=null)try{NextflowFiles.writeJson(out.resolve("FAILURE.json"),Map.of("status",status,"stage",stage,"cause",failure.toString(),"required_action",ACTION));Files.writeString(out.resolve("WARNINGS_FAILURE.md"),"# "+status+"\n\nStage: "+stage+"\n\n"+failure+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}catch(IOException diagnostic){stderr.println("Retain failed workflow; diagnostic write failed: "+diagnostic);}
            return code;
        }
    }
    private static void verify(Path root,Path reader,Path out)throws IOException {
        NextflowFiles.directory(root);
        if(Files.exists(root.resolve("FAILURE.json"))||Files.exists(root.resolve("WARNINGS_FAILURE.md")))throw new IOException("Failed/provisional workflow cannot admit a final result");
        var invocation=json(root.resolve("workflow-invocation.json"));var options=strings(invocation.get("options"));validateFit(options);var caps=workflowLimits(options);
        var complete=json(root.resolve("COMPONENT_COMPLETED.json"));NextflowFiles.keys(complete,"status","files","new_tool_credit");
        if(!STATUS.equals(complete.get("status"))||NextflowFiles.integer(complete.get("new_tool_credit"),0,Long.MAX_VALUE)!=0)throw new IOException("Unsupported workflow completion scope");
        var expected=new TreeMap<String,NextflowFiles.Pin>();for(var e:NextflowFiles.object(complete.get("files")).entrySet())expected.put(NextflowFiles.member(NextflowFiles.text(e.getKey())),NextflowFiles.fingerprint(e.getValue(),caps.fileBytes()));
        same(expected,inventory(root,"COMPONENT_COMPLETED.json",caps),"Complete workflow inventory differs");
        if(!expected.containsKey("index-build/COMPONENT_COMPLETED.json")||!expected.containsKey("alignment/COMPONENT_COMPLETED.json"))throw new IOException("Complete nested stage markers are missing");
        bind(root,invocation,json(root.resolve("workflow-audit.json")),caps);
        requireStage(call(true,List.of("--verify",root.resolve("index-build").toString()),out.resolve("index-build-verify.stdout"),out.resolve("index-build-verify.stderr")),"saved seed-build scientific verification",out.resolve("index-build-verify.stderr"));
        requireStage(call(false,List.of("--verify",root.resolve("alignment").toString(),"--runtime",reader.toString()),out.resolve("alignment-verify.stdout"),out.resolve("alignment-verify.stderr")),"saved alignment scientific verification",out.resolve("alignment-verify.stderr"));
        same(expected,inventory(root,"COMPONENT_COMPLETED.json",caps),"Read-only workflow verification changed complete inputs");
        NextflowFiles.writeJson(out.resolve("VERIFIED.json"),Map.of("status","pass-complete-owned-malt052-build-blastn-workflow","identical_complete_index_handoff",true,"complete_fasta_mapping_query_rechecked",true,"original_fitting_repeated",false,"producer_authenticated",false,"new_tool_credit",0));
    }
    static void bind(Path root,Map<?,?> invocation,Map<?,?> report,WorkflowLimits caps)throws IOException {
        NextflowFiles.keys(invocation,"schema","producer_root","working_directory","options");
        if(!"MALT052_BUILD_BLASTN_WORKFLOW_V1".equals(invocation.get("schema")))throw new IOException("Unknown complete workflow invocation schema");
        var o=strings(invocation.get("options"));validateFit(o);String producer=linuxAbsolute(NextflowFiles.text(invocation.get("producer_root"))),cwd=linuxAbsolute(NextflowFiles.text(invocation.get("working_directory")));
        same(producer,resolveOriginal(cwd,o.get("--out")),"Original workflow output declaration differs");
        NextflowFiles.keys(report,"status","producer_root","method","profile","index_build","alignment","new_tool_credit","whole_malt_qualified","biological_result_admitted","producer_authenticated");
        if(!STATUS.equals(report.get("status"))||NextflowFiles.integer(report.get("new_tool_credit"),0,Long.MAX_VALUE)!=0||!Boolean.FALSE.equals(report.get("whole_malt_qualified"))||!Boolean.FALSE.equals(report.get("biological_result_admitted"))||!Boolean.FALSE.equals(report.get("producer_authenticated")))throw new IOException("Contradictory workflow scope/biology claim");
        same(producer,report.get("producer_root"),"Workflow producer root differs");same(o.get("--method"),report.get("method"),"Workflow mapping method declaration differs");same(o.get("--profile"),report.get("profile"),"Workflow profile declaration differs");
        var build=json(root.resolve("index-build/audit.json"));var alignment=json(root.resolve("alignment/audit.json"));
        same(build,report.get("index_build"),"Workflow index-build summary differs");same(alignment,report.get("alignment"),"Workflow alignment summary differs");
        same(o.get("--method"),build.get("method"),"Index mapping method differs");same(o.get("--format"),build.get("format"),"Index mapping format differs");same(o.get("--namespace"),build.get("namespace"),"Index mapping namespace differs");
        same(Malt052BuildCli.limits(o).document(),build.get("capacities"),"Index-build resource declaration differs");
        var parameters=new TreeMap<>(o);parameters.put("--index-provenance","native-alternative");same(Malt052AlignCli.parameters(parameters).document(),alignment.get("parameters"),"Alignment parameter/profile declaration differs");same(Malt052AlignCli.capacities(o).document(),alignment.get("capacities"),"Alignment resource declaration differs");
        same(build.get("runtime_inventory_sha256"),alignment.get("runtime_inventory_sha256"),"Cross-stage original provider differs");
        String runtime=resolveOriginal(cwd,o.get("--runtime"));
        for(String name:List.of("index-build","alignment")) {
            var process=json(root.resolve(name+"/native/process/process.json"));same(producer+"/"+name,process.get("workspace_root"),"Original stage output root differs: "+name);
            var command=NextflowFiles.strings(process.get("command"));if(command.size()<8)throw new IOException("Incomplete original stage command");same(runtime+"/jre/bin/java",command.get(7),"Original runtime declaration differs: "+name);
        }
        same(inventory(root.resolve("index-build/derived/index"),null,caps),inventory(root.resolve("alignment/inputs/database"),null,caps),"Alignment did not consume the complete identical built index");
        var sources=sourceBindings(root,invocation,caps.fileBytes());same(sources,NextflowFiles.json(root.resolve("source-bindings.json"),67108864),"Original complete source bindings differ");
        var fasta=sources.stream().filter(r->r.get("role").equals("fasta")).toList();var members=new ArrayList<String>();var locations=new ArrayList<Map<String,Object>>();
        for(var row:fasta){String member=NextflowFiles.text(row.get("member")).substring("index-build/".length());members.add(member);locations.add(Map.of("member",member,"original_location",row.get("location")));}
        same(members,build.get("fasta_members"),"Complete original FASTA order/population differs");same(locations,build.get("source_locations"),"Original FASTA source locations differ");
        for(var row:sources) {
            String role=NextflowFiles.text(row.get("role")),member=NextflowFiles.text(row.get("member"));
            if(role.equals("mapping"))same(member.substring("index-build/".length()),build.get("mapping_member"),"Original mapping source member differs");
            if(role.equals("query"))same(member.substring("alignment/".length()),alignment.get("query"),"Original query source member differs");
        }
        if(!Files.readString(root.resolve("WARNINGS.md"),StandardCharsets.UTF_8).equals(warnings()))throw new IOException("Workflow interpretation/recovery warnings differ");
    }
    private static List<Map<String,Object>> sourceBindings(Path root,Map<?,?> invocation,long maximum)throws IOException {
        var o=strings(invocation.get("options"));String cwd=linuxAbsolute(NextflowFiles.text(invocation.get("working_directory")));var result=new ArrayList<Map<String,Object>>();var locations=new ArrayList<String>();
        if(o.containsKey("--fasta"))locations.add(resolveOriginal(cwd,o.get("--fasta")));
        else {
            String list=resolveOriginal(cwd,o.get("--fasta-list"));var rows=listRows(root.resolve("index-build/inputs/original-fasta-list.tsv"),maximum);
            String parent=list.lastIndexOf('/')==0?"/":list.substring(0,list.lastIndexOf('/'));
            for(String row:rows)locations.add(resolveOriginal(parent,row));
            addSource(result,root,"fasta-list",list,"index-build/inputs/original-fasta-list.tsv",maximum);
        }
        if(new HashSet<>(locations).size()!=locations.size())throw new IOException("Duplicate original FASTA source binding");
        for(int i=0;i<locations.size();i++){String location=locations.get(i);addSource(result,root,"fasta",location,"index-build/inputs/fasta/"+String.format(Locale.ROOT,"%06d",i)+(location.endsWith(".gz")?".fa.gz":".fa"),maximum);}
        String mapping=resolveOriginal(cwd,o.get("--mapping")),query=resolveOriginal(cwd,o.get("--query"));
        addSource(result,root,"mapping",mapping,"index-build/inputs/mapping.tsv"+(mapping.endsWith(".gz")?".gz":""),maximum);addSource(result,root,"query",query,"alignment/inputs/reads.fastq"+(query.endsWith(".gz")?".gz":""),maximum);return List.copyOf(result);
    }
    private static void addSource(List<Map<String,Object>> rows,Path root,String role,String location,String member,long maximum)throws IOException {rows.add(Map.of("role",role,"location",location,"member",member,"pin",NextflowFiles.pin(root.resolve(member),maximum,()->{})));}
    private static List<Path> originals(Map<String,String> o,int maximumFiles,long maximumBytes)throws IOException {
        var paths=new ArrayList<Path>();
        if(o.containsKey("--fasta"))paths.add(canonical(o.get("--fasta"),true));
        else {Path list=canonical(o.get("--fasta-list"),true);var rows=listRows(list,maximumBytes);if(rows.size()>maximumFiles)throw new IOException("Complete FASTA file capacity exceeded");for(String row:rows)paths.add(canonical(list.getParent().resolve(row).toString(),true));paths.add(list);}
        if(new HashSet<>(paths).size()!=paths.size())throw new IOException("Duplicate complete FASTA source file");paths.add(canonical(o.get("--mapping"),true));paths.add(canonical(o.get("--query"),true));return List.copyOf(paths);
    }
    private static List<String> listRows(Path file,long maximum)throws IOException {
        var rows=NextflowFiles.utf8(NextflowFiles.bytes(file,(int)Math.min(16777216,maximum))).lines().toList();
        if(rows.size()<2||!rows.get(0).equals("fasta"))throw new IOException("Literal one-column fasta header and complete nonempty population required");
        for(String row:rows.subList(1,rows.size()))if(row.isBlank()||row.indexOf('\t')>=0||row.indexOf('\0')>=0)throw new IOException("Require one complete FASTA path per row");return rows.subList(1,rows.size());
    }
    static String resolveOriginal(String cwd,String raw)throws IOException {
        linuxAbsolute(cwd);if(raw==null||raw.isEmpty()||raw.indexOf('\\')>=0||raw.chars().anyMatch(c->c<32||c==127))throw new IOException("Invalid captured original Linux path");
        String combined=raw.startsWith("/")?raw:cwd+"/"+raw;var parts=new ArrayDeque<String>();
        for(String part:combined.split("/",-1)){if(part.isEmpty()||part.equals("."))continue;if(part.equals("..")){if(parts.isEmpty())throw new IOException("Original path escapes filesystem root");parts.removeLast();}else parts.addLast(part);}
        return linuxAbsolute("/"+String.join("/",parts));
    }
    private static String linuxAbsolute(String value)throws IOException {
        if(value.equals("/"))return value;
        if(!value.startsWith("/")||value.length()<2||value.indexOf('\\')>=0||value.chars().anyMatch(c->c<32||c==127))throw new IOException("Require captured canonical absolute Linux path");
        for(String part:value.substring(1).split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals(".."))throw new IOException("Noncanonical captured original Linux path");return value;
    }
    private static void validateFit(Map<String,String> o)throws IOException {
        var allowed=new HashSet<>(BUILD);allowed.addAll(ALIGN);allowed.addAll(Set.of("--runtime","--out","--maximum-workflow-files","--maximum-workflow-bytes"));
        if(!allowed.containsAll(o.keySet())||!o.keySet().containsAll(Set.of("--runtime","--mapping","--format","--namespace","--method","--profile","--query","--out"))||o.containsKey("--fasta")==o.containsKey("--fasta-list"))throw new IOException("Declare complete fit inputs, explicit method/profile and exactly one FASTA source form");
        if(!o.get("--method").equals("native-accession-alternative"))throw new IOException("Explicit native-accession-alternative selection required; original accession-map path is unqualified");
        Malt052BuildCli.limits(o);Malt052AlignCli.parameters(o);Malt052AlignCli.capacities(o);workflowLimits(o);
    }
    static WorkflowLimits workflowLimits(Map<String,String> o)throws IOException {return new WorkflowLimits((int)number(o,"--maximum-workflow-files",500000,1,10000000),number(o,"--maximum-workflow-bytes",1000000000000L,1,1000000000000000L),Malt052BuildCli.limits(o).fileBytes());}
    private static long number(Map<String,String> o,String key,long fallback,long minimum,long maximum)throws IOException {String raw=o.getOrDefault(key,Long.toString(fallback));try{if(!raw.matches("[1-9][0-9]*"))throw new NumberFormatException();long v=Long.parseLong(raw);if(v<minimum||v>maximum)throw new NumberFormatException();return v;}catch(NumberFormatException failure){throw new IOException("Positive exact bounded workflow capacity required: "+key,failure);}}
    private static Map<String,String> options(String[] args)throws IOException {var allowed=new HashSet<>(BUILD);allowed.addAll(ALIGN);allowed.addAll(Set.of("--runtime","--out","--verify","--reader-runtime","--maximum-workflow-files","--maximum-workflow-bytes"));var result=new TreeMap<String,String>();for(int i=0;i<args.length;i+=2)if(i+1>=args.length||!allowed.contains(args[i])||result.putIfAbsent(args[i],args[i+1])!=null)throw new IOException("Require unique complete known options; see --help");return result;}
    private static Map<String,String> strings(Object value)throws IOException {var result=new TreeMap<String,String>();for(var e:NextflowFiles.object(value).entrySet())result.put(NextflowFiles.text(e.getKey()),NextflowFiles.text(e.getValue()));return result;}
    private static Map<?,?> json(Path path)throws IOException{return NextflowFiles.object(NextflowFiles.json(path,67108864));}
    private static List<String> arguments(Map<String,String> options,Set<String> selected,String... prefix){var args=new ArrayList<String>(List.of(prefix));for(var row:options.entrySet())if(selected.contains(row.getKey()))args.addAll(List.of(row.getKey(),row.getValue()));return args;}
    private static int call(boolean build,List<String> args,Path stdout,Path stderr)throws IOException {try(var so=new PrintStream(Files.newOutputStream(stdout,StandardOpenOption.CREATE_NEW),false,StandardCharsets.UTF_8);var se=new PrintStream(Files.newOutputStream(stderr,StandardOpenOption.CREATE_NEW),false,StandardCharsets.UTF_8)){return build?Malt052BuildCli.run(args.toArray(String[]::new),so,se):Malt052AlignCli.run(args.toArray(String[]::new),so,se);}}
    private static void requireStage(int code,String stage,Path diagnostic)throws IOException {if(code==3)throw new Skipped(stage+" unavailable; inspect "+diagnostic);if(code!=0)throw new IOException(stage+" failed; inspect "+diagnostic);}
    private static Map<String,NextflowFiles.Pin> inventory(Path root,String omitted,WorkflowLimits caps)throws IOException {NextflowFiles.directory(root);var result=new TreeMap<String,NextflowFiles.Pin>();long bytes=0;try(var paths=Files.walk(root)){var iterator=paths.iterator();while(iterator.hasNext()){Path p=iterator.next();if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS))continue;String member=NextflowFiles.member(root.relativize(p).toString().replace('\\','/'));if(member.equals(omitted))continue;if(result.size()>=caps.files())throw new IOException("Complete workflow file capacity exceeded");var pin=NextflowFiles.pin(p,caps.fileBytes(),()->{});bytes=Math.addExact(bytes,pin.bytes());if(bytes>caps.bytes())throw new IOException("Complete workflow byte capacity exceeded");result.put(member,pin);}}return result;}
    private static Path canonical(String value,boolean exists)throws IOException {Path p=Path.of(value).toAbsolutePath().normalize(),check=exists?p:p.getParent();if(check==null||!check.toRealPath().equals(check))throw new IOException("Canonical existing source/path parent required: "+p);return p;}
    private static void disjoint(Path out,List<Path> inputs)throws IOException {for(Path p:inputs)if(out.startsWith(p)||p.startsWith(out))throw new IOException("Owned output must not overlap original inputs/provider");}
    private static String required(Map<String,String> o,String key)throws IOException {String value=o.get(key);if(value==null||value.isEmpty())throw new IOException("Missing explicit option: "+key);return value;}
    private static void same(Object a,Object b,String cause)throws IOException {if(!NextflowJson.json(a).equals(NextflowJson.json(b)))throw new IOException(cause);}
    private static String warnings(){return "# MALT052 WORKFLOW WARNINGS AND REQUIRED ACTIONS\n\n**The accession mapping stage is the explicitly selected native-accession-alternative. Original MALT0.5.2 constructs the unclassified seed index; its original accession-map path is unqualified. Preserve both native/index and derived/index.**\n\nUnchanged original MALT0.5.2 performs BlastN fitting against the complete mapped index produced by this build. CUSTOM052 represents the reported SemiGlobal/90%/25-alignments/100000-per-reference profile; STANDALONE is a distinct explicit choice. Further options remain explicit departures from these defaults. Resource and population capacities refuse complete analysis rather than truncate inputs. The paper's130G/16-worker resources are representable but are not claimed executed by software controls. Historical2017/2021 reference populations, author-setting recovery, full MALT/nf-core/HOPS equivalence and biological authentication remain unproven. Empty or insufficient alignments never establish biological absence.\n\nRead index-build/WARNINGS.md, alignment/WARNINGS.md, both original native/process/process.stderr files and workflow stage stderr. Sampled process RSS is not a hard aggregate RSS bound. A complete index can remain independently usable when later alignment fails; a failed workflow has no admitted final result. Saved verification checks complete captured FASTA/mapping/query populations, exact index handoff, both stage scientific audits and original declaration bindings. It repeats no original seed/alignment fitting and authenticates no producer.\n\n"+ACTION+"\n";}
    private static String help(){return "--runtime PINNED_LINUX_PROVIDER (--fasta FILE[.gz] | --fasta-list COMPLETE_TSV) --mapping FILE[.gz] --format two-column|ncbi-accession2taxid --namespace literal|accession|accession.version --query FASTQ[.gz] --method native-accession-alternative --profile CUSTOM052|STANDALONE --out NEW_DIRECTORY\nThe FASTA list has literal header fasta and one path per following row, relative to its directory; complete order is retained. Input paths must resolve canonically without symbolic-link aliases. Original build/alignment parameter and capacity options retain their names; shared --maximum-input-bytes is passed to both stages only when provided (defaults20GB build/4GB alignment). Shared threads/heap/gc/wall/file limits apply to both stages. The paper's130G/16-worker request is --heap-mib 133120 --threads 16; provision these resources explicitly. Root inventory limits --maximum-workflow-files 500000 --maximum-workflow-bytes 1000000000000 refuse rather than truncate. All individual stage limits still apply.\nPortable saved verification: --verify COMPLETE_WORKFLOW --reader-runtime EXACT_MALT052_READER_PROVIDER --out NEW_DIRECTORY\nSKIP3 skips dependent analysis when prerequisites are unavailable; FAIL2 admits no result after a scientific/integrity/input failure. All attempts and independent completed stages are retained.\n"+ACTION;}
}
