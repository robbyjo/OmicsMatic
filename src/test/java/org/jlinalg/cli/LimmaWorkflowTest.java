/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

/** Synthetic original-runtime fixtures and meaningful R-free workflow refusal controls. */
final class LimmaWorkflowTest {
    private static final Map<String,byte[]> BLOBS=new HashMap<>();
    private static final Map<String,Object> CASES=fixtures();
    @TestFactory Stream<DynamicTest> savedOriginalCompositionsVerifyWithoutR() {
        return CASES.keySet().stream().map(name->DynamicTest.dynamicTest(name,()->LimmaWorkflowCli.verify(restore(name,home("original-"+name)))));
    }
    @Test void independentCoverageRetainsAllOperationAndBoundaryFamilies()throws Exception {
        assertEquals(45,CASES.size());var operations=new HashSet<String>();for(Object row:CASES.values())operations.add(text(object(row,"case"),"operation"));
        assertEquals(Set.of("LM","VOOM","EDGER_VOOM_LMFIT","BATCH","CAMERAPR","NORMALIZE"),operations);
        assertTrue(CASES.keySet().containsAll(Set.of("BORROW_ZERO_DF","BORROW_ZERO_DF_TREAT","UTF8_CHUNKS","SMALL_P","MODERN_TREND_SPAN","EDGER_SAMPLE_WEIGHTS","CAMERA_ONE_SET","MULTI_DEPENDENT")));
    }
    @Test void unicodeNumericAndNaLikeIdentitiesRemainDistinctAcrossOutputChunks()throws Exception {
        Path root=restore("UTF8_CHUNKS",home("utf8"));var input=SusieWorkflowFiles.table(root.resolve("worker/matrix.tsv"),3000,20);var output=SusieWorkflowFiles.table(root.resolve("results/expression.tsv"),3000,20);
        assertEquals(2051,input.rows().size());for(int i=0;i<input.rows().size();i++)assertEquals(input.rows().get(i)[0],output.rows().get(i)[0]);
        var ids=input.rows().stream().map(row->row[0]).toList();assertTrue(ids.containsAll(List.of("001","NA","gene_é","gene_<U+00E9>","gene_e\u0301")));assertEquals(2051,new HashSet<>(ids).size());
        assertTrue(Files.readString(root.resolve("results/coefficients.tsv")).startsWith("feature_id\tcontrast_é\n"));LimmaWorkflowCli.verify(root);
    }
    @Test void originalZeroDfNaSigmaIsKeptAndBorrowingIsAudited()throws Exception {
        Path root=restore("BORROW_ZERO_DF",home("zero-df"));var rows=Files.readAllLines(root.resolve("results/variance.tsv"));String[] first=rows.get(1).split("\t",-1);
        assertEquals("NA",first[1]);assertEquals(0,Double.parseDouble(first[2]));assertTrue(Double.parseDouble(first[5])>0);
        String before=FollowupSupport.hash(root.resolve("results/variance.tsv"));LimmaWorkflowCli.verify(root);assertEquals(before,FollowupSupport.hash(root.resolve("results/variance.tsv")));
        assertTrue(Files.readString(root.resolve("WARNINGS.md")).contains("zero-residual-df"));
    }
    @Test void providedNormalizationFactorsAreNotRecentered()throws Exception {
        Path root=restore("PROVIDED_LIBRARIES_FACTORS",home("provided"));var rows=Files.readAllLines(root.resolve("results/normalization.tsv"));for(String line:rows.subList(1,rows.size()))assertEquals(2,Double.parseDouble(line.split("\t",-1)[2]));LimmaWorkflowCli.verify(root);
    }
    @TestFactory Stream<DynamicTest> mathematicalAndSourceContradictionsFailAfterResealing() {
        return Stream.of("t","positive P zero","positive adjusted P zero","posterior","total df","source script","source zip","identity","journal truncated","convergence flag","snapshot deleted","sigma NA","extra result").map(name->DynamicTest.dynamicTest(name,()->{
            String source=name.startsWith("positive ")?"SMALL_P":name.equals("journal truncated")||name.equals("convergence flag")?"LM_BLOCK":"LM";
            Path root=restore(source,home("tamper"));
            switch(name) {
                case "t"->change(root.resolve("results/t.tsv"),1,1,"999");
                case "positive P zero"->positiveToZero(root.resolve("results/pvalue.tsv"));
                case "positive adjusted P zero"->positiveToZero(root.resolve("results/padjust.tsv"));
                case "posterior"->change(root.resolve("results/variance.tsv"),1,5,"123.456");
                case "total df"->change(root.resolve("results/variance.tsv"),1,6,"0.5");
                case "source script"->Files.writeString(root.resolve("source/limma_workflow.R"),"\n# changed\n",StandardCharsets.UTF_8,StandardOpenOption.APPEND);
                case "source zip"->Files.write(root.resolve("source/reference/source.zip"),new byte[]{0},StandardOpenOption.APPEND);
                case "identity"->{var lines=Files.readAllLines(root.resolve("worker/matrix.tsv"));lines.set(0,lines.get(0).replace("sample 3","Sample 3"));Files.write(root.resolve("worker/matrix.tsv"),lines,StandardCharsets.UTF_8);}
                case "journal truncated"->{Path file=root.resolve("worker/internal-model-journal.bin");try(var channel=java.nio.channels.FileChannel.open(file,StandardOpenOption.WRITE)){channel.truncate(channel.size()-1);}}
                case "convergence flag"->change(root.resolve("worker/convergence.tsv"),1,8,"0");
                case "snapshot deleted"->{try(var paths=Files.list(root.resolve("worker/objects"))){Files.delete(paths.sorted().findFirst().orElseThrow());}}
                case "sigma NA"->change(root.resolve("results/variance.tsv"),1,1,"NA");
                case "extra result"->Files.writeString(root.resolve("results/unreferenced.tsv"),"unused\n");
                default->throw new AssertionError();
            }
            LimmaWorkflowCli.seal(root);assertThrows(IOException.class,()->LimmaWorkflowCli.verify(root));
        }));
    }
    @Test void replayCannotWriteInsideOrOverwriteItsVerifiedParent()throws Exception {
        Path h=home("replay"),parent=restore("LM",h.resolve("parent"));String original=FollowupSupport.hash(parent.resolve("capture-files.json"));
        Path request=h.resolve("control.json");json(request,Map.of("schema_version",1,"mode","REPLAY","analysis",parent.toString(),"out",parent.resolve("nested").toString()));var error=new ByteArrayOutputStream();
        assertEquals(2,LimmaWorkflowCli.run(new String[]{"--request",request.toString()},new PrintStream(OutputStream.nullOutputStream()),new PrintStream(error)));
        assertTrue(error.toString(StandardCharsets.UTF_8).contains("outside the original analysis"));assertFalse(Files.exists(parent.resolve("nested")));assertEquals(original,FollowupSupport.hash(parent.resolve("capture-files.json")));LimmaWorkflowCli.verify(parent);
    }
    @Test void unavailableRuntimeSkipsAndLeavesProminentRecoveryInsteadOfResults()throws Exception {
        Path h=home("skip");var request=request(h);Path input=h.resolve("request.json");json(input,request);
        assertEquals(3,LimmaWorkflowCli.run(new String[]{"--request",input.toString()},new PrintStream(OutputStream.nullOutputStream()),new PrintStream(OutputStream.nullOutputStream())));
        Path root=h.resolve("analysis");assertFalse(Boolean.TRUE.equals(read(root.resolve("analysis-status.json")).get("results_admitted")));String warning=Files.readString(root.resolve("WARNINGS.md"));assertTrue(warning.contains("SKIP3"));assertTrue(warning.contains("missing-Rscript"));assertTrue(warning.contains("restore the named exact permitted"));assertThrows(IOException.class,()->LimmaWorkflowCli.verify(root));
    }
    @Test void unknownEditionSkipsWithoutMakingAnAnalysis()throws Exception {
        Path h=home("version");var r=request(h);r.put("profile","limma-3.62.2-R4.6.1");Path source=h.resolve("request.json");json(source,r);
        assertEquals(3,LimmaWorkflowCli.run(new String[]{"--request",source.toString()},new PrintStream(OutputStream.nullOutputStream()),new PrintStream(OutputStream.nullOutputStream())));assertFalse(Files.exists(h.resolve("analysis")));
    }
    @Test void invalidDataFailsBeforeAnUnavailableRuntimeCanHideIt()throws Exception {
        Path h=home("invalid");var r=request(h);Files.writeString(h.resolve("matrix.tsv"),"feature_id\ta\tb\tc\td\ng1\tNaN\t2\t3\t4\n");Path source=h.resolve("request.json");json(source,r);
        assertEquals(2,LimmaWorkflowCli.run(new String[]{"--request",source.toString()},new PrintStream(OutputStream.nullOutputStream()),new PrintStream(OutputStream.nullOutputStream())));assertFalse(Boolean.TRUE.equals(read(h.resolve("analysis/analysis-status.json")).get("results_admitted")));assertFalse(read(h.resolve("analysis/manifest.json")).containsKey("owned_worker_pid"));
    }
    @Test void unusedSettingsAndAmbiguousVoomPassesAreRefused()throws Exception {
        Path h=home("contract");var base=request(h);var span=new LinkedHashMap<>(base);span.put("moderation",Map.of("method","EBAYES","legacy","true","span",.6));assertThrows(IOException.class,()->LimmaWorkflowContracts.validate(span));
        var unknown=new LinkedHashMap<>(base);unknown.put("R_code","arbitrary()");assertThrows(IOException.class,()->LimmaWorkflowContracts.validate(unknown));
        var voom=new LinkedHashMap<>(base);voom.put("operation","VOOM");voom.put("units","raw-counts");voom.put("normalization",Map.of("method","none"));voom.put("block","block.tsv");voom.put("block_levels",List.of("one","two"));voom.put("correlation",Map.of("mode","estimate"));assertThrows(IOException.class,()->LimmaWorkflowContracts.validate(voom));
        var ignored=new LinkedHashMap<>(base);ignored.put("batch","unused.tsv");assertThrows(IOException.class,()->LimmaWorkflowContracts.validate(ignored));
    }
    @Test void rawDecimalFractionsCannotRoundIntoAcceptedIntegerCounts()throws Exception {
        Path h=home("raw-count");var r=request(h);r.put("operation","VOOM");r.put("units","raw-counts");r.put("normalization",Map.of("method","none"));r.put("correlation",Map.of("mode","none"));
        Files.writeString(h.resolve("matrix.tsv"),"feature_id\ta\tb\tc\td\ng1\t2.00000000000000001\t2\t3\t4\n");var effective=LimmaWorkflowContracts.validate(r);Files.createDirectory(h.resolve("worker"));assertThrows(IOException.class,()->LimmaWorkflowFiles.prepare(h,effective,false));
    }
    @Test void quadraticAdjustmentBudgetIsCheckedBeforeLaunchingR()throws Exception {
        Path h=home("budget");var r=request(h);r.put("adjust_method","hommel");r.put("maximum_work",100000);var rows=new StringBuilder("feature_id\ta\tb\tc\td\n");for(int i=0;i<1000;i++)rows.append('g').append(i).append("\t1\t2\t3\t4\n");Files.writeString(h.resolve("matrix.tsv"),rows.toString());Files.createDirectory(h.resolve("worker"));assertThrows(IOException.class,()->LimmaWorkflowFiles.prepare(h,LimmaWorkflowContracts.validate(r),false));
    }
    @AfterAll static void report(){System.out.println("LIMMA_SAVED_ORIGINAL_CASES="+CASES.size()+" R_FREE_VERIFY=true");}
    private static Map<String,Object> request(Path h)throws IOException {
        Files.writeString(h.resolve("matrix.tsv"),"feature_id\ta\tb\tc\td\ng1\t1\t2\t3\t4\ng2\t3\t2\t5\t4\ng3\t2\t3\t4\t7\n");Files.writeString(h.resolve("design.tsv"),"sample_id\tIntercept\tgroup\na\t1\t0\nb\t1\t0\nc\t1\t1\nd\t1\t1\n");Files.createDirectory(h.resolve("libraries"));var r=new LinkedHashMap<String,Object>();
        r.put("schema_version",1);r.put("mode","RUN");r.put("profile",LimmaWorkflowContracts.PROFILE);r.put("operation","LM");r.put("out",h.resolve("analysis").toString());r.put("matrix",h.resolve("matrix.tsv").toString());r.put("design",h.resolve("design.tsv").toString());r.put("units","continuous-values");r.put("rscript",h.resolve("missing-Rscript").toString());r.put("r_libraries",List.of(h.resolve("libraries").toString()));r.put("moderation",Map.of("method","EBAYES"));r.put("contrasts",List.of(Map.of("id","difference","values",List.of(0,1))));r.put("adjustment_scope","per-contrast");return r;
    }
    private static void positiveToZero(Path file)throws IOException {var rows=Files.readAllLines(file);int selected=-1;double minimum=1;for(int i=1;i<rows.size();i++){double v=Double.parseDouble(rows.get(i).split("\t",-1)[1]);if(v>0&&v<minimum){selected=i;minimum=v;}}if(selected<0)throw new AssertionError("Missing original positive probability control");change(file,selected,1,"0");}
    private static void change(Path file,int row,int column,String value)throws IOException {var lines=Files.readAllLines(file);String[] fields=lines.get(row).split("\t",-1);fields[column]=value;lines.set(row,String.join("\t",fields));Files.write(file,lines,StandardCharsets.UTF_8);}
    private static Path home(String label)throws IOException {Path base=Path.of(System.getProperty("jlinalg.limma.testOutput","build/limma-workflow-test-output")).toAbsolutePath();Files.createDirectories(base);return Files.createTempDirectory(base,label+"-");}
    private static Path restore(String name,Path root)throws IOException {
        if(!Files.exists(root))Files.createDirectory(root);var files=object(object(CASES.get(name),"case").get("files"),"files");
        for(var e:files.entrySet()){Path relative=Path.of(e.getKey());if(relative.isAbsolute()||!relative.normalize().equals(relative)||e.getKey().contains("\\")||e.getKey().contains(".."))throw new IOException("Unsafe fixture path");var pin=object(e.getValue(),"pin");byte[] raw=BLOBS.get(text(pin,"sha256"));assertNotNull(raw);assertEquals(((Number)pin.get("bytes")).longValue(),raw.length);Path target=root.resolve(relative);Files.createDirectories(target.getParent());Files.write(target,raw,StandardOpenOption.CREATE_NEW);}return root;
    }
    private static Map<String,Object> fixtures() {
        try(InputStream source=LimmaWorkflowTest.class.getResourceAsStream("limma-workflow-originals.zip")){assertNotNull(source);try(ZipInputStream zip=new ZipInputStream(source)){Map<String,Object> metadata=null;long bytes=0;var names=new HashSet<String>();for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){assertTrue(names.add(entry.getName()));byte[] raw=zip.readNBytes(32*1024*1024+1);bytes+=raw.length;assertTrue(raw.length<=32*1024*1024&&bytes<=256*1024*1024);if(entry.getName().equals("FIXTURE.json"))metadata=object(SimpleJson.parse(new String(raw,StandardCharsets.UTF_8)),"fixture");else {assertTrue(entry.getName().startsWith("blobs/"));String sha=entry.getName().substring(6);assertEquals(sha,LimmaWorkflowRuntime.sha(raw));assertNull(BLOBS.put(sha,raw));}}assertNotNull(metadata);assertEquals(45,((Number)metadata.get("case_count")).intValue());return object(metadata.get("cases"),"cases");}}
        catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
}
