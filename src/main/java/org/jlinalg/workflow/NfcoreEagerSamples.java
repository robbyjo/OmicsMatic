/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.workflow;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/** Native inspection of already parsed nf-core/eager2.3.4/2.4.4 TSV rows.
 * Mirrors extract_data's metadata and filename checks. This API does not parse
 * TSV quoting, resolve paths, merge libraries, validate reads or admit analysis.
 * Original cells remain literal; in particular NA is case sensitive. Source
 * permissiveness (for example arbitrary lanes and strand labels) is preserved.
 */
public final class NfcoreEagerSamples {
    private NfcoreEagerSamples() { }
    public enum Version { EAGER_234, EAGER_244 }
    public static final List<String> COLUMNS = List.of("Sample_Name", "Library_ID", "Lane",
        "Colour_Chemistry", "SeqType", "Organism", "Strandedness", "UDG_Treatment", "R1", "R2", "BAM");

    /** Complete raw metadata snapshot, in input order. No identifiers are repaired. */
    public record Row(Map<String,String> cells) {
        public Row { cells = Collections.unmodifiableMap(new LinkedHashMap<>(cells)); }
        public String sample() { return cells.get("Sample_Name"); }
        public String library() { return cells.get("Library_ID"); }
        public List<String> files() {
            var paths = new ArrayList<String>();
            for (String field : List.of("R1", "R2", "BAM"))
                if (!cells.get(field).equals("NA")) paths.add(cells.get(field));
            return List.copyOf(paths);
        }
    }

    /** The existence predicate must use the same base directory and file scope
     * as the original Nextflow invocation. It is queried for each non-NA cell,
     * including repeated paths, before format/metadata checks, as upstream does.
     * Empty input produces an empty inspection, never a scientific success.
     */
    public static List<Row> inspect(Version version, List<? extends Map<String,String>> rows,
            Predicate<String> fileExists) throws IOException {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(rows, "rows");
        Objects.requireNonNull(fileExists, "fileExists");
        var result = new ArrayList<Row>();
        for (int index = 0; index < rows.size(); index++) {
            Map<String,String> cells = new LinkedHashMap<>(Objects.requireNonNull(rows.get(index), "row"));
            if (!cells.keySet().containsAll(COLUMNS)) throw invalid(index, "malformed column names");
            if (cells.size() != 11) throw invalid(index, "malformed row: require eleven mapped columns");
            for (String field : COLUMNS) {
                String value = cells.get(field);
                if (value == null) throw invalid(index, field + " is null" + (version == Version.EAGER_234
                    ? "; upstream2.3.4 calls isEmpty on this null value and also refuses the row" : ""));
                if (value.isEmpty()) throw invalid(index, field + " is empty");
            }
            for (String field : List.of("R1", "R2", "BAM")) {
                String value = cells.get(field);
                if (!value.equals("NA") && !fileExists.test(value))
                    throw invalid(index, "missing input file in " + field + ": " + value);
            }
            String r1 = cells.get("R1"), r2 = cells.get("R2"), bam = cells.get("BAM");
            if (r1.equals("NA") && r2.equals("NA") && bam.equals("NA")) throw invalid(index, "all files are NA");
            if (!bam.equals("NA") && cells.get("SeqType").equals("PE")) throw invalid(index, "BAM input cannot be PE");
            if (!List.of("none", "half", "full").contains(cells.get("UDG_Treatment"))) throw invalid(index, "invalid UDG treatment");
            if (!List.of("2", "4").contains(cells.get("Colour_Chemistry"))) throw invalid(index, "invalid colour chemistry");
            if (!List.of("PE", "SE").contains(cells.get("SeqType"))) throw invalid(index, "invalid sequence type");
            for (String field : List.of("R1", "R2")) {
                String value = cells.get(field);
                if (!value.equals("NA") && !fastq(value)) throw invalid(index, "unrecognized FASTQ suffix in " + field);
            }
            if (!bam.equals("NA") && !bam.toLowerCase(Locale.ROOT).endsWith("bam")) throw invalid(index, "unrecognized BAM suffix");
            result.add(new Row(cells));
        }
        return List.copyOf(result);
    }
    private static boolean fastq(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.endsWith("fastq.gz") || lower.endsWith("fq.gz") || lower.endsWith("fastq") || lower.endsWith("fq");
    }
    private static IOException invalid(int index, String cause) {
        return new IOException("EAGER ROW INSPECTION FAIL2 at input row " + (index + 1) + ": " + cause
            + ". Required action: retain original input and correct the named field/path before analysis. No row or result admitted.");
    }
}
