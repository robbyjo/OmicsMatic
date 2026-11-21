/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Genuine complete original CDS captures and contradictions after manifest resealing. */
class Monocle100GraphCaptureTest {
    @TempDir Path temp;
    private Path fixture(String name) throws IOException {
        Path root=temp.resolve(name);if(Files.exists(root))return root;
        try(var raw=getClass().getResourceAsStream("/org/jlinalg/cli/monocle100-graph.zip")) {
            assertNotNull(raw);try(var zip=new ZipInputStream(raw)) {
                int selected=0,entries=0;long total=0;java.util.zip.ZipEntry entry;
                while((entry=zip.getNextEntry())!=null) {
                    assertTrue(++entries<1000);if(!entry.getName().startsWith(name+"/"))continue;
                    Path target=temp.resolve(entry.getName()).normalize();assertTrue(target.startsWith(root));assertFalse(entry.isDirectory());byte[] bytes=zip.readAllBytes();total+=bytes.length;assertTrue(total<64000000);Files.createDirectories(target.getParent());Files.write(target,bytes,StandardOpenOption.CREATE_NEW);selected++;
                }
                assertTrue(selected>40);
            }
        }
        return root;
    }
    private static int run(String... args) {return Monocle100GraphCli.run(args,new PrintStream(new ByteArrayOutputStream()),new PrintStream(new ByteArrayOutputStream()));}
    private int verify(Path root) {return run("VERIFY","--capture",root.toString(),"--out",temp.resolve("verified").toString());}
    private static Map<String,Object> read(Path file) throws IOException {return Monocle100NeighborsFiles.read(file,16777216);}
    private static void rewrite(Path file,Object value) throws IOException {Files.writeString(file,NextflowJson.parameters(value)+"\n",StandardCharsets.UTF_8);}
    private static void field(Path root,String file,String key,Object value) throws IOException {var raw=read(root.resolve(file));raw.put(key,value);rewrite(root.resolve(file),raw);}
    private static void reseal(Path root) throws IOException {Files.delete(root.resolve("manifest.json"));Monocle100GraphFiles.graphManifest(root,Monocle100GraphContracts.caps(Map.of()));}
    private void refused(Path root) {assertEquals(2,verify(root));assertFalse(Files.exists(temp.resolve("verified/RESULT.json")));}
    @ParameterizedTest @ValueSource(strings={"learn","run","disconnected","unconverged"})
    void completeCapturesVerifyWithoutRuntimeAndPreserveTheirManifest(String name) throws IOException {
        Path root=fixture(name);String before=FollowupSupport.hash(root.resolve("manifest.json"));assertEquals(0,verify(root));assertEquals(before,FollowupSupport.hash(root.resolve("manifest.json")));var result=read(temp.resolve("verified/RESULT.json"));assertEquals(false,result.get("original_runtime_executed"));var audit=Monocle100NeighborsFiles.object(result.get("audit"));assertEquals(false,audit.get("scientific_test_admitted"));assertEquals(false,audit.get("root_direction_biologically_verified"));assertEquals(name.equals("run")||name.equals("disconnected"),audit.get("pseudotime_computed"));
    }
    @Test void disconnectedCellsRetainOriginalInfinitePseudotime() throws IOException {
        Path root=fixture("disconnected");assertEquals(0,verify(root));var audit=read(root.resolve("results/audit.json"));assertEquals(27,Monocle100NeighborsFiles.integer(audit.get("infinite_cells"),0,54));assertEquals(55,Files.readAllLines(root.resolve("results/pseudotime.tsv")).size());assertEquals(27,Files.readAllLines(root.resolve("results/pseudotime.tsv")).stream().filter(line->line.endsWith("\tInf")).count());
    }
    @Test void explicitRetentionNeverClaimsConvergenceOrPseudotime() throws IOException {
        Path root=fixture("unconverged");assertEquals(0,verify(root));var audit=read(root.resolve("results/audit.json"));assertEquals(false,audit.get("objective_convergence_verified"));assertEquals(false,audit.get("pseudotime_computed"));assertTrue(Files.readString(root.resolve("ORIGINAL-WARNINGS.txt")).contains("RETAIN_ORIGINAL_NONCONVERGED_GRAPH"));
    }
    static Stream<Arguments> changedFields() {
        return Stream.of(Arguments.of("worker/report.json","R","4.6.1"),Arguments.of("worker/report.json","platform","windows"),Arguments.of("worker/report.json","profile","monocle3-1.3.3"),Arguments.of("worker/report.json","seed",123),Arguments.of("worker/report.json","input_invariants_preserved",false),Arguments.of("worker/report.json","output_RDS_scientific_roundtrip_identical",false),Arguments.of("worker/report.json","guarded_functions",List.of()),Arguments.of("worker/report.json","graph_learned_by_this_run",false),Arguments.of("worker/report.json","pseudotime_computed",false),Arguments.of("worker/report.json","root_direction_biologically_verified",true),Arguments.of("worker/report.json","scientific_test_admitted",true),Arguments.of("analysis.json","graph_admitted",false),Arguments.of("analysis.json","pseudotime_admitted",false),Arguments.of("analysis.json","scientific_test_admitted",true),Arguments.of("worker/PROCESS.json","exit_code",2),Arguments.of("worker/PROCESS.json","owned_live_after_cleanup",List.of(123)),Arguments.of("worker/SUPERVISOR-END.json","alive_after_cleanup",true),Arguments.of("worker/SUPERVISOR-END.json","owned_live_after_cleanup",List.of(123)),Arguments.of("source-request.json","pool_conditions",true),Arguments.of("effective-request.json","profile","foreign"));
    }
    @ParameterizedTest @MethodSource("changedFields") void contradictoryScopeAndAdmissionCannotBeResealed(String file,String key,Object value) throws IOException {Path root=fixture("run");field(root,file,key,value);reseal(root);refused(root);}
    @ParameterizedTest @ValueSource(strings={"inputs/source-cds.rds","worker/input-invariants.rds","worker/input-scientific-state.rds","worker/learned-cds.rds","worker/output-cds.rds","worker/output-invariants.rds","worker/output-scientific-state.rds","worker/original-principal-graph.rds","worker/original-ordering.rds","worker/original-projected-graph.rds","worker/RNG.rds"})
    void missingCompleteModelMemberCannotBeResealed(String name) throws IOException {Path root=fixture("run");Files.move(root.resolve(name),temp.resolve("preserved-missing-model"));reseal(root);refused(root);}
    @Test void emptyCompleteModelMemberCannotBeResealed() throws IOException {Path root=fixture("run");Files.write(root.resolve("worker/output-cds.rds"),new byte[0]);reseal(root);refused(root);}
    @Test void missingCompletionMarkerIsFailureRatherThanDependencySkip() throws IOException {Path root=fixture("run");Files.move(root.resolve("COMPLETED.json"),temp.resolve("preserved-marker"));refused(root);}
    @Test void completeNativeGeodesicsMustMatchOriginalProjection() throws IOException {
        Path root=fixture("run");var raw=read(root.resolve("worker/prepared-ordering.json"));var values=new ArrayList<Object>(Monocle100NeighborsFiles.list(raw.get("expected_cell_pseudotime")));values.set(0,"999999");raw.put("expected_cell_pseudotime",values);rewrite(root.resolve("worker/prepared-ordering.json"),raw);reseal(root);refused(root);
    }
    @Test void unreachableOriginalPseudotimeCannotBeReplacedWithZero() throws IOException {
        Path root=fixture("disconnected");var raw=read(root.resolve("worker/prepared-ordering.json"));var values=new ArrayList<Object>(Monocle100NeighborsFiles.list(raw.get("expected_cell_pseudotime")));int index=values.indexOf("Inf");assertTrue(index>=0);values.set(index,"0");raw.put("expected_cell_pseudotime",values);rewrite(root.resolve("worker/prepared-ordering.json"),raw);reseal(root);refused(root);
    }
    @Test void rootMappingCannotBeChangedIndependentlyOfTheExplicitRequest() throws IOException {Path root=fixture("run");field(root,"worker/prepared-ordering.json","roots",List.of("Y_999"));reseal(root);refused(root);}
    @Test void duplicateCompleteCellAxisCannotBeResealed() throws IOException {
        Path root=fixture("run");var raw=read(root.resolve("worker/input-projection.json"));var cells=new ArrayList<Object>(Monocle100NeighborsFiles.list(raw.get("cell_ids")));cells.set(1,cells.get(0));raw.put("cell_ids",cells);rewrite(root.resolve("worker/input-projection.json"),raw);reseal(root);refused(root);
    }
    @Test void changedLearningObjectiveClassificationCannotBeResealed() throws IOException {
        Path root=fixture("run");field(root,"worker/convergence.json","all_converged",false);var report=read(root.resolve("worker/report.json"));report.put("convergence",read(root.resolve("worker/convergence.json")));rewrite(root.resolve("worker/report.json"),report);reseal(root);refused(root);
    }
    @Test void truncatedPseudotimeExportCannotBeResealed() throws IOException {Path root=fixture("run");Files.writeString(root.resolve("results/pseudotime.tsv"),"cell_id\tpseudotime\n");reseal(root);refused(root);}
    @Test void changedCapturedSourceCannotBeResealed() throws IOException {Path root=fixture("run");Files.writeString(root.resolve("source/worker.R"),"\n# changed capture\n",StandardOpenOption.APPEND);reseal(root);refused(root);}
    @Test void selectedRuntimeCodeMustBeUnchanged() throws IOException {Path root=fixture("run");field(root,"runtime-code-after.json","foreign",Map.of("bytes",1,"sha256","0".repeat(64)));reseal(root);refused(root);}
    @Test void learnCannotAcquireAnOrderingArtifactByResealing() throws IOException {Path root=fixture("learn");Files.write(root.resolve("worker/original-projected-graph.rds"),new byte[]{1});reseal(root);refused(root);}
    @Test void replayCannotOverwriteOrNestInsideItsParent() throws IOException {
        Path root=fixture("run");String before=FollowupSupport.hash(root.resolve("manifest.json"));assertEquals(2,run("REPLAY","--capture",root.toString(),"--out",root.resolve("child").toString(),"--runtime",temp.resolve("not-accessed.json").toString()));assertFalse(Files.exists(root.resolve("child")));assertEquals(before,FollowupSupport.hash(root.resolve("manifest.json")));
    }
}
