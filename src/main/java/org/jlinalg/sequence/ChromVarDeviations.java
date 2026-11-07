/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * Native chromVAR expectation and deviation cores for explicitly prepared backgrounds.
 * Numerical source profiles 1.16.0 and 1.34.1 have identical retained function bodies.
 * This class does not select GC/intensity backgrounds, scan motifs, construct R objects,
 * or certify a historical package installation. All peak indices in this API are one-based.
 */
public final class ChromVarDeviations {
    private ChromVarDeviations() { }
    private static final double MAX_COUNT=9_007_199_254_740_991d;
    private static final long NA_BITS=0x7ff80000000007a2L;
    private static final double NA=Double.longBitsToDouble(NA_BITS);
    public enum Profile { SOURCE_1_16_0, SOURCE_1_34_1 }
    public enum Kind { FINITE, NA, NAN, POSITIVE_INFINITY, NEGATIVE_INFINITY }

    /** Capacities are admission limits; exceeding one never selects a smaller input population. */
    public record Limits(int peaks,int cells,int storedValues,int annotations,int annotationIndices,
                         long backgroundValues,long sampleMemberships,long outputValues,
                         long intermediateValues,long maximumWork) {
        public Limits {
            if(peaks<1||cells<1||storedValues<0||annotations<1||annotationIndices<0||peaks>2_000_000||cells>2_000_000||storedValues>100_000_000||annotations>1_000_000||annotationIndices>100_000_000||backgroundValues<0||backgroundValues>100_000_000L||sampleMemberships<0||sampleMemberships>100_000_000L||outputValues<1||outputValues>200_000_000L||intermediateValues<0||intermediateValues>200_000_000L||maximumWork<1)throw new IllegalArgumentException("Invalid chromVAR capacities");
        }
        public static Limits defaults(){return new Limits(2_000_000,2_000_000,20_000_000,100_000,20_000_000,20_000_000L,10_000_000L,10_000_000L,10_000_000L,1_000_000_000L);}
    }

    /** Immutable nonnegative real CSC counts, peaks by cells; duplicate coordinates must be resolved explicitly. */
    public static final class Counts {
        private final List<String> peaks,cells;
        private final int[] pointers,rows;
        private final double[] values,peakTotals,cellTotals;
        private final double total;
        public Counts(List<String> peaks,List<String> cells,int[] pointers,int[] rows,double[] values,Limits limits,BooleanSupplier cancelled) {
            Objects.requireNonNull(peaks);Objects.requireNonNull(cells);Objects.requireNonNull(pointers);Objects.requireNonNull(rows);Objects.requireNonNull(values);Objects.requireNonNull(limits);Budget b=new Budget(limits,cancelled);
            if(peaks.isEmpty()||cells.isEmpty()||peaks.size()>limits.peaks||cells.size()>limits.cells||values.length>limits.storedValues||pointers.length!=cells.size()+1||rows.length!=values.length||pointers[0]!=0||pointers[cells.size()]!=values.length)throw new IllegalArgumentException("Invalid bounded chromVAR CSC dimensions");
            this.peaks=identities(peaks,b);this.cells=identities(cells,b);this.pointers=pointers.clone();this.rows=rows.clone();this.values=values.clone();peakTotals=new double[peaks.size()];cellTotals=new double[cells.size()];double sum=0;
            for(int col=0;col<cells.size();col++) {
                if(this.pointers[col]<0||this.pointers[col+1]<this.pointers[col]||this.pointers[col+1]>values.length)throw new IllegalArgumentException("Invalid chromVAR CSC pointers");int previous=-1;
                for(int k=this.pointers[col];k<this.pointers[col+1];k++) {
                    b.add(1);int row=this.rows[k];double value=this.values[k];if(row<=previous||row>=peaks.size()||!Double.isFinite(value)||value<0||value>MAX_COUNT)throw new IllegalArgumentException("Require sorted unique CSC rows and finite nonnegative chromVAR counts within2^53-1");previous=row;peakTotals[row]+=value;cellTotals[col]+=value;sum+=value;
                    if(peakTotals[row]>MAX_COUNT||cellTotals[col]>MAX_COUNT||!Double.isFinite(sum))throw new IllegalArgumentException("Count totals exceed the finite exact-count admission domain");
                }
            }
            total=sum;b.check();
        }
        public List<String> peaks(){return peaks;}public List<String> cells(){return cells;}
        public int[] pointers(){return pointers.clone();}public int[] rows(){return rows.clone();}public double[] values(){return values.clone();}
        public double[] peakTotals(){return peakTotals.clone();}public double[] cellTotals(){return cellTotals.clone();}public double total(){return total;}
        private double value(int peak,int cell,Budget b){b.add(1);int lo=pointers[cell],hi=pointers[cell+1]-1;while(lo<=hi){b.add(1);int mid=(lo+hi)>>>1;if(rows[mid]==peak)return values[mid];if(rows[mid]<peak)lo=mid+1;else hi=mid-1;}return 0;}
    }

