/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Original algorithm: Marie Verbanck, MRPRESSO1.0, GPL-3;
 * rondolab/MR-PRESSO commit3e3c92d7eda6dce0d1d66077373ec0f7ff4f7e87.
 * Retain source provenance and warnings. No whole-tool/paper qualification is implied. */
package org.jlinalg.mrpresso;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jlinalg.compute.BackendContext;
import org.jlinalg.compute.BackendPolicy;
import jdistlib.accelerator.ComputeBackend;

/** Native original-profile PRESSO calculation, separate from older analytic diagnostics. */
public final class MrPressoOriginal {
    private MrPressoOriginal() { }
    public static final String SOURCE_COMMIT="3e3c92d7eda6dce0d1d66077373ec0f7ff4f7e87";

    /** Immutable prepared associations. Labels retain the supplied R row-name order. */
    public record Data(List<String> rowNames,List<String> exposureNames,double[] outcome,double[][] exposure,
                       double[] outcomeStandardError,double[][] exposureStandardError) {
        public Data {
            rowNames=List.copyOf(rowNames);exposureNames=List.copyOf(exposureNames);
            outcome=outcome.clone();exposure=copy(exposure);outcomeStandardError=outcomeStandardError.clone();exposureStandardError=copy(exposureStandardError);
            int n=outcome.length,k=exposureNames.size();
            require(n>0&&k>0&&k<=64,"Require at least one row and1..64 exposure columns");
            require(rowNames.size()==n&&exposure.length==n&&outcomeStandardError.length==n&&exposureStandardError.length==n,"Prepared input axes differ");
            require(rowNames.stream().distinct().count()==n&&exposureNames.stream().distinct().count()==k,"R row/exposure names must be unique; no repair is performed");
            for(int i=0;i<n;i++)require(exposure[i].length==k&&exposureStandardError[i].length==k,"Exposure matrix axes differ");
        }
        @Override public double[] outcome(){return outcome.clone();}
        @Override public double[] outcomeStandardError(){return outcomeStandardError.clone();}
        @Override public double[][] exposure(){return copy(exposure);}
        @Override public double[][] exposureStandardError(){return copy(exposureStandardError);}
    }

    /** Explicit original scientific options; RNG state is supplied separately. */
    public record Options(boolean outlierTest,boolean distortionTest,double significanceThreshold,int distributions) {
        public Options {
            require(Double.isFinite(significanceThreshold)&&significanceThreshold>=0&&significanceThreshold<=1,"Explicit significance threshold must be in[0,1]");
            require(distributions>0,"Positive original NbDistribution required");
        }
        public static Options defaults(){return new Options(false,false,.05,1000);}
    }

    /** Finite-Monte-Carlo value; zero is exposed as a strict upper bound. */
    public record Probability(long exceedances,int distributions,double originalValue,boolean lessThanBound,double resolution,boolean reportable) {
        static Probability of(long count,int distributions){
            double raw=(double)count/distributions;return new Probability(count,distributions,raw,count==0,1.0/distributions,Double.isFinite(raw)&&raw>=0&&raw<=1);
        }
        static Probability undefined(int distributions){return new Probability(-1,distributions,Double.NaN,false,Double.NaN,false);}
        public double bound(){return lessThanBound?resolution:Double.NaN;}
    }

    /** Source-weighted no-intercept coefficient inference, including undefined fields. */
    public record Fit(int rank,double[] beta,double[] standardError,double[] statistic,double[] pValue,int residualDf,boolean essentiallyPerfect) {
        public Fit {beta=beta.clone();standardError=standardError.clone();statistic=statistic.clone();pValue=pValue.clone();}
        @Override public double[] beta(){return beta.clone();}
        @Override public double[] standardError(){return standardError.clone();}
        @Override public double[] statistic(){return statistic.clone();}
        @Override public double[] pValue(){return pValue.clone();}
    }

    public record Outlier(String rowName,int retainedOrdinal,double squaredResidual,Probability probability,double adjustedInternalP) { }
    public enum DistortionStatus { CALCULATED, NO_SIGNIFICANT_OUTLIERS, ALL_INSTRUMENTS_OUTLIERS }
    public record Distortion(DistortionStatus status,int[] outlierOrdinals,double[] coefficientPercent,Probability probability) {
        public Distortion {outlierOrdinals=outlierOrdinals.clone();coefficientPercent=coefficientPercent.clone();}
        @Override public int[] outlierOrdinals(){return outlierOrdinals.clone();}
        @Override public double[] coefficientPercent(){return coefficientPercent.clone();}
    }
    public record Result(int[] retainedInputOrdinals,List<String> retainedRowNames,Fit raw,Fit corrected,double globalRss,
                         Probability globalProbability,List<Outlier> outliers,Distortion distortion,
                         int[] rngBefore,int[] rngAfter,List<String> warnings) {
        public Result {
            retainedInputOrdinals=retainedInputOrdinals.clone();retainedRowNames=List.copyOf(retainedRowNames);outliers=List.copyOf(outliers);
            rngBefore=rngBefore.clone();rngAfter=rngAfter.clone();warnings=List.copyOf(warnings);
        }
        @Override public int[] retainedInputOrdinals(){return retainedInputOrdinals.clone();}
        @Override public int[] rngBefore(){return rngBefore.clone();}
        @Override public int[] rngAfter(){return rngAfter.clone();}
    }

