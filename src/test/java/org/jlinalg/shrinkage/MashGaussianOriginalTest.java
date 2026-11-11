/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.shrinkage;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
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

/** Complete original controls, with one explicitly recorded component-likelihood boundary. */
class MashGaussianOriginalTest {
    private static final Runnable CHECK=()->{};
    private static Map<String,byte[]> evidence() throws IOException {
        var files=new HashMap<String,byte[]>();String development=System.getenv("JLINALG_MASHR_GAUSSIAN_CONTROLS");
        if(development!=null&&!development.isBlank()){
            Path root=Path.of(development);try(var paths=Files.walk(root)){for(Path path:paths.filter(Files::isRegularFile).toList())files.put(root.relativize(path).toString().replace('\\','/'),Files.readAllBytes(path));}
        }else{
            try(var input=MashGaussianOriginalTest.class.getResourceAsStream("mashr-original/original-controls.zip")){
                assertNotNull(input,"Missing retained original controls");byte[] raw=input.readAllBytes();assertEquals(1860127,raw.length);
                try{assertEquals("5ec128b5c1ff4dafb4143bf41f5e815c02246d89dd7f3c31b32e0244f12969eb",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));}
                catch(NoSuchAlgorithmException error){throw new AssertionError(error);}
                long total=0;try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))){
                    for(var item=zip.getNextEntry();item!=null;item=zip.getNextEntry()){assertFalse(item.isDirectory());assertFalse(item.getName().contains(".."));byte[] value=zip.readNBytes(16_777_217);assertTrue(value.length<=16_777_216);total+=value.length;assertTrue(total<=67_108_864);assertNull(files.put(item.getName(),value));assertTrue(files.size()<=5000);}
                }
            }
        }
        return files;
    }
    private static String text(Map<String,byte[]> files,String name){assertTrue(files.containsKey(name),name);return new String(files.get(name),StandardCharsets.UTF_8).strip();}
    private static double[][] matrix(Map<String,byte[]> files,String name){return text(files,name).lines().map(s->Stream.of(s.split("\t")).mapToDouble(Double::parseDouble).toArray()).toArray(double[][]::new);}
    private static void near(List<String> failures,String label,double expected,double actual){
        double limit=1e-9+2e-8*Math.abs(expected);
        if(!Double.isFinite(expected)||!Double.isFinite(actual)||Math.abs(expected-actual)>limit)failures.add(label+" expected="+expected+" actual="+actual+" tolerance="+limit);
    }
    @TestFactory Stream<DynamicTest> originalControls() throws IOException {
        var files=evidence();var tests=new ArrayList<DynamicTest>();
        var cases=text(files,"cases.tsv").lines().skip(1).toList();assertEquals(43,cases.size());
        for(String line:cases){
            String[] spec=line.split("\t");String name=spec[0];int n=Integer.parseInt(spec[1]),rows=Integer.parseInt(spec[2]),k=Integer.parseInt(spec[3]);
            var priors=new ArrayList<double[][]>();for(int c=0;c<k;c++)priors.add(matrix(files,name+"/expanded-"+c+".tsv"));
            var options=new MashGaussian.Options(Double.parseDouble(spec[4]),Double.parseDouble(spec[5]),1e-12,Math.ulp(1.),1e-7,true);
            var model=MashGaussian.prepare(priors,matrix(files,name+"/proportions.tsv")[0],matrix(files,name+"/residual.tsv"),options,MashGaussian.Limits.defaults(),CHECK);
            double[][] bhat=matrix(files,name+"/bhat.tsv"),shat=matrix(files,name+"/shat.tsv");
            tests.add(DynamicTest.dynamicTest(name+" canonical source matrices",()->{
                var basis=MashGaussian.canonical(n);assertEquals(text(files,name+"/canonical-names.txt").lines().toList(),basis.names());
                var matrices=basis.covariances();for(int c=0;c<matrices.size();c++){double[][] original=matrix(files,name+"/canonical-"+c+".tsv");for(int i=0;i<n;i++)assertArrayEquals(original[i],matrices.get(c)[i],0);}
            }));
            for(String algorithm:List.of("R","Rcpp")){
                String prefix=name+"/"+algorithm+"/",status=text(files,prefix+"status.txt");
                tests.add(DynamicTest.dynamicTest(name+" "+algorithm,()->{
                    if(status.equals("FAILED")){
                        String error=text(files,prefix+"error.txt");assertTrue(error.contains("stage: original-mash-call"),error);
                        assertTrue(name.startsWith("explicit-large-SE-")&&algorithm.equals("R")&&error.contains("computationally singular")
                            ||n==1&&algorithm.equals("Rcpp")&&error.contains("incorrect number of dimensions"),error);
                        // The failed call is evidence, never a substitute expected posterior.
                        for(int r=0;r<rows;r++){var result=model.evaluate(bhat[r],shat[r],CHECK);for(double lfsr:result.lfsr())assertTrue(lfsr>=0&&lfsr<=1);}
                        return;
                    }
                    assertEquals("COMPLETED",status);var original=new HashMap<String,double[][]>();
                    for(String field:List.of("mean","sd","negative","zero","lfsr","covariance-column-major","weights","loglik","component-loglik"))original.put(field,matrix(files,prefix+field+".tsv"));
                    var failures=new ArrayList<String>();
                    for(int r=0;r<rows;r++){
                        var result=model.evaluate(bhat[r],shat[r],CHECK);
                        for(var pair:List.of(new Field("mean",result.mean()),new Field("sd",result.standardDeviation()),new Field("negative",result.negativeProbability()),new Field("zero",result.zeroProbability()),new Field("lfsr",result.lfsr()),new Field("weights",result.posteriorWeights()),new Field("component-loglik",result.componentLogLikelihoods())))
                            for(int c=0;c<pair.values().length;c++){
                                if(name.equals("boundary-small-SE")&&pair.name().equals("component-loglik")&&c==1){
                                    // The exact two-by-two determinant/quadratic form has a
                                    // separately retained 80-digit Decimal oracle. Both original
                                    // algorithms lose ~8274 log units here; this is NOT equivalence.
                                    double exact=Double.parseDouble(text(files,"boundary-oracle.tsv").split("\t")[1]);
                                    assertTrue(Math.abs(original.get(pair.name())[r][c]-exact)>8000);
                                    assertEquals(exact,pair.values()[c],20);
                                    assertEquals(0,result.posteriorWeights()[1]);
                                    assertTrue(result.numericalNotes().stream().anyMatch(s->s.startsWith("COMPONENT_SCORE_RESOLUTION:")));
                                }else near(failures,"row "+r+" "+pair.name()+"["+c+"]",original.get(pair.name())[r][c],pair.values()[c]);
                            }
                        near(failures,"row "+r+" likelihood",original.get("loglik")[0][r],result.logLikelihood());
                        for(int i=0;i<n;i++)for(int j=0;j<n;j++)near(failures,"row "+r+" covariance["+i+","+j+"]",original.get("covariance-column-major")[r][i+j*n],result.covariance()[i][j]);
                    }
                    assertTrue(failures.isEmpty(),()->String.join("\n",failures.subList(0,Math.min(30,failures.size())))+"\nTotal mismatches: "+failures.size());
                }));
            }
        }
        return tests.stream();
    }
    private record Field(String name,double[] values) { }
}
