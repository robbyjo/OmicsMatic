/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Closed original TwoSampleMR local-clumping RUN/VERIFY/REPLAY. */
public final class TwoSampleMrHistoricalClumpCli {
    private TwoSampleMrHistoricalClumpCli() { }
    static final String ENGINE="original-twosamplemr-0.6.8-local-clumping";
    static final String SCOPE="DEVELOPMENT_SCOPE: original TwoSampleMR0.6.8 806703f0 / ieugwasr1.2.1 ce5d0c07 / official PLINK1.9.0 20260927 on WindowsR4.6.1. Literal instrument rows and complete SNP-major BED/BIM/FAM are retained. Original grouping, singleton bypasses, source defaults, duplicates, P-value formatting and parsed-index coercions are preserved. Two ieugwasr functions receive observation prefixes with exact original-statement AST inverses; original cleanup follows artifact copies and bindings are restored. VERIFY checks integrity, literal intake/reference structure and source input/report/selection projections, not an independent refit of every LD likelihood or authentication of coordinated replacement. Original PLINK thread/memory defaults are retained. Native compatible clumping remains separate. The tagged0.6.8 source is bound by all149 function hashes, not an installed RemoteSha or proven paper execution commit. Dependencies are declared present-day versions; historical dependency-era reproduction, older API clumping, global registration and catalog admission remain open.";
    static final String ACTION="Read WARNINGS.md, analysis-status.json, worker/report.json, decisions.tsv and every original PLINK/R log. A singleton may bypass reference/significance checks; a complete empty selection has no downstream MR estimate. No silent SNP/allele/population, p-value, threshold, duplicate, version or estimator repair. FAIL2: NO COMPLETE RESULT; retain inputs, full available original returns/errors, raw reports, conditions and partials, correct the named data/design, numeric, integrity, capacity or process cause and retry NEW output. SKIP3: dependent analysis skipped; supply the named exact permitted runtime/input or retain the skip. Downloads/private installation are allowed where permitted; if prohibited, explain the specific restriction and skip the affected analysis. "+SCOPE;
    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            if(args.length==1&&args[0].equals("--help")){emit(out,"TwoSampleMrHistoricalClumpCli --request UTF8_JSON | --verify COMPLETE_BUNDLE\nRUN: original local clump_data on literal SNP rows and explicit BED/BIM/FAM. VERIFY: offline integrity/intake and source-report/selection checks. REPLAY: same original workflow in NEW output.\n"+ACTION);return 0;}
            if(args.length==2&&args[0].equals("--verify")){verify(Path.of(args[1]));emit(out,"Verified saved TwoSampleMR capture offline. "+SCOPE);return 0;}
            if(args.length!=2||!args[0].equals("--request"))throw new IOException("Require --request JSON, --verify BUNDLE or --help");
            Path source=Path.of(args[1]).toAbsolutePath().normalize();byte[] original=bytes(source,2_000_000);var raw=parse(original);String mode=text(raw,"mode");
            if(mode.equals("VERIFY")){control(raw,false);verify(resolve(source.getParent(),text(raw,"analysis")));emit(out,"Verified saved TwoSampleMR capture offline. "+SCOPE);return 0;}
            if(mode.equals("REPLAY")) {
                control(raw,true);Path prior=resolve(source.getParent(),text(raw,"analysis"));verify(prior);
                Path destination=resolve(source.getParent(),text(raw,"out"));replayOutput(prior,destination);var request=read(prior.resolve("request.json"));
                for(String key:TwoSampleMrHistoricalClumpContracts.INPUTS)if(request.containsKey(key))request.put(key,resolve(prior,text(request,key)).toString());
                var binding=read(prior.resolve("worker/runtime-bindings.json"));
                request.put("rscript",raw.containsKey("rscript")?resolve(source.getParent(),text(raw,"rscript")).toString():text(binding,"rscript"));
                request.put("r_libraries",raw.containsKey("r_libraries")?strings(raw.get("r_libraries"),"r_libraries",1,16):binding.get("libraries"));
                if(raw.containsKey("plink_executable"))request.put("plink_executable",resolve(source.getParent(),text(raw,"plink_executable")).toString());else request.put("plink_executable",text(binding,"plink_executable"));
                if(raw.containsKey("plink_sha256"))request.put("plink_sha256",text(raw,"plink_sha256"));
                request.put("out",destination.toString());
                return execute(source.getParent(),request,RsemJson.bytes(request),Map.of("analysis",prior.toString(),"seal_sha256",FollowupSupport.hash(prior.resolve("capture-files.json"))),out,err);
            }
            return execute(source.getParent(),raw,original,Map.of(),out,err);
        }catch(TwoSampleMrHistoricalClumpRuntime.Unavailable error){emit(err,"SKIP3 — DEPENDENT ANALYSIS SKIPPED: "+error+"\n"+ACTION);return 3;}
        catch(OutOfMemoryError error){emit(err,"FAIL2 — NO COMPLETE RESULT: Java heap exhausted; preserve diagnostics, choose explicit heap/capacities and NEW output. "+ACTION);return 2;}
        catch(Exception error){emit(err,"FAIL2 — NO COMPLETE RESULT: "+error+"\n"+ACTION);return 2;}
    }
    private static void control(Map<String,Object> raw,boolean replay)throws IOException {
        keys(raw,replay?Set.of("schema_version","mode","analysis","out","rscript","r_libraries","plink_executable","plink_sha256"):Set.of("schema_version","mode","analysis"));
        if(integer(raw,"schema_version",0,1,1)!=1)throw new IOException("Require schema_version1");text(raw,"analysis");if(replay)text(raw,"out");
    }
    private static int execute(Path parent,Map<String,Object> raw,byte[] original,Map<String,Object> replay,PrintStream out,PrintStream err)throws IOException {
        if(raw.get("profile") instanceof String profile&&!TwoSampleMrHistoricalClumpContracts.PROFILES.contains(profile))throw new TwoSampleMrHistoricalClumpRuntime.Unavailable("Exact requested TwoSampleMR/R profile is not qualified: "+profile+". Restore the named exact permitted separately qualified profile or retain the skip; adjacent editions are not substituted.");
        var request=TwoSampleMrHistoricalClumpContracts.validate(raw);Path root=newOutput(resolve(parent,text(request,"out")));
        var manifest=new LinkedHashMap<String,Object>();manifest.put("schema_version",1);manifest.put("engine",ENGINE);manifest.put("scope",SCOPE);
        manifest.put("profile",request.get("profile"));manifest.put("operation",request.get("operation"));manifest.put("source_request_sha256",TwoSampleMrHistoricalClumpRuntime.sha(original));
        int code=2;String error=null;json(root.resolve("analysis-status.json"),state("RUNNING_NO_RESULT_ADMITTED",false,null));
        try {
            Files.write(root.resolve("source-request.json"),original,StandardOpenOption.CREATE_NEW);if(!replay.isEmpty())json(root.resolve("replay-source.json"),replay);
            Files.createDirectory(root.resolve("inputs"));Files.createDirectory(root.resolve("worker"));Files.createDirectory(root.resolve("results"));
            var captured=new LinkedHashMap<String,Object>(raw);captured.put("out",".");var pins=new TreeMap<String,Object>();
            long remaining=((Number)request.get("maximum_input_bytes")).longValue();var originals=new LinkedHashMap<String,Path>();var sourcePins=new LinkedHashMap<String,String>();
            for(String key:TwoSampleMrHistoricalClumpContracts.INPUTS) {
                Path input=resolve(parent,text(raw,key));if(!Files.exists(input)||!Files.isReadable(input))throw new TwoSampleMrHistoricalClumpRuntime.Unavailable("Required permitted input is unavailable: "+input);
                if(!Files.isRegularFile(input,LinkOption.NOFOLLOW_LINKS))throw new IOException("Require regular source files; no input alias/type substitution");
                originals.put(key,input);sourcePins.put(key,FollowupSupport.hash(input));
            }
            for(String key:TwoSampleMrHistoricalClumpContracts.INPUTS)if(raw.containsKey(key)) {
                Path target=root.resolve(TwoSampleMrHistoricalClumpContracts.captured(key));remaining-=capture(resolve(parent,text(raw,key)),target,remaining);
                pins.put(TwoSampleMrHistoricalClumpContracts.captured(key),FollowupSupport.hash(target));captured.put(key,TwoSampleMrHistoricalClumpContracts.captured(key));
            }
            for(String key:TwoSampleMrHistoricalClumpContracts.INPUTS)if(!sourcePins.get(key).equals(FollowupSupport.hash(originals.get(key)))||!sourcePins.get(key).equals(pins.get(TwoSampleMrHistoricalClumpContracts.captured(key))))throw new IOException("Instrument/reference set changed across capture; no mixed snapshot admitted");
            json(root.resolve("request.json"),captured);var effective=TwoSampleMrHistoricalClumpContracts.validate(captured);json(root.resolve("effective-request.json"),effective);
            manifest.put("captured_input_sha256",pins);TwoSampleMrHistoricalClumpRuntime.sources(root,false);TwoSampleMrHistoricalClumpFiles.prepare(root,effective,false);
            TwoSampleMrHistoricalClumpRuntime.run(root,parent,request,manifest);
            for(var entry:pins.entrySet())if(!FollowupSupport.hash(root.resolve(entry.getKey())).equals(entry.getValue()))throw new IOException("Captured input changed during execution");
            TwoSampleMrHistoricalClumpRuntime.sources(root,true);TwoSampleMrHistoricalClumpFiles.prepare(root,effective,true);TwoSampleMrHistoricalClumpAudit.outputs(root,effective,false);code=0;
        }catch(TwoSampleMrHistoricalClumpRuntime.Unavailable unavailable){code=3;error=unavailable.toString();}
        catch(Exception failed){error=failed.toString();if(failed instanceof InterruptedException||failed instanceof InterruptedIOException)Thread.currentThread().interrupt();}
        catch(OutOfMemoryError failed){error="Java heap exhausted; choose explicit heap/capacities and NEW output";}
        String status=code==0?"completed-with-warnings":code==3?"SKIP3_DEPENDENT_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_RESULT";
        manifest.put("analysis_status",status);manifest.put("results_admitted",code==0);if(error!=null)manifest.put("error",error);
        json(root.resolve("manifest.json"),manifest);json(root.resolve("analysis-status.json"),state(status,code==0,error));
        Files.writeString(root.resolve("WARNINGS.md"),warningText(root,status,error),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        try{seal(root);if(code==0)verify(root);}
        catch(IOException failure){code=2;error=failure.toString();manifest.put("analysis_status","FAIL2_NO_COMPLETE_RESULT");manifest.put("results_admitted",false);manifest.put("verification_error",failure.toString());json(root.resolve("manifest.json"),manifest);json(root.resolve("analysis-status.json"),state("FAIL2_NO_COMPLETE_RESULT",false,failure.toString()));Files.writeString(root.resolve("WARNINGS.md"),warningText(root,"FAIL2_NO_COMPLETE_RESULT",failure.toString()),StandardCharsets.UTF_8);seal(root);}
        emit(code==0?out:err,(code==0?"Completed original local clumping: ":code==3?"SKIP3 — DEPENDENT ANALYSIS SKIPPED: ":"FAIL2 — NO COMPLETE RESULT: ")+root);
        if(error!=null)emit(err,"CAUSE / REQUIRED RECOVERY: "+error);
        emit(err,"WARNINGS / REQUIRED ACTIONS: "+root.resolve("WARNINGS.md")+"\n"+ACTION);return code;
    }
    static void verify(Path root)throws IOException {
        root=root.toAbsolutePath().normalize();var recorded=read(root.resolve("capture-files.json"),16_000_000);keys(recorded,Set.of("schema_version","authentication","files"));
        if(integer(recorded,"schema_version",0,1,1)!=1||!Boolean.FALSE.equals(recorded.get("authentication")))throw new IOException("Invalid seal/authentication claim");
        var expected=object(recorded.get("files"),"file pins");var actual=TwoSampleMrHistoricalClumpRuntime.footprint(root);actual.remove("capture-files.json");
        if(!actual.keySet().equals(expected.keySet()))throw new IOException("Capture file population differs");
        for(var entry:expected.entrySet())if(!FollowupSupport.hash(safe(root,entry.getKey())).equals(entry.getValue()))throw new IOException("Capture hash differs: "+entry.getKey());
        var manifest=read(root.resolve("manifest.json"));var state=read(root.resolve("analysis-status.json"));
        if(!ENGINE.equals(manifest.get("engine"))||!SCOPE.equals(manifest.get("scope"))||!"completed-with-warnings".equals(manifest.get("analysis_status"))||!Boolean.TRUE.equals(manifest.get("results_admitted"))||!"completed-with-warnings".equals(state.get("status"))||!Boolean.TRUE.equals(state.get("results_admitted"))||!ACTION.equals(state.get("required_action")))throw new IOException("Failed/skipped/incomplete captures are not admitted results");
        var original=read(root.resolve("source-request.json"));TwoSampleMrHistoricalClumpContracts.validate(original);
        if(!FollowupSupport.hash(root.resolve("source-request.json")).equals(manifest.get("source_request_sha256")))throw new IOException("Original request identity differs");
        var normalized=new LinkedHashMap<String,Object>(original);normalized.put("out",".");
        for(String key:TwoSampleMrHistoricalClumpContracts.INPUTS)if(original.containsKey(key))normalized.put(key,TwoSampleMrHistoricalClumpContracts.captured(key));
        if(!same(normalized,read(root.resolve("request.json"))))throw new IOException("Captured request differs from original declared request");
        var effective=TwoSampleMrHistoricalClumpContracts.validate(normalized);if(!same(effective,read(root.resolve("effective-request.json"))))throw new IOException("Effective request differs");
        if(!Objects.equals(effective.get("profile"),manifest.get("profile"))||!Objects.equals(effective.get("operation"),manifest.get("operation")))throw new IOException("Model/profile record differs");
        var inputs=object(manifest.get("captured_input_sha256"),"input pins");var wanted=new TreeMap<String,Object>();
        for(String key:TwoSampleMrHistoricalClumpContracts.INPUTS)if(effective.containsKey(key)){String name=TwoSampleMrHistoricalClumpContracts.captured(key);wanted.put(name,FollowupSupport.hash(root.resolve(name)));}
        if(!wanted.equals(inputs))throw new IOException("Captured input inventory differs");
        TwoSampleMrHistoricalClumpRuntime.sources(root,true);TwoSampleMrHistoricalClumpFiles.prepare(root,effective,true);TwoSampleMrHistoricalClumpAudit.outputs(root,effective,true);
        if(!Files.readString(root.resolve("WARNINGS.md"),StandardCharsets.UTF_8).equals(warningText(root,"completed-with-warnings",null)))throw new IOException("Warnings/recovery text differs");
    }
    static void seal(Path root)throws IOException {
        var pins=new TreeMap<String,Object>();for(String name:TwoSampleMrHistoricalClumpRuntime.footprint(root).keySet())if(!name.equals("capture-files.json"))pins.put(name,FollowupSupport.hash(safe(root,name)));
        json(root.resolve("capture-files.json"),Map.of("schema_version",1,"authentication",false,"files",pins));
    }
    private static Path safe(Path root,String name)throws IOException {
        Path p=Path.of(name);if(p.isAbsolute()||!p.normalize().equals(p)||name.contains("\\")||name.contains("..")||!root.resolve(p).normalize().startsWith(root))throw new IOException("Unsafe capture member");return root.resolve(p);
    }
    private static long capture(Path source,Path target,long cap)throws IOException {
        if(!Files.exists(source)||!Files.isReadable(source))throw new TwoSampleMrHistoricalClumpRuntime.Unavailable("Required permitted input is unavailable: "+source);
        if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)||Files.size(source)>cap)throw new IOException("Input is nonregular or exceeds maximum_input_bytes");
        String before=FollowupSupport.hash(source);long count=0;
        try(InputStream input=Files.newInputStream(source);OutputStream output=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)){
            byte[] buffer=new byte[65536];int read;while((read=input.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Input capture interrupted");if(read>cap-count)throw new IOException("Input grew beyond declared capacity");output.write(buffer,0,read);count+=read;}}
        if(!before.equals(FollowupSupport.hash(source))||!before.equals(FollowupSupport.hash(target)))throw new IOException("Input changed during capture");return count;
    }
    private static void replayOutput(Path prior,Path destination)throws IOException {
        Path original=prior.toRealPath(),existing=destination.toAbsolutePath().normalize();var missing=new ArrayDeque<Path>();
        while(!Files.exists(existing,LinkOption.NOFOLLOW_LINKS)) {
            if(existing.getParent()==null)throw new IOException("Cannot resolve REPLAY output; choose a NEW directory outside the original analysis");
            missing.addFirst(existing.getFileName());existing=existing.getParent();
        }
        Path actual=existing.toRealPath();for(Path name:missing)actual=actual.resolve(name);
        if(actual.normalize().startsWith(original))throw new IOException("REPLAY output must be outside the original analysis; preserve the parent capture and choose a NEW sibling directory");
    }
    private static Path newOutput(Path root)throws IOException {
        if(Files.exists(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("Preserve existing output; choose a NEW directory");
        Path existing=root;var missing=new ArrayDeque<Path>();while(!Files.exists(existing,LinkOption.NOFOLLOW_LINKS)){missing.addFirst(existing.getFileName());existing=existing.getParent();if(existing==null)throw new IOException("Cannot resolve output ancestor");}
        Path actual=existing.toRealPath();for(Path name:missing)actual=actual.resolve(name);if(!actual.normalize().equals(root))throw new IOException("Capture output cannot use symbolic links or junction aliases; choose its explicit resolved path");
        for(Path ancestor=root.getParent();ancestor!=null;ancestor=ancestor.getParent())if(Files.exists(ancestor.resolve("capture-files.json"),LinkOption.NOFOLLOW_LINKS)||Files.exists(ancestor.resolve("analysis-status.json"),LinkOption.NOFOLLOW_LINKS))throw new IOException("Output cannot be nested within a retained analysis; choose NEW sibling output");
        Files.createDirectories(root.getParent());return Files.createDirectory(root);
    }
    private static byte[] bytes(Path path,long cap)throws IOException {if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)>cap)throw new IOException("Require bounded regular JSON input");return Files.readAllBytes(path);}
    private static Map<String,Object> parse(byte[] raw)throws IOException {
        var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
        return object(SimpleJson.parse(decoder.decode(java.nio.ByteBuffer.wrap(raw)).toString()),"request");
    }
    private static Map<String,Object> state(String status,boolean admitted,String error){var value=new LinkedHashMap<String,Object>();value.put("schema_version",1);value.put("status",status);value.put("results_admitted",admitted);value.put("required_action",ACTION);if(error!=null)value.put("error",error);return value;}
    private static String warningText(Path root,String status,String error)throws IOException {
        String upstream=Files.isRegularFile(root.resolve("worker/warnings.txt"))?Files.readString(root.resolve("worker/warnings.txt"),StandardCharsets.UTF_8):"No complete upstream warning ledger is available; inspect partials/logs.\n";
        String audited=Files.isRegularFile(root.resolve("audit-warnings.txt"))?Files.readString(root.resolve("audit-warnings.txt"),StandardCharsets.UTF_8):"No complete semantic warning ledger is available.\n";
        return "# TwoSampleMR warnings and required actions\n\nStatus: "+status+"\n\n"+(error==null?"":error+"\n\n")+ACTION+"\n\n## Source behavior and required review\n\n"+audited+"\n## Original and adapter warnings\n\n"+upstream;
    }
    private static void emit(PrintStream stream,String text){stream.println(text);}
}
