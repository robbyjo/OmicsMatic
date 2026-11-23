/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.SquidpyWorkflowFiles.*;

/** Original public Squidpy APIs with captured inputs, offline audit and real replay. */
public final class SquidpyWorkflowCli {
    private SquidpyWorkflowCli() { }
    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    static int run(String[] args,PrintStream out,PrintStream err){
        Path root=null;
        try{
            if(args.length==1&&args[0].equals("--help")){emit(out,help());return 0;}
            if(args.length==2&&args[0].equals("--verify")){
                Path target=Path.of(args[1]).toAbsolutePath().normalize();var result=SquidpyWorkflowAudit.verify(target);
                emit(out,"Verified saved Squidpy typed object, inventory and admission offline: "+target+"; operation="+result.get("operation"));return 0;
            }
            require(args.length==2&&args[0].equals("--request"),"Use --request JSON, --verify CAPTURE or --help");
            Path source=Path.of(args[1]).toAbsolutePath().normalize();byte[] raw=bytes(source,67108864);var original=object(GprofilerJson.read(raw));
            Path parent=null;Map<String,Object> parentSeal=null,parentRequest=null,request;
            if("REPLAY".equals(original.get("mode"))){
                keys(original,Set.of("schema_version","mode","analysis","out"));integer(original.get("schema_version"),1,1);
                parent=SquidpyWorkflowContracts.path(source.getParent(),text(original.get("analysis"),"analysis"));SquidpyWorkflowAudit.verify(parent);parent=parent.toRealPath();
                parentSeal=pin(parent.resolve("manifest.json"));request=read(parent.resolve("canonical-request.json"));parentRequest=new LinkedHashMap<>(request);
                request.put("out",SquidpyWorkflowContracts.path(source.getParent(),text(original.get("out"),"out")).toString());
                Path target=Path.of(request.get("out").toString());require(!target.startsWith(parent)&&!parent.startsWith(target),"Replay output must be physically separate from its parent");
            }else request=SquidpyWorkflowContracts.validate(original,source.getParent());
            root=newOutput(Path.of(request.get("out").toString()));Files.createDirectory(root.resolve("inputs"));Files.createDirectory(root.resolve("worker"));
            Files.write(root.resolve("source-request.json"),raw,StandardOpenOption.CREATE_NEW);write(root.resolve("canonical-request.json"),request);
            var binding=map("original",original,"canonical",request,"source_base",source.getParent().toString());
            if(parent!=null){binding.put("replay_source_canonical",parentRequest);binding.put("replay_analysis_address",parent.toString());}
            write(root.resolve("request-binding.json"),binding);write(root.resolve("producer.json"),SquidpyWorkflowAudit.producer());SquidpyWorkflowRuntime.sources(root);
            var manifest=map("schema_version",1,"workflow",SquidpyWorkflowContracts.PROFILE,"mode",parent==null?"RUN":"REPLAY","results_admitted",false);
            if(parent!=null){
                manifest.put("replay_parent_manifest",parentSeal);
                for(String name:List.of("manifest.json","original-inventory.json","input-inventory.json"))Files.copy(parent.resolve(name),root.resolve("replay-parent-"+name));
            }
            SquidpyWorkflowRuntime.execute(root,request,manifest,parent);
            Files.createDirectory(root.resolve("admitted"));var summary=SquidpyWorkflowAudit.summary(root,request,true);write(root.resolve("summary.json"),summary);
            var originals=map();for(Object value:list(summary.get("original_export_files"))){String name=relative(value.toString());originals.put(name,pin(root.resolve("worker/out").resolve(name)));}
            write(root.resolve("original-inventory.json"),originals);
            if(parent!=null){
                SquidpyWorkflowAudit.verify(parent);require(same(pin(parent.resolve("manifest.json")),parentSeal),"Replay parent changed during re-execution");
                var before=semanticExports(read(parent.resolve("original-inventory.json")));var after=semanticExports(originals);boolean equal=same(before,after);
                write(root.resolve("replay-comparison.json"),map("typed_semantic_exports_byte_identical",equal,"parent",before,"replay",after,"h5ad_transport_bytes_retained_but_not_compared",true,"independent_refit",false));
                require(equal||Boolean.TRUE.equals(object(request.get("admission")).get("allow_replay_differences")),"Re-executed original typed scientific exports differ; preserve this capture and inspect replay-comparison.json. Explicit predeclared allow_replay_differences is required to admit differences");
            }
            text(root.resolve("WARNINGS.md"),"# WARNINGS AND REQUIRED ACTIONS\n\n"+SquidpyWorkflowContracts.SCOPE+"\n\n"+SquidpyWorkflowContracts.ACTION+"\n\n"+resultWarnings(summary)+"\nOriginal returned objects, row/column/identifier order, source warnings and missing values remain in worker/out and worker/report.json. The admitted typed views label every withheld probability explicitly; an original undefined cell is never silently treated as a valid estimate. Runtime/source hashes are not host authentication or OS RAM/job quotas. Read the complete request and source/runtime manifests before downstream use.\n"+originalMessages(root));
            write(root.resolve("analysis-status.json"),map("status","completed-with-warnings","exit_code",0,"results_admitted",true,"required_action",SquidpyWorkflowContracts.ACTION,"worker_report_scope","Original API-return phase; final admission is recorded here and in COMPLETED.json"));
            manifest.put("results_admitted",true);manifest.put("inventory",inventory(root,request));write(root.resolve("manifest.json"),manifest);
            write(root.resolve("COMPLETED.json"),map("schema_version",1,"manifest_sha256",pin(root.resolve("manifest.json")).get("sha256"),"authentication",false));SquidpyWorkflowAudit.verify(root);
            emit(err,resultWarnings(summary)+"Read "+root.resolve("WARNINGS.md"));
            emit(out,"Completed original Squidpy "+request.get("operation")+"; finite result units="+summary.get("finite_result_rows")+", no-result units="+summary.get("no_result_rows")+", withheld probability cells="+summary.get("withheld_probability_cells")+"; "+root);return 0;
        }catch(SquidpyWorkflowContracts.Unavailable error){return failed(root,error,3,err);}
        catch(LinkageError error){return failed(root,new IOException("Producer classes could not link; restore the complete exact project JAR",error),2,err);}
        catch(Exception error){return failed(root,error,2,err);}
    }
    private static Map<String,Object> semanticExports(Map<String,Object> raw){var result=new TreeMap<String,Object>();for(var entry:raw.entrySet())if(!entry.getKey().endsWith("/original-state.h5ad"))result.put(entry.getKey(),entry.getValue());return result;}
    private static int failed(Path root,Exception error,int code,PrintStream err){
        String status=code==3?"SKIP3: DEPENDENT ANALYSIS SKIPPED":"FAIL2: NO COMPLETE RESULT";
        if(root!=null)try{
            for(String name:List.of("COMPLETED.json","manifest.json","WARNINGS.md","analysis-status.json"))if(Files.exists(root.resolve(name),LinkOption.NOFOLLOW_LINKS))Files.move(root.resolve(name),root.resolve("unadmitted-"+name));
            if(Files.exists(root.resolve("admitted"),LinkOption.NOFOLLOW_LINKS))Files.move(root.resolve("admitted"),root.resolve("unadmitted-exports"));
            write(root.resolve("FAILURE.json"),map("status",status,"exit_code",code,"results_admitted",false,"cause",error.toString()));
            write(root.resolve("analysis-status.json"),map("status",status,"exit_code",code,"results_admitted",false,"cause",error.toString(),"required_action",SquidpyWorkflowContracts.ACTION));
            text(root.resolve("WARNINGS.md"),"# "+status+"\n\n"+error+"\n\n"+SquidpyWorkflowContracts.ACTION+"\n"+originalMessages(root));
        }catch(Exception diagnostic){emit(err,"Additional failure-journal problem: "+diagnostic);}
        emit(err,status+": "+error+"\n"+SquidpyWorkflowContracts.ACTION+(root==null?"":"\nRead "+root.resolve("WARNINGS.md")));return code;
    }
    private static String originalMessages(Path root)throws IOException {
        var result=new StringBuilder("\n## Original runtime messages\n\nThe excerpts below retain the first8192 bytes per log. Read the complete logs and typed warning records in worker/report.json for source/import/adapter diagnostic distinctions.\n");
        for(String name:List.of("worker/stdout.log","worker/stderr.log")){
            Path file=root.resolve(name);if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))continue;byte[] excerpt;try(var in=Files.newInputStream(file)){excerpt=in.readNBytes(8192);}if(excerpt.length==0)continue;
            result.append("\n### ").append(name).append(" (excerpt)\n\n");for(String line:new String(excerpt,StandardCharsets.UTF_8).split("\\R",-1))result.append("    ").append(line).append('\n');
        }return result.toString();
    }
    static String resultWarnings(Map<String,Object> summary)throws IOException {
        return "SCIENTIFIC ADMISSION: finite result units="+number(summary,"finite_result_rows")+", no-result units="+number(summary,"no_result_rows")+", withheld probability cells="+number(summary,"withheld_probability_cells")+", source identifier changes="+number(summary,"identifier_changes")+", recorded runtime/source/adapter warnings="+number(summary,"recorded_warning_count")+". Missing/undefined and tainted adjusted probabilities are not estimates; original data and conditions remain retained. Read analysis-status.json for final Java admission; worker/report.json records the original API-return phase.\n";
    }
    private static void emit(PrintStream out,String message){out.writeBytes((message+System.lineSeparator()).getBytes(StandardCharsets.UTF_8));out.flush();}
    static String help(){return "SquidpyWorkflowCli --request UTF8_JSON | --verify CAPTURE | --help\nRUN declares exact local Python/Squidpy/dependency editions, original inputs/options, explicit result policies and resource bounds. REPLAY={schema_version:1,mode:REPLAY,analysis:PARENT,out:NEW} reruns the original tool on captured inputs. VERIFY is offline and does not refit Python models.\n"+SquidpyWorkflowContracts.SCOPE+"\n"+SquidpyWorkflowContracts.ACTION;}
}
