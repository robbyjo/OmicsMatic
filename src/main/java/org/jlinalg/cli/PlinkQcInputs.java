/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.PlinkBedDataset;

/** Exact identities, explicit QC reference populations, and verified original BED export. */
final class PlinkQcInputs {
    private PlinkQcInputs() { }
    record Id(String fid,String iid) { }
    record Sample(int ordinal,Id original,Id staged,String father,String mother,String sex,String phenotype,
            boolean founder,boolean initialInclude,String group) { }
    record Variant(int ordinal,String original,String staged,String chromosome,String cm,String bp,
            String a1,String a2,boolean autosomal,boolean resolved) { }
    record Prepared(Path prefix,Map<Id,Sample> samples,Map<String,Variant> variants,
            Set<Id> initialSamples,Set<String> initialVariants,Map<String,Path> inputs,
            Map<String,String> inputHashes,Map<String,String> stagedHashes,PlinkGlmInputs.Bounds bounds) { }

    static Prepared prepare(Path prefix,Path cohort,Path directory,String unresolved,PlinkGlmInputs.Bounds bounds) throws Exception {
        var paths=new LinkedHashMap<String,Path>();for(String suffix:List.of("bed","bim","fam"))paths.put(suffix,Path.of(prefix+"."+suffix));paths.put("samples",cohort);
        for(Path p:paths.values())if(!Files.isRegularFile(p))throw new IOException("Missing QC input: "+p);
        if(Files.size(paths.get("bed"))>bounds.bed() || Files.size(cohort)>bounds.cohort())throw new IOException("QC BED/sample input exceeds its declared byte bound");
        long metadata=Math.addExact(Files.size(paths.get("bim")),Files.size(paths.get("fam")));if(metadata>bounds.metadata())throw new IOException("QC BIM/FAM exceeds --maximum-metadata-mib");
        long estimate=Math.multiplyExact(Math.addExact(metadata,Files.size(cohort)),16);if(estimate>bounds.retained())throw new IOException("QC initial metadata estimate exceeds --maximum-retained-mib");
        var hashes=new LinkedHashMap<String,String>();for(var entry:paths.entrySet())hashes.put(entry.getKey(),FollowupSupport.hash(entry.getValue()));
        var rawVariants=new ArrayList<String[]>();var ids=new LinkedHashSet<String>();var finished=new HashSet<String>();String last="";
        try(var stream=PlinkGlmInputs.limited(paths.get("bim"),bounds.metadata())) {
            for(String line;(line=PlinkGlmInputs.line(stream,bounds.line()))!=null;){if(line.isBlank())continue;String[] f=fields(line,"BIM");
                if(rawVariants.size()>=bounds.variants() || !ids.add(f[1]))throw new IOException("QC duplicate BIM ID or --maximum-variants exceeded: "+f[1]);
                if(f[4].contains(",")||f[5].contains(","))throw new IOException("QC requires a biallelic BED pair,not comma-separated alleles");
                if(Math.addExact(estimate,Math.multiplyExact(ids.size(),4096L))>bounds.retained())throw new IOException("QC identity metadata estimate exceeds --maximum-retained-mib");
                String chromosome=chromosome(f[0]);if(!chromosome.equals(last)){if(!last.isEmpty())finished.add(last);if(finished.contains(chromosome))throw new IOException("Original QC requires contiguous chromosome blocks;prepare a separate sorted fileset");last=chromosome;}
                rawVariants.add(f);
            }
        }
        var limits=new PlinkBedDataset.Limits(bounds.samples(),bounds.variants(),bounds.variants(),bounds.metadata(),bounds.retained(),bounds.line());
        try(var data=PlinkBedDataset.open(prefix,ids,limits,PlinkBedDataset.AllelePolicy.QC_ALLOW_MISSING)) {
            if(Math.multiplyExact((long)data.samples().size(),data.variantCount())>bounds.cells())throw new IOException("QC validation exceeds --maximum-cells");
            if(Math.addExact(estimate,Math.addExact(Math.multiplyExact(data.samples().size(),4096L),Math.multiplyExact(data.variantCount(),4096)))>bounds.retained())throw new IOException("QC combined metadata estimate exceeds --maximum-retained-mib");
            var fam=new ArrayList<String[]>();try(var stream=PlinkGlmInputs.limited(paths.get("fam"),bounds.metadata())){for(String line;(line=PlinkGlmInputs.line(stream,bounds.line()))!=null;)if(!line.isBlank())fam.add(fields(line,"FAM"));}
            if(fam.size()!=data.samples().size())throw new IOException("FAM changed during QC admission");
            for(int i=0;i<fam.size();i++)if(!fam.get(i)[0].equals(data.samples().get(i).familyId())||!fam.get(i)[1].equals(data.samples().get(i).individualId()))throw new IOException("FAM identity order changed during QC admission");
            var index=new LinkedHashMap<Id,Integer>();for(int i=0;i<fam.size();i++)index.put(new Id(fam.get(i)[0],fam.get(i)[1]),i);
            String[][] selections=new String[fam.size()][];
            try(var stream=PlinkGlmInputs.limited(cohort,bounds.cohort())) {
                if(!"fid\tiid\tinclude\tgroup".equals(PlinkGlmInputs.line(stream,bounds.line())))throw new IOException("QC samples header must be exact TSV: fid, iid, include, group");
                int count=0;for(String line;(line=PlinkGlmInputs.line(stream,bounds.line()))!=null;){if(++count>bounds.samples())throw new IOException("QC sample table row bound exceeded");String[] f=line.split("\t",-1);if(f.length!=4)throw new IOException("QC sample row requires four tab-separated fields");Integer ordinal=index.get(new Id(f[0],f[1]));if(ordinal==null || selections[ordinal]!=null)throw new IOException("Unknown or duplicate QC FID/IID pair");if(!Set.of("yes","no").contains(f[2]) || !Set.of("control","case","unassigned").contains(f[3]))throw new IOException("QC include must be yes/no and group control/case/unassigned");selections[ordinal]=f;}
            }
            var families=new LinkedHashMap<String,String>();var individuals=new LinkedHashMap<Id,String>();
            for(String[] f:fam){families.computeIfAbsent(f[0],x->"F"+(families.size()+1));for(int i=1;i<=3;i++)if(!f[i].equals("0"))individuals.computeIfAbsent(new Id(f[0],f[i]),x->"I"+(individuals.size()+1));}
            var samples=new LinkedHashMap<Id,Sample>();var initialSamples=new LinkedHashSet<Id>();
            try(var writer=Files.newBufferedWriter(directory.resolve("input.fam"))) {
                for(int i=0;i<fam.size();i++){if(selections[i]==null)throw new IOException("QC sample table must name every FAM pair exactly once");String[] f=fam.get(i);Id original=new Id(f[0],f[1]),staged=new Id(families.get(f[0]),individuals.get(original));boolean founder=f[2].equals("0")&&f[3].equals("0");var sample=new Sample(i,original,staged,f[2],f[3],f[4],f[5],founder,selections[i][2].equals("yes"),selections[i][3]);samples.put(staged,sample);if(sample.initialInclude())initialSamples.add(staged);
                    String father=f[2].equals("0")?"0":individuals.get(new Id(f[0],f[2])),mother=f[3].equals("0")?"0":individuals.get(new Id(f[0],f[3]));writer.write(staged.fid()+" "+staged.iid()+" "+father+" "+mother+" "+f[4]+" -9\n");}
            }
            var variants=new LinkedHashMap<String,Variant>();var initialVariants=new LinkedHashSet<String>();int ordinal=0;
            try(var writer=Files.newBufferedWriter(directory.resolve("input.bim"))) {
                for(var variant:data.selectedVariants()) {
                    String[] f=rawVariants.get(ordinal);if(!variant.id().equals(f[1]) || variant.ordinal()!=ordinal)throw new IOException("BIM changed during QC admission");data.readPackedRow(variant);
                    boolean autosomal=chromosome(f[0]).matches("[1-9]|1[0-9]|2[0-2]"),resolved=variant.resolvedAlleles();String staged="V"+(ordinal+1);
                    if(autosomal&&!resolved&&unresolved.equals("error"))throw new IOException("Unresolved autosomal allele labels require an explicit exclude/retain-with-warning policy: "+variant.id());
                    variants.put(staged,new Variant(ordinal,f[1],staged,f[0],f[2],f[3],f[4],f[5],autosomal,resolved));if(autosomal&&(resolved||!unresolved.equals("exclude")))initialVariants.add(staged);
                    writer.write(chromosome(f[0])+" "+staged+" "+f[2]+" "+f[3]+" "+f[4]+" "+f[5]+"\n");ordinal++;
                }
            }
            if(initialSamples.isEmpty()||initialVariants.isEmpty())throw new IOException("No initially included samples/autosomal markers remain under the explicit input policies");
            Files.copy(paths.get("bed"),directory.resolve("input.bed"));if(!hashes.get("bed").equals(FollowupSupport.hash(directory.resolve("input.bed"))))throw new IOException("QC BED snapshot differs");PlinkGlmInputs.verify(paths,hashes);
            var stagedHashes=new LinkedHashMap<String,String>();for(String name:List.of("input.bed","input.bim","input.fam"))stagedHashes.put(name,FollowupSupport.hash(directory.resolve(name)));
            return new Prepared(prefix,samples,variants,initialSamples,initialVariants,paths,hashes,stagedHashes,bounds);
        }
    }

