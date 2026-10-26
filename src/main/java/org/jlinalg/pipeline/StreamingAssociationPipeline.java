/*
 * Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.pipeline;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jlinalg.association.AssociationBatchResult;
import org.jlinalg.association.AssociationEngineOptions;
import org.jlinalg.association.AssociationEstimate;
import org.jlinalg.association.AssociationFailure;
import org.jlinalg.association.FastGlmAssociation;
import org.jlinalg.association.FastOlsAssociation;
import org.jlinalg.glm.GlmFamily;
import org.jlinalg.glm.GlmOptions;
import org.jlinalg.gwas.AssociationScanOptions;
import org.jlinalg.gwas.AssociationScanResult;
import org.jlinalg.gwas.RemlAssociationScanner;
import org.jlinalg.ols.OlsOptions;

/**
 * Bounded-memory single-trait GWAS scanner over any {@link VariantSource}.
 *
 * <p>Filtering is calculated after sample alignment and before mean imputation.
 * Results remain in source order. The fast OLS path reuses covariates within
 * each block; the REML path reuses one P3D/EMMAX null model across all
 * blocks.</p>
 */
public final class StreamingAssociationPipeline {
    private StreamingAssociationPipeline() { }

    /** Finite conditional hard-call tests with optional, separately labelled Firth effect estimates. */
    public static AssociationPipelineSummary exactConditionalLogitTo(VariantSource source,List<String> ids,double[] response,double[][] covariates,
            int[] strata,double[] offset,org.jlinalg.regression.ExactConditionalLogit.Options controls,org.jlinalg.regression.FirthLogistic.Options effectControls,
            int effectMaximumParameters,AssociationEngineOptions engine,AssociationPipelineOptions pipeline,AssociationPipelineSink sink)throws IOException {
        validate(source,ids,response,covariates,pipeline);
        if(controls==null||effectControls==null||engine==null||effectMaximumParameters<0)throw new IllegalArgumentException("Exact test, effect and execution controls are required");
        if(!java.util.Set.of(org.jlinalg.compute.BackendPolicy.CPU,org.jlinalg.compute.BackendPolicy.AUTO,org.jlinalg.compute.BackendPolicy.PREFERRED).contains(engine.backendPolicy()))throw new IllegalArgumentException("Exact conditional inference requires a CPU-compatible backend policy");
        ExactConditionalGwas.validateDesign(covariates,strata);
        return scanDetailedTo(source,ids,pipeline,(variants,names,included)->{var result=ExactConditionalGwas.scan(response,strata,offset,included,controls,effectControls,effectMaximumParameters,engine,pipeline.variantFilter().quality());return new BlockResult(result.estimates(),result.failures(),result.inference());},sink);
    }