    /** Factor levels in declared order; assignments are zero-based level indices, with -1 for missing group labels. */
    public record Grouping(List<String> levels,int[] assignments) {
        public Grouping {if(levels.size()>2_000_000||assignments.length>2_000_000)throw new IllegalArgumentException("Excessive chromVAR grouping dimensions");levels=List.copyOf(levels);assignments=assignments.clone();Set<String> seen=new HashSet<>();for(String level:levels){SignacFragments.identity(level);if(!seen.add(level))throw new IllegalArgumentException("Repeated chromVAR group level");}for(int group:assignments)if(group< -1||group>=levels.size())throw new IllegalArgumentException("Group assignment outside declared factor levels");}
        @Override public int[] assignments(){return assignments.clone();}
    }
    public record Expectation(Profile profile,double[] values,long work,List<String> warnings) {
        public Expectation {values=values.clone();warnings=List.copyOf(warnings);}@Override public double[] values(){return values.clone();}
    }
    /** Sampled arrays use canonical iterations-by-cells shape; sampledWasSourceVector records R dimension dropping. */
    public record Intermediates(double[] observed,double[] expected,double[] observedDeviation,
                                double[][] sampled,double[][] sampledExpected,double[][] sampledDeviation,
                                boolean sampledWasSourceVector) {
        public Intermediates {observed=observed.clone();expected=expected.clone();observedDeviation=observedDeviation.clone();sampled=copy(sampled);sampledExpected=copy(sampledExpected);sampledDeviation=copy(sampledDeviation);}
        @Override public double[] observed(){return observed.clone();}@Override public double[] expected(){return expected.clone();}@Override public double[] observedDeviation(){return observedDeviation.clone();}
        @Override public double[][] sampled(){return copy(sampled);}@Override public double[][] sampledExpected(){return copy(sampledExpected);}@Override public double[][] sampledDeviation(){return copy(sampledDeviation);}
    }
    public record Single(Profile profile,double[] z,double[] deviations,double fractionMatches,double fractionBackgroundOverlap,
                         int thresholdMaskedCells,Intermediates intermediates,long work,List<String> warnings) {
        public Single {z=z.clone();deviations=deviations.clone();warnings=List.copyOf(warnings);}@Override public double[] z(){return z.clone();}@Override public double[] deviations(){return deviations.clone();}
    }
    public record Result(Profile profile,List<String> motifs,List<String> cells,double[][] z,double[][] deviations,
                         double[] fractionMatches,double[] fractionBackgroundOverlap,long work,List<String> warnings) {
        public Result {motifs=List.copyOf(motifs);cells=List.copyOf(cells);z=copy(z);deviations=copy(deviations);fractionMatches=fractionMatches.clone();fractionBackgroundOverlap=fractionBackgroundOverlap.clone();warnings=List.copyOf(warnings);}
        @Override public double[][] z(){return copy(z);}@Override public double[][] deviations(){return copy(deviations);}
        @Override public double[] fractionMatches(){return fractionMatches.clone();}@Override public double[] fractionBackgroundOverlap(){return fractionBackgroundOverlap.clone();}
    }

