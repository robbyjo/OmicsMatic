/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import static org.jlinalg.cli.NextflowFiles.*;

/** Portable source/input/output resolution from one completely verified engine attempt. */
final class NfcoreRnasplice104Files {
    final Path attempt;
    final NfcoreValidationBundle.Verified verified;
    final NextflowCapture.Snapshot capture;
    final Map<?,?> outputs;
    final String producer;
    NfcoreRnasplice104Files(NfcoreValidationBundle.Verified verified)throws IOException{
        this.verified=verified;attempt=verified.attempt();capture=NextflowCapture.decode(json(attempt.resolve("capture.json"),67108864),new NextflowCapture.Limits(1000000,1000000000000L,1000000000000L));outputs=object(object(json(attempt.resolve("outputs.json"),67108864)).get("members"));producer=text(object(json(attempt.resolve("session.json"),67108864)).get("producer_root"));NfcoreRnasplice104Request.path(producer);
    }
    String alias(String name)throws IOException{String value=capture.aliases().get(name);if(value==null)throw new IOException("Missing complete RNA captured alias: "+name);return value;}
    NextflowCapture.Item input(String name)throws IOException{
        member(name);for(var root:capture.roots()){String prefix=root.key()+"/"+root.leaf();if(name.equals(prefix)||name.startsWith(prefix+"/")){var value=root.items().get(name.equals(prefix)?"":name.substring(prefix.length()+1));if(value==null)break;return value;}}throw new IOException("Missing exact captured RNA member: "+name);
    }
    Path blob(Pin pin)throws IOException{Path path=attempt.resolve("objects").resolve(pin.sha256());if(!pin(path,pin.bytes(),()->{}).equals(pin))throw new IOException("Complete RNA blob bytes changed");return path;}
    Path output(String name)throws IOException{member(name);if(!outputs.containsKey(name))throw new IOException("Missing required complete-cohort original RNA output file: "+name+". No sample/output subset is admitted.");Map<?,?> row=object(outputs.get(name));if(!"file".equals(row.get("kind")))throw new IOException("Missing required original RNA output file: "+name);return blob(fingerprint(row.get("pin"),1000000000000L));}
    Map<String,Object> verifyMaterialization(Map<?,?> index,NfcoreRnasplice104Request.Request request,String adapterProducer)throws IOException{
        keys(index,"schema_version","files","directories");integer(index.get("schema_version"),1,1);if(!producer.equals(adapterProducer+"/engine")||capture.roots().size()!=1||!capture.aliases().keySet().equals(java.util.Set.of("bundle","workflow","engine","plugins")))throw new IOException("Typed RNA complete single-root source/capture relation differs");String bundle=alias("bundle");if(!bundle.equals("r0/materialized")||!alias("workflow").equals(bundle+"/upstream")||!alias("engine").equals(bundle+"/engine")||!alias("plugins").equals(bundle+"/plugins"))throw new IOException("Typed RNA nested aliases differ");var root=capture.roots().get(0);Map<?,?> expected=object(index.get("files"));var foundFiles=new HashSet<String>();var foundDirectories=new HashSet<String>();
        for(var row:root.items().entrySet()){String name=row.getKey();var item=row.getValue();if(item.kind().equals("directory")){foundDirectories.add(name);continue;}if(!item.kind().equals("file"))throw new IOException("Linked/unassessable RNA materialization member: "+name);Map<?,?> before=object(expected.get(name));keys(before,"pin","mode");if(!fingerprint(before.get("pin"),1000000000000L).equals(item.original().pin())||integer(before.get("mode"),0,0777)!=item.mode())throw new IOException("RNA captured materialization differs: "+name);foundFiles.add(name);}
        if(!foundFiles.equals(expected.keySet())||!foundDirectories.equals(new HashSet<>(strings(index.get("directories")))))throw new IOException("Incomplete/extra RNA materialization census");
        for(var entry:NfcoreRnasplice104Profile.sourceFiles().entrySet()){var item=input(bundle+"/upstream/"+entry.getKey());Map<?,?> row=entry.getValue();if(!item.kind().equals("file")||!item.original().pin().equals(NfcoreRnasplice104Profile.sourcePin(row))||item.mode()!=(Boolean.TRUE.equals(row.get("executable"))?0755:0644))throw new IOException("Original RNA source/Git mode differs: "+entry.getKey());}
        var layout=org.jlinalg.workflow.NfcoreInputBindings.plan(producer,adapterProducer+"/materialized");String sheet=utf8(bytes(blob(input(bundle+"/samplesheet.csv").original().pin()),67108864));if(!sheet.equals(NfcoreRnasplice104Prepare.samplesheet(request,layout))||!utf8(bytes(blob(input(bundle+"/contrastsheet.csv").original().pin()),67108864)).equals(NfcoreRnasplice104Prepare.contrastsheet(request)))throw new IOException("Complete RNA cohort/contrast bindings differ from captured request");
        if(!utf8(bytes(blob(input(bundle+"/upstream/runtime/host.config").original().pin()),1048576)).equals(NfcoreRnasplice104Profile.overlay(producer,layout.capturedRoot(),request.executorCpus(),request.executorMemory()))||!utf8(bytes(blob(input(bundle+"/config/nfcore_custom.config").original().pin()),1048576)).equals(NfcoreRnasplice104Prepare.EXTERNAL))throw new IOException("RNA exact runtime-only/institutional configuration differs");
        for(var entry:NfcoreRnasplice104Profile.images().entrySet()){Pin image=fingerprint(object(entry.getValue()).get("pin"),5000000000L);if(!input(bundle+"/images/"+image.sha256()+".sif").original().pin().equals(image))throw new IOException("Original RNA source image differs: "+entry.getKey());}
        var report=new TreeMap<String,Object>();report.put("original_source_files",206);report.put("original_git_executable_files",29);report.put("source_images",31);report.put("source_image_aliases",72);report.put("biological_samples",request.samples().size());report.put("technical_libraries",NfcoreRnasplice104Prepare.reads(request).size());report.put("contrasts",request.contrasts().size());report.put("source_identifiers_repaired",false);return report;
    }
    /** Exact source BAM-list order; never sort it to manufacture biological pairing. */
    Map<String,Object> cohortAndPairing(NfcoreRnasplice104Request.Request request)throws IOException{
        var names=new TreeMap<String,String>();var groups=new TreeMap<String,java.util.Set<String>>();for(var sample:request.samples()){String name=sample.id()+"_sorted.bam";names.put(name,sample.id());output("star_salmon/"+name);groups.computeIfAbsent(sample.condition(),key->new HashSet<>()).add(sample.id());}
        var used=new HashSet<String>();for(var contrast:request.contrasts()){used.add(contrast.treatment());used.add(contrast.control());}var observed=new TreeMap<String,List<String>>();for(var group:groups.entrySet()){if(!used.contains(group.getKey()))continue;String raw=utf8(bytes(output("star_salmon/rmats/bamlist/"+group.getKey()+"_bamlist.txt"),67108864));if(!raw.endsWith("\n"))throw new IOException("Original RNA BAM list lacks its source line ending");raw=raw.substring(0,raw.length()-1);if(raw.endsWith("\r"))raw=raw.substring(0,raw.length()-1);if(raw.indexOf('\r')>=0||raw.indexOf('\n')>=0)throw new IOException("Unexpected original RNA BAM-list rows");var order=new java.util.ArrayList<String>();for(String value:raw.split(",",-1)){String id=names.get(value);if(id==null)throw new IOException("Unrecognized original source BAM-list entry; no sample inference: "+value);order.add(id);}if(order.size()!=new HashSet<>(order).size()||!new HashSet<>(order).equals(group.getValue()))throw new IOException("Original source BAM cohort differs from the complete biological request: "+group.getKey());observed.put(group.getKey(),List.copyOf(order));}
        var pairs=new TreeMap<String,Object>();for(var contrast:request.contrasts())if(request.rmatsPairedStatistics())pairs.put(contrast.id(),pairOrder(observed.get(contrast.treatment()),observed.get(contrast.control()),request.rmatsPairings().get(contrast.id()),contrast.id()).stream().map(pair->Map.of("treatment",pair.treatment(),"control",pair.control())).toList());
        return Map.of("source_BAM_order",observed,"rmats_paired_statistics",request.rmatsPairedStatistics(),"verified_biological_pairs",pairs,"BAM_order_changed",false);
    }
    static List<NfcoreRnasplice104Request.Pair> pairOrder(List<String> first,List<String> second,List<NfcoreRnasplice104Request.Pair> expected,String contrast)throws IOException{
        if(first.size()!=second.size())throw new IOException("Original paired-statistics source BAM lists have unequal group sizes");var actual=new java.util.ArrayList<NfcoreRnasplice104Request.Pair>();for(int i=0;i<first.size();i++)actual.add(new NfcoreRnasplice104Request.Pair(first.get(i),second.get(i)));if(!new HashSet<>(actual).equals(new HashSet<>(expected)))throw new IOException("Original source BAM arrival order does not represent the explicitly declared biological pairing for "+contrast+". No BAM reordering, sample relabelling, unpaired-model fallback or partial paired result is admitted. Preserve the original execution and use a workflow/recipe that represents the intended pairing.");return List.copyOf(actual);
    }
}
