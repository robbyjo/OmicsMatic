/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GctaWorkflowCliTest {
    @TempDir Path temp;
    private record Calls(List<List<String>> samples,List<String> markers,int algorithm,byte[][] calls) { }
    private record Invocation(int code,String stdout,String stderr) { }

    @Test void nativeFileWorkflowsMatchAllCapturedSourceCasesAndRetainRefusals() throws Exception {
        var files=new LinkedHashMap<String,byte[]>();
        try(var stream=getClass().getResourceAsStream("/org/jlinalg/genetics/gcta1941-hardcall-originals.zip")) {
            assertNotNull(stream);try(var zip=new ZipInputStream(stream)){for(var e=zip.getNextEntry();e!=null;e=zip.getNextEntry())files.put(e.getName(),zip.readAllBytes());}
        }
        int successes=0,refusals=0,fields=0;
        for(String line:new String(files.get("CASES.tsv"),StandardCharsets.UTF_8).lines().skip(1).toList()) {
            String[] item=line.split("\t");String name=item[0];Calls calls=read(files.get(name+"/input.bin"));Path folder=temp.resolve(name);Files.createDirectory(folder);
            var request=base(folder.resolve("analysis"),"GRM","native");request.put("genotypes",bed(folder,calls));request.put("grm_algorithm",calls.algorithm());
            byte[] raw=Files.readAllBytes(folder.resolve("data.bed"));Invocation run=invoke(folder.resolve("request.json"),request);
            assertArrayEquals(raw,Files.readAllBytes(folder.resolve("data.bed")),name);
            Path analysis=folder.resolve("analysis");assertTrue(Files.isRegularFile(analysis.resolve("source-request.json")));assertTrue(Files.isRegularFile(analysis.resolve("WARNINGS.md")));
            if(item[1].equals("SUCCESS")) {
                assertEquals(0,run.code,run.stderr);successes++;
                for(String suffix:List.of("grm.bin","grm.N.bin")) {
                    byte[] actual=Files.readAllBytes(analysis.resolve("worker/result."+suffix)),expected=files.get(name+"/result."+suffix);
                    assertArrayEquals(expected,actual,name+" "+suffix);fields+=actual.length/4;
                }
                assertEquals(0,GctaWorkflowCli.run(new String[]{"--verify",analysis.toString()},System.out,System.err));
                assertTrue(Files.isRegularFile(analysis.resolve("worker/result.markers.tsv")));
                assertTrue(Files.isRegularFile(analysis.resolve("worker/result.grm.N.exact.tsv")));
            }else {
                assertEquals(2,run.code,run.stderr);assertTrue(run.stderr.contains("FAIL2"));assertFalse(Files.exists(analysis.resolve("model-objects.json")));
                assertFalse(Boolean.TRUE.equals(SeuratDifferentialContracts.read(analysis.resolve("analysis-status.json")).get("results_admitted")));refusals++;
            }
        }
        assertEquals(29,successes);assertEquals(6,refusals);assertEquals(190428,fields);
    }

    @Test void sparseFloatThresholdExcludesEqualValuesAndDoesNotForceDiagonalEntries() throws Exception {
        Path folder=temp.resolve("sparse");Files.createDirectory(folder);var request=sparse(folder,folder.resolve("analysis"));
        Invocation run=invoke(folder.resolve("request.json"),request);assertEquals(0,run.code,run.stderr);
        assertEquals("1\t1\t0.050000004\n2\t1\t2\n",Files.readString(folder.resolve("analysis/worker/result.grm.sp")));
        var model=SeuratDifferentialContracts.read(folder.resolve("analysis/model-objects.json"));
        assertEquals((double).05f,((Number)model.get("source_float32_cutoff")).doubleValue());
        assertEquals(2,((Number)model.get("sparse_entries")).intValue());
        request.put("out",folder.resolve("negative-cutoff").toString());request.put("sparse_cutoff",-.1);
        var negative=invoke(folder.resolve("negative.json"),request);assertEquals(0,negative.code,negative.stderr);
        assertEquals("0\t0\t0.049999997\n1\t0\t0.050000001\n1\t1\t0.050000004\n2\t1\t2\n2\t2\t-0\n",Files.readString(folder.resolve("negative-cutoff/worker/result.grm.sp")));
    }

    @Test void nativeReplayPreservesParentAndRejectsNestedOutputsAndResealedNumericTampering() throws Exception {
        Path folder=temp.resolve("replay");Files.createDirectory(folder);var request=sparse(folder,folder.resolve("parent"));
        assertEquals(0,invoke(folder.resolve("request.json"),request).code);Path parent=folder.resolve("parent");
        String seal=FollowupSupport.hash(parent.resolve("capture-files.json"));
        var replay=new LinkedHashMap<String,Object>();replay.put("schema_version",1);replay.put("mode","REPLAY");replay.put("analysis",parent.toString());replay.put("out",folder.resolve("child").toString());
        var actual=invoke(folder.resolve("replay.json"),replay);assertEquals(0,actual.code,actual.stderr);assertEquals(seal,FollowupSupport.hash(parent.resolve("capture-files.json")));
        replay.put("out",parent.resolve("nested").toString());assertEquals(2,invoke(folder.resolve("nested.json"),replay).code);assertFalse(Files.exists(parent.resolve("nested")));assertEquals(seal,FollowupSupport.hash(parent.resolve("capture-files.json")));
        Files.writeString(parent.resolve("worker/result.grm.sp"),"1\t1\t0.050000004\n2\t1\t1\n");GctaWorkflowCli.seal(parent);
        assertEquals(2,GctaWorkflowCli.run(new String[]{"--verify",parent.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(new ByteArrayOutputStream())));
    }

    @Test void unavailableOriginalRuntimeSkipsWithoutNativeFallbackAndRetainsEveryAvailableInput() throws Exception {
        Path folder=temp.resolve("skip");Files.createDirectory(folder);var request=base(folder.resolve("analysis"),"GRM","original");
        request.put("runtime",folder.resolve("required-gcta.exe").toString());request.put("genotypes",bed(folder,tiny()));
        var run=invoke(folder.resolve("request.json"),request);assertEquals(3,run.code,run.stderr);assertTrue(run.stderr.contains("SKIP3"));assertTrue(run.stderr.contains("required-gcta.exe"));
        for(String suffix:List.of("bed","bim","fam"))assertArrayEquals(Files.readAllBytes(folder.resolve("data."+suffix)),Files.readAllBytes(folder.resolve("analysis/inputs/genotypes."+suffix)));
        assertFalse(Files.exists(folder.resolve("analysis/worker/result.grm.bin")));assertFalse(Boolean.TRUE.equals(SeuratDifferentialContracts.read(folder.resolve("analysis/analysis-status.json")).get("results_admitted")));
    }

    @Test void unknownEditionIsSkippedAndUnsupportedBinaryCodingIsRefusedWithoutRecoding() throws Exception {
        Path folder=temp.resolve("edition");Files.createDirectory(folder);var request=base(folder.resolve("analysis"),"GRM","native");request.put("genotypes",bed(folder,tiny()));request.put("profile","GCTA-1.92.4");
        var run=invoke(folder.resolve("request.json"),request);assertEquals(3,run.code,run.stderr);assertTrue(run.stderr.contains("do not substitute"));
        var binary=base(folder.resolve("binary"),"FASTGWA_BINARY","original");binary.put("runtime","missing.exe");binary.put("binary_coding","1/2");
        binary.put("genotypes",request.get("genotypes"));binary.put("sparse",Map.of("values","missing.sp","ids","missing.id"));binary.put("phenotype","missing.pheno");
        var refused=invoke(folder.resolve("binary.json"),binary);assertEquals(2,refused.code);assertTrue(refused.stderr.contains("no automatic recoding"));assertFalse(Files.exists(folder.resolve("binary")));
    }

    @Test void literalUnicodeCompositeIdentitiesAndUnchangedMissingGenotypesSurviveFileIntake() throws Exception {
        Path folder=temp.resolve("literal");Files.createDirectory(folder);
        var calls=new Calls(List.of(List.of("\u2003家族","same"),List.of("F2","same"),List.of("F3","x")),List.of("v"),0,new byte[][]{{0,1,2}});
        var request=base(folder.resolve("analysis"),"GRM","native");request.put("genotypes",bed(folder,calls));var run=invoke(folder.resolve("request.json"),request);assertEquals(0,run.code,run.stderr);
        assertTrue(Files.readString(folder.resolve("analysis/worker/result.grm.id")).startsWith("\u2003家族\tsame\n"));
        String marker=Files.readAllLines(folder.resolve("analysis/worker/result.markers.tsv")).get(1);assertEquals("v\t3\t1.0\t0.5\tINCLUDED",marker);
    }

    @Test void missingInputsAndCapacityFailuresNeverAdmitPartialResults() throws Exception {
        Path folder=temp.resolve("capacity");Files.createDirectory(folder);var request=base(folder.resolve("analysis"),"GRM","native");var genotype=bed(folder,tiny());
        request.put("genotypes",genotype);request.put("maximum_native_cells",1);var capacity=invoke(folder.resolve("capacity.json"),request);assertEquals(2,capacity.code);assertTrue(capacity.stderr.contains("maximum_native_cells"));
        request.put("out",folder.resolve("missing").toString());request.remove("maximum_native_cells");var changed=new LinkedHashMap<String,Object>(genotype);changed.put("bed",folder.resolve("unavailable.bed").toString());request.put("genotypes",changed);
        var missing=invoke(folder.resolve("missing.json"),request);assertEquals(3,missing.code);assertTrue(Files.exists(folder.resolve("missing/inputs/genotypes.bim")));assertTrue(Files.exists(folder.resolve("missing/inputs/genotypes.fam")));assertFalse(Files.exists(folder.resolve("missing/worker/result.grm.bin")));
    }

    private static Map<String,Object> base(Path out,String model,String engine) {
        var r=new LinkedHashMap<String,Object>();r.put("schema_version",1);r.put("mode","RUN");r.put("profile","GCTA-1.94.1");r.put("engine",engine);r.put("model",model);r.put("out",out.toString());r.put("preparation_note","Synthetic explicit prepared inputs for source controls");return r;
    }
    private static Map<String,Object> sparse(Path folder,Path out) throws Exception {
        var r=base(out,"SPARSE_GRM","native");Path values=folder.resolve("matrix.grm.bin"),ids=folder.resolve("matrix.grm.id");
        var b=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);for(float value:new float[]{Math.nextDown(.05f),.05f,Math.nextUp(.05f),-.1f,2,-0.0f})b.putFloat(value);Files.write(values,b.array());Files.writeString(ids,"F1\tI1\nF2\tI2\nF3\tI3\n");
        r.put("grms",List.of(Map.of("values",values.toString(),"ids",ids.toString())));r.put("sparse_cutoff",.05);return r;
    }
    private static Invocation invoke(Path path,Map<String,Object> request) throws Exception {
        Files.write(path,RsemJson.bytes(request));var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=GctaWorkflowCli.run(new String[]{"--request",path.toString()},new PrintStream(out),new PrintStream(err));return new Invocation(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));
    }
    private static Calls tiny(){return new Calls(List.of(List.of("F1","I1"),List.of("F2","I2"),List.of("F3","I3")),List.of("v"),0,new byte[][]{{0,1,2}});}
    private static Map<String,Object> bed(Path folder,Calls data) throws Exception {
        int n=data.samples.size();var bytes=new ByteArrayOutputStream();bytes.write(new byte[]{0x6c,0x1b,1});int[] map={3,2,0,1};
        for(var row:data.calls)for(int start=0;start<n;start+=4){int packed=0;for(int i=start;i<Math.min(n,start+4);i++)packed|=map[row[i]]<<(2*(i-start));bytes.write(packed);}
        Path prefix=folder.resolve("data");Files.write(Path.of(prefix+".bed"),bytes.toByteArray());var fam=new StringBuilder();var bim=new StringBuilder();
        for(var id:data.samples)fam.append(id.get(0)).append('\t').append(id.get(1)).append("\t0\t0\t0\t-9\n");
        for(int i=0;i<data.markers.size();i++)bim.append("1\t").append(data.markers.get(i)).append("\t0\t").append(10000L*(i+1)).append("\tA\tG\n");
        Files.writeString(Path.of(prefix+".fam"),fam,StandardCharsets.UTF_8);Files.writeString(Path.of(prefix+".bim"),bim,StandardCharsets.UTF_8);
        return Map.of("bed",prefix+".bed","bim",prefix+".bim","fam",prefix+".fam");
    }
    private static Calls read(byte[] bytes) throws Exception {
        try(var in=new DataInputStream(new ByteArrayInputStream(bytes))) {
            assertEquals(0x47434731,in.readInt());int n=in.readInt(),m=in.readInt(),algorithm=in.readInt();var ids=new ArrayList<List<String>>();var variants=new ArrayList<String>();byte[][] calls=new byte[m][n];
            for(int i=0;i<n;i++)ids.add(List.of(string(in),string(in)));for(int v=0;v<m;v++){variants.add(string(in));in.readFully(calls[v]);}assertEquals(-1,in.read());return new Calls(ids,variants,algorithm,calls);
        }
    }
    private static String string(DataInputStream in) throws Exception{return new String(in.readNBytes(in.readInt()),StandardCharsets.UTF_8);}
}
