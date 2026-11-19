/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Original method: Giambartolomei, Wallace and colleagues, coloc (GPL).
 * Source behavior audited against coloc 6.0.3, commit
 * 27776b1bb14e19d1542798ac7477fa378fc2fc0d, R/claudia.R and R/check.R.
 */
package org.jlinalg.coloc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import jdistlib.Normal;

/** Single-causal-variant colocalization from regression or P-value summary statistics.
 * No R process, genotype processing, allele harmonization or LD inference is performed.
 * Invalid inputs and nonfinite source-compatible posterior calculations throw and admit no result.
 */
public final class ColocAbf {
    private ColocAbf() { }
    private record Processed(double[] v, double[] z, double[] r, double[] lbf, Double sdY) { }
    private record Pair(int first, int second, String id) { }

    public static ColocAbfResult analyze(ColocAbfInput first, ColocAbfInput second) {
        return analyze(first, second, ColocAbfOptions.defaults());
    }

    public static ColocAbfResult analyze(ColocAbfInput first, ColocAbfInput second, ColocAbfOptions options) {
        if (first == null || second == null || options == null)
            throw new IllegalArgumentException("two datasets and options are required");
        var warnings = new ArrayList<String>();
        Processed a = process(first, "1", warnings), b = process(second, "2", warnings);
        int n1 = first.variants().size(), n2 = second.variants().size();
        double[] weights1 = options.rawWeights1(), weights2 = options.rawWeights2();
        if ((weights1 != null && weights1.length != n1) || (weights2 != null && weights2.length != n2))
            throw new IllegalArgumentException("prior weight length must match the original dataset");
        if (weights1 != null && weights2 != null)
            warnings.add("BOTH_TRAITS_WEIGHTED: weight information sources should be independent");
        double p1 = adjust(options.trait1Prior(), n1, "1", warnings);
        double p2 = adjust(options.trait2Prior(), n2, "2", warnings);
        var index = new HashMap<String,Integer>();
        for (int j = 0; j < n2; j++) index.put(second.variants().get(j), j);
        var pairs = new ArrayList<Pair>();
        int positionMismatches = 0;
        for (int i = 0; i < n1; i++) {
            String id = first.variants().get(i);Integer j = index.get(id);
            if (j == null) continue;
            if (first.rawPositions() != null && second.rawPositions() != null
                    && first.rawPositions()[i] != second.rawPositions()[j]) {
                positionMismatches++;continue;
            }
            pairs.add(new Pair(i, j, id));
        }
        // R merge() in the reference campaign uses C collation on UTF-8 IDs.
        pairs.sort((x,y) -> compareCodePoints(x.id(), y.id()));
        int n = pairs.size();
        if (n == 0) throw new IllegalArgumentException("datasets have no common SNP/position merge keys");
        if (positionMismatches > 0)
            warnings.add("POSITION_KEY_MISMATCHES_EXCLUDED: " + positionMismatches);
        double p12 = adjust(options.sharedPrior(), n, "12", warnings);
        double[] l1 = new double[n], l2 = new double[n], both = new double[n];
        double[] w1 = weights1 == null ? null : new double[n], w2 = weights2 == null ? null : new double[n];
        for (int k = 0; k < n; k++) {
            Pair pair = pairs.get(k);l1[k] = a.lbf()[pair.first()];l2[k] = b.lbf()[pair.second()];
            both[k] = l1[k] + l2[k];
            if (w1 != null) w1[k] = weights1[pair.first()];
            if (w2 != null) w2[k] = weights2[pair.second()];
        }
        double conditionalDenominator = logSum(both);
        // Upstream coloc.abf retains this UNWEIGHTED SNP.PP.H4 column even
        // when its regional hypotheses use prior weights. Do not silently fix it.
        if (w1 != null || w2 != null)
            warnings.add("SOURCE_SNP_PP_H4_UNWEIGHTED: regional hypotheses use weights; this conditional column does not");
        double[] x = new double[n], y = new double[n], diagonal = new double[n], shared = new double[n];
        double sumW1 = w1 == null ? 1 : sum(w1), sumW2 = w2 == null ? 1 : sum(w2);
        for (int k = 0; k < n; k++) {
            double prior1 = w1 == null ? p1 : n * p1 * (w1[k] / sumW1);
            double prior2 = w2 == null ? p2 : n * p2 * (w2[k] / sumW2);
            x[k] = Math.log(prior1) + l1[k];y[k] = Math.log(prior2) + l2[k];
            diagonal[k] = Math.log(prior1) + Math.log(prior2) + both[k];
            shared[k] = Math.log(prior1 * prior2 * (p12 / (p1 * p2))) + both[k];
        }
        double h3;
        double[] logs;
        if (w1 == null && w2 == null) {
            h3 = logDifference(logSum(l1) + logSum(l2), logSum(both));
            logs = new double[] {0, Math.log(p1) + logSum(l1), Math.log(p2) + logSum(l2),
                Math.log(p1) + Math.log(p2) + h3, Math.log(p12) + logSum(both)};
        } else {
            h3 = logDifference(logSum(x) + logSum(y), logSum(diagonal));
            logs = new double[] {0, logSum(x), logSum(y), h3, logSum(shared)};
        }
        if (h3 == Double.NEGATIVE_INFINITY)
            warnings.add("H3_ZERO_OR_OUT_OF_RESOLUTION: source log-difference is -Infinity; no stable-formula replacement applied");
        double denominator = logSum(logs);double[] posteriors = new double[5];
        for (int h = 0; h < 5; h++) {
            posteriors[h] = Math.exp(logs[h] - denominator);
            if (!Double.isFinite(posteriors[h]))
                throw new IllegalArgumentException("nonfinite source-compatible hypothesis posterior; no result admitted");
        }
        var variants = new ArrayList<ColocAbfResult.Variant>(n);
        for (int k = 0; k < n; k++) {
            Pair pair = pairs.get(k);int i = pair.first(), j = pair.second();
            double pp = Math.exp(both[k] - conditionalDenominator);
            if (!Double.isFinite(pp))
                throw new IllegalArgumentException("nonfinite source conditional SNP posterior; no result admitted");
            variants.add(new ColocAbfResult.Variant(pair.id(), position(first,i), position(second,j),
                a.v()[i],a.z()[i],a.r()[i],a.lbf()[i],b.v()[j],b.z()[j],b.r()[j],b.lbf()[j],pp));
        }
        return new ColocAbfResult(variants,posteriors,new double[] {p1,p2,p12},a.sdY(),b.sdY(),
            w1,w2,warnings,n1,n2,positionMismatches);
    }

