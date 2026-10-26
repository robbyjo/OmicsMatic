/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.survival;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;
import org.jlinalg.association.AssociationBatchResult;
import org.jlinalg.association.AssociationEngineOptions;
import org.jlinalg.association.AssociationFailure;
import org.jlinalg.association.AssociationFailurePolicy;
import org.jlinalg.association.VariableMissingPolicy;
import org.jlinalg.compute.BackendProvenance;
import org.jlinalg.inference.AssociationStatistics;
import org.jlinalg.internal.MatrixOps;

/** Prepared-null, bounded-block score scanner for Cox association tests. */
public final class FastCoxAssociation {
    private final CoxSurvivalData survival;
    private final int observations;
    private final int covariateCount;
    private final double[] covariates;
    private final double[] offset;
    private final CoxOptions coxOptions;
    private final CoxResult nullModel;
    private final CoxAdaptiveLikelihood.Result nullLikelihood;
    private final double[] nullCoefficients, nullCovariance;
    private final CoxRiskSetPlan rightCensoredPlan;
    private final CoxCountingProcessPlan countingProcessPlan;
    private final List<String> clusterIds;
    private final double[] scoreCorrelation;

    private FastCoxAssociation(CoxSurvivalData survival, int observations,
            int covariateCount, double[] covariates, double[] offset,
            CoxOptions coxOptions, CoxResult nullModel,CoxAdaptiveLikelihood.Result nullLikelihood,
            List<String> clusterIds, double[] scoreCorrelation) {
        this.survival = survival;
        this.observations = observations;
        this.covariateCount = covariateCount;
        this.covariates = covariates;
        this.offset = offset;
        this.coxOptions = coxOptions;
        this.nullModel = nullModel;
        this.nullLikelihood = nullLikelihood;
        this.nullCoefficients=nullModel.beta();this.nullCovariance=nullModel.covariance();double[] units=new double[covariateCount];
        for(int j=0;j<covariateCount;j++){
            double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;for(int i=0;i<observations;i++){low=Math.min(low,covariates[i*covariateCount+j]);high=Math.max(high,covariates[i*covariateCount+j]);}
            double center=.5*low+.5*high,unit=Math.max(Math.abs(high-center),Math.abs(low-center));units[j]=unit;
            if(!(unit>0)||!Double.isFinite(unit))throw new IllegalArgumentException("nonvarying or unrepresentable Cox null covariate");
            for(int i=0;i<observations;i++)this.covariates[i*covariateCount+j]=(this.covariates[i*covariateCount+j]-center)/unit;
            nullCoefficients[j]*=unit;if(!Double.isFinite(nullCoefficients[j]))throw new IllegalArgumentException("unrepresentable standardized Cox null coefficient");
        }
        for(int j=0;j<covariateCount;j++)for(int k=0;k<covariateCount;k++){
            nullCovariance[j*covariateCount+k]=(nullCovariance[j*covariateCount+k]*units[j])*units[k];
            if(!Double.isFinite(nullCovariance[j*covariateCount+k])||j==k&&!(nullCovariance[j*covariateCount+k]>0))throw new IllegalArgumentException("Cox null covariance is not representable in source units; rescale covariates");
        }
        this.rightCensoredPlan = CoxRiskSetPlan.prepare(survival);
        this.countingProcessPlan = rightCensoredPlan == null
            ? CoxCountingProcessPlan.prepare(survival) : null;
        this.clusterIds = clusterIds;
        this.scoreCorrelation = scoreCorrelation;
    }

    public static FastCoxAssociation prepare(
            CoxSurvivalData survival, double[][] covariates,
            double[] offset, CoxOptions coxOptions,
            AssociationEngineOptions engineOptions) {
        return prepare(survival, covariates, offset, coxOptions,
            engineOptions, null, null);
    }

