/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Gaussian model and reporting conventions follow mashr0.2.79 (BSD-3-Clause).
 * See /org/jlinalg/omics/mashr-BSD-NOTICE.txt. */
package org.jlinalg.shrinkage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import jdistlib.Normal;
import jdistlib.accelerator.CpuComputeBackend;

/**
 * Gaussian multivariate shrinkage for a supplied finite mixture of covariance
 * matrices. Priors are expanded actual covariances: this class never guesses a
 * scale grid, normalizes covariance diagonals, estimates residual correlations,
 * learns data-driven covariances, or converts unsigned P values into effects.
 * Posterior means and covariances are returned on the original effect scale.
 */
public final class MashGaussian {
    private MashGaussian() { }

    /** Explicit Gaussian model, prior-pruning and numerical admission settings. */
    public record Options(double alpha,double minimumPriorWeight,double covarianceTolerance,
                          double minimumStandardError,double maximumLogScoreUlp,boolean posteriorCovariance) {
        public Options {
            if(!Double.isFinite(alpha)||!Double.isFinite(minimumPriorWeight)||minimumPriorWeight<0||minimumPriorWeight>=1
                    ||!Double.isFinite(covarianceTolerance)||covarianceTolerance<0||covarianceTolerance>1e-4
                    ||!Double.isFinite(minimumStandardError)||minimumStandardError<0
                    ||!Double.isFinite(maximumLogScoreUlp)||maximumLogScoreUlp<=0)
                throw new IllegalArgumentException("Invalid explicit Gaussian shrinkage options");
        }
        /** mashr default alpha/prior threshold and SE-zero threshold, with explicit native tolerances. */
        public static Options defaults(){return new Options(0,1e-10,1e-12,Math.ulp(1.0),1e-7,true);}
    }
    /** Complete per-model/per-row capacities; exceeding one never returns a prefix. */
    public record Limits(int conditions,int components,long estimatedWork,long estimatedOwnedBytes) {
        public Limits {if(conditions<1||conditions>2048||components<1||components>1_000_000||estimatedWork<1||estimatedWork>1_000_000_000_000_000L||estimatedOwnedBytes<65536||estimatedOwnedBytes>1_099_511_627_776L)throw new IllegalArgumentException("Invalid Gaussian shrinkage capacities");}
        /** Portable bounds; actual planned allocations must also fit half the current Java heap. */
        public static Limits defaults(){return new Limits(256,4096,100_000_000_000L,268_435_456);}
    }
    /** Named canonical matrices in source order; no null matrix or scale grid is added implicitly. */
    public record Basis(List<String> names,List<double[][]> covariances) {
        public Basis {names=List.copyOf(names);covariances=copyMatrices(covariances);if(names.size()!=covariances.size())throw new IllegalArgumentException("Canonical names/covariances differ");}
        /** Matrix dimension without materializing defensive copies of the complete basis. */
        public int conditions(){return covariances.isEmpty()?0:covariances.get(0).length;}
        @Override public List<double[][]> covariances(){return copyMatrices(covariances);}
    }
    /**
     * Complete row result. Negative/positive probabilities exclude point mass at
     * zero; lfsr includes that mass and can exceed0.5. Full-mixture likelihoods
     * precede posterior prior-weight pruning. The scaled likelihood is for
     * Bhat/SE^alpha, with its Jacobian shown separately by the original-scale value.
     */
    public record Posterior(double logLikelihood,double scaledLogLikelihood,double retainedPriorMass,
                            double[] componentLogLikelihoods,double[] posteriorWeights,double[] mean,double[] standardDeviation,
                            double[] negativeProbability,double[] zeroProbability,double[] lfsr,double[][] covariance,List<String> numericalNotes) {
        public Posterior {componentLogLikelihoods=componentLogLikelihoods.clone();posteriorWeights=posteriorWeights.clone();mean=mean.clone();standardDeviation=standardDeviation.clone();negativeProbability=negativeProbability.clone();zeroProbability=zeroProbability.clone();lfsr=lfsr.clone();covariance=covariance==null?null:copy(covariance);numericalNotes=List.copyOf(numericalNotes);}
        @Override public double[] componentLogLikelihoods(){return componentLogLikelihoods.clone();}
        @Override public double[] posteriorWeights(){return posteriorWeights.clone();}
        @Override public double[] mean(){return mean.clone();}
        @Override public double[] standardDeviation(){return standardDeviation.clone();}
        @Override public double[] negativeProbability(){return negativeProbability.clone();}
        @Override public double[] zeroProbability(){return zeroProbability.clone();}
        @Override public double[] lfsr(){return lfsr.clone();}
        @Override public double[][] covariance(){return covariance==null?null:copy(covariance);}
    }
    /** Posterior with E[(Bhat-beta)/SE | Bhat], calculated without subtracting large means. */
    public record Evaluation(Posterior posterior,double[] standardizedResidualMean) {
        public Evaluation {Objects.requireNonNull(posterior);standardizedResidualMean=standardizedResidualMean==null?null:standardizedResidualMean.clone();}
        @Override public double[] standardizedResidualMean(){return standardizedResidualMean==null?null:standardizedResidualMean.clone();}
    }

