/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.PlinkBedDataset;

/** Independent explicit fitted/AF populations and immutable declared marker panel. */
final class PlinkPcaInputs {
    private PlinkPcaInputs() { }
    record Calls(int referenceCalled,long countedAlleles,int basisCalled,int referenceMissing,int basisMissing) {
        double frequency(){return countedAlleles/(2.0*referenceCalled);}
    }
    record Prepared(PlinkQcInputs.Prepared bed,Set<PlinkQcInputs.Id> basis,Set<PlinkQcInputs.Id> reference,
            Set<String> panel,Map<String,Calls> calls,Path samplePath,Path panelPath,String sampleHash,String panelHash,
            Map<String,String> maskHashes) { }
    private record Roles(boolean fit,boolean reference) { }

    static Prepared prepare(Path prefix,Path samplePath,Path panelPath,Path directory,String unresolved,
            String missing,int minimumReferenceCalls,PlinkGlmInputs.Bounds bounds) throws Exception {
        if(!Set.of("error","upstream").contains(missing)||minimumReferenceCalls<2)throw new IOException("PCA requires explicit missing-genotypes error|upstream and minimum-reference-calls>=2");
        for(Path path:List.of(samplePath,panelPath))if(!Files.isRegularFile(path)||Files.size(path)>bounds.cohort())throw new IOException("Missing/oversized PCA sample/panel input: "+path);
        String sampleHash=FollowupSupport.hash(samplePath),panelHash=FollowupSupport.hash(panelPath);
        var roles=new LinkedHashMap<PlinkQcInputs.Id,Roles>();
        try(var stream=PlinkGlmInputs.limited(samplePath,bounds.cohort())) {
            if(!"fid\tiid\tfit\tfrequency_reference".equals(PlinkGlmInputs.line(stream,bounds.line())))throw new IOException("PCA sample header must be exact TSV: fid,iid,fit,frequency_reference");
            for(String line;(line=PlinkGlmInputs.line(stream,bounds.line()))!=null;) {
                if(roles.size()>=bounds.samples())throw new IOException("PCA sample role-table bound exceeded");String[] row=line.split("\t",-1);
                if(row.length!=4||!Set.of("yes","no").contains(row[2])||!Set.of("yes","no").contains(row[3]))throw new IOException("PCA sample row requires exact FID/IID and independent yes/no fit/frequency_reference flags");
                if(roles.putIfAbsent(new PlinkQcInputs.Id(row[0],row[1]),new Roles(row[2].equals("yes"),row[3].equals("yes")))!=null)throw new IOException("Duplicate PCA FID/IID pair");
            }
        }
        var requested=new LinkedHashSet<String>();
        try(var stream=PlinkGlmInputs.limited(panelPath,bounds.cohort())) {
            for(String line;(line=PlinkGlmInputs.line(stream,bounds.line()))!=null;) {
                if(requested.size()>=bounds.variants())throw new IOException("PCA declared panel row bound exceeded");
                if(line.isEmpty()||line.codePoints().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c))||!requested.add(line))throw new IOException("PCA panel must have one unique exact variant ID per nonblank line");
            }
        }
        if(requested.isEmpty())throw new IOException("PCA declared marker panel is empty");
        Path cohort=directory.resolve("pca-admission-samples.tsv");
        try(var writer=Files.newBufferedWriter(cohort)) {
            writer.write("fid\tiid\tinclude\tgroup\n");for(var entry:roles.entrySet())writer.write(entry.getKey().fid()+"\t"+entry.getKey().iid()+"\t"+(entry.getValue().fit()||entry.getValue().reference()?"yes":"no")+"\tunassigned\n");
        }
        var bed=PlinkQcInputs.prepare(prefix,cohort,directory,unresolved,bounds);
        var basis=new LinkedHashSet<PlinkQcInputs.Id>();var reference=new LinkedHashSet<PlinkQcInputs.Id>();
        for(var sample:bed.samples().values()) {
            Roles selected=roles.get(sample.original());if(selected==null)throw new IOException("PCA table must name every FAM pair exactly once");
            if(selected.fit())basis.add(sample.staged());if(selected.reference())reference.add(sample.staged());
        }
        if(basis.size()<2||reference.size()<minimumReferenceCalls)throw new IOException("PCA requires at least two fitted samples and the declared minimum frequency-reference samples");
        var panel=new LinkedHashSet<String>();
        for(var variant:bed.variants().values())if(requested.contains(variant.original())) {
            if(!bed.initialVariants().contains(variant.staged())||!variant.resolved())throw new IOException("PCA declared panel includes a nonautosomal/unresolved marker: "+variant.original());
            panel.add(variant.staged());
        }
        if(panel.size()!=requested.size())throw new IOException("PCA declared panel contains unknown marker identities");
        var referenceIndices=reference.stream().mapToInt(id->bed.samples().get(id).ordinal()).toArray();
        var basisIndices=basis.stream().mapToInt(id->bed.samples().get(id).ordinal()).toArray();
        var calls=new LinkedHashMap<String,Calls>();int[] sampleObserved=new int[basis.size()];
        var byOriginal=new LinkedHashMap<String,String>();for(String id:panel)byOriginal.put(bed.variants().get(id).original(),id);
        var limits=new PlinkBedDataset.Limits(bounds.samples(),bounds.variants(),bounds.variants(),bounds.metadata(),bounds.retained(),bounds.line());
        try(var data=PlinkBedDataset.open(prefix,byOriginal.keySet(),limits);var writer=Files.newBufferedWriter(directory.resolve("variant-reference.tsv"))) {
            writer.write("variant\tupstream_variant\tcounted_allele\tother_allele\tfrequency_reference_called\tfrequency_reference_missing\tcounted_alleles\tfrequency\tfitted_called\tfitted_missing\tstatus\n");
            int invalid=0;
            for(var variant:data.selectedVariants()) {
                byte[] row=data.readPackedRow(variant);int called=0,fitCalled=0;long alleleCount=0;
                for(int index:referenceIndices){int value=call(row,index);if(value>=0){called++;alleleCount+=value;}}
                for(int i=0;i<basisIndices.length;i++)if(call(row,basisIndices[i])>=0){fitCalled++;sampleObserved[i]++;}
                Calls counts=new Calls(called,alleleCount,fitCalled,referenceIndices.length-called,basisIndices.length-fitCalled);
                String state=called<minimumReferenceCalls?"INSUFFICIENT_REFERENCE_CALLS":alleleCount==0||alleleCount==2L*called?"ZERO_REFERENCE_VARIANCE":fitCalled==0?"NO_FITTED_CALLS":missing.equals("error")&&(counts.referenceMissing()>0||counts.basisMissing()>0)?"MISSING_CALLS_PROHIBITED":"ADMITTED";
                if(!state.equals("ADMITTED"))invalid++;
                String id=byOriginal.get(variant.id());calls.put(id,counts);
                writer.write(variant.id()+"\t"+id+"\t"+variant.allele1()+"\t"+variant.allele2()+"\t"+called+"\t"+counts.referenceMissing()+"\t"+alleleCount+"\t"+(called==0?"":counts.frequency())+"\t"+fitCalled+"\t"+counts.basisMissing()+"\t"+state+"\n");
            }
            if(invalid>0)throw new IOException(invalid+" PCA panel markers fail declared reference/missing/variance policies;see upstream/variant-reference.tsv.No marker was silently removed");
        }
        for(int i=0;i<sampleObserved.length;i++)if(sampleObserved[i]==0)throw new IOException("Fitted PCA sample has no observed declared-panel genotypes;review explicit sample/panel choices");
        PlinkQcInputs.keep(directory.resolve("pca-basis.keep"),basis);PlinkQcInputs.keep(directory.resolve("pca-frequency.keep"),reference);PlinkQcInputs.snps(directory.resolve("pca-panel.extract"),panel);
        var hashes=new LinkedHashMap<String,String>();for(String name:List.of("pca-basis.keep","pca-frequency.keep","pca-panel.extract"))hashes.put(name,FollowupSupport.hash(directory.resolve(name)));
        var result=new Prepared(bed,Set.copyOf(basis),Set.copyOf(reference),java.util.Collections.unmodifiableSet(panel),java.util.Collections.unmodifiableMap(calls),samplePath,panelPath,sampleHash,panelHash,Map.copyOf(hashes));
        verify(result,directory);return result;
    }
    static void verify(Prepared input,Path directory) throws Exception {
        PlinkQcInputs.verify(input.bed(),directory);
        if(!input.sampleHash().equals(FollowupSupport.hash(input.samplePath()))||!input.panelHash().equals(FollowupSupport.hash(input.panelPath())))throw new IOException("PCA source sample/panel changed during analysis");
        for(var entry:input.maskHashes().entrySet())if(!entry.getValue().equals(FollowupSupport.hash(directory.resolve(entry.getKey()))))throw new IOException("PCA staged basis/reference/panel mask changed");
    }
    static Map<String,PlinkPcaReports.Variant> variants(Prepared input) {
        var result=new LinkedHashMap<String,PlinkPcaReports.Variant>();for(String id:input.panel()){var v=input.bed().variants().get(id);result.put(id,new PlinkPcaReports.Variant(id,v.chromosome(),v.a1(),v.a2()));}return result;
    }
    private static int call(byte[] row,int sample){int code=(row[sample/4]>>>(2*(sample%4)))&3;return code==1?-1:code==0?2:code==2?1:0;}
}