    /** Refuses invalid/capacity/numerically unresolved inputs; never silently repairs a fit. */
    public static Result analyze(Data input,Options options,MrPressoRandom random) {
        Objects.requireNonNull(input);Objects.requireNonNull(options);Objects.requireNonNull(random);
        int[] before=random.state();int columns=input.exposureNames.size();var retained=new ArrayList<Integer>();
        for(int i=0;i<input.outcome.length;i++){
            boolean missing=Double.isNaN(input.outcome[i])||Double.isNaN(input.outcomeStandardError[i]);
            for(int j=0;j<columns;j++)missing|=Double.isNaN(input.exposure[i][j])||Double.isNaN(input.exposureStandardError[i][j]);
            if(!missing)retained.add(i);
        }
        int n=retained.size(),b=options.distributions;
        require(n>columns+2,"Not enough intrumental variables");require(n<b,"Not enough elements to compute empirical P-values, increase NbDistribution");
        require((long)n*columns<=8_000_000L,"Prepared matrix exceeds explicit native capacity; no truncated analysis");
        require((long)b*columns<=8_000_000L,"Native distortion-distribution capacity exceeded; no truncated analysis");
        double[] y=new double[n],sy=new double[n],weights=new double[n];double[][] x=new double[n][columns],sx=new double[n][columns];
        var labels=new ArrayList<String>();
        for(int i=0;i<n;i++){
            int original=retained.get(i);double first=input.exposure[original][0],sign=first==0?0:first>0?1:-1;
            require(Double.isFinite(input.outcome[original])&&Double.isFinite(input.outcomeStandardError[original])&&input.outcomeStandardError[original]>0,"Nonfinite outcome or nonpositive outcome standard error; no native estimate");
            y[i]=input.outcome[original]*sign;sy[i]=input.outcomeStandardError[original];weights[i]=1/(sy[i]*sy[i]);
            require(Double.isFinite(weights[i])&&weights[i]>0,"Source weights exceed the finite native numerical profile");
            for(int j=0;j<columns;j++){
                require(Double.isFinite(input.exposure[original][j])&&Double.isFinite(input.exposureStandardError[original][j])&&input.exposureStandardError[original][j]>=0,"Nonfinite exposure or negative exposure standard error; no native estimate");
                x[i][j]=input.exposure[original][j]*sign;sx[i][j]=input.exposureStandardError[original][j];
            }
            labels.add(input.rowNames.get(original));
        }
        var warnings=new ArrayList<String>();
        if(n<input.outcome.length)warnings.add("SOURCE_COMPLETE_CASES: rows containing NA/NaN are omitted across the selected association/error columns; input ordinals and labels retained.");
        if(columns>1)warnings.add("SOURCE_MULTIVARIABLE_RECYCLING: preserve pinned original R column-major vector recycling in empirical outlier/distortion comparisons; no elementwise-column repair.");
        try(var context=BackendContext.select(BackendPolicy.CPU)){
            ComputeBackend backend=context.backend();Loo observed=loo(x,y,weights,backend);int[] all=ordinals(n);
            double[][] predictor=new double[n][columns];
            for(int i=0;i<n;i++){
                var model=MrPressoLinearModel.fit(x,y,weights,except(n,i));
                require(model.rank()==columns,"Source prediction model is rank deficient; retain original diagnostics without a native estimate");
                predictor[i]=model.beta();
            }
            long globalExceedances=0;long[] exceedances=new long[n];double[][] generatedX=new double[n][columns];double[] generatedY=new double[n];
            double[] differences=new double[n];
            for(int i=0;i<n;i++)differences[i]=y[i]-dot(x[i],observed.coefficients[i]);
            for(int iteration=0;iteration<b;iteration++){
                if(Thread.currentThread().isInterrupted())throw new IllegalStateException("MR-PRESSO interrupted; no complete native result");
                for(int j=0;j<columns;j++)for(int i=0;i<n;i++)generatedX[i][j]=random.normal(x[i][j],sx[i][j]);
                for(int i=0;i<n;i++)generatedY[i]=random.normal(dot(x[i],predictor[i]),sy[i]);
                Loo generated=loo(generatedX,generatedY,weights,backend);if(generated.rss>observed.rss)globalExceedances++;
                if(options.outlierTest)for(int i=0;i<n;i++){
                    double expected=generatedY[i];
                    // R multiplies a B-by-k matrix by a k-vector, recycling
                    // down its column-major storage, including B not divisible by k.
                    for(int j=0;j<columns;j++)expected-=generatedX[i][j]*observed.coefficients[i][(int)(((long)iteration+(long)b*j)%columns)];
                    if(expected*expected>differences[i]*differences[i])exceedances[i]++;
                }
            }
            Probability global=Probability.of(globalExceedances,b);boolean activeOutliers=options.outlierTest&&global.originalValue<options.significanceThreshold;
            var outliers=new ArrayList<Outlier>();var significant=new ArrayList<Integer>();
            if(activeOutliers)for(int i=0;i<n;i++){
                double adjusted=Math.min(1,((double)exceedances[i]/b)*n);
                // Outlier bounds use n/B, whereas the global/distortion bound uses1/B.
                Probability p=new Probability(exceedances[i],b,adjusted,exceedances[i]==0,(double)n/b,true);
                outliers.add(new Outlier(labels.get(i),i+1,differences[i]*differences[i],p,adjusted));
                if(adjusted<=options.significanceThreshold)significant.add(i);
            }
            var rawModel=MrPressoLinearModel.fit(x,y,weights,all);require(rawModel.rank()==columns,"Source main model is rank deficient; native coefficient-axis inference is unreportable");
            Fit corrected=null;Distortion distortion=null;
            if(options.distortionTest&&activeOutliers){
                int[] bad=significant.stream().mapToInt(Integer::intValue).toArray();int[] reported=Arrays.stream(bad).map(v->v+1).toArray();
                if(bad.length==0)distortion=new Distortion(DistortionStatus.NO_SIGNIFICANT_OUTLIERS,reported,new double[]{Double.NaN},Probability.undefined(b));
                else if(bad.length==n)distortion=new Distortion(DistortionStatus.ALL_INSTRUMENTS_OUTLIERS,reported,new double[]{Double.NaN},Probability.undefined(b));
                else{
                    int[] good=Arrays.stream(all).filter(i->!significant.contains(i)).toArray();double[][] biasFits=new double[b][columns];
                    int[] drawn=new int[n];System.arraycopy(bad,0,drawn,0,bad.length);
                    for(int iteration=0;iteration<b;iteration++){
                        if(Thread.currentThread().isInterrupted())throw new IllegalStateException("MR-PRESSO distortion interrupted; no complete native result");
                        for(int j=0;j<good.length;j++){
                            int chosen;
                            if(good.length==1){int[] sample=random.sampleWithoutReplacement(good[0]+1,good[0]+1);chosen=sample[0];}
                            else{int[] sample=random.sampleWithoutReplacement(good.length,good.length);chosen=good[sample[0]];}
                            drawn[bad.length+j]=chosen;
                        }
                        // Original truncates after constructing all n entries: retained
                        // outliers lead this sample and discarded draws still occur.
                        biasFits[iteration]=MrPressoLinearModel.fit(x,y,weights,Arrays.copyOf(drawn,good.length)).beta();
                    }
                    var fixed=MrPressoLinearModel.fit(x,y,weights,good);corrected=fit(fixed);
                    if(fixed.rank()<columns||fixed.residualDf()<=0)warnings.add("SOURCE_CORRECTED_FIT_UNREPORTABLE: corrected coefficient axes or residual degrees of freedom are unavailable; retain undefined fields without inference.");
                    double[] observedBias=new double[columns];
                    for(int j=0;j<columns;j++)observedBias[j]=(rawModel.beta()[j]-fixed.beta()[j])/Math.abs(fixed.beta()[j]);
                    long count=0;boolean undefined=false;
                    for(int j=0;j<columns;j++)for(int iteration=0;iteration<b;iteration++){
                        int source=(int)(((long)iteration+(long)b*j)%columns);
                        double value=(rawModel.beta()[source]-biasFits[iteration][j])/Math.abs(biasFits[iteration][j]);
                        if(Double.isNaN(value)||Double.isNaN(observedBias[source]))undefined=true;
                        else if(Math.abs(value)>Math.abs(observedBias[source]))count++;
                    }
                    Probability probability=undefined?Probability.undefined(b):Probability.of(count,b);
                    if(!probability.reportable)warnings.add("SOURCE_DISTORTION_PROBABILITY_UNREPORTABLE: original multivariable denominator/recycling can yield an undefined or out-of-range value; preserve it without clamping or inference.");
                    for(int j=0;j<columns;j++)observedBias[j]*=100;
                    distortion=new Distortion(DistortionStatus.CALCULATED,reported,observedBias,probability);
                }
            }
            if(activeOutliers&&(double)n/b>options.significanceThreshold)warnings.add("SOURCE_OUTLIER_PRECISION_UNSTABLE: n/NbDistribution exceeds SignifThreshold; source empirical resolution is insufficient. Increase NbDistribution and retry NEW output.");
            if(corrected==null)warnings.add("SOURCE_NO_CORRECTED_ESTIMATE: original source sets corrected MR to NA when its distortion/correction branch did not produce a fitted model; no replacement by the raw estimate.");
            if(rawModel.essentiallyPerfect())warnings.add("NATIVE_ESSENTIALLY_PERFECT_FIT_UNREPORTABLE: weighted residual norm is at most1e-15 of fitted norm; finite coefficient inference is not admitted. This native reliability guard does not assert an identical original R warning.");
            return new Result(retained.stream().mapToInt(Integer::intValue).toArray(),labels,fit(rawModel),corrected,observed.rss,global,outliers,distortion,before,random.state(),warnings);
        }
    }

