/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jlinalg.genetics.PlinkBedDataset;

/** Native hard-call/cohort calculations for auditing the original SAIGE PLINK path. */
final class SaigeGenotypes {
    private SaigeGenotypes() { }
    enum AlleleOrder { ALT_FIRST, REF_FIRST }
    /** Original "minor" fills minor-allele dosage zero, i.e. major homozygotes. */
    enum Missing {
        BEST_GUESS("best_guess"), MEAN("mean"), MAJOR_HOMOZYGOTE("minor");
        final String upstream;
        Missing(String upstream){this.upstream=upstream;}
    }
    record Summary(int samples,int missing,double rawAlleleCount,double rawFrequency,
                   double rawMinorCount,double missingRate,double upstreamQcFrequency,
                   double upstreamQcMinorCount,boolean flipped,
                   double imputedMinorDosage,double alleleCount,double frequency,double minorCount,
                   int cases,int controls,double caseFrequency,double controlFrequency,
                   int caseHom,int caseHet,int controlHom,int controlHet) { }
    record Calls(Summary summary,double[] minorDosages) {
        Calls {minorDosages=minorDosages.clone();}
        @Override public double[] minorDosages(){return minorDosages.clone();}
    }

    /** SAIGE matches IID alone: reject collisions even when FID differs. */
    static int[] cohortIndices(List<PlinkBedDataset.Sample> samples,List<String> cohort) {
        Objects.requireNonNull(samples);Objects.requireNonNull(cohort);
        if(cohort.isEmpty())throw new IllegalArgumentException("SAIGE model cohort is empty");
        Map<String,Integer> index=new HashMap<>();
        for(int i=0;i<samples.size();i++)if(index.put(samples.get(i).individualId(),i)!=null)
            throw new IllegalArgumentException("Original SAIGE uses IID alone; duplicate genotype IID: "+samples.get(i).individualId());
        var seen=new HashSet<String>();int[] result=new int[cohort.size()];
        for(int i=0;i<cohort.size();i++){
            String id=cohort.get(i);Integer position=index.get(id);
            if(id==null||id.isBlank()||!seen.add(id)||position==null)
                throw new IllegalArgumentException("Model IIDs must be unique and present in the genotype source: "+id);
            result[i]=position;
        }
        return result;
    }

    static Calls read(PlinkBedDataset bed,PlinkBedDataset.Variant variant,int[] cohort,
                      int[] binaryOutcomes,AlleleOrder order,Missing method)throws java.io.IOException {
        Objects.requireNonNull(order);
        double[] source=bed.readAllele1Dosages(variant),selected=new double[cohort.length];
        var unique=new HashSet<Integer>();
        for(int i=0;i<cohort.length;i++){
            int position=cohort[i];if(position<0||position>=source.length||!unique.add(position))throw new IllegalArgumentException("Invalid/duplicate genotype cohort index");
            selected[i]=source[position];
        }
        return calculatePlink(selected,binaryOutcomes,order,method);
    }

    /** Preserve original PLINK ref-first subtraction and MAC operation order at QC boundaries. */
    static Calls calculatePlink(double[] allele1HardCalls,int[] binaryOutcomes,AlleleOrder order,Missing method) {
        return calculatePlink(allele1HardCalls,binaryOutcomes,order,method,0,0);
    }
    /** Original optional cleanup uses inclusive absolute dosage and pre-QC MAC plus imputed dosage. */
    static Calls calculatePlink(double[] allele1HardCalls,int[] binaryOutcomes,AlleleOrder order,Missing method,double zeroCutoff,double zeroMac) {
        Objects.requireNonNull(order);double sum=0;int observed=0;double[] effect=allele1HardCalls.clone();
        for(int i=0;i<effect.length;i++)if(!Double.isNaN(effect[i])){sum+=effect[i];observed++;if(order==AlleleOrder.REF_FIRST)effect[i]=2-effect[i];}
        double frequency=observed==0?Double.NaN:sum/observed/2;
        if(order==AlleleOrder.REF_FIRST)frequency=1-frequency;
        return calculate(effect,binaryOutcomes,method,frequency,zeroCutoff,zeroMac);
    }

    /** Initial original single-marker QC before imputation; a second MAF/MAC check follows it. */
    static boolean passesOriginalQc(Summary summary,double minimumMaf,double minimumMac,double maximumMissing) {
        if(!Double.isFinite(minimumMaf)||minimumMaf<0||minimumMaf>.5||!Double.isFinite(minimumMac)||minimumMac<0||!Double.isFinite(maximumMissing)||maximumMissing<0||maximumMissing>1)throw new IllegalArgumentException("Invalid SAIGE QC thresholds");
        double maf=Math.min(summary.upstreamQcFrequency(),1-summary.upstreamQcFrequency());
        return summary.missingRate()<=maximumMissing&&maf>=minimumMaf&&summary.upstreamQcMinorCount()>=minimumMac;
    }

    /** Both original single-marker QC stages, without changing floating thresholds. */
    static boolean passesFinalSingleQc(Summary summary,double minimumMaf,double minimumMac,double maximumMissing) {
        return passesOriginalQc(summary,minimumMaf,minimumMac,maximumMissing)&&passesGroupQc(summary,minimumMaf,minimumMac,maximumMissing);
    }

