/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.jlinalg.mr.SmrStatistics;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

class SmrStatisticsTest {
    static Map<String,Object> corpus;
    @BeforeAll static void load() throws IOException {
        try(var in=new GZIPInputStream(Objects.requireNonNull(SmrStatisticsTest.class.getResourceAsStream("/org/jlinalg/mr/smr/statistics-controls-v1.json.gz")))) {
            corpus=object(NextflowParameters.parse(new String(in.readAllBytes(),StandardCharsets.UTF_8),32<<20,5000000));
        }
        assertEquals(72,list(corpus.get("kernel_inputs")).size());
        assertEquals(72,list(corpus.get("kernel_original")).size());
    }
    static double number(Object x) throws IOException { return finite(x,-Double.MAX_VALUE,Double.MAX_VALUE); }
    static double[] vector(Object x) throws IOException {var raw=list(x);double[] r=new double[raw.size()];for(int i=0;i<r.length;i++)r[i]=number(raw.get(i));return r;}
    static double[][] matrix(Object x) throws IOException {var raw=list(x);double[][] r=new double[raw.size()][];for(int i=0;i<r.length;i++)r[i]=vector(raw.get(i));return r;}
    static Stream<Integer> cases(){return java.util.stream.IntStream.range(0,72).boxed();}
    static void close(double expected,double actual,double relative,String detail){assertEquals(expected,actual,Math.max(2e-15,relative*Math.abs(expected)),detail);}
    static void matrices(double[][] expected,double[][] actual,double relative,String detail){assertEquals(expected.length,actual.length);for(int i=0;i<expected.length;i++){assertEquals(expected[i].length,actual[i].length);for(int j=0;j<expected[i].length;j++)close(expected[i][j],actual[i][j],relative,detail+"["+i+","+j+"]");}}
    static SmrStatistics.DeviationKernel kernel(Map<String,Object> c,int top) throws IOException {return SmrStatistics.deviationKernel(vector(c.get("exposure")),vector(c.get("exposure_se")),vector(c.get("outcome")),vector(c.get("outcome_se")),matrix(c.get("LD")),top);}

