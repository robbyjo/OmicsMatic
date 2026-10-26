/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Scientific workflow defaults materialized in newly resolved study contracts. */
public final class StudyWorkflowPolicy {
    private StudyWorkflowPolicy() { }

    public static Map<String, Object> manuscript(Map<String, Object> configured) {
        Map<String, Object> policy = new LinkedHashMap<>(configured);
        Set<String> allowed = Set.of("max_main_words", "word_limit_mode", "minimum_findings", "maximum_findings");
        for (String key : policy.keySet())
            if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown manuscript_policy field: " + key);
        // Preserve an explicitly supplied legacy ceiling. Newly resolved default
        // manuscripts use a planning target and retain detailed supplements.
        policy.putIfAbsent("word_limit_mode", configured.containsKey("max_main_words") ? "strict" : "target");
        if (!"target".equals(policy.get("word_limit_mode")) && !"strict".equals(policy.get("word_limit_mode")))
            throw new IllegalArgumentException("manuscript_policy.word_limit_mode must be target or strict");
        policy.putIfAbsent("max_main_words", 8000);
        policy.putIfAbsent("minimum_findings", 3);
        policy.putIfAbsent("maximum_findings", 5);
        int words = integer(policy, "max_main_words", 1, 1000000);
        int minimum = integer(policy, "minimum_findings", 1, 100);
        int maximum = integer(policy, "maximum_findings", minimum, 100);
        policy.put("max_main_words", words);
        policy.put("minimum_findings", minimum);
        policy.put("maximum_findings", maximum);
        return policy;
    }

    static void resolveAssociation(Map<String, Object> analysis, boolean genetic) {
        Map<String, Object> options = StudyIO.optionalMap(analysis.get("association"), "analysis.association");
        if (genetic) {
            options.putIfAbsent("minimum_mac", 20.0);
            options.putIfAbsent("maximum_marker_missing", 0.02);
            options.putIfAbsent("minimum_sample_call_rate", 0.98);
            options.putIfAbsent("minimum_hwe_p",0.0);
            options.putIfAbsent("minimum_missingness_p",0.0);
            options.putIfAbsent("missingness_max_states",100000);
            options.putIfAbsent("chromosome_scope","cox".equals(options.get("model"))?"human_autosomes":"all");
            options.putIfAbsent("hwe_filter_scope",Set.of("binomial","quasi-binomial","probit","binomial-probit").contains(String.valueOf(options.get("family")))?"controls":"all");
            if("binomial".equals(options.get("family"))&&"glm".equals(options.get("model")))options.putIfAbsent("binary_test","efficient-score");
            if("exact-conditional".equals(options.get("binary_test"))){options.putIfAbsent("exact_maximum_states",200000);options.putIfAbsent("exact_maximum_work",50000000);options.putIfAbsent("exact_effect_maximum_parameters",50);}
            if ("imputed".equals(options.get("genotype_kind"))) options.putIfAbsent("minimum_info", 0.8);
        }
        if ("cox".equals(options.get("model"))) options.put("survival",StudySurvivalPolicy.resolve(StudyIO.optionalMap(options.get("survival"),"association.survival")));
        validateAssociation(options);
        if (!options.isEmpty()) analysis.put("association", options);
    }

