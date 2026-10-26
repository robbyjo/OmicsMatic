/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyMrTestingPolicy;
import org.jlinalg.study.StudyNeed;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyMrMultiplicityTest {
    @TempDir Path project;
    private Context context(Map<String, Object> extra) throws Exception {
        Path input = project.resolve("input"), output = project.resolve("output"); Files.createDirectories(input.resolve("standard-results")); Files.createDirectories(output);
        Files.writeString(input.resolve("manifest.yaml"), "operation_type: validate_harmonize\nstate: complete\n");
        Files.writeString(input.resolve("standard-results/a.tsv"), "feature_id\tp_value\ng1\t.0001\ng2\t.0002\ng3\t.0003\ng4\t.0004\n");
        Files.writeString(input.resolve("feature-annotation.tsv"), "feature_id\tgene_symbol\tgene_id\tchromosome\tstart\tend\tgenome_build\tmapping_status\n"
            + "g1\tGENE1\tENSG1\t1\t100\t200\tGRCh38\tlocated\n" + "g2\tGENE2\tENSG2\t1\t100\t200\tGRCh38\tlocated\n"
            + "g3\tGENE3\tENSG3\t1\t100\t200\tGRCh38\tlocated\n" + "g4\tGENE4\tENSG4\t1\t100\t200\tGRCh38\tlocated\n");
        Files.writeString(project.resolve("x.tsv"), "synthetic exposure\n"); Files.writeString(project.resolve("y.tsv"), "synthetic outcome\n");
        Map<String, Object> study = Map.of("datasets", List.of(Map.of("id", "a", "type", "transcriptomics", "files", Map.of("exposure", "x.tsv", "outcome", "y.tsv"))),
            "hypotheses", List.of(Map.of("id", "H1", "outcome", "BMI", "analysis", Map.of("bioinformatics", extra))));
        StudyTestSources.seal(input,"validate_harmonize","harmonize","H1");
        return new Context(project, output, Map.of("id", "mr", "type", "mendelian_randomization", "hypothesis", "H1", "datasets", List.of("a")), study, Map.of(), Map.of("harmonize", input));
    }
    private Map<String, Object> complete(String feature, String pair, double p) {
        var result = new LinkedHashMap<String, Object>(); result.put("feature_id", feature); result.put("pair_id", pair); result.put("status", "complete");
        result.put("sources", Map.of("feature_id", feature, "exposure", Map.of("file_role", "exposure", "trait", "isoform ratio"), "outcome", Map.of("file_role", "outcome", "trait", "BMI")));
        result.put("mr_primary", Map.of("requested_estimator", "ivw-fixed", "estimate", ".5", "standard_error", ".1", "causal_p_value", Double.toString(p)));
        result.put("mr_estimates", List.of(Map.of("requested_estimator", "egger", "causal_p_value", "1e-100")));
        return result;
    }
    @Test void correctionUsesTheDeclaredDenominatorAndStandardAdjustmentReferences() {
        var p = Map.of("a", .01, "b", .04, "c", .2);
        var bh = StudyMrMultiplicity.adjust(p, 5, "BH"); assertEquals(.05, bh.get("a"), 1e-15); assertEquals(.1, bh.get("b"), 1e-15); assertEquals(1.0 / 3, bh.get("c"), 1e-15);
        assertEquals(.01 * 5 * (1 + .5 + 1.0 / 3 + .25 + .2), StudyMrMultiplicity.adjust(p, 5, "BY").get("a"), 1e-15);
        assertEquals(.2, StudyMrMultiplicity.adjust(p, 5, "bonferroni").get("b"), 1e-15); assertEquals(.04, StudyMrMultiplicity.adjust(p, 5, "NONE").get("b"));
        assertThrows(IllegalArgumentException.class, () -> StudyMrMultiplicity.adjust(p, 2, "BH"));
    }
    @Test void nativeStudyDispatchAccountsForEveryEligibleFeatureWithoutAnySourceDeclaration() throws Exception {
        Context context = context(Map.of()); var result = new StudyCliExecutor().execute(context);
        assertEquals(org.jlinalg.study.StudyOperationExecutor.State.WAITING_FOR_INPUT, result.state());
        assertEquals(4, map(result.details().get("mr_testing")).get("unresolved_tests"));
        assertEquals(4, DelimitedData.readResult(context.outputDirectory().resolve("mr-feature-dispositions.tsv")).rows().size());
        assertTrue(result.outputs().contains("request.yaml"));
        assertFalse(Files.exists(context.outputDirectory().resolve("mr-instruments.tsv")));
    }

    @Test void pendingEligibleFeaturesRemainInTheFamilyAndCannotProduceConfirmedSignificance() throws Exception {
        Context context = context(Map.of()); var results = new ArrayList<Object>(List.of(complete("g1", "p1", .01), complete("g2", "p2", .04), complete("g3", "p3", .2)));
        List<StudyNeed> needs = new ArrayList<>(); var summary = StudyMrMultiplicity.finish(context, results, Map.of(), needs);
        assertEquals(4, summary.get("family_size")); assertEquals(1, summary.get("unresolved_tests")); assertEquals("provisional_unresolved_primary_tests", summary.get("status")); assertEquals(1, needs.size());
        var table = DelimitedData.readResult(context.outputDirectory().resolve("mr-primary-results.tsv")); assertEquals(4, table.rows().size());
        var first = table.rows().stream().filter(row -> row[table.column("feature_id")].equals("g1")).findFirst().orElseThrow();
        assertEquals(.04, Double.parseDouble(first[table.column("adjusted_p")]), 1e-15); assertEquals("false", first[table.column("significant_primary")]);
        var missing = table.rows().stream().filter(row -> row[table.column("feature_id")].equals("g4")).findFirst().orElseThrow();
        assertEquals("NA", missing[table.column("causal_p_value")]); assertEquals("NA", missing[table.column("adjusted_p")]);
        assertEquals(4, results.size()); assertTrue(map(map(results.get(0)).get("mr_multiplicity")).containsKey("family_status"));
    }
    @Test void exhaustedSourcesCompleteCoverageAndDuplicateSourceHypothesesShareOneTest() throws Exception {
        Context context = context(Map.of()); var results = new ArrayList<Object>(List.of(complete("g1", "p1", .01), complete("g1", "alias", .01), complete("g2", "p2", .04), complete("g3", "p3", .2)));
        List<StudyNeed> needs = new ArrayList<>(); var summary = StudyMrMultiplicity.finish(context, results,
            Map.of("features", List.of(Map.of("feature_id", "g4", "status", "insufficient_strong_candidates_before_outcome_or_ld_lookup"))), needs);
        assertEquals(4, summary.get("family_size")); assertEquals(1, summary.get("not_estimable_tests")); assertEquals(1, summary.get("duplicate_pair_aliases"));
        assertEquals("complete_declared_family", summary.get("status")); assertTrue(needs.isEmpty());
        var first = map(map(results.get(0)).get("mr_multiplicity")); assertEquals(.04, (Double) first.get("adjusted_p"), 1e-15); assertEquals(true, first.get("significant_primary"));
        assertEquals(4, DelimitedData.readResult(context.outputDirectory().resolve("mr-primary-results.tsv")).rows().size());
    }
    @Test void conflictingDuplicatePrimariesRequireReviewRatherThanChoosingTheSmallestP() throws Exception {
        Context context = context(Map.of()); var results = new ArrayList<Object>(List.of(complete("g1", "p1", .01), complete("g1", "different-rule", .00001), complete("g2", "p2", .04), complete("g3", "p3", .2), complete("g4", "p4", .8)));
        List<StudyNeed> needs = new ArrayList<>(); var summary = StudyMrMultiplicity.finish(context, results, Map.of(), needs);
        assertEquals(4, summary.get("family_size")); assertEquals(1, summary.get("unresolved_tests")); assertEquals(1, needs.size());
        assertEquals("conflicting_duplicate_primary", map(results.get(0)).get("mr_adjustment_status"));
        String text = Files.readString(context.outputDirectory().resolve("mr-primary-results.tsv")); assertTrue(text.contains("conflicting_duplicate_primary\tNA"));
    }
    @Test void inconsistentEstimabilityOfDuplicateSourcesCannotSelectTheUsableAnalysis() throws Exception {
        Context context = context(Map.of()); var failed = complete("g1", "different-filter", .01); failed.remove("mr_primary"); failed.put("status", "not_estimable");
        var results = new ArrayList<Object>(List.of(complete("g1", "p1", .01), failed, complete("g2", "p2", .04), complete("g3", "p3", .2), complete("g4", "p4", .8)));
        List<StudyNeed> needs = new ArrayList<>(); var summary = StudyMrMultiplicity.finish(context, results, Map.of(), needs);
        assertEquals(1, summary.get("unresolved_tests")); assertEquals(1, needs.size());
        assertEquals("NA", map(map(results.get(0)).get("mr_multiplicity")).get("adjusted_p"));
    }

    @Test void explicitSourceCapsRetainAllEligibleDispositionsWithoutCallingOmittedFeaturesNull() throws Exception {
        Context context = context(Map.of("mr", Map.of("max_features", 2))); var results = new ArrayList<Object>(List.of(complete("g1", "p1", .01), complete("g2", "p2", .04)));
        List<StudyNeed> needs = new ArrayList<>(); var summary = StudyMrMultiplicity.finish(context, results, Map.of(), needs);
        assertEquals(4, summary.get("eligible_features")); assertEquals(2, summary.get("family_size")); assertEquals(2, summary.get("outside_explicit_feature_budget")); assertTrue(needs.isEmpty());
        var rows = DelimitedData.readResult(context.outputDirectory().resolve("mr-feature-dispositions.tsv")); assertEquals(4, rows.rows().size());
        assertEquals(2, rows.rows().stream().filter(row -> row[rows.column("disposition")].equals("outside_explicit_feature_budget")).count());
    }
    @Test void missingAnnotationCannotShrinkTheEligibleMrDenominator() throws Exception {
        var c=context(Map.of());var upstream=c.dependencyDirectories().get("harmonize");Files.delete(upstream.resolve("feature-annotation.tsv"));StudyTestSources.seal(upstream,"validate_harmonize","harmonize","H1");
        assertTrue(StudyBioinformatics.features(c).isEmpty());assertEquals(4,StudyBioinformatics.mrFeatures(c).size());assertTrue(StudyBioinformatics.mrAllFeatures(c).stream().allMatch(f->f.status().equals("missing_annotation")));
        var results=new ArrayList<Object>(List.of(complete("g1","p1",.01)));var needs=new ArrayList<StudyNeed>();var summary=StudyMrMultiplicity.finish(c,results,Map.of(),needs);
        assertEquals(4,summary.get("family_size"));assertEquals(3,summary.get("unresolved_tests"));assertEquals(4L,summary.get("eligible_without_local_location"));assertEquals(true,summary.get("eligibility_before_annotation"));
        var table=DelimitedData.readResult(c.outputDirectory().resolve("mr-primary-results.tsv"));var row=table.rows().stream().filter(r->r[table.column("feature_id")].equals("g1")).findFirst().orElseThrow();assertEquals(.04,Double.parseDouble(row[table.column("adjusted_p")]));assertEquals("false",row[table.column("significant_primary")]);
        var inventory=DelimitedData.readResult(c.outputDirectory().resolve("mr-feature-dispositions.tsv"));assertEquals(4,inventory.rows().size());assertTrue(inventory.rows().stream().allMatch(r->r[inventory.column("annotation_status")].equals("missing_annotation")&&r[inventory.column("association_testing_denominator")].equals("4")&&r[inventory.column("association_source_sha256")].matches("[a-f0-9]{64}")));
    }
    @Test void missingAndAmbiguousAnnotationsRemainInsideAnExplicitFeatureCap() throws Exception {
        var c=context(Map.of("mr",Map.of("max_features",2)));var upstream=c.dependencyDirectories().get("harmonize");var file=upstream.resolve("feature-annotation.tsv");String original=Files.readString(file);Files.writeString(file,original.replace("g2\tGENE2\tENSG2\t1\t100\t200\tGRCh38\tlocated\n","")+"g1\tOTHER\tOTHER\t2\t100\t200\tGRCh38\tlocated\n");StudyTestSources.seal(upstream,"validate_harmonize","harmonize","H1");
        var selected=StudyBioinformatics.mrFeatures(c);assertEquals(List.of("g1","g2"),selected.stream().map(StudyBioinformatics.Feature::id).toList());assertEquals(List.of("ambiguous_annotation","missing_annotation"),selected.stream().map(StudyBioinformatics.Feature::status).toList());assertTrue(selected.stream().allMatch(f->f.gene().isEmpty()&&f.chromosome().isEmpty()));
        var summary=StudyMrMultiplicity.finish(c,new ArrayList<>(),Map.of(),new ArrayList<>());assertEquals(4,summary.get("eligible_features"));assertEquals(2,summary.get("family_size"));assertEquals(2,summary.get("outside_explicit_feature_budget"));assertEquals(2,summary.get("unresolved_tests"));
    }
    @Test void automaticSourcesDoNotAccessCandidatesOrProvidersWhenEveryLocationIsUnavailable() throws Exception {
        var c=context(Map.of("mr",Map.of("exposure_ancestry","European")));var upstream=c.dependencyDirectories().get("harmonize");Files.delete(upstream.resolve("feature-annotation.tsv"));StudyTestSources.seal(upstream,"validate_harmonize","harmonize","H1");
        var prepared=StudyMrSources.prepare(c);assertTrue(prepared.pairs().isEmpty());assertEquals(false,prepared.audit().get("source_lookup_attempted"));assertEquals(4,list(prepared.audit().get("features")).size());assertFalse(Files.exists(c.outputDirectory().resolve("automatic-mr-sources")));
        var result=new StudyGeneticEvidence().execute(c);assertEquals(org.jlinalg.study.StudyOperationExecutor.State.WAITING_FOR_INPUT,result.state());assertEquals(4,map(result.details().get("mr_testing")).get("unresolved_tests"));assertTrue(result.details().toString().contains("exact feature"));
    }
    @Test void policyDefaultsAndOverridesAreExplicitAndRejectPostHocEstimatorSelection() {
        assertEquals(Map.of("primary_estimator", "ivw-fixed", "adjustment", "BH", "alpha", .05), StudyMrTestingPolicy.resolve(Map.of()));
        assertEquals("ivw-random", StudyMrTestingPolicy.resolve(Map.of("primary_estimator", "ivw-random")).get("primary_estimator"));
        assertThrows(IllegalArgumentException.class, () -> StudyMrTestingPolicy.resolve(Map.of("primary_estimator", "smallest-p")));
        assertThrows(IllegalArgumentException.class, () -> StudyMrTestingPolicy.resolve(Map.of("alpha", 1)));
    }
}
