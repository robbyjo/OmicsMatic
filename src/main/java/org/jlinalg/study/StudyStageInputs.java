/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

/** Explicit cohort-preparation inputs; downstream genetic inputs belong to their consuming stages. */
public final class StudyStageInputs {
    private StudyStageInputs() { }
    private static final Set<String> CORE = Set.of("matrix", "phenotype", "results", "annotation",
        "sample", "samples", "hwe_samples", "grm", "pedigree", "ploidy_samples", "qc_genotypes",
        "qc_samples", "sample_qc_report", "sample_qc_samples", "sample_qc_pcs");

    public static boolean core(String type) {
        return Set.of("validate_harmonize", "participant_qc", "association").contains(type);
    }

    public static Map<String,Object> intakeFiles(Map<String,Object> dataset) {
        var result = new LinkedHashMap<String,Object>();
        StudyIO.optionalMap(dataset.get("files"), "files").forEach((role, path) -> {
            if (CORE.contains(role)) result.put(role, path);
        });
        return result;
    }

    static Map<String,Object> files(Map<String,Object> operation, Map<String,Object> dataset) {
        if ("assay_qc".equals(operation.get("type"))) {var source=StudyIO.optionalMap(dataset.get("files"),"files");var selected=new LinkedHashMap<String,Object>();if(source.containsKey("matrix"))selected.put("matrix",source.get("matrix"));return selected;}
        if (core(String.valueOf(operation.get("type"))) && !operation.containsKey("supplied_result_id")
                && !StudyIO.optionalMap(operation.get("configuration"), "configuration").containsKey("command"))
            return intakeFiles(dataset);
        return StudyIO.optionalMap(dataset.get("files"), "files");
    }
    /** Runtime intake observes availability, without reading unconsumed downstream data. */
    static Map<String,Object> availability(Path project, Map<String,Object> study) throws IOException {
        var rows = new ArrayList<Object>();
        for (Object raw : StudyIO.list(study.get("datasets"), "datasets")) {
            var d = StudyIO.map(raw, "dataset");
            if ("retired".equals(d.get("status"))) continue;
            for (var file : StudyIO.optionalMap(d.get("files"), "files").entrySet()) {
                Path path = project.resolve(String.valueOf(file.getValue())).normalize();
                boolean exists = Files.isRegularFile(path);
                var row = new LinkedHashMap<String,Object>();
                row.put("dataset_id", d.get("id"));
                row.put("file_role", file.getKey());
                row.put("path", file.getValue());
                row.put("status", exists ? "present" : "missing");
                row.put("cohort_preparation_input", CORE.contains(file.getKey()));
                if (exists) row.put("observed_bytes", Files.size(path));
                rows.add(row);
            }
        }
        return Map.of("schema_version", 1, "scope", "run_start_availability_only", "files", rows,
            "interpretation", "Availability is not consumption or scientific validation. Each executed operation records its actual declared input hashes. Paths remain local project metadata.");
    }
}
