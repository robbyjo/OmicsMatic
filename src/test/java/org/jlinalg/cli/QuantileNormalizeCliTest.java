package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuantileNormalizeCliTest {
    @TempDir Path dir;private String output;
    private Path file(String name,String text)throws Exception{Path path=dir.resolve(name);Files.writeString(path,text);return path;}
    private int run(String name,Path matrix,String kind,String...extra)throws Exception{
        var args=new ArrayList<>(List.of("quantile-normalize","--matrix",matrix.toString(),"--id-column","feature","--columns","B,A","--axis","columns","--input-kind",kind,"--out",dir.resolve(name).toString(),"--no-config"));args.addAll(List.of(extra));var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);int code=JLinAlgCli.run(args.toArray(String[]::new),stream,stream);output=bytes.toString();return code;
    }
    @Test void exactIdsSelectedColumnOrderAndScientificWarningsAreRetained()throws Exception{
        Path matrix=file("input.tsv","feature\tA\tB\tignored\n001\t3\t1\tx\nENSG.4\t1\t3\tx\n003\t3\t2\tx\n004\t2\t3\tx\n");String before=Files.readString(matrix);
        assertEquals(0,run("normalized",matrix,"raw-counts"),output);var rows=Files.readAllLines(dir.resolve("normalized/results.tsv"));assertEquals("feature\tB\tA",rows.get(0));assertEquals("001\t1.0\t3.0",rows.get(1));assertTrue(rows.get(2).startsWith("ENSG.4\t"));assertEquals(before,Files.readString(matrix));
        assertTrue(output.contains("ANALYSIS WARNINGS"));assertTrue(Files.readString(dir.resolve("normalized/WARNINGS.md")).contains("not raw counts"));assertTrue(Files.readString(dir.resolve("normalized/session.yaml")).contains("source_sha256"));assertTrue(Files.readString(dir.resolve("normalized/analysis-status.json")).contains("completed-with-warnings"));
    }
    @Test void suppliedTargetSortsRanksRatherThanMatchingTargetLabels()throws Exception{
        Path matrix=file("input.tsv","feature\tA\tB\nf1\t3\t1\nf2\t1\t3\nf3\t3\t2\nf4\t2\t3\n"),target=file("target.tsv","unrelated\tvalue\nz\t8\ny\t2\nx\t6\nw\t4\n");
        assertEquals(0,run("targeted",matrix,"continuous","--target",target.toString(),"--target-column","value"),output);assertEquals("f1\t2.0\t7.0",Files.readAllLines(dir.resolve("targeted/results.tsv")).get(1));assertEquals("1\t2.0",Files.readAllLines(dir.resolve("targeted/target.tsv")).get(1));
        Path shortTarget=file("short.tsv","value\n1\n");assertEquals(2,run("wrong-target",matrix,"continuous","--target",shortTarget.toString(),"--target-column","value"),output);assertFalse(Files.exists(dir.resolve("wrong-target/results.tsv")));assertTrue(Files.readString(dir.resolve("wrong-target/WARNINGS.md")).contains("Target length"));
    }
    @Test void countContractsMissingValuesAndDuplicateIdsRetainFailedBundles()throws Exception{
        for(String value:new String[]{"NaN","NA","Infinity","1.5","-1"}){
            Path matrix=file("bad-"+value+".tsv","feature\tA\tB\nf\t"+value+"\t2\n");String name="failed-"+value;assertEquals(2,run(name,matrix,"raw-counts"),output);assertFalse(Files.exists(dir.resolve(name+"/results.tsv")));assertTrue(Files.exists(dir.resolve(name+"/WARNINGS.md")));assertTrue(Files.readString(dir.resolve(name+"/analysis-status.json")).contains("failed"));
        }
        Path duplicate=file("duplicate.tsv","feature\tA\tB\n001\t1\t2\n001\t2\t1\n");assertEquals(2,run("duplicate",duplicate,"continuous"),output);assertTrue(Files.readString(dir.resolve("duplicate/WARNINGS.md")).contains("Duplicate row ID"));
        Path estimated=file("estimated.tsv","feature\tA\tB\nf\t1.5\t2.5\n");assertEquals(0,run("estimated",estimated,"estimated-counts"),output);
    }
    @Test void resourceLimitsAndOrientationFailExplicitlyAndHelpNamesNativeScope()throws Exception{
        Path matrix=file("input.tsv","feature\tA\tB\nf1\t1\t2\nf2\t2\t1\n");assertEquals(2,run("cells",matrix,"continuous","--max-cells","3"),output);assertTrue(Files.readString(dir.resolve("cells/WARNINGS.md")).contains("max-cells"));assertEquals(2,run("bytes",matrix,"continuous","--max-bytes","1"),output);
        var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);assertEquals(0,JLinAlgCli.run(new String[]{"quantile-normalize","--help","--no-config"},stream,stream));assertTrue(bytes.toString().contains("No imputation"));assertEquals(2,JLinAlgCli.run(new String[]{"quantile-normalize","--matrix",matrix.toString(),"--id-column","feature","--columns","A,B","--axis","rows","--input-kind","continuous","--out",dir.resolve("wrong-axis").toString(),"--no-config"},stream,stream));assertTrue(bytes.toString().contains("axis0 orientation"));
    }
    @Test void rawIntegerTextCannotSilentlyRoundIntoAnIntegerDouble()throws Exception{
        for(String value:new String[]{"1.0000000000000001","9007199254740993","9007199254740992"}){
            Path matrix=file("precise-"+value+".tsv","feature\tA\tB\nf\t"+value+"\t2\n");String name="reject-"+value;
            assertEquals(2,run(name,matrix,"raw-counts"),output);assertFalse(Files.exists(dir.resolve(name+"/results.tsv")));assertTrue(Files.readString(dir.resolve(name+"/WARNINGS.md")).contains("exact integer in0..9007199254740991"));
        }
        Path valid=file("safe.tsv","feature\tA\tB\nf\t9007199254740991\t9007199254740991\n");assertEquals(0,run("safe",valid,"raw-counts"),output);assertEquals(9007199254740991d,Double.parseDouble(Files.readAllLines(dir.resolve("safe/results.tsv")).get(1).split("\t")[1]));
    }
}
