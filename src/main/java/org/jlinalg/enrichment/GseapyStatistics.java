/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.enrichment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Native arithmetic for the original GSEApy offline over-representation test.
 *
 * <p>This API follows {@code gseapy.stats.calc_pvalues}, rather than a generic
 * GSEA running walk. Identifiers are literal; caller-supplied identifiers must
 * already have the desired case and species mapping. It does not perform the
 * high-level Enrichr client's version-dependent case conversion, library
 * discovery, background lookup or remote-service analysis.</p>
 *
 * <p>Only terms with at least one intersecting query gene enter the original
 * correction family. Edition 1.0.4 has a different odds formula from 1.1.2,
 * 1.1.3 and 1.1.8. None of these local tests is two-tailed. Explicitly invalid
 * numeric population sizes retain the original undefined tail and diagnostic
 * odds, without converting them into significant results.</p>
 */
public final class GseapyStatistics {
    private GseapyStatistics() { }

    /** Exact source edition; an unreported paper edition must not be inferred. */
    public enum Edition { V1_0_4, V1_1_2, V1_1_3, V1_1_8 }
    /** Original multiple_testing_correction methods. */
    public enum Correction { BENJAMINI_HOCHBERG, BONFERRONI }

    /** Explicit population mode, keeping a numeric size distinct from a gene universe. */
    public sealed interface Background permits Population, Universe { }
    /** Numeric population size; unlike a gene universe it cannot filter identifiers. */
    public record Population(int size) implements Background {
        public Population { if(size<0)throw new IllegalArgumentException("Population size must be nonnegative"); }
    }
    /** Literal explicit background identifiers, deduplicated without trimming or case repair. */
    public record Universe(Set<String> genes) implements Background {
        public Universe { genes=Set.copyOf(Objects.requireNonNull(genes,"background genes")); }
    }

    /** Immutable native result; hit order is explicitly Unicode code-point order. */
    public record Row(String term,int querySize,int overlap,int termSize,List<String> hits,
            double pValue,double adjustedPValue,double oddsRatio,double combinedScore,
            boolean combinedScoreAvailable,double rejected) {
        public Row { hits=List.copyOf(hits); }
        /** Prominent source diagnostics and required actions; retain the original numeric values. */
        public List<String> warnings(){
            var messages=new ArrayList<String>();
            if(Double.isNaN(pValue)||Double.isNaN(adjustedPValue))messages.add("UNDEFINED ORIGINAL TEST: the numeric population is incompatible with a query/term margin. Preserve NaN and all returned terms; correct the explicit background on a new analysis or skip dependent inference. Do not drop undefined terms or replace their P values.");
            if(pValue==0)messages.add("ORIGINAL TAIL UNDERFLOW: zero is a floating-point tail, not proof of an exactly impossible null event. Retain the zero and any infinite combined score; do not insert an epsilon or claim an exact zero probability.");
            if(oddsRatio<0)messages.add("DIAGNOSTIC ODDS: a negative source formula reflects an incompatible numeric population. Inspect the declared background and margins; do not interpret this diagnostic as a valid enrichment effect.");
            if(combinedScoreAvailable&&!Double.isFinite(combinedScore))messages.add("NONFINITE ORIGINAL COMBINED SCORE: preserve its exact kind and inspect the tail/background diagnostics before using a ranking. Do not clip or fabricate a finite score.");
            return List.copyOf(messages);
        }
    }
    /** Original numeric reject mask: zero, one or undefined NaN. */
    public record Corrected(double[] adjusted,double[] rejected) {
        public Corrected { adjusted=adjusted.clone();rejected=rejected.clone(); }
        @Override public double[] adjusted(){return adjusted.clone();}
        @Override public double[] rejected(){return rejected.clone();}
    }

    /**
     * Run the original local one-sided hypergeometric test on explicit sets.
     * Duplicates are removed as in Python sets. Zero-overlap terms are omitted,
     * including terms removed entirely by an explicit background intersection.
     * Results follow Python's sorted term order, with deterministic hit-set
     * membership presentation rather than a claim about Python hash iteration.
     */
    public static List<Row> overRepresentation(Edition edition,Collection<String> suppliedQuery,
            Map<String,? extends Collection<String>> geneSets,Background background,double alpha) {
        Objects.requireNonNull(edition,"edition");Objects.requireNonNull(background,"background");
        Objects.requireNonNull(geneSets,"gene sets");checkAlpha(alpha);
        Set<String> query=identifiers(suppliedQuery);Set<String> universe=null;int population;
        if(background instanceof Universe given){universe=given.genes();population=universe.size();query.retainAll(universe);}
        else population=((Population)background).size();
        var terms=new ArrayList<>(geneSets.keySet());
        for(String term:terms)Objects.requireNonNull(term,"term identifier");terms.sort(CODE_POINTS);
        var raw=new ArrayList<Row>();
        for(String term:terms){
            Set<String> category=identifiers(geneSets.get(term));if(universe!=null)category.retainAll(universe);
            var hits=new ArrayList<String>();for(String gene:query)if(category.contains(gene))hits.add(gene);
            if(hits.isEmpty())continue;hits.sort(CODE_POINTS);int x=hits.size(),m=category.size(),k=query.size();
            double p=upperTail(population,k,m,x);double odds=oddsRatio(edition,population,k,m,x);
            boolean combined=edition!=Edition.V1_0_4;
            raw.add(new Row(term,k,x,m,hits,p,Double.NaN,odds,combined?-Math.log(p)*odds:Double.NaN,combined,Double.NaN));
        }
        double[] p=new double[raw.size()];for(int i=0;i<p.length;i++)p[i]=raw.get(i).pValue();
        Corrected correction=correct(p,alpha,Correction.BENJAMINI_HOCHBERG);var rows=new ArrayList<Row>();
        double[] adjusted=correction.adjusted(),rejected=correction.rejected();
        for(int i=0;i<raw.size();i++){Row r=raw.get(i);rows.add(new Row(r.term(),r.querySize(),r.overlap(),r.termSize(),r.hits(),r.pValue(),adjusted[i],r.oddsRatio(),r.combinedScore(),r.combinedScoreAvailable(),rejected[i]));}
        return List.copyOf(rows);
    }

