/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jlinalg.study.StudyRareFrequencyPolicy;

/** Frequency source bytes, including a missing-resource marker, bind mask cache identity. */
public final class StudyRareFrequencyResources {
    private StudyRareFrequencyResources() { }
    public static Map<String,String> inputHashes(Path project, Map<String,Object> study, Map<String,Object> operation) throws IOException {
        var hashes = new LinkedHashMap<String,String>();
        if (!"rare_variant_masks".equals(operation.get("type")) || operation.containsKey("supplied_result_id")) return hashes;
        for (Object raw : StudyPublicEvidence.list(study.get("hypotheses"))) {
            var h = StudyPublicEvidence.map(raw);
            if (!h.get("id").equals(operation.get("hypothesis"))) continue;
            var rare = StudyPublicEvidence.map(StudyPublicEvidence.map(h.get("analysis")).get("rare_variants"));
            var policy = StudyRareFrequencyPolicy.resolve(StudyPublicEvidence.map(rare.get("external_frequency")));
            if (Boolean.TRUE.equals(policy.get("enabled")) && policy.containsKey("file")) {
                Path path = project.resolve(policy.get("file").toString()).normalize();
                hashes.put("rare_frequency:source", Files.isRegularFile(path) ? FollowupSupport.hash(path) : "missing");
            }
        }
        return hashes;
    }
}
