/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.genetics.KingRobustKinship;
import org.jlinalg.genetics.PlinkBedDataset;
import static org.jlinalg.cli.PlinkRelatednessSupport.*;

/** Exact tuple identities, streamed SNP-major bytes and bounded native pair batches. */
final class PlinkRelatednessInputs {
    private PlinkRelatednessInputs() { }
    record Id(String fid,String iid) { }
    record Prepared(Path prefix,List<PlinkBedDataset.Sample> samples,Map<Id,Integer> index,int variants,
                    long sameFamilyPairs,long estimatedResidentBytes) {
        int sample(Id id)throws IOException {Integer value=index.get(id);require(value!=null,"Original report refers to an unknown exact FID/IID tuple: "+id);return value;}
        long totalPairs(){return (long)samples.size()*(samples.size()-1)/2;}
        Id id(int sample){var s=samples.get(sample);return new Id(s.familyId(),s.individualId());}
    }
    static Prepared prepare(Path root,PlinkRelatednessContracts.Request request,Clock clock)throws IOException {
        var l=request.limits();Path prefix=root.resolve("inputs/cohort");
        List<PlinkBedDataset.Sample> samples;int variants;
        long metadata=Math.addExact(Files.size(Path.of(prefix+".bim")),Files.size(Path.of(prefix+".fam")));
        require(metadata<=l.metadataBytes(),"metadata_bytes exceeded");
        var limits=new PlinkBedDataset.Limits(l.samples(),l.variants(),1,l.metadataBytes(),l.workingBytes(),l.lineBytes());
        try(var dataset=PlinkBedDataset.open(prefix,Set.of(),limits)){samples=dataset.samples();variants=Math.toIntExact(dataset.variantCount());}
        require(samples.size()>=2&&variants>0,"Require at least two declared participants and one SNP");clock.check();
        require(Math.addExact(1_048_576L,Math.addExact(Math.multiplyExact(4L,metadata),Math.multiplyExact(384L,samples.size())))<=l.workingBytes(),"Estimated input-metadata working_bytes exceeded");
        var ids=new HashMap<Id,Integer>();var families=new HashMap<String,Long>();
        for(int i=0;i<samples.size();i++){var s=samples.get(i);token(s.familyId());token(s.individualId());require(!s.individualId().equals("0"),"Individual ID 0 is unsupported; do not rename it silently");require(ids.put(new Id(s.familyId(),s.individualId()),i)==null,"Duplicate FID/IID tuple");families.merge(s.familyId(),1L,Long::sum);}
        int famRows=0;
        try(var in=new Lines(Path.of(prefix+".fam"),l.lineBytes(),clock)){
            String line;while((line=in.next())!=null){var f=fields(line,6,"FAM");for(String s:f)token(s);require(Double.isFinite(Double.parseDouble(f[5])),"FAM phenotype must be finite numeric, with source missing code explicitly retained");famRows++;}
        }catch(NumberFormatException e){throw new IOException("FAM phenotype is not a supported finite numeric token; no value is imputed",e);}
        require(famRows==samples.size(),"FAM population changed while preparing");
        int bimRows=0,previousChromosome=0;long previousPosition=-1;
        try(var in=new Lines(Path.of(prefix+".bim"),l.lineBytes(),clock)){
            String line;while((line=in.next())!=null){var f=fields(line,6,"BIM");for(String s:f)token(s);int chromosome=Integer.parseInt(f[0]);long position=Long.parseLong(f[3]);
                require(f[0].equals(Integer.toString(chromosome))&&chromosome>=1&&chromosome<=22,"Only canonical numeric diploid human autosomes 1..22 are admitted; prepare other chromosomes explicitly");
                require(position>0&&position<=Integer.MAX_VALUE&&Double.isFinite(Double.parseDouble(f[2])),"Unsupported BIM coordinate/genetic-distance token");
                require(chromosome>previousChromosome||(chromosome==previousChromosome&&position>=previousPosition),"BIM must already be sorted; no sorting is inferred");
                require(f[4].length()==1&&f[5].length()==1&&"ACGT".contains(f[4])&&"ACGT".contains(f[5])&&!f[4].equals(f[5]),"Require resolved distinct uppercase A/C/G/T SNP alleles");
                previousChromosome=chromosome;previousPosition=position;bimRows++;
            }
        }catch(NumberFormatException e){throw new IOException("Invalid BIM numeric metadata",e);}
        require(bimRows==variants,"BIM population changed while preparing");
        // The general BED reader validates the header/size; check every unused pad bit.
        if(samples.size()%4!=0)scan(prefix,samples.size(),variants,clock,null,null);
        long familyPairs=0;for(long n:families.values())familyPairs=Math.addExact(familyPairs,n*(n-1)/2);
        long resident=Math.addExact(1_048_576L,Math.addExact(Math.multiplyExact(384L,samples.size()),2L*((samples.size()+3L)/4)));
        return new Prepared(prefix,List.copyOf(samples),Collections.unmodifiableMap(ids),variants,familyPairs,resident);
    }
    static String[] fields(String line,int expected,String kind)throws IOException {
        require(!line.isBlank(),"Blank "+kind+" rows are unsupported; retain and correct the input explicitly");String[] f=line.strip().split("\\s+");require(f.length==expected,kind+" must have exactly "+expected+" columns");return f;
    }
    private static void token(String value)throws IOException {
        require(!value.isEmpty()&&!value.startsWith("#"),"Empty/comment-like metadata token is unsupported");
        for(int i=0;i<value.length();i++){char c=value.charAt(i);require(!Character.isISOControl(c)&&!Character.isWhitespace(c)&&!Character.isSpaceChar(c)&&Character.getType(c)!=Character.FORMAT,"Unsupported whitespace/control/format character in metadata token");}
    }
    static List<KingRobustKinship.Estimate> count(Prepared p,List<KingRobustKinship.Pair> pairs,long retainedBatchBytes,
            PlinkRelatednessContracts.Limits l,Clock clock)throws IOException {
        if(pairs.isEmpty())return List.of();
        long maximumProjected=Math.min((long)p.samples().size(),2L*pairs.size());
        long budget=l.workingBytes()-p.estimatedResidentBytes()-retainedBatchBytes-Math.multiplyExact(256L,pairs.size())-128L*maximumProjected;
        require(budget>0,"Native pair-batch working_bytes exceeded; choose suitable explicit working_bytes/pair_batch_size");
        // A pair's IBS/KING counts depend only on its two calls at every SNP.
        // Project a bijective scratch panel for this audit batch; never change
        // the original program's complete sample/variant population or frequencies.
        var projection=new LinkedHashMap<Integer,Integer>();
        for(var pair:pairs){projection.computeIfAbsent(pair.first(),key->projection.size());projection.computeIfAbsent(pair.second(),key->projection.size());}
        int[] originalIndices=new int[projection.size()];for(var e:projection.entrySet())originalIndices[e.getValue()]=e.getKey();
        var projectedPairs=new ArrayList<KingRobustKinship.Pair>(pairs.size());for(var pair:pairs)projectedPairs.add(new KingRobustKinship.Pair(projection.get(pair.first()),projection.get(pair.second())));
        var counter=new KingRobustKinship.Accumulator(projection.size(),projectedPairs,p.variants(),budget);
        scan(p.prefix(),p.samples().size(),p.variants(),clock,counter,originalIndices);counter.finish();
        var estimates=new ArrayList<KingRobustKinship.Estimate>(pairs.size());for(int i=0;i<pairs.size();i++)estimates.add(counter.estimate(i));return estimates;
    }
    private static void scan(Path prefix,int samples,int variants,Clock clock,KingRobustKinship.Accumulator counter,int[] indices)throws IOException {
        int rowBytes=Math.toIntExact((samples+3L)/4);byte[] row=new byte[rowBytes];
        byte[] projected=indices==null?null:new byte[(indices.length+3)/4];
        try(var in=new BufferedInputStream(Files.newInputStream(Path.of(prefix+".bed")))){
            require(Arrays.equals(in.readNBytes(3),new byte[]{0x6c,0x1b,1}),"Not a SNP-major PLINK BED");
            for(int v=0;v<variants;v++){clock.check();require(in.readNBytes(row,0,row.length)==row.length,"Truncated BED during native scan");int remainder=samples%4;
                require(remainder==0||((row[row.length-1]&255) >>> (2*remainder))==0,"Nonzero unused BED padding bits; no hidden sample calls are admitted");
                if(counter!=null){
                    if(indices==null)counter.acceptPackedBed(row);
                    else {Arrays.fill(projected,(byte)0);for(int j=0;j<indices.length;j++){int old=indices[j];int code=((row[old/4]&255) >>> (2*(old%4)))&3;projected[j/4]|=(byte)(code << (2*(j%4)));}counter.acceptPackedBed(projected);}
                }
            }
            require(in.read()==-1,"Extra BED bytes after complete declared population");
        }
    }
    static long lowerOrdinal(int first,int second,int samples)throws IOException {require(first>second,"Original PLINK2 pair orientation changed");return (long)first*(first-1)/2+second;}
    static long upperOrdinal(int first,int second,int samples)throws IOException {require(first<second,"Original PLINK1 pair orientation changed");return (long)first*(2L*samples-first-1)/2+(second-first-1);}
}