    /**
     * Prepares one null fit. Cluster IDs enable sandwich score variance;
     * an optional row-major observation correlation enables relatedness-aware
     * score variance.
     */
    public static FastCoxAssociation prepare(
            CoxSurvivalData survival, double[][] covariates,
            double[] offset, CoxOptions coxOptions,
            AssociationEngineOptions engineOptions,
            List<String> clusterIds, double[] scoreCorrelation) {
        return prepare(survival,covariates,offset,coxOptions,engineOptions,clusterIds,scoreCorrelation,CoxLikelihoodExistence.Options.defaults());
    }
    /** Explicit certificate bounds; no score scan is prepared from an uncertified nuisance maximum. */
    public static FastCoxAssociation prepare(CoxSurvivalData survival,double[][] covariates,double[] offset,CoxOptions coxOptions,
            AssociationEngineOptions engineOptions,List<String> clusterIds,double[] scoreCorrelation,CoxLikelihoodExistence.Options existenceOptions) {
        if (survival == null || covariates == null || coxOptions == null
                || engineOptions == null
                || covariates.length != survival.observations())
            throw new IllegalArgumentException(
                "Cox score-scan inputs are required and row-aligned");
        int rows = survival.observations();
        int columns=covariates[0]==null?-1:covariates[0].length;
        double[] fixed;
        if(columns==0){for(double[] row:covariates)if(row==null||row.length!=0)throw new IllegalArgumentException("rectangular null-only Cox design required");fixed=new double[0];}
        else fixed = MatrixOps.rowMajor(covariates, rows);
        double[] offsets = offset == null ? new double[rows] : offset.clone();
        if (offsets.length != rows)
            throw new IllegalArgumentException("one Cox offset is required per row");
        if (clusterIds != null && (clusterIds.size() != rows
                || clusterIds.stream().anyMatch(value -> value == null)))
            throw new IllegalArgumentException(
                "one nonnull cluster id is required per row");
        if (scoreCorrelation != null
                && scoreCorrelation.length != Math.multiplyExact(rows, rows))
            throw new IllegalArgumentException(
                "score correlation must be an observation-square matrix");
        for(double value:offsets)if(!Double.isFinite(value))throw new IllegalArgumentException("finite Cox offsets required");
        var likelihood=CoxAdaptiveLikelihood.analyze(survival,covariates,existenceOptions);if(!likelihood.finiteMaximum())throw new CoxLikelihoodException(likelihood.proof());
        CoxResult nullModel = CoxRegression.fit(survival, covariates,
            offsets, coxOptions, engineOptions.backendPolicy());
        if (!nullModel.converged())
            throw new IllegalArgumentException("Cox null model did not converge: "
                + nullModel.convergenceMessage());
        return new FastCoxAssociation(survival, rows, covariates[0].length,
            fixed, offsets, coxOptions, nullModel,likelihood,
            clusterIds == null ? null : List.copyOf(clusterIds),
            scoreCorrelation == null ? null : scoreCorrelation.clone());
    }

    public AssociationBatchResult scan(double[][] predictors,
            List<String> predictorNames, AssociationEngineOptions options,
            CoxScoreVariance variance) {
        if (predictors == null || predictors.length != observations)
            throw new IllegalArgumentException(
                "predictor rows must equal null-model observations");
        return scan(MatrixOps.rowMajorUnchecked(predictors, observations),
            predictors[0].length, predictorNames, options, variance);
    }

