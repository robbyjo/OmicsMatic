/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.xwas;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class BinaryPredictionScoresTest {
    private static final Path ROOT=Path.of("src/test/resources/binary-score");
    private record Data(double[] y,double[][] x,double[][] covariates) { }
    private static Data data()throws Exception {
        List<String> lines=Files.readAllLines(ROOT.resolve("input.tsv"));int n=lines.size()-1;double[] y=new double[n];double[][] x=new double[n][3],cov=new double[n][3];
        for(int i=0;i<n;i++){String[] r=lines.get(i+1).split("\t");y[i]=Double.parseDouble(r[0]);for(int j=0;j<3;j++){x[i][j]=r[j+1].equals("NA")?Double.NaN:Double.parseDouble(r[j+1]);cov[i][j]=Double.parseDouble(r[j+4]);}}return new Data(y,x,cov);
    }
    private static int[] rows(int from,int to){return java.util.stream.IntStream.range(from,to).toArray();}
    private static BinaryPredictionScores.Options controls(){return new BinaryPredictionScores.Options(10000,1e-9,.3,200000000);}
    private static double[] vector(Map<?,?> row,String key){return ((List<?>)row.get(key)).stream().mapToDouble(v->((Number)v).doubleValue()).toArray();}
    @Test void ridgeLassoAndElasticNetMatchIndependentConvexReferences()throws Exception {
        var d=data();Map<?,?> reference=new Yaml().load(Files.readString(ROOT.resolve("reference.json")));
        for(Object raw:(List<?>)reference.get("cases")){var expected=(Map<?,?>)raw;double lambda=((Number)expected.get("lambda")).doubleValue(),alpha=((Number)expected.get("alpha")).doubleValue();var fit=BinaryPredictionScores.train(d.y,d.x,d.covariates,rows(0,80),new int[0],new double[]{lambda},alpha,false,true,controls());var f=fit.frozen();
            assertArrayEquals(vector(expected,"weights"),f.featureWeights(),2e-6);assertArrayEquals(vector(expected,"covariate_weights"),f.covariateWeights(),2e-6);assertArrayEquals(vector(expected,"baseline_weights"),f.baselineWeights(),2e-6);assertArrayEquals(vector(expected,"means"),f.featureMeans(),1e-14);assertArrayEquals(vector(expected,"scales"),f.featureScales(),1e-14);assertEquals(((Number)expected.get("objective")).doubleValue(),f.fullFit().objective(),1e-11);assertTrue(f.fullFit().maximumKktResidual()<=1e-9);assertTrue(f.baselineFit().maximumKktResidual()<=1e-9);assertEquals(0,f.featureWeights()[2]);
        }
    }
    @Test void tuningUsesLogLossAndRefitUsesOnlyDeclaredDevelopmentRows()throws Exception {
        var d=data();double[] lambdas={.5,.05,.005};var a=BinaryPredictionScores.train(d.y,d.x,d.covariates,rows(0,80),rows(80,130),lambdas,.5,true,true,controls());assertEquals(130,a.frozen().developmentRows());double min=a.tuningLogLoss().stream().mapToDouble(Double::doubleValue).min().orElseThrow();for(int i=0;i<lambdas.length;i++)if(lambdas[i]==a.selectedLambda())assertEquals(min,a.tuningLogLoss().get(i));
        for(int i=130;i<d.y.length;i++){d.y[i]=1-d.y[i];d.x[i][0]=1e10;d.covariates[i][1]=-1e10;}var b=BinaryPredictionScores.train(d.y,d.x,d.covariates,rows(0,80),rows(80,130),lambdas,.5,true,true,controls());assertEquals(a.tuningLogLoss(),b.tuningLogLoss());assertArrayEquals(a.frozen().featureWeights(),b.frozen().featureWeights(),0);assertArrayEquals(a.frozen().baselineWeights(),b.frozen().baselineWeights(),0);assertArrayEquals(a.frozen().covariateWeights(),b.frozen().covariateWeights(),0);
    }
    @Test void fullPredictionUsesItsOwnCovariatesAndFrozenComparatorIsSeparate()throws Exception {
        var d=data();var f=BinaryPredictionScores.train(d.y,d.x,d.covariates,rows(0,80),new int[0],new double[]{.02},0,false,true,controls()).frozen();var x=HeldOutScores.rows(d.x,rows(130,180));var c=HeldOutScores.rows(d.covariates,rows(130,180));var scores=f.score(x);var predictions=f.predict(x,c);var baseline=f.baseline(c);boolean differs=false;
        for(int i=0;i<x.length;i++){double eta=scores[i];for(int j=0;j<c[i].length;j++)eta+=c[i][j]*f.covariateWeights()[j];assertEquals(BinaryPredictionScores.sigmoid(eta),predictions[i],1e-15);assertTrue(predictions[i]>0&&predictions[i]<1);differs|=Math.abs(predictions[i]-baseline[i])>.01;}assertTrue(differs);double before=f.featureWeights()[0];double[] edited=f.featureWeights();edited[0]=100;assertEquals(before,f.featureWeights()[0]);
    }
    @Test void invalidOutcomesLeaksAndResourceLimitsNeverProduceAFrozenModel()throws Exception {
        var d=data();assertThrows(IllegalArgumentException.class,()->BinaryPredictionScores.train(d.y,d.x,d.covariates,rows(0,80),rows(70,120),new double[]{.2},0,false,true,controls()));assertThrows(IllegalArgumentException.class,()->BinaryPredictionScores.train(d.y,d.x,d.covariates,rows(0,80),new int[0],new double[]{0},0,false,true,controls()));assertThrows(IllegalArgumentException.class,()->BinaryPredictionScores.train(d.y,d.x,d.covariates,rows(0,80),new int[0],new double[]{.2},0,false,false,controls()));
        for(var controls:List.of(new BinaryPredictionScores.Options(1,1e-9,.3,200000000),new BinaryPredictionScores.Options(10000,1e-9,.3,1)))assertThrows(IllegalArgumentException.class,()->BinaryPredictionScores.train(d.y,d.x,d.covariates,rows(0,80),new int[0],new double[]{.02},.5,false,true,controls));d.y[0]=2;assertThrows(IllegalArgumentException.class,()->BinaryPredictionScores.train(d.y,d.x,d.covariates,rows(0,80),new int[0],new double[]{.02},.5,false,true,controls()));
    }
    @Test void featureUnitChangesPreservePredictionsWithoutVarianceUnderflowOrOverflow()throws Exception {
        var original=data();var fitted=BinaryPredictionScores.train(original.y,original.x,original.covariates,rows(0,80),new int[0],new double[]{.02},.5,false,true,controls());var expected=fitted.frozen().predict(HeldOutScores.rows(original.x,rows(130,180)),HeldOutScores.rows(original.covariates,rows(130,180)));
        for(double factor:new double[]{1e-200,1e200}){var d=data();for(double[] row:d.x)row[0]*=factor;var changed=BinaryPredictionScores.train(d.y,d.x,d.covariates,rows(0,80),new int[0],new double[]{.02},.5,false,true,controls());assertArrayEquals(expected,changed.frozen().predict(HeldOutScores.rows(d.x,rows(130,180)),HeldOutScores.rows(d.covariates,rows(130,180))),1e-10);}
    }
}
