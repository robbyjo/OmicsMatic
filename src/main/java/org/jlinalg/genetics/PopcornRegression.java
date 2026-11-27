/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Source rules: Brielin C Brown's Popcorn, declared GNU GPL v3.
 * Original revisions: 3c21c41f (CLI1.0), 20a29e8c (CLI1.1). */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import jdistlib.ChiSquare;
import jdistlib.accelerator.ComputeBackend;

/**
 * Original Popcorn cross-population regression and block-jackknife rules.
 * Input rows are already parsed, allele-aligned and filtered. This numerical
 * API does not infer LD-score mode, sample preparation, overlap or an author
 * recipe. Score computation, file harmonization, MLE and full workflow admission
 * are separate. Source estimates are never clamped to biological bounds.
 */
public final class PopcornRegression {
    private PopcornRegression() { }
    public enum Version { CLI_1_0_3C21C41F, CLI_1_1_20A29E8C }

    /** One complete aligned row; score mode is supplied by the enclosing workflow. */
    public record Row(double n1,double n2,double z1,double z2,
            double score1,double score2,double crossScore) {
        public Row {
            for(double x:new double[]{n1,n2,z1,z2,score1,score2,crossScore})
                if(!Double.isFinite(x))throw new IllegalArgumentException("Finite complete Popcorn regression row required");
            if(!(n1>0&&n2>0))throw new IllegalArgumentException("Positive actual per-SNP sample sizes required");
        }
    }
    /** Source point estimates and fitted WLS intercepts, without biological clipping. */
    public record Point(double heritability1,double heritability2,double geneticCorrelation,
            double geneticCovariance,double intercept1,double intercept2,double crossIntercept) {
        public double[] values(){return new double[]{heritability1,heritability2,geneticCorrelation};}
    }
    /** Indices refer to retained input order, not to equal genomic-length intervals. */
    public record Block(int fromInclusive,int toExclusive) { }
    public record Result(Version version,int totalScoreRows,int retainedRows,Point point,
            List<Block> blocks,double[][] deleteValues,double[][] sourcePseudoValues,
            double[][] covariance,double[] standardErrors,double[] sourceZ,double[] sourceP) {
        public Result {
            blocks=List.copyOf(blocks);deleteValues=copy(deleteValues);sourcePseudoValues=copy(sourcePseudoValues);
            covariance=copy(covariance);standardErrors=standardErrors.clone();sourceZ=sourceZ.clone();sourceP=sourceP.clone();
        }
        @Override public double[][] deleteValues(){return copy(deleteValues);}
        @Override public double[][] sourcePseudoValues(){return copy(sourcePseudoValues);}
        @Override public double[][] covariance(){return copy(covariance);}
        @Override public double[] standardErrors(){return standardErrors.clone();}
        @Override public double[] sourceZ(){return sourceZ.clone();}
        @Override public double[] sourceP(){return sourceP.clone();}
    }
    /** Reproduce a source error rather than silently replacing its model or repairing it. */
    public static final class SourceFailure extends IllegalArgumentException {
        private static final long serialVersionUID=1L;
        public SourceFailure(String message){super(message);}
    }

    /**
     * Default source regression with intercepts and optional original jackknife.
     * M is the score-file row count before summary-statistic merging/filtering;
     * it remains fixed in every deleted-block fit. Full source1.1 fits reject
     * nonpositive heritability. Source1.0 can retain unreportable NaN estimates.
     */
    public static Result fit(List<Row> aligned,int totalScoreRows,Version version,
            boolean jackknife,ComputeBackend backend,Runnable check) {
        Objects.requireNonNull(version);Objects.requireNonNull(backend);Objects.requireNonNull(check);
        List<Row> rows=List.copyOf(aligned);
        if(rows.size()<2||totalScoreRows<rows.size())throw new IllegalArgumentException("Complete aligned rows and their original score-file M required");
        Point point=point(rows,totalScoreRows,backend,check);
        if(version==Version.CLI_1_1_20A29E8C&&(point.heritability1<=0||point.heritability2<=0))
            throw new SourceFailure("Original Popcorn1.1 rejects nonpositive full-fit heritability; retain estimates/inputs for investigation, without clipping or substituting a model");
        List<Block> blocks=jackknife?blocks(rows.size()):List.of();
        double[][] deleted=new double[blocks.size()][3],pseudo=new double[blocks.size()][3];double[] full=point.values();
        for(int b=0;b<blocks.size();b++) {
            check.run();Block block=blocks.get(b);var retained=new ArrayList<Row>(rows.size()-(block.toExclusive-block.fromInclusive));
            for(int i=0;i<rows.size();i++){check.run();if(i<block.fromInclusive||i>=block.toExclusive)retained.add(rows.get(i));}
            // Original deleted-block calls bypass the1.1 constructor's h2 check.
            deleted[b]=point(retained,totalScoreRows,backend,check).values();
            for(int j=0;j<3;j++)pseudo[b][j]=blocks.size()*deleted[b][j]-(blocks.size()-1.0)*full[j];
        }
        double[][] covariance=maskedCovariance(pseudo,check);double[] errors=new double[3],z=new double[3],p=new double[3];
        for(int j=0;j<3;j++) {
            int valid=0;for(double[] row:pseudo)if(!Double.isNaN(row[j]))valid++;
            errors[j]=valid>0?Math.sqrt(covariance[j][j]/valid):Double.NaN;
            z[j]=(j==2?1.0-full[j]:full[j])/errors[j];
            // Preserve source 1-CDF rounding/underflow and its H0:rho=1 test.
            p[j]=1.0-ChiSquare.cumulative(z[j]*z[j],1.0,true,false);
        }
        return new Result(version,totalScoreRows,rows.size(),point,blocks,deleted,pseudo,covariance,errors,z,p);
    }