    /** Fits full and coefficient-constrained Firth logit models for each eligible
     * variant. Both fits retain the full design in the Jeffreys penalty. */
    public static AssociationPipelineSummary firthLogisticTo(VariantSource source,List<String> ids,
            double[] response,double[][] covariates,double[] offset,org.jlinalg.regression.FirthLogistic.Options controls,
            AssociationEngineOptions engine,AssociationPipelineOptions pipeline,AssociationPipelineSink sink)throws IOException {
        validate(source,ids,response,covariates,pipeline);
        if(engine==null||controls==null)throw new IllegalArgumentException("Firth and execution controls are required");
        if(!java.util.Set.of(org.jlinalg.compute.BackendPolicy.CPU,org.jlinalg.compute.BackendPolicy.AUTO,org.jlinalg.compute.BackendPolicy.PREFERRED).contains(engine.backendPolicy()))
            throw new IllegalArgumentException("Firth inference currently requires a portable CPU-compatible backend policy");
        return scanTo(source,ids,pipeline,(variants,names)->{
            int count=names.size(),p=covariates[0].length+1;
            var tests=new org.jlinalg.regression.FirthLogistic.Test[count];var errors=new RuntimeException[count];
            Runnable work=()->java.util.stream.IntStream.range(0,count).parallel().forEach(index->{
                try {
                    double[][] design=new double[response.length][p];
                    double total=0;int called=0;
                    for(double[] row:variants)if(Double.isFinite(row[index])){total+=row[index];called++;}
                    if(called==0)throw new IllegalArgumentException("Firth predictor has no called values");double mean=total/called;
                    for(int i=0;i<response.length;i++) {
                        System.arraycopy(covariates[i],0,design[i],0,p-1);double dosage=variants[i][index];
                        if(Double.isNaN(dosage)){if(engine.predictorMissingPolicy()==org.jlinalg.association.VariableMissingPolicy.ERROR)throw new IllegalArgumentException("Missing Firth predictor value");dosage=mean;}
                        design[i][p-1]=dosage;
                    }
                    tests[index]=org.jlinalg.regression.FirthLogistic.test(response,design,offset,p-1,0,controls);
                }catch(RuntimeException failure){errors[index]=failure;}
            });
            var pool=new java.util.concurrent.ForkJoinPool(Math.min(count,engine.parallelism()));
            try{pool.submit(work).join();}finally{pool.shutdown();}
            List<AssociationEstimate> estimates=new ArrayList<>();List<AssociationFailure> failures=new ArrayList<>();List<java.util.Map<String,String>> metadata=new ArrayList<>();
            for(int index=0;index<count;index++) {
                var test=tests[index];var details=new java.util.LinkedHashMap<String,String>();
                details.put("test_method","firth_penalized_likelihood_ratio");details.put("calibration","asymptotic_chi_square_1df");details.put("estimator","firth_jeffreys_logit");
                details.put("standard_error_method","ordinary_fisher_at_penalized_estimate");details.put("statistic_type","chi_square");
                if(test!=null) {
                    details.put("fit_converged",Boolean.toString(test.full().converged()));details.put("null_fit_converged",Boolean.toString(test.constrained().converged()));
                    details.put("fit_iterations",Integer.toString(test.full().iterations()));details.put("null_fit_iterations",Integer.toString(test.constrained().iterations()));
                    details.put("penalized_log_likelihood",Double.toString(test.full().penalizedLogLikelihood()));details.put("null_penalized_log_likelihood",Double.toString(test.constrained().penalizedLogLikelihood()));
                    details.put("convergence_reason",test.status()+":"+test.full().reason()+":"+test.constrained().reason());
                    if(test.converged())details.put("log_p_value",Double.toString(test.logPValue()));
                }else details.put("convergence_reason","fit_error");
                metadata.add(java.util.Map.copyOf(details));
                if(errors[index]!=null||!test.converged()) {
                    RuntimeException error=errors[index]!=null?errors[index]:new IllegalArgumentException("Firth full/constrained optimization did not converge: "+details.get("convergence_reason"));
                    if(engine.failurePolicy()==org.jlinalg.association.AssociationFailurePolicy.FAIL_FAST)throw error;
                    failures.add(new AssociationFailure(index,names.get(index),error.getClass().getSimpleName(),error.getMessage()));
                    estimates.add(new AssociationEstimate(names.get(index),Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN));
                }else estimates.add(new AssociationEstimate(names.get(index),test.beta(),test.standardError(),test.likelihoodRatio(),test.pValue(),test.logPValue()/Math.log(10),-test.logPValue()/Math.log(10),Double.NaN));
            }
            return new BlockResult(estimates,failures,metadata);
        },sink);
    }

    /** Streams model-based Cox score tests through a prepared cohort null. */
    public static AssociationPipelineSummary fastCoxTo(VariantSource source,List<String> ids,
            org.jlinalg.survival.FastCoxAssociation model,AssociationEngineOptions engine,
            AssociationPipelineOptions pipeline,AssociationPipelineSink sink) throws IOException {
        if(source==null || ids==null || model==null || engine==null || pipeline==null || sink==null
                || ids.size()!=model.observations())
            throw new IllegalArgumentException("Cox source, aligned sample IDs, prepared null, options, and sink are required");
        return scanTo(source,ids,pipeline,(variants,names)->BlockResult.of(model.scan(
            variants,names,engine,org.jlinalg.survival.CoxScoreVariance.MODEL_BASED)),sink);
    }

