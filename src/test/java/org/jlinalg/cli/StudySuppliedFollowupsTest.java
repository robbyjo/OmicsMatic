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
import java.util.Set;
import org.jlinalg.study.StudyContract;
import org.jlinalg.study.StudyPlanner;
import org.jlinalg.study.StudyResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudySuppliedFollowupsTest {
    @TempDir Path root;
    private Map<String,Object> source(String kind, Map<String,Object> scope) {
        return new LinkedHashMap<>(Map.of("id","external", "kind",kind,
            "files",Map.of("results","meta.tsv"), "scope",scope,
            "method",Map.of("name","Synthetic external analysis"),
            "family",Map.of("id","all-features","coverage","complete","tested_count",4,"adjustment","BH")));
    }
    private Map<String,Object> analysis(Map<String,Object> source) {
        return new LinkedHashMap<>(Map.of("mode","auto","supplied_results",List.of(source)));
    }
    private Path write(Map<String,Object> analysis) throws Exception {
        Path path=root.resolve("study.json");
        StudyPublicEvidence.write(path,Map.of("schema_version",1,
            "project",Map.of("id","auto-supplied","title","Supplied result completion", "description","Synthetic fixture", "objective","exploration"),
            "resources",Map.of("directory","fixture-resources","network","offline","downloads","deny"),
            "datasets",List.of(),"hypotheses",List.of(Map.of("id","H","description","Complete missing follow-ups", "analysis",analysis))));
        return path;
    }
    private StudyContract resolve(Map<String,Object> a) throws Exception { return StudyResolver.resolve(write(a)); }
    private static List<String> goals(StudyContract study) {
        return StudyPublicEvidence.list(StudyPublicEvidence.map(StudyPublicEvidence.map(StudyPublicEvidence.list(study.document().get("hypotheses")).get(0)).get("analysis")).get("goals")).stream().map(Object::toString).toList();
    }
    private static Map<String,Object> receipt(StudyContract study) {
        return StudyPublicEvidence.map(StudyPublicEvidence.map(StudyPublicEvidence.map(study.document().get("_resolution")).get("followup_applicability")).get("H"));
    }
    private static List<Map<String,Object>> operations(StudyContract study) {
        return StudyPublicEvidence.list(StudyPlanner.compile(study).document().get("operations")).stream().map(StudyPublicEvidence::map).toList();
    }
    @Test void suppliedGwasMetaInfersEnrichmentAndDiagnosticsWithoutSourceDatasets() throws Exception {
        var study=resolve(analysis(source("meta_analysis",Map.of("modality","gwas","feature_id_type","variant"))));
        assertTrue(goals(study).containsAll(List.of("gwas_enrichment","gwas_diagnostics")));
        assertEquals(goals(study).size(),Set.copyOf(goals(study)).size());
        var ops=operations(study);String meta=ops.stream().filter(o->"meta_analysis".equals(o.get("type"))).findFirst().orElseThrow().get("id").toString();
        assertEquals("external",receipt(study).get("source_id")); assertEquals(meta,receipt(study).get("operation_id"));
        assertTrue(ops.stream().filter(o->Set.of("gwas_enrichment","gwas_diagnostics").contains(o.get("type"))).allMatch(o->StudyPublicEvidence.list(o.get("depends_on")).contains(meta)));
        assertTrue(ops.stream().noneMatch(o->Set.of("association","prefit_qc","rare_variant_scores").contains(o.get("type"))));
    }
    @Test void legacyDeclaredGwasWithoutNamespaceStillInfersVariantFollowups() throws Exception {
        assertTrue(goals(resolve(analysis(source("meta_analysis",Map.of("modality","genomics"))))).contains("gwas_enrichment"));
    }
    @Test void suppliedMolecularMetaInfersBothEnrichmentRoutesAndPathwayModules() throws Exception {
        var study=resolve(analysis(source("meta_analysis",Map.of("modality","expression","species","human"))));
        assertTrue(goals(study).containsAll(List.of("gene_ontology","pathway_enrichment","ranked_gsea","pathway_network","network_modules","module_gene_ontology")));
        var ops=operations(study);var gsea=ops.stream().filter(o->"ranked_gsea".equals(o.get("type"))).findFirst().orElseThrow();
        assertEquals(1,ops.stream().filter(o->"ranked_gsea".equals(o.get("type"))).count());
        assertTrue(StudyPublicEvidence.list(gsea.get("depends_on")).contains(receipt(study).get("operation_id")));
        assertTrue(ops.stream().noneMatch(o->"network_analysis".equals(o.get("type"))),"Pooled statistics cannot supply participant correlations");
    }
    @Test void proteinPrimaryInfersSequenceContextButNotRiskScoreConstruction() throws Exception {
        var study=resolve(analysis(source("meta_analysis",Map.of("modality","proteomics","feature_id_type","uniprot_accession"))));
        assertTrue(goals(study).contains("protein_structure")); assertFalse(goals(study).contains("risk_score_evaluation"));
        assertEquals(true,receipt(study).get("protein_structure_applicable"));
    }
    @Test void namespaceAloneCanDeclareGenesOrProteinsWithoutGuessingFromNames() throws Exception {
        assertTrue(goals(resolve(analysis(source("meta_analysis",Map.of("feature_id_type","gene_symbol"))))).contains("ranked_gsea"));
        assertTrue(goals(resolve(analysis(source("meta_analysis",Map.of("feature_id_type","uniprot_isoform"))))).contains("protein_structure"));
        var unknown=analysis(source("meta_analysis",Map.of("assay","GWAS protein expression")));
        assertFalse(goals(resolve(unknown)).stream().anyMatch(Set.of("ranked_gsea","protein_structure","gwas_enrichment")::contains));
    }
    @Test void geneLevelGwasGetsGeneFollowupsAndNeverSnpEnrichment() throws Exception {
        var study=resolve(analysis(source("meta_analysis",Map.of("modality","gwas","feature_id_type","ensembl_gene"))));
        assertTrue(goals(study).containsAll(List.of("gene_ontology","ranked_gsea")));
        assertFalse(goals(study).contains("gwas_enrichment")); assertFalse(goals(study).contains("gwas_diagnostics"));
    }
    @Test void explicitUnknownGwasNamespaceIsNotSilentlyTreatedAsVariants() throws Exception {
        assertFalse(goals(resolve(analysis(source("meta_analysis",Map.of("modality","gwas","feature_id_type","custom_locus_set"))))).contains("gwas_enrichment"));
    }
    @Test void sensitivityAndDiscoveryOnlyPoolsCannotOwnOfficialFollowups() throws Exception {
        var sensitivity=source("meta_analysis",Map.of("modality","proteomics")); sensitivity.put("role","sensitivity");
        assertFalse(goals(resolve(analysis(sensitivity))).contains("protein_structure"));
        assertFalse(goals(resolve(analysis(source("discovery_meta_analysis",Map.of("modality","gwas"))))).contains("gwas_enrichment"));
    }
    @Test void explicitAssociationAndDifferentOperationKeepTheirPrimaryIdentity() throws Exception {
        var a=analysis(source("meta_analysis",Map.of("modality","gwas")));
        a.put("primary_result",Map.of("kind","association","rationale","Prespecified cohort analysis"));
        assertFalse(goals(resolve(a)).contains("gwas_enrichment"));
        a.put("primary_result",Map.of("operation_id","H-op-999-meta_analysis","rationale","Other registered pool"));
        assertFalse(goals(resolve(a)).contains("gwas_enrichment"));
    }
    @Test void explicitMatchingOperationUsesActualTopologicalImportIdentity() throws Exception {
        var meta=source("meta_analysis",Map.of("modality","gwas"));
        meta.put("depends_on",List.of("sensitivity"));
        var sensitivity=source("meta_analysis",Map.of("modality","proteomics"));sensitivity.put("id","sensitivity");sensitivity.put("role","sensitivity");
        var a=analysis(meta);a.put("supplied_results",List.of(meta,sensitivity));
        a.put("primary_result",Map.of("kind","meta_analysis","operation_id","H-op-002-meta_analysis","rationale","Registered external primary"));
        var study=resolve(a);assertTrue(goals(study).contains("gwas_enrichment"));assertEquals("H-op-002-meta_analysis",receipt(study).get("operation_id"));
    }
    @Test void disabledPoliciesAndExplicitManualGoalsRemainRespected() throws Exception {
        var a=analysis(source("meta_analysis",Map.of("modality","proteomics")));
        a.put("gsea",Map.of("enabled",false));a.put("protein_structure",Map.of("enabled",false));a.put("network",Map.of("enabled",false));
        assertFalse(goals(resolve(a)).stream().anyMatch(Set.of("ranked_gsea","protein_structure","pathway_network")::contains));
        a=analysis(source("meta_analysis",Map.of("modality","gwas")));a.put("gwas_enrichment",Map.of("enabled",false));a.put("gwas_diagnostics",Map.of("enabled",false));
        assertFalse(goals(resolve(a)).stream().anyMatch(Set.of("gwas_enrichment","gwas_diagnostics")::contains));
        a.put("mode","manual");a.put("goals",List.of("finding_inventory"));var manual=resolve(a);
        assertEquals(List.of("finding_inventory"),goals(manual));assertEquals(false,receipt(manual).get("automatic_goal_merge"));
    }
    @Test void suppliedCompletedEnrichmentIsReusedWithoutNativeReplacement() throws Exception {
        var a=analysis(source("meta_analysis",Map.of("modality","gwas")));
        var enrichment=source("gwas_enrichment",Map.of("modality","gwas"));enrichment.put("id","completed-enrichment");a.put("supplied_results",List.of(StudyPublicEvidence.list(a.get("supplied_results")).get(0),enrichment));
        var ops=operations(resolve(a)).stream().filter(o->"gwas_enrichment".equals(o.get("type"))).toList();
        assertEquals(1,ops.size());assertEquals("completed-enrichment",ops.get(0).get("supplied_result_id"));
    }
    @Test void completedMolecularStagesEachRetainTheirSuppliedOwner() throws Exception {
        var a=analysis(source("meta_analysis",Map.of("modality","proteomics")));var definitions=new ArrayList<Object>(StudyPublicEvidence.list(a.get("supplied_results")));
        var kinds=List.of("ranked_gsea","gene_ontology","pathway_enrichment","network_modules","module_gene_ontology","protein_structure");
        for(String kind:kinds){var d=source(kind,Map.of("modality","proteomics"));d.put("id","external-"+kind);definitions.add(d);}a.put("supplied_results",definitions);
        var ops=operations(resolve(a));for(String kind:kinds){var owners=ops.stream().filter(o->kind.equals(o.get("type"))).toList();assertEquals(1,owners.size(),kind);assertEquals("external-"+kind,owners.get(0).get("supplied_result_id"));}
    }
    @Test void metadataReferenceCannotConvertAGeneLevelPoolIntoVariantStatistics() throws Exception {
        var d=source("meta_analysis",Map.of("modality","gwas","feature_id_type","gene_symbol","cohorts",List.of("A")));
        Path path=write(analysis(d));var document=StudyYaml.loadArtifact(path);
        document.put("datasets",List.of(Map.of("id","A","cohort","A","type","gwas","stage","analysis_reference","supplied_result_id","external","files",Map.of())));
        StudyPublicEvidence.write(path,document);var study=StudyResolver.resolve(path);
        assertFalse(goals(study).contains("gwas_enrichment"));assertFalse(goals(study).contains("gwas_diagnostics"));
    }
    @Test void ownedMetadataReferenceCanSupplyTheMissingModality() throws Exception {
        var d=source("meta_analysis",Map.of("cohorts",List.of("A")));Path path=write(analysis(d));var document=StudyYaml.loadArtifact(path);
        document.put("datasets",List.of(Map.of("id","A","cohort","A","type","gwas","stage","analysis_reference","supplied_result_id","external","files",Map.of())));
        StudyPublicEvidence.write(path,document);var study=StudyResolver.resolve(path);
        assertTrue(goals(study).containsAll(List.of("gwas_enrichment","gwas_diagnostics")));
        assertEquals("registered_analysis_references",receipt(study).get("modality_origin"));
        assertEquals("gwas",receipt(study).get("modality"));
    }
    @Test void partialPrimarySchedulesVisiblePrerequisitesWithoutDeclaringItComplete() throws Exception {
        var d=source("meta_analysis",Map.of("modality","expression"));d.put("reported_status","partial");var study=resolve(analysis(d));
        assertTrue(goals(study).contains("ranked_gsea"));assertEquals("partial",receipt(study).get("reported_status"));assertEquals(true,receipt(study).get("runtime_primary_validation_required"));
    }
    @Test void automaticOfflineRunCompletesGseaFromTheMappedSuppliedPool() throws Exception {
        Files.writeString(root.resolve("meta.tsv"),"feature_id\tbeta\tse\tp_value\nA\t4\t1\t.2\nB\t3\t1\t.3\nC\t-2\t1\t.6\nD\t-1\t1\t.8\n");
        Files.writeString(root.resolve("sets.gmt"),"top\tTop\tA\tB\nbottom\tBottom\tC\tD\n");
        var a=analysis(source("meta_analysis",Map.of("modality","expression","feature_id_type","gene_symbol","species","human")));
        Files.writeString(root.resolve("annotation.tsv"),"feature_id\tgene_symbol\nA\tA\nB\tB\nC\tC\nD\tD\n");
        var mapped=source("meta_analysis",Map.of("modality","expression","feature_id_type","gene_symbol","species","human"));
        mapped.put("files",Map.of("results","meta.tsv","feature_annotation","annotation.tsv"));a.put("supplied_results",List.of(mapped));
        a.put("gsea",Map.of("gene_sets","sets.gmt","species","human","minimum_size",2,"maximum_size",2,"gene_id_type","symbol"));
        var errors=new ByteArrayOutputStream();int exit=JLinAlgCli.run(new String[]{"study","auto",write(a).toString(),"--offline"},new PrintStream(new ByteArrayOutputStream()),new PrintStream(errors));
        assertEquals(0,exit,errors.toString());
        var status=StudyYaml.loadArtifact(root.resolve("state/project-status.yaml"));
        try(var files=Files.walk(root.resolve("runs").resolve(status.get("run_id").toString()).resolve("operations"))) {
            var manifests=new ArrayList<Map<String,Object>>();
            for(Path file:files.filter(p->p.getFileName().toString().equals("manifest.yaml")).toList())manifests.add(StudyYaml.loadArtifact(file));
            var gsea=manifests.stream().filter(m->"ranked_gsea".equals(m.get("operation_type"))).findFirst().orElseThrow();
            assertEquals("complete",gsea.get("state"),gsea.toString());
            assertEquals(4,StudyPublicEvidence.map(gsea.get("details")).get("ranked_genes"));
            assertTrue(manifests.stream().noneMatch(m->"association".equals(m.get("operation_type"))));
        }
    }
    @Test void automaticSourceOnlyRunRetainsMissingMappingsAsPrerequisitesWithoutCrashing() throws Exception {
        Files.writeString(root.resolve("meta.tsv"),"feature_id\tbeta\tse\tp_value\nA\t4\t1\t.2\nB\t3\t1\t.3\nC\t-2\t1\t.6\nD\t-1\t1\t.8\n");
        var a=analysis(source("meta_analysis",Map.of("modality","expression","species","human")));
        var out=new ByteArrayOutputStream();var errors=new ByteArrayOutputStream();
        assertEquals(0,JLinAlgCli.run(new String[]{"study","auto",write(a).toString(),"--offline"},new PrintStream(out),new PrintStream(errors)),out+"\n"+errors);
        var status=StudyYaml.loadArtifact(root.resolve("state/project-status.yaml"));
        try(var files=Files.walk(root.resolve("runs").resolve(status.get("run_id").toString()).resolve("operations"))) {
            var manifests=new ArrayList<Map<String,Object>>();for(Path file:files.filter(p->p.getFileName().toString().equals("manifest.yaml")).toList())manifests.add(StudyYaml.loadArtifact(file));
            assertTrue(manifests.stream().noneMatch(m->"failed".equals(m.get("state"))));
            assertEquals("waiting_for_input",manifests.stream().filter(m->"ranked_gsea".equals(m.get("operation_type"))).findFirst().orElseThrow().get("state"));
        }
    }
    @Test void autoProteinPrimaryProducesAnExactSequencePrerequisiteRatherThanSkippingStructure() throws Exception {
        Files.writeString(root.resolve("meta.tsv"),"feature_id\tbeta\tse\tp_value\nP12345\t4\t1\t.00001\nQ12345\t3\t1\t.00002\nO12345\t-2\t1\t.00003\nP23456\t-1\t1\t.00004\n");
        var a=analysis(source("meta_analysis",Map.of("modality","proteomics","feature_id_type","uniprot_accession","species","human")));
        var out=new ByteArrayOutputStream();var errors=new ByteArrayOutputStream();
        assertEquals(0,JLinAlgCli.run(new String[]{"study","auto",write(a).toString(),"--offline"},new PrintStream(out),new PrintStream(errors)),out+"\n"+errors);
        var status=StudyYaml.loadArtifact(root.resolve("state/project-status.yaml"));
        try(var files=Files.walk(root.resolve("runs").resolve(status.get("run_id").toString()).resolve("operations"))) {
            var manifests=new ArrayList<Map<String,Object>>();for(Path file:files.filter(p->p.getFileName().toString().equals("manifest.yaml")).toList())manifests.add(StudyYaml.loadArtifact(file));
            var protein=manifests.stream().filter(m->"protein_structure".equals(m.get("operation_type"))).findFirst().orElseThrow();assertEquals("waiting_for_input",protein.get("state"),protein.toString());
            assertTrue(StudyPublicEvidence.map(protein.get("details")).containsKey("needs"));assertTrue(manifests.stream().noneMatch(m->"failed".equals(m.get("state"))));
        }
    }
}
