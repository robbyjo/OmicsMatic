/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.mr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Exposure-selected single-signal MR with measurement-quality-aware LD replacement.
 * Both effects always belong to the selected SAME variant; no cross-variant ratio,
 * INFO correction, outcome-P selection or claim of causal identification is made.
 */
public final class LinkedVariantMr {
    private LinkedVariantMr() { }

    /** Immutable per-allele association, with source QC and measurement provenance. */
    public record Association(String id, String chromosome, long position,
            String effectAllele, String otherAllele, double beta, double se,
            double p, double eaf, int n, boolean qcPass, String measurement,
            String qualityMetric, double quality) {
        public Association {
            token(id, "variant ID"); token(chromosome, "chromosome");
            chromosome = chromosome.replaceFirst("(?i)^chr", "");
            if (chromosome.isBlank() || position < 1 || n < 2)
                throw new IllegalArgumentException("Invalid position or sample size for " + id);
            effectAllele = allele(effectAllele); otherAllele = allele(otherAllele);
            if (effectAllele.equals(otherAllele)) throw new IllegalArgumentException("Identical alleles: " + id);
            if (!Double.isFinite(beta) || !Double.isFinite(se) || se <= 0
                    || !Double.isFinite((beta / se) * (beta / se)))
                throw new IllegalArgumentException("Invalid beta/SE for " + id);
            probability(p, "association P"); probability(eaf, "effect allele frequency");
            if (eaf == 0 || eaf == 1) throw new IllegalArgumentException("Monomorphic association: " + id);
            if (!Set.of("sequenced", "genotyped", "imputed", "unknown").contains(measurement))
                throw new IllegalArgumentException("Unknown measurement for " + id);
            qualityMetric = qualityMetric == null ? "" : qualityMetric;
            if (!Double.isNaN(quality)) probability(quality, "imputation quality");
            if (!measurement.equals("imputed") && (!qualityMetric.isEmpty() || !Double.isNaN(quality)))
                throw new IllegalArgumentException("Only imputed rows may declare imputation quality: " + id);
        }
        public double fStatistic() { return (beta / se) * (beta / se); }
    }

    /** LD is used only to establish replacement eligibility, not to rescale effects. */
    public record Proxy(String target, String variant, double rSquared) {
        public Proxy {
            token(target, "LD target"); token(variant, "LD proxy");
            if (target.equals(variant)) throw new IllegalArgumentException("Self-proxy LD row");
            probability(rSquared, "LD r-squared");
        }
    }

    /** Thresholds must be fixed before inspecting outcome association results. */
    public record Policy(double instrumentP, double minimumF, String imputationMetric,
            double minimumQuality, double minimumProxyR2, double confidenceLevel) {
        public Policy {
            if (!(instrumentP > 0 && instrumentP <= 5e-8) || !Double.isFinite(minimumF) || minimumF < 10)
                throw new IllegalArgumentException("Require instrument P in (0,5e-8] and F >= 10");
            token(imputationMetric, "imputation metric");
            probability(minimumQuality, "minimum imputation quality");
            if (!(minimumProxyR2 >= .8 && minimumProxyR2 <= 1))
                throw new IllegalArgumentException("Minimum proxy r-squared must be in [0.8,1]");
            if (!(confidenceLevel > 0 && confidenceLevel < 1))
                throw new IllegalArgumentException("Invalid confidence level");
        }
    }

    public record Decision(String stage, String variant, String reason, double rSquared) { }
    public record Result(String status, String leadVariant, String selectedVariant,
            String originalOutcomeDisposition, double proxyR2,
            HarmonizedInstrument instrument, MrEstimate estimate, List<Decision> decisions) {
        public Result { decisions = List.copyOf(decisions); }
    }