    public AssociationBatchResult scan(double[] predictors, int predictorCount,
            List<String> predictorNames, AssociationEngineOptions options,
            CoxScoreVariance variance) {
        if (predictors == null || predictorCount < 1
                || predictors.length != observations * predictorCount
                || options == null || variance == null)
            throw new IllegalArgumentException(
                "predictor dimensions and score controls are required");
        if (variance == CoxScoreVariance.CLUSTER_ROBUST && clusterIds == null)
            throw new IllegalArgumentException(
                "cluster-robust score variance requires cluster IDs");
        if (variance == CoxScoreVariance.RELATEDNESS
                && scoreCorrelation == null)
            throw new IllegalArgumentException(
                "relatedness score variance requires a score correlation");
        List<String> names = names(predictorNames, predictorCount);
        double[] beta = nan(predictorCount);
        double[] standardErrors = nan(predictorCount);
        ConcurrentLinkedQueue<AssociationFailure> failures =
            new ConcurrentLinkedQueue<>();
        long started = System.nanoTime();
        int blocks = (predictorCount + options.chunkSize() - 1)
            / options.chunkSize();
        execute(blocks, options.parallelism(), block -> {
            int first = block * options.chunkSize();
            int count = Math.min(options.chunkSize(), predictorCount - first);
            try {
                evaluateBlock(predictors, predictorCount, first, count,
                    names, options, variance, beta, standardErrors, failures);
            } catch (RuntimeException exception) {
                if (options.failurePolicy() == AssociationFailurePolicy.FAIL_FAST)
                    throw exception;
                for (int index = first; index < first + count; index++)
                    failures.add(new AssociationFailure(index, names.get(index),
                        exception.getClass().getSimpleName(), message(exception)));
            }
        });
        AssociationStatistics statistics = AssociationStatistics.normal(
            beta, standardErrors);
        List<AssociationFailure> ordered = failures.stream()
            .sorted(Comparator.comparingInt(AssociationFailure::index)).toList();
        return new AssociationBatchResult(names, statistics.beta(),
            statistics.standardErrors(), statistics.statistics(),
            statistics.pValues(), statistics.degreesOfFreedom(),
            statistics.negativeLog10PValues(), ordered, covariateCount,
            Math.min(blocks, options.parallelism()),
            System.nanoTime() - started);
    }

    public CoxResult nullModel() { return nullModel; }
    public CoxAdaptiveLikelihood.Result nullLikelihood() { return nullLikelihood; }
    public BackendProvenance backend() { return nullModel.backend(); }
    public int observations() { return observations; }

    /** Full efficient score/information at the fitted Cox null, variant-major
     * finite inputs. Model-based covariance only; no cluster/relatedness or
     * rare-event tail correction is implied. Covariate score is projected out. */
    public org.jlinalg.settest.SetTestScoreState score(double[][] variants) {
        if(variants==null || variants.length==0)throw new IllegalArgumentException("variant rows required");
        if(clusterIds!=null || scoreCorrelation!=null)throw new IllegalArgumentException("full score export currently requires model-based Cox variance");
        int m=variants.length,p=covariateCount,k=p+m;
        double[] design=new double[observations*k],coefficients=new double[k];
        System.arraycopy(nullCoefficients,0,coefficients,0,p);double[] units=new double[m];
        for(int i=0;i<observations;i++)System.arraycopy(covariates,i*p,design,i*k,p);
        for(int j=0;j<m;j++) {
            if(variants[j]==null || variants[j].length!=observations)throw new IllegalArgumentException("variant sample alignment differs from null");
            double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
            for(int i=0;i<observations;i++) {
                if(!Double.isFinite(variants[j][i]))throw new IllegalArgumentException("finite imputed variants required");
                low=Math.min(low,variants[j][i]);high=Math.max(high,variants[j][i]);
            }
            double center=.5*low+.5*high,unit=Math.max(Math.abs(high-center),Math.abs(low-center));if(unit==0)unit=1;units[j]=unit;
            for(int i=0;i<observations;i++)design[i*k+p+j]=(variants[j][i]-center)/unit;
        }
        var evaluation=CoxPartialLikelihood.evaluate(survival,design,k,coefficients,offset,
            coxOptions.ties(),rightCensoredPlan,countingProcessPlan);
        double[] inverse=nullCovariance,information=evaluation.information(),scores=evaluation.score();
        double[] u=new double[m],v=new double[m*m];double[][] projection=new double[m][p];
        for(int j=0;j<m;j++) {
            for(int c=0;c<p;c++)for(int d=0;d<p;d++)projection[j][c]+=information[(p+j)*k+d]*inverse[d*p+c];
            u[j]=scores[p+j];for(int c=0;c<p;c++)u[j]-=projection[j][c]*scores[c];
            for(int h=0;h<=j;h++) {
                double value=information[(p+j)*k+p+h];
                for(int c=0;c<p;c++)value-=projection[j][c]*information[c*k+p+h];
                v[j*m+h]=v[h*m+j]=value;
            }
        }
        for(int j=0;j<m;j++){
            u[j]*=units[j];if(!Double.isFinite(u[j]))throw new IllegalArgumentException("Cox score is not representable in source units; rescale predictors");
            for(int k2=0;k2<=j;k2++){
                double original=v[j*m+k2],restored=(original*units[j])*units[k2];
                if(!Double.isFinite(restored)||j==k2&&original>0&&!(restored>0))throw new IllegalArgumentException("Cox score covariance is not representable in source units; rescale predictors");
                v[j*m+k2]=restored;v[k2*m+j]=restored;
            }
        }
        return new org.jlinalg.settest.SetTestScoreState(u,v,m);
    }

