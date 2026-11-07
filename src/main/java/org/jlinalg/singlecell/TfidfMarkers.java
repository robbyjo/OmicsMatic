/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.singlecell;

import java.nio.charset.StandardCharsets;
import java.util.*;
import jdistlib.HyperGeometric;

/** Independent TF-IDF frequencies, hypergeometric upper tails and per-cluster BH.
 * Marker selection follows the observed SoupX quickMarkers top-N convention.
 * This component does not estimate or remove ambient RNA or infer cell clusters. */
public final class TfidfMarkers {
    private TfidfMarkers() { }
    /** Zero-based coordinate; repeated coordinates are refused, including explicit zeros. */
    public record Entry(int gene,int cell,double value) { }
    /** Complete gene/cluster statistic, including genes not selected as markers. */
    public record Row(String gene,String cluster,int expressingCells,int clusterCells,int globalExpressingCells,
                      double frequency,double outsideFrequency,double secondBestFrequency,double globalFrequency,
                      String secondBestCluster,double tfidf,double idf,double pValue,double qValue,boolean selected) { }
    public record Result(List<Row> statistics,List<Row> markers,int selectedOutsideFdr,long operations) {
        public Result { statistics=List.copyOf(statistics);markers=List.copyOf(markers); }
    }
    /** Logical work bound; the JVM heap is controlled separately. */
    public static final class Budget {
        private final long maximum;private long used;
        public Budget(long maximum){if(maximum<1)throw new IllegalArgumentException("Require positive work capacity");this.maximum=maximum;}
        public long used(){return used;}
        private void use(){if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Marker calculation interrupted");if(used==maximum)throw new IllegalStateException("Marker operation capacity exceeded");used++;}
    }
    /**
     * All lists describe literal complete axes, not factor levels. A negative topN means ALL.
     * Thresholding is strict value > expressionCut; zero is always non-expressing.
     * Require at least two genes and two groups, with expression in every group.
     * Gene and group order is unsigned UTF-8 byte order (the reference C collation).
     * If enough genes have q&lt;fdr, the top N scores are returned even when a selected
     * gene itself has q&gt;=fdr. That original convention is exposed, not repaired.
     */
    public static Result calculate(List<String> genes,List<String> cells,List<String> clusters,List<Entry> entries,
                                   int topN,double fdr,double expressionCut,long maximumPairs,Budget budget){
        Objects.requireNonNull(budget,"budget");genes=axis(genes,"genes");cells=axis(cells,"cells");Objects.requireNonNull(clusters,"clusters");Objects.requireNonNull(entries,"entries");
        if(genes.size()<2||cells.size()<2||clusters.size()!=cells.size())throw new IllegalArgumentException("Require at least two genes/cells and complete cell-order clusters");
        if(topN==0||topN< -1||!Double.isFinite(fdr)||fdr<=0||fdr>1||!Double.isFinite(expressionCut)||expressionCut<0)throw new IllegalArgumentException("Require topN positive or -1 (ALL), 0<FDR<=1 and finite nonnegative expression cutoff");
        var groupSet=new TreeSet<String>(TfidfMarkers::compareUtf8);for(String group:clusters){budget.use();name(group);groupSet.add(group);}
        List<String> groups=List.copyOf(groupSet);int g=genes.size(),k=groups.size(),n=cells.size();
        if(k<2||(long)g*k>maximumPairs||maximumPairs<1)throw new IllegalArgumentException("Require at least two observed groups and gene/group pairs within capacity");
        var groupIndex=new HashMap<String,Integer>();for(int i=0;i<k;i++)groupIndex.put(groups.get(i),i);
        int[] cellGroup=new int[n],groupSizes=new int[k],observedGroup=new int[k],global=new int[g];int[][] observed=new int[g][k];
        for(int i=0;i<n;i++){budget.use();int c=groupIndex.get(clusters.get(i));cellGroup[i]=c;groupSizes[c]++;}
        var coordinates=new HashSet<Long>();
        for(Entry e:entries){budget.use();Objects.requireNonNull(e,"entry");if(e.gene<0||e.gene>=g||e.cell<0||e.cell>=n||!Double.isFinite(e.value)||e.value<0)throw new IllegalArgumentException("Invalid nonnegative finite sparse coordinate/value");
            long coordinate=(long)e.gene*n+e.cell;if(!coordinates.add(coordinate))throw new IllegalArgumentException("Repeated sparse coordinate; no implicit sum or overwrite");
            if(e.value>expressionCut){int c=cellGroup[e.cell];observed[e.gene][c]++;global[e.gene]++;observedGroup[c]++;}
        }
        for(int count:observedGroup)if(count==0)throw new IllegalArgumentException("Every observed group requires at least one above-cutoff expression; original empty-group behavior is outside this component's domain");
        Integer[] geneOrder=new Integer[g];for(int i=0;i<g;i++)geneOrder[i]=i;
        final List<String> names=genes;Arrays.sort(geneOrder,(a,b)->{budget.use();return compareUtf8(names.get(a),names.get(b));});
        var all=new ArrayList<Row>();var selectedRows=new ArrayList<Row>();int outside=0;
        for(int c=0;c<k;c++){
            double[] p=new double[g],q=new double[g],score=new double[g];Integer[] rank=geneOrder.clone();
            for(int i=0;i<g;i++){budget.use();p[i]=HyperGeometric.cumulative(observed[i][c]-1.0,global[i],n-global[i],groupSizes[c],false,false);if(!Double.isFinite(p[i])||p[i]<0||p[i]>1)throw new ArithmeticException("Invalid hypergeometric probability");score[i]=((double)observed[i][c]/groupSizes[c])*Math.log((double)n/global[i]);}
            Arrays.sort(rank,(a,b)->{budget.use();return Double.compare(p[a],p[b]);});double minimum=1;
            for(int at=g-1;at>=0;at--){budget.use();int i=rank[at];minimum=Math.min(minimum,((double)g/(at+1))*p[i]);q[i]=minimum;}
            int significant=0;for(double value:q){budget.use();if(value<fdr)significant++;}
            rank=geneOrder.clone();Arrays.sort(rank,(a,b)->{budget.use();if(Double.isNaN(score[a]))return Double.isNaN(score[b])?0:1;if(Double.isNaN(score[b]))return -1;return Double.compare(score[b],score[a]);});
            boolean[] selected=new boolean[g];int limit=topN<0?Integer.MAX_VALUE:topN;
            if(significant>=limit){for(int at=0;at<limit;at++){budget.use();selected[rank[at]]=true;}}
            else for(int i:rank){budget.use();selected[i]=q[i]<fdr;}
            Row[] rows=new Row[g];
            for(int i:geneOrder){budget.use();double second=-1;String secondName=null;
                for(int other=0;other<k;other++){budget.use();if(other!=c){double frequency=(double)observed[i][other]/groupSizes[other];if(frequency>second){second=frequency;secondName=groups.get(other);}}}
                double idf=Math.log((double)n/global[i]);Row row=new Row(genes.get(i),groups.get(c),observed[i][c],groupSizes[c],global[i],(double)observed[i][c]/groupSizes[c],(double)(global[i]-observed[i][c])/(n-groupSizes[c]),second,(double)global[i]/n,secondName,score[i],idf,p[i],q[i],selected[i]);rows[i]=row;all.add(row);
            }
            for(int i:rank){budget.use();if(selected[i]){selectedRows.add(rows[i]);if(q[i]>=fdr)outside++;}}
        }
        return new Result(all,selectedRows,outside,budget.used());
    }
    private static List<String> axis(List<String> values,String label){Objects.requireNonNull(values,label);var seen=new HashSet<String>();for(String value:values){name(value);if(!seen.add(value))throw new IllegalArgumentException("Duplicate literal "+label+" identifier");}return List.copyOf(values);}
    private static void name(String value){if(value==null||value.isEmpty()||value.indexOf('\0')>=0||value.indexOf('\t')>=0||value.indexOf('\n')>=0||value.indexOf('\r')>=0)throw new IllegalArgumentException("Require nonempty literal identifiers without TSV control characters");for(int i=0;i<value.length();i++){char ch=value.charAt(i);if(Character.isHighSurrogate(ch)){if(++i>=value.length()||!Character.isLowSurrogate(value.charAt(i)))throw new IllegalArgumentException("Unpaired UTF-16 surrogate");}else if(Character.isLowSurrogate(ch))throw new IllegalArgumentException("Unpaired UTF-16 surrogate");}}
    private static int compareUtf8(String a,String b){return Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
}
