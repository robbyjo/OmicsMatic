package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IgvToolsCliTest {
    @TempDir Path root;
    private record Run(int code,String out,String err) { }
    private static Run run(String... args){var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=IgvToolsCli.run(args,new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));return new Run(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));}
    private Path recipe()throws Exception {Path fa=root.resolve("参考.fa"),bed=root.resolve("features.bed");Files.writeString(fa,">chr1\n"+"ACGT".repeat(250)+"\n");Files.writeString(bed,"chr1\t1\t50\tfeature\t100\t+\n");var recipe=Map.of("assembly","synthetic","reference",fa.toString(),"reference_index","generate","width",1200,"height",850,"sequence",true,"views",List.of(Map.of("id","v","contig","chr1","start",0,"end",100)),"tracks",List.of(Map.of("id","feature","kind","bed6","file",bed.toString(),"height",80,"display","expanded","color",List.of(1,2,3),"negative_color",List.of(4,5,6))));Path input=root.resolve("views.json");Files.writeString(input,RegenieRuntime.json(recipe));return input;}
    private static void status(Path out,String expected)throws Exception {assertTrue(Files.readString(out.resolve("analysis-status.json")).contains(expected));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("Required action:"));assertFalse(Files.exists(out.resolve("results.json")));assertFalse(Files.exists(out.resolve("v.png")));}
    @Test void missingRuntimeSkipsWithRetainedInputsAndExplicitRecovery()throws Exception {Path out=root.resolve("skip");Run result=run("--recipe",recipe().toString(),"--out",out.toString());assertEquals(3,result.code(),result.err());status(out,"skipped-unavailable-dependency");assertTrue(Files.isRegularFile(out.resolve("partial/inputs/reference.fa")));assertTrue(result.err().contains("ANALYSIS SKIPPED"));assertTrue(Files.readString(out.resolve("WARNINGS.md")).contains("--allow-install"));}
    @Test void unadmittedRuntimeSkipsWithoutLaunchingProcess()throws Exception {Path home=Files.createDirectory(root.resolve("unadmitted")),out=root.resolve("skip");Files.writeString(home.resolve(".jlinalg-igv.json"),"{}");assertEquals(3,run("--recipe",recipe().toString(),"--home",home.toString(),"--out",out.toString()).code());status(out,"skipped-unavailable-dependency");assertFalse(Files.readString(out.resolve("session.json")).contains("owned_pid"));}
    @Test void malformedInputFailsBeforeDependencyCheck()throws Exception {Path recipe=recipe(),out=root.resolve("fail");Files.writeString(recipe,"{}");assertEquals(2,run("--recipe",recipe.toString(),"--out",out.toString()).code());status(out,"failed");}
    @Test void unicodeOptionsCarrierAndExistingOutputPreservation()throws Exception {Path out=root.resolve("未安装"),input=recipe(),options=root.resolve("options.json");Files.writeString(options,RegenieRuntime.json(Map.of("recipe",input.toString(),"out",out.toString())));assertEquals(3,run("--options-file",options.toString()).code());status(out,"skipped-unavailable-dependency");assertTrue(Files.readString(out.resolve("session.json")).contains("recipe-sha256"));Files.writeString(out.resolve("keep"),"preserved");assertEquals(2,run("--recipe",input.toString(),"--out",out.toString()).code());assertEquals("preserved",Files.readString(out.resolve("keep")));}
    @Test void helpUnknownDuplicateAndRecursiveOptionsAreExplicit()throws Exception {assertTrue(run("--help").out().contains("every base-count column"));assertEquals(2,run("--out","one","--out","two").code());Path options=root.resolve("bad.json");Files.writeString(options,"{\"options-file\":\"again.json\"}");assertEquals(2,run("--options-file",options.toString()).code());assertEquals(2,run("--unknown","bad").code());}
}
