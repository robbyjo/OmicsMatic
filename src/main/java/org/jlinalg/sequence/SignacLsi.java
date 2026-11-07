/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Native Signac LSI postprocessing and independent sparse singular-triplet diagnostics. */
public final class SignacLsi {
    private SignacLsi() { }
    public record Embeddings(double[][] values, double[] standardDeviations, List<String> warnings) {
        public Embeddings { values=copy(values);standardDeviations=standardDeviations.clone();warnings=List.copyOf(warnings); }
        @Override public double[][] values(){return copy(values);}
        @Override public double[] standardDeviations(){return standardDeviations.clone();}
    }
    /**
     * Uses raw left singular vectors, not vectors multiplied by singular values.
     * Embedding scaling uses sample SD across cells; stdev uses the number of
     * selected features as in the upstream source. Clipping is applied only
     * when scaling is enabled. Constant components retain NaN and a warning.
     */
    public static Embeddings postprocess(double[][] u,double[] singularValues,int selectedFeatures,
                                         boolean scale,Double clip,BooleanSupplier cancelled) {
        Objects.requireNonNull(u);Objects.requireNonNull(singularValues);Objects.requireNonNull(cancelled);check(cancelled);
        if(u.length<2||singularValues.length<1||selectedFeatures<1||((long)u.length*singularValues.length)>50_000_000L)
            throw new IllegalArgumentException("Require at least two cells, positive component/feature counts and at most 50 million embedding values");
        if(clip!=null&&(!Double.isFinite(clip)||clip<=0))throw new IllegalArgumentException("LSI clipping requires a finite positive bound");
        double[][] out=new double[u.length][singularValues.length];double[] sdev=new double[singularValues.length];List<String> warnings=new ArrayList<>();
        for(int row=0;row<u.length;row++){
            if((row&1023)==0)check(cancelled);
            if(u[row]==null||u[row].length!=singularValues.length)throw new IllegalArgumentException("Ragged raw singular vectors");
            for(int col=0;col<singularValues.length;col++)if(!Double.isFinite(u[row][col]))throw new IllegalArgumentException("Raw singular vectors must be finite");
        }
        for(int col=0;col<singularValues.length;col++){
            check(cancelled);double d=singularValues[col];
            if(!Double.isFinite(d)||d<0||(col>0&&d>singularValues[col-1]))throw new IllegalArgumentException("Singular values must be finite, nonnegative and nonincreasing");
            sdev[col]=d/Math.sqrt(Math.max(1,selectedFeatures-1));
            double mean=0,compensation=0;
            for(int row=0;row<u.length;row++){if((row&4095)==0)check(cancelled);double y=u[row][col]-compensation,t=mean+y;compensation=(t-mean)-y;mean=t;}
            mean/=u.length;
            // R's mean correction and sample variance use residuals around the mean.
            double correction=0;for(int row=0;row<u.length;row++){if((row&4095)==0)check(cancelled);correction+=u[row][col]-mean;}mean+=correction/u.length;
            double sum=0,c=0;
            for(int row=0;row<u.length;row++){if((row&4095)==0)check(cancelled);double delta=u[row][col]-mean,y=delta*delta-c,t=sum+y;c=(t-sum)-y;sum=t;}
            double sd=Math.sqrt(sum/(u.length-1));
            if(scale&&sd==0)warnings.add("ZERO_COMPONENT_SD: component "+(col+1)+" has constant raw cell embeddings; NaN is retained. Skip finite-only analysis or explicitly choose a justified unscaled analysis.");
            for(int row=0;row<u.length;row++){
                if((row&4095)==0)check(cancelled);double value=scale?(u[row][col]-mean)/sd:u[row][col];
                if(scale&&clip!=null){if(value>clip)value=clip;if(value< -clip)value= -clip;}
                out[row][col]=value;
            }
        }
        if(!scale&&clip!=null)warnings.add("CLIP_IGNORED_WITH_UNSCALED_EMBEDDINGS: upstream clipping applies only after scaling; no clipping was performed.");
        check(cancelled);return new Embeddings(out,sdev,warnings);
    }
    public record Diagnostics(double maximumRelativeLeftResidual,double maximumRelativeRightResidual,
                              double maximumUOrthogonalityError,double maximumVOrthogonalityError) { }
    /** Checks A V = U D and A^T U = V D for A = transpose(feature-by-cell data). */
    public static Diagnostics diagnose(int featureCount,int cellCount,int[] pointers,int[] rows,double[] values,
                                       double[][] u,double[][] v,double[] d,BooleanSupplier cancelled) {
        Objects.requireNonNull(cancelled);check(cancelled);
        if(featureCount<1||cellCount<2||pointers.length!=cellCount+1||pointers[0]!=0||pointers[cellCount]!=values.length||rows.length!=values.length
                ||u.length!=cellCount||v.length!=featureCount||d.length<1||(((long)featureCount+cellCount)*d.length)>50_000_000L)
            throw new IllegalArgumentException("Malformed or excessive singular-triplet dimensions");
        validateVectors(u,d.length);validateVectors(v,d.length);
        double norm=0;
        for(int cell=0;cell<cellCount;cell++){
            if((cell&1023)==0)check(cancelled);if(pointers[cell]<0||pointers[cell+1]<pointers[cell]||pointers[cell+1]>values.length)throw new IllegalArgumentException("Malformed CSC pointers");
            int previous=-1;
            for(int k=pointers[cell];k<pointers[cell+1];k++){
                if((k&4095)==0)check(cancelled);if(rows[k]<=previous||rows[k]>=featureCount||!Double.isFinite(values[k]))throw new IllegalArgumentException("Require sorted unique CSC coordinates with finite values");
                previous=rows[k];norm=Math.hypot(norm,values[k]);
            }
        }
        double left=0,right=0;
        for(int col=0;col<d.length;col++){
            check(cancelled);if(!Double.isFinite(d[col])||d[col]<0||(col>0&&d[col]>d[col-1]))throw new IllegalArgumentException("Invalid singular values");
            double[] atU=new double[featureCount];double l=0,r=0;
            for(int cell=0;cell<cellCount;cell++){
                if((cell&1023)==0)check(cancelled);double av=0;
                for(int k=pointers[cell];k<pointers[cell+1];k++){if((k&4095)==0)check(cancelled);av+=values[k]*v[rows[k]][col];atU[rows[k]]+=values[k]*u[cell][col];}
                l=Math.hypot(l,av-d[col]*u[cell][col]);
            }
            for(int feature=0;feature<featureCount;feature++){if((feature&4095)==0)check(cancelled);r=Math.hypot(r,atU[feature]-d[col]*v[feature][col]);}
            left=Math.max(left,l/Math.max(1,norm));right=Math.max(right,r/Math.max(1,norm));
        }
        double ue=orthogonality(u,d.length,cancelled),ve=orthogonality(v,d.length,cancelled);check(cancelled);
        return new Diagnostics(left,right,ue,ve);
    }
    private static void validateVectors(double[][] x,int n){for(double[] row:x){if(row==null||row.length!=n)throw new IllegalArgumentException("Ragged singular vectors");for(double value:row)if(!Double.isFinite(value))throw new IllegalArgumentException("Nonfinite singular vectors");}}
    private static double orthogonality(double[][] x,int n,BooleanSupplier cancelled){double error=0;for(int a=0;a<n;a++)for(int b=0;b<=a;b++){check(cancelled);double dot=0;for(int row=0;row<x.length;row++){if((row&4095)==0)check(cancelled);dot+=x[row][a]*x[row][b];}error=Math.max(error,Math.abs(dot-(a==b?1:0)));}return error;}
    private static double[][] copy(double[][] x){double[][] result=new double[x.length][];for(int i=0;i<x.length;i++)result[i]=x[i].clone();return result;}
    private static void check(BooleanSupplier cancelled){if(Thread.currentThread().isInterrupted()||cancelled.getAsBoolean())throw new CancellationException("Signac LSI numerical check cancelled");}
}
