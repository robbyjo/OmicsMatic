/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Every requested full original analysis, with no model, window, allele or gene reduction. */
final class SpliceAiToolsWorkflow {
    private SpliceAiToolsWorkflow(){ }
    static Map<String,Object> run(Path root,Map<String,Object> r,SpliceAiToolsInputs.Prepared inputs,SpliceAiToolsRuntime.Prepared runtime)throws IOException,InterruptedException{
        var results=new LinkedHashMap<String,Object>();
        for(Object value:list(r.get("analyses"),"analyses")){
            var analysis=object(value,"analysis");String id=text(analysis,"id");Path directory=Files.createDirectory(root.resolve("worker/"+id));var worker=new LinkedHashMap<String,Object>();worker.put("input",inputs.original().toString());worker.put("output",directory.resolve("original.vcf").toString());worker.put("reference",inputs.fasta().toString());worker.put("annotation",inputs.annotation());worker.put("distance",analysis.get("distance"));worker.put("mask",analysis.get("mask"));worker.put("threads",r.get("threads"));worker.put("report",directory.resolve("runtime-report.json").toString());json(directory.resolve("worker-request.json"),worker);Path script=root.resolve("source/worker.py");SpliceAiToolsRuntime.execute(root,directory,id,runtime.command(script,directory.resolve("worker-request.json")),runtime,r,new LinkedHashMap<>());runtime.report(directory,script,r);
            var report=SpliceAiToolsTables.render(inputs.original(),directory.resolve("original.vcf"),root.resolve("results/"+id),r,false);admit(report);results.put(id,report);SpliceAiToolsRuntime.capacity(root,r,false,new long[1]);
        }
        return results;
    }
    static void admit(Map<String,Object> report)throws IOException{if(((Number)report.get("partial_tuples")).longValue()!=0||((Number)report.get("nonfinite_score_annotations")).longValue()!=0||((Number)report.get("annotations_without_gene")).longValue()!=0)throw new IOException("Fresh original inference returned partial/nonfinite or gene-unassigned tuples; retain diagnostics, no complete prediction result");}
}