    static Set<Id> population(Prepared p,Set<Id> current,String scope,boolean controls) throws IOException {
        var result=new LinkedHashSet<Id>();for(var id:current){var sample=p.samples().get(id);if((!scope.equals("founders")||sample.founder())&&(!controls||sample.group().equals("control")))result.add(id);}
        if(result.isEmpty())throw new IOException("The requested QC reference population is empty (scope="+scope+", controls="+controls+")");return result;
    }
    static void keep(Path path,Set<Id> values) throws IOException {try(var writer=Files.newBufferedWriter(path)){for(Id id:values)writer.write(id.fid()+"\t"+id.iid()+"\n");}}
    static void snps(Path path,Set<String> values) throws IOException {try(var writer=Files.newBufferedWriter(path)){for(String value:values)writer.write(value+"\n");}}
    static void verify(Prepared p,Path directory) throws Exception {PlinkGlmInputs.verify(p.inputs(),p.inputHashes());for(var e:p.stagedHashes().entrySet())if(!e.getValue().equals(FollowupSupport.hash(directory.resolve(e.getKey()))))throw new IOException("Staged QC input changed: "+e.getKey());}

    /** Validate every retained genotype, then publish original identities/metadata and upstream-coded alleles. */
    static long export(Prepared p,Path finalPrefix,Set<Id> keptSamples,Set<String> keptVariants,Path output) throws Exception {
        var outputVariants=new LinkedHashMap<String,String[]>();try(var stream=PlinkGlmInputs.limited(Path.of(finalPrefix+".bim"),p.bounds().metadata())){for(String line;(line=PlinkGlmInputs.line(stream,p.bounds().line()))!=null;){String[] f=fields(line,"final BIM");if(!keptVariants.contains(f[1])||outputVariants.putIfAbsent(f[1],f)!=null)throw new IOException("Unknown/duplicate final QC variant");}}
        if(!outputVariants.keySet().equals(keptVariants))throw new IOException("Final QC variant family differs from the retained decision mask");
        var originalIds=new LinkedHashSet<String>();for(String id:keptVariants)originalIds.add(p.variants().get(id).original());var limits=new PlinkBedDataset.Limits(p.bounds().samples(),p.bounds().variants(),p.bounds().variants(),p.bounds().metadata(),p.bounds().retained(),p.bounds().line());
        try(var source=PlinkBedDataset.open(p.prefix(),originalIds,limits,PlinkBedDataset.AllelePolicy.QC_ALLOW_MISSING);var result=PlinkBedDataset.open(finalPrefix,keptVariants,limits,PlinkBedDataset.AllelePolicy.QC_ALLOW_MISSING)) {
            if(result.samples().size()!=keptSamples.size()||result.variantCount()!=keptVariants.size())throw new IOException("Final QC dimensions differ from decision masks");
            var sourceVariants=new LinkedHashMap<String,PlinkBedDataset.Variant>();for(var v:source.selectedVariants())sourceVariants.put(v.id(),v);int[] sampleIndices=new int[result.samples().size()];var seen=new HashSet<Id>();
            for(int i=0;i<result.samples().size();i++){var sample=result.samples().get(i);Id id=new Id(sample.familyId(),sample.individualId());if(!keptSamples.contains(id)||!seen.add(id))throw new IOException("Unknown or duplicate final QC sample");sampleIndices[i]=p.samples().get(id).ordinal();}
            long cells=Math.multiplyExact((long)keptSamples.size(),keptVariants.size());if(cells>p.bounds().cells())throw new IOException("Final QC genotype validation exceeds --maximum-cells");
            try(var bim=Files.newBufferedWriter(output.resolve("genotypes.bim"));var fam=Files.newBufferedWriter(output.resolve("genotypes.fam"))) {
                for(var sample:result.samples()){var original=p.samples().get(new Id(sample.familyId(),sample.individualId()));fam.write(original.original().fid()+" "+original.original().iid()+" "+original.father()+" "+original.mother()+" "+original.sex()+" "+original.phenotype()+"\n");}
                for(var variant:result.selectedVariants()){var original=p.variants().get(variant.id());boolean same=allele(original.a1()).equals(allele(variant.allele1()))&&allele(original.a2()).equals(allele(variant.allele2()));boolean reversed=allele(original.a1()).equals(allele(variant.allele2()))&&allele(original.a2()).equals(allele(variant.allele1()));if(!same&&!reversed)throw new IOException("Original QC changed allele identity: "+original.original());
                    byte[] before=source.readPackedRow(sourceVariants.get(original.original())),after=result.readPackedRow(variant);
                    for(int i=0;i<sampleIndices.length;i++){int j=sampleIndices[i],expected=(before[j/4]>>>(2*(j%4)))&3,actual=(after[i/4]>>>(2*(i%4)))&3;if(!same&&reversed&&(expected==0||expected==3))expected=3-expected;if(actual!=expected)throw new IOException("QC changed a retained call at "+original.original()+"/"+p.samples().get(new Id(result.samples().get(i).familyId(),result.samples().get(i).individualId())).original());}
                    if(!chromosome(variant.chromosome()).equals(chromosome(original.chromosome()))||variant.position()!=Long.parseLong(original.bp()))throw new IOException("QC changed a variant coordinate");
                    bim.write(original.chromosome()+" "+original.original()+" "+original.cm()+" "+original.bp()+" "+variant.allele1()+" "+variant.allele2()+"\n");
                }
            }
            Files.copy(Path.of(finalPrefix+".bed"),output.resolve("genotypes.bed"));return cells;
        }
    }
    private static String allele(String value){return Set.of("0",".").contains(value)?"0":value;}
    private static String[] fields(String line,String kind) throws IOException {String[] f=line.strip().split("[ \\t]+");if(f.length!=6)throw new IOException(kind+" requires six fields");return f;}
    private static String chromosome(String text) throws IOException {
        if(text.matches("[0-9]|1[0-9]|2[0-6]"))return Integer.toString(Integer.parseInt(text));return switch(text){case "X"->"23";case "Y"->"24";case "XY"->"25";case "MT"->"26";default->throw new IOException("QC requires canonical human chromosome codes,found: "+text);};
    }
}
