/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.HashSet;
import org.jlinalg.compute.BackendContext;
import org.jlinalg.compute.BackendPolicy;

/** Native reconstruction of original conditional arithmetic; no covariance validity is inferred. */
final class SaigeConditionalGeometry {
    private SaigeConditionalGeometry() { }

    /** Stored coordinates are used literally, including an original unreflected triangle. */
    record StoredSigma(int dimension,int[] rows,int[] columns,double[] values) {
        StoredSigma {
            if(dimension<1||dimension>1_000_000||rows.length!=columns.length||rows.length!=values.length||rows.length>10_000_000)
                throw new IllegalArgumentException("Invalid bounded original Sigma dimensions");
            rows=rows.clone();columns=columns.clone();values=values.clone();var seen=new HashSet<Long>();
            for(int i=0;i<values.length;i++) {
                if(rows[i]<0||rows[i]>=dimension||columns[i]<0||columns[i]>=dimension||!Double.isFinite(values[i])
                        ||!seen.add((long)rows[i]*dimension+columns[i]))
                    throw new IllegalArgumentException("Invalid/duplicate original Sigma coordinate");
            }
        }
        @Override public int[] rows(){return rows.clone();}
        @Override public int[] columns(){return columns.clone();}
        @Override public double[] values(){return values.clone();}
        double[] multiply(double[] vector) {
            vector(vector,dimension);double[] result=new double[dimension];
            for(int i=0;i<values.length;i++)result[rows[i]]+=values[i]*vector[columns[i]];
            return result;
        }
        double[] diagonal() {
            double[] result=new double[dimension];
            for(int i=0;i<values.length;i++)if(rows[i]==columns[i])result[rows[i]]=values[i];
            return result;
        }
    }

    record Pcg(double[] solution,int iterations,double residualSquared,boolean converged) {
        Pcg {solution=solution.clone();}
        @Override public double[] solution(){return solution.clone();}
    }

    /** Replays the original absolute squared-residual stopping rule and diagonal preconditioner. */
    static Pcg pcg(StoredSigma sigma,double[] right,int maximum,double squaredTolerance)throws IOException {
        int n=sigma.dimension;vector(right,n);
        if(maximum<1||maximum>100_000||!Double.isFinite(squaredTolerance)||squaredTolerance<0)
            throw new IllegalArgumentException("Declare finite absolute squared-residual tolerance and iteration bound");
        double[] diagonal=sigma.diagonal(),x=new double[n],r=right.clone(),z=new double[n];
        for(int i=0;i<n;i++) {
            if(!Double.isFinite(diagonal[i])||diagonal[i]<=0)throw new IOException("Original Sigma has missing/nonpositive preconditioner diagonal");
            z[i]=r[i]/diagonal[i];
        }
        double[] p=z.clone();int iteration=0;double norm=dot(r,r);
        while(norm>squaredTolerance&&iteration<maximum) {
            iteration++;double[] ap=sigma.multiply(p);double rz=dot(r,z),denominator=dot(p,ap);
            if(!Double.isFinite(rz)||!Double.isFinite(denominator)||denominator==0||rz==0)
                throw new IOException("Original PCG arithmetic has a nonfinite/zero denominator");
            double alpha=rz/denominator;double[] nextR=new double[n],nextZ=new double[n];
            for(int i=0;i<n;i++){x[i]+=alpha*p[i];nextR[i]=r[i]-alpha*ap[i];nextZ[i]=nextR[i]/diagonal[i];}
            double beta=dot(nextZ,nextR)/dot(z,r);
            for(int i=0;i<n;i++)p[i]=nextZ[i]+beta*p[i];
            r=nextR;z=nextZ;norm=dot(r,r);
            if(!Double.isFinite(alpha)||!Double.isFinite(beta)||!Double.isFinite(norm))
                throw new IOException("Original PCG arithmetic became nonfinite");
        }
        return new Pcg(x,iteration,norm,norm<=squaredTolerance);
    }

    record Conditioning(int markers,double[] inverse,double[] scores,int numericalRank,double asymmetry) {
        Conditioning {inverse=inverse.clone();scores=scores.clone();}
        @Override public double[] inverse(){return inverse.clone();}
        @Override public double[] scores(){return scores.clone();}
    }
    record Schur(double score,double variance,double scoreReduction,double varianceReduction) { }

    /** Original pinv accepts nonsymmetric matrices; retain that behavior and record asymmetry. */
    static Conditioning conditioning(double[] covariance,double[] scores)throws IOException {
        int q=scores.length;if(q<1||q>64)throw new IllegalArgumentException("Conditioning requires 1..64 markers");
        vector(covariance,Math.multiplyExact(q,q));vector(scores,q);double asymmetry=0;
        for(int i=0;i<q;i++)for(int j=0;j<i;j++)asymmetry=Math.max(asymmetry,Math.abs(covariance[i*q+j]-covariance[j*q+i]));
        double[] inverse=new double[q*q];int rank=0;
        try(var context=BackendContext.select(BackendPolicy.CPU)) {
            var svd=context.backend().dgesvd(covariance.clone(),q,q);double[] singular=svd.singularValues();
            double[] left=svd.leftSingularVectors(),right=svd.rightSingularVectorsTransposed();int components=svd.components();
            double tolerance=q*Math.ulp(1.0)*singular[0];
            for(int k=0;k<components;k++)if(singular[k]>tolerance) {
                rank++;for(int i=0;i<q;i++)for(int j=0;j<q;j++)inverse[i*q+j]+=right[k*q+i]*left[j*components+k]/singular[k];
            }
        }catch(IllegalArgumentException|IllegalStateException failure){throw new IOException("Conditional source-compatible pseudoinverse failed",failure);}
        for(double value:inverse)if(!Double.isFinite(value))throw new IOException("Conditional pseudoinverse is nonfinite");
        return new Conditioning(q,inverse,scores,rank,asymmetry);
    }

    static Schur schur(double score,double variance,double[] cross,Conditioning conditioning) {
        vector(cross,conditioning.markers);if(!Double.isFinite(score)||!Double.isFinite(variance))throw new IllegalArgumentException("Require finite primary score and variance");
        int q=conditioning.markers;double scoreReduction=0,varianceReduction=0;
        for(int i=0;i<q;i++)for(int j=0;j<q;j++) {
            double term=cross[i]*conditioning.inverse[i*q+j];
            scoreReduction+=term*conditioning.scores[j];varianceReduction+=term*cross[j];
        }
        return new Schur(score-scoreReduction,variance-varianceReduction,scoreReduction,varianceReduction);
    }

    private static void vector(double[] value,int expected) {
        if(value.length!=expected)throw new IllegalArgumentException("Conditional geometry vector shape changed");
        for(double x:value)if(!Double.isFinite(x))throw new IllegalArgumentException("Conditional geometry requires finite values");
    }
    private static double dot(double[] a,double[] b){double value=0;for(int i=0;i<a.length;i++)value+=a[i]*b[i];return value;}
}
