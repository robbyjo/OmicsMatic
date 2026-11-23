/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.jlinalg.gprofiler.GprofilerJson;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.ScenicWorkflowFiles.*;

/** Prepared original GRN/CTX plus native activity: RUN, complete refit VERIFY and REPLAY. */
public final class ScenicToolsCli {
    private ScenicToolsCli(){ }
    public static void main(String[] args){int code=run(args,System.out,System.err);if(code!=0)System.exit(code);}
    static int run(String[] args,PrintStream out,PrintStream error) {
        Path root=null;long started=System.nanoTime();
        try {
            if(args.length==1&&args[0].equals("--help")){out.println(help());return 0;}
            if(args.length==2&&args[0].equals("--inspect")){ScenicWorkflowAudit.inspect(Path.of(args[1]));out.println("Saved capture closure checked; no upstream fit repeated, no whole-workflow qualification claimed.");return 0;}
            require(args.length==2&&args[0].equals("--request"),"Use --request JSON, --inspect CAPTURE or --help; full VERIFY requires a new output witness in its request");
            Path source=Path.of(args[1]).toAbsolutePath().normalize();byte[] raw=bytes(source,16_777_216);var original=object(GprofilerJson.read(raw));
            String base=ScenicWorkflowContracts.address(source.getParent()),style=ScenicWorkflowContracts.style();
            String mode=text(original.get("mode"),"mode");Map<String,Object> request;Path parent=null;Map<String,Object> parentSeal=null;
            if(Set.of("VERIFY","REPLAY").contains(mode)){
                keys(original,Set.of("schema_version","mode","analysis","out","python"));integer(original.get("schema_version"),1,1);
                parent=ScenicWorkflowAudit.existing(Path.of(ScenicWorkflowContracts.resolve(base,text(original.get("analysis"),"analysis"),style)));
                request=new LinkedHashMap<>(ScenicWorkflowAudit.inspect(parent));parentSeal=pin(parent.resolve("manifest.json"));
                request.put("out",ScenicWorkflowContracts.resolve(base,text(original.get("out"),"fresh witness output"),style));
                if(original.containsKey("python"))request.put("python",ScenicWorkflowContracts.resolve(base,text(original.get("python"),"python"),style));
                Path target=Path.of(request.get("out").toString());require(!target.startsWith(parent)&&!parent.startsWith(target),"Fresh VERIFY/REPLAY output must be disjoint from the read-only physical parent");
            }else request=ScenicWorkflowContracts.validate(original,base,style);
            root=newOutput(Path.of(request.get("out").toString()));Files.createDirectory(root.resolve("worker"));
            Files.write(root.resolve("source-request.json"),raw,StandardOpenOption.CREATE_NEW);write(root.resolve("canonical-request.json"),request);
            write(root.resolve("request-binding.json"),map("source_base",base,"path_style",style,"mode",mode,"source_request",original,"canonical_request",request));
            var producer=ScenicWorkflowSource.producer();write(root.resolve("producer.json"),producer);ScenicWorkflowRuntime.sources(root);
            text(root.resolve("WARNINGS.md"),"# SCENIC analysis scope and warnings\n\n"+ScenicWorkflowContracts.SCOPE+"\n\n"+ScenicWorkflowContracts.ACTION+"\n\nOriginal target/module failures prevent completion. Source placeholder zeros, nonfinite values and ineligible fields have explicit diagnostics. An eligible finite zero is a numerical value, not biological absence. Uniform or all-zero expression can still yield positive ranked activity; numeric eligibility is not cell QC or biological validity. Full VERIFY/REPLAY requires the same recorded runtime closure and physical paths.\n\n"+ScenicWorkflowContracts.ADMISSION+"\n");
            var process=map();ScenicWorkflowRuntime.execute(root,request,process,parent);
            long deadline=started+TimeUnit.SECONDS.toNanos(ScenicWorkflowContracts.cap(request,"timeout_seconds"));
            var activity=ScenicWorkflowActivity.compute(root,request,deadline);ScenicWorkflowAudit.fieldOutcomes(root,request);ScenicWorkflowSource.sameProducer(producer);
            String observed=ScenicWorkflowAudit.outcomeWarnings(root,activity);if(!observed.isEmpty()){Files.writeString(root.resolve("WARNINGS.md"),"\n## Observed source outcomes\n\n"+observed,StandardCharsets.UTF_8,StandardOpenOption.APPEND);error.println(observed);}
            boolean admitted=ScenicWorkflowAudit.eligibleFields(activity)>0;
            var status=map("schema_version",1,"profile",ScenicWorkflowContracts.PROFILE,"status",ScenicWorkflowContracts.COMPLETE,"capture_complete",true,"results_admitted",admitted,"original_call_returned",true,"native_activity",activity,
                "eligible_activity_fields",ScenicWorkflowAudit.eligibleFields(activity),"unreportable_activity_fields",number(activity,"ineligible_fields"),"admission_scope",ScenicWorkflowContracts.ADMISSION);
            if(parent!=null){
                require(same(parentSeal,pin(parent.resolve("manifest.json")))&&same(read(parent.resolve("manifest.json")),inventory(parent,read(parent.resolve("canonical-request.json")))),"Read-only parent changed during fresh computation");
                ScenicWorkflowAudit.compare(parent,root);
                Files.createDirectory(root.resolve("parent-proof"));
                for(String name:List.of("manifest.json","COMPLETED.json","canonical-request.json","input-inventory.json","producer.json"))Files.copy(parent.resolve(name),root.resolve("parent-proof/"+name));
                write(root.resolve("fresh-comparison.json"),map("mode",mode,"parent_address",ScenicWorkflowContracts.address(parent),"parent_manifest_identity",parentSeal,"all_declared_scientific_outputs_equal",true,
                    "original_GRN_and_CTX_reexecuted",true,"native_activity_recomputed",true,"parent_modified",false,"comparison_scope","Complete saved scientific exports compared after fresh original GRN/CTX and native activity; raw warnings/tracebacks retained separately. Same exact producer and runtime closure required."));
            }
            ScenicWorkflowAudit.unchangedBindings(root,request);write(root.resolve("analysis-status.json"),status);require(System.nanoTime()<=deadline,"Complete workflow exceeded its deadline; no completion admitted");
            var manifest=inventory(root,request);ScenicWorkflowAudit.namespace(root,request,manifest);write(root.resolve("manifest.json"),manifest);
            write(root.resolve("COMPLETED.json"),map("schema_version",1,"status",ScenicWorkflowContracts.COMPLETE,"capture_complete",true,"results_admitted",admitted,"manifest_identity",pin(root.resolve("manifest.json"))));
            out.println("Completed scoped SCENIC "+mode+" capture: "+root+". Read WARNINGS.md, analysis-status.json and every activity eligibility flag. VERIFY repeats the original fits and native activity; --inspect checks saved closure only.");return 0;
        }catch(Exception failure){
            int code=failure instanceof ScenicWorkflowContracts.Unavailable?3:2;
            if(root!=null)try{retain(root,code,failure);}catch(Exception retention){error.println("Capture retention warning: "+retention);}
            error.println((code==3?"SKIP3: DEPENDENT ANALYSIS SKIPPED: ":"FAIL2: NO COMPLETE RESULT: ")+failure+"\n"+ScenicWorkflowContracts.ACTION);return code;
        }
    }
    private static void retain(Path root,int code,Exception failure)throws IOException {
        for(String name:List.of("COMPLETED.json","manifest.json","analysis-status.json"))if(Files.exists(root.resolve(name),LinkOption.NOFOLLOW_LINKS))Files.move(root.resolve(name),root.resolve("retained-partial-"+name));
        write(root.resolve("analysis-status.json"),map("schema_version",1,"profile",ScenicWorkflowContracts.PROFILE,"status",code==3?"SKIP3_DEPENDENT_ANALYSIS_SKIPPED":"FAIL2_NO_COMPLETE_RESULT","results_admitted",false,"error",failure.toString()));
        write(root.resolve("FAILURE.json"),map("complete_result",false,"error",failure.toString(),"required_action",ScenicWorkflowContracts.ACTION));
        if(Files.exists(root.resolve("WARNINGS.md")))Files.move(root.resolve("WARNINGS.md"),root.resolve("retained-initial-WARNINGS.md"));
        text(root.resolve("WARNINGS.md"),(code==3?"# SKIP3: dependent analysis skipped":"# FAIL2: no complete result")+"\n\n"+failure+"\n\n## Required action\n\n"+ScenicWorkflowContracts.ACTION+"\n\nEarlier scope warnings remain in retained-initial-WARNINGS.md when present.\n");
    }
    private static String help(){return "scenic-tools --request JSON | --inspect CAPTURE | --help\nSchema1 RUN requires engine=upstream-and-native, profile="+ScenicWorkflowContracts.PROFILE+", operation=NETWORK, python, out, inputs{fit_expression,activity_expression,tfs,annotations,databases:[{name,file}]}, options{seed,...}. Literal cell-by-gene UTF-8 TSV: header cell followed by unique gene names. Explicit Linux x86_64 source-bound Python runtime required; no silent installation or reference download. Separate fitting/activity matrices are preserved; no downsampling or normalization is inferred. VERIFY and REPLAY requests require analysis and a NEW out witness; both actually repeat all original GRN/CTX calls and native activity, leaving the parent read-only. --inspect checks saved closure only.\n"+ScenicWorkflowContracts.SCOPE+"\n"+ScenicWorkflowContracts.ADMISSION+"\n"+ScenicWorkflowContracts.ACTION;}
}
