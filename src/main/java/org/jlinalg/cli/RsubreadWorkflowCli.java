/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.sequence.RsubreadCountMatrix;
import org.jlinalg.sequence.RsubreadCountMatrix.Name;
import org.jlinalg.sequence.RsubreadCountResult;
import org.jlinalg.sequence.RsubreadDataFrame;
import static org.jlinalg.cli.RsubreadSupport.*;

/** Typed original bulk-counting execution. Whole-batch admission follows final input/runtime/output checks. */
public final class RsubreadWorkflowCli {
    private RsubreadWorkflowCli() { }
    public static void main(String[] args) { int code=run(args,System.out,System.err);if(code!=0)System.exit(code); }
    public static int run(String[] args,PrintStream output,PrintStream error) {
        Path root=null;boolean ownsRoot=false;int code=2;Map<String,Object> state=new LinkedHashMap<>();
        try {
            if(args.length!=1)throw new IOException("Supply one typed ANALYZE or VERIFY JSON request");Path path=Path.of(args[0]).toAbsolutePath().normalize();var raw=read(path);
            if("VERIFY".equals(raw.get("mode"))) {
                keys(raw,Set.of("schema_version","mode","analysis"));integer(raw.get("schema_version"),1,1);verify(resolve(path.getParent(),text(raw.get("analysis"))));
                output.println("VERIFIED FROZEN STRUCTURAL CONSISTENCY ONLY; original R/counting was not rerun");return 0;
            }
            var request=RsubreadContracts.normalize(raw);root=resolve(path.getParent(),text(request.get("out")));
            if(Files.exists(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("Output already exists; use NEW paths");Files.createDirectories(root.getParent());Files.createDirectory(root);ownsRoot=true;
            state.putAll(Map.of("schema_version",1,"status","FAIL2_NO_RESULT_ADMITTED","analysis_admitted",false,"whole_package_implemented",false,"whole_paper_reproduced",false,"whole_goal_complete",false,"authentication",false,"required_action",ACTION));
            json(root.resolve("source-request.json"),raw);json(root.resolve("effective-request.json"),request);
            json(root.resolve("execution-location.json"),Map.of("execution_root",root.toString(),"separator",root.getFileSystem().getSeparator()));
            if(!Boolean.TRUE.equals(request.get("allow_execution")))throw new Unavailable("Execution forbidden; original counting and all dependent batch analysis skipped");
            if(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows"))throw new Unavailable("The declared original runtime profiles require Windows x86_64; dependent analysis skipped on this platform");
            Path bindingReceipt=resolve(path.getParent(),text(request.get("binding")));var binding=RsubreadRuntimeCli.loadBinding(bindingReceipt);String edition=text(binding.get("edition"));state.put("edition",edition);
            json(root.resolve("runtime-binding.json"),binding);json(root.resolve("runtime-binding-receipt.json"),read(bindingReceipt));RsubreadProfiles.capture(root,edition);
            for(String name:List.of("rsubread_count_worker.R","rsubread_result_wire.R"))Files.write(root.resolve("source/"+name),RsubreadProfiles.resource(name),StandardOpenOption.CREATE_NEW);
            var limits=object(request.get("limits"));long[] budget={((Number)limits.get("maximum_input_bytes")).longValue()};Map<String,Map<String,Object>> captured=new LinkedHashMap<>();
            List<Map<String,Object>> cases=new ArrayList<>();Files.createDirectory(root.resolve("inputs"));Files.createDirectory(root.resolve("cases"));
            for(Object value:list(request.get("cases"))) {
                cancel(root);var spec=object(value);String id=text(spec.get("id"));Path home=root.resolve("cases/"+id);Files.createDirectory(home);
                var info=new LinkedHashMap<String,Object>();info.put("id",id);info.put("spec",spec);var libraries=new ArrayList<Map<String,Object>>();
                for(Object item:list(spec.get("files"))) {var file=object(item);var mapped=new LinkedHashMap<>(file);mapped.put("input",capture(root,path.getParent(),text(file.get("path")),captured,budget));libraries.add(mapped);}
                info.put("libraries",libraries);var annotation=object(spec.get("annotation"));info.put("annotation_input",capture(root,path.getParent(),text(annotation.get("path")),captured,budget));
                info.put("genome_input",spec.get("genome")==null?null:capture(root,path.getParent(),text(spec.get("genome")),captured,budget));
                info.put("execution_root",root.toString());info.put("separator",root.getFileSystem().getSeparator());json(home.resolve("case-info.json"),info);cases.add(info);
            }
            json(root.resolve("captured-inputs.json"),captured);checkCaptured(root,captured,true);RsubreadProfiles.checkCapture(root,edition);
            Path rscript=Path.of(text(binding.get("rscript"))),rhome=Path.of(text(binding.get("r_home")));List<Map<String,Object>> summaries=new ArrayList<>();
            for(var info:cases) {
                cancel(root);String id=text(info.get("id"));Path home=root.resolve("cases/"+id),worker=home.resolve("worker");Files.createDirectory(worker);
                RsubreadProfiles.paths(worker.resolve("runtime-paths.hex.tsv"),binding);String wire=arguments(info);Files.writeString(worker.resolve("arguments.tsv"),wire,StandardCharsets.US_ASCII,StandardOpenOption.CREATE_NEW);
                long cap=((Number)limits.get("maximum_output_bytes")).longValue(),used=outputBytes(root),workerBytes=outputBytes(worker);
                if(used>cap)throw new IOException("Whole-batch maximum_output_bytes exceeded before original execution");
                RsubreadProcess.run(root,worker,rscript,rhome,List.of(root.resolve("source/rsubread_count_worker.R").toString(),root.resolve("source").toString(),worker.resolve("runtime-paths.hex.tsv").toString(),worker.resolve("arguments.tsv").toString()),
                    ((Number)limits.get("timeout_seconds")).longValue(),cap-used+workerBytes,new LinkedHashMap<>());
                RsubreadRuntimeCli.validateWorker(worker,RsubreadProfiles.profile(edition),binding);var result=checkCase(root,info,limits);var summary=summary(info,result);
                json(home.resolve("PROVISIONAL.json"),Map.of("status","provisional-not-batch-admitted","analysis_admitted",false,"summary",summary,"files",inventory(worker)));summaries.add(summary);
                if(outputBytes(root)>cap)throw new IOException("Whole-batch maximum_output_bytes exceeded; no case admitted");
            }
            checkCaptured(root,captured,true);cancel(root);RsubreadProfiles.checkCapture(root,edition);checkWorkerResources(root);
            if(!RsubreadRuntimeCli.runtimeFiles(binding).equals(object(binding.get("runtime_files"))))throw new IOException("Bound runtime changed during original counting; no batch result admitted");
            for(var info:cases) {
                cancel(root);Path home=root.resolve("cases/"+text(info.get("id")));var provisional=read(home.resolve("PROVISIONAL.json"));
                if(!info.equals(read(home.resolve("case-info.json")))||!inventory(home.resolve("worker")).equals(object(provisional.get("files")))||!summary(info,checkCase(root,info,limits)).equals(object(provisional.get("summary"))))throw new IOException("Earlier provisional outputs/populations changed; no case is admitted");
            }
            if(!raw.equals(read(root.resolve("source-request.json")))||!request.equals(read(root.resolve("effective-request.json")))||!captured.equals(read(root.resolve("captured-inputs.json")))||!binding.equals(read(root.resolve("runtime-binding.json"))))throw new IOException("Captured request/input/runtime declarations changed before admission");
            checkPin(root.resolve("runtime-binding.json"),read(root.resolve("runtime-binding-receipt.json")).get("binding"));
            state.put("status","completed-original-counting-with-warnings");state.put("analysis_admitted",true);state.put("summaries",summaries);state.put("scope","Original bulk short-read counting, complete literal R count lists and native value/relationship checks. No independent read assignment, junction discovery, original-GTF parsing parity, full R object serialization or paper-runtime identification.");code=0;
        }catch(Unavailable e){code=3;state.put("status","SKIP3_DEPENDENT_ANALYSIS_SKIPPED");state.put("error",e.toString());}
        catch(Exception e){code=2;state.put("status","FAIL2_NO_RESULT_ADMITTED");state.put("error",e.toString());}
        catch(OutOfMemoryError e){code=2;state.put("status","FAIL2_NO_RESULT_ADMITTED");state.put("error","Java heap exhausted; use adequate explicit capacities and NEW outputs");}
        if(ownsRoot) {
            boolean interrupted=Thread.interrupted();
            try {
                state.put("analysis_admitted",code==0);state.put("required_action",ACTION);
                warnings(root,"Rsubread warnings and required actions",state.getOrDefault("error","Require exit0 AND COMPLETED.json analysis_admitted=true for the entire batch. PROVISIONAL files, upstream exit0 and existing count tables alone do not admit a result. Defaults are explicit new-analysis choices, not recovered paper settings. checkFragLength=FALSE leaves fragment thresholds inactive; countReadPairs is ignored upstream for single-end libraries. Read each original log, effective request, input mapping and runtime scope.").toString());
                if(code==0) {
                    if(interrupted)throw new IOException("Interrupted before final batch admission");cancel(root);json(root.resolve("capture-files.json"),Map.of("schema_version",1,"authentication",false,"files",inventory(root)));cancel(root);
                }
                Path pending=root.resolve("COMPLETED.pending.json");json(pending,state);
                if(code==0&&outputBytes(root)>((Number)object(read(root.resolve("effective-request.json")).get("limits")).get("maximum_output_bytes")).longValue())throw new IOException("Whole-batch maximum_output_bytes exceeded during final admission");
                cancel(root);Files.move(pending,root.resolve("COMPLETED.json"),StandardCopyOption.ATOMIC_MOVE);
            }catch(Exception e) {
                code=2;state.put("status","FAIL2_FINAL_ADMISSION_FAILED");state.put("analysis_admitted",false);state.put("error",e.toString());
                try{Files.writeString(root.resolve("FINAL-ERROR.txt"),"FAIL2 — NO RESULT ADMITTED\n"+e+"\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);if(!Files.exists(root.resolve("COMPLETED.json")))json(root.resolve("COMPLETED.json"),state);}catch(Exception report){emit(error,"FAIL2: Could not write final failure receipt: "+report);}
            }finally{if(interrupted)Thread.currentThread().interrupt();}
        }
        emit(code==0?output:error,(code==0?"COMPLETED ORIGINAL RSUBREAD BATCH: ":code==3?"SKIP3 — DEPENDENT ANALYSIS SKIPPED: ":"FAIL2 — NO RESULT ADMITTED: ")+root+"\n"+state.getOrDefault("error","")+"\nWARNINGS / REQUIRED ACTIONS: "+ACTION);return code;
    }
    private static void emit(PrintStream stream,String value) {stream.writeBytes((value+"\n").getBytes(StandardCharsets.UTF_8));}

    private static Map<String,Object> capture(Path root,Path requestDirectory,String spelling,Map<String,Map<String,Object>> cache,long[] remaining) throws IOException {
        if(cache.containsKey(spelling))return cache.get(spelling);cancel(root);Path original=resolve(requestDirectory,spelling);
        if(!Files.isRegularFile(original,LinkOption.NOFOLLOW_LINKS)||Files.size(original)>remaining[0])throw new IOException("Input unavailable, nonregular or exceeds maximum_input_bytes: "+original);
        String relative=String.format(Locale.ROOT,"inputs/%06d/",cache.size())+original.getFileName();Path target=root.resolve(relative);Files.createDirectories(target.getParent());var before=pin(original);long size=0;
        try(var input=Files.newInputStream(original);var output=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)) {
            byte[] bytes=new byte[65536];for(int n;(n=input.read(bytes))!=-1;) {cancel(root);if(n>remaining[0]-size)throw new IOException("Input grew beyond maximum_input_bytes");output.write(bytes,0,n);size+=n;}
        }
        if(!before.equals(pin(original))||!before.equals(pin(target)))throw new IOException("Input changed during complete capture: "+original);remaining[0]-=size;
        Map<String,Object> result=Map.of("source_spelling",spelling,"source_original",original.toString(),"captured_path",relative,"counting_argument",target.toString(),"pin",before);cache.put(spelling,result);return result;
    }
    private static void checkCaptured(Path root,Map<String,? extends Object> cache,boolean originals) throws IOException {
        for(var entry:cache.entrySet()) {
            cancel(root);var row=object(entry.getValue());if(!entry.getKey().equals(row.get("source_spelling")))throw new IOException("Captured source spelling/key differs");
            checkPin(contained(root,text(row.get("captured_path"))),row.get("pin"));if(originals)checkPin(Path.of(text(row.get("source_original"))),row.get("pin"));
        }
    }
    private static long outputBytes(Path root) throws IOException {
        long bytes=0;try(var paths=Files.walk(root)) {
            var iterator=paths.iterator();while(iterator.hasNext()) {
                checkInterrupt();Path path=iterator.next();if(path.startsWith(root.resolve("inputs")))continue;
                if(Files.isSymbolicLink(path))throw new IOException("Unsupported output link");if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))continue;
                if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Unsupported nonregular output node");bytes=Math.addExact(bytes,Files.size(path));
            }
        }return bytes;
    }
    private static String encoded(Object value) throws IOException { return "S"+HexFormat.of().formatHex(text(value).getBytes(StandardCharsets.UTF_8)); }
    private static void field(StringBuilder wire,String name,String type,List<?> values) {wire.append(name).append('\t').append(type);for(Object value:values)wire.append('\t').append(value);wire.append('\n');}
    private static String historicPath(Map<String,Object> info,String relative) throws IOException {return text(info.get("execution_root"))+text(info.get("separator"))+relative.replace("/",text(info.get("separator")));}
    private static String arguments(Map<String,Object> info) throws IOException {
        var spec=object(info.get("spec"));var annotation=object(spec.get("annotation"));var options=object(spec.get("options"));var wire=new StringBuilder();
        var paths=new ArrayList<String>();var paired=new ArrayList<Integer>();var pairs=new ArrayList<Integer>();var strand=new ArrayList<Long>();
        for(Object value:list(info.get("libraries"))) {var file=object(value);paths.add(encoded(object(file.get("input")).get("counting_argument")));paired.add(bool(file.get("paired_end"))?1:0);pairs.add(bool(file.get("count_read_pairs"))?1:0);strand.add(integer(file.get("strand"),0,2));}
        field(wire,"files","STRING",paths);field(wire,"isPairedEnd","BOOL",paired);field(wire,"countReadPairs","BOOL",pairs);field(wire,"strandSpecific","INT",strand);
        field(wire,"annot.ext","STRING",List.of(encoded(object(info.get("annotation_input")).get("counting_argument"))));field(wire,"isGTFAnnotationFile","BOOL",List.of(annotation.get("format").equals("GTF")?1:0));
        var types=new ArrayList<String>();for(Object type:list(annotation.get("feature_types")))types.add(encoded(type));field(wire,"GTF.featureType","STRING",types);field(wire,"GTF.attrType","STRING",List.of(encoded(annotation.get("attribute"))));
        var extras=new ArrayList<String>();for(Object extra:list(annotation.get("extra_attributes")))extras.add(encoded(extra));field(wire,"GTF.attrType.extra",extras.isEmpty()?"NULL":"STRING",extras.isEmpty()?List.of("NULL"):extras);
        field(wire,"genome",info.get("genome_input")==null?"NULL":"STRING",List.of(info.get("genome_input")==null?"NULL":encoded(object(info.get("genome_input")).get("counting_argument"))));
        field(wire,"tmpDir","STRING",List.of(encoded(historicPath(info,"cases/"+text(info.get("id"))+"/worker"))));
        for(var e:options.entrySet()) {
            String type=RsubreadContracts.BOOLEAN_DEFAULTS.containsKey(e.getKey())?"BOOL":RsubreadContracts.INTEGER_DEFAULTS.containsKey(e.getKey())?"INT":"DOUBLE";
            field(wire,e.getKey(),type,List.of(type.equals("BOOL")?(bool(e.getValue())?1:0):e.getValue()));
        }return wire.toString();
    }
    private static RsubreadDataFrame frame(Path source,Map<String,Object> limits) throws IOException {
        var cap=new RsubreadDataFrame.Limits(((Number)limits.get("maximum_rows")).intValue(),((Number)limits.get("maximum_columns")).intValue(),((Number)limits.get("maximum_cells")).intValue(),((Number)limits.get("maximum_text_bytes")).intValue(),((Number)limits.get("maximum_total_text_bytes")).longValue());
        try(var in=Files.newBufferedReader(source,StandardCharsets.US_ASCII)){return RsubreadDataFrame.readWire(in,cap);}
    }
    private static RsubreadCountResult checkCase(Path root,Map<String,Object> info,Map<String,Object> limits) throws IOException {
        cancel(root);Path worker=contained(root,"cases/"+text(info.get("id"))+"/worker"),home=worker.resolve("values");
        if(!Files.readString(worker.resolve("analysis-status.txt")).strip().equals("COMPLETE_ORIGINAL_RSUBREAD_COUNT_LIST")||!Files.readString(worker.resolve("worker-status.txt")).strip().equals("PASS_ORIGINAL_RSUBREAD_RUNTIME_GUARDS"))throw new IOException("Original complete count-list/runtime status missing");
        if(!arguments(info).equals(Files.readString(worker.resolve("arguments.tsv"),StandardCharsets.US_ASCII)))throw new IOException("Typed original argument wire changed");
        RsubreadCountMatrix counts;try(var input=Files.newBufferedReader(home.resolve("counts.matrix"),StandardCharsets.US_ASCII)) {
            counts=RsubreadCountMatrix.readWire(input,new RsubreadCountMatrix.Limits(((Number)limits.get("maximum_rows")).intValue(),((Number)limits.get("maximum_columns")).intValue(),((Number)limits.get("maximum_cells")).intValue(),((Number)limits.get("maximum_text_bytes")).intValue(),((Number)limits.get("maximum_total_text_bytes")).longValue()));
        }
        var spec=object(info.get("spec"));boolean junction=bool(object(spec.get("options")).get("juncCounts"));
        Set<String> expected=new HashSet<>(Set.of("counts.matrix","annotation.frame","stat.frame","targets.frame","members.txt"));if(junction)expected.add("counts_junction.frame");
        try(var paths=Files.list(home)){if(!paths.map(p->p.getFileName().toString()).collect(java.util.stream.Collectors.toSet()).equals(expected))throw new IOException("Original result member population differs");}
        var targets=frame(home.resolve("targets.frame"),limits);if(targets.columns()!=1||targets.column(0).type()!=RsubreadDataFrame.Type.CHARACTER)throw new IOException("Original targets vector shape/storage differs");
        var result=new RsubreadCountResult(counts,frame(home.resolve("annotation.frame"),limits),targets.column(0).characters(),frame(home.resolve("stat.frame"),limits),junction?frame(home.resolve("counts_junction.frame"),limits):null);
        if(result.annotation().columns()!=6+list(object(spec.get("annotation")).get("extra_attributes")).size())throw new IOException("Original extra annotation population differs");
        var members=Files.readAllLines(home.resolve("members.txt"));if(!members.equals(junction?List.of("counts","counts_junction","annotation","targets","stat"):List.of("counts","annotation","targets","stat")))throw new IOException("Original list order differs");
        var baseNames=new ArrayList<String>();var full=new ArrayList<String>();for(Object value:list(info.get("libraries"))) {
            var mapping=object(object(value).get("input"));String path=text(mapping.get("counting_argument"));full.add(path);String relative=text(mapping.get("captured_path"));baseNames.add(relative.substring(relative.lastIndexOf('/')+1));
        }
        List<Name> targetNames=(new HashSet<>(baseNames).size()==baseNames.size()?baseNames:full).stream().map(Name::literal).toList();
        if(!result.targets().equals(targetNames))throw new IOException("Original sample names/positions differ from explicit captured-input mapping");
        if(Files.size(worker.resolve("complete-original-result.rds"))==0||Files.size(worker.resolve("arguments.rds"))==0)throw new IOException("Complete original RDS/argument object unavailable");return result;
    }
    private static Map<String,Object> summary(Map<String,Object> info,RsubreadCountResult result) {
        return Map.of("id",info.get("id"),"rows",(long)result.counts().rows(),"libraries",(long)result.counts().columns(),"storage",result.counts().storage().name(),"junction_rows",result.junction().map(j->(long)j.rows()).orElse(-1L),"annotation_columns",(long)result.annotation().columns());
    }
    private static void checkWorkerResources(Path root) throws IOException {
        for(String name:List.of("rsubread_count_worker.R","rsubread_result_wire.R"))if(!Arrays.equals(Files.readAllBytes(root.resolve("source/"+name)),RsubreadProfiles.resource(name)))throw new IOException("Captured original worker/value bridge changed");
    }
    /** Historical structural replay only: neither the original runtime nor source files outside the capture are needed. */
    public static void verify(Path directory) throws IOException {
        Path root=directory.toAbsolutePath().normalize();var completed=read(root.resolve("COMPLETED.json"));if(!"completed-original-counting-with-warnings".equals(completed.get("status"))||!Boolean.TRUE.equals(completed.get("analysis_admitted")))throw new IOException("Skipped/failed/incomplete batches are not admitted results");
        var seal=read(root.resolve("capture-files.json"));if(!Boolean.FALSE.equals(seal.get("authentication")))throw new IOException("Invalid authentication claim");var actual=inventory(root);actual.remove("capture-files.json");actual.remove("COMPLETED.json");if(!actual.equals(object(seal.get("files"))))throw new IOException("Capture file population/bytes changed");
        var effective=RsubreadContracts.normalize(read(root.resolve("source-request.json")));if(!effective.equals(read(root.resolve("effective-request.json"))))throw new IOException("Effective typed request changed");
        String edition=text(completed.get("edition"));RsubreadProfiles.checkCapture(root,edition);checkWorkerResources(root);var captured=read(root.resolve("captured-inputs.json"));checkCaptured(root,captured,false);
        var location=read(root.resolve("execution-location.json"));checkPin(root.resolve("runtime-binding.json"),read(root.resolve("runtime-binding-receipt.json")).get("binding"));
        var summaries=new ArrayList<Map<String,Object>>();for(Object value:list(effective.get("cases"))) {
            var spec=object(value);var info=read(contained(root,"cases/"+text(spec.get("id"))+"/case-info.json"));if(!spec.equals(object(info.get("spec"))))throw new IOException("Captured case declarations changed");
            if(!spec.get("id").equals(info.get("id"))||!location.get("execution_root").equals(info.get("execution_root"))||!location.get("separator").equals(info.get("separator")))throw new IOException("Case identity/execution location differs");
            var expectedLibraries=new ArrayList<Map<String,Object>>();for(Object file:list(spec.get("files"))) {var library=new LinkedHashMap<>(object(file));library.put("input",captured.get(text(library.get("path"))));expectedLibraries.add(library);}
            if(!expectedLibraries.equals(list(info.get("libraries")))||!Objects.equals(captured.get(text(object(spec.get("annotation")).get("path"))),info.get("annotation_input"))||!Objects.equals(spec.get("genome")==null?null:captured.get(text(spec.get("genome"))),info.get("genome_input")))throw new IOException("Captured source-to-library/annotation/reference mappings differ");
            summaries.add(summary(info,checkCase(root,info,object(effective.get("limits")))));
        }
        if(!summaries.equals(list(completed.get("summaries"))))throw new IOException("Completed batch populations differ");
    }
}
