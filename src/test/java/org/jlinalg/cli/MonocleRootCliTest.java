/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class MonocleRootCliTest {
    @TempDir Path temp;
    static Stream<Arguments> references()throws Exception {return Monocle3PseudotimeTest.references();}
    private Map<String,Object> graph(){
        var g=new LinkedHashMap<String,Object>();g.put("cell_ids",List.of("a","b","c"));g.put("cell_coordinates",List.of(List.of(0,0),List.of(4,0),List.of(8,0)));
        g.put("principal_ids",List.of("r"));g.put("principal_coordinates",List.of(List.of(2.1,0)));g.put("roots",List.of("r"));g.put("graph_vertex_ids",List.of("a","b","c"));g.put("edge_weight_mode","weighted");g.put("edges",List.of(Map.of("from",0,"to",1,"weight",3),Map.of("from",1,"to",2,"weight",5)));return g;
    }
    private Map<String,Object> request(String profile){return new LinkedHashMap<>(Map.of("schema_version",1,"mode","ORDER","source_profile",profile,"prepared_graph","graph.json","out","result"));}
    private int root(String... args){return JLinAlgCli.run(args,new PrintStream(new ByteArrayOutputStream(),true,StandardCharsets.UTF_8),new PrintStream(new ByteArrayOutputStream(),true,StandardCharsets.UTF_8));}
    private int run(Map<String,Object> graph,Map<String,Object> request)throws Exception {NextflowFiles.writeJson(temp.resolve("graph.json"),graph);NextflowFiles.writeJson(temp.resolve("request.json"),request);return root("monocle-tools","ORDER","--request",temp.resolve("request.json").toString());}
    private static Map<String,Object> read(Path path)throws Exception {return Monocle3WorkflowCli.read(path,1048576);}
    private List<?> rows(String name)throws Exception {return NextflowFiles.list(NextflowParameters.parse(Files.readString(temp.resolve("result/results/"+name+".json")),1048576,100000));}
    @ParameterizedTest(name="{0}") @MethodSource("references")
    void actualRootOrderMatchesAllOriginalOutcomes(String label,Map<?,?> input,Map<?,?> reference)throws Exception {
        var g=new LinkedHashMap<String,Object>();for(String key:List.of("cell_ids","cell_coordinates","principal_ids","principal_coordinates","roots","graph_vertex_ids","edges"))g.put(key,input.get(key));g.put("edge_weight_mode","weighted");
        boolean returned=(Boolean)reference.get("returned");assertEquals(returned?0:2,run(g,request(NextflowFiles.text(reference.get("version")))),label);
        var state=read(temp.resolve("result/analysis-status.json"));assertEquals(returned,state.get("result_admitted"));assertEquals(returned,Files.isDirectory(temp.resolve("result/results")));
        if(!returned){assertTrue(state.get("cause").toString().contains("original helper fails"));return;}
        compare(reference.get("input_cell_pseudotime"),rows("cell-pseudotime"));compare(reference.get("graph_pseudotime"),rows("graph-pseudotime"));
        var selected=rows("selected-roots");var expected=NextflowFiles.list(reference.get("selected_cell_indices_zero_based"));assertEquals(expected.size(),selected.size());
        for(int i=0;i<selected.size();i++)assertEquals(NextflowFiles.integer(expected.get(i),0,1000000),NextflowFiles.integer(NextflowFiles.object(selected.get(i)).get("zero_based_cell_index"),0,1000000));
        assertEquals(NextflowFiles.strings(input.get("cell_ids")),rows("cell-pseudotime").stream().map(v->{try{return NextflowFiles.object(v).get("id");}catch(IOException e){throw new UncheckedIOException(e);}}).toList());
    }
    private static void compare(Object expected,List<?> actual)throws Exception {var values=NextflowFiles.strings(expected);assertEquals(values.size(),actual.size());for(int i=0;i<values.size();i++){var row=NextflowFiles.object(actual.get(i));double e=values.get(i).equals("Inf")?Double.POSITIVE_INFINITY:Double.parseDouble(values.get(i));String token=NextflowFiles.text(row.get("pseudotime_token"));double got=token.equals("Inf")?Double.POSITIVE_INFINITY:Double.parseDouble(token);assertEquals(e,got,0.0);assertEquals(Double.isInfinite(e)?"positive-infinity":"value",row.get("pseudotime_kind"));}}
    @Test void rootAndBothModeHelpAreReachable(){assertEquals(0,root("monocle-tools","--help"));assertEquals(0,root("monocle-tools","ORDER","--help"));assertEquals(0,root("monocle-tools","RUN","--help"));assertEquals(0,root("--help"));assertEquals(2,root("monocle-tools"));assertEquals(2,root("monocle-tools","DEG"));}
    @Test void explicitlyUnweightedEdgesUseUnitDistances()throws Exception {var g=graph();g.put("edge_weight_mode","unweighted");g.put("edges",List.of(Map.of("from",0,"to",1),Map.of("from",1,"to",2)));assertEquals(0,run(g,request("1.4.27")));compare(List.of("1","0","1"),rows("cell-pseudotime"));}
    @Test void weightAndSourceProfileMisstatementsAreRefused()throws Exception {
        var g=graph();g.put("edge_weight_mode","unweighted");assertEquals(2,run(g,request("1.4.27")));assertFalse(Files.exists(temp.resolve("result/results")));
        var before=Files.readAllBytes(temp.resolve("result/analysis-status.json"));var r=request("2.40.0");assertEquals(2,root("monocle-tools","RUN","--request",temp.resolve("request.json").toString()));assertArrayEquals(before,Files.readAllBytes(temp.resolve("result/analysis-status.json")));
        NextflowFiles.writeJson(temp.resolve("bad-profile.json"),r);assertEquals(2,root("monocle-tools","ORDER","--request",temp.resolve("bad-profile.json").toString()));
    }
    @Test void byteAndOperationLimitsAdmitNoResult()throws Exception {
        var r=request("1.4.27");r.put("maximum_graph_bytes",1);assertEquals(2,run(graph(),r));assertFalse(Files.exists(temp.resolve("result/results")));
        r=request("1.4.27");r.put("out","operation-failure");r.put("maximum_native_operations",1);NextflowFiles.writeJson(temp.resolve("operation.json"),r);assertEquals(2,root("monocle-tools","ORDER","--request",temp.resolve("operation.json").toString()));assertFalse(Files.exists(temp.resolve("operation-failure/results")));
    }
    @Test void repeatedRootsAndDisconnectedCellsRemainExplicit()throws Exception {var g=graph();g.put("roots",List.of("r","r"));g.put("edges",List.of(Map.of("from",0,"to",1,"weight",3)));assertEquals(0,run(g,request("1.3.1")));assertEquals(2,rows("selected-roots").size());compare(List.of("3","0","Inf"),rows("cell-pseudotime"));assertTrue(Files.readString(temp.resolve("result/WARNINGS.md")).contains("INFINITE_PSEUDOTIME"));}
    @Test void quotesSpacesUnicodeAndLiteralNAArePreserved()throws Exception {var g=graph();var ids=List.of(" a \"β ","NA"," c ");g.put("cell_ids",ids);g.put("graph_vertex_ids",ids);assertEquals(0,run(g,request("1.0.0")));assertEquals(ids,rows("cell-pseudotime").stream().map(v->{try{return NextflowFiles.object(v).get("id");}catch(IOException e){throw new UncheckedIOException(e);}}).toList());assertTrue(Files.readString(temp.resolve("result/results/cell-pseudotime.tsv")).contains("\" a \"\"β \""));}
    @Test void rootRunMissingRuntimeIsSkippedWithoutResult()throws Exception {
        Files.writeString(temp.resolve("counts.tsv"),"gene_id\ta\tb\ng1\t1\t2\ng2\t3\t4\n");Files.writeString(temp.resolve("cells.tsv"),"cell_id\tage\na\t1\nb\t2\n");Files.writeString(temp.resolve("genes.tsv"),"gene_id\tgene_short_name\ng1\tg1\ng2\tg2\n");
        var r=new LinkedHashMap<String,Object>();r.put("schema_version",1);r.put("mode","RUN");r.put("version","1.4.27");r.put("count_units","raw-integer-counts");r.put("counts","counts.tsv");r.put("cell_metadata","cells.tsv");r.put("gene_metadata","genes.tsv");r.put("metadata_types",Map.of("age",Map.of("type","numeric")));r.put("rscript","missing-Rscript");r.put("r_libraries",List.of("missing-private-library"));r.put("out","result");r.put("analyses",List.of(Map.of("id","models","seed",1,"fit_models",Map.of("family","quasipoisson","intercept",true,"terms",List.of(Map.of("column","age"))))));NextflowFiles.writeJson(temp.resolve("run.json"),r);
        assertEquals(3,root("monocle-tools","RUN","--request",temp.resolve("run.json").toString()));assertEquals(false,read(temp.resolve("result/analysis-status.json")).get("result_admitted"));assertFalse(Files.exists(temp.resolve("result/results")));
    }
    @Test void existingOutputIsNeverOverwritten()throws Exception {assertEquals(0,run(graph(),request("1.4.27")));byte[] before=Files.readAllBytes(temp.resolve("result/manifest.yaml"));assertEquals(2,root("monocle-tools","ORDER","--request",temp.resolve("request.json").toString()));assertArrayEquals(before,Files.readAllBytes(temp.resolve("result/manifest.yaml")));}
}
