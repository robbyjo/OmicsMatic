/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class GenomicRangesWorkflowContractsTest {
    @TempDir Path temporary;
    static final String PROFILE="windows-x86_64-r4.1.0-genomicranges1.44.0";
    Map<String,Object> request() {
        var r=new LinkedHashMap<String,Object>();r.put("schema_version",1);r.put("operation","nearest");r.put("profile",PROFILE);r.put("rscript",temporary.resolve("missing-Rscript.exe").toString());r.put("r_libraries",List.of(temporary.toString()));r.put("input_rds",temporary.resolve("objects.rds").toString());r.put("subject_mode","provided");
        r.put("options",new LinkedHashMap<>(Map.of("select","all","ignore.strand",false)));r.put("limits",new LinkedHashMap<>(Map.of("maximum_input_bytes",16777216,"maximum_member_bytes",33554432,"maximum_output_bytes",134217728,"maximum_files",1000,"maximum_rows",10000,"maximum_pairs",100000,"timeout_seconds",60)));return r;
    }
    @TestFactory Stream<DynamicTest> rejectsScientificAndCapacityAmbiguity() {
        var cases=new LinkedHashMap<String,Consumer<Map<String,Object>>>();
        cases.put("missing-option",r->r.put("options",Map.of("select","all")));
        cases.put("extra-option",r->r.put("options",Map.of("select","all","ignore.strand",false,"invented-default",1)));
        cases.put("string-logical",r->r.put("options",Map.of("select","all","ignore.strand","FALSE")));
        cases.put("unknown-profile",r->r.put("profile","newest"));
        cases.put("unknown-operation",r->r.put("operation","infer-genes"));
        cases.put("unknown-select",r->r.put("options",Map.of("select","all-nearest-ties","ignore.strand",false)));
        cases.put("subject-ambiguity",r->r.put("subject_mode","unused"));
        cases.put("missing-subject-mode",r->r.remove("subject_mode"));
        cases.put("unknown-field",r->r.put("repair-identifiers",true));
        cases.put("empty-libraries",r->r.put("r_libraries",List.of()));
        cases.put("duplicate-libraries",r->r.put("r_libraries",List.of(temporary.toString(),temporary.toString())));
        cases.put("invalid-utf8-path",r->r.put("input_rds","bad-\ud800.rds"));
        cases.put("fractional-row-capacity",r->{try{var limits=GenomicRangesWorkflowFiles.map(r.get("limits"));limits.put("maximum_rows",1.5);r.put("limits",limits);}catch(IOException e){throw new AssertionError(e);}});
        cases.put("missing-limit",r->{try{var limits=GenomicRangesWorkflowFiles.map(r.get("limits"));limits.remove("maximum_pairs");r.put("limits",limits);}catch(IOException e){throw new AssertionError(e);}});
        return cases.entrySet().stream().map(c->DynamicTest.dynamicTest(c.getKey(),()->{var r=request();c.getValue().accept(r);assertThrows(IOException.class,()->GenomicRangesWorkflowContracts.validate(r,temporary));}));
    }
    @Test void preservesFractionalPromoterValuesAndExplicitNamePolicy()throws Exception {
        var r=request();r.put("operation","promoters");r.put("subject_mode","unused");r.put("options",Map.of("upstream",List.of(2000.5,-0.5),"downstream",200.25,"use.names",false));
        var validated=GenomicRangesWorkflowContracts.validate(r,temporary);assertEquals(GenomicRangesWorkflowFiles.map(r.get("options")),GenomicRangesWorkflowFiles.map(validated.get("options")));
        Path controls=temporary.resolve("controls");Files.createDirectory(controls);GenomicRangesWorkflowRuntime.controls(controls,validated,false);
        String value=Files.readString(controls.resolve("options.tsv"));assertTrue(value.contains("upstream\tnumeric\t"+GenomicRangesWorkflowRuntime.hex("2000.5,-0.5")));assertTrue(value.contains("use.names\tboolean\t"+GenomicRangesWorkflowRuntime.hex("FALSE")));
        GenomicRangesWorkflowRuntime.controls(controls,validated,true);
    }
    @Test void rejectsNonfinitePromoterTransportWithoutReplacingIt()throws Exception {
        for(Object value:List.of(Double.NaN,Double.POSITIVE_INFINITY,List.of(1,Double.NEGATIVE_INFINITY))){var r=request();r.put("operation","promoters");r.put("subject_mode","unused");r.put("options",Map.of("upstream",value,"downstream",200,"use.names",true));assertThrows(IOException.class,()->GenomicRangesWorkflowContracts.validate(r,temporary));}
    }
    @Test void emptyPromoterVectorIsTransportedWithoutInventedValues()throws Exception {
        var r=request();r.put("operation","promoters");r.put("subject_mode","unused");r.put("options",Map.of("upstream",List.of(),"downstream",List.of(),"use.names",true));var validated=GenomicRangesWorkflowContracts.validate(r,temporary);
        Path controls=temporary.resolve("empty-controls");Files.createDirectory(controls);GenomicRangesWorkflowRuntime.controls(controls,validated,false);assertTrue(Files.readString(controls.resolve("options.tsv")).contains("upstream\tnumeric\t\n"));
    }
    @Test void missingInputCreatesSkipWithoutAdmittedResultsOrOverwriting()throws Exception {
        Path request=temporary.resolve("request.json");Files.write(request,RsemJson.bytes(request()));Path output=temporary.resolve("skipped");var bytes=new ByteArrayOutputStream();int code=GenomicRangesToolsCli.run(new String[]{"RUN","--request",request.toString(),"--out",output.toString()},new PrintStream(bytes),new PrintStream(bytes));assertEquals(3,code,bytes.toString());assertFalse(Files.exists(output.resolve("admitted")));assertTrue(Files.readString(output.resolve("WARNINGS.md")).contains("SKIP3"));
        byte[] before=Files.readAllBytes(output.resolve("seal.json"));assertEquals(2,GenomicRangesToolsCli.run(new String[]{"RUN","--request",request.toString(),"--out",output.toString()},new PrintStream(bytes),new PrintStream(bytes)));assertArrayEquals(before,Files.readAllBytes(output.resolve("seal.json")));
        assertEquals(3,GenomicRangesToolsCli.run(new String[]{"VERIFY","--run",output.toString()},new PrintStream(bytes),new PrintStream(bytes)),bytes.toString());
    }
    @Test void helpDoesNotRequireAnInstalledRuntime() {
        var bytes=new ByteArrayOutputStream();assertEquals(0,GenomicRangesToolsCli.run(new String[]{"--help"},new PrintStream(bytes),new PrintStream(bytes)));assertTrue(bytes.toString().contains("RUN"));assertTrue(bytes.toString().contains("REPLAY"));assertTrue(bytes.toString().contains("exit3"));
    }
    @Test void globalDispatcherReachesTheSameWorkflowHelp() {
        var direct=new ByteArrayOutputStream();var global=new ByteArrayOutputStream();assertEquals(0,GenomicRangesToolsCli.run(new String[]{"--help"},new PrintStream(direct),new PrintStream(direct)));assertEquals(0,JLinAlgCli.run(new String[]{"genomicranges-tools","--help"},new PrintStream(global),new PrintStream(global)));assertArrayEquals(direct.toByteArray(),global.toByteArray());
    }
    @Test void footprintAcceptsOnlyTheExpectedAtomicMarkerLifecycle()throws Exception {
        Path captures=temporary.resolve("marker-lifecycle");Files.createDirectory(captures);var r=GenomicRangesWorkflowContracts.validate(request(),temporary);
        var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
        try{
            // Each capture publishes once to a NEW target,as the real protocol
            // does. Replacing an already published Windows marker is different.
            for(int i=0;i<200;i++){
                Path root=Files.createDirectory(captures.resolve("capture-"+i));Path worker=Files.createDirectory(root.resolve("worker"));Path pending=worker.resolve("READY.pending");Files.writeString(pending,"ready\n",java.nio.file.StandardOpenOption.CREATE_NEW);
                var publisher=pool.submit(()->{Files.move(pending,worker.resolve("READY"));return true;});
                for(int scan=0;scan<5;scan++)GenomicRangesWorkflowProcess.footprint(root,r);
                assertTrue(publisher.get(30,java.util.concurrent.TimeUnit.SECONDS));GenomicRangesWorkflowProcess.footprint(root,r);
            }
        }finally{pool.shutdownNow();}
    }
    @Test void runtimeFingerprintsKeepExecutionAndDataFilesWhileExcludingPackageDocumentation()throws Exception {
        Path runtime=Files.createDirectory(temporary.resolve("runtime-package"));for(String dir:List.of("R","libs","data","extdata","Meta","help","html","doc","demo","tests","unitTests")){Path d=Files.createDirectory(runtime.resolve(dir));Files.writeString(d.resolve("fixture.dat"),dir);}
        Files.writeString(runtime.resolve("DESCRIPTION"),"Package: artificial\nVersion:1\n");Files.writeString(runtime.resolve("NAMESPACE"),"export(artificial)\n");var first=GenomicRangesWorkflowRuntime.fingerprintRoots(List.of(runtime));assertEquals(7,first.size());
        for(String path:first.keySet())assertFalse(Set.of("help","html","doc","demo","tests","unitTests").contains(runtime.relativize(Path.of(path)).getName(0).toString()));
        Files.writeString(runtime.resolve("help/fixture.dat"),"changed documentation");assertEquals(first,GenomicRangesWorkflowRuntime.fingerprintRoots(List.of(runtime)));
        Files.writeString(runtime.resolve("data/fixture.dat"),"changed runtime data");assertNotEquals(first,GenomicRangesWorkflowRuntime.fingerprintRoots(List.of(runtime)));
    }
}
