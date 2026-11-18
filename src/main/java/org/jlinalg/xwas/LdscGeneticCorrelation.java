/* Copyright (C) 2026 JLinAlg contributors
 * Based on LDSC regression/IRWLS/jackknife algorithms, Copyright (C) 2014-2015
 * Brendan Bulik-Sullivan and Hilary Finucane. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.xwas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jdistlib.Normal;
import static org.jlinalg.xwas.LdscHeritability.*;

/** Prepared, unpartitioned observed-scale LDSC RG/Gencov, selected CBIIT3.0.2 core. */
public final class LdscGeneticCorrelation {
    public record WeightUpdate(String stage,int update,double estimatedCovariance,
            double weightingCovariance,double weightingHsq1,double weightingHsq2,double intercept) { }
    public record NormalTest(double z,double p,double logP) { }
    public record CovarianceFit(double covariance,double standardError,double coefficient,
            double coefficientStandardError,double intercept,Double interceptStandardError,
            double meanZProduct,double meanGeometricSampleSize,NormalTest normalTest,
            int step1Rows,int[] step1Separators,int[] fullSeparators,double[][] deleteValues,
            double[][] parameterCovariance,double[] finalWeights,List<WeightUpdate> weightUpdates) {
        public CovarianceFit {
            step1Separators=step1Separators.clone();fullSeparators=fullSeparators.clone();
            deleteValues=copy(deleteValues);parameterCovariance=copy(parameterCovariance);
            finalWeights=finalWeights.clone();weightUpdates=List.copyOf(weightUpdates);
        }
        @Override public int[] step1Separators(){return step1Separators.clone();}
        @Override public int[] fullSeparators(){return fullSeparators.clone();}
        @Override public double[][] deleteValues(){return copy(deleteValues);}
        @Override public double[][] parameterCovariance(){return copy(parameterCovariance);}
        @Override public double[] finalWeights(){return finalWeights.clone();}
    }
    /** Null correlation diagnostics mean original NA; NaN retains original nonfinite uncertainty. */
    public record Result(LdscHeritability.Result trait1,LdscHeritability.Result trait2,
            CovarianceFit geneticCovariance,Double correlation,Double correlationStandardError,
            Double biasCorrectedCorrelation,NormalTest correlationTest,double[] deleteRatios,
            String correlationStatus,int negativeTrait1Deletes,int negativeTrait2Deletes,
            boolean sourceSummaryOutOfRange) {
        public Result {deleteRatios=deleteRatios.clone();}
        @Override public double[] deleteRatios(){return deleteRatios.clone();}
        public boolean finiteCorrelationInference(){return correlationStatus.equals("FINITE_RAW_INFERENCE");}
    }
    private LdscGeneticCorrelation() { }
    private static double[][] copy(double[][] x){return Arrays.stream(x).map(double[]::clone).toArray(double[][]::new);}
    /**
     * Both signed Z vectors must already refer to the same effect allele and complete,
     * genome-ordered SNP intersection. Each two-step Hsq mask is trait-specific;
     * the covariance mask is their strict-cutoff intersection. Original RG pairs
     * their delete estimates by block ordinal, even when mapped row boundaries differ.
     */
    public static Result fit(double[] z1,double[] z2,double[] ld,double[] weightLd,double[] n1,double[] n2,
            double referenceVariants,int blocks,Double interceptHsq1,Double interceptHsq2,
            Double interceptCovariance,Double twoStep) {
        if(z1==null||z2==null||ld==null||weightLd==null||n1==null||n2==null)
            throw new IllegalArgumentException("Complete aligned arrays required");
        int rows=z1.length;
        if(rows<3||rows>MAXIMUM_ROWS||z2.length!=rows||ld.length!=rows||weightLd.length!=rows||n1.length!=rows||n2.length!=rows
                ||!(referenceVariants>0)||!Double.isFinite(referenceVariants)||blocks<2||blocks>Math.min(rows,MAXIMUM_BLOCKS))
            throw new IllegalArgumentException("Matching complete arrays, positive M and valid block count required");
        if(interceptCovariance!=null&&!Double.isFinite(interceptCovariance)
                ||twoStep!=null&&(interceptHsq1!=null||interceptHsq2!=null||interceptCovariance!=null))
            throw new IllegalArgumentException("Two-step requires all three intercepts free; fixed covariance intercept must be finite");
        z1=z1.clone();z2=z2.clone();ld=ld.clone();weightLd=weightLd.clone();n1=n1.clone();n2=n2.clone();
        double[] chi1=new double[rows],chi2=new double[rows],y=new double[rows],geometricN=new double[rows];
        var sy=new Sum();var sn=new Sum();var snld=new Sum();
        for(int i=0;i<rows;i++) {
            finite(z1[i]);finite(z2[i]);finite(ld[i]);finite(weightLd[i]);positive(n1[i]);positive(n2[i]);
            chi1[i]=finite(z1[i]*z1[i]);chi2[i]=finite(z2[i]*z2[i]);y[i]=finite(z1[i]*z2[i]);
            geometricN[i]=positive(Math.sqrt(finite(n1[i]*n2[i])));sy.add(y[i]/rows);sn.add(geometricN[i]/rows);snld.add(finite(geometricN[i]*ld[i])/rows);
        }
        var h1=LdscHeritability.fit(chi1,ld,weightLd,n1,referenceVariants,blocks,interceptHsq1,twoStep);
        var h2=LdscHeritability.fit(chi2,ld,weightLd,n2,referenceVariants,blocks,interceptHsq2,twoStep);
        double nbar=positive(sn.value()),initialIntercept=interceptCovariance==null?0:interceptCovariance;
        double initialCov=finite(referenceVariants*(sy.value()-initialIntercept)/snld.value());
        var updates=new ArrayList<WeightUpdate>();updates.add(update("initial",-1,initialCov,h1.h2(),h2.h2(),initialIntercept));
        double[] initial=weights(ld,weightLd,n1,n2,referenceVariants,h1,h2,initialCov,initialIntercept);
        double[] x=new double[rows];for(int i=0;i<rows;i++)x[i]=finite(geometricN[i]*ld[i]/nbar);
        Fit fitted;int step1Count=rows;int[] firstSeparators=new int[0];
        if(twoStep==null) {
            double[] response=y.clone();if(interceptCovariance!=null)for(int i=0;i<rows;i++)response[i]=finite(y[i]-interceptCovariance);
            fitted=irwls(x,response,ld,weightLd,n1,n2,referenceVariants,nbar,initial,separators(rows,blocks),interceptCovariance,h1,h2,updates,"one-step");
        } else {
            int[] selected=new int[rows];step1Count=0;
            for(int i=0;i<rows;i++)if(chi1[i]<twoStep&&chi2[i]<twoStep)selected[step1Count++]=i;
            if(step1Count<blocks)throw new IllegalArgumentException("Joint two-step intercept population has fewer rows than declared blocks");
            selected=Arrays.copyOf(selected,step1Count);firstSeparators=separators(step1Count,blocks);
            // Preserve the original first-stage N-scaled LD column, as in Hsq.
            Fit first=irwls(select(x,selected),select(y,selected),select(x,selected),select(weightLd,selected),select(n1,selected),select(n2,selected),
                referenceVariants,nbar,select(initial,selected),firstSeparators,null,h1,h2,updates,"two-step-intercept");
            int[] mapped=new int[blocks+1];mapped[blocks]=rows;for(int i=1;i<blocks;i++)mapped[i]=selected[firstSeparators[i]];
            double[] response=y.clone();for(int i=0;i<rows;i++)response[i]=finite(y[i]-first.intercept());
            Fit second=irwls(x,response,ld,weightLd,n1,n2,referenceVariants,nbar,initial,mapped,first.intercept(),h1,h2,updates,"two-step-slope");
            var numerator=new Sum();var denominator=new Sum();double maximum=Arrays.stream(initial).max().orElseThrow();
            for(int i=0;i<rows;i++){double w=positive(initial[i]/maximum);numerator.add(finite(w*x[i]));denominator.add(finite(w*x[i]*x[i]));}
            double correction=finite(numerator.value()/positive(denominator.value()));double[][] deletes=new double[blocks][2];
            for(int i=0;i<blocks;i++){deletes[i][0]=finite(second.deletes()[i][0]-correction*(first.deletes()[i][1]-first.intercept()));deletes[i][1]=first.deletes()[i][1];}
            fitted=new Fit(second.slope(),first.intercept(),deletes,mapped,second.weights());
        }
        double scale=positive(referenceVariants/nbar),gc=finite(fitted.slope()*scale);double[][] deletes=new double[blocks][2];
        for(int i=0;i<blocks;i++){deletes[i][0]=finite(fitted.deletes()[i][0]*scale);deletes[i][1]=fitted.deletes()[i][1];}
        if(gc==0||Arrays.stream(deletes).anyMatch(a->a[0]==0))throw new ArithmeticException("Original Gencov category-proportion diagnostic is undefined at zero full/delete covariance; no result admitted");
        double[][] covariance=covariance(deletes);double gcSe=finite(Math.sqrt(covariance[0][0]));
        var component=new CovarianceFit(gc,gcSe,finite(fitted.slope()/nbar),finite(gcSe/referenceVariants),fitted.intercept(),
            interceptCovariance==null?finite(Math.sqrt(covariance[1][1])):null,sy.value(),nbar,normalTest(gc,gcSe),
            step1Count,firstSeparators,fitted.separators(),deletes,covariance,fitted.weights(),updates);
        double[][] d1=h1.deleteValues(),d2=h2.deleteValues();int negative1=0,negative2=0;
        for(int i=0;i<blocks;i++){if(d1[i][0]<0)negative1++;if(d2[i][0]<0)negative2++;}
        if(h1.h2()<=0||h2.h2()<=0)return new Result(h1,h2,component,null,null,null,null,new double[0],"NONPOSITIVE_HERITABILITY",negative1,negative2,false);
        double ratio=finite(gc/positive(Math.sqrt(finite(h1.h2()*h2.h2()))));
        double[] dr=new double[blocks];boolean nan=false;var sum=new Sum();
        for(int i=0;i<blocks;i++) {
            double product=finite(d1[i][0]*d2[i][0]);
            if(product<0){dr[i]=Double.NaN;nan=true;}
            else {dr[i]=finite(deletes[i][0]/positive(Math.sqrt(product)));sum.add(dr[i]/blocks);}
        }
        if(nan)return new Result(h1,h2,component,ratio,Double.NaN,Double.NaN,new NormalTest(Double.NaN,Double.NaN,Double.NaN),dr,"NONFINITE_DELETE_UNCERTAINTY",negative1,negative2,Math.abs(ratio)>1.2);
        double mean=sum.value();var squares=new Sum();for(double value:dr)squares.add(finite((value-mean)*(value-mean)));
        double se=finite(Math.sqrt(squares.value()*((blocks-1.0)/blocks)));
        double corrected=finite(blocks*ratio-(blocks-1.0)*mean);
        return new Result(h1,h2,component,ratio,se,corrected,normalTest(ratio,se),dr,"FINITE_RAW_INFERENCE",negative1,negative2,Math.abs(ratio)>1.2);
    }
    private static WeightUpdate update(String stage,int iteration,double gc,double h1,double h2,double intercept){return new WeightUpdate(stage,iteration,gc,Math.max(-1,Math.min(1,gc)),Math.max(0,Math.min(1,h1)),Math.max(0,Math.min(1,h2)),intercept);}
    private static double[] weights(double[] ld,double[] wld,double[] n1,double[] n2,double m,
            LdscHeritability.Result h1,LdscHeritability.Result h2,double gc,double intercept) {
        double bh1=Math.max(0,Math.min(1,h1.h2())),bh2=Math.max(0,Math.min(1,h2.h2())),bg=Math.max(-1,Math.min(1,gc));
        double[] w=new double[ld.length];for(int i=0;i<w.length;i++) {
            double l=Math.max(1,ld[i]);double a=finite(n1[i]*(bh1*l)/m+h1.intercept()),b=finite(n2[i]*(bh2*l)/m+h2.intercept());
            double c=finite(Math.sqrt(finite(n1[i]*n2[i]))*(bg*l)/m+intercept);
            w[i]=positive((1/finite(a*b+c*c))*(1/Math.max(1,wld[i])));
        }
        return w;
    }
    private static Fit irwls(double[] x,double[] y,double[] ld,double[] wld,double[] n1,double[] n2,double m,double nbar,
            double[] initial,int[] boundaries,Double fixed,LdscHeritability.Result h1,LdscHeritability.Result h2,List<WeightUpdate> trace,String stage) {
        double[] w=initial.clone();for(int iteration=0;iteration<2;iteration++) {
            double[] estimate=fitMoments(moments(x,y,w,0,x.length),fixed);double gc=finite(m*estimate[0]/nbar),intercept=fixed==null?estimate[1]:fixed;
            trace.add(update(stage,iteration,gc,h1.h2(),h2.h2(),intercept));w=weights(ld,wld,n1,n2,m,h1,h2,gc,intercept);
        }
        return finish(x,y,w,boundaries,fixed);
    }
    /** Original zero-SE convention uses +Inf Z even for a negative estimate. */
    static NormalTest normalTest(double estimate,double se) {
        if(Double.isNaN(se))return new NormalTest(Double.NaN,Double.NaN,Double.NaN);
        if(se<0||!Double.isFinite(se)||!Double.isFinite(estimate))throw new ArithmeticException("Unrepresentable LDSC normal test");
        double z=se==0?Double.POSITIVE_INFINITY:estimate/se;
        double logP=Math.log(2)+Normal.cumulative(Math.abs(z),0,1,false,true);
        if(Double.isNaN(logP)||logP>0)throw new ArithmeticException("Unresolved LDSC normal tail");
        return new NormalTest(z,Math.exp(logP),logP);
    }
}