    /**
     * Selects the strongest eligible exposure variant in a prespecified cis interval.
     * A usable exact outcome ALWAYS wins, including beta=0 or P=1. Only unusable
     * measurements trigger an LD search. Every fallback requires a strong measured
     * exposure association at the proxy itself. Unknown quality is not a null result.
     */
    public static Result analyze(List<Association> exposure, List<Association> outcome,
            List<Proxy> proxies, String chromosome, long start, long end, Policy policy) {
        if (exposure == null || outcome == null || proxies == null || policy == null)
            throw new IllegalArgumentException("Inputs and policy are required");
        token(chromosome, "cis chromosome"); chromosome = chromosome.replaceFirst("(?i)^chr", "");
        if (start < 1 || end < start) throw new IllegalArgumentException("Invalid cis interval");
        Map<String, Association> x = index(exposure), y = index(outcome);
        Set<String> links = new java.util.HashSet<>();
        for (Proxy proxy : proxies) {
            if (!links.add(proxy.target() + "\t" + proxy.variant()))
                throw new IllegalArgumentException("Duplicate target/proxy LD pair");
            if (!x.containsKey(proxy.target()) || !x.containsKey(proxy.variant()))
                throw new IllegalArgumentException("LD identifiers must have exposure records: " + proxy);
            if (!x.get(proxy.target()).chromosome().equals(x.get(proxy.variant()).chromosome()))
                throw new IllegalArgumentException("Cross-chromosome proxy");
        }
        List<Decision> audit = new ArrayList<>();
        List<Association> eligible = new ArrayList<>();
        for (Association association : exposure.stream().sorted(exposureOrder()).toList()) {
            String reason = exposureReason(association, chromosome, start, end, policy);
            audit.add(new Decision("exposure", association.id(), reason, Double.NaN));
            if (reason.equals("eligible")) eligible.add(association);
        }
        if (eligible.isEmpty()) return new Result("not_testable_no_strong_exposure", "", "", "not_queried",
            Double.NaN, null, null, audit);
        Association lead = eligible.get(0);
        String original = outcomeReason(lead, y.get(lead.id()), policy);
        audit.add(new Decision("exact_outcome", lead.id(), original, 1));
        if (original.equals("usable")) return fit("exact", lead.id(), lead, y.get(lead.id()), original, 1, policy, audit);

        List<Proxy> usable = new ArrayList<>();
        for (Proxy proxy : proxies.stream().filter(p -> p.target().equals(lead.id()))
                .sorted(Comparator.comparing(Proxy::variant)).toList()) {
            Association candidate = x.get(proxy.variant());
            String reason = proxy.rSquared() < policy.minimumProxyR2() ? "insufficient_ld"
                : exposureReason(candidate, chromosome, start, end, policy);
            if (reason.equals("eligible")) reason = outcomeReason(candidate, y.get(candidate.id()), policy);
            audit.add(new Decision("proxy", candidate.id(), reason, proxy.rSquared()));
            if (reason.equals("usable")) usable.add(proxy);
        }
        // Deliberately exclude outcome beta, SE and P from every ranking key.
        usable.sort(Comparator.comparingDouble(Proxy::rSquared).reversed()
            .thenComparing(Comparator.comparingDouble((Proxy p) -> measurementQuality(y.get(p.variant()))).reversed())
            .thenComparing(p -> x.get(p.variant()), exposureOrder()));
        if (usable.isEmpty()) return new Result("not_testable_no_reliable_outcome", lead.id(), "", original,
            Double.NaN, null, null, audit);
        Proxy selected = usable.get(0);
        audit.add(new Decision("selection", selected.variant(), "highest_ld_then_measurement_quality_then_exposure", selected.rSquared()));
        return fit("proxy_same_variant_effects", lead.id(), x.get(selected.variant()), y.get(selected.variant()),
            original, selected.rSquared(), policy, audit);
    }

    /** Conservative family correction includes untestable and unprocessed declared tests. */
    public static double bonferroni(double p, int declaredFamilySize) {
        probability(p, "MR P");
        if (declaredFamilySize < 1) throw new IllegalArgumentException("Positive declared family size required");
        return Math.min(1, p * declaredFamilySize);
    }

