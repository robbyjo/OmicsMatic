/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MolecularGeometryCliTest {
    @TempDir Path root;
    private static final String MEASURE="measurement_id\tkind\tax\tay\taz\tbx\tby\tbz\tcx\tcy\tcz\tdx\tdy\tdz\n";
    private static final String FIT="pair_id\tmobile_x\tmobile_y\tmobile_z\ttarget_x\ttarget_y\ttarget_z\n";
    private record Run(int code,String out,String err) { }
    private static Run run(String... args) {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
        int code=MolecularGeometryCli.run(args,new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));
        return new Run(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));
    }
    private Path file(String name,String text)throws Exception {Path path=root.resolve(name);Files.writeString(path,text,StandardCharsets.UTF_8);return path;}
    private static String measurement(String id,String kind,String... coordinates) {
        String[] f=new String[14];Arrays.fill(f,"");f[0]=id;f[1]=kind;System.arraycopy(coordinates,0,f,2,coordinates.length);return String.join("\t",f)+"\n";
    }
    private static List<String> args(Path input,Path out,String operation,String units) {
        return List.of("--operation",operation,"--input",input.toString(),"--units",units,"--out",out.toString());
    }
    private static Run invoke(List<String> base,String... extra) {var list=new java.util.ArrayList<>(base);list.addAll(List.of(extra));return run(list.toArray(String[]::new));}
    private static void failed(Path dir)throws Exception {
        assertTrue(Files.readString(dir.resolve("analysis-status.json")).contains("failed"));
        assertTrue(Files.readString(dir.resolve("WARNINGS.md")).contains("Required action:"));
        assertFalse(Files.exists(dir.resolve("results.tsv")));
    }
    @Test void distanceAngleAndTorsionRetainOrderAndUnits()throws Exception {
        Path input=file("measure.tsv",MEASURE+measurement("d","distance","0","0","0","3","4","0")
                +measurement("a","angle","1","0","0","0","0","0","0","1","0")
                +measurement("t","dihedral","0","1","0","0","0","0","1","0","0","1","0","1"));
        Path out=root.resolve("measure");var run=invoke(args(input,out,"measure","angstrom"));assertEquals(0,run.code(),run.err());
        var rows=Files.readAllLines(out.resolve("results.tsv"));assertEquals(4,rows.size());
        assertEquals("d\tdistance\t5.0\tangstrom",rows.get(1));assertEquals("a\tangle\t90.0\tdegree",rows.get(2));assertEquals("t\tdihedral\t90.0\tdegree",rows.get(3));
        assertTrue(run.err().contains("ANALYSIS WARNINGS"));
    }
    @Test void nanometersConvertAllCoordinatesBeforeMeasurements()throws Exception {
        Path input=file("nm.tsv",MEASURE+measurement("d","distance","0","0","0",".3",".4","0"));Path out=root.resolve("nm");
        assertEquals(0,invoke(args(input,out,"measure","nanometer")).code());
        assertTrue(Files.readString(out.resolve("results.tsv")).contains("5.0\tangstrom"));
        assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("multiplied by10"));
    }
    @Test void fitsUseExplicitPairsAndExportEveryResidual()throws Exception {
        Path input=file("fit.tsv",FIT+"one\t0\t0\t0\t10\t20\t30\n"+"two\t1\t0\t0\t10\t21\t30\n"+"three\t0\t2\t0\t8\t20\t30\n"+"four\t0\t0\t3\t10\t20\t33\n");
        Path out=root.resolve("fit");var run=invoke(args(input,out,"fit","angstrom"),"--degeneracy","error");assertEquals(0,run.code(),run.err());
        String[] summary=Files.readAllLines(out.resolve("results.tsv")).get(1).split("\t");assertEquals("4",summary[0]);assertEquals(0,Double.parseDouble(summary[2]),1e-12);assertEquals("true",summary[4]);
        var rows=Files.readAllLines(out.resolve("fit-pairs.tsv"));assertEquals(5,rows.size());assertTrue(rows.get(1).startsWith("one\t"));
        assertTrue(Files.readString(out.resolve("session.yaml")).contains("no scale or reflection"));
    }
    @Test void nonuniqueFitNeedsExplicitPermissionAndWarns()throws Exception {
        Path input=file("line.tsv",FIT+"a\t0\t0\t0\t0\t0\t0\n"+"b\t1\t0\t0\t1\t0\t0\n"+"c\t2\t0\t0\t2\t0\t0\n");
        Path denied=root.resolve("denied");assertEquals(2,invoke(args(input,denied,"fit","angstrom"),"--degeneracy","error").code());failed(denied);
        Path allowed=root.resolve("allowed");assertEquals(0,invoke(args(input,allowed,"fit","angstrom"),"--degeneracy","allow-nonunique").code());
        assertTrue(Files.readString(allowed.resolve("WARNINGS.md")).contains("NONUNIQUE FIT EXPLICITLY ALLOWED"));
        assertTrue(Files.readString(allowed.resolve("results.tsv")).contains("false"));
    }
    @Test void failedSecondRowQuarantinesEarlierScientificOutput()throws Exception {
        String row=measurement("same","distance","0","0","0","1","0","0");Path input=file("duplicates.tsv",MEASURE+row+row);Path out=root.resolve("duplicates");
        assertEquals(2,invoke(args(input,out,"measure","angstrom")).code());failed(out);
        assertTrue(Files.readString(out.resolve("partial/results.tsv")).contains("same\tdistance"));
    }
    @Test void malformedAndUndefinedMeasurementsFailWithNamedRecovery()throws Exception {
        var rows=List.of(measurement("bad","angle","0","0","0","0","0","0","1","0","0"),measurement("bad","distance","NaN","0","0","1","0","0"),measurement("bad","unknown","0","0","0","1","0","0"),measurement("bad","distance","0","0","0","1","0","0","7","0","0"));
        for(int i=0;i<rows.size();i++) {Path input=file("bad"+i+".tsv",MEASURE+rows.get(i));Path out=root.resolve("bad"+i);assertEquals(2,invoke(args(input,out,"measure","angstrom")).code());failed(out);}
    }
    @Test void declaredRowAndByteBoundsAreEnforced()throws Exception {
        Path input=file("bounded.tsv",MEASURE+measurement("a","distance","0","0","0","1","0","0")+measurement("b","distance","0","0","0","1","0","0"));
        Path row=root.resolve("row");assertEquals(2,invoke(args(input,row,"measure","angstrom"),"--max-rows","1").code());failed(row);
        Path bytes=root.resolve("bytes");assertEquals(2,invoke(args(input,bytes,"measure","angstrom"),"--max-bytes","1").code());failed(bytes);
    }
    @Test void unitsAndFitPolicyAreNeverInferred()throws Exception {
        Path input=file("empty.tsv",FIT);
        for(String units:List.of("unknown","angstrom")) {Path out=root.resolve("policy-"+units);assertEquals(2,invoke(args(input,out,"fit",units)).code());failed(out);}
        Path out=root.resolve("no-units");assertEquals(2,run("--operation","fit","--input",input.toString(),"--degeneracy","error","--out",out.toString()).code());failed(out);
    }
    @Test void utf8RecipesSupportUnicodePathsAndDirectOverrides()throws Exception {
        Path input=file("输入.tsv",MEASURE+measurement("α","distance","0","0","0","3","4","0"));Path out=root.resolve("结果");Path unused=root.resolve("unused");
        Path recipe=file("recipe.json",RegenieRuntime.json(Map.of("operation","measure","input",input.toString(),"units","nanometer","out",unused.toString())));
        var result=run("--options-file",recipe.toString(),"--units","angstrom","--out",out.toString());assertEquals(0,result.code(),result.err());
        assertTrue(Files.readString(out.resolve("results.tsv")).contains("α\tdistance\t5.0\tangstrom"));assertFalse(Files.exists(unused));
        assertTrue(Files.readString(out.resolve("session.yaml")).contains("options_file_sha256"));
    }
    @Test void existingOutputsAndUnknownDuplicateOptionsArePreserved()throws Exception {
        Path out=Files.createDirectory(root.resolve("existing"));Path marker=out.resolve("marker");Files.writeString(marker,"keep");
        assertEquals(2,run("--operation","measure","--out",out.toString()).code());assertEquals("keep",Files.readString(marker));
        assertEquals(2,run("--unknown","x").code());assertEquals(2,run("--out","a","--out","b").code());
        assertTrue(run("--help").out().contains("PyMOL align/super"));
    }
}
