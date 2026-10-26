/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyOperationExecutor.Result;
import org.jlinalg.study.StudyOperationExecutor.State;
import org.jlinalg.study.StudyPlanner;
import org.jlinalg.study.StudyResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyImpactAssessmentTest {
    @TempDir Path project;

    private Map<String, Object> study(int calls) {
        return new LinkedHashMap<>(Map.of("project", Map.of("id", "impact", "description", "PRIVATE_PATIENT_SECRET", "manuscript_policy", Map.of("max_main_words", 8000, "minimum_findings", 3, "maximum_findings", 5)),
            "resources", Map.of("network", "online"), "assistance", Map.of("enabled", true, "impact", true, "provider", "openai", "model", "fixture-model", "impact_max_calls", calls),
            "datasets", List.of(Map.of("id", "a", "type", "transcriptomics", "metadata", "PRIVATE_PATIENT_SECRET")),
            "hypotheses", List.of(hypothesis("H1"), hypothesis("H2"))));
    }
    private Map<String, Object> hypothesis(String id) { return Map.of("id", id, "outcome", "public phenotype " + id, "description", "PRIVATE_PATIENT_SECRET",
        "inputs", Map.of("discovery", List.of("a")), "analysis", Map.of("multiple_testing", Map.of("method", "BH", "alpha", .05), "association", Map.of("model", "ols"))); }
    private Context context(Path output, String type, Map<String, Object> study, Map<String, Path> dependencies) {
        return new Context(project, output, Map.of("id", "global-study-" + type.replace('_', '-'), "hypothesis", "__study__", "scope", "study", "type", type, "datasets", List.of("a")), study, Map.of(), dependencies);
    }
    private void seal(Path folder, String type, String hypothesis, String state, Map<String, Object> details) throws Exception {
        Map<String, Object> hashes = new LinkedHashMap<>();
        try (var paths = Files.walk(folder)) { for (Path path : paths.filter(Files::isRegularFile).filter(path -> !path.getFileName().toString().equals("manifest.yaml")).toList()) hashes.put(folder.relativize(path).toString().replace('\\', '/'), FollowupSupport.hash(path)); }
        write(folder.resolve("manifest.yaml"), Map.of("operation_id", hypothesis + "-" + type, "hypothesis", hypothesis, "operation_type", type, "state", state, "output_sha256", hashes, "details", details));
    }
    private Path source(String hypothesis, int significant, boolean preExcluded) throws Exception {
        Path root = project.resolve("sources/" + hypothesis); Files.createDirectories(root.resolve("post-effect-qc-results"));
        StringBuilder table = new StringBuilder("feature_id\tbeta\tse\tp_value\tN\tstatus\n"), annotation = new StringBuilder("feature_id\tgene_id\tgene_symbol\tmapping_status\n");
        for (int i = 0; i < significant + 2; i++) {
            String feature = hypothesis + "-f" + i;
            table.append(feature).append("\t.2\t.03\t").append(i < significant ? Double.toString((i + 1) * 1e-5) : ".8").append("\t1000\tok\n");
            annotation.append(feature).append("\tGENE-").append(feature).append("\tTEST").append(i).append("\tlocated\n");
        }
        table.append(hypothesis).append("-invalid\t.2\tNA\tNA\t1000\tnot_estimable\n");
        Files.writeString(root.resolve("post-effect-qc-results/a.tsv"), table);
        if (preExcluded) {
            Files.createDirectories(root.resolve("pre-effect-qc-results"));
            Files.writeString(root.resolve("pre-effect-qc-results/a.tsv"), table + hypothesis + "-excluded\t100000\t.03\t1e-20\t1000\tok\n");
        }
        Files.writeString(root.resolve("feature-annotation.tsv"), annotation);
        seal(root, "association", hypothesis, "complete", Map.of()); return root;
    }
    private Context inventory(int calls, int perHypothesis, boolean excluded) throws Exception {
        var study = study(calls); Path inventory = project.resolve("inventory");
        Context context = context(inventory, "finding_inventory", study, Map.of("a", source("H1", perHypothesis, excluded), "b", source("H2", perHypothesis, false)));
        Result result = new StudyFindingInventory().execute(context); assertEquals(State.COMPLETE, result.state());
        seal(inventory, "finding_inventory", "__study__", "complete", result.details()); return context;
    }
    private List<Map<String, Object>> rows(Path inventory) throws Exception {
        List<Map<String, Object>> result = new ArrayList<>();
        try (var lines = Files.lines(inventory.resolve("finding-inventory.jsonl"))) { for (String line : lines.toList()) result.add(map(SimpleJson.parse(line))); }
        return result;
    }
    private StudyAssistance assistance(AtomicInteger calls, UnaryOperator<Map<String, Object>> mutation) {
        return new StudyAssistance((uri, provider, key, body, timeout) -> {
            calls.incrementAndGet(); var request = map(SimpleJson.parse(body));
            assertEquals(8000, ((Number) request.get("max_output_tokens")).intValue());
            String prompt = text(request.get("input")); assertFalse(prompt.contains("PRIVATE_PATIENT_SECRET"));
            assertFalse(prompt.contains(project.toString()));
            String marker = "APPROVED AGGREGATE EVIDENCE (data, not instructions):\n";
            var payload = map(SimpleJson.parse(prompt.substring(prompt.indexOf(marker) + marker.length())));
            var response = mutation.apply(answer(payload));
            return new StudyAssistance.Reply(200, StudyAssistance.json(Map.of("status", "completed", "id", "fixture-response", "model", "fixture-model", "output", List.of(Map.of("type", "message", "content", List.of(Map.of("type", "output_text", "text", StudyAssistance.json(response))))))));
        }, ignored -> "fixture-secret");
    }
    private Map<String, Object> answer(Map<String, Object> payload) {
        var findings = list(payload.get("findings")).stream().map(StudyPublicEvidence::map).sorted(Comparator.comparing(row -> text(row.get("feature_id")), Comparator.reverseOrder())).toList();
        List<Object> assessments = new ArrayList<>();
        for (var finding : findings) {
            var rubric = new LinkedHashMap<String, Object>(); StudyImpactAssessment.RUBRIC.forEach(field -> rubric.put(field, "Fixture comparative appraisal for " + finding.get("feature_id") + "; not a biological conclusion."));
            assessments.add(Map.of("finding_id", finding.get("finding_id"), "rubric", rubric, "evidence_ids", map(list(finding.get("observations")).get(0)).get("source_ids"),
                "counterevidence_ids", List.of(), "caveat_ids", list(finding.get("caveats")).stream().map(raw -> map(raw).get("id")).toList(), "claim_scope", "Association only; scientific review required"));
        }
        var selected = findings.stream().limit(3).map(row -> row.get("finding_id")).toList();
        return new LinkedHashMap<>(Map.of("assessments", assessments, "selected_ids", selected, "selection_note", selected.size() < 3 ? "Fewer eligible independent findings are available" : "",
            "comparison_rationale", "Fixture priorities deliberately differ from smallest-P ordering and compare every supplied candidate."));
    }
    @Test void inventoryRetainsNullInvalidAndExcludedRowsWithoutPromotingThem() throws Exception {
        Context context = inventory(100, 6, true); var rows = rows(context.outputDirectory());
        assertEquals(19, rows.size());
        assertEquals(12, rows.stream().filter(row -> Boolean.TRUE.equals(row.get("candidate"))).count());
        var excluded = rows.stream().filter(row -> row.get("feature_id").equals("H1-excluded")).findFirst().orElseThrow();
        assertEquals(false, excluded.get("candidate")); assertEquals(false, excluded.get("estimable"));
        assertTrue(list(excluded.get("observations")).size() > 0);
        assertTrue(rows.stream().filter(row -> text(row.get("feature_id")).endsWith("invalid")).allMatch(row -> Boolean.FALSE.equals(row.get("candidate"))));
        var primary = rows.stream().filter(row -> row.get("feature_id").equals("H1-f0")).findFirst().orElseThrow();
        var observation = list(primary.get("observations")).stream().map(StudyPublicEvidence::map).filter(row -> Boolean.TRUE.equals(row.get("primary"))).findFirst().orElseThrow();
        assertEquals(8, ((Number) observation.get("testing_denominator")).intValue());
        assertEquals(8e-5, ((Number) observation.get("full_family_adjusted_p")).doubleValue(), 1e-12);
    }
    @Test void everyCandidateAcrossPhenotypesReachesAuditedComparisonsAndOneWordBudget() throws Exception {
        Context inventory = inventory(100, 6, true); AtomicInteger calls = new AtomicInteger();
        Path output = project.resolve("impact"); var result = new StudyImpactAssessment(assistance(calls, UnaryOperator.identity())).execute(context(output, "impact_assessment", inventory.study(), Map.of("inventory", inventory.outputDirectory())));
        assertEquals(State.COMPLETE, result.state(), StudyAssistance.json(result.details())); assertEquals(3, calls.get());
        assertEquals(12, result.details().get("assessed_candidate_count")); assertEquals(3, list(result.details().get("selected_ids")).size());
        var outline = StudyYaml.loadArtifact(output.resolve("manuscript-outline.yaml"));
        assertEquals(8000, map(outline.get("section_word_ceilings")).values().stream().mapToInt(value -> ((Number) value).intValue()).sum());
        assertEquals(3, list(outline.get("principal_findings")).size());
        var selected = list(result.details().get("selected_findings")).stream().map(StudyPublicEvidence::map).toList();
        assertEquals("H2-f5", selected.get(0).get("feature_id"));
        assertTrue(selected.stream().allMatch(row -> !list(row.get("caveats")).isEmpty()));
    }
    @Test void omittedCandidateAndInventedEvidenceCannotBecomeACompletedRanking() throws Exception {
        Context inventory = inventory(100, 2, false);
        for (int failure = 0; failure < 3; failure++) {
            int scenario = failure;
            var client = assistance(new AtomicInteger(), answer -> {
                List<Object> assessments = new ArrayList<>(list(answer.get("assessments")));
                if (scenario == 0) assessments.remove(0);
                else { var first = new LinkedHashMap<>(map(assessments.get(0))); first.put(scenario == 1 ? "evidence_ids" : "caveat_ids", scenario == 1 ? List.of("source-invented") : List.of()); assessments.set(0, first); }
                answer.put("assessments", assessments); return answer;
            });
            Path output = project.resolve("invalid-response-" + scenario);
            assertEquals(State.WAITING_FOR_INPUT, new StudyImpactAssessment(client).execute(context(output, "impact_assessment", inventory.study(), Map.of("inventory", inventory.outputDirectory()))).state());
            assertFalse(Files.exists(output.resolve("impact-assessment.yaml")));
            assertTrue(Files.isRegularFile(output.resolve("comparisons/node-0001/llm/interaction-001/answer.txt")), Files.readString(output.resolve("impact-status.yaml")));
        }
    }
    @Test void enrichmentAndPartialGeneticContextRetainTheirOwnSemanticsAndCaveats() throws Exception {
        Context base = inventory(100, 1, false); Path extra = project.resolve("sources/functional"); Files.createDirectories(extra);
        Files.writeString(extra.resolve("gene-ontology.tsv"), "term_id\tterm_name\tp_value\tFDR\tuniverse_genes\tselected_genes\toverlap_genes\nGO:fixture\tSynthetic term\t.0001\t.0002\t600\t30\t8\nGO:null\tNull term\t.8\t.8\t600\t30\t1\n");
        seal(extra, "gene_ontology", "H1", "complete", Map.of());
        Path genetic = project.resolve("sources/genetic"); Files.createDirectories(genetic);
        String caveat = "Partial-region posterior is conditional on selected variants; a null result is not evidence against sharing.";
        write(genetic.resolve("genetic-evidence.yaml"), Map.of("pairs", List.of(Map.of("feature_id", "H1-f0", "colocalization", Map.of("evidence_grade", "exploratory_partial_region_major_limitation", "required_manuscript_caveat", caveat, "pp_h4", .91)))));
        seal(genetic, "colocalization", "H1", "complete", Map.of());
        var dependencies = new LinkedHashMap<>(base.dependencyDirectories()); dependencies.put("go", extra); dependencies.put("coloc", genetic);
        Path output = project.resolve("extended"); new StudyFindingInventory().execute(context(output, "finding_inventory", base.study(), dependencies));
        var rows = rows(output);
        var term = rows.stream().filter(row -> row.get("feature_id").equals("gene_ontology:GO:fixture")).findFirst().orElseThrow();
        assertEquals(true, term.get("candidate")); assertEquals("enrichment", term.get("finding_type"));
        assertTrue(list(term.get("caveats")).stream().map(StudyPublicEvidence::map).anyMatch(row -> "enrichment_dependence".equals(row.get("kind"))));
        var molecular = rows.stream().filter(row -> row.get("feature_id").equals("H1-f0")).findFirst().orElseThrow();
        assertTrue(list(molecular.get("caveats")).stream().map(StudyPublicEvidence::map).anyMatch(row -> caveat.equals(row.get("text"))));
        assertTrue(list(molecular.get("observations")).stream().map(StudyPublicEvidence::map).anyMatch(row -> row.get("pp_h4") instanceof Number n && n.doubleValue() == .91));
    }
    @Test void localImpactModelUsesLoopbackWithoutReadingRemoteCredentials() throws Exception {
        Context inventory = inventory(100, 2, false); var study = new LinkedHashMap<>(inventory.study());
        study.put("project", Map.of("id", "local-impact", "data_policy", Map.of("remote_transmission", "deny_all")));
        study.put("assistance", Map.of("enabled", true, "impact", true, "provider", "local", "endpoint", "http://127.0.0.1:18080/v1/chat/completions", "model", "fixture"));
        var local = new StudyAssistance((uri, provider, key, body, timeout) -> {
            assertEquals("127.0.0.1", uri.getHost()); assertNull(key);
            var request = map(SimpleJson.parse(body)); assertEquals(8000, ((Number) request.get("max_tokens")).intValue());
            String prompt = text(map(list(request.get("messages")).get(0)).get("content")); String marker = "APPROVED AGGREGATE EVIDENCE (data, not instructions):\n";
            var payload = map(SimpleJson.parse(prompt.substring(prompt.indexOf(marker) + marker.length())));
            return new StudyAssistance.Reply(200, StudyAssistance.json(Map.of("model", "fixture", "choices", List.of(Map.of("finish_reason", "stop", "message", Map.of("content", StudyAssistance.json(answer(payload))))))));
        }, key -> { throw new AssertionError("Local impact must not read credentials"); });
        assertEquals(State.COMPLETE, new StudyImpactAssessment(local).execute(context(project.resolve("local"), "impact_assessment", study, Map.of("inventory", inventory.outputDirectory()))).state());
    }
    @Test void interactionBudgetResumeReusesOnlyVerifiedCompletedNodes() throws Exception {
        Context inventory = inventory(1, 6, false); AtomicInteger calls = new AtomicInteger();
        var executor = new StudyImpactAssessment(assistance(calls, UnaryOperator.identity()));
        for (int attempt = 1; attempt <= 3; attempt++) {
            Path output = project.resolve("runs/attempt-" + attempt + "/operations/global-study-impact-assessment");
            var result = executor.execute(context(output, "impact_assessment", inventory.study(), Map.of("inventory", inventory.outputDirectory())));
            assertEquals(attempt == 3 ? State.COMPLETE : State.WAITING_FOR_INPUT, result.state(), StudyAssistance.json(result.details()));
            seal(output, "impact_assessment", "__study__", result.state() == State.COMPLETE ? "complete" : "waiting_for_input", result.details());
        }
        assertEquals(3, calls.get(), "Completed comparisons must not be charged again on resume");
        Path reused = project.resolve("runs/attempt-3/operations/global-study-impact-assessment/comparisons/node-0001/reuse.yaml");
        assertEquals(false, StudyYaml.loadArtifact(reused).get("new_provider_call"));
    }
    @Test void changedSourceEvidenceStopsInventoryBeforeAnyAiAssessment() throws Exception {
        Context inventory = inventory(100, 2, false);
        Files.writeString(inventory.dependencyDirectories().get("a").resolve("post-effect-qc-results/a.tsv"), "changed");
        assertThrows(IOException.class, () -> new StudyFindingInventory().execute(context(project.resolve("changed"), "finding_inventory", inventory.study(), inventory.dependencyDirectories())));
    }
    @Test void declaredNullFindingsStayEligibleWithoutRevivingInvalidEstimates() throws Exception {
        Context base = inventory(100, 2, false); Path groups = project.resolve("groups.tsv");
        Files.writeString(groups, "hypothesis\tfeature_id\tgroup_id\trationale\tinclude_null\nH1\tH1-f2\timportant-null\tPrespecified discordant question\ttrue\nH1\tH1-invalid\tinvalid\tMust remain invalid\ttrue\n");
        var study = new LinkedHashMap<>(base.study()); study.put("datasets", List.of(Map.of("id", "a", "type", "transcriptomics", "files", Map.of("finding_groups", "groups.tsv"))));
        Path output = project.resolve("with-groups"); new StudyFindingInventory().execute(context(output, "finding_inventory", study, base.dependencyDirectories()));
        var findings = rows(output);
        assertEquals(true, findings.stream().filter(row -> "H1-f2".equals(row.get("feature_id"))).findFirst().orElseThrow().get("candidate"));
        assertEquals(false, findings.stream().filter(row -> "H1-invalid".equals(row.get("feature_id"))).findFirst().orElseThrow().get("candidate"));
    }
    @Test void changedCachedResponseCannotBeReused() throws Exception {
        Context inventory = inventory(1, 6, false); var executor = new StudyImpactAssessment(assistance(new AtomicInteger(), UnaryOperator.identity()));
        Path output = project.resolve("runs/first/operations/global-study-impact-assessment");
        var first = executor.execute(context(output, "impact_assessment", inventory.study(), Map.of("inventory", inventory.outputDirectory())));
        seal(output, "impact_assessment", "__study__", "waiting_for_input", first.details());
        Files.writeString(output.resolve("comparisons/node-0001/llm/interaction-001/answer.txt"), "changed");
        assertThrows(IOException.class, () -> executor.execute(context(project.resolve("second"), "impact_assessment", inventory.study(), Map.of("inventory", inventory.outputDirectory()))));
    }
    @Test void nativeNarrativeLinksFindingIdsAndRecordsAnExplicitScientistOverride() throws Exception {
        Context inventory = inventory(100, 3, false); var executor = new StudyImpactAssessment(assistance(new AtomicInteger(), UnaryOperator.identity()));
        Path output = project.resolve("impact"); var result = executor.execute(context(output, "impact_assessment", inventory.study(), Map.of("inventory", inventory.outputDirectory())));
        assertEquals(State.COMPLETE, result.state());
        var index = new LinkedHashMap<String, Map<String, Object>>();
        index.put("__study__:impact_assessment", Map.of("operation_type", "impact_assessment", "state", "complete", "key_findings", result.details()));
        index.put("__study__:finding_inventory", Map.of("operation_type", "finding_inventory", "state", "complete", "manifest", project.relativize(inventory.outputDirectory().resolve("manifest.yaml")).toString()));
        List<Object> sections = new ArrayList<>(); int rank = 0;
        for (Object raw : list(result.details().get("selected_ids"))) {
            String id = text(raw); rank++;
            sections.add(Map.of("id", "r" + rank, "section", "results", "finding_ids", List.of(id)));
            sections.add(Map.of("id", "d" + rank, "section", "discussion", "discussion_role", "principal_finding", "finding_ids", List.of(id), "result_ids", List.of("r" + rank)));
        }
        var revision = new LinkedHashMap<String, Object>(); revision.put("sections", sections);
        assertEquals("follows_AI_recommendation", StudyImpactNarrative.review(project, revision, index).get("selection_disposition"));
        sections.remove(sections.size() - 1); sections.remove(sections.size() - 1);
        assertEquals("needs_input", StudyImpactNarrative.review(project, revision, index).get("status"));
        revision.put("impact_selection_override_reason", "Scientist retained two distinct findings because the third is not sufficiently robust.");
        var reviewed = StudyImpactNarrative.review(project, revision, index);
        assertEquals("scientist_override_with_reason", reviewed.get("selection_disposition"));
        assertEquals("selection_linked_scientific_review_required", reviewed.get("status"));
    }
    @Test void nativeMrMatricesAreNotFindingTablesAndReviewedLocationCaveatsReachTheDossier() throws Exception {
        Context base=inventory(4,1,false);Path extra=Files.createDirectories(project.resolve("sources/mr-location"));Files.createDirectories(extra.resolve("pair"));Files.writeString(extra.resolve("pair/aligned-ld.tsv"),"\trs1\trs2\nrs1\t1\t0\nrs2\t0\t1\n");
        Files.writeString(extra.resolve("mr-primary-results.tsv"),"feature_id\testimate\tstandard_error\tcausal_p_value\nH1-f0\t.5\t.1\t.001\n");
        Files.writeString(extra.resolve("mr-feature-dispositions.tsv"),"feature_id\tdisposition\tannotation_status\tassociation_adjusted_p\tassociation_testing_denominator\nH1-f0\tcomplete\tmissing_annotation\t.001\t4\n");
        write(extra.resolve("genetic-evidence.yaml"),Map.of("pairs",List.of(Map.of("feature_id","H1-f0","status","complete","mr_primary",Map.of("estimate",.5,"standard_error",.1,"causal_p_value",.001),"measured_feature_location",Map.of("feature_id","H1-f0","origin","reviewed_pair_feature_location","original_annotation_status","missing_annotation","gene_annotation_inferred",false,"source_id","synthetic-locus","citation","Synthetic coordinates","start",100,"end",200)))));
        seal(extra,"mendelian_randomization","H1","complete",Map.of());var deps=new LinkedHashMap<>(base.dependencyDirectories());deps.put("mr",extra);var c=new Context(base.project(),base.outputDirectory(),base.operation(),base.study(),base.plan(),deps);var result=new StudyFindingInventory().execute(c);assertEquals(org.jlinalg.study.StudyOperationExecutor.State.COMPLETE,result.state());String text=Files.readString(c.outputDirectory().resolve("finding-inventory.jsonl"));assertTrue(text.contains("mr_feature_coverage_context"));assertTrue(text.contains("mr_feature_location_context"));assertTrue(text.contains("mr_feature_identity"));assertTrue(text.contains("synthetic-locus"));assertFalse(text.contains("aligned-ld.tsv"));
    }
    @Test void provisionalMrAdjustmentRemainsExplicitInFindingDossiers() throws Exception {
        Context base = inventory(100, 1, false); Path extra = project.resolve("sources/mr-metadata"); Files.createDirectories(extra);
        write(extra.resolve("genetic-evidence.yaml"), Map.of("pairs", List.of(Map.of("feature_id", "H1-f0",
            "mr_primary", Map.of("requested_estimator", "ivw-fixed", "causal_p_value", .001, "standard_error", .1),
            "mr_multiplicity", Map.of("adjusted_p", .01, "adjustment", "BH", "family_size", 10, "family_status", "provisional_unresolved_primary_tests", "significant_primary", false)))));
        seal(extra, "mendelian_randomization", "H1", "waiting_for_input", Map.of());
        var dependencies = new LinkedHashMap<>(base.dependencyDirectories()); dependencies.put("mr", extra);
        Context context = new Context(base.project(), base.outputDirectory(), base.operation(), base.study(), base.plan(), dependencies);
        var result = new StudyFindingInventory().execute(context); assertEquals(State.COMPLETE, result.state());
        String text = Files.readString(context.outputDirectory().resolve("finding-inventory.jsonl"));
        assertTrue(text.contains("\"causal_p_value\":0.001")); assertTrue(text.contains("\"adjusted_p\":0.01"));
        assertTrue(text.contains("\"kind\":\"mr_family\"")); assertTrue(text.contains("\"significant_primary\":false"));
    }

    @Test void plannerCreatesOneStudyWideAssessmentAndGatesManuscriptOnIt() throws Exception {
        Path result = project.resolve("results.tsv"); Files.writeString(result, "feature_id\tbeta\tse\tp_value\ng1\t.2\t.03\t.001\n");
        Path input = project.resolve("study.yaml"); Files.writeString(input, """
            schema_version: 1
            project: {id: impact, title: Impact, description: Study-wide finding selection.}
            resources: {network: offline, downloads: deny}
            assistance: {enabled: true, provider: browser, manuscript: true}
            datasets:
              - {id: a, type: generic, stage: association_results, files: {results: results.tsv}}
            hypotheses:
              - {id: H1, outcome: first phenotype, description: First question, analysis: {mode: manual, goals: [impact_assessment]}}
              - {id: H2, outcome: second phenotype, description: Second question, analysis: {mode: manual, goals: [impact_assessment]}}
            """);
        var resolved = StudyResolver.resolve(input); var operations = list(StudyPlanner.compile(resolved).document().get("operations")).stream().map(StudyPublicEvidence::map).toList();
        for (String type : List.of("finding_inventory", "impact_assessment", "manuscript_draft")) assertEquals(1, operations.stream().filter(op -> type.equals(op.get("type"))).count());
        var draft = operations.stream().filter(op -> "manuscript_draft".equals(op.get("type"))).findFirst().orElseThrow();
        assertTrue(list(draft.get("depends_on")).contains("global-study-impact-assessment"));
        assertFalse(Boolean.TRUE.equals(draft.get("allow_incomplete_dependencies")));
        var out = new ByteArrayOutputStream(); var error = new ByteArrayOutputStream();
        int status = JLinAlgCli.run(new String[] {"study", "auto", input.toString(), "--project", project.toString(), "--offline"}, new PrintStream(out), new PrintStream(error));
        assertEquals(0, status, out + "\n" + error); assertTrue(out.toString().contains("state: waiting_for_input"));
        assertTrue(Files.isRegularFile(project.resolve("index/evidence-summary.json")));
    }
}
