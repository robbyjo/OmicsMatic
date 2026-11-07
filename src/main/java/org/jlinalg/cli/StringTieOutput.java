/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jlinalg.sequence.StringTieGtf;
import org.jlinalg.sequence.StringTieNormalization;
import org.jlinalg.sequence.StringTieTables;

/** Complete native output audits; original fitting is delegated to the exact admitted edition. */
final class StringTieOutput {
    private StringTieOutput() { }
    static Map<String, Object> audit(Path output, StringTieRecipe.Request request, StringTieInputs.Data inputs, Runnable check) throws IOException {
        var profile = switch (request.mode()) { case ASSEMBLE -> StringTieGtf.Profile.ASSEMBLY; case ESTIMATE -> StringTieGtf.Profile.ESTIMATION; case MERGE -> StringTieGtf.Profile.MERGE; };
        var gtf = StringTieInputs.gtf(output.resolve("transcripts.gtf"), inputs.reference(), profile, request, check);
        var result = new LinkedHashMap<String, Object>(); result.put("mode", request.mode().name()); result.put("version", request.version()); result.put("transcripts", gtf.transcripts().size());
        result.put("exons", gtf.transcripts().stream().mapToLong(t -> t.exons().size()).sum()); result.put("exonic_bases_with_transcript_multiplicity", gtf.transcripts().stream().mapToLong(StringTieGtf.Transcript::exonicLength).sum());
        result.put("empty_output", gtf.transcripts().isEmpty()); result.put("biological_accuracy_certified", false);
        if (request.mode() == StringTieRecipe.Mode.ESTIMATE) {
            try (var genes = StringTieInputs.reader(output.resolve("gene-abundance.tsv")); var transcripts = StringTieInputs.reader(output.resolve("t_data.ctab")); var exons = StringTieInputs.reader(output.resolve("e_data.ctab"));
                 var introns = StringTieInputs.reader(output.resolve("i_data.ctab")); var e2t = StringTieInputs.reader(output.resolve("e2t.ctab")); var i2t = StringTieInputs.reader(output.resolve("i2t.ctab"))) {
                var tables = StringTieTables.auditEstimation(new StringTieTables.Inputs(genes, transcripts, exons, introns, e2t, i2t), inputs.guide(), gtf, request.population(), StringTieRecipe.tables(request.limits()), check);
                var normalization = StringTieNormalization.auditEstimation(StringTieNormalization.Edition.of(request.version()), gtf, tables, check);
                result.put("population_policy", request.population().name()); result.put("emitted", tables.reference().emitted()); result.put("omitted", tables.reference().omitted());
                result.put("reference_population", tables.reference().rows().stream().map(r -> Map.of("transcript", r.transcriptId(), "reference_ordinal", r.referenceOrdinal(), "output_ordinal", r.outputOrdinal(), "disposition", r.disposition().name())).toList());
                result.put("FPKM_denominator", denominator(normalization.fpkmDenominator())); result.put("TPM_denominator", denominator(normalization.tpmDenominator()));
                result.put("genes", tables.genes().size()); result.put("wildcard_feature_links", tables.wildcardLinks());
                result.put("gene_reduction_diagnostics", tables.geneRelations().stream().map(r -> Map.of("gene", r.geneId(), "union_exon_length", r.unionExonLength(), "weighted_coverage_residual", r.weightedCoverageResidual().toPlainString(),
                    "printed_FPKM_residual", r.printedFpkmResidual().toPlainString(), "printed_TPM_residual", r.printedTpmResidual().toPlainString(), "emitted", r.emittedGtfTranscripts(), "omitted", r.omittedGtfTranscripts())).toList());
            }
        }
        return result;
    }
    private static Map<String, Object> denominator(StringTieNormalization.Denominator value) {
        var result = new LinkedHashMap<String, Object>(); result.put("resolution", value.resolution().name()); result.put("rows", value.rows()); result.put("positive_outputs", value.positiveOutputs()); result.put("positive_numerators", value.positiveNumerators());
        result.put("active_branch_threshold_float64", Double.toHexString(value.activeBranchThreshold())); var bounds = value.activeBranchBounds();
        result.put("active_branch_minimum", bounds == null ? null : bounds.minimum().toPlainString()); result.put("active_branch_maximum", bounds == null || bounds.maximum() == null ? null : bounds.maximum().toPlainString()); return result;
    }
}