    public static double missingValue(){return NA;}
    public static Kind kind(double value){if(Double.doubleToRawLongBits(value)==NA_BITS)return Kind.NA;if(Double.isNaN(value))return Kind.NAN;if(value==Double.POSITIVE_INFINITY)return Kind.POSITIVE_INFINITY;if(value==Double.NEGATIVE_INFINITY)return Kind.NEGATIVE_INFINITY;return Kind.FINITE;}
    public static String format(double value){return switch(kind(value)){case NA->"NA";case NAN->"NaN";case POSITIVE_INFINITY->"Inf";case NEGATIVE_INFINITY->"-Inf";case FINITE->Double.toString(value);};}

    public static Expectation expectations(Counts counts,boolean norm,Grouping grouping,Profile profile,Limits limits,BooleanSupplier cancelled) {
        Objects.requireNonNull(profile);Budget b=new Budget(limits,cancelled);admit(counts,limits);int p=counts.peaks.size(),c=counts.cells.size();double[] result=new double[p];List<String> warnings=warnings(profile);
        if(grouping==null) {
            if(!norm)for(int peak=0;peak<p;peak++){b.add(1);result[peak]=divide(counts.peakTotals[peak],counts.total);}
            else for(int peak=0;peak<p;peak++){double sum=0;for(int cell=0;cell<c;cell++)sum=add(sum,divide(counts.value(peak,cell,b),counts.cellTotals[cell]));result[peak]=sum;}
        } else {
            if(grouping.assignments.length!=c)throw new IllegalArgumentException("Group assignments must match every cell");int n=grouping.levels.size();List<List<Integer>> groups=new ArrayList<>();for(int i=0;i<n;i++)groups.add(new ArrayList<>());for(int cell=0;cell<c;cell++){b.add(1);if(grouping.assignments[cell]>=0)groups.get(grouping.assignments[cell]).add(cell);}
            if(!norm)for(var group:groups)if(group.size()==1||p==1&&!group.isEmpty())throw new IllegalArgumentException("Original grouped norm=FALSE rowSums loses array dimensions for a singleton selected axis");
            for(var group:groups){double total=0;if(!norm)for(int cell:group){b.add(1);total+=counts.cellTotals[cell];}
                for(int peak=0;peak<p;peak++){b.add(1);double sum=0;for(int cell:group){double value=counts.value(peak,cell,b);sum=add(sum,norm?divide(value,counts.cellTotals[cell]):value);}result[peak]=add(result[peak],norm?sum:divide(sum,total));}
            }
            for(int peak=0;peak<p;peak++){b.add(1);result[peak]=divide(result[peak],n);}
        }
        if(norm)warnings.add("SOURCE_NORM_SUM: original norm=TRUE sums per-cell fractions; it does not average cells or renormalize the resulting expectation.");if(nonfinite(result)>0)warnings.add("SOURCE_NONFINITE_EXPECTATION: source NaN/NA/Inf are retained; review zero totals and declared factor levels.");b.check();return new Expectation(profile,result,b.used,warnings);
    }

