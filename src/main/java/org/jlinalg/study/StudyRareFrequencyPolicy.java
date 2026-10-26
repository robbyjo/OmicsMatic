/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Prespecified external ALT-frequency eligibility, separate from observed cohort MAF. */
public final class StudyRareFrequencyPolicy {
    private StudyRareFrequencyPolicy() { }

    public static Map<String,Object> resolve(Map<String,Object> configured) {
        var policy = new LinkedHashMap<>(configured);
        if (!Set.of("enabled", "file", "source", "release", "genome_build", "populations",
                "minimum_allele_number", "maximum_rows", "rationale").containsAll(policy.keySet()))
            throw new IllegalArgumentException("Unknown rare_variants.external_frequency field");
        policy.putIfAbsent("enabled", !configured.isEmpty());
        if (!(policy.get("enabled") instanceof Boolean))
            throw new IllegalArgumentException("external_frequency.enabled must be boolean");
        for (String field : Set.of("file", "source", "release", "genome_build", "rationale")) if (policy.containsKey(field)) {
            String value = StudyIO.requireString(policy, field, "external_frequency");
            if (value.length() > 4000 || value.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("external_frequency." + field + " must be bounded nonblank text");
        }
        if (policy.containsKey("populations")) {
            var seen = new LinkedHashSet<String>();
            for (Object raw : StudyIO.list(policy.get("populations"), "external_frequency.populations")) {
                if (!(raw instanceof String label) || !label.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}") || !seen.add(label))
                    throw new IllegalArgumentException("external_frequency.populations must contain distinct explicit source labels");
            }
            if (seen.isEmpty()) throw new IllegalArgumentException("external_frequency.populations cannot be empty");
            policy.put("populations", java.util.List.copyOf(seen));
        }
        policy.putIfAbsent("maximum_rows", 1000000);
        integer(policy, "maximum_rows", 1, 10000000);
        if (policy.containsKey("minimum_allele_number")) integer(policy, "minimum_allele_number", 1, 9007199254740991L);
        return policy;
    }

    private static void integer(Map<String,Object> policy, String field, long minimum, long maximum) {
        if (!(policy.get(field) instanceof Number n) || !Double.isFinite(n.doubleValue())
                || n.doubleValue() != n.longValue() || n.longValue() < minimum || n.longValue() > maximum)
            throw new IllegalArgumentException("external_frequency." + field + " must be an integer in [" + minimum + "," + maximum + "]");
    }
}
