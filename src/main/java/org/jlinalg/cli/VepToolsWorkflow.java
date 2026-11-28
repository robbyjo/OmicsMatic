/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Original VEP calls with requested flags, unchanged complete inputs and explicit fresh index generation. */
final class VepToolsWorkflow {
    private VepToolsWorkflow() { }
    static Map<String,Object> run(Path root,Map<String,Object> r,VepToolsInputs.Prepared inputs,VepToolsRuntime.Prepared runtime,Map<String,Object> execution)throws IOException,InterruptedException {
        VepToolsRuntime.health(root,runtime,r,execution);var reference=inputs.reference();for(Path path:List.of(reference.fasta(),reference.fai()))VepToolsRuntime.portable(path);if(reference.cache()!=null)VepToolsRuntime.portable(reference.cache());
        if(reference.annotation()!=null)index(root,"transcripts",reference.annotation(),"gff",runtime,r,execution);
        for(var custom:inputs.custom())if(!text(custom,"format").equals("bigwig"))index(root,"custom-"+text(custom,"name"),root.resolve(text(custom,"file")),text(custom,"format"),runtime,r,execution);
        long resourceBytes=reference.external()==null?0:reference.external().bytes();try(var files=Files.walk(root.resolve("inputs"))){for(Path p:files.filter(Files::isRegularFile).toList())if(!p.equals(inputs.input())){long size=Files.size(p);if(size>VepToolsRequest.number(r,"maximum_resource_bytes")-resourceBytes)throw new IOException("Complete resource plus generated-index capacity exceeded");resourceBytes+=size;}}var inputHashes=VepToolsCli.hashes(root.resolve("inputs"));
        var results=new LinkedHashMap<String,Object>();
        for(Object value:list(r.get("analyses"),"analyses")){
            var analysis=object(value,"analysis");String id=text(analysis,"id");var options=object(analysis.get("options"),"options");Path directory=Files.createDirectory(root.resolve("worker/"+id));List<String> command=command(root,inputs,runtime,r,options,directory);VepToolsRuntime.execute(root,directory,id,command,runtime,r,execution);
            boolean summary=Set.of("most_severe","summary").contains(options.get("transcript_mode"));var report=VepToolsTables.render(inputs.input(),directory.resolve("original.vcf"),root.resolve("results/"+id),r,summary,false);results.put(id,report);VepToolsRuntime.capacity(root,r,false,new long[1]);
        }
        if(!inputHashes.equals(VepToolsCli.hashes(root.resolve("inputs"))))throw new IOException("Captured input/reference/custom data or generated indexes changed during original VEP execution");return results;
    }
    private static void index(Path root,String id,Path data,String format,VepToolsRuntime.Prepared runtime,Map<String,Object> r,Map<String,Object> execution)throws IOException,InterruptedException {
        VepToolsRuntime.portable(data);Path expected=Path.of(data+".tbi");if(Files.exists(expected,LinkOption.NOFOLLOW_LINKS))throw new IOException("Preserve existing owned index; generated-index paths must be new");Path directory=Files.createDirectory(root.resolve("worker/index-"+id));VepToolsRuntime.execute(root,directory,"index-"+id,List.of(runtime.environment().resolve("bin/tabix").toString(),"-p",format,data.toString()),runtime,r,execution);if(!Files.isRegularFile(expected,LinkOption.NOFOLLOW_LINKS)||Files.size(expected)==0||Files.size(expected)>VepToolsRequest.number(r,"maximum_resource_bytes"))throw new IOException("Exact source tabix did not produce a complete bounded new index");
    }
    static List<String> command(Path root,VepToolsInputs.Prepared inputs,VepToolsRuntime.Prepared runtime,Map<String,Object> r,Map<String,Object> options,Path directory)throws IOException {
        var ref=inputs.reference();List<String> args=new ArrayList<>(List.of("--format","vcf","--vcf","--allele_number","--safe","--no_stats","--input_file",inputs.input().toString(),"--output_file",directory.resolve("original.vcf").toString(),"--warning_file",directory.resolve("warnings.txt").toString(),"--species",ref.species(),"--assembly",ref.assembly(),"--fasta",ref.fasta().toString(),"--buffer_size",Long.toString(VepToolsRequest.number(r,"buffer_size"))));
        // Original105 --offline enables --cache even with --gtf/--gff. Supplied
        // transcript annotations instead use the source's default database=0.
        if(ref.cache()!=null)args.addAll(List.of("--offline","--cache","--cache_version","105","--dir_cache",ref.cache().toString()));else args.addAll(List.of("--"+ref.kind(),ref.annotation().toString()));
        if(VepToolsRequest.number(r,"threads")>1)args.addAll(List.of("--fork",Long.toString(VepToolsRequest.number(r,"threads"))));String mode=text(options,"transcript_mode");if(!mode.equals("all"))args.add("--"+mode);
        for(String key:VepToolsRequest.BOOLEANS)if(Boolean.TRUE.equals(options.get(key)))args.add("--"+key);
        for(String key:List.of("sift","polyphen"))if(!text(options,key).equals("none"))args.addAll(List.of("--"+key,text(options,key)));
        for(String key:List.of("distance","max_sv_size"))args.addAll(List.of("--"+key,Long.toString(VepToolsRequest.number(options,key))));
        if(options.containsKey("pick_order"))args.addAll(List.of("--pick_order",String.join(",",strings(options.get("pick_order"),"pick_order",1,10))));
        for(var custom:inputs.custom()){
            Path file=root.resolve(text(custom,"file"));VepToolsRuntime.portable(file);List<String> fields=new ArrayList<>(List.of(file.toString(),text(custom,"name"),text(custom,"format"),text(custom,"type"),bool(custom,"coordinates",false)?"1":"0"));for(Object item:list(custom.get("fields"),"fields"))fields.add((String)item);args.addAll(List.of("--custom",String.join(",",fields)));
        }
        return runtime.command(args);
    }
}
