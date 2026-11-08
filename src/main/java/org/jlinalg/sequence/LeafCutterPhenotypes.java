/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.util.*;
import jdistlib.Normal;

/**
 * Java implementation of the preprocessing in original LeafCutter
 * prepare_phenotype_table.py, commit2c9907ef66adf0bfb3092f0ceb6886ee5c046fbb
 * (repository Apache-2.0). Preserve the packaged notice/source profile.
 * Source pseudocount, missingness/variation filters, mean imputation, population
 * scaling and column-wise inverse-normal scores are explicit here. PCA,
 * file compression/indexing, QTL mapping and R/Stan models are separate stages.
 */
public final class LeafCutterPhenotypes {
    private LeafCutterPhenotypes() { }
    public record Counts(long numerator,long denominator) {
        public Counts {require(numerator>=0&&denominator>=0&&numerator<=denominator,"Require physical nonnegative intron/cluster counts with numerator<=denominator; no replacement or rounding");}
    }
    public record Row(String id,List<Counts> counts) {
        public Row {parseId(id);counts=List.copyOf(counts);}
    }
    public record Options(List<String> blacklistedChromosomes) {
        public Options {blacklistedChromosomes=List.copyOf(blacklistedChromosomes);for(String name:blacklistedChromosomes)require(name.indexOf('\0')<0,"Embedded NUL in literal chromosome blacklist");}
        public static Options sourceDefaults(){return new Options(List.of("X","Y"));}
    }
    public record Limits(int maximumSamples,int maximumInputRows,long maximumInputCells,long maximumRankComparisons) {
        public Limits {require(maximumSamples>0&&maximumInputRows>0&&maximumInputCells>0&&maximumRankComparisons>0,"Positive complete-input and ranking capacities required");}
        public static Limits defaults(){return new Limits(100000,1000000,10000000,200000000);}
    }
    public enum Disposition { KEPT,SOURCE_BLACKLISTED_CHROMOSOME,SOURCE_MISSINGNESS_FILTER,SOURCE_LOW_VARIATION_FILTER }
    /** Null ratio entries are the source's pre-imputation missing state; source counts remain retained separately. */
    public record Decision(int ordinal,Row original,Disposition disposition,List<Double> sourceRatios,Integer missing,
                           Double observedMean,Double observedMinimum,Double observedMaximum,Double populationStddev) {
        public Decision {sourceRatios=Collections.unmodifiableList(new ArrayList<>(sourceRatios));}
    }
    public record Kept(int inputOrdinal,String id,String chromosome,String startText,String endText,long start,
                       List<Double> imputedRatios,List<Double> standardized,List<Double> inverseNormalScores,
                       double observedMean,double observedMinimum,double observedMaximum,double scalingMean,double populationStddev) {
        public Kept {imputedRatios=List.copyOf(imputedRatios);standardized=List.copyOf(standardized);inverseNormalScores=List.copyOf(inverseNormalScores);}
    }
    public record Result(List<String> samples,List<Row> originalRows,List<String> chromosomesSeen,List<Decision> decisions,
                         List<Kept> kept,List<String> warnings) {
        public Result {samples=List.copyOf(samples);originalRows=List.copyOf(originalRows);chromosomesSeen=List.copyOf(chromosomesSeen);decisions=List.copyOf(decisions);kept=List.copyOf(kept);warnings=List.copyOf(warnings);}
        /** Source QQ-normal file order; the PCA input itself keeps the original retained input order. */
        public List<Kept> sortedForSourceFiles(){var rows=new ArrayList<>(kept);rows.sort(Comparator.comparing(Kept::chromosome,LeafCutterPhenotypes::compareText).thenComparingLong(Kept::start).thenComparing(r->r.chromosome()+"\t"+r.startText()+"\t"+r.endText()+"\t"+r.id(),LeafCutterPhenotypes::compareText));return List.copyOf(rows);}
    }
    private record Id(String chromosome,String startText,String endText,long start) { }