    @ParameterizedTest @MethodSource("cases") void completeOriginalCovarianceAndIndependentMatrixProjection(int index) throws IOException {
        var c=object(list(corpus.get("kernel_inputs")).get(index));var gold=object(list(corpus.get("kernel_original")).get(index));assertEquals(c.get("id"),gold.get("id"));int top=(int)integer(gold.get("original_top_index"),0,499);var actual=kernel(c,top);double[][] original=matrix(gold.get("original_covariance"));matrices(original,actual.ratioCovariance(),8e-13,"complete original covariance");int n=original.length;double[][] difference=new double[n-1][n];for(int i=0,k=0;i<n;i++)if(i!=top){difference[k][top]=1;difference[k++][i]=-1;}
        var a=new Array2DRowRealMatrix(difference);var projected=a.multiply(new Array2DRowRealMatrix(original)).multiply(a.transpose());for(int i=0;i<n-1;i++)projected.addToEntry(i,i,1e-8);matrices(projected.getData(),actual.deviationCovariance(),8e-12,"independent A*C*A' projection");double[] bx=vector(c.get("exposure")),by=vector(c.get("outcome"));double[] ratios=new double[n];for(int i=0;i<n;i++)ratios[i]=by[i]/bx[i];var delta=a.operate(ratios);double total=0;for(int i=0;i<n-1;i++)total+=delta[i]*delta[i]/projected.getEntry(i,i);close(total,actual.statistic(),8e-11,"complete independently projected statistic");assertEquals(top,actual.topIndex());
        double[][] correlation=actual.deviationCorrelation();for(int i=0;i<n-1;i++)for(int j=0;j<n-1;j++)close(projected.getEntry(i,j)/Math.sqrt(projected.getEntry(i,i)*projected.getEntry(j,j)),correlation[i][j],8e-12,"complete normalized deviation covariance");
    }
    @Test void originalChiSquareFunctionTailsForAllInputs() throws IOException {
        for(int k=0;k<72;k++){var c=object(list(corpus.get("kernel_inputs")).get(k));var gold=object(list(corpus.get("kernel_original")).get(k));double[] bx=vector(c.get("exposure")),sx=vector(c.get("exposure_se")),by=vector(c.get("outcome")),sy=vector(c.get("outcome_se")),expected=vector(gold.get("original_chisq_survival_for_ratio_diagnostics"));for(int i=0;i<bx.length;i++)close(expected[i],SmrStatistics.ratio(bx[i],sx[i],by[i],sy[i]).pValue(),4e-12,"original DCDFlib chi-square tail");}
    }
    static double f32(String text){return (double)Float.parseFloat(text);}
    static void formatted(String original,double actual,boolean scientific){String rounded=String.format(Locale.ROOT,scientific?"%.6e":"%.6g",actual);assertEquals(Double.parseDouble(original),Double.parseDouble(rounded),"Original source output precision: "+original+" versus "+rounded);}
    @Test void completeOriginalRatioRowsAcrossBothSourceVersions() throws IOException {
        var qtl=new HashMap<String,String[]>();String[] lines=text(corpus.get("original_qtl_text")).split("\n");for(int i=1;i<lines.length;i++){String[] row=lines[i].split("\t");qtl.put(row[6]+"\t"+row[0],row);}var gwas=new HashMap<String,String[]>();lines=text(corpus.get("original_GWAS_text")).split("\n");for(int i=1;i<lines.length;i++){String[] row=lines[i].split("\t");gwas.put(row[0],row);}
        int tables=0,rows=0;for(Object raw:list(corpus.get("original_tables"))){var table=object(raw);var header=strings(table.get("header"),100);for(Object rr:list(table.get("rows"))){var row=strings(rr,100);String probe=row.get(header.indexOf("probeID")),snp=row.get(header.indexOf("topSNP"));String[] x=qtl.get(probe+"\t"+snp),y=gwas.get(snp);assertNotNull(x);assertNotNull(y);var ratio=SmrStatistics.ratio(f32(x[11]),f32(x[12]),Double.parseDouble(y[4]),Double.parseDouble(y[5]));formatted(row.get(header.indexOf("b_SMR")),ratio.effect(),false);formatted(row.get(header.indexOf("se_SMR")),ratio.standardError(),false);formatted(row.get(header.indexOf("p_SMR")),ratio.pValue(),true);rows++;}tables++;}assertEquals(10,tables);assertEquals(40,rows);
        assertTrue(SmrStatistics.ratio(.5,.02,.1,.012).standardError()>.012/.5,"Exposure SE must not be silently dropped as in a first-order-only Wald SE");
    }
    @Test void explicitOverlapVarianceMatchesFiniteDifferenceJacobian(){double bx=.4,sx=.02,by=.1,sy=.012,rho=.4,h=1e-7;double dx=((by/(bx+h))-(by/(bx-h)))/(2*h),dy=(((by+h)/bx)-((by-h)/bx))/(2*h);double variance=dx*dx*sx*sx+dy*dy*sy*sy+2*dx*dy*rho*sx*sy;var r=SmrStatistics.ratio(bx,sx,by,sy,rho);assertEquals(variance,r.standardError()*r.standardError(),2e-13);assertNotEquals(SmrStatistics.ratio(bx,sx,by,sy).standardError(),r.standardError());}
    @Test void exposureScalingAlleleReversalAndZeroOutcome(){var a=SmrStatistics.ratio(.4,.02,.1,.012);var b=SmrStatistics.ratio(4,.2,.1,.012);close(a.effect()/10,b.effect(),1e-14,"exposure units");close(a.standardError()/10,b.standardError(),1e-14,"exposure SE units");close(a.pValue(),b.pValue(),1e-13,"scale invariant P");assertEquals(a,SmrStatistics.ratio(-.4,.02,-.1,.012));var zero=SmrStatistics.ratio(.4,.02,0,.012);assertEquals(0,zero.statistic());assertEquals(1,zero.pValue());assertEquals(0,zero.logPValue());}
    @Test void underflowRetainsFiniteLogTail(){var r=SmrStatistics.ratio(1,1e-6,100,1);assertEquals(0,r.pValue());assertTrue(Double.isFinite(r.logPValue())&&r.logPValue() < -1000);}
    @Test void kernelDefensivelyCopiesEveryArray() throws IOException {var c=object(list(corpus.get("kernel_inputs")).get(0));var a=kernel(c,0);double b=a.ratios()[0],v=a.ratioCovariance()[0][0],d=a.deviationCovariance()[0][0],r=a.deviationCorrelation()[0][0],q=a.squaredStandardizedDeviations()[0];a.ratios()[0]=999;a.ratioCovariance()[0][0]=999;a.deviationCovariance()[0][0]=999;a.deviationCorrelation()[0][0]=999;a.squaredStandardizedDeviations()[0]=999;assertEquals(b,a.ratios()[0]);assertEquals(v,a.ratioCovariance()[0][0]);assertEquals(d,a.deviationCovariance()[0][0]);assertEquals(r,a.deviationCorrelation()[0][0]);assertEquals(q,a.squaredStandardizedDeviations()[0]);}
    @Test void invalidOrDegenerateEstimatesProduceNoNativeResult(){assertThrows(IllegalArgumentException.class,()->SmrStatistics.ratio(0,.1,.2,.1));assertThrows(IllegalArgumentException.class,()->SmrStatistics.ratio(.2,0,.2,.1));assertThrows(IllegalArgumentException.class,()->SmrStatistics.ratio(.2,.1,Double.NaN,.1));assertThrows(IllegalArgumentException.class,()->SmrStatistics.ratio(.2,.1,.2,.1,1.01));assertThrows(IllegalArgumentException.class,()->SmrStatistics.ratio(.2,.02,.1,.01,1));assertThrows(IllegalArgumentException.class,()->SmrStatistics.ratio(1e-300,1e-300,1,.1));}
    @Test void incompleteAsymmetricOrNonfiniteLDIsRefused(){double[] b={.4,.3},s={.02,.02},y={.1,.2};assertThrows(IllegalArgumentException.class,()->SmrStatistics.deviationKernel(b,s,y,s,new double[][]{{1,.5},{.4,1}},0));assertThrows(IllegalArgumentException.class,()->SmrStatistics.deviationKernel(b,s,y,s,new double[][]{{1,Double.NaN},{Double.NaN,1}},0));assertThrows(IllegalArgumentException.class,()->SmrStatistics.deviationKernel(b,s,y,s,new double[][]{{1},{.4,1}},0));assertThrows(IllegalArgumentException.class,()->SmrStatistics.deviationKernel(b,s,y,s,new double[][]{{1,.4},{.4,.9}},0));}
    @Test void negativeDeviationVarianceIsNeverClamped(){double[] b={.01,.02},s={.1,.1},y={.2,.3};var e=assertThrows(IllegalArgumentException.class,()->SmrStatistics.deviationKernel(b,s,y,s,new double[][]{{1,.5},{.5,1}},0));assertTrue(e.getMessage().contains("Nonpositive HEIDI deviation variance"));}
}
