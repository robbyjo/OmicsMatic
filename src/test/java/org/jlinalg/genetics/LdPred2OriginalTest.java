/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

/** Answers are produced by the unchanged original installed R/C++ package. */
class LdPred2OriginalTest {
    private record Model(String name,LdCorrelation corr,double[] beta,double[] se,double[] n,double[] scale,
            LdPred2.GridParameters parameters,int[] seed,Map<String,byte[]> files) {
        String text(String file) { return new String(files.get(name+"/"+file),StandardCharsets.UTF_8); }
    }
    private static Map<String,byte[]> fixture() throws IOException {
        Map<String,byte[]> files=new LinkedHashMap<>();
        try(var input=LdPred2OriginalTest.class.getResourceAsStream("ldpred2-bigsnpr122-original.zip")) {
            assertNotNull(input);
            try(var z=new ZipInputStream(input)) {
                for(var e=z.getNextEntry();e!=null;e=z.getNextEntry())assertNull(files.put(e.getName(),z.readAllBytes()));
            }
        }
        return files;
    }
    private static double[][] matrix(String text) {
        return text.lines().map(line->java.util.Arrays.stream(line.split("\t")).mapToDouble(Double::parseDouble).toArray()).toArray(double[][]::new);
    }
    private static double[] vector(String text) { return text.lines().mapToDouble(Double::parseDouble).toArray(); }
    private static int[] state(String text) { return text.lines().mapToInt(Integer::parseInt).toArray(); }
    private static List<Model> models() throws IOException {
        Map<String,byte[]> files=fixture();List<Model> result=new ArrayList<>();
        for(String name:new String(files.get("case-index.txt"),StandardCharsets.UTF_8).lines().toList()) {
            Properties p=new Properties();p.load(new ByteArrayInputStream(files.get(name+"/parameters.properties")));
            double[][] stats=matrix(new String(files.get(name+"/summary.tsv"),StandardCharsets.UTF_8));int m=stats.length;
            double[] beta=new double[m],se=new double[m],n=new double[m],scale=new double[m];
            for(int i=0;i<m;i++){beta[i]=stats[i][0];se[i]=stats[i][1];n[i]=stats[i][2];scale[i]=Math.sqrt(n[i]*(se[i]*se[i])+beta[i]*beta[i]);}
            double[][] dense=matrix(new String(files.get(name+"/correlation.tsv"),StandardCharsets.UTF_8));
            int count=0;for(double[] row:dense)for(double value:row)if(value!=0)count++;
            int[] pointers=new int[m+1],rows=new int[count];double[] values=new double[count];int at=0;
            for(int col=0;col<m;col++){pointers[col]=at;for(int row=0;row<m;row++)if(dense[row][col]!=0){rows[at]=row;values[at++]=dense[row][col];}}pointers[m]=at;
            var parameters=new LdPred2.GridParameters(Double.parseDouble(p.getProperty("heritability")),Double.parseDouble(p.getProperty("causalProportion")),Boolean.parseBoolean(p.getProperty("sparse")),Integer.parseInt(p.getProperty("burnIn")),Integer.parseInt(p.getProperty("iterations")));
            result.add(new Model(name,LdCorrelation.ofCsc(m,pointers,rows,values),beta,se,n,scale,parameters,state(new String(files.get(name+"/seed-before.txt"),StandardCharsets.UTF_8)),files));
        }
        assertEquals(49,result.size());return result;
    }

    @Test void allOriginalInfinitesimalEffectsMatchWithUnequalSampleSizesAndDiverseLd() throws Exception {
        double worst=0;
        for(Model m:models()) {
            double[] expected=vector(m.text("inf-allele-effects.txt"));
            double[] actual=LdPred2.infinitesimal(m.corr,m.beta,m.se,m.n,m.parameters.heritability(),new LdPred2.SolveOptions(1e-12,1000)).alleleEffects();
            for(int j=0;j<actual.length;j++)worst=Math.max(worst,Math.abs(actual[j]-expected[j]));
            assertArrayEquals(expected,actual,2e-10,m.name);
        }
        System.out.println("Original bigsnpr 1.12.2: all 49 infinitesimal fits; maximum absolute effect discrepancy="+worst);
    }

    @Test void originalGibbsPosteriorMeansDivergenceAndRandomStateMatchForAll49Models() throws Exception {
        int finite=0,divergent=0;double worst=0;
        for(Model m:models()) {
            var random=new LdPred2Random.RStream(m.seed);
            if(m.text("grid-core-status.txt").strip().equals("DIVERGENCE")) {
                assertThrows(LdPred2.DivergenceException.class,()->LdPred2.gibbs(m.corr,m.beta,m.se,m.n,m.parameters,random,null),m.name);divergent++;
            } else {
                double[] actual=LdPred2.gibbs(m.corr,m.beta,m.se,m.n,m.parameters,random,null).alleleEffects();
                double[] expected=vector(m.text("grid-core-allele-effects.txt"));
                for(int j=0;j<actual.length;j++)worst=Math.max(worst,Math.abs(actual[j]-expected[j]));
                assertArrayEquals(expected,actual,2e-11,m.name);finite++;
            }
            assertArrayEquals(state(m.text("seed-after.txt")),random.state(),m.name+" final original C++ random state");
        }
        assertTrue(finite>=24);assertTrue(divergent>0);assertEquals(49,finite+divergent);
        System.out.println("Original bigsnpr 1.12.2 grid core: finite="+finite+", divergent="+divergent+", maximum absolute posterior discrepancy="+worst+"; all final R states matched");
    }

    @Test void retainedSamplesAreDistinguishedFromPosteriorMeansAndMatchOriginalPublicSamplingOnAcceptedModels() throws Exception {
        int compared=0;double worst=0;
        for(Model m:models()) {
            if(m.text("grid-core-status.txt").strip().equals("DIVERGENCE"))continue;
            double[][] expected=matrix(m.text("public-allele-samples.tsv"));List<double[]> actual=new ArrayList<>();
            var random=new LdPred2Random.RStream(m.seed);
            LdPred2.gibbs(m.corr,m.beta,m.se,m.n,m.parameters,random,(k,b)->actual.add(b));
            assertEquals(m.parameters.iterations(),actual.size());
            for(int k=0;k<actual.size();k++)for(int j=0;j<m.beta.length;j++) {
                double value=actual.get(k)[j]*m.scale[j];worst=Math.max(worst,Math.abs(value-expected[j][k]));
                assertEquals(expected[j][k],value,2e-11,m.name+" variant="+j+" sample="+k);
            }
            assertArrayEquals(state(m.text("public-sampling-seed-after.txt")),random.state(),m.name+" public sampling random state");compared++;
        }
        assertTrue(compared>0);
        System.out.println("Original public sampling: accepted models="+compared+", maximum absolute sampled-effect discrepancy="+worst);
    }
}