    public static AssociationPipelineResult fastOls(
            VariantSource source,
            List<String> analysisSampleIds,
            double[] response,
            double[][] covariates,
            double[] weights,
            double[] offset,
            OlsOptions olsOptions,
            AssociationEngineOptions engineOptions,
            AssociationPipelineOptions pipelineOptions) throws IOException {
        validate(source, analysisSampleIds, response, covariates,
            pipelineOptions);
        return collect(source, analysisSampleIds, pipelineOptions,
            (variants, names) -> BlockResult.of(
                FastOlsAssociation.scanPredictors(
                    response, covariates, variants, names, weights, offset,
                    olsOptions, engineOptions)));
    }

    public static AssociationPipelineSummary fastOlsTo(
            VariantSource source,
            List<String> analysisSampleIds,
            double[] response,
            double[][] covariates,
            double[] weights,
            double[] offset,
            OlsOptions olsOptions,
            AssociationEngineOptions engineOptions,
            AssociationPipelineOptions pipelineOptions,
            AssociationPipelineSink sink) throws IOException {
        validate(source, analysisSampleIds, response, covariates,
            pipelineOptions);
        return scanTo(source, analysisSampleIds, pipelineOptions,
            (variants, names) -> BlockResult.of(
                FastOlsAssociation.scanPredictors(
                    response, covariates, variants, names, weights, offset,
                    olsOptions, engineOptions)), sink);
    }

    /**
     * Fits one covariate-only GLM and streams efficient-score tests for all
     * variants. The prepared null model is reused across every input block.
     */
    public static AssociationPipelineSummary fastGlmTo(
            VariantSource source,
            List<String> analysisSampleIds,
            double[] response,
            double[][] covariates,
            GlmFamily family,
            double[] weights,
            double[] offset,
            GlmOptions glmOptions,
            AssociationEngineOptions engineOptions,
            AssociationPipelineOptions pipelineOptions,
            AssociationPipelineSink sink) throws IOException {
        validate(source, analysisSampleIds, response, covariates,
            pipelineOptions);
        FastGlmAssociation prepared = FastGlmAssociation.prepare(response,
            covariates, family, weights, offset, glmOptions, engineOptions);
        return scanTo(source, analysisSampleIds, pipelineOptions,
            (variants, names) -> BlockResult.of(
                prepared.scan(variants, names, engineOptions)), sink);
    }

    public static AssociationPipelineResult remlP3d(
            VariantSource source,
            List<String> analysisSampleIds,
            RemlAssociationScanner scanner,
            AssociationScanOptions scanOptions,
            AssociationPipelineOptions pipelineOptions) throws IOException {
        if (scanner == null || scanOptions == null)
            throw new IllegalArgumentException("REML scanner and options are required");
        if (source == null || analysisSampleIds == null
                || analysisSampleIds.isEmpty() || pipelineOptions == null)
            throw new IllegalArgumentException(
                "source, analysis samples, and pipeline options are required");
        return collect(source, analysisSampleIds, pipelineOptions,
            (variants, names) -> {
                AssociationScanResult result =
                    scanner.scan(variants, names, scanOptions);
                return BlockResult.of(result);
            });
    }

