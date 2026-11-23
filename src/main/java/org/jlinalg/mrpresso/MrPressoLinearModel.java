/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Householder convention follows the project's source-qualified R lm profile.
 * The stable norm follows Reference BLAS Algorithm978; retain
 * LICENSES/LAPACK-3.12.1-BSD.txt when distributing this component. */
package org.jlinalg.mrpresso;

import java.util.Arrays;
import jdistlib.T;

/** Owned no-intercept weighted Householder fit for the original PRESSO workflow. */
final class MrPressoLinearModel {
    private MrPressoLinearModel() { }
    record Fit(int rank,double[] beta,double[] standardError,double[] statistic,double[] pValue,
               double residualVariance,int residualDf,boolean essentiallyPerfect) { }

    static Fit fit(double[][] x,double[] y,double[] weights,int[] rows) {
        int n=rows.length,p=x[0].length,limit=Math.min(n,p);
        double[][] a=new double[p][n];double[] qty=new double[n],roots=new double[n],initial=new double[p],auxiliary=new double[p];
        int[] pivot=new int[p];for(int j=0;j<p;j++)pivot[j]=j;
        for(int i=0;i<n;i++){
            int r=rows[i];roots[i]=Math.sqrt(weights[r]);qty[i]=roots[i]*y[r];
            for(int j=0;j<p;j++)a[j][i]=roots[i]*x[r][j];
        }
        for(int j=0;j<p;j++)initial[j]=norm(a[j],0);
        int rank=0,active=p;
        for(int j=0;j<limit;j++){
            while(j<active){
                double length=norm(a[j],j);
                if(length!=0&&length>=initial[j]*1e-7)break;
                double[] moved=a[j];double movedNorm=initial[j];int movedIndex=pivot[j];
                for(int k=j;k<p-1;k++){a[k]=a[k+1];initial[k]=initial[k+1];pivot[k]=pivot[k+1];}
                a[p-1]=moved;initial[p-1]=movedNorm;pivot[p-1]=movedIndex;active--;
            }
            if(j>=active)break;
            double length=norm(a[j],j);if(a[j][j]!=0)length=Math.copySign(length,a[j][j]);
            double inverse=1/length;for(int i=j;i<n;i++)a[j][i]*=inverse;a[j][j]+=1;
            for(int k=j+1;k<p;k++)reflect(a[j],a[j][j],j,a[k]);
            auxiliary[j]=a[j][j];a[j][j]=-length;rank++;
        }
        for(int j=0;j<rank;j++)reflect(a[j],auxiliary[j],j,qty);
        double[] coefficients=Arrays.copyOf(qty,rank);
        for(int j=rank-1;j>=0;j--){
            coefficients[j]/=a[j][j];for(int i=0;i<j;i++)coefficients[i]-=coefficients[j]*a[j][i];
        }
        double[] residual=qty.clone();Arrays.fill(residual,0,rank,0);
        for(int j=rank-1;j>=0;j--)reflect(a[j],auxiliary[j],j,residual);
        double rss=0,weightedResidualNorm=0,weightedFittedNorm=0;
        for(int i=0;i<n;i++){
            double unweighted=residual[i]/roots[i];rss+=weights[rows[i]]*(unweighted*unweighted);
            double fitted=y[rows[i]]-unweighted;
            weightedResidualNorm=Math.hypot(weightedResidualNorm,residual[i]);
            weightedFittedNorm=Math.hypot(weightedFittedNorm,roots[i]*fitted);
        }
        int df=n-rank;double variance=rss/df;
        double[][] inverse=new double[rank][rank];
        for(int column=0;column<rank;column++)for(int row=rank-1;row>=0;row--){
            double value=row==column?1:0;for(int k=row+1;k<rank;k++)value-=a[k][row]*inverse[k][column];
            inverse[row][column]=value/a[row][row];
        }
        double[] beta=new double[p],se=new double[p],statistic=new double[p],probability=new double[p];
        Arrays.fill(beta,Double.NaN);Arrays.fill(se,Double.NaN);Arrays.fill(statistic,Double.NaN);Arrays.fill(probability,Double.NaN);
        for(int j=0;j<rank;j++){
            int target=pivot[j];double diagonal=0;for(int k=0;k<rank;k++)diagonal+=inverse[j][k]*inverse[j][k];
            beta[target]=coefficients[j];se[target]=Math.sqrt(diagonal*variance);statistic[target]=beta[target]/se[target];
            probability[target]=df>0?2*T.cumulative(Math.abs(statistic[target]),df,false,false):Double.NaN;
        }
        // A native admission guard, not a claim about R's warning text. Compare
        // norms in the same weighted response units; mixing weighted residual
        // variance with unweighted fitted variance can miss perfect fits.
        boolean essentiallyPerfect=Double.isFinite(weightedResidualNorm)&&Double.isFinite(weightedFittedNorm)
            &&weightedFittedNorm>0&&weightedResidualNorm/weightedFittedNorm<=1e-15;
        return new Fit(rank,beta,se,statistic,probability,variance,df,essentiallyPerfect);
    }

    private static void reflect(double[] column,double auxiliary,int start,double[] vector) {
        double dot=auxiliary*vector[start];for(int i=start+1;i<vector.length;i++)dot+=column[i]*vector[i];
        double factor=-dot/auxiliary;vector[start]+=factor*auxiliary;for(int i=start+1;i<vector.length;i++)vector[i]+=factor*column[i];
    }

    private static double norm(double[] x,int start) {
        double small=Math.scalb(1.0,-511),big=Math.scalb(1.0,486),scaleSmall=Math.scalb(1.0,537),scaleBig=Math.scalb(1.0,-538);
        double low=0,middle=0,high=0;boolean noHigh=true;
        for(int i=start;i<x.length;i++){
            double value=Math.abs(x[i]);
            if(value>big){double scaled=value*scaleBig;high+=scaled*scaled;noHigh=false;}
            else if(value<small){if(noHigh){double scaled=value*scaleSmall;low+=scaled*scaled;}}
            else middle+=value*value;
        }
        if(high>0){if(middle>0||Double.isNaN(middle))high+=(middle*scaleBig)*scaleBig;return Math.sqrt(high)/scaleBig;}
        if(low>0){
            if(middle>0||Double.isNaN(middle))return Math.hypot(Math.sqrt(middle),Math.sqrt(low)/scaleSmall);
            return Math.sqrt(low)/scaleSmall;
        }
        return Math.sqrt(middle);
    }
}
