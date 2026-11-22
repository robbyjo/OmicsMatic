/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ElectrostaticGridCliTest {
    @TempDir Path root;
    private static final String GRID="""
        object 1 class gridpositions counts 2 2 2
        origin 0 0 0
        delta 1 0 0
        delta 0 1 0
        delta 0 0 1
        object 2 class gridconnections counts 2 2 2
        object 3 class array type double rank 0 items 8 data follows
        0 1 10 11 100 101 110 111
        attribute "dep" string "positions"
        object "scalar grid" class field
        component "positions" value 1
        component "connections" value 2
        component "data" value 3
        """;
    private record Run(int code,String out,String err) { }
    private static Run run(String... arguments) {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
        int code=ElectrostaticGridCli.run(arguments,new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));
        return new Run(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));
    }
    private Path file(String name,String contents)throws Exception {Path path=root.resolve(name);Files.writeString(path,contents,StandardCharsets.UTF_8);return path;}
    private static List<String> args(Path input,Path out) {return List.of("--input",input.toString(),"--length-units","angstrom","--value-units","arbitrary","--out",out.toString());}
    private static Run invoke(List<String> arguments,String... extra) {var values=new ArrayList<>(arguments);values.addAll(List.of(extra));return run(values.toArray(String[]::new));}
    private static void failed(Path out)throws Exception {
        assertTrue(Files.readString(out.resolve("analysis-status.json")).contains("failed"));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("Required action:"));
        assertFalse(Files.exists(out.resolve("grid.json")));assertFalse(Files.exists(out.resolve("samples.tsv")));
    }
    @Test void fullGridInspectionRetainsUnitsSourceAndNodeStatistics()throws Exception {
        Path input=file("grid.dx",GRID),out=root.resolve("inspection");var run=invoke(args(input,out));assertEquals(0,run.code(),run.err());assertTrue(run.err().contains("WARNINGS.md"));
        assertEquals(GRID,Files.readString(out.resolve("original.dx")));String grid=Files.readString(out.resolve("grid.json"));assertTrue(grid.contains("55.5"));assertTrue(grid.contains("arbitrary"));assertFalse(grid.contains("volts_per"));assertFalse(Files.exists(out.resolve("samples.tsv")));
        assertTrue(Files.readString(out.resolve("session.json")).contains("source_sha256"));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("does not run APBS"));
    }
    @Test void interiorAndClosedBoundarySamplingPreserveOrder()throws Exception {
        Path input=file("grid.dx",GRID),points=file("points.tsv","point_id\tx\ty\tz\ncenter\t.5\t.5\t.5\nlast\t1\t1\t1\n"),out=root.resolve("sample");
        var run=invoke(args(input,out),"--points",points.toString(),"--outside","error");assertEquals(0,run.code(),run.err());var rows=Files.readAllLines(out.resolve("samples.tsv"));assertEquals(3,rows.size());assertTrue(rows.get(1).contains("true\t55.5\tarbitrary\t\t"));assertTrue(rows.get(2).contains("true\t111.0\tarbitrary\t\t"));
    }
    @Test void missingOutsideIsBlankAndProminentWhileErrorQuarantinesPartials()throws Exception {
        Path input=file("grid.dx",GRID),points=file("points.tsv","point_id\tx\ty\tz\ninside\t.5\t.5\t.5\noutside\t2\t0\t0\n"),missing=root.resolve("missing"),error=root.resolve("error");
        assertEquals(0,invoke(args(input,missing),"--points",points.toString(),"--outside","missing").code());String row=Files.readAllLines(missing.resolve("samples.tsv")).get(2);assertTrue(row.contains("false\t\tarbitrary\t\t"));assertTrue(Files.readString(missing.resolve("WARNINGS.md")).contains("OUT-OF-GRID SAMPLES: 1"));
        assertEquals(2,invoke(args(input,error),"--points",points.toString(),"--outside","error").code());failed(error);assertTrue(Files.exists(error.resolve("partial/samples.tsv")));
    }
    @Test void validZeroGridRemainsSuccessfulFiniteZero()throws Exception {
        Path input=file("zero.dx",GRID.replace("0 1 10 11 100 101 110 111","0 0 0 0 0 0 0 0")),points=file("point.tsv","point_id\tx\ty\tz\nzero\t.2\t.4\t.6\n"),out=root.resolve("zero");
        var run=invoke(args(input,out),"--points",points.toString(),"--outside","error");assertEquals(0,run.code(),run.err());assertTrue(Files.readString(out.resolve("samples.tsv")).contains("true\t0.0\tarbitrary"));
    }
    @Test void explicitNanometersScaleBothGeometryAndPoints()throws Exception {
        Path input=file("grid.dx",GRID),points=file("points.tsv","point_id\tx\ty\tz\ncenter\t.5\t.5\t.5\n"),out=root.resolve("nm");
        var options=new ArrayList<>(args(input,out));options.set(options.indexOf("angstrom"),"nanometer");var run=invoke(options,"--points",points.toString(),"--outside","error");assertEquals(0,run.code(),run.err());assertTrue(Files.readString(out.resolve("samples.tsv")).contains("center\t5.0\t5.0\t5.0\ttrue\t55.5"));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("both grid geometry and point coordinates"));
    }
    @Test void thermalVoltageRequiresTemperatureAndUsesSignedConversion()throws Exception {
        Path input=file("grid.dx",GRID.replace("0 1 10 11 100 101 110 111","-5 -5 -5 -5 -5 -5 -5 -5")),points=file("point.tsv","point_id\tx\ty\tz\nnegative\t.5\t.5\t.5\n"),out=root.resolve("volts");var options=new ArrayList<>(args(input,out));options.set(options.indexOf("arbitrary"),"kt-per-e");
        var run=invoke(options,"--points",points.toString(),"--outside","error","--temperature","298.15");assertEquals(0,run.code(),run.err());String[] row=Files.readAllLines(out.resolve("samples.tsv")).get(1).split("\t",-1);assertEquals(-.12846289560542925,Double.parseDouble(row[7]),1e-16);
        Path missing=root.resolve("missing-temperature");options.set(options.indexOf(out.toString()),missing.toString());assertEquals(2,invoke(options).code());failed(missing);
    }
    @Test void undeclaredUnitsOrSamplingPoliciesFailWithRecovery()throws Exception {
        Path input=file("grid.dx",GRID),points=file("point.tsv","point_id\tx\ty\tz\none\t0\t0\t0\n");int index=0;
        for(List<String> extra:List.of(List.of("--temperature","298.15"),List.of("--points",points.toString()),List.of("--outside","missing"),List.of("--points",points.toString(),"--outside","extrapolate"))){Path out=root.resolve("policy-"+index++);assertEquals(2,invoke(args(input,out),extra.toArray(String[]::new)).code());failed(out);}
        Path out=root.resolve("no-units");assertEquals(2,run("--input",input.toString(),"--out",out.toString()).code());failed(out);
    }
    @Test void everyScalarIsAuditedEvenWhenNoPointRequestsIt()throws Exception {
        Path input=file("bad.dx",GRID.replace("110 111","110 NaN")),points=file("point.tsv","point_id\tx\ty\tz\nfirst\t0\t0\t0\n"),out=root.resolve("bad");assertEquals(2,invoke(args(input,out),"--points",points.toString(),"--outside","error").code());failed(out);assertTrue(Files.exists(out.resolve("partial/original.dx")));
    }
    @Test void originalSolverNaNGridIsQuarantinedDespiteItsSuccessfulExit()throws Exception {
        Path input=root.resolve("original-zero-rhs.dx");try(var raw=getClass().getResourceAsStream("/org/jlinalg/protein/apbs-grid/sphere-q0-65.dx.gz")){assertNotNull(raw);try(var gzip=new GZIPInputStream(raw)){Files.copy(gzip,input);}}
        Path out=root.resolve("original-invalid");var run=invoke(args(input,out));assertEquals(2,run.code());failed(out);assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("nonfinite"));assertEquals(Files.size(input),Files.size(out.resolve("partial/original.dx")));
    }
    @Test void malformedDuplicateAndExcessPointRowsFail()throws Exception {
        Path input=file("grid.dx",GRID);int index=0;for(String text:List.of("id\tx\ty\tz\nx\t0\t0\t0\n","point_id\tx\ty\tz\n","point_id\tx\ty\tz\nx\t0\t0\t0\nx\t1\t1\t1\n","point_id\tx\ty\tz\nx\tNaN\t0\t0\n")){Path points=file("badpoints"+index+".tsv",text),out=root.resolve("badpoints"+index++);assertEquals(2,invoke(args(input,out),"--points",points.toString(),"--outside","error").code());failed(out);}
        Path points=file("two.tsv","point_id\tx\ty\tz\na\t0\t0\t0\nb\t1\t1\t1\n"),out=root.resolve("bounded-points");assertEquals(2,invoke(args(input,out),"--points",points.toString(),"--outside","error","--max-points","1").code());failed(out);
    }
    @Test void byteAndGridPointLimitsFailBeforeAnalysis()throws Exception {
        Path input=file("grid.dx",GRID);for(String[] extra:new String[][]{{"--max-bytes","1"},{"--max-grid-points","7"}}){Path out=root.resolve(extra[0].substring(2));assertEquals(2,invoke(args(input,out),extra).code());failed(out);}
    }
    @Test void unicodeRecipeOverridesAndExistingOutputPreservation()throws Exception {
        Path input=file("输入.dx",GRID),points=file("坐标.tsv","point_id\tx\ty\tz\nα\t.5\t.5\t.5\n"),out=root.resolve("结果"),unused=root.resolve("unused");Path recipe=file("options.json",RegenieRuntime.json(Map.of("input",input.toString(),"points",points.toString(),"outside","error","length-units","angstrom","value-units","arbitrary","out",unused.toString())));
        var run=run("--options-file",recipe.toString(),"--out",out.toString());assertEquals(0,run.code(),run.err());assertTrue(Files.readString(out.resolve("samples.tsv")).contains("α\t"));assertFalse(Files.exists(unused));assertTrue(Files.readString(out.resolve("session.json")).contains("recipe-sha256"));
        Path existing=Files.createDirectory(root.resolve("existing")),marker=file("existing/marker","keep");assertEquals(2,invoke(args(input,existing)).code());assertEquals("keep",Files.readString(marker));assertEquals(2,run("--unknown","x").code());assertEquals(2,run("--out","a","--out","b").code());assertTrue(run("--help").out().contains("Zero/constant"));
    }
}