    /** Construct the source identity, singleton, equal-effect and three simple-heterogeneity bases. */
    public static Basis canonical(int conditions){return canonical(conditions,Limits.defaults(),()->{});}
    /** Construct all canonical matrices after admitting their complete storage and work. */
    public static Basis canonical(int conditions,Limits limits,Runnable check){
        Objects.requireNonNull(limits);Objects.requireNonNull(check);check.run();
        if(conditions<1||conditions>limits.conditions()||conditions+5>limits.components())throw new IllegalArgumentException("Complete canonical basis dimensions exceed capacity");
        var conditionNames=new ArrayList<String>();for(int i=0;i<conditions;i++)conditionNames.add("V"+(i+1));return canonical(conditionNames,limits,check);
    }
    /** Source canonical ordering with supplied unique condition names for singleton components. */
    public static Basis canonical(List<String> conditionNames,Limits limits,Runnable check){
        Objects.requireNonNull(conditionNames);Objects.requireNonNull(limits);Objects.requireNonNull(check);check.run();int conditions=conditionNames.size();
        if(conditions<1||conditions>limits.conditions()||conditions+5>limits.components())throw new IllegalArgumentException("Complete canonical basis dimensions exceed capacity");
        var seen=new java.util.HashSet<String>();for(String name:conditionNames)if(name==null||name.isBlank()||!seen.add(name))throw new IllegalArgumentException("Condition names must be nonblank and unique");
        capacity(conditions,conditions+5,Options.defaults(),limits);work(conditions,conditions+5,1,limits);var names=new ArrayList<String>();var matrices=new ArrayList<double[][]>();
        var identity=new double[conditions][conditions];for(int i=0;i<conditions;i++)identity[i][i]=1;names.add("identity");matrices.add(identity);
        for(int i=0;i<conditions;i++){check.run();var u=new double[conditions][conditions];u[i][i]=1;names.add(conditionNames.get(i));matrices.add(u);}
        var equal=new double[conditions][conditions];for(var row:equal)Arrays.fill(row,1);names.add("equal_effects");matrices.add(equal);
        for(int k=1;k<=3;k++){var u=new double[conditions][conditions];for(int i=0;i<conditions;i++)for(int j=0;j<conditions;j++)u[i][j]=i==j?1:k*.25;names.add("simple_het_"+k);matrices.add(u);}
        check.run();return new Basis(names,matrices);
    }