    /**
     * Source hypergeometric survival probability P(X &gt;= overlap).
     * Counts are nonnegative with a possible observed intersection. If a
     * supplied numeric population is smaller than either margin, SciPy's
     * undefined NaN is retained. A feasible margin with an observation below
     * its support has tail one, as in the original survival function.
     */
    public static double upperTail(int population,int query,int term,int overlap){
        counts(population,query,term,overlap);
        if(population==0||query>population||term>population)return Double.NaN;
        return jdistlib.HyperGeometric.cumulative(overlap-1.0,term,population-term,query,false,false);
    }

    /** Exact source-version odds expression, including diagnostic negative values for invalid populations. */
    public static double oddsRatio(Edition edition,int population,int query,int term,int overlap){
        Objects.requireNonNull(edition,"edition");counts(population,query,term,overlap);
        double x=overlap,m=term,k=query,bg=population;
        if(edition==Edition.V1_0_4)return ((x+0.5)*(bg-m+0.5))/((m+0.5)*(k-x+0.5));
        return ((x+0.5)*(bg-m-k+x+0.5))/((m-x+0.5)*(k-x+0.5));
    }

    /**
     * Source correction on the supplied family. NaNs retain NaN in both
     * outputs. BH uses inclusive rejection; Bonferroni uses strict rejection
     * and retains adjusted values greater than one, as this source does.
     */
    public static Corrected correct(double[] supplied,double alpha,Correction method){
        Objects.requireNonNull(method,"correction");Objects.requireNonNull(supplied,"P values");checkAlpha(alpha);
        double[] q=supplied.clone(),reject=supplied.clone();var order=new ArrayList<Integer>();
        for(int i=0;i<supplied.length;i++){
            double v=supplied[i];if(Double.isNaN(v))continue;
            if(!Double.isFinite(v)||v<0||v>1)throw new IllegalArgumentException("P values must be finite in [0,1] or literal NaN");
            order.add(i);
        }
        order.sort(Comparator.comparingDouble(i->supplied[i]));int n=order.size();
        if(method==Correction.BONFERRONI){for(int i:order){q[i]=supplied[i]*n;reject[i]=q[i]<alpha?1:0;}}
        else {
            double running=Double.POSITIVE_INFINITY;int lastRejected=-1;
            for(int j=0;j<n;j++){int i=order.get(j);double ecdf=(j+1.0)/n;if(supplied[i]<=ecdf*alpha)lastRejected=j;}
            for(int j=n-1;j>=0;j--){int i=order.get(j);running=Math.min(running,supplied[i]/((j+1.0)/n));q[i]=Math.min(1,running);reject[i]=j<=lastRejected?1:0;}
        }
        return new Corrected(q,reject);
    }

    private static Set<String> identifiers(Collection<String> genes){
        Objects.requireNonNull(genes,"gene identifiers");var out=new HashSet<String>();for(String gene:genes)out.add(Objects.requireNonNull(gene,"gene identifier"));return out;
    }
    private static void counts(int population,int query,int term,int overlap){
        if(population<0||query<0||term<0||overlap<0||overlap>Math.min(query,term))throw new IllegalArgumentException("Nonnegative counts with possible observed overlap are required");
    }
    private static void checkAlpha(double alpha){if(!Double.isFinite(alpha)||alpha<0||alpha>1)throw new IllegalArgumentException("Alpha must be finite in [0,1]");}
    private static final Comparator<String> CODE_POINTS=(a,b)->{
        int i=0,j=0;while(i<a.length()&&j<b.length()){int x=a.codePointAt(i),y=b.codePointAt(j);if(x!=y)return Integer.compare(x,y);i+=Character.charCount(x);j+=Character.charCount(y);}
        return Integer.compare(a.length()-i,b.length()-j);
    };
}
