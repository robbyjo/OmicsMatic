/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.shrinkage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Native representative-population mixture fitting and ad hoc residual EM. */
public final class MashFit {
    private MashFit() { }
    /** Native stopping states; rollback is the source ad hoc stopping rule, not a global optimum. */
    public enum Status { FIXED_RESIDUAL, RESIDUAL_TOLERANCE, RESIDUAL_ROLLBACK, WEIGHTS_NONCONVERGED, RESIDUAL_LIMIT }
    public enum ResidualMode { FIXED, EM_CORRELATION, EM_COVARIANCE }
    /** The Gaussian/prior-pruning model and optimizer are explicit. */
    public record Options(MashGaussian.Options gaussian,MashMixtureWeights.Options weights,ResidualMode residualMode,
                          int maximumResidualIterations,double residualTolerance) {
        public Options {
            Objects.requireNonNull(gaussian);Objects.requireNonNull(weights);Objects.requireNonNull(residualMode);
            if(maximumResidualIterations<1||maximumResidualIterations>1_000_000||!Double.isFinite(residualTolerance)||residualTolerance<0)
                throw new IllegalArgumentException("Invalid native residual EM options");
            if(residualMode!=ResidualMode.FIXED&&!gaussian.posteriorCovariance())throw new IllegalArgumentException("Residual EM requires full posterior covariances");
        }
        public static Options defaults(){return new Options(MashGaussian.Options.defaults(),MashMixtureWeights.Options.defaults(),ResidualMode.FIXED,30,1);}
    }
    /** Complete data allocation and actual work across all residual iterations. */
    public record Limits(int rows,MashGaussian.Limits gaussian,MashMixtureWeights.Limits weights,long cells,long ownedBytes,
                         long totalGaussianWork,long totalWeightCells) {
        public Limits {Objects.requireNonNull(gaussian);Objects.requireNonNull(weights);if(rows<1||cells<1||ownedBytes<65536||totalGaussianWork<1||totalWeightCells<1)throw new IllegalArgumentException("Invalid complete native mash capacities");}
        public static Limits defaults(){return new Limits(1_000_000,MashGaussian.Limits.defaults(),MashMixtureWeights.Limits.defaults(),16_000_000,268_435_456,100_000_000_000L,10_000_000_000L);}
    }
    /** Per-proposal state. The returned arrays own their storage. */
    public record Step(int proposal,boolean accepted,double logLikelihood,double deltaLogLikelihood,
                       double[][] residual,double[] proportions,boolean weightsConverged,int weightIterations,double weightDualGap) {
        public Step {residual=copy(residual);proportions=proportions.clone();}
        @Override public double[][] residual(){return copy(residual);}
        @Override public double[] proportions(){return proportions.clone();}
    }
    /** Optional streaming evidence sink; exceptions abort, rather than dropping evidence. */
    public interface Listener {
        default void step(Step step,MashMixtureWeights.Fit completeWeightFit) { }
        default void proposal(int proposal,double[][] proposedResidual) { }
        default void weights(double[][] residual,MashMixtureWeights.Fit completeWeightFit) { }
        default void warning(String phase,int row,String message) { }
    }
    /** Complete returned fit; an exhausted optimization never becomes a usable model. */
    public record Fit(Status status,MashGaussian.Model model,double[][] residual,double[] proportions,double logLikelihood,
                      MashMixtureWeights.Fit weightFit,int proposals,int acceptedProposals,List<Step> steps,
                      long gaussianWork,long weightCells,List<String> notes) {
        public Fit {Objects.requireNonNull(status);Objects.requireNonNull(model);residual=copy(residual);proportions=proportions.clone();steps=List.copyOf(steps);notes=List.copyOf(notes);}
        @Override public double[][] residual(){return copy(residual);}
        @Override public double[] proportions(){return proportions.clone();}
        public boolean usable(){return status!=Status.WEIGHTS_NONCONVERGED&&status!=Status.RESIDUAL_LIMIT;}
        public Fit requireUsable(){if(!usable())throw new IllegalStateException("NATIVE_MASH_NONCONVERGED: "+status+". Retain all proposed models and diagnostics; increase permitted iteration/work limits or use a separately validated optimizer. No complete model is admitted.");return this;}
    }
    public record NullEstimate(double[][] covariance,List<Integer> selectedRows) {
        public NullEstimate {covariance=copy(covariance);selectedRows=List.copyOf(selectedRows);}
        @Override public double[][] covariance(){return copy(covariance);}
    }
    /** Source order: optional null, then grid-major covariance matrices scaled by squared grid values. */
    public static MashGaussian.Basis expand(MashGaussian.Basis basis,double[] grid,boolean normalize,boolean pointMass,Limits limits,Runnable check){
        Objects.requireNonNull(basis);Objects.requireNonNull(grid);Objects.requireNonNull(limits);Objects.requireNonNull(check);check.run();
        if(basis.names().isEmpty()||grid.length==0)throw new IllegalArgumentException("Require complete covariance bases and an explicit scale grid");
        int n=basis.conditions(),k=Math.toIntExact(Math.addExact(Math.multiplyExact((long)basis.names().size(),grid.length),pointMass?1:0));capacity(1,n,k,limits);var raw=basis.covariances();
        double last=0;for(double scale:grid){if(!Double.isFinite(scale)||scale<=last||!Double.isFinite(scale*scale)||scale*scale==0)throw new IllegalArgumentException("Scale grid must be strictly increasing with positive finite representable squared values");last=scale;}
        var bases=new ArrayList<double[][]>();for(double[][] supplied:raw){check.run();if(supplied.length!=n)throw new IllegalArgumentException("Covariance basis dimensions differ");double[][] u=copy(supplied);double maximum=0;
            for(int i=0;i<n;i++){if(u[i].length!=n)throw new IllegalArgumentException("Ragged covariance basis");for(double value:u[i])if(!Double.isFinite(value))throw new IllegalArgumentException("Nonfinite covariance basis");maximum=Math.max(maximum,u[i][i]);}
            if(normalize&&maximum!=0)for(int i=0;i<n;i++)for(int j=0;j<n;j++)u[i][j]/=maximum;bases.add(u);
        }
        var names=new ArrayList<String>();var matrices=new ArrayList<double[][]>();if(pointMass){names.add("null");matrices.add(new double[n][n]);}
        for(int g=0;g<grid.length;g++)for(int b=0;b<bases.size();b++){check.run();double[][] u=new double[n][n];for(int i=0;i<n;i++)for(int j=0;j<n;j++){u[i][j]=bases.get(b)[i][j]*(grid[g]*grid[g]);if(!Double.isFinite(u[i][j]))throw new ArithmeticException("Expanded covariance exceeds representable arithmetic");}names.add(basis.names().get(b)+"."+(g+1));matrices.add(u);}
        check.run();return new MashGaussian.Basis(names,matrices);
    }
    /** Strict all-condition |Z| threshold, sample covariance, and optional correlation conversion. */
    public static NullEstimate estimateNull(double[][] effects,double[][] standardErrors,double threshold,boolean correlation,Limits limits,Runnable check){
        Objects.requireNonNull(effects);Objects.requireNonNull(standardErrors);Objects.requireNonNull(limits);Objects.requireNonNull(check);check.run();
        if(!Double.isFinite(threshold)||threshold<=0||effects.length==0)throw new IllegalArgumentException("Invalid null-correlation request");
        int n=effects[0].length;capacity(effects.length,n,1,limits);validateRows(effects,standardErrors,n,0,check);var selected=new ArrayList<Integer>();double[] mean=new double[n],compensation=new double[n];
        for(int row=0;row<effects.length;row++){check.run();boolean keep=true;for(int c=0;c<n;c++)keep&=Math.abs(effects[row][c]/standardErrors[row][c])<threshold;if(keep){selected.add(row);for(int c=0;c<n;c++)add(mean,compensation,c,effects[row][c]/standardErrors[row][c]);}}
        if(selected.size()<Math.max(n,2))throw new IllegalArgumentException("Not enough null rows for the source sample-covariance estimator; supply a justified residual matrix or more null data");
        for(int c=0;c<n;c++)mean[c]/=selected.size();double[][] covariance=new double[n][n],correction=new double[n][n];
        for(int row:selected){check.run();for(int i=0;i<n;i++)for(int j=0;j<=i;j++)add(covariance[i],correction[i],j,(effects[row][i]/standardErrors[row][i]-mean[i])*(effects[row][j]/standardErrors[row][j]-mean[j]));}
        for(int i=0;i<n;i++)for(int j=0;j<=i;j++){double value=covariance[i][j]/(selected.size()-1);if(!Double.isFinite(value))throw new ArithmeticException("Unreportable complete null covariance");covariance[i][j]=covariance[j][i]=value;}
        check.run();return new NullEstimate(correlation?correlation(covariance):covariance,selected);
    }
    /** Fit only the explicitly supplied representative rows. No target data enter this method. */
    public static Fit fit(double[][] effects,double[][] standardErrors,List<double[][]> expandedCovariances,double[] dirichlet,
                          double[][] initialResidual,Options options,Limits limits,Listener listener,Runnable check){
        Objects.requireNonNull(effects);Objects.requireNonNull(standardErrors);Objects.requireNonNull(expandedCovariances);Objects.requireNonNull(dirichlet);Objects.requireNonNull(initialResidual);Objects.requireNonNull(options);Objects.requireNonNull(limits);Objects.requireNonNull(listener);Objects.requireNonNull(check);check.run();
        int n=initialResidual.length,k=expandedCovariances.size(),rows=effects.length;capacity(rows,n,k,limits);validateRows(effects,standardErrors,n,options.gaussian().minimumStandardError(),check);
        long histories=options.weights().history()?128L*(options.weights().maximumIterations()+1L):0;
        long planned=Math.addExact(65536,Math.addExact(histories,Math.multiplyExact(128L,Math.addExact(Math.multiplyExact((long)rows,k+2L*n),Math.multiplyExact((long)k,n*(long)n)))));
        if(planned>limits.ownedBytes()||planned>Runtime.getRuntime().maxMemory()/2)throw new IllegalArgumentException("Complete native data and simultaneous optimizer histories exceed declared storage or half the Java heap; increase a permitted budget/heap or explicitly disable optimizer history");
        if(dirichlet.length!=k)throw new IllegalArgumentException("Dirichlet vector differs from complete covariance count");
        double[][] bhat=copy(effects),shat=copy(standardErrors),residual=copy(initialResidual);var priors=new ArrayList<double[][]>();for(double[][] u:expandedCovariances){check.run();priors.add(copy(u));}
        double[] prior=dirichlet.clone();var work=new Work(limits);var notes=new LinkedHashSet<String>();var steps=new ArrayList<Step>();
        Candidate current=fitAt(bhat,shat,priors,prior,residual,options,limits,listener,work,notes,check);
        work.step(n,k);Step first=step(0,current.weights().converged(),current.weights().logLikelihood(),0,residual,current.weights());steps.add(first);listener.step(first,current.weights());
        if(!current.weights().converged())return result(Status.WEIGHTS_NONCONVERGED,current,residual,0,0,steps,work,notes);
        if(options.residualMode()==ResidualMode.FIXED)return result(Status.FIXED_RESIDUAL,current,residual,0,0,steps,work,notes);
        int accepted=0;
        for(int proposal=1;proposal<=options.maximumResidualIterations();proposal++){
            check.run();double[][] next=expectedNoise(bhat,shat,current.model(),limits,listener,work,notes,check);
            if(options.residualMode()==ResidualMode.EM_CORRELATION)next=correlation(next);
            listener.proposal(proposal,copy(next));
            Candidate candidate=fitAt(bhat,shat,priors,prior,next,options,limits,listener,work,notes,check);
            double delta=candidate.weights().logLikelihood()-current.weights().logLikelihood();boolean accept=candidate.weights().converged()&&delta>=0;
            work.step(n,k);Step step=step(proposal,accept,candidate.weights().logLikelihood(),delta,next,candidate.weights());steps.add(step);listener.step(step,candidate.weights());
            if(!candidate.weights().converged()){notes.add("WEIGHTS_NONCONVERGED: proposed residual model is retained but is not admitted.");return result(Status.WEIGHTS_NONCONVERGED,current,residual,proposal,accepted,steps,work,notes);}
            if(delta<0){notes.add("RESIDUAL_ROLLBACK: the ad hoc residual update decreased the full unpenalized likelihood; the preceding model is returned. This stopping rule does not certify a global residual optimum.");return result(Status.RESIDUAL_ROLLBACK,current,residual,proposal,accepted,steps,work,notes);}
            current=candidate;residual=next;accepted++;
            if(delta<=options.residualTolerance())return result(Status.RESIDUAL_TOLERANCE,current,residual,proposal,accepted,steps,work,notes);
        }
        notes.add("RESIDUAL_LIMIT: maximum proposals reached without the declared tolerance/rollback stopping condition; no completed residual fit is admitted.");
        return result(Status.RESIDUAL_LIMIT,current,residual,options.maximumResidualIterations(),accepted,steps,work,notes);
    }
    /** Average posterior second moment of standardized noise, using stable conditional-noise means. */
    public static double[][] expectedNoise(double[][] effects,double[][] errors,MashGaussian.Model model,Limits limits,Runnable check){
        Objects.requireNonNull(model);Objects.requireNonNull(limits);Objects.requireNonNull(check);capacity(effects.length,model.conditions(),model.components(),limits);validateRows(effects,errors,model.conditions(),0,check);
        return expectedNoise(effects,errors,model,limits,new Listener(){},new Work(limits),new LinkedHashSet<>(),check);
    }
    private record Candidate(MashGaussian.Model model,MashMixtureWeights.Fit weights) { }
    private static Candidate fitAt(double[][] effects,double[][] errors,List<double[][]> covariances,double[] prior,double[][] residual,Options options,Limits limits,Listener listener,Work work,LinkedHashSet<String> notes,Runnable check){
        int k=covariances.size();work.requireWeights(effects.length,k);work.prepare(residual.length,k);double[] uniform=new double[k];Arrays.fill(uniform,1./k);var g=options.gaussian();
        var likelihoodOptions=new MashGaussian.Options(g.alpha(),0,g.covarianceTolerance(),g.minimumStandardError(),g.maximumLogScoreUlp(),false);
        var initial=MashGaussian.prepare(covariances,uniform,residual,likelihoodOptions,limits.gaussian(),check);double[][] likelihood=new double[effects.length][];
        for(int row=0;row<effects.length;row++){check.run();work.gaussian(initial.conditions(),k);var value=initial.evaluate(effects[row],errors[row],check);likelihood[row]=value.componentLogLikelihoods();warn("training-likelihood",row,value.numericalNotes(),listener,notes);}
        var originalLimits=limits.weights();var weightLimits=new MashMixtureWeights.Limits(originalLimits.rows(),originalLimits.components(),originalLimits.cells(),Math.min(originalLimits.evaluatedCells(),limits.totalWeightCells()-work.weights),originalLimits.estimatedOwnedBytes());
        var weights=MashMixtureWeights.fit(likelihood,prior,null,options.weights(),weightLimits,check);work.weights(weights.evaluatedCells());
        listener.weights(copy(residual),weights);
        for(String note:weights.numericalNotes()){notes.add(note);listener.warning("weight-fit",-1,note);}
        work.prepare(residual.length,k);var model=MashGaussian.prepare(covariances,weights.proportions(),residual,g,limits.gaussian(),check);return new Candidate(model,weights);
    }
    private static double[][] expectedNoise(double[][] effects,double[][] errors,MashGaussian.Model model,Limits limits,Listener listener,Work work,LinkedHashSet<String> notes,Runnable check){
        int n=model.conditions();double[][] covariance=new double[n][n],correction=new double[n][n];
        for(int row=0;row<effects.length;row++){
            check.run();work.gaussian(n,model.components());var evaluation=model.evaluateWithResidual(effects[row],errors[row],check);double[] noise=evaluation.standardizedResidualMean();double[][] posterior=evaluation.posterior().covariance();
            if(posterior==null)throw new IllegalArgumentException("Complete residual moment requires posterior covariance output");warn("residual-moment",row,evaluation.posterior().numericalNotes(),listener,notes);
            for(int i=0;i<n;i++)for(int j=0;j<=i;j++){double value=noise[i]*noise[j]+posterior[i][j]/errors[row][i]/errors[row][j];if(!Double.isFinite(value))throw new ArithmeticException("Unreportable standardized posterior noise second moment");add(covariance[i],correction[i],j,value);}
        }
        for(int i=0;i<n;i++)for(int j=0;j<=i;j++){double value=covariance[i][j]/effects.length;if(!Double.isFinite(value))throw new ArithmeticException("Unreportable complete posterior noise covariance");covariance[i][j]=covariance[j][i]=value;}
        check.run();return covariance;
    }
    private static Fit result(Status status,Candidate fit,double[][] residual,int proposals,int accepted,List<Step> steps,Work work,LinkedHashSet<String> notes){return new Fit(status,fit.model(),residual,fit.weights().proportions(),fit.weights().logLikelihood(),fit.weights(),proposals,accepted,steps,work.gaussian,work.weights,List.copyOf(notes));}
    private static Step step(int proposal,boolean accepted,double likelihood,double delta,double[][] residual,MashMixtureWeights.Fit fit){return new Step(proposal,accepted,likelihood,delta,residual,fit.proportions(),fit.converged(),fit.iterations(),fit.relativeDualGap());}
    private static void warn(String phase,int row,List<String> warnings,Listener listener,LinkedHashSet<String> notes){for(String warning:warnings){listener.warning(phase,row,warning);notes.add(warning);}}
    private static double[][] correlation(double[][] source){int n=source.length;double[][] result=new double[n][n];for(int i=0;i<n;i++){if(!Double.isFinite(source[i][i])||source[i][i]<=0)throw new ArithmeticException("Cannot normalize a nonpositive/nonfinite residual variance; no jitter is added");for(int j=0;j<=i;j++){double value=i==j?1:source[i][j]/Math.sqrt(source[i][i])/Math.sqrt(source[j][j]);if(!Double.isFinite(value))throw new ArithmeticException("Unreportable residual correlation");result[i][j]=result[j][i]=value;}}return result;}
    private static void validateRows(double[][] effects,double[][] errors,int n,double minimum,Runnable check){if(effects.length==0||effects.length!=errors.length)throw new IllegalArgumentException("Complete effect/SE population dimensions differ or are empty");for(int row=0;row<effects.length;row++){check.run();if(effects[row]==null||errors[row]==null||effects[row].length!=n||errors[row].length!=n)throw new IllegalArgumentException("Ragged complete effect/SE population");for(int c=0;c<n;c++)if(!Double.isFinite(effects[row][c])||!Double.isFinite(errors[row][c])||errors[row][c]<=minimum)throw new IllegalArgumentException("Require finite effects and positive finite SE above the declared zero threshold; no missingness is inferred");}}
    private static void capacity(int rows,int n,int k,Limits limits){if(rows<1||rows>limits.rows()||n<1||n>limits.gaussian().conditions()||k<1||k>limits.gaussian().components()||rows>limits.weights().rows()||k>limits.weights().components())throw new IllegalArgumentException("Complete native mash dimensions exceed declared capacity");long cells=Math.addExact(Math.multiplyExact((long)rows,k+2L*n),Math.multiplyExact((long)k,n*(long)n));long bytes=Math.addExact(65536,Math.multiplyExact(128L,cells));if(cells>limits.cells()||bytes>limits.ownedBytes()||bytes>Runtime.getRuntime().maxMemory()/2)throw new IllegalArgumentException("Complete native mash allocation exceeds declared storage or half the Java heap; no row/component prefix is admitted");}
    private static void add(double[] sum,double[] correction,int i,double value){double adjusted=value-correction[i],next=sum[i]+adjusted;correction[i]=(next-sum[i])-adjusted;sum[i]=next;}
    private static double[][] copy(double[][] matrix){double[][] result=new double[matrix.length][];for(int i=0;i<matrix.length;i++)result[i]=Objects.requireNonNull(matrix[i]).clone();return result;}
    private static final class Work {
        private final Limits limits;private long gaussian,weights,traceBytes;
        Work(Limits limits){this.limits=limits;}
        void charge(int n,int k,int multiplier){gaussian=Math.addExact(gaussian,Math.multiplyExact((long)multiplier*k,Math.multiplyExact((long)n*n,n)));if(gaussian>limits.totalGaussianWork())throw new IllegalArgumentException("Native mash total Gaussian work capacity exceeded; preserve diagnostics and increase the permitted budget");}
        void gaussian(int n,int k){charge(n,k,12);}void prepare(int n,int k){charge(n,k,3);}
        void requireWeights(int rows,int k){if(Math.multiplyExact(2L*rows,k)>limits.totalWeightCells()-weights)throw new IllegalArgumentException("Native mash total optimizer budget cannot cover a complete evaluation");}
        void weights(long cells){weights=Math.addExact(weights,cells);if(weights>limits.totalWeightCells())throw new IllegalArgumentException("Native mash total optimizer work capacity exceeded; no partial fit is admitted");}
        void step(int n,int k){traceBytes=Math.addExact(traceBytes,Math.addExact(256,Math.multiplyExact(32L,n*(long)n+k)));if(traceBytes>limits.ownedBytes()/4||traceBytes>Runtime.getRuntime().maxMemory()/8)throw new IllegalArgumentException("Complete native residual trace exceeds its retained-state storage budget; no trace prefix is admitted");}
    }
}
