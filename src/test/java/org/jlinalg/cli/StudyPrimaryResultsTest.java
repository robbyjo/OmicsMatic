/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyOperationExecutor.State;
import org.jlinalg.study.StudyPrimaryPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyPrimaryResultsTest {
    @TempDir Path root;
    static final String HEADER="feature_id\tbeta\tse\tp_value\n";
    private Path source(String id,String type,String hypothesis,String name,String data)throws Exception {
        Path dir=Files.createDirectory(root.resolve(id));Path file=dir.resolve(name);Files.createDirectories(file.getParent());Files.writeString(file,data);
        StudyTestSources.seal(dir,type,id,hypothesis);return dir;
    }
    private Context context(String id,Map<String,Object> analysis,Map<String,Path> dependencies)throws Exception {
        return new Context(root,Files.createDirectory(root.resolve(id)),Map.of("id",id,"type","candidate_prioritization","hypothesis","H","datasets",List.of("a","b")),
            Map.of("resources",Map.of("directory","fixture-resources","network","offline","downloads","deny"),"datasets",List.of(),"hypotheses",List.of(Map.of("id","H","analysis",analysis))),Map.of(),dependencies);
    }
    private Map<String,Object> partialPoolAnalysis() {
        return new LinkedHashMap<>(Map.of("supplied_results",List.of(Map.of("id","unfinished-pool","kind","meta_analysis","reported_status","partial",
            "files",Map.of("results","external.tsv"),"scope",Map.of(),"method",Map.of("name","Incomplete external pool")))));
    }
    @Test void separatelyRetainedPartialPoolBlocksImplicitCohortFallback() throws Exception {
        var cohort=source("cohort","association","H","standard-results/a.tsv",HEADER+"a1\t1\t1\t.001\n");
        var c=context("partial-pool-hold",partialPoolAnalysis(),Map.of("cohort",cohort));
        var failure=assertThrows(StudyPrimaryResults.Unavailable.class,()->StudyPrimaryResults.official(c));
        assertEquals("incomplete_primary_meta_analysis",failure.receipt().get("status"));
    }
    @Test void explicitCohortChoiceStillWorksWithRetainedPartialPool() throws Exception {
        var cohort=source("cohort","association","H","standard-results/a.tsv",HEADER+"a1\t1\t1\t.001\n");var analysis=partialPoolAnalysis();
        analysis.put("primary_result",Map.of("kind","association","dataset_id","a","rationale","Prespecified cohort target"));
        assertEquals("cohort",StudyPrimaryResults.official(context("explicit-despite-partial",analysis,Map.of("cohort",cohort))).source().producer().id());
    }
    @Test void completedNativePoolCanFulfilTheRequestedPartialPool() throws Exception {
        var cohort=source("cohort","association","H","standard-results/a.tsv",HEADER+"a1\t1\t1\t.001\n");
        var meta=source("native-pool","meta_analysis","H","meta-analysis.tsv",HEADER+"m1\t2\t1\t.02\n");
        var selected=StudyPrimaryResults.official(context("native-after-partial",partialPoolAnalysis(),Map.of("cohort",cohort,"meta",meta)));
        assertEquals("native-pool",selected.source().producer().id());assertEquals(Map.of("m1",.02),selected.probabilities());
    }
    @Test void metaHasOneIdentityAcrossConsumerOrderAndUsesDeclaredBy()throws Exception {
        var cohort=source("cohort","association","H","standard-results/a.tsv",HEADER+"cohort-only\t5\t1\t1e-20\n");
        var meta=source("meta","meta_analysis","H","meta-analysis.tsv",HEADER+"m1\t1\t1\t.01\nm2\t2\t1\t.2\n");
        var decoy=source("decoy","discovery_meta_analysis","H","meta-analysis.tsv",HEADER+"discovery-only\t8\t1\t1e-30\n");
        String identity="";
        for(int order=0;order<2;order++) {
            var deps=new LinkedHashMap<String,Path>();if(order==0){deps.put("cohort",cohort);deps.put("meta",meta);}else{deps.put("meta",meta);deps.put("cohort",cohort);}deps.put("decoy",decoy);
            var c=context("order"+order,Map.of("multiple_testing",Map.of("method","BY")),deps);var selected=StudyPrimaryResults.official(c);
            assertEquals(selected.receipt().get("producer_manifest_sha256"),FollowupSupport.hash(c.outputDirectory().resolve(selected.receipt().get("producer_manifest_snapshot").toString())));
            assertEquals(Map.of("m1",.01,"m2",.2),selected.probabilities());assertEquals(.03,selected.adjusted().get("m1"),1e-14);
            if(order==0)identity=selected.receipt().get("primary_identity_sha256").toString();else assertEquals(identity,selected.receipt().get("primary_identity_sha256"));
            assertEquals(State.COMPLETE,new StudyCliExecutor().execute(c).state());var table=DelimitedData.readResult(c.outputDirectory().resolve("candidate-prioritization.tsv"));
            assertEquals("m1",table.rows().get(0)[table.column("feature_id")]);assertEquals(.03,Double.parseDouble(table.rows().get(0)[table.column("FDR")]),1e-14);
        }
    }
    @Test void plannedOrIncompleteMetaNeverFallsBackToStrongCohort()throws Exception {
        var cohort=source("cohort","association","H","standard-results/a.tsv",HEADER+"cohort-only\t5\t1\t1e-20\n");
        var c=context("missing",Map.of("goals",List.of("meta_analysis")),Map.of("cohort",cohort));
        assertThrows(StudyPrimaryResults.Unavailable.class,()->StudyPrimaryResults.official(c));assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(c).state());
        var meta=source("meta","meta_analysis","H","meta-analysis.tsv",HEADER);var m=new LinkedHashMap<String,Object>(StudyYaml.loadArtifact(meta.resolve("manifest.yaml")));m.put("state","waiting_for_input");StudyPublicEvidence.write(meta.resolve("manifest.yaml"),m);
        var incomplete=context("incomplete",Map.of(),Map.of("cohort",cohort,"meta",meta));
        assertTrue(assertThrows(StudyPrimaryResults.Unavailable.class,()->StudyPrimaryResults.official(incomplete)).getMessage().contains("not complete"));
        var retained=source("retained-import","meta_analysis","H","supplied-results-limited.tsv",HEADER+"limited\t1\t1\t1e-20\n");
        var importManifest=new LinkedHashMap<String,Object>(StudyYaml.loadArtifact(retained.resolve("manifest.yaml")));importManifest.put("details",Map.of("primary_selection_eligible",false));StudyPublicEvidence.write(retained.resolve("manifest.yaml"),importManifest);
        var partial=context("partial-import",Map.of(),Map.of("cohort",cohort,"meta",retained));
        assertEquals("incomplete_primary_meta_analysis",assertThrows(StudyPrimaryResults.Unavailable.class,()->StudyPrimaryResults.official(partial)).receipt().get("status"));
        var op=new LinkedHashMap<String,Object>(incomplete.operation());op.put("type","finding_inventory");op.put("scope","study");var inventory=new Context(root,Files.createDirectory(root.resolve("inventory")),op,incomplete.study(),incomplete.plan(),incomplete.dependencyDirectories());
        var result=new StudyFindingInventory().execute(inventory);assertEquals(0L,result.details().get("candidates"));assertEquals(List.of("H"),result.details().get("unresolved_primary_hypotheses"));
        var row=StudyPublicEvidence.map(SimpleJson.parse(Files.readAllLines(inventory.outputDirectory().resolve("finding-inventory.jsonl")).get(0)));
        assertFalse((Boolean)row.get("candidate"));assertTrue(StudyPublicEvidence.list(row.get("observations")).stream().map(StudyPublicEvidence::map).noneMatch(o->Boolean.TRUE.equals(o.get("primary"))));
    }
    @Test void cohortSourceNeedsExplicitChoiceAndAssociationSupersedesValidation()throws Exception {
        var validation=source("validation","validate_harmonize","H","standard-results/a.tsv",HEADER+"a1\t1\t1\t.001\n");
        var assoc=source("association","association","H","standard-results/a.tsv",HEADER+"old\t1\t1\t.00001\n");
        Files.createDirectories(assoc.resolve("post-effect-qc-results"));Files.writeString(assoc.resolve("post-effect-qc-results/a.tsv"),HEADER+"retained\t1\t1\t.1\n");StudyTestSources.seal(assoc,"association","association","H");
        var other=source("other","association","H","standard-results/b.tsv",HEADER+"best\t1\t1\t1e-30\n");
        var c=context("ambiguous",Map.of(),Map.of("validation",validation,"association",assoc,"other",other));assertThrows(StudyPrimaryResults.Unavailable.class,()->StudyPrimaryResults.official(c));
        var explicit=context("explicit",Map.of("primary_result",Map.of("kind","association","dataset_id","a","rationale","Prespecified target cohort")),c.dependencyDirectories());
        var selected=StudyPrimaryResults.official(explicit);assertEquals(Map.of("retained",.1),selected.probabilities());assertEquals("association",selected.source().producer().id());
        var scope=context("dataset",Map.of(),c.dependencyDirectories());assertEquals(false,StudyPrimaryResults.dataset(scope,"a","dataset_ranking").receipt().get("official_primary"));
    }
    @Test void contrastsAreScopedBeforeCorrectionWithoutMinimumPSelection()throws Exception {
        var meta=source("meta","meta_analysis","H","meta-analysis.tsv","feature_id\tterm\tbeta\tse\tp_value\nf1\tdiscovery\t2\t1\t1e-20\nf1\tpooled\t1\t1\t.2\nf2\tpooled\t1\t1\t.3\n");
        var c=context("pooled",Map.of(),Map.of("meta",meta));assertEquals(Map.of("f1",.2,"f2",.3),StudyPrimaryResults.official(c).probabilities());
        var explicit=context("contrast",Map.of("primary_result",Map.of("term","discovery","rationale","Registered coefficient label in supplied model")),Map.of("meta",meta));assertEquals(Map.of("f1",1e-20),StudyPrimaryResults.official(explicit).probabilities());
        var assoc=source("assoc","association","H","standard-results/a.tsv","feature_id\tterm\tp_value\nf1\tt1\t.1\nf1\tt2\t.01\n");assertThrows(StudyPrimaryResults.Unavailable.class,()->StudyPrimaryResults.official(context("contrast-needed",Map.of(),Map.of("assoc",assoc))));
    }
    @Test void sourceScopeCanonicalPathsHashesAndFrozenIdentityAreEnforced()throws Exception {
        var correct=source("correct","meta_analysis","H","meta-analysis.tsv",HEADER+"f1\t1\t1\t.1\n");
        var otherH=source("otherH","meta_analysis","OTHER","meta-analysis.tsv",HEADER+"wrong\t1\t1\t1e-20\n");
        var c=context("scope",Map.of(),Map.of("correct",correct,"otherH",otherH));assertEquals(Map.of("f1",.1),StudyPrimaryResults.official(c).probabilities());
        Files.writeString(correct.resolve("meta-analysis.tsv"),HEADER+"changed\t1\t1\t.1\n");assertThrows(IOException.class,()->StudyPrimaryResults.official(c));
        StudyTestSources.seal(correct,"meta_analysis","correct","H");assertTrue(assertThrows(IOException.class,()->StudyPrimaryResults.official(c)).getMessage().contains("frozen primary identity"));
        var lookalike=source("lookalike","meta_analysis","H","nested/meta-analysis.tsv",HEADER+"wrong\t1\t1\t.01\n");assertThrows(IOException.class,()->StudyPrimaryResults.official(context("nested",Map.of(),Map.of("nested",lookalike))));
    }
    @Test void explicitOverridesRequireRationaleAndSourceMultiplicityDoesNotDependOnOrder()throws Exception {
        assertThrows(IllegalArgumentException.class,()->StudyPrimaryPolicy.resolve(Map.of("dataset_id","a")));assertThrows(IllegalArgumentException.class,()->StudyPrimaryPolicy.resolve(Map.of("kind","sensitivity_analysis","rationale","Best result")));
        var first=source("first","meta_analysis","H","meta-analysis.tsv",HEADER+"first\t1\t1\t.2\n");var second=source("second","meta_analysis","H","meta-analysis.tsv",HEADER+"second\t1\t1\t.00001\n");
        var deps=Map.of("first",first,"second",second);assertThrows(StudyPrimaryResults.Unavailable.class,()->StudyPrimaryResults.official(context("many",Map.of(),deps)));
        var selected=StudyPrimaryResults.official(context("registered",Map.of("primary_result",Map.of("operation_id","first","rationale","Registered primary producer")),deps));assertEquals(Map.of("first",.2),selected.probabilities());
    }
    @Test void nonfiniteEstimatesCannotBecomeOfficialFindings()throws Exception {
        var meta=source("meta","meta_analysis","H","meta-analysis.tsv",HEADER+"infinite_se\t1\tInfinity\t1e-20\nvalid\t1\t1\t.3\n");
        assertEquals(Map.of("valid",.3),StudyPrimaryResults.official(context("finite",Map.of(),Map.of("meta",meta))).probabilities());
    }
    @Test void suppliedFamilyAdjustmentsAndMetadataArePartOfTheFrozenIdentity()throws Exception {
        var meta=source("meta","meta_analysis","H","meta-analysis.tsv","feature_id\tbeta\tse\tp_value\tadjusted_p\nf1\t1\t1\t1e-20\t.4\nf2\t1\t1\t.8\t1\n");
        var metadata=new LinkedHashMap<String,Object>(Map.of("kind","meta_analysis","canonical_results","meta-analysis.tsv","canonical_sha256",FollowupSupport.hash(meta.resolve("meta-analysis.tsv")),"primary_selection_eligible",true,
            "resolved_family",Map.of("id","original-assay","adjustment","BH","coverage","selected_only","tested_count",1000,"alpha",.05)));
        StudyPublicEvidence.write(meta.resolve("supplied-analysis.yaml"),metadata);StudyTestSources.seal(meta,"meta_analysis","meta","H");
        var c=context("external",Map.of("multiple_testing",Map.of("method","NONE")),Map.of("meta",meta));var selected=StudyPrimaryResults.official(c);
        assertEquals(.4,selected.adjusted().get("f1"),0);assertEquals("BH",selected.adjustment());assertEquals(1000,selected.receipt().get("testing_denominator"));assertFalse(selected.completeObservedFamily());
        metadata.put("resolved_family",Map.of("id","changed-assay","adjustment","BH","coverage","selected_only","tested_count",2000,"alpha",.05));
        StudyPublicEvidence.write(meta.resolve("supplied-analysis.yaml"),metadata);StudyTestSources.seal(meta,"meta_analysis","meta","H");
        assertTrue(assertThrows(IOException.class,()->StudyPrimaryResults.official(c)).getMessage().contains("frozen primary identity"));
    }
    @Test void registeredTermCreatesOneOfficialGseaOperationWithoutAnImplicitDatasetContrast()throws Exception {
        var study=Map.of("schema_version",1,"project",Map.of("id","primary-term","title","Registered contrast","description","Synthetic planning contract"),
            "resources",Map.of("directory","fixture-resources","network","offline","downloads","deny"),
            "datasets",List.of(Map.of("id","a","type","expression","stage","association_results","files",Map.of("results","a.tsv"))),
            "hypotheses",List.of(Map.of("id","H","description","Synthetic registered contrast.","inputs",Map.of("discovery",List.of("a")),"analysis",Map.of("mode","manual","goals",List.of("ranked_gsea"),"primary_result",Map.of("term","registered","rationale","Prespecified model coefficient")))));
        Path input=root.resolve("study.yaml");StudyPublicEvidence.write(input,study);
        var planned=org.jlinalg.study.StudyPlanner.compile(org.jlinalg.study.StudyResolver.resolve(input));
        var ranks=StudyPublicEvidence.list(planned.document().get("operations")).stream().map(StudyPublicEvidence::map).filter(op->"ranked_gsea".equals(op.get("type"))).toList();
        assertEquals(1,ranks.size());assertFalse(ranks.get(0).containsKey("dataset_id"));
    }
    @Test void nativeCorrectionAndPrimaryAlphaCannotChangeWithinOneFrozenOperation()throws Exception {
        var meta=source("meta","meta_analysis","H","meta-analysis.tsv",HEADER+"f1\t1\t1\t.01\nf2\t1\t1\t.8\n");
        var first=context("frozen",Map.of(),Map.of("meta",meta));StudyPrimaryResults.official(first);
        for(var testing:List.of(Map.of("method","BY"),Map.of("method","BH","alpha",.01))) {
            var study=new LinkedHashMap<String,Object>(first.study());study.put("hypotheses",List.of(Map.of("id","H","analysis",Map.of("multiple_testing",testing))));
            var changed=new Context(root,first.outputDirectory(),first.operation(),study,first.plan(),first.dependencyDirectories());
            assertTrue(assertThrows(IOException.class,()->StudyPrimaryResults.official(changed)).getMessage().contains("frozen primary identity"));
        }
    }
    @Test void anUnavailableRegisteredAssociationCannotFallBackToItsHarmonizationCopy()throws Exception {
        var validation=source("validation","validate_harmonize","H","standard-results/a.tsv",HEADER+"old\t1\t1\t1e-20\n");
        var first=context("planned",Map.of(),Map.of("validation",validation));
        var plan=Map.<String,Object>of("operations",List.of(Map.of("id","new-association","type","association","hypothesis","H","dataset_id","a")));
        var pending=new Context(root,first.outputDirectory(),first.operation(),first.study(),plan,first.dependencyDirectories());
        assertEquals("incomplete_primary_association",assertThrows(StudyPrimaryResults.Unavailable.class,()->StudyPrimaryResults.official(pending)).receipt().get("status"));
    }
    @Test void suppliedMultiContrastAdjustmentsReachInventoryAndDiagnosticsWithoutCrossContrastDuplicates()throws Exception {
        var meta=source("meta","meta_analysis","H","meta-analysis.tsv","feature_id\tterm\tbeta\tse\tp_value\tadjusted_p\nf1\tother\t3\t1\t1e-20\t.001\nf1\tpooled\t1\t1\t.2\t.4\nf2\tpooled\t1\t1\t.3\t.4\n");
        StudyPublicEvidence.write(meta.resolve("supplied-analysis.yaml"),Map.of("kind","meta_analysis","canonical_results","meta-analysis.tsv","canonical_sha256",FollowupSupport.hash(meta.resolve("meta-analysis.tsv")),"primary_selection_eligible",true,
            "resolved_family",Map.of("id","source-contrasts","adjustment","BH","coverage","complete","tested_count",3,"alpha",.05)));
        StudyTestSources.seal(meta,"meta_analysis","meta","H");
        var c=context("scoped-import",Map.of(),Map.of("meta",meta));
        var result=new StudyFindingInventory().execute(c);assertEquals(State.COMPLETE,result.state());assertEquals(0L,result.details().get("candidates"));
        var first=StudyPublicEvidence.map(SimpleJson.parse(Files.readAllLines(c.outputDirectory().resolve("finding-inventory.jsonl")).get(0)));
        var primary=StudyPublicEvidence.list(first.get("observations")).stream().map(StudyPublicEvidence::map).filter(o->Boolean.TRUE.equals(o.get("primary"))).toList();
        assertEquals(1,primary.size());assertEquals("pooled",primary.get(0).get("term"));assertEquals(.4,((Number)primary.get(0).get("full_family_adjusted_p")).doubleValue(),0);
        assertEquals(Map.of("f1",.4,"f2",.4),StudyCohortResults.read(meta.resolve("meta-analysis.tsv"),"BY").adjusted());
    }
}
