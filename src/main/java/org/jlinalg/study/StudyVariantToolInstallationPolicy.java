/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Managed software setup is distinct from data download and remote model access. */
final class StudyVariantToolInstallationPolicy {
    private StudyVariantToolInstallationPolicy() { }
    static Map<String,Object> resolve(Object supplied, String tool, boolean automatic) {
        var result = new LinkedHashMap<>(StudyIO.optionalMap(supplied, "variant tool installation"));
        var allowed = tool.equals("annovar") ? Set.of("mode", "home", "python", "archive", "source_url_env", "sha256", "accept_license") : Set.of("mode", "home", "python");
        if (!allowed.containsAll(result.keySet())) throw new IllegalArgumentException("Unknown " + tool + " installation setting");
        result.putIfAbsent("mode", automatic ? "automatic" : "external");
        if (!Set.of("automatic", "external").contains(result.get("mode"))) throw new IllegalArgumentException("Installation mode must be automatic or external");
        for (var entry : result.entrySet()) if (!entry.getKey().equals("accept_license")) {
            if (!(entry.getValue() instanceof String value) || value.isBlank() || value.length() > 4096 || value.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Installation settings must be bounded nonblank strings");
        }
        if (result.containsKey("accept_license") && !(result.get("accept_license") instanceof Boolean)) throw new IllegalArgumentException("accept_license must be boolean");
        if (result.containsKey("source_url_env") && !((String)result.get("source_url_env")).matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("source_url_env must name an environment variable");
        if (result.containsKey("sha256") && !((String)result.get("sha256")).matches("[a-fA-F0-9]{64}")) throw new IllegalArgumentException("Installation sha256 must be a SHA-256 digest");
        if (result.containsKey("archive") && result.containsKey("source_url_env")) throw new IllegalArgumentException("Supply an ANNOVAR archive or download-URL environment variable, not both");
        return result;
    }
}
