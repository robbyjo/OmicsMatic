/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.survival;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Finite-MPLE check for unweighted baseline Cox partial likelihood (Breslow or
 * Efron). For every observed event i and member j of its actual risk set, let
 * D_ij = x_i-x_j. A full-rank D and strictly positive weights w with D'w=0
 * certify a finite unique maximum. D*b >= 0 with a strict inequality certifies
 * monotone likelihood. Tied failures constrain each other in both directions.
 * Offsets are fixed finite constants and do not change this recession cone.
 *
 * A bounded nonnegative least-squares search finds candidate certificates.
 * Acceptance uses exact arithmetic on the original binary double inputs: a
 * separating direction is checked at every risk contrast, or a small exact
 * rational basis correction produces a strictly positive balanced measure.
 * An incomplete search, numerical ambiguity or resource limit is UNRESOLVED,
 * never evidence of a finite maximum. No statistical calibration is implied.
 */
public final class CoxLikelihoodExistence {
    private CoxLikelihoodExistence() { }
    public enum Status { FINITE_MAXIMUM, MONOTONE_LIKELIHOOD, UNRESOLVED }
    public record Options(long maximumPairVisits, long maximumWork, int maximumIterations,
                          int maximumDimension, int maximumCertificateBits, double tolerance) {
        public Options {
            if(maximumPairVisits<1 || maximumWork<1 || maximumIterations<1 || maximumDimension<1
                    || maximumCertificateBits<256 || !(tolerance>=1e-14 && tolerance<=1e-6))
                throw new IllegalArgumentException("invalid Cox likelihood-existence bounds");
        }
        public static Options defaults(){return new Options(10_000_000,1_000_000_000,1000,64,16384,1e-10);}
    }
    /** Local certificate indices refer to original input rows, not participant identifiers. */
    public record Pair(int eventRow,int riskRow) implements java.io.Serializable { }
    public record ExtraWeight(Pair pair,double value) implements java.io.Serializable { }
    public record BasisCorrection(Pair pair,String numerator,String denominator) implements java.io.Serializable { }
    public record ExactCoefficient(int column,String numerator,String denominator) implements java.io.Serializable { }
    public record Result(Status status,String message,int dimension,int numericalRank,long contrasts,
                         long pairVisits,long work,int iterations,double searchResidual,
                         double[] columnCenters,double[] columnScales,double[] direction,List<ExactCoefficient> exactDirection,
                         List<ExtraWeight> extraWeights,List<BasisCorrection> basisCorrections) implements java.io.Serializable {
        public Result {
            columnCenters=columnCenters.clone();columnScales=columnScales.clone();direction=direction.clone();
            exactDirection=List.copyOf(exactDirection);
            extraWeights=List.copyOf(extraWeights);basisCorrections=List.copyOf(basisCorrections);
        }
        @Override public double[] columnCenters(){return columnCenters.clone();}
        @Override public double[] columnScales(){return columnScales.clone();}
        @Override public double[] direction(){return direction.clone();}
        public boolean finiteMaximum(){return status==Status.FINITE_MAXIMUM;}
    }
    public static Result analyze(CoxSurvivalData survival,double[][] x){return analyze(survival,x,Options.defaults());}
    public static Result analyze(CoxSurvivalData survival,double[][] x,Options options){
        if(survival==null || options==null || x==null || x.length!=survival.observations() || x[0]==null)
            throw new IllegalArgumentException("survival response and matching finite design required");
        int p=x[0].length;
        for(double[] row:x){if(row==null || row.length!=p)throw new IllegalArgumentException("rectangular Cox design required");for(double v:row)if(!Double.isFinite(v))throw new IllegalArgumentException("finite Cox design required");}
        return new Search(survival,x,options).run();
    }
    private interface Visitor { void accept(Pair pair,double[] contrast,double norm); }
    private static final class Limit extends RuntimeException {
        private static final long serialVersionUID=1L;
        Limit(String message){super(message);}
    }
    private static final class Search {
        final CoxSurvivalData response;final double[][] original,scaled;final Options options;final int n,p;
        final double[] centers,scales;final List<Pair> basis=new ArrayList<>();final List<double[]> orthogonal=new ArrayList<>();
        final List<Pair> active=new ArrayList<>();final List<double[]> vectors=new ArrayList<>();
        double[] weights=new double[0],mean,residual;long visits,work,contrasts;int iterations;
        Search(CoxSurvivalData response,double[][] original,Options options){
            this.response=response;this.original=original;this.options=options;n=original.length;p=original[0].length;
            centers=new double[p];scales=new double[p];scaled=new double[n][];
        }
        Result result(Status status,String message,double[] direction,List<ExtraWeight> extra,List<BasisCorrection> corrections){
            return result(status,message,direction,List.of(),extra,corrections);
        }
        Result result(Status status,String message,double[] direction,List<ExactCoefficient> exact,List<ExtraWeight> extra,List<BasisCorrection> corrections){return new Result(status,message,p,basis.size(),contrasts,visits,work,iterations,residual==null?Double.NaN:norm(residual),centers,scales,direction,exact,extra,corrections);}
        Result unresolved(String message){return result(Status.UNRESOLVED,message,new double[0],List.of(),List.of());}
        void spend(long units){if(units<0 || work>options.maximumWork()-units)throw new Limit("likelihood-existence work bound reached");work+=units;}
        Result run(){
            try{
                if(p==0)return result(Status.FINITE_MAXIMUM,"No regression coefficients to estimate",new double[0],List.of(),List.of());
                if(p>options.maximumDimension())return unresolved("likelihood-existence dimension bound reached");
                spend(4L*n*p);for(int i=0;i<n;i++)scaled[i]=new double[p];
                for(int j=0;j<p;j++){
                    double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
                    for(double[] row:original){low=Math.min(low,row[j]);high=Math.max(high,row[j]);}
                    centers[j]=.5*low+.5*high;scales[j]=Math.max(Math.abs(high-centers[j]),Math.abs(low-centers[j]));
                    if(!(scales[j]>0))return unresolved("constant design column; full contrast rank not certified");
                    for(int i=0;i<n;i++)scaled[i][j]=(original[i][j]-centers[j])/scales[j];
                }
                double[] total=new double[p],compensation=new double[p];
                scan((pair,d,length)->{
                    contrasts++;for(int j=0;j<p;j++){double y=d[j]-compensation[j],t=total[j]+y;compensation[j]=(t-total[j])-y;total[j]=t;}
                    if(basis.size()<p){double[] candidate=orthogonalize(d,orthogonal);double length2=norm(candidate);if(length2>options.tolerance()){for(int j=0;j<p;j++)candidate[j]/=length2;basis.add(pair);orthogonal.add(candidate);}}
                });
                if(basis.size()!=p)return unresolved("full risk-contrast rank not certified");
                mean=total;for(int j=0;j<p;j++)mean[j]/=contrasts;residual=mean.clone();
                for(iterations=0;iterations<options.maximumIterations();iterations++){
                    if(norm(residual)<=options.tolerance())return finiteCertificate();
                    final Pair[] selected={null};final double[][] vector={null};final double[] best={-options.tolerance()*norm(residual)};
                    scan((pair,d,length)->{double score=dot(d,residual);if(score<best[0] && !active.contains(pair)){best[0]=score;selected[0]=pair;vector[0]=d.clone();}});
                    if(selected[0]==null)return monotoneCertificate();
                    active.add(selected[0]);vectors.add(vector[0]);weights=Arrays.copyOf(weights,weights.length+1);
                    boolean solved=false;
                    for(int inner=0;inner<=p+1;inner++){
                        spend((long)p*(p+1)*(p+1));
                        double[] target=mean.clone();for(int j=0;j<p;j++)target[j]=-target[j];
                        double[] candidate=leastSquares(vectors,target,options.tolerance());
                        if(candidate==null)return unresolved("NNLS active risk contrasts are numerically dependent");
                        double fraction=1;boolean allPositive=true;
                        for(int k=0;k<candidate.length;k++)if(candidate[k]<=0){allPositive=false;double denominator=weights[k]-candidate[k];if(denominator>0)fraction=Math.min(fraction,weights[k]/denominator);}
                        if(allPositive){weights=candidate;solved=true;break;}
                        for(int k=0;k<weights.length;k++)weights[k]+=fraction*(candidate[k]-weights[k]);
                        for(int k=weights.length-1;k>=0;k--)if(weights[k]<=1e-14){active.remove(k);vectors.remove(k);weights=remove(weights,k);}
                    }
                    if(!solved)return unresolved("NNLS active-set inner bound reached");
                    residual=mean.clone();for(int k=0;k<weights.length;k++)for(int j=0;j<p;j++)residual[j]+=weights[k]*vectors.get(k)[j];
                }
                return unresolved("likelihood-existence iteration bound reached");
            }catch(Limit exhausted){return unresolved(exhausted.getMessage());}
        }
        void scan(Visitor visitor){
            var start=response.startView();var stop=response.stopView();var event=response.eventView();var strata=response.strataView();
            for(int i=0;i<n;i++)if(event[i])for(int j=0;j<n;j++){
                if(visits>=options.maximumPairVisits())throw new Limit("likelihood-existence pair-visit bound reached");visits++;spend(1);
                if(i==j || strata[i]!=strata[j] || !(start[j]<stop[i] && stop[j]>=stop[i]))continue;
                double[] d=new double[p];boolean different=false;spend(4L*p);
                for(int k=0;k<p;k++){d[k]=scaled[i][k]-scaled[j][k];different|=original[i][k]!=original[j][k];}
                if(!different)continue;double length=norm(d);if(!(length>0) || !Double.isFinite(length))throw new Limit("unrepresentable scaled risk contrast");
                for(int k=0;k<p;k++)d[k]/=length;visitor.accept(new Pair(i,j),d,length);
            }
        }
        Result monotoneCertificate(){
            double[] direction=new double[p];double largest=0;
            for(int j=0;j<p;j++){direction[j]=residual[j]/scales[j];largest=Math.max(largest,Math.abs(direction[j]));}
            if(!(largest>0) || !Double.isFinite(largest))return unresolved("unrepresentable monotone candidate direction");
            for(int j=0;j<p;j++)direction[j]/=largest;
            for(int digits:new int[]{0,15,12,9,6}){
                double[] candidate=direction.clone();if(digits>0)for(int j=0;j<p;j++)candidate[j]=new BigDecimal(candidate[j]).round(new java.math.MathContext(digits)).doubleValue();
                final boolean[] valid={true},strict={false};
                scan((pair,d,length)->{spend(8L*p);BigDecimal value=BigDecimal.ZERO;for(int j=0;j<p;j++)value=value.add(difference(pair,j).multiply(new BigDecimal(candidate[j])));int sign=value.signum();if(sign<0)valid[0]=false;if(sign>0)strict[0]=true;});
                if(valid[0] && strict[0]){List<ExactCoefficient> exact=new ArrayList<>();for(int j=0;j<p;j++){var r=rational(new BigDecimal(candidate[j]));exact.add(new ExactCoefficient(j,r.numerator().toString(),r.denominator().toString()));}return result(Status.MONOTONE_LIKELIHOOD,"Exact risk-contrast direction proves no finite maximum",candidate,exact,List.of(),List.of());}
            }
            Rational[] rationalDirection=boundaryDirection(direction);
            if(rationalDirection!=null){
                final boolean[] valid={true},strict={false};
                scan((pair,d,length)->{Rational value=new Rational(BigInteger.ZERO,BigInteger.ONE);for(int j=0;j<p;j++)value=checked(value.add(rational(difference(pair,j)).multiply(rationalDirection[j])));if(value.signum()<0)valid[0]=false;if(value.signum()>0)strict[0]=true;});
                if(valid[0] && strict[0]){
                    List<ExactCoefficient> exact=new ArrayList<>();double[] approximate=new double[p];for(int j=0;j<p;j++){var r=rationalDirection[j];exact.add(new ExactCoefficient(j,r.numerator().toString(),r.denominator().toString()));approximate[j]=new BigDecimal(r.numerator()).divide(new BigDecimal(r.denominator()),java.math.MathContext.DECIMAL64).doubleValue();}
                    return result(Status.MONOTONE_LIKELIHOOD,"Exact rational risk-contrast direction proves no finite maximum",approximate,exact,List.of(),List.of());
                }
            }
            return unresolved("NNLS boundary candidate failed exact monotonicity certification");
        }
        Rational[] boundaryDirection(double[] candidate){
            List<Pair> zeroPairs=new ArrayList<>();List<double[]> zeroBasis=new ArrayList<>();double residualNorm=norm(residual);
            scan((pair,d,length)->{
                if(zeroPairs.size()<p && Math.abs(dot(d,residual))<=100*options.tolerance()*residualNorm){
                    double[] vector=orthogonalize(d,zeroBasis);double size=norm(vector);if(size>options.tolerance()){for(int j=0;j<p;j++)vector[j]/=size;zeroPairs.add(pair);zeroBasis.add(vector);}
                }
            });
            int count=zeroPairs.size();if(count==p)return null;
            Rational[][] matrix=new Rational[count][p];for(int i=0;i<count;i++)for(int j=0;j<p;j++)matrix[i][j]=rational(difference(zeroPairs.get(i),j));
            int[] pivots=new int[count];int rank=0;
            for(int column=0;column<p && rank<count;column++){
                int row=rank;while(row<count && matrix[row][column].signum()==0)row++;if(row==count)continue;
                var tmp=matrix[rank];matrix[rank]=matrix[row];matrix[row]=tmp;var divisor=matrix[rank][column];
                for(int j=column;j<p;j++)matrix[rank][j]=checked(matrix[rank][j].divide(divisor));
                for(int i=0;i<count;i++)if(i!=rank){var factor=matrix[i][column];if(factor.signum()!=0)for(int j=column;j<p;j++)matrix[i][j]=checked(matrix[i][j].subtract(factor.multiply(matrix[rank][j])));}
                pivots[rank++]=column;
            }
            if(rank!=count)return null;
            boolean[] pivotColumn=new boolean[p];for(int column:pivots)pivotColumn[column]=true;
            Rational[] direction=new Rational[p];for(int j=0;j<p;j++)direction[j]=pivotColumn[j]?new Rational(BigInteger.ZERO,BigInteger.ONE):rational(new BigDecimal(candidate[j]));
            for(int i=0;i<count;i++){Rational value=new Rational(BigInteger.ZERO,BigInteger.ONE);for(int j=0;j<p;j++)if(!pivotColumn[j])value=checked(value.subtract(matrix[i][j].multiply(direction[j])));direction[pivots[i]]=value;}
            return direction;
        }
        Result finiteCertificate(){
            List<ExtraWeight> extra=new ArrayList<>();
            for(int k=0;k<active.size();k++){
                Pair pair=active.get(k);double length=contrastNorm(pair);double weight=(contrasts*weights[k])/length;
                if(!(weight>=0) || !Double.isFinite(weight))return unresolved("unrepresentable positive certificate weight");
                extra.add(new ExtraWeight(pair,weight));
            }
            BigDecimal[] total=new BigDecimal[p],baseWeights=new BigDecimal[p];Arrays.fill(total,BigDecimal.ZERO);
            scan((pair,d,length)->{
                spend(12L*p);double base=1/length;if(!Double.isFinite(base))throw new Limit("unrepresentable base certificate weight");
                BigDecimal weight=new BigDecimal(base);for(var e:extra)if(e.pair().equals(pair))weight=weight.add(new BigDecimal(e.value()));
                int index=basis.indexOf(pair);if(index>=0)baseWeights[index]=weight;
                for(int j=0;j<p;j++)total[j]=total[j].add(difference(pair,j).multiply(weight));
            });
            Rational[][] matrix=new Rational[p][p+1];
            for(int j=0;j<p;j++){for(int k=0;k<p;k++)matrix[j][k]=rational(difference(basis.get(k),j));matrix[j][p]=rational(total[j].negate());}
            for(int col=0;col<p;col++){
                int pivot=col;while(pivot<p && matrix[pivot][col].signum()==0)pivot++;if(pivot==p)return unresolved("exact contrast basis is rank deficient");
                var temp=matrix[col];matrix[col]=matrix[pivot];matrix[pivot]=temp;var divisor=matrix[col][col];
                for(int k=col;k<=p;k++)matrix[col][k]=checked(matrix[col][k].divide(divisor));
                for(int j=0;j<p;j++)if(j!=col){var factor=matrix[j][col];if(factor.signum()!=0)for(int k=col;k<=p;k++)matrix[j][k]=checked(matrix[j][k].subtract(factor.multiply(matrix[col][k])));}
            }
            List<BasisCorrection> corrections=new ArrayList<>();
            for(int k=0;k<p;k++){
                var correction=matrix[k][p];if(rational(baseWeights[k]).add(correction).signum()<=0)return unresolved("exact balancing correction is not strictly positive");
                corrections.add(new BasisCorrection(basis.get(k),correction.numerator().toString(),correction.denominator().toString()));
            }
            for(int j=0;j<p;j++){
                Rational balance=rational(total[j]);for(int k=0;k<p;k++)balance=checked(balance.add(rational(difference(basis.get(k),j)).multiply(matrix[k][p])));
                if(balance.signum()!=0)return unresolved("exact positive-certificate balance verification failed");
            }
            return result(Status.FINITE_MAXIMUM,"Exact full-rank positive risk-contrast balance proves a finite unique maximum",new double[0],extra,corrections);
        }
        double contrastNorm(Pair pair){double[] d=new double[p];for(int j=0;j<p;j++)d[j]=scaled[pair.eventRow()][j]-scaled[pair.riskRow()][j];return norm(d);}
        BigDecimal difference(Pair pair,int column){return new BigDecimal(original[pair.eventRow()][column]).subtract(new BigDecimal(original[pair.riskRow()][column]));}
        Rational rational(BigDecimal value){spend(1);int scale=value.scale();return checked(scale>=0?new Rational(value.unscaledValue(),BigInteger.TEN.pow(scale)):new Rational(value.unscaledValue().multiply(BigInteger.TEN.pow(-scale)),BigInteger.ONE));}
        Rational checked(Rational value){spend(1);if(value.numerator().bitLength()>options.maximumCertificateBits() || value.denominator().bitLength()>options.maximumCertificateBits())throw new Limit("exact likelihood-certificate bit bound reached");return value;}
    }
    private record Rational(BigInteger numerator,BigInteger denominator){
        Rational {if(denominator.signum()==0)throw new ArithmeticException("zero rational denominator");if(denominator.signum()<0){numerator=numerator.negate();denominator=denominator.negate();}var gcd=numerator.gcd(denominator);numerator=numerator.divide(gcd);denominator=denominator.divide(gcd);}
        int signum(){return numerator.signum();}
        Rational add(Rational other){return new Rational(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),denominator.multiply(other.denominator));}
        Rational subtract(Rational other){return new Rational(numerator.multiply(other.denominator).subtract(other.numerator.multiply(denominator)),denominator.multiply(other.denominator));}
        Rational multiply(Rational other){return new Rational(numerator.multiply(other.numerator),denominator.multiply(other.denominator));}
        Rational divide(Rational other){return new Rational(numerator.multiply(other.denominator),denominator.multiply(other.numerator));}
    }
    private static double[] remove(double[] values,int index){double[] result=new double[values.length-1];System.arraycopy(values,0,result,0,index);System.arraycopy(values,index+1,result,index,values.length-index-1);return result;}
    private static double dot(double[] x,double[] y){double total=0;for(int j=0;j<x.length;j++)total+=x[j]*y[j];return total;}
    private static double norm(double[] x){double value=0;for(double v:x)value=Math.hypot(value,v);return value;}
    private static double[] orthogonalize(double[] vector,List<double[]> basis){double[] result=vector.clone();for(int pass=0;pass<2;pass++)for(double[] q:basis){double product=dot(q,result);for(int j=0;j<result.length;j++)result[j]-=product*q[j];}return result;}
    private static double[] leastSquares(List<double[]> columns,double[] target,double tolerance){
        int size=columns.size();if(size==0)return new double[0];if(size>target.length)return null;
        List<double[]> basis=new ArrayList<>();double[][] r=new double[size][size];double[] rhs=new double[size];
        for(int k=0;k<size;k++){
            double[] q=columns.get(k).clone();for(int pass=0;pass<2;pass++)for(int j=0;j<k;j++){double product=dot(basis.get(j),q);r[j][k]+=product;for(int c=0;c<q.length;c++)q[c]-=product*basis.get(j)[c];}
            double length=norm(q);if(!(length>tolerance))return null;r[k][k]=length;for(int c=0;c<q.length;c++)q[c]/=length;basis.add(q);rhs[k]=dot(q,target);
        }
        double[] result=new double[size];for(int k=size-1;k>=0;k--){double value=rhs[k];for(int j=k+1;j<size;j++)value-=r[k][j]*result[j];result[k]=value/r[k][k];}return result;
    }
}
