/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** MR inference has its own prespecified primary estimator and correction family. */
public final class StudyMrTestingPolicy {
    private StudyMrTestingPolicy() { }
    public static Map<String, Object> resolve(Map<String, Object> supplied) {
        var result = new LinkedHashMap<>(supplied);
        if (!Set.of("primary_estimator", "adjustment", "alpha").containsAll(result.keySet()))
            throw new IllegalArgumentException("Unknown mr_testing setting");
        result.putIfAbsent("primary_estimator", "ivw-fixed"); result.putIfAbsent("adjustment", "BH"); result.putIfAbsent("alpha", .05);
        if (!Set.of("ivw-fixed", "ivw-random").contains(result.get("primary_estimator")))
            throw new IllegalArgumentException("mr_testing.primary_estimator must be ivw-fixed or ivw-random; a single instrument uses the declared Wald-ratio fallback");
        if (!Set.of("BH", "BY", "bonferroni", "NONE").contains(result.get("adjustment")))
            throw new IllegalArgumentException("mr_testing.adjustment must be BH, BY, bonferroni or NONE");
        if (!(result.get("alpha") instanceof Number alpha) || !Double.isFinite(alpha.doubleValue()) || alpha.doubleValue() <= 0 || alpha.doubleValue() >= 1)
            throw new IllegalArgumentException("mr_testing.alpha must be in (0,1)");
        return result;
    }
}