    public static Single single(Counts counts,int[] peakSet,int[][] backgroundPeaks,double[] expectation,double threshold,
                                boolean intermediateResults,Profile profile,Limits limits,BooleanSupplier cancelled) {
        Objects.requireNonNull(profile);if(!Double.isFinite(threshold))throw new IllegalArgumentException("Require a finite declared threshold");Budget b=new Budget(limits,cancelled);Context context=context(counts,backgroundPeaks,expectation,limits,b);Single result=compute(context,peakSet,threshold,intermediateResults,profile,limits,b);b.check();return result;
    }
    /** Full original core: positive pooled peak counts, default threshold one, and source one-cell output-shape gate. */
    public static Result compute(Counts counts,List<String> motifs,List<int[]> peakSets,int[][] backgroundPeaks,double[] expectation,
                                 Profile profile,Limits limits,BooleanSupplier cancelled) {
        Objects.requireNonNull(profile);Objects.requireNonNull(motifs);Objects.requireNonNull(peakSets);Budget b=new Budget(limits,cancelled);admit(counts,limits);
        if(motifs.isEmpty()||motifs.size()!=peakSets.size()||motifs.size()>limits.annotations||2L*motifs.size()*counts.cells.size()>limits.outputValues)throw new IllegalArgumentException("Invalid/excessive chromVAR annotation/output dimensions");
        for(double total:counts.peakTotals){b.add(1);if(total<=0)throw new IllegalArgumentException("All peaks must have at least one fragment in one sample");}
        long members=0;for(int[] set:peakSets){Objects.requireNonNull(set);members+=set.length;if(members>limits.annotationIndices)throw new IllegalArgumentException("Too many chromVAR annotation indices");}for(String motif:motifs)SignacFragments.identity(motif);
        Context context=context(counts,backgroundPeaks,expectation,limits,b);double[][] z=new double[motifs.size()][],dev=new double[motifs.size()][];double[] matches=new double[motifs.size()],overlap=new double[motifs.size()];List<String> warnings=warnings(profile);int masked=0;
        for(int i=0;i<motifs.size();i++){Single result=compute(context,peakSets.get(i),1,false,profile,limits,b);z[i]=result.z;dev[i]=result.deviations;matches[i]=result.fractionMatches;overlap[i]=result.fractionBackgroundOverlap;masked+=result.thresholdMaskedCells;}
        if(counts.cells.size()==1&&motifs.size()>1)throw new IllegalArgumentException("Original one-cell multiple-annotation vapply/transpose result has incompatible dimnames");
        if(masked>0)warnings.add("SOURCE_THRESHOLD_NA: "+masked+" annotation/cell entries have expected counts below the original threshold one.");if(nonfinite(z)+nonfinite(dev)>0)warnings.add("SOURCE_NONFINITE_DEVIATIONS: source NA/NaN/Inf are retained; review expected counts and background variance.");b.check();return new Result(profile,motifs,counts.cells,z,dev,matches,overlap,b.used,warnings);
    }

