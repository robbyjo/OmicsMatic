/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jlinalg.sequence.StringTieGtf;
import org.jlinalg.sequence.StringTieNormalization;
import org.jlinalg.sequence.StringTieTables;

/** Independent native audits of the exact, small, bundled original-runtime health population. */
final class StringTieHealth {
    private StringTieHealth() { }
    static final String[] TABLES = {"gene-abundance.tsv", "t_data.ctab", "e_data.ctab", "i_data.ctab", "e2t.ctab", "i2t.ctab"};
    private static final StringTieGtf.Limits GTF = new StringTieGtf.Limits(10000, 1000, 100, 100, 100, 65536, 1048576);
    static Map<String, Long> reference(Path inputs) throws IOException {
        var result = new LinkedHashMap<String, Long>();
        for (String line : text(inputs.resolve("reference.sizes")).split("\n")) {
            String[] fields = line.split("\t", -1);
            require(fields.length == 2, "Invalid exact synthetic chromosome-size row");
            long length; try { length = Long.parseLong(fields[1]); } catch (NumberFormatException error) { throw new IOException("Invalid synthetic chromosome length", error); }
            require(length > 0 && result.putIfAbsent(fields[0], length) == null, "Duplicate/invalid synthetic chromosome identity");
        }
        return Map.copyOf(result);
    }
    static Map<String, Object> audit(Path inputs, Path output, String version, Map<?, ?> health, Runnable check) throws IOException {
        Map<String, Long> reference = reference(inputs);
        var bam = StringTieBam.inspect(inputs.resolve("reads.bam"), reference,
            new StringTieBam.Policies(StringTieBam.MissingNh.ERROR, StringTieBam.MissingXs.ERROR, StringTieBam.SortHeader.REQUIRE_COORDINATE),
            StringTieBam.Limits.defaults(), row -> { }, check);
        require(bam.records() == 840 && bam.mapped() == 840 && bam.spliced() == 120 && bam.matchedBases() == 42000 && bam.skippedBases() == 26000,
            "Complete synthetic BAM census differs from the pinned original health population");
        var annotation = StringTieGtf.read(new StringReader(text(inputs.resolve("guide.gtf"))), reference, StringTieGtf.Profile.ANNOTATION, GTF, check);
        String raw = text(output.resolve("transcripts.gtf"));
        var estimation = StringTieGtf.read(new StringReader(raw), reference, StringTieGtf.Profile.ESTIMATION, GTF, check);
        var expected = NextflowFiles.object(health.get("expected_outputs"));
        require(expected.keySet().equals(java.util.Set.of(TABLES)), "Incomplete pinned StringTie health table population");
        for (String name : TABLES) require(NextflowFiles.pin(output.resolve(name), 1048576, check).equals(NextflowFiles.fingerprint(expected.get(name), 1048576)), "Fresh original StringTie health output differs: " + name);
        // Original GTF comments include the invocation's paths. Retain them raw;
        // the predeclared scientific comparison excludes only comment lines.
        var scientific = new StringBuilder();
        for (String line : raw.split("\n")) if (!line.startsWith("#")) scientific.append(line).append('\n');
        byte[] body = scientific.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        require(new NextflowFiles.Pin(body.length, NextflowFiles.sha(body)).equals(NextflowFiles.fingerprint(health.get("expected_GTF_scientific"), 1048576)), "Fresh StringTie GTF scientific rows differ from the exact-edition original");
        var tables = StringTieTables.auditEstimation(new StringTieTables.Inputs(reader(output, TABLES[0]), reader(output, TABLES[1]), reader(output, TABLES[2]),
            reader(output, TABLES[3]), reader(output, TABLES[4]), reader(output, TABLES[5])), annotation, estimation, StringTieGtf.PopulationPolicy.COMPLETE,
            new StringTieTables.Limits(10000, 65536, 1048576, 1000, 1000000), check);
        var normalization = StringTieNormalization.auditEstimation(StringTieNormalization.Edition.of(version), estimation, tables, check);
        require(estimation.transcripts().size() == 4 && tables.exons().size() == 7 && tables.genes().size() == 3 && normalization.rows().size() == 4 && normalization.omittedTranscriptIds().isEmpty(), "Incomplete native synthetic health audit");
        bam.verify(check);
        return Map.of("status", "passed-fresh-exact-original-synthetic-fit-and-native-audits", "version", version, "BAM", bam.document(),
            "transcripts", 4, "exons", 7, "genes", 3, "FPKM_denominator", denominator(normalization.fpkmDenominator()),
            "TPM_denominator", denominator(normalization.tpmDenominator()), "gene_reduction_diagnostics", tables.geneRelations().stream().map(r -> Map.of(
                "gene_id", r.geneId(), "union_exon_length", r.unionExonLength(), "weighted_coverage_residual", r.weightedCoverageResidual().toPlainString(),
                "printed_FPKM_residual", r.printedFpkmResidual().toPlainString(), "printed_TPM_residual", r.printedTpmResidual().toPlainString(),
                "emitted_transcripts", r.emittedGtfTranscripts(), "omitted_transcripts", r.omittedGtfTranscripts())).toList(), "biological_accuracy_certified", false);
    }
    private static Map<String, Object> denominator(StringTieNormalization.Denominator value) {
        var result = new LinkedHashMap<String, Object>();
        result.put("resolution", value.resolution().name()); result.put("rows", value.rows()); result.put("positive_outputs", value.positiveOutputs());
        result.put("positive_numerators", value.positiveNumerators()); result.put("active_branch_threshold", value.activeBranchThreshold());
        var bounds = value.activeBranchBounds(); result.put("active_branch_minimum", bounds == null ? null : bounds.minimum().toPlainString());
        result.put("active_branch_maximum", bounds == null || bounds.maximum() == null ? null : bounds.maximum().toPlainString());
        return result;
    }
    private static StringReader reader(Path root, String name) throws IOException { return new StringReader(text(root.resolve(name))); }
    private static String text(Path file) throws IOException { return NextflowFiles.utf8(NextflowFiles.bytes(file, 1048576)); }
    private static void require(boolean condition, String reason) throws IOException { if (!condition) throw new IOException(reason); }
}
