/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class Monocle100GraphContractsTest {
    @TempDir Path temp;
    static Map<String,Object> selected() {
        var value=new LinkedHashMap<String,Object>();value.put("schema_version",1);value.put("operation","RUN");value.put("reduction_slot","UMAP");value.put("seed",100022);value.put("dataset_provenance","Complete synthetic prepared original1.0.0 CDS");value.put("use_partition",true);value.put("close_loop",false);value.put("learn_graph_control",Map.of("ncenter",5,"rann.k",6,"maxiter",100));value.put("convergence_policy","require");value.put("root_cells",List.of("cell-001"));return value;
    }
    static Map<String,Object> source() {var value=selected();value.put("source_cds","complete-cds.rds");value.put("runtime","runtime.json");value.put("out","new-output");return value;}
    static Stream<Arguments> invalidParameters() {
        return Stream.of(Arguments.of("operation","AUTO"),Arguments.of("operation","learn"),Arguments.of("reduction_slot","UMAT"),Arguments.of("reduction_slot","PCA"),Arguments.of("seed",-1),Arguments.of("seed",2147483648L),Arguments.of("dataset_provenance",""),Arguments.of("use_partition","true"),Arguments.of("close_loop",1),Arguments.of("convergence_policy","auto"),Arguments.of("root_cells",List.of()),Arguments.of("root_cells",List.of("cell\t001")),Arguments.of("maximum_cells",1000001),Arguments.of("maximum_native_operations",0),Arguments.of("maximum_json_bytes",1048575),Arguments.of("maximum_output_bytes",1023),Arguments.of("maximum_files",99),Arguments.of("timeout_seconds",0));
    }
    @ParameterizedTest @MethodSource("invalidParameters") void invalidInterpretationOrCapacityIsRefused(String key,Object invalid) {
        var value=selected();value.put(key,invalid);assertThrows(IOException.class,()->Monocle100GraphContracts.parameters(value));
    }
    @ParameterizedTest @ValueSource(strings={"operation","reduction_slot","seed","dataset_provenance","use_partition","close_loop","learn_graph_control","convergence_policy","root_cells"})
    void scientificChoicesAreRequired(String key) {var value=selected();value.remove(key);assertThrows(IOException.class,()->Monocle100GraphContracts.parameters(value));}
    static Stream<Arguments> invalidControls() {
        return Stream.of(Arguments.of("ncenter",0),Arguments.of("ncenter",10000),Arguments.of("rann.k",1.5),Arguments.of("maxiter",0),Arguments.of("eps",0),Arguments.of("L1.sigma",0),Arguments.of("L1.gamma",-1),Arguments.of("scale","true"),Arguments.of("orthogonal_proj_tip",1),Arguments.of("prune_graph",0),Arguments.of("minimal_branch_len",-1),Arguments.of("euclidean_distance_ratio",Double.POSITIVE_INFINITY),Arguments.of("geodesic_distance_ratio",Double.NaN),Arguments.of("ignored_control",true));
    }
    @ParameterizedTest @MethodSource("invalidControls") void invalidOrUnrecognizedOriginalControlIsRefused(String key,Object invalid) {
        var value=selected();value.put("learn_graph_control",Map.of(key,invalid));assertThrows(IOException.class,()->Monocle100GraphContracts.parameters(value));
    }
    @Test void declaredOriginalDefaultsAndNullableCenterAndNeighborControlsArePreserved() throws IOException {
        var value=selected();value.put("learn_graph_control",Map.of());assertEquals(Map.of(),Monocle100GraphContracts.parameters(value).get("learn_graph_control"));
        var controls=new LinkedHashMap<String,Object>();controls.put("ncenter",null);controls.put("rann.k",null);value.put("learn_graph_control",controls);assertEquals(controls,Monocle100GraphContracts.parameters(value).get("learn_graph_control"));
        controls.put("eps",null);assertThrows(IOException.class,()->Monocle100GraphContracts.parameters(value));
    }
    @Test void repeatedExplicitRootsRemainInSourceOrder() throws IOException {
        var value=selected();value.remove("root_cells");value.put("root_pr_nodes",List.of("Y_2","Y_1","Y_2"));assertEquals(List.of("Y_2","Y_1","Y_2"),Monocle100GraphContracts.parameters(value).get("root_pr_nodes"));
        value.put("root_cells",List.of("cell-001"));assertThrows(IOException.class,()->Monocle100GraphContracts.parameters(value));
    }
    @Test void learnRequiresNoRootsAndAdmitsNoIgnoredGraphProvenance() throws IOException {
        var value=selected();value.put("operation","LEARN");assertThrows(IOException.class,()->Monocle100GraphContracts.parameters(value));value.remove("root_cells");var parameters=Monocle100GraphContracts.parameters(value);assertTrue(Monocle100GraphContracts.learns(parameters));assertFalse(Monocle100GraphContracts.orders(parameters));value.put("graph_provenance","ignored supplied graph");assertThrows(IOException.class,()->Monocle100GraphContracts.parameters(value));
    }
    @Test void orderDoesNotAcceptIgnoredLearningControls() throws IOException {
        var value=selected();value.put("operation","ORDER");value.put("graph_provenance","Complete original supplied learned graph");assertThrows(IOException.class,()->Monocle100GraphContracts.parameters(value));
        for(String key:List.of("learn_graph_control","convergence_policy","use_partition","close_loop"))value.remove(key);
        var parameters=Monocle100GraphContracts.parameters(value);assertFalse(Monocle100GraphContracts.learns(parameters));assertTrue(Monocle100GraphContracts.orders(parameters));value.remove("graph_provenance");assertThrows(IOException.class,()->Monocle100GraphContracts.parameters(value));
    }
    @Test void retainedOriginalPolicyIsExplicitAndNeverAutomaticallySelected() throws IOException {
        var value=selected();value.put("learn_graph_control",Map.of("maxiter",1));assertEquals("require",Monocle100GraphContracts.parameters(value).get("convergence_policy"));value.put("convergence_policy","retain-original");assertEquals("retain-original",Monocle100GraphContracts.parameters(value).get("convergence_policy"));
    }
    @ParameterizedTest @ValueSource(strings={"normalize_counts","maximum_rank_entries","engine","choose_root"})
    void unknownSourceFieldsAreRefused(String key) {var value=source();value.put(key,1);assertThrows(IOException.class,()->Monocle100GraphContracts.sourceParameters(value));}
    @Test void savedParametersCannotAddOrChangeAnInterpretation() throws IOException {
        var value=Monocle100GraphContracts.parameters(selected());NextflowFiles.writeJson(temp.resolve("effective-request.json"),value);assertEquals(value,Monocle100GraphContracts.savedParameters(temp));Files.delete(temp.resolve("effective-request.json"));value.put("profile","other");NextflowFiles.writeJson(temp.resolve("effective-request.json"),value);assertThrows(IOException.class,()->Monocle100GraphContracts.savedParameters(temp));
    }
    @Test void outputCannotOverwriteExistingEvidenceOrContainInputs() throws IOException {
        Files.write(temp.resolve("complete-cds.rds"),new byte[]{1});NextflowFiles.writeJson(temp.resolve("runtime.json"),Map.of());NextflowFiles.writeJson(temp.resolve("request.json"),source());assertEquals(temp.resolve("complete-cds.rds").toRealPath(),Monocle100GraphContracts.request(temp.resolve("request.json")).cds());Files.createDirectory(temp.resolve("new-output"));Files.writeString(temp.resolve("new-output/keep"),"preserved");assertThrows(IOException.class,()->Monocle100GraphContracts.request(temp.resolve("request.json")));assertEquals("preserved",Files.readString(temp.resolve("new-output/keep")));
    }
    @Test void runtimeSelectionIsClosedAndKeepsLibraryOrder() throws IOException {
        var value=new LinkedHashMap<String,Object>();value.put("schema_version",1);value.put("profile",Monocle100GraphContracts.GRAPH_PROFILE);value.put("python","python3");value.put("rscript","Rscript");value.put("r_home","R");value.put("native_library_path","lib");value.put("r_libraries",List.of("private","base"));Path file=temp.resolve("runtime.json");NextflowFiles.writeJson(file,value);assertEquals(List.of(temp.resolve("private"),temp.resolve("base")),Monocle100GraphContracts.runtime(file).libraries());Files.delete(file);value.put("install_missing",true);NextflowFiles.writeJson(file,value);assertThrows(IOException.class,()->Monocle100GraphContracts.runtime(file));
    }
    @Test void missingInputSkipsDependentAnalysisAndExplainsRecovery() throws IOException {
        Path file=temp.resolve("request.json");NextflowFiles.writeJson(file,source());var error=new ByteArrayOutputStream();assertEquals(3,Monocle100GraphCli.run(new String[]{"RUN","--request",file.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(error,true,StandardCharsets.UTF_8)));assertTrue(error.toString(StandardCharsets.UTF_8).contains("SKIP3"));assertTrue(error.toString(StandardCharsets.UTF_8).contains("what must be supplied"));assertFalse(Files.exists(temp.resolve("new-output")));
    }
    @ParameterizedTest @ValueSource(strings={"RUN","VERIFY","REPLAY","AUTO","--install"})
    void incompleteOrUnsupportedCommandCannotStartAnAnalysis(String command) {assertEquals(2,Monocle100GraphCli.run(new String[]{command},new PrintStream(new ByteArrayOutputStream()),new PrintStream(new ByteArrayOutputStream())));}
}
