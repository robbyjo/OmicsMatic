/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.ScdrsJson.*;

/** Typed execution of original historical scDRS CLI functions with complete saved output checks. */
public final class ScdrsToolsCli {
    private ScdrsToolsCli() { }
    static final String SCOPE="Historical scDRS1.0.0,1.0.1,1.0.2, explicit private Linux x86_64 Python3.10.16 dependency/source profiles. Original munge_gs, compute_score and perform_downstream CLI functions run unchanged. H5AD normalization/filter choices, matched-control scoring, covariates, proportion adjustment, group association/Geary heterogeneity, annotation correlation and gene prioritization are original upstream algorithms. Complete output values, IDs, shapes, source process completion and runtime invariance are checked. Optional native BH uses one trait/annotation family in this single dataset, with strict q<declared cutoff on literal exported group P values. Native numerical components are separate; saved verification does not independently refit LOESS, covariance regression, scores, empirical probabilities or neighbor graphs. Logical capacities are not OS RAM quotas. Source defaults and observed failures are retained. No paper settings, independent statistical replication, biological validity or producer authentication inferred.";
    static final String ACTION="Require exit0, result_admitted=true, COMPLETED.json and successful --verify before interpretation. Read WARNINGS.md, session.json, analysis-summary.json, inspection/input-contract.json, identifier-dispositions.tsv and original source logs. SKIP3: skip dependent analysis when exact permitted software/capabilities are absent; export the helper and restore/install named prerequisites where permitted, using NEW paths. FAIL2: NO RESULT ADMITTED for invalid input, integrity, process, capacity or unassessable/nonfinite output. Preserve all attempts and unadmitted-original outputs. Resolve the named cause and retry NEW paths; do not silently change versions, encode covariates, repair IDs/NaN/Inf, alter control counts or pool correction families. Hashes check integrity, not authenticity.";
    private static final Set<String> FINAL_FILES=Set.of("manifest.json","COMPLETED.json");
    public static void main(String[] args){System.exit(run(args,System.out,System.err));}
    public static int run(String[] args,PrintStream output,PrintStream error){
        Path out=null;ScdrsRequest.Request request=null;boolean owned=false;
        Map<String,Object> state=map("status","FAIL2_NO_SCDRS_RESULT_ADMITTED","result_admitted",false);
        try{
            if(args.length==1&&args[0].equals("--help")){output.println("scdrs-tools --request JSON | --verify BUNDLE | --export-runtime-helper NEW_DIRECTORY\n"+SCOPE+"\n"+ACTION);return 0;}
            if(args.length==2&&args[0].equals("--export-runtime-helper")){
                Path dir=Path.of(args[1]).toAbsolutePath().normalize();ScdrsFiles.newDirectory(dir);
                List<String> names=new ArrayList<>(List.of("prepare_scdrs_runtime.py","scdrs-workflow-lock.json","scdrs-1.0.2-LICENSE.txt","numpy-1.23.5-LICENSE.txt","statsmodels-0.13.5-LICENSE.txt"));
                Map<?,?> lock=object(SimpleJson.parse(new String(ScdrsRuntime.resource("scdrs-workflow-lock.json"),StandardCharsets.UTF_8)));
                for(Object item:list(lock.get("wheels"))){Map<?,?> w=object(item);if(text(w.get("delivery")).equals("bundled"))names.add(text(w.get("filename")));}
                for(String name:names)Files.write(dir.resolve(name),ScdrsRuntime.resource(name),StandardOpenOption.CREATE_NEW);
                Files.writeString(dir.resolve("WARNINGS.md"),"# Setup only; no analysis\n\n"+SCOPE+"\n\n**REQUIRED ACTION:** "+ACTION+"\n\nUse Linux Python3.11+ to run the helper. Select --version explicitly, NEW --out, and either --existing-environment or NEW native-Linux --environment-home. Install/download require explicit flags; --archive-dir supplies offline archives. Three small source-audited wheels are included beside the helper. No shared packages are changed.\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                output.println("Exported original scDRS setup helper/lock and source-audited wheels only.\n"+ACTION);return 0;
            }
            if(args.length==2&&args[0].equals("--verify")){verify(Path.of(args[1]).toAbsolutePath().normalize());output.println("PASS complete saved scDRS output contracts and explicit native BH families; no original-runtime refit or producer authentication.");return 0;}
            need(args.length==2&&args[0].equals("--request"),"Require --request JSON, --verify BUNDLE, --export-runtime-helper NEW_DIRECTORY or --help");
            Path recipe=ScdrsRuntime.canonical(Path.of(args[1]).toAbsolutePath().normalize(),false);
            Map<String,Object> recipePin=ScdrsRuntime.pin(recipe,1048576);request=ScdrsRequest.read(recipe);out=request.out();
            ScdrsFiles.protect(request);ScdrsFiles.newDirectory(out);owned=true;
            Files.writeString(out.resolve("WARNINGS.md"),"# scDRS warnings and required actions\n\n"+SCOPE+"\n\n**"+ACTION+"**\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            ScdrsJson.write(out.resolve("STARTED.json"),map("status","RUNNING_SCDRS_PREFLIGHT_ONLY","result_admitted",false));
            copy(recipe,out.resolve("request-original.json"),1048576);need(recipePin.equals(ScdrsRuntime.pin(recipe,1048576)),"Original request changed");
            ScdrsRuntime.Runtime runtime=ScdrsRuntime.read(request.profile(),request.version());
            Map<String,Object> profilePin=ScdrsRuntime.pin(request.profile(),67108864);copy(request.profile(),out.resolve("runtime-profile.json"),67108864);
            var inputs=new java.util.LinkedHashMap<String,Object>();var originalPins=new java.util.LinkedHashMap<String,Map<String,Object>>();long total=0;
            for(String role:List.of("h5ad","gene_sets","covariates","scores","gene_statistics")){
                Path original=request.inputs().get(role);if(original==null){inputs.put(role,null);continue;}
                ScdrsRuntime.canonical(original,false);Map<String,Object> pin=ScdrsRuntime.pin(original,request.limits().inputBytes());
                total=Math.addExact(total,(Long)pin.get("bytes"));need(total<=request.limits().inputBytes(),"Combined input byte capacity exceeded");
                String filename=original.getFileName().toString();ScdrsRuntime.member(filename);
                Path target=out.resolve("inputs").resolve(role).resolve(filename);Files.createDirectories(target.getParent());copy(original,target,request.limits().inputBytes());
                need(pin.equals(ScdrsRuntime.pin(target,request.limits().inputBytes())),"Input changed during byte-preserving staging");
                originalPins.put(role,pin);inputs.put(role,out.relativize(target).toString().replace('\\','/'));
            }
            ScdrsJson.write(out.resolve("input-source-pins.json"),originalPins);
            Map<?,?> originalRequest=object(ScdrsJson.read(recipe,1048576));
            var saved=new java.util.LinkedHashMap<String,Object>();for(var e:originalRequest.entrySet())saved.put(text(e.getKey()),e.getValue());
            saved.put("runtime_profile","runtime-profile.json");saved.put("output_dir",".");saved.put("inputs",inputs);
            ScdrsJson.write(out.resolve("request-for-verification.json"),saved);
            Path worker=out.resolve("source-worker.py");Files.write(worker,ScdrsRuntime.resource("run_scdrs_workflow.py"),StandardOpenOption.CREATE_NEW);
            ScdrsFiles.inventory(out,request.limits(),Set.of());
            int exit=ScdrsFiles.supervise(List.of(runtime.python().toString(),"-s","-B",worker.toString(),"--source",runtime.source().toString(),
                "--request",out.resolve("request-for-verification.json").toString(),"--out",out.toString()),out,request.limits());
            if(exit==3)throw new ScdrsRuntime.Unavailable("Original worker reports a missing dependency/capability; inspect source-worker-failure.json and source.stderr");
            need(exit==0,"Original workflow failed: exit"+exit+"; inspect retained source-worker-failure.json and source logs");
            ScdrsRequest.Request savedRequest=ScdrsRequest.read(out.resolve("request-for-verification.json"),true);
            Map<String,Object> audit=ScdrsAudit.inspect(savedRequest,out,true);ScdrsJson.write(out.resolve("analysis-summary.json"),audit);
            ScdrsRuntime.verify(runtime);
            need(profilePin.equals(ScdrsRuntime.pin(request.profile(),67108864))&&recipePin.equals(ScdrsRuntime.pin(recipe,1048576)),"Original profile/request changed during execution");
            for(var e:originalPins.entrySet())need(e.getValue().equals(ScdrsRuntime.pin(request.inputs().get(e.getKey()),request.limits().inputBytes())),"Original input changed during execution");
            need(audit.equals(ScdrsAudit.inspect(savedRequest,out,false)),"Saved output audit differs before admission");
            state=map("status","PASS_SCOPED_ORIGINAL_SCDRS_WORKFLOW","result_admitted",true,"version",request.version(),"operation",request.operation(),
                "source_commit",runtime.metadata().get("source_commit"),"runtime_unchanged",true,"whole_paper_reproduced",false,"producer_authenticated",false);
            ScdrsJson.write(out.resolve("session.json"),state);append(out,state,audit.get("warnings"));
            ScdrsJson.write(out.resolve("manifest.json"),map("schema_version",1L,"files",ScdrsFiles.inventory(out,request.limits(),FINAL_FILES)));
            ScdrsJson.write(out.resolve("COMPLETED.json"),map("status","COMPLETE_SCOPED_SCDRS_BUNDLE","result_admitted",true,"manifest",ScdrsRuntime.pin(out.resolve("manifest.json"),67108864)));
            output.println("PASS original scDRS workflow and complete output checks. Read "+out.resolve("WARNINGS.md")+" and run --verify.\n"+ACTION);return 0;
        }catch(ScdrsRuntime.Unavailable missing){state=map("status","SKIP3_SCDRS_DEPENDENT_ANALYSIS_UNAVAILABLE","result_admitted",false,"cause",missing.toString());error.println("SKIP3: "+missing+"\nREQUIRED ACTION: "+ACTION);}
        catch(LinkageError missing){state=map("status","SKIP3_SCDRS_JAVA_DEPENDENCY_UNAVAILABLE","result_admitted",false,"cause",missing.toString());error.println("SKIP3: "+missing+"\nREQUIRED ACTION: use the complete documented Java classpath; "+ACTION);}
        catch(Exception failure){state=map("status","FAIL2_NO_SCDRS_RESULT_ADMITTED","result_admitted",false,"cause",failure.toString());error.println("FAIL2: NO RESULT ADMITTED: "+failure+"\nREQUIRED ACTION: "+ACTION);}
        if(owned&&out!=null)try{
            if(Files.exists(out.resolve("COMPLETED.json")))Files.move(out.resolve("COMPLETED.json"),out.resolve("COMPLETED-unadmitted.json"));
            if(Files.exists(out.resolve("original")))Files.move(out.resolve("original"),out.resolve("unadmitted-original"));
            if(Files.exists(out.resolve("session.json")))Files.move(out.resolve("session.json"),out.resolve("session-before-finalization-failure.json"));
            ScdrsJson.write(out.resolve("session.json"),state);append(out,state,List.of());
            ScdrsJson.write(out.resolve("attempt-inventory.json"),map("files",ScdrsFiles.inventory(out,request.limits(),Set.of("attempt-inventory.json"))));
        }catch(Exception diagnostic){error.println("FAIL2 diagnostic finalization failed; preserve entire attempt: "+diagnostic);}
        return state.get("status").toString().startsWith("SKIP3")?3:2;
    }
    private static void copy(Path source,Path target,long maximum)throws IOException {
        try(var in=Files.newInputStream(source);var out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)){
            byte[] bytes=new byte[1048576];long total=0;for(int n;(n=in.read(bytes))!=-1;){total=Math.addExact(total,n);need(total<=maximum,"Staged copy grew beyond declared byte capacity");out.write(bytes,0,n);}
        }
    }
    private static void append(Path out,Map<String,Object> state,Object warnings)throws IOException {
        StringBuilder text=new StringBuilder("\n\n## Final disposition\n\n**").append(state.get("status")).append("; result_admitted=").append(state.get("result_admitted")).append("**\n\n");
        if(state.containsKey("cause"))text.append(state.get("cause")).append("\n\n");
        for(Object warning:list(warnings))text.append("* ").append(text(warning)).append('\n');
        text.append("\n**REQUIRED ACTION:** ").append(ACTION).append('\n');Files.writeString(out.resolve("WARNINGS.md"),text,StandardCharsets.UTF_8,StandardOpenOption.APPEND);
    }
    static void verify(Path root)throws IOException {
        ScdrsRuntime.canonical(root,true);Map<?,?> completed=object(ScdrsJson.read(root.resolve("COMPLETED.json"),1048576));
        keys(completed,"status","result_admitted","manifest");need(text(completed.get("status")).equals("COMPLETE_SCOPED_SCDRS_BUNDLE")&&bool(completed.get("result_admitted")),"No complete admitted scDRS result");
        need(object(completed.get("manifest")).equals(ScdrsRuntime.pin(root.resolve("manifest.json"),67108864)),"Saved manifest bytes differ");
        Map<?,?> manifest=object(ScdrsJson.read(root.resolve("manifest.json"),67108864));keys(manifest,"schema_version","files");integer(manifest.get("schema_version"),1,1);
        ScdrsRequest.Request r=ScdrsRequest.read(root.resolve("request-for-verification.json"),true);
        need(object(manifest.get("files")).equals(ScdrsFiles.inventory(root,r.limits(),FINAL_FILES)),"Complete saved file inventory/bytes differ");
        Map<?,?> profile=object(ScdrsJson.read(root.resolve("runtime-profile.json"),67108864));ScdrsRuntime.savedProfile(profile,r.version());
        need(ScdrsRuntime.digest(Files.readAllBytes(root.resolve("source-worker.py"))).equals(ScdrsRuntime.digest(ScdrsRuntime.resource("run_scdrs_workflow.py"))),"Saved source worker differs from packaged implementation");
        Map<?,?> session=object(ScdrsJson.read(root.resolve("session.json"),1048576));
        need(text(session.get("status")).equals("PASS_SCOPED_ORIGINAL_SCDRS_WORKFLOW")&&bool(session.get("result_admitted"))&&bool(session.get("runtime_unchanged"))&&!bool(session.get("whole_paper_reproduced"))&&!bool(session.get("producer_authenticated"))&&text(session.get("version")).equals(r.version())&&text(session.get("operation")).equals(r.operation())&&text(session.get("source_commit")).equals(text(profile.get("source_commit"))),"Saved session admits no matching scoped result");
        need(object(ScdrsJson.read(root.resolve("analysis-summary.json"),64000000)).equals(ScdrsAudit.inspect(r,root,false)),"Complete saved scientific output contracts differ");
        need(object(manifest.get("files")).equals(ScdrsFiles.inventory(root,r.limits(),FINAL_FILES)),"Saved files changed during verification");
    }
}
