/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyResultMetadataPolicy;

/** Keep pooled estimates separate from source-reported cohort coverage. */
final class StudyMetaMetadata {
    static final List<String> COUNTS = List.of("reported_n_sum", "reported_n_min", "reported_n_max", "reported_n_known_cohorts", "reported_n_missing_cohorts");
    private StudyMetaMetadata() { }
    static List<String> attach(Path output, Map<String, Path> inputs) throws IOException {
        var fields = new java.util.LinkedHashSet<String>();
        var tables = new LinkedHashMap<String, DelimitedData>();
        var hashes = new LinkedHashMap<String, Object>();
        for (var entry : inputs.entrySet()) {
            var table = DelimitedData.readResult(entry.getValue()); tables.put(entry.getKey(), table);
            for (String field : table.header()) if (StudyResultMetadataPolicy.names().contains(field)) fields.add(field);
            hashes.put(entry.getKey(), FollowupSupport.hash(entry.getValue()));
        }
        var byFeature = new LinkedHashMap<String, List<Map<String, String>>>();
        List<String> sourceHeader = new ArrayList<>(List.of("dataset_id", "feature_id")); sourceHeader.addAll(fields);
        List<String[]> sourceRows = new ArrayList<>();
        for (var entry : tables.entrySet()) {
            var table = entry.getValue(); int id = table.column("feature_id");
            for (String[] row : table.rows()) {
                var metadata = new LinkedHashMap<String, String>(); metadata.put("dataset_id", entry.getKey()); metadata.put("feature_id", row[id]);
                for (String field : fields) metadata.put(field, table.header().contains(field) ? row[table.column(field)] : "NA");
                double beta = value(table, row, "beta"), se = value(table, row, "se");
                if (!Double.isFinite(beta) || !Double.isFinite(se) || se <= 0) continue;
                byFeature.computeIfAbsent(row[id], ignored -> new ArrayList<>()).add(metadata);
                sourceRows.add(sourceHeader.stream().map(metadata::get).toArray(String[]::new));
            }
        }
        Path directory = output.getParent(), raw = directory.resolve("meta-estimates.tsv");
        var pooled = DelimitedData.readResult(output);
        List<String> header = new ArrayList<>(pooled.header()); header.addAll(COUNTS);
        var rows = new ArrayList<String[]>();
        for (String[] row : pooled.rows()) {
            var sources = byFeature.getOrDefault(row[pooled.column("feature_id")], List.of());
            if (Double.parseDouble(row[pooled.column("n_cohorts")]) != sources.size()) throw new IOException("Meta-analysis metadata cohort coverage differs from the fitted family");
            double sum = 0, min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY; int known = 0;
            for (var source : sources) {
                double n; try { n = Double.parseDouble(source.getOrDefault("n", "NA")); } catch (NumberFormatException missing) { continue; }
                if (!Double.isFinite(n) || n <= 0) continue;
                known++; sum += n; min = Math.min(min, n); max = Math.max(max, n);
            }
            var result = new ArrayList<String>(List.of(row));
            result.add(known == 0 ? "NA" : Double.toString(sum)); result.add(known == 0 ? "NA" : Double.toString(min)); result.add(known == 0 ? "NA" : Double.toString(max));
            result.add(Integer.toString(known)); result.add(Integer.toString(sources.size() - known)); rows.add(result.toArray(String[]::new));
        }
        Files.move(output, raw);
        FollowupSupport.table(output, header, rows);
        FollowupSupport.table(directory.resolve("cohort-result-metadata.tsv"), sourceHeader, sourceRows);
        StudyPublicEvidence.write(directory.resolve("meta-result-metadata.yaml"), Map.of("schema_version", 1,
            "raw_estimates", "meta-estimates.tsv", "raw_sha256", FollowupSupport.hash(raw), "augmented_sha256", FollowupSupport.hash(output), "source_sha256", hashes,
            "count_fields", COUNTS, "interpretation", "reported_n_sum is the sum of available contributing cohorts' source-reported N, not a deduplicated participant count or effective sample size. Missing cohort counts are explicit. Effect estimates, uncertainty and significance are copied unchanged from the backend; no N is inferred from degrees of freedom, file lengths or enrollment."));
        return List.of("meta-estimates.tsv", "cohort-result-metadata.tsv", "meta-result-metadata.yaml");
    }
    private static double value(DelimitedData table, String[] row, String field) {
        try { return Double.parseDouble(row[table.column(field)]); } catch (NumberFormatException invalid) { return Double.NaN; }
    }
}
