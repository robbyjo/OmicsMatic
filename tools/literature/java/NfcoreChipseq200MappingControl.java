/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import org.jlinalg.workflow.NfcoreChipseq200Mappings;

/** Complete original checker comparison; no scientific analysis or ID repair. */
public final class NfcoreChipseq200MappingControl {
    private NfcoreChipseq200MappingControl() { }
    public static void main(String[] args) {
        try {
            var limits = NfcoreChipseq200Mappings.Limits.defaults(); NfcoreChipseq200Mappings.Plan plan;
            try (var input = Files.newBufferedReader(Path.of(args[0]), StandardCharsets.UTF_8)) { plan = NfcoreChipseq200Mappings.inspect(input, limits); }
            try (var checked = Files.newBufferedReader(Path.of(args[1]), StandardCharsets.UTF_8)) { NfcoreChipseq200Mappings.auditChecked(checked, plan, limits); }
            if (!java.util.Arrays.equals(plan.sourceCheckedCsv().getBytes(StandardCharsets.UTF_8), Files.readAllBytes(Path.of(args[1])))) throw new java.io.IOException("Complete original checked CSV bytes differ");
            boolean literal = true, graph = true;
            try { plan.requireLiteralIdentifiers(); } catch (java.io.IOException sourceChangesIdentifiers) { literal = false; }
            try { plan.requireConsistentLibraryMetadata(); } catch (java.io.IOException ambiguousLibrary) { graph = false; }
            for (var read : plan.reads()) if (!read.readGroupId().equals(read.id()) || !read.readGroupLibrary().equals(read.id()) || !read.readGroupSample().equals(read.libraryId())) throw new java.io.IOException("Original RG namespace differs");
            var report = new LinkedHashMap<String, Object>(); report.put("source_rows", plan.sourceRows().size()); report.put("checked_rows", plan.reads().size()); report.put("checked_cells", plan.reads().size() * 6); report.put("normalizations", plan.normalizations().size()); report.put("literal_scientific_identifiers", literal); report.put("consistent_library_metadata", graph); report.put("source_bytes_match", true); report.put("scientific_analysis_executed", false); report.put("biological_accuracy_certified", false); NextflowFiles.writeJson(Path.of(args[2]), report); System.out.println("PASS_COMPLETE_ORIGINAL_SAMPLESHEET_MAPPING_ONLY");
        } catch (Exception failure) { System.err.println("FAIL2 NO MAPPING ADMISSION: " + failure + "; retain original/checked cells and normalization warnings; no identifier repair or file substitution is inferred."); System.exit(2); }
    }
}