    /** Original adaptive10..200 block count, with all remainder rows retained. */
    public static List<Block> blocks(int rows) {
        if(rows<1)throw new IllegalArgumentException("Positive retained row population required");
        int number=(int)Math.min(200.0,Math.max(10.0,rows/200.0));var result=new ArrayList<Block>(number);
        for(int i=0;i<number;i++)result.add(new Block((int)((long)i*rows/number),(int)((long)(i+1)*rows/number)));
        return List.copyOf(result);
    }

    private static Point point(List<Row> rows,int m,ComputeBackend backend,Runnable check) {
        int n=rows.size();if(n==0)throw new SourceFailure("Original deleted-block regression has no rows");
        double[] x1=new double[n],x2=new double[n],xc=new double[n],y1=new double[n],y2=new double[n],yc=new double[n],w1=new double[n],w2=new double[n],wc=new double[n];
        for(int i=0;i<n;i++) {
            check.run();Row row=rows.get(i);x1[i]=row.score1*(row.n1/m);x2[i]=row.score2*(row.n2/m);xc[i]=row.crossScore*Math.sqrt(row.n1*row.n2)/m;
            y1[i]=row.z1*row.z1;y2[i]=row.z2*row.z2;yc[i]=row.z1*row.z2;
            w1[i]=1.0/Math.max(row.score1,1.0);w2[i]=1.0/Math.max(row.score2,1.0);wc[i]=1.0/Math.max(row.crossScore,1.0);
        }
        double[] h1=wls(x1,y1,w1,backend,check),h2=wls(x2,y2,w2,backend,check);
        boolean constant=xc[0]!=0.0;for(int i=1;i<n;i++)constant&=xc[i]==xc[0];
        if(constant)throw new SourceFailure("Original statsmodels.add_constant skips a nonzero constant cross-score design; Popcorn then indexes a missing slope. No repaired regression is substituted");
        double[] cross=wls(xc,yc,wc,backend,check);double root=Math.sqrt(h1[1]*h2[1]),rho=cross[1]/root;
        if(h1[1]==0.0||h2[1]==0.0)rho=Double.NaN;
        return new Point(h1[1],h2[1],rho,rho*root,h1[0],h2[0],cross[0]);
    }

    private static double[] wls(double[] x,double[] y,double[] weights,ComputeBackend backend,Runnable check) {
        int n=x.length;double[] design=new double[Math.multiplyExact(n,2)],response=new double[n];
        for(int i=0;i<n;i++){check.run();double root=Math.sqrt(weights[i]);design[2*i]=root;design[2*i+1]=root*x[i];response[i]=root*y[i];}
        var svd=backend.dgesvd(design,n,2);int components=svd.components();double[] singular=svd.singularValues(),u=svd.leftSingularVectors(),vt=svd.rightSingularVectorsTransposed(),beta=new double[2];
        // statsmodels pinv_extended uses rcond=1e-15 in original coordinates.
        double threshold=1e-15*singular[0];
        for(int j=0;j<components;j++)if(singular[j]>threshold) {
            double projection=0.0;for(int i=0;i<n;i++){check.run();projection+=u[i*components+j]*response[i];}projection/=singular[j];
            for(int column=0;column<2;column++)beta[column]+=vt[j*2+column]*projection;
        }
        return beta;
    }
    private static double[][] maskedCovariance(double[][] values,Runnable check) {
        double[] means=new double[3];int[] counts=new int[3];
        for(double[] row:values)for(int j=0;j<3;j++)if(!Double.isNaN(row[j])){means[j]+=row[j];counts[j]++;}
        for(int j=0;j<3;j++)means[j]/=counts[j];double[][] result=new double[3][3];
        for(int i=0;i<3;i++)for(int j=0;j<3;j++) {
            double sum=0;int count=0;
            for(double[] row:values){check.run();if(!Double.isNaN(row[i])&&!Double.isNaN(row[j])){sum+=(row[i]-means[i])*(row[j]-means[j]);count++;}}
            result[i][j]=count>1?sum/(count-1.0):Double.NaN;
        }
        return result;
    }
    private static double[][] copy(double[][] value){double[][] result=new double[value.length][];for(int i=0;i<value.length;i++)result[i]=value[i].clone();return result;}
}
