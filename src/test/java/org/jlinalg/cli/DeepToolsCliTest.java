package org.jlinalg.cli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DeepToolsCliTest {
    @TempDir Path dir;private String output;
    private Path file(String name,String content)throws Exception {Path p=dir.resolve(name);Files.writeString(p,content);return p;}
    private int run(String name,String method,Path first,Path sizes,String...rest)throws Exception {
        var args=new ArrayList<>(List.of("deep-tools","--method",method,"--input",first.toString(),"--sizes",sizes.toString(),"--genome-build","SYNTHETIC","--bin-size","5","--out",dir.resolve(name).toString(),"--no-config"));args.addAll(List.of(rest));var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);int code=JLinAlgCli.run(args.toArray(String[]::new),stream,stream);output=bytes.toString();if(code!=0&&Files.exists(dir.resolve(name+"/WARNINGS.md")))output+=Files.readString(dir.resolve(name+"/WARNINGS.md"));return code;
    }
    private List<String[]> rows(String name)throws Exception {return Files.readAllLines(dir.resolve(name+"/results.tsv")).stream().skip(1).map(s->s.split("\t")).toList();}
    private String[] comparison(Path second,String missing,String invalid){return new String[]{"--input2",second.toString(),"--operation","subtract","--missing",missing,"--invalid",invalid};}
    @Test void globalAndCommandHelpExposeNativeScopeAndExistingPrivateTools()throws Exception {
        var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);assertEquals(0,JLinAlgCli.run(new String[]{"--help","--no-config"},stream,stream));String text=bytes.toString();assertTrue(text.contains("deep-tools --help"));assertTrue(text.contains("trimmomatic-tools --help"));assertTrue(text.contains("magma-tools --help"));bytes.reset();assertEquals(0,JLinAlgCli.run(new String[]{"deep-tools","--help","--no-config"},stream,stream));assertTrue(bytes.toString().contains("No BAM/BigWig import"));
    }
    @Test void exactWeightedBinsAndExplicitMissingEstimandsPreserveAllReferencePositions()throws Exception {
        Path sizes=file("sizes","chrA\t11\nchrAbsent\t4\n"),a=file("a","chrA\t0\t2\t4\nchrA\t2\t5\t2\nchrA\t5\t10\t6\n"),b=file("b","chrA\t0\t5\t1\nchrA\t5\t11\t3\n");
        assertEquals(0,run("zero","compare-native",a,sizes,comparison(b,"zero","error")),output);var result=rows("zero");assertEquals(4,result.size());assertEquals(1.8,Double.parseDouble(result.get(0)[7]),1e-14);assertEquals(3,Double.parseDouble(result.get(1)[7]));assertEquals(-3,Double.parseDouble(result.get(2)[7]));assertEquals("chrAbsent",result.get(3)[0]);assertTrue(Files.readString(dir.resolve("zero/WARNINGS.md")).contains("UNREPRESENTED BASES"));
        assertEquals(0,run("covered","compare-native",a,sizes,comparison(b,"covered","retain")),output);assertEquals("MISSING_INPUT",rows("covered").get(2)[8]);assertEquals("NA",rows("covered").get(2)[7]);assertEquals(2,Files.readAllLines(dir.resolve("covered/signal.bedgraph")).size());
        assertEquals(2,run("missing","compare-native",a,sizes,comparison(b,"error","error")),output);assertFalse(Files.exists(dir.resolve("missing/results.tsv")));assertTrue(Files.exists(dir.resolve("missing/partial")));
    }
    @Test void zeroFilterPrecedesScaleAndPseudocountAndNeverHidesOmissions()throws Exception {
        Path sizes=file("sizes","c\t5\n"),a=file("a","c\t0\t5\t1\n"),b=file("b","c\t0\t5\t2\n");
        assertEquals(0,run("scaled-zero","compare-native",a,sizes,"--input2",b.toString(),"--operation","ratio","--missing","zero","--invalid","error","--scale1","0","--scale2","0","--pseudo1","1","--pseudo2","2","--skip-zero-zero","true"),output);assertEquals(.5,Double.parseDouble(rows("scaled-zero").get(0)[7]));assertEquals("FINITE",rows("scaled-zero").get(0)[8]);
        Path zero=file("zero","c\t0\t5\t0\n");assertEquals(0,run("omitted","compare-native",zero,sizes,"--input2",zero.toString(),"--operation","ratio","--missing","zero","--invalid","retain","--pseudo1","1","--pseudo2","2","--skip-zero-zero","true"),output);assertEquals("omitted-zero-over-zero",rows("omitted").get(0)[8]);assertEquals("NA",rows("omitted").get(0)[7]);assertEquals(0,Files.size(dir.resolve("omitted/signal.bedgraph")));assertTrue(Files.readString(dir.resolve("omitted/WARNINGS.md")).contains("NO FINITE"));
    }
    @Test void suppliedRawBinCountsUseNominalWidthIncludingShortTerminalBins()throws Exception {
        Path sizes=file("sizes","c\t11\n"),counts=file("counts","c\t0\t5\t10\nc\t5\t10\t20\nc\t10\t11\t2\n");String before=Files.readString(counts);
        assertEquals(0,run("rpkm","scale-native",counts,sizes,"--normalization","RPKM","--count-kind","raw","--mapped-alignments","1000000"),output);var r=rows("rpkm");assertEquals(2000,Double.parseDouble(r.get(0)[5]));assertEquals(4000,Double.parseDouble(r.get(1)[5]));assertEquals(400,Double.parseDouble(r.get(2)[5]));assertTrue(Files.readString(dir.resolve("rpkm/WARNINGS.md")).contains("SUPPLIED NORMALIZATION CONTRACT"));assertEquals(before,Files.readString(counts));
        assertEquals(0,run("rpgc","scale-native",counts,sizes,"--normalization","RPGC","--count-kind","raw","--mapped-alignments","20","--effective-genome-size","100","--represented-length","10"),output);assertEquals(5,Double.parseDouble(rows("rpgc").get(0)[5]));
    }
    @Test void fractionalRawCountsAndBadGridRequireExplicitRepair()throws Exception {
        Path sizes=file("sizes","c\t10\n"),fraction=file("fraction","c\t0\t5\t1.5\n"),bad=file("bad","c\t1\t5\t2\n");
        assertEquals(2,run("raw","scale-native",fraction,sizes,"--normalization","NONE","--count-kind","raw"),output);assertTrue(output.contains("raw mode requires integers"));assertFalse(Files.exists(dir.resolve("raw/results.tsv")));
        assertEquals(0,run("estimate","scale-native",fraction,sizes,"--normalization","NONE","--count-kind","estimate","--scale-factor","2"),output);assertEquals(3,Double.parseDouble(rows("estimate").get(0)[5]));assertTrue(Files.readString(dir.resolve("estimate/WARNINGS.md")).contains("PROCESSED/WEIGHTED"));
        assertEquals(2,run("grid","scale-native",bad,sizes,"--normalization","NONE","--count-kind","raw"),output);assertTrue(output.contains("nominal bin grid"));
        assertEquals(2,run("zero-depth","scale-native",fraction,sizes,"--normalization","CPM","--count-kind","estimate","--mapped-alignments","0"),output);assertTrue(output.contains("positive kept alignments"));
    }
    @Test void undefinedArithmeticIsRetainedAsNaRatherThanZero()throws Exception {
        Path sizes=file("sizes","c\t5\n"),a=file("a","c\t0\t5\t0\n");assertEquals(0,run("undefined","compare-native",a,sizes,"--input2",a.toString(),"--operation","ratio","--missing","zero","--invalid","retain"),output);assertEquals("NONFINITE_RESULT",rows("undefined").get(0)[8]);assertEquals("NA",rows("undefined").get(0)[7]);assertTrue(Files.readString(dir.resolve("undefined/WARNINGS.md")).contains("NONFINITE/MISSING BINS"));
        assertEquals(2,run("limit","compare-native",a,sizes,"--input2",a.toString(),"--operation","add","--missing","zero","--invalid","error","--max-bins","0"),output);
    }
    @Test void longReferenceGeometryDoesNotNarrowToInt()throws Exception {
        Path sizes=file("sizes","long\t5000000001\n"),a=file("a","long\t0\t5000000001\t1\n"),b=file("b","long\t0\t5000000001\t2\n");
        var args=new String[]{"deep-tools","--method","compare-native","--input",a.toString(),"--input2",b.toString(),"--sizes",sizes.toString(),"--genome-build","SYNTHETIC","--bin-size","5000000000","--operation","ratio","--missing","error","--invalid","error","--out",dir.resolve("long").toString(),"--no-config"};var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);assertEquals(0,JLinAlgCli.run(args,stream,stream),bytes.toString());var r=rows("long");assertEquals(2,r.size());assertEquals("5000000001",r.get(1)[2]);assertEquals(.5,Double.parseDouble(r.get(1)[7]));
    }
}
