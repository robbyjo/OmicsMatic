/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.xwas;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class MixedLdScoreRegressionTest {
    private static final Path ROOT=Path.of("src/test/resources/sldsc-mixed");
    private record Data(double[] chi,double[][] ld,double[] w,double[] n,int[] blocks,double[] sums,double[][] q,boolean[] binary) { }
    private static Data data()throws Exception {
        var summary=Files.readAllLines(ROOT.resolve("summary.tsv"));var ld=Files.readAllLines(ROOT.resolve("ld-scores.tsv"));int n=summary.size()-1,c=5;double[] chi=new double[n],ns=new double[n],w=new double[n],sums=new double[c];double[][] x=new double[n][c],q=new double[c][c];int[] blocks=new int[n];boolean[] binary=new boolean[c];
        for(int i=0;i<n;i++){String[] s=summary.get(i+1).split("\t"),l=ld.get(i+1).split("\t");chi[i]=Double.parseDouble(s[5]);ns[i]=Double.parseDouble(s[6]);w[i]=Double.parseDouble(l[5]);for(int j=0;j<c;j++)x[i][j]=Double.parseDouble(l[j+6]);for(int b=0;b<13;b++)if(i>=b*n/13)blocks[i]=b;}
        var annotations=Files.readAllLines(ROOT.resolve("annotations.tsv"));var products=Files.readAllLines(ROOT.resolve("overlap.tsv"));for(int j=0;j<c;j++){String[] a=annotations.get(j+1).split("\t"),p=products.get(j+1).split("\t");sums[j]=Double.parseDouble(a[4]);binary[j]=a[3].equals("binary");for(int k=0;k<c;k++)q[j][k]=Double.parseDouble(p[k+1]);}
        return new Data(chi,x,w,ns,blocks,sums,q,binary);
    }
    private static StratifiedLdScoreRegression.Result fit(Data d,boolean free){return StratifiedLdScoreRegression.fitMixed(d.chi,d.ld,d.w,d.n,d.blocks,d.sums,d.q,d.binary,free,10000000);}
    private static double[] vector(Map<?,?> map,String key){return ((List<?>)map.get(key)).stream().mapToDouble(v->v==null?Double.NaN:((Number)v).doubleValue()).toArray();}
    private static void array(double[] expected,double[] actual){assertEquals(expected.length,actual.length);for(int i=0;i<expected.length;i++)if(Double.isNaN(expected[i]))assertTrue(Double.isNaN(actual[i]),"index "+i);else assertEquals(expected[i],actual[i],Math.max(1e-28,Math.abs(expected[i])*3e-7),"index "+i);}
    private static void proportionSeArray(double[] expected,double[] actual){
        // The all-SNP proportion is identically one, so its true SE is zero.
        // QR and normal-equation roundoff need an absolute check only here.
        assertEquals(0,expected[0],1e-12);assertEquals(0,actual[0],1e-12);
        array(java.util.Arrays.copyOfRange(expected,1,expected.length),java.util.Arrays.copyOfRange(actual,1,actual.length));
    }
    @Test void continuousAndBinaryStatisticsMatchIndependentFullWeightedFitsAndRatios()throws Exception {
        Map<?,?> reference=new Yaml().load(Files.readString(ROOT.resolve("reference.json")));var d=data();
        for(Object raw:(List<?>)reference.get("cases")){var row=(Map<?,?>)raw;var e=(Map<?,?>)row.get("expected");var r=fit(d,Boolean.TRUE.equals(row.get("free_intercept")));var standardized=StratifiedLdScoreRegression.standardizedEffects(r,d.sums,d.q);
            array(vector(e,"coefficients"),r.coefficients());array(vector(e,"coefficient_covariance"),r.coefficientCovariance());array(vector(e,"category"),r.categoryHeritability());array(vector(e,"proportions"),r.proportions());proportionSeArray(vector(e,"proportion_se"),r.proportionStandardErrors());array(vector(e,"enrichments"),r.enrichments());proportionSeArray(vector(e,"enrichment_se"),r.enrichmentStandardErrors());array(vector(e,"contrasts"),r.contrasts());array(vector(e,"contrast_se"),r.contrastStandardErrors());array(vector(e,"p_values"),r.probabilities());array(vector(e,"delete_totals"),r.deleteTotals());array(vector(e,"delete_intercepts"),r.deleteIntercepts());array(vector(e,"weights"),r.weights());
            array(vector(e,"annotation_sd"),standardized.annotationStandardDeviations());array(vector(e,"standardized_coefficients"),standardized.effects());array(vector(e,"standardized_se"),standardized.standardErrors());
            assertEquals(((Number)e.get("total")).doubleValue(),r.totalHeritability(),1e-10);assertEquals(((Number)e.get("total_se")).doubleValue(),r.totalStandardError(),1e-11);assertEquals(((Number)e.get("intercept")).doubleValue(),r.intercept(),1e-9);assertEquals(((Number)e.get("intercept_se")).doubleValue(),r.interceptStandardError(),1e-9);
            var dc=(List<?>)e.get("delete_coefficients");var ds=(List<?>)e.get("standardized_deletes");for(int b=0;b<dc.size();b++){array(((List<?>)dc.get(b)).stream().mapToDouble(v->((Number)v).doubleValue()).toArray(),r.deleteCoefficients()[b]);array(((List<?>)ds.get(b)).stream().mapToDouble(v->v==null?Double.NaN:((Number)v).doubleValue()).toArray(),standardized.deleteEffects()[b]);}
        }
    }
    @Test void centredSignedAnnotationsHaveNoFakeCategoryFractionOrEnrichment()throws Exception {var d=data();assertTrue(Math.abs(d.sums[3])<1e-12);assertTrue(java.util.Arrays.stream(d.ld).anyMatch(r->r[3]<0));var r=fit(d,true);assertTrue(Double.isNaN(r.categoryHeritability()[2]));assertTrue(Double.isNaN(r.proportions()[2]));assertTrue(Double.isNaN(r.enrichments()[2]));assertTrue(Double.isNaN(r.contrasts()[2]));assertTrue(Double.isFinite(r.probabilities()[2]));assertTrue(Double.isFinite(StratifiedLdScoreRegression.standardizedEffects(r,d.sums,d.q).effects()[3]));}
    @Test void affineAnnotationUnitsPreserveFitBinaryEnrichmentAndStandardizedEffect()throws Exception {
        var original=data();var before=fit(original,true);var bs=StratifiedLdScoreRegression.standardizedEffects(before,original.sums,original.q);
        for(double factor:new double[]{2.5,-3}){var d=data();double offset=.7;double[][] old=java.util.Arrays.stream(d.q).map(double[]::clone).toArray(double[][]::new);double[] oldSums=d.sums.clone();d.sums[2]=factor*oldSums[2]+offset*oldSums[0];for(int j=0;j<5;j++){d.q[2][j]=factor*old[2][j]+offset*old[0][j];d.q[j][2]=factor*old[j][2]+offset*old[j][0];}d.q[2][2]=factor*factor*old[2][2]+2*factor*offset*old[0][2]+offset*offset*old[0][0];for(double[] row:d.ld)row[2]=factor*row[2]+offset*row[0];var after=fit(d,true);var as=StratifiedLdScoreRegression.standardizedEffects(after,d.sums,d.q);
            assertEquals(before.totalHeritability(),after.totalHeritability(),1e-10);assertEquals(before.coefficients()[2]/factor,after.coefficients()[2],1e-12);assertEquals(before.enrichments()[1],after.enrichments()[1],1e-9);assertEquals(Math.signum(factor)*bs.effects()[2],as.effects()[2],1e-9);assertEquals(bs.standardErrors()[2],as.standardErrors()[2],1e-9);assertEquals(before.probabilities()[2],after.probabilities()[2],Math.max(1e-250,before.probabilities()[2]*1e-5));array(before.weights(),after.weights());
        }
    }
    @Test void standardizedSeIncludesTotalHeritabilityUncertaintyAndDefensiveCopies()throws Exception {var d=data();var r=fit(d,true);var s=StratifiedLdScoreRegression.standardizedEffects(r,d.sums,d.q);double naive=Math.sqrt(r.coefficientCovariance()[12])*s.annotationStandardDeviations()[2]*d.sums[0]/r.totalHeritability();assertTrue(Math.abs(naive-s.standardErrors()[2])>naive*1e-5);double original=s.effects()[2];double[] effects=s.effects();effects[2]=999;assertEquals(original,s.effects()[2],0);double[][] deletes=s.deleteEffects();double first=deletes[0][2];deletes[0][2]=999;assertEquals(first,s.deleteEffects()[0][2],0);}
    @Test void inconsistentConstantIndefiniteOrDependentReferenceModelsCannotCreateEffects()throws Exception {
        var d=data();d.q[2][2]=-1;Data badDiagonal=d;assertThrows(IllegalArgumentException.class,()->fit(badDiagonal,true));
        d=data();d.sums[2]+=10;Data wrongSum=d;assertThrows(IllegalArgumentException.class,()->fit(wrongSum,true));
        d=data();d.binary[0]=false;Data noBaseline=d;assertThrows(IllegalArgumentException.class,()->fit(noBaseline,true));
        d=data();d.sums[2]=0;d.q[2][2]=0;Data constant=d;assertThrows(IllegalArgumentException.class,()->fit(constant,true));
        d=data();for(int j=0;j<5;j++){d.q[2][j]=d.q[3][j];d.q[j][2]=d.q[j][3];}d.q[2][2]=d.q[3][3];d.sums[2]=d.sums[3];Data dependent=d;assertThrows(IllegalArgumentException.class,()->fit(dependent,true));
    }
    @Test void budgetAndCollinearLdNeverDropRegisteredAnnotations()throws Exception {var d=data();assertThrows(IllegalArgumentException.class,()->StratifiedLdScoreRegression.fitMixed(d.chi,d.ld,d.w,d.n,d.blocks,d.sums,d.q,d.binary,true,1));for(double[] row:d.ld)row[2]=row[3];assertThrows(IllegalArgumentException.class,()->fit(d,true));}
}