    private static Result fit(String status, String lead, Association x, Association y,
            String original, double r2, Policy policy, List<Decision> audit) {
        int sign = alignment(x, y);
        boolean complemented = !x.effectAllele().equals(y.effectAllele()) && !x.effectAllele().equals(y.otherAllele());
        var instrument = new HarmonizedInstrument(x.id(), x.effectAllele(), x.otherAllele(),
            x.beta(), x.se(), sign * y.beta(), y.se(), x.eaf(), sign == 1 ? y.eaf() : 1 - y.eaf(), sign == -1, complemented);
        MrEstimate estimate = MendelianRandomization.waldRatio(instrument, policy.confidenceLevel());
        if (!Double.isFinite(estimate.estimate()) || !Double.isFinite(estimate.standardError())
                || !Double.isFinite(estimate.confidenceLower()) || !Double.isFinite(estimate.confidenceUpper()))
            throw new IllegalArgumentException("MR effect or confidence interval exceeds numerical range");
        return new Result(status, lead, x.id(), original, r2, instrument, estimate, audit);
    }

    private static Comparator<Association> exposureOrder() {
        return Comparator.comparingDouble(Association::p)
            .thenComparing(Comparator.comparingDouble(Association::fStatistic).reversed())
            .thenComparing(Association::id);
    }
    private static String exposureReason(Association a, String chromosome, long start, long end, Policy policy) {
        if (!a.chromosome().equals(chromosome) || a.position() < start || a.position() > end) return "outside_cis_interval";
        String quality = qualityReason(a, policy);
        if (!quality.equals("usable")) return quality;
        if (palindrome(a)) return "palindromic_alleles";
        if (a.p() > policy.instrumentP()) return "exposure_p_above_threshold";
        if (a.fStatistic() < policy.minimumF()) return "weak_exposure_instrument";
        return "eligible";
    }
    private static String outcomeReason(Association x, Association y, Policy policy) {
        if (y == null) return "outcome_missing";
        String quality = qualityReason(y, policy);
        if (!quality.equals("usable")) return quality;
        if (!x.chromosome().equals(y.chromosome()) || x.position() != y.position()) return "coordinate_mismatch";
        if (palindrome(y)) return "palindromic_alleles";
        if (alignment(x, y) == 0) return "allele_mismatch";
        return "usable";
    }
    private static String qualityReason(Association a, Policy policy) {
        if (!a.qcPass()) return "source_qc_failed";
        if (a.measurement().equals("unknown")) return "measurement_unknown";
        if (a.measurement().equals("imputed")) {
            if (Double.isNaN(a.quality()) || a.qualityMetric().isEmpty()) return "imputation_quality_unknown";
            if (!a.qualityMetric().equals(policy.imputationMetric())) return "imputation_metric_mismatch";
            if (a.quality() < policy.minimumQuality()) return "imputation_quality_below_threshold";
        }
        return "usable";
    }
    private static double measurementQuality(Association a) { return a.measurement().equals("imputed") ? a.quality() : 1; }
    private static int alignment(Association x, Association y) {
        if (x.effectAllele().equals(y.effectAllele()) && x.otherAllele().equals(y.otherAllele())) return 1;
        if (x.effectAllele().equals(y.otherAllele()) && x.otherAllele().equals(y.effectAllele())) return -1;
        if (x.effectAllele().equals(complement(y.effectAllele())) && x.otherAllele().equals(complement(y.otherAllele()))) return 1;
        if (x.effectAllele().equals(complement(y.otherAllele())) && x.otherAllele().equals(complement(y.effectAllele()))) return -1;
        return 0;
    }
    private static boolean palindrome(Association a) { return a.effectAllele().equals(complement(a.otherAllele())); }
    private static String complement(String a) {
        return switch (a) { case "A" -> "T"; case "T" -> "A"; case "C" -> "G"; case "G" -> "C"; default -> ""; };
    }
    private static String allele(String a) {
        if (a == null || !a.toUpperCase(Locale.ROOT).matches("[ACGT]"))
            throw new IllegalArgumentException("Only biallelic SNP alleles A/C/G/T are supported");
        return a.toUpperCase(Locale.ROOT);
    }
    private static Map<String, Association> index(List<Association> rows) {
        var result = new LinkedHashMap<String, Association>();
        for (Association row : rows) if (result.put(row.id(), row) != null)
            throw new IllegalArgumentException("Duplicate variant ID: " + row.id());
        return result;
    }
    private static void probability(double value, String label) {
        if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Invalid " + label);
    }
    private static void token(String value, String label) {
        if (value == null || value.isBlank() || value.matches("(?s).*[\\t\\r\\n].*"))
            throw new IllegalArgumentException("Invalid " + label);
    }
}