    private record Context(Counts counts,int[][] background,double[] expectation,int iterations) { }
    private record Weights(int[] rows,int[] multiplicities,int count) { }
    private static Context context(Counts counts,int[][] background,double[] expectation,Limits limits,Budget b) {
        admit(counts,limits);Objects.requireNonNull(background);Objects.requireNonNull(expectation);int p=counts.peaks.size();if(background.length!=p||expectation.length!=p)throw new IllegalArgumentException("Background and expectation rows must match every peak");int iterations=background[0].length;if((long)p*iterations>limits.backgroundValues)throw new IllegalArgumentException("Background index matrix exceeds capacity");int[][] copy=new int[p][];
        for(int row=0;row<p;row++){if(background[row]==null||background[row].length!=iterations)throw new IllegalArgumentException("Ragged chromVAR background matrix");copy[row]=background[row].clone();for(int index:copy[row]){b.add(1);if(index<1||index>p)throw new IllegalArgumentException("Background indices must be one-based peak indices in range");}}
        double[] e=expectation.clone();for(int i=0;i<e.length;i++){b.add(1);if(kind(e[i])==Kind.NAN)e[i]=Double.NaN;}return new Context(counts,copy,e,iterations);
    }
    private static Single compute(Context context,int[] requested,double threshold,boolean intermediates,Profile profile,Limits limits,Budget b) {
        Objects.requireNonNull(requested);Counts counts=context.counts;int cells=counts.cells.size(),iterations=context.iterations;if(requested.length>limits.annotationIndices||2L*cells>limits.outputValues)throw new IllegalArgumentException("ChromVAR annotation/output capacity exceeded");requested=requested.clone();
        if(requested.length==0){double[] missing=new double[cells];Arrays.fill(missing,NA);return new Single(profile,missing,missing,0,NA,0,null,b.used,List.of("SOURCE_EMPTY_ANNOTATION: no peaks matched; scores/deviations and background overlap remain NA."));}
        if((long)iterations*requested.length>limits.sampleMemberships)throw new IllegalArgumentException("Sampled annotation memberships exceed capacity");long intermediateValues=3L*iterations*cells+3L*cells;if(intermediates&&intermediateValues>limits.intermediateValues)throw new IllegalArgumentException("Intermediate chromVAR outputs exceed capacity");
        Weights selected=weights(requested,counts.peaks.size(),b);Weights[] sampledWeights=new Weights[iterations];double[] sampledMass=new double[iterations];double overlap=0;
        for(int iteration=0;iteration<iterations;iteration++){int[] sampled=new int[requested.length];for(int i=0;i<requested.length;i++){b.add(1);sampled[i]=context.background[requested[i]-1][iteration];}sampledWeights[iteration]=weights(sampled,counts.peaks.size(),b);sampledMass[iteration]=dot(sampledWeights[iteration],context.expectation,b);for(int i=0;i<sampledWeights[iteration].rows.length;i++){b.add(1);int at=Arrays.binarySearch(selected.rows,sampledWeights[iteration].rows[i]);if(at>=0)overlap+=(double)sampledWeights[iteration].multiplicities[i]*selected.multiplicities[at];}}
        overlap=divide(divide(overlap,iterations),requested.length);double mass=dot(selected,context.expectation,b);double[] z=new double[cells],dev=new double[cells],observed=intermediates?new double[cells]:null,expected=intermediates?new double[cells]:null,raw=intermediates?new double[cells]:null;double[][] sampled=intermediates?new double[iterations][cells]:null,sampledExpected=intermediates?new double[iterations][cells]:null,sampledDeviation=intermediates?new double[iterations][cells]:null;double[] buffer=new double[iterations];int masked=0;
        for(int cell=0;cell<cells;cell++) {
            double count=dot(selected,counts,cell,b),e=multiply(mass,counts.cellTotals[cell]),deviation=divide(subtract(count,e),e);
            for(int iteration=0;iteration<iterations;iteration++){double sample=dot(sampledWeights[iteration],counts,cell,b),sampleExpected=multiply(sampledMass[iteration],counts.cellTotals[cell]);buffer[iteration]=divide(subtract(sample,sampleExpected),sampleExpected);if(intermediates){sampled[iteration][cell]=sample;sampledExpected[iteration][cell]=sampleExpected;sampledDeviation[iteration][cell]=buffer[iteration];}}
            double backgroundMean=requested.length==1&&iterations!=1&&cells!=1?matrixMean(buffer):mean(buffer);dev[cell]=subtract(deviation,backgroundMean);z[cell]=divide(dev[cell],sd(buffer));if(e<threshold){dev[cell]=z[cell]=NA;masked++;}if(intermediates){observed[cell]=count;expected[cell]=e;raw[cell]=deviation;}
        }
        List<String> warnings=warnings(profile);if(masked>0)warnings.add("SOURCE_THRESHOLD_NA: "+masked+" cells have expected counts below the declared source threshold.");if(nonfinite(z)+nonfinite(dev)>0)warnings.add("SOURCE_NONFINITE_DEVIATIONS: NA/NaN/Inf are retained; review expected counts and background variance.");
        Intermediates detail=intermediates?new Intermediates(observed,expected,raw,sampled,sampledExpected,sampledDeviation,requested.length==1&&(iterations==1||cells==1)):null;return new Single(profile,z,dev,(double)requested.length/counts.peaks.size(),overlap,masked,detail,b.used,warnings);
    }
    private static Weights weights(int[] indices,int peaks,Budget b){TreeMap<Integer,Integer> counts=new TreeMap<>();for(int index:indices){b.add(1);if(index<1||index>peaks)throw new IllegalArgumentException("Annotation indices must be one-based peak indices in range");counts.merge(index-1,1,Integer::sum);}int[] rows=new int[counts.size()],weights=new int[counts.size()];int at=0;for(var entry:counts.entrySet()){b.add(1);rows[at]=entry.getKey();weights[at++]=entry.getValue();}return new Weights(rows,weights,indices.length);}
    private static double dot(Weights w,double[] values,Budget b){double sum=0;for(int i=0;i<w.rows.length;i++){b.add(1);sum=add(sum,multiply(values[w.rows[i]],w.multiplicities[i]));}return sum;}
    private static double dot(Weights w,Counts counts,int cell,Budget b){double sum=0;for(int i=0;i<w.rows.length;i++){b.add(1);sum=add(sum,multiply(counts.value(w.rows[i],cell,b),w.multiplicities[i]));}return sum;}
    private static double matrixMean(double[] values){double sum=0;for(double value:values)sum=add(sum,value);return divide(sum,values.length);}
    private static double mean(double[] values){if(values.length==0)return Double.NaN;double plain=0;boolean nonfinite=false;for(double value:values){plain=add(plain,value);nonfinite|=!Double.isFinite(value)||!Double.isFinite(plain);}if(nonfinite)return divide(plain,values.length);boolean same=true;double first=values[0],sum=0,correction=0;for(double x:values){if(Double.doubleToLongBits(x)!=Double.doubleToLongBits(first))same=false;double next=sum+x;correction+=Math.abs(sum)>=Math.abs(x)?(sum-next)+x:(x-next)+sum;sum=next;}if(same)return first;return divide(add(sum,correction),values.length);}
    private static double sd(double[] values){if(values.length<2)return NA;for(double value:values)if(Double.isNaN(value))return NA;double center=mean(values);double correction=0;for(double value:values)correction+=value-center;center+=correction/values.length;double sum=0;for(double value:values){double d=value-center;sum+=d*d;}return clean(Math.sqrt(sum/(values.length-1)));}
    private static double clean(double v){return Double.isNaN(v)?Double.NaN:v;}
    private static double add(double a,double b){return Double.isNaN(a)?a:Double.isNaN(b)?b:clean(a+b);}
    private static double subtract(double a,double b){return Double.isNaN(a)?a:Double.isNaN(b)?b:clean(a-b);}
    private static double multiply(double a,double b){return Double.isNaN(a)?a:Double.isNaN(b)?b:clean(a*b);}
    private static double divide(double a,double b){return Double.isNaN(a)?a:Double.isNaN(b)?b:clean(a/b);}
    private static void admit(Counts counts,Limits limits){Objects.requireNonNull(counts);Objects.requireNonNull(limits);if(counts.peaks.size()>limits.peaks||counts.cells.size()>limits.cells||counts.values.length>limits.storedValues)throw new IllegalArgumentException("Count matrix exceeds these chromVAR capacities");}
    private static List<String> identities(List<String> input,Budget b){Set<String> seen=new HashSet<>();List<String> result=new ArrayList<>(input.size());for(String id:input){b.add(1);SignacFragments.identity(id);if(!seen.add(id))throw new IllegalArgumentException("Repeated chromVAR count identity");result.add(id);}return List.copyOf(result);}
    private static List<String> warnings(Profile profile){return new ArrayList<>(List.of("EXPLICIT_PREPARED_BACKGROUND: native "+profile+" arithmetic uses the supplied peaks-by-iterations index matrix; no GC/intensity sampling, motif scanning or historical R runtime execution is performed."));}
    private static long nonfinite(double[] x){long count=0;for(double v:x)if(!Double.isFinite(v))count++;return count;}
    private static long nonfinite(double[][] x){long count=0;for(double[] row:x)count+=nonfinite(row);return count;}
    private static double[][] copy(double[][] x){return Arrays.stream(x).map(double[]::clone).toArray(double[][]::new);}
    private static final class Budget {private final long maximum;private final BooleanSupplier cancelled;private long used;Budget(Limits limits,BooleanSupplier cancelled){maximum=Objects.requireNonNull(limits).maximumWork;this.cancelled=Objects.requireNonNull(cancelled);check();}void add(long n){if(n<0||n>maximum-used)throw new IllegalArgumentException("chromVAR work capacity exceeded");used+=n;if((used&4095)==0)check();}void check(){SignacFragments.check(cancelled);}}
}
