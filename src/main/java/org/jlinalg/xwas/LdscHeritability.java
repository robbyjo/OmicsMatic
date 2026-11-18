/* Copyright (C) 2026 JLinAlg contributors
 * Based on LDSC regression/IRWLS/jackknife algorithms, Copyright (C) 2014-2015
 * Brendan Bulik-Sullivan and Hilary Finucane. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.xwas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Prepared, unpartitioned observed-scale LDSC Hsq, including the original two-step estimator.
 * Rows must already be QC-filtered, aligned and genome-ordered. No row is silently removed.
 * The selected reference is CBIIT/ldsc 3.0.2 core commit 6c673952cee74bd5c57aef1555a03b1c015399a0.
 */
public final class LdscHeritability {
    public static final int MAXIMUM_ROWS=2_000_000, MAXIMUM_BLOCKS=10_000;
    /** Weight feedback clamps h2 to [0,1]; final h2 is never clamped. update=-1 is initialization. */
    public record WeightUpdate(String stage,int update,double estimatedH2,double weightingH2,double intercept) { }
    /** Covariance/deletes use axes (observed h2, intercept). Null diagnostics mean original NA. */
    public record Result(int rows,int blocks,double referenceVariants,double meanSampleSize,
            double h2,double h2StandardError,double coefficient,double coefficientStandardError,
            double intercept,Double interceptStandardError,double meanChiSquare,double lambdaGc,
            Double ratio,Double ratioStandardError,int step1Rows,int[] step1Separators,
            int[] fullSeparators,double[][] deleteValues,double[][] covariance,
            double[] finalWeights,List<WeightUpdate> weightUpdates) {
        public Result {
            step1Separators=step1Separators.clone();fullSeparators=fullSeparators.clone();
            deleteValues=copy(deleteValues);covariance=copy(covariance);finalWeights=finalWeights.clone();
            weightUpdates=List.copyOf(weightUpdates);
        }
        @Override public int[] step1Separators(){return step1Separators.clone();}
        @Override public int[] fullSeparators(){return fullSeparators.clone();}
        @Override public double[][] deleteValues(){return copy(deleteValues);}
        @Override public double[][] covariance(){return copy(covariance);}
        @Override public double[] finalWeights(){return finalWeights.clone();}
        private static double[][] copy(double[][] x){return Arrays.stream(x).map(double[]::clone).toArray(double[][]::new);}
    }
    private record Fit(double slope,double intercept,double[][] deletes,int[] separators,double[] weights) { }
    private LdscHeritability() { }

