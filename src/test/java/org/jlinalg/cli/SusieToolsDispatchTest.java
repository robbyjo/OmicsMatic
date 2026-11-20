/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Actual root-route checks; scientific captures are the unchanged original-package fixture. */
final class SusieToolsDispatchTest {
    private static final Map<String,byte[]> PAYLOAD=fixture();
    private static final Map<String,Object> INDEX=index();
    private static Path home(String label)throws IOException {
        Path root=Path.of(System.getProperty("omicsmatic.susie.dispatch.test_root","build/susie-dispatch-tests")).toAbsolutePath();
        Files.createDirectories(root);return Files.createTempDirectory(root,label+"-");
    }
    private record Run(int status,String output,String error) { }
    private static Run root(String... args) {
        var output=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();int status;
        try(var out=new PrintStream(output,true,StandardCharsets.UTF_8);var err=new PrintStream(error,true,StandardCharsets.UTF_8)) {
            status=JLinAlgCli.run(args,out,err);
        }
        return new Run(status,output.toString(StandardCharsets.UTF_8),error.toString(StandardCharsets.UTF_8));
    }
    private static int request(Path h,Map<String,Object> request)throws IOException {
        Path path=h.resolve("request.json");json(path,request);return root("susie-tools","--request",path.toString()).status();
    }
    private static Map<String,Object> valid(Path h)throws IOException {
        Files.writeString(h.resolve("variants.tsv"),"variant_id\tz\na\t6\nb\t2\n");
        Files.writeString(h.resolve("matrix.tsv"),"variant_id\ta\tb\na\t1\t0.1\nb\t0.1\t1\n");
        return new LinkedHashMap<>(Map.ofEntries(Map.entry("schema_version",1),Map.entry("mode","RUN"),
            Map.entry("profile","SuSiE-0.14.2-R4.6.1"),Map.entry("model","RSS_Z"),Map.entry("sample_size",60),
            Map.entry("ld_origin","reference"),Map.entry("variants","variants.tsv"),Map.entry("matrix","matrix.tsv"),
            Map.entry("out",h.resolve("analysis").toString()),Map.entry("rscript",h.resolve("missing-Rscript").toString()),
            Map.entry("r_libraries",List.of(h.toString()))));
    }
    @TestFactory Stream<DynamicTest> rootVerifiesOrRefusesAll41OriginalCaptures()throws IOException {
        return list(INDEX.get("cases"),"cases").stream().map(SusieToolsDispatchTest::unchecked).map(row->
            DynamicTest.dynamicTest(row.get("name").toString(),()->{
                Path captured=restore(row);var result=root("susie-tools","--verify",captured.toString());
                assertEquals(((Number)row.get("project_exit")).intValue(),result.status(),result.error());
                if(result.status()==0)assertTrue(result.output().contains("Verified saved SuSiE capture offline"));
                else assertTrue(result.error().contains("NO COMPLETE RESULT"));
            }));
    }
    @Test void mainHelpListsBothExplicitRoutes() {
        var result=root("--help");assertEquals(0,result.status());
        String command="susie-tools --help";assertTrue(result.output().contains(command));
        assertEquals(result.output().indexOf(command),result.output().lastIndexOf(command));
        assertTrue(result.output().contains("susie --summary"));
    }
    @Test void workflowAndNativeHelpRemainDistinct() {
        var workflow=root("susie-tools","--help");var nativeHelp=root("susie","--help");
        assertEquals(0,workflow.status());assertEquals(0,nativeHelp.status());
        assertTrue(workflow.output().contains("--request"));assertFalse(workflow.output().contains("--summary"));
        assertTrue(nativeHelp.output().contains("--summary"));assertFalse(nativeHelp.output().contains("--request"));
    }
    @Test void missingRuntimePropagatesSkipAndRetainsInputs()throws IOException {
        Path h=home("missing-runtime");var r=valid(h);assertEquals(3,request(h,r));
        assertEquals(false,read(h.resolve("analysis/analysis-status.json")).get("results_admitted"));
        assertTrue(Files.exists(h.resolve("analysis/inputs/variants")));
        assertTrue(Files.readString(h.resolve("analysis/WARNINGS.md")).contains("SKIP3"));
    }
    @Test void missingInputNamesTheRequiredRecoveryOnTheRootConsole()throws IOException {
        Path h=home("missing-input");var r=valid(h);r.put("variants","unavailable.tsv");Path path=h.resolve("request.json");json(path,r);
        var result=root("susie-tools","--request",path.toString());assertEquals(3,result.status());
        assertTrue(result.error().contains("unavailable.tsv"));assertTrue(result.error().contains("REQUIRED"));
        assertEquals(false,read(h.resolve("analysis/analysis-status.json")).get("results_admitted"));
    }
    @Test void unsupportedHistoricalEditionDoesNotBecomeNativeOrAdjacentSource()throws IOException {
        Path h=home("01143");var r=valid(h);r.put("profile","SuSiE-0.11.43-R4.6.1");
        assertEquals(3,request(h,r));assertFalse(Files.exists(h.resolve("analysis")));
    }
    @Test void unknownExecutableFieldsAreRefusedBeforeOutput()throws IOException {
        Path h=home("unknown");var r=valid(h);r.put("r_expression","never executed");
        assertEquals(2,request(h,r));assertFalse(Files.exists(h.resolve("analysis")));
    }
    @Test void existingOutputIsPreserved()throws IOException {
        Path h=home("existing");var r=valid(h);Path output=h.resolve("analysis");Files.createDirectory(output);
        Files.writeString(output.resolve("KEEP"),"original bytes\n");assertEquals(2,request(h,r));
        try(var files=Files.list(output)){assertEquals(List.of(output.resolve("KEEP")),files.toList());}
        assertEquals("original bytes\n",Files.readString(output.resolve("KEEP")));
    }
    @Test void replayUsesOriginalRuntimeRequirementWithoutChangingParent()throws IOException {
        Path h=home("replay");Path parent=restore(named("0.14.2-individual-base"));String seal=FollowupSupport.hash(parent.resolve("capture-files.json"));
        var r=new LinkedHashMap<String,Object>();r.put("schema_version",1);r.put("mode","REPLAY");r.put("analysis",parent.toString());
        r.put("out",h.resolve("replay").toString());r.put("rscript",h.resolve("missing-rscript").toString());r.put("r_libraries",List.of(h.toString()));
        assertEquals(3,request(h,r));assertEquals(false,read(h.resolve("replay/analysis-status.json")).get("results_admitted"));
        assertEquals(seal,FollowupSupport.hash(parent.resolve("capture-files.json")));assertEquals(0,root("susie-tools","--verify",parent.toString()).status());
    }
    @Test void replayCannotWriteInsideItsVerifiedParent()throws IOException {
        Path h=home("nested-replay");Path parent=restore(named("0.14.2-individual-base"));Path nested=parent.resolve("nested/new-output");
        String seal=FollowupSupport.hash(parent.resolve("capture-files.json"));
        var r=new LinkedHashMap<String,Object>();r.put("schema_version",1);r.put("mode","REPLAY");r.put("analysis",parent.toString());
        r.put("out",nested.toString());r.put("rscript",h.resolve("missing-rscript").toString());r.put("r_libraries",List.of(h.toString()));
        Path path=h.resolve("request.json");json(path,r);var result=root("susie-tools","--request",path.toString());
        assertEquals(2,result.status());assertTrue(result.error().contains("outside the original analysis"));
        assertFalse(Files.exists(parent.resolve("nested")));assertEquals(seal,FollowupSupport.hash(parent.resolve("capture-files.json")));
        assertEquals(0,root("susie-tools","--verify",parent.toString()).status());
    }
    private static Map<String,Object> named(String name)throws IOException {
        for(Object raw:list(INDEX.get("cases"),"cases")){var row=object(raw,"case");if(name.equals(row.get("name")))return row;}
        throw new IOException("Required fixture case missing");
    }
    private static Path restore(Map<String,Object> row)throws IOException {
        Path root=home("capture-"+row.get("name"));
        for(var entry:object(row.get("files"),"files").entrySet()){
            Path relative=Path.of(entry.getKey());if(relative.isAbsolute()||!relative.normalize().equals(relative)||entry.getKey().contains("\\")||entry.getKey().contains(":"))throw new IOException("Unsafe fixture path");
            var wanted=object(entry.getValue(),"pin");byte[] raw=PAYLOAD.get("blobs/"+wanted.get("sha256"));
            if(raw==null||raw.length!=((Number)wanted.get("bytes")).intValue()||!SusieWorkflowRuntime.sha(raw).equals(wanted.get("sha256")))throw new IOException("Fixture pin differs");
            Path target=root.resolve(relative);if(!target.normalize().startsWith(root))throw new IOException("Fixture escapes output");
            Files.createDirectories(target.getParent());Files.write(target,raw,StandardOpenOption.CREATE_NEW);
        }return root;
    }
    private static Map<String,Object> unchecked(Object value){try{return object(value,"case");}catch(IOException e){throw new UncheckedIOException(e);}}
    private static Map<String,Object> index(){try{return object(SimpleJson.parse(new String(PAYLOAD.get("INDEX.json"),StandardCharsets.UTF_8)),"index");}catch(IOException e){throw new UncheckedIOException(e);}}
    private static Map<String,byte[]> fixture(){
        try(InputStream stream=SusieToolsDispatchTest.class.getResourceAsStream("/org/jlinalg/omics/susie-workflow/reference-20261119.zip")){
            if(stream==null)throw new IOException("Required original fixture unavailable");byte[] archive=stream.readAllBytes();
            if(!SusieWorkflowRuntime.sha(archive).equals("be7ca436a6d7478503e25b42f583b52c5ff0d1dbc3ce6a92d92100b234924235"))throw new IOException("Original fixture changed");
            var result=new LinkedHashMap<String,byte[]>();try(var zip=new ZipInputStream(new ByteArrayInputStream(archive),StandardCharsets.UTF_8)){
                java.util.zip.ZipEntry e;long total=0;while((e=zip.getNextEntry())!=null){
                    if(e.isDirectory()||result.containsKey(e.getName())||!e.getName().equals("INDEX.json")&&!e.getName().matches("blobs/[0-9a-f]{64}"))throw new IOException("Invalid fixture member");
                    byte[] raw=zip.readNBytes(16_000_001);total+=raw.length;if(raw.length>16_000_000||total>128_000_000)throw new IOException("Fixture capacity exceeded");result.put(e.getName(),raw);
                }
            }return Collections.unmodifiableMap(result);
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
}
