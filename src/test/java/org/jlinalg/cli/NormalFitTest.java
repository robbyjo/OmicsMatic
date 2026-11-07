/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import java.util.zip.GZIPInputStream;
import org.jlinalg.distributional.NormalFit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

final class NormalFitTest {
    static Map<String,Object> controls()throws Exception {
        try(var stream=NormalFitTest.class.getResourceAsStream("/org/jlinalg/distributional/normal-fit/original-controls-v1.json.gz")){
            assertNotNull(stream);try(var gzip=new GZIPInputStream(stream)){return object(SimpleJson.parse(new String(gzip.readAllBytes(),StandardCharsets.UTF_8)));}
        }
    }
    static Stream<Map<String,Object>> fits()throws Exception{return object(controls().get("fits")).values().stream().map(x->{try{return object(x);}catch(Exception e){throw new IllegalStateException(e);}});}
    static Stream<Map<String,Object>> probabilities()throws Exception{return list(controls().get("probabilities")).stream().map(x->{try{return object(x);}catch(Exception e){throw new IllegalStateException(e);}});}
    static double number(Object value){String s=value.toString();return switch(s){case "NA","NaN"->Double.NaN;case "Inf"->Double.POSITIVE_INFINITY;case "-Inf"->Double.NEGATIVE_INFINITY;default->Double.parseDouble(s);};}
    static double[] vector(Object raw)throws Exception {return list(raw).stream().mapToDouble(NormalFitTest::number).toArray();}
    static void close(double expected,double actual,double scale){if(!Double.isFinite(expected)){assertEquals(expected,actual);return;}if(expected==0){assertTrue(actual==0,"Reference numeric zero differs");return;}assertEquals(expected,actual,Math.max(32*Math.ulp(expected),2e-12*Math.max(Math.abs(expected),scale)));}
    @ParameterizedTest @MethodSource("fits") void analyticalFitsMatchOriginalReferenceAndRecordOptimizerLimits(Map<String,Object> row)throws Exception {
        double[] x=vector(row.get("data"));
        if(!Boolean.TRUE.equals(row.get("native_positive_finite_variance_domain"))){assertThrows(IllegalArgumentException.class,()->NormalFit.fit(x));return;}
        var expected=object(row.get("analytical"));var fit=NormalFit.fit(x);double scale=Math.sqrt(number(expected.get("variance")));
        close(number(expected.get("mean")),fit.mean(),scale);close(number(expected.get("sd")),fit.standardDeviation(),scale);close(number(expected.get("variance")),fit.variance(),number(expected.get("variance")));close(number(expected.get("loglik")),fit.logLikelihood(),1);assertEquals(x.length,fit.observations());
        var methods=object(row.get("original_methods"));
        for(String method:List.of("mle","mme")){var original=object(methods.get(method));if(Boolean.TRUE.equals(original.get("returned"))){var estimate=object(original.get("estimate"));close(number(estimate.get("mean")),fit.mean(),scale);close(number(estimate.get("sd")),fit.standardDeviation(),scale);close(number(original.get("loglik")),fit.logLikelihood(),1);close(number(original.get("aic")),fit.aic(),1);close(number(original.get("bic")),fit.bic(),1);}}
    }
    @ParameterizedTest @MethodSource("probabilities") void completeTailAndBhFamiliesMatchOriginalR(Map<String,Object> row)throws Exception {
        double mean=number(row.get("mean")),sd=number(row.get("sd"));var fit=new NormalFit.Fit(2,mean,sd,sd*sd,0,0,0);var tail=NormalFit.Tail.valueOf(text(row.get("tail")).toUpperCase(Locale.ROOT).replace('-','_'));
        var actual=NormalFit.tests(fit,vector(row.get("values")),tail,0.001,false);double[] p=vector(row.get("p")),logp=vector(row.get("logp")),q=vector(row.get("q")),logq=vector(row.get("logq"));
        for(int i=0;i<actual.size();i++){var v=actual.get(i);close(p[i],v.pValue(),1e-300);close(logp[i],v.logPValue(),1e-300);close(q[i],v.qValue(),1e-300);close(logq[i],v.logQValue(),1e-300);assertEquals(q[i]<0.001,v.meetsCutoff());}
    }
    @Test void varianceDenominatorAndLikelihoodAreAnalytical(){var fit=NormalFit.fit(new double[]{-1,1});assertEquals(0,fit.mean());assertEquals(1,fit.variance());assertEquals(1,fit.standardDeviation());assertEquals(-Math.log(2*Math.PI)-1,fit.logLikelihood(),1e-14);}
    @Test void constantAndMissingDataNeverBecomeSuccessfulFits(){for(double[] x:List.of(new double[]{0,0,0},new double[]{Double.NaN,1},new double[]{Double.POSITIVE_INFINITY,1},new double[]{1},new double[]{}))assertThrows(IllegalArgumentException.class,()->NormalFit.fit(x));}
    @Test void cutoffRulesAndUnderflowRemainExplicit(){var fit=NormalFit.fit(new double[]{-1,1});assertFalse(NormalFit.tests(fit,new double[]{0},NormalFit.Tail.UPPER,0.5,false).get(0).meetsCutoff());assertTrue(NormalFit.tests(fit,new double[]{0},NormalFit.Tail.UPPER,0.5,true).get(0).meetsCutoff());var extreme=NormalFit.tests(fit,new double[]{40,41},NormalFit.Tail.UPPER,0.001,false);assertEquals(0,extreme.get(0).pValue());assertTrue(Double.isFinite(extreme.get(0).logPValue()));assertTrue(extreme.get(0).logQValue()<0);}
    @Test void invalidProbabilitiesAndBudgetsAreRefused(){var fit=NormalFit.fit(new double[]{-1,1});assertThrows(IllegalArgumentException.class,()->NormalFit.tests(fit,new double[]{Double.NaN},NormalFit.Tail.UPPER,0.1,false));assertThrows(IllegalStateException.class,()->NormalFit.fit(new double[]{-1,1},new NormalFit.Budget(1)));assertThrows(IllegalStateException.class,()->NormalFit.tests(fit,new double[]{1,2,3},NormalFit.Tail.UPPER,0.1,false,new NormalFit.Budget(1)));}
    @Test void cancellationIsObserved(){try{Thread.currentThread().interrupt();assertThrows(IllegalStateException.class,()->NormalFit.fit(new double[]{0,1}));}finally{Thread.interrupted();}}
    @Test void nativeMethodDoesNotMutateSuppliedData(){double[] data={0.1,0.2,0.3,0.5};double[] before=data.clone();NormalFit.fit(data);assertArrayEquals(before,data);}
    @Test void cliRetainsLiteralIdsAndWholeFamilies()throws Exception {
        Path root=Files.createTempDirectory("normal-fit-test-");try{
            Files.writeString(root.resolve("samples.tsv"),"observation_id\tvalue\nNA\t-1\n a\"β \t1\n");Files.writeString(root.resolve("tests.tsv"),"test_id\tvalue\np\"β\t40\nNA\t0\n");
            var request=new LinkedHashMap<String,Object>();request.put("schema_version",1);request.put("mode","BACKGROUND");request.put("estimator","normal-mle");request.put("samples","samples.tsv");request.put("tests","tests.tsv");request.put("out","result");request.put("value_units","synthetic-continuous");request.put("background_origin","caller-supplied-reference");request.put("tail","upper");request.put("adjustment","BH");request.put("cutoff",0.5);request.put("cutoff_rule","inclusive");NextflowFiles.writeJson(root.resolve("request.json"),request);
            assertEquals(0,NormalFitToolsCli.run(new String[]{"--request",root.resolve("request.json").toString()},System.out,System.err));String tests=Files.readString(root.resolve("result/results/tests.tsv"));assertTrue(tests.contains("p\"β\t40\tupper"));assertTrue(tests.contains("NA\t0\tupper"));assertTrue(Files.readString(root.resolve("result/WARNINGS.md")).contains("TAIL PRECISION WARNING"));assertEquals(2,NormalFitToolsCli.run(new String[]{"--request",root.resolve("request.json").toString()},System.out,System.err));
        }finally{try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
    }
}
