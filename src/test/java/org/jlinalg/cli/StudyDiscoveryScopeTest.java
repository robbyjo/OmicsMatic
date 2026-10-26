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

class StudyDiscoveryScopeTest {
    @TempDir Path root;

    @Test void suppliedPoolContainingValidationCannotSelectIndependentReplication() throws Exception {
        var c=fixture("leak",Map.of("datasets",List.of("a","b","v"),"cohorts",List.of("A","B","V"),"phenotype","synthetic"));
        var result=new StudyCliExecutor().execute(c);
        assertEquals(State.WAITING_FOR_INPUT,result.state());
        assertFalse(Files.exists(c.outputDirectory().resolve("replication.tsv")));
    }

    @Test void knownDiscoveryValidationOverlapIsCheckedWithoutDiscoveryTables() throws Exception {
        var c=fixture("overlap",scope());
        var study=new LinkedHashMap<>(c.study());
        study.put("cohort_relationships",List.of(Map.of("between",List.of("A","V"),"type","overlapping","overlap_fraction",.1)));
        c=new Context(c.project(),c.outputDirectory(),c.operation(),study,c.plan(),c.dependencyDirectories());
        var result=new StudyCliExecutor().execute(c);
        assertEquals(State.WAITING_FOR_INPUT,result.state());
        assertFalse(Files.exists(c.outputDirectory().resolve("replication.tsv")));
    }

    @Test void exactReferenceScopeCompletesValidationWithoutOriginalFiles() throws Exception {
        var c=fixture("accepted",scope());
        var result=new StudyCliExecutor().execute(c);
        assertEquals(State.COMPLETE,result.state(),result.summary());
        assertTrue(Files.exists(c.outputDirectory().resolve("replication.tsv")));
        assertFalse(Files.exists(c.project().resolve("a.tsv")));
        var audit=StudyYaml.loadArtifact(c.outputDirectory().resolve("discovery-scope-audit.yaml"));
        assertEquals(true,audit.get("external_scope_checked"));
        assertEquals(List.of(),audit.get("issues"));
        assertEquals("partially_declared_not_certified",audit.get("status"));
    }

