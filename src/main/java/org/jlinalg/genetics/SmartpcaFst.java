/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Source-specific EIG v7.2.1 qpsubs.c and smartpca.c behavior, Harvard Medical
 * School BSD-3-Clause notice retained in org/jlinalg/omics/smartpca-4f7e9f0/. */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Autosomal hard-call fstonly, including source preprocessing and weighted jackknife. */
public final class SmartpcaFst {
    private SmartpcaFst() { }
    public enum PositionConvention { INPUT_MORGANS, SOURCE_DIVIDE_ALL_BY_100, SOURCE_PHYSICAL_TIMES_1E_MINUS_8 }
    public enum MarkerStatus { USED, EXCLUDED_NON_AUTOSOMAL, SOURCE_NODATA_AT_MOST_ONE_SELECTED_SAMPLE }
    public enum Admission { COMPUTED, SOURCE_NO_JACKKNIFE_AT_MOST_ONE_BLOCK, NO_POSITIVE_TOTAL_DENOMINATOR, NONFINITE_SOURCE_ESTIMATE }
    public record Position(int chromosome,double geneticPosition,int physicalPosition) {
        public Position { require(chromosome>=0&&chromosome<=99&&Double.isFinite(geneticPosition)&&geneticPosition>=0&&geneticPosition<=1e6&&physicalPosition>=0,"Require numeric chromosome0..99, finite genetic position0..1e6 and nonnegative source int32 physical position"); }
    }
    public record Options(boolean inbreed,double blockLength,SmartpcaPrepared.Limits limits) {
        public Options { require(Double.isFinite(blockLength)&&blockLength>0,"Positive finite source block length required");Objects.requireNonNull(limits,"limits"); }
        public static Options defaults(){return new Options(false,.05,SmartpcaPrepared.Limits.defaults());}
    }
    public record Marker(int ordinal,MarkerStatus status,double effectiveGeneticPosition,int block,int selectedObservedSamples) { }
    /** Pair arrays follow (0,1),(0,2),...,(g-2,g-1); rejected locus denominator is -1. */
    public record Locus(int markerOrdinal,int block,int[] genotypeCounts,double[] numerator,double[] denominator,
            double[] inbreedingNumerator,double[] inbreedingDenominator) {
        public Locus { genotypeCounts=genotypeCounts.clone();numerator=numerator.clone();denominator=denominator.clone();inbreedingNumerator=inbreedingNumerator.clone();inbreedingDenominator=inbreedingDenominator.clone(); }
        @Override public int[] genotypeCounts(){return genotypeCounts.clone();}
        @Override public double[] numerator(){return numerator.clone();}
        @Override public double[] denominator(){return denominator.clone();}
        @Override public double[] inbreedingNumerator(){return inbreedingNumerator.clone();}
        @Override public double[] inbreedingDenominator(){return inbreedingDenominator.clone();}
    }
    /** Raw values are retained even when admission is false; negative finite FST is not clamped. */
    public record Estimate(int firstPopulation,int secondPopulation,double markerOrderRatio,double blockOrderRatio,
            double estimate,double standardError,double totalNumerator,double totalDenominator,int validLoci,int positiveDenominatorLoci,Admission admission) { }
    public record Result(List<Marker> markers,PositionConvention positionConvention,int[] blockMarkerCounts,
            int[] selectedContributingSamplesByPopulation,List<Estimate> pairs,List<Estimate> inbreeding) {
        public Result { markers=List.copyOf(markers);blockMarkerCounts=blockMarkerCounts.clone();selectedContributingSamplesByPopulation=selectedContributingSamplesByPopulation.clone();pairs=List.copyOf(pairs);inbreeding=List.copyOf(inbreeding); }
        @Override public int[] blockMarkerCounts(){return blockMarkerCounts.clone();}
        @Override public int[] selectedContributingSamplesByPopulation(){return selectedContributingSamplesByPopulation.clone();}
    }
    public static Result fit(byte[][] calls,int[] samplePopulation,int populations,List<Position> positions,Options options) {
        return fit(calls,samplePopulation,populations,positions,options,null);
    }
    /** Observer gets immutable per-locus diagnostics, without retaining the full marker-by-pair output. */
    public static Result fit(byte[][] calls,int[] samplePopulation,int populations,List<Position> positions,Options options,Consumer<Locus> observer) {
        Objects.requireNonNull(calls,"calls");Objects.requireNonNull(samplePopulation,"samplePopulation");Objects.requireNonNull(positions,"positions");Objects.requireNonNull(options,"options");
        require(calls.length>0&&calls.length==positions.size()&&samplePopulation.length>=2&&populations>=2&&populations<=100,"Require complete nonempty autosomal panel and2..100 declared populations");
        int samples=samplePopulation.length,pairs=Math.multiplyExact(populations,populations-1)/2;long selected=0;
        for(int group:samplePopulation){require(group>=-1&&group<populations,"Unknown literal population index; -1 explicitly excludes an individual");if(group>=0)selected++;}
        require(selected>=2,"At least two selected individuals required");admit(calls.length,samples,populations,pairs,1,options);
        double maximum=0;
        for(Position position:positions){Objects.requireNonNull(position,"position");maximum=Math.max(maximum,position.geneticPosition());}
        PositionConvention convention=maximum<=.00001?PositionConvention.SOURCE_PHYSICAL_TIMES_1E_MINUS_8:maximum>100?PositionConvention.SOURCE_DIVIDE_ALL_BY_100:PositionConvention.INPUT_MORGANS;
        var markers=new ArrayList<Marker>(calls.length);var weights=new ArrayList<Integer>();boolean[] contributing=new boolean[samples];int previousChromosome=-1,block=-1;double start=Double.NaN;
        for(int m=0;m<calls.length;m++) {
            interrupted();byte[] row=calls[m];require(row!=null&&row.length==samples,"Complete marker width differs from declared individuals");int observed=0;
            for(int i=0;i<samples;i++){byte value=row[i];require(value==0||value==1||value==2||value==9,"Require source hard calls0/1/2/9");if(samplePopulation[i]>=0&&value!=9)observed++;}
            Position p=positions.get(m);double genetic=effective(p,convention);
            MarkerStatus status=p.chromosome()<1||p.chromosome()>22?MarkerStatus.EXCLUDED_NON_AUTOSOMAL:observed<=1?MarkerStatus.SOURCE_NODATA_AT_MOST_ONE_SELECTED_SAMPLE:MarkerStatus.USED;
            int tag=-1;
            if(status==MarkerStatus.USED) {
                if(p.chromosome()!=previousChromosome||genetic-start>=options.blockLength()){previousChromosome=p.chromosome();start=genetic;block++;weights.add(0);}
                tag=block;weights.set(block,weights.get(block)+1);
                for(int i=0;i<samples;i++)if(samplePopulation[i]>=0&&row[i]!=9)contributing[i]=true;
            }
            markers.add(new Marker(m,status,genetic,tag,observed));
        }
        require(!weights.isEmpty(),"Original fstonly has no retained autosomal marker data; no complete result");
        int[] counts=weights.stream().mapToInt(Integer::intValue).toArray(),populationSamples=new int[populations];
        for(int i=0;i<samples;i++)if(contributing[i])populationSamples[samplePopulation[i]]++;
        int dimensions=pairs+(options.inbreed()?populations:0),blocks=counts.length;admit(calls.length,samples,populations,pairs,blocks,options);
        var estimates=new ArrayList<Estimate>();var inbreeding=new ArrayList<Estimate>();
        require(!options.inbreed()||blocks>1,"Original inbreeding weighted jackknife aborts with at most one block; no complete source FST result");
        if(blocks<=1) {
            for(int a=0;a<populations;a++)for(int b=a+1;b<populations;b++)estimates.add(zero(a,b));
            if(options.inbreed())for(int a=0;a<populations;a++)inbreeding.add(zero(a,a));
            return new Result(markers,convention,counts,populationSamples,estimates,inbreeding);
        }
        double[][] top=new double[blocks][dimensions],bottom=new double[blocks][dimensions];
        double[] markerTop=new double[dimensions],markerBottom=new double[dimensions];int[] valid=new int[dimensions],positive=new int[dimensions];
        int[] genotypes=new int[populations*3];double[] numerator=new double[pairs],denominator=new double[pairs],inTop=new double[options.inbreed()?populations:0],inBottom=new double[inTop.length];
        for(Marker marker:markers) {
            if(marker.status()!=MarkerStatus.USED)continue;interrupted();Arrays.fill(genotypes,0);
            byte[] row=calls[marker.ordinal()];for(int i=0;i<samples;i++)if(samplePopulation[i]>=0&&row[i]!=9)genotypes[samplePopulation[i]*3+row[i]]++;
            locus(genotypes,populations,options.inbreed(),numerator,denominator,inTop,inBottom);
            if(observer!=null)observer.accept(new Locus(marker.ordinal(),marker.block(),genotypes,numerator,denominator,inTop,inBottom));
            for(int d=0;d<dimensions;d++) {
                double n=d<pairs?numerator[d]:inTop[d-pairs],v=d<pairs?denominator[d]:inBottom[d-pairs];
                if(v<0)continue;
                top[marker.block()][d]+=n;bottom[marker.block()][d]+=v;markerTop[d]+=n;markerBottom[d]+=v;valid[d]++;if(v>0)positive[d]++;
            }
        }
        int dimension=0;
        for(int a=0;a<populations;a++)for(int b=a+1;b<populations;b++)estimates.add(estimate(a,b,dimension++,top,bottom,markerTop,markerBottom,valid,positive,counts));
        if(options.inbreed())for(int a=0;a<populations;a++)inbreeding.add(estimate(a,a,dimension++,top,bottom,markerTop,markerBottom,valid,positive,counts));
        return new Result(markers,convention,counts,populationSamples,estimates,inbreeding);
    }
    private static double effective(Position position,PositionConvention convention) {
        return switch(convention){case INPUT_MORGANS->position.geneticPosition();case SOURCE_DIVIDE_ALL_BY_100->position.geneticPosition()/100.;case SOURCE_PHYSICAL_TIMES_1E_MINUS_8->1e-8*position.physicalPosition();};
    }
    private static Estimate zero(int a,int b){return new Estimate(a,b,Double.NaN,Double.NaN,0,0,Double.NaN,Double.NaN,0,0,Admission.SOURCE_NO_JACKKNIFE_AT_MOST_ONE_BLOCK);}
    private static Estimate estimate(int a,int b,int d,double[][] top,double[][] bottom,double[] markerTop,double[] markerBottom,int[] valid,int[] positive,int[] counts) {
        interrupted();double n=0,v=0;
        for(int k=0;k<counts.length;k++){n+=top[k][d];v+=bottom[k][d];}
        double mean=n/(v+1e-10),natural=markerTop[d]/(markerBottom[d]+1e-10);double[] deleted=new double[counts.length],weights=new double[counts.length];
        for(int k=0;k<counts.length;k++){deleted[k]=(n-top[k][d])/((v-bottom[k][d])+1e-10);weights[k]=counts[k];}
        double[] jack=weightedJackknife(mean,deleted,weights);
        Admission status=!(v>0)?Admission.NO_POSITIVE_TOTAL_DENOMINATOR:!Double.isFinite(jack[0])||!Double.isFinite(jack[1])?Admission.NONFINITE_SOURCE_ESTIMATE:Admission.COMPUTED;
        return new Estimate(a,b,natural,mean,jack[0],jack[1],n,v,valid[d],positive[d],status);
    }
    /** Source wjackest/wjackestx, including global marker-count weights and filtering below1e-6. */
    public static double[] weightedJackknife(double mean,double[] deleted,double[] weights) {
        interrupted();
        Objects.requireNonNull(deleted,"deleted");Objects.requireNonNull(weights,"weights");require(deleted.length==weights.length,"Complete delete-block/weight arrays required");
        int groups=0;double total=0,difference=0,weighted=0;
        for(int i=0;i<weights.length;i++)if(!(weights[i]<1e-6)){groups++;total+=weights[i];difference+=-(deleted[i]-mean);weighted+=weights[i]*deleted[i];}
        require(groups>1,"Source weighted jackknife has fewer than two retained blocks; no inferred standard error");
        double estimate=difference+weighted/total,variance=0;
        for(int i=0;i<weights.length;i++)if(!(weights[i]<1e-6)){double h=total/weights[i],w=h-1,tau=h*mean-w*deleted[i];tau-=estimate;variance+=(tau*tau)/w;}
        return new double[]{estimate,Math.sqrt(variance/groups)};
    }
    private static void locus(int[] c,int populations,boolean inbreed,double[] n,double[] d,double[] inTop,double[] inBottom) {
        Arrays.fill(n,0);Arrays.fill(d,-1);int pair=0;
        for(int a=0;a<populations;a++)for(int b=a+1;b<populations;b++) {
            int i=a*3,j=b*3;double s=(double)c[i]+c[i+1]+c[i+2],t=(double)c[j]+c[j+1]+c[j+2];
            if(inbreed) {
                if(s<2||t<2){pair++;continue;}
                double p=(c[i+1]+2.*c[i+2])/(2*s),q=(c[j+1]+2.*c[j+2])/(2*t),h1=heterozygosity(c,i,s),h2=heterozygosity(c,j,t);
                double value=(p-q)*(p-q);value+=c[i+1]/(4*s*s);value+=c[j+1]/(4*t*t);value-=h1/s+h2/t;
                n[pair]=value;d[pair]=value+h1+h2;
            }else {
                double x=c[i+1]+2.*c[i+2],y=2.*c[i]+c[i+1],u=c[j+1]+2.*c[j+2],z=2.*c[j]+c[j+1];
                double first=x+y,second=u+z;if(first<1.5||second<1.5){pair++;continue;}
                double p=x/first,q=u/second,h1=x*y/(first*(first-1)),h2=u*z/(second*(second-1));
                double value=(p-q)*(p-q);value-=h1/first;value-=h2/second;n[pair]=value;value+=h1;value+=h2;d[pair]=value;
            }
            require(!(d[pair]<0),"Original fstcolyy negative denominator logic error; raw calculation is not admitted");pair++;
        }
        for(int a=0;a<inTop.length;a++){int i=3*a;double s=(double)c[i]+c[i+1]+c[i+2];inTop[a]=0;inBottom[a]=-1;if(s<2)continue;double h=2*heterozygosity(c,i,s);inTop[a]=h-c[i+1]/s;inBottom[a]=h;}
    }
    private static double heterozygosity(int[] c,int i,double s) {
        double x0=c[i],x1=c[i+1],x2=c[i+2];double h=x0*x2+(x0+x2)*x1/2+x1*(x1-1)/4;return h/(s*(s-1));
    }
    private static void admit(int markers,int samples,int groups,int pairs,int blocks,Options options) {
        try {
            long dimensions=Math.addExact(pairs,options.inbreed()?groups:0),rows=Math.multiplyExact((long)blocks,dimensions);
            long bytes=Math.addExact(Math.multiplyExact(rows,48),Math.addExact(Math.multiplyExact((long)markers,128),Math.multiplyExact((long)samples,16)));
            long work=Math.addExact(Math.multiplyExact((long)markers,Math.addExact(3L*samples,64L*dimensions)),Math.multiplyExact(rows,64));
            require(bytes<=options.limits().maximumRetainedBytes()&&work<=options.limits().maximumProducts(),"Complete source FST panel/block/population calculation exceeds explicit work/storage estimate; no automatic subsetting");
        }catch(ArithmeticException overflow){throw new IllegalArgumentException("Source FST dimensions exceed representable resource bounds",overflow);}
    }
    private static void interrupted(){if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Native source FST interrupted; no complete result");}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
}
