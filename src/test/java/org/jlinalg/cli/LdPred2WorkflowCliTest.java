/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LdPred2WorkflowCliTest {
    @TempDir Path temp;
    private Map<String,String> reference() throws Exception {
        Map<String,String> files=new LinkedHashMap<>();
        try(var input=getClass().getResourceAsStream("/org/jlinalg/genetics/ldpred2-whole-grid-r433.zip")) {
            assertNotNull(input);try(var zip=new ZipInputStream(input)){for(var e=zip.getNextEntry();e!=null;e=zip.getNextEntry())files.put(e.getName(),new String(zip.readAllBytes(),StandardCharsets.UTF_8));}
        }
        return files;
    }
    private Path fixture(boolean zero,boolean inf) throws Exception {
        Map<String,String> ref=reference();Path folder=temp.resolve("data-"+UUID());Files.createDirectory(folder);
        double[][] stats=ref.get("summary.tsv").lines().map(x->Arrays.stream(x.split("\t")).mapToDouble(Double::parseDouble).toArray()).toArray(double[][]::new);
        double[][] ld=ref.get("ld.tsv").lines().map(x->Arrays.stream(x.split("\t")).mapToDouble(Double::parseDouble).toArray()).toArray(double[][]::new);int m=stats.length;
        StringBuilder summary=new StringBuilder("variant\teffect_allele\tother_allele\tbeta\tbeta_se\tn_eff\n"),variants=new StringBuilder("variant\teffect_allele\tother_allele\n");
        for(int i=0;i<m;i++){String id=i==0?"NA":"v"+i;String label=id+"\tA\tG";variants.append(label).append('\n');summary.append(label).append('\t').append(zero?0:stats[i][0]).append('\t').append(stats[i][1]).append('\t').append(stats[i][2]).append('\n');}
        Files.writeString(folder.resolve("summary.tsv"),summary);Files.writeString(folder.resolve("variants.tsv"),variants);
        List<Integer> pointers=new ArrayList<>(),rows=new ArrayList<>();List<Double> values=new ArrayList<>();
        for(int col=0;col<m;col++){pointers.add(rows.size());for(int row=0;row<m;row++)if(ld[row][col]!=0){rows.add(row);values.add(ld[row][col]);}}pointers.add(rows.size());
        ByteBuffer p=ByteBuffer.allocate(pointers.size()*4).order(ByteOrder.LITTLE_ENDIAN);pointers.forEach(p::putInt);Files.write(folder.resolve("p.bin"),p.array());
        ByteBuffer r=ByteBuffer.allocate(rows.size()*4).order(ByteOrder.LITTLE_ENDIAN);rows.forEach(r::putInt);Files.write(folder.resolve("i.bin"),r.array());
        ByteBuffer x=ByteBuffer.allocate(values.size()*8).order(ByteOrder.LITTLE_ENDIAN);values.forEach(x::putDouble);Files.write(folder.resolve("x.bin"),x.array());
        StringBuilder models=new StringBuilder("model\th2\tp\tsparse\n");int k=0;
        if(zero){models.append("dense\t0.3\t1\tfalse\n");models.append("sparse\t0.3\t0.5\ttrue\n");}
        else for(String line:ref.get("grid.tsv").lines().toList()){String[] cols=line.split("\t");models.append("m").append(++k).append('\t').append(cols[1]).append('\t').append(cols[0]).append('\t').append(cols[2].equals("1")?"true":"false").append('\n');}
        Files.writeString(folder.resolve("models.tsv"),models);
        String config="engine=native\nmethod="+(inf?"inf":"grid")+"\nsummary=summary.tsv\nld_variants=variants.tsv\nld_pointers=p.bin\nld_rows=i.bin\nld_values=x.bin\ngenome_build=synthetic\nld_population=synthetic\njava_heap_mb=256\n"+(inf?"heritability=0.3\n":"models=models.tsv\nseed=4096\nburn_in=10\niterations=20\n");
        Path request=folder.resolve("request.properties");Files.writeString(request,config);return request;
    }
    private static String UUID(){return java.util.UUID.randomUUID().toString();}
    private int run(String... args){return LdPred2WorkflowCli.run(args,new PrintStream(new ByteArrayOutputStream()),new PrintStream(new ByteArrayOutputStream()));}
    @Test void nativeWholeGridMatchesEveryOriginalPublicEffectModelOrderAndFinalRState() throws Exception {
        Path request=fixture(false,false),out=temp.resolve("grid");assertEquals(0,run("--request",request.toString(),"--out",out.toString()));
        Map<String,String> ref=reference();double[][] expected=ref.get("effects.tsv").lines().map(x->Arrays.stream(x.split("\t")).mapToDouble(Double::parseDouble).toArray()).toArray(double[][]::new);
        for(int model=0;model<6;model++){List<String> rows=Files.readAllLines(out.resolve("results/m"+(model+1)+"/effects.tsv"));for(int i=0;i<expected.length;i++)assertEquals(expected[i][model],Double.parseDouble(rows.get(i+1).split("\t")[3]),2e-13,"model="+model+" row="+i);assertTrue(rows.get(1).startsWith("NA\tA\tG\t"));}
        assertArrayEquals(ref.get("after.txt").lines().mapToInt(Integer::parseInt).toArray(),Arrays.stream(Files.readString(out.resolve("results/rng-after.txt")).strip().split(",")).mapToInt(Integer::parseInt).toArray());
        Path verify=temp.resolve("recomputed");assertEquals(0,run("--verify",out.toString(),"--out",verify.toString()));assertTrue(Files.exists(verify.resolve("COMPLETED.properties")));
    }
    @Test void capturedInputsRemainSufficientAfterOriginalInputRemoval() throws Exception {
        Path request=fixture(false,true),out=temp.resolve("inf");assertEquals(0,run("--request",request.toString(),"--out",out.toString()));
        Files.move(request.getParent().resolve("summary.tsv"),request.getParent().resolve("moved-summary.tsv"));
        assertEquals(0,run("--verify",out.toString(),"--out",temp.resolve("verified-inf").toString()));
    }
    @Test void originalDivergenceRejectsOnlyThatCandidateAndCannotCreateWeightsForIt() throws Exception {
        Path request=fixture(true,false),out=temp.resolve("mixed");assertEquals(0,run("--request",request.toString(),"--out",out.toString()));
        assertFalse(Files.exists(out.resolve("results/dense/effects.tsv")));assertTrue(Files.exists(out.resolve("results/dense/REFUSAL.txt")));assertTrue(Files.exists(out.resolve("results/sparse/effects.tsv")));
        assertTrue(Files.readString(out.resolve("OUTCOME.txt")).contains("Rejected models: 1"));
    }
    @Test void allRejectedModelsAdmitNoCompleteCollection() throws Exception {
        Path request=fixture(true,false);Files.writeString(request.getParent().resolve("models.tsv"),"model\th2\tp\tsparse\ndense\t0.3\t1\tfalse\n");Path out=temp.resolve("none");
        assertEquals(2,run("--request",request.toString(),"--out",out.toString()));assertFalse(Files.exists(out.resolve("COMPLETED.properties")));assertTrue(Files.exists(out.resolve("results/dense/REFUSAL.txt")));
    }
    @Test void alleleMismatchAndExistingOutputsAreRefusedWithoutRepair() throws Exception {
        Path request=fixture(false,true),labels=request.getParent().resolve("variants.tsv");Files.writeString(labels,Files.readString(labels).replace("NA\tA\tG","NA\tG\tA"));Path out=temp.resolve("mismatch");
        assertEquals(2,run("--request",request.toString(),"--out",out.toString()));assertFalse(Files.exists(out.resolve("COMPLETED.properties")));
        Path existing=temp.resolve("existing");Files.createDirectory(existing);Files.writeString(existing.resolve("sentinel"),"unchanged");assertEquals(2,run("--request",request.toString(),"--out",existing.toString()));try(var children=Files.list(existing)){assertEquals(List.of("sentinel"),children.map(p->p.getFileName().toString()).toList());}
    }
    @Test void wallLimitKillsOwnedComputationAndLeavesNoAcceptedReceipt() throws Exception {
        Path request=fixture(false,false);Files.writeString(request,Files.readString(request)+"wall_ms=1\n");Path out=temp.resolve("timed-out");assertEquals(2,run("--request",request.toString(),"--out",out.toString()));assertFalse(Files.exists(out.resolve("COMPLETED.properties")));
    }
    @Test void lateSuccessRecordingFailureInvalidatesTheCompletionMarker() throws Exception {
        Path request=fixture(false,true),out=temp.resolve("late-recording-failure");
        PrintStream broken=new PrintStream(new OutputStream(){@Override public void write(int b)throws IOException{throw new IOException("controlled output stream failure");}});
        assertEquals(2,LdPred2WorkflowCli.run(new String[]{"--request",request.toString(),"--out",out.toString()},broken,new PrintStream(new ByteArrayOutputStream())));assertFalse(Files.exists(out.resolve("COMPLETED.properties")));assertTrue(Files.exists(out.resolve("FAILURE.txt")));
    }
}
