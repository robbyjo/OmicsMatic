/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Contract/refusal tests plus offline checks of an actual original-program capture. */
final class ScanpyWorkflowTest {
    @TempDir Path temporary;
    private static Map<String,Object> map(Object... values){Map<String,Object> result=new LinkedHashMap<>();for(int i=0;i<values.length;i+=2)result.put((String)values[i],values[i+1]);return result;}
    private Map<String,Object> request()throws IOException {
        Files.writeString(temporary.resolve("matrix.mtx"),"%%MatrixMarket matrix coordinate real general\n2 2 3\n1 1 2\n1 2 3\n2 2 1\n");
        Files.writeString(temporary.resolve("cells.tsv"),"cell\ncell A\ncell-B\n");Files.writeString(temporary.resolve("genes.tsv"),"gene\ngene-1\ngene-2\n");
        return map("schema_version",1,"mode","RUN","profile",ScanpyWorkflowContracts.PROFILE,"python",temporary.resolve("missing-python").toString(),"out",temporary.resolve("analysis").toString(),
            "input",map("format","matrix_market","matrix","matrix.mtx","cells","cells.tsv","features","genes.tsv","dtype","float64","orientation","features_by_cells"),
            "steps",List.of(map("operation","copy_layer","params",map("name","counts"))));
    }
    private int run(Map<String,Object> request,ByteArrayOutputStream error)throws IOException {
        Path path=temporary.resolve("request-"+UUID.randomUUID()+".json");json(path,request);
        try(var out=new PrintStream(new ByteArrayOutputStream(),true,StandardCharsets.UTF_8);var err=new PrintStream(error,true,StandardCharsets.UTF_8)){return ScanpyWorkflowCli.run(new String[]{"--request",path.toString()},out,err);}
    }
    private int run(Map<String,Object> request)throws IOException{return run(request,new ByteArrayOutputStream());}
    @Test void explicitDtypeAndOrientationAreRequired()throws IOException {
        var r=request();var input=object(r.get("input"),"input");input.remove("dtype");r.put("input",input);assertThrows(IOException.class,()->ScanpyWorkflowContracts.validate(r));
        input.put("dtype","float32");assertThrows(IOException.class,()->ScanpyWorkflowContracts.validate(r));
        input.put("dtype","float64");input.remove("orientation");assertThrows(IOException.class,()->ScanpyWorkflowContracts.validate(r));
    }
    @Test void h5adDoesNotInventDtypeOrMetadataSchema()throws IOException {
        var r=request();r.put("input",map("format","h5ad","matrix","provided.h5ad"));var effective=ScanpyWorkflowContracts.validate(r);
        assertEquals(Set.of("format","matrix"),object(effective.get("input"),"input").keySet());
    }
    @Test void unknownProfileDoesNotCreateOutput()throws IOException {var r=request();r.put("profile","Scanpy-1.9.3");assertEquals(2,run(r));assertFalse(Files.exists(temporary.resolve("analysis")));}
    @Test void noDefaultRecipeOrFunctionSpellingRepair()throws IOException {
        var r=request();r.put("steps",List.of(map("operation","pp.harmony_integrate")));assertThrows(IOException.class,()->ScanpyWorkflowContracts.validate(r));
        r.put("steps",List.of());assertThrows(IOException.class,()->ScanpyWorkflowContracts.validate(r));
        r.put("steps",List.of(map("operation","sc.ppl.log1p")));assertThrows(IOException.class,()->ScanpyWorkflowContracts.validate(r));
    }
    @Test void unknownTopLevelAndCapacityFieldsAreRefused()throws IOException {
        var r=request();r.put("automatic_fix",true);assertThrows(IOException.class,()->ScanpyWorkflowContracts.validate(r));r.remove("automatic_fix");
        r.put("limits",map("silently_subsample",1000));assertThrows(IOException.class,()->ScanpyWorkflowContracts.validate(r));
    }
    @Test void oversizedOrFractionalCapacitiesAreRefused()throws IOException {
        var r=request();r.put("timeout_seconds",1.5);assertThrows(IOException.class,()->ScanpyWorkflowContracts.validate(r));r.remove("timeout_seconds");
        r.put("limits",map("maximum_working_bytes",40000000001L));assertThrows(IOException.class,()->ScanpyWorkflowContracts.validate(r));
    }
    @Test void categoricalLevelsRemainExplicitAndUnique()throws IOException {
        var r=request();var input=object(r.get("input"),"input");input.put("obs_columns",map("batch",map("levels",List.of("001","002"),"ordered",true)));r.put("input",input);
        assertEquals(input.get("obs_columns"),object(ScanpyWorkflowContracts.validate(r).get("input"),"input").get("obs_columns"));
        input.put("obs_columns",map("batch",map("levels",List.of("001","001"))));assertThrows(IOException.class,()->ScanpyWorkflowContracts.validate(r));
    }
    @Test void excessiveParameterNestingIsRefused()throws IOException {
        var r=request();Object value="leaf";for(int i=0;i<15;i++)value=List.of(value);r.put("steps",List.of(map("operation","subset","params",map("predicate",value))));
        assertThrows(IOException.class,()->ScanpyWorkflowContracts.validate(r));
    }
    @Test void missingPythonSkipsAndProminentlyExplainsRecovery()throws IOException {
        var r=request();var error=new ByteArrayOutputStream();assertEquals(3,run(r,error));String text=error.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("SKIP3"));assertTrue(text.contains("restore"));assertTrue(text.contains("WARNINGS"));
        var state=read(temporary.resolve("analysis/analysis-status.json"));assertEquals(false,state.get("results_admitted"));assertTrue(state.get("status").toString().startsWith("SKIP3"));
        assertThrows(IOException.class,()->ScanpyWorkflowCli.verify(temporary.resolve("analysis")));assertTrue(Files.exists(temporary.resolve("analysis/capture-files.json")));
    }
    @Test void missingInputSkipsBeforeWorkerStarts()throws IOException {
        var r=request();Files.delete(temporary.resolve("matrix.mtx"));assertEquals(3,run(r));assertFalse(Files.exists(temporary.resolve("analysis/worker/runtime-binding.json")));
    }
    @Test void existingOutputIsPreservedExactly()throws IOException {
        var r=request();Path root=Files.createDirectory(temporary.resolve("analysis"));Files.writeString(root.resolve("retain"),"original evidence\r\n");assertEquals(2,run(r));assertEquals("original evidence\r\n",Files.readString(root.resolve("retain")));try(var files=Files.list(root)){assertEquals(1,files.count());}
    }
    @Test void fullInputCapacityRefusesRatherThanTruncates()throws IOException {
        var r=request();r.put("maximum_input_bytes",1);assertEquals(2,run(r));assertEquals(false,read(temporary.resolve("analysis/analysis-status.json")).get("results_admitted"));
    }
    @Test void packagedProfileContainsOriginalSourceAndDependencies()throws IOException {
        var p=object(SimpleJson.parse(new String(ScanpyWorkflowRuntime.resource("SOURCE-PROFILE.json"),StandardCharsets.UTF_8)),"profile");
        assertEquals(143,object(p.get("scanpy_sources"),"sources").size());assertEquals("1.13.0",object(p.get("versions"),"versions").get("scanpy"));assertEquals(14,object(p.get("versions"),"versions").size());
    }
    private Path live() {
        String path=System.getProperty("jlinalg.scanpy.test.bundle");assumeTrue(path!=null,"Optional actual completed original-program capture not supplied");return Path.of(path);
    }
    private Path copied()throws IOException {
        Path source=live(),target=temporary.resolve("copied");try(var paths=Files.walk(source)){for(Path p:paths.toList()){Path to=target.resolve(source.relativize(p));if(Files.isDirectory(p))Files.createDirectories(to);else Files.copy(p,to);}}return target;
    }
    @Test void actualFullPipelineVerifiesWithoutExecutingPython()throws IOException {ScanpyWorkflowCli.verify(live());}
    @Test void changingCapturedSourceIsRefusedEvenAfterReseal()throws IOException {
        Path root=copied();Files.writeString(root.resolve("source/worker.py"),"# changed worker\n");ScanpyWorkflowCli.seal(root);assertThrows(IOException.class,()->ScanpyWorkflowCli.verify(root));
    }
    @Test void removingExecutedStepsIsRefusedEvenAfterReseal()throws IOException {
        Path root=copied();var report=read(root.resolve("worker/report.json"),16000000);report.put("steps",List.of());json(root.resolve("worker/report.json"),report);ScanpyWorkflowCli.seal(root);assertThrows(IOException.class,()->ScanpyWorkflowCli.verify(root));
    }
    @Test void changedIdentityIsRefusedEvenAfterReseal()throws IOException {
        Path root=copied(),path=root.resolve("worker/outputs/cells.tsv");String text=Files.readString(path);assertTrue(text.contains("cell-000"));Files.writeString(path,text.replace("cell-000","renamed-cell"));ScanpyWorkflowCli.seal(root);assertThrows(IOException.class,()->ScanpyWorkflowCli.verify(root));
    }
    @Test void nonfiniteMatrixIsRefusedEvenAfterReseal()throws IOException {
        Path root=copied(),path=root.resolve("worker/outputs/matrix.mtx");List<String> rows=Files.readAllLines(path);int i=1;while(rows.get(i).startsWith("%"))i++;i++;String[] fields=rows.get(i).trim().split("\\s+");rows.set(i,fields[0]+" "+fields[1]+" NaN");Files.write(path,rows);ScanpyWorkflowCli.seal(root);assertThrows(IOException.class,()->ScanpyWorkflowCli.verify(root));
    }
    @Test void unlistedFileIsRefused()throws IOException {Path root=copied();Files.writeString(root.resolve("foreign.txt"),"extra");assertThrows(IOException.class,()->ScanpyWorkflowCli.verify(root));}
    @Test void replayWithMissingRuntimeSkipsAndPreservesPriorCapture()throws IOException {
        Path prior=live();String before=FollowupSupport.hash(prior.resolve("capture-files.json"));assertEquals(3,run(map("schema_version",1,"mode","REPLAY","analysis",prior.toString(),"out",temporary.resolve("replay").toString(),"python",temporary.resolve("missing-python").toString())));assertEquals(before,FollowupSupport.hash(prior.resolve("capture-files.json")));assertEquals(false,read(temporary.resolve("replay/analysis-status.json")).get("results_admitted"));
    }
}
