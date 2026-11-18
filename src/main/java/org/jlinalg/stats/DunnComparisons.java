/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.stats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import jdistlib.ChiSquare;
import jdistlib.Normal;
import org.jlinalg.enrichment.FumaGeneSetEnrichment;

/** Two-sided independent-sample Dunn comparisons, pooled midranks and explicit correction families. */
public final class DunnComparisons {
    public static final int MAXIMUM_OBSERVATIONS = 2_000_000, MAXIMUM_COMPARISONS = 1_000_000;
    public enum Adjustment { NONE, BONFERRONI, HOLM, SIDAK, HOLM_SIDAK, BH, BY }
    /** Index order determines the signed statistic: second group minus first group. */
    public record Contrast(int first, int second) { }
    public record Group(int index, int size, double meanRank) { }
    public record Comparison(int first, int second, double meanRankDifference, double standardError,
                             double z, double pValue, double logPValue,
                             double adjustedPValue, double logAdjustedPValue) { }
    public record Result(List<Group> groups, List<Comparison> comparisons, int observations,
                         double rankVariance, double tieCorrection, int tiedValueGroups, int largestTie,
                         double kruskalWallis, double omnibusPValue, double logOmnibusPValue,
                         Adjustment adjustment) {
        public Result { groups = List.copyOf(groups); comparisons = List.copyOf(comparisons); }
    }
    private DunnComparisons() { }

