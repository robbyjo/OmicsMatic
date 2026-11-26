/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.assembly.MerquryBoundary;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;

/** Replays the exact original Java histogram helper on complete independently checked histograms. */
final class MerquryCutoffs {
    private MerquryCutoffs() { }
    /** Explicit native operation; this does not relabel a failed full-ploidy CLI report. */
    static Map<String,Object> replayNative(Path captured,MerquryRequest.Spec spec,Runnable cancellation)throws IOException {
        var actual=MerquryPreparationAudit.databases(captured,spec,cancellation);var receipts=new ArrayList<Object>();
        try(var paths=Files.newDirectoryStream(captured.resolve("engine/work"),"*.filt")) {
            var files=new ArrayList<Path>();paths.forEach(files::add);files.sort(Comparator.comparing(Path::toString));
            for(Path path:files) {
                cancellation.run();String prefix=path.getFileName().toString().replaceFirst("\\.filt$","");var counts=actual.get(prefix+".meryl");require(counts!=null,"Missing complete native cutoff database: "+prefix);
                var expected=new TreeMap<Long,Long>();for(long value:counts.values())expected.merge(value,1L,Math::addExact);
                var observed=new TreeMap<Long,Long>();var bins=new ArrayList<MerquryBoundary.Bin>();Path input=captured.resolve("engine/work/"+prefix+".hist");require(Files.size(input)<=spec.capacity().outputBytes(),"Complete native cutoff histogram exceeds capacity");
                try(var reader=Files.newBufferedReader(input,StandardCharsets.US_ASCII)) {
                    for(String line=reader.readLine();line!=null;line=reader.readLine()) {
                        cancellation.run();String[] f=line.trim().split("\\s+",-1);require(f.length==2,"Malformed complete native cutoff histogram");long depth=MerquryTables.positive(f[0]),distinct=MerquryTables.positive(f[1]);
                        require(depth<=Integer.MAX_VALUE&&observed.putIfAbsent(depth,distinct)==null,"Original cutoff int32 depth domain exceeded or repeated histogram bin");bins.add(new MerquryBoundary.Bin((int)depth,distinct));
                    }
                }
                require(expected.equals(observed),"Native cutoff histogram differs from full independently decoded database counts");var result=MerquryBoundary.calculate(bins,cancellation);String cutoff=Integer.toString(result.effectiveMinimumDepth());
                List<String> saved=Files.readAllLines(captured.resolve("engine/work/"+prefix+".hist.boundary.tsv"),StandardCharsets.US_ASCII);
                require(saved.equals(List.of("operation\tfirst_valley_found\teffective_minimum_depth","native-effective-minimum-depth\t"+result.firstValleyFound()+"\t"+cutoff))&&Files.readString(path,StandardCharsets.US_ASCII).trim().equals(cutoff),"Saved native effective-depth operation differs from full source-equation replay");
                require(!Files.exists(captured.resolve("engine/work/"+prefix+".hist.ploidy"),LinkOption.NOFOLLOW_LINKS),"Native boundary profile must not masquerade as the original full-ploidy report");
                receipts.add(Map.of("prefix",prefix,"cutoff",cutoff,"complete_bins",bins.size(),"first_valley_found",result.firstValleyFound()));
            }
        }
        population(receipts,spec,actual);
        return Map.of("status","PASS_ALL_COMPLETE_NATIVE_EFFECTIVE_DEPTH_REPLAYS","thresholds",receipts,"original_full_ploidy_formatter_executed",false,"full_workflow_admitted",false);
    }
    static Map<String,Object> replay(Path captured,Path newScratch,MerquryRequest.Spec spec,Path java,
            Map<String,String> environment,NextflowProcess.Check integrity,Runnable cancellation)throws IOException {
        directory(captured);require(!Files.exists(newScratch,LinkOption.NOFOLLOW_LINKS),"Use NEW owned cutoff inspection paths");Files.createDirectory(newScratch);
        var actual=MerquryPreparationAudit.databases(captured,spec,cancellation);var receipts=new ArrayList<Object>();int number=0;
        try(var entries=Files.newDirectoryStream(captured.resolve("engine/work"),"*.filt")) {
            var files=new ArrayList<Path>();entries.forEach(files::add);files.sort(Comparator.comparing(Path::toString));
            for(Path path:files) {
                cancellation.run();String prefix=path.getFileName().toString().replaceFirst("\\.filt$","");var counts=actual.get(prefix+".meryl");require(counts!=null,"Missing complete unfiltered database for automatic threshold: "+prefix);
                var expected=new TreeMap<Long,Long>();for(long n:counts.values())expected.merge(n,1L,Math::addExact);
                Path original=captured.resolve("engine/work/"+prefix+".hist");require(Files.size(original)<=spec.capacity().outputBytes(),"Complete original histogram exceeds byte capacity");
                var observed=new TreeMap<Long,Long>();
                for(String line:Files.readAllLines(original,StandardCharsets.US_ASCII)) {
                    cancellation.run();String[] f=line.trim().split("\\s+",-1);require(f.length==2,"Malformed complete original threshold histogram");long multiplicity=MerquryTables.positive(f[0]),keys=MerquryTables.positive(f[1]);require(observed.putIfAbsent(multiplicity,keys)==null,"Repeated histogram bin");
                }
                require(expected.equals(observed),"Automatic threshold histogram differs from complete original Meryl database counts");
                Path stage=Files.createDirectory(newScratch.resolve("threshold-"+number++));Path input=stage.resolve("input.hist");Files.copy(original,input);Pin histogram=pin(input,spec.capacity().outputBytes(),cancellation);require(histogram.equals(pin(original,spec.capacity().outputBytes(),cancellation)),"Full threshold histogram changed during copy");
                Path helper=captured.resolve("sources/merqury/eval/kmerHistToPloidyDepth.jar");Pin helperPin=pin(helper,16777216,cancellation);
                var process=NextflowProcess.executeSession(List.of(java.toString(),"-jar","-Xmx1g",helper.toString(),input.toString()),environment,stage.resolve("process"),stage,newScratch,spec.process(),integrity,cancellation);
                require(process.exitCode()==0&&process.ownedLiveAfterCleanup().isEmpty(),"Original threshold replay did not complete cleanly");
                String replay=utf8(bytes(process.stdout(),1048576)),saved=utf8(bytes(captured.resolve("engine/work/"+prefix+".hist.ploidy"),1048576));
                require(replay.equals(saved),"Saved automatic ploidy/threshold report differs from exact original deterministic helper replay");
                String[] lines=replay.split("\\R");require(lines.length>=2,"Original threshold helper did not define a boundary");String[] second=lines[1].trim().split("\\s+");require(second.length>=3,"Original threshold boundary row malformed");String cutoff=Files.readString(path,StandardCharsets.US_ASCII).trim();require(cutoff.equals(second[second.length-1]),"Original recorded filter cutoff differs from complete histogram helper replay");
                require(helperPin.equals(pin(helper,16777216,cancellation))&&histogram.equals(pin(input,spec.capacity().outputBytes(),cancellation))&&histogram.equals(pin(original,spec.capacity().outputBytes(),cancellation)),"Threshold source/helper/input changed during replay");
                receipts.add(Map.of("prefix",prefix,"complete_bins",expected.size(),"cutoff",cutoff,"histogram",histogram,"original_helper",helperPin));
            }
        }
        population(receipts,spec,actual);
        return Map.of("status","PASS_ALL_COMPLETE_ORIGINAL_HISTOGRAM_HELPER_REPLAYS","thresholds",receipts,"scientific_model_refit",false,"full_workflow_admitted",false);
    }
    private static void population(List<Object> receipts,MerquryRequest.Spec spec,Map<String,Map<String,Long>> databases)throws IOException {
        var expected=new TreeSet<String>();
        if(spec.mode().equals("evaluate"))expected.add("reads");
        if(spec.mode().equals("build-hapmers")) {
            if(Boolean.TRUE.equals(spec.settings().filterParents()))expected.addAll(List.of("mat_not_pat","pat_not_mat"));
            if(databases.containsKey("child.meryl"))expected.addAll(List.of("mat.inherited","pat.inherited","shrd.inherited"));
        }
        var actual=new TreeSet<String>();for(Object item:receipts)require(actual.add(text(object(item).get("prefix"))),"Repeated automatic cutoff role");require(actual.equals(expected),"Missing/extra complete automatic cutoff population");
    }
}
