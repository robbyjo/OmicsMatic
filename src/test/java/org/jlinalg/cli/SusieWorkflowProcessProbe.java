/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Explicit synthetic lifetime controls only; never a SuSiE model or an admissible scientific capture. */
public final class SusieWorkflowProcessProbe {
    private SusieWorkflowProcessProbe() { }
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Require NEW probe output and exact Rscript path");
        Path root=Path.of(args[0]).toAbsolutePath();if(Files.exists(root))throw new IOException("Use NEW probe output");Files.createDirectories(root);
        Path executable=Path.of(args[1]).toAbsolutePath();var records=new ArrayList<Object>();
        records.add(probe(root.resolve("timeout"),executable,true));records.add(probe(root.resolve("output-cap"),executable,false));
        json(root.resolve("COMPLETED.json"),Map.of("status","PASS_SYNTHETIC_SUSIE_OWNED_LIFETIME_TIMEOUT_OUTPUT_CAP_AND_DESCENDANT_CLEANUP",
            "scientific_models_run",false,"source_overlaid_in_a_scientific_analysis",false,"controls",records));
        System.out.println("PASS synthetic owned lifetime controls; no scientific model was run");
    }
    private static Map<String,Object> probe(Path root,Path rscript,boolean timeout)throws Exception {
        Path worker=root.resolve("worker"),source=root.resolve("source");Files.createDirectories(worker);Files.createDirectory(source);
        String code=timeout?
            "writeLines('Sys.sleep(60)', 'child.R')\nchild<-file.path(getwd(),'child.R')\nexe<-file.path(R.home('bin'),'Rscript.exe')\nsystem2(exe,c('--vanilla',shQuote(child)),wait=FALSE,stdout='child.stdout',stderr='child.stderr')\nSys.sleep(60)\n":
            "writeBin(as.raw(rep(1L,100000)), 'over-cap.bin')\nSys.sleep(60)\n";
        Files.writeString(source.resolve("susie_workflow.R"),code,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        var request=new LinkedHashMap<String,Object>();request.put("profile","SuSiE-0.14.2-R4.6.1");request.put("rscript",rscript.toString());request.put("r_libraries",List.of(root.toString()));
        request.put("timeout_seconds",timeout?1L:30L);request.put("maximum_output_bytes",timeout?1_000_000L:512L);
        var manifest=new LinkedHashMap<String,Object>();String error=null;
        try{SusieWorkflowRuntime.run(root,root,request,manifest);}catch(IOException failure){error=failure.toString();}
        if(error==null||!error.contains(timeout?"timed out":"maximum_output_bytes"))throw new IOException("Wrong lifetime refusal: "+error);
        var cleanup=list(manifest.get("owned_process_cleanup"),"owned cleanup");if(cleanup.isEmpty())throw new IOException("Missing owned cleanup receipt");
        for(Object item:cleanup)if(!Boolean.FALSE.equals(object(item,"process").get("alive_after_cleanup")))throw new IOException("Owned process remains alive");
        if(timeout&&cleanup.size()<2)throw new IOException("Synthetic descendant was not observed before timeout");
        json(root.resolve("RAW-PROCESS-MANIFEST.json"),manifest);
        var result=new LinkedHashMap<String,Object>();result.put("control",timeout?"timeout-and-child":"output-cap");result.put("expected_refusal",error);
        result.put("cleanup",cleanup);result.put("scientific_models_run",false);result.put("original_source_guard_invoked",false);result.put("complete_scientific_result",false);
        json(root.resolve("CONTROL.json"),result);return result;
    }
}
