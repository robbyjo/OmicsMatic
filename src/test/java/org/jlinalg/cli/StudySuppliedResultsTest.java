/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyOperationExecutor.State;
import org.jlinalg.study.StudySuppliedResultPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudySuppliedResultsTest {
    @TempDir Path project;
    @Test void explicitAggregateMappingResolvesSuppliedSampleSizeAliasesWithoutDroppingTheChoice()throws Exception {
        Files.writeString(project.resolve("results.tsv"),"feature_id\tbeta\tse\tp_value\tadjusted_p\tn\tOBS_CT\tn_cases\tn_controls\nrs1\t.1\t.1\t.01\t.01\t1000\t800\t400\t400\n");var d=definition("meta_analysis",Map.of("id","full","coverage","complete","tested_count",1,"adjustment","BH","adjusted_p_from_full_family",true));assertThrows(java.io.IOException.class,()->new StudySuppliedResults().execute(context("ambiguous-count",d)));d.put("columns",Map.of("result_metadata",Map.of("n","OBS_CT")));var c=context("chosen-count",d);var result=new StudySuppliedResults().execute(c);assertEquals(State.COMPLETE,result.state());var table=DelimitedData.readResult(c.outputDirectory().resolve("meta-analysis.tsv"));assertEquals("800",table.rows().get(0)[table.column("n")]);assertEquals("OBS_CT",StudyPublicEvidence.map(StudyPublicEvidence.map(result.details().get("aggregate_result_metadata")).get("source_columns")).get("n"));d.put("columns",Map.of("results",Map.of("result_metadata",Map.of("n","OBS_CT"))));var role=context("role-count",d);assertEquals(State.COMPLETE,new StudySuppliedResults().execute(role).state());var roleTable=DelimitedData.readResult(role.outputDirectory().resolve("meta-analysis.tsv"));assertEquals("800",roleTable.rows().get(0)[roleTable.column("n")]);
    }
    @Test void suppliedEffectsRetainValidatedAllelesCountsAndExplicitModelWithoutUnmappedProse()throws Exception {
        Files.writeString(project.resolve("results.tsv"),"feature_id\tbeta\tse\tp_value\tadjusted_p\tN\tN_CASE\tN_CONTROL\tMAC\tMAF\tREF\tALT\tprovider_model\tpatient_note\nrs1\t.1\t.1\t.01\t.01\t1000\t500\t500\t20\t.01\tA\tG\tfirth\tPRIVATE_SENTINEL\n");var d=definition("meta_analysis",Map.of("id","full","coverage","complete","tested_count",1,"adjustment","BH","adjusted_p_from_full_family",true));d.put("columns",Map.of("model","provider_model"));var c=context("validated-metadata",d);var result=new StudySuppliedResults().execute(c);assertEquals(State.COMPLETE,result.state(),result.summary());var table=DelimitedData.readResult(c.outputDirectory().resolve("meta-analysis.tsv"));var row=table.rows().get(0);for(var field:Map.of("n","1000","n_cases","500","n_controls","500","minor_allele_count","20","minor_allele_frequency",".01","reference_allele","A","alternate_allele","G","model","firth").entrySet())assertEquals(field.getValue(),row[table.column(field.getKey())]);assertFalse(table.header().contains("patient_note"));assertFalse(Files.readString(c.outputDirectory().resolve("meta-analysis.tsv")).contains("PRIVATE_SENTINEL"));assertTrue(result.details().containsKey("aggregate_result_metadata"));
        Files.writeString(project.resolve("results.tsv"),Files.readString(project.resolve("results.tsv")).replace("\t500\t500\t20","\t900\t900\t20"));var invalid=context("bad-counts",d);assertThrows(java.io.IOException.class,()->new StudySuppliedResults().execute(invalid));
    }
    private Map<String, Object> definition(String kind, Map<String, Object> family) {
        var definition = new LinkedHashMap<String, Object>();
        definition.put("id", "external"); definition.put("kind", kind);
        definition.put("files", Map.of("results", "results.tsv"));
        definition.put("scope", Map.of("phenotype", "synthetic", "cohorts", List.of("A", "B")));
        definition.put("method", Map.of("name", "External synthetic protocol", "software", "fixture"));
        if (!family.isEmpty()) definition.put("family", family);
        return definition;
    }
    private Context context(String name, Map<String, Object> definition) throws Exception {
        var analysis = Map.<String, Object>of("supplied_results", List.of(definition));
        var resolved = StudySuppliedResultPolicy.resolve(analysis).get(0);
        return new Context(project, Files.createDirectory(project.resolve(name)),
            Map.of("type", "sensitivity".equals(resolved.get("role")) ? "supplied_sensitivity" : resolved.get("kind"),
                "hypothesis", "H", "datasets", List.of(), "supplied_result_id", "external"),
            Map.of("datasets", List.of(), "hypotheses", List.of(Map.of("id", "H", "analysis", analysis))), Map.of(), Map.of());
    }
    private static void seal(Context context, org.jlinalg.study.StudyOperationExecutor.Result result) throws Exception {
        var hashes = new LinkedHashMap<String, Object>();
        try (var paths = Files.walk(context.outputDirectory())) {
            for (Path file : paths.filter(Files::isRegularFile).toList()) hashes.put(context.outputDirectory().relativize(file).toString().replace('\\','/'), FollowupSupport.hash(file));
        }
        StudyPublicEvidence.write(context.outputDirectory().resolve("manifest.yaml"), Map.of("operation_id", "imported", "hypothesis", "H",
            "operation_type", context.operation().get("type"), "state", result.state().value(), "output_sha256", hashes, "details", result.details()));
    }
    @Test void suppliedColocalizationRetainsMultipleSignalPairsAndTheirSourceCoverage()throws Exception {
        Files.writeString(project.resolve("results.tsv"),"feature_id\tregion_id\ttrait1_effect_index\ttrait2_effect_index\tpp_h0\tpp_h1\tpp_h2\tpp_h3\tpp_h4\toutcome_variant_match_fraction\texposure_export_p_threshold\texposure_converged\nprobe1\tregion\t0\t0\t.01\t.01\t.01\t.02\t.95\t.03\t1e-7\ttrue\nprobe1\tregion\t1\t0\t.01\t.01\t.01\t.95\t.02\t.03\t1e-7\ttrue\n");
        var definition=definition("colocalization",Map.of());definition.put("evidence_grade","exploratory_partial_region_major_limitation");var c=context("supplied-multiple-signals",definition);String hash=FollowupSupport.hash(project.resolve("results.tsv"));var result=new StudySuppliedResults().execute(c);assertEquals(State.COMPLETE,result.state(),result.details().toString());var table=DelimitedData.readResult(c.outputDirectory().resolve("colocalization-results.tsv"));assertEquals(2,table.rows().size());assertEquals(".03",table.rows().get(0)[table.column("outcome_variant_match_fraction")]);assertEquals("1e-7",table.rows().get(0)[table.column("exposure_export_p_threshold")]);assertEquals("1",table.rows().get(1)[table.column("trait1_effect_index")]);assertEquals(hash,FollowupSupport.hash(project.resolve("results.tsv")));assertEquals(false,result.details().get("analysis_executed_by_jlinalg"));
    }
    @Test void suppliedColocalizationChecksProbabilityCoherenceWithoutRenormalizingRounding()throws Exception {
        Files.writeString(project.resolve("results.tsv"),"feature_id\tregion_id\tpp_h0\tpp_h1\tpp_h2\tpp_h3\tpp_h4\nprobe1\tregion\t.01\t.01\t.01\t.02\t.951\n");var strict=definition("colocalization",Map.of());var rejected=assertThrows(java.io.IOException.class,()->new StudySuppliedResults().execute(context("strict-posteriors",strict)));assertTrue(rejected.getMessage().contains("sum to one"));
        var rounded=definition("colocalization",Map.of());rounded.put("method",Map.of("name","External rounded fixture","software","fixture","parameters",Map.of("posterior_sum_tolerance",.002)));var c=context("rounded-posteriors",rounded);assertEquals(State.COMPLETE,new StudySuppliedResults().execute(c).state());var table=DelimitedData.readResult(c.outputDirectory().resolve("colocalization-results.tsv"));assertEquals(".951",table.rows().get(0)[table.column("pp_h4")]);
    }

    @Test void completeSuppliedByFamilyUsesItsDeclaredFullDenominator()throws Exception {
        Files.writeString(project.resolve("results.tsv"),"feature_id\tbeta\tse\tp_value\nprobe1\t1\t.1\t.02\nother\t1\t.1\t.8\n");var c=context("supplied-by",definition("meta_analysis",Map.of("id","by-family","coverage","complete","tested_count",2,"adjustment","BY")));var result=new StudySuppliedResults().execute(c);assertEquals(State.COMPLETE,result.state(),result.details().toString());var table=DelimitedData.readResult(c.outputDirectory().resolve("meta-analysis.tsv"));assertEquals(.06,Double.parseDouble(table.rows().get(0)[table.column("adjusted_p")]),1e-14);assertEquals("BY",StudyPublicEvidence.map(result.details().get("resolved_family")).get("adjustment"));
    }

    @Test void completeMetaFamilyIsImportedAndAdjustedWithoutRefittingOrChangingInputs() throws Exception {
        Path input = project.resolve("results.tsv");
        Files.writeString(input, "feature_id\tbeta\tse\tp_value\ng1\t1\t.2\t.001\ng2\t-.1\t.2\t.5\n");
        String original = FollowupSupport.hash(input);
        var definition = definition("meta_analysis", Map.of("id", "all-features", "coverage", "complete", "tested_count", 2, "adjustment", "BH"));
        Context context = context("complete", definition);
        var result = new StudyCliExecutor().execute(context);
        assertEquals(State.COMPLETE, result.state());
        assertEquals(false, result.details().get("analysis_executed_by_jlinalg"));
        assertEquals("externally_produced", result.details().get("execution_origin"));
        var table = DelimitedData.readResult(context.outputDirectory().resolve("meta-analysis.tsv"));
        assertEquals(.002, Double.parseDouble(table.rows().get(0)[table.column("adjusted_p")]), 1e-14);
        assertEquals(.5, Double.parseDouble(table.rows().get(1)[table.column("adjusted_p")]), 1e-14);
        assertEquals("pooled", table.rows().get(0)[table.column("term")]);
        assertEquals(original, FollowupSupport.hash(input));
        assertEquals(original, FollowupSupport.hash(context.outputDirectory().resolve("supplied/results.tsv")));
        assertFalse(Files.exists(context.outputDirectory().resolve("meta-estimates.tsv")), "No native meta-analysis was fitted");
    }
    @Test void selectedRowsPreserveSourceAdjustmentInsteadOfRecomputingTheirOwnFamily() throws Exception {
        Files.writeString(project.resolve("results.tsv"), "gene\teffect\terror\tP\tq\ng1\t1\t.1\t.000001\t.4\n");
        var definition = definition("meta_analysis", Map.of("id", "full-external-family", "coverage", "selected_only", "tested_count", 400000,
            "adjustment", "BH", "adjusted_p_from_full_family", true));
        definition.put("columns", Map.of("feature_id", "gene", "beta", "effect", "se", "error", "p_value", "P", "adjusted_p", "q"));
        Context context = context("selected", definition);
        assertEquals(State.COMPLETE, new StudyCliExecutor().execute(context).state());
        var table = DelimitedData.readResult(context.outputDirectory().resolve("meta-analysis.tsv"));
        assertEquals(.4, Double.parseDouble(table.rows().get(0)[table.column("adjusted_p")]), 0);
    }
    @Test void selectedOnlyAdjustmentSurvivesSealedLiteratureSelectionAndRejectsTampering() throws Exception {
        Files.writeString(project.resolve("results.tsv"), "feature_id\tbeta\tse\tp_value\tadjusted_p\ng1\t1\t.1\t.000001\t.4\n");
        Files.writeString(project.resolve("annotation.tsv"), "feature_id\tgene_symbol\tgene_id\ng1\tGENE1\tENSG00001\n");
        var definition = definition("meta_analysis", Map.of("id", "full-family", "coverage", "selected_only", "tested_count", 400000,
            "adjustment", "BH", "adjusted_p_from_full_family", true));
        definition.put("files", Map.of("results", "results.tsv", "feature_annotation", "annotation.tsv"));
        Context imported = context("sealed", definition);
        var result = new StudyCliExecutor().execute(imported); seal(imported, result);
        Context downstream = new Context(project, Files.createDirectory(project.resolve("literature")),
            Map.of("type", "literature_search", "hypothesis", "H", "datasets", List.of()), imported.study(), Map.of(), Map.of("meta", imported.outputDirectory()));
        assertTrue(StudyBioinformatics.mrFeatures(downstream).isEmpty(), "Source q=.4 must not become subset BH=1e-6");
        var findings = StudyPublicEvidence.map(org.jlinalg.study.StudyEvidenceIndex.keyFindings(imported.outputDirectory(), "meta_analysis"));
        assertEquals(400000, ((Number)findings.get("tested_features")).intValue());
        assertEquals(1, findings.get("supplied_eligible_features"));
        assertEquals(0L, findings.get("supplied_significant_adjusted_0_05_features"));
        assertFalse(findings.containsKey("BH_FDR_0_05_features"), "A selected subset cannot establish a total discovery count");
        Context inventoryContext = new Context(project, Files.createDirectory(project.resolve("inventory")),
            Map.of("type", "finding_inventory", "scope", "study", "datasets", List.of()), imported.study(), Map.of(), Map.of("meta", imported.outputDirectory()));
        var inventory = new StudyFindingInventory().execute(inventoryContext);
        assertEquals(0L, inventory.details().get("candidates"));
        var dossier = StudyPublicEvidence.map(SimpleJson.parse(Files.readString(inventoryContext.outputDirectory().resolve("finding-inventory.jsonl")).strip()));
        var observation = StudyPublicEvidence.map(StudyPublicEvidence.list(dossier.get("observations")).get(0));
        assertEquals(400000, ((Number)observation.get("testing_denominator")).intValue());
        assertEquals(.4, ((Number)observation.get("full_family_adjusted_p")).doubleValue());
        Files.writeString(imported.outputDirectory().resolve("meta-analysis.tsv"), "feature_id\tbeta\tse\tp_value\tadjusted_p\ng1\t1\t.1\t.000001\t.000001\n");
        assertThrows(java.io.IOException.class, () -> StudyBioinformatics.mrFeatures(downstream));
    }
    @Test void allSuppliedAnalysesPlanAndExecuteWithoutRawDataOrDuplicateFits() throws Exception {
        Files.writeString(project.resolve("meta.tsv"), "feature_id\tbeta\tse\tp_value\ng1\t1\t.1\t.001\n");
        Files.writeString(project.resolve("go.tsv"), "term_id\tterm_name\tp_value\nGO:1\tSynthetic term\t.001\n");
        Files.writeString(project.resolve("mr.tsv"), "feature_id\tbeta\tse\tp_value\ng1\t.2\t.1\t.04\n");
        Files.writeString(project.resolve("coloc.tsv"), "feature_id\tregion_id\tpp_h4\ng1\tr1\t.95\n");
        Files.writeString(project.resolve("nodes.tsv"), "node_id\ng1\ng2\n");
        Files.writeString(project.resolve("edges.tsv"), "source\ttarget\tweight\ng1\tg2\t.8\n");
        var definitions = new ArrayList<Object>();
        for (var entry : Map.of("meta_analysis", "meta.tsv", "gene_ontology", "go.tsv", "mendelian_randomization", "mr.tsv", "colocalization", "coloc.tsv").entrySet()) {
            var definition = definition(entry.getKey(), entry.getKey().equals("colocalization") ? Map.of()
                : Map.of("id", entry.getKey() + "-family", "coverage", "complete", "tested_count", 1, "adjustment", "BH"));
            definition.put("id", entry.getKey()); definition.put("files", Map.of("results", entry.getValue()));
            if (entry.getKey().equals("colocalization")) {
                definition.put("evidence_grade", "exploratory_partial_region_major_limitation");
                definition.put("limitations", List.of("Source exported only significant QTL variants."));
            }
            definitions.add(definition);
        }
        var network = definition("network_analysis", Map.of()); network.put("id", "network");
        network.put("files", Map.of("nodes", "nodes.tsv", "edges", "edges.tsv")); definitions.add(network);
        var study = Map.of("schema_version", 1, "project", Map.of("id", "provided", "title", "Provided analyses", "description", "Synthetic import protocol."),
            "resources", Map.of("network", "offline", "downloads", "deny", "directory", "fixture-resources"), "datasets", List.of(),
            "hypotheses", List.of(Map.of("id", "H", "description", "Use completed analyses without their unavailable raw data.",
                "analysis", Map.of("mode", "manual", "goals", List.of("meta_analysis", "gene_ontology", "network_analysis", "mendelian_randomization", "colocalization"), "supplied_results", definitions))));
        Path path = project.resolve("study.yaml"); StudyPublicEvidence.write(path, study);
        var error = new ByteArrayOutputStream();
        assertEquals(0, JLinAlgCli.run(new String[] {"study", "auto", path.toString(), "--offline", "--actor-type", "external", "--actor-id", "synthetic-import-fixture"},
            new PrintStream(new ByteArrayOutputStream()), new PrintStream(error)), error.toString());
        var manifests = new ArrayList<Map<String,Object>>();
        try (var paths = Files.walk(project.resolve("runs"))) {
            for (Path manifest : paths.filter(file -> file.getFileName().toString().equals("manifest.yaml")).toList()) manifests.add(StudyYaml.loadArtifact(manifest));
        }
        assertTrue(manifests.stream().allMatch(manifest -> "complete".equals(manifest.get("state"))), manifests.toString());
        for (String kind : List.of("meta_analysis", "gene_ontology", "network_analysis", "mendelian_randomization", "colocalization")) {
            var selected = manifests.stream().filter(manifest -> kind.equals(manifest.get("operation_type"))).toList();
            assertEquals(1, selected.size(), kind + " must not be rerun");
            assertEquals(false, StudyPublicEvidence.map(selected.get(0).get("details")).get("analysis_executed_by_jlinalg"));
        }
        var coloc = manifests.stream().filter(manifest -> "colocalization".equals(manifest.get("operation_type"))).findFirst().orElseThrow();
        assertTrue(String.valueOf(StudyPublicEvidence.map(coloc.get("details")).get("required_manuscript_caveat")).contains("a null result is not evidence against a shared causal variant"));
        assertFalse(manifests.stream().anyMatch(manifest -> List.of("association", "participant_qc", "sensitivity_analysis").contains(manifest.get("operation_type"))));
        assertEquals(0, JLinAlgCli.run(new String[] {"study", "verify", "--project", project.toString()},
            new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream())));
    }
    @Test void suppliedMetaFeedsMissingNativeEnrichmentWithItsFullMappedBackground() throws Exception {
        StringBuilder estimates = new StringBuilder("feature_id\tbeta\tse\tp_value\n");
        StringBuilder annotation = new StringBuilder("feature_id\tgene_symbol\n");
        for (int i=1;i<=30;i++) {
            estimates.append("g").append(i).append("\t.4\t.1\t").append(i<=6 ? ".0001" : ".8").append('\n');
            annotation.append("g").append(i).append("\tA").append(i).append('\n');
        }
        Files.writeString(project.resolve("results.tsv"), estimates);
        Files.writeString(project.resolve("annotation.tsv"), annotation);
        var definition = definition("meta_analysis", Map.of("id", "complete-features", "coverage", "complete", "tested_count", 30, "adjustment", "BH"));
        definition.put("files", Map.of("results", "results.tsv", "feature_annotation", "annotation.tsv"));
        Context imported = context("external-meta", definition);
        var result = new StudyCliExecutor().execute(imported); seal(imported, result);
        var study = new LinkedHashMap<>(imported.study());
        study.put("resources", Map.of("network", "offline", "downloads", "deny", "directory", "fixture-resources"));
        Path database = StudyResourceManager.resourceRoot(project, study).resolve("enrichment/go-human");
        assertTrue(database.toAbsolutePath().normalize().startsWith(project.toAbsolutePath().normalize()),
            "The synthetic resource must remain inside this test's temporary project, regardless of machine-wide configuration");
        Files.createDirectories(database);
        StringBuilder gmt = new StringBuilder();
        for (int term=0;term<3;term++) {
            gmt.append("GO:").append(term+1).append("\tSynthetic term ").append(term+1);
            for(int gene=term*10+1;gene<=term*10+10;gene++) gmt.append("\tA").append(gene);
            gmt.append('\n');
        }
        Files.writeString(database.resolve("data.gmt"), gmt);
        var manifest = new java.util.Properties();
        manifest.setProperty("format_version","1"); manifest.setProperty("provider","GO"); manifest.setProperty("gene_id_type","symbol");
        manifest.setProperty("data_format","gmt"); manifest.setProperty("data_file","data.gmt"); manifest.setProperty("sha256.data.gmt", FollowupSupport.hash(database.resolve("data.gmt")));
        try(var writer=Files.newBufferedWriter(database.resolve("manifest.properties"))) { manifest.store(writer,"Synthetic offline resource; not biological GO annotations"); }
        Context enrichment = new Context(project, Files.createDirectory(project.resolve("new-go")),
            Map.of("type", "gene_ontology", "hypothesis", "H", "datasets", List.of()), study, Map.of(), Map.of("meta", imported.outputDirectory()));
        String before = FollowupSupport.hash(imported.outputDirectory().resolve("meta-analysis.tsv"));
        var enriched = new StudyCliExecutor().execute(enrichment);
        assertEquals(State.COMPLETE, enriched.state(), enriched.details().toString());
        assertEquals(30, Files.readAllLines(enrichment.outputDirectory().resolve("background-genes.txt")).size());
        assertEquals(6, Files.readAllLines(enrichment.outputDirectory().resolve("selected-genes.txt")).size());
        var rows = DelimitedData.readResult(enrichment.outputDirectory().resolve("gene-ontology.tsv"));
        String[] target = rows.rows().stream().filter(row -> row[rows.column("term_id")].equals("GO:1")).findFirst().orElseThrow();
        double expected = 210.0 / 593775.0;
        assertEquals(expected, Double.parseDouble(target[rows.column("p_value")]), 1e-12);
        assertEquals(3*expected, Double.parseDouble(target[rows.column("FDR")]), 1e-12);
        assertEquals(before, FollowupSupport.hash(imported.outputDirectory().resolve("meta-analysis.tsv")));
    }
    @Test void nativeSensitivityRequiresMatchingExternalScopeSettingsAndFeatureUniverse() throws Exception {
        Files.writeString(project.resolve("results.tsv"), "feature_id\tbeta\tse\tp_value\ng1\t1.1\t.1\t.001\n");
        Files.writeString(project.resolve("a.tsv"), "feature_id\tbeta\tse\tp_value\ng1\t1\t.1\t.001\n");
        Files.writeString(project.resolve("b.tsv"), "feature_id\tbeta\tse\tp_value\ng1\t1.2\t.1\t.001\n");
        var contract = new LinkedHashMap<String,Object>();
        for(String field:org.jlinalg.study.StudyPoolingPolicy.EFFECT_FIELDS) contract.put(field,"synthetic common " + field);
        var definition=definition("meta_analysis",Map.of("id","full","coverage","complete","tested_count",1,"adjustment","BH"));
        definition.put("scope",Map.of("cohorts",List.of("A","B"),"datasets",List.of("a","b"),"effect_contract",contract));
        definition.put("method",Map.of("name","External fixed model","parameters",Map.of("model","fixed","inference","normal","confidence_level",.95,"min_cohorts",2)));
        definition.put("provenance",Map.of("input_sha256",Map.of("a",FollowupSupport.hash(project.resolve("a.tsv")),"b",FollowupSupport.hash(project.resolve("b.tsv")))));
        Context original=context("matched-external",definition);
        var study=new LinkedHashMap<>(original.study());
        study.put("datasets",List.of(Map.of("id","a","cohort","A"),Map.of("id","b","cohort","B")));
        study.put("hypotheses",List.of(Map.of("id","H","analysis",Map.of("supplied_results",List.of(definition),"pooling",Map.of("effect_contract",contract)))));
        Context imported=new Context(project,original.outputDirectory(),original.operation(),study,Map.of(),Map.of());
        var result=new StudyCliExecutor().execute(imported);seal(imported,result);
        Path baseline=imported.outputDirectory().resolve("meta-analysis.tsv");
        Map<String,Path> inputs=Map.of("a",project.resolve("a.tsv"),"b",project.resolve("b.tsv"));
        assertNull(StudySuppliedFamilies.sensitivityIssue(imported,baseline,inputs));
        assertTrue(StudySuppliedFamilies.sensitivityIssue(imported,baseline,Map.of("a",project.resolve("a.tsv"))).contains("exactly the sources"));
        Files.writeString(project.resolve("b.tsv"),"feature_id\tbeta\tse\tp_value\ng2\t1.2\t.1\t.001\n");
        assertTrue(StudySuppliedFamilies.sensitivityIssue(imported,baseline,inputs).contains("different eligible feature universes"));
        Files.writeString(project.resolve("b.tsv"),"feature_id\tbeta\tse\tp_value\ng1\t1.3\t.1\t.001\n");
        assertTrue(StudySuppliedFamilies.sensitivityIssue(imported,baseline,inputs).contains("input versions differ"));
    }
    @Test void unknownFamilyRetainsRawEvidenceAndCannotBecomeAnOfficialPrimary() throws Exception {
        Files.writeString(project.resolve("results.tsv"), "feature_id\tbeta\tse\tp_value\ng1\t1\t.1\t.001\n");
        Context context = context("unknown-family", definition("meta_analysis", Map.of()));
        var result = new StudyCliExecutor().execute(context);
        assertEquals(State.WAITING_FOR_INPUT, result.state());
        assertEquals(false, result.details().get("primary_selection_eligible"));
        assertFalse(Files.exists(context.outputDirectory().resolve("meta-analysis.tsv")));
        assertTrue(Files.isRegularFile(context.outputDirectory().resolve("supplied-results-limited.tsv")));
        assertTrue(Files.isRegularFile(context.outputDirectory().resolve("supplied/results.tsv")));
    }
    @Test void suppliedSensitivityCannotPublishPrimaryMetaFilename() throws Exception {
        Files.writeString(project.resolve("results.tsv"), "feature_id\tbeta\tse\tp_value\ng1\t1\t.1\t.001\n");
        var definition = definition("meta_analysis", Map.of("id", "sensitivity-family", "coverage", "complete", "tested_count", 1, "adjustment", "BH"));
        definition.put("role", "sensitivity");
        Context context = context("sensitivity", definition);
        assertEquals(State.COMPLETE, new StudyCliExecutor().execute(context).state());
        assertFalse(Files.exists(context.outputDirectory().resolve("meta-analysis.tsv")));
        assertTrue(Files.isRegularFile(context.outputDirectory().resolve("supplied-sensitivity.tsv")));
    }
    @Test void suppliedGraphValidatesNodesAndModuleMembershipWithoutBuildingNewEdges() throws Exception {
        Files.writeString(project.resolve("nodes.tsv"), "node_id\na\nb\nc\n");
        Files.writeString(project.resolve("edges.tsv"), "source\ttarget\tweight\na\tb\t-.8\n");
        Files.writeString(project.resolve("modules.tsv"), "node_id\tmodule_id\na\tM1\nb\tM1\nb\tM2\n");
        var definition = definition("network_analysis", Map.of());
        definition.put("files", Map.of("nodes", "nodes.tsv", "edges", "edges.tsv", "modules", "modules.tsv"));
        Context context = context("network", definition);
        var result = new StudyCliExecutor().execute(context);
        assertEquals(State.COMPLETE, result.state());
        assertEquals(3, result.details().get("nodes")); assertEquals(1, result.details().get("edges"));
        assertEquals(3, DelimitedData.readResult(context.outputDirectory().resolve("network-modules.tsv")).rows().size());
        Files.writeString(project.resolve("edges.tsv"), "source\ttarget\na\tunknown\n");
        assertThrows(java.io.IOException.class, () -> new StudyCliExecutor().execute(context("bad-network", definition)));
    }
    @Test void importDefinitionsHaveStableDependenciesAndRejectCyclesOrDuplicatePrimaries() {
        var a = definition("meta_analysis", Map.of()); a.put("id", "meta");
        var b = definition("gene_ontology", Map.of()); b.put("id", "go"); b.put("depends_on", List.of("meta"));
        var ordered = StudySuppliedResultPolicy.resolve(Map.of("supplied_results", List.of(b,a)));
        assertEquals(List.of("meta", "go"), ordered.stream().map(row -> row.get("id")).toList());
        a.put("depends_on", List.of("go"));
        assertThrows(IllegalArgumentException.class, () -> StudySuppliedResultPolicy.resolve(Map.of("supplied_results", List.of(a,b))));
        a.remove("depends_on"); var duplicate = new LinkedHashMap<>(a); duplicate.put("id", "other-meta");
        assertThrows(IllegalArgumentException.class, () -> StudySuppliedResultPolicy.resolve(Map.of("supplied_results", List.of(a,duplicate))));
        duplicate.put("role", "sensitivity");
        assertEquals(2, StudySuppliedResultPolicy.resolve(Map.of("supplied_results", List.of(a,duplicate))).size());
    }

    @Test void unsignedRareSetsKeepJointMaskTestFamilyAndPrespecifiedPrimary() throws Exception {
        Files.writeString(project.resolve("results.tsv"), "feature_id\tmask_id\ttest\tp_value\tis_primary_test\n"
            + "g1\tlof\tSKAT-O\t.001\tTRUE\ng1\tlof\tburden\t.00001\tfalse\n"
            + "g1\tmissense\tSKAT-O\t.2\ttrue\ng1\tmissense\tburden\t.3\tfalse\n");
        var definition = definition("rare_variant_analysis", Map.of("id", "all-masks-tests", "coverage", "complete", "tested_count", 4, "adjustment", "BONFERRONI"));
        Context imported = context("rare-supplied", definition);
        var result = new StudyCliExecutor().execute(imported); seal(imported,result);
        assertEquals(State.COMPLETE,result.state());
        var table = DelimitedData.readResult(imported.outputDirectory().resolve("rare-results.tsv"));
        assertEquals(4,table.rows().size());
        assertFalse(table.header().contains("beta")); assertFalse(table.header().contains("direction"));
        assertEquals("unsigned_set",table.rows().get(0)[table.column("result_type")]);
        assertEquals(.004,Double.parseDouble(table.rows().get(0)[table.column("adjusted_p")]),1e-14);
        Context inventory = new Context(project, Files.createDirectory(project.resolve("rare-inventory")),
            Map.of("type","finding_inventory","scope","study","datasets",List.of()),imported.study(),Map.of(),Map.of("rare",imported.outputDirectory()));
        var inventoried = new StudyFindingInventory().execute(inventory);
        assertEquals(1L,inventoried.details().get("candidates"));
        var dossier = StudyPublicEvidence.map(SimpleJson.parse(Files.readString(inventory.outputDirectory().resolve("finding-inventory.jsonl")).strip()));
        var observations = StudyPublicEvidence.list(dossier.get("observations")).stream().map(StudyPublicEvidence::map).toList();
        assertEquals(2,observations.stream().filter(row -> Boolean.TRUE.equals(row.get("primary"))).count());
        assertTrue(observations.stream().filter(row -> Boolean.TRUE.equals(row.get("primary"))).allMatch(row -> ((Number)row.get("testing_denominator")).intValue()==4 && row.get("adjustment").equals("BONFERRONI")),observations.toString());
        Files.writeString(project.resolve("results.tsv"),"feature_id\tmask_id\ttest\tp_value\tis_primary_test\ng1\tlof\tSKAT-O\t.1\ttrue\ng1\tlof\tburden\t.001\ttrue\n");
        assertThrows(java.io.IOException.class,() -> new StudyCliExecutor().execute(context("ambiguous-primary",definition)));
    }

    @Test void suppliedPrimarySuppressesInferredRawFitsButExplicitAssociationStillWins() throws Exception {
        var definition = definition("meta_analysis",Map.of("id","external-family","coverage","complete","tested_count",1,"adjustment","BH"));
        var analysis = new LinkedHashMap<String,Object>(Map.of("supplied_results",List.of(definition)));
        var hypothesis = new LinkedHashMap<String,Object>(Map.of("id","H","description","Synthetic mixed inputs.","outcome","synthetic","analysis",analysis));
        var study = Map.of("schema_version",1,"project",Map.of("id","mixed","title","Mixed inputs","description","Synthetic planner fixture."),
            "resources",Map.of("directory","fixture-resources","network","offline","downloads","deny"),
            "cohorts",List.of(Map.of("id","A")),"datasets",List.of(Map.of("id","raw","cohort","A","type","generic","stage","processed_matrix","files",Map.of("matrix","matrix.tsv","phenotype","phenotype.tsv"))),
            "hypotheses",List.of(hypothesis));
        Path path = project.resolve("mixed.yaml"); StudyPublicEvidence.write(path,study);
        var resolved = org.jlinalg.study.StudyResolver.resolve(path);
        var plan = org.jlinalg.study.StudyPlanner.compile(resolved);
        var types = StudyPublicEvidence.list(plan.document().get("operations")).stream().map(StudyPublicEvidence::map).map(row -> row.get("type")).toList();
        assertFalse(types.contains("association")); assertFalse(types.contains("participant_qc"));
        assertEquals(1,types.stream().filter("meta_analysis"::equals).count());
        analysis.put("goals",List.of("association")); StudyPublicEvidence.write(path,study);
        var explicit = org.jlinalg.study.StudyPlanner.compile(org.jlinalg.study.StudyResolver.resolve(path));
        assertTrue(StudyPublicEvidence.list(explicit.document().get("operations")).stream().map(StudyPublicEvidence::map).anyMatch(row -> "association".equals(row.get("type"))));
    }

    @Test void missingSuppliedFileWaitsWithoutBlockingIndependentImport() throws Exception {
        Files.writeString(project.resolve("go.tsv"),"term_id\tp_value\nGO:1\t.1\n");
        var meta=definition("meta_analysis",Map.of("id","meta-family","coverage","complete","tested_count",1,"adjustment","BH"));
        var go=definition("gene_ontology",Map.of("id","go-family","coverage","complete","tested_count",1,"adjustment","BH"));
        go.put("id","go");go.put("files",Map.of("results","go.tsv"));
        var study=Map.of("schema_version",1,"project",Map.of("id","partial-import","title","Partial import","description","Synthetic missing input fixture."),
            "resources",Map.of("directory","fixture-resources","network","offline","downloads","deny"),"datasets",List.of(),
            "hypotheses",List.of(Map.of("id","H","description","Retain unavailable sources.","analysis",Map.of("mode","manual","goals",List.of("meta_analysis","gene_ontology"),"supplied_results",List.of(meta,go)))));
        Path path=project.resolve("study.yaml");StudyPublicEvidence.write(path,study);
        assertTrue(org.jlinalg.study.StudyValidator.validate(path,true).valid());
        var errors=new ByteArrayOutputStream();
        JLinAlgCli.run(new String[]{"study","auto",path.toString(),"--offline","--actor-type","external","--actor-id","synthetic-protocol-fixture"},new PrintStream(new ByteArrayOutputStream()),new PrintStream(errors));
        var states=new LinkedHashMap<String,Object>();
        try(var paths=Files.walk(project.resolve("runs"))) {
            for(Path file:paths.filter(item -> item.getFileName().toString().equals("manifest.yaml")).toList()) {
                var manifest=StudyYaml.loadArtifact(file);states.put(String.valueOf(manifest.get("operation_type")),manifest.get("state"));
            }
        }
        assertEquals("waiting_for_input",states.get("meta_analysis"),errors.toString());
        assertEquals("complete",states.get("gene_ontology"),errors.toString());
    }
}
