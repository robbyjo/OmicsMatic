/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Explicit sourced location of an exact molecular feature; no gene identity is inferred. */
public final class StudyFeatureLocationPolicy {
    private StudyFeatureLocationPolicy() { }
    public static Map<String,Object> resolve(Map<String,Object> raw) {
        var result = new LinkedHashMap<>(raw);
        var allowed = Set.of("feature_id", "genome_build", "chromosome", "start", "end", "coordinate_system", "source_id", "citation", "rationale");
        for (String key : result.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown feature_location field: " + key);
        for (String key : Set.of("feature_id", "genome_build", "chromosome", "coordinate_system", "source_id", "citation", "rationale")) {
            String value = StudyIO.requireString(result, key, "feature_location");
            if (value.length() > 2000 || value.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid bounded feature_location." + key);
        }
        if (!Set.of("GRCh37", "GRCh38").contains(result.get("genome_build"))) throw new IllegalArgumentException("feature_location requires an explicit GRCh37/GRCh38 build");
        if (!"1-based-closed".equals(result.get("coordinate_system"))) throw new IllegalArgumentException("feature_location.coordinate_system must be 1-based-closed");
        String chromosome = ((String) result.get("chromosome")).replaceFirst("(?i)^chr", "").toUpperCase(Locale.ROOT);
        if (chromosome.equals("M")) chromosome = "MT";
        if (!chromosome.matches("(?:[1-9]|1[0-9]|2[0-2]|X|Y|MT)")) throw new IllegalArgumentException("Unsupported feature_location human chromosome");
        result.put("chromosome", chromosome);
        long start = coordinate(result, "start"), end = coordinate(result, "end");
        if (end < start) throw new IllegalArgumentException("feature_location.end must be at or after start");
        result.put("start", start); result.put("end", end);
        return result;
    }
    private static long coordinate(Map<String,Object> value, String key) {
        if (!(value.get(key) instanceof Number number) || !Double.isFinite(number.doubleValue()) || number.doubleValue() != Math.rint(number.doubleValue())
                || number.doubleValue() < 1 || number.doubleValue() > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid feature_location." + key);
        return number.longValue();
    }
}
