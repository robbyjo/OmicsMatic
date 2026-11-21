/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GctaGlobalDispatchTest {
    @TempDir Path temp;
    private record Invocation(int code,String out,String error) { }
    @Test void globalHelpAndSyntaxPreserveStandaloneContract() {
        var global=invoke("gcta-tools","--help");assertEquals(0,global.code);assertTrue(global.out.contains("MTCOJO"));
        var bytes=new ByteArrayOutputStream();assertEquals(0,GctaWorkflowCli.run(new String[]{"--help"},new PrintStream(bytes),System.err));
        assertEquals(bytes.toString(StandardCharsets.UTF_8),global.out);
        assertEquals(2,invoke("gcta-tools").code);assertEquals(3,invoke("gcta-tools","--request",temp.resolve("absent.json").toString()).code);
        assertTrue(invoke("--help").out.contains("gcta-tools --help"));
    }
    @Test void globalNativeRunVerifyAndReplayPreserveParentBytes() throws Exception {
        Path values=temp.resolve("matrix.bin"),ids=temp.resolve("matrix.id");var binary=ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);for(float value:new float[]{1,.25f,1})binary.putFloat(value);Files.write(values,binary.array());Files.writeString(ids,"F1\tI1\nF2\tI2\n");
        var request=new LinkedHashMap<String,Object>();request.put("schema_version",1);request.put("mode","RUN");request.put("profile","GCTA-1.94.1");request.put("engine","native");request.put("model","SPARSE_GRM");request.put("out",temp.resolve("parent").toString());request.put("preparation_note","Synthetic two-person prepared GRM for global dispatch qualification");request.put("grms",List.of(Map.of("values",values.toString(),"ids",ids.toString())));request.put("sparse_cutoff",.1);
        Path recipe=temp.resolve("run.json");Files.write(recipe,RsemJson.bytes(request));var run=invoke("gcta-tools","--request",recipe.toString());assertEquals(0,run.code,run.error);
        Path parent=temp.resolve("parent");String seal=FollowupSupport.hash(parent.resolve("capture-files.json"));assertEquals("0\t0\t1\n1\t0\t0.25\n1\t1\t1\n",Files.readString(parent.resolve("worker/result.grm.sp")));assertEquals(0,invoke("gcta-tools","--verify",parent.toString()).code);
        Path replay=temp.resolve("replay.json");Files.write(replay,RsemJson.bytes(Map.of("schema_version",1,"mode","REPLAY","analysis",parent.toString(),"out",temp.resolve("child").toString())));var result=invoke("gcta-tools","--request",replay.toString());assertEquals(0,result.code,result.error);assertEquals(seal,FollowupSupport.hash(parent.resolve("capture-files.json")));assertArrayEquals(Files.readAllBytes(parent.resolve("worker/result.grm.sp")),Files.readAllBytes(temp.resolve("child/worker/result.grm.sp")));
    }
    @Test void globalMissingOriginalRuntimeSkipsWithoutNativeFallback() throws Exception {
        Path bed=temp.resolve("reference.bed"),bim=temp.resolve("reference.bim"),fam=temp.resolve("reference.fam");Files.write(bed,new byte[]{0x6c,0x1b,1,11});Files.writeString(bim,"1\trsA\t0\t1\tA\tG\n");Files.writeString(fam,"F1 I1 0 0 0 -9\nF2 I2 0 0 0 -9\nF3 I3 0 0 0 -9\n");
        var request=new LinkedHashMap<String,Object>();request.put("schema_version",1);request.put("mode","RUN");request.put("profile","GCTA-1.94.1");request.put("engine","original");request.put("model","GRM");request.put("out",temp.resolve("skipped").toString());request.put("runtime",temp.resolve("exact-runtime-absent").toString());request.put("preparation_note","Synthetic prepared complete genotypes; deliberate missing runtime");request.put("genotypes",Map.of("bed",bed.toString(),"bim",bim.toString(),"fam",fam.toString()));
        Path recipe=temp.resolve("missing-runtime.json");Files.write(recipe,RsemJson.bytes(request));var result=invoke("gcta-tools","--request",recipe.toString());assertEquals(3,result.code,result.error);assertTrue(result.error.contains("SKIP3"));assertFalse(Files.exists(temp.resolve("skipped/worker/result.grm.bin")));for(String suffix:List.of("bed","bim","fam"))assertArrayEquals(Files.readAllBytes(temp.resolve("reference."+suffix)),Files.readAllBytes(temp.resolve("skipped/inputs/genotypes."+suffix)));
    }
    private static Invocation invoke(String... arguments) {
        var out=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();int code=JLinAlgCli.run(arguments,new PrintStream(out),new PrintStream(error));return new Invocation(code,out.toString(StandardCharsets.UTF_8),error.toString(StandardCharsets.UTF_8));
    }
}
