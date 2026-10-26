/* SPDX-License-Identifier: GPL-3.0-or-later */
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

class StudyNetworkNullTest {
    @TempDir Path root;
    private static final String EDGES="source\ttarget\tweight\na\tb\t.9\na\tc\t.8\nb\tc\t.75\n";
    private Context fixture(String name,Map<String,Object> settings)throws Exception{
        Path project=Files.createDirectory(root.resolve(name));var policy=new LinkedHashMap<String,Object>(Map.of("exchangeability_rationale","Synthetic fixed graph-label randomization; no biological claim."));policy.putAll(settings);
        var datasets=new ArrayList<Object>();var dependencies=new LinkedHashMap<String,Path>();
        for(String id:List.of("a","b")){
            datasets.add(Map.of("id",id,"type","expression","stage","processed_matrix","cohort",id.toUpperCase(java.util.Locale.ROOT),"species","human","feature_id_type","gene_symbol","genome_build","GRCh38","files",Map.of("matrix",id+".tsv")));
            Path graph=Files.createDirectory(project.resolve(id));Files.writeString(graph.resolve("network-nodes.tsv"),"node_id\na\nb\nc\nd\ne\n");Files.writeString(graph.resolve("network-edges.tsv"),EDGES);StudyPublicEvidence.write(graph.resolve("network-analysis.yaml"),Map.of("dataset_id",id,"network_type","molecular_correlation","correlation","pearson","threshold",.7));seal(graph,"network_analysis",Map.of());dependencies.put(id,graph);
            if(id.equals("a")){Path module=Files.createDirectory(project.resolve("module"));Files.writeString(module.resolve("network-modules.tsv"),"node_id\tmodule_id\tmodule_type\na\tcore\tfrozen\nb\tcore\tfrozen\nc\tcore\tfrozen\nd\toutside\tfrozen\ne\toutside\tfrozen\n");seal(module,"network_modules",Map.of("source_network_output_sha256",StudyPublicEvidence.map(StudyYaml.loadArtifact(graph.resolve("manifest.yaml")).get("output_sha256"))));dependencies.put("modules",module);}
        }
        var h=Map.of("id","H","description","Synthetic label null","outcome","y","inputs",Map.of("discovery",List.of("a"),"replication",List.of("b")),"analysis",Map.of("mode","manual","goals",List.of("network_preservation_null"),"network_preservation_null",policy,"network_preservation",Map.of("references",List.of("dataset:a"),"graph_namespaces",Map.of("dataset:a","HGNC:synthetic","dataset:b","HGNC:synthetic"))));
        var study=Map.<String,Object>of("schema_version",1,"project",Map.of("id","fixture","title","Conditional graph fixture","description","Synthetic tests only."),"datasets",datasets,"hypotheses",List.of(h));
        return new Context(project,Files.createDirectory(project.resolve("output")),Map.of("id","test","type","network_preservation_null","hypothesis","H","datasets",List.of("a","b")),study,Map.of(),dependencies);
    }
    private static void seal(Path dir,String type,Map<String,Object> details)throws Exception{var hashes=new LinkedHashMap<String,Object>();try(var files=Files.list(dir)){for(Path p:files.filter(Files::isRegularFile).filter(p->!p.getFileName().toString().equals("manifest.yaml")).toList())hashes.put(p.getFileName().toString(),FollowupSupport.hash(p));}StudyPublicEvidence.write(dir.resolve("manifest.yaml"),Map.of("operation_type",type,"hypothesis","H","state","complete","output_sha256",hashes,"details",details));}
    private static String[] core(Context c)throws Exception{var table=DelimitedData.readResult(c.outputDirectory().resolve("network-preservation-tests.tsv"));return table.rows().stream().filter(row->row[2].equals("core")).findFirst().orElseThrow();}
    @Test void exactNullUsesWholeSharedUniverseAndReservesNonEstimableFamilyPositions()throws Exception{
        var c=fixture("exact",Map.of());Result r=new StudyCliExecutor().execute(c);assertEquals(State.COMPLETE,r.state(),r.details().toString());var row=core(c);assertEquals("5",row[5]);assertEquals("120",row[9]);assertEquals("12",row[10]);assertEquals(.1,Double.parseDouble(row[11]),1e-15);assertEquals(.3,Double.parseDouble(row[15]),1e-15);assertEquals("2",row[17]);assertEquals("false",row[19]);assertEquals(false,r.details().get("primary_selection_eligible"));
        assertEquals(3,DelimitedData.readResult(c.outputDirectory().resolve("network-null-statistic-counts.tsv")).rows().size());assertEquals(EDGES,Files.readString(c.project().resolve("a/network-edges.tsv")));
    }
    @Test void registeredStrataConditionTheExactTestAndAreContentHashed()throws Exception{
        var c=fixture("strata",Map.of("strata_file","strata.tsv"));Files.writeString(c.project().resolve("strata.tsv"),"node_id\tstratum\na\tcore\nb\tcore\nc\tcore\nd\tother\ne\tother\n");
        var hashes=StudyNetworkResources.inputHashes(c.project(),c.study(),c.operation());new StudyCliExecutor().execute(c);assertEquals("12",core(c)[9]);assertEquals(1,Double.parseDouble(core(c)[11]));
        Files.writeString(c.project().resolve("strata.tsv"),"node_id\tstratum\na\tall\nb\tall\nc\tall\nd\tall\ne\tall\n");assertNotEquals(hashes,StudyNetworkResources.inputHashes(c.project(),c.study(),c.operation()));
    }
    @Test void aMissingStratumDoesNotBecomeAnUnrestrictedPermutation()throws Exception{
        var c=fixture("missing",Map.of("strata_file","strata.tsv"));Files.writeString(c.project().resolve("strata.tsv"),"node_id\tstratum\na\tall\n");Result r=new StudyCliExecutor().execute(c);assertEquals(State.WAITING_FOR_INPUT,r.state());assertEquals("NA",core(c)[11]);assertEquals("NA",core(c)[15]);assertEquals("provisional_unresolved_tests",core(c)[18]);
    }
    @Test void wholeFamilyWorkBoundsNeverSelectAnEasyOrFavorablePrefix()throws Exception{
        var c=fixture("bounded",Map.of("max_work",1));Result r=new StudyCliExecutor().execute(c);assertEquals(State.WAITING_FOR_INPUT,r.state());assertEquals("held_budget",core(c)[20]);assertEquals(0,r.details().get("tested"));assertEquals("NA",core(c)[15]);assertTrue(Files.exists(c.outputDirectory().resolve("prepared-preservation/graph-001-network-edges.tsv")));
    }
    @Test void exactBoundDoesNotSilentlyFallBackAndSampledResultsReplayExactly()throws Exception{
        var c=fixture("exact-bound",Map.of("method","exact","exact_maximum",119));assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(c).state());assertEquals("NA",core(c)[11]);
        var sampled=fixture("sampled",Map.of("method","monte_carlo","draws",9999));new StudyCliExecutor().execute(sampled);assertEquals("monte_carlo",core(sampled)[8]);assertTrue(Double.parseDouble(core(sampled)[11])>0);
        var again=new Context(sampled.project(),Files.createDirectory(sampled.project().resolve("again")),sampled.operation(),sampled.study(),sampled.plan(),sampled.dependencyDirectories());new StudyCliExecutor().execute(again);assertArrayEquals(core(sampled),core(again));
    }
    @Test void missingRationaleAndIncompatibleGraphsKeepRawAndAdjustedInferenceUnavailable()throws Exception{
        var c=fixture("rationale",Map.of());var study=new LinkedHashMap<>(c.study());var h=new LinkedHashMap<>(StudyPublicEvidence.map(StudyPublicEvidence.list(study.get("hypotheses")).get(0)));var a=new LinkedHashMap<>(StudyPublicEvidence.map(h.get("analysis")));a.put("network_preservation_null",Map.of());h.put("analysis",a);study.put("hypotheses",List.of(h));var noRationale=new Context(c.project(),c.outputDirectory(),c.operation(),study,c.plan(),c.dependencyDirectories());assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(noRationale).state());assertEquals("NA",core(c)[11]);
        var bad=fixture("context",Map.of());StudyPublicEvidence.write(bad.project().resolve("b/network-analysis.yaml"),Map.of("dataset_id","b","network_type","molecular_correlation","correlation","spearman","threshold",.7));seal(bad.project().resolve("b"),"network_analysis",Map.of());assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(bad).state());assertEquals("held_source_context",core(bad)[20]);assertEquals("NA",core(bad)[15]);
    }
    @Test void suppliedNullPreservesOriginalCorrectionAndNeverRefitsSourceGraphs()throws Exception{
        var c=fixture("native",Map.of());new StudyCliExecutor().execute(c);var table=DelimitedData.readResult(c.outputDirectory().resolve("network-preservation-tests.tsv"));
        var definition=Map.of("id","external","kind","network_preservation_null","files",Map.of("results","native/output/network-preservation-tests.tsv"),"scope",Map.of(),"method",Map.of("name","Previously completed conditional graph null"),"family",Map.of("id","source-family","coverage","complete","tested_count",2,"adjustment","BY","alpha",.05));
        var study=Map.<String,Object>of("datasets",List.of(),"hypotheses",List.of(Map.of("id","H","analysis",Map.of("supplied_results",List.of(definition)))));
        var imported=new Context(root,Files.createDirectory(root.resolve("imported")),Map.of("id","import","type","network_preservation_null","hypothesis","H","supplied_result_id","external"),study,Map.of(),Map.of());var r=new StudyCliExecutor().execute(imported);assertEquals(State.COMPLETE,r.state(),r.details().toString());assertEquals(false,r.details().get("analysis_executed_by_jlinalg"));assertEquals(false,r.details().get("primary_selection_eligible"));
        var result=DelimitedData.readResult(imported.outputDirectory().resolve("network-preservation-tests.tsv"));assertArrayEquals(table.rows().get(0),result.rows().get(0));
        var row=table.rows().get(0).clone();row[11]="0";assertThrows(java.io.IOException.class,()->StudyNetworkNull.validateSupplied(table.header(),List.<String[]>of(row),definition));
    }
    @Test void plannerRequestsOnlyMissingInferenceAndDoesNotTreatSuppliedTestsAsRawGraphs()throws Exception{
        var c=fixture("plan",Map.of("enabled",true));Path file=c.project().resolve("study.yaml");StudyPublicEvidence.write(file,c.study());var ops=StudyPublicEvidence.list(org.jlinalg.study.StudyPlanner.compile(org.jlinalg.study.StudyResolver.resolve(file)).document().get("operations")).stream().map(StudyPublicEvidence::map).toList();
        assertEquals(1,ops.stream().filter(op->op.get("type").equals("network_preservation_null")).count());assertEquals(2,ops.stream().filter(op->op.get("type").equals("network_analysis")).count());assertEquals(1,ops.stream().filter(op->op.get("type").equals("network_modules")).count());
        var definition=Map.of("id","external","kind","network_preservation_null","files",Map.of("results","previous.tsv"),"scope",Map.of(),"method",Map.of("name","Previous graph null"));var s=new LinkedHashMap<>(c.study());s.put("datasets",List.of());s.put("hypotheses",List.of(Map.of("id","H","description","Only imported tests","analysis",Map.of("mode","manual","goals",List.of("network_preservation_null"),"supplied_results",List.of(definition)))));StudyPublicEvidence.write(file,s);ops=StudyPublicEvidence.list(org.jlinalg.study.StudyPlanner.compile(org.jlinalg.study.StudyResolver.resolve(file)).document().get("operations")).stream().map(StudyPublicEvidence::map).toList();assertFalse(ops.stream().anyMatch(op->List.of("network_analysis","network_modules","network_preservation").contains(op.get("type"))));
    }
    @Test void importedCountsPrecisionAndCorrectionCannotContradictTheDeclaredFamily()throws Exception{
        var c=fixture("validation",Map.of());new StudyCliExecutor().execute(c);var table=DelimitedData.readResult(c.outputDirectory().resolve("network-preservation-tests.tsv"));
        var definition=Map.<String,Object>of("method",Map.of(),"family",Map.of("coverage","complete","tested_count",2,"alpha",.05));
        StudyNetworkNull.validateSupplied(table.header(),table.rows(),definition);
        for(var change:Map.of("universe_nodes","NaN","reference_edges","100","observed_statistic","4","p_resolution",".2","tail_lower_95",".5","adjusted_p",".01","family_size","2000").entrySet()){
            var rows=table.rows().stream().map(String[]::clone).toList();var row=rows.stream().filter(r->r[2].equals("core")).findFirst().orElseThrow();row[table.column(change.getKey())]=change.getValue();
            assertThrows(java.io.IOException.class,()->StudyNetworkNull.validateSupplied(table.header(),rows,definition),change.getKey());
        }
    }
}
