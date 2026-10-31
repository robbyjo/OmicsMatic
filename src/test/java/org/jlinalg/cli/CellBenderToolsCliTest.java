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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CellBenderToolsCliTest {
    @TempDir Path dir;
    private final ByteArrayOutputStream output=new ByteArrayOutputStream();
    private Map<String,String> options()throws Exception{Files.writeString(dir.resolve("matrix.mtx"),"%%MatrixMarket matrix coordinate integer general\n2 3 2\n1 1 10\n2 2 1\n");Files.writeString(dir.resolve("features.tsv"),"G1\tONE\tGene Expression\nG2\tTWO\tGene Expression\n");Files.writeString(dir.resolve("barcodes.tsv"),"A\nB\nZERO\n");var o=new LinkedHashMap<String,String>();o.put("method","remove-background");o.put("matrix",dir.resolve("matrix.mtx").toString());o.put("features",dir.resolve("features.tsv").toString());o.put("barcodes",dir.resolve("barcodes.tsv").toString());o.put("dataset","synthetic-library");o.put("feature-reference","synthetic-genes-v1");o.put("count-kind","raw-umi");o.put("raw-unfiltered","true");o.put("feature-type","Gene Expression");o.put("device","cpu");o.put("expected-cells","1");o.put("total-droplets","2");o.put("home",dir.resolve("absent-runtime").toString());o.put("out",dir.resolve("result").toString());return o;}
    private int run(Map<String,String> options){var args=new ArrayList<String>();options.forEach((k,v)->{args.add("--"+k);args.add(v);});return CellBenderToolsCli.run(args.toArray(String[]::new),new PrintStream(output),new PrintStream(output));}
    @Test void helpNamesTheOriginalCpuBoundaryAndRequiredRecovery(){assertEquals(0,CellBenderToolsCli.run(new String[]{"--help"},new PrintStream(output),new PrintStream(output)));for(String token:List.of("CellBender","--raw-unfiltered true","Linux","SKIP","0.3.1","Required action"))assertTrue(output.toString().contains(token),token);}
    @Test void missingRuntimeSkipsWithDiagnosticsAndNoResults()throws Exception{assertEquals(3,run(options()),output.toString());assertFalse(Files.exists(dir.resolve("result/results.tsv")));assertTrue(Files.readString(dir.resolve("result/WARNINGS.md")).contains("Required action"));assertTrue(Files.readString(dir.resolve("result/analysis-status.json")).contains("skipped-dependency-unavailable"));}
    @Test void cudaRequestDoesNotSilentlyFallBackToCpu()throws Exception{var o=options();o.put("device","cuda");assertEquals(3,run(o));assertTrue(Files.readString(dir.resolve("result/session.json")).contains("no CPU fallback"));assertFalse(Files.exists(dir.resolve("result/results.tsv")));}
    @Test void dependencyCheckDoesNotPretendToFitAModel()throws Exception{var o=new LinkedHashMap<String,String>();o.put("method","check");o.put("home",dir.resolve("absent").toString());o.put("out",dir.resolve("check").toString());assertEquals(3,run(o));assertFalse(Files.exists(dir.resolve("check/results.tsv")));}
    @Test void rawUnfilteredDeclarationIsRequired()throws Exception{var o=options();o.put("raw-unfiltered","false");assertEquals(2,run(o));assertFalse(Files.exists(dir.resolve("result")));assertTrue(output.toString().contains("empty droplets"));}
    @Test void fractionalEstimateContractIsRejectedBeforeAnyModel()throws Exception{var o=options();o.put("count-kind","estimate");assertEquals(2,run(o));assertFalse(Files.exists(dir.resolve("result")));}
    @Test void invalidFprNeverLaunchesAnAnalysis()throws Exception{var o=options();o.put("fpr","1");assertEquals(2,run(o));assertFalse(Files.exists(dir.resolve("result")));}
    @Test void zeroEpochInitializationCannotBeAdmittedAsFitting()throws Exception{var o=options();o.put("epochs","0");assertEquals(2,run(o));assertFalse(Files.exists(dir.resolve("result")));}
    @Test void unknownAndHistoricalRuntimeFlagsAreRejected()throws Exception{var o=options();o.put("force-use-checkpoint","true");assertEquals(2,run(o));assertFalse(Files.exists(dir.resolve("result")));assertTrue(output.toString().contains("Unknown option"));}
    @Test void existingOutputIsNeverOverwritten()throws Exception{var o=options();Files.createDirectory(dir.resolve("result"));Files.writeString(dir.resolve("result/sentinel"),"keep");assertEquals(2,run(o));assertEquals("keep",Files.readString(dir.resolve("result/sentinel")));}
    @Test void runtimeAndOutputOverlapIsRejectedWithoutMutation()throws Exception{var o=options();o.put("home",dir.resolve("runtime").toString());o.put("out",dir.resolve("runtime/new-result").toString());assertEquals(2,run(o));assertFalse(Files.exists(dir.resolve("runtime")));}
    @Test void carrierPathsResolveRelativeToTheCarrier()throws Exception{var o=options();for(String name:List.of("matrix","features","barcodes"))o.put(name,Path.of(o.get(name)).getFileName().toString());o.put("home","absent-runtime");o.put("out","carrier-result");Path carrier=dir.resolve("recipe.json");Files.writeString(carrier,RegenieRuntime.json(o));assertEquals(3,CellBenderToolsCli.run(new String[]{"--cellbender-options-file",carrier.toString()},new PrintStream(output),new PrintStream(output)));assertTrue(Files.readString(dir.resolve("carrier-result/session.json")).contains("recipe.json"));}
    @Test void carrierRequiresStringOptionsAndForbidsRecursion()throws Exception{Path carrier=dir.resolve("bad.json");Files.writeString(carrier,"{\"method\":\"check\",\"threads\":2}");assertEquals(2,CellBenderToolsCli.run(new String[]{"--cellbender-options-file",carrier.toString()},new PrintStream(output),new PrintStream(output)));Files.writeString(carrier,"{\"cellbender-options-file\":\"recursive.json\"}");assertEquals(2,CellBenderToolsCli.run(new String[]{"--cellbender-options-file",carrier.toString()},new PrintStream(output),new PrintStream(output)));}
    @Test void missingInputPublishesAFailedDiagnosticBundle()throws Exception{var o=options();o.put("matrix",dir.resolve("missing.mtx").toString());assertEquals(2,run(o));assertTrue(Files.readString(dir.resolve("result/session.json")).contains("Input file does not exist"));assertFalse(Files.exists(dir.resolve("result/results.tsv")));}
    @Test void inputByteBoundIsAppliedBeforeRuntimeAdmission()throws Exception{var o=options();o.put("max-input-bytes","1");assertEquals(2,run(o));assertTrue(Files.readString(dir.resolve("result/session.json")).contains("byte bound"));}
    @Test void checkRejectsIrrelevantFitOptions()throws Exception{var o=options();o.put("method","check");assertEquals(2,run(o));assertFalse(Files.exists(dir.resolve("result")));}
    @Test void explicitCellAndDropletChoicesCannotBeMissing()throws Exception{var o=options();o.remove("expected-cells");assertEquals(2,run(o));assertTrue(output.toString().contains("--expected-cells is required"));}
    @Test void unsupportedNaiveFallbackDoesNotMasqueradeAsAGenerativeFit()throws Exception{var o=options();o.put("model","naive");assertEquals(2,run(o));assertFalse(Files.exists(dir.resolve("result")));}
}
