/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.coloc.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Native ABF RUN/VERIFY/REPLAY; distinct from the existing SuSiE coloc CLI. */
public final class ColocAbfWorkflowCli {
    private ColocAbfWorkflowCli() { }
    private static final Set<String> FILES=Set.of("source-request.json","request.json","inputs/trait1.tsv","inputs/trait2.tsv",
        "producer.json","results.json","summary.tsv","variants.tsv","matched-weights.tsv","WARNINGS.md","analysis-status.json");
    private record Calculation(ColocAbfResult result,List<String> warnings,ColocAbfInput first,ColocAbfInput second) { }
    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    static int run(String[] args,PrintStream out,PrintStream err){
        try {
            if(args.length==1&&args[0].equals("--help")){emit(out,help());return 0;}
            if(args.length==2&&args[0].equals("--verify")){
                Path root=Path.of(args[1]).toAbsolutePath().normalize();verify(root,true);emit(out,"Verified native COLOC ABF by input parsing and complete calculation replay: "+root);showWarnings(root,err);return 0;
            }
            if(args.length!=2||!args[0].equals("--request"))throw new IOException("Require --request UTF8_JSON, --verify BUNDLE or --help");
            Path source=Path.of(args[1]).toAbsolutePath().normalize();byte[] original=ColocAbfFiles.bytes(source,2_000_000);
            Map<String,Object> raw=object(SimpleJson.parse(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(original)).toString()),"request");
            String mode=text(raw,"mode");
            if(mode.equals("VERIFY")){
                control(raw,false);Path root=resolve(source.getParent(),text(raw,"analysis"));verify(root,true);
                emit(out,"Verified native COLOC ABF by input parsing and complete calculation replay: "+root);showWarnings(root,err);return 0;
            }
            if(mode.equals("REPLAY"))return replay(source,raw,original,out,err);
            return execute(source.getParent(),raw,original,null,null,out,err);
        }catch(ColocAbfFiles.Unavailable e){emit(err,"SKIP3 — DEPENDENT ANALYSIS SKIPPED: "+e.getMessage()+"\n"+ColocAbfContracts.ACTION);return 3;}
        catch(OutOfMemoryError e){emit(err,"FAIL2 — NO RESULT ADMITTED: Java heap exhausted. Use an appropriate explicit heap/capacities and NEW output. "+ColocAbfContracts.ACTION);return 2;}
        catch(Exception e){emit(err,"FAIL2 — NO RESULT ADMITTED: "+e+"\n"+ColocAbfContracts.ACTION);return 2;}
    }
    private static int execute(Path parent,Map<String,Object> raw,byte[] original,byte[] replayRequest,Map<String,Object> replayParent,PrintStream out,PrintStream err)throws IOException {
        Map<String,Object> request=ColocAbfContracts.validate(raw);Path root=resolve(parent,text(request,"out"));
        if(Files.exists(root,LinkOption.NOFOLLOW_LINKS))throw new IOException("Output already exists; use a NEW directory: "+root);
        Files.createDirectories(root.getParent());Files.createDirectory(root);List<String> warnings=new ArrayList<>();warnings.add(ColocAbfContracts.SCOPE);
        try {
            Files.write(root.resolve("source-request.json"),original,StandardOpenOption.CREATE_NEW);
            Map<String,Object> portable=ColocAbfContracts.portable(request);ColocAbfFiles.json(root.resolve("request.json"),portable);Files.createDirectory(root.resolve("inputs"));
            if(replayRequest!=null){Files.write(root.resolve("replay-request.json"),replayRequest,StandardOpenOption.CREATE_NEW);ColocAbfFiles.json(root.resolve("replay-parent.json"),replayParent);}
            long remaining=((Number)request.get("maximum_input_bytes")).longValue();
            for(String key:List.of("trait1","trait2")){
                var t=object(request.get(key),key);remaining-=ColocAbfFiles.capture(resolve(parent,text(t,"file")),root.resolve("inputs/"+key+".tsv"),remaining);
            }
            ColocAbfFiles.json(root.resolve("producer.json"),ColocAbfFiles.producer());
            Calculation calculated=calculate(root,portable);warnings=new ArrayList<>(calculated.warnings());
            ColocAbfTables.tables(root,calculated.result(),calculated.first(),calculated.second(),((Number)request.get("maximum_output_bytes")).longValue(),false);
            ColocAbfFiles.json(root.resolve("results.json"),ColocAbfTables.result(calculated.result(),warnings));
            Files.writeString(root.resolve("WARNINGS.md"),warningText("completed-with-warnings",warnings),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            ColocAbfFiles.json(root.resolve("analysis-status.json"),state("completed-with-warnings",true,null));
            seal(root);verify(root,false);
            showWarnings(root,err);
            ColocAbfFiles.json(root.resolve("COMPLETED.pending"),Map.of("schema_version",1,"profile",ColocAbfContracts.PROFILE,
                "manifest_sha256",ColocAbfFiles.pin(root.resolve("manifest.json")).get("sha256"),"authentication",false));
            Files.move(root.resolve("COMPLETED.pending"),root.resolve("COMPLETED.json"),StandardCopyOption.ATOMIC_MOVE);
            emit(out,"Completed native COLOC ABF: "+root+"; "+calculated.result().variants().size()+" shared variants; H4="+calculated.result().hypothesisPosteriors()[4]);return 0;
        }catch(ColocAbfFiles.Unavailable e){return failure(root,3,e,warnings,err);}
        catch(OutOfMemoryError e){return failure(root,2,new IOException("Java heap exhausted; no complete scientific result admitted",e),warnings,err);}
        catch(Exception e){return failure(root,2,e,warnings,err);}
    }
    private static Calculation calculate(Path root,Map<String,Object> request)throws IOException {
        long cap=((Number)request.get("maximum_input_bytes")).longValue(),bytes=0;
        for(String key:List.of("trait1","trait2")){
            Path p=root.resolve("inputs/"+key+".tsv");long size=Files.size(p);if(size>cap-bytes)throw new IOException("Captured population exceeds combined input byte capacity");bytes+=size;
        }
        long rows=((Number)request.get("maximum_variants")).longValue();
        var first=ColocAbfTables.read(root.resolve("inputs/trait1.tsv"),object(request.get("trait1"),"trait1"),rows,"1");
        var second=ColocAbfTables.read(root.resolve("inputs/trait2.tsv"),object(request.get("trait2"),"trait2"),rows,"2");
        var priors=object(request.get("priors"),"priors");
        var options=new ColocAbfOptions(((Number)priors.get("p1")).doubleValue(),((Number)priors.get("p2")).doubleValue(),((Number)priors.get("p12")).doubleValue(),first.weights(),second.weights());
        var result=ColocAbf.analyze(first.input(),second.input(),options);
        List<String> warnings=new ArrayList<>();warnings.add(ColocAbfContracts.SCOPE);warnings.addAll(first.warnings());warnings.addAll(second.warnings());warnings.addAll(result.warnings());
        return new Calculation(result,List.copyOf(warnings),first.input(),second.input());
    }
    static void verify(Path root,boolean completed)throws IOException {
        root=root.toAbsolutePath().normalize();
        if(!Files.exists(root,LinkOption.NOFOLLOW_LINKS))throw new ColocAbfFiles.Unavailable("Restore complete COLOC bundle: "+root,null);
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(root))throw new IOException("Require a regular unlinked bundle directory");
        if(completed&&!Files.isRegularFile(root.resolve("COMPLETED.json"),LinkOption.NOFOLLOW_LINKS))throw new IOException("No complete result marker; failed/skipped/partial analyses cannot verify");
        var manifest=ColocAbfFiles.read(root.resolve("manifest.json"));keys(manifest,Set.of("schema_version","profile","authentication","files"));
        if(integer(manifest,"schema_version",0,1,1)!=1||!ColocAbfContracts.PROFILE.equals(manifest.get("profile"))||!Boolean.FALSE.equals(manifest.get("authentication")))throw new IOException("Unexpected manifest profile or authentication claim");
        var actual=ColocAbfFiles.inventory(root);var expected=object(manifest.get("files"),"complete file inventory");
        if(!same(actual,expected))throw new IOException("Complete bundle file population/hash differs");
        var names=new HashSet<>(FILES);boolean replay=actual.containsKey("replay-request.json")||actual.containsKey("replay-parent.json");
        if(replay){names.add("replay-request.json");names.add("replay-parent.json");}
        if(!actual.keySet().equals(names))throw new IOException("Unexpected or incomplete native COLOC bundle file population");
        if(completed){
            var marker=ColocAbfFiles.read(root.resolve("COMPLETED.json"));keys(marker,Set.of("schema_version","profile","manifest_sha256","authentication"));
            if(integer(marker,"schema_version",0,1,1)!=1||!ColocAbfContracts.PROFILE.equals(marker.get("profile"))||!Boolean.FALSE.equals(marker.get("authentication"))||!ColocAbfFiles.pin(root.resolve("manifest.json")).get("sha256").equals(marker.get("manifest_sha256")))throw new IOException("Complete marker differs from exact manifest");
        }
        if(!same(ColocAbfFiles.read(root.resolve("analysis-status.json")),state("completed-with-warnings",true,null)))throw new IOException("Failed/skipped/changed status is not an admitted result");
        var source=ColocAbfContracts.validate(ColocAbfFiles.read(root.resolve("source-request.json")));
        var request=ColocAbfFiles.read(root.resolve("request.json"));
        if(!same(ColocAbfContracts.portable(source),request))throw new IOException("Captured settings differ from original request");
        var producer=ColocAbfFiles.read(root.resolve("producer.json"));keys(producer,Set.of("selected_class_sha256","java_version","os_name","authentication"));
        if(!Boolean.FALSE.equals(producer.get("authentication"))||!same(producer.get("selected_class_sha256"),ColocAbfFiles.producer().get("selected_class_sha256")))throw new IOException("Restore exact original producer classes; do not refresh saved hashes");
        text(producer,"java_version");text(producer,"os_name");
        Calculation calculated=calculate(root,request);var result=ColocAbfFiles.read(root.resolve("results.json"));
        ColocAbfTables.compareJson(ColocAbfTables.result(calculated.result(),calculated.warnings()),result);
        ColocAbfTables.tables(root,calculated.result(),calculated.first(),calculated.second(),((Number)request.get("maximum_output_bytes")).longValue(),true);
        List<String> savedWarnings=new ArrayList<>();for(Object value:list(result.get("warnings"),"warnings")){
            if(!(value instanceof String s))throw new IOException("Invalid warning record");savedWarnings.add(s);
        }
        if(!warningText("completed-with-warnings",savedWarnings).equals(Files.readString(root.resolve("WARNINGS.md"),StandardCharsets.UTF_8)))throw new IOException("Saved warning/recovery text differs");
        if(replay){
            control(ColocAbfFiles.read(root.resolve("replay-request.json")),true);
            var parent=ColocAbfFiles.read(root.resolve("replay-parent.json"));keys(parent,Set.of("parent_analysis","verified_parent_manifest_sha256","profile","native_recompute_only"));
            text(parent,"parent_analysis");String hash=text(parent,"verified_parent_manifest_sha256");
            if(!hash.matches("[a-f0-9]{64}")||!ColocAbfContracts.PROFILE.equals(parent.get("profile"))||!Boolean.TRUE.equals(parent.get("native_recompute_only")))throw new IOException("Replay parent record malformed");
        }
        long outputBytes=0;
        for(var e:actual.entrySet())if(!e.getKey().startsWith("inputs/"))outputBytes=Math.addExact(outputBytes,((Number)object(e.getValue(),"file pin").get("bytes")).longValue());
        if(outputBytes>((Number)request.get("maximum_output_bytes")).longValue())throw new IOException("Complete result and diagnostic files exceed maximum_output_bytes");
        if(!same(actual,ColocAbfFiles.inventory(root)))throw new IOException("Bundle changed during verification");
    }
    private static int replay(Path source,Map<String,Object> raw,byte[] original,PrintStream out,PrintStream err)throws IOException {
        control(raw,true);Path parent=resolve(source.getParent(),text(raw,"analysis"));verify(parent,true);
        var request=ColocAbfFiles.read(parent.resolve("request.json"));request.put("out",resolve(source.getParent(),text(raw,"out")).toString());
        for(String key:List.of("trait1","trait2")){
            var t=new LinkedHashMap<>(object(request.get(key),key));t.put("file",parent.resolve("inputs/"+key+".tsv").toString());request.put(key,t);
        }
        var proof=Map.<String,Object>of("parent_analysis",parent.toString(),"verified_parent_manifest_sha256",ColocAbfFiles.pin(parent.resolve("manifest.json")).get("sha256"),"profile",ColocAbfContracts.PROFILE,"native_recompute_only",true);
        return execute(source.getParent(),request,(NextflowJson.json(request)+"\n").getBytes(StandardCharsets.UTF_8),original,proof,out,err);
    }
    private static void control(Map<String,Object> raw,boolean replay)throws IOException {
        keys(raw,replay?Set.of("schema_version","mode","analysis","out"):Set.of("schema_version","mode","analysis"));
        if(integer(raw,"schema_version",0,1,1)!=1||!(replay?"REPLAY":"VERIFY").equals(raw.get("mode")))throw new IOException("Invalid verification/replay schema or mode");
        text(raw,"analysis");if(replay)text(raw,"out");
    }
    static void seal(Path root)throws IOException {
        var manifest=Map.of("schema_version",1,"profile",ColocAbfContracts.PROFILE,"authentication",false,"files",ColocAbfFiles.inventory(root));
        Files.writeString(root.resolve("manifest.json"),NextflowJson.json(manifest)+"\n",StandardCharsets.UTF_8);
    }
    private static Map<String,Object> state(String status,boolean admitted,Exception error){
        var state=new LinkedHashMap<String,Object>();state.put("status",status);state.put("results_admitted",admitted);state.put("profile",ColocAbfContracts.PROFILE);state.put("required_action",ColocAbfContracts.ACTION);
        if(error!=null)state.put("error",error.toString());return state;
    }
    private static int failure(Path root,int code,Exception error,List<String> prior,PrintStream err){
        String status=code==3?"SKIP3_DEPENDENT_ANALYSIS_SKIPPED":"FAIL2_NO_RESULT_ADMITTED";List<String> warnings=new ArrayList<>(prior);warnings.add(status+": "+error);
        try {
            for(String name:List.of("analysis-status.json","WARNINGS.md"))if(Files.exists(root.resolve(name)))Files.copy(root.resolve(name),root.resolve("unadmitted-"+name));
            Files.writeString(root.resolve("analysis-status.json"),NextflowJson.json(state(status,false,error))+"\n",StandardCharsets.UTF_8);
            Files.writeString(root.resolve("WARNINGS.md"),warningText(status,warnings),StandardCharsets.UTF_8);seal(root);
        }catch(Exception recordFailure){emit(err,"Additional failure while saving diagnostics: "+recordFailure);}
        emit(err,(code==3?"SKIP3 — DEPENDENT ANALYSIS SKIPPED: ":"FAIL2 — NO RESULT ADMITTED: ")+root+"\n"+error+"\nWARNINGS / REQUIRED ACTIONS: "+root.resolve("WARNINGS.md")+"\n"+ColocAbfContracts.ACTION);return code;
    }
    private static String warningText(String status,List<String> warnings){return "# "+status+"\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: "+ColocAbfContracts.ACTION+"\n";}
    private static void showWarnings(Path root,PrintStream err)throws IOException {
        var result=ColocAbfFiles.read(root.resolve("results.json"));var warnings=list(result.get("warnings"),"warnings");
        emit(err,"WARNINGS / REQUIRED ACTIONS ("+warnings.size()+" records): "+root.resolve("WARNINGS.md"));
        for(int i=0;i<Math.min(warnings.size(),20);i++)emit(err,"WARNING: "+warnings.get(i));
        if(warnings.size()>20)emit(err,"Console shows the first 20 records; all "+warnings.size()+" warnings are retained in WARNINGS.md and results.json.");
        emit(err,ColocAbfContracts.ACTION);
    }
    private static void emit(PrintStream stream,String message){stream.writeBytes((message+"\n").getBytes(StandardCharsets.UTF_8));}
    static String help(){return """
        Usage: coloc-abf --request UTF8_JSON | --verify COMPLETE_BUNDLE | --help
        schema_version: 1. RUN requests supply out, trait1 and trait2. Each trait
        supplies file, type (quant|cc), and explicit columns mapping: snp plus
        beta/varbeta or pvalues/MAF. Quantitative estimates require sdY or MAF/N;
        P-value mode requires scalar N and, for cc, case fraction s. varbeta is
        variance, not SE. Optional roles: position, prior_weight. Files are exact
        unquoted UTF-8 TSV; IDs and headers are never trimmed or harmonized.
        Optional priors: {p1:1e-4,p2:1e-4,p12:1e-5}. Limits: maximum_input_bytes
        (combined, default 256000000, max 1000000000), maximum_variants (per trait,
        default/max 1000000), maximum_output_bytes (default 1000000000, max 4000000000).
        VERIFY requests supply analysis. REPLAY requests supply analysis and a
        NEW out; all original scientific settings are retained. Paths resolve
        against the request directory. Native calculation needs Java, not R.
        Existing coloc consumes SuSiE signal files and is a different command.
        """+ColocAbfContracts.SCOPE+"\n"+ColocAbfContracts.ACTION;}
}
