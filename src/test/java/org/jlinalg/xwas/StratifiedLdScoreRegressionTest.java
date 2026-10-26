/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.xwas;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class StratifiedLdScoreRegressionTest {
    private static final Path ROOT=Path.of("src/test/resources/sldsc");
    private record Data(double[] chi,double[][] ld,double[] weights,double[] n,int[] blocks,double[] counts,double[][] overlap) { }
    private static Data data()throws Exception{
        var summary=Files.readAllLines(ROOT.resolve("summary.tsv"));var ld=Files.readAllLines(ROOT.resolve("ld-scores.tsv"));int n=summary.size()-1;double[] chi=new double[n],ns=new double[n],weights=new double[n];double[][] scores=new double[n][4];int[] blocks=new int[n];
        int block=0;for(int i=0;i<n;i++){String[] s=summary.get(i+1).split("\t"),r=ld.get(i+1).split("\t");chi[i]=Double.parseDouble(s[5]);ns[i]=Double.parseDouble(s[6]);weights[i]=Double.parseDouble(r[5]);for(int j=0;j<4;j++)scores[i][j]=Double.parseDouble(r[j+6]);while(block+1<13&&i>=(block+1)*n/13)block++;blocks[i]=block;}
        double[] counts=new double[4];var annotation=Files.readAllLines(ROOT.resolve("annotations.tsv"));var overlap=Files.readAllLines(ROOT.resolve("overlap.tsv"));double[][] matrix=new double[4][4];for(int j=0;j<4;j++){counts[j]=Double.parseDouble(annotation.get(j+1).split("\t")[3]);String[] row=overlap.get(j+1).split("\t");for(int k=0;k<4;k++)matrix[j][k]=Double.parseDouble(row[k+1]);}return new Data(chi,scores,weights,ns,blocks,counts,matrix);
    }
    private static double[] vector(Map<?,?> m,String key){return ((List<?>)m.get(key)).stream().mapToDouble(v->v==null?Double.NaN:((Number)v).doubleValue()).toArray();}
    private static double scalar(Map<?,?> m,String key){return ((Number)m.get(key)).doubleValue();}
    private static StratifiedLdScoreRegression.Result fit(Data d,boolean free){return StratifiedLdScoreRegression.fit(d.chi,d.ld,d.weights,d.n,d.blocks,d.counts,d.overlap,free,10000000);}
    private static void array(double[] expected,double[] actual){assertEquals(expected.length,actual.length);for(int i=0;i<expected.length;i++)if(Double.isNaN(expected[i]))assertTrue(Double.isNaN(actual[i]),"index "+i);else assertEquals(expected[i],actual[i],Math.max(1e-13,Math.abs(expected[i])*3e-8),"index "+i);}
    private static void tinyArray(double[] expected,double[] actual,double floor){for(int i=0;i<expected.length;i++)if(Double.isFinite(expected[i]))assertEquals(expected[i],actual[i],Math.max(floor,Math.abs(expected[i])*3e-8),"small quantity index "+i);}
    @Test void matchesIndependentQrAndPinnedUpstreamForFreeAndFixedIntercepts()throws Exception{
        var d=data();Map<?,?> document=new Yaml().load(Files.readString(ROOT.resolve("reference.json")));
        for(Object raw:(List<?>)document.get("cases")){var row=(Map<?,?>)raw;var e=(Map<?,?>)row.get("expected");var r=fit(d,Boolean.TRUE.equals(row.get("free_intercept")));
            array(vector(e,"coefficients"),r.coefficients());array(vector(e,"coefficient_covariance"),r.coefficientCovariance());array(vector(e,"category"),r.categoryHeritability());array(vector(e,"proportions"),r.proportions());array(vector(e,"proportion_se"),r.proportionStandardErrors());array(vector(e,"enrichments"),r.enrichments());array(vector(e,"enrichment_se"),r.enrichmentStandardErrors());array(vector(e,"contrasts"),r.contrasts());array(vector(e,"contrast_se"),r.contrastStandardErrors());array(vector(e,"p_values"),r.probabilities());array(vector(e,"delete_totals"),r.deleteTotals());array(vector(e,"delete_intercepts"),r.deleteIntercepts());array(vector(e,"weights"),r.weights());
            assertEquals(scalar(e,"intercept"),r.intercept(),1e-9);assertEquals(scalar(e,"intercept_se"),r.interceptStandardError(),1e-9);assertEquals(scalar(e,"total"),r.totalHeritability(),1e-10);assertEquals(scalar(e,"total_se"),r.totalStandardError(),1e-10);assertEquals(scalar(e,"mean_chi_square"),r.meanChiSquare(),1e-12);assertEquals(scalar(e,"aggregate_weight_heritability"),r.aggregateWeightHeritability(),1e-12);
            tinyArray(vector(e,"coefficient_covariance"),r.coefficientCovariance(),1e-28);tinyArray(vector(e,"p_values"),r.probabilities(),1e-300);
            var deletes=(List<?>)e.get("delete_coefficients");for(int b=0;b<deletes.size();b++)array(((List<?>)deletes.get(b)).stream().mapToDouble(v->((Number)v).doubleValue()).toArray(),r.deleteCoefficients()[b]);
        }
    }
    @Test void overlappingCategoryHeritabilityIsNotItsConditionalCoefficientTimesCount()throws Exception{
        var d=data();var r=fit(d,true);assertTrue(Math.abs(r.categoryHeritability()[1]-r.coefficients()[1]*d.counts[1])>.01);assertTrue(r.coefficients()[2]<0);assertTrue(r.categoryHeritability()[2]>0);assertEquals(1,r.enrichments()[0],1e-14);assertTrue(Double.isNaN(r.probabilities()[0]));
        double[] co=r.coefficients();co[0]=100;assertNotEquals(100,r.coefficients()[0]);double[][] del=r.deleteCoefficients();del[0][0]=100;assertNotEquals(100,r.deleteCoefficients()[0][0]);
    }
    @Test void refusesInvalidMomentsBlocksCollinearityAndWorkWithoutDroppingAnnotations()throws Exception{
        var d=data();assertThrows(IllegalArgumentException.class,()->StratifiedLdScoreRegression.fit(d.chi,d.ld,d.weights,d.n,d.blocks,d.counts,d.overlap,true,1));
        double[][] malformed={{1}};assertThrows(IllegalArgumentException.class,()->StratifiedLdScoreRegression.validateMoments(d.counts,malformed));
        d.overlap[1][2]++;assertThrows(IllegalArgumentException.class,()->fit(d,true));d.overlap[1][2]--;
        d.blocks[0]=1;assertThrows(IllegalArgumentException.class,()->fit(d,true));d.blocks[0]=0;
        for(double[] row:d.ld)row[2]=row[1];assertThrows(IllegalArgumentException.class,()->fit(d,true));
    }
    @Test void annotationPermutationKeepsTheSameScientificModel()throws Exception{
        var d=data();var before=fit(d,true);int[] perm={0,3,1,2};double[][] x=new double[d.ld.length][4],overlap=new double[4][4];double[] counts=new double[4];
        for(int j=0;j<4;j++){counts[j]=d.counts[perm[j]];for(int k=0;k<4;k++)overlap[j][k]=d.overlap[perm[j]][perm[k]];for(int i=0;i<x.length;i++)x[i][j]=d.ld[i][perm[j]];}
        var after=fit(new Data(d.chi,x,d.weights,d.n,d.blocks,counts,overlap),true);assertEquals(before.totalHeritability(),after.totalHeritability(),1e-12);for(int j=0;j<4;j++){assertEquals(before.coefficients()[perm[j]],after.coefficients()[j],1e-12);assertEquals(before.enrichments()[perm[j]],after.enrichments()[j],1e-10);}
    }
    @Test void identicalAnnotationsCannotBeGivenArbitraryDifferentLdScores() {
        double[] counts={1000,200,200};double[][] overlap={{1000,200,200},{200,200,200},{200,200,200}};
        assertThrows(IllegalArgumentException.class,()->StratifiedLdScoreRegression.validateMoments(counts,overlap));
    }
}