    public static void validateAssociation(Map<String, Object> options) {
        if ("cox".equals(options.get("model"))) {
            var allowed=Set.of("model","survival","offset_column","threads","rationale","references","minimum_mac","maximum_mac","minimum_maf","maximum_maf","minimum_info","maximum_marker_missing","minimum_sample_call_rate","minimum_hwe_p","minimum_missingness_p","missingness_max_states","chromosome_scope","hwe_filter_scope","genotype_kind","human_ploidy");
            if(!allowed.containsAll(options.keySet()))throw new IllegalArgumentException("Unsupported native Cox association controls; use the explicit survival contract or a matching supplied/configured workflow");
            StudySurvivalPolicy.resolve(StudyIO.optionalMap(options.get("survival"),"association.survival"));
            if(options.containsKey("family")||options.containsKey("link")||options.containsKey("case_value")||options.containsKey("control_value")) throw new IllegalArgumentException("Cox uses its survival endpoint/codes; GLM family/link and case-control coding are inapplicable");
        } else if(options.containsKey("survival")) {
            if(options.containsKey("model")&&!String.valueOf(options.get("model")).isBlank())throw new IllegalArgumentException("association.survival requires model: cox");
            StudySurvivalPolicy.resolve(StudyIO.optionalMap(options.get("survival"),"association.survival"));
        }
        StudyHumanPloidyPolicy.resolve(options);
        if(options.containsKey("offset_column")&&(!(options.get("offset_column") instanceof String offset)||!offset.matches("[A-Za-z_][A-Za-z0-9_.]{0,199}")))throw new IllegalArgumentException("offset_column must be a bounded column identifier");
        for (String key : Set.of("minimum_mac", "maximum_mac"))
            if (options.containsKey(key)) number(options, key, 0, Double.MAX_VALUE);
        if (options.containsKey("maximum_mac") && options.containsKey("minimum_mac")
                && ((Number) options.get("maximum_mac")).doubleValue() < ((Number) options.get("minimum_mac")).doubleValue())
            throw new IllegalArgumentException("maximum_mac must be at least minimum_mac");
        for (String key : Set.of("minimum_maf", "maximum_maf"))
            if (options.containsKey(key)) number(options, key, 0, 0.5);
        if (options.containsKey("minimum_maf") && options.containsKey("maximum_maf")
                && ((Number) options.get("maximum_maf")).doubleValue() < ((Number) options.get("minimum_maf")).doubleValue())
            throw new IllegalArgumentException("maximum_maf must be at least minimum_maf");
        for (String key : Set.of("maximum_marker_missing", "minimum_sample_call_rate", "minimum_info", "minimum_hwe_p","minimum_missingness_p"))
            if (options.containsKey(key)) number(options, key, 0, 1);
        if (options.containsKey("genotype_kind") && !Set.of("auto","hard_calls", "imputed", "sequenced").contains(options.get("genotype_kind")))
            throw new IllegalArgumentException("genotype_kind must be auto, hard_calls, imputed or sequenced");
        if(options.containsKey("chromosome_scope")&&!Set.of("all","human_autosomes").contains(options.get("chromosome_scope")))throw new IllegalArgumentException("chromosome_scope must be all or human_autosomes");
        if(options.containsKey("hwe_filter_scope")&&!Set.of("all","controls","cases").contains(options.get("hwe_filter_scope")))throw new IllegalArgumentException("hwe_filter_scope must be all, controls or cases");
        if(options.containsKey("missingness_max_states"))integer(options,"missingness_max_states",1,Integer.MAX_VALUE);
        if(options.containsKey("case_value")!=options.containsKey("control_value"))throw new IllegalArgumentException("case_value and control_value must be supplied together");
        for(String key:Set.of("case_value","control_value"))if(options.containsKey(key)) {
            Object value=options.get(key);
            if(!(value instanceof String s&&!s.isBlank()&&s.length()<=200&&!s.chars().anyMatch(Character::isISOControl))&&!(value instanceof Number n&&Double.isFinite(n.doubleValue())))throw new IllegalArgumentException(key+" must be a nonblank bounded label or finite number");
        }
        if(options.containsKey("case_value")&&options.get("case_value").toString().trim().equalsIgnoreCase(options.get("control_value").toString().trim()))throw new IllegalArgumentException("Case and control codes must differ");
        if(options.containsKey("binary_test")) {
            if(!Set.of("efficient-score","firth-lrt","exact-conditional").contains(options.get("binary_test")))throw new IllegalArgumentException("binary_test must be efficient-score, firth-lrt or exact-conditional");
            if(!"binomial".equals(options.get("family"))||options.containsKey("model")&&!String.valueOf(options.get("model")).isBlank()&&!"glm".equals(options.get("model")))throw new IllegalArgumentException("binary_test requires the registered glm/binomial model");
        }
        if(options.containsKey("firth_maximum_iterations"))integer(options,"firth_maximum_iterations",1,100000);
        if(options.containsKey("firth_tolerance")) {double tolerance=number(options,"firth_tolerance",Double.MIN_NORMAL,.099999);if(!(tolerance>0))throw new IllegalArgumentException("firth_tolerance must be positive");}
        if((options.containsKey("firth_maximum_iterations")||options.containsKey("firth_tolerance"))&&!Set.of("firth-lrt","exact-conditional").contains(String.valueOf(options.get("binary_test"))))throw new IllegalArgumentException("Firth controls require a registered Firth effect/inference route");
        if(options.containsKey("exact_strata_column")&&(!(options.get("exact_strata_column") instanceof String s)||!s.matches("[A-Za-z_][A-Za-z0-9_.]{0,199}")))throw new IllegalArgumentException("exact_strata_column must be a bounded column identifier");
        if(options.containsKey("exact_maximum_states"))integer(options,"exact_maximum_states",1,Integer.MAX_VALUE);
        if(options.containsKey("exact_effect_maximum_parameters"))integer(options,"exact_effect_maximum_parameters",0,10000);
        if(options.containsKey("exact_maximum_work")){double work=number(options,"exact_maximum_work",1,9007199254740991.0);if(work!=Math.rint(work))throw new IllegalArgumentException("exact_maximum_work must be an integer");}
        if(options.keySet().stream().anyMatch(k->k.startsWith("exact_"))&&!"exact-conditional".equals(options.get("binary_test")))throw new IllegalArgumentException("Exact controls require binary_test: exact-conditional");
    }

    private static int integer(Map<String, Object> values, String key, int minimum, int maximum) {
        double value = number(values, key, minimum, maximum);
        if (value != Math.rint(value)) throw new IllegalArgumentException(key + " must be an integer");
        return (int) value;
    }

    private static double number(Map<String, Object> values, String key, double minimum, double maximum) {
        if (!(values.get(key) instanceof Number n) || !Double.isFinite(n.doubleValue())
                || n.doubleValue() < minimum || n.doubleValue() > maximum)
            throw new IllegalArgumentException(key + " must be finite and in [" + minimum + "," + maximum + "]");
        return n.doubleValue();
    }
}
