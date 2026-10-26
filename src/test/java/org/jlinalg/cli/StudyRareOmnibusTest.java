/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyOperationExecutor.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyRareOmnibusTest {
    @TempDir Path root;
    private final Map<String,Object> policy=new LinkedHashMap<>(Map.of("selection","primary","adjustment","BY"));
    private Map<String,Object> roles=Map.of();
    private String replication="all_results";
    private Map<String,Object> study(){return Map.of("datasets",List.of(Map.of("id","a","type","gwas","species","human")),"hypotheses",List.of(Map.of("id","H","inputs",roles,"analysis",Map.of("rare_omnibus",policy,"replication",Map.of("adjustment_scope",replication),"multiple_testing",Map.of("method","BH","alpha",.05)))));}
    private Context context(String name,String type,Map<String,Path> deps)throws Exception{return new Context(root,Files.createDirectory(root.resolve(name)),Map.of("id",name,"type",type,"hypothesis","H","datasets",List.of("a")),study(),Map.of(),deps);}
    private static void seal(Context c,Result r)throws Exception{StudyTestSources.seal(c.outputDirectory(),c.operation().get("type").toString(),c.operation().get("id").toString(),"H");var m=StudyYaml.loadArtifact(c.outputDirectory().resolve("manifest.yaml"));m.put("details",r.details());m.put("state",r.state().value());StudyPublicEvidence.write(c.outputDirectory().resolve("manifest.yaml"),m);}
    private Path source(List<String[]> rows)throws Exception{
        Path dir=Files.createDirectory(root.resolve("source"));FollowupSupport.table(dir.resolve("rare-results.tsv"),List.of("scope","feature_id","mask_id","test","p_value","log_p_value","adjusted_p","is_primary_test","status","result_type"),rows);
        Files.writeString(dir.resolve("feature-annotation.tsv"),"feature_id\tgene_id\tgene_symbol\tmapping_status\ns1\tENSG1\tG1\tmissing_location\ns2\tENSG2\tG2\tmissing_location\n");StudyTestSources.seal(dir,"rare_variant_analysis","raw","H");return dir;
    }
    private static String[] row(String scope,String set,String mask,String test,double p,boolean primary){return new String[]{scope,set,mask,test,String.valueOf(p),p>0?String.valueOf(Math.log(p)):"NA",".987",String.valueOf(primary&&scope.equals("primary")),"ok","unsigned_set"};}
    private List<String[]> rows(String... scopes){var rows=new ArrayList<String[]>();for(String scope:scopes)for(String set:List.of("s1","s2"))for(String mask:List.of("lof","missense")){rows.add(row(scope,set,mask,"acat-o",set.equals("s1")?(mask.equals("lof")?.001:.02):.4,true));rows.add(row(scope,set,mask,"burden",.9,false));}return rows;}
    private Context run(String name,Path source,State state)throws Exception{var c=context(name,"rare_set_omnibus",Map.of("source",source));var r=new StudyCliExecutor().execute(c);assertEquals(state,r.state(),r.details().toString());seal(c,r);return c;}
    private static String[] unit(DelimitedData t,String scope,String feature){return t.rows().stream().filter(r->r[t.column("scope")].equals(scope)&&r[t.column("feature_id")].equals(feature)).findFirst().orElseThrow();}
    private static double value(DelimitedData t,String[] row,String key){return Double.parseDouble(row[t.column(key)]);}
    @Test void registeredPrimaryTestsReceiveASeparateUnitCorrectionAndDoNotRewriteSourceFdr()throws Exception{
        Path source=source(rows("primary"));String hash=FollowupSupport.hash(source.resolve("rare-results.tsv"));var c=run("omnibus",source,State.COMPLETE);var t=DelimitedData.readResult(c.outputDirectory().resolve("rare-omnibus-results.tsv"));var s1=unit(t,"primary","s1");assertEquals(.002,value(t,s1,"p_value"),1e-16);assertEquals(.006,value(t,s1,"adjusted_p"),1e-16);assertEquals("2",s1[t.column("component_count")]);assertEquals("2",s1[t.column("testing_denominator")]);var method=StudyPublicEvidence.map(StudyPublicEvidence.map(StudyYaml.loadArtifact(c.outputDirectory().resolve("manifest.yaml")).get("details")).get("method_report"));assertTrue(method.get("method").toString().contains("cross-mask"));assertEquals("JLinAlg "+JLinAlgCli.version(),method.get("software"));assertTrue(StudyPublicEvidence.list(method.get("references")).contains("https://doi.org/10.1214/aos/1013699998"));assertEquals(hash,FollowupSupport.hash(source.resolve("rare-results.tsv")));assertEquals(hash,FollowupSupport.hash(c.outputDirectory().resolve("source-rare-results.tsv")));
        var projection=context("followup","rare_set_followup",Map.of("omnibus",c.outputDirectory()));var result=new StudyCliExecutor().execute(projection);assertEquals(State.COMPLETE,result.state());seal(projection,result);assertEquals(true,result.details().get("source_unit_omnibus"));
        var inventory=context("inventory","finding_inventory",Map.of("source",source,"omnibus",c.outputDirectory()));assertEquals(State.COMPLETE,new StudyFindingInventory().execute(inventory).state());String json=Files.readString(inventory.outputDirectory().resolve("finding-inventory.jsonl"));assertTrue(json.contains("\"effective_method\":\"bonferroni_min\""));assertTrue(json.contains("\"adjustment\":\"BY\""));var records=new ArrayList<Map<String,Object>>();for(String line:Files.readAllLines(inventory.outputDirectory().resolve("finding-inventory.jsonl")))records.add(StudyPublicEvidence.map(SimpleJson.parse(line)));for(var record:records)for(Object raw:StudyPublicEvidence.list(record.get("observations"))){var observation=StudyPublicEvidence.map(raw);if("rare_variant_analysis".equals(observation.get("operation_type")))assertEquals(false,observation.get("primary"));}
    }
    @Test void aMissingMaskHoldsTheWholeGeneFamilyRatherThanCorrectingSuccessfulRows()throws Exception{
        var rows=rows("primary");rows.removeIf(r->r[1].equals("s2")&&r[2].equals("missense"));var c=run("held",source(rows),State.WAITING_FOR_INPUT);var t=DelimitedData.readResult(c.outputDirectory().resolve("rare-omnibus-results.tsv"));assertEquals("unresolved_components",unit(t,"primary","s2")[t.column("status")]);assertTrue(t.rows().stream().allMatch(r->r[t.column("adjusted_p")].equals("NA")));var m=StudyYaml.loadArtifact(c.outputDirectory().resolve("manifest.yaml"));assertEquals(false,StudyPublicEvidence.map(m.get("details")).get("primary_selection_eligible"));
    }
    @Test void anIndependentEligibilityLedgerCanExplainAnEmptyMaskButCannotDiscardAnObservedTest()throws Exception{
        var rows=rows("primary");rows.removeIf(r->r[1].equals("s2")&&r[2].equals("missense"));Path source=source(rows);policy.put("component_eligibility","eligibility.tsv");Files.writeString(root.resolve("eligibility.tsv"),"scope\tfeature_id\tmask_id\ttest\treason\n*\ts2\tmissense\tacat-o\tno_eligible_variants\n");var c=run("eligible",source,State.COMPLETE);var t=DelimitedData.readResult(c.outputDirectory().resolve("rare-omnibus-results.tsv"));var s2=unit(t,"primary","s2");assertEquals("1",s2[t.column("ineligible_components")]);assertEquals(.4,value(t,s2,"p_value"),1e-15);
        Files.writeString(root.resolve("eligibility.tsv"),"scope\tfeature_id\tmask_id\ttest\treason\n*\ts1\tlof\tacat-o\tno_eligible_variants\n");var invalid=context("invalid","rare_set_omnibus",Map.of("source",source));assertThrows(java.io.IOException.class,()->new StudyCliExecutor().execute(invalid));
    }
    @Test void aModelFailureCannotMasqueradeAsAnOutcomeIndependentExclusion()throws Exception{
        var rows=rows("primary");for(String[] row:rows)if(row[1].equals("s2")&&row[2].equals("missense")&&row[3].equals("acat-o")){row[4]="NA";row[5]="NA";row[8]="model_failed";}Path source=source(rows);policy.put("component_eligibility","eligibility.tsv");Files.writeString(root.resolve("eligibility.tsv"),"scope\tfeature_id\tmask_id\ttest\treason\n*\ts2\tmissense\tacat-o\tno_eligible_variants\n");var c=context("failure","rare_set_omnibus",Map.of("source",source));assertThrows(java.io.IOException.class,()->new StudyCliExecutor().execute(c));
    }
    @Test void explicitGeneGroupingCombinesAllRegisteredSetsBeforeGeneFdr()throws Exception{
        policy.put("unit","gene");policy.put("gene_map","genes.tsv");Files.writeString(root.resolve("genes.tsv"),"feature_id\tgene_id\tgene_symbol\ns1\tENSG1\tG1\ns2\tENSG1\tG1\n");var c=run("genes",source(rows("primary")),State.COMPLETE);var t=DelimitedData.readResult(c.outputDirectory().resolve("rare-omnibus-results.tsv"));assertEquals(1,t.rows().size());var gene=unit(t,"primary","ENSG1");assertEquals(.004,value(t,gene,"p_value"),1e-16);assertEquals(.004,value(t,gene,"adjusted_p"),1e-16);assertEquals("4",gene[t.column("component_count")]);assertTrue(gene[t.column("source_set_ids")].contains("s2"));assertTrue(Files.readString(c.outputDirectory().resolve("feature-annotation.tsv")).contains("ENSG1\tENSG1\tG1"));
    }
    @Test void unmappedGeneUnitsRemainInTheRegisteredInventoryAndHoldCorrection()throws Exception{
        policy.put("unit","gene");policy.put("gene_map","genes.tsv");Files.writeString(root.resolve("genes.tsv"),"feature_id\tgene_id\tgene_symbol\ns1\tENSG1\tG1\n");var c=run("unmapped",source(rows("primary")),State.WAITING_FOR_INPUT);var t=DelimitedData.readResult(c.outputDirectory().resolve("rare-omnibus-results.tsv"));assertEquals(2,t.rows().size());assertEquals("unmapped_gene",unit(t,"primary","unmapped:s2")[t.column("status")]);assertTrue(t.rows().stream().allMatch(r->r[t.column("adjusted_p")].equals("NA")));
    }
    @Test void allTestsMeansTheRegisteredFullGridRatherThanTheMostFavorableTest()throws Exception{
        policy.put("component_tests","all");var c=run("all",source(rows("primary")),State.COMPLETE);var t=DelimitedData.readResult(c.outputDirectory().resolve("rare-omnibus-results.tsv"));assertEquals(.004,value(t,unit(t,"primary","s1"),"p_value"),1e-16);assertEquals("4",unit(t,"primary","s1")[t.column("component_count")]);
    }
    @Test void underflowTailSurvivesCombinationAndAdjustmentButAnIncoherentZeroDoesNot()throws Exception{
        var rows=rows("primary");for(String[] row:rows)if(row[1].equals("s1")&&row[2].equals("lof")&&row[3].equals("acat-o")){row[4]="0.0";row[5]="-1000";}Path source=source(rows);var c=run("tail",source,State.COMPLETE);var t=DelimitedData.readResult(c.outputDirectory().resolve("rare-omnibus-results.tsv"));assertEquals(-1000+Math.log(2),value(t,unit(t,"primary","s1"),"log_p_value"),1e-12);assertEquals(-1000+Math.log(6),value(t,unit(t,"primary","s1"),"log_adjusted_p"),1e-12);
        Files.writeString(source.resolve("rare-results.tsv"),Files.readString(source.resolve("rare-results.tsv")).replace("-1000","-1"));StudyTestSources.seal(source,"rare_variant_analysis","raw","H");var invalid=context("invalid","rare_set_omnibus",Map.of("source",source));assertThrows(java.io.IOException.class,()->new StudyCliExecutor().execute(invalid));
    }
    @Test void discoverySelectedValidationCorrectsOnlyTheFrozenUnitsAndHasNoInventedDirection()throws Exception{
        roles=Map.of("discovery",List.of("a"),"replication",List.of("b"));replication="discovery_subset";var rows=rows("primary","discovery","cohort:b");for(String[] row:rows)if(row[0].equals("cohort:b")&&row[1].equals("s1")&&row[3].equals("acat-o"))row[4]=row[2].equals("lof")?".01":".3";for(String[] row:rows)row[5]=String.valueOf(Math.log(Double.parseDouble(row[4])));var c=run("replicated",source(rows),State.COMPLETE);var t=DelimitedData.readResult(c.outputDirectory().resolve("rare-omnibus-replication.tsv"));assertEquals(1,t.rows().size());var row=t.rows().get(0);assertEquals("s1",row[t.column("feature_id")]);assertEquals(.02,value(t,row,"validation_adjusted_p"),1e-15);assertEquals("1",row[t.column("validation_testing_denominator")]);assertEquals("NA",row[t.column("same_sign")]);assertEquals("true",row[t.column("replicated")]);
    }
    @Test void unresolvedValidationKeepsTheSeparatelyCompletePrimaryAndCannotReportNonReplication()throws Exception{
        roles=Map.of("discovery",List.of("a"),"replication",List.of("b"));var rows=rows("primary","discovery","cohort:b");rows.removeIf(r->r[0].equals("cohort:b")&&r[1].equals("s1")&&r[2].equals("missense"));var c=run("partial",source(rows),State.WAITING_FOR_INPUT);var t=DelimitedData.readResult(c.outputDirectory().resolve("rare-omnibus-replication.tsv"));assertEquals("NA",t.rows().get(0)[t.column("replicated")]);var projection=context("followup","rare_set_followup",Map.of("omnibus",c.outputDirectory()));assertEquals(State.COMPLETE,new StudyCliExecutor().execute(projection).state());
    }
    @Test void supplementalOmnibusCannotReplaceTheOriginalPrimaryFamily()throws Exception{
        policy.put("selection","supplemental");Path source=source(rows("primary"));var c=run("supplement",source,State.COMPLETE);var projection=context("followup","rare_set_followup",Map.of("raw",source,"omnibus",c.outputDirectory()));var result=new StudyCliExecutor().execute(projection);assertEquals(State.WAITING_FOR_INPUT,result.state(),"Synthetic raw source deliberately lacks its original family metadata; supplemental omnibus must not be substituted");
    }
    @Test void sourceTamperingIsRejectedBeforeCombiningAnything()throws Exception{
        Path source=source(rows("primary"));Files.writeString(source.resolve("rare-results.tsv"),"changed\n");var c=context("tamper","rare_set_omnibus",Map.of("source",source));assertThrows(java.io.IOException.class,()->new StudyCliExecutor().execute(c));
    }
    @Test void cauchyBoundaryHasTheRegisteredConservativeFallback(){var result=StudyRareOmnibus.combine(List.of(Math.log(.001),0.),"acat");assertEquals(Math.log(.002),result.logP(),1e-15);assertEquals("bonferroni_min_boundary_fallback",result.method());}
    @Test void aMissingRegisteredLedgerHoldsSelectionEvenWhenObservedTestsAreComplete()throws Exception{
        policy.put("component_eligibility","not-delivered.tsv");var c=run("missing-ledger",source(rows("primary")),State.WAITING_FOR_INPUT);var t=DelimitedData.readResult(c.outputDirectory().resolve("rare-omnibus-results.tsv"));assertTrue(t.rows().stream().allMatch(r->r[t.column("adjusted_p")].equals("NA")));
    }
    @Test void boundedExecutionDoesNotPromoteASelectedUnitPrefix()throws Exception{
        policy.put("maximum_units",1);var c=run("bounded",source(rows("primary")),State.WAITING_FOR_INPUT);assertTrue(DelimitedData.readResult(c.outputDirectory().resolve("rare-omnibus-results.tsv")).rows().isEmpty());assertEquals(false,StudyPublicEvidence.map(StudyYaml.loadArtifact(c.outputDirectory().resolve("manifest.yaml")).get("details")).get("primary_selection_eligible"));
    }
    @Test void suppliedScientificScopeSurvivesTheOmnibusIntoDownstreamEnrichment()throws Exception{
        Path source=source(rows("primary"));var m=StudyYaml.loadArtifact(source.resolve("manifest.yaml"));m.put("details",Map.of("scope",Map.of("species","human","phenotype","synthetic disease","population","synthetic population")));StudyPublicEvidence.write(source.resolve("manifest.yaml"),m);
        var base=context("omnibus","rare_set_omnibus",Map.of("source",source));var s=new LinkedHashMap<String,Object>(base.study());s.put("datasets",List.of());var c=new Context(root,base.outputDirectory(),base.operation(),s,base.plan(),base.dependencyDirectories());var r=new StudyCliExecutor().execute(c);assertEquals(State.COMPLETE,r.state());assertEquals("human",StudyPublicEvidence.map(r.details().get("scope")).get("species"));seal(c,r);
        var projection=context("projection","rare_set_followup",Map.of("omnibus",c.outputDirectory()));var noParticipants=new Context(root,projection.outputDirectory(),projection.operation(),s,projection.plan(),projection.dependencyDirectories());var result=new StudyCliExecutor().execute(noParticipants);assertEquals(State.COMPLETE,result.state());assertEquals("9606",result.details().get("species"));
    }
    @Test void aDisabledSupplementalStageDoesNotReadOrDemandAComponentSource()throws Exception{
        policy.put("enabled",false);policy.put("selection","supplemental");var c=context("disabled","rare_set_omnibus",Map.of());assertEquals(State.NOT_APPLICABLE,new StudyCliExecutor().execute(c).state());
    }
    @Test void primaryTestSelectionCannotSilentlyIgnoreAnExplicitDifferentTestList(){assertThrows(IllegalArgumentException.class,()->org.jlinalg.study.StudyRareOmnibusPolicy.resolve(Map.of("component_tests","primary","tests",List.of("burden"))));}
}
