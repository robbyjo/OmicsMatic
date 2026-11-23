/*
 * Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Source behavior: EIG 7.2.1 doshrinkp/doproj, 4f7e9f0bf7c85f45ad27d895e1dd7d28a86ec3d1.
 * Original copyright (c) 2006-2016 Broad Institute, Inc. and Harvard Medical
 * School; the BSD notice is retained in org/jlinalg/omics/smartpca-4f7e9f0/.
 */
package org.jlinalg.genetics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import jdistlib.accelerator.ComputeBackend;
import org.jlinalg.compute.BackendContext;
import org.jlinalg.compute.BackendPolicy;

/** Source perturbation shrinkmode, distinct from autoshrink/fastshrink. */
public final class SmartpcaShrinkage {
    private SmartpcaShrinkage() { }
    public record Perturbation(int zeroBasedAxis,int basisOrdinal,double eigenvalue,
            double delta,double multiplier,boolean originalBadDeltaBranch) { }
    /** Projection coefficients precede the original all-sample unit-norm output scaling. */
    public record Result(double[][] scores,double[][] rawProjectionCoefficients,
            double[] eigenvalues,double[][] eigenvectors,List<Perturbation> perturbations) {
        public Result {
            scores=copy(scores);rawProjectionCoefficients=copy(rawProjectionCoefficients);
            eigenvalues=eigenvalues.clone();eigenvectors=copy(eigenvectors);perturbations=List.copyOf(perturbations);
        }
        @Override public double[][] scores(){return copy(scores);}
        @Override public double[][] rawProjectionCoefficients(){return copy(rawProjectionCoefficients);}
        @Override public double[] eigenvalues(){return eigenvalues.clone();}
        @Override public double[][] eigenvectors(){return copy(eigenvectors);}
    }