    public static AssociationPipelineSummary remlP3dTo(
            VariantSource source,
            List<String> analysisSampleIds,
            RemlAssociationScanner scanner,
            AssociationScanOptions scanOptions,
            AssociationPipelineOptions pipelineOptions,
            AssociationPipelineSink sink) throws IOException {
        if (scanner == null || scanOptions == null)
            throw new IllegalArgumentException("REML scanner and options are required");
        if (source == null || analysisSampleIds == null
                || analysisSampleIds.isEmpty() || pipelineOptions == null)
            throw new IllegalArgumentException(
                "source, analysis samples, and pipeline options are required");
        return scanTo(source, analysisSampleIds, pipelineOptions,
            (variants, names) -> BlockResult.of(
                scanner.scan(variants, names, scanOptions)), sink);
    }

    private static AssociationPipelineResult collect(
            VariantSource source,
            List<String> analysisSampleIds,
            AssociationPipelineOptions options,
            BlockScanner scanner) throws IOException {
        List<AssociationPipelineEstimate> estimates = new ArrayList<>();
        List<VariantFilterResult> excluded = new ArrayList<>();
        List<AssociationPipelineFailure> failures = new ArrayList<>();
        AssociationPipelineSummary summary = scanTo(source, analysisSampleIds,
            options, scanner, new AssociationPipelineSink() {
                @Override public void acceptEstimate(
                        AssociationPipelineEstimate estimate) {
                    estimates.add(estimate);
                }
                @Override public void acceptExcluded(
                        VariantFilterResult result) {
                    excluded.add(result);
                }
                @Override public void acceptFailure(
                        AssociationPipelineFailure failure) {
                    failures.add(failure);
                }
            });
        return new AssociationPipelineResult(summary.sourceVariants(), estimates,
            excluded, failures);
    }

    private static AssociationPipelineSummary scanTo(
            VariantSource source,
            List<String> analysisSampleIds,
            AssociationPipelineOptions options,
            BlockScanner scanner,
            AssociationPipelineSink sink) throws IOException {
        return scanDetailedTo(source,analysisSampleIds,options,(variants,names,included)->scanner.scan(variants,names),sink);
    }
    private static AssociationPipelineSummary scanDetailedTo(VariantSource source,List<String> analysisSampleIds,AssociationPipelineOptions options,DetailedBlockScanner scanner,AssociationPipelineSink sink)throws IOException {
        if (sink == null)
            throw new IllegalArgumentException("association sink is required");
        int[] sampleOrder = SampleAlignment.requireOrder(
            source.metadata().sampleIds(), analysisSampleIds);
        long sourceVariants = 0;
        long tested = 0;
        long excludedCount = 0;
        long failureCount = 0;
        try (VariantBlockReader reader = source.open(sampleOrder)) {
            for (VariantBlock block;
                    (block = reader.read(options.variantBlockSize())) != null;) {
                sourceVariants += block.variants().size();
                List<VariantFilterResult> included = new ArrayList<>();
                List<VariantFilterResult> filteredBlock = new ArrayList<>();
                for (VariantRecord variant : block.variants()) {
                    VariantFilterResult result = VariantFilters.evaluate(
                        variant, options.variantFilter());
                    filteredBlock.add(result);
                    if (result.included()) included.add(result);
                }
                List<String> names = included.stream()
                    .map(result -> result.variant().id()).toList();
                BlockResult blockResult = included.isEmpty() ? new BlockResult(List.of(), List.of())
                    : scanner.scan(transpose(included, analysisSampleIds.size()), names,included);
                AssociationFailure[] failures = new AssociationFailure[included.size()];
                for (AssociationFailure failure : blockResult.failures()) failures[failure.index()] = failure;
                int index = 0;
                for (VariantFilterResult filtered : filteredBlock) {
                    if (!filtered.included()) {
                        sink.acceptExcluded(filtered);
                        excludedCount++;
                        continue;
                    }
                    AssociationFailure failure = failures[index];
                    AssociationEstimate estimate = blockResult.estimate(index++);
                    boolean testOnly="p_value_only".equals(blockResult.inference().get(index-1).get("result_type"));
                    if (failure != null || !testOnly&&(!Double.isFinite(estimate.beta())
                            || !Double.isFinite(estimate.standardError()))
                            || !Double.isFinite(estimate.pValue())) {
                        sink.acceptFailure(new AssociationPipelineFailure(filtered.variant().id(),
                            failure == null ? "NonEstimablePredictor" : failure.exceptionType(),
                            failure == null ? "variant is not estimable after adjustment" : failure.message(),
                            filtered.variant(),filtered.statistics(),blockResult.inference().get(index-1)));
                        failureCount++;
                        continue;
                    }
                    AssociationPipelineEstimate output =
                        new AssociationPipelineEstimate(
                        filtered.variant(), filtered.statistics(),
                        estimate.beta(), estimate.standardError(),
                        estimate.statistic(), estimate.degreesOfFreedom(),
                        estimate.pValue(), estimate.log10PValue(),
                        estimate.negativeLog10PValue(),blockResult.inference().get(index-1));
                    sink.acceptEstimate(output);
                    tested++;
                }
            }
        }
        return new AssociationPipelineSummary(sourceVariants, tested,
            excludedCount, failureCount);
    }