    public static Result allPairs(double[][] observations, Adjustment adjustment) {
        Objects.requireNonNull(observations, "Declare the complete rank population");
        long pairs = (long)observations.length*(observations.length-1)/2;
        if (pairs > MAXIMUM_COMPARISONS) throw new IllegalArgumentException("Complete comparison family exceeds one million pairs; no subset admitted");
        var family = new ArrayList<Contrast>();
        for (int first = 0; first < observations.length; first++) for (int second = first+1; second < observations.length; second++) family.add(new Contrast(first,second));
        return compare(observations,family,adjustment);
    }
    public static Result versusControl(double[][] observations, int control, Adjustment adjustment) {
        Objects.requireNonNull(observations, "Declare the complete rank population");
        if (control < 0 || control >= observations.length) throw new IllegalArgumentException("Control is absent from the complete group axis");
        if (observations.length-1 > MAXIMUM_COMPARISONS) throw new IllegalArgumentException("Complete control-comparison family exceeds one million pairs");
        var family = new ArrayList<Contrast>();
        for (int second = 0; second < observations.length; second++) if (second != control) family.add(new Contrast(control,second));
        return compare(observations,family,adjustment);
    }
    /**
     * Every declared observation participates in ranking, including groups not
     * present in a selected contrast. Only the explicitly supplied unique
     * two-sided comparison family participates in multiplicity adjustment.
     * No missing-value removal, omnibus significance gate or paired model.
     */
    public static Result compare(double[][] observations, List<Contrast> family, Adjustment adjustment) {
        Objects.requireNonNull(observations); Objects.requireNonNull(family); Objects.requireNonNull(adjustment);
        if (observations.length < 2 || observations.length > MAXIMUM_OBSERVATIONS) throw new IllegalArgumentException("Require at least two complete nonempty groups");
        if (family.isEmpty() || family.size() > MAXIMUM_COMPARISONS) throw new IllegalArgumentException("Declare 1..1000000 complete comparisons");
        int n = 0; int[] sizes = new int[observations.length];
        for (int i = 0; i < sizes.length; i++) {
            if (observations[i] == null || observations[i].length == 0) throw new IllegalArgumentException("Empty/missing group; no removal");
            sizes[i] = observations[i].length; n = Math.addExact(n,sizes[i]);
            if (n > MAXIMUM_OBSERVATIONS) throw new IllegalArgumentException("Complete rank population exceeds two million observations");
        }
        var selected = List.copyOf(family); var unique = new HashSet<Long>();
        for (Contrast c : selected) {
            if (c.first() < 0 || c.second() < 0 || c.first() >= sizes.length || c.second() >= sizes.length || c.first() == c.second()) throw new IllegalArgumentException("Invalid/self group comparison");
            long key = ((long)Math.min(c.first(),c.second()) << 32)|Math.max(c.first(),c.second());
            if (!unique.add(key)) throw new IllegalArgumentException("Duplicate or reversed duplicate two-sided hypothesis in the correction family");
        }
        double[] pooled = new double[n]; int[] source = new int[n]; Integer[] order = new Integer[n];
        int index = 0;
        for (int group = 0; group < sizes.length; group++) for (double value : observations[group]) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite observation; no dropping, imputation or rank result");
            pooled[index] = value; source[index] = group; order[index] = index; index++;
        }
        Arrays.sort(order,(a,b) -> Double.compare(pooled[a],pooled[b]));
        long[] twiceRankSums = new long[sizes.length]; long tieSum = 0; int tiedGroups = 0, largest = 1;
        for (int start = 0; start < n;) {
            int end = start+1;
            while (end < n && pooled[order[start]] == pooled[order[end]]) end++;
            long count = end-start, twiceRank = (long)start+1+end;
            if (count > 1) {tieSum = Math.addExact(tieSum,count*count*count-count); tiedGroups++; largest = Math.max(largest,(int)count);}
            for (int i = start; i < end; i++) twiceRankSums[source[order[i]]] += twiceRank;
            start = end;
        }
        // At N<=2e6, N^3-N and every partial tie sum fit signed long exactly.
        // Subtracting these integers avoids cancellation for an almost-all-tied population.
        long untied = (long)n*n*n-n, corrected = untied-tieSum;
        if (corrected <= 0) throw new IllegalArgumentException("All observations are tied; rank variance and the complete Dunn family are undefined");
        double variance = corrected/(12.0*(n-1)), correction = corrected/(double)untied;
        var groups = new ArrayList<Group>(); double sum = 0, rounding = 0;
        for (int i = 0; i < sizes.length; i++) {
            double mean = twiceRankSums[i]/(2.0*sizes[i]); groups.add(new Group(i,sizes[i],mean));
            double centered = mean-(n+1.0)/2, term = sizes[i]*centered*centered-rounding, updated = sum+term;
            rounding = (updated-sum)-term; sum = updated;
        }
        double h = sum/variance, omnibusLog = ChiSquare.cumulative(h,sizes.length-1,false,true);
        if (!Double.isFinite(h) || Double.isNaN(omnibusLog) || omnibusLog > 0) throw new ArithmeticException("Unrepresentable omnibus rank diagnostic; no complete family");
        double[] differences = new double[selected.size()], errors = new double[selected.size()], statistics = new double[selected.size()], logs = new double[selected.size()];
        for (int i = 0; i < selected.size(); i++) {
            Contrast c = selected.get(i); differences[i] = groups.get(c.second()).meanRank()-groups.get(c.first()).meanRank();
            errors[i] = Math.sqrt(variance)*Math.sqrt(1.0/sizes[c.first()]+1.0/sizes[c.second()]); statistics[i] = differences[i]/errors[i];
            logs[i] = Math.min(0,Math.log(2)+Normal.cumulative(Math.abs(statistics[i]),0,1,false,true));
            if (!Double.isFinite(statistics[i]) || Double.isNaN(logs[i])) throw new ArithmeticException("Unrepresentable Dunn statistic or probability; complete family refused");
        }
        double[] adjusted;
        if (adjustment == Adjustment.NONE || logs.length == 1) adjusted = logs.clone();
        else if (adjustment == Adjustment.SIDAK || adjustment == Adjustment.HOLM_SIDAK) adjusted = sidakFamily(logs,adjustment == Adjustment.HOLM_SIDAK);
        else adjusted = FumaGeneSetEnrichment.adjustLog(logs,FumaGeneSetEnrichment.Adjustment.valueOf(adjustment.name()),1);
        var comparisons = new ArrayList<Comparison>();
        for (int i = 0; i < selected.size(); i++) {Contrast c = selected.get(i); comparisons.add(new Comparison(c.first(),c.second(),differences[i],errors[i],statistics[i],Math.exp(logs[i]),logs[i],Math.exp(adjusted[i]),adjusted[i]));}
        return new Result(groups,comparisons,n,variance,correction,tiedGroups,largest,h,Math.exp(omnibusLog),omnibusLog,adjustment);
    }
    private static double[] sidakFamily(double[] logs, boolean stepDown) {
        Integer[] order = new Integer[logs.length]; for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order,(a,b) -> Double.compare(logs[a],logs[b])); double[] result = new double[logs.length]; double previous = Double.NEGATIVE_INFINITY;
        for (int rank = 0; rank < order.length; rank++) {
            int index = order[rank], multiplicity = stepDown ? order.length-rank : order.length; double logP = logs[index];
            // For subnormal P, the O(m*P) correction to log(m*P) is far below an
            // FP64 log ulp at these family bounds. Avoid rounding through exp(P).
            double adjusted = multiplicity == 1 ? logP : logP < Math.log(Double.MIN_NORMAL) ? logP+Math.log(multiplicity) : Math.log(-Math.expm1(multiplicity*Math.log1p(-Math.exp(logP))));
            if (stepDown) {previous = Math.max(previous,adjusted); adjusted = previous;}
            result[index] = Math.min(0,adjusted);
        }
        return result;
    }
}
