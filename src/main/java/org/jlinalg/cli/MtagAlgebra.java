/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.math.BigInteger;
import org.jlinalg.genetics.MtagScipy121NormalTail;

/** Full-matrix original estimator algebra, preserving scaled-matrix asymmetry from binary64 operation order. */
final class MtagAlgebra {
    private MtagAlgebra() { }
    record Effect(double[] beta,double[] se,double[] z,double[] factor) { }
    static final class Covariance {
        final double[][] omega,sigma;
        final int traits;
        final boolean sourcePerfectConstraint;
        Covariance(double[][] omega,double[][] sigma,String constraint) {
            this.omega=copy(omega);this.sigma=copy(sigma);traits=omega.length;sourcePerfectConstraint=!constraint.equals("none");
            if(traits<2||traits>8)throw new IllegalArgumentException("MTAG covariance needs2 through8 ordered traits");
            symmetric(this.omega,traits,"Omega");symmetric(this.sigma,traits,"Sigma");
            if(!positiveSemidefinite(this.sigma))throw new ArithmeticException("Original binary64 Sigma is not positive semidefinite under exact rational assessment; no covariance repair or result admission");
            if(sourcePerfectConstraint) {
                for(int i=0;i<traits;i++)for(int j=0;j<traits;j++){
                    double expected=Math.sqrt(this.omega[i][i]*this.omega[j][j]);
                    if(!Double.isFinite(expected)||this.omega[i][j]<0||Math.abs(this.omega[i][j]-expected)>2*Math.ulp(expected))
                        throw new ArithmeticException("Original Omega differs from its explicitly selected perfect-correlation constraint");
                }
                if(constraint.equals("equal_h2"))for(double[] row:this.omega)for(double value:row)
                    if(value!=1)throw new ArithmeticException("Original equal-heritability mode requires its explicit constant Omega of ones");
            } else if(!positiveSemidefinite(this.omega))
                throw new ArithmeticException("Original binary64 Omega is not positive semidefinite under exact rational assessment; no covariance repair or result admission");
        }
        Effect estimate(double[] z,double[] n) {
            if(z.length!=traits||n.length!=traits)throw new IllegalArgumentException("Complete ordered MTAG Z/N dimensions required");
            double[] inverseN=new double[traits],b=new double[traits];
            for(int i=0;i<traits;i++){
                finite(z[i]);if(!Double.isFinite(n[i])||n[i]<=0||n[i]>1e9)throw new IllegalArgumentException("Positive finite effective N<=1e9 required");
                inverseN[i]=finite(1/Math.sqrt(n[i]));b[i]=finite(inverseN[i]*z[i]);
            }
            double[] beta=new double[traits],se=new double[traits],score=new double[traits],factor=new double[traits];
            for(int target=0;target<traits;target++) {
                double[] y=new double[traits];double[][] transposed=new double[traits][traits];double tau=omega[target][target];
                for(int i=0;i<traits;i++){
                    y[i]=finite(omega[i][target]/tau);
                    for(int j=0;j<traits;j++)transposed[j][i]=finite(omega[i][j]-omega[i][target]*omega[j][target]/tau
                        +inverseN[i]*sigma[i][j]*inverseN[j]);
                }
                // Original weights are yy^T inv(xx). A transpose solve retains
                // every full scaled-matrix cell, without symmetrizing roundoff.
                double[] weights=solve(transposed,y);double denominator=dot(weights,y);
                if(!(denominator>0))throw new ArithmeticException("Unassessable original MTAG denominator; no result");
                double error=finite(Math.sqrt(1/denominator));if(!(error>0))throw new ArithmeticException("Unassessable original MTAG standard error; no result");
                beta[target]=finite(dot(weights,b)/denominator);se[target]=error;score[target]=finite(beta[target]/error);
                double sum=0;for(double w:weights)sum=finite(sum+w);factor[target]=finite(sum/denominator);
            }
            return new Effect(beta,se,score,factor);
        }
    }
    static double p(double originalZ) {return MtagScipy121NormalTail.twoSidedP(originalZ);}
    private static double[] solve(double[][] a,double[] rhs) {
        double[][] original=copy(a);double[] b=rhs.clone();int n=b.length;
        for(int k=0;k<n;k++) {
            int pivot=k;for(int i=k+1;i<n;i++)if(Math.abs(a[i][k])>Math.abs(a[pivot][k]))pivot=i;
            if(a[pivot][k]==0)throw new ArithmeticException("Original conditional MTAG covariance is singular; no jitter or repair");
            if(pivot!=k){double[] row=a[k];a[k]=a[pivot];a[pivot]=row;double v=b[k];b[k]=b[pivot];b[pivot]=v;}
            for(int i=k+1;i<n;i++){
                double f=finite(a[i][k]/a[k][k]);a[i][k]=0;
                for(int j=k+1;j<n;j++)a[i][j]=finite(a[i][j]-f*a[k][j]);b[i]=finite(b[i]-f*b[k]);
            }
        }
        double[] x=new double[n];for(int i=n-1;i>=0;i--){double v=b[i];for(int j=i+1;j<n;j++)v=finite(v-a[i][j]*x[j]);x[i]=finite(v/a[i][i]);}
        for(int i=0;i<n;i++){
            double v=0,bound=Math.abs(rhs[i]);for(int j=0;j<n;j++){v=finite(v+original[i][j]*x[j]);bound=finite(bound+Math.abs(original[i][j]*x[j]));}
            if(Math.abs(v-rhs[i])>5e-12*Math.max(1,bound))throw new ArithmeticException("Unassessable native MTAG solve residual; no result");
        }
        return x;
    }
    private static void symmetric(double[][] a,int n,String label) {
        if(a.length!=n)throw new IllegalArgumentException(label+" dimensions differ");
        for(double[] row:a){if(row.length!=n)throw new IllegalArgumentException(label+" complete dimensions differ");for(double value:row)finite(value);}
        for(int i=0;i<n;i++){if(!(a[i][i]>0))throw new ArithmeticException(label+" diagonal must be positive; no variance repair");for(int j=0;j<i;j++)if(a[i][j]!=a[j][i])throw new IllegalArgumentException(label+" is asymmetric; no averaging");}
    }
    /** Exact Schur complements on rational representations of the original binary64 values. */
    static boolean positiveSemidefinite(double[][] values) {
        int n=values.length;Fraction[][] a=new Fraction[n][n];for(int i=0;i<n;i++)for(int j=0;j<n;j++)a[i][j]=Fraction.of(values[i][j]);
        for(int k=0;k<n;k++) {
            int pivot=-1;
            for(int i=k;i<n;i++){if(a[i][i].numerator.signum()<0)return false;if(a[i][i].numerator.signum()>0&&pivot<0)pivot=i;}
            if(pivot<0){for(int i=k;i<n;i++)for(int j=k;j<n;j++)if(a[i][j].numerator.signum()!=0)return false;return true;}
            if(pivot!=k){Fraction[] row=a[k];a[k]=a[pivot];a[pivot]=row;for(int i=0;i<n;i++){Fraction v=a[i][k];a[i][k]=a[i][pivot];a[i][pivot]=v;}}
            for(int i=k+1;i<n;i++)for(int j=i;j<n;j++){Fraction v=a[i][j].minus(a[i][k].times(a[j][k]).divide(a[k][k]));a[i][j]=a[j][i]=v;}
        }
        return true;
    }
    private record Fraction(BigInteger numerator,BigInteger denominator) {
        Fraction {
            if(denominator.signum()==0)throw new ArithmeticException("Zero exact covariance pivot");
            if(denominator.signum()<0){numerator=numerator.negate();denominator=denominator.negate();}
            BigInteger gcd=numerator.gcd(denominator);numerator=numerator.divide(gcd);denominator=denominator.divide(gcd);
            if(numerator.bitLength()>262144||denominator.bitLength()>262144)throw new ArithmeticException("Exact covariance-assessment capacity exceeded; no approximate PSD substitution");
        }
        static Fraction of(double value) {
            finite(value);long bits=Double.doubleToRawLongBits(value),mantissa=bits&0x000fffffffffffffL;int exponent=(int)((bits>>>52)&2047);
            if(exponent==0)exponent=-1074;else {mantissa|=1L<<52;exponent-=1075;}
            BigInteger num=BigInteger.valueOf(mantissa),den=BigInteger.ONE;if(bits<0)num=num.negate();
            if(exponent>=0)num=num.shiftLeft(exponent);else den=den.shiftLeft(-exponent);return new Fraction(num,den);
        }
        Fraction minus(Fraction b){return new Fraction(numerator.multiply(b.denominator).subtract(b.numerator.multiply(denominator)),denominator.multiply(b.denominator));}
        Fraction times(Fraction b){return new Fraction(numerator.multiply(b.numerator),denominator.multiply(b.denominator));}
        Fraction divide(Fraction b){return new Fraction(numerator.multiply(b.denominator),denominator.multiply(b.numerator));}
    }
    private static double dot(double[] a,double[] b){double value=0;for(int i=0;i<a.length;i++)value=finite(value+a[i]*b[i]);return value;}
    private static double finite(double value){if(!Double.isFinite(value))throw new ArithmeticException("Unassessable MTAG algebra; no nonfinite repair");return value;}
    private static double[][] copy(double[][] a){double[][] b=new double[a.length][];for(int i=0;i<a.length;i++)b[i]=a[i].clone();return b;}
}
