/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.settest;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class ConditionalBinarySetTestsTest {
    private static final Path ROOT=Path.of("src/test/resources/conditional-binary-set/reference.json");
    private static double[] vector(Map<?,?> map,String key){return ((List<?>)map.get(key)).stream().mapToDouble(v->((Number)v).doubleValue()).toArray();}
    private static double[][] matrix(Map<?,?> map,String key){return ((List<?>)map.get(key)).stream().map(v->((List<?>)v).stream().mapToDouble(x->((Number)x).doubleValue()).toArray()).toArray(double[][]::new);}
    private static ConditionalBinarySetTests.Options exact(double threshold){return new ConditionalBinarySetTests.Options("exact",100000,100,1000,71,1000000000,20000000,threshold);}
    private static ConditionalBinarySetTests.Weights weights(Map<?,?> c){Map<?,?> w=(Map<?,?>)c.get("weights");return new ConditionalBinarySetTests.Weights(vector(w,"burden"),vector(w,"kernel"),vector(w,"acat"),String.valueOf(w.get("acat_scheme")));}
    private static List<?> cases()throws Exception{Map<?,?> d=new Yaml().load(Files.readString(ROOT));return (List<?>)d.get("cases");}
    @Test void allFiveTestsMatchIndependentIndividualLabelEnumeration()throws Exception{
        for(Object raw:cases()){Map<?,?> c=(Map<?,?>)raw,e=(Map<?,?>)c.get("expected");double[] y=vector(c,"y");int[] strata=java.util.Arrays.stream(vector(c,"strata")).mapToInt(v->(int)v).toArray();var model=ConditionalSetNull.prepare(y,strata,vector(c,"offset"),10000000);assertArrayEquals(vector(e,"null_means"),model.nullMeans(),1e-12);
            var r=ConditionalBinarySetTests.analyze(model,matrix(c,"genotype"),vector(c,"maf"),vector(c,"mac"),weights(c),exact(((Number)c.get("threshold")).doubleValue()));double[] expected=vector(e,"observed_p"),statistics=vector(e,"observed_statistics");assertEquals(5,r.tests().size());for(int i=0;i<5;i++){assertEquals(expected[i],r.tests().get(i).pValue(),3e-10,c.get("name")+" / "+r.tests().get(i).test());assertEquals(statistics[i],r.tests().get(i).statistic(),Math.max(1e-10,Math.abs(statistics[i])*4e-12));}assertTrue(r.allocationStates()<=((Number)e.get("states")).longValue());assertEquals(0,r.draws());
        }
    }
    @Test void completeFiniteNullHasConservativeTypeOneErrorForAllFiveStatistics()throws Exception{
        Map<?,?> c=(Map<?,?>)cases().get(0),e=(Map<?,?>)c.get("expected");double[][] labels=matrix(e,"all_labels"),expected=matrix(e,"all_p"),g=matrix(c,"genotype");double[] probabilities=vector(e,"probabilities"),off=vector(c,"offset"),maf=vector(c,"maf"),mac=vector(c,"mac");int[] strata=java.util.Arrays.stream(vector(c,"strata")).mapToInt(v->(int)v).toArray();double[][] rejected=new double[5][5];double[] levels={.01,.05,.1,.25,.5};
        for(int row=0;row<labels.length;row++){var model=ConditionalSetNull.prepare(labels[row],strata,off,1000000);var r=ConditionalBinarySetTests.analyze(model,g,maf,mac,weights(c),exact(2));for(int test=0;test<5;test++){assertEquals(expected[row][test],r.tests().get(test).pValue(),3e-10);for(int a=0;a<levels.length;a++)if(r.tests().get(test).pValue()<=levels[a]+1e-14)rejected[test][a]+=probabilities[row];}}
        for(int t=0;t<5;t++)for(int a=0;a<levels.length;a++)assertTrue(rejected[t][a]<=levels[a]+1e-12);
    }
    @Test void rarePatternCompressionCanEnumerateALargeCohortWithTinyMac() {
        int n=10000;double[] y=new double[n];for(int i=0;i<5;i++)y[i]=1;double[][] g=new double[n][2];g[0][0]=1;g[1][1]=1;var model=ConditionalSetNull.prepare(y,new int[n],null,1000000);var r=ConditionalBinarySetTests.analyze(model,g,new double[]{.5/n,.5/n},new double[]{1,1},new double[]{1,1},exact(10));assertEquals(4,r.allocationStates());double expected=5.0*4/n/(n-1);assertEquals(expected,r.tests().get(0).pValue(),1e-15);assertTrue(r.work()<1000000);
    }
    @Test void independentPilotMonteCarloIsSeededNonzeroAndReportsTailUncertainty()throws Exception{
        Map<?,?> c=(Map<?,?>)cases().get(1);var model=ConditionalSetNull.prepare(vector(c,"y"),java.util.Arrays.stream(vector(c,"strata")).mapToInt(v->(int)v).toArray(),vector(c,"offset"),1000000);var controls=new ConditionalBinarySetTests.Options("monte_carlo",100000,200,5000,123,1000000000,20000000,2);var a=ConditionalBinarySetTests.analyze(model,matrix(c,"genotype"),vector(c,"maf"),vector(c,"mac"),weights(c),controls);var b=ConditionalBinarySetTests.analyze(model,matrix(c,"genotype"),vector(c,"maf"),vector(c,"mac"),weights(c),controls);assertEquals(a,b);assertEquals(200,a.pilotDraws());assertEquals(5000,a.draws());for(var t:a.tests()){assertTrue(t.pValue()>=1.0/5001-1e-15);assertTrue(t.tailProbabilityLower95()<=t.tailProbabilityUpper95());assertEquals((t.tailDraws()+1.0)/5001,t.pValue(),1e-14);}assertTrue(a.interpretation().contains("separate pilot"));
        Map<?,?> independent=new Yaml().load(Files.readString(ROOT.resolveSibling("mc-reference.json")));List<?> tests=(List<?>)independent.get("tests");for(int i=0;i<5;i++){Map<?,?> expected=(Map<?,?>)tests.get(i);var t=a.tests().get(i);assertEquals(((Number)expected.get("hits")).longValue(),t.tailDraws(),t.test());assertEquals(((Number)expected.get("statistic")).doubleValue(),t.statistic(),Math.max(1e-10,Math.abs(t.statistic())*4e-12));assertEquals(((Number)expected.get("lower95")).doubleValue(),t.tailProbabilityLower95(),2e-12);assertEquals(((Number)expected.get("upper95")).doubleValue(),t.tailProbabilityUpper95(),2e-12);}
    }
    @Test void weightedGroupedSamplerMatchesIndependentIndividualLabelProbabilities()throws Exception{
        Map<?,?> c=(Map<?,?>)cases().get(1),e=(Map<?,?>)c.get("expected");double[] means=vector(e,"null_means"),probabilities=vector(e,"probabilities");double[][] g=matrix(c,"genotype"),labels=matrix(e,"all_labels");var expected=new java.util.TreeMap<String,Double>();
        for(int a=0;a<labels.length;a++){double[] u=new double[g[0].length];for(int i=0;i<g.length;i++)for(int j=0;j<u.length;j++)u[j]+=(labels[a][i]-means[i])*g[i][j];expected.merge(scoreKey(u),probabilities[a],Double::sum);}
        var model=ConditionalSetNull.prepare(vector(c,"y"),java.util.Arrays.stream(vector(c,"strata")).mapToInt(v->(int)v).toArray(),vector(c,"offset"),1000000);var budget=new ConditionalSetNull.Budget(1000000000,20000000);var distribution=new ConditionalSetNull.Distribution(model,g,100000,budget);var observed=new java.util.TreeMap<String,Integer>();var random=new java.util.SplittableRandom(813071);int draws=60000;
        for(int i=0;i<draws;i++)observed.merge(scoreKey(distribution.sample(random,budget)),1,Integer::sum);assertTrue(expected.keySet().containsAll(observed.keySet()));
        for(var entry:expected.entrySet()){double p=entry.getValue();assertEquals(draws*p,observed.getOrDefault(entry.getKey(),0),7*Math.sqrt(draws*p*(1-p))+20);}
    }
    private static String scoreKey(double[] u){return java.util.Arrays.toString(java.util.Arrays.stream(u).mapToLong(v->Math.round(v*1e9)).toArray());}
    @Test void extremeFiniteOffsetTailsRetainLogProbabilityAfterDoubleUnderflow() {
        double[] y={1,1,0,0};double[][] g={{1},{1},{0},{0}};var model=ConditionalSetNull.prepare(y,new int[4],new double[]{-700,-700,0,0},1000000);var r=ConditionalBinarySetTests.analyze(model,g,new double[]{.25},new double[]{2},new double[]{1},exact(10));assertEquals(0,r.tests().get(0).pValue());assertEquals(-1400,r.tests().get(0).logPValue(),1e-9);
    }
    @Test void budgetAndModelFailuresNeverSubstituteAnAsymptoticOrTruncatedTest()throws Exception{
        Map<?,?> c=(Map<?,?>)cases().get(0);var model=ConditionalSetNull.prepare(vector(c,"y"),new int[12],null,1000000);var bad=new ConditionalBinarySetTests.Options("exact",1,100,100,1,10000000,20000000,2);assertThrows(IllegalArgumentException.class,()->ConditionalBinarySetTests.analyze(model,matrix(c,"genotype"),vector(c,"maf"),vector(c,"mac"),weights(c),bad));for(var controls:List.of(new ConditionalBinarySetTests.Options("auto",100,100,100,1,1,20000000,2),new ConditionalBinarySetTests.Options("auto",100,100,100,1,10000000,1,2)))assertThrows(IllegalArgumentException.class,()->ConditionalBinarySetTests.analyze(model,matrix(c,"genotype"),vector(c,"maf"),vector(c,"mac"),weights(c),controls));assertThrows(IllegalArgumentException.class,()->ConditionalSetNull.prepare(new double[]{0,.5,1},new int[3],null,1000));
    }
}