    @Test void assumedDifferentCohortNamesDoNotProveSourceValidationIndependence() throws Exception {
        var c=fixture("undeclared",scope());var study=new LinkedHashMap<>(c.study());study.put("cohort_relationships",List.of());
        c=new Context(c.project(),c.outputDirectory(),c.operation(),study,c.plan(),c.dependencyDirectories());
        assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(c).state());
        assertTrue(Files.readString(c.outputDirectory().resolve("discovery-scope-audit.yaml")).contains("source_validation_independence_not_explicitly_declared"));
    }

    @Test void mismatchedCohortsAndTraitsHoldDespiteMatchingDatasetNames() throws Exception {
        for(String kind:List.of("cohorts","phenotype","outcome","missing_trait","duplicate_scope")) {
            var scope=scope();
            if(kind.equals("cohorts"))scope.put("cohorts",List.of("A","V"));
            if(kind.equals("phenotype"))scope.put("phenotype","another_trait");
            if(kind.equals("outcome"))scope.put("outcome","another_trait");
            if(kind.equals("missing_trait"))scope.remove("phenotype");
            if(kind.equals("duplicate_scope"))scope.put("datasets",List.of("a","b","a"));
            if(kind.equals("duplicate_scope")) { assertThrows(IllegalArgumentException.class,()->fixture(kind,scope)); continue; }
            var c=fixture(kind,scope);var result=new StudyCliExecutor().execute(c);
            assertEquals(State.WAITING_FOR_INPUT,result.state(),kind);
            assertFalse(Files.exists(c.outputDirectory().resolve("replication.tsv")),kind);
        }
    }

    @Test void cohortOnlySourceMembershipCanBindDeclaredReferences() throws Exception {
        var scope=scope();scope.remove("datasets");var c=fixture("cohort-only",scope);
        assertEquals(State.COMPLETE,new StudyCliExecutor().execute(c).state());
        assertTrue(org.jlinalg.study.StudyValidator.validate(writeStudy(c),true).valid());
    }

    @Test void strictEffectContractNeedsSourceDeclarationsAndKnownConflictsAlwaysHold() throws Exception {
        var missing=fixture("strict-missing",scope());
        missing=analysis(missing,Map.of("pooling",Map.of("require_complete_contract",true,"effect_contract",contract())));
        assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(missing).state());
        var complete=scope();complete.put("effect_contract",contract());var valid=fixture("strict-complete",complete);
        valid=analysis(valid,Map.of("pooling",Map.of("require_complete_contract",true,"effect_contract",contract())));
        assertEquals(State.COMPLETE,new StudyCliExecutor().execute(valid).state());
        var wrong=scope();wrong.put("effect_contract",Map.of("effect_units","incompatible"));var conflict=fixture("contract-conflict",wrong);
        conflict=analysis(conflict,Map.of("pooling",Map.of("effect_contract",contract())));
        assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(conflict).state());
    }

    @Test void conflictingVariantEffectCodingHoldsWithoutAutomaticFlip() throws Exception {
        var scope=scope();scope.put("modality","genomics");scope.put("genome_build","GRCh38");
        var c=fixture("alleles",scope);
        var datasets=new ArrayList<Object>();
        for(Object raw:StudyPublicEvidence.list(c.study().get("datasets"))) {var d=new LinkedHashMap<>(StudyPublicEvidence.map(raw));d.put("type","gwas");d.put("genome_build","GRCh38");datasets.add(d);}
        var study=new LinkedHashMap<>(c.study());study.put("datasets",datasets);
        c=new Context(c.project(),c.outputDirectory(),c.operation(),study,c.plan(),c.dependencyDirectories());
        Path pool=c.dependencyDirectories().get("pool"),validation=c.dependencyDirectories().get("validation");
        // Explicitly regenerate and seal a new synthetic source edition.
        Files.writeString(c.project().resolve("pool.tsv"),"feature_id\tbeta\tse\tp_value\tadjusted_p\teffect_allele\tother_allele\ng1\t.4\t.1\t.0000001\t.04\tG\tA\n");
        Path next=Files.createDirectory(c.project().resolve("new-pool"));
        var imported=new StudyCliExecutor().execute(new Context(c.project(),next,Map.of("type","discovery_meta_analysis","hypothesis","H","supplied_result_id","pool"),study,Map.of(),Map.of()));
        assertEquals(State.COMPLETE,imported.state());StudyTestSources.seal(next,"discovery_meta_analysis","new-pool","H");
        Files.writeString(validation.resolve("standard-results/v.tsv"),"feature_id\tbeta\tse\tp_value\teffect_allele\tother_allele\ng1\t-.3\t.1\t.001\tA\tG\n");
        StudyTestSources.seal(validation,"validate_harmonize","new-validation","H");
        c=new Context(c.project(),c.outputDirectory(),c.operation(),study,c.plan(),Map.of("pool",next,"validation",validation));
        assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(c).state());
        assertTrue(Files.readString(c.outputDirectory().resolve("discovery-scope-audit.yaml")).contains("conflicting_source_validation_variant_coding"));
        assertFalse(Files.exists(c.outputDirectory().resolve("replication.tsv")));
    }

    @Test void metadataReferencesAreValidatedAndCannotBecomeValidationOrNativeMetaEstimates() throws Exception {
        assertTrue(org.jlinalg.study.StudyOperationRegistry.descriptor("validate_harmonize").acceptedStages().contains("analysis_reference"));
        assertTrue(org.jlinalg.study.StudyOperationRegistry.descriptor("discovery_replication").acceptedStages().contains("analysis_reference"));
        assertFalse(org.jlinalg.study.StudyOperationRegistry.descriptor("meta_analysis").acceptedStages().contains("analysis_reference"));
        var c=fixture("structure",scope());
        assertTrue(org.jlinalg.study.StudyValidator.validate(writeStudy(c),true).valid());
        var resolved=org.jlinalg.study.StudyResolver.resolve(c.project().resolve("study.json"));
        var plan=org.jlinalg.study.StudyPlanner.compile(resolved);
        assertFalse(StudyPublicEvidence.list(plan.document().get("operations")).stream().map(StudyPublicEvidence::map).anyMatch(op->Set.of("association","participant_qc").contains(op.get("type"))));
        var operation=new LinkedHashMap<>(c.operation());operation.put("type","meta_analysis");
        var nativeMeta=new Context(c.project(),Files.createDirectory(c.project().resolve("native-meta")),operation,c.study(),c.plan(),c.dependencyDirectories());
        var result=new StudyCliExecutor().execute(nativeMeta);
        assertEquals(State.WAITING_FOR_INPUT,result.state());
        assertFalse(Files.exists(nativeMeta.outputDirectory().resolve("meta-analysis.tsv")));
        var validationRole=new LinkedHashMap<>(c.operation());validationRole.put("roles",Map.of("discovery",List.of("a"),"replication",List.of("b","v")));
        var invalidValidation=new Context(c.project(),Files.createDirectory(c.project().resolve("invalid-validation")),validationRole,c.study(),c.plan(),c.dependencyDirectories());
        assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(invalidValidation).state());
        assertTrue(Files.readString(invalidValidation.outputDirectory().resolve("discovery-scope-audit.yaml")).contains("pooled_reference_cannot_supply_validation_estimates"));
    }

    private Path writeStudy(Context c)throws Exception {Path p=c.project().resolve("study.json");StudyPublicEvidence.write(p,c.study());return p;}
    private Map<String,Object> contract(){return Map.of("estimand","adjusted association","orientation","feature to synthetic trait","effect_scale","linear beta","effect_units","trait per feature unit","phenotype_transformation","identity","feature_transformation","identity");}
    private Context analysis(Context c,Map<String,Object> values){
        var h=new LinkedHashMap<>(StudyPublicEvidence.map(StudyPublicEvidence.list(c.study().get("hypotheses")).get(0)));
        var a=new LinkedHashMap<>(StudyPublicEvidence.map(h.get("analysis")));a.putAll(values);h.put("analysis",a);
        var study=new LinkedHashMap<>(c.study());study.put("hypotheses",List.of(h));
        return new Context(c.project(),c.outputDirectory(),c.operation(),study,c.plan(),c.dependencyDirectories());
    }

    private Map<String,Object> scope(){return new LinkedHashMap<>(Map.of("datasets",List.of("a","b"),"cohorts",List.of("A","B"),"phenotype","synthetic"));}

    private Context fixture(String id,Map<String,Object> scope)throws Exception {
        Path project=Files.createDirectory(root.resolve(id));
        Files.writeString(project.resolve("pool.tsv"),"feature_id\tbeta\tse\tp_value\tadjusted_p\ng1\t.4\t.1\t.0000001\t.04\n");
        Files.writeString(project.resolve("validation.tsv"),"feature_id\tbeta\tse\tp_value\ng1\t.3\t.1\t.001\n");
        var definition=new LinkedHashMap<String,Object>(Map.of("id","pool","kind","discovery_meta_analysis","files",Map.of("results","pool.tsv"),"scope",scope,
            "method",Map.of("name","Synthetic external discovery pool"),"family",Map.of("id","full-family","coverage","selected_only","tested_count",400000,"adjustment","BH","adjusted_p_from_full_family",true)));
        var analysis=new LinkedHashMap<String,Object>(Map.of("mode","manual","goals",List.of("discovery_replication"),"supplied_results",List.of(definition),"replication",Map.of("adjustment_scope","discovery_subset")));
        var datasets=new ArrayList<Object>();
        for(String name:List.of("a","b"))datasets.add(new LinkedHashMap<>(Map.of("id",name,"cohort",name.toUpperCase(java.util.Locale.ROOT),"type","generic",
            "stage","analysis_reference","supplied_result_id","pool","files",Map.of())));
        datasets.add(new LinkedHashMap<>(Map.of("id","v","cohort","V","type","generic","stage","association_results","files",Map.of("results","validation.tsv"))));
        var roles=Map.of("discovery",List.of("a","b"),"replication",List.of("v"));
        var hypothesis=new LinkedHashMap<String,Object>(Map.of("id","H","description","Synthetic scoped validation.","outcome","synthetic","inputs",roles,"analysis",analysis));
        var study=new LinkedHashMap<String,Object>(Map.of("schema_version",1,"project",Map.of("id","scope-fixture","title","Scope fixture","description","Synthetic software fixture."),
            "resources",Map.of("network","offline","downloads","deny","directory","fixture-resources"),"cohorts",List.of(Map.of("id","A"),Map.of("id","B"),Map.of("id","V")),
            "cohort_relationships",List.of(Map.of("between",List.of("A","B","V"),"type","independent")),"datasets",datasets,"hypotheses",List.of(hypothesis)));
        Path pool=Files.createDirectory(project.resolve("imported"));
        var imported=new StudyCliExecutor().execute(new Context(project,pool,Map.of("type","discovery_meta_analysis","hypothesis","H","supplied_result_id","pool"),study,Map.of(),Map.of()));
        boolean conflictingTrait=scope.containsKey("phenotype")&&!scope.get("phenotype").equals("synthetic")||scope.containsKey("outcome")&&!scope.get("outcome").equals("synthetic");
        boolean conflictingPopulation=StudyPublicEvidence.list(scope.get("datasets")).contains("v")||StudyPublicEvidence.list(scope.get("cohorts")).contains("V");
        assertEquals(conflictingTrait||conflictingPopulation?State.WAITING_FOR_INPUT:State.COMPLETE,imported.state(),imported.summary());
        var hashes=new LinkedHashMap<String,Object>();try(var files=Files.walk(pool)){for(Path file:files.filter(Files::isRegularFile).toList())hashes.put(pool.relativize(file).toString().replace('\\','/'),FollowupSupport.hash(file));}
        StudyPublicEvidence.write(pool.resolve("manifest.yaml"),Map.of("operation_type","discovery_meta_analysis","operation_id","pool-operation","hypothesis","H","state",imported.state().value(),"details",imported.details(),"output_sha256",hashes));
        Path validation=Files.createDirectory(project.resolve("validation"));Files.createDirectory(validation.resolve("standard-results"));
        Files.copy(project.resolve("validation.tsv"),validation.resolve("standard-results/v.tsv"));
        StudyTestSources.seal(validation,"validate_harmonize","validation-operation","H");
        return new Context(project,Files.createDirectory(project.resolve("comparison")),Map.of("type","discovery_replication","hypothesis","H","datasets",List.of("a","b","v"),"roles",roles,"pooled_discovery",true),
            study,Map.of(),Map.of("pool",pool,"validation",validation));
    }
}
