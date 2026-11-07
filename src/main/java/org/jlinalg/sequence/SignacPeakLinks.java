/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import org.jlinalg.genetics.GenomicIntervals;

/** Native independent numerical/geometry audit for original Signac LinkPeaks and captured background draws. */
public final class SignacPeakLinks {
    private SignacPeakLinks() { }
    public enum Method { PEARSON, SPARSE_SPEARMAN }
    public enum Profile { CURRENT_1_17_1, SOURCE_1_14_0 }
    public record Sparse(List<String> features,List<String> cells,int[] pointers,int[] rows,double[] values) {
        public Sparse {
            features=List.copyOf(features);cells=List.copyOf(cells);pointers=pointers.clone();rows=rows.clone();values=values.clone();
            if(features.isEmpty()||cells.size()<2||features.size()>2_000_000||cells.size()>2_000_000||values.length>20_000_000||pointers.length!=cells.size()+1||pointers[0]!=0||pointers[cells.size()]!=values.length||rows.length!=values.length)throw new IllegalArgumentException("Invalid bounded sparse link matrix dimensions");
            for(var ids:List.of(features,cells)){Set<String> seen=new HashSet<>();for(String id:ids){SignacFragments.identity(id);if(!seen.add(id))throw new IllegalArgumentException("Link matrix identities must be unique");}}
            for(int cell=0;cell<cells.size();cell++){if(pointers[cell]<0||pointers[cell+1]<pointers[cell]||pointers[cell+1]>values.length)throw new IllegalArgumentException("Invalid CSC pointers");int previous=-1;for(int k=pointers[cell];k<pointers[cell+1];k++){if(rows[k]<=previous||rows[k]>=features.size()||!Double.isFinite(values[k]))throw new IllegalArgumentException("Require finite values and strictly sorted unique CSC coordinates");previous=rows[k];}}
        }
        @Override public int[] pointers(){return pointers.clone();}@Override public int[] rows(){return rows.clone();}@Override public double[] values(){return values.clone();}
    }
    public record Gene(String chromosome,int start,int end,String strand,String id,String name) {
        public Gene {SignacFragments.identity(chromosome);SignacFragments.identity(id);SignacGeneActivity.strand(strand);if(name!=null&&!name.isEmpty())SignacFragments.identity(name);if(start<1||end<start||end>536_870_912)throw new IllegalArgumentException("Require closed gene coordinates within the TBI domain");}
        public int tss(){return strand.equals("-")?end:start;}
    }
    public record Options(Method method,int distance,Integer minDistance,int minCells,int sample,double pCutoff,double scoreCutoff,boolean geneIds,List<String> genesUse,long maximumWork) {
        public Options {Objects.requireNonNull(method);genesUse=genesUse==null?null:List.copyOf(genesUse);if(distance<0||distance>536_870_912||minCells<0||sample<2||sample>1_000_000||!Double.isFinite(pCutoff)||pCutoff<0||pCutoff>1||!Double.isFinite(scoreCutoff)||scoreCutoff<0||maximumWork<1)throw new IllegalArgumentException("Invalid LinkPeaks method, strict thresholds, background sample or work bound");}
        public static Options defaults(){return new Options(Method.PEARSON,500000,null,10,200,0.05,0.05,false,null,2_000_000_000L);}
    }
    public record Background(String gene,String peak,double coefficient,List<String> peaks,double[] coefficients,double zscore,double pvalue,boolean retained,List<String> pool) {
        public Background {peaks=List.copyOf(peaks);coefficients=coefficients.clone();pool=List.copyOf(pool);}
        @Override public double[] coefficients(){return coefficients.clone();}
    }
    public record Link(String chromosome,int start,int end,String gene,String peak,double coefficient,double zscore,double pvalue) { }
    public record Diagnostics(int eligiblePeaks,int eligibleGenes,int testedPairs,int retainedLinks,double maximumCoefficientError,double maximumZError,double maximumLogPError,long sparseWork,List<String> warnings) {public Diagnostics{warnings=List.copyOf(warnings);}}
    private static final class Budget {
        long used;final long maximum;final BooleanSupplier cancelled;Budget(long maximum,BooleanSupplier cancelled){this.maximum=maximum;this.cancelled=cancelled;}
        void use(long n)throws IOException{SignacFragments.check(cancelled);if(n<0||used>maximum-n)throw new IOException("Native LinkPeaks sparse audit exceeds its explicit work bound");used+=n;}
    }
    private static final class Sum {double value,error;void add(double x){double y=x-error,t=value+y;error=(t-value)-y;value=t;}}
    private static final class Rows {
        final int n;final int[] pointers,cells;final double[] values;final int[] positive;final double[] sum,squares;
        Rows(Sparse matrix,Method method,Budget budget)throws IOException {
            n=matrix.cells.size();int features=matrix.features.size();pointers=new int[features+1];cells=new int[matrix.values.length];values=new double[matrix.values.length];positive=new int[features];sum=new double[features];squares=new double[features];budget.use(matrix.values.length);
            for(int row:matrix.rows)pointers[row+1]++;for(int row=0;row<features;row++)pointers[row+1]+=pointers[row];int[] next=pointers.clone();
            for(int cell=0;cell<n;cell++)for(int k=matrix.pointers[cell];k<matrix.pointers[cell+1];k++){int row=matrix.rows[k],at=next[row]++;cells[at]=cell;values[at]=matrix.values[k];if(values[at]>0)positive[row]++;}
            if(method==Method.SPARSE_SPEARMAN)for(int row=0;row<features;row++){
                int start=pointers[row],length=pointers[row+1]-start;Integer[] order=new Integer[length];for(int i=0;i<length;i++)order[i]=i;budget.use(length);Arrays.sort(order,(a,b)->values[start+a]<values[start+b]?-1:values[start+a]>values[start+b]?1:0);double[] ranks=new double[length];double offset=(n-length-1)/2.0;
                for(int i=0;i<length;){int to=i+1;while(to<length&&values[start+order[to]]==values[start+order[i]])to++;double rank=(i+1+to)/2.0+offset;for(int j=i;j<to;j++)ranks[order[j]]=rank;i=to;}System.arraycopy(ranks,0,values,start,length);
            }
            for(int row=0;row<features;row++){Sum total=new Sum(),squared=new Sum();for(int k=pointers[row];k<pointers[row+1];k++){total.add(values[k]);squared.add(values[k]*values[k]);}sum[row]=total.value;squares[row]=squared.value;}
        }
    }
    private static double correlation(Rows x,int a,Rows y,int b,Budget budget)throws IOException {
        if(x.n!=y.n)throw new IOException("Correlation cell populations differ");int i=x.pointers[a],j=y.pointers[b],endX=x.pointers[a+1],endY=y.pointers[b+1];budget.use((long)endX-i+endY-j);Sum product=new Sum();while(i<endX&&j<endY){if(x.cells[i]<y.cells[j])i++;else if(x.cells[i]>y.cells[j])j++;else product.add(x.values[i++]*y.values[j++]);}
        double mx=x.sum[a]/x.n,my=y.sum[b]/y.n,covariance=(product.value-x.n*mx*my)/(x.n-1),sx=Math.sqrt((x.squares[a]-x.n*mx*mx)/(x.n-1)),sy=Math.sqrt((y.squares[b]-y.n*my*my)/(x.n-1));return covariance/(sx*sy);
    }
    /** Source sparse-rank transformation, including explicit-zero/negative-value behavior, followed by corSparse arithmetic. */
    public static double correlation(double[] x,boolean[] storedX,double[] y,boolean[] storedY,Method method,BooleanSupplier cancelled)throws IOException {
        if(x.length!=y.length||x.length<2||x.length!=storedX.length||y.length!=storedY.length)throw new IllegalArgumentException("Require paired vectors and explicit sparse-storage masks");List<String> names=new ArrayList<>();for(int i=0;i<x.length;i++)names.add("cell"+i);Sparse a=vector(x,storedX,names),b=vector(y,storedY,names);if(method==Method.SPARSE_SPEARMAN&&(a.values.length==0||b.values.length==0))throw new IOException("Original SparsifiedRanks cannot assign an empty column's NULL ranks to its numeric slot");Budget budget=new Budget(100_000_000,cancelled);return correlation(new Rows(a,method,budget),0,new Rows(b,method,budget),0,budget);
    }
    private static Sparse vector(double[] values,boolean[] stored,List<String> cells){int count=0;for(int i=0;i<values.length;i++){if(!stored[i]&&values[i]!=0)throw new IllegalArgumentException("Unstored vector slots must be zero");if(stored[i])count++;}int[] p=new int[values.length+1],rows=new int[count];double[] x=new double[count];int at=0;for(int i=0;i<values.length;i++){if(stored[i])x[at++]=values[i];p[i+1]=at;}return new Sparse(List.of("vector"),cells,p,rows,x);}
    public static double sourceNormalTail(double zscore){return jdistlib.Normal.cumulative(-Math.abs(zscore),0,1,true,false);}
    public static Diagnostics audit(Sparse counts,Sparse expression,List<SignacFragments.Region> peaks,List<Gene> coordinates,Options options,Profile profile,List<Background> captured,List<Link> reported,BooleanSupplier cancelled)throws IOException {
        Objects.requireNonNull(options);Objects.requireNonNull(profile);Budget budget=new Budget(options.maximumWork(),cancelled);if(!counts.cells.equals(expression.cells)||peaks.size()!=counts.features.size())throw new IOException("Peak/expression cells or explicit peak identities disagree");
        for(int i=0;i<peaks.size();i++)if(!peaks.get(i).name().equals(counts.features.get(i)))throw new IOException("Peak IDs must equal canonical chromosome-start-end coordinates in exact matrix row order");
        for(double value:counts.values)if(value<0||value!=Math.floor(value)||value>SignacFragments.MAX_EXACT)throw new IOException("Peak input requires nonnegative exactly representable integer counts");
        Rows x=new Rows(counts,options.method(),budget),y=new Rows(expression,options.method(),budget);Map<String,Integer> peakIndex=index(counts.features),geneIndex=index(expression.features);Set<String> requested=options.genesUse()==null?null:new HashSet<>(options.genesUse());
        List<GenomicIntervals.Interval> intervals=new ArrayList<>();List<Integer> eligiblePeaks=new ArrayList<>();for(int i=0;i<peaks.size();i++)if(x.positive[i]>options.minCells()){var p=peaks.get(i);intervals.add(new GenomicIntervals.Interval(p.chromosome(),p.start()-1L,p.end(),Integer.toString(i),"."));eligiblePeaks.add(i);}
        GenomicIntervals.Index proximity=new GenomicIntervals.Index(intervals);List<Gene> genes=new ArrayList<>();Set<String> unique=new HashSet<>();for(var gene:coordinates){String key=options.geneIds()?gene.id():gene.name();Integer row=geneIndex.get(key);if(row!=null&&y.positive[row]>options.minCells()&&(requested==null||requested.contains(key))){if(!unique.add(key))throw new IOException("Ambiguous repeated gene coordinates for a tested expression feature");genes.add(gene);}}
        if(genes.isEmpty())throw new IOException("No tested expression genes have coordinates after strict min.cells filtering");
        Map<String,Background> backgrounds=new HashMap<>();for(var bg:captured)if(backgrounds.put(key(bg.gene(),bg.peak()),bg)!=null)throw new IOException("Repeated background-audit pair");Map<String,Link> links=new HashMap<>();for(var link:reported)if(links.put(key(link.gene(),link.peak()),link)!=null)throw new IOException("Repeated reported link");
        int tested=0,retained=0,totalNear=0;double coefficientError=0,zError=0,logPError=0;Set<String> seen=new HashSet<>(),seenLinks=new HashSet<>();
        for(var gene:genes){
            budget.use(1);String geneName=options.geneIds()?gene.id():gene.name();int geneRow=geneIndex.get(geneName);Set<Integer> near=near(proximity,gene,options.distance());if(options.minDistance()!=null&&options.minDistance()>0)for(int row:near(proximity,gene,options.minDistance()))if(!near.remove(row))near.add(row);totalNear+=near.size();if(near.size()<2)continue;
            Pattern exclusion=Pattern.compile("^"+gene.chromosome()+(profile==Profile.CURRENT_1_17_1?"-":""));List<String> pool=new ArrayList<>();for(int row:eligiblePeaks)if(!exclusion.matcher(counts.features.get(row)).find())pool.add(counts.features.get(row));Set<String> poolSet=new HashSet<>(pool);
            for(int row=0;row<peaks.size();row++)if(near.contains(row)){
                double coefficient=correlation(x,row,y,geneRow,budget);if(!Double.isFinite(coefficient))throw new IOException("Original correlation is undefined; no LinkPeaks result can be admitted");String pair=key(geneName,counts.features.get(row));Background bg=backgrounds.get(pair);boolean passes=Math.abs(coefficient)>options.scoreCutoff();
                if((bg!=null)!=passes)throw new IOException("Candidate score threshold boundary or source-audit pair inventory differs: "+pair.replace('\0','/'));if(!passes)continue;tested++;seen.add(pair);
                if(bg.peaks.size()!=options.sample()||bg.coefficients.length!=options.sample()||new HashSet<>(bg.peaks).size()!=bg.peaks.size()||!pool.equals(bg.pool)||!poolSet.containsAll(bg.peaks))throw new IOException("Captured background sample/pool violates the explicit source profile");
                coefficientError=Math.max(coefficientError,close(coefficient,bg.coefficient,5e-11,"candidate correlation"));double[] values=new double[options.sample()];
                for(int i=0;i<values.length;i++){Integer backgroundRow=peakIndex.get(bg.peaks.get(i));if(backgroundRow==null)throw new IOException("Unknown sampled background peak");values[i]=correlation(x,backgroundRow,y,geneRow,budget);if(!Double.isFinite(values[i]))throw new IOException("Undefined sampled background correlation");coefficientError=Math.max(coefficientError,close(values[i],bg.coefficients[i],5e-11,"background correlation"));}
                Sum sum=new Sum();for(double value:values)sum.add(value);double mean=sum.value/values.length;Sum squared=new Sum();for(double value:values)squared.add((value-mean)*(value-mean));double sd=Math.sqrt(squared.value/(values.length-1)),z=(coefficient-mean)/sd,p=sourceNormalTail(z);if(Double.isNaN(z)||!Double.isFinite(p))throw new IOException("Undefined background SD/z-score/p-value");
                zError=Math.max(zError,close(z,bg.zscore,5e-9,"z-score"));logPError=Math.max(logPError,closeP(p,bg.pvalue));boolean keep=p<options.pCutoff();if(keep!=bg.retained)throw new IOException("P-value threshold boundary differs between original and independent audit");
                Link link=links.get(pair);if((link!=null)!=keep)throw new IOException("Reported link set differs from strict source p-value filtering");if(!keep)continue;retained++;seenLinks.add(pair);var peak=peaks.get(row);double midpoint=peak.start()+((long)peak.end()-peak.start()+1)/2.0;int start=(int)Math.min(gene.tss(),midpoint),end=(int)Math.max(gene.tss(),midpoint);
                if(!link.chromosome.equals(peak.chromosome())||link.start!=start||link.end!=end)throw new IOException("Reported link range differs from source TSS/peak-midpoint geometry");coefficientError=Math.max(coefficientError,close(coefficient,link.coefficient,5e-11,"reported correlation"));zError=Math.max(zError,close(z,link.zscore,5e-9,"reported z-score"));logPError=Math.max(logPError,closeP(p,link.pvalue));
            }
        }
        if(totalNear==0)throw new IOException("No peaks overlap the source distance threshold");if(!seen.equals(backgrounds.keySet())||!seenLinks.equals(links.keySet()))throw new IOException("Extra captured background or reported link identities");List<String> warnings=new ArrayList<>();warnings.add("SOURCE_ONE_SIDED_NORMAL_TAIL: p=pnorm(-abs(z)), without a factor of two; strict min.cells/score/p-value thresholds and the at-least-two-cis-peaks rule are retained.");
        if(profile==Profile.SOURCE_1_14_0)warnings.add("HISTORICAL_SOURCE_PROFILE: literal1.14 functions use current dependencies, query-only KDE weighting and the chromosome-prefix rule without a hyphen; chr1 excludes chr10 from trans backgrounds. Historical package binaries were not executed.");
        if(options.method()==Method.SPARSE_SPEARMAN)warnings.add("SOURCE_SPARSE_RANKS: stored entries are ranked with the source offset; explicit zeros and negative expression can differ from ordinary dense Spearman. No dense-rank replacement was made.");if(options.minDistance()!=null&&options.minDistance()<0)warnings.add("NEGATIVE_MIN_DISTANCE_IGNORED: the original warns and treats a negative min.distance as no minimum.");if(retained==0)warnings.add("EMPTY_LINK_RESULT: no links passed the requested source thresholds; no cutoff was relaxed.");
        warnings.add("UPSTREAM_BACKGROUND_SAMPLING: Java checks sampled pool membership, correlations and link statistics; captured verification does not replay R KDE or RNG. Source profile, seed records and actual upstream execution remain separate provenance claims.");return new Diagnostics(eligiblePeaks.size(),genes.size(),tested,retained,coefficientError,zError,logPError,budget.used,warnings);
    }
    private static Map<String,Integer> index(List<String> names){Map<String,Integer> result=new HashMap<>();for(int i=0;i<names.size();i++)result.put(names.get(i),i);return result;}
    private static String key(String gene,String peak){return gene+'\0'+peak;}
    private static Set<Integer> near(GenomicIntervals.Index index,Gene gene,int distance){long start=Math.max(0L,(long)gene.tss()-distance-1),end=(long)gene.tss()+distance;Set<Integer> result=new HashSet<>();for(var hit:index.overlaps(new GenomicIntervals.Interval(gene.chromosome(),start,end,"tss","."),false))result.add(Integer.parseInt(hit.id()));return result;}
    private static double close(double expected,double actual,double tolerance,String name)throws IOException{if(expected==actual)return 0;if(!Double.isFinite(expected)||!Double.isFinite(actual)||Math.abs(expected-actual)>tolerance*Math.max(1,Math.abs(expected)))throw new IOException("Independent source "+name+" differs: "+expected+" vs "+actual);return Math.abs(expected-actual);}
    private static double closeP(double expected,double actual)throws IOException{if(expected==actual)return 0;if(!Double.isFinite(actual)||actual<0||actual>0.5||expected==0||actual==0)throw new IOException("Source normal-tail underflow/value differs");double error=Math.abs(Math.log(expected)-Math.log(actual));if(error>1e-6)throw new IOException("Independent source normal-tail log P differs");return error;}
}
