/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyOperationExecutor.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudySuppliedScopeTest {
    @TempDir Path root;

    @Test void officialMetaWithConflictingPhenotypeIsRetainedButCannotBecomeCanonical() throws Exception {
        Files.writeString(root.resolve("results.tsv"),"feature_id\tbeta\tse\tp_value\ng1\t.4\t.1\t.001\n");
        var supplied=definition("meta_analysis","other_trait",Map.of("results","results.tsv"));
        var study=study(List.of(),List.of(supplied),List.of("meta_analysis"));
        Path output=Files.createDirectory(root.resolve("import"));
        var result=new StudyCliExecutor().execute(new Context(root,output,Map.of("type","meta_analysis","hypothesis","H","supplied_result_id","external"),study,Map.of(),Map.of()));
        assertEquals(State.WAITING_FOR_INPUT,result.state());
        assertTrue(Files.exists(output.resolve("supplied/results.tsv")));
        assertFalse(Files.exists(output.resolve("meta-analysis.tsv")));
    }

    @Test void suppliedNetworkAStillPlansMissingNativeNetworkB() throws Exception {
        var supplied=definition("network_analysis","index_trait",Map.of("nodes","nodes.tsv","edges","edges.tsv"));
        supplied.put("scope",Map.of("phenotype","index_trait","datasets",List.of("a"),"cohorts",List.of("A")));
        var datasets=new ArrayList<Object>();
        for(String id:List.of("a","b"))datasets.add(Map.of("id",id,"cohort",id.toUpperCase(java.util.Locale.ROOT),"type","expression","stage","processed_matrix","files",Map.of("matrix",id+".tsv","phenotype","pheno.tsv")));
        Path path=root.resolve("study.yaml");StudyPublicEvidence.write(path,study(datasets,List.of(supplied),List.of("network_modules")));
        var plan=org.jlinalg.study.StudyPlanner.compile(org.jlinalg.study.StudyResolver.resolve(path));
        var nativeNetworks=StudyPublicEvidence.list(plan.document().get("operations")).stream().map(StudyPublicEvidence::map)
            .filter(op->"network_analysis".equals(op.get("type"))&&!op.containsKey("supplied_result_id")).toList();
        assertEquals(1,nativeNetworks.size());
        assertEquals("b",nativeNetworks.get(0).get("dataset_id"));
        assertEquals(2,StudyPublicEvidence.list(plan.document().get("operations")).stream().map(StudyPublicEvidence::map).filter(op->"network_modules".equals(op.get("type"))).count());
    }

    @Test void knownTraitConflictsHoldAcrossSuppliedStatisticalAndNetworkKinds() throws Exception {
        Files.writeString(root.resolve("results.tsv"),"feature_id\tbeta\tse\tp_value\ng1\t.4\t.1\t.001\n");
        for(String kind:List.of("gene_ontology","pathway_enrichment","ranked_gsea","gwas_enrichment","mendelian_randomization","colocalization","fine_mapping","rare_variant_analysis","risk_score_evaluation","subgroup_interactions","discovery_replication","effect_comparison","cohort_overlap","heterogeneity_review")) {
            var d=definition(kind,"other_trait",Map.of("results","results.tsv"));var c=importContext(kind,study(List.of(),List.of(d),List.of()),"external");
            var result=new StudyCliExecutor().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state(),kind);
            assertFalse(result.details().containsKey("canonical_results"),kind);
            assertEquals("incompatible_or_unresolved",StudyPublicEvidence.map(result.details().get("scope_validation")).get("status"));
        }
    }

    @Test void incompleteContextIsRecordedWithoutInventingAConflict() throws Exception {
        Files.writeString(root.resolve("results.tsv"),"feature_id\tbeta\tse\tp_value\ng1\t.4\t.1\t.001\n");
        var d=definition("meta_analysis","index_trait",Map.of("results","results.tsv"));d.put("scope",Map.of());
        var result=new StudyCliExecutor().execute(importContext("unknown-context",study(List.of(),List.of(d),List.of()),"external"));
        assertEquals(State.COMPLETE,result.state());
        assertEquals("partially_declared_not_certified",StudyPublicEvidence.map(result.details().get("scope_validation")).get("status"));
    }

    @Test void rowBuildMustAgreeWithSourceScopeAndAliasesRemainCompatible() throws Exception {
        Files.writeString(root.resolve("results.tsv"),"feature_id\tbeta\tse\tp_value\tgenome_build\ng1\t.4\t.1\t.001\tGRCh37\n");
        for(String build:List.of("GRCh38","hg19")) {
            var d=definition("meta_analysis","index_trait",Map.of("results","results.tsv"));d.put("scope",Map.of("phenotype","index_trait","genome_build",build));
            var c=importContext(build,study(List.of(),List.of(d),List.of()),"external");var result=new StudyCliExecutor().execute(c);
            assertEquals(build.equals("hg19")?State.COMPLETE:State.WAITING_FOR_INPUT,result.state());
            assertEquals(build.equals("hg19"),Files.exists(c.outputDirectory().resolve("meta-analysis.tsv")));
            if(build.equals("GRCh38"))assertTrue(Files.exists(c.outputDirectory().resolve("supplied-results-scope-limited.tsv")));
        }
    }

    @Test void mainMetaRequiresMatchingPopulationWhileSensitivityCanUseASubset() throws Exception {
        Files.writeString(root.resolve("results.tsv"),"feature_id\tbeta\tse\tp_value\ng1\t.4\t.1\t.001\n");
        var datasets=List.<Object>of(Map.of("id","a","cohort","A","type","generic","files",Map.of()),Map.of("id","b","cohort","B","type","generic","files",Map.of()));
        for(String role:List.of("primary","sensitivity")) {
            var d=definition("meta_analysis","index_trait",Map.of("results","results.tsv"));d.put("role",role);d.put("scope",Map.of("phenotype","index_trait","datasets",List.of("a"),"cohorts",List.of("A")));
            var c=importContext(role,study(datasets,List.of(d),List.of()),"external");var result=new StudyCliExecutor().execute(c);
            assertEquals(role.equals("primary")?State.WAITING_FOR_INPUT:State.COMPLETE,result.state());
            assertFalse(Files.exists(c.outputDirectory().resolve("meta-analysis.tsv")));
            if(role.equals("sensitivity"))assertTrue(Files.exists(c.outputDirectory().resolve("supplied-sensitivity.tsv")));
        }
    }

    @Test void aKnownInvalidDependencyHoldsButAnUnavailableConsistentParentDoesNotRefit() throws Exception {
        Files.writeString(root.resolve("go.tsv"),"term_id\tp_value\nGO:1\t.01\n");
        for(String trait:List.of("index_trait","other_trait")) {
            var parent=definition("meta_analysis",trait,Map.of("results","missing.tsv"));
            var go=definition("gene_ontology","index_trait",Map.of("results","go.tsv"));go.put("id","go");go.put("depends_on",List.of("external"));
            var c=importContext("dependency-"+trait,study(List.of(),List.of(parent,go),List.of()),"go");var result=new StudyCliExecutor().execute(c);
            assertEquals(trait.equals("index_trait")?State.COMPLETE:State.WAITING_FOR_INPUT,result.state());
            assertEquals(trait.equals("index_trait"),Files.exists(c.outputDirectory().resolve("gene-ontology.tsv")));
        }
    }

    @Test void externalMrPopulationIsNotMistakenForTheAssociationCohort() throws Exception {
        Files.writeString(root.resolve("results.tsv"),"feature_id\tbeta\tse\tp_value\ng1\t.4\t.1\t.001\n");
        var d=definition("mendelian_randomization","index_trait",Map.of("results","results.tsv"));d.put("scope",Map.of("outcome","index_trait","exposure","molecular_trait","cohorts",List.of("EXTERNAL_GWAS")));
        var result=new StudyCliExecutor().execute(importContext("external-mr",study(List.of(Map.of("id","a","cohort","A","type","generic","files",Map.of())),List.of(d),List.of()),"external"));
        assertEquals(State.COMPLETE,result.state(),result.summary());
    }

    @Test void disjointSuppliedGraphsAndAReferenceLeaveOnlyBForNativeCompletion() throws Exception {
        var a=definition("network_analysis","index_trait",Map.of("nodes","a-nodes.tsv","edges","a-edges.tsv"));a.put("id","graph-a");a.put("scope",Map.of("phenotype","index_trait","datasets",List.of("a"),"cohorts",List.of("A")));
        var c=definition("network_analysis","index_trait",Map.of("nodes","c-nodes.tsv","edges","c-edges.tsv"));c.put("id","graph-c");c.put("scope",Map.of("phenotype","index_trait","datasets",List.of("c"),"cohorts",List.of("C")));
        var datasets=List.<Object>of(Map.of("id","a","cohort","A","type","expression","stage","analysis_reference","supplied_result_id","graph-a","files",Map.of()),
            Map.of("id","b","cohort","B","type","expression","stage","processed_matrix","files",Map.of("matrix","matrix.tsv","phenotype","pheno.tsv")),
            Map.of("id","c","cohort","C","type","expression","stage","analysis_reference","supplied_result_id","graph-c","files",Map.of()));
        var document=new LinkedHashMap<>(study(datasets,List.of(a,c),List.of("network_modules")));document.put("cohorts",List.of(Map.of("id","A"),Map.of("id","B"),Map.of("id","C")));
        Path path=root.resolve("multiple.yaml");StudyPublicEvidence.write(path,document);var plan=org.jlinalg.study.StudyPlanner.compile(org.jlinalg.study.StudyResolver.resolve(path));
        var operations=StudyPublicEvidence.list(plan.document().get("operations")).stream().map(StudyPublicEvidence::map).toList();
        assertEquals(List.of("b"),operations.stream().filter(op->op.get("type").equals("network_analysis")&&!op.containsKey("supplied_result_id")).map(op->op.get("dataset_id")).toList());
        assertEquals(3,operations.stream().filter(op->op.get("type").equals("network_modules")).count());
        c.put("scope",a.get("scope"));assertThrows(IllegalArgumentException.class,()->org.jlinalg.study.StudySuppliedResultPolicy.resolve(Map.of("supplied_results",List.of(a,c))));
    }

    @Test void suppliedOnlyModeDoesNotGuessMissingPopulationOrScheduleNativeGraphs() throws Exception {
        var d=definition("network_analysis","index_trait",Map.of("nodes","nodes.tsv","edges","edges.tsv"));
        var datasets=List.<Object>of(Map.of("id","b","cohort","B","type","expression","stage","processed_matrix","files",Map.of("matrix","matrix.tsv","phenotype","pheno.tsv")));
        var document=new LinkedHashMap<>(study(datasets,List.of(d),List.of("network_modules")));var h=new LinkedHashMap<>(StudyPublicEvidence.map(StudyPublicEvidence.list(document.get("hypotheses")).get(0)));
        var analysis=new LinkedHashMap<>(StudyPublicEvidence.map(h.get("analysis")));analysis.put("network",Map.of("complete_missing",false));h.put("analysis",analysis);document.put("hypotheses",List.of(h));
        Path path=root.resolve("supplied-only.yaml");StudyPublicEvidence.write(path,document);var operations=StudyPublicEvidence.list(org.jlinalg.study.StudyPlanner.compile(org.jlinalg.study.StudyResolver.resolve(path)).document().get("operations")).stream().map(StudyPublicEvidence::map).toList();
        assertFalse(operations.stream().anyMatch(op->op.get("type").equals("network_analysis")&&!op.containsKey("supplied_result_id")));
        assertEquals(1,operations.stream().filter(op->op.get("type").equals("network_modules")).count());
    }

    @Test void originalPrivateMetadataIsLocalWhileAutomaticMethodsAndDossiersAreBounded() throws Exception {
        String secret="PRIVATE_SOURCE_SENTINEL";Files.writeString(root.resolve("results.tsv"),"feature_id\tbeta\tse\tp_value\ng1\t.4\t.1\t.001\n");
        var d=definition("meta_analysis","index_trait",Map.of("results","results.tsv"));d.put("method",Map.of("name","External method","parameters",Map.of("private_note",secret)));
        d.put("scope",Map.of("phenotype","index_trait","population",secret));d.put("provenance",Map.of("note",secret));d.put("limitations",List.of(secret));
        var family=new LinkedHashMap<>(StudyPublicEvidence.map(d.get("family")));family.put("rationale",secret);d.put("family",family);
        var c=importContext("private",study(List.of(),List.of(d),List.of()),"external");var imported=new StudyCliExecutor().execute(c);assertEquals(State.COMPLETE,imported.state());
        assertTrue(Files.readString(c.outputDirectory().resolve("supplied-analysis.yaml")).contains(secret));
        assertFalse(StudyAssistance.json(imported.details().get("method_report")).contains(secret));
        assertFalse(String.valueOf(imported.details().get("required_manuscript_caveat")).contains(secret));
        seal(c,imported);
        var inventory=new Context(root,Files.createDirectory(root.resolve("inventory")),Map.of("type","finding_inventory","scope","study","datasets",List.of()),c.study(),Map.of(),Map.of("meta",c.outputDirectory()));
        assertEquals(State.COMPLETE,new StudyCliExecutor().execute(inventory).state());
        assertFalse(Files.readString(inventory.outputDirectory().resolve("finding-inventory.jsonl")).contains(secret));
    }

    @Test void annotationContextAndRegisteredRelatedScoreTraitsDoNotInheritTheIndexTrait() throws Exception {
        Files.writeString(root.resolve("annotation.tsv"),"variant_id\nrs1\n");
        var annotation=definition("variant_annotation","other_trait",Map.of("results","annotation.tsv"));
        assertEquals(State.COMPLETE,new StudyCliExecutor().execute(importContext("annotation",study(List.of(),List.of(annotation),List.of()),"external")).state());
        Files.writeString(root.resolve("related.tsv"),"score_id\tphenotype\tevaluation_population\tbeta\tse\tp_value\ns1\trelated_trait\tindependent_population\t.2\t.1\t.04\n");
        var score=definition("risk_score_related_phenotypes","related_trait",Map.of("results","related.tsv"));
        var document=new LinkedHashMap<>(study(List.of(),List.of(score),List.of()));var h=new LinkedHashMap<>(StudyPublicEvidence.map(StudyPublicEvidence.list(document.get("hypotheses")).get(0)));
        var analysis=new LinkedHashMap<>(StudyPublicEvidence.map(h.get("analysis")));analysis.put("risk_score",Map.of("related_phenotypes",List.of(Map.of("column","related_trait"))));h.put("analysis",analysis);document.put("hypotheses",List.of(h));
        assertEquals(State.COMPLETE,new StudyCliExecutor().execute(importContext("related",document,"external")).state());
        Files.writeString(root.resolve("related.tsv"),Files.readString(root.resolve("related.tsv")).replace("related_trait","undeclared_trait"));
        assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(importContext("undeclared-related",document,"external")).state());
    }

    @Test void aCompletedParentWithConflictingRowsHoldsItsDeclaredDownstreamResult() throws Exception {
        Files.writeString(root.resolve("parent.tsv"),"feature_id\tbeta\tse\tp_value\tgenome_build\ng1\t.4\t.1\t.001\tGRCh37\n");
        Files.writeString(root.resolve("go.tsv"),"term_id\tp_value\nGO:1\t.01\n");
        var parent=definition("meta_analysis","index_trait",Map.of("results","parent.tsv"));parent.put("scope",Map.of("phenotype","index_trait","genome_build","GRCh38"));
        var go=definition("gene_ontology","index_trait",Map.of("results","go.tsv"));go.put("id","go");go.put("depends_on",List.of("external"));
        var document=study(List.of(),List.of(parent,go),List.of());var p=importContext("row-invalid-parent",document,"external");var imported=new StudyCliExecutor().execute(p);
        assertEquals(State.WAITING_FOR_INPUT,imported.state());seal(p,imported);
        var child=importContext("row-invalid-child",document,"go");var context=new Context(child.project(),child.outputDirectory(),child.operation(),child.study(),child.plan(),Map.of("meta",p.outputDirectory()));
        var result=new StudyCliExecutor().execute(context);assertEquals(State.WAITING_FOR_INPUT,result.state());
        assertFalse(Files.exists(child.outputDirectory().resolve("gene-ontology.tsv")));
    }

    private Context importContext(String name,Map<String,Object> study,String id)throws Exception {
        return new Context(root,Files.createDirectory(root.resolve(name)),Map.of("type",id.equals("go")?"gene_ontology":"meta_analysis","hypothesis","H","supplied_result_id",id),study,Map.of(),Map.of());
    }
    private void seal(Context c,org.jlinalg.study.StudyOperationExecutor.Result result)throws Exception {
        var hashes=new LinkedHashMap<String,Object>();try(var files=Files.walk(c.outputDirectory())){for(Path p:files.filter(Files::isRegularFile).toList())hashes.put(c.outputDirectory().relativize(p).toString().replace('\\','/'),FollowupSupport.hash(p));}
        StudyPublicEvidence.write(c.outputDirectory().resolve("manifest.yaml"),Map.of("operation_id","meta","operation_type","meta_analysis","hypothesis","H","state",result.state().value(),"details",result.details(),"output_sha256",hashes));
    }

    private Map<String,Object> definition(String kind,String trait,Map<String,Object> files){
        var result=new LinkedHashMap<String,Object>(Map.of("id","external","kind",kind,"files",files,"scope",Map.of("phenotype",trait),"method",Map.of("name","Synthetic external analysis")));
        if(!kind.equals("network_analysis"))result.put("family",Map.of("id","source-family","coverage","complete","tested_count",1,"adjustment","BH"));
        return result;
    }
    private Map<String,Object> study(List<Object> datasets,List<Object> supplied,List<String> goals){
        return Map.of("schema_version",1,"project",Map.of("id","scope-fixture","title","Scope fixture","description","Synthetic reuse fixture."),"datasets",datasets,
            "cohorts",List.of(Map.of("id","A"),Map.of("id","B")),"cohort_relationships",List.of(Map.of("between",List.of("A","B"),"type","independent")),
            "resources",Map.of("network","offline","downloads","deny","directory","fixture-resources"),
            "hypotheses",List.of(Map.of("id","H","description","Retain correct supplied work and complete missing work.","outcome","index_trait","analysis",Map.of("mode","manual","goals",goals,"supplied_results",supplied))));
    }
}
