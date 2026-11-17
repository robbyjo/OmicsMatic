/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.spatial;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import jdistlib.Normal;

/**
 * Global residual Moran inference for an explicit full-rank unweighted linear
 * model and supplied directed graph weights. Follows the Cliff/Ord moments
 * used by spdep::lm.morantest (Bivand and contributors, GPL >= 2).
 * No graph learning, factor coding, intercept, response transform or missing
 * observation removal is implicit. Only the normal-reference test is provided.
 */
public final class RegressionMoran {
    public static final long DEFAULT_MAXIMUM_OPERATIONS = 500_000_000L;
    public enum Alternative { GREATER, LESS, TWO_SIDED }
    /** A directed non-self arc, with strictly positive finite supplied weight. */
    public record Arc(int from, int to, double weight) { }
    public record Result(double moranI, double expectation, double variance,
            double statistic, double pValue, double logPValue,
            double responseScale, double scaledResidualNorm) { }

    private RegressionMoran() { }

    public static Prepared prepare(double[][] design, List<Arc> weights, boolean zeroPolicy) {
        return prepare(design, weights, zeroPolicy, 1e-7, DEFAULT_MAXIMUM_OPERATIONS);
    }

    /**
     * Prepare once and reuse the same graph/design/moments across complete gene
     * testing families. Design columns are explicit, including any intercept.
     * Directed duplicates and self-arcs are refused; reciprocal arcs are retained.
     * The statistical weight matrix is U=(W+W')/2, without row normalization.
     * zeroPolicy=true reproduces spdep's effective-N adjustment for original
     * rows with no outgoing neighbors, without removing their design/response rows.
     */
    public static Prepared prepare(double[][] design, List<Arc> weights, boolean zeroPolicy,
            double rankTolerance, long maximumOperations) {
        require(design != null && design.length >= 3 && design.length <= 1_000_000, "Require 3..1000000 observations");
        int n = design.length;
        require(design[0] != null, "Rectangular finite design required");
        int p = design[0].length;
        require(p >= 1 && p <= 128 && p < n && (long)n*p <= 16_000_000, "Unsupported complete design dimensions");
        require(weights != null && !weights.isEmpty() && weights.size() <= 2_000_000, "Require 1..2000000 directed arcs");
        require(Double.isFinite(rankTolerance) && rankTolerance >= 1e-12 && rankTolerance <= 1e-2, "Rank tolerance must be in [1e-12,1e-2]");
        require(maximumOperations > 0 && maximumOperations <= 2_000_000_000L, "Operation limit must be in 1..2000000000");
        long work = 64L*n*p*p + 16L*weights.size()*p + 32L*(n+weights.size());
        require(work <= maximumOperations, "Complete sparse graph/design preparation exceeds declared operation limit");
        Arc[] arcs = weights.toArray(Arc[]::new);
        boolean[] outgoing = new boolean[n], connected = new boolean[n];
        for (Arc arc : arcs) {
            require(arc != null && arc.from >= 0 && arc.to >= 0 && arc.from < n && arc.to < n
                && arc.from != arc.to && arc.weight > 0 && Double.isFinite(arc.weight), "Invalid non-self directed positive-weight arc");
            outgoing[arc.from] = true; connected[arc.from] = true; connected[arc.to] = true;
        }
        int active = 0;
        for (int i = 0; i < n; i++) {
            if (outgoing[i]) active++;
            require(zeroPolicy || connected[i], "Symmetrized graph has isolated observations with zero_policy=false");
        }
        int effectiveN = zeroPolicy ? active : n;
        require(effectiveN > p, "Effective observation count must exceed full model rank");
        Arrays.sort(arcs, Comparator.comparingLong(RegressionMoran::pair).thenComparingInt(Arc::from));
        int[] from = new int[arcs.length], to = new int[arcs.length];
        double[] symmetric = new double[arcs.length]; int edges = 0;
        var s0 = new Sum(); var s1 = new Sum();
        for (int i = 0; i < arcs.length;) {
            Arc first = arcs[i++]; double weight = first.weight/2;
            if (i < arcs.length && pair(arcs[i]) == pair(first)) {
                Arc second = arcs[i++];
                require(second.from != first.from, "Duplicate directed arc; no duplicate summing is inferred");
                weight += second.weight/2;
                require(i == arcs.length || pair(arcs[i]) != pair(first), "Duplicate directed arc");
            }
            numeric(weight > 0 && Double.isFinite(weight), "Symmetrized weight is unrepresentable");
            from[edges] = Math.min(first.from, first.to); to[edges] = Math.max(first.from, first.to); symmetric[edges++] = weight;
            s0.add(2*weight); s1.add(4*weight*weight);
        }
        double[][] q = basis(design, p, rankTolerance);
        double[][] z = new double[p][n];
        for (int e = 0; e < edges; e++) for (int c = 0; c < p; c++) {
            z[c][from[e]] += symmetric[e]*q[c][to[e]];
            z[c][to[e]] += symmetric[e]*q[c][from[e]];
        }
        var trace = new Sum(); var traceSquared = new Sum(); var zSquared = new Sum();
        for (int c = 0; c < p; c++) {
            trace.add(dot(q[c], z[c])); zSquared.add(dot(z[c], z[c]));
            for (int d = 0; d < p; d++) {
                double value = .5*dot(q[c], z[d]) + .5*dot(q[d], z[c]);
                traceSquared.add(value*value);
            }
        }
        double df = effectiveN - (double)p, ratio = effectiveN/s0.value;
        double a = s1.value, b = 2*traceSquared.value, c = -4*zSquared.value, d = -2*trace.value*trace.value/df;
        var bracket = new Sum(); bracket.add(a); bracket.add(b); bracket.add(c); bracket.add(d);
        double uncertainty = 256*Math.ulp(1.0)*(Math.abs(a)+Math.abs(b)+Math.abs(c)+Math.abs(d));
        numeric(bracket.value > uncertainty, "Residual Moran variance is nonpositive or unresolved at floating-point precision");
        double variance = ratio*ratio*bracket.value/df/(df+2), expectation = -ratio*trace.value/df;
        numeric(Double.isFinite(variance) && variance > 0 && Double.isFinite(expectation), "Residual Moran moments are unrepresentable");
        return new Prepared(n, p, effectiveN, active, arcs.length, zeroPolicy, rankTolerance, work,
            q, Arrays.copyOf(from, edges), Arrays.copyOf(to, edges), Arrays.copyOf(symmetric, edges),
            s0.value, expectation, variance);
    }

