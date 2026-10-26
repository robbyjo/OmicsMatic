/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.network.NetworkModules;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyOperationExecutor.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyNetworksTest {
    @TempDir Path root;
    private Context context(String folder,String type,Map<String,Object> network,List<Object> datasets,Map<String,Path> deps) throws Exception {
        var study=Map.of("datasets",datasets,"resources",Map.of("directory","fixture-resources","network","offline","downloads","deny"),
            "hypotheses",List.of(Map.of("id","H","covariates",List.of("sex"),"analysis",Map.of("network",network))));
        return new Context(root,Files.createDirectory(root.resolve(folder)),Map.of("id",folder,"type",type,"hypothesis","H","datasets",datasets.isEmpty()?List.of():List.of("d"),"dataset_id","d"),study,Map.of(),deps);
    }
    private static void seal(Context c,org.jlinalg.study.StudyOperationExecutor.Result result) throws Exception {
        var hashes=new LinkedHashMap<String,Object>();try(var paths=Files.walk(c.outputDirectory())){for(Path path:paths.filter(Files::isRegularFile).toList())hashes.put(c.outputDirectory().relativize(path).toString().replace('\\','/'),FollowupSupport.hash(path));}
        StudyPublicEvidence.write(c.outputDirectory().resolve("manifest.yaml"),Map.of("operation_id",c.operation().get("id"),"operation_type",c.operation().get("type"),"hypothesis","H","state",result.state().value(),"output_sha256",hashes,"details",result.details()));
    }
    private List<Object> matrix() throws Exception {
        int[] z={1,1,1,1,-1,-1,-1,-1},x={1,1,-1,-1,1,1,-1,-1},y={1,-1,1,-1,1,-1,1,-1};
        StringBuilder matrix=new StringBuilder("feature_id"),pheno=new StringBuilder("id\tsex\n"),annotation=new StringBuilder("feature_id\tgene_symbol\n");
        for(int i=0;i<8;i++){matrix.append("\ts").append(i);pheno.append("s").append(i).append('\t').append(z[i]>0?"F":"M").append('\n');}matrix.append('\n');
        for(int j=0;j<5;j++){matrix.append("g").append(j);for(int i=0;i<8;i++)matrix.append('\t').append(switch(j){case 0->10*z[i]+x[i];case 1->5*z[i]+2*x[i];case 2->-4*z[i]-x[i];case 3->2*z[i]+y[i];default->3;});matrix.append('\n');annotation.append("g").append(j).append("\tA").append(j).append('\n');}
        Files.writeString(root.resolve("matrix.tsv"),matrix);Files.writeString(root.resolve("pheno.tsv"),pheno);Files.writeString(root.resolve("annotation.tsv"),annotation);
        return List.of(Map.of("id","d","type","expression","stage","processed_matrix","species","human","cohort","A","files",Map.of("matrix","matrix.tsv","phenotype","pheno.tsv","annotation","annotation.tsv")));
    }
    @Test void covariateResidualNetworkRetainsNegativeEdgesConstantDispositionAndStableScope() throws Exception {
        var p=Map.<String,Object>of("minimum_samples",8,"categorical_covariates",List.of("sex"),"correlation","pearson","bootstraps",10,"independent_samples",true,"preprocessing","Synthetic orthogonal feature vectors");
        var datasets=matrix();var c=context("native","network_analysis",p,datasets,Map.of());String before=FollowupSupport.hash(root.resolve("matrix.tsv"));
        var result=new StudyCliExecutor().execute(c);assertEquals(State.COMPLETE,result.state(),result.details().toString());seal(c,result);
        var edges=DelimitedData.readResult(c.outputDirectory().resolve("network-edges.tsv"));assertEquals(3,edges.rows().size());
        for(String[] row:edges.rows())assertEquals(row[0].equals("g0")&&row[1].equals("g1")?1:-1,Double.parseDouble(row[edges.column("weight")]),1e-12);
        assertEquals(4,result.details().get("eligible_features"));assertEquals(8,result.details().get("analysis_samples"));
        assertTrue(Files.readString(c.outputDirectory().resolve("feature-qc.tsv")).contains("constant_or_fully_explained_by_covariates"));
        assertEquals(before,FollowupSupport.hash(root.resolve("matrix.tsv")));
        var repeat=context("repeat","network_analysis",p,datasets,Map.of());new StudyCliExecutor().execute(repeat);
        assertEquals(Files.readString(c.outputDirectory().resolve("edge-stability.tsv")),Files.readString(repeat.outputDirectory().resolve("edge-stability.tsv")));
        var modules=context("modules","network_modules",p,datasets,Map.of("network",c.outputDirectory()));var grouped=new StudyCliExecutor().execute(modules);
        assertEquals(State.COMPLETE,grouped.state());assertEquals(2,grouped.details().get("connected_components"));assertEquals(1,grouped.details().get("maximal_cliques"));
        assertTrue(Files.readString(modules.outputDirectory().resolve("network-modules.tsv")).contains("maximal_clique"));
    }
    @Test void bootstrapRequiresIndependentUnitsAndWorkBoundsNeverSelectTopFeatures() throws Exception {
        var data=matrix();var p=Map.<String,Object>of("minimum_samples",8,"categorical_covariates",List.of("sex"),"bootstraps",10);
        var waiting=new StudyCliExecutor().execute(context("dependent","network_analysis",p,data,Map.of()));assertEquals(State.WAITING_FOR_INPUT,waiting.state());assertTrue(waiting.summary().contains("independent_samples"));
        var bounded=new StudyCliExecutor().execute(context("bounded","network_analysis",Map.of("max_features",2),data,Map.of()));assertEquals(State.WAITING_FOR_INPUT,bounded.state());assertFalse(Files.exists(root.resolve("bounded/network-nodes.tsv")));
    }
    @Test void tiedSpearmanAndListwiseMissingSamplesHaveExplicitScope() throws Exception {
        var rho=StudyNetworks.correlations(new double[][]{{1,2},{1,2},{2,1},{2,1}},"spearman");assertEquals(-1,rho[0][1],1e-14);
        var data=matrix();Files.writeString(root.resolve("pheno.tsv"),Files.readString(root.resolve("pheno.tsv")).replace("s0\tF","s0\tNA"));
        var c=context("missing-sample","network_analysis",Map.of("minimum_samples",8,"categorical_covariates",List.of("sex")),data,Map.of());var result=new StudyCliExecutor().execute(c);
        assertEquals(State.WAITING_FOR_INPUT,result.state());assertEquals(7,result.details().get("analysis_samples"));assertTrue(Files.readString(c.outputDirectory().resolve("sample-exclusions.tsv")).contains("missing_covariate"));
        assertTrue(result.outputs().contains("analysis-samples.tsv"),"Waiting-state participant audits must also be sealed");
    }
    @Test void resourceCacheIdentityTracksActualLocalGoFilesAndMissingDirectionWaits() throws Exception {
        Path db=root.resolve("fixture-resources/enrichment/go-human");assertTrue(db.normalize().startsWith(root.normalize()));Files.createDirectories(db);Files.writeString(db.resolve("data.gmt"),"GO:1\tSynthetic\tA\n");
        Files.writeString(db.resolve("manifest.properties"),"sha256.data.gmt="+FollowupSupport.hash(db.resolve("data.gmt"))+"\n");
        var c=context("resource-key","module_gene_ontology",Map.of(),List.of(),Map.of());var before=StudyNetworkResources.inputHashes(root,c.study(),c.operation());Files.writeString(db.resolve("data.gmt"),"GO:1\tSynthetic\tB\n");
        var after=StudyNetworkResources.inputHashes(root,c.study(),c.operation());assertNotEquals(before.get("module_go:sha256.data.gmt"),after.get("module_go:sha256.data.gmt"));
        var source=externalGraph("ambiguous-direction",true,Map.of());StudyPublicEvidence.write(source.outputDirectory().resolve("supplied-analysis.yaml"),Map.of("method",Map.of("name","External graph")));
        var modules=context("direction-wait","network_modules",Map.of(),List.of(),Map.of("source",source.outputDirectory()));var result=new StudyCliExecutor().execute(modules);assertEquals(State.WAITING_FOR_INPUT,result.state());assertTrue(result.summary().contains("edge_direction"));
    }
    @Test void maximalCliquesMatchExhaustiveIndependentEnumerationAcrossAllFiveNodeGraphs() {
        for(int mask=0;mask<(1<<10);mask++) {
            var graph=new ArrayList<BitSet>();for(int i=0;i<5;i++)graph.add(new BitSet());int bit=0;
            for(int a=0;a<5;a++)for(int b=a+1;b<5;b++,bit++)if((mask&(1<<bit))!=0){graph.get(a).set(b);graph.get(b).set(a);}
            var actual=NetworkModules.maximalCliques(graph,2,1000,100000,20);assertTrue(actual.complete());Set<Set<Integer>> expected=new java.util.HashSet<>();
            for(int subset=1;subset<32;subset++){if(Integer.bitCount(subset)<2)continue;Set<Integer> members=new java.util.HashSet<>();for(int i=0;i<5;i++)if((subset&(1<<i))!=0)members.add(i);boolean clique=true,maximal=true;
                for(int a:members)for(int b:members)if(a!=b&&!graph.get(a).get(b))clique=false;
                for(int i=0;i<5;i++)if(!members.contains(i)){boolean extendsClique=true;for(int a:members)extendsClique&=graph.get(i).get(a);if(extendsClique)maximal=false;}
                if(clique&&maximal)expected.add(members);
            }
            assertEquals(expected,new java.util.HashSet<>(actual.members().stream().map(Set::copyOf).toList()),"graph mask "+mask);
        }
    }
    private Context externalGraph(String folder,boolean supplyModules,Map<String,Object> policy) throws Exception {
        var c=context(folder,"network_analysis",policy,List.of(),Map.of());StringBuilder nodes=new StringBuilder("node_id\n"),edges=new StringBuilder("source\ttarget\tweight\n"),annotations=new StringBuilder("feature_id\tgene_id\tgene_symbol\tmapping_status\n"),modules=new StringBuilder("node_id\tmodule_id\n");
        for(int i=1;i<=30;i++){nodes.append("g").append(i).append('\n');annotations.append("g").append(i).append("\t\tA").append(i).append("\tmissing_location\n");if(i<=6)modules.append("g").append(i).append("\tM1\n");}
        for(int i=11;i<=13;i++)modules.append("g").append(i).append("\tM2\n");
        // Two overlapping maximal triangles plus isolates; no significance-selected graph.
        for(String pair:List.of("g1\tg2","g1\tg3","g2\tg3","g2\tg4","g3\tg4"))edges.append(pair).append("\t.8\n");
        Files.writeString(c.outputDirectory().resolve("network-nodes.tsv"),nodes);Files.writeString(c.outputDirectory().resolve("network-edges.tsv"),edges);Files.writeString(c.outputDirectory().resolve("feature-annotation.tsv"),annotations);
        if(supplyModules)Files.writeString(c.outputDirectory().resolve("network-modules.tsv"),modules);
        StudyPublicEvidence.write(c.outputDirectory().resolve("network-analysis.yaml"),Map.of("network_type","molecular_correlation","species","human"));
        seal(c,new org.jlinalg.study.StudyOperationExecutor.Result(State.COMPLETE,"fixture",List.of(),Map.of()));return c;
    }
    @Test void suppliedModulesAreReusedAndJointGoUsesAllMappedNetworkNodesIncludingIsolates() throws Exception {
        var graph=externalGraph("external",true,Map.of());var modules=context("reused-modules","network_modules",Map.of(),List.of(),Map.of("graph",graph.outputDirectory()));var grouped=new StudyCliExecutor().execute(modules);seal(modules,grouped);
        assertEquals("supplied_memberships_reused",grouped.details().get("module_origin"));assertEquals(9,DelimitedData.readResult(modules.outputDirectory().resolve("network-modules.tsv")).rows().size());
        Path database=root.resolve("fixture-resources/enrichment/go-human");assertTrue(database.normalize().startsWith(root.normalize()));Files.createDirectories(database);StringBuilder gmt=new StringBuilder();for(int term=0;term<3;term++){gmt.append("GO:").append(term+1).append("\tSynthetic fixture");for(int i=term*10+1;i<=term*10+10;i++)gmt.append("\tA").append(i);gmt.append('\n');}Files.writeString(database.resolve("data.gmt"),gmt);
        Files.writeString(database.resolve("manifest.properties"),"format_version=1\nprovider=GO\nspecies=human\ngene_id_type=symbol\ndata_format=gmt\ndata_file=data.gmt\nsha256.data.gmt="+FollowupSupport.hash(database.resolve("data.gmt"))+"\n");
        var go=context("go","module_gene_ontology",Map.of(),List.of(),Map.of("modules",modules.outputDirectory()));var result=new StudyCliExecutor().execute(go);
        assertEquals(State.COMPLETE,result.state(),result.details().toString());assertEquals(30,result.details().get("background_genes"));assertEquals(6,result.details().get("tested_module_term_pairs"));seal(go,result);
        var table=DelimitedData.readResult(go.outputDirectory().resolve("module-gene-ontology.tsv"));var row=table.rows().stream().filter(r->r[table.column("term_id")].equals("GO:1")).findFirst().orElseThrow();
        assertEquals(210.0/593775,Double.parseDouble(row[table.column("p_value")]),1e-12);assertEquals(6*210.0/593775,Double.parseDouble(row[table.column("adjusted_p")]),1e-12,"Adjustment must use both modules' terms, not a separate per-module family");
        var meta=context("nonsignificant-meta","meta_analysis",Map.of(),List.of(),Map.of());Files.writeString(meta.outputDirectory().resolve("meta-analysis.tsv"),"feature_id\tbeta\tse\tp_value\tterm\tstatus\ng1\t.3\t.1\t.5\tpooled\tok\n");seal(meta,new org.jlinalg.study.StudyOperationExecutor.Result(State.COMPLETE,"fixture",List.of(),Map.of()));
        var inventory=context("network-inventory","finding_inventory",Map.of(),List.of(),Map.of("meta",meta.outputDirectory(),"modules",modules.outputDirectory(),"go",go.outputDirectory()));var inventoried=new StudyFindingInventory().execute(inventory);
        assertEquals(0L,inventoried.details().get("candidates"),"Module GO must not promote a nonsignificant association");
        var dossier=StudyPublicEvidence.map(SimpleJson.parse(Files.readString(inventory.outputDirectory().resolve("finding-inventory.jsonl")).strip()));var observations=StudyPublicEvidence.list(dossier.get("observations")).stream().map(StudyPublicEvidence::map).toList();
        assertTrue(observations.stream().anyMatch(item->"network_membership".equals(item.get("kind"))));assertTrue(observations.stream().anyMatch(item->"module_GO_context".equals(item.get("kind"))));
    }
    @Test void boundedCliqueSearchRetainsPartialEvidenceAndBlocksUnresolvedJointGoFamily() throws Exception {
        var policy=Map.<String,Object>of("max_cliques",1);var graph=externalGraph("truncated-graph",false,policy);var modules=context("truncated","network_modules",policy,List.of(),Map.of("graph",graph.outputDirectory()));var grouped=new StudyCliExecutor().execute(modules);seal(modules,grouped);
        assertEquals(State.WAITING_FOR_INPUT,grouped.state());assertEquals(false,grouped.details().get("clique_search_complete"));assertEquals("max_cliques",grouped.details().get("search_limit"));
        var go=context("limited-go","module_gene_ontology",policy,List.of(),Map.of("modules",modules.outputDirectory()));var result=new StudyCliExecutor().execute(go);assertEquals(State.WAITING_FOR_INPUT,result.state());assertTrue(result.summary().contains("Incomplete clique"));
    }
    @Test void pathwayOverlapUsesSourceFdrAndExactMembershipInsteadOfMolecularCorrelation() throws Exception {
        Files.writeString(root.resolve("ranked.tsv"),"term_id\tp_value\tadjusted_p\nT1\t.00001\t.01\nT2\t.00001\t.02\nT3\t.00001\t.4\n");
        Files.writeString(root.resolve("sets.gmt"),"T1\tOne\tA\tB\tC\nT2\tTwo\tA\tB\tD\nT3\tNot significant\tA\tB\tC\n");
        var definition=Map.of("id","gsea","kind","ranked_gsea","files",Map.of("results","ranked.tsv","gene_sets","sets.gmt"),"scope",Map.of("species","human"),"method",Map.of("name","External ranked enrichment"),"family",Map.of("id","all-terms","coverage","selected_only","tested_count",100,"adjustment","BH","adjusted_p_from_full_family",true));
        var imported=context("gsea","ranked_gsea",Map.of(),List.of(),Map.of());var study=new LinkedHashMap<>(imported.study());study.put("hypotheses",List.of(Map.of("id","H","analysis",Map.of("supplied_results",List.of(definition),"network",Map.of("pathway_threshold",.5)))));
        var source=new Context(root,imported.outputDirectory(),Map.of("id","gsea","type","ranked_gsea","hypothesis","H","supplied_result_id","gsea"),study,Map.of(),Map.of());var result=new StudyCliExecutor().execute(source);seal(source,result);assertEquals(State.COMPLETE,result.state());
        var graph=new Context(root,Files.createDirectory(root.resolve("pathway")),Map.of("type","pathway_network","hypothesis","H"),study,Map.of(),Map.of("gsea",source.outputDirectory()));var plotted=new StudyCliExecutor().execute(graph);
        assertEquals(State.COMPLETE,plotted.state());assertEquals(2,plotted.details().get("nodes"));var edges=DelimitedData.readResult(graph.outputDirectory().resolve("network-edges.tsv"));assertEquals(1,edges.rows().size());assertEquals(.5,Double.parseDouble(edges.rows().get(0)[edges.column("weight")]),0);
    }
    @Test void plannerKeepsPathwayOnlyWorkSeparateAndBuildsOneMolecularGraphPerDataset() throws Exception {
        var analysis=new LinkedHashMap<String,Object>(Map.of("mode","manual","goals",List.of("pathway_network","network_modules","module_gene_ontology"),"supplied_results",List.of(Map.of("id","gsea","kind","ranked_gsea","files",Map.of("results","ranked.tsv"),"scope",Map.of("species","human"),"method",Map.of("name","External ranked enrichment")))));
        var study=new LinkedHashMap<String,Object>(Map.of("schema_version",1,"project",Map.of("id","planning","title","Network scope","description","Synthetic planner fixture"),"resources",Map.of("directory","fixture-resources","network","offline","downloads","deny"),"datasets",List.of(),"hypotheses",List.of(Map.of("id","H","description","Keep graph types separate","analysis",analysis))));
        Path source=root.resolve("study.yaml");StudyPublicEvidence.write(source,study);var plan=org.jlinalg.study.StudyPlanner.compile(org.jlinalg.study.StudyResolver.resolve(source));
        var operations=StudyPublicEvidence.list(plan.document().get("operations")).stream().map(StudyPublicEvidence::map).toList();
        assertEquals(1,operations.stream().filter(op->"network_modules".equals(op.get("type"))).count());assertFalse(operations.stream().anyMatch(op->"network_analysis".equals(op.get("type"))));
        analysis.remove("supplied_results");analysis.put("goals",List.of("network_analysis","network_modules","module_gene_ontology"));
        study.put("cohorts",List.of(Map.of("id","A"),Map.of("id","B")));study.put("datasets",List.of(Map.of("id","a","cohort","A","type","expression","stage","processed_matrix","files",Map.of("matrix","a.tsv")),Map.of("id","b","cohort","B","type","expression","stage","processed_matrix","files",Map.of("matrix","b.tsv"))));
        StudyPublicEvidence.write(source,study);plan=org.jlinalg.study.StudyPlanner.compile(org.jlinalg.study.StudyResolver.resolve(source));operations=StudyPublicEvidence.list(plan.document().get("operations")).stream().map(StudyPublicEvidence::map).toList();
        assertEquals(2,operations.stream().filter(op->"network_analysis".equals(op.get("type"))).count());assertEquals(2,operations.stream().filter(op->"module_gene_ontology".equals(op.get("type"))).count());
        assertTrue(operations.stream().filter(op->"network_analysis".equals(op.get("type"))).allMatch(op->StudyPublicEvidence.list(op.get("datasets")).size()==1));
    }
    @Test void emptyEligibleGraphCompletesWithoutInventingMissingMappingsOrGoTests() throws Exception {
        var graph=context("empty-graph","pathway_network",Map.of(),List.of(),Map.of());Files.writeString(graph.outputDirectory().resolve("network-nodes.tsv"),"node_id\n");Files.writeString(graph.outputDirectory().resolve("network-edges.tsv"),"source\ttarget\tweight\n");StudyPublicEvidence.write(graph.outputDirectory().resolve("network-analysis.yaml"),Map.of("network_type","pathway_gene_overlap","species","human"));seal(graph,new org.jlinalg.study.StudyOperationExecutor.Result(State.COMPLETE,"no selected pathways",List.of(),Map.of()));
        var modules=context("empty-modules","network_modules",Map.of(),List.of(),Map.of("graph",graph.outputDirectory()));var grouped=new StudyCliExecutor().execute(modules);seal(modules,grouped);assertEquals(State.COMPLETE,grouped.state());
        var go=context("empty-go","module_gene_ontology",Map.of(),List.of(),Map.of("modules",modules.outputDirectory()));var result=new StudyCliExecutor().execute(go);assertEquals(State.COMPLETE,result.state());assertEquals(0,result.details().get("tested_module_term_pairs"));assertEquals(false,StudyPublicEvidence.map(result.details().get("method_report")).get("performed"));assertFalse(result.details().containsKey("needs"));assertTrue(DelimitedData.readResult(go.outputDirectory().resolve("module-gene-ontology.tsv")).rows().isEmpty());
    }
}