    private void evaluateBlock(double[] predictors, int total, int first,
            int count, List<String> names, AssociationEngineOptions options,
            CoxScoreVariance variance, double[] beta, double[] standardErrors,
            ConcurrentLinkedQueue<AssociationFailure> failures) {
        int columns = covariateCount + count;
        double[] design = new double[observations * columns];
        boolean[] valid = new boolean[count];
        double[] units=new double[count];
        Arrays.fill(valid, true);
        for (int row = 0; row < observations; row++)
            System.arraycopy(covariates, row * covariateCount, design,
                row * columns, covariateCount);
        for (int variable = 0; variable < count; variable++) {
            int source = first + variable;
            double low=Double.POSITIVE_INFINITY,high=Double.NEGATIVE_INFINITY;
            int finite = 0;
            int missing = -1;
            for (int row = 0; row < observations; row++) {
                double value = predictors[row * total + source];
                if (Double.isFinite(value)) { low=Math.min(low,value);high=Math.max(high,value);finite++; }
                else if (missing < 0) missing = row;
            }
            String failure = finite == 0 ? "predictor has no finite values"
                : missing >= 0 && options.predictorMissingPolicy()
                    == VariableMissingPolicy.ERROR
                    ? "non-finite predictor at row " + missing : null;
            if (failure != null) {
                fail(source, names.get(source), failure, options, failures);
                valid[variable] = false;
                continue;
            }
            double center=.5*low+.5*high,unit=Math.max(Math.abs(high-center),Math.abs(low-center));
            if(!(unit>0)||!Double.isFinite(unit)){fail(source,names.get(source),"predictor is constant or unrepresentable",options,failures);valid[variable]=false;continue;}units[variable]=unit;
            double mean=0;for(int row=0;row<observations;row++){double value=predictors[row*total+source];if(Double.isFinite(value))mean+=(value-center)/unit;}mean/=finite;
            for (int row = 0; row < observations; row++) {
                double value = predictors[row * total + source];
                design[row * columns + covariateCount + variable] =
                    Double.isFinite(value) ? (value-center)/unit : mean;
            }
        }
        double[] coefficients = new double[columns];
        System.arraycopy(nullCoefficients, 0, coefficients, 0, covariateCount);
        CoxPartialLikelihood.Evaluation evaluation =
            CoxPartialLikelihood.evaluate(survival, design, columns,
                coefficients, offset, coxOptions.ties(), rightCensoredPlan,
                countingProcessPlan);
        double[] residuals = variance == CoxScoreVariance.MODEL_BASED ? null
            : CoxDiagnostics.scoreResiduals(survival, design, columns,
                coefficients, offset, coxOptions.ties());
        for (int variable = 0; variable < count; variable++) {
            if (!valid[variable]) continue;
            int destination = first + variable;
            int predictor = covariateCount + variable;
            double[] projection = new double[covariateCount];
            for (int left = 0; left < covariateCount; left++)
                for (int right = 0; right < covariateCount; right++)
                    projection[left] += evaluation.information()[
                        predictor * columns + right]
                        * nullCovariance[right * covariateCount + left];
            double information = evaluation.information()[
                predictor * columns + predictor];
            for (int column = 0; column < covariateCount; column++)
                information -= projection[column]
                    * evaluation.information()[column * columns + predictor];
            if (!(information > 1e-14) || !Double.isFinite(information)) {
                fail(destination, names.get(destination),
                    "predictor is constant or collinear with null covariates",
                    options, failures);
                continue;
            }
            double score = evaluation.score()[predictor];
            for(int column=0;column<covariateCount;column++)score-=projection[column]*evaluation.score()[column];
            double estimate=score/information/units[variable];
            double varianceValue = 1.0 / information;
            if (residuals != null) {
                double[] efficient = new double[observations];
                for (int row = 0; row < observations; row++) {
                    efficient[row] = residuals[row * columns + predictor];
                    for (int column = 0; column < covariateCount; column++)
                        efficient[row] -= projection[column]
                            * residuals[row * columns + column];
                }
                double meat = variance == CoxScoreVariance.CLUSTER_ROBUST
                    ? clusterMeat(efficient) : relatednessMeat(efficient);
                varianceValue = meat / (information * information);
            }
            double se=Math.sqrt(Math.max(0.0,varianceValue))/units[variable];
            if(!Double.isFinite(estimate)||!Double.isFinite(se)||!(se>0)){fail(destination,names.get(destination),"Cox one-step effect/SE is not representable in source units",options,failures);continue;}
            beta[destination]=estimate;standardErrors[destination]=se;
        }
    }