    /** Validate and defensively retain expanded covariance components and mixture proportions. */
    public static Model prepare(List<double[][]> expandedCovariances,double[] proportions,double[][] residualCovariance,Options options,Limits limits,Runnable check){
        return new Model(expandedCovariances,proportions,residualCovariance,options,limits,check);
    }
    /** Reusable, immutable fixed-mixture model; each evaluation processes one complete feature. */
    public static final class Model {
        private final int n,k;private final double[] weights,lower;private final double[][][] factors;private final boolean[][] structuralZero;
        private final double logDetV,retainedMass;private final Options options;private final Limits limits;private final List<String> notes;
        private Model(List<double[][]> covariances,double[] proportions,double[][] residual,Options options,Limits limits,Runnable check){
            Objects.requireNonNull(covariances);Objects.requireNonNull(proportions);Objects.requireNonNull(residual);this.options=Objects.requireNonNull(options);this.limits=Objects.requireNonNull(limits);Objects.requireNonNull(check);check.run();
            n=residual.length;k=covariances.size();if(n<1||n>limits.conditions()||k<1||k>limits.components()||proportions.length!=k)throw new IllegalArgumentException("Complete Gaussian model dimensions exceed capacities or differ");
            capacity(n,k,options,limits);work(n,k,3,limits);weights=proportions.clone();double sum=0,retained=0;
            for(double w:weights){if(!Double.isFinite(w)||w<0||w>1)throw new IllegalArgumentException("Mixture proportions must be finite probabilities");sum+=w;}
            if(!Double.isFinite(sum)||Math.abs(sum-1)>1e-10)throw new IllegalArgumentException("Mixture proportions must sum to one; no arbitrary normalization is inferred");
            for(int p=0;p<k;p++){weights[p]/=sum;if(weights[p]>options.minimumPriorWeight())retained+=weights[p];}
            if(retained==0)throw new IllegalArgumentException("Every component is removed by the explicit strict prior-weight threshold");retainedMass=retained;
            var mutableNotes=new ArrayList<String>();double[][] v=symmetric(residual,n,options.covarianceTolerance(),"residual covariance",mutableNotes);
            factors=new double[k][][];structuralZero=new boolean[k][n];
            try(var cpu=new CpuComputeBackend()){
                var cholesky=cpu.dpotrf(pack(v),n);lower=cholesky.lower();logDetV=cholesky.logDeterminant();if(!Double.isFinite(logDetV))throw new ArithmeticException("Unreportable residual covariance determinant");
                for(int p=0;p<k;p++){
                    check.run();double[][] u=symmetric(covariances.get(p),n,options.covarianceTolerance(),"prior component"+p,mutableNotes);double scale=maxAbs(u);
                    for(int i=0;i<n;i++){if(u[i][i]<0)throw new IllegalArgumentException("Negative prior marginal variance");structuralZero[p][i]=u[i][i]==0;if(structuralZero[p][i])for(int j=0;j<n;j++)if(u[i][j]!=0)throw new IllegalArgumentException("A zero prior variance requires an exactly zero covariance row");}
                    factors[p]=new double[n][n];if(scale==0)continue;double[] normalized=pack(u);for(int i=0;i<normalized.length;i++)normalized[i]/=scale;
                    var eigen=cpu.dsyev(normalized,n);double[] values=eigen.eigenvalues(),vectors=eigen.eigenvectors();int clipped=0;
                    for(int c=0;c<n;c++){
                        double value=values[c];if(!Double.isFinite(value)||value < -options.covarianceTolerance()*n)throw new IllegalArgumentException("Prior component is not positive semidefinite within the explicit numerical tolerance");if(value<0){value=0;clipped++;}
                        double root=Math.sqrt(value)*Math.sqrt(scale);for(int r=0;r<n;r++)factors[p][r][c]=structuralZero[p][r]?0:vectors[r*n+c]*root;
                    }
                    for(int r=0;r<n;r++)if(!structuralZero[p][r]){boolean represented=false;for(double value:factors[p][r])represented|=value!=0;if(!represented)throw new ArithmeticException("A nonzero prior marginal was lost in covariance factorization; no false point mass is admitted");}
                    if(clipped>0)mutableNotes.add("PRIOR_ROUNDOFF: component"+p+" clipped"+clipped+" small negative normalized eigenvalues;original singularity and zero rows remain explicit.");
                }
            }
            if(Math.abs(sum-1)>0)mutableNotes.add("PROPORTION_ROUNDOFF: supplied sum="+sum+" was normalized within the admitted1e-10 simplex tolerance.");
            if(retainedMass<1)mutableNotes.add("PRIOR_PRUNING: posterior summaries use only proportions strictly greater than"+options.minimumPriorWeight()+";full-mixture likelihoods retain all components.");notes=List.copyOf(mutableNotes);
        }
        /** Number of observed conditions. */ public int conditions(){return n;}
        /** Number of original supplied components, including excluded/zero-weight components. */ public int components(){return k;}
        /** Complete fixed proportions in original component order. */ public double[] proportions(){return weights.clone();}
        /** Evaluate original-scale effects and explicitly supplied standard errors; no missingness is guessed. */
        public Posterior evaluate(double[] effects,double[] standardErrors,Runnable check){
            return evaluateRow(effects,standardErrors,check,false).posterior();
        }
        /** Evaluate posterior effects and stable standardized conditional-noise means. */
        public Evaluation evaluateWithResidual(double[] effects,double[] standardErrors,Runnable check){
            return evaluateRow(effects,standardErrors,check,true);
        }
        private Evaluation evaluateRow(double[] effects,double[] standardErrors,Runnable check,boolean residualMean){
            Objects.requireNonNull(effects);Objects.requireNonNull(standardErrors);Objects.requireNonNull(check);if(effects.length!=n||standardErrors.length!=n)throw new IllegalArgumentException("Effect/SE dimensions differ from the complete model");check.run();work(n,k,12,limits);
            double[] z=new double[n],priorScale=new double[n],whitenScale=new double[n];double logSe=0;
            for(int i=0;i<n;i++){
                double s=standardErrors[i];if(!Double.isFinite(effects[i])||!Double.isFinite(s)||s<=options.minimumStandardError())throw new IllegalArgumentException("Require finite effects and SE above the explicit zero threshold;missing values or P-value conversions need a declared upstream policy");
                z[i]=effects[i]/s;priorScale[i]=Math.pow(s,options.alpha());whitenScale[i]=Math.pow(s,options.alpha()-1);logSe+=Math.log(s);
                if(!Double.isFinite(z[i])||!positiveFinite(priorScale[i])||!positiveFinite(whitenScale[i]))throw new ArithmeticException("Effect/SE/alpha transformation exceeds representable Gaussian arithmetic");
            }
            double[] x=solveLower(lower,z,n),scores=new double[k];Component[] components=new Component[k];var numerical=new ArrayList<String>(notes);
            try(var cpu=new CpuComputeBackend()){
                for(int p=0;p<k;p++){
                    check.run();double[][] whitened=new double[n][n];
                    for(int c=0;c<n;c++){double[] column=new double[n];for(int i=0;i<n;i++)column[i]=factors[p][i][c]*whitenScale[i];double[] solved=solveLower(lower,column,n);for(int i=0;i<n;i++)whitened[i][c]=solved[i];}
                    // Square-root Gaussian calculations use augmented QR. Forming
                    // I+GG' can lose the identity at extreme scales; completing
                    // a rank-deficient SVD can contaminate its null-space score.
                    double[] observation=new double[2*n*n],latent=new double[2*n*n];
                    for(int i=0;i<n;i++)for(int j=0;j<n;j++){
                        observation[i*n+j]=latent[i*n+j]=i==j?1:0;
                        observation[(n+i)*n+j]=whitened[j][i];latent[(n+i)*n+j]=whitened[i][j];
                    }
                    var observationQr=cpu.dgeqp3(observation,2*n,n);double[] observationR=observationQr.packed();int[] observationPivot=observationQr.pivot();
                    if(observationQr.rank()!=n)throw new ArithmeticException("Augmented observation QR is numerically rank deficient;no component is dropped");
                    double[] permutedX=new double[n];for(int i=0;i<n;i++)permutedX[i]=x[observationPivot[i]];
                    double[] normalized=solveUpperTranspose(observationR,permutedX,n);double q=0,logDet=0;
                    for(int i=0;i<n;i++){q+=normalized[i]*normalized[i];logDet+=2*Math.log(Math.abs(observationR[i*n+i]));}
                    scores[p]=-.5*(q+logDet);if(Double.isNaN(scores[p])||scores[p]==Double.POSITIVE_INFINITY)throw new ArithmeticException("Invalid Gaussian likelihood score");
                    if(!Double.isFinite(scores[p])||Math.ulp(scores[p])>options.maximumLogScoreUlp())numerical.add("COMPONENT_SCORE_RESOLUTION: component"+p+" has coarse absolute log-score resolution;its component log density is approximate. Dominant full/retained mixture scores must independently pass the explicit resolution gate.");
                    double[] residual=null;
                    if(residualMean){
                        double[] solved=solveUpper(observationR,normalized,n),unpermuted=new double[n];
                        for(int i=0;i<n;i++)unpermuted[observationPivot[i]]=solved[i];residual=new double[n];
                        for(int i=0;i<n;i++){for(int j=0;j<=i;j++)residual[i]+=lower[i*n+j]*unpermuted[j];if(!Double.isFinite(residual[i]))throw new ArithmeticException("Unreportable standardized conditional-noise mean");}
                    }
                    var latentQr=cpu.dgeqp3(latent,2*n,n);double[] latentR=latentQr.packed();int[] latentPivot=latentQr.pivot();
                    if(latentQr.rank()!=n)throw new ArithmeticException("Augmented latent QR is numerically rank deficient;no prior direction is dropped");
                    double[] response=new double[2*n];System.arraycopy(x,0,response,n,n);double[] latentMean=latentQr.solveLeastSquares(response);
                    double[][] h=new double[n][n];double[] mean=new double[n],variance=new double[n];double[][] covariance=options.posteriorCovariance()?new double[n][n]:null;
                    for(int i=0;i<n;i++){
                        double[] permutedFactor=new double[n];for(int j=0;j<n;j++){permutedFactor[j]=factors[p][i][latentPivot[j]];mean[i]+=factors[p][i][j]*latentMean[j];}
                        mean[i]*=priorScale[i];double[] solved=solveUpperTranspose(latentR,permutedFactor,n);
                        for(int c=0;c<n;c++){h[i][c]=solved[c]*priorScale[i];if(!Double.isFinite(h[i][c]))throw new ArithmeticException("Unreportable Gaussian posterior factor");variance[i]+=h[i][c]*h[i][c];}
                    }
                    for(int i=0;i<n;i++){
                        if(structuralZero[p][i]){mean[i]=0;variance[i]=0;}
                        if(!Double.isFinite(mean[i])||!Double.isFinite(variance[i]))throw new ArithmeticException("Unreportable Gaussian posterior mean/variance");
                        if(variance[i]==0&&!structuralZero[p][i])throw new ArithmeticException("Posterior variance underflow at a nonzero prior marginal; no false zero-effect probability is admitted");
                    }
                    if(covariance!=null)for(int i=0;i<n;i++)for(int j=0;j<=i;j++){double value=0;for(int c=0;c<n;c++)value+=h[i][c]*h[j][c];covariance[i][j]=covariance[j][i]=structuralZero[p][i]||structuralZero[p][j]?0:value;}
                    components[p]=new Component(mean,variance,covariance,residual);
                }
            }
            double normalizer=-.5*(n*Math.log(2*Math.PI)+logDetV+2*logSe);Mixture full=mixture(scores,weights,0,options.maximumLogScoreUlp()),selected=mixture(scores,weights,options.minimumPriorWeight(),options.maximumLogScoreUlp());
            double[] posterior=selected.weights(),mean=new double[n],variance=new double[n],negative=new double[n],zero=new double[n],lfsr=new double[n];double[][] covariance=options.posteriorCovariance()?new double[n][n]:null;
            double[] residual=residualMean?new double[n]:null;
            for(int p=0;p<k;p++)for(int i=0;i<n;i++)mean[i]+=posterior[p]*components[p].mean()[i];
            for(int p=0;p<k;p++){
                double weight=posterior[p];if(weight==0)continue;Component component=components[p];
                for(int i=0;i<n;i++){
                    if(residual!=null)residual[i]+=weight*component.residual()[i];
                    double delta=component.mean()[i]-mean[i];variance[i]+=weight*(component.variance()[i]+delta*delta);
                    if(component.variance()[i]==0)zero[i]+=weight;else negative[i]+=weight*Normal.cumulative(0,component.mean()[i],Math.sqrt(component.variance()[i]),true,false);
                    if(covariance!=null)for(int j=0;j<=i;j++){double value=weight*(component.covariance()[i][j]+delta*(component.mean()[j]-mean[j]));covariance[i][j]+=value;if(i!=j)covariance[j][i]+=value;}
                }
            }
            double[] sd=new double[n];for(int i=0;i<n;i++){if(!Double.isFinite(variance[i])||variance[i]<0)throw new ArithmeticException("Unreportable mixture posterior variance");sd[i]=Math.sqrt(variance[i]);zero[i]=probability(zero[i]);negative[i]=probability(negative[i]);lfsr[i]=probability(zero[i]+Math.min(negative[i],Math.max(0,1-negative[i]-zero[i])));}
            if(residual!=null)for(double value:residual)if(!Double.isFinite(value))throw new ArithmeticException("Unreportable mixture conditional-noise mean");
            double[] logDensities=new double[k];for(int p=0;p<k;p++)logDensities[p]=normalizer+scores[p];check.run();return new Evaluation(new Posterior(normalizer+full.logValue(),normalizer+full.logValue()+options.alpha()*logSe,retainedMass,logDensities,posterior,mean,sd,negative,zero,lfsr,covariance,numerical),residual);
        }
    }
    private record Component(double[] mean,double[] variance,double[][] covariance,double[] residual) { }
    private record Mixture(double logValue,double[] weights) { }
    private static Mixture mixture(double[] scores,double[] proportions,double threshold,double maximumUlp){
        double max=Double.NEGATIVE_INFINITY;for(int i=0;i<scores.length;i++)if(proportions[i]>threshold)max=Math.max(max,scores[i]);if(!Double.isFinite(max))throw new ArithmeticException("All admitted Gaussian likelihoods are unreportable");
        if(Math.ulp(max)>maximumUlp)throw new ArithmeticException("Gaussian relative likelihood-score resolution exceeds the explicit numerical budget;do not report ambiguous mixture weights");
        double[] weights=new double[scores.length];double sum=0;for(int i=0;i<scores.length;i++)if(proportions[i]>threshold){weights[i]=proportions[i]*Math.exp(scores[i]-max);sum+=weights[i];}if(!positiveFinite(sum))throw new ArithmeticException("Gaussian mixture normalizer is unreportable");for(int i=0;i<weights.length;i++)weights[i]/=sum;return new Mixture(max+Math.log(sum),weights);
    }
    private static void capacity(int n,int k,Options options,Limits limits){
        long cells=Math.addExact(Math.multiplyExact((long)k,n*(long)n),Math.addExact(Math.multiplyExact((long)k,options.posteriorCovariance()?n*(long)n:n),Math.addExact(32L*n*n,4L*k*n+16L*k)));
        long bytes=Math.addExact(65536,Math.multiplyExact(24,cells));if(bytes>limits.estimatedOwnedBytes()||bytes>Runtime.getRuntime().maxMemory()/2)throw new IllegalArgumentException("Complete Gaussian model/row storage exceeds declared capacity or half the Java heap;increase permitted capacity/heap rather than truncating components");
    }
    private static void work(int n,int k,int multiplier,Limits limits){long steps=Math.multiplyExact(Math.multiplyExact(Math.multiplyExact((long)n,n),n),Math.multiplyExact((long)k,multiplier));if(steps>limits.estimatedWork())throw new IllegalArgumentException("Complete Gaussian estimated work exceeds capacity;no component prefix is returned");}
    private static boolean positiveFinite(double x){return Double.isFinite(x)&&x>0;}
    private static double probability(double p){if(!Double.isFinite(p)||p < -1e-12||p>1+1e-12)throw new ArithmeticException("Invalid posterior probability");return Math.max(0,Math.min(1,p));}
    private static double[][] symmetric(double[][] source,int n,double tolerance,String label,List<String> notes){
        if(source==null||source.length!=n)throw new IllegalArgumentException(label+" dimension differs");double[][] copy=new double[n][n];double scale=0;
        for(int i=0;i<n;i++){if(source[i]==null||source[i].length!=n)throw new IllegalArgumentException(label+" is ragged");for(int j=0;j<n;j++){if(!Double.isFinite(source[i][j]))throw new IllegalArgumentException(label+" contains nonfinite values");scale=Math.max(scale,Math.abs(source[i][j]));}}
        boolean changed=false;for(int i=0;i<n;i++)for(int j=0;j<=i;j++){if(Math.abs(source[i][j]-source[j][i])>tolerance*scale)throw new IllegalArgumentException(label+" is asymmetric beyond the explicit tolerance");double value=source[i][j]==source[j][i]?source[i][j]:source[i][j]/2+source[j][i]/2;copy[i][j]=copy[j][i]=value;changed|=source[i][j]!=source[j][i];}
        if(changed)notes.add("SYMMETRY_ROUNDOFF: "+label+" was symmetrized within the explicit tolerance.");return copy;
    }
    private static double[] solveLower(double[] lower,double[] rhs,int n){double[] x=rhs.clone();for(int i=0;i<n;i++){for(int j=0;j<i;j++)x[i]-=lower[i*n+j]*x[j];x[i]/=lower[i*n+i];if(!Double.isFinite(x[i]))throw new ArithmeticException("Unreportable residual whitening");}return x;}
    private static double[] solveUpperTranspose(double[] upper,double[] rhs,int n){double[] x=rhs.clone();for(int i=0;i<n;i++){for(int j=0;j<i;j++)x[i]-=upper[j*n+i]*x[j];x[i]/=upper[i*n+i];if(!Double.isFinite(x[i]))throw new ArithmeticException("Unreportable augmented-QR solve");}return x;}
    private static double[] solveUpper(double[] upper,double[] rhs,int n){double[] x=rhs.clone();for(int i=n-1;i>=0;i--){for(int j=i+1;j<n;j++)x[i]-=upper[i*n+j]*x[j];x[i]/=upper[i*n+i];if(!Double.isFinite(x[i]))throw new ArithmeticException("Unreportable augmented-QR noise solve");}return x;}
    private static double maxAbs(double[][] matrix){double value=0;for(double[] row:matrix)for(double x:row)value=Math.max(value,Math.abs(x));return value;}
    private static double[] pack(double[][] matrix){int n=matrix.length;double[] packed=new double[n*n];for(int i=0;i<n;i++)for(int j=0;j<n;j++)packed[i*n+j]=matrix[i][j];return packed;}
    private static double[][] copy(double[][] matrix){double[][] result=new double[matrix.length][];for(int i=0;i<matrix.length;i++)result[i]=matrix[i].clone();return result;}
    private static List<double[][]> copyMatrices(List<double[][]> matrices){var result=new ArrayList<double[][]>();for(double[][] matrix:matrices)result.add(copy(matrix));return List.copyOf(result);}
}
