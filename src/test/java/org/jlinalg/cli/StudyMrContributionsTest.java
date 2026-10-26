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
import org.jlinalg.study.StudyNeed;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyOperationExecutor.Result;
import org.jlinalg.study.StudyOperationExecutor.State;
import org.jlinalg.study.StudySuppliedResultPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyMrContributionsTest {
    @TempDir Path root;
    private Map<String,Object> definition(String id,String file,List<String> features,String analysis)throws Exception {
        var d=new LinkedHashMap<String,Object>();d.put("id",id);d.put("kind","mendelian_randomization");d.put("role","contribution");d.put("files",Map.of("results",file));
        d.put("scope",Map.of("feature_ids",features,"exposure","isoform ratio","outcome","BMI","ancestry","European","genome_build","GRCh38","tissue","blood","assay","irQTL"));
        d.put("method",Map.of("name","Synthetic externally completed MR","parameters",Map.of("primary_estimator","ivw-fixed","reuse_rationale","Features were specified from association selection before reviewing MR P values")));
        d.put("family",Map.of("id","source-family","coverage","selected_only","tested_count",500,"adjustment","BY","adjusted_p_from_full_family",true));d.put("provenance",Map.of("analysis_id",analysis,"results_sha256",FollowupSupport.hash(root.resolve(file))));return d;
    }
    private void data(String file,String body)throws Exception {Files.writeString(root.resolve(file),"feature_id\tbeta\tse\tp_value\tadjusted_p\tmethod\tn_instruments\tstatus\treason\tpatient_note\n"+body);}
    private String normalRows(){return "g1\t.5\t.1\t.001\t.9\tivw-fixed\t3\tcomplete\t\tPRIVATE_SENTINEL\ng2\t.1\t.2\t.2\t.95\twald-ratio\t1\tcomplete\t\tPRIVATE_SENTINEL\n";}
    private Context base(List<Map<String,Object>> definitions,Map<String,Object> bio)throws Exception {
        Path primary=Files.createDirectories(root.resolve("primary/standard-results"));Files.writeString(primary.resolve("a.tsv"),"feature_id\tbeta\tse\tp_value\ng1\t.5\t.1\t1e-6\ng2\t.5\t.1\t2e-6\ng3\t.5\t.1\t3e-6\ng4\t.5\t.1\t4e-6\n");StudyTestSources.seal(primary.getParent(),"validate_harmonize","harmonize","H");
        var settings=new LinkedHashMap<String,Object>(bio);settings.putIfAbsent("mr_testing",Map.of("adjustment","BY"));var analysis=Map.<String,Object>of("supplied_results",definitions,"bioinformatics",settings);StudySuppliedResultPolicy.resolve(analysis);
        var study=Map.<String,Object>of("datasets",List.of(Map.of("id","a","type","transcriptomics")),"hypotheses",List.of(Map.of("id","H","outcome","BMI","analysis",analysis)));
        return new Context(root,Files.createTempDirectory(root,"mr-"),Map.of("id","mr","type","mendelian_randomization","hypothesis","H","datasets",List.of("a")),study,Map.of(),Map.of("primary",primary.getParent()));
    }
    private void seal(Context c,Result result)throws Exception {
        var hashes=new LinkedHashMap<String,Object>();try(var paths=Files.walk(c.outputDirectory())){for(Path p:paths.filter(Files::isRegularFile).toList())hashes.put(c.outputDirectory().relativize(p).toString().replace('\\','/'),FollowupSupport.hash(p));}
        write(c.outputDirectory().resolve("manifest.yaml"),Map.of("operation_id",c.operation().get("id"),"operation_type",c.operation().get("type"),"hypothesis","H","state",result.state().value(),"output_sha256",hashes,"details",result.details()));
    }
    private Context importAll(Context c)throws Exception {
        var deps=new LinkedHashMap<>(c.dependencyDirectories());for(var d:StudySuppliedResultPolicy.resolve(StudyResultTables.analysis(c))){var imported=new Context(root,Files.createTempDirectory(root,"import-"),Map.of("id","import-"+d.get("id"),"type","supplied_contribution","hypothesis","H","supplied_result_id",d.get("id")),c.study(),Map.of(),Map.of());var result=new StudySuppliedResults().execute(imported);seal(imported,result);deps.put(text(d.get("id")),imported.outputDirectory());}return new Context(c.project(),c.outputDirectory(),c.operation(),c.study(),c.plan(),deps);
    }
    private Map<String,Object> nativeResult(String feature,String status,double p){var r=new LinkedHashMap<String,Object>();r.put("feature_id",feature);r.put("pair_id","native-"+feature);r.put("status",status);r.put("sources",Map.of("feature_id",feature,"exposure",Map.of("source_id","x","trait","quantity"),"outcome",Map.of("source_id","y","trait","BMI")));if(status.equals("complete"))r.put("mr_primary",Map.of("requested_estimator","ivw-fixed","estimate",".6","standard_error",".1","causal_p_value",Double.toString(p)));return r;}
    @Test void externalRowsKeepSourceAdjustmentsWhileJointMrCorrectsTheCompleteFamily()throws Exception {
        data("done.tsv",normalRows());var d=definition("done","done.tsv",List.of("g1","g2"),"analysis-A");var c=importAll(base(List.of(d),Map.of()));var loaded=StudyMrContributions.load(c);assertTrue(loaded.needs().isEmpty());assertEquals(2,loaded.records().size());var records=new ArrayList<Object>(loaded.records());records.add(nativeResult("g3","complete",.01));records.add(nativeResult("g4","not_estimable",Double.NaN));var needs=new ArrayList<StudyNeed>();var summary=StudyMrMultiplicity.finish(c,records,Map.of(),needs);assertEquals(4,summary.get("family_size"));assertEquals(3,summary.get("estimable_primary_tests"));assertEquals(1,summary.get("not_estimable_tests"));assertEquals("complete_declared_family",summary.get("status"));assertTrue(needs.isEmpty());
        var g1=map(records.get(0));assertEquals(.001*4*(1+.5+1.0/3+.25),(double)map(g1.get("mr_multiplicity")).get("adjusted_p"),1e-15);assertEquals(".9",map(list(g1.get("supplied_sources")).get(0)).get("source_adjusted_p"));assertEquals(false,g1.get("analysis_executed_by_jlinalg"));assertEquals(true,g1.get("reused_supplied_result"));assertFalse(Files.readString(c.dependencyDirectories().get("done").resolve("supplied-contribution.tsv")).contains("PRIVATE_SENTINEL"));
        var m=StudyYaml.loadArtifact(c.dependencyDirectories().get("done").resolve("supplied-analysis.yaml"));assertEquals(false,m.get("primary_selection_eligible"));assertEquals(true,m.get("contribution_reuse_eligible"));
    }
    @Test void missingCoverageRowsReserveTheWholeDeclaredScopeInsteadOfUsingSuccessfulSubsets()throws Exception {
        data("partial.tsv",normalRows().split("\n")[0]+"\n");var c=importAll(base(List.of(definition("partial","partial.tsv",List.of("g1","g2"),"analysis-A")),Map.of()));var loaded=StudyMrContributions.load(c);assertEquals(2,loaded.covered().size());assertEquals(2,loaded.records().size());assertTrue(loaded.records().stream().map(StudyPublicEvidence::map).allMatch(r->r.get("status").equals("waiting_for_input")));assertFalse(loaded.needs().isEmpty());
    }
    @Test void wrongExpectedHashQuarantinesCoverageAndNeverFallsBackToNativePairs()throws Exception {
        data("done.tsv",normalRows());var d=definition("done","done.tsv",List.of("g1","g2"),"analysis-A");d.put("provenance",Map.of("analysis_id","analysis-A","results_sha256","0".repeat(64)));var pairs=List.of(Map.of("id","unused1","feature_id","g1"),Map.of("id","unused2","feature_id","g2"));var c=importAll(base(List.of(d),Map.of("genetic_pairs",pairs)));var result=new StudyGeneticEvidence().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());assertFalse(Files.exists(c.outputDirectory().resolve("unused1")));assertFalse(Files.exists(c.outputDirectory().resolve("unused2")));assertEquals(4,map(result.details().get("mr_testing")).get("unresolved_tests"));assertEquals(2,DelimitedData.readResult(c.outputDirectory().resolve("mr-skipped-supplied-pairs.tsv")).rows().size());
    }
    @Test void identicalSourceAliasesDoNotAddTestsButConflictingSourceAnalysesRemainPending()throws Exception {
        data("done.tsv",normalRows());var first=definition("first","done.tsv",List.of("g1","g2"),"analysis-A");var second=definition("second","done.tsv",List.of("g1","g2"),"analysis-A");var c=importAll(base(List.of(first,second),Map.of()));var loaded=StudyMrContributions.load(c);assertEquals(2,loaded.records().size());assertTrue(loaded.needs().isEmpty());assertEquals(2,list(map(loaded.records().get(0)).get("supplied_sources")).size());
        second.put("provenance",Map.of("analysis_id","different-primary","results_sha256",FollowupSupport.hash(root.resolve("done.tsv"))));var conflict=importAll(base(List.of(first,second),Map.of()));var failed=StudyMrContributions.load(conflict);assertEquals(2,failed.records().size());assertFalse(failed.needs().isEmpty());assertTrue(failed.records().stream().map(StudyPublicEvidence::map).allMatch(r->r.get("status").equals("waiting_for_input")&&!r.containsKey("mr_primary")));
    }
    @Test void tamperedCanonicalTablesCannotBeUsedEvenWhenTheirMetadataStillSaysComplete()throws Exception {
        data("done.tsv",normalRows());var c=importAll(base(List.of(definition("done","done.tsv",List.of("g1","g2"),"analysis-A")),Map.of()));Files.writeString(c.dependencyDirectories().get("done").resolve("supplied-contribution.tsv"),"changed\n");var loaded=StudyMrContributions.load(c);assertFalse(loaded.needs().isEmpty());assertTrue(loaded.records().stream().map(StudyPublicEvidence::map).allMatch(r->r.get("status").equals("waiting_for_input")));
    }
    @Test void changedInputSnapshotsInvalidateReuseEvenWhenTheCanonicalRowsAreUnchanged()throws Exception {
        data("done.tsv",normalRows());var c=importAll(base(List.of(definition("done","done.tsv",List.of("g1","g2"),"analysis-A")),Map.of()));var folder=c.dependencyDirectories().get("done");var metadata=StudyYaml.loadArtifact(folder.resolve("supplied-analysis.yaml"));String snapshot=text(map(map(metadata.get("source_files")).get("results")).get("snapshot"));Files.writeString(folder.resolve(snapshot),"changed original source snapshot\n");var loaded=StudyMrContributions.load(c);assertFalse(loaded.needs().isEmpty());assertTrue(loaded.records().stream().map(StudyPublicEvidence::map).allMatch(r->r.get("status").equals("waiting_for_input")&&Boolean.FALSE.equals(r.get("reused_supplied_result"))));
    }
    @Test void suppliedUnestimableTestsRemainCompletedAndInsideTheJointDenominator()throws Exception {
        data("none.tsv","g1\tNA\tNA\tNA\tNA\tivw-fixed\t0\tnot_estimable\tNo usable instruments in the declared source\tPRIVATE_SENTINEL\n");var d=definition("none","none.tsv",List.of("g1"),"analysis-A");d.put("reported_status","not_estimable");var c=importAll(base(List.of(d),Map.of()));var loaded=StudyMrContributions.load(c);assertTrue(loaded.needs().isEmpty());assertEquals("not_estimable",map(loaded.records().get(0)).get("status"));var records=new ArrayList<Object>(loaded.records());for(String f:List.of("g2","g3","g4"))records.add(nativeResult(f,"complete",.01));var summary=StudyMrMultiplicity.finish(c,records,Map.of(),new ArrayList<>());assertEquals(4,summary.get("family_size"));assertEquals(1,summary.get("not_estimable_tests"));assertEquals(0,summary.get("unresolved_tests"));
    }
    @Test void methodCountsAndSourceOutcomeCannotBeSilentlySubstituted()throws Exception {
        data("done.tsv",normalRows());var d=definition("done","done.tsv",List.of("g1","g2"),"analysis-A");var scope=new LinkedHashMap<>(map(d.get("scope")));scope.put("outcome","another-trait");d.put("scope",scope);var c=importAll(base(List.of(d),Map.of()));assertFalse(StudyMrContributions.load(c).needs().isEmpty());var t=DelimitedData.readResult(root.resolve("done.tsv"));var invalid=t.rows().stream().map(String[]::clone).toList();invalid.get(0)[t.column("method")]="egger";assertFalse(StudyMrContributions.validateTable(t.header(),invalid,d).isEmpty());d.put("reported_status","not_estimable");assertFalse(StudyMrContributions.validateTable(t.header(),t.rows(),d).isEmpty());
    }
    @Test void fullyCoveredFeaturesDoNotOpenUnusedAutomaticSourcesOrNativeInputs()throws Exception {
        data("done.tsv",normalRows());var c=base(List.of(definition("done","done.tsv",List.of("g1","g2"),"analysis-A")),Map.of("mr",Map.of("max_features",2,"exposure_ancestry","European","outcome_provider","unavailable"),"genetic_pairs",List.of(Map.of("id","unused","feature_id","g1"))));c=importAll(c);var result=new StudyGeneticEvidence().execute(c);assertEquals(State.COMPLETE,result.state(),result.details().toString());assertFalse(Files.exists(c.outputDirectory().resolve("unused")));assertEquals(2,map(result.details().get("mr_testing")).get("family_size"));assertEquals(2,map(result.details().get("mr_testing")).get("estimable_primary_tests"));assertEquals(false,map(result.details().get("automatic_mr_sources")).get("source_lookup_attempted"));assertTrue(result.summary().contains("reused 2 externally completed feature tests, processed 0 native pairs"));
    }
    @Test void knownUnitsOrInstrumentProtocolDifferencesCannotBeCollapsedAsSourceAliases()throws Exception {
        data("done.tsv",normalRows());for(String difference:List.of("units","protocol")) {
            var a=definition("first","done.tsv",List.of("g1","g2"),"analysis-A");var b=definition("second","done.tsv",List.of("g1","g2"),"analysis-A");
            if(difference.equals("units")){var scope=new LinkedHashMap<>(map(b.get("scope")));scope.put("units","a different exposure scale");b.put("scope",scope);}else{var method=new LinkedHashMap<>(map(b.get("method")));var parameters=new LinkedHashMap<>(map(method.get("parameters")));parameters.put("clump_r2",.01);method.put("parameters",parameters);b.put("method",method);}
            var c=importAll(base(List.of(a,b),Map.of()));var loaded=StudyMrContributions.load(c);assertFalse(loaded.needs().isEmpty(),difference);assertTrue(loaded.records().stream().map(StudyPublicEvidence::map).allMatch(r->r.get("status").equals("waiting_for_input")),difference);
        }
    }
    @Test void fullPrimaryAndContributionStrategiesCannotBothOwnTheSameMrStage()throws Exception {
        data("done.tsv",normalRows());var contribution=definition("partial","done.tsv",List.of("g1","g2"),"analysis-A");var whole=new LinkedHashMap<>(contribution);whole.put("id","whole");whole.put("role","primary");assertThrows(IllegalArgumentException.class,()->StudySuppliedResultPolicy.resolve(Map.of("supplied_results",List.of(whole,contribution))));var unsupported=new LinkedHashMap<>(contribution);unsupported.put("kind","gene_ontology");assertThrows(IllegalArgumentException.class,()->StudySuppliedResultPolicy.resolve(Map.of("supplied_results",List.of(unsupported))));
    }
}
