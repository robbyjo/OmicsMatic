/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.genetics.PlinkAssociations;
import org.jlinalg.genetics.PlinkBedDataset;

/** Validated, bounded, complete-case inputs for the original additive GLM workflow. */
final class PlinkGlmInputs {
    private PlinkGlmInputs() { }
    record Id(String fid,String iid) { }
    record Variant(String original,String staged,String chromosome,long position,
            String a1,String a2,int observed) { }
    record Prepared(Map<String,Variant> variants,Map<String,PlinkAssociations.Alleles> alleles,
            int samples,int eligible,int excluded,int cases,int controls,int missingSentinel,
            List<String> covariates,Map<String,String> inputHashes,Map<String,Path> inputs,
            Map<String,String> stagedHashes) { }
    record Bounds(int samples,int variants,long cells,long metadata,long retained,int line,long bed,long cohort) { }

    static Prepared prepare(Path prefix,Path cohort,Path directory,String model,String coding,
            String missing,List<String> covariates,Bounds bounds) throws Exception {
        Path bed=Path.of(prefix+".bed"),bim=Path.of(prefix+".bim"),fam=Path.of(prefix+".fam");
        var inputs=new LinkedHashMap<String,Path>();inputs.put("bed",bed);inputs.put("bim",bim);inputs.put("fam",fam);inputs.put("cohort",cohort);
        for (Path path:inputs.values()) if (!Files.isRegularFile(path)) throw new IOException("Missing input: "+path);
        if (Files.size(bed)>bounds.bed() || Files.size(cohort)>bounds.cohort()) throw new IOException("BED/cohort input exceeds its declared byte bound");
        if (Math.addExact(Files.size(bim),Files.size(fam))>bounds.metadata()) throw new IOException("BIM/FAM exceeds --maximum-metadata-mib");
        long initialEstimate=Math.multiplyExact(Math.addExact(Files.size(cohort),Math.addExact(Files.size(bim),Files.size(fam))),16);
        if (initialEstimate>bounds.retained()) throw new IOException("Initial metadata byte estimate exceeds --maximum-retained-mib");
        var hashes=new LinkedHashMap<String,String>();for (var entry:inputs.entrySet()) hashes.put(entry.getKey(),FollowupSupport.hash(entry.getValue()));
        var ids=new LinkedHashMap<String,Boolean>();
        try (InputStream stream=limited(bim,bounds.metadata())) {
            for (String line;(line=line(stream,bounds.line()))!=null;) {
                if (line.isBlank()) continue;String[] fields=fields(line,6,"BIM");
                if (ids.size()>=bounds.variants()) throw new IOException("BIM exceeds --maximum-variants");
                if (Math.addExact(initialEstimate,Math.multiplyExact(ids.size()+1L,4096))>bounds.retained()) throw new IOException("BIM identity metadata estimate exceeds --maximum-retained-mib");
                if (ids.putIfAbsent(fields[1],Boolean.TRUE)!=null) throw new IOException("Duplicate BIM variant: "+fields[1]);
            }
        }
        var limits=new PlinkBedDataset.Limits(bounds.samples(),bounds.variants(),bounds.variants(),bounds.metadata(),bounds.retained(),bounds.line());
        try (var data=PlinkBedDataset.open(prefix,ids.keySet(),limits)) {
            if (Math.multiplyExact(data.variantCount(),(long)data.samples().size())>bounds.cells()) throw new IOException("Sample-by-variant validation exceeds --maximum-cells");
            long estimated=Math.addExact(Math.multiplyExact(Files.size(cohort),16),Math.addExact(Math.multiplyExact((long)data.samples().size(),4096),Math.multiplyExact(data.variantCount(),4096)));
            if (estimated>bounds.retained()) throw new IOException("Conservative combined metadata estimate exceeds --maximum-retained-mib");
            var samples=data.samples();var known=new LinkedHashMap<Id,Integer>();
            for (int i=0;i<samples.size();i++) known.put(new Id(samples.get(i).familyId(),samples.get(i).individualId()),i);
            String[][] rows=new String[samples.size()][];int eligible=0,excluded=0,cases=0,controls=0;boolean binary=!model.equals("linear");
            var observedValues=new HashSet<Double>();
            try (InputStream stream=limited(cohort,bounds.cohort())) {
                String header=line(stream,bounds.line());var names=new ArrayList<>(List.of("fid","iid","phenotype"));names.addAll(covariates);
                if (header==null || !Arrays.equals(header.split("\t",-1),names.toArray(String[]::new))) throw new IOException("Cohort header must be exact tab-separated fid, iid, phenotype, then the declared covariates in order");
                int count=0;
                for (String text;(text=line(stream,bounds.line()))!=null;) {
                    if (++count>bounds.samples()) throw new IOException("Cohort row bound exceeded");
                    String[] values=text.split("\t",-1);if (values.length!=names.size()) throw new IOException("Cohort row width differs at row "+count);
                    token(values[0],"cohort FID");token(values[1],"cohort IID");Id id=new Id(values[0],values[1]);Integer ordinal=known.get(id);
                    if (ordinal==null || rows[ordinal]!=null) throw new IOException("Unknown or duplicate exact cohort FID/IID: "+id);
                    for (int i=2;i<values.length;i++) {
                        if (values[i].equals("NA")) continue;double value=numeric(values[i]);observedValues.add(value);
                        if (i==2) {
                            if (binary) {
                                double control=coding.equals("0/1")?0:1;
                                if (value!=control && value!=control+1) throw new IOException("Phenotype does not match --binary-coding at "+id);
                            }
                        }
                    }
                    rows[ordinal]=values;
                }
            }
            boolean[] included=new boolean[samples.size()];
            for (int i=0;i<rows.length;i++) {
                if (rows[i]==null) throw new IOException("Cohort must name every FAM sample exactly once; absent: "+samples.get(i).familyId()+"/"+samples.get(i).individualId());
                included[i]=!Arrays.asList(rows[i]).subList(2,rows[i].length).contains("NA");
                if (included[i]) {
                    eligible++;
                    if (binary) { double control=coding.equals("0/1")?0:1;if (numeric(rows[i][2])==control) controls++;else cases++; }
                } else excluded++;
            }
            if (excluded>0 && missing.equals("error")) throw new IOException("Cohort has "+excluded+" missing phenotype/covariate rows; --missing error prohibits exclusion");
            if (eligible<=covariates.size()+2) throw new IOException("Too few complete-case samples for intercept, genotype and declared covariates");
            if (binary && (cases==0 || controls==0)) throw new IOException("Eligible cohort must contain both cases and controls");
            // Both original loaders infer binary traits from these legacy codes. Never silently transform a QT.
            if (!binary) {
                boolean unambiguousQuantitative=false;
                for (int i=0;i<rows.length;i++) if (included[i]) { double value=numeric(rows[i][2]);if (value!=0 && value!=1 && value!=2) unambiguousQuantitative=true; }
                if (!unambiguousQuantitative) throw new IOException("Eligible linear phenotype contains only legacy binary/missing codes 0,1,2; this workflow cannot guarantee quantitative interpretation. Use a workflow with an explicit quantitative input type; do not recode or transform the trait merely to bypass this check");
            }
            // Original PLINK1 rejects -2147483648 even though it is a Java int.
            int sentinel=Integer.MIN_VALUE+1;while (observedValues.contains((double)sentinel)) { if (sentinel==Integer.MAX_VALUE) throw new IOException("No unused numeric missing sentinel");sentinel++; }
            var familyNames=new LinkedHashMap<String,String>();var individualNames=new LinkedHashMap<Id,String>();
            // Include recorded parents even when not sampled, preserving founder/pedigree status.
            List<String[]> famRows=new ArrayList<>();
            try (InputStream stream=limited(fam,bounds.metadata())) {
                for (String text;(text=line(stream,bounds.line()))!=null;) if (!text.isBlank()) {
                    String[] fields=fields(text,6,"FAM");famRows.add(fields);
                    familyNames.computeIfAbsent(fields[0],key->"F"+(familyNames.size()+1));
                    for (int j=1;j<=3;j++) if (!fields[j].equals("0")) individualNames.computeIfAbsent(new Id(fields[0],fields[j]),key->"I"+(individualNames.size()+1));
                }
            }
            if (famRows.size()!=samples.size()) throw new IOException("FAM changed during validation");
            try (var stagedFam=Files.newBufferedWriter(directory.resolve("input.fam"));
                 var phenotypes=Files.newBufferedWriter(directory.resolve("phenotypes.tsv"));
                 var covars=Files.newBufferedWriter(directory.resolve("covariates.tsv"));
                 var keep=Files.newBufferedWriter(directory.resolve("eligible.tsv"));
                 var ledger=Files.newBufferedWriter(directory.resolve("sample-ledger.tsv"))) {
                phenotypes.write("FID\tIID\tTRAIT\n");covars.write("FID\tIID");for (int j=0;j<covariates.size();j++) covars.write("\tC"+(j+1));covars.write("\n");
                ledger.write("ordinal\tfid\tiid\tupstream_fid\tupstream_iid\tFAM_founder\tFAM_sex\tstatus\n");
                for (int i=0;i<samples.size();i++) {
                    String[] fields=famRows.get(i);String fid=familyNames.get(fields[0]),iid=individualNames.get(new Id(fields[0],fields[1]));
                    if (!fields[0].equals(samples.get(i).familyId()) || !fields[1].equals(samples.get(i).individualId())) throw new IOException("FAM order changed during validation");
                    String father=fields[2].equals("0")?"0":individualNames.get(new Id(fields[0],fields[2]));String mother=fields[3].equals("0")?"0":individualNames.get(new Id(fields[0],fields[3]));
                    stagedFam.write(fid+" "+iid+" "+father+" "+mother+" "+fields[4]+" "+sentinel+"\n");
                    boolean missingY=rows[i][2].equals("NA"),missingX=Arrays.asList(rows[i]).subList(3,rows[i].length).contains("NA");
                    String status=included[i]?"ELIGIBLE":missingY?(missingX?"MISSING_PHENOTYPE_AND_COVARIATE":"MISSING_PHENOTYPE"):"MISSING_COVARIATE";
                    ledger.write((i+1)+"\t"+fields[0]+"\t"+fields[1]+"\t"+fid+"\t"+iid+"\t"+samples.get(i).founder()+"\t"+fields[4]+"\t"+status+"\n");
                    if (included[i]) {
                        keep.write(fid+"\t"+iid+"\n");double value=numeric(rows[i][2]);if (binary && coding.equals("0/1")) value++;
                        phenotypes.write(fid+"\t"+iid+"\t"+value+"\n");covars.write(fid+"\t"+iid);for (int j=3;j<rows[i].length;j++) covars.write("\t"+numeric(rows[i][j]));covars.write("\n");
                    }
                }
            }
            var variants=new LinkedHashMap<String,Variant>();var alleles=new LinkedHashMap<String,PlinkAssociations.Alleles>();var finished=new HashSet<String>();String previous="";
            try (var stagedBim=Files.newBufferedWriter(directory.resolve("input.bim"));var metadata=Files.newBufferedWriter(directory.resolve("variant-metadata.tsv"))) {
                metadata.write("ordinal\tvariant\tupstream_variant\tchromosome\tposition\tbim_a1\tbim_a2\tcomplete_case_observed_genotypes\n");
                int ordinal=0;
                for (var variant:data.selectedVariants()) {
                    String chromosome=variant.chromosome();if (!chromosome.matches("[1-9]|1[0-9]|2[0-2]")) throw new IOException("Only canonical human autosomes 1..22 are supported: "+variant.id());
                    if (!chromosome.equals(previous)) { if (!previous.isEmpty()) finished.add(previous);if (finished.contains(chromosome)) throw new IOException("BED variants must form contiguous chromosome blocks; preserve original inputs and prepare a separate ordered fileset");previous=chromosome; }
                    for (String allele:List.of(variant.allele1(),variant.allele2())) if (!allele.matches("[ACGT]{1,1000}")) throw new IOException("Require resolved uppercase DNA allele strings (1..1000 bases) at "+variant.id());
                    double[] dosages=data.readAllele1Dosages(variant);int observed=0;for (int i=0;i<dosages.length;i++) if (included[i] && !Double.isNaN(dosages[i])) observed++;
                    String name="V"+(++ordinal);Variant selected=new Variant(variant.id(),name,chromosome,variant.position(),variant.allele1(),variant.allele2(),observed);
                    variants.put(name,selected);alleles.put(name,new PlinkAssociations.Alleles(selected.a1(),selected.a2()));
                    // Genetic distance does not enter this GLM; original BIM bytes are hashed separately.
                    stagedBim.write(chromosome+" "+name+" 0 "+variant.position()+" "+selected.a1()+" "+selected.a2()+"\n");
                    metadata.write(ordinal+"\t"+selected.original()+"\t"+name+"\t"+chromosome+"\t"+selected.position()+"\t"+selected.a1()+"\t"+selected.a2()+"\t"+observed+"\n");
                }
            }
            Files.copy(bed,directory.resolve("input.bed"));if (!hashes.get("bed").equals(FollowupSupport.hash(directory.resolve("input.bed")))) throw new IOException("BED snapshot differs from the original input hash");
            verify(inputs,hashes);var stagedHashes=new LinkedHashMap<String,String>();
            for (String name:List.of("input.bed","input.bim","input.fam","phenotypes.tsv","covariates.tsv","eligible.tsv")) stagedHashes.put(name,FollowupSupport.hash(directory.resolve(name)));
            return new Prepared(variants,alleles,samples.size(),eligible,excluded,cases,controls,sentinel,List.copyOf(covariates),hashes,inputs,stagedHashes);
        }
    }
    static void verify(Map<String,Path> paths,Map<String,String> hashes) throws Exception {
        for (var entry:paths.entrySet()) if (!hashes.get(entry.getKey()).equals(FollowupSupport.hash(entry.getValue()))) throw new IOException("Input changed during original association: "+entry.getKey());
    }
    static void verifyStaged(Path directory,Prepared inputs) throws Exception {
        for (var entry:inputs.stagedHashes().entrySet()) if (!entry.getValue().equals(FollowupSupport.hash(directory.resolve(entry.getKey())))) throw new IOException("Staged input changed during original association: "+entry.getKey());
    }
    static List<String> covariates(String text) {
        if (text.equals("none")) return List.of();var names=new ArrayList<String>();
        for (String name:text.split(",",-1)) {
            if (!name.matches("[A-Za-z][A-Za-z0-9_]{0,63}") || Set.of("fid","iid","phenotype").contains(name.toLowerCase(java.util.Locale.ROOT)) || names.contains(name)) throw new IllegalArgumentException("Declare distinct ASCII covariate column names or none");names.add(name);
        }
        if (names.size()>100) throw new IllegalArgumentException("At most100 declared numeric covariates");return List.copyOf(names);
    }
    private static String[] fields(String text,int count,String kind) throws IOException {
        String[] fields=text.strip().split("[ \\t]+");if (fields.length!=count) throw new IOException(kind+" requires "+count+" fields");for (String field:fields) token(field,kind);return fields;
    }
    private static void token(String value,String label) throws IOException {
        if (value.isEmpty()) throw new IOException("Empty "+label);for (int i=0;i<value.length();i++) if (Character.isISOControl(value.charAt(i)) || Character.isWhitespace(value.charAt(i)) || Character.isSpaceChar(value.charAt(i))) throw new IOException("Whitespace/control in "+label);
    }
    private static double numeric(String value) throws IOException {
        if (!value.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")) throw new IOException("Require finite numeric cohort values or exact NA, found: "+value);
        double result=Double.parseDouble(value);if (!Double.isFinite(result)) throw new IOException("Nonfinite cohort number");
        if (result==0 && new java.math.BigDecimal(value).signum()!=0) throw new IOException("Cohort number underflows Java double; preserve the source and use a workflow supporting its resolution");return result;
    }
    static InputStream limited(Path path,long maximum) throws IOException {
        return new java.io.FilterInputStream(new BufferedInputStream(Files.newInputStream(path))) {
            private long count;
            @Override public int read() throws IOException { int value=super.read();if (value!=-1 && ++count>maximum) throw new IOException("Input grew beyond its declared byte bound: "+path);return value; }
        };
    }
    static String line(InputStream input,int bound) throws IOException {
        var bytes=new ByteArrayOutputStream();boolean any=false;for (int value;(value=input.read())!=-1;) { any=true;if (value==10) break;if (bytes.size()>=bound) throw new IOException("Input line exceeds --maximum-line-bytes");bytes.write(value); }
        if (!any) return null;
        byte[] raw=bytes.toByteArray();int count=raw.length;if (count>0 && raw[count-1]==13) count--;
        return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw,0,count)).toString();
    }
}
