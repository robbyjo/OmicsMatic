package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyOperationExecutor.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyGeneticEvidenceTest {
    @TempDir Path project;

    @Test void automaticSourceSummaryPreservesFullHashedAuditWithoutDuplicatingLookupRows() throws Exception {
        Path file=project.resolve("automatic-mr-sources.yaml");
        String original="full per-variant lookup audit\n";
        Files.writeString(file,original);
        var feature=Map.<String,Object>of("feature_id","ENST1.1","status","prepared","pair_id","ENST1-1",
            "strong_candidate_instruments",10000,"outcome",Map.of("variant_lookup",List.of(Map.of("variant_id","rs1"))));
        var summary=StudyGeneticEvidence.automaticSourceSummary(Map.of("generated_pairs",1,"features",List.of(feature)),file);
        var compact=(Map<?,?>)((List<?>)summary.get("features")).get(0);
        assertEquals(10000,compact.get("strong_candidate_instruments"));
        assertFalse(compact.containsKey("outcome"));
        assertEquals(1,summary.get("generated_pairs"));
        assertEquals(FollowupSupport.hash(file),((Map<?,?>)summary.get("complete_audit")).get("sha256"));
        assertEquals(original,Files.readString(file));
    }

    @Test void runsMrHarmonizationAndSensitivityFromReviewedRegionalSources() throws Exception {
        Context context = context("mendelian_randomization", Map.of());
        var result = new StudyGeneticEvidence().execute(context);
        assertEquals(State.COMPLETE, result.state(), result.details().toString());
        assertTrue(Files.readString(context.outputDirectory().resolve("pair/mr-ivw-fixed.tsv")).contains("0.5"));
        assertTrue(Files.isRegularFile(context.outputDirectory().resolve("pair/mr-egger.tsv")));
        assertTrue(Files.isRegularFile(context.outputDirectory().resolve("pair/mr-loo-0-estimate.tsv")));
        assertTrue(Files.readString(context.outputDirectory().resolve("pair/harmonization.tsv")).contains("rs2\t1\t200\tA\tG\t0.2\t0.1\t-1\t1"));
        assertTrue(Files.readString(context.outputDirectory().resolve("pair/exposure-source.tsv.source.yaml")).contains("sha256"));
        assertEquals(1, ((Map<?, ?>) result.details().get("mr_testing")).get("family_size"));
        var primary = DelimitedData.readResult(context.outputDirectory().resolve("mr-primary-results.tsv"));
        assertEquals(1, primary.rows().size()); assertEquals("ivw-fixed", primary.rows().get(0)[primary.column("primary_estimator")]);
        assertEquals(Double.parseDouble(primary.rows().get(0)[primary.column("causal_p_value")]), Double.parseDouble(primary.rows().get(0)[primary.column("adjusted_p")]));
    }

    @Test void reviewedMrPairBeyondTopTenRunsWhileUnpairedEligibleFindingsRemainPending() throws Exception {
        Context context=context("mendelian_randomization",Map.of());
        Path upstream=context.dependencyDirectories().get("harmonize");
        StringBuilder primary=new StringBuilder("feature_id\tp_value\n");
        for(int i=0;i<15;i++)primary.append("other-").append(i).append("\t1e-9\n");
        primary.append("probe1\t1e-8\n");
        Files.writeString(upstream.resolve("standard-results/d1.tsv"),primary);
        String annotation=Files.readString(upstream.resolve("feature-annotation.tsv"));
        for(int i=0;i<15;i++)annotation+="other-"+i+"\tTEST\tENSG1\t1\t100\t300\tGRCh38\tlocated\n";
        Files.writeString(upstream.resolve("feature-annotation.tsv"),annotation);StudyTestSources.seal(upstream,"validate_harmonize","harmonize","h1");
        assertFalse(StudyBioinformatics.features(context).stream().anyMatch(f->f.id().equals("probe1")));
        var result=new StudyGeneticEvidence().execute(context);
        assertEquals(State.WAITING_FOR_INPUT,result.state(),result.details().toString());
        assertTrue(Files.isRegularFile(context.outputDirectory().resolve("pair/mr-ivw-fixed.tsv")));
        assertEquals("all_significant_features",((Map<?,?>)result.details().get("feature_selection")).get("scope"));
        assertEquals(16, ((Map<?, ?>) result.details().get("mr_testing")).get("family_size"));
        assertEquals(15, ((Map<?, ?>) result.details().get("mr_testing")).get("unresolved_tests"));
    }

    @Test void runsFineMappingAndColocalizationWithPriorSensitivity() throws Exception {
        Context context = context("colocalization", Map.of());
        var result = new StudyGeneticEvidence().execute(context);
        assertEquals(State.COMPLETE, result.state(), result.details().toString());
        assertTrue(Files.isRegularFile(context.outputDirectory().resolve("pair/exposure-fine-mapping.tsv")));
        assertTrue(Files.isRegularFile(context.outputDirectory().resolve("pair/colocalization.tsv")));
        assertTrue(Files.isRegularFile(context.outputDirectory().resolve("pair/colocalization-prior-sensitivity.tsv")));
        var canonical=DelimitedData.readResult(context.outputDirectory().resolve("colocalization-results.tsv"));assertFalse(canonical.rows().isEmpty());
        for(String[] row:canonical.rows()){assertEquals("true",row[canonical.column("exposure_converged")]);assertEquals("regional_analysis_with_declared_complete_sources",row[canonical.column("evidence_grade")]);assertEquals("not_classified_no_registered_threshold",row[canonical.column("prior_stability")]);}
    }

    @Test @SuppressWarnings("unchecked")
    void oneOperationRetainsEligibilityChecksForEveryPair() throws Exception {
        Context base=context("mendelian_randomization",Map.of());
        var original=StudyBioinformatics.config(base);
        var pair=(Map<String,Object>)((List<?>)original.get("genetic_pairs")).get(0);
        var second=new LinkedHashMap<>(pair);second.put("id","second");
        var ineligible=new LinkedHashMap<>(pair);ineligible.put("id","ineligible");ineligible.put("feature_id","absent");
        var bio=new LinkedHashMap<>(original);bio.put("genetic_pairs",List.of(pair,second,ineligible));
        var h=new LinkedHashMap<>((Map<String,Object>)((List<?>)base.study().get("hypotheses")).get(0));
        h.put("analysis",Map.of("bioinformatics",bio));
        var study=new LinkedHashMap<>(base.study());study.put("hypotheses",List.of(h));
        Context context=new Context(base.project(),base.outputDirectory(),base.operation(),study,base.plan(),base.dependencyDirectories());
        var result=new StudyGeneticEvidence().execute(context);
        assertEquals(State.WAITING_FOR_INPUT,result.state());
        assertTrue(Files.isRegularFile(context.outputDirectory().resolve("pair/mr-ivw-fixed.tsv")));
        assertTrue(Files.isRegularFile(context.outputDirectory().resolve("second/mr-ivw-fixed.tsv")));
        assertFalse(Files.exists(context.outputDirectory().resolve("ineligible/mr-ivw-fixed.tsv")));
        var rows=(List<Map<String,Object>>)(List<?>)result.details().get("pairs");
        assertEquals(List.of("complete","complete","waiting_for_input"),rows.stream().map(r->r.get("status")).toList());
    }

    @Test void overlapUnapprovedAndWrongFeatureCannotProduceInference() throws Exception {
        for (Map<String, Object> change : List.of(Map.<String, Object>of("sample_overlap", "unknown"),
                Map.<String, Object>of("approved", false), Map.<String, Object>of("feature_id", "wrong-feature"))) {
            Context context = context("mendelian_randomization", change);
            var result = new StudyGeneticEvidence().execute(context);
            assertEquals(State.WAITING_FOR_INPUT, result.state());
            assertFalse(Files.exists(context.outputDirectory().resolve("pair/mr-ivw-fixed.tsv")));
            assertFalse(((List<?>) result.details().get("needs")).isEmpty());
        }
    }

    @Test void mismatchedBuildAndIndefiniteLdStopBeforeInference() throws Exception {
        Context context = context("mendelian_randomization", Map.of("outcome", source("outcome", "GRCh37")));
        var result = new StudyGeneticEvidence().execute(context);
        assertEquals(State.WAITING_FOR_INPUT, result.state());
        context = context("mendelian_randomization", Map.of());
        Files.writeString(project.resolve("ld.tsv"), "variant_id\trs1\trs2\trs3\nrs1\t1\t.9\t.9\nrs2\t.9\t1\t-.9\nrs3\t.9\t-.9\t1\n");
        result = new StudyGeneticEvidence().execute(context);
        assertEquals(State.WAITING_FOR_INPUT, result.state());
        assertTrue(result.details().toString().contains("not positive semidefinite"));
    }

    @Test void genotypeDerivedMrLdCanSkipOnlyTheCubicPsdRecheck() throws Exception {
        Path matrix = project.resolve("indefinite-ld.tsv");
        Files.writeString(matrix, "variant_id\trs1\trs2\trs3\nrs1\t1\t.9\t.9\nrs2\t.9\t1\t-.9\nrs3\t.9\t-.9\t1\n");
        List<StudyGeneticEvidence.Aligned> aligned = List.of(
            aligned("rs1", 100), aligned("rs2", 200), aligned("rs3", 300));
        assertDoesNotThrow(() -> StudyGeneticEvidence.matrix(matrix, aligned, false));
        var failure = assertThrows(java.io.IOException.class,
            () -> StudyGeneticEvidence.matrix(matrix, aligned, true));
        assertTrue(failure.getMessage().contains("not positive semidefinite"));
    }

    @Test void perfectLdRoundoffIsCanonicalizedButRealInvalidLdIsRejected() throws Exception {
        Path matrix=project.resolve("perfect-ld.tsv");
        var selected=List.of(aligned("rs1",100),aligned("rs2",200));
        Files.writeString(matrix,"variant_id\trs1\trs2\nrs1\t1\t1.0000000000000002\nrs2\t1.0000000000000002\t1\n");
        assertEquals(1,StudyGeneticEvidence.matrix(matrix,selected,false)[0][1]);
        assertEquals(1,StudyGeneticEvidence.matrix(matrix,selected,true)[0][1]);
        Files.writeString(matrix,"variant_id\trs1\trs2\nrs1\t1\t-1.0000000000000002\nrs2\t-1.0000000000000002\t1\n");
        assertEquals(-1,StudyGeneticEvidence.matrix(matrix,selected,false)[0][1]);
        Files.writeString(matrix,"variant_id\trs1\trs2\nrs1\t1\t1.000001\nrs2\t1.000001\t1\n");
        assertThrows(java.io.IOException.class,()->StudyGeneticEvidence.matrix(matrix,selected,false));
    }

    @Test void streamedLdReordersRowsAndColumnsAndOrientsTheSelectedSubmatrix()
            throws Exception {
        Path matrix = project.resolve("reordered.csv");
        Files.writeString(matrix, "variant_id,\"rs2\",rs3,rs1\n"
            + "rs3,.2,1,.3\nrs1,.1,.3,1\nrs2,1,.2,.1\n");
        var rs3 = aligned("rs3", 300);
        var flipped = new StudyGeneticEvidence.Aligned(rs3.exposure(),
            rs3.outcome(), 1, -1);
        double[][] actual = StudyGeneticEvidence.matrix(matrix,
            List.of(flipped, aligned("rs1", 100)), true);
        assertArrayEquals(new double[] {1, -.3}, actual[0], 1e-15);
        assertArrayEquals(new double[] {-.3, 1}, actual[1], 1e-15);
    }

    @Test void streamedLdStillRejectsMalformedOrMismatchedRows() throws Exception {
        Path matrix = project.resolve("malformed.tsv");
        for (String rows : List.of("rs1\t1\t0\nrs1\t0\t1\n",
                "rs1\t1\t0\n", "rs1\t1\t0\nrsX\t0\t1\n",
                "rs1\t1\nrs2\t0\t1\n")) {
            Files.writeString(matrix, "variant_id\trs1\trs2\n" + rows);
            assertThrows(java.io.IOException.class, () -> StudyGeneticEvidence.matrix(
                matrix, List.of(aligned("rs1", 100)), false));
        }
    }

    @Test void denseClumpingLdCanExceedFineMappingBudgetWithoutKeepingStrings()
            throws Exception {
        int n = 5001;
        Path matrix = project.resolve("dense-clumping.tsv");
        try (var writer = Files.newBufferedWriter(matrix)) {
            writer.write("variant_id");
            for (int i = 1; i <= n; i++) writer.write("\trs" + i);
            writer.newLine();
            String zeros = "\t0".repeat(n);
            for (int i = 1; i <= n; i++) {
                StringBuilder row = new StringBuilder("rs" + i).append(zeros);
                row.setCharAt(("rs" + i).length() + 2 * i - 1, '1');
                writer.append(row).append('\n');
            }
        }
        double[][] actual = StudyGeneticEvidence.matrix(matrix,
            List.of(aligned("rs5001", 5001)), false);
        assertEquals(1, actual[0][0]);
        var failure = assertThrows(java.io.IOException.class,
            () -> StudyGeneticEvidence.matrix(matrix,
                List.of(aligned("rs5001", 5001)), true));
        assertTrue(failure.getMessage().contains("at most 5000"));
    }

    @Test void palindromeAndWeakInstrumentsAreNotSilentlyUsed() throws Exception {
        Context context = context("mendelian_randomization", Map.of());
        Files.writeString(project.resolve("exposure.tsv"), Files.readString(project.resolve("exposure.tsv")).replace("rs2\t1\t200\tA\tG", "rs2\t1\t200\tA\tT"));
        var result = new StudyGeneticEvidence().execute(context);
        assertEquals(State.COMPLETE, result.state(), result.details().toString());
        assertTrue(result.details().toString().contains("palindromic"));
        assertFalse(Files.readString(context.outputDirectory().resolve("pair/mr-instruments.tsv")).contains("rs2"));
        context = context("mendelian_randomization", Map.of());
        Files.writeString(project.resolve("exposure.tsv"), Files.readString(project.resolve("exposure.tsv")).replace("1e-12", ".1"));
        result = new StudyGeneticEvidence().execute(context);
        assertEquals(State.COMPLETE, result.state());
        assertTrue(result.details().toString().contains("not_estimable"));
        assertTrue(result.details().toString().contains("No independent strong instrument"));
        assertTrue(((List<?>) result.details().get("needs")).isEmpty());
    }

    @Test void oneIndependentStrongInstrumentProducesLimitedWaldRatio() throws Exception {
        Context context = context("mendelian_randomization", Map.of());
        Files.writeString(project.resolve("ld.tsv"), "variant_id\trs1\trs2\trs3\nrs1\t1\t.9\t.9\nrs2\t.9\t1\t.9\nrs3\t.9\t.9\t1\n");
        var result = new StudyGeneticEvidence().execute(context);
        assertEquals(State.COMPLETE, result.state(), result.details().toString());
        Path output = context.outputDirectory().resolve("pair/mr-wald-ratio.tsv");
        assertTrue(Files.isRegularFile(output));
        assertTrue(Files.readString(output).contains("WALD_RATIO\t0.5"));
        assertFalse(Files.exists(context.outputDirectory().resolve("pair/mr-ivw-fixed.tsv")));
        assertTrue(result.details().toString().contains("Only one independent strong instrument remained"));
        assertTrue(result.details().toString().contains("not estimable"));
    }

    @Test void oneAlignedVariantCanProduceWaldRatio() throws Exception {
        Context context = context("mendelian_randomization", Map.of());
        Files.writeString(project.resolve("outcome.tsv"),
            "variant_id\tchromosome\tposition\teffect_allele\tother_allele\tbeta\tse\tp_value\n"
                + "rs1\t1\t100\tA\tG\t0.05\t0.01\t1e-8\n");
        var result = new StudyGeneticEvidence().execute(context);
        assertEquals(State.COMPLETE, result.state(), result.details().toString());
        assertTrue(Files.isRegularFile(context.outputDirectory().resolve("pair/mr-wald-ratio.tsv")));
        assertTrue(result.details().toString().contains("aligned_variants=1"));
    }

    @Test void partialColocalizationFiltersLargeOutcomeToExposureCandidates() throws Exception {
        Map<String, Object> exposure = source("exposure", "GRCh38");
        exposure.put("complete_region", false); exposure.put("export_p_threshold", 1e-7);
        StringBuilder outcome = new StringBuilder("variant_id\tchromosome\tposition\teffect_allele\tother_allele\tbeta\tse\tp_value\n");
        outcome.append("rs1\t1\t100\tA\tG\t0.05\t0.01\t1e-8\nrs2\t1\t200\tG\tA\t-0.1\t0.01\t1e-8\nrs3\t1\t300\tA\tG\t0.15\t0.01\t1e-8\n");
        for (int i = 0; i < 5001; i++) outcome.append("extra").append(i)
            .append("\t1\t400\tA\tG\t0.001\t0.01\t0.5\n");
        Context context = context("colocalization", Map.of("exposure", exposure,
            "partial_colocalization", true, "minimum_exposure_overlap", 0.5));
        Files.writeString(project.resolve("outcome.tsv"), outcome);
        var result = new StudyGeneticEvidence().execute(context);
        assertEquals(State.COMPLETE, result.state(), result.details().toString());
        assertFalse(result.details().toString().contains("5000-variant"));
        var pair=StudyPublicEvidence.map(StudyPublicEvidence.list(result.details().get("pairs")).get(0));
        assertEquals(5004,pair.get("outcome_variants"));assertEquals(3,pair.get("outcome_retained_for_alignment"));
        assertEquals(3.0/5004,((Number)pair.get("outcome_variant_match_fraction")).doubleValue(),1e-15);
        assertEquals(1.0,((Number)pair.get("outcome_candidate_match_fraction")).doubleValue());
        assertEquals(5005,Files.readAllLines(context.outputDirectory().resolve("pair/outcome-regional-variants.tsv")).size());
    }

    @Test void convergedFineMappingWithoutCredibleSignalIsCompletedNotEstimable() throws Exception {
        Context context = context("colocalization", Map.of());
        Files.writeString(project.resolve("outcome.tsv"),
            "variant_id\tchromosome\tposition\teffect_allele\tother_allele\tbeta\tse\tp_value\n"
                + "rs1\t1\t100\tA\tG\t1e-8\t1\t0.99\n"
                + "rs2\t1\t200\tG\tA\t-1e-8\t1\t0.99\n"
                + "rs3\t1\t300\tA\tG\t1e-8\t1\t0.99\n");
        var result = new StudyGeneticEvidence().execute(context);
        assertEquals(State.COMPLETE, result.state(), result.details().toString());
        assertTrue(result.details().toString().contains("not_estimable"));
        assertTrue(result.details().toString().contains("no credible signal"));
        assertTrue(((List<?>) result.details().get("needs")).isEmpty());
    }

    @Test void exhaustedPartialRegionSourcesAreCompletedNotEstimable() throws Exception {
        Map<String, Object> exposure = source("exposure", "GRCh38");
        exposure.put("complete_region", false); exposure.put("export_p_threshold", 1e-7);
        Context context = context("colocalization", Map.of("exposure", exposure,
            "partial_colocalization", true, "minimum_exposure_overlap", 0.5));
        Files.writeString(project.resolve("outcome.tsv"),
            "variant_id\tchromosome\tposition\teffect_allele\tother_allele\tbeta\tse\tp_value\n"
                + "rs1\t1\t100\tA\tG\t0.05\t0.01\t1e-8\n");
        var result = new StudyGeneticEvidence().execute(context);
        assertEquals(State.COMPLETE, result.state(), result.details().toString());
        assertTrue(result.details().toString().contains("not_estimable"));
        assertTrue(result.details().toString().contains("fewer than two variants could be aligned"));
        assertTrue(((List<?>) result.details().get("needs")).isEmpty());
    }

    @Test void studyFineMappingRecordsExtendedIterationCeiling() throws Exception {
        Context context = context("colocalization", Map.of());
        var result = new StudyGeneticEvidence().execute(context);
        assertEquals(State.COMPLETE, result.state(), result.details().toString());
        assertTrue(Files.readString(context.outputDirectory().resolve("pair/exposure-command.log"))
            .contains("--max-iterations, 2000"));
        assertTrue(result.details().toString().contains("maximum_iterations_per_attempt=2000"));
        assertTrue(result.details().toString().contains("requested_maximum_effects=3"));
        assertTrue(result.details().toString().contains("selected_effects=3"));
        assertTrue(result.details().toString().contains("exploratory_largest_convergent_effect_count_fallback=false"));
    }

    @Test void automaticLdNeverGuessesAncestryAndDoesNotLiftBuild() throws Exception {
        var result = new StudyGeneticEvidence().execute(context("fine_mapping", Map.of("ld", Map.of())));
        assertEquals(State.WAITING_FOR_INPUT, result.state());
        assertTrue(result.details().toString().contains("ancestry is not guessed"));
        result = new StudyGeneticEvidence().execute(context("fine_mapping", Map.of("ld", Map.of("population", "EUR"))));
        assertEquals(State.WAITING_FOR_INPUT, result.state());
        assertTrue(result.details().toString().contains("no implicit liftover"));
    }

    @Test void filteredExposureCanSupportReviewedMrButNotColocalization() throws Exception {
        Map<String, Object> exposure = source("exposure", "GRCh38");
        exposure.put("complete_region", false); exposure.put("export_p_threshold", 1e-7);
        var mr = new StudyGeneticEvidence().execute(context("mendelian_randomization", Map.of("exposure", exposure)));
        assertEquals(State.COMPLETE, mr.state(), mr.details().toString());
        var coloc = new StudyGeneticEvidence().execute(context("colocalization", Map.of("exposure", exposure)));
        assertEquals(State.WAITING_FOR_INPUT, coloc.state());
        assertTrue(coloc.details().toString().contains("full regional statistics"));
        var partial = new StudyGeneticEvidence().execute(context("colocalization", Map.of(
            "exposure", exposure, "partial_colocalization", true, "minimum_exposure_overlap", 0.5)));
        assertEquals(State.COMPLETE, partial.state(), partial.details().toString());
        assertTrue(partial.details().toString().contains("exploratory_partial_region_major_limitation"));
        assertTrue(partial.details().toString().contains("absence of colocalization is not evidence of absence"));
        assertTrue(partial.details().toString().contains("exploratory_largest_convergent_effect_count_fallback=true"));
        exposure.put("export_p_threshold", 1e-10);
        mr = new StudyGeneticEvidence().execute(context("mendelian_randomization", Map.of("exposure", exposure)));
        assertEquals(State.WAITING_FOR_INPUT, mr.state());
    }

    @Test void mrReadsIrqtlLogProbabilityAndCompositeRsidWithoutUnderflow() throws Exception {
        Context context = context("mendelian_randomization", Map.of());
        String exposure = Files.readString(project.resolve("exposure.tsv"))
            .replace("p_value", "log10_p")
            .replace("rs1\t1\t100\tA\tG\t0.1\t0.01\t1e-12", "chr1:100:G:A:rs1\t1\t100\tA\tG\t0.1\t0.01\t-12")
            .replace("rs2\t1\t200\tA\tG\t0.2\t0.01\t1e-12", "chr1:200:G:A:rs2\t1\t200\tA\tG\t0.2\t0.01\t-12")
            .replace("rs3\t1\t300\tA\tG\t0.3\t0.01\t1e-12", "chr1:300:G:A:rs3\t1\t300\tA\tG\t0.3\t0.01\t-400");
        Files.writeString(project.resolve("exposure.tsv"), exposure);
        var result = new StudyGeneticEvidence().execute(context);
        assertEquals(State.COMPLETE, result.state(), result.details().toString());
        String instruments = Files.readString(context.outputDirectory().resolve("pair/mr-instruments.tsv"));
        assertTrue(instruments.contains("rs1"));
        assertFalse(instruments.contains("chr1:100"));
    }

    private Context withAnalysis(Context base,Map<String,Object> extra) {
        var h=new LinkedHashMap<>(StudyPublicEvidence.map(StudyPublicEvidence.list(base.study().get("hypotheses")).get(0)));var a=new LinkedHashMap<>(StudyPublicEvidence.map(h.get("analysis")));a.putAll(extra);h.put("analysis",a);var study=new LinkedHashMap<>(base.study());study.put("hypotheses",List.of(h));return new Context(base.project(),base.outputDirectory(),base.operation(),study,base.plan(),base.dependencyDirectories());
    }
    @Test void anExplicitPartialRequestCannotBePromotedByCompleteSourceDeclarations()throws Exception {
        var c=context("colocalization",Map.of("partial_colocalization",true));var result=new StudyGeneticEvidence().execute(c);assertEquals(State.COMPLETE,result.state(),result.details().toString());var table=DelimitedData.readResult(c.outputDirectory().resolve("colocalization-results.tsv"));assertFalse(table.rows().isEmpty());assertTrue(table.rows().stream().allMatch(row->row[table.column("evidence_grade")].equals("exploratory_partial_region_major_limitation")&&row[table.column("required_manuscript_caveat")].contains("observed, selected variants")));
    }
    @Test void nonconvergenceRetainsItsAttemptAndCoverageWithoutAColocalizationPosterior()throws Exception {
        var c=withAnalysis(context("colocalization",Map.of()),Map.of("colocalization",Map.of("maximum_iterations",1)));var result=new StudyGeneticEvidence().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());var pair=StudyPublicEvidence.map(StudyPublicEvidence.list(result.details().get("pairs")).get(0));var fine=StudyPublicEvidence.map(StudyPublicEvidence.map(pair.get("effective_parameters")).get("exposure_fine_mapping"));assertEquals(false,fine.get("converged"));assertEquals(1,StudyPublicEvidence.list(fine.get("attempts")).size());assertTrue(Files.isRegularFile(c.outputDirectory().resolve("pair/regional-coverage.yaml")));assertTrue(DelimitedData.readResult(c.outputDirectory().resolve("colocalization-results.tsv")).rows().isEmpty());
    }
    @Test void reviewedColocalizationPairBeyondTheContextualCapExecutesWithUnrequestedDispositions()throws Exception {
        var c=context("colocalization",Map.of());Path upstream=c.dependencyDirectories().get("harmonize");var table=new StringBuilder("feature_id\tp_value\n");String annotation=Files.readString(upstream.resolve("feature-annotation.tsv"));for(int i=0;i<15;i++){table.append("other-").append(i).append("\t1e-10\n");annotation+="other-"+i+"\tTEST\tENSG1\t1\t100\t300\tGRCh38\tlocated\n";}table.append("probe1\t1e-9\n");Files.writeString(upstream.resolve("standard-results/d1.tsv"),table);Files.writeString(upstream.resolve("feature-annotation.tsv"),annotation);StudyTestSources.seal(upstream,"validate_harmonize","harmonize","h1");assertFalse(StudyBioinformatics.features(c).stream().anyMatch(f->f.id().equals("probe1")));assertEquals(16,StudyBioinformatics.colocalizationFeatures(c).size());var result=new StudyGeneticEvidence().execute(c);assertEquals(State.COMPLETE,result.state(),result.details().toString());assertEquals(15L,StudyPublicEvidence.map(result.details().get("colocalization_review")).get("unrequested_features"));assertEquals(16,DelimitedData.readResult(c.outputDirectory().resolve("colocalization-feature-dispositions.tsv")).rows().size());
    }

    private Map<String, Object> source(String role, String build) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("dataset_id", "d1"); source.put("file_role", role); source.put("source_id", "synthetic-" + role);
        source.put("citation", "Synthetic test fixture, not biological evidence"); source.put("genome_build", build);
        source.put("ancestry", "European"); source.put("sample_size", 10000); source.put("complete_region", true);
        source.put("trait", role.equals("outcome") ? "BMI" : "splicing"); source.put("tissue", "blood"); source.put("assay", "sQTL");
        source.put("feature_id", "probe1");
        return source;
    }

    private static StudyGeneticEvidence.Aligned aligned(String id, long position) {
        var variant = new StudyGeneticEvidence.Variant(id, "1", position,
            "A", "G", 0.1, 0.01, 1e-12);
        return new StudyGeneticEvidence.Aligned(variant, variant, 1, 1);
    }
    private Map<String,Object> reviewedLocation() {
        return Map.of("feature_id","probe1","genome_build","GRCh38","chromosome","1","start",100,"end",300,"coordinate_system","1-based-closed","source_id","synthetic-locus-v1","citation","Synthetic source; no biological claim","rationale","Exact simulated feature interval");
    }
    @Test void reviewedExactFeatureLocationEnablesMrWithoutGuessingMissingOrAmbiguousGenes() throws Exception {
        for(String status:List.of("missing_annotation","ambiguous","conflicting_rows")) {
            var c=context("mendelian_randomization",Map.of("feature_location",reviewedLocation()));var upstream=c.dependencyDirectories().get("harmonize");var annotation=upstream.resolve("feature-annotation.tsv");
            if(status.equals("missing_annotation"))Files.delete(annotation);else if(status.equals("ambiguous"))Files.writeString(annotation,Files.readString(annotation).replace("located","ambiguous"));else Files.writeString(annotation,Files.readString(annotation)+"probe1\tOTHER\tENSG2\t2\t100\t300\tGRCh38\tlocated\n");
            StudyTestSources.seal(upstream,"validate_harmonize","harmonize","h1");var result=new StudyGeneticEvidence().execute(c);assertEquals(State.COMPLETE,result.state(),result.details().toString());var pair=StudyPublicEvidence.map(StudyPublicEvidence.list(result.details().get("pairs")).get(0));var location=StudyPublicEvidence.map(pair.get("measured_feature_location"));assertEquals("reviewed_pair_feature_location",location.get("origin"));assertEquals(false,location.get("gene_annotation_inferred"));assertEquals("",StudyPublicEvidence.map(pair.get("primary_feature_selection")).get("gene_id"));
            var table=DelimitedData.readResult(c.outputDirectory().resolve("mr-primary-results.tsv"));assertEquals(.5,Double.parseDouble(table.rows().get(0)[table.column("estimate")]),1e-12);assertEquals(1,table.rows().size());assertTrue(result.details().toString().contains("synthetic-locus-v1"));
        }
    }
    @Test void anUnlocatedEligibleFeatureWithoutAReviewedLocationRemainsPendingRatherThanAbsent() throws Exception {
        var c=context("mendelian_randomization",Map.of());var upstream=c.dependencyDirectories().get("harmonize");Files.delete(upstream.resolve("feature-annotation.tsv"));StudyTestSources.seal(upstream,"validate_harmonize","harmonize","h1");var result=new StudyGeneticEvidence().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());assertEquals(1,StudyPublicEvidence.map(result.details().get("mr_testing")).get("family_size"));assertEquals(1,StudyPublicEvidence.map(result.details().get("mr_testing")).get("unresolved_tests"));assertFalse(Files.exists(c.outputDirectory().resolve("pair/exposure-source.tsv")));
    }
    @Test void conflictingIdentityBuildApprovalAndIntervalCannotBeBypassedWithAReviewedLocation() throws Exception {
        for(String bad:List.of("feature_id","genome_build","start","region","approved")) {
            var location=new LinkedHashMap<>(reviewedLocation());var extra=new LinkedHashMap<String,Object>();if(bad.equals("feature_id"))location.put(bad,"other");if(bad.equals("genome_build"))location.put(bad,"GRCh37");if(bad.equals("start"))location.put(bad,101);if(bad.equals("region"))extra.put("region",Map.of("chromosome","1","start",101,"end",500));if(bad.equals("approved"))extra.put("approved",false);extra.put("feature_location",location);
            var c=context("mendelian_randomization",extra);var result=new StudyGeneticEvidence().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state(),bad);assertFalse(Files.exists(c.outputDirectory().resolve("pair/exposure-source.tsv")),bad);assertEquals(1,StudyPublicEvidence.map(result.details().get("mr_testing")).get("unresolved_tests"));
        }
    }
    private Context context(String operation, Map<String, Object> changes) throws Exception {
        String header = "variant_id\tchromosome\tposition\teffect_allele\tother_allele\tbeta\tse\tp_value\n";
        Files.writeString(project.resolve("exposure.tsv"), header + "rs1\t1\t100\tA\tG\t0.1\t0.01\t1e-12\nrs2\t1\t200\tA\tG\t0.2\t0.01\t1e-12\nrs3\t1\t300\tA\tG\t0.3\t0.01\t1e-12\n");
        Files.writeString(project.resolve("outcome.tsv"), header + "rs1\t1\t100\tA\tG\t0.05\t0.01\t1e-8\nrs2\t1\t200\tG\tA\t-0.1\t0.01\t1e-8\nrs3\t1\t300\tA\tG\t0.15\t0.01\t1e-8\n");
        Files.writeString(project.resolve("ld.tsv"), "variant_id\trs1\trs2\trs3\nrs1\t1\t0\t0\nrs2\t0\t1\t0\nrs3\t0\t0\t1\n");
        Files.writeString(project.resolve("alleles.tsv"), "variant_id\teffect_allele\tother_allele\nrs1\tA\tG\nrs2\tA\tG\nrs3\tA\tG\n");
        Map<String, Object> ld = source("ld", "GRCh38"); ld.put("alleles_file_role", "alleles");
        Map<String, Object> pair = new LinkedHashMap<>();
        pair.put("id", "pair"); pair.put("feature_id", "probe1"); pair.put("approved", true); pair.put("rationale", "Synthetic test");
        pair.put("sample_overlap", "none"); pair.put("sample_overlap_basis", "Separate simulated samples"); pair.put("instrument_validity_rationale", "Simulation only");
        pair.put("region", Map.of("chromosome", "1", "start", 1, "end", 500));
        pair.put("exposure", source("exposure", "GRCh38")); pair.put("outcome", source("outcome", "GRCh38")); pair.put("ld", ld); pair.putAll(changes);
        Map<String, Object> study = Map.of("resources", Map.of("directory","fixture-resources","network", "offline","downloads","deny"),
            "datasets", List.of(Map.of("id", "d1", "files", Map.of("exposure", "exposure.tsv", "outcome", "outcome.tsv", "ld", "ld.tsv", "alleles", "alleles.tsv"))),
            "hypotheses", List.of(Map.of("id", "h1", "outcome", "BMI", "analysis", Map.of("bioinformatics", Map.of("genetic_pairs", List.of(pair))))));
        Path upstream = Files.createTempDirectory(project, "upstream-");
        Files.createDirectories(upstream.resolve("standard-results"));
        Files.writeString(upstream.resolve("manifest.yaml"), "operation_type: validate_harmonize\nstate: complete\n");
        Files.writeString(upstream.resolve("standard-results/d1.tsv"), "feature_id\tp_value\nprobe1\t1e-9\n");
        Files.writeString(upstream.resolve("feature-annotation.tsv"), "feature_id\tgene_symbol\tgene_id\tchromosome\tstart\tend\tgenome_build\tmapping_status\nprobe1\tTEST\tENSG1\t1\t100\t300\tGRCh38\tlocated\n");
        StudyTestSources.seal(upstream,"validate_harmonize","harmonize","h1");
        return new Context(project, Files.createTempDirectory(project, "output-"), Map.of("hypothesis", "h1", "type", operation, "datasets", List.of("d1")), study, Map.of(), Map.of("harmonize", upstream));
    }
}