    /** Immutable prepared design/weights; safe to reuse across responses and threads. */
    public static final class Prepared {
        private final int n, p, effectiveN, active, arcs;
        private final boolean zeroPolicy;
        private final double tolerance, s0, expectation, variance;
        private final long operations;
        private final double[][] q;
        private final int[] from, to;
        private final double[] weights;
        private Prepared(int n, int p, int effectiveN, int active, int arcs, boolean zeroPolicy, double tolerance,
                long operations, double[][] q, int[] from, int[] to, double[] weights,
                double s0, double expectation, double variance) {
            this.n=n; this.p=p; this.effectiveN=effectiveN; this.active=active; this.arcs=arcs; this.zeroPolicy=zeroPolicy;
            this.tolerance=tolerance; this.operations=operations; this.q=q; this.from=from; this.to=to;
            this.weights=weights; this.s0=s0; this.expectation=expectation; this.variance=variance;
        }
        public int observations() { return n; }
        public int rank() { return p; }
        public int effectiveObservations() { return effectiveN; }
        public int outgoingIsolates() { return n - active; }
        public int directedArcs() { return arcs; }
        public int symmetricEdges() { return weights.length; }
        public boolean zeroPolicy() { return zeroPolicy; }
        public double rankTolerance() { return tolerance; }
        public long preparationOperations() { return operations; }
        public long operationsPerResponse() { return 16L*n*(p+1) + 16L*weights.length; }
        public double weightSum() { return s0; }
        public double expectation() { return expectation; }
        public double variance() { return variance; }

