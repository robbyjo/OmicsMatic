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
import org.jlinalg.study.StudyOperationExecutor.*;
import org.jlinalg.study.StudyPlanner;
import org.jlinalg.study.StudyResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudySuppliedModulesTest {
    @TempDir Path root;
    private Map<String,Object> definition(String kind,String file,int count){var d=new LinkedHashMap<String,Object>(Map.of("id",kind,"kind",kind,"files",Map.of("results",file),"scope",Map.of("species","human"),"method",Map.of("name","Completed synthetic lab analysis")));if(kind.equals("module_gene_ontology"))d.put("family",Map.of("id","full-module-term-family","coverage","complete","tested_count",count,"adjustment","BH"));return d;}
    private Map<String,Object> study(List<Map<String,Object>> definitions){return Map.of("datasets",List.of(),"resources",Map.of("network","offline","downloads","deny","directory","fixture-resources"),"hypotheses",List.of(Map.of("id","H","analysis",Map.of("supplied_results",definitions,"network",Map.of("go_min_size",1,"go_max_size",10,"go_min_module_genes",1)))));}
    private Context context(String name,Map<String,Object> definition){String kind=text(definition.get("kind"));return new Context(root,root.resolve(name),Map.of("id",name,"type",kind,"hypothesis","H","supplied_result_id",definition.get("id")),study(List.of(definition)),Map.of(),Map.of());}
    private Result execute(String name,Map<String,Object> d)throws Exception{return new StudyCliExecutor().execute(context(name,d));}
    private void seal(String name,String type,Result result)throws Exception {var hashes=new LinkedHashMap<String,String>();for(String file:result.outputs())hashes.put(file,FollowupSupport.hash(root.resolve(name).resolve(file)));write(root.resolve(name).resolve("manifest.yaml"),Map.of("operation_id",name,"operation_type",type,"hypothesis","H","state",result.state().value(),"output_sha256",hashes,"details",result.details()));}
    @Test void completedModuleGoReusesSourceAdjustmentWithoutGraphMembershipOrDatabase()throws Exception {
        Files.writeString(root.resolve("go.tsv"),"module_id\tterm_id\tp_value\tadjusted_p\nM1\tGO:1\t.00001\t.35\n");var d=definition("module_gene_ontology","go.tsv",1000);d.put("family",Map.of("id","full-source-family","coverage","selected_only","tested_count",1000,"adjustment","BH","adjusted_p_from_full_family",true));var r=execute("imported",d);assertEquals(State.COMPLETE,r.state(),r.summary());assertEquals(false,r.details().get("analysis_executed_by_jlinalg"));var t=DelimitedData.readResult(root.resolve("imported/module-gene-ontology.tsv"));assertEquals(.35,Double.parseDouble(t.rows().get(0)[t.column("adjusted_p")]),0);assertEquals("NA",t.rows().get(0)[t.column("universe_genes")]);assertEquals(1000,map(r.details().get("resolved_family")).get("tested_count"));assertFalse(Files.exists(root.resolve("fixture-resources")));
    }
    @Test void partialMembershipsAndGoImportWithoutCompletedStagePromotion()throws Exception {
        Files.writeString(root.resolve("members.tsv"),"node_id\tmodule_id\ng1\tM1\n");
        Files.writeString(root.resolve("go.tsv"),"module_id\tterm_id\tp_value\tadjusted_p\nM1\tGO:1\t.01\t.02\n");
        for(String kind:List.of("network_modules","module_gene_ontology")) {
            var d=definition(kind,kind.equals("network_modules")?"members.tsv":"go.tsv",1);d.put("reported_status","partial");
            var result=execute(kind,d);assertEquals(State.COMPLETE,result.state());
            assertEquals(false,result.details().get("replaces_native_stage"));
            assertTrue(result.outputs().stream().anyMatch(name->name.startsWith("supplied/results.")));
            assertFalse(Files.exists(root.resolve(kind).resolve(kind.equals("network_modules")?"network-modules.tsv":"module-gene-ontology.tsv")));
            assertFalse(Boolean.TRUE.equals(result.details().get("primary_selection_eligible")));
        }
    }
    @Test void identicalTermsInDifferentModulesAndTypesDefineDistinctJointTests()throws Exception {
        Files.writeString(root.resolve("go.tsv"),"module_id\tmodule_type\tterm_id\tp_value\nM1\tclique\tGO:1\t.01\nM1\tcommunity\tGO:1\t.03\n");var r=execute("joint",definition("module_gene_ontology","go.tsv",2));assertEquals(State.COMPLETE,r.state(),r.summary());var t=DelimitedData.read(root.resolve("joint/module-gene-ontology.tsv"));assertEquals(2,t.rows().size());assertEquals(.02,Double.parseDouble(t.rows().get(0)[t.column("adjusted_p")]),1e-14);assertEquals(.03,Double.parseDouble(t.rows().get(1)[t.column("adjusted_p")]),1e-14);assertEquals(2,map(r.details().get("resolved_family")).get("supplied_estimable_tests"));
    }
    @Test void finiteLogProbabilitiesSurviveRoundedZeroValuesAndCompleteBh()throws Exception {
        Files.writeString(root.resolve("go.tsv"),"module_id\tterm_id\tp_value\tlog_p_value\nM1\tGO:1\t0\t-1000\nM2\tGO:1\t0\t-1100\nM3\tGO:1\t0\t-900\n");assertEquals(State.COMPLETE,execute("logs",definition("module_gene_ontology","go.tsv",3)).state());var t=DelimitedData.read(root.resolve("logs/module-gene-ontology.tsv"));double[] expected={-1000+Math.log(1.5),-1100+Math.log(3),-900};for(int i=0;i<3;i++){assertEquals(expected[i],Double.parseDouble(t.rows().get(i)[t.column("log_adjusted_p")]),1e-10);assertEquals("0.0",t.rows().get(i)[t.column("adjusted_p")]);}
    }
    @Test void positiveDecimalUnderflowCanSupplyItsOwnLogAndUnqualifiedZeroCannotInventOne()throws Exception {
        Files.writeString(root.resolve("go.tsv"),"module_id\tterm_id\tp_value\nM1\tGO:1\t1e-999\n");var r=execute("tiny",definition("module_gene_ontology","go.tsv",1));assertEquals(State.COMPLETE,r.state());var t=DelimitedData.read(root.resolve("tiny/module-gene-ontology.tsv"));assertEquals(-999*Math.log(10),Double.parseDouble(t.rows().get(0)[t.column("log_adjusted_p")]),1e-10);
        Files.writeString(root.resolve("zero.tsv"),"module_id\tterm_id\tp_value\nM1\tGO:1\t0\n");r=execute("unqualified-zero",definition("module_gene_ontology","zero.tsv",1));assertEquals(State.WAITING_FOR_INPUT,r.state());assertFalse(Files.exists(root.resolve("unqualified-zero/module-gene-ontology.tsv")));
    }
    @Test void contradictoryCountsProbabilitiesAndRepeatedPairsAreRejected()throws Exception {
        for(String data:List.of("M1\tGO:1\t-1e-999\t10\t2\t3\t1\n","M1\tGO:1\t1.00000000000000001\t10\t2\t3\t1\n","M1\tGO:1\t.1\t10\t2\t3\t4\n","M1\tGO:1\t.1\t10\t9\t9\t1\n","M1\tGO:1\t.1\t18446744073709551626\t2\t3\t1\n","M1\tGO:1\t.1\t10\t2\t3\t1\nM1\tGO:1\t.1\t10\t2\t3\t1\n")){
            Files.writeString(root.resolve("bad.tsv"),"module_id\tterm_id\tp_value\tuniverse_genes\tmodule_genes\tterm_genes\toverlap_genes\n"+data);assertThrows(java.io.IOException.class,()->execute("bad-"+Math.abs(data.hashCode()),definition("module_gene_ontology","bad.tsv",2)));}
    }
    private Map<String,Object> memberships(boolean nodes)throws Exception {
        Files.writeString(root.resolve("members.tsv"),"node_id\tmodule_id\tmodule_type\ng1\tM\tcommunity\ng2\tM\tcommunity\ng3\tM\tclique\ng4\tM\tclique\n");var d=definition("network_modules","members.tsv",0);
        if(nodes){Files.writeString(root.resolve("nodes.tsv"),"node_id\ng1\ng2\ng3\ng4\ng5\ng6\n");Files.writeString(root.resolve("annotation.tsv"),"feature_id\tgene_symbol\ng1\tA1\ng2\tA2\ng3\tA3\ng4\tA4\ng5\tA5\ng6\tA6\n");d.put("files",Map.of("results","members.tsv","nodes","nodes.tsv","feature_annotation","annotation.tsv"));}return d;
    }
    private Result nativeGo(String name,Map<String,Object> d)throws Exception {
        var imported=execute(name+"-members",d);assertEquals(State.COMPLETE,imported.state(),imported.summary());seal(name+"-members","network_modules",imported);
        return new StudyCliExecutor().execute(new Context(root,root.resolve(name),Map.of("id",name,"type","module_gene_ontology","hypothesis","H"),study(List.of(d)),Map.of(),Map.of("source",root.resolve(name+"-members"))));
    }
    @Test void missingBackgroundDoesNotForceGraphReconstructionOrInferOneFromMembers()throws Exception {
        var d=memberships(false);var r=nativeGo("missing-background",d);assertEquals(State.WAITING_FOR_INPUT,r.state());assertTrue(r.summary().contains("complete node universe"));assertFalse(Files.exists(root.resolve("missing-background/module-gene-ontology.tsv")));assertFalse(Files.exists(root.resolve("missing-background/network-edges.tsv")));
    }
    @Test void onlyMissingModuleGoRunsOnAnExplicitUniverseIncludingIsolates()throws Exception {
        var d=memberships(true);Path db=root.resolve("fixture-resources/enrichment/go-human");Files.createDirectories(db);Files.writeString(db.resolve("data.gmt"),"GO:1\tSynthetic first\tA1\tA2\nGO:2\tSynthetic second\tA3\tA4\n");Files.writeString(db.resolve("manifest.properties"),"format_version=1\nprovider=GO\ngene_id_type=symbol\ndata_format=gmt\ndata_file=data.gmt\nsha256.data.gmt="+FollowupSupport.hash(db.resolve("data.gmt"))+"\n");var r=nativeGo("missing-only",d);assertEquals(State.COMPLETE,r.state(),r.summary());assertEquals(6,r.details().get("background_genes"));var t=DelimitedData.read(root.resolve("missing-only/module-gene-ontology.tsv"));assertEquals(4,t.rows().size());assertEquals(2,t.rows().stream().map(row->row[t.column("module_type")]).distinct().count());
        for(String[] row:t.rows()){int overlap=Integer.parseInt(row[t.column("overlap_genes")]);double expected=overlap==2?1./15:1.;assertEquals(expected,Double.parseDouble(row[t.column("p_value")]),1e-12);assertEquals(overlap==2?2./15:1.,Double.parseDouble(row[t.column("adjusted_p")]),1e-12);}
    }
    @Test void nodeRoleMappingDoesNotBecomeAnUnrelatedResultColumnMapping()throws Exception {
        var d=memberships(true);Files.writeString(root.resolve("nodes.tsv"),Files.readString(root.resolve("nodes.tsv")).replace("node_id","NodeIdentifier"));d.put("columns",Map.of("nodes",Map.of("node_id","NodeIdentifier")));var r=execute("role-columns",d);assertEquals(State.COMPLETE,r.state(),r.summary());assertEquals(6,DelimitedData.read(root.resolve("role-columns/network-nodes.tsv")).rows().size());assertEquals(2L,r.details().get("modules"));
    }
    private List<Map<String,Object>> plan(List<Map<String,Object>> defs,List<String> goals)throws Exception {
        var document=Map.of("schema_version",1,"project",Map.of("id","module-reuse","title","Reuse completed modules","description","Synthetic provided analyses"),"datasets",List.of(),"hypotheses",List.of(Map.of("id","H","description","Only complete missing work","analysis",Map.of("mode","manual","goals",goals,"supplied_results",defs))));write(root.resolve("study.yaml"),document);return list(StudyPlanner.compile(StudyResolver.resolve(root.resolve("study.yaml"))).document().get("operations")).stream().map(StudyPublicEvidence::map).toList();
    }
    @Test void completedModuleGoCreatesNoUpstreamNativeGraphOrModulePrerequisites()throws Exception {
        var ops=plan(List.of(definition("module_gene_ontology","already-done.tsv",2)),List.of("module_gene_ontology"));assertTrue(ops.stream().noneMatch(o->List.of("network_analysis","network_modules","assay_qc","participant_qc","association").contains(o.get("type"))));assertEquals(1,ops.stream().filter(o->o.get("type").equals("module_gene_ontology")&&o.containsKey("supplied_result_id")).count());
    }
    @Test void suppliedMembershipsScheduleOnlyMissingGoAndAllProvidedSchedulesNoAnalysis()throws Exception {
        var module=definition("network_modules","members.tsv",0);var ops=plan(List.of(module),List.of("module_gene_ontology"));assertTrue(ops.stream().noneMatch(o->o.get("type").equals("network_analysis")));assertEquals(1,ops.stream().filter(o->o.get("type").equals("network_modules")).count());assertEquals(1,ops.stream().filter(o->o.get("type").equals("module_gene_ontology")&&!o.containsKey("supplied_result_id")).count());
        ops=plan(List.of(module,definition("module_gene_ontology","go.tsv",2)),List.of("network_modules","module_gene_ontology"));assertTrue(ops.stream().filter(o->List.of("network_modules","module_gene_ontology").contains(o.get("type"))).allMatch(o->o.containsKey("supplied_result_id")));assertTrue(ops.stream().noneMatch(o->o.get("type").equals("network_analysis")));
    }
}
