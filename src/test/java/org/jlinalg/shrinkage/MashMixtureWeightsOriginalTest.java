/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.shrinkage;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class MashMixtureWeightsOriginalTest {
    private static Map<String,byte[]> evidence()throws IOException,NoSuchAlgorithmException{
        try(var input=MashMixtureWeightsOriginalTest.class.getResourceAsStream("mashr-original/weight-controls.zip")){
            assertNotNull(input);byte[] raw=input.readAllBytes();assertEquals(1160724,raw.length);
            assertEquals("f2933ba1aebcab255e6b55560bc0f87d1e7f43533bd050e292f8f4de4777468a",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
            var files=new HashMap<String,byte[]>();long total=0;try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))){
                for(var item=zip.getNextEntry();item!=null;item=zip.getNextEntry()){
                    assertFalse(item.isDirectory());assertFalse(item.getName().contains(".."));byte[] value=zip.readNBytes(4_194_305);assertTrue(value.length<=4_194_304);total+=value.length;assertTrue(total<=16_777_216);assertNull(files.put(item.getName(),value));assertTrue(files.size()<=2000);
                }
            }return files;
        }
    }
    private static String text(Map<String,byte[]> files,String path){assertTrue(files.containsKey(path),path);return new String(files.get(path),StandardCharsets.UTF_8).strip();}
    private static double[][] matrix(Map<String,byte[]> files,String path){return text(files,path).lines().map(s->Stream.of(s.split("\t")).mapToDouble(Double::parseDouble).toArray()).toArray(double[][]::new);}
    private static double objective(double[][] scores,double[] prior,double[] proportions){
        double sum=0;for(double[] row:scores){double max=java.util.Arrays.stream(row).max().orElseThrow(),likelihood=0;for(int i=0;i<row.length;i++)likelihood+=proportions[i]*Math.exp(row[i]-max);sum+=Math.log(likelihood);}
        for(int i=0;i<prior.length;i++)if(prior[i]>1)sum+=(prior[i]-1)*Math.log(proportions[i]);return sum;
    }
    @TestFactory Stream<DynamicTest> originalWeightControls()throws IOException,NoSuchAlgorithmException{
        var files=evidence();var cases=text(files,"cases.tsv").lines().skip(1).toList();assertEquals(26,cases.size());var tests=new ArrayList<DynamicTest>();
        for(String line:cases){String name=line.split("\t")[0];tests.add(DynamicTest.dynamicTest(name,()->{
            double[][] scores=matrix(files,name+"/component-loglik.tsv");double[] prior=matrix(files,name+"/prior.tsv")[0],initial=matrix(files,name+"/initial.tsv")[0];
            var fit=MashMixtureWeights.fit(scores,prior,initial,new MashMixtureWeights.Options(200000,1e-9,false),MashMixtureWeights.Limits.defaults(),()->{}).requireConverged();
            assertEquals(objective(scores,prior,fit.proportions()),fit.relativeObjective(),1e-10);
            for(String method:List.of("mixEM","mixSQP")){
                String prefix=name+"/"+method+"/";assertEquals("COMPLETED",text(files,prefix+"status.txt"));assertTrue(text(files,prefix+"warnings.txt").isEmpty());
                double[] source=matrix(files,prefix+"objective-gap-lambda.tsv")[0],sourcePi=matrix(files,prefix+"proportions.tsv")[0];assertTrue(source[1]<=1e-8,"Original optimizer lacks the expected independent dual-gap certificate");
                assertEquals(objective(scores,prior,sourcePi),source[0],1e-10);
                // Both methods optimize the same concave objective. Equivalent
                // optima need not have identical nonidentified component weights.
                assertEquals(source[0],fit.relativeObjective(),source[2]*(source[1]+fit.relativeDualGap())+1e-8);
            }
            System.out.println(name+"\titerations="+fit.iterations()+"\tdual_gap="+fit.relativeDualGap()+"\tobjective="+fit.relativeObjective());
        }));}
        return tests.stream();
    }
}
