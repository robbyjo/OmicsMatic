/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyMrFamilyPolicy;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyOperationExecutor.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyMrFamilyAdjustmentTest {
    @TempDir Path root;
    private Map<String,Object> study(String method){return Map.of("project",Map.of("mr_testing_families",List.of(Map.of("id","traits","hypotheses",List.of("H1","H2"),"adjustment",method,"rationale","Synthetic prespecified joint family"))),"hypotheses",List.of(Map.of("id","H1","outcome","trait1","analysis",Map.of()),Map.of("id","H2","outcome","trait2","analysis",Map.of())));}
    private Context context(String method,Map<String,Path> dependencies)throws Exception {var study=study(method);var policy=StudyMrFamilyPolicy.resolve(study).get(0);return new Context(root,Files.createDirectories(root.resolve("joint-"+method)),Map.of("id","joint","type","mr_family_adjustment","hypothesis","__study__","configuration",policy),study,Map.of(),dependencies);}
    private Path nativeSource(String h,String name,String rows,int count,boolean complete)throws Exception {
        Path dir=Files.createDirectory(root.resolve(name));Files.writeString(dir.resolve("mr-primary-results.tsv"),"test_id\tfeature_id\tpair_ids\tdisposition\tprimary_estimator\testimate\tstandard_error\tcausal_p_value\tadjusted_p\tadjustment\tfamily_size\tfamily_status\tsignificant_primary\n"+rows.replace("SIZE",Integer.toString(count)).replace("FAMILY",complete?"complete_declared_family":"provisional_unresolved_primary_tests"));
        if(count==0)StudyPublicEvidence.write(dir.resolve("mr-testing.yaml"),Map.of("status","complete_declared_family","family_size",0,"eligible_features",0));
        seal(dir,h,complete?"complete":"waiting_for_input",Map.of());return dir;
    }
    private void seal(Path dir,String h,String state,Map<String,Object> details)throws Exception {
        var hashes=new LinkedHashMap<String,Object>();try(var paths=Files.list(dir)){for(Path file:paths.filter(p->!p.getFileName().toString().equals("manifest.yaml")).toList())hashes.put(file.getFileName().toString(),FollowupSupport.hash(file));}
        StudyPublicEvidence.write(dir.resolve("manifest.yaml"),Map.of("operation_id",dir.getFileName().toString(),"operation_type","mendelian_randomization","hypothesis",h,"state",state,"output_sha256",hashes,"details",details));
    }
    private String tested(String id,String feature,double p){return id+"\t"+feature+"\tpair-"+id+"\tprimary_tested\tivw-fixed\t.5\t.1\t"+p+"\t.03\tBH\tSIZE\tFAMILY\ttrue\n";}
    private String unavailable(String id){return id+"\tunavailable\t\tnot_estimable\tNA\tNA\tNA\tNA\tNA\tBH\tSIZE\tFAMILY\tfalse\n";}
    @Test void pooledPrimaryRawPValuesUseTheWholeDeclaredDenominatorAndPreserveSources()throws Exception {
        var a=nativeSource("H1","a",tested("a","g1",.01)+tested("b","g2",.04)+unavailable("c"),3,true);var b=nativeSource("H2","b",tested("a","g1",.2)+tested("d","g3",.03),2,true);
        String before=FollowupSupport.hash(a.resolve("mr-primary-results.tsv"));var c=context("BH",Map.of("a",a,"b",b));var result=new StudyMrFamilyAdjustment().execute(c);assertEquals(State.COMPLETE,result.state());assertEquals(5,result.details().get("family_size"));var table=DelimitedData.readResult(c.outputDirectory().resolve("mr-family-results.tsv"));assertEquals(5,table.rows().size());double[] expected={.05,.2/3,Double.NaN,.25,.2/3};
        for(int i=0;i<5;i++){var r=table.rows().get(i);assertEquals("complete_cross_hypothesis_family",r[table.column("joint_family_status")]);if(Double.isNaN(expected[i]))assertEquals("NA",r[table.column("joint_adjusted_p")]);else assertEquals(expected[i],Double.parseDouble(r[table.column("joint_adjusted_p")]),1e-14);assertEquals("BH",r[table.column("source_adjustment")]);}
        assertEquals(before,FollowupSupport.hash(a.resolve("mr-primary-results.tsv")));assertEquals(4,result.details().get("estimable_primary_tests"));
    }
    @Test void byAndBonferroniMatchIndependentKnownValues()throws Exception {
        var a=nativeSource("H1","a",tested("a","g1",.01)+tested("b","g2",.04)+unavailable("c"),3,true);var b=nativeSource("H2","b",tested("a","g1",.2)+tested("d","g3",.03),2,true);
        for(String method:List.of("BY","bonferroni","NONE")){var c=context(method,Map.of("a",a,"b",b));assertEquals(State.COMPLETE,new StudyMrFamilyAdjustment().execute(c).state());var t=DelimitedData.readResult(c.outputDirectory().resolve("mr-family-results.tsv"));double expected=method.equals("BY")?.05*(1+.5+1./3+.25+.2):method.equals("NONE")?.01:.05;assertEquals(expected,Double.parseDouble(t.rows().get(0)[t.column("joint_adjusted_p")]),1e-14);}
    }
    @Test void unresolvedMemberWithholdsAllJointDiscoveriesAndAllAdjustedValues()throws Exception {
        var a=nativeSource("H1","a",tested("a","g1",1e-20),1,true);var b=nativeSource("H2","b",tested("b","g2",1e-20),1,false);var c=context("BY",Map.of("a",a,"b",b));var result=new StudyMrFamilyAdjustment().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());var t=DelimitedData.readResult(c.outputDirectory().resolve("mr-family-results.tsv"));for(var row:t.rows()){assertEquals("NA",row[t.column("joint_adjusted_p")]);assertEquals("NA",row[t.column("joint_family_size")]);assertEquals("false",row[t.column("joint_significant_primary")]);}assertTrue(result.details().containsKey("needs"));
    }
    @Test void missingProducerIsNotAnEmptySuccessfulPhenotypeFamily()throws Exception {
        var a=nativeSource("H1","a",tested("a","g1",1e-20),1,true);var result=new StudyMrFamilyAdjustment().execute(context("BY",Map.of("a",a)));assertEquals(State.WAITING_FOR_INPUT,result.state());assertEquals("unresolved",result.details().get("family_size"));
    }
    @Test void sealedExplicitZeroEligibleNativeFamilyCanContributeZeroTests()throws Exception {
        var a=nativeSource("H1","a","",0,true);seal(a,"H1","not_applicable",Map.of());var b=nativeSource("H2","b",tested("b","g1",.01),1,true);var result=new StudyMrFamilyAdjustment().execute(context("BY",Map.of("a",a,"b",b)));assertEquals(State.COMPLETE,result.state());assertEquals(1,result.details().get("family_size"));
    }
    @Test void incompleteSuppliedCountsAndUnknownCoverageCannotBecomeJointFdr()throws Exception {
        Path a=Files.createDirectory(root.resolve("a"));Files.writeString(a.resolve("mr-primary-results.tsv"),"feature_id\tp_value\tadjusted_p\tstatus\ng1\t.001\t.01\tcomplete\n");seal(a,"H1","complete",Map.of("primary_selection_eligible",true,"resolved_family",Map.of("coverage","complete","tested_count",10,"adjustment","BH")));
        var b=nativeSource("H2","b",tested("a","g1",.001),1,true);var result=new StudyMrFamilyAdjustment().execute(context("BY",Map.of("a",a,"b",b)));assertEquals(State.WAITING_FOR_INPUT,result.state());var coverage=StudyPublicEvidence.map(StudyPublicEvidence.list(result.details().get("coverage")).get(0));assertEquals(10,coverage.get("source_declared_tests"));assertEquals(1,coverage.get("observed_rows"));
    }
    @Test void nativeAndCompleteSuppliedPrimaryFamiliesCanBeCombined()throws Exception {
        Path a=Files.createDirectory(root.resolve("a"));Files.writeString(a.resolve("mr-primary-results.tsv"),"feature_id\tp_value\tadjusted_p\tstatus\ng1\t.001\t.01\tcomplete\n");seal(a,"H1","complete",Map.of("primary_selection_eligible",true,"resolved_family",Map.of("coverage","complete","tested_count",1,"adjustment","BH")));
        var b=nativeSource("H2","b",tested("a","g1",.04),1,true);var c=context("BY",Map.of("a",a,"b",b));assertEquals(State.COMPLETE,new StudyMrFamilyAdjustment().execute(c).state());var t=DelimitedData.readResult(c.outputDirectory().resolve("mr-family-results.tsv"));assertEquals(.003,Double.parseDouble(t.rows().get(0)[t.column("joint_adjusted_p")]),1e-15);
    }
    @Test void nativeLookingSuppliedColumnsCannotBypassTheirSourceCoverageContract()throws Exception {
        var a=nativeSource("H1","a",tested("a","g1",.01),1,true);seal(a,"H1","complete",Map.of("execution_origin","externally_produced","primary_selection_eligible",true,"resolved_family",Map.of("coverage","selected_only","tested_count",10,"adjustment","BH")));
        var b=nativeSource("H2","b",tested("a","g1",.01),1,true);var result=new StudyMrFamilyAdjustment().execute(context("BY",Map.of("a",a,"b",b)));assertEquals(State.WAITING_FOR_INPUT,result.state());
    }
    @Test void tamperingIsRejectedBeforeJointResultsAreWritten()throws Exception {
        var a=nativeSource("H1","a",tested("a","g1",.01),1,true);var b=nativeSource("H2","b",tested("a","g1",.01),1,true);Files.writeString(a.resolve("mr-primary-results.tsv"),"tampered");assertThrows(java.io.IOException.class,()->new StudyMrFamilyAdjustment().execute(context("BY",Map.of("a",a,"b",b))));
    }
    @Test void duplicateTestIdsAndContradictoryNotEstimableCallsCannotBeCombined()throws Exception {
        var a=nativeSource("H1","a",tested("a","g1",.01)+tested("a","g2",.02),2,true);var b=nativeSource("H2","b",tested("a","g1",.01),1,true);assertThrows(java.io.IOException.class,()->new StudyMrFamilyAdjustment().execute(context("BY",Map.of("a",a,"b",b))));
        Files.writeString(a.resolve("mr-primary-results.tsv"),Files.readString(b.resolve("mr-primary-results.tsv")).replace("primary_tested","not_estimable"));seal(a,"H1","complete",Map.of());assertThrows(java.io.IOException.class,()->new StudyMrFamilyAdjustment().execute(context("BH",Map.of("a",a,"b",b))));
    }
    @Test void failedFamilyProducerWithoutASummaryRemainsIndexable()throws Exception {
        var findings=org.jlinalg.study.StudyEvidenceIndex.keyFindings(root,"mr_family_adjustment");assertEquals("unavailable",StudyPublicEvidence.map(findings).get("status"));
        Files.writeString(root.resolve("mr-family.yaml"),"invalid: [");StudyPublicEvidence.write(root.resolve("manifest.yaml"),Map.of("state","failed","operation_type","mr_family_adjustment"));assertEquals("unavailable",StudyPublicEvidence.map(org.jlinalg.study.StudyEvidenceIndex.keyFindings(root,"mr_family_adjustment")).get("status"));
    }
    @Test void membershipAndAdjustmentPolicyAreExplicitAndDisjoint() {
        assertEquals("BY",StudyMrFamilyPolicy.resolve(Map.of("project",Map.of("mr_testing_families",List.of(Map.of("id","f","hypotheses",List.of("H1","H2"),"rationale","Declared family"))),"hypotheses",List.of(Map.of("id","H1"),Map.of("id","H2")))).get(0).get("adjustment"));
        var bad=new LinkedHashMap<>(study("BH"));bad.put("hypotheses",List.of(Map.of("id","H1"),Map.of("id","H2","status","retired")));assertThrows(IllegalArgumentException.class,()->StudyMrFamilyPolicy.resolve(bad));
        var project=new LinkedHashMap<>(StudyPublicEvidence.map(study("BH").get("project")));var family=StudyPublicEvidence.list(project.get("mr_testing_families")).get(0);project.put("mr_testing_families",List.of(family,family));bad.put("project",project);assertThrows(IllegalArgumentException.class,()->StudyMrFamilyPolicy.resolve(bad));
    }
    @Test void jointMrEvidenceControlsMrOnlyInventoryCandidatesWithoutOverwritingSourceSignificance()throws Exception {
        var dependencies=new LinkedHashMap<String,Path>();for(String h:List.of("H1","H2")){
            Path dir=Files.createDirectory(root.resolve(h));double p=h.equals("H1")?.03:.9;Files.writeString(dir.resolve("mr-primary-results.tsv"),"feature_id\tbeta\tse\tp_value\tadjusted_p\tstatus\ng\t.5\t.1\t"+p+"\t"+p+"\tcomplete\n");seal(dir,h,"complete",Map.of("primary_selection_eligible",true,"resolved_family",Map.of("coverage","complete","tested_count",1,"adjustment","BH")));dependencies.put(h,dir);
        }
        var c=context("BH",dependencies);var result=new StudyMrFamilyAdjustment().execute(c);assertEquals(State.COMPLETE,result.state());
        var hashes=new LinkedHashMap<String,Object>();for(String f:result.outputs())hashes.put(f,FollowupSupport.hash(c.outputDirectory().resolve(f)));
        StudyPublicEvidence.write(c.outputDirectory().resolve("manifest.yaml"),Map.of("operation_type","mr_family_adjustment","operation_id","joint","hypothesis","__study__","state","complete","details",result.details(),"output_sha256",hashes));dependencies.put("joint",c.outputDirectory());
        Path out=Files.createDirectory(root.resolve("inventory"));var inventory=new StudyFindingInventory().execute(new Context(root,out,Map.of("id","inventory","type","finding_inventory","hypothesis","__study__","datasets",List.of()),c.study(),Map.of(),dependencies));assertEquals(State.COMPLETE,inventory.state());
        var findingRows=DelimitedData.readResult(out.resolve("finding-inventory.tsv"));assertEquals(2,findingRows.rows().size());for(var row:findingRows.rows())assertEquals("false",row[findingRows.column("candidate")]);
        String json=Files.readString(out.resolve("finding-inventory.jsonl"));assertTrue(json.contains("\"source_primary_significant\":true"),json);assertTrue(json.contains("\"joint_adjusted_p\":\"0.06\""),json);assertTrue(json.contains("\"source_adjusted_p\":\"0.03\""),json);
    }
    @Test void plannerOrdersDeclaredFamiliesAfterTheirMrMembersAndBeforeStudyInventory()throws Exception {
        var config=new LinkedHashMap<>(study("BY"));var project=new LinkedHashMap<>(StudyPublicEvidence.map(config.get("project")));project.put("id","joint-mr-plan");project.put("title","Synthetic MR family");project.put("description","Synthetic planning contract");config.put("project",project);config.put("schema_version",1);config.put("resources",Map.of("network","offline","downloads","deny"));
        var hs=new ArrayList<Object>();for(String h:List.of("H1","H2"))hs.add(Map.of("id",h,"description","Synthetic MR", "outcome",h,"inputs",Map.of(),"analysis",Map.of("mode","manual","goals",List.of("mendelian_randomization","impact_assessment"))));config.put("hypotheses",hs);config.put("datasets",List.of());
        Path path=root.resolve("plan-study.yaml");StudyPublicEvidence.write(path,config);var plan=org.jlinalg.study.StudyPlanner.compile(org.jlinalg.study.StudyResolver.resolve(path));
        var ops=StudyPublicEvidence.list(plan.document().get("operations")).stream().map(StudyPublicEvidence::map).toList();var family=ops.stream().filter(op->"mr_family_adjustment".equals(op.get("type"))).findFirst().orElseThrow();assertEquals(2,StudyPublicEvidence.list(family.get("depends_on")).size());
        var inventory=ops.stream().filter(op->"finding_inventory".equals(op.get("type"))).findFirst().orElseThrow();assertTrue(StudyPublicEvidence.list(inventory.get("depends_on")).contains(family.get("id")));assertTrue(ops.indexOf(family)<ops.indexOf(inventory));
    }
}
