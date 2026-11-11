/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.singlecell;

import java.util.*;
import jdistlib.Poisson;

/** Independent ambient-profile arithmetic, non-expression Poisson/BH masks and global Poisson MLE.
 * This does not replay automatic gamma/density estimation or count correction. */
public final class SoupXAmbient {
    private SoupXAmbient() { }
    private static final long EXACT_INTEGER=9007199254740991L;
    public record Entry(int gene,int cell,long count) { }
    public static final class Budget {
        private final long maximum;private long used;
        public Budget(long maximum){if(maximum<1)throw new IllegalArgumentException("Require positive ambient-operation capacity");this.maximum=maximum;}
        public long used(){return used;}
        void use(){if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Ambient calculation interrupted");if(used==maximum)throw new IllegalStateException("Ambient operation capacity exceeded");used++;}
    }
    /** Complete axes and unique coordinates; explicit zeros remain in entries. */
    public static final class Counts {
        private final List<String> genes,cells;private final List<Entry> entries;private final Map<Long,Long> counts;private final Map<String,Integer> geneIndex,cellIndex;private final long[] columnTotals;
        public Counts(List<String> genes,List<String> cells,List<Entry> entries,Budget budget){
            this.genes=axis(genes,budget);this.cells=axis(cells,budget);Objects.requireNonNull(entries,"entries");if(this.genes.isEmpty()||this.cells.isEmpty())throw new IllegalArgumentException("Require complete nonempty count axes");geneIndex=index(this.genes);cellIndex=index(this.cells);columnTotals=new long[cells.size()];var map=new HashMap<Long,Long>();long[] rows=new long[genes.size()];
            for(Entry e:entries){budget.use();Objects.requireNonNull(e,"entry");if(e.gene<0||e.gene>=genes.size()||e.cell<0||e.cell>=cells.size()||e.count<0||e.count>EXACT_INTEGER)throw new IllegalArgumentException("Invalid exact nonnegative integer coordinate/count");if(map.putIfAbsent((long)e.gene*cells.size()+e.cell,e.count)!=null)throw new IllegalArgumentException("Repeated sparse coordinate; no implicit sum or overwrite");columnTotals[e.cell]=add(columnTotals[e.cell],e.count);rows[e.gene]=add(rows[e.gene],e.count);}
            this.entries=List.copyOf(entries);counts=Collections.unmodifiableMap(map);
        }
        public List<String> genes(){return genes;}public List<String> cells(){return cells;}public List<Entry> entries(){return entries;}
        public long count(int gene,int cell){if(gene<0||gene>=genes.size()||cell<0||cell>=cells.size())throw new IndexOutOfBoundsException();return counts.getOrDefault((long)gene*cells.size()+cell,0L);}
        public long columnTotal(int cell){return columnTotals[cell];}
    }
    public record Profile(List<String> genes,List<String> selectedDroplets,List<Long> counts,List<Double> fractions,long total){
        public Profile {genes=List.copyOf(genes);selectedDroplets=List.copyOf(selectedDroplets);counts=List.copyOf(counts);fractions=List.copyOf(fractions);}
    }
    /** Matrix rows are complete cell order; columns are the declared gene-set order. */
    public record Mask(List<String> cells,List<String> geneSets,List<List<Boolean>> accepted,List<List<Double>> cellQValues){
        public Mask {cells=List.copyOf(cells);geneSets=List.copyOf(geneSets);accepted=accepted.stream().map(List::copyOf).toList();cellQValues=cellQValues.stream().map(List::copyOf).toList();}
    }
    public record GlobalPoisson(int acceptedCellSets,long observed,double exposure,double rho){ }
    /** Strict total-UMI endpoints match estimateSoup; empty selections retain zero/NaN diagnostics. */
    public static Profile profile(Counts raw,double lower,double upper,Budget budget){
        Objects.requireNonNull(raw,"raw");Objects.requireNonNull(budget,"budget");if(!Double.isFinite(lower)||!Double.isFinite(upper)||lower<0||lower>=upper)throw new IllegalArgumentException("Require finite ordered nonnegative droplet limits");
        boolean[] selected=new boolean[raw.cells.size()];var names=new ArrayList<String>();long total=0;for(int c=0;c<selected.length;c++){budget.use();long n=raw.columnTotals[c];if(n>lower&&n<upper){selected[c]=true;names.add(raw.cells.get(c));total=add(total,n);}}
        long[] counts=new long[raw.genes.size()];for(Entry e:raw.entries){budget.use();if(selected[e.cell])counts[e.gene]=add(counts[e.gene],e.count);}
        var values=new ArrayList<Long>();var fractions=new ArrayList<Double>();for(long value:counts){budget.use();values.add(value);fractions.add((double)value/total);}return new Profile(raw.genes,names,values,fractions,total);
    }
    /** Verify a declared raw-subset relation without reordering axes, changing values or intersecting cells. */
    public static void verifyRawSubset(Counts raw,Counts filtered,Budget budget){
        if(!raw.genes.equals(filtered.genes))throw new IllegalArgumentException("Raw/filtered genes must match exactly in order");int[] mapping=new int[filtered.cells.size()];
        for(int i=0;i<mapping.length;i++){budget.use();Integer at=raw.cellIndex.get(filtered.cells.get(i));if(at==null)throw new IllegalArgumentException("Filtered barcode absent from declared raw-subset input");mapping[i]=at;}
        var inverse=new HashMap<Integer,Integer>();for(int i=0;i<mapping.length;i++)inverse.put(mapping[i],i);
        for(Entry e:filtered.entries){budget.use();if(e.count!=raw.count(e.gene,mapping[e.cell]))throw new IllegalArgumentException("Filtered count differs from declared raw subset");}
        for(Entry e:raw.entries){budget.use();Integer c=inverse.get(e.cell);if(c!=null&&e.count!=filtered.count(e.gene,c))throw new IllegalArgumentException("Raw count absent/different in declared filtered subset");}
    }
    /** Per-gene-set Poisson upper tails/BH over all cells; cluster acceptance is minimum q>=FDR.
     * A null cluster list explicitly means each cell forms its own group. */
    public static Mask nonExpressing(Counts filtered,Profile profile,Map<String,List<String>> geneSets,List<String> clusters,
                                     double maximumContamination,double fdr,long maximumCellSetPairs,Budget budget){
        validateProfile(filtered,profile);var sets=sets(filtered,geneSets,budget);int n=filtered.cells.size(),k=sets.size();
        if((long)n*k>maximumCellSetPairs||maximumCellSetPairs<1||!Double.isFinite(maximumContamination)||maximumContamination<0||maximumContamination>1||!Double.isFinite(fdr)||fdr<0||fdr>1)throw new IllegalArgumentException("Invalid complete mask capacity, maximum contamination or FDR");
        List<String> groups=clusters==null?filtered.cells:List.copyOf(clusters);if(groups.size()!=n)throw new IllegalArgumentException("Require complete cell-order cluster labels");for(String group:groups)name(group);
        double[][] q=new double[n][k];boolean[][] accepted=new boolean[n][k];int j=0;
        for(var set:sets.entrySet()){
            double fraction=0;for(int g:set.getValue()){budget.use();fraction+=profile.fractions.get(g);}double[] p=new double[n];Integer[] order=new Integer[n];
            for(int c=0;c<n;c++){budget.use();long observed=0;for(int g:set.getValue()){budget.use();observed=add(observed,filtered.count(g,c));}double expected=filtered.columnTotals[c]*maximumContamination*fraction;p[c]=Poisson.cumulative(observed-1.0,expected,false,false);if(!Double.isFinite(p[c])||p[c]<0||p[c]>1)throw new ArithmeticException("Invalid non-expression Poisson tail");order[c]=c;}
            Arrays.sort(order,(a,b)->{budget.use();return Double.compare(p[a],p[b]);});double minimum=1;for(int at=n-1;at>=0;at--){budget.use();int c=order[at];minimum=Math.min(minimum,((double)n/(at+1))*p[c]);q[c][j]=minimum;}
            var groupMinimum=new HashMap<String,Double>();for(int c=0;c<n;c++){budget.use();groupMinimum.merge(groups.get(c),q[c][j],Math::min);}for(int c=0;c<n;c++){budget.use();accepted[c][j]=groupMinimum.get(groups.get(c))>=fdr;}j++;
        }
        var values=new ArrayList<List<Boolean>>();var probabilities=new ArrayList<List<Double>>();for(int c=0;c<n;c++){var row=new ArrayList<Boolean>();var pv=new ArrayList<Double>();for(int i=0;i<k;i++){budget.use();row.add(accepted[c][i]);pv.add(q[c][i]);}values.add(row);probabilities.add(pv);}return new Mask(filtered.cells,new ArrayList<>(sets.keySet()),values,probabilities);
    }
    /** Analytical global Poisson intercept MLE. Does not reproduce R GLM iteration or confidence intervals. */
    public static GlobalPoisson globalPoisson(Counts filtered,Profile profile,Map<String,List<String>> geneSets,Mask mask,Budget budget){
        validateProfile(filtered,profile);var sets=sets(filtered,geneSets,budget);if(!filtered.cells.equals(mask.cells)||!new ArrayList<>(sets.keySet()).equals(mask.geneSets)||mask.accepted.size()!=filtered.cells.size())throw new IllegalArgumentException("Manual mask axes must match literal cells and gene-set order");
        long observed=0;double exposure=0,correction=0;int included=0,j=0;
        for(var set:sets.entrySet()){
            double fraction=0;for(int g:set.getValue()){budget.use();fraction+=profile.fractions.get(g);}
            for(int c=0;c<filtered.cells.size();c++){budget.use();if(mask.accepted.get(c).size()!=sets.size())throw new IllegalArgumentException("Ragged manual mask");if(mask.accepted.get(c).get(j)){
                included++;long count=0;for(int g:set.getValue()){budget.use();count=add(count,filtered.count(g,c));}observed=add(observed,count);double term=filtered.columnTotals[c]*fraction,next=exposure+term;correction+=Math.abs(exposure)>=Math.abs(term)?(exposure-next)+term:(term-next)+exposure;exposure=next;
            }}j++;
        }
        exposure+=correction;if(included==0||!Double.isFinite(exposure)||exposure<=0)throw new IllegalArgumentException("Require accepted manual cells with positive finite soup exposure; no zero-exposure omission");double rho=observed/exposure;if(!Double.isFinite(rho))throw new ArithmeticException("Global Poisson rho is not representable");return new GlobalPoisson(included,observed,exposure,rho);
    }
    private static LinkedHashMap<String,List<Integer>> sets(Counts counts,Map<String,List<String>> input,Budget budget){Objects.requireNonNull(input,"geneSets");if(input.isEmpty())throw new IllegalArgumentException("Require explicit non-expression gene sets");var result=new LinkedHashMap<String,List<Integer>>();for(var e:input.entrySet()){budget.use();name(e.getKey());Objects.requireNonNull(e.getValue(),"members");if(e.getValue().isEmpty())throw new IllegalArgumentException("Empty non-expression gene set");var members=new ArrayList<Integer>();var seen=new HashSet<String>();for(String g:e.getValue()){budget.use();Integer at=counts.geneIndex.get(g);if(at==null||!seen.add(g))throw new IllegalArgumentException("Absent/repeated gene-set member; no identifier repair or deduplication");members.add(at);}result.put(e.getKey(),List.copyOf(members));}return result;}
    private static void validateProfile(Counts counts,Profile profile){Objects.requireNonNull(profile,"profile");if(!counts.genes.equals(profile.genes)||profile.fractions.size()!=counts.genes.size()||profile.counts.size()!=counts.genes.size()||profile.total<=0)throw new IllegalArgumentException("Require complete matching positive-total soup profile");double sum=0;for(int i=0;i<profile.fractions.size();i++){double p=profile.fractions.get(i);if(!Double.isFinite(p)||p<0||p>1)throw new IllegalArgumentException("Invalid soup fraction; no nonfinite replacement");sum+=p;}if(Math.abs(sum-1)>1e-10)throw new IllegalArgumentException("Soup profile must sum to1; no renormalization");}
    private static long add(long a,long b){if(b>EXACT_INTEGER-a)throw new IllegalArgumentException("Count sum exceeds exact binary64 integer domain");return a+b;}
    private static List<String> axis(List<String> input,Budget budget){Objects.requireNonNull(input,"axis");var seen=new HashSet<String>();for(String id:input){budget.use();name(id);if(!seen.add(id))throw new IllegalArgumentException("Duplicate literal count-axis ID");}return List.copyOf(input);}
    private static Map<String,Integer> index(List<String> input){var map=new HashMap<String,Integer>();for(int i=0;i<input.size();i++)map.put(input.get(i),i);return map;}
    private static void name(String value){if(value==null||value.isEmpty()||value.indexOf('\0')>=0||value.indexOf('\t')>=0||value.indexOf('\r')>=0||value.indexOf('\n')>=0)throw new IllegalArgumentException("Require nonempty literal names without TSV control characters");}
}
