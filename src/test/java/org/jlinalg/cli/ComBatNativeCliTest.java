/* Copyright (C) 2026 OmicsMatic contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;

class ComBatNativeCliTest {
    @TempDir(cleanup=CleanupMode.NEVER) Path temporary;
    private Path fixture(String name)throws Exception {
        Path target=Files.createDirectory(temporary.resolve(name));
        try(var input=new ZipInputStream(Objects.requireNonNull(getClass().getResourceAsStream("/org/jlinalg/confounding/sva346-original-reference.zip")))) {
            for(var entry=input.getNextEntry();entry!=null;entry=input.getNextEntry())if(entry.getName().startsWith(name+"/"))Files.write(target.resolve(entry.getName().substring(name.length()+1)),input.readAllBytes(),StandardOpenOption.CREATE_NEW);
        }
        return target;
    }
    private Map<String,Object> request(Path fixture,String out) {
        var p=new LinkedHashMap<String,Object>();p.put("schema_version",1);p.put("source_version","3.46.0");p.put("operation","ComBat");p.put("units","continuous-values");p.put("matrix",fixture.resolve("matrix.tsv").toString());p.put("batch",fixture.resolve("batch.tsv").toString());p.put("design",Files.exists(fixture.resolve("design.tsv"))?fixture.resolve("design.tsv").toString():null);p.put("par_prior",true);p.put("mean_only",false);p.put("reference_batch",null);p.put("max_iterations",10000);p.put("max_input_bytes",5000000);p.put("max_entries",1000000);p.put("max_work",1000000000L);p.put("max_seconds",60);p.put("execution_policy","ALLOW");p.put("population","complete own synthetic population");p.put("preparation","no alignment, filtering or unit conversion");p.put("out",temporary.resolve(out).toString());return p;
    }
    private int execute(Map<String,Object> request,String label)throws Exception {
        Path input=temporary.resolve(label+".json");Files.writeString(input,NextflowJson.json(request),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);return run("--request",input.toString());
    }
    private int run(String...args)throws Exception {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=JLinAlgCli.run(concat(args),new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));assertTrue(err.toString(StandardCharsets.UTF_8).contains("WARNINGS")||Arrays.asList(args).contains("--help"));return code;
    }
    private static String[] concat(String[] args){String[] r=new String[args.length+1];r[0]="combat-native";System.arraycopy(args,0,r,1,args.length);return r;}
    private Map<?,?> status(String name)throws Exception{return NextflowFiles.object(NextflowFiles.json(temporary.resolve(name+"/analysis-status.json"),1048576));}
    @Test void globalRunAndCompleteFreshVerificationPreserveTheOriginalBundle()throws Exception {
        var r=request(fixture("PARAMETRIC"),"analysis");assertEquals(0,execute(r,"run"));assertEquals(Boolean.TRUE,status("analysis").get("result_admitted"));Path saved=temporary.resolve("analysis");var before=NextflowFiles.tree(saved,128,10000000,10000000,()->{});assertEquals(0,run("--verify",saved.toString(),"--out",temporary.resolve("verified").toString()));assertEquals(before,NextflowFiles.tree(saved,128,10000000,10000000,()->{}));
        assertEquals(2,run("--request",temporary.resolve("run.json").toString()));assertEquals(0,run("--help"));
    }
    @Test void skipsMissingForbiddenAndDifferentModelPrerequisites()throws Exception {
        var input=fixture("PARAMETRIC");var missing=request(input,"missing");missing.put("matrix",temporary.resolve("unavailable.tsv").toString());assertEquals(3,execute(missing,"missing"));assertEquals(Boolean.FALSE,status("missing").get("result_admitted"));var forbidden=request(input,"forbidden");forbidden.put("execution_policy","FORBID");assertEquals(3,execute(forbidden,"forbidden"));var other=request(input,"other");other.put("operation","ComBat_seq");assertEquals(3,execute(other,"other"));
    }
    @Test void originalUnderflowIsRetainedAndRefusedWithoutAFiniteFallback()throws Exception {
        var r=request(fixture("NONPARAMETRIC_UNDERFLOW"),"undefined");r.put("par_prior",false);assertEquals(2,execute(r,"underflow"));assertEquals(Boolean.FALSE,status("undefined").get("result_admitted"));assertTrue(Files.readString(temporary.resolve("undefined/adjusted.tsv")).contains("NaN"));var report=NextflowFiles.object(NextflowFiles.json(temporary.resolve("undefined/results.json"),1048576));assertEquals(28800,((Number)report.get("undefined_observed_values")).intValue());assertFalse(Files.exists(temporary.resolve("undefined/manifest.json")));
        r=request(fixture("MISSING_VARIANCE_NONPARAMETRIC"),"missing-variance");r.put("par_prior",false);assertEquals(2,execute(r,"missing-variance"));assertEquals(Boolean.FALSE,status("missing-variance").get("result_admitted"));report=NextflowFiles.object(NextflowFiles.json(temporary.resolve("missing-variance/results.json"),1048576));assertEquals(1510,((Number)report.get("undefined_observed_values")).intValue());
    }
    @Test void sampleMismatchAndEntryCapacityRefuseTheWholeFit()throws Exception {
        var input=fixture("PARAMETRIC");var limited=request(input,"limited");limited.put("max_entries",1);assertEquals(2,execute(limited,"limited"));assertEquals(Boolean.FALSE,status("limited").get("result_admitted"));Path batch=input.resolve("batch.tsv");String text=Files.readString(batch);Files.writeString(batch,text.replaceFirst("s1\\t","other\t"));assertEquals(2,execute(request(input,"mismatch"),"mismatch"));assertEquals(Boolean.FALSE,status("mismatch").get("result_admitted"));
    }
    @Test void changedSavedResultsCannotBeVerified()throws Exception {
        var r=request(fixture("PARAMETRIC"),"analysis");assertEquals(0,execute(r,"run"));Path changed=temporary.resolve("changed");Files.createDirectory(changed);try(var paths=Files.walk(temporary.resolve("analysis"))){for(Path p:paths.toList()){if(p.equals(temporary.resolve("analysis")))continue;Path q=changed.resolve(temporary.resolve("analysis").relativize(p));if(Files.isDirectory(p))Files.createDirectory(q);else Files.copy(p,q);}}Files.writeString(changed.resolve("adjusted.tsv"),"tampered\n");assertEquals(2,run("--verify",changed.toString(),"--out",temporary.resolve("refused").toString()));assertEquals(Boolean.FALSE,status("refused").get("result_admitted"));
    }
    @Test void literalIdentifiersAndOriginalMissingMaskArePreserved()throws Exception {
        Path input=fixture("MISSING");for(String name:List.of("matrix.tsv","batch.tsv","design.tsv")){Path p=input.resolve(name);String text=Files.readString(p).replace("s1\t","NA\t");if(name.equals("matrix.tsv"))text=text.replace("g1\t","  literal-β  \t");Files.writeString(p,text);}assertEquals(0,execute(request(input,"literal"),"literal"));String adjusted=Files.readString(temporary.resolve("literal/adjusted.tsv"));assertTrue(adjusted.startsWith("feature_id\tNA\t"));assertTrue(adjusted.contains("\n  literal-β  \t"));assertEquals(2,adjusted.lines().skip(1).map(line->line.split("\t",-1)).flatMap(row->Arrays.stream(row).skip(1)).filter("NA"::equals).count());
    }
}
