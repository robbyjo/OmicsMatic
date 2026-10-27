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
import org.jlinalg.study.*;
import org.jlinalg.study.StudyOperationExecutor.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StudyPartialSuppliedTest {
    @TempDir Path root;
    private Map<String,Object> definition(String kind) {
        return new LinkedHashMap<>(Map.of("id","unfinished", "kind",kind, "reported_status","partial",
            "files",kind.equals("network_analysis")?Map.of("nodes","nodes.tsv","edges","edges.tsv"):Map.of("results","results.tsv"),
            "scope",Map.of(), "method",Map.of("name","External incomplete synthetic analysis")));
    }
    private StudyContract resolve(Map<String,Object> analysis) throws Exception {
        var document=Map.<String,Object>of("schema_version",1,"project",Map.of("id","partial-supplied","title","Partial external analysis","description","Synthetic partial import completion fixture","objective","exploration"),
            "resources",Map.of("directory","resources","network","offline","downloads","deny"),"datasets",List.of(),
            "hypotheses",List.of(Map.of("id","H","description","Complete missing work","analysis",analysis)));
        Path path=root.resolve("study.json");write(path,document);return StudyResolver.resolve(path);
    }
    private List<Map<String,Object>> operations(StudyContract study) {
        return list(StudyPlanner.compile(study).document().get("operations")).stream().map(StudyPublicEvidence::map).toList();
    }
    private Map<String,Object> analysis(StudyContract study) {
        return map(map(list(study.document().get("hypotheses")).get(0)).get("analysis"));
    }
    @ParameterizedTest @ValueSource(strings={"gene_ontology","pathway_enrichment","ranked_gsea","gwas_enrichment","gwas_diagnostics","variant_annotation","variant_scoring","protein_structure","risk_score_evaluation","risk_score_related_phenotypes","network_analysis","network_modules","module_gene_ontology","mendelian_randomization","colocalization","fine_mapping"})
    void automaticPartialImportsNeverSuppressNativeCompletion(String kind) throws Exception {
        var study=resolve(new LinkedHashMap<>(Map.of("mode","auto","supplied_results",List.of(definition(kind)))));
        var ops=operations(study);var imported=ops.stream().filter(o->o.containsKey("supplied_result_id")).toList();
        assertEquals(1,imported.size());assertEquals("supplied_partial",imported.get(0).get("type"));
        assertTrue(list(analysis(study).get("goals")).contains(kind));
        assertTrue(ops.stream().anyMatch(o->kind.equals(o.get("type"))&&!o.containsKey("supplied_result_id")),kind);
    }
    @Test void completedSourceStillOwnsTheStageWithoutNativeRerun() throws Exception {
        var d=definition("variant_scoring");d.put("reported_status","complete");
        var ops=operations(resolve(new LinkedHashMap<>(Map.of("mode","auto","goals",List.of("variant_scoring"),"supplied_results",List.of(d)))));
        var scores=ops.stream().filter(o->"variant_scoring".equals(o.get("type"))).toList();
        assertEquals(1,scores.size());assertEquals("unfinished",scores.get(0).get("supplied_result_id"));
    }
    @Test void manualGoalsRetainPartialEvidenceWithoutAddingUnrequestedAnalysis() throws Exception {
        var study=resolve(new LinkedHashMap<>(Map.of("mode","manual","goals",List.of("finding_inventory"),"supplied_results",List.of(definition("protein_structure")))));
        assertEquals(List.of("finding_inventory"),analysis(study).get("goals"));
        assertFalse(operations(study).stream().anyMatch(o->"protein_structure".equals(o.get("type"))));
    }
    @Test void disabledStagePolicyDoesNotBecomeAnAutomaticRequest() throws Exception {
        var study=resolve(new LinkedHashMap<>(Map.of("mode","auto","protein_structure",Map.of("enabled",false),"supplied_results",List.of(definition("protein_structure")))));
        assertFalse(list(analysis(study).get("goals")).contains("protein_structure"));
        assertFalse(operations(study).stream().anyMatch(o->"protein_structure".equals(o.get("type"))));
    }
    @Test void partialNetworkClaimsNoDatasetCoverageAndCanOverlapACompletedSource() {
        var partial=definition("network_analysis");partial.put("scope",Map.of("datasets",List.of("A")));
        var complete=new LinkedHashMap<>(partial);complete.put("id","finished");complete.put("reported_status","complete");
        var a=Map.<String,Object>of("supplied_results",List.of(partial,complete));
        assertEquals(2,StudySuppliedResultPolicy.resolve(a).size());
        var coverage=StudyNetworkReuse.coverage(a,Map.of("A",Map.of("id","A")),List.of("A"));
        assertEquals(java.util.Set.of("finished"),coverage.sources().keySet());
        var partialOnly=StudyNetworkReuse.coverage(Map.of("supplied_results",List.of(partial)),Map.of("A",Map.of("id","A")),List.of("A"));
        assertTrue(partialOnly.covered().isEmpty());assertTrue(partialOnly.sources().isEmpty());
    }
    @Test void partialPoolWithoutSourceEstimatesHasAnExplicitCompletionNeed() throws Exception {
        Files.writeString(root.resolve("results.tsv"),"feature_id\tbeta\tse\tp_value\nA\t.1\t.2\t.6\n");
        var d=definition("meta_analysis");d.put("family",Map.of("id","partial-family","coverage","complete","tested_count",1,"adjustment","BH"));
        var study=resolve(new LinkedHashMap<>(Map.of("mode","auto","supplied_results",List.of(d))));
        var plan=StudyPlanner.compile(study).document();var op=list(plan.get("operations")).stream().map(StudyPublicEvidence::map).filter(o->o.containsKey("supplied_result_id")).findFirst().orElseThrow();
        Path out=Files.createDirectory(root.resolve("import"));var result=new StudySuppliedResults().execute(new Context(root,out,op,study.document(),plan,Map.of()));
        assertEquals(State.WAITING_FOR_INPUT,result.state());assertEquals(false,result.details().get("primary_selection_eligible"));
        assertTrue(result.details().get("needs").toString().contains("No native completion can be scheduled"));
        assertTrue(Files.exists(out.resolve("supplied-results-limited.tsv")));assertFalse(Files.exists(out.resolve("meta-analysis.tsv")));
    }
    @Test void partialNetworkDoesNotExportCanonicalGraphOrModuleArtifacts() throws Exception {
        Files.writeString(root.resolve("nodes.tsv"),"node_id\ng1\ng2\n");Files.writeString(root.resolve("edges.tsv"),"source\ttarget\tcorrelation\ng1\tg2\t.8\n");
        var study=resolve(new LinkedHashMap<>(Map.of("mode","auto","supplied_results",List.of(definition("network_analysis")))));
        var plan=StudyPlanner.compile(study).document();var op=list(plan.get("operations")).stream().map(StudyPublicEvidence::map).filter(o->o.containsKey("supplied_result_id")).findFirst().orElseThrow();
        Path out=Files.createDirectory(root.resolve("graph"));var result=new StudySuppliedResults().execute(new Context(root,out,op,study.document(),plan,Map.of()));
        assertEquals(State.WAITING_FOR_INPUT,result.state());assertEquals(false,result.details().get("replaces_native_stage"));
        assertTrue(Files.exists(out.resolve("supplied-partial-network-edges.tsv")));assertFalse(Files.exists(out.resolve("network-edges.tsv")));
        assertFalse(Files.exists(out.resolve("network-analysis.yaml")));assertEquals(1,list(result.details().get("native_completion_operations")).size());
    }
    @Test void partialEnrichmentDoesNotRecomputeCorrectionFromItsAvailableRows() throws Exception {
        Files.writeString(root.resolve("results.tsv"),"term_id\tp_value\tadjusted_p\nGO:1\t.001\t.4\n");
        var d=definition("gene_ontology");d.put("family",Map.of("id","source-family","coverage","complete","tested_count",1000,"adjustment","BH"));
        var study=resolve(new LinkedHashMap<>(Map.of("mode","auto","supplied_results",List.of(d))));
        var plan=StudyPlanner.compile(study).document();var op=list(plan.get("operations")).stream().map(StudyPublicEvidence::map).filter(o->o.containsKey("supplied_result_id")).findFirst().orElseThrow();
        Path out=Files.createDirectory(root.resolve("enrichment"));var result=new StudySuppliedResults().execute(new Context(root,out,op,study.document(),plan,Map.of()));
        assertEquals(State.COMPLETE,result.state());assertTrue(list(result.details().get("needs")).isEmpty());
        var t=DelimitedData.readResult(out.resolve("supplied-results-limited.tsv"));assertEquals(.4,Double.parseDouble(t.rows().get(0)[t.column("adjusted_p")]));
        assertEquals(1000,map(result.details().get("family")).get("tested_count"));assertFalse(Files.exists(out.resolve("gene-ontology.tsv")));
    }
    @ParameterizedTest @ValueSource(strings={"A","B"})
    void nativeGraphCompletionMustMatchThePartialSourcesPopulation(String nativeDataset) throws Exception {
        Files.writeString(root.resolve("nodes.tsv"),"node_id\ng1\ng2\n");Files.writeString(root.resolve("edges.tsv"),"source\ttarget\tweight\ng1\tg2\t.8\n");
        var d=definition("network_analysis");d.put("scope",Map.of("datasets",List.of("A")));
        var analysis=new LinkedHashMap<String,Object>(Map.of("goals",List.of("network_analysis"),"supplied_results",List.of(d)));
        var document=Map.<String,Object>of("datasets",List.of(Map.of("id","A","cohort","A","type","expression","stage","analysis_reference"),Map.of("id","B","cohort","B","type","expression","stage","processed_matrix")),
            "hypotheses",List.of(Map.of("id","H","inputs",Map.of("discovery",List.of("A","B")),"analysis",analysis)));
        var imported=Map.<String,Object>of("id","imported","type","supplied_partial","hypothesis","H","supplied_result_id","unfinished");
        var nativeOp=Map.<String,Object>of("id","native","type","network_analysis","hypothesis","H","dataset_id",nativeDataset);
        Path out=Files.createDirectory(root.resolve("population-"+nativeDataset));var result=new StudySuppliedResults().execute(new Context(root,out,imported,document,Map.of("operations",List.of(imported,nativeOp)),Map.of()));
        assertEquals(nativeDataset.equals("A")?State.COMPLETE:State.WAITING_FOR_INPUT,result.state(),result.details().toString());
        assertEquals(List.of("A"),result.details().get("partial_network_requested_datasets"));
        assertEquals(nativeDataset.equals("A")?List.of():List.of("A"),result.details().get("partial_network_unscheduled_datasets"));
        assertFalse(Files.exists(out.resolve("network-edges.tsv")));
    }
}
