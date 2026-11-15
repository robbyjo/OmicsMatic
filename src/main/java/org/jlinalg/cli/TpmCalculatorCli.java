/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Native source-defined TPMCalculator file workflow and complete saved-input replay. */
public final class TpmCalculatorCli {
    private TpmCalculatorCli() { }
    static final String ACTION="SKIP3 skips analysis/verification when a named permitted input, Java/dependency runtime or output prerequisite is unavailable. "
        +"Supply that permitted prerequisite and retry a NEW output directory. No software is downloaded or installed by this analysis. "
        +"FAIL2 admits no complete result, including parser errors, capacity/time/heap failures, input changes, failed replay or recording failure after computation returned. "
        +"Read FAILURE.json, failure-trace.txt, WARNINGS.md, WARNINGS.json and complete worker/stdout.bin and worker/stderr.bin. Preserve originals and partials. "
        +"Only a verified final COMPLETED.json with operation run and result_admitted true admits an analysis; a worker RETURN.json or a partial product never does. "
        +"Verification recomputes from complete captured GTF/BAM files with the recorded native implementation. It does not require the original external file locations. "
        +"SHA256 establishes byte integrity, not source authorship or biological accuracy. The paper's TPMCalculator version/options remain unknown; this explicit project profile is not a paper reproduction. "
        +"Original read counting, gene modelling, antisense exclusions, rounding, unsigned-coordinate effects and nonfinite values are retained; inspect the complete state and conditions before interpretation.";
    @FunctionalInterface interface Hook {void at(String phase,Path out)throws IOException;}
    public static void main(String[] args){System.exit(run(args));}
    public static int run(String[] args){return run(args,(phase,out)->{});}
    static int run(String[] args,Hook hook) {
        Path out=null;boolean owned=false,started=false;String phase="ARGUMENTS";
        try {
            if(args.length==0||args[0].equals("--help")) {
                System.out.println("run --request JSON --out NEW\nverify --bundle SAVED --out NEW\nProfile: "+TpmCalculatorRequest.PROFILE+"\n"+ACTION);return 0;
            }
            String operation=args[0];Map<String,String> options=arguments(args);
            if(!Set.of("run","verify").contains(operation)||!options.keySet().equals(Set.of(operation.equals("run")?"request":"bundle","out")))throw new IOException("Use run --request JSON --out NEW or verify --bundle SAVED --out NEW");
            Path input=Path.of(options.get(operation.equals("run")?"request":"bundle")).toAbsolutePath().normalize();out=Path.of(options.get("out")).toAbsolutePath().normalize();
            TpmCalculatorRecords.disjoint(out,input);TpmCalculatorRecords.newDirectory(out);owned=true;
            TpmCalculatorRecords.write(out.resolve("WARNINGS.md"),("# Native TPMCalculator: warnings and required actions\n\n"+ACTION+"\n").getBytes(StandardCharsets.UTF_8));
            TpmCalculatorRecords.json(out.resolve("COMMAND.json"),Map.of("operation",operation,"arguments",List.of(args)));
            phase="IMPLEMENTATION";var implementation=TpmCalculatorRecords.implementation();
            TpmCalculatorRecords.json(out.resolve("ENGINE.json"),Map.of("schema",1,"implementation",implementation.identity(),"controller_runtime",runtime()));
            TpmCalculatorRequest.Request request;Map<String,Object> originals=null;String priorManifest=null;
            if(operation.equals("run")) {
                phase="REQUEST";byte[] raw;
                try{raw=NextflowFiles.bytes(input,TpmCalculatorRecords.METADATA);}catch(IOException e){throw new TpmCalculatorRecords.Unavailable("Supply a readable canonical UTF8 request within the documented16MiB metadata capacity: "+input,e);}
                TpmCalculatorRecords.write(out.resolve("source-request.json"),raw);request=TpmCalculatorRequest.read(TpmCalculatorRequest.parse(raw,TpmCalculatorRecords.METADATA),false);
                phase="CAPTURE";originals=capture(input,out,request);TpmCalculatorRecords.json(out.resolve("INPUTS.json"),originals);
                TpmCalculatorRecords.json(out.resolve("REQUEST.json"),request.effective());
                if(!java.util.Arrays.equals(NextflowFiles.bytes(input,TpmCalculatorRecords.METADATA),raw))throw new IOException("Original request changed during capture");
            }else {
                phase="SAVED_BUNDLE_VALIDATION";
                if(!Files.isDirectory(input,LinkOption.NOFOLLOW_LINKS)||!input.toRealPath().equals(input))throw new TpmCalculatorRecords.Unavailable("Supply the canonical unlinked saved native analysis directory: "+input);
                request=validate(input,implementation);priorManifest=TpmCalculatorRecords.pin(input.resolve("MANIFEST.json"),request.limits().bundleBytes()).sha256();
                TpmCalculatorRecords.json(out.resolve("REQUEST.json"),request.effective());
                TpmCalculatorRecords.json(out.resolve("SOURCE-BUNDLE.json"),Map.of("bundle",input.toString(),"manifest_sha256",priorManifest,"original_paths_accessed",false));
            }
            hook.at("BEFORE_WORKER",out);phase="NATIVE_COMPUTATION_AND_RECORDING";started=true;
            TpmCalculatorProcess.run(out,operation.equals("run")?out:input,TpmCalculatorRequest.read(request.effective(),true),implementation);
            hook.at("AFTER_WORKER",out);phase="RESULT_VALIDATION";
            Map<?,?> returned=NextflowFiles.object(TpmCalculatorRecords.read(out.resolve("worker/RETURN.json")));
            NextflowFiles.keys(returned,"status","computed","products_recorded","conditions","libdeflate_enabled","java_runtime","java_vm","os","architecture");
            if(!"RETURNED_AND_RECORDED_NATIVE_PRODUCTS".equals(returned.get("status"))||!Boolean.TRUE.equals(returned.get("computed"))||!Boolean.TRUE.equals(returned.get("products_recorded"))||!Boolean.FALSE.equals(returned.get("libdeflate_enabled")))throw new IOException("No complete native worker return is admitted");
            Map<?,?> conditions=NextflowFiles.object(TpmCalculatorRecords.read(out.resolve("products/CONDITIONS.json")));
            if(!NextflowJson.json(conditions).equals(NextflowJson.json(returned.get("conditions"))))throw new IOException("Native worker return/recorded conditions differ");
            validateState(out.resolve("products"),conditions);warnings(out,conditions);
            phase="INPUT_AND_IMPLEMENTATION_RECHECK";
            if(!NextflowJson.json(implementation.identity()).equals(NextflowJson.json(TpmCalculatorRecords.implementation().identity())))throw new IOException("Native implementation changed during computation");
            if(operation.equals("run")) {
                originalInputs(out,request,NextflowFiles.object(TpmCalculatorRecords.read(out.resolve("INPUTS.json"))),true);
                if(!java.util.Arrays.equals(Files.readAllBytes(out.resolve("source-request.json")),NextflowFiles.bytes(input,TpmCalculatorRecords.METADATA)))throw new IOException("Original request changed during computation");
                requireLayout(out,request,true);
            }else {
                phase="SEMANTIC_REPLAY_COMPARISON";compareProducts(input.resolve("products"),out.resolve("products"),request);
                validate(input,implementation);
                if(!TpmCalculatorRecords.pin(input.resolve("MANIFEST.json"),request.limits().bundleBytes()).sha256().equals(priorManifest))throw new IOException("Saved native bundle changed during replay");
                TpmCalculatorRecords.json(out.resolve("VERIFICATION.json"),Map.of("status","PASS_COMPLETE_CAPTURED_INPUT_RECOMPUTATION","source_manifest_sha256",priorManifest,
                    "all_semantic_state_and_source_table_values_replayed",true,"raw_NaN_payload_sign_replay_required",false,"original_inputs_reopened",false,"biological_accuracy_established",false));
            }
            hook.at("BEFORE_COMPLETE",out);phase="COMPLETE_RECORDING";
            TpmCalculatorRecords.manifest(out,request.limits());TpmCalculatorRecords.checkManifest(out,request.limits());
            TpmCalculatorRecords.complete(out,Map.of("schema",1,"profile",TpmCalculatorRequest.PROFILE,"operation",operation,"status",operation.equals("run")?"PASS_NATIVE_TPMCALCULATOR_RECORDED_ANALYSIS":"PASS_NATIVE_TPMCALCULATOR_SAVED_REPLAY",
                "result_admitted",operation.equals("run"),"saved_replay_verified",operation.equals("verify"),"manifest_sha256",TpmCalculatorRecords.pin(out.resolve("MANIFEST.json"),request.limits().bundleBytes()).sha256(),
                "paper_reproduced",false,"whole_goal_complete",false));
            TpmCalculatorRecords.inventory(out,request.limits().bundleBytes(),request.limits().files(),Set.of(),false);
            hook.at("AFTER_COMPLETE",out);
            System.out.println("PASS_NATIVE_TPMCALCULATOR_"+(operation.equals("run")?"RECORDED_ANALYSIS":"SAVED_INPUT_REPLAY")+"\nWARNING: "+ACTION);return 0;
        }catch(Throwable failure) {
            if(failure instanceof InterruptedException)Thread.currentThread().interrupt();
            int code=failure instanceof TpmCalculatorRecords.Unavailable||!started&&failure instanceof LinkageError?3:2;
            String label=code==3?"SKIP3_NATIVE_TPMCALCULATOR_PREREQUISITE_UNAVAILABLE":"FAIL2_NATIVE_TPMCALCULATOR_RESULT_UNADMITTED";
            System.err.println(label+": "+failure+"\nRequired action: "+ACTION);
            if(owned) {
                try{TpmCalculatorRecords.revoke(out);}catch(IOException e){System.err.println("FAIL2: Could not quarantine an unadmitted completion; preserve the directory and do not consume it: "+e);}
                try {
                    var trace=new StringWriter();failure.printStackTrace(new PrintWriter(trace));TpmCalculatorRecords.write(out.resolve("failure-trace.txt"),trace.toString().getBytes(StandardCharsets.UTF_8));
                    TpmCalculatorRecords.json(out.resolve("FAILURE.json"),Map.of("status",label,"phase",phase,"worker_attempted",started,"result_admitted",false,"cause",failure.toString(),"required_action",ACTION));
                    Files.writeString(out.resolve("WARNINGS.md"),"\n\n**"+label+"**\n\n"+failure+"\n\n"+ACTION+"\n",StandardCharsets.UTF_8,StandardOpenOption.APPEND);
                }catch(Exception recording){System.err.println("FAIL2_FAILURE_RECORDING: "+recording+"; no analysis or replay completion is admitted. Preserve all partials.");}
            }
            return code;
        }
    }
    private static Map<String,String> arguments(String[] args)throws IOException {
        var out=new LinkedHashMap<String,String>();for(int k=1;k<args.length;k+=2) {
            if(k+1==args.length||!args[k].startsWith("--")||out.putIfAbsent(args[k].substring(2),args[k+1])!=null)throw new IOException("Require unique named arguments with complete values");
        }return out;
    }
    private static Map<String,Object> runtime(){return Map.of("java",System.getProperty("java.runtime.version"),"vm",System.getProperty("java.vm.name"),"os",System.getProperty("os.name"),"arch",System.getProperty("os.arch"));}
    private static Map<String,Object> capture(Path source,Path out,TpmCalculatorRequest.Request request)throws IOException {
        Path folder=Files.createDirectory(out.resolve("inputs"));long remaining=request.limits().inputBytes();var samples=new ArrayList<Object>();
        Path annotation=TpmCalculatorRequest.original(source,request.annotation());TpmCalculatorRecords.disjoint(out,annotation);
        var a=new LinkedHashMap<>(TpmCalculatorRecords.capture(annotation,folder.resolve("annotation.gtf"),remaining));
        a.put("captured","inputs/annotation.gtf");a.put("original_argument",request.annotation());remaining-=((NextflowFiles.Pin)a.get("pin")).bytes();
        for(var sample:request.samples()) {
            Path original=TpmCalculatorRequest.original(source,sample.original());TpmCalculatorRecords.disjoint(out,original);
            var row=new LinkedHashMap<>(TpmCalculatorRecords.capture(original,TpmCalculatorRecords.child(out,sample.member()),remaining));
            row.put("captured",sample.member());row.put("original_argument",sample.original());row.put("sample_name",sample.name());samples.add(row);remaining-=((NextflowFiles.Pin)row.get("pin")).bytes();
        }
        return Map.of("schema",1,"annotation",a,"samples",samples);
    }
    private static void originalInputs(Path bundle,TpmCalculatorRequest.Request request,Map<?,?> originals,boolean recheckOriginal)throws IOException {
        NextflowFiles.keys(originals,"schema","annotation","samples");NextflowFiles.integer(originals.get("schema"),1,1);
        var rows=new ArrayList<Map<?,?>>(List.of(NextflowFiles.object(originals.get("annotation"))));
        for(Object value:NextflowFiles.list(originals.get("samples")))rows.add(NextflowFiles.object(value));
        if(rows.size()!=request.samples().size()+1)throw new IOException("Captured original sample inventory differs");
        long total=0;
        for(int k=0;k<rows.size();k++) {
            Map<?,?> row=rows.get(k);NextflowFiles.keys(row,k==0?new String[]{"original","captured","source_identity","pin","original_argument"}:new String[]{"original","captured","source_identity","pin","original_argument","sample_name"});
            String member=k==0?"inputs/annotation.gtf":request.samples().get(k-1).member();String arg=k==0?request.annotation():request.samples().get(k-1).original();
            if(!member.equals(row.get("captured"))||!arg.equals(row.get("original_argument"))||k>0&&!request.samples().get(k-1).name().equals(row.get("sample_name")))throw new IOException("Captured paths, argument bytes, sample names or order differ from the original request");
            var pin=NextflowFiles.fingerprint(row.get("pin"),request.limits().inputBytes());total=Math.addExact(total,pin.bytes());
            if(total>request.limits().inputBytes()||!TpmCalculatorRecords.pin(TpmCalculatorRecords.child(bundle,member),request.limits().inputBytes()).equals(pin))throw new IOException("Captured native input inventory/bytes differ");
            Map<?,?> identity=NextflowFiles.object(row.get("source_identity"));if(!NextflowJson.json(identity.get("pin")).equals(NextflowJson.json(row.get("pin"))))throw new IOException("Original input identity/captured pin differs");
            if(recheckOriginal) {
                Path p=Path.of(NextflowFiles.text(row.get("original")));
                if(!NextflowJson.json(TpmCalculatorRecords.identity(p,request.limits().inputBytes())).equals(NextflowJson.json(row.get("source_identity"))))throw new IOException("Original native input changed during analysis: "+p);
            }
        }
    }
    private static TpmCalculatorRequest.Request validate(Path bundle,TpmCalculatorRecords.Implementation implementation)throws IOException {
        Map<?,?> completed=NextflowFiles.object(TpmCalculatorRecords.read(bundle.resolve("COMPLETED.json")));
        NextflowFiles.keys(completed,"schema","profile","operation","status","result_admitted","saved_replay_verified","manifest_sha256","paper_reproduced","whole_goal_complete");
        NextflowFiles.integer(completed.get("schema"),1,1);
        if(!Boolean.TRUE.equals(completed.get("result_admitted"))||!"run".equals(completed.get("operation"))||!"PASS_NATIVE_TPMCALCULATOR_RECORDED_ANALYSIS".equals(completed.get("status"))||!TpmCalculatorRequest.PROFILE.equals(completed.get("profile"))
            ||!Boolean.FALSE.equals(completed.get("saved_replay_verified"))||!Boolean.FALSE.equals(completed.get("paper_reproduced"))||!Boolean.FALSE.equals(completed.get("whole_goal_complete")))throw new IOException("No admitted source-scoped native run completion is present");
        var original=TpmCalculatorRequest.read(TpmCalculatorRecords.read(bundle.resolve("source-request.json")),false);
        var effective=TpmCalculatorRequest.read(TpmCalculatorRecords.read(bundle.resolve("REQUEST.json")),true);
        if(!NextflowJson.json(original.effective()).equals(NextflowJson.json(effective.effective())))throw new IOException("Recorded computational request differs from original request choices");
        if(!NextflowFiles.text(completed.get("manifest_sha256")).equals(TpmCalculatorRecords.pin(bundle.resolve("MANIFEST.json"),original.limits().bundleBytes()).sha256()))throw new IOException("Native completion/manifest identity differs");
        TpmCalculatorRecords.checkManifest(bundle,original.limits());requireLayout(bundle,original,true);
        Map<?,?> engine=NextflowFiles.object(TpmCalculatorRecords.read(bundle.resolve("ENGINE.json")));
        if(!NextflowJson.json(engine.get("implementation")).equals(NextflowJson.json(implementation.identity())))throw new TpmCalculatorRecords.Unavailable("Saved native implementation differs; supply the recorded permitted application artifact to replay it. No historical result is invalidated by this prerequisite skip");
        originalInputs(bundle,original,NextflowFiles.object(TpmCalculatorRecords.read(bundle.resolve("INPUTS.json"))),false);
        Map<?,?> conditions=NextflowFiles.object(TpmCalculatorRecords.read(bundle.resolve("products/CONDITIONS.json")));
        validateState(bundle.resolve("products"),conditions);validateWorker(bundle,effective,engine);
        if(!NextflowJson.json(TpmCalculatorRecords.read(bundle.resolve("WARNINGS.json"))).equals(NextflowJson.json(Map.of("warnings",warningRows(conditions),"required_action",ACTION))))throw new IOException("Saved native warnings no longer describe the complete original conditions");
        if(!Files.readString(bundle.resolve("WARNINGS.md"),StandardCharsets.UTF_8).equals(warningText(conditions)))throw new IOException("Saved prominent warnings/required actions differ");return original;
    }
    private static void validateState(Path products,Map<?,?> conditions)throws IOException {
        if(!NextflowJson.json(TpmCalculatorRecords.read(products.resolve("STATE-SCHEMA.json"))).equals(NextflowJson.json(TpmCalculatorProducts.schema())))throw new IOException("Native complete state schema differs");
        long rows=0,nans=0,infinities=0;try(var raw=Files.newBufferedReader(products.resolve("state-raw.jsonl"),StandardCharsets.UTF_8);var semantic=Files.newBufferedReader(products.resolve("state.jsonl"),StandardCharsets.UTF_8)) {
            for(;;) {
                String a=line(raw),b=line(semantic);if(a==null||b==null){if(a!=null||b!=null)throw new IOException("Truncated native raw/semantic state");break;}
                List<?> fields=NextflowFiles.list(NextflowParameters.parse(a,TpmCalculatorRecords.METADATA,TpmCalculatorRecords.METADATA));if(fields.size()!=32)throw new IOException("Native state requires all32 fields");
                var expected=new ArrayList<Object>();for(int k=0;k<32;k++) {
                    Object value=fields.get(k);
                    if(k>=19&&k%2==1) {
                        String hex=NextflowFiles.text(value);if(!hex.matches("[0-9a-f]{16}"))throw new IOException("Invalid complete raw FP64 state");
                        double number=Double.longBitsToDouble(Long.parseUnsignedLong(hex,16));expected.add(Double.isNaN(number)?"NaN":Double.toHexString(number));
                        if(Double.isNaN(number))nans++;else if(Double.isInfinite(number))infinities++;
                    }else expected.add(value);
                }
                if(!NextflowJson.json(expected).equals(b))throw new IOException("Raw native returns and semantic state differ");rows++;
            }
        }
        if(rows!=NextflowFiles.integer(conditions.get("state_rows"),0,Long.MAX_VALUE)||nans!=NextflowFiles.integer(conditions.get("NaN_fields"),0,Long.MAX_VALUE)
            ||infinities!=NextflowFiles.integer(conditions.get("infinite_fields"),0,Long.MAX_VALUE)||nans+infinities!=NextflowFiles.integer(conditions.get("nonfinite_fields"),0,Long.MAX_VALUE))throw new IOException("Incomplete native state row/nonfinite condition inventory");
    }
    private static String line(BufferedReader reader)throws IOException {
        var s=new StringBuilder();int ch;while((ch=reader.read())!=-1&&ch!='\n'){if(s.length()>=TpmCalculatorRecords.METADATA)throw new IOException("Native state row exceeds declared metadata recording capacity");s.append((char)ch);}return ch==-1&&s.isEmpty()?null:s.toString();
    }
    private static void compareProducts(Path before,Path replay,TpmCalculatorRequest.Request request)throws IOException {
        Map<String,Object> old=TpmCalculatorRecords.inventory(before,request.limits().bundleBytes(),request.limits().files(),Set.of("state-raw.jsonl"),true);
        Map<String,Object> fresh=TpmCalculatorRecords.inventory(replay,request.limits().bundleBytes(),request.limits().files(),Set.of("state-raw.jsonl"),true);
        if(!NextflowJson.json(old).equals(NextflowJson.json(fresh)))throw new IOException("Saved native state/source tables differ from fresh captured-input computation");
    }
    private static void requireLayout(Path root,TpmCalculatorRequest.Request request,boolean run)throws IOException {
        var expected=new TreeSet<String>(List.of("WARNINGS.md","COMMAND.json","ENGINE.json","source-request.json","REQUEST.json","INPUTS.json","WARNINGS.json",
            "inputs","inputs/annotation.gtf","worker","worker/tmp","worker/REQUEST.json","worker/LAUNCH.json","worker/EXIT.json","worker/RETURN.json","worker/stdout.bin","worker/stderr.bin",
            "products","products/state-raw.jsonl","products/state.jsonl","products/STATE-SCHEMA.json","products/CONDITIONS.json"));
        int n=0;for(var sample:request.samples()) {
            expected.add(sample.member());String dir=String.format(java.util.Locale.ROOT,"products/sample-%06d",++n);expected.add(dir);
            for(String file:request.extended()?List.of("genes.out","genes.ent","genes.uni","transcripts.out","transcripts.ent"):List.of("genes.out","genes.ent","genes.uni"))expected.add(dir+"/"+file);
        }
        if(!TpmCalculatorRecords.inventory(root,request.limits().bundleBytes(),request.limits().files(),TpmCalculatorRecords.TERMINAL,false).keySet().equals(expected))throw new IOException("Native complete bundle layout contains missing or unexpected records/products/directories");
    }
    private static List<Object> warningRows(Map<?,?> conditions)throws IOException {
        var rows=new ArrayList<Object>();rows.add(Map.of("code","SOURCE_PROFILE_NOT_AUTHOR_EDITION","required_action","Record the explicit project profile and inspect original source quirks. Do not infer P050's unreported edition, options, or a paper reproduction."));
        rows.add(Map.of("code","SOURCE_TABLE_PRECISION","required_action","Source-layout tables use original precision6 and can round large summary counts. Use state-raw.jsonl for exact counters/returned FP64 bits and state.jsonl for portable semantic replay."));
        rows.add(Map.of("code","JDK_INFLATER_SELECTED","required_action","No optional libdeflate installation is needed. The Java worker explicitly selects the qualified HTSJDK5.0.0 JDK inflater profile."));
        if(NextflowFiles.integer(conditions.get("nonfinite_fields"),0,Long.MAX_VALUE)>0)rows.add(Map.of("code","ORIGINAL_NONFINITE_VALUES_RETAINED","required_action","Inspect CONDITIONS.json and raw state. Original zero-length/overlapping-feature NaN or infinity is retained; do not coerce it to zero. Changed inputs require a new analysis."));
        if(!NextflowFiles.list(conditions.get("excluded_antisense_gene_ids")).isEmpty())rows.add(Map.of("code","ORIGINAL_ANTISENSE_FILTER","required_action","Review every excluded gene ID in CONDITIONS.json. The original case-sensitive -AS rule is retained."));
        long missing=0;for(Object value:NextflowFiles.list(conditions.get("samples")))missing=Math.addExact(missing,NextflowFiles.integer(NextflowFiles.object(value).get("annotation_missing_chromosome_alignments"),0,Long.MAX_VALUE));
        if(missing>0)rows.add(Map.of("code","BAM_CHROMOSOMES_ABSENT_FROM_GTF","alignments",missing,"required_action","Review captured BAM/GTF reference names and CONDITIONS.json. These alignments count in original processed totals without gene assignment; names are not repaired."));
        if(Boolean.TRUE.equals(conditions.get("source_table_control_identifiers")))rows.add(Map.of("code","SOURCE_TABLE_RAW_CONTROL_IDENTIFIERS","required_action","Use unambiguous JSONL state for identifiers with control characters. Source-layout tables preserve raw identifiers and may be ambiguous to generic TSV readers."));
        return rows;
    }
    private static String warningText(Map<?,?> conditions)throws IOException {
        return "# Native TPMCalculator: warnings and required actions\n\n"+ACTION+"\n"+"\n## Recorded conditions\n\n"+NextflowJson.json(warningRows(conditions))+"\n\nRead products/CONDITIONS.json and complete worker streams. Raw FP64 NaN payload/sign is retained; replay compares NaN classifications, not payload/sign.\n";
    }
    private static void warnings(Path root,Map<?,?> conditions)throws IOException {
        TpmCalculatorRecords.json(root.resolve("WARNINGS.json"),Map.of("warnings",warningRows(conditions),"required_action",ACTION));
        String before="# Native TPMCalculator: warnings and required actions\n\n"+ACTION+"\n";
        if(!Files.readString(root.resolve("WARNINGS.md"),StandardCharsets.UTF_8).equals(before))throw new IOException("Native initial warning record changed");
        Files.writeString(root.resolve("WARNINGS.md"),warningText(conditions).substring(before.length()),StandardCharsets.UTF_8,StandardOpenOption.APPEND);
    }
    private static void validateWorker(Path bundle,TpmCalculatorRequest.Request request,Map<?,?> engine)throws IOException {
        Map<?,?> recipe=NextflowFiles.object(TpmCalculatorRecords.read(bundle.resolve("worker/REQUEST.json")));
        NextflowFiles.keys(recipe,"schema","effective","input_root","output_root","implementation");NextflowFiles.integer(recipe.get("schema"),1,1);
        if(!NextflowJson.json(recipe.get("effective")).equals(NextflowJson.json(request.effective()))||!NextflowJson.json(recipe.get("implementation")).equals(NextflowJson.json(engine.get("implementation"))))throw new IOException("Saved native worker recipe differs from complete source choices/implementation");
        String output=NextflowFiles.text(recipe.get("output_root"));String separator=output.endsWith("/products")?"/":output.endsWith("\\products")?"\\":null;
        if(separator==null)throw new IOException("Unknown native recorded output binding");String root=output.substring(0,output.length()-9);
        if(root.isEmpty()||!root.equals(recipe.get("input_root")))throw new IOException("Recorded run input/output bindings differ");
        Map<?,?> launch=NextflowFiles.object(TpmCalculatorRecords.read(bundle.resolve("worker/LAUNCH.json"))),exit=NextflowFiles.object(TpmCalculatorRecords.read(bundle.resolve("worker/EXIT.json")));
        NextflowFiles.keys(launch,"command","environment","pid","started_utc","heap_mib","wall_seconds","external_quantification_program","limits_scope");
        NextflowFiles.keys(exit,"pid","exit_code","elapsed_millis","owned_worker_alive","captured_stream_bytes","ended_utc");
        long pid=NextflowFiles.integer(launch.get("pid"),1,Long.MAX_VALUE);
        if(pid!=NextflowFiles.integer(exit.get("pid"),1,Long.MAX_VALUE)||NextflowFiles.integer(exit.get("exit_code"),0,0)!=0||!Boolean.FALSE.equals(exit.get("owned_worker_alive"))||!Boolean.FALSE.equals(launch.get("external_quantification_program")))throw new IOException("Saved worker process exit/identity contradicts an admitted native run");
        if(NextflowFiles.integer(launch.get("heap_mib"),64,Integer.MAX_VALUE)!=request.limits().heapMiB()||NextflowFiles.integer(launch.get("wall_seconds"),1,Integer.MAX_VALUE)!=request.limits().wallSeconds())throw new IOException("Recorded worker resource choices differ from request");
        long streams=Math.addExact(Files.size(bundle.resolve("worker/stdout.bin")),Files.size(bundle.resolve("worker/stderr.bin")));
        if(streams!=NextflowFiles.integer(exit.get("captured_stream_bytes"),0,request.limits().bundleBytes()))throw new IOException("Native complete captured stream length differs from process record");
        Map<?,?> env=NextflowFiles.object(launch.get("environment"));
        if(!Set.of("SystemRoot","WINDIR","HOME","USERPROFILE","TMP","TEMP","TMPDIR","LANG","LC_ALL").containsAll(env.keySet())||!env.keySet().containsAll(Set.of("HOME","USERPROFILE","TMP","TEMP","TMPDIR","LANG","LC_ALL")))throw new IOException("Recorded native worker environment contains an undeclared/missing binding");
        if(!"C.UTF-8".equals(env.get("LANG"))||!"C.UTF-8".equals(env.get("LC_ALL")))throw new IOException("Native recorded locale differs");
        for(String name:List.of("HOME","USERPROFILE"))if(!(root+separator+"worker").equals(env.get(name)))throw new IOException("Native worker home binding differs");
        for(String name:List.of("TMP","TEMP","TMPDIR"))if(!(root+separator+"worker"+separator+"tmp").equals(env.get(name)))throw new IOException("Native worker temporary binding differs");
        List<String> command=NextflowFiles.strings(launch.get("command"));
        if(command.size()!=18||command.get(15).isEmpty()||!(command.get(0).endsWith("/bin/java")||command.get(0).endsWith("\\bin\\java.exe")))throw new IOException("Recorded native worker command is not the fixed Java invocation");
        List<String> expected=List.of(command.get(0),"-Xmx"+request.limits().heapMiB()+"m","-XX:-UsePerfData","-XX:ErrorFile="+root+separator+"worker"+separator+"jvm-fatal-%p.log",
            "-Dfile.encoding=UTF-8","-Duser.language=en","-Duser.country=US","-Duser.timezone=UTC","-Dstdout.encoding=UTF-8","-Dstderr.encoding=UTF-8","-Dsun.stdout.encoding=UTF-8","-Dsun.stderr.encoding=UTF-8",
            "-Djava.io.tmpdir="+root+separator+"worker"+separator+"tmp","-Dsamjdk.use_libdeflate=false","-cp",command.get(15),TpmCalculatorWorker.class.getName(),root+separator+"worker"+separator+"REQUEST.json");
        if(!command.equals(expected))throw new IOException("Recorded worker command/bindings contradict fixed native execution");
        Map<?,?> returned=NextflowFiles.object(TpmCalculatorRecords.read(bundle.resolve("worker/RETURN.json")));
        NextflowFiles.keys(returned,"status","computed","products_recorded","conditions","libdeflate_enabled","java_runtime","java_vm","os","architecture");
        if(!"RETURNED_AND_RECORDED_NATIVE_PRODUCTS".equals(returned.get("status"))||!Boolean.TRUE.equals(returned.get("computed"))||!Boolean.TRUE.equals(returned.get("products_recorded"))||!Boolean.FALSE.equals(returned.get("libdeflate_enabled"))
            ||!NextflowJson.json(returned.get("conditions")).equals(NextflowJson.json(TpmCalculatorRecords.read(bundle.resolve("products/CONDITIONS.json")))))throw new IOException("Saved native worker return/conditions are incomplete or contradictory");
    }
}