        public Result test(double[] response, Alternative alternative) {
            require(response != null && response.length == n && alternative != null, "Complete finite response and explicit alternative required");
            double scale = 0;
            for (double value : response) { require(Double.isFinite(value), "Missing/nonfinite responses are refused, not removed"); scale=Math.max(scale, Math.abs(value)); }
            numeric(scale > 0, "Zero residual variation");
            double[] residual = new double[n];
            for (int i=0; i<n; i++) residual[i]=response[i]/scale;
            double originalNorm = Math.sqrt(dot(residual, residual));
            for (int pass=0; pass<2; pass++) for (double[] column : q) {
                double coefficient=dot(column, residual);
                for (int i=0; i<n; i++) residual[i]-=coefficient*column[i];
            }
            double norm=Math.sqrt(dot(residual,residual));
            numeric(norm > 64*Math.ulp(1.0)*p*originalNorm, "Residual variation is zero or unresolved at floating-point precision");
            for (int i=0; i<n; i++) residual[i]/=norm;
            var quadratic=new Sum();
            for (int e=0; e<weights.length; e++) quadratic.add(2*weights[e]*residual[from[e]]*residual[to[e]]);
            double observed=effectiveN/s0*quadratic.value, statistic=(observed-expectation)/Math.sqrt(variance);
            numeric(Double.isFinite(observed) && Double.isFinite(statistic), "Residual Moran statistic is unrepresentable");
            double probability, log;
            if (alternative==Alternative.TWO_SIDED) {
                probability=2*Normal.cumulative(Math.abs(statistic),0,1,false,false);
                log=Math.log(2)+Normal.cumulative(Math.abs(statistic),0,1,false,true);
            } else {
                boolean lower=alternative==Alternative.LESS;
                probability=Normal.cumulative(statistic,0,1,lower,false);
                log=Normal.cumulative(statistic,0,1,lower,true);
            }
            numeric(Double.isFinite(probability) && probability>=0 && probability<=1 && !Double.isNaN(log) && log<=0, "Unresolved residual Moran reference probability");
            return new Result(observed,expectation,variance,statistic,probability,log,scale,norm);
        }
    }

    private static double[][] basis(double[][] design,int p,double tolerance) {
        int n=design.length; double[][] columns=new double[p][n]; double[] scales=new double[p];
        for (int i=0; i<n; i++) {
            require(design[i]!=null && design[i].length==p, "Complete rectangular design required");
            for (int j=0; j<p; j++) { double v=design[i][j]; require(Double.isFinite(v), "Missing/nonfinite design values are refused"); scales[j]=Math.max(scales[j],Math.abs(v)); }
        }
        for (int j=0; j<p; j++) {
            require(scales[j]>0,"Zero/rank-deficient design column");
            for (int i=0; i<n; i++) columns[j][i]=design[i][j]/scales[j];
            double norm=Math.sqrt(dot(columns[j],columns[j]));
            for (int i=0; i<n; i++) columns[j][i]/=norm;
        }
        // Column pivoting and two-pass modified Gram-Schmidt on normalized columns.
        // No rank-deficient columns are discarded; the complete declared model fails.
        for (int c=0; c<p; c++) {
            int pivot=c; double best=-1;
            for (int j=c; j<p; j++) { double norm=dot(columns[j],columns[j]); if(norm>best){best=norm;pivot=j;} }
            double[] chosen=columns[pivot]; columns[pivot]=columns[c]; columns[c]=chosen;
            for (int pass=0; pass<2; pass++) for (int j=0; j<c; j++) subtract(chosen,columns[j]);
            double norm=Math.sqrt(dot(chosen,chosen));
            require(norm>tolerance,"Complete design is rank deficient or below declared relative rank tolerance");
            for (int i=0; i<n; i++) chosen[i]/=norm;
            for (int j=c+1; j<p; j++) for(int pass=0; pass<2; pass++) subtract(columns[j],chosen);
        }
        for(int a=0;a<p;a++)for(int b=0;b<p;b++)
            numeric(Math.abs(dot(columns[a],columns[b])-(a==b?1:0))<=1e-10,"Design orthogonalization did not converge");
        return columns;
    }
    private static void subtract(double[] x,double[] unit) {double coefficient=dot(x,unit);for(int i=0;i<x.length;i++)x[i]-=coefficient*unit[i];}
    private static long pair(Arc arc) {return ((long)Math.min(arc.from,arc.to)<<32)|Math.max(arc.from,arc.to);}
    private static double dot(double[] x,double[] y) {var sum=new Sum();for(int i=0;i<x.length;i++)sum.add(x[i]*y[i]);return sum.value;}
    private static final class Sum {
        private double value, correction;
        private void add(double term) {double adjusted=term-correction,next=value+adjusted;numeric(Double.isFinite(term)&&Double.isFinite(next),"Residual Moran accumulation exceeds numeric capacity");correction=(next-value)-adjusted;value=next;}
    }
    private static void require(boolean condition,String cause) {if(!condition)throw new IllegalArgumentException(cause);}
    private static void numeric(boolean condition,String cause) {if(!condition)throw new ArithmeticException(cause);}
}