    private record Loo(double rss,double[][] coefficients) { }
    private static Loo loo(double[][] x,double[] y,double[] weights,ComputeBackend backend) {
        int n=y.length,k=x[0].length;double[][] weighted=new double[n][k],coefficients=new double[n][k];double[] response=new double[n];
        for(int i=0;i<n;i++){
            double root=Math.sqrt(weights[i]);response[i]=y[i]*root;
            for(int j=0;j<k;j++){weighted[i][j]=x[i][j]*root;require(Double.isFinite(weighted[i][j]),"Weighted source design is nonfinite");}
            require(Double.isFinite(response[i]),"Weighted source response is nonfinite");
        }
        double rss=0,compensation=0;
        for(int omitted=0;omitted<n;omitted++){
            if(Thread.currentThread().isInterrupted())throw new IllegalStateException("MR-PRESSO leave-one-out calculation interrupted; no complete native result");
            double[] gram=new double[k*k],inverse=new double[k*k];
            for(int i=0;i<n;i++)if(i!=omitted)for(int a=0;a<k;a++){
                for(int c=0;c<k;c++)gram[a*k+c]+=weighted[i][a]*weighted[i][c];
            }
            double[] beta=coefficients[omitted];
            if(k==1){require(gram[0]>0&&Double.isFinite(gram[0]),"Singular/nonfinite original leave-one-out design");inverse[0]=1/gram[0];}
            else{
                var eigen=backend.dsyev(gram,k);double[] values=eigen.eigenvalues(),vectors=eigen.eigenvectors();
                for(int e=0;e<k;e++){
                    require(values[e]>0&&Double.isFinite(values[e]),"Nonpositive original normal-equation eigenvalue; no repaired inverse");
                    double reciprocal=1/values[e];
                    for(int a=0;a<k;a++)for(int c=0;c<k;c++)inverse[a*k+c]+=vectors[a*k+e]*(reciprocal*vectors[c*k+e]);
                }
            }
            // Preserve the original left-associative inv(X'X) %*% X' %*% Y
            // stages rather than substituting inv(X'X) %*% (X'Y).
            for(int a=0;a<k;a++)for(int i=0;i<n;i++)if(i!=omitted){
                double term=0;for(int c=0;c<k;c++)term+=inverse[a*k+c]*weighted[i][c];beta[a]+=term*response[i];
            }
            double difference=response[omitted]-dot(weighted[omitted],beta),square=difference*difference;
            require(Double.isFinite(square),"Original leave-one-out residual is nonfinite; no native estimate");
            double term=square-compensation,total=rss+term;compensation=(total-rss)-term;rss=total;
        }
        return new Loo(rss,coefficients);
    }

    private static Fit fit(MrPressoLinearModel.Fit fit){return new Fit(fit.rank(),fit.beta(),fit.standardError(),fit.statistic(),fit.pValue(),fit.residualDf(),fit.essentiallyPerfect());}
    private static double dot(double[] a,double[] b){double result=0;for(int j=0;j<a.length;j++)result+=a[j]*b[j];return result;}
    private static int[] ordinals(int n){int[] values=new int[n];for(int i=0;i<n;i++)values[i]=i;return values;}
    private static int[] except(int n,int omitted){int[] values=new int[n-1];for(int i=0,j=0;i<n;i++)if(i!=omitted)values[j++]=i;return values;}
    private static double[][] copy(double[][] values){double[][] result=new double[values.length][];for(int i=0;i<values.length;i++)result[i]=values[i].clone();return result;}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
}
