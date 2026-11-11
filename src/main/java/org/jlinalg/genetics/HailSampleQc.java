/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Native metrics following Hail0.2.139 sample_qc, _qc_allele_type and
 * numeric_allele_type (MIT, Hail Team). Nonmissing allele strings and haploid
 * or diploid calls are explicit; a null call is missing and a filtered entry
 * is a separate state. This is not a Hail MatrixTable/VDS reader, association
 * fitter, Batch engine or independently certified biological QC policy.
 */
public final class HailSampleQc {
    private HailSampleQc() { }
    private static final Pattern BASES=Pattern.compile("^[ACGTNM]+$");
    private static final Pattern SYMBOLIC=Pattern.compile("^\\.|\\.$|^<|>$|\\[|\\]");

    /** The numeric values of Hail's enumeration are not copied as a public ABI. */
    public enum AlleleType { UNKNOWN,SNP,MNP,INSERTION,DELETION,COMPLEX,STAR,SYMBOLIC,TRANSITION,TRANSVERSION }
    public record Schema(boolean depthInt32,boolean genotypeQualityInt32) { }
    public record Call(List<Integer> alleles,boolean phased) {
        public Call {
            alleles=List.copyOf(alleles);require(alleles.size()==1||alleles.size()==2,"Native sample QC requires explicit haploid or diploid calls");
            for(int allele:alleles)require(allele>=0,"Negative allele indices are not missing calls; use a null call for the source missing state");
        }
    }
    /** DP/GQ are independent of whether GT is missing; filtered entries do not aggregate them. */
    public record Entry(Call call,Integer depth,Integer genotypeQuality,boolean filtered) { }
    public record Row(List<String> alleles,List<Entry> entries) {
        public Row {
            alleles=List.copyOf(alleles);entries=List.copyOf(entries);require(!alleles.isEmpty(),"A reference allele string is required");
            for(String allele:alleles)require(allele.indexOf('\0')<0,"Embedded NUL is outside the original string profile");
        }
    }
    public record Limits(int maximumSamples,long maximumRows,long maximumEntries) {
        public Limits {require(maximumSamples>0&&maximumRows>0&&maximumEntries>0,"Positive complete-input capacities required");}
        public static Limits defaults(){return new Limits(1000000,10000000,1000000000);}
    }
    /** Original integer-input aggregates return float64 extrema; empty stats retain NaN mean/stdev and missing extrema. */
    public record Stats(double mean,double stdev,Double min,Double max) { }
    public record Sample(String id,int ordinal,long rows,double callRate,long nCalled,long nNotCalled,long nFiltered,
                         long nHomRef,long nHet,long nHomVar,long nNonRef,long nSingleton,long nSnp,
                         long nInsertion,long nDeletion,long nTransition,long nTransversion,long nStar,
                         Double transitionTransversion,Double hetHomVar,Double insertionDeletion,
                         Stats depth,Stats genotypeQuality) { }

