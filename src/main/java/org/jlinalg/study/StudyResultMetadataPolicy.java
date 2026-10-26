/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Typed aggregate result fields; arbitrary source columns are never copied. */
public final class StudyResultMetadataPolicy {
    public enum Kind { COUNT, NONNEGATIVE, POSITIVE, FINITE, PROBABILITY, MAF, TOKEN, LABEL }
    public record Field(Kind kind, List<String> aliases, boolean automatic) { }
    private static final Map<String, Field> FIELDS = fields();
    private StudyResultMetadataPolicy() { }
    public static Map<String, Field> descriptors() { return FIELDS; }
    public static Set<String> names() { return FIELDS.keySet(); }
    private static Map<String, Field> fields() {
        var result = new LinkedHashMap<String, Field>();
        add(result, "n", Kind.COUNT, "N", "n_samples", "sample_size", "OBS_CT");
        add(result, "n_cases", Kind.COUNT, "N_CASE", "N_CASES");
        add(result, "n_controls", Kind.COUNT, "N_CONTROL", "N_CONTROLS");
        for(String key:List.of("n_events","n_censored","survival_strata"))add(result,key,Kind.COUNT);
        add(result, "effective_n", Kind.POSITIVE, "N_EFF", "n_effective");
        add(result, "n_cohorts", Kind.COUNT);
        add(result, "reported_n_sum", Kind.NONNEGATIVE);
        add(result, "reported_n_min", Kind.POSITIVE);
        add(result, "reported_n_max", Kind.POSITIVE);
        add(result, "reported_n_known_cohorts", Kind.COUNT);
        add(result, "reported_n_missing_cohorts", Kind.COUNT);
        add(result, "called", Kind.COUNT, "called_samples");
        for(String key:List.of("fit_iterations","null_fit_iterations"))add(result,key,Kind.COUNT);
        for(String key:List.of("test_n_samples","test_n_cases","test_n_controls","test_strata","informative_strata","exact_work","exact_maximum_states","qc_n_samples","qc_n_cases","qc_n_controls"))add(result,key,Kind.COUNT);
        add(result,"conditional_score",Kind.FINITE);add(result,"conditional_null_mean",Kind.NONNEGATIVE);add(result,"conditional_null_variance",Kind.NONNEGATIVE);add(result,"exact_score_direction",Kind.TOKEN);
        for(String key:List.of("fit_converged","null_fit_converged"))add(result,key,Kind.TOKEN);
        for(String key:List.of("penalized_log_likelihood","null_penalized_log_likelihood","log_p_value"))add(result,key,Kind.FINITE);
        add(result,"allele_number",Kind.NONNEGATIVE,"AN");
        for(String key:List.of("called_cases","called_controls","missing_cases","missing_controls","non_diploid_samples","hwe_filter_n"))add(result,key,Kind.COUNT);
        for(String key:List.of("minor_allele_count_cases","minor_allele_count_controls"))add(result,key,Kind.NONNEGATIVE);
        for(String key:List.of("missingness_p_value","hwe_filter_p"))add(result,key,Kind.PROBABILITY);
        for(String key:List.of("minor_allele_orientation","source_multiallelic_split"))add(result,key,Kind.TOKEN);
        add(result, "missing", Kind.COUNT, "missing_samples");
        add(result, "minor_allele_count", Kind.NONNEGATIVE, "MAC");
        add(result, "minor_allele_frequency", Kind.MAF, "MAF");
        add(result, "effect_allele_frequency", Kind.PROBABILITY, "EAF");
        add(result, "call_rate", Kind.PROBABILITY);
        add(result, "missing_rate", Kind.PROBABILITY);
        add(result, "imputation_info", Kind.FINITE, "INFO", "imputation_quality");
        add(result, "hwe_p_all", Kind.PROBABILITY);
        add(result, "hwe_p_cases", Kind.PROBABILITY);
        add(result, "hwe_p_controls", Kind.PROBABILITY);
        add(result, "hwe_n_all", Kind.COUNT);
        add(result, "hwe_n_cases", Kind.COUNT);
        add(result, "hwe_n_controls", Kind.COUNT);
        add(result, "df_numerator", Kind.POSITIVE);
        add(result, "df_denominator", Kind.POSITIVE, "degrees_of_freedom", "df");
        add(result, "ci_lower", Kind.FINITE);
        add(result, "ci_upper", Kind.FINITE);
        add(result, "statistic", Kind.FINITE, "test_statistic");
        add(result, "partial_r2", Kind.PROBABILITY);
        add(result, "q", Kind.NONNEGATIVE, "q_statistic");
        add(result, "q_df", Kind.NONNEGATIVE);
        add(result, "q_p_value", Kind.PROBABILITY);
        add(result, "i_squared", Kind.NONNEGATIVE);
        add(result, "tau_squared", Kind.NONNEGATIVE);
        add(result, "direction", Kind.TOKEN);
        add(result, "effect_allele", Kind.TOKEN);
        add(result, "other_allele", Kind.TOKEN);
        add(result, "reference_allele", Kind.TOKEN, "REF");
        add(result, "alternate_allele", Kind.TOKEN, "ALT");
        add(result, "chromosome", Kind.TOKEN, "CHROM", "CHR");
        add(result, "position", Kind.COUNT, "POS", "BP");
        add(result, "genome_build", Kind.TOKEN);
        for (String key : List.of("test_method", "calibration", "model", "effect_scale", "effect_units", "result_type", "hwe_method","hwe_filter_method","hwe_filter_scope","missingness_method","genotype_origin","ploidy_status","estimator","standard_error_method","statistic_type","convergence_reason","effect_estimation_status","exact_status","exact_reason","exact_alternative","likelihood_existence","likelihood_proof_sha256"))
            result.put(key, new Field(Kind.LABEL, List.of(key), false));
        return Collections.unmodifiableMap(result);
    }
    private static void add(Map<String, Field> fields, String key, Kind kind, String... aliases) {
        var names = new java.util.ArrayList<String>(); names.add(key); names.addAll(List.of(aliases));
        fields.put(key, new Field(kind, List.copyOf(names), true));
    }
}