    private static double[][] transpose(
            List<VariantFilterResult> variants, int samples) {
        double[][] result = new double[samples][variants.size()];
        for (int variant = 0; variant < variants.size(); variant++) {
            double[] dosages = variants.get(variant).variant().dosagesView();
            if (dosages.length != samples)
                throw new IllegalArgumentException(
                    "variant dosage count does not match aligned samples");
            for (int sample = 0; sample < samples; sample++)
                result[sample][variant] = dosages[sample];
        }
        return result;
    }

    private static void validate(
            VariantSource source, List<String> analysisSampleIds,
            double[] response, double[][] covariates,
            AssociationPipelineOptions options) {
        if (source == null || analysisSampleIds == null
                || analysisSampleIds.isEmpty() || response == null
                || covariates == null || options == null)
            throw new IllegalArgumentException("pipeline inputs are required");
        if (response.length != analysisSampleIds.size()
                || covariates.length != analysisSampleIds.size())
            throw new IllegalArgumentException(
                "response and covariate rows must match analysis samples");
    }

    @FunctionalInterface
    private interface DetailedBlockScanner {BlockResult scan(double[][] variants,List<String> names,List<VariantFilterResult> included);}
    @FunctionalInterface
    private interface BlockScanner {
        BlockResult scan(double[][] variants, List<String> names);
    }

    private record BlockResult(
            List<AssociationEstimate> estimates,
            List<AssociationFailure> failures,List<java.util.Map<String,String>> inference) {
        private BlockResult(List<AssociationEstimate> estimates,List<AssociationFailure> failures) {
            this(estimates,failures,java.util.Collections.nCopies(estimates.size(),java.util.Map.of()));
        }
        private AssociationEstimate estimate(int index) {
            return estimates.get(index);
        }
        private static BlockResult of(AssociationBatchResult result) {
            List<AssociationEstimate> estimates =
                new ArrayList<>(result.names().size());
            for (int index = 0; index < result.names().size(); index++)
                estimates.add(result.estimate(index));
            return new BlockResult(estimates, result.failures());
        }
        private static BlockResult of(AssociationScanResult result) {
            List<AssociationEstimate> estimates =
                new ArrayList<>(result.markerNames().size());
            double[] beta = result.beta();
            double[] standardErrors = result.standardErrors();
            double[] statistics = result.tStatistics();
            double[] p = result.pValues();
            double[] logP = result.log10PValues();
            double[] minusLogP = result.negativeLog10PValues();
            double[] degrees = result.statistics().degreesOfFreedom();
            for (int index = 0; index < beta.length; index++) {
                estimates.add(new AssociationEstimate(
                    result.markerNames().get(index), beta[index],
                    standardErrors[index], statistics[index], p[index],
                    logP[index], minusLogP[index], degrees[index]));
            }
            return new BlockResult(estimates, List.of());
        }
    }
}
