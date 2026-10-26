/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jlinalg.study.StudyFeatureLocationPolicy;

/** Resolve measured-feature coordinates independently from gene-name availability. */
final class StudyGeneticFeatureLocation {
    static final String CAVEAT = "The exact molecular feature uses a reviewed source location while its local gene annotation remains missing, incomplete or ambiguous. The location declaration does not resolve a gene name, establish assay equivalence or validate a biological mechanism; MR instrument and causal assumptions require separate review.";
    private StudyGeneticFeatureLocation() { }
    static Map<String,Object> resolve(StudyBioinformatics.Feature finding, Map<String,Object> pair, String build, boolean mr) {
        Map<String,Object> declared = pair.containsKey("feature_location") ? StudyFeatureLocationPolicy.resolve(map(pair.get("feature_location"))) : Map.of();
        if (!declared.isEmpty() && !finding.id().equals(declared.get("feature_id"))) throw new IllegalArgumentException("feature_location must identify the exact measured feature");
        var resolved = new LinkedHashMap<String,Object>();
        if ("located".equals(finding.status())) {
            resolved.put("feature_id", finding.id()); resolved.put("genome_build", finding.build());
            resolved.put("chromosome", finding.chromosome().replaceFirst("(?i)^chr", ""));
            resolved.put("start", Long.parseLong(finding.start())); resolved.put("end", Long.parseLong(finding.end()));
            resolved.put("coordinate_system", "1-based-closed"); resolved.put("origin", "sealed_feature_annotation");
            if (!declared.isEmpty()) for (String key : new String[]{"genome_build", "chromosome", "start", "end"})
                if (!resolved.get(key).equals(declared.get(key))) throw new IllegalArgumentException("Reviewed feature_location conflicts with the sealed feature annotation: " + key);
        } else {
            if (!mr || declared.isEmpty()) throw new IllegalArgumentException("The measured feature needs a verified location; MR can use an approved pair's exact, sourced feature_location without inventing a gene annotation");
            resolved.putAll(declared); resolved.put("origin", "reviewed_pair_feature_location");
        }
        if (!build.equals(resolved.get("genome_build"))) throw new IllegalArgumentException("Measured feature location differs from the exposure/outcome/LD genome build");
        if (!declared.isEmpty()) resolved.put("reviewed_location_declaration", declared);
        resolved.put("original_annotation_status", finding.status()); resolved.put("gene_annotation_inferred", false);
        resolved.put("interpretation", "Exact measured-feature coordinates support the reviewed regional MR contract. Gene identity, assay equivalence, instrument validity and biological causality are not established by a location declaration.");
        return resolved;
    }
}
