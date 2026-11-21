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

/** Actual original-package captures, exact source weights, and contradictions after resealing. */
class Monocle100NeighborsCaptureTest {
    @TempDir Path temp;
    private Path fixture(String name) throws IOException {
        Path root=temp.resolve(name);if(Files.exists(root))return root;
        try(var raw=getClass().getResourceAsStream("/org/jlinalg/cli/monocle100-neighbors.zip")) {
            assertNotNull(raw);try(var zip=new ZipInputStream(raw)) {
                int selected=0,entries=0;long total=0;java.util.zip.ZipEntry entry;
                while((entry=zip.getNextEntry())!=null) {
                    assertTrue(++entries<200);if(!entry.getName().startsWith(name+"/"))continue;
                    Path target=temp.resolve(entry.getName()).normalize();assertTrue(target.startsWith(root));assertFalse(entry.isDirectory());
                    byte[] bytes=zip.readAllBytes();total+=bytes.length;assertTrue(total<16000000);Files.createDirectories(target.getParent());Files.write(target,bytes,StandardOpenOption.CREATE_NEW);selected++;
                }
                assertTrue(selected>40);
            }
        }
        return root;
    }
    private static int run(String... args) {
        return Monocle100NeighborsCli.run(args,new PrintStream(new ByteArrayOutputStream()),new PrintStream(new ByteArrayOutputStream()));
    }
    private int verify(Path root) {return run("VERIFY","--capture",root.toString(),"--out",temp.resolve("verified").toString());}
    private static Map<String,Object> read(Path file) throws IOException {return Monocle100NeighborsFiles.read(file,16777216);}
    private static void rewrite(Path file,Object value) throws IOException {Files.writeString(file,NextflowJson.parameters(value)+"\n",StandardCharsets.UTF_8);}
    private static void field(Path root,String file,String key,Object value) throws IOException {var map=read(root.resolve(file));map.put(key,value);rewrite(root.resolve(file),map);}
    private static void reseal(Path root) throws IOException {Files.delete(root.resolve("manifest.json"));Monocle100NeighborsFiles.manifest(root,2147483648L,10000);}
    private void refused(Path root) {assertEquals(2,verify(root));assertFalse(Files.exists(temp.resolve("verified/RESULT.json")));}
    @ParameterizedTest @ValueSource(strings={"original","java"})
    void completeOriginalPackageCapturesVerifyWithoutRuntime(String engine) throws IOException {
        Path root=fixture(engine);String before=FollowupSupport.hash(root.resolve("manifest.json"));assertEquals(0,verify(root));assertEquals(before,FollowupSupport.hash(root.resolve("manifest.json")));
        var result=read(temp.resolve("verified/RESULT.json"));assertEquals(false,result.get("original_runtime_executed"));var audit=Monocle100NeighborsFiles.object(result.get("audit"));
        assertEquals(27,Monocle100NeighborsFiles.integer(audit.get("cells"),1,1000000));assertEquals(74,Monocle100NeighborsFiles.integer(audit.get("arcs"),0,1000000));assertEquals(engine.equals("original"),audit.get("original_weights_binary64_identical"));assertEquals(false,audit.get("scientific_test_admitted"));
    }
    @Test void everyExportMatchesTheCompleteOriginalWeightTableBitForBit() throws IOException {
        Path root=fixture("original");var parameters=Monocle100NeighborsContracts.savedParameters(root);var audit=Monocle100NeighborsAudit.audit(root,parameters,Monocle100NeighborsContracts.caps(parameters));
        var original=read(root.resolve("worker/original-listw.json"));var rows=Monocle100NeighborsAudit.namedRows(original.get("neighbors_zero_based"),27);var weights=Monocle100NeighborsFiles.list(original.get("weights_exact_tokens"));int index=0;
        for(int from=0;from<27;from++) {
            var neighbors=Monocle100NeighborsFiles.list(rows.get(from));var tokens=Monocle100NeighborsFiles.list(weights.get(from));assertEquals(neighbors.size(),tokens.size());
            for(int column=0;column<neighbors.size();column++) {
                var arc=audit.weights().arcs().get(index++);assertEquals(from,arc.from());assertEquals(Monocle100NeighborsFiles.integer(neighbors.get(column),0,26),arc.to());
                assertEquals(Double.doubleToRawLongBits(Double.parseDouble(Monocle100NeighborsFiles.text(tokens.get(column)))),Double.doubleToRawLongBits(arc.weight()));
            }
        }
        assertEquals(74,index);
    }
    static Stream<Arguments> changedFields() {
        return Stream.of(Arguments.of("worker/report.json","R","4.6.1"),Arguments.of("worker/report.json","platform","windows"),Arguments.of("worker/report.json","profile","monocle3-1.3.3"),Arguments.of("worker/report.json","seed",123),Arguments.of("worker/report.json","k",6),Arguments.of("worker/report.json","reduction_slot","UMAT"),Arguments.of("worker/report.json","engine","java"),Arguments.of("worker/report.json","input_projection_unchanged",false),Arguments.of("worker/report.json","RNG_unchanged",false),Arguments.of("worker/report.json","original_calculateLW_executed",false),Arguments.of("worker/report.json","original_listw_roundtrip_identical",false),Arguments.of("worker/report.json","source_cds_cells",26),Arguments.of("worker/report.json","source_cds_genes",2),Arguments.of("worker/report.json","scientific_test_admitted",true),Arguments.of("analysis.json","weights_admitted",false),Arguments.of("analysis.json","scientific_test_admitted",true),Arguments.of("worker/PROCESS.json","exit_code",2),Arguments.of("worker/PROCESS.json","owned_live_after_cleanup",List.of(123)),Arguments.of("worker/SUPERVISOR-END.json","alive_after_cleanup",true),Arguments.of("worker/SUPERVISOR-END.json","owned_live_after_cleanup",List.of(123)),Arguments.of("source-request.json","unreported_normalization",true),Arguments.of("effective-request.json","choose_root",true));
    }
    @ParameterizedTest @MethodSource("changedFields")
    void contradictoryAdmissionAndScientificFieldsCannotBeResealed(String file,String key,Object value) throws IOException {
        Path root=fixture("original");field(root,file,key,value);reseal(root);refused(root);
    }
    @ParameterizedTest @ValueSource(strings={"COMPLETED.json","worker/report.json","worker/original-nearest.rds","worker/RNG.rds","inputs/source-cds.rds"})
    void missingCompleteCaptureMemberCannotVerify(String file) throws IOException {
        Path root=fixture("original");Files.move(root.resolve(file),temp.resolve("preserved-missing-member"));refused(root);
    }
    @Test void changedSelectedDistanceIsRefusedAfterResealing() throws IOException {
        Path root=fixture("original");var value=read(root.resolve("worker/original-nearest.json"));var rows=new ArrayList<Object>(Monocle100NeighborsFiles.list(value.get("distance_tokens")));var first=new ArrayList<Object>(Monocle100NeighborsFiles.list(rows.get(0)));first.set(0,"-1");rows.set(0,first);value.put("distance_tokens",rows);rewrite(root.resolve("worker/original-nearest.json"),value);reseal(root);refused(root);
    }
    @Test void duplicateOrIncompleteCellAxisCannotBeResealed() throws IOException {
        Path root=fixture("original");var value=read(root.resolve("worker/input-projection.json"));var cells=new ArrayList<Object>(Monocle100NeighborsFiles.list(value.get("cell_ids")));cells.set(1,cells.get(0));value.put("cell_ids",cells);rewrite(root.resolve("worker/input-projection.json"),value);reseal(root);refused(root);
    }
    @Test void changedFirstRankingColumnCannotBeHiddenByDroppingItFromOneView() throws IOException {
        Path root=fixture("original");var value=read(root.resolve("worker/prepared-neighbors.json"));var rows=new ArrayList<Object>(Monocle100NeighborsFiles.list(value.get("original_ranking_zero_based")));var first=new ArrayList<Object>(Monocle100NeighborsFiles.list(rows.get(0)));first.set(0,first.get(1));rows.set(0,first);value.put("original_ranking_zero_based",rows);rewrite(root.resolve("worker/prepared-neighbors.json"),value);reseal(root);refused(root);
    }
    @Test void changedOriginalWeightCannotBeResealedAsJavaAgreement() throws IOException {
        Path root=fixture("original");var value=read(root.resolve("worker/original-listw.json"));var rows=new ArrayList<Object>(Monocle100NeighborsFiles.list(value.get("weights_exact_tokens")));var first=new ArrayList<Object>(Monocle100NeighborsFiles.list(rows.get(0)));first.set(0,"0.123");rows.set(0,first);value.put("weights_exact_tokens",rows);rewrite(root.resolve("worker/original-listw.json"),value);reseal(root);refused(root);
    }
    @Test void changedExportCannotBeResealedAsCompleteOriginalGraph() throws IOException {
        Path root=fixture("original");Files.writeString(root.resolve("results/weights.tsv"),"from_cell\tto_cell\tweight\n");reseal(root);refused(root);
    }
    @Test void changedSourceGuardCannotBeResealed() throws IOException {
        Path root=fixture("original");Files.writeString(root.resolve("source/worker.R"),"\n# changed captured source\n",StandardOpenOption.APPEND);reseal(root);refused(root);
    }
    @Test void changedSourceFingerprintsCannotBeResealed() throws IOException {
        Path root=fixture("original");field(root,"source/PROFILE.json","function_fingerprints",Map.of());reseal(root);refused(root);
    }
    @Test void javaEngineCannotPretendItReturnedOriginalListw() throws IOException {
        Path root=fixture("java");Files.write(root.resolve("worker/original-listw.rds"),new byte[]{1});reseal(root);refused(root);
    }
    @Test void changedRuntimeCodeCannotBeResealed() throws IOException {
        Path root=fixture("original");field(root,"runtime-code-after.json","foreign-runtime",Map.of("bytes",1,"sha256","0".repeat(64)));reseal(root);refused(root);
    }
    @Test void replayCannotBeWrittenInsideItsParent() throws IOException {
        Path root=fixture("original");String before=FollowupSupport.hash(root.resolve("manifest.json"));assertEquals(2,run("REPLAY","--capture",root.toString(),"--out",root.resolve("child").toString(),"--runtime",temp.resolve("not-accessed.json").toString()));assertFalse(Files.exists(root.resolve("child")));assertEquals(before,FollowupSupport.hash(root.resolve("manifest.json")));
    }
}
