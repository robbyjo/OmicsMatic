/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

/**
 * Source-specific pySCENIC0.12.1/ctxcore0.2.0 cell activity scoring. This is
 * separate from {@link SignatureScores}' deterministic-tie R-style AUCell.
 * NumPy1.23.5 RandomState scalar seeding/shuffle and pandas1.5.3 rank semantics
 * are explicit. Expression NaN is ranked at the bottom, never imputed.
 * Original GPL sources and NumPy notices accompany this profile's resources.
 * Coexpression fitting, motif pruning, binarization and SCENIC+ are separate.
 */
public final class PyScenicAucell {
    private PyScenicAucell() { }
    public static final String PROFILE="pyscenic-0.12.1-ctxcore-0.2.0-numpy-1.23.5-aucell-v1";
    /** Serial pandas.unstack sorts both axes; the multiprocessing branch preserves input axes. */
    public enum Layout { SERIAL_UNSTACK, MULTIPROCESS_INPUT_ORDER }
    public enum Status { COMPUTED, SOURCE_ZERO_INSUFFICIENT_GENE_COVERAGE, SOURCE_ZERO_EMPTY_RECOVERY_WINDOW, NONFINITE_NORMALIZATION, NEGATIVE_WEIGHT_DIAGNOSTIC }
    public record Limits(long maxEntries,long maxWork,long maxEstimatedBytes) {
        public Limits { require(maxEntries>0&&maxWork>0&&maxEstimatedBytes>0,"Positive explicit resource limits required"); }
        public static Limits defaults(){return new Limits(32_000_000,2_000_000_000L,1_073_741_824);}
    }
    /** Preserve supplied weights, including nonfinite values in ignored/low-coverage source branches. */
    public record Signature(String name,Map<String,Double> geneWeights) {
        public Signature {
            label(name);require(geneWeights!=null&&!geneWeights.isEmpty(),"Nonempty gene signature required");
            var copy=new LinkedHashMap<String,Double>();
            for(var entry:geneWeights.entrySet()){label(entry.getKey());require(entry.getValue()!=null,"Numeric gene weight required");copy.put(entry.getKey(),entry.getValue());}
            geneWeights=Collections.unmodifiableMap(copy);
        }
    }
    /** The fraction is source-validated only when a signature passes its80-percent coverage branch. */
    public record Options(double aucFraction,boolean noWeights,boolean normalize,long seed,Layout layout,Limits limits) {
        public Options { require(seed>=0&&seed<=0xffff_ffffL,"Explicit uint32 NumPy RandomState seed required");require(layout!=null&&limits!=null,"Explicit layout/limits required"); }
        public static Options defaults(long seed){return new Options(.05,false,false,seed,Layout.SERIAL_UNSTACK,Limits.defaults());}
    }
    /** Full zero-based ranks, in the original source's shuffled column order. */
    public static final class Ranked {
        private final List<String> cells,genes;private final int[][] ranks;private final int[] permutation;
        private Ranked(List<String> cells,List<String> genes,int[][] ranks,int[] permutation){this.cells=List.copyOf(cells);this.genes=List.copyOf(genes);this.ranks=copy(ranks);this.permutation=permutation.clone();}
        public List<String> cells(){return cells;} public List<String> genes(){return genes;}
        public int[][] ranks(){return copy(ranks);} public int[] permutation(){return permutation.clone();}
    }
    public record Regulon(String name,int signatureGenes,int presentGenes,double coverage,boolean cutoffApplied,
                          Integer reducedRankCutoff,double rawColumnMaximum,boolean negativeEffectiveWeights,Status status) { }
    public static final class Result {
        private final List<String> cells,regulons;private final double[][] raw,scores;private final List<Regulon> diagnostics;
        private Result(List<String> cells,List<String> regulons,double[][] raw,double[][] scores,List<Regulon> diagnostics){
            this.cells=List.copyOf(cells);this.regulons=List.copyOf(regulons);this.raw=copy(raw);this.scores=copy(scores);this.diagnostics=List.copyOf(diagnostics);
        }
        public List<String> cells(){return cells;}public List<String> regulons(){return regulons;}
        public double[][] raw(){return copy(raw);}public double[][] scores(){return copy(scores);}
        public List<Regulon> diagnostics(){return diagnostics;}
        /** Source placeholders and nonfinite/negative-weight diagnostics do not establish valid activity inference. */
        public boolean eligible(int cell,int regulon){return diagnostics.get(regulon).status()==Status.COMPUTED&&Double.isFinite(scores[cell][regulon]);}
    }

