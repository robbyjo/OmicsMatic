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

class Monocle100NeighborsContractsTest {
    @TempDir Path temp;
    static Map<String,Object> selected() {
        var value=new LinkedHashMap<String,Object>();value.put("schema_version",1);value.put("engine","java");value.put("neighbor_graph","knn");value.put("k",5);value.put("reduction_slot","UMAT");value.put("seed",42);value.put("dataset_provenance","Complete synthetic prepared source CDS");return value;
    }
    static Stream<Arguments> invalidParameters() {
        return Stream.of(Arguments.of("engine","automatic"),Arguments.of("engine","original-or-java"),Arguments.of("neighbor_graph","infer"),Arguments.of("k",0),Arguments.of("k",1.5),Arguments.of("seed",-1),Arguments.of("seed",2147483648L),Arguments.of("reduction_slot","UM\tAP"),Arguments.of("dataset_provenance",""),Arguments.of("maximum_rank_entries",16000001),Arguments.of("maximum_native_operations",0),Arguments.of("maximum_json_bytes",1048575),Arguments.of("maximum_output_bytes",1023),Arguments.of("maximum_address_space_bytes",1),Arguments.of("maximum_sampled_rss_bytes",1),Arguments.of("maximum_cells",1000001),Arguments.of("maximum_files",99),Arguments.of("timeout_seconds",0));
    }
    @ParameterizedTest @MethodSource("invalidParameters") void invalidInterpretationOrCapacityIsRefused(String key,Object invalid) {
        var value=selected();value.put(key,invalid);assertThrows(IOException.class,()->Monocle100NeighborsContracts.parameters(value));
    }
    @ParameterizedTest @ValueSource(strings={"engine","neighbor_graph","k","seed","reduction_slot","dataset_provenance"})
    void missingScientificSettingIsRefused(String key) {
        var value=selected();value.remove(key);assertThrows(IOException.class,()->Monocle100NeighborsContracts.parameters(value));
    }
    @Test void literalReportedUmatSlotIsPreserved() throws IOException {
        assertEquals("UMAT",Monocle100NeighborsContracts.parameters(selected()).get("reduction_slot"));
    }
    @Test void savedScientificParametersRejectForeignFields() throws IOException {
        var value=Monocle100NeighborsContracts.parameters(selected());value.put("choose_root",true);
        NextflowFiles.writeJson(temp.resolve("effective-request.json"),value);assertThrows(IOException.class,()->Monocle100NeighborsContracts.savedParameters(temp));
    }
    @Test void sourceScientificParametersRejectIgnoredUnknownFields() {
        var value=source();value.put("normalize_counts",true);assertThrows(IOException.class,()->Monocle100NeighborsContracts.sourceParameters(value));
    }
    private static Map<String,Object> source() {
        var value=selected();value.put("source_cds","complete-cds.rds");value.put("runtime","runtime.json");value.put("out","new-output");return value;
    }
    @Test void sourceRequestResolvesRelativeInputsAndPreservesExistingOutput() throws IOException {
        Files.write(temp.resolve("complete-cds.rds"),new byte[]{1});NextflowFiles.writeJson(temp.resolve("runtime.json"),Map.of());NextflowFiles.writeJson(temp.resolve("request.json"),source());
        var request=Monocle100NeighborsContracts.request(temp.resolve("request.json"));assertEquals(temp.resolve("complete-cds.rds").toRealPath(),request.cds());
        Files.createDirectory(temp.resolve("new-output"));Files.writeString(temp.resolve("new-output/keep"),"preserved");
        assertThrows(IOException.class,()->Monocle100NeighborsContracts.request(temp.resolve("request.json")));assertEquals("preserved",Files.readString(temp.resolve("new-output/keep")));
    }
    @Test void sourceOutputCannotContainTheInput() throws IOException {
        Files.write(temp.resolve("complete-cds.rds"),new byte[]{1});NextflowFiles.writeJson(temp.resolve("runtime.json"),Map.of());var value=source();value.put("out",temp.toString());NextflowFiles.writeJson(temp.resolve("request.json"),value);
        assertThrows(IOException.class,()->Monocle100NeighborsContracts.request(temp.resolve("request.json")));
    }
    @Test void selectedRuntimeIsExplicitAndClosed() throws IOException {
        var value=new LinkedHashMap<String,Object>();value.put("schema_version",1);value.put("profile",Monocle100NeighborsFiles.PROFILE);value.put("python","python3");value.put("rscript","Rscript");value.put("r_home","R");value.put("native_library_path","lib");value.put("r_libraries",List.of("private","base"));
        Path path=temp.resolve("runtime.json");NextflowFiles.writeJson(path,value);var runtime=Monocle100NeighborsContracts.runtime(path);assertEquals(List.of(temp.resolve("private"),temp.resolve("base")),runtime.libraries());
        Files.delete(path);value.put("install_missing",true);NextflowFiles.writeJson(path,value);assertThrows(IOException.class,()->Monocle100NeighborsContracts.runtime(path));
    }
    @Test void missingSourceHasProminentSkipAndNoCreatedResult() throws IOException {
        Path path=temp.resolve("request.json");NextflowFiles.writeJson(path,source());var error=new ByteArrayOutputStream();
        assertEquals(3,Monocle100NeighborsCli.run(new String[]{"RUN","--request",path.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(error,true,StandardCharsets.UTF_8)));
        String text=error.toString(StandardCharsets.UTF_8);assertTrue(text.contains("SKIP3"));assertTrue(text.contains("state what must be supplied"));assertFalse(Files.exists(temp.resolve("new-output")));
    }
    @Test void completeJsonAndTableExportsMustFitVerificationCapacity() throws IOException {
        var selected=selected();selected.put("maximum_json_bytes",1048576);selected.put("maximum_output_bytes",4194304);var caps=Monocle100NeighborsContracts.caps(selected);
        Files.write(temp.resolve("too-large.json"),new byte[1048577]);assertThrows(IOException.class,()->Monocle100NeighborsFiles.outputLimits(temp,caps,0,0));
    }
    @Test void missingRuntimeSkipsTheDependentRunWithoutStartingAnOriginalWorker() throws IOException {
        Files.write(temp.resolve("complete-cds.rds"),new byte[]{1});
        NextflowFiles.writeJson(temp.resolve("runtime.json"),Map.of("schema_version",1,"profile",Monocle100NeighborsFiles.PROFILE,"python","unavailable-python","rscript","unavailable-Rscript","r_home","unavailable-R","native_library_path","unavailable-native-library","r_libraries",List.of("unavailable-R-library")));
        Path path=temp.resolve("request.json");NextflowFiles.writeJson(path,source());var error=new ByteArrayOutputStream();
        assertEquals(3,Monocle100NeighborsCli.run(new String[]{"RUN","--request",path.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(error,true,StandardCharsets.UTF_8)));
        var analysis=Monocle100NeighborsFiles.read(temp.resolve("new-output/analysis.json"),1048576);assertEquals(false,analysis.get("weights_admitted"));assertEquals("SKIP3_DEPENDENT_NEIGHBOR_ANALYSIS_SKIPPED",analysis.get("status"));assertFalse(Files.exists(temp.resolve("new-output/worker/PROCESS-STARTED.json")));assertFalse(Files.exists(temp.resolve("new-output/COMPLETED.json")));
    }
    @Test void reservedCompletionMarkerCountsAgainstCompleteCaptureCapacity() throws IOException {
        var selected=selected();selected.put("maximum_output_bytes",1024);var caps=Monocle100NeighborsContracts.caps(selected);Files.write(temp.resolve("captured.bin"),new byte[1000]);
        Monocle100NeighborsFiles.outputLimits(temp,caps,24,1);assertThrows(IOException.class,()->Monocle100NeighborsFiles.outputLimits(temp,caps,25,1));
    }
    @Test void completeFilePopulationIncludesPendingCompletion() throws IOException {
        var selected=selected();selected.put("maximum_files",100);var caps=Monocle100NeighborsContracts.caps(selected);
        for(int index=0;index<99;index++)Files.write(temp.resolve("part-"+index),new byte[0]);Monocle100NeighborsFiles.outputLimits(temp,caps,0,1);
        assertThrows(IOException.class,()->Monocle100NeighborsFiles.outputLimits(temp,caps,0,2));
    }
    @ParameterizedTest @ValueSource(strings={"--install","RUN","VERIFY","REPLAY","AUTO"})
    void incompleteOrUnrecognizedCommandsCannotLaunchAnalysis(String command) {
        assertEquals(2,Monocle100NeighborsCli.run(new String[]{command},new PrintStream(new ByteArrayOutputStream()),new PrintStream(new ByteArrayOutputStream())));
    }
}
