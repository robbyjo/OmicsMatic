/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;

final class EdgeRWorkflowTest {
    private record Run(int code,String console) { }
    private static Path home(String name)throws IOException {
        Path base=Path.of(System.getProperty("omicsmatic.edger.models.test_root","build/edger-model-tests")).toAbsolutePath();Files.createDirectories(base);return Files.createTempDirectory(base,name+"-");
    }
    private static Run call(String...args) {
        var bytes=new ByteArrayOutputStream();try(var output=new PrintStream(bytes,true,StandardCharsets.UTF_8)){int code=EdgeRWorkflowCli.run(args,output,output);return new Run(code,bytes.toString(StandardCharsets.UTF_8));}
    }
    private static Run request(Path h,Map<String,Object> r)throws IOException {Path file=h.resolve("request-"+UUID.randomUUID()+".json");json(file,r);return call("--request",file.toString());}
    private static Map<String,Object> request(Path h)throws IOException {
        Files.writeString(h.resolve("counts.tsv"),"feature_id\t a \ta\ts3\ts4\nNA\t2\t3\t8\t9\n001\t5\t7\t9\t11\n空\t1\t9\t4\t7\n",StandardCharsets.UTF_8);
        Files.writeString(h.resolve("groups.tsv"),"sample_id\tgroup\n a \tA\na\tA\ns3\tB\ns4\tB\n",StandardCharsets.UTF_8);
        Files.writeString(h.resolve("design.tsv"),"sample_id\tIntercept\ttreatment effect\n a \t1\t0\na\t1\t0\ns3\t1\t1\ns4\t1\t1\n",StandardCharsets.UTF_8);
        var r=new LinkedHashMap<String,Object>();r.put("schema_version",1);r.put("mode","RUN");r.put("profile","edgeR-3.42.4-R4.6.1");r.put("model","GLM_LRT");
        r.put("counts","counts.tsv");r.put("groups","groups.tsv");r.put("design","design.tsv");r.put("group_levels",List.of("A","B"));r.put("count_units","raw-counts");
        r.put("normalization",Map.of("method","TMM"));r.put("filter",Map.of("method","none"));r.put("dispersion",Map.of("method","provided","value",.1));
        r.put("results",List.of(Map.of("id","B versus A","coefficients",List.of(2))));r.put("rscript",h.resolve("missing-Rscript").toString());r.put("r_libraries",List.of(h.toString()));r.put("out",h.resolve("analysis").toString());return r;
    }
    private record Invalid(String name,Consumer<Map<String,Object>> mutation) { }
    @TestFactory Stream<DynamicTest> closedScientificRequestsRefuseUnsupportedOrIgnoredSettings() {
        List<Invalid> invalid=List.of(
            new Invalid("R code",r->r.put("r_code","stop('code is not data')")),
            new Invalid("transformed counts",r->r.put("count_units","CPM")),
            new Invalid("unused normalization option",r->r.put("normalization",Map.of("method","RLE","p",.5))),
            new Invalid("provided factors missing",r->r.put("normalization",Map.of("method","provided"))),
            new Invalid("legacy edition cannot use adjusted QL",r->{r.put("model","GLM_QLF");r.put("fit",Map.of("ql_mode","adjusted"));}),
            new Invalid("QL option unused by LRT",r->r.put("fit",Map.of("ql_mode","legacy"))),
            new Invalid("QL-only auto dispersion",r->r.put("dispersion",Map.of("method","adjusted-QL-auto"))),
            new Invalid("negative dispersion",r->r.put("dispersion",Map.of("method","provided","value",-.1))),
            new Invalid("missing supplied dispersion",r->r.put("dispersion",Map.of("method","provided"))),
            new Invalid("zero contrast",r->r.put("results",List.of(Map.of("id","x","contrast",List.of(0,0))))),
            new Invalid("repeated coefficients",r->r.put("results",List.of(Map.of("id","x","coefficients",List.of(2,2))))),
            new Invalid("null contrast term",r->r.put("results",List.of(Map.of("id","x","contrast",Arrays.asList(null,1))))),
            new Invalid("null coefficient",r->r.put("results",List.of(Map.of("id","x","coefficients",Arrays.asList((Object)null))))),
            new Invalid("both selectors",r->r.put("results",List.of(Map.of("id","x","coefficients",List.of(2),"contrast",List.of(-1,1))))),
            new Invalid("repeated result identity",r->r.put("results",List.of(Map.of("id","x","coefficients",List.of(1)),Map.of("id","x","coefficients",List.of(2))))),
            new Invalid("unused exact design",r->{r.put("model","EXACT");r.put("results",List.of(Map.of("id","x","pair",List.of("A","B"))));}),
            new Invalid("renormalization before normalization",r->r.put("filter",Map.of("method","filterByExpr","basis","group","order","before-normalization","renormalize_after_filter",true))),
            new Invalid("null winsor term",r->r.put("dispersion",Map.of("method","estimateDisp","winsor_tail_p",Arrays.asList(.1,null)))),
            new Invalid("invalid winsor probabilities",r->r.put("dispersion",Map.of("method","estimateDisp","winsor_tail_p",List.of(.5,.5)))),
            new Invalid("unused no-filter option",r->r.put("filter",Map.of("method","none","min_count",1))),
            new Invalid("missing runtime libraries",r->r.put("r_libraries",List.of())),
            new Invalid("empty result population",r->r.put("results",List.of()))
        );
        return invalid.stream().map(c->DynamicTest.dynamicTest(c.name(),()->{Path h=home("invalid");var r=request(h);c.mutation().accept(r);assertThrows(IOException.class,()->EdgeRWorkflowContracts.validate(r));assertFalse(Files.exists(h.resolve("analysis")));}));
    }
    @Test void literalFloat64TransportPreservesIdentitiesAndNumericDesign()throws IOException {
        Path h=home("literal");var r=EdgeRWorkflowContracts.validate(request(h));Files.createDirectory(h.resolve("worker"));var p=EdgeRWorkflowFiles.prepare(h,r,false);
        assertEquals(List.of("NA","001","空"),p.features());assertEquals(List.of(" a ","a","s3","s4"),p.samples());assertEquals(List.of("Intercept","treatment effect"),p.coefficients());
        assertTrue(Files.readString(h.resolve("worker/counts.tsv")).startsWith("feature_index\ts1\ts2\ts3\ts4\nf1\t2.0"));
        assertEquals(p,EdgeRWorkflowFiles.prepare(h,r,true));
    }
    @TestFactory Stream<DynamicTest> malformedCountsGroupsDesignAndCapacitiesDoNotReachR() {
        List<String> controls=List.of("fraction rounds to integer","duplicate feature","duplicate sample","permuted group","permuted design","wrong contrast length","coefficient outside design","entry cap","work cap","negative counts","nonfinite counts","bad UTF8","unused group");
        return controls.stream().map(name->DynamicTest.dynamicTest(name,()->{
            Path h=home("input");var r=request(h);
            switch(name) {
                case "fraction rounds to integer"->Files.writeString(h.resolve("counts.tsv"),Files.readString(h.resolve("counts.tsv")).replace("\t2\t","\t2.0000000000000000001\t"));
                case "duplicate feature"->Files.writeString(h.resolve("counts.tsv"),Files.readString(h.resolve("counts.tsv")).replace("\n001\t","\nNA\t"));
                case "duplicate sample"->Files.writeString(h.resolve("counts.tsv"),Files.readString(h.resolve("counts.tsv")).replace("\ts3\t","\ta\t"));
                case "permuted group"->Files.writeString(h.resolve("groups.tsv"),Files.readString(h.resolve("groups.tsv")).replace("\ns3\t","\nother\t"));
                case "permuted design"->Files.writeString(h.resolve("design.tsv"),Files.readString(h.resolve("design.tsv")).replace("\ns3\t","\nother\t"));
                case "wrong contrast length"->r.put("results",List.of(Map.of("id","x","contrast",List.of(1))));
                case "coefficient outside design"->r.put("results",List.of(Map.of("id","x","coefficients",List.of(3))));
                case "entry cap"->r.put("maximum_entries",1);
                case "work cap"->r.put("maximum_work",1);
                case "negative counts"->Files.writeString(h.resolve("counts.tsv"),Files.readString(h.resolve("counts.tsv")).replace("\t2\t","\t-2\t"));
                case "nonfinite counts"->Files.writeString(h.resolve("counts.tsv"),Files.readString(h.resolve("counts.tsv")).replace("\t2\t","\tNaN\t"));
                case "bad UTF8"->Files.write(h.resolve("counts.tsv"),new byte[]{(byte)0xc3,0x28});
                case "unused group"->r.put("group_levels",List.of("A","B","C"));
                default->throw new AssertionError(name);
            }
            Run result=request(h,r);assertEquals(2,result.code(),result.console());assertTrue(result.console().contains("FAIL2"));
            if(Files.exists(h.resolve("analysis/manifest.json")))assertFalse(read(h.resolve("analysis/manifest.json")).containsKey("owned_worker_pid"));
        }));
    }
    @Test void missingRuntimeAndHistoricalUnsupportedProfileAreExplicitSkips()throws IOException {
        Path h=home("skip");var r=request(h);Run result=request(h,r);assertEquals(3,result.code(),result.console());assertTrue(Files.readString(h.resolve("analysis/WARNINGS.md")).contains("SKIP3"));assertEquals(Boolean.FALSE,read(h.resolve("analysis/analysis-status.json")).get("results_admitted"));
        r.put("profile","edgeR-3.32.1-R4.6.1");r.put("out",h.resolve("unsupported").toString());result=request(h,r);assertEquals(3,result.code());assertTrue(result.console().contains("3.32.1"));assertFalse(Files.exists(h.resolve("unsupported")));
    }
    @Test void existingOutputAndDuplicateJsonKeysAreRefused()throws IOException {
        Path h=home("preserve");var r=request(h);Files.createDirectory(h.resolve("analysis"));Files.writeString(h.resolve("analysis/original.txt"),"preserve");assertEquals(2,request(h,r).code());assertEquals("preserve",Files.readString(h.resolve("analysis/original.txt")));
        Path p=h.resolve("duplicate.json");Files.writeString(p,"{\"mode\":\"RUN\",\"mode\":\"RUN\"}");assertEquals(2,call("--request",p.toString()).code());
    }
    private static final List<String> FIXTURES=List.of("3.42.4-EXACT-provided-factors","3.42.4-LRT-filter-after-design","4.10.7-QLF-adjusted-auto","3.40.2-GLM_LRT");
    private static Path fixture(String selected)throws IOException {
        Path h=home("original-capture"),root=h.resolve("capture");Files.createDirectory(root);
        try(var source=EdgeRWorkflowTest.class.getResourceAsStream("/org/jlinalg/differential/edger-workflow/captures-20261120.zip")) {
            if(source==null)throw new IOException("Missing qualified original captures");try(var zip=new ZipInputStream(source)) {
                for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) {
                    String prefix=selected+"/";if(!entry.getName().startsWith(prefix)||entry.isDirectory())continue;
                    Path relative=Path.of(entry.getName().substring(prefix.length()));if(relative.isAbsolute()||!relative.normalize().equals(relative))throw new IOException("Unsafe fixture path");
                    Path path=root.resolve(relative);Files.createDirectories(path.getParent());Files.copy(zip,path);
                }
            }
        }return root;
    }
    @TestFactory Stream<DynamicTest> qualifiedOriginalModelCapturesVerifyWithoutR() {
        return FIXTURES.stream().map(name->DynamicTest.dynamicTest(name,()->{Path root=fixture(name);Run result=call("--verify",root.toString());assertEquals(0,result.code(),result.console());assertTrue(Files.readString(root.resolve("results/filter.tsv")).contains("\nNA\t"));}));
    }
    @TestFactory Stream<DynamicTest> resealedCorruptionDoesNotBecomeACompleteResult() {
        return Stream.of("root extra","worker extra","source changed","published identity changed","CPM changed both copies","FDR changed both copies","logFC changed both copies","failed flag","iteration limit","comparison metadata","captured request changed","filter mask changed").map(name->DynamicTest.dynamicTest(name,()->{
            Path root=fixture("3.42.4-LRT-filter-after-design");assertEquals(0,call("--verify",root.toString()).code());
            switch(name) {
                case "root extra"->Files.writeString(root.resolve("unreferenced.txt"),"extra");
                case "worker extra"->Files.writeString(root.resolve("worker/unreferenced.txt"),"extra");
                case "source changed"->Files.writeString(root.resolve("source/edger_workflow.R"),"stop('changed original capture source')\n");
                case "published identity changed"->Files.writeString(root.resolve("results/filter.tsv"),Files.readString(root.resolve("results/filter.tsv")).replace("\nNA\t","\nrepaired-identity\t"));
                case "CPM changed both copies"->{change(root.resolve("worker/cpm.tsv"),1,"123.456");change(root.resolve("results/cpm.tsv"),1,"123.456");}
                case "FDR changed both copies"->{var fields=Files.readAllLines(root.resolve("worker/result-1.tsv")).get(0).split("\t");change(root.resolve("worker/result-1.tsv"),fields.length-1,"0.313");change(root.resolve("results/result-1.tsv"),fields.length-1,"0.313");}
                case "logFC changed both copies"->{change(root.resolve("worker/result-1.tsv"),1,"123.456");change(root.resolve("results/result-1.tsv"),1,"123.456");}
                case "failed flag"->{change(root.resolve("worker/fit-diagnostics.tsv"),4,"TRUE");change(root.resolve("results/fit-diagnostics.tsv"),4,"TRUE");}
                case "iteration limit"->{change(root.resolve("worker/fit-diagnostics.tsv"),3,"251");change(root.resolve("results/fit-diagnostics.tsv"),3,"251");}
                case "comparison metadata"->Files.writeString(root.resolve("worker/comparisons.json"),"[]\n");
                case "captured request changed"->{var changed=read(root.resolve("request.json"));changed.put("adjust_method","none");json(root.resolve("request.json"),changed);}
                case "filter mask changed"->{var lines=Files.readAllLines(root.resolve("worker/keep.tsv"));lines.set(1,"f1\tFALSE");Files.write(root.resolve("worker/keep.tsv"),lines,StandardCharsets.UTF_8);}
                default->throw new AssertionError(name);
            }
            EdgeRWorkflowCli.seal(root);assertEquals(2,call("--verify",root.toString()).code(),name);
        }));
    }
    private static void change(Path path,int column,String value)throws IOException {var lines=Files.readAllLines(path);String[] row=lines.get(1).split("\t",-1);row[column]=value;lines.set(1,String.join("\t",row));Files.write(path,lines,StandardCharsets.UTF_8);}
    @Test void nestedReplayIsRefusedBeforeParentChanges()throws IOException {
        Path root=fixture("4.10.7-QLF-adjusted-auto"),h=root.getParent();byte[] before=Files.readAllBytes(root.resolve("capture-files.json"));
        Run result=request(h,Map.of("schema_version",1,"mode","REPLAY","analysis",root.toString(),"out",root.resolve("nested/result").toString()));
        assertEquals(2,result.code());assertFalse(Files.exists(root.resolve("nested")));assertArrayEquals(before,Files.readAllBytes(root.resolve("capture-files.json")));assertEquals(0,call("--verify",root.toString()).code());
    }
}