    /** Prepared hard calls and a fit of those exact calls/options are recomputed together. */
    public static Result fit(byte[][] calls,int[] basis,SmartpcaPrepared.Options options) {
        try(BackendContext context=BackendContext.select(BackendPolicy.CPU)){return fit(calls,basis,options,context.backend());}
    }
    public static Result fit(byte[][] calls,int[] selected,SmartpcaPrepared.Options options,ComputeBackend backend) {
        Objects.requireNonNull(options,"options");Objects.requireNonNull(backend,"backend");
        Objects.requireNonNull(calls,"calls");Objects.requireNonNull(selected,"basis");
        require(calls.length>0&&calls[0]!=null&&calls[0].length>0&&selected.length>=2,"Nonempty prepared shrinkage panel and fitting basis required");
        admit(calls.length,selected.length,options.components(),calls[0].length,options.limits());
        // The primary pass in the original does ordinary projection when shrinkmode
        // is selected. The shrinkage pass itself always solves least squares.
        var primaryOptions=new SmartpcaPrepared.Options(options.components(),options.useNorm(),options.alternateNormStyle(),
            options.minimumAlleleCount(),options.maximumMissing(),SmartpcaPrepared.Projection.ORTHOGONAL,options.limits());
        var primary=SmartpcaPrepared.fit(calls,selected,primaryOptions,backend);
        int[] basis=primary.basisIndices();int n=basis.length,m=calls.length,k=options.components(),samples=calls[0].length;
        var markers=primary.markers();double[][] matrix=new double[n][m];
        for(int s=0;s<m;s++) {
            interrupted();var marker=markers.get(s);
            if(marker.status()!=SmartpcaPrepared.MarkerStatus.USED)continue;
            for(int i=0;i<n;i++)matrix[i][s]=calls[s][basis[i]]==9?0:(calls[s][basis[i]]-marker.mean())*marker.multiplier();
        }
        // doshrinkp explicitly rebuilds this Gram with the complete marker loop.
        double[] gram=new double[n*n];
        for(int i=0;i<n;i++)for(int j=0;j<n;j++)for(int s=0;s<m;s++)gram[i*n+j]+=matrix[i][s]*matrix[j][s];
        double trace=0;for(int i=0;i<n;i++)trace+=gram[i*n+i];double scale=trace/(n-1);
        require(scale>0&&Double.isFinite(scale),"Zero/nonfinite original shrinkage covariance trace");
        double reciprocal=1/scale;for(int i=0;i<gram.length;i++)gram[i]*=reciprocal;
        interrupted();var decomposition=backend.dsyev(gram,n);double[] values=decomposition.eigenvalues(),flat=decomposition.eigenvectors();
        require(values.length==n&&flat.length==n*n,"Incomplete shrinkage eigensolver return");
        Integer[] order=new Integer[n];for(int i=0;i<n;i++){finite(values[i],"eigenvalue");order[i]=i;}
        Arrays.sort(order,(a,b)->Double.compare(values[b],values[a]));
        double[] lambda=new double[n];double[][] vectors=new double[n][n];
        for(int c=0;c<n;c++){lambda[c]=values[order[c]];for(int i=0;i<n;i++){vectors[c][i]=flat[i*n+order[c]];finite(vectors[c][i],"eigenvector");}}
        double[][] loadings=new double[k][m];
        for(int c=0;c<k;c++) {
            require(lambda[c]>0,"Nonpositive requested shrinkage axis; no repaired eigenvalue");
            centerAndNormalize(vectors[c]);
            loadings[c]=load(vectors[c],matrix,m);
            rmsNormalize(loadings[c]);
        }
        double[][] coefficients=new double[samples][k];
        for(int sample=0;sample<samples;sample++){interrupted();coefficients[sample]=project(calls,sample,markers,loadings,backend);}
        var diagnostics=new ArrayList<Perturbation>();
        for(int c=0;c<k;c++) {
            double[] eigen=vectors[c];double lam=lambda[c];
            for(int omitted=0;omitted<n;omitted++) {
                interrupted();double[] product=new double[n];
                // D has the negated row/column of X, with its diagonal counted once.
                for(int i=0;i<n;i++)for(int j=0;j<n;j++) {
                    double value=(i==omitted||j==omitted)?-gram[omitted*n+(i==omitted?j:i)]:0;
                    product[i]+=value*eigen[j];
                }
                double delta=dot(product,eigen);double[] difference=new double[n];
                for(int other=0;other<n;other++)if(other!=c) {
                    double gap=lam-lambda[other];
                    require(gap!=0&&Double.isFinite(gap),"Repeated/unreportable shrinkage eigenvalue gap; source perturbation is undefined");
                    double coefficient=dot(vectors[other],product)/gap;finite(coefficient,"eigenvector perturbation");
                    for(int i=0;i<n;i++)difference[i]+=coefficient*vectors[other][i];
                }
                boolean bad=!(lam>delta);double multiplier=bad?1:lam/(lam+delta);finite(multiplier,"source shrinkage multiplier");
                diagnostics.add(new Perturbation(c,omitted,lam,delta,multiplier,bad));
                double[] adjusted=new double[n];for(int i=0;i<n;i++)adjusted[i]=eigen[i]+difference[i];
                adjusted[omitted]=0;double mean=0;for(double value:adjusted)mean+=value;mean/=(n-1);
                for(int i=0;i<n;i++)adjusted[i]-=mean;adjusted[omitted]=0;
                centerAndNormalize(adjusted);
                double[][] changed=loadings.clone();changed[c]=load(adjusted,matrix,m);rmsNormalize(changed[c]);
                double[] estimate=project(calls,basis[omitted],markers,changed,backend);
                coefficients[basis[omitted]][c]=estimate[c]*multiplier;finite(coefficients[basis[omitted]][c],"shrunk basis coefficient");
            }
        }
        // printevecs is called with nrows=numindivs and normalizes each output
        // axis over *all* individuals, not just those used to define the basis.
        double[][] scores=copy(coefficients);
        for(int c=0;c<k;c++) {
            double norm=0;for(int i=0;i<samples;i++)norm+=scores[i][c]*scores[i][c];
            require(norm>0&&Double.isFinite(norm),"Zero/nonfinite original all-sample shrinkage output norm");
            double factor=1/Math.sqrt(norm);for(int i=0;i<samples;i++){scores[i][c]*=factor;finite(scores[i][c],"shrinkage output coordinate");}
        }
        return new Result(scores,coefficients,lambda,vectors,diagnostics);
    }
    private static double[] project(byte[][] calls,int sample,List<SmartpcaPrepared.Marker> markers,double[][] loadings,ComputeBackend backend) {
        int k=loadings.length;double[] normal=new double[k*k],rhs=new double[k];
        for(int s=0;s<calls.length;s++) {
            if(calls[s][sample]==9)continue;
            var marker=markers.get(s);double y=marker.status()==SmartpcaPrepared.MarkerStatus.USED?
                calls[s][sample]*marker.multiplier()-marker.mean()*marker.multiplier():0;
            for(int a=0;a<k;a++) {
                rhs[a]+=loadings[a][s]*y;
                for(int b=a;b<k;b++){normal[b*k+a]+=loadings[a][s]*loadings[b][s];normal[a*k+b]=normal[b*k+a];}
            }
        }
        // solvit uses a Cholesky solve. Failed/singular source projections may
        // leave stale reused coefficients; refuse those rather than invent data.
        return cholesky(normal,rhs,k);
    }
    private static double[] cholesky(double[] matrix,double[] rhs,int n) {
        double[][] lower=new double[n][n];
        for(int i=0;i<n;i++)for(int j=0;j<=i;j++) {
            double value=matrix[i*n+j];for(int p=0;p<j;p++)value-=lower[i][p]*lower[j][p];
            if(i==j){require(value>0&&Double.isFinite(value),"Singular/unreportable original shrinkage projection; no ridge, stale coefficient or pseudoinverse");lower[i][j]=Math.sqrt(value);}
            else{lower[i][j]=value/lower[j][j];finite(lower[i][j],"projection Cholesky factor");}
        }
        double[] x=rhs.clone();
        for(int i=0;i<n;i++){for(int j=0;j<i;j++)x[i]-=lower[i][j]*x[j];x[i]/=lower[i][i];}
        for(int i=n-1;i>=0;i--){for(int j=i+1;j<n;j++)x[i]-=lower[j][i]*x[j];x[i]/=lower[i][i];finite(x[i],"projection coefficient");}
        return x;
    }
    private static double[] load(double[] eigen,double[][] matrix,int markers) {
        double[] answer=new double[markers];for(int s=0;s<markers;s++)for(int i=0;i<eigen.length;i++)answer[s]+=eigen[i]*matrix[i][s];return answer;
    }
    private static void centerAndNormalize(double[] values) {
        double mean=0;for(double value:values)mean+=value;mean/=values.length;
        double norm=0;for(int i=0;i<values.length;i++){values[i]-=mean;norm+=values[i]*values[i];}
        require(norm>0&&Double.isFinite(norm),"Zero/nonfinite original centered perturbation norm");
        double scale=1/Math.sqrt(norm);for(int i=0;i<values.length;i++)values[i]*=scale;
    }
    private static void rmsNormalize(double[] values) {
        double sum=0;for(double value:values)sum+=value*value;double mean=sum/values.length;
        require(mean>0&&Double.isFinite(mean),"Zero/nonfinite original loading RMS");
        double scale=1/Math.sqrt(mean);for(int i=0;i<values.length;i++)values[i]*=scale;
    }
    private static double dot(double[] a,double[] b){double sum=0;for(int i=0;i<a.length;i++)sum+=a[i]*b[i];return sum;}
    private static void admit(int m,int n,int k,int samples,SmartpcaPrepared.Limits limits) {
        try {
            long mn=Math.multiplyExact((long)m,n),nn=Math.multiplyExact((long)n,n);
            long bytes=Math.addExact(Math.multiplyExact(mn,32),Math.addExact(Math.multiplyExact(nn,112),Math.multiplyExact((long)m*k,64)));
            long n3=Math.multiplyExact(nn,n),k2=Math.multiplyExact((long)k,k),k3=Math.multiplyExact(k2,k),k4=Math.multiplyExact(k3,k);
            long products=Math.multiplyExact(8L,n3);
            products=Math.addExact(products,Math.multiplyExact(4L*k,n3));
            products=Math.addExact(products,Math.multiplyExact(4L*k,Math.multiplyExact(nn,m)));
            products=Math.addExact(products,Math.multiplyExact(4L*m,Math.multiplyExact((long)n,k3)));
            products=Math.addExact(products,Math.multiplyExact(4L*n,k4));
            products=Math.addExact(products,Math.multiplyExact(4L*samples,Math.multiplyExact((long)m,k2)));
            products=Math.addExact(products,Math.multiplyExact(2L*m,nn));
            require(bytes<=limits.maximumRetainedBytes()&&products<=limits.maximumProducts(),"Complete source shrinkage exceeds explicit work/storage bounds; supply sufficient resources without automatic subsetting");
        }catch(ArithmeticException overflow){throw new IllegalArgumentException("Source shrinkage dimensions exceed representable work/storage bounds",overflow);}
    }
    private static void finite(double value,String label){require(Double.isFinite(value),"Nonfinite "+label+"; no complete shrinkage result");}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
    private static void interrupted(){if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Smartpca shrinkage interrupted; no complete result");}
    private static double[][] copy(double[][] matrix){return Arrays.stream(matrix).map(double[]::clone).toArray(double[][]::new);}
}
