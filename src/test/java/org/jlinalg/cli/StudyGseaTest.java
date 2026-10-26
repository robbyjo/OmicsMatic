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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyGseaTest {
    @TempDir Path root;
    private Context context(String folder,Map<String,Object> p,Map<String,Path> deps) throws Exception {
        return new Context(root,Files.createDirectory(root.resolve(folder)),Map.of("id",folder,"type","ranked_gsea","hypothesis","H","datasets",List.of()),
            Map.of("resources",Map.of("directory","fixture-resources","network","offline","downloads","deny"),"datasets",List.of(),"hypotheses",List.of(Map.of("id","H","analysis",Map.of("gsea",p)))),Map.of(),deps);
    }
    private Map<String,Object> ranking() throws Exception {
        Files.writeString(root.resolve("ranks.tsv"),"gene_id\tstatistic\nA\t4\nB\t3\nC\t-1\nD\t-2\n");Files.writeString(root.resolve("sets.gmt"),"top\tTop\tA\tB\nbottom\tBottom\tC\tD\n");
        return new LinkedHashMap<>(Map.of("ranking",Map.of("file","ranks.tsv","coverage","complete","tested_genes",4),"gene_sets","sets.gmt","species","human","minimum_size",2,"maximum_size",2,"maximum_exact_states",100,"adjustment","BY"));
    }
    private static void seal(Context c,org.jlinalg.study.StudyOperationExecutor.Result result) throws Exception {
        var hashes=new LinkedHashMap<String,Object>();try(var paths=Files.walk(c.outputDirectory())){for(Path file:paths.filter(Files::isRegularFile).toList())hashes.put(c.outputDirectory().relativize(file).toString().replace('\\','/'),FollowupSupport.hash(file));}
        StudyPublicEvidence.write(c.outputDirectory().resolve("manifest.yaml"),Map.of("operation_id",c.operation().get("id"),"operation_type",c.operation().get("type"),"hypothesis","H","state",result.state().value(),"details",result.details(),"output_sha256",hashes));
    }
    @Test void completeRankingNeedsNoRawDataAndPathwayKeepsNativeByAdjustment() throws Exception {
        var p=ranking();Context original=context("gsea",p,Map.of());var result=new StudyCliExecutor().execute(original);assertEquals(State.COMPLETE,result.state(),result.summary());seal(original,result);
        var table=DelimitedData.readResult(original.outputDirectory().resolve("ranked-gsea.tsv"));assertEquals(.75,Double.parseDouble(table.rows().get(0)[table.column("adjusted_p")]),1e-13);assertEquals("A;B",table.rows().get(0)[table.column("leading_edge_genes")]);
        var study=new LinkedHashMap<>(original.study());study.put("hypotheses",List.of(Map.of("id","H","analysis",Map.of("gsea",p,"network",Map.of("term_alpha",.6)))));
        var path=new Context(root,Files.createDirectory(root.resolve("pathway")),Map.of("type","pathway_network","hypothesis","H"),study,Map.of(),Map.of("gsea",original.outputDirectory()));
        var graph=new StudyCliExecutor().execute(path);assertEquals(State.COMPLETE,graph.state(),graph.summary());assertEquals(0,graph.details().get("nodes"),"Native BY q=.75 must not become recomputed BH=.5");
        assertEquals("symbol",graph.details().get("membership_gene_id_type"));assertEquals("human",graph.details().get("species"));
        var inventory=new Context(root,Files.createDirectory(root.resolve("inventory")),Map.of("type","finding_inventory","scope","study","datasets",List.of()),study,Map.of(),Map.of("gsea",original.outputDirectory()));var inventoried=new StudyFindingInventory().execute(inventory);assertEquals(0L,inventoried.details().get("candidates"));
        for(String line:Files.readAllLines(inventory.outputDirectory().resolve("finding-inventory.jsonl"))){
            var finding=StudyPublicEvidence.map(SimpleJson.parse(line));var observation=StudyPublicEvidence.map(StudyPublicEvidence.list(finding.get("observations")).get(0));
            assertEquals(.75,((Number)observation.get("full_family_adjusted_p")).doubleValue(),1e-13);assertEquals("BY",observation.get("adjustment"));
            assertTrue(String.valueOf(observation.get("required_manuscript_caveat")).contains("not Broad"));assertTrue(observation.containsKey("leading_edge_genes"));
        }
        var allStudy=new LinkedHashMap<>(study);allStudy.put("hypotheses",List.of(Map.of("id","H","analysis",Map.of("gsea",p,"network",Map.of("term_alpha",1.0)))));
        var allGraph=new Context(root,Files.createDirectory(root.resolve("all-pathways")),Map.of("id","all-pathways","type","pathway_network","hypothesis","H"),allStudy,Map.of(),Map.of("gsea",original.outputDirectory()));var allResult=new StudyCliExecutor().execute(allGraph);seal(allGraph,allResult);
        var modules=new Context(root,Files.createDirectory(root.resolve("pathway-modules")),Map.of("id","pathway-modules","type","network_modules","hypothesis","H"),allStudy,Map.of(),Map.of("graph",allGraph.outputDirectory()));var moduleResult=new StudyCliExecutor().execute(modules);seal(modules,moduleResult);
        var linked=new Context(root,Files.createDirectory(root.resolve("linked-inventory")),inventory.operation(),allStudy,Map.of(),Map.of("gsea",original.outputDirectory(),"modules",modules.outputDirectory()));new StudyFindingInventory().execute(linked);
        var linkedLines=Files.readAllLines(linked.outputDirectory().resolve("finding-inventory.jsonl"));
        for(String line:linkedLines){
            var observations=StudyPublicEvidence.list(StudyPublicEvidence.map(SimpleJson.parse(line)).get("observations"));
            assertTrue(observations.stream().map(StudyPublicEvidence::map).anyMatch(row->"network_membership".equals(row.get("kind"))),"Pathway memberships must link to namespaced GSEA term dossiers");
        }
    }
    @Test void rankingOnlyStudyCliPlansExecutesAndReusesWithoutAssociationOrMetaRefits() throws Exception {
        var p=ranking();var study=Map.of("schema_version",1,"project",Map.of("id","ranked-only","title","Ranked-only GSEA","description","Synthetic exact-ranking protocol"),"resources",Map.of("directory","fixture-resources","network","offline","downloads","deny"),"datasets",List.of(),
            "hypotheses",List.of(Map.of("id","H","description","Complete GSEA and missing pathway work","analysis",Map.of("mode","manual","goals",List.of("ranked_gsea","pathway_network","network_modules","module_gene_ontology"),"gsea",p,"network",Map.of("term_alpha",.6)))));
        Path input=root.resolve("study.json");StudyPublicEvidence.write(input,study);var errors=new ByteArrayOutputStream();
        for(int i=0;i<2;i++)assertEquals(0,JLinAlgCli.run(new String[]{"study","auto",input.toString(),"--offline","--actor-type","external","--actor-id","synthetic-protocol-fixture"},new PrintStream(new ByteArrayOutputStream()),new PrintStream(errors)),errors.toString());
        var status=StudyYaml.loadArtifact(root.resolve("state/project-status.yaml"));assertEquals("completed",status.get("state"));Path run=root.resolve("runs").resolve(String.valueOf(status.get("run_id"))).resolve("operations");
        try(var paths=Files.list(run)){for(Path dir:paths.toList()){var manifest=StudyYaml.loadArtifact(dir.resolve("manifest.yaml"));assertEquals("complete",manifest.get("state"),manifest.toString());assertEquals(true,manifest.get("reused_from_cache"));assertFalse(List.of("association","meta_analysis","participant_qc","network_analysis").contains(manifest.get("operation_type")));}}
        assertEquals(0,JLinAlgCli.run(new String[]{"study","verify","--project",root.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(errors)),errors.toString());
    }
    @Test void selectedRankingUnknownDenominatorAndPhenotypeNullWaitWithoutSubstitution() throws Exception {
        var p=ranking();p.put("ranking",Map.of("file","ranks.tsv","coverage","selected_only","tested_genes",4));var limited=context("selected",p,Map.of());assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(limited).state());assertFalse(Files.exists(limited.outputDirectory().resolve("ranked-gsea.tsv")));
        p.put("ranking",Map.of("file","ranks.tsv","coverage","complete","tested_genes",40000));assertTrue(new StudyCliExecutor().execute(context("mismatch",p,Map.of())).summary().contains("tested_genes"));
        p.put("null_model","phenotype");var requested=new StudyCliExecutor().execute(context("phenotype",p,Map.of()));assertEquals(State.WAITING_FOR_INPUT,requested.state());assertTrue(requested.summary().contains("cannot substitute"));assertEquals(false,StudyPublicEvidence.map(requested.details().get("method_report")).get("performed"));
    }
    @Test void primaryRankingIncludesNonsignificantFeaturesAndNeverChoosesBestProbe() throws Exception {
        Files.writeString(root.resolve("results.tsv"),"feature_id\tbeta\tse\tp_value\nf1\t1\t1\t.5\nf2\t3\t1\t.5\nf3\t-1\t1\t.8\nf4\t4\t1\t1\n");Files.writeString(root.resolve("annotation.tsv"),"feature_id\tgene_symbol\nf1\tA\nf2\tA\nf3\tB\nf4\tC\n");Files.writeString(root.resolve("sets.gmt"),"T1\tOne\tA\tB\nT2\tTwo\tC\n");
        var definition=Map.of("id","meta","kind","meta_analysis","files",Map.of("results","results.tsv","feature_annotation","annotation.tsv"),"scope",Map.of("species","human"),"method",Map.of("name","External synthetic effects"),"family",Map.of("id","full-source","coverage","complete","tested_count",4,"adjustment","BH"));
        var p=new LinkedHashMap<String,Object>(Map.of("gene_sets","sets.gmt","species","human","minimum_size",1,"maximum_size",2));var study=Map.of("datasets",List.of(),"resources",Map.of("directory","fixture-resources","network","offline","downloads","deny"),"hypotheses",List.of(Map.of("id","H","analysis",Map.of("supplied_results",List.of(definition),"gsea",p))));
        var imported=new Context(root,Files.createDirectory(root.resolve("meta")),Map.of("id","meta","type","meta_analysis","hypothesis","H","supplied_result_id","meta"),study,Map.of(),Map.of());var importedResult=new StudyCliExecutor().execute(imported);seal(imported,importedResult);
        var blocked=new Context(root,Files.createDirectory(root.resolve("duplicate-gene")),Map.of("id","gsea-blocked","type","ranked_gsea","hypothesis","H","datasets",List.of()),study,Map.of(),Map.of("meta",imported.outputDirectory()));var unresolved=new StudyCliExecutor().execute(blocked);assertEquals(State.WAITING_FOR_INPUT,unresolved.state());assertTrue(unresolved.summary().contains("Multiple measured features"));
        p.put("collapse","median");var analyzed=new Context(root,Files.createDirectory(root.resolve("median")),Map.of("id","gsea-median","type","ranked_gsea","hypothesis","H","datasets",List.of()),study,Map.of(),Map.of("meta",imported.outputDirectory()));var result=new StudyCliExecutor().execute(analyzed);assertEquals(State.COMPLETE,result.state(),result.summary());assertEquals(4,result.details().get("source_estimable_features"));assertEquals(3,result.details().get("ranked_genes"));assertEquals(false,result.details().get("source_FDR_filter_applied"));
        var genes=DelimitedData.readResult(analyzed.outputDirectory().resolve("ranked-genes.tsv"));var a=genes.rows().stream().filter(row->row[0].equals("A")).findFirst().orElseThrow();assertEquals(2,Double.parseDouble(a[1]),0);
    }
    @Test void cohortsHaveSeparateRankingsAndPathwayGraphsUntilAPrimaryPoolExists() throws Exception {
        var study=Map.of("schema_version",1,"project",Map.of("id","separate","title","Separate ranks","description","Synthetic planner scopes"),"resources",Map.of("directory","fixture-resources","network","offline","downloads","deny"),"datasets",List.of(
            Map.of("id","a","cohort","A","type","expression","stage","association_results","files",Map.of("results","a.tsv")),Map.of("id","b","cohort","B","type","expression","stage","association_results","files",Map.of("results","b.tsv"))),
            "hypotheses",List.of(Map.of("id","H","description","No implicit pooling","analysis",Map.of("mode","manual","goals",List.of("ranked_gsea","pathway_network")))));
        Path input=root.resolve("plan.yaml");StudyPublicEvidence.write(input,study);var plan=org.jlinalg.study.StudyPlanner.compile(org.jlinalg.study.StudyResolver.resolve(input));var operations=StudyPublicEvidence.list(plan.document().get("operations")).stream().map(StudyPublicEvidence::map).toList();
        assertEquals(2,operations.stream().filter(op->"ranked_gsea".equals(op.get("type"))).count());assertEquals(2,operations.stream().filter(op->"pathway_network".equals(op.get("type"))).count());assertTrue(operations.stream().filter(op->"pathway_network".equals(op.get("type"))).allMatch(op->StudyPublicEvidence.list(op.get("depends_on")).size()==1));
    }
    @Test void rankingAndCollectionFileVersionsEnterCacheIdentity() throws Exception {
        var p=ranking();var c=context("cache",p,Map.of());var before=StudyGseaResources.inputHashes(root,c.study(),c.operation());Files.writeString(root.resolve("ranks.tsv"),Files.readString(root.resolve("ranks.tsv")).replace("A\t4","A\t5"));var after=StudyGseaResources.inputHashes(root,c.study(),c.operation());assertNotEquals(before.get("gsea:ranking"),after.get("gsea:ranking"));assertEquals(before.get("gsea:gene_sets"),after.get("gsea:gene_sets"));
    }
    @Test void selectedOnlyPrimaryCannotBeReinterpretedAsACompleteRanking() throws Exception {
        var p=ranking();p.remove("ranking");Files.writeString(root.resolve("selected.tsv"),"feature_id\tbeta\tse\tp_value\tadjusted_p\nf1\t1\t.1\t.000001\t.4\n");
        var definition=Map.of("id","meta","kind","meta_analysis","files",Map.of("results","selected.tsv"),"scope",Map.of("species","human"),"method",Map.of("name","External selected export"),"family",Map.of("id","original-family","coverage","selected_only","tested_count",400000,"adjustment","BH","adjusted_p_from_full_family",true));
        var study=Map.of("datasets",List.of(),"resources",Map.of("directory","fixture-resources","network","offline","downloads","deny"),"hypotheses",List.of(Map.of("id","H","analysis",Map.of("gsea",p,"supplied_results",List.of(definition)))));
        var imported=new Context(root,Files.createDirectory(root.resolve("selected-meta")),Map.of("id","meta","type","meta_analysis","hypothesis","H","supplied_result_id","meta"),study,Map.of(),Map.of());var result=new StudyCliExecutor().execute(imported);seal(imported,result);assertEquals(State.COMPLETE,result.state());
        var gsea=new Context(root,Files.createDirectory(root.resolve("gsea-selected")),Map.of("id","gsea","type","ranked_gsea","hypothesis","H","datasets",List.of()),study,Map.of(),Map.of("meta",imported.outputDirectory()));var blocked=new StudyCliExecutor().execute(gsea);assertEquals(State.WAITING_FOR_INPUT,blocked.state());assertTrue(blocked.summary().contains("selected-only"));assertFalse(Files.exists(gsea.outputDirectory().resolve("ranked-gsea.tsv")));
        var changed=new LinkedHashMap<String,Object>(definition);var family=new LinkedHashMap<>(StudyPublicEvidence.map(definition.get("family")));family.put("coverage","complete");changed.put("family",family);
        var relabeledStudy=new LinkedHashMap<String,Object>(study);relabeledStudy.put("hypotheses",List.of(Map.of("id","H","analysis",Map.of("gsea",p,"supplied_results",List.of(changed)))));
        var relabeled=new Context(root,Files.createDirectory(root.resolve("relabeled-meta")),imported.operation(),relabeledStudy,Map.of(),Map.of());var importedAgain=new StudyCliExecutor().execute(relabeled);seal(relabeled,importedAgain);
        var retry=new Context(root,Files.createDirectory(root.resolve("relabeled-gsea")),gsea.operation(),relabeledStudy,Map.of(),Map.of("meta",relabeled.outputDirectory()));var inconsistent=new StudyCliExecutor().execute(retry);assertEquals(State.WAITING_FOR_INPUT,inconsistent.state());assertTrue(inconsistent.summary().contains("denominator exceeds"));
    }
    @Test void emptyResourceDiffersFromAnObservedNoEligibleTermFamily() throws Exception {
        var p=ranking();Files.writeString(root.resolve("sets.gmt"),"");var empty=new StudyCliExecutor().execute(context("empty-resource",p,Map.of()));assertEquals(State.WAITING_FOR_INPUT,empty.state());assertTrue(empty.summary().contains("collection is empty"));
        Files.writeString(root.resolve("sets.gmt"),"unmatched\tUnmatched\tX\tY\n");var absent=new StudyCliExecutor().execute(context("no-eligible-terms",p,Map.of()));assertEquals(State.COMPLETE,absent.state());assertEquals(0,absent.details().get("tested_terms"));assertEquals(false,StudyPublicEvidence.map(absent.details().get("method_report")).get("performed"));
    }
    @Test void variantStatisticsDoNotBecomeIndependentNearestGeneScores() throws Exception {
        var p=ranking();p.remove("ranking");Files.writeString(root.resolve("variants.tsv"),"feature_id\tbeta\tse\tp_value\nrs1\t.1\t.2\t.6\nrs2\t.2\t.2\t.3\n");
        var definition=Map.of("id","meta","kind","meta_analysis","files",Map.of("results","variants.tsv"),"scope",Map.of("species","human","feature_id_type","variant","modality","gwas"),"method",Map.of("name","External GWAS"),"family",Map.of("id","full-variants","coverage","complete","tested_count",2,"adjustment","BH"));
        var study=Map.of("datasets",List.of(),"resources",Map.of("directory","fixture-resources","network","offline","downloads","deny"),"hypotheses",List.of(Map.of("id","H","analysis",Map.of("gsea",p,"supplied_results",List.of(definition)))));
        var imported=new Context(root,Files.createDirectory(root.resolve("gwas-meta")),Map.of("id","meta","type","meta_analysis","hypothesis","H","supplied_result_id","meta"),study,Map.of(),Map.of());var importedResult=new StudyCliExecutor().execute(imported);seal(imported,importedResult);
        var gsea=new Context(root,Files.createDirectory(root.resolve("gwas-gsea")),Map.of("id","gsea","type","ranked_gsea","hypothesis","H","datasets",List.of()),study,Map.of(),Map.of("meta",imported.outputDirectory()));var result=new StudyCliExecutor().execute(gsea);assertEquals(State.WAITING_FOR_INPUT,result.state());assertTrue(result.summary().contains("LD-aware"));assertFalse(Files.exists(gsea.outputDirectory().resolve("ranked-gsea.tsv")));
    }
    @Test void builtinHumanCollectionsRejectMismatchedSpeciesMetadata() throws Exception {
        var p=ranking();p.remove("gene_sets");p.put("collection","GO");Path db=root.resolve("fixture-resources/enrichment/go-human");assertTrue(db.normalize().startsWith(root.normalize()));Files.createDirectories(db);Files.writeString(db.resolve("data.gmt"),"GO:1\tSynthetic wrong-species fixture\tA\tB\n");
        Files.writeString(db.resolve("manifest.properties"),"format_version=1\nprovider=GO\nspecies=mouse\ngene_id_type=symbol\ndata_format=gmt\ndata_file=data.gmt\nsha256.data.gmt="+FollowupSupport.hash(db.resolve("data.gmt"))+"\n");
        var c=context("species-mismatch",p,Map.of());var failure=assertThrows(java.io.IOException.class,()->new StudyCliExecutor().execute(c));assertTrue(failure.getMessage().contains("species"));assertFalse(Files.exists(c.outputDirectory().resolve("ranked-gsea.tsv")));
    }
}
