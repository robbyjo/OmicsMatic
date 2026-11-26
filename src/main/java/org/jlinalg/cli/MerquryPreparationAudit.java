/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.assembly.*;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

/** Full preparation equations; automatic histogram cutoffs are separately replayed by MerquryCutoffs. */
final class MerquryPreparationAudit {
    private MerquryPreparationAudit() { }
    static Map<String,Map<String,Long>> databases(Path root,MerquryRequest.Spec spec,Runnable check)throws IOException {
        var result=new TreeMap<String,Map<String,Long>>();
        for(Object raw:list(json(root.resolve("exports/DATABASES.json"),16777216))) {
            check.run();var row=object(raw);keys(row,"database","table");String database=member(text(row.get("database"))),table=member(text(row.get("table")));
            require(database.startsWith("engine/work/")&&database.endsWith(".meryl")&&!database.substring(12).contains("/")&&table.startsWith("exports/")&&result.size()<spec.capacity().files(),"Unexpected complete Meryl export namespace");
            String name=database.substring("engine/work/".length());require(result.putIfAbsent(name,MerquryTables.counts(root.resolve(table),spec.settings().k(),spec.capacity().outputBytes(),spec.capacity().distinctKmers(),check))==null,"Repeated complete Meryl database role");
        }
        return result;
    }
    static Map<String,Object> run(Path root,MerquryRequest.Spec spec,Runnable check)throws IOException {
        var actual=databases(root,spec,check);var expected=new TreeMap<String,Map<String,Long>>();var effective=object(json(root.resolve("effective.json"),1048576));long bases=0,records=0,decodedBytes=0;int k=spec.settings().k();
        if(spec.mode().equals("count-kmers")) {
            var merged=new TreeMap<String,Long>();
            for(Object raw:list(effective.get("inputs"))) {
                var row=object(raw);Path path=root.resolve(member(text(row.get("captured"))));var cap=spec.capacity();MerquryFastx.Result counted;
                try(var input=Files.newInputStream(path)){counted=MerquryFastx.count(input,k,null,new MerquryFastx.Limits(cap.decodedBytes(),cap.lineCharacters(),cap.recordBases(),cap.records(),cap.bases(),cap.distinctKmers()),check);}
                bases=Math.addExact(bases,counted.bases());records=Math.addExact(records,counted.sequences().size());decodedBytes=Math.addExact(decodedBytes,counted.decodedBytes());require(bases<=cap.bases()&&records<=cap.records()&&decodedBytes<=cap.decodedBytes(),"Combined complete count input population exceeds capacity");
                for(var e:counted.counts().entrySet()){check.run();merged.merge(e.getKey(),e.getValue(),Math::addExact);require(merged.size()<=cap.distinctKmers(),"Combined complete distinct key capacity exceeded");}
            }
            expected.put("reads.k"+k+".meryl",merged);
        }else {
            require(spec.mode().equals("build-hapmers"),"Preparation audit supports count-kmers or build-hapmers");
            var mat=required(actual,"mat.meryl");var pat=required(actual,"pat.meryl");expected.put("mat.meryl",mat);expected.put("pat.meryl",pat);
            var matOnly=MerquryAssessment.difference(mat,pat,k,check);var patOnly=MerquryAssessment.difference(pat,mat,k,check);
            expected.put("mat_not_pat.meryl",matOnly);expected.put("pat_not_mat.meryl",patOnly);
            if(Boolean.TRUE.equals(spec.settings().filterParents())) {
                matOnly=filter(root,"mat_not_pat",matOnly,expected,k,check);patOnly=filter(root,"pat_not_mat",patOnly,expected,k,check);
            }
            expected.put("mat.only.meryl",matOnly);expected.put("pat.only.meryl",patOnly);
            var shared=MerquryAssessment.intersect(mat,pat,k,check);expected.put("mat_and_pat.meryl",shared);expected.put("shrd.meryl",shared);
            if(actual.containsKey("child.meryl")) {
                var child=required(actual,"child.meryl");expected.put("child.meryl",child);
                var inheritedMat=MerquryAssessment.intersect(child,matOnly,k,check);var inheritedPat=MerquryAssessment.intersect(child,patOnly,k,check);var inheritedShared=MerquryAssessment.intersect(child,shared,k,check);
                expected.put("child_and_mat.only.meryl",inheritedMat);expected.put("child_and_pat.only.meryl",inheritedPat);expected.put("child_and_shrd.meryl",inheritedShared);
                expected.put("mat.inherited.meryl",inheritedMat);expected.put("pat.inherited.meryl",inheritedPat);expected.put("shrd.inherited.meryl",inheritedShared);
                expected.put("mat.hapmer.meryl",filter(root,"mat.inherited",inheritedMat,expected,k,check));expected.put("pat.hapmer.meryl",filter(root,"pat.inherited",inheritedPat,expected,k,check));expected.put("shrd.filt.meryl",filter(root,"shrd.inherited",inheritedShared,expected,k,check));
                var union=MerquryAssessment.unionSum(List.of(inheritedMat,inheritedPat,inheritedShared),k,check);expected.put("child.inherited.meryl",union);expected.put("read.only.meryl",MerquryAssessment.difference(child,union,k,check));
            }
            if(actual.containsKey("child.meryl")) {
                var histogram=new TreeMap<String,Long>();
                for(var group:Map.of("child-only","read.only.meryl","mat","mat.inherited.meryl","pat","pat.inherited.meryl","shared","shrd.inherited.meryl").entrySet())
                    for(long count:required(actual,group.getValue()).values())histogram.merge(group.getKey()+"\t"+count,1L,Math::addExact);
                MerquryEvaluationAudit.histogram(root.resolve("engine/work/inherited_hapmers.hist"),"k-mer\tkmer_multiplicity\tCount",histogram,check);
                var cutoffs=new ArrayList<String>();for(String name:List.of("mat","pat","shrd"))cutoffs.add((name.equals("shrd")?"shared":name)+"\t"+Files.readString(root.resolve("engine/work/"+name+".inherited.filt")).trim());
                require(MerquryEvaluationAudit.lines(root.resolve("engine/work/cutoffs.txt"),1048576).equals(cutoffs),"Missing/changed complete parental plot cutoffs");MerquryEvaluationAudit.plots(root.resolve("engine/work"),"inherited_hapmers",check);
            }
        }
        require(expected.keySet().equals(actual.keySet()),"Missing/extra complete source preparation database: expected="+expected.keySet()+", actual="+actual.keySet());long keys=0;
        for(var entry:expected.entrySet()){check.run();MerquryTables.equalCounts(entry.getValue(),required(actual,entry.getKey()),spec.mode()+" "+entry.getKey());keys=Math.addExact(keys,entry.getValue().size());}
        return map("status","PASS_COMPLETE_PREPARATION_COUNT_EQUATIONS_ONLY","mode",spec.mode(),"databases",expected.size(),"complete_keys",keys,"bases",bases,"records",records,"automatic_cutoffs_recomputed",false,"fresh_binary_decode_required",true,"full_workflow_admitted",false);
    }
    private static Map<String,Long> required(Map<String,Map<String,Long>> rows,String name)throws IOException {var table=rows.get(name);require(table!=null,"Missing complete original Meryl database: "+name);return table;}
    private static Map<String,Long> filter(Path root,String prefix,Map<String,Long> original,Map<String,Map<String,Long>> expected,int k,Runnable check)throws IOException {
        String raw=Files.readString(root.resolve("engine/work/"+prefix+".filt")).trim();require(raw.matches("0|[1-9][0-9]{0,18}"),"Original automatic cutoff missing/malformed; no replacement threshold");long cutoff=Long.parseLong(raw);
        var filtered=MerquryAssessment.greaterThan(original,cutoff,k,check);expected.put(prefix+".gt"+raw+".meryl",filtered);return filtered;
    }
}