    /** Original gene/region QC instead uses post-imputation frequency/count. */
    static boolean passesGroupQc(Summary summary,double minimumMaf,double minimumMac,double maximumMissing) {
        if(!Double.isFinite(minimumMaf)||minimumMaf<0||minimumMaf>.5||!Double.isFinite(minimumMac)||minimumMac<0||!Double.isFinite(maximumMissing)||maximumMissing<0||maximumMissing>1)throw new IllegalArgumentException("Invalid SAIGE group QC thresholds");
        double maf=Math.min(summary.frequency(),1-summary.frequency()),mac=Math.min(summary.alleleCount(),2.0*summary.samples()-summary.alleleCount());
        return summary.missingRate()<=maximumMissing&&maf>=minimumMaf&&mac>=minimumMac;
    }

    /** Input counts the declared effect/ALT allele; no biological REF/strand is inferred. */
    static Calls calculate(double[] effectHardCalls,int[] binaryOutcomes,Missing method) {
        return calculate(effectHardCalls,binaryOutcomes,method,Double.NaN);
    }

    private static Calls calculate(double[] effectHardCalls,int[] binaryOutcomes,Missing method,double upstreamFrequency) {
        return calculate(effectHardCalls,binaryOutcomes,method,upstreamFrequency,0,0);
    }
    private static Calls calculate(double[] effectHardCalls,int[] binaryOutcomes,Missing method,double upstreamFrequency,double zeroCutoff,double zeroMac) {
        Objects.requireNonNull(effectHardCalls);Objects.requireNonNull(method);
        if(!Double.isFinite(zeroCutoff)||zeroCutoff<0||zeroCutoff>2||!Double.isFinite(zeroMac)||zeroMac<0)throw new IllegalArgumentException("Invalid original dosage-cleanup controls");
        int n=effectHardCalls.length;
        if(n==0||binaryOutcomes!=null&&binaryOutcomes.length!=n)throw new IllegalArgumentException("Require a nonempty cohort and matching explicit binary outcomes");
        int missing=0,cases=0;double rawAC=0;
        for(int i=0;i<n;i++){
            double g=effectHardCalls[i];
            if(Double.isNaN(g))missing++;
            else if(g==0||g==1||g==2)rawAC+=g;
            else throw new IllegalArgumentException("Require diploid hard calls 0,1,2 or missing");
            if(binaryOutcomes!=null){int y=binaryOutcomes[i];if(y!=0&&y!=1)throw new IllegalArgumentException("Explicit binary outcomes must be 0/1");cases+=y;}
        }
        if(missing==n)throw new IllegalArgumentException("All cohort genotype calls are missing; no allele frequency can be established");
        if(binaryOutcomes!=null&&(cases==0||cases==n))throw new IllegalArgumentException("Binary cohort requires both cases and controls");
        double rawAF=rawAC/(double)(n-missing)/2,rawMAC=Math.min(rawAC,2.0*(n-missing)-rawAC);
        if(Double.isNaN(upstreamFrequency))upstreamFrequency=rawAF;
        double missingRate=missing/(double)n,upstreamMac=Math.min(upstreamFrequency,1-upstreamFrequency)*n*(1-missingRate)*2;
        boolean flip=upstreamFrequency>.5;double minorAF=flip?1-upstreamFrequency:upstreamFrequency;
        double fill=switch(method){case BEST_GUESS->Math.floor(2*minorAF+.5);case MEAN->2*minorAF;case MAJOR_HOMOZYGOTE->0;};
        boolean clean=zeroCutoff>0&&upstreamMac+fill*missing<=zeroMac;
        if(clean&&fill<=zeroCutoff)fill=0;
        double[] minor=new double[n];double minorAC=0,caseMinor=0,controlMinor=0;int caseMinorHom=0,caseHet=0,controlMinorHom=0,controlHet=0;
        for(int i=0;i<n;i++){
            double g=effectHardCalls[i];g=Double.isNaN(g)?fill:flip?2-g:g;if(clean&&g<=zeroCutoff)g=0;minor[i]=g;minorAC+=g;
            if(binaryOutcomes!=null){
                boolean isCase=binaryOutcomes[i]==1;
                if(isCase){caseMinor+=g;if(g>=1.5&&g<=2)caseMinorHom++;if(g>=.5&&g<1.5)caseHet++;}
                else{controlMinor+=g;if(g>=1.5&&g<=2)controlMinorHom++;if(g>=.5&&g<1.5)controlHet++;}
            }
        }
        // Admitted Armadillo arrayops::accumulate uses separate even/odd accumulators.
        double even=0,odd=0;for(int i=0;i<n;i++)if((i&1)==0)even+=minor[i];else odd+=minor[i];minorAC=even+odd;
        double ac=flip?2.0*n-minorAC:minorAC,af=flip?1-minorAC/(2.0*n):minorAC/(2.0*n);int controls=binaryOutcomes==null?0:n-cases;
        double caseAF=binaryOutcomes==null?Double.NaN:caseMinor/(2.0*cases),controlAF=binaryOutcomes==null?Double.NaN:controlMinor/(2.0*controls);
        if(flip&&binaryOutcomes!=null){caseAF=1-caseAF;controlAF=1-controlAF;}
        int caseHom=flip?cases-caseHet-caseMinorHom:caseMinorHom,controlHom=flip?controls-controlHet-controlMinorHom:controlMinorHom;
        return new Calls(new Summary(n,missing,rawAC,rawAF,rawMAC,missingRate,upstreamFrequency,upstreamMac,flip,fill,ac,af,minorAC,cases,controls,caseAF,controlAF,caseHom,caseHet,controlHom,controlHet),minor);
    }
}