    /**
     * Compute the complete retained phenotype family. Original counts/IDs and
     * each filtering decision are retained. Input is a physical integer-count
     * matrix with unique nonempty sample/row IDs; malformed/ambiguous inputs
     * outside this profile are rejected rather than silently repaired. No Result
     * is returned when source preprocessing is undefined or a capacity is exceeded.
     */
    public static Result compute(List<String> samples,List<Row> input,Options options,Limits limits) {
        samples=List.copyOf(samples);input=List.copyOf(input);Objects.requireNonNull(options);Objects.requireNonNull(limits);require(!samples.isEmpty()&&samples.size()<=limits.maximumSamples(),"Empty or capacity-exceeding complete sample family");require(input.size()<=limits.maximumInputRows()&&(long)input.size()<=limits.maximumInputCells()/samples.size(),"Complete phenotype row/cell capacity exceeded; no partial Result");var sampleIds=new HashSet<String>();for(String name:samples){dataToken(name,"sample");require(!name.equals("chrom")&&sampleIds.add(name),"Duplicate/ambiguous sample header; source dictionary reuse is outside this native valid-header profile");}
        var ids=new HashSet<String>();var chromosomes=new LinkedHashSet<String>();for(Row row:input){require(ids.add(row.id()),"Duplicate intron ID; source ambiguity is outside this native profile");require(row.counts().size()==samples.size(),"Ragged source count family; no sample may be omitted");chromosomes.add(parseId(row.id()).chromosome());}
        int n=samples.size();var decisions=new ArrayList<Decision>();var kept=new ArrayList<Kept>();boolean largeCounts=false;
        for(int ordinal=0;ordinal<input.size();ordinal++){
            Row row=input.get(ordinal);Id id=parseId(row.id());if(options.blacklistedChromosomes().contains(id.chromosome())){decisions.add(new Decision(ordinal,row,Disposition.SOURCE_BLACKLISTED_CHROMOSOME,List.of(),null,null,null,null,null));continue;}
            var ratios=new ArrayList<Double>();var observed=new ArrayList<Double>();int missing=0;
            for(Counts counts:row.counts()){
                largeCounts|=counts.numerator()>9007199254740992L||counts.denominator()>9007199254740992L;
                if(counts.denominator()<1){ratios.add(null);missing++;}
                else {double value=((double)counts.numerator()+0.5)/((double)counts.denominator()+0.5);ratios.add(value);observed.add(value);}
            }
            if(missing>n*0.4){decisions.add(new Decision(ordinal,row,Disposition.SOURCE_MISSINGNESS_FILTER,ratios,missing,null,null,null,null));continue;}
            double[] obs=observed.stream().mapToDouble(Double::doubleValue).toArray();double mean=sum(obs,0,obs.length)/obs.length,min=observed.stream().mapToDouble(Double::doubleValue).min().orElseThrow(),max=observed.stream().mapToDouble(Double::doubleValue).max().orElseThrow();double[] values=new double[n];for(int i=0;i<n;i++)values[i]=ratios.get(i)==null?mean:ratios.get(i);double scalingMean=sum(values,0,n)/n;double[] residuals=new double[n],squares=new double[n];for(int i=0;i<n;i++){residuals[i]=values[i]-scalingMean;squares[i]=residuals[i]*residuals[i];}double sd=Math.sqrt(sum(squares,0,n)/n);
            if(sd<0.005){decisions.add(new Decision(ordinal,row,Disposition.SOURCE_LOW_VARIATION_FILTER,ratios,missing,mean,min,max,sd));continue;}
            // sklearn.preprocessing.scale on the finite valid-ratio profile
            // uses population stddev. Its zero/near-constant guard is unreachable
            // after this source's retained-variation threshold.
            double[] scaled=new double[n];for(int i=0;i<n;i++)scaled[i]=residuals[i]/sd;decisions.add(new Decision(ordinal,row,Disposition.KEPT,ratios,missing,mean,min,max,sd));kept.add(new Kept(ordinal,row.id(),id.chromosome(),id.startText(),id.endText(),id.start(),boxed(values),boxed(scaled),List.of(),mean,min,max,scalingMean,sd));
        }
        require(!kept.isEmpty(),"Source phenotype preprocessing is undefined with no retained introns; preserve all inputs/decisions and correct the named filters or data, not a subset");int m=kept.size();double[][] normalized=new double[m][n];long[] comparisons={0};
        for(int column=0;column<n;column++){
            final int j=column;Integer[] order=new Integer[m];for(int i=0;i<m;i++)order[i]=i;Arrays.sort(order,(a,b)->{require(comparisons[0]<limits.maximumRankComparisons(),"Complete inverse-normal ranking capacity exceeded; no partial Result");comparisons[0]++;return Double.compare(kept.get(a).standardized().get(j),kept.get(b).standardized().get(j));});double offset=m<=10?3.0/8.0:0.5;
            for(int first=0;first<m;){int last=first+1;double value=kept.get(order[first]).standardized().get(column);while(last<m&&kept.get(order[last]).standardized().get(column)==value)last++;double rank=(first+1.0+last)/2.0;double probability=(rank-offset)/(m+1.0-2.0*offset);double score=Normal.quantile(probability,0,1,true,false);require(Double.isFinite(score),"Nonfinite inverse-normal score; no replacement or partial Result");for(int i=first;i<last;i++)normalized[order[i]][column]=score;first=last;}
        }
        var complete=new ArrayList<Kept>();for(int i=0;i<m;i++){var row=kept.get(i);complete.add(new Kept(row.inputOrdinal(),row.id(),row.chromosome(),row.startText(),row.endText(),row.start(),row.imputedRatios(),row.standardized(),boxed(normalized[i]),row.observedMean(),row.observedMinimum(),row.observedMaximum(),row.scalingMean(),row.populationStddev()));}
        var warnings=new ArrayList<String>();warnings.add("Original count matrix and per-row decisions remain retained. Derived values follow the fixed source0.5 pseudocount on numerator AND denominator, strict missingness>40% and population SD<0.005 filters, observed-mean imputation, population row scaling and column-wise average-tie inverse-normal ranks. This is not a generic rank-target quantile normalization or per-intron inverse-normal transform.");warnings.add("The source default blacklist matches literal X and Y; chrX/chrY are different strings and are not silently removed. Source QQ file output sorting is separate from the input-order normalized matrix used for original PCA. Original counts/IDs, including zero denominators, are not overwritten.");warnings.add("Native preprocessing component only: no PCA, original Python execution, compression/indexing, QTL association or R/Stan differential model is run by this API. Invalid/ambiguous/negative/overflow/capacity inputs return no Result. Read the complete ledger and retry the whole call after correcting the named cause; never omit a failing row/sample or infer a paper threshold.");if(largeCounts)warnings.add("Some original counts exceed2^53. They remain exact int64 in originalRows; the original phenotype algorithm converts counts to float64 before its pseudocount and division. Derived values retain that source rounding rather than pretending integer precision survived the conversion.");return new Result(samples,input,new ArrayList<>(chromosomes),decisions,complete,warnings);
    }
    private static List<Double> boxed(double[] values){var result=new ArrayList<Double>(values.length);for(double value:values)result.add(value);return List.copyOf(result);}
    /** NumPy1.26 contiguous float64 pairwise reduction, including its8-lane/128-element split order. */
    private static double sum(double[] values,int start,int length){
        if(length<8){double result=-0.0;for(int i=0;i<length;i++)result+=values[start+i];return result;}
        if(length<=128){double[] r=Arrays.copyOfRange(values,start,start+8);int i=8;for(;i<length-length%8;i+=8)for(int k=0;k<8;k++)r[k]+=values[start+i+k];double result=((r[0]+r[1])+(r[2]+r[3]))+((r[4]+r[5])+(r[6]+r[7]));for(;i<length;i++)result+=values[start+i];return result;}
        int half=length/2;half-=half%8;return sum(values,start,half)+sum(values,start+half,length-half);
    }
    private static Id parseId(String id){dataToken(id,"intron");String[] f=id.split(":",-1);require(f.length==4&&!f[3].isEmpty(),"Require original chromosome:start:end:cluster intron ID");require(!f[0].isEmpty()&&f[0].codePoints().allMatch(c->Character.isLetterOrDigit(c)||c=='_'||c=='.'||c=='-'),"Chromosome must have a portable unmodified source output filename; no identifier repair");long start=Long.parseLong(f[1]),end=Long.parseLong(f[2]);require(start>=0&&end>=start,"Nonnegative ordered source coordinates required; original coordinate text retained");return new Id(f[0],f[1],f[2],start);}
    private static int compareText(String left,String right){int i=0,j=0;while(i<left.length()&&j<right.length()){int a=left.codePointAt(i),b=right.codePointAt(j);if(a!=b)return Integer.compare(a,b);i+=Character.charCount(a);j+=Character.charCount(b);}return Integer.compare(left.length()-i,right.length()-j);}
    private static void dataToken(String value,String name){Objects.requireNonNull(value,name);require(!value.isEmpty()&&value.codePoints().noneMatch(c->Character.isWhitespace(c)||Character.isSpaceChar(c)||c==0||(c>=0xd800&&c<=0xdfff)),"Invalid empty/whitespace/NUL/unpaired-surrogate "+name+" identifier; no normalization");}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
}
