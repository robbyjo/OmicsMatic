/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class MonocleResponsePreparationCliTest {
    @TempDir Path temp;
    private record Input(Path directory,Map<String,Object> original,Map<String,Object> request) { }

    private static PrintStream stream(ByteArrayOutputStream bytes) {
        return new PrintStream(bytes,true,StandardCharsets.UTF_8);
    }
    private static List<String> labels(Object value) throws IOException {
        var result=new ArrayList<String>();
        for(Object item:NextflowFiles.list(value))result.add((String)item);
        return result;
    }
    private static Map<String,Object> read(Path path) throws IOException {
        return Monocle100NeighborsFiles.object(SimpleJson.parse(Files.readString(path)));
    }
    private static void table(Path path,List<String> columns,List<String> rows,double[][] values) throws IOException {
        var text=new StringBuilder(String.join("\t",columns)).append('\n');
        for(int row=0;row<rows.size();row++) {
            text.append(rows.get(row));
            for(double value:values[row])text.append('\t').append(value);
            text.append('\n');
        }
        Files.writeString(path,text);
    }
    private static void column(Path path,String name,List<String> cells,Object raw) throws IOException {
        double[] values=MonocleResponsePreparationTest.vector(raw);
        double[][] matrix=new double[values.length][1];
        for(int i=0;i<values.length;i++)matrix[i][0]=values[i];
        table(path,List.of("cell_id",name),cells,matrix);
    }
    private Input input(String id) throws Exception {
        Map<String,Object> original=null;
        for(Object item:NextflowFiles.list(MonocleResponsePreparationTest.references().get("cases"))) {
            var candidate=Monocle100NeighborsFiles.object(item);
            if(candidate.get("id").equals(id))original=candidate;
        }
        assertNotNull(original);
        Path directory=Files.createDirectory(temp.resolve(id));
        var cells=labels(original.get("cell_ids"));var genes=labels(original.get("gene_ids"));
        var header=new ArrayList<>(List.of("gene_id"));header.addAll(cells);
        table(directory.resolve("expression.tsv"),header,genes,MonocleResponsePreparationTest.matrix(original.get("expression")));
        var request=new LinkedHashMap<String,Object>();
        request.put("schema_version",1);request.put("transform",original.get("transform"));
        request.put("expression","expression.tsv");request.put("input_provenance","Complete deterministic original-source control "+id);
        request.put("population_declaration","All supplied cells/genes; pre-filter metadata is explicitly supplied");
        request.put("out","capture");
        if(original.get("size_factors")!=null) {
            column(directory.resolve("size_factors.tsv"),"size_factor",cells,original.get("size_factors"));
            request.put("size_factors","size_factors.tsv");
        }
        if(original.get("detected_genes")!=null) {
            column(directory.resolve("detected_genes.tsv"),"nFeature_RNA",cells,original.get("detected_genes"));
            request.put("detected_genes","detected_genes.tsv");
            request.put("detected_genes_provenance","Supplied pre-filter nFeature_RNA; not recomputed from selected genes");
        }
        if(original.get("base_design")!=null) {
            var designHeader=new ArrayList<>(List.of("cell_id"));designHeader.addAll(labels(original.get("base_design_columns")));
            table(directory.resolve("base_design.tsv"),designHeader,cells,MonocleResponsePreparationTest.matrix(original.get("base_design")));
            request.put("base_design","base_design.tsv");request.put("design_provenance","Explicit intercept, Batch B/C, sex M and numeric PMI; no coding inferred");
        }
        return new Input(directory,original,request);
    }
    private static int command(String[] arguments,ByteArrayOutputStream errors) {
        var args=new ArrayList<>(List.of("monocle-tools","RESPONSES-1.0.0"));args.addAll(List.of(arguments));
        return JLinAlgCli.run(args.toArray(String[]::new),stream(new ByteArrayOutputStream()),stream(errors));
    }
    private static int run(Input input,ByteArrayOutputStream errors) throws IOException {
        Path request=input.directory().resolve("request.json");Files.writeString(request,NextflowJson.json(input.request())+"\n");
        return command(new String[]{"RUN","--request",request.toString()},errors);
    }
    private static int captureCommand(String action,Path capture,Path out,ByteArrayOutputStream errors) {
        return command(new String[]{action,"--capture",capture.toString(),"--out",out.toString()},errors);
    }
    private static void matchesTable(Path path,List<String> rowIds,double[][] expected) throws IOException {
        var lines=Files.readAllLines(path);assertEquals(expected.length+1,lines.size());
        for(int row=0;row<expected.length;row++) {
            String[] fields=lines.get(row+1).split("\t",-1);assertEquals(rowIds.get(row),fields[0]);assertEquals(expected[row].length+1,fields.length);
            for(int col=0;col<expected[row].length;col++)MonocleResponsePreparationTest.close(expected[row][col],Double.parseDouble(fields[col+1]),"complete original table");
        }
    }
    @ParameterizedTest
    @ValueSource(strings={"quasi-design","uninormal-design","binomial-design","quasi-metadata","asis-plain","extreme-log"})
    void completePreparationReplaysAndRetainsOriginalInferenceOrDeclaredDegeneracyRefusal(String id) throws Exception {
        var input=input(id);var errors=new ByteArrayOutputStream();assertEquals(0,run(input,errors),errors.toString(StandardCharsets.UTF_8));
        Path capture=input.directory().resolve("capture");var original=input.original();var cells=labels(original.get("cell_ids"));
        assertEquals("gene_id\t"+String.join("\t",cells),Files.readAllLines(capture.resolve("responses.tsv")).get(0));
        matchesTable(capture.resolve("responses.tsv"),labels(original.get("gene_ids")),MonocleResponsePreparationTest.matrix(original.get("expected_responses")));
        var analysis=read(capture.resolve("analysis.json"));assertEquals(true,analysis.get("response_preparation_admitted"));assertEquals(false,analysis.get("scientific_test_admitted"));
        if(original.get("expected_log_n_genes")!=null) {
            double[] logged=MonocleResponsePreparationTest.vector(original.get("expected_log_n_genes"));double[][] values=new double[logged.length][1];
            for(int i=0;i<logged.length;i++)values[i][0]=logged[i];
            matchesTable(capture.resolve("log_n_genes.tsv"),cells,values);
        } else assertFalse(Files.exists(capture.resolve("log_n_genes.tsv")));
        Path design;
        if(original.get("base_design")!=null) {
            design=capture.resolve("design.tsv");matchesTable(design,cells,MonocleResponsePreparationTest.matrix(original.get("expected_design")));
        } else {
            design=input.directory().resolve("explicit-external-design.tsv");
            table(design,List.of("cell_id","intercept","trend"),cells,MonocleResponsePreparationTest.matrix(original.get("expected_design")));
            assertFalse(Files.exists(capture.resolve("design.tsv")));
        }
        var weights=new StringBuilder("from_cell\tto_cell\tweight\n");
        for(int i=0;i<cells.size();i++)for(int j:new int[]{(i+cells.size()-1)%cells.size(),(i+1)%cells.size()})weights.append(cells.get(i)).append('\t').append(cells.get(j)).append("\t0.5\n");
        Path weightPath=input.directory().resolve("weights.tsv");Files.writeString(weightPath,weights);
        if(id.equals("asis-plain")) {
            // This original control's fourth response is mathematically in the
            // explicit intercept/trend design. R reports finite inference from
            // rounding residuals; preserve the native unresolved-variation policy.
            double[] response=MonocleResponsePreparationTest.matrix(original.get("expected_responses"))[3];
            double[][] basis=MonocleResponsePreparationTest.matrix(original.get("expected_design"));
            for(int i=0;i<response.length;i++)assertEquals(-4+(48.0/7)*basis[i][1],response[i],4e-15);
        }
        var alternatives=NextflowFiles.object(original.get("tests"));int compared=0;
        for(String alternative:List.of("greater","less","two.sided")) {
            var request=new LinkedHashMap<String,Object>();request.put("schema_version",1);request.put("design",design.toString());request.put("weights",weightPath.toString());request.put("expression",capture.resolve("responses.tsv").toString());request.put("response_units","prepared-linear-response");request.put("response_provenance","Native explicit preparation matched to official Monocle branch");request.put("population_declaration","Complete original-source synthetic control "+id);request.put("zero_policy",true);request.put("alternative",alternative);Path out=input.directory().resolve("moran-"+alternative);request.put("out",out.toString());Path json=input.directory().resolve("moran-request-"+alternative+".json");NextflowFiles.writeJson(json,request);
            int status=JLinAlgCli.run(new String[]{"monocle-tools","MORAN-LM","--request",json.toString()},stream(new ByteArrayOutputStream()),stream(errors));
            if(id.equals("asis-plain")) {
                assertEquals(2,status);assertTrue(errors.toString(StandardCharsets.UTF_8).contains("Gene gene-4: Residual variation is zero or unresolved at floating-point precision"));assertEquals(false,read(out.resolve("analysis.json")).get("result_admitted"));assertFalse(Files.exists(out.resolve("COMPLETED.json")));assertFalse(Files.exists(out.resolve("tests.json")));continue;
            }
            assertEquals(0,status,errors.toString(StandardCharsets.UTF_8));
            var rows=NextflowFiles.list(SimpleJson.parse(Files.readString(out.resolve("tests.json"))));var expected=NextflowFiles.list(alternatives.get(alternative));assertEquals(expected.size(),rows.size());
            for(int gene=0;gene<rows.size();gene++) {
                var actual=NextflowFiles.object(rows.get(gene));var wanted=NextflowFiles.object(expected.get(gene));assertEquals(wanted.get("gene_id"),actual.get("gene_id"));
                for(String field:List.of("morans_I","expectation","variance","z","p_value","log_p_value","q_value")) {
                    double value=RegressionMoranTest.number(wanted.get(field));assertEquals(value,RegressionMoranTest.number(actual.get(field)),1e-10+1e-11*Math.abs(value),id+" "+alternative+" "+field);
                }
                assertEquals(RegressionMoranTest.number(wanted.get("p_value"))==0,RegressionMoranTest.number(actual.get("p_value"))==0);compared++;
            }
        }
        assertEquals(id.equals("asis-plain")?0:id.equals("extreme-log")?6:12,compared);
        var before=Monocle100NeighborsFiles.snapshot(capture,67108864,100);
        assertEquals(0,captureCommand("VERIFY",capture,input.directory().resolve("verified"),errors),errors.toString(StandardCharsets.UTF_8));
        Path replay=input.directory().resolve("replay");assertEquals(0,captureCommand("REPLAY",capture,replay,errors),errors.toString(StandardCharsets.UTF_8));
        assertArrayEquals(Files.readAllBytes(capture.resolve("responses.tsv")),Files.readAllBytes(replay.resolve("responses.tsv")));
        assertEquals(true,read(replay.resolve("replay.json")).get("parent_preserved"));assertEquals(before,Monocle100NeighborsFiles.snapshot(capture,67108864,100));
    }
    private static void edit(Path path,int row,int column,String value) throws IOException {
        var lines=new ArrayList<>(Files.readAllLines(path));String[] fields=lines.get(row).split("\t",-1);fields[column]=value;lines.set(row,String.join("\t",fields));Files.write(path,lines);
    }
    @ParameterizedTest
    @ValueSource(strings={"zero-factor","negative-expression","nonfinite-expression","hex-expression","fractional-feature","rounded-feature","overflow-feature","metadata-axis","design-axis","reserved-column","duplicate-column","ragged-row","empty-row","duplicate-gene","duplicate-cell","too-little-work"})
    void invalidCompleteInputsCannotBeRepairedOrPartiallyAdmitted(String change) throws Exception {
        var input=input("quasi-design");Path directory=input.directory();
        switch(change) {
            case "zero-factor"->edit(directory.resolve("size_factors.tsv"),1,1,"0");
            case "negative-expression"->edit(directory.resolve("expression.tsv"),1,1,"-1");
            case "nonfinite-expression"->edit(directory.resolve("expression.tsv"),2,4,"NaN");
            case "hex-expression"->edit(directory.resolve("expression.tsv"),1,1,"0x1p2");
            case "fractional-feature"->edit(directory.resolve("detected_genes.tsv"),1,1,"9007199254740991.5");
            case "rounded-feature"->edit(directory.resolve("detected_genes.tsv"),1,1,"9007199254740993");
            case "overflow-feature"->edit(directory.resolve("detected_genes.tsv"),1,1,"1e40");
            case "metadata-axis"->edit(directory.resolve("detected_genes.tsv"),1,0,"unknown");
            case "design-axis"->edit(directory.resolve("base_design.tsv"),2,0,"unknown");
            case "reserved-column"->edit(directory.resolve("base_design.tsv"),0,1,"log_n_genes");
            case "duplicate-column"->edit(directory.resolve("base_design.tsv"),0,2,"intercept");
            case "ragged-row"->Files.writeString(directory.resolve("expression.tsv"),Files.readString(directory.resolve("expression.tsv"))+"bad\t1\n");
            case "empty-row"->Files.writeString(directory.resolve("expression.tsv"),Files.readString(directory.resolve("expression.tsv"))+"\n");
            case "duplicate-gene"->edit(directory.resolve("expression.tsv"),2,0,"gene \"one\"");
            case "duplicate-cell"->edit(directory.resolve("expression.tsv"),0,2,"cell-001");
            case "too-little-work"->input.request().put("maximum_operations",48*5+4*48*4);
            default->throw new AssertionError(change);
        }
        var errors=new ByteArrayOutputStream();assertEquals(2,run(input,errors),change+" "+errors.toString(StandardCharsets.UTF_8));
        Path capture=directory.resolve("capture");assertEquals(false,read(capture.resolve("analysis.json")).get("response_preparation_admitted"));assertFalse(Files.exists(capture.resolve("COMPLETED.json")));assertTrue(Files.exists(capture.resolve("inputs/expression.tsv")));
    }
    @ParameterizedTest
    @ValueSource(strings={"ignored-factor","missing-factor","missing-metadata-provenance","ignored-design-provenance","design-without-metadata","unknown-field"})
    void ambiguousRequestContractsAreRefusedBeforeOwningOutput(String change) throws Exception {
        var input=input("quasi-design");var request=input.request();
        switch(change) {
            case "ignored-factor"->request.put("transform","as-is");
            case "missing-factor"->request.remove("size_factors");
            case "missing-metadata-provenance"->request.remove("detected_genes_provenance");
            case "ignored-design-provenance"->request.remove("base_design");
            case "design-without-metadata"->{request.remove("detected_genes");request.remove("detected_genes_provenance");}
            case "unknown-field"->request.put("estimate_size_factors",true);
            default->throw new AssertionError(change);
        }
        assertEquals(2,run(input,new ByteArrayOutputStream()));assertFalse(Files.exists(input.directory().resolve("capture")));
    }
    @Test void missingInputSkipsAndOccupiedDestinationsArePreserved() throws Exception {
        var input=input("quasi-design");Files.delete(input.directory().resolve("size_factors.tsv"));var errors=new ByteArrayOutputStream();assertEquals(3,run(input,errors));assertTrue(errors.toString(StandardCharsets.UTF_8).contains("SKIP3"));assertFalse(Files.exists(input.directory().resolve("capture")));
        Path occupied=Files.createDirectory(input.directory().resolve("occupied"));Files.writeString(occupied.resolve("retain"),"retain");input.request().put("out","occupied");input.request().put("size_factors","detected_genes.tsv");assertEquals(2,run(input,new ByteArrayOutputStream()));assertEquals("retain",Files.readString(occupied.resolve("retain")));
    }
    @Test void verificationAndReplayUseCapturedInputsAfterOriginalPathsDisappear() throws Exception {
        var input=input("quasi-design");assertEquals(0,run(input,new ByteArrayOutputStream()));
        for(String name:List.of("expression.tsv","size_factors.tsv","detected_genes.tsv","base_design.tsv","request.json"))Files.delete(input.directory().resolve(name));
        Path capture=input.directory().resolve("capture");assertEquals(0,captureCommand("VERIFY",capture,input.directory().resolve("verify"),new ByteArrayOutputStream()));assertEquals(0,captureCommand("REPLAY",capture,input.directory().resolve("replay"),new ByteArrayOutputStream()));
    }
    private static void reseal(Path capture) throws IOException {
        Files.writeString(capture.resolve("manifest.json"),NextflowJson.json(Map.of("schema_version",1,"profile",MonocleResponsePreparationCli.PROFILE,"files",Monocle100NeighborsFiles.snapshot(capture,67108864,100)))+"\n");
    }
    @ParameterizedTest
    @ValueSource(strings={"response","metadata","design","report","effective-request","input-population","marker","missing-marker"})
    void changedAndResealedCapturesCannotClaimCompleteArithmetic(String change) throws Exception {
        var input=input("quasi-design");assertEquals(0,run(input,new ByteArrayOutputStream()));Path capture=input.directory().resolve("capture");
        switch(change) {
            case "response"->edit(capture.resolve("responses.tsv"),1,1,"123");
            case "metadata"->edit(capture.resolve("log_n_genes.tsv"),1,1,"123");
            case "design"->edit(capture.resolve("design.tsv"),1,1,"123");
            case "report"->{var report=read(capture.resolve("analysis.json"));report.put("scientific_test_admitted",true);Files.writeString(capture.resolve("analysis.json"),NextflowJson.json(report)+"\n");}
            case "effective-request"->{var request=read(capture.resolve("effective-request.json"));request.put("input_provenance","changed");Files.writeString(capture.resolve("effective-request.json"),NextflowJson.json(request)+"\n");}
            case "input-population"->Files.writeString(capture.resolve("inputs/ignored.tsv"),"ignored");
            case "marker"->{var marker=read(capture.resolve("COMPLETED.json"));marker.put("scientific_test_admitted",true);Files.writeString(capture.resolve("COMPLETED.json"),NextflowJson.json(marker)+"\n");}
            case "missing-marker"->Files.delete(capture.resolve("COMPLETED.json"));
            default->throw new AssertionError(change);
        }
        reseal(capture);assertEquals(2,captureCommand("VERIFY",capture,input.directory().resolve("refused"),new ByteArrayOutputStream()));assertFalse(Files.exists(input.directory().resolve("refused")));
    }
    @Test void brokenConsoleCannotLeaveAnAdmittedPreparation() throws Exception {
        var input=input("quasi-design");Path request=input.directory().resolve("request.json");NextflowFiles.writeJson(request,input.request());
        var broken=new PrintStream(new OutputStream(){@Override public void write(int value)throws IOException{throw new IOException("owned broken output");}},true,StandardCharsets.UTF_8);
        assertEquals(2,MonocleResponsePreparationCli.run(new String[]{"RUN","--request",request.toString()},broken,stream(new ByteArrayOutputStream())));assertFalse(Files.exists(input.directory().resolve("capture/COMPLETED.json")));assertEquals(false,read(input.directory().resolve("capture/analysis.json")).get("response_preparation_admitted"));
    }
}