    private static Processed process(ColocAbfInput input, String trait, List<String> warnings) {
        int n = input.variants().size();double[] v = new double[n], z = new double[n];
        double[] r = new double[n], lbf = new double[n];
        boolean quant = input.type() == ColocAbfInput.TraitType.QUANTITATIVE;
        Double sdY = quant && input.usesEffectEstimates() ? input.traitStandardDeviation() : null;
        if (quant && input.usesEffectEstimates() && sdY == null) {
            sdY = estimateSdY(input);warnings.add("SDY_ESTIMATED_TRAIT_" + trait + ": " + sdY);
        }
        double sdPrior = quant ? (input.usesEffectEstimates() ? 0.15 * sdY : 0.15) : 0.2;
        double minimumP = 1;
        for (int i = 0; i < n; i++) {
            if (input.usesEffectEstimates()) {
                v[i] = input.rawVariance()[i];z[i] = input.rawBeta()[i] / Math.sqrt(v[i]);
                minimumP = Math.min(minimumP, 2 * Normal.cumulative(-Math.abs(z[i]),0,1,true,false));
            } else {
                double f = input.rawFrequencies()[i];
                double inv = 2 * input.sampleSize() * f * (1-f);
                if (!quant) inv = inv * input.caseFraction() * (1-input.caseFraction());
                v[i] = 1 / inv;z[i] = Normal.quantile(0.5 * input.rawPValues()[i],0,1,false,false);
                minimumP = Math.min(minimumP,input.rawPValues()[i]);
            }
            r[i] = (sdPrior * sdPrior) / (sdPrior * sdPrior + v[i]);
            lbf[i] = 0.5 * (Math.log(1-r[i]) + r[i] * (z[i]*z[i]));
            if (!Double.isFinite(v[i]) || !Double.isFinite(z[i]) || !Double.isFinite(r[i])
                    || Double.isNaN(lbf[i]) || lbf[i] == Double.POSITIVE_INFINITY)
                throw new IllegalArgumentException("nonfinite source-compatible ABF in trait " + trait + "; no result admitted");
            if (lbf[i] == Double.NEGATIVE_INFINITY)
                warnings.add("NEGATIVE_INFINITE_ABF_TRAIT_" + trait + ": " + input.variants().get(i));
        }
        if (minimumP > 1e-6) warnings.add("NO_P_BELOW_1E_6_TRAIT_" + trait + ": check that variance, not SE, was supplied");
        return new Processed(v,z,r,lbf,sdY);
    }

    private static double estimateSdY(ColocAbfInput input) {
        double maximum = 0;
        for (double v : input.rawVariance()) maximum = Math.max(maximum,1/v);
        double numerator = 0, denominator = 0;
        for (int i = 0; i < input.variants().size(); i++) {
            double x = (1/input.rawVariance()[i]) / maximum;
            double f = input.rawFrequencies()[i], y = 2 * input.sampleSize() * f * (1-f);
            numerator += x*y;denominator += x*x;
        }
        double value = Math.sqrt((numerator / denominator) / maximum);
        if (!(value > 0) || !Double.isFinite(value))
            throw new IllegalArgumentException("sdY estimation is not finite and positive");
        return value;
    }
    private static double adjust(double p, int n, String trait, List<String> warnings) {
        if (n*p < 1) return p;
        double value = 1.0/(n+1.0);
        warnings.add("PRIOR_" + trait + "_ADJUSTED: " + p + " -> " + value + " for n=" + n);
        return value;
    }
    private static double sum(double[] values) { double out = 0;for (double v : values) out += v;return out; }
    private static double logSum(double[] values) {
        double max = Double.NEGATIVE_INFINITY;
        for (double v : values) max = Math.max(max,v);
        double sum = 0;for (double v : values) sum += Math.exp(v-max);
        return max+Math.log(sum);
    }
    private static double logDifference(double x, double y) {
        double max = Math.max(x,y);
        return max+Math.log(Math.max(Math.exp(x-max)-Math.exp(y-max),0));
    }
    private static Long position(ColocAbfInput input,int index) {
        return input.rawPositions() == null ? null : input.rawPositions()[index];
    }
    private static int compareCodePoints(String a, String b) {
        int i=0,j=0;
        while (i<a.length() && j<b.length()) {
            int x=a.codePointAt(i),y=b.codePointAt(j);if (x!=y) return Integer.compare(x,y);
            i+=Character.charCount(x);j+=Character.charCount(y);
        }
        return Integer.compare(a.length()-i,b.length()-j);
    }
}