    /** Seeded pandas.sample(axis=1), descending method=first ranks, na_option=bottom, uint32 then minus1. */
    public static Ranked rank(List<String> cells,List<String> genes,double[][] expression,long seed,Limits limits) {
        axes(cells,genes);require(expression!=null&&expression.length==cells.size(),"Expression row count differs from cells");
        require(seed>=0&&seed<=0xffff_ffffL,"Explicit uint32 NumPy RandomState seed required");
        budget(cells.size(),genes.size(),0,limits,true);int[] permutation=permutation(genes.size(),seed);var shuffled=new ArrayList<String>();
        for(int index:permutation)shuffled.add(genes.get(index));int[][] ranks=new int[cells.size()][];
        for(int row=0;row<cells.size();row++){
            interrupted();double[] values=expression[row];require(values!=null&&values.length==genes.size(),"Ragged expression matrix");
            Integer[] order=new Integer[genes.size()];for(int j=0;j<order.length;j++)order[j]=j;
            Arrays.sort(order,(a,b)->{
                double x=values[permutation[a]],y=values[permutation[b]];
                if(Double.isNaN(x))return Double.isNaN(y)?Integer.compare(a,b):1;
                if(Double.isNaN(y))return -1;
                return x==y?Integer.compare(a,b):x>y?-1:1;
            });
            ranks[row]=new int[genes.size()];for(int at=0;at<order.length;at++)ranks[row][order[at]]=at;
        }
        return new Ranked(cells,shuffled,ranks,permutation);
    }
    /** Accept explicit prepared source ranks; every row must be a permutation of0..N-1. */
    public static Ranked prepared(List<String> cells,List<String> genes,int[][] ranks,Limits limits) {
        axes(cells,genes);budget(cells.size(),genes.size(),0,limits,false);require(ranks!=null&&ranks.length==cells.size(),"Rank row count differs from cells");
        for(int[] row:ranks){interrupted();require(row!=null&&row.length==genes.size(),"Ragged rank matrix");boolean[] seen=new boolean[genes.size()];for(int value:row){require(value>=0&&value<seen.length&&!seen[value],"Each rank row must permute0..N-1");seen[value]=true;}}
        int[] identity=new int[genes.size()];for(int i=0;i<identity.length;i++)identity[i]=i;
        return new Ranked(cells,genes,ranks,identity);
    }
    public static Result scoreExpression(List<String> cells,List<String> genes,double[][] expression,List<Signature> signatures,Options options) {
        require(options!=null,"Explicit options required");axes(cells,genes);
        double rankingWork=(double)cells.size()*genes.size()*Math.max(1,Math.log(genes.size())/Math.log(2));
        require(rankingWork<options.limits().maxWork(),"Combined ranking/scoring work cap exceeded");
        Ranked ranked=rank(cells,genes,expression,options.seed(),options.limits());
        Limits remaining=new Limits(options.limits().maxEntries(),(long)Math.floor(options.limits().maxWork()-rankingWork),options.limits().maxEstimatedBytes());
        return score(ranked,signatures,new Options(options.aucFraction(),options.noWeights(),options.normalize(),options.seed(),options.layout(),remaining));
    }
    /** Source coverage check precedes cutoff/weight validation; normalization retains division-by-zero NaN/Inf. */
    public static Result score(Ranked ranked,List<Signature> signatures,Options options) {
        require(ranked!=null&&signatures!=null&&!signatures.isEmpty()&&options!=null,"Ranks, nonempty signatures and explicit options required");
        int n=ranked.cells.size(),g=ranked.genes.size(),r=signatures.size();budget(n,g,r,options.limits(),false);
        var names=new ArrayList<String>();for(Signature signature:signatures){require(signature!=null,"Missing signature");names.add(signature.name());}unique(names);
        double[][] raw=new double[n][r],scores=new double[n][r];var diagnostics=new ArrayList<Regulon>();double work=(double)n*g+(double)g*r+2d*n*r;
        for(int col=0;col<r;col++){
            interrupted();Signature signature=signatures.get(col);var indices=new ArrayList<Integer>();var weights=new ArrayList<Double>();boolean negative=false;
            for(int j=0;j<g;j++)if(signature.geneWeights().containsKey(ranked.genes.get(j))){double weight=options.noWeights()?1:signature.geneWeights().get(ranked.genes.get(j));indices.add(j);weights.add(weight);negative|=weight<0;}
            int present=indices.size();double coverage=(double)present/signature.geneWeights().size();boolean covered=present>0&&coverage>=.80;
            work+=(double)n*present*Math.max(1,Math.log(Math.max(1,present))/Math.log(2));
            require(work<=options.limits().maxWork(),"Observed signature scoring work cap exceeded");
            Integer cutoff=null;Status status=covered?(negative?Status.NEGATIVE_WEIGHT_DIAGNOSTIC:Status.COMPUTED):Status.SOURCE_ZERO_INSUFFICIENT_GENE_COVERAGE;
            if(covered){
                double fraction=options.aucFraction();require(Double.isFinite(fraction)&&fraction>0&&fraction<=1,"Source AUC threshold must be in(0,1]");
                int rounded=(int)Math.rint(fraction*g);require(rounded>0&&rounded<g,"Source rounded cutoff must be positive and below genome size");cutoff=rounded-1;
                if(cutoff==0&&!negative)status=Status.SOURCE_ZERO_EMPTY_RECOVERY_WINDOW;
                double[] w=new double[present];for(int j=0;j<present;j++)w[j]=weights.get(j);
                for(int row=0;row<n;row++){
                    interrupted();double[] ranks=new double[present];for(int j=0;j<present;j++)ranks[j]=ranked.ranks[row][indices.get(j)];
                    raw[row][col]=CisTargetRecovery.ctxcoreDirectAuc(ranks,w,g,fraction);
                }
            }
            double max=Double.NEGATIVE_INFINITY;for(int row=0;row<n;row++)max=Math.max(max,raw[row][col]);
            boolean nonfinite=false;for(int row=0;row<n;row++){scores[row][col]=options.normalize()?raw[row][col]/max:raw[row][col];nonfinite|=!Double.isFinite(scores[row][col]);}
            if(covered&&nonfinite)status=Status.NONFINITE_NORMALIZATION;
            diagnostics.add(new Regulon(signature.name(),signature.geneWeights().size(),present,coverage,covered,cutoff,max,negative,status));
        }
        if(options.layout()==Layout.MULTIPROCESS_INPUT_ORDER)return new Result(ranked.cells,names,raw,scores,diagnostics);
        Integer[] cells=sorted(ranked.cells),modules=sorted(names);var orderedCells=new ArrayList<String>();var orderedModules=new ArrayList<String>();var orderedDiagnostics=new ArrayList<Regulon>();
        double[][] orderedRaw=new double[n][r],orderedScores=new double[n][r];
        for(int row=0;row<n;row++){orderedCells.add(ranked.cells.get(cells[row]));for(int col=0;col<r;col++){orderedRaw[row][col]=raw[cells[row]][modules[col]];orderedScores[row][col]=scores[cells[row]][modules[col]];}}
        for(int col:modules){orderedModules.add(names.get(col));orderedDiagnostics.add(diagnostics.get(col));}
        return new Result(orderedCells,orderedModules,orderedRaw,orderedScores,orderedDiagnostics);
    }