    /**
     * Stream the declared post-row/column-filter matrix. Duplicate sample names
     * remain distinct by ordinal. No result list is returned on any invalid,
     * ragged or capacity-exceeding row; input calls and allele strings are not
     * normalized, imputed, deduplicated or substituted.
     */
    public static List<Sample> compute(List<String> sampleIds,Schema schema,Iterable<Row> rows,Limits limits) {
        Objects.requireNonNull(schema,"schema");Objects.requireNonNull(rows,"rows");Objects.requireNonNull(limits,"limits");sampleIds=List.copyOf(sampleIds);require(sampleIds.size()<=limits.maximumSamples(),"Complete sample capacity exceeded");
        for(String id:sampleIds)require(id.indexOf('\0')<0,"Embedded NUL sample identifier");var counts=new ArrayList<Counts>(sampleIds.size());for(int i=0;i<sampleIds.size();i++)counts.add(new Counts());long nRows=0;
        for(Row row:rows){
            Objects.requireNonNull(row,"row");nRows=Math.addExact(nRows,1);require(nRows<=limits.maximumRows(),"Complete variant-row capacity exceeded");require(sampleIds.isEmpty()||nRows<=limits.maximumEntries()/sampleIds.size(),"Complete genotype-entry capacity exceeded");require(row.entries().size()==sampleIds.size(),"Ragged genotype row; no sample may be omitted");long[] ac=new long[row.alleles().size()];var types=new AlleleType[Math.max(0,row.alleles().size()-1)];
            for(int index=1;index<row.alleles().size();index++)types[index-1]=qcAlleleType(row.alleles().get(0),row.alleles().get(index));
            for(Entry entry:row.entries())if(!entry.filtered()&&entry.call()!=null)for(int allele:entry.call().alleles()){require(allele<ac.length,"Call allele index exceeds the row's declared allele family");ac[allele]=Math.addExact(ac[allele],1);}
            for(int index=0;index<sampleIds.size();index++){
                Entry entry=row.entries().get(index);Counts c=counts.get(index);
                if(entry.filtered()){c.filtered=Math.addExact(c.filtered,1);continue;}
                if(schema.depthInt32()&&entry.depth()!=null)c.dp.add(entry.depth());if(schema.genotypeQualityInt32()&&entry.genotypeQuality()!=null)c.gq.add(entry.genotypeQuality());
                if(entry.call()==null){c.missing=Math.addExact(c.missing,1);continue;}
                c.called=Math.addExact(c.called,1);List<Integer> genotype=entry.call().alleles();boolean homRef=true;for(int allele:genotype)homRef&=allele==0;
                if(homRef)c.homRef=Math.addExact(c.homRef,1);else if(genotype.size()==2&&!genotype.get(0).equals(genotype.get(1)))c.het=Math.addExact(c.het,1);
                for(int allele:genotype){
                    if(allele==0)continue;if(ac[allele]==1)c.singleton=Math.addExact(c.singleton,1);
                    switch(types[allele-1]){
                        case TRANSITION -> c.transition=Math.addExact(c.transition,1);
                        case TRANSVERSION -> c.transversion=Math.addExact(c.transversion,1);
                        case INSERTION -> c.insertion=Math.addExact(c.insertion,1);
                        case DELETION -> c.deletion=Math.addExact(c.deletion,1);
                        case STAR -> c.star=Math.addExact(c.star,1);
                        default -> { /* Original sample_qc does not expose the other allele-type counts. */ }
                    }
                }
            }
        }
        var result=new ArrayList<Sample>(sampleIds.size());
        for(int i=0;i<sampleIds.size();i++){
            Counts c=counts.get(i);require(Math.addExact(Math.addExact(c.called,c.missing),c.filtered)==nRows,"Complete source call/missing/filter count conservation failed");long homVar=c.called-c.homRef-c.het;
            result.add(new Sample(sampleIds.get(i),i,nRows,(double)c.called/nRows,c.called,c.missing,c.filtered,c.homRef,c.het,homVar,c.called-c.homRef,c.singleton,Math.addExact(c.transition,c.transversion),c.insertion,c.deletion,c.transition,c.transversion,c.star,ratio(c.transition,c.transversion),ratio(c.het,homVar),ratio(c.insertion,c.deletion),schema.depthInt32()?c.dp.result():null,schema.genotypeQualityInt32()?c.gq.result():null));
        }
        return List.copyOf(result);
    }

    /** Original numeric_allele_type uses anchored uppercase A/C/G/T/N/M and suffix-based indel tests. */
    public static AlleleType alleleType(String ref,String alt) {
        Objects.requireNonNull(ref,"reference allele");Objects.requireNonNull(alt,"alternate allele");
        if(!BASES.matcher(ref).matches())return AlleleType.UNKNOWN;
        if(BASES.matcher(alt).matches()){
            if(ref.length()==alt.length()){
                if(ref.length()==1)return ref.equals(alt)?AlleleType.UNKNOWN:AlleleType.SNP;
                int differences=0;for(int i=0;i<ref.length();i++)if(ref.charAt(i)!=alt.charAt(i))differences++;
                return differences==1?AlleleType.SNP:AlleleType.MNP;
            }
            if(ref.length()<alt.length()&&ref.charAt(0)==alt.charAt(0)&&alt.endsWith(ref.substring(1)))return AlleleType.INSERTION;
            if(ref.charAt(0)==alt.charAt(0)&&ref.endsWith(alt.substring(1)))return AlleleType.DELETION;
            return AlleleType.COMPLEX;
        }
        if(alt.equals("*"))return AlleleType.STAR;
        return SYMBOLIC.matcher(alt).find()?AlleleType.SYMBOLIC:AlleleType.UNKNOWN;
    }
    public static AlleleType qcAlleleType(String ref,String alt) {
        AlleleType type=alleleType(ref,alt);if(type!=AlleleType.SNP)return type;
        for(int i=0;i<ref.length();i++){char r=ref.charAt(i),a=alt.charAt(i);if(r!=a&&(r=='A'&&a=='G'||r=='G'&&a=='A'||r=='C'&&a=='T'||r=='T'&&a=='C'))return AlleleType.TRANSITION;}
        return AlleleType.TRANSVERSION;
    }
    private static Double ratio(long numerator,long denominator){return denominator==0?null:(double)numerator/denominator;}
    private static final class Counts {
        long called,missing,filtered,homRef,het,singleton,transition,transversion,insertion,deletion,star;final Running dp=new Running(),gq=new Running();
    }
    private static final class Running {
        long n;double mean,m2;Integer min,max;
        void add(int value){n=Math.addExact(n,1);double delta=value-mean;mean+=delta/n;m2+=delta*(value-mean);min=min==null?value:Math.min(min,value);max=max==null?value:Math.max(max,value);}
        Stats result(){return new Stats(n==0?Double.NaN:mean,n==0?Double.NaN:Math.sqrt(m2/n),min==null?null:min.doubleValue(),max==null?null:max.doubleValue());}
    }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
}