    private double clusterMeat(double[] score) {
        Map<String, Double> sums = new LinkedHashMap<>();
        for (int row = 0; row < observations; row++)
            sums.merge(clusterIds.get(row), score[row], Double::sum);
        double result = sums.values().stream()
            .mapToDouble(value -> value * value).sum();
        return result;
    }

    private double relatednessMeat(double[] score) {
        double result = 0.0;
        for (int row = 0; row < observations; row++)
            for (int column = 0; column < observations; column++)
                result += score[row]
                    * scoreCorrelation[row * observations + column]
                    * score[column];
        return result;
    }

    private static void fail(int index, String name, String message,
            AssociationEngineOptions options,
            ConcurrentLinkedQueue<AssociationFailure> failures) {
        if (options.failurePolicy() == AssociationFailurePolicy.FAIL_FAST)
            throw new IllegalArgumentException(name + ": " + message);
        failures.add(new AssociationFailure(index, name,
            "NonEstimablePredictor", message));
    }

    private static List<String> names(List<String> supplied, int count) {
        if (supplied == null) {
            List<String> result = new ArrayList<>(count);
            for (int index = 0; index < count; index++)
                result.add("variable" + (index + 1));
            return List.copyOf(result);
        }
        if (supplied.size() != count || supplied.stream()
                .anyMatch(value -> value == null || value.isBlank()))
            throw new IllegalArgumentException(
                "one nonblank name is required per predictor");
        return List.copyOf(supplied);
    }

    private static void execute(int blocks, int parallelism,
            java.util.function.IntConsumer operation) {
        ForkJoinPool pool = new ForkJoinPool(Math.min(blocks, parallelism));
        try {
            pool.submit(() -> IntStream.range(0, blocks).parallel()
                .forEach(operation)).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cox score scan was interrupted",
                exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Cox score scan failed", cause);
        } finally {
            pool.shutdownNow();
        }
    }

    private static String message(RuntimeException exception) {
        return exception.getMessage() == null ? "" : exception.getMessage();
    }

    private static double[] nan(int size) {
        double[] result = new double[size];
        Arrays.fill(result, Double.NaN);
        return result;
    }
}
