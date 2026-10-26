/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyResultMetadataPolicy;

/** Result normalization preserves declared aggregate metadata with field semantics. */
final class StudyResultMetadata {
    private final DelimitedData table;
    private final Map<String, String> columns = new LinkedHashMap<>();
    private final Map<String, Integer> present = new LinkedHashMap<>();
    private final Map<String, Integer> absent = new LinkedHashMap<>();
    StudyResultMetadata(DelimitedData table, Map<String, Object> configuration) throws IOException {
        this.table = table;
        Object raw = configuration.get("result_metadata");
        if (raw != null && !(raw instanceof Map<?, ?>)) throw new IOException("columns.result_metadata must be a mapping");
        Map<String, Object> explicit = StudyPublicEvidence.map(raw);
        for (String key : explicit.keySet()) if (!StudyResultMetadataPolicy.names().contains(key))
            throw new IOException("Unknown aggregate result metadata field: " + key);
        for (var entry : StudyResultMetadataPolicy.descriptors().entrySet()) {
            String key = entry.getKey();
            if (explicit.containsKey(key)) {
                if (!(explicit.get(key) instanceof String source) || source.isBlank() || !table.header().contains(source))
                    throw new IOException("columns.result_metadata." + key + " must identify an existing source column");
                columns.put(key, source);
            } else if (entry.getValue().automatic()) {
                List<String> matches = table.header().stream().filter(header -> entry.getValue().aliases().stream().anyMatch(header::equalsIgnoreCase)).distinct().toList();
                if (matches.size() > 1) throw new IOException("Ambiguous " + key + " columns " + matches + "; declare columns.result_metadata." + key);
                if (!matches.isEmpty()) columns.put(key, matches.get(0));
            }
        }
        columns.keySet().forEach(key -> { present.put(key, 0); absent.put(key, 0); });
    }
    List<String> header() { return List.copyOf(columns.keySet()); }
    List<String> values(String[] row) throws IOException {
        var values = new LinkedHashMap<String, String>();
        for (var entry : columns.entrySet()) {
            String value = row[table.column(entry.getValue())].trim();
            if (missing(value)) { values.put(entry.getKey(), "NA"); continue; }
            validate(entry.getKey(), value); values.put(entry.getKey(), value);
        }
        if (known(values, "n") && Double.parseDouble(values.get("n")) <= 0) throw new IOException("Result metadata n must be positive when supplied");
        if (known(values, "position") && Double.parseDouble(values.get("position")) <= 0) throw new IOException("Result metadata position must be positive");
        if (known(values, "ci_lower") && known(values, "ci_upper") && Double.parseDouble(values.get("ci_lower")) > Double.parseDouble(values.get("ci_upper")))
            throw new IOException("Result metadata confidence interval is reversed");
        if (known(values, "n") && known(values, "n_cases") && known(values, "n_controls")
                && Double.parseDouble(values.get("n_cases")) + Double.parseDouble(values.get("n_controls")) > Double.parseDouble(values.get("n")))
            throw new IOException("Result metadata case/control counts exceed n");
        if (known(values,"n") && known(values,"n_events") && known(values,"n_censored")
                && Double.parseDouble(values.get("n_events"))+Double.parseDouble(values.get("n_censored"))>Double.parseDouble(values.get("n")))
            throw new IOException("Result metadata event/censor counts exceed n");
        return List.copyOf(values.values());
    }
    void retained(List<String> values) {
        int index = 0;
        for (String key : columns.keySet()) { var counts = missing(values.get(index++)) ? absent : present; counts.put(key, counts.get(key) + 1); }
    }
    Map<String, Object> audit() {
        return Map.of("schema_version", 1, "source_columns", columns, "nonmissing_retained", present, "missing_retained", absent,
            "interpretation", "Typed aggregate fields copied from source columns; absent values stay unavailable. Counts are source-reported, not independently verified. No arbitrary text or participant-level columns are copied. INFO semantics remain provider-specific; labels require explicit mappings.");
    }
    private static boolean known(Map<String, String> row, String key) { return row.containsKey(key) && !missing(row.get(key)); }
    private static boolean missing(String value) { return value.isBlank() || List.of("NA", "NAN", "NULL", ".").contains(value.toUpperCase(java.util.Locale.ROOT)); }
    private static void validate(String key, String value) throws IOException {
        var kind = StudyResultMetadataPolicy.descriptors().get(key).kind();
        if (kind == StudyResultMetadataPolicy.Kind.TOKEN || kind == StudyResultMetadataPolicy.Kind.LABEL) {
            String pattern = key.equals("direction") ? "[+?0-]{1,10000}" : kind == StudyResultMetadataPolicy.Kind.TOKEN ? "[A-Za-z0-9<>*_.:+/-]{1,128}" : "[\\p{L}\\p{N}][\\p{L}\\p{N} _.,:;()+/%^=<>-]{0,159}";
            if (!value.matches(pattern)) throw new IOException("Result metadata " + key + " must be a bounded aggregate label");
            return;
        }
        double number;
        try { number = Double.parseDouble(value); } catch (NumberFormatException invalid) { throw new IOException("Result metadata " + key + " must be numeric", invalid); }
        if (!Double.isFinite(number)) throw new IOException("Result metadata " + key + " must be finite");
        boolean valid = switch (kind) {
            case COUNT -> number >= 0 && number == Math.rint(number) && number <= 9007199254740991.0;
            case NONNEGATIVE -> number >= 0;
            case POSITIVE -> number > 0;
            case PROBABILITY -> number >= 0 && number <= 1;
            case MAF -> number >= 0 && number <= .5;
            default -> true;
        };
        if(key.equals("log_p_value")&&number>0)throw new IOException("Result metadata log_p_value must be a nonpositive natural log probability");
        if (!valid) throw new IOException("Result metadata " + key + " violates " + kind + " semantics");
    }
}
