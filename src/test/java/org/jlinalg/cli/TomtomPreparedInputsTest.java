/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.jlinalg.sequence.TomtomResults;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class TomtomPreparedInputsTest {
    @TempDir Path temporary;
    static Stream<String> originalInputs()throws IOException {
        Map<String,byte[]> files=archive();Map<?,?> expected=NextflowFiles.object(SimpleJson.parse(new String(files.get("EXPECTED.json"),StandardCharsets.UTF_8)));var ids=new ArrayList<String>();
        for(Object value:(List<?>)expected.get("cases")){var row=(Map<?,?>)value;if(row.get("kind").equals("original-complete-input-and-output"))ids.add((String)row.get("id"));}return ids.stream();
    }
    @ParameterizedTest @MethodSource("originalInputs")
    void completeOriginalInputsCaptureUnchangedWithEveryMetadataDisposition(String id)throws Exception {
        Map<String,byte[]> files=archive();String extension=id.endsWith("-xml")?".xml":id.endsWith("-txt")?".txt":".meme";
        Path query=temporary.resolve("original query α"+extension),target=temporary.resolve("original targets.meme");Files.write(query,files.get("inputs/"+id+extension));Files.write(target,files.get("inputs/targets.meme"));
        var request=request(recipe(query,List.of(target)),"recipe.json");var capture=TomtomInputs.capture(temporary.resolve("captured"),request,()->{});capture.verify(true,()->{});
        assertEquals(2,capture.files().size());assertEquals(2,capture.files().get(0).document().entries().size());assertEquals(query.toString(),capture.files().get(0).producerPath());
        assertArrayEquals(Files.readAllBytes(query),Files.readAllBytes(capture.query()));assertEquals(4,Files.readAllLines(capture.root().resolve("motif-ledger.jsonl")).size());
        assertEquals(NextflowFiles.sha(Files.readAllBytes(query)),capture.files().get(0).pin().sha256());
        assertTrue(Files.readString(capture.root().resolve("input-ledger.json")).contains("CAPTURED_INPUTS_ONLY_NO_RUNTIME_OR_FIT_ADMISSION"));
        assertTrue(capture.files().stream().noneMatch(f->f.member().contains("α")||f.member().contains(" ")));
    }
    @ParameterizedTest @ValueSource(strings={"ed","kullback","pearson","allr","sandelin","blic1","blic5","llr1","llr5"})
    void closedDistancesAndScientificFlagsProduceOnlyTypedArguments(String distance)throws Exception {
        var paths=inputs();var recipe=recipe(paths.get(0),List.of(paths.get(1)));recipe.put("distance",distance);recipe.put("reverse_complement",false);recipe.put("internal",true);recipe.put("complete_scores",false);recipe.put("output_mode","TEXT");
        var request=request(recipe,"recipe.json");var args=request.arguments("/runtime/bin/tomtom","/capture/query.meme",List.of("/capture/target.meme"),null,null);
        assertEquals(distance,args.get(args.indexOf("-dist")+1));for(String flag:List.of("-norc","-internal","-incomplete-scores","-text"))assertTrue(args.contains(flag));assertFalse(args.contains("-o"));
        assertEquals(List.of("/capture/query.meme","/capture/target.meme"),args.subList(args.size()-2,args.size()));
    }
    @Test void forbiddenOriginalBackgroundCombinationsFailBeforeAnyCaptureOrLaunch()throws Exception {
        var paths=inputs();
        for(String distance:List.of("ed","kullback","pearson","sandelin")){var recipe=recipe(paths.get(0),List.of(paths.get(1)));recipe.put("distance",distance);recipe.put("background",background("UNIFORM",null));
            assertTrue(assertThrows(IOException.class,()->request(recipe,"r-"+distance+".json")).getMessage().contains("forbids -bfile"));}
    }
    @Test void zeroCutoffEvalueAndExactPseudocountAreRetained()throws Exception {
        var paths=inputs();var recipe=recipe(paths.get(0),List.of(paths.get(1)));recipe.put("threshold",Map.of("kind","EVALUE","value",new BigDecimal("1.25")));recipe.put("pseudocount",new BigDecimal("0.00010"));
        var request=request(recipe,"e.json");var args=request.arguments("/home/bin/tomtom","/capture/q.meme",List.of("/capture/t.meme"),null,"/run/report");assertTrue(args.contains("-evalue"));assertEquals("0.00010",args.get(args.indexOf("-motif-pseudo")+1));assertEquals(new BigDecimal("1.25"),request.threshold());
        recipe.put("threshold",Map.of("kind","QVALUE","value",0));assertEquals(0,request(recipe,"q0.json").threshold().signum());
        recipe.put("threshold",Map.of("kind","QVALUE","value",1.25));assertThrows(IOException.class,()->request(recipe,"q-invalid.json"));
    }
    @Test void processAndCombinedModelLimitsAreEnforcedBeforeOriginalProcesses()throws Exception {
        var paths=inputs();
        for(Map<String,Integer> limits:List.of(Map.of("open_files",1),Map.of("input_bytes",Integer.MAX_VALUE),Map.of("threads",Integer.MAX_VALUE),Map.of("xml_depth",257),Map.of("width",513))){var recipe=recipe(paths.get(0),List.of(paths.get(1)));recipe.put("limits",limits);assertThrows(IOException.class,()->request(recipe,"limits-"+limits.keySet().iterator().next()+".json"));}
        for(Map<String,Integer> limits:List.of(Map.of("motifs",3),Map.of("matrix_values",90),Map.of("pairs",3))){var recipe=recipe(paths.get(0),List.of(paths.get(1)));recipe.put("limits",limits);var request=request(recipe,"combined-"+limits.keySet().iterator().next()+".json");assertThrows(IOException.class,()->TomtomInputs.capture(temporary.resolve("capture-"+limits.keySet().iterator().next()),request,()->{}));}
    }
    @Test void sourceDuplicatesAndTextDatabaseAmbiguityRemainNamedUnsupportedPopulations()throws Exception {
        var paths=inputs();String original=Files.readString(paths.get(1));Files.writeString(paths.get(1),original.replace("target-b","target-a"));var request=request(recipe(paths.get(0),List.of(paths.get(1))),"duplicate.json");
        assertTrue(assertThrows(TomtomResults.UnsupportedInput.class,()->TomtomInputs.capture(temporary.resolve("duplicate"),request,()->{})).getMessage().contains("duplicate-ID"));
        Files.writeString(paths.get(1),original);var recipe=recipe(paths.get(0),List.of(paths.get(1),paths.get(1)));recipe.put("output_mode","TEXT");var text=request(recipe,"text.json");
        assertTrue(assertThrows(TomtomResults.UnsupportedInput.class,()->TomtomInputs.capture(temporary.resolve("ambiguous"),text,()->{})).getMessage().contains("FULL_REPORT"));
        recipe.put("output_mode","FULL_REPORT");var full=request(recipe,"full.json");assertEquals(3,TomtomInputs.capture(temporary.resolve("full"),full,()->{}).files().size());
    }
    @Test void capturedBytesOriginalFilesAndEmptyDirectoriesCannotChangeUndetected()throws Exception {
        var paths=inputs();var request=request(recipe(paths.get(0),List.of(paths.get(1))),"recipe.json");var context=TomtomInputs.capture(temporary.resolve("captured"),request,()->{});
        Path empty=context.root().resolve("unexpected");Files.createDirectory(empty);assertThrows(IOException.class,()->context.verify(false,()->{}));Files.delete(empty);
        String source=Files.readString(paths.get(0));Files.writeString(paths.get(0),source.replace("first","other"));assertThrows(IOException.class,()->context.verify(true,()->{}));context.verify(false,()->{});
        Files.writeString(context.query(),Files.readString(context.query()).replace("first","other"));assertThrows(IOException.class,()->context.verify(false,()->{}));
    }
    @Test void orderZeroExternalBackgroundIsCapturedIndependentlyAndMalformedOrdersFail()throws Exception {
        var paths=inputs();Path bg=temporary.resolve("background α.txt");Files.writeString(bg,"# explicit zero order\nT .4 G .1\nC .1 A .4\n");var recipe=recipe(paths.get(0),List.of(paths.get(1)));recipe.put("distance","llr1");recipe.put("background",background("FILE",bg.toString()));
        var request=request(recipe,"recipe.json");var context=TomtomInputs.capture(temporary.resolve("captured"),request,()->{});assertEquals(1,context.backgrounds().normalizationPasses());assertArrayEquals(new double[]{.4,.1,.1,.4},context.backgrounds().original());assertArrayEquals(Files.readAllBytes(bg),Files.readAllBytes(Path.of(context.backgroundFile())));
        Files.writeString(bg,"A .25 C .25 G .25 T .25 AA .1\n");var invalid=request(recipe,"invalid.json");assertThrows(IOException.class,()->TomtomInputs.capture(temporary.resolve("bad-order"),invalid,()->{}));
    }
    @Test void sourceRecipeMutationUnknownFlagsAndUnquotedCarrierPathsNeverReachAProcess()throws Exception {
        var paths=inputs();var recipe=recipe(paths.get(0),List.of(paths.get(1)));var request=request(recipe,"recipe.json");Files.writeString(request.source(),Files.readString(request.source())+" ");assertThrows(IOException.class,()->TomtomInputs.capture(temporary.resolve("never-created"),request,()->{}));assertFalse(Files.exists(temporary.resolve("never-created")));
        recipe.put("extra_flags",List.of("-dist","ed"));assertThrows(IOException.class,()->request(recipe,"opaque.json"));
        for(String path:List.of("/runtime/bin/tomtom;echo","/runtime/../tomtom","/runtime with space/tomtom","relative"))assertThrows(IOException.class,()->request.arguments(path,"/q.meme",List.of("/t.meme"),null,"/report"));
    }
    @Test void portableRecipeReadsForeignProducerPathsAsStringsWithoutOpeningThem()throws Exception {
        var paths=inputs();var recipe=recipe(paths.get(0),List.of(paths.get(1)));recipe.put("query","C:\\source with spaces\\α.meme");recipe.put("targets",List.of("/foreign/motifs.meme"));Path path=temporary.resolve("portable.json");Files.writeString(path,RsemJson.json(recipe));
        var request=TomtomRecipe.readCaptured(path,()->{});assertEquals("C:\\source with spaces\\α.meme",request.query());assertEquals(List.of("/foreign/motifs.meme"),request.targets());
    }
    @Test void cancellationRetainsPartialCaptureWithoutACompletedContext()throws Exception {
        var paths=inputs();var request=request(recipe(paths.get(0),List.of(paths.get(1))),"recipe.json");var calls=new AtomicInteger();assertThrows(CancellationException.class,()->TomtomInputs.capture(temporary.resolve("captured"),request,()->{if(calls.incrementAndGet()==30)throw new CancellationException("controlled");}));
    }
    private List<Path> inputs()throws IOException {var files=archive();Path q=temporary.resolve("q.meme"),t=temporary.resolve("t.meme");Files.write(q,files.get("inputs/mass-1.0.meme"));Files.write(t,files.get("inputs/targets.meme"));return List.of(q,t);}
    private TomtomRecipe.Request request(Map<String,Object> recipe,String name)throws IOException {Path path=temporary.resolve(name);Files.writeString(path,RsemJson.json(recipe));return TomtomRecipe.read(path,()->{});}
    static Map<String,Object> recipe(Path query,List<Path> targets){
        var recipe=new LinkedHashMap<String,Object>();recipe.put("schema_version",1);recipe.put("profile",TomtomRecipe.PROFILE);recipe.put("query",query.toString());recipe.put("targets",targets.stream().map(Path::toString).toList());recipe.put("distance","ed");recipe.put("reverse_complement",true);recipe.put("pseudocount",new BigDecimal("0.1"));recipe.put("minimum_overlap",1);recipe.put("internal",false);recipe.put("complete_scores",true);recipe.put("threshold",Map.of("kind","QVALUE","value",1));recipe.put("background",background("QUERY",null));recipe.put("output_mode","FULL_REPORT");recipe.put("limits",Map.of("input_bytes",8000000,"decoded_characters",8000000,"matrix_values",1000000,"wall_seconds",240,"cpu_seconds",240,"address_space_bytes",2147483648L,"file_bytes",67108864,"workspace_bytes",134217728,"sampled_rss_bytes",1073741824));return recipe;
    }
    private static Map<String,Object> background(String source,String path){var bg=new LinkedHashMap<String,Object>();bg.put("source",source);bg.put("path",path);return bg;}
    static Map<String,byte[]> archive()throws IOException {
        byte[] raw;try(var in=TomtomPreparedInputsTest.class.getResourceAsStream("/org/jlinalg/sequence/tomtom-inputs/original-input-controls.zip")){assertNotNull(in);raw=in.readAllBytes();}assertEquals("e7b94b363c89a9bc91e27528c395178217f8646c55c41ed9b98c87aaf79d9c66",NextflowFiles.sha(raw));
        var files=new LinkedHashMap<String,byte[]>();try(var z=new ZipInputStream(new java.io.ByteArrayInputStream(raw))){for(var entry=z.getNextEntry();entry!=null;entry=z.getNextEntry()){assertFalse(entry.isDirectory());assertNull(files.put(entry.getName(),z.readAllBytes()));}}return files;
    }
}