    /**
     * A null fixedIntercept means estimate the intercept. A nonnull twoStep is the
     * strict chi-square cutoff for estimating that intercept; all rows return for
     * the second-stage slope. Fixed-intercept and two-step are mutually exclusive.
     * Exactly two IRWLS weight updates precede the final fit; block deletes retain
     * the final whole-stage weights. Reference M is not the regression row count.
     */
    public static Result fit(double[] chiSquare,double[] ldScore,double[] weightLdScore,
            double[] sampleSize,double referenceVariants,int blocks,Double fixedIntercept,Double twoStep) {
        if(chiSquare==null||ldScore==null||weightLdScore==null||sampleSize==null)
            throw new IllegalArgumentException("Complete prepared arrays required");
        int rows=chiSquare.length;
        if(rows<3||rows>MAXIMUM_ROWS||ldScore.length!=rows||weightLdScore.length!=rows||sampleSize.length!=rows
                ||blocks<2||blocks>Math.min(rows,MAXIMUM_BLOCKS)||!(referenceVariants>0)||!Double.isFinite(referenceVariants))
            throw new IllegalArgumentException("Matching 3..2million rows, 2..10000 blocks and positive finite reference M required");
        if(fixedIntercept!=null&&!Double.isFinite(fixedIntercept)||twoStep!=null&&(!(twoStep>0)||!Double.isFinite(twoStep))
                ||fixedIntercept!=null&&twoStep!=null)
            throw new IllegalArgumentException("Declare a finite fixed intercept OR a positive two-step cutoff, never both");
        double[] y=chiSquare.clone(),ld=ldScore.clone(),wld=weightLdScore.clone(),ns=sampleSize.clone();
        var sumY=new Sum();var sumN=new Sum();var sumNx=new Sum();
        for(int i=0;i<rows;i++) {
            if(!Double.isFinite(y[i])||y[i]<0||!Double.isFinite(ld[i])||!Double.isFinite(wld[i])||!(ns[i]>0)||!Double.isFinite(ns[i]))
                throw new IllegalArgumentException("Finite chi-square>=0, finite LD scores and positive finite per-row N required; no row removal");
            sumY.add(y[i]/rows);sumN.add(ns[i]/rows);sumNx.add(finite(ns[i]*ld[i])/rows);
        }
        double nbar=positive(sumN.value()),meanY=finite(sumY.value());
        double initialIntercept=fixedIntercept==null?1:fixedIntercept;
        double initialH2=finite(referenceVariants*(meanY-initialIntercept)/sumNx.value());
        var updates=new ArrayList<WeightUpdate>();
        double[] initialWeights=weights(ld,wld,ns,referenceVariants,initialH2,initialIntercept);
        updates.add(update("initial",-1,initialH2,initialIntercept));
        double[] x=new double[rows];for(int i=0;i<rows;i++)x[i]=finite(ns[i]*ld[i]/nbar);
        Fit fitted;int step1Count=rows;int[] firstSeparators=new int[0];
        if(twoStep==null) {
            double[] response=y.clone();if(fixedIntercept!=null)for(int i=0;i<rows;i++)response[i]=finite(y[i]-fixedIntercept);
            fitted=irwls(x,response,ld,wld,ns,referenceVariants,nbar,initialWeights,separators(rows,blocks),fixedIntercept,updates,"one-step");
        } else {
            int[] selected=new int[rows];step1Count=0;
            for(int i=0;i<rows;i++)if(y[i]<twoStep)selected[step1Count++]=i;
            if(step1Count<blocks)throw new IllegalArgumentException("Two-step intercept population has fewer rows than declared blocks");
            selected=Arrays.copyOf(selected,step1Count);firstSeparators=separators(step1Count,blocks);
            // Original Hsq passes the already N-scaled first design column as
            // first-stage LD during weight updates. Preserve that source behavior.
            Fit first=irwls(select(x,selected),select(y,selected),select(x,selected),select(wld,selected),select(ns,selected),
                referenceVariants,nbar,select(initialWeights,selected),firstSeparators,null,updates,"two-step-intercept");
            int[] mapped=new int[blocks+1];mapped[blocks]=rows;
            for(int i=1;i<blocks;i++)mapped[i]=selected[firstSeparators[i]];
            double[] residual=y.clone();for(int i=0;i<rows;i++)residual[i]=finite(y[i]-first.intercept);
            Fit second=irwls(x,residual,ld,wld,ns,referenceVariants,nbar,initialWeights,mapped,first.intercept,updates,"two-step-slope");
            var numerator=new Sum();var denominator=new Sum();
            double max=Arrays.stream(initialWeights).max().orElseThrow();
            for(int i=0;i<rows;i++){double w=positive(initialWeights[i]/max);numerator.add(finite(w*x[i]));denominator.add(finite(w*x[i]*x[i]));}
            double correction=finite(numerator.value()/positive(denominator.value()));
            double[][] deletes=new double[blocks][2];
            for(int i=0;i<blocks;i++) {
                deletes[i][0]=finite(second.deletes[i][0]-correction*(first.deletes[i][1]-first.intercept));
                deletes[i][1]=first.deletes[i][1];
            }
            fitted=new Fit(second.slope,first.intercept,deletes,mapped,second.weights);
        }
        double scale=positive(referenceVariants/nbar),h2=finite(fitted.slope*scale);
        double[][] deletes=new double[blocks][2];
        for(int i=0;i<blocks;i++){deletes[i][0]=finite(fitted.deletes[i][0]*scale);deletes[i][1]=fitted.deletes[i][1];}
        // Hsq's single-category proportion and ratio jackknife divide by these
        // quantities. Preserve the original undefined-diagnostic refusal.
        if(h2==0||Arrays.stream(deletes).anyMatch(a->a[0]==0))
            throw new ArithmeticException("Original Hsq category-proportion diagnostic is undefined for zero full/delete heritability; no result admitted");
        double[][] covariance=covariance(deletes);double h2Se=finite(Math.sqrt(covariance[0][0]));
        Double intSe=fixedIntercept==null?finite(Math.sqrt(covariance[1][1])):null;
        Double ratio=fixedIntercept==null&&meanY>1?finite((fitted.intercept-1)/(meanY-1)):null;
        Double ratioSe=ratio!=null?finite(intSe/(meanY-1)):null;
        double[] ordered=y.clone();Arrays.sort(ordered);
        double median=rows%2==1?ordered[rows/2]:finite(ordered[rows/2-1]/2+ordered[rows/2]/2);
        return new Result(rows,blocks,referenceVariants,nbar,h2,h2Se,finite(fitted.slope/nbar),finite(h2Se/referenceVariants),
            fitted.intercept,intSe,meanY,finite(median/.4549),ratio,ratioSe,step1Count,firstSeparators,
            fitted.separators,deletes,covariance,fitted.weights,updates);
    }
    private static WeightUpdate update(String name,int iteration,double h2,double intercept){return new WeightUpdate(name,iteration,h2,Math.max(0,Math.min(1,h2)),intercept);}
    private static Fit irwls(double[] x,double[] y,double[] weightLd,double[] wld,double[] ns,double m,double nbar,
            double[] initial,int[] separators,Double fixed,List<WeightUpdate> trace,String stage) {
        double[] w=initial.clone();
        for(int iteration=0;iteration<2;iteration++) {
            double[] estimate=fitMoments(moments(x,y,w,0,x.length),fixed);
            double h2=finite(m*estimate[0]/nbar),intercept=fixed==null?estimate[1]:fixed;
            trace.add(update(stage,iteration,h2,intercept));w=weights(weightLd,wld,ns,m,h2,intercept);
        }
        int blocks=separators.length-1;Moments[] pieces=new Moments[blocks],prefix=new Moments[blocks+1],suffix=new Moments[blocks+1];
        double maximum=Arrays.stream(w).max().orElseThrow();double[] relative=new double[w.length];
        for(int i=0;i<w.length;i++)relative[i]=positive(w[i]/maximum);
        prefix[0]=new Moments();suffix[blocks]=new Moments();
        for(int i=0;i<blocks;i++){pieces[i]=momentsRelative(x,y,relative,separators[i],separators[i+1]);prefix[i+1]=merge(prefix[i],pieces[i]);}
        for(int i=blocks-1;i>=0;i--)suffix[i]=merge(pieces[i],suffix[i+1]);
        double[] full=fitMoments(prefix[blocks],fixed);double[][] deleted=new double[blocks][2];
        for(int i=0;i<blocks;i++)deleted[i]=fitMoments(merge(prefix[i],suffix[i+1]),fixed);
        return new Fit(full[0],full[1],deleted,separators.clone(),w);
    }
    private static double[] weights(double[] ld,double[] wld,double[] ns,double m,double h2,double intercept) {
        double bounded=Math.max(0,Math.min(1,h2));double[] w=new double[ld.length];
        for(int i=0;i<w.length;i++) {
            double expected=finite(intercept+bounded*ns[i]/m*Math.max(1,ld[i]));
            w[i]=positive((1/(2*expected*expected))*(1/Math.max(1,wld[i])));
        }
        return w;
    }
    private static int[] separators(int rows,int blocks) {
        int[] result=new int[blocks+1];double step=(double)rows/blocks;
        // Match floor(linspace(0,N,B+1)), including its floating-point placement.
        for(int i=1;i<blocks;i++)result[i]=(int)Math.floor(i*step);result[blocks]=rows;
        return result;
    }
    private static double[] select(double[] x,int[] selected){double[] result=new double[selected.length];for(int i=0;i<result.length;i++)result[i]=x[selected[i]];return result;}
    private static final class Sum {
        double sum,correction;
        void add(double x){double adjusted=x-correction,next=sum+adjusted;correction=(next-sum)-adjusted;sum=finite(next);}
        double value(){return sum;}
    }
    private static final class Moments {
        int count;double weight,mx,my,xx,xy;
        void add(double x,double y,double w) {
            double next=positive(weight+w),dx=finite(x-mx),dy=finite(y-my),factor=finite(w*weight/next);
            xx=finite(xx+factor*dx*dx);xy=finite(xy+factor*dx*dy);
            mx=finite(mx+w/next*dx);my=finite(my+w/next*dy);weight=next;count++;
        }
    }
    private static Moments moments(double[] x,double[] y,double[] w,int from,int to) {
        double maximum=Arrays.stream(w).max().orElseThrow();var result=new Moments();
        for(int i=from;i<to;i++)result.add(x[i],y[i],positive(w[i]/maximum));return result;
    }
    private static Moments momentsRelative(double[] x,double[] y,double[] w,int from,int to) {
        var result=new Moments();for(int i=from;i<to;i++)result.add(x[i],y[i],w[i]);return result;
    }
    private static Moments merge(Moments a,Moments b) {
        var r=new Moments();r.count=a.count+b.count;
        if(a.count==0){r.weight=b.weight;r.mx=b.mx;r.my=b.my;r.xx=b.xx;r.xy=b.xy;return r;}
        if(b.count==0){r.weight=a.weight;r.mx=a.mx;r.my=a.my;r.xx=a.xx;r.xy=a.xy;return r;}
        r.weight=positive(a.weight+b.weight);double dx=finite(b.mx-a.mx),dy=finite(b.my-a.my),factor=finite(a.weight*b.weight/r.weight);
        r.mx=finite(a.mx+b.weight/r.weight*dx);r.my=finite(a.my+b.weight/r.weight*dy);
        r.xx=finite(a.xx+b.xx+factor*dx*dx);r.xy=finite(a.xy+b.xy+factor*dx*dy);return r;
    }
    private static double[] fitMoments(Moments m,Double fixed) {
        double slope,intercept;
        if(fixed==null) {
            if(m.count<2||!(m.xx>0))throw new ArithmeticException("Full/delete LDSC free-intercept design is rank deficient");
            slope=finite(m.xy/m.xx);intercept=finite(m.my-slope*m.mx);
        } else {
            if(m.count<1)throw new ArithmeticException("Empty LDSC delete design");
            double denominator=positive(m.xx+m.weight*m.mx*m.mx);
            slope=finite((m.xy+m.weight*m.mx*m.my)/denominator);intercept=fixed;
        }
        return new double[]{slope,intercept};
    }
    private static double[][] covariance(double[][] deletes) {
        int b=deletes.length;double[] means=new double[2];
        for(int j=0;j<2;j++){var sum=new Sum();for(double[] d:deletes)sum.add(d[j]/b);means[j]=sum.value();}
        double[][] result=new double[2][2];
        for(int j=0;j<2;j++)for(int k=0;k<=j;k++) {
            var sum=new Sum();for(double[] d:deletes)sum.add(finite((d[j]-means[j])*(d[k]-means[k])));
            result[j][k]=result[k][j]=finite(sum.value()*((b-1.0)/b));
        }
        return result;
    }
    private static double finite(double value){if(!Double.isFinite(value))throw new ArithmeticException("Unrepresentable LDSC intermediate/result; no result admitted");return value;}
    private static double positive(double value){finite(value);if(!(value>0))throw new ArithmeticException("Nonpositive/unrepresentable LDSC weight, scale or design; no result admitted");return value;}
}