    /** NumPy1.23.5 RandomState scalar-seeded MT19937 and masked interval Fisher-Yates. */
    public static int[] permutation(int size,long seed) {
        require(size>=0&&size<=10_000_000&&seed>=0&&seed<=0xffff_ffffL,"Bounded size and uint32 seed required");
        int[] values=new int[size];for(int i=0;i<size;i++)values[i]=i;var random=new Mt(seed);
        for(int i=size-1;i>0;i--){interrupted();int mask=i;mask|=mask>>>1;mask|=mask>>>2;mask|=mask>>>4;mask|=mask>>>8;mask|=mask>>>16;int j;do{j=random.next()&mask;}while(j>i);int old=values[i];values[i]=values[j];values[j]=old;}
        return values;
    }
    private static final class Mt {
        private final int[] state=new int[624];private int at=624;
        Mt(long seed){state[0]=(int)seed;for(int i=1;i<624;i++)state[i]=1812433253*(state[i-1]^(state[i-1]>>>30))+i;}
        int next(){
            if(at==624){int k=0,y;for(;k<227;k++){y=(state[k]&0x80000000)|(state[k+1]&0x7fffffff);state[k]=state[k+397]^(y>>>1)^((y&1)==0?0:0x9908b0df);}for(;k<623;k++){y=(state[k]&0x80000000)|(state[k+1]&0x7fffffff);state[k]=state[k-227]^(y>>>1)^((y&1)==0?0:0x9908b0df);}y=(state[623]&0x80000000)|(state[0]&0x7fffffff);state[623]=state[396]^(y>>>1)^((y&1)==0?0:0x9908b0df);at=0;}
            int y=state[at++];y^=y>>>11;y^=(y<<7)&0x9d2c5680;y^=(y<<15)&0xefc60000;y^=y>>>18;return y;
        }
    }
    private static void budget(int cells,int genes,int signatures,Limits limits,boolean sortRanks){
        require(limits!=null,"Explicit resource limits required");long entries=(long)cells*genes,outputs=(long)cells*signatures;
        require(entries<=limits.maxEntries()&&outputs<=limits.maxEntries(),"Cell/gene or output entry cap exceeded");
        double log=Math.max(1,Math.log(genes)/Math.log(2)),work=entries*(sortRanks?log:1);
        require(work<=limits.maxWork(),"Estimated ranking/scoring work cap exceeded");
        require(entries*24d+outputs*64d+genes*64d<=limits.maxEstimatedBytes(),"Estimated input/rank/result storage cap exceeded");interrupted();
    }
    private static Integer[] sorted(List<String> labels){Integer[] order=new Integer[labels.size()];for(int i=0;i<order.length;i++)order[i]=i;Arrays.sort(order,(a,b)->scalarCompare(labels.get(a),labels.get(b)));return order;}
    private static int scalarCompare(String a,String b){int i=0,j=0;while(i<a.length()&&j<b.length()){int x=a.codePointAt(i),y=b.codePointAt(j);if(x!=y)return Integer.compare(x,y);i+=Character.charCount(x);j+=Character.charCount(y);}return Integer.compare(a.length()-i,b.length()-j);}
    private static void axes(List<String> cells,List<String> genes){require(cells!=null&&genes!=null&&!cells.isEmpty()&&!genes.isEmpty(),"Nonempty cell/gene axes required");unique(cells);unique(genes);}
    private static void unique(List<String> values){var seen=new HashSet<String>();for(String value:values){label(value);require(seen.add(value),"Duplicate literal identifier: "+value);}}
    private static void label(String value){require(value!=null&&!value.isEmpty(),"Nonempty literal identifier required");}
    private static void interrupted(){if(Thread.currentThread().isInterrupted())throw new CancellationException("Native pySCENIC AUCell interrupted; no complete result");}
    private static int[][] copy(int[][] values){int[][] out=new int[values.length][];for(int i=0;i<out.length;i++)out[i]=values[i].clone();return out;}
    private static double[][] copy(double[][] values){double[][] out=new double[values.length][];for(int i=0;i<out.length;i++)out[i]=values[i].clone();return out;}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
}
