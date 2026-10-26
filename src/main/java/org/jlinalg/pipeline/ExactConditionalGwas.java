/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.association.*;
import org.jlinalg.regression.ExactConditionalLogit;
import org.jlinalg.regression.FirthLogistic;
import org.jlinalg.stats.Alternative;

/** Exact-test blocks; optional approximate effect estimation never gates a valid test. */
final class ExactConditionalGwas {
    private ExactConditionalGwas() { }
    record Block(List<AssociationEstimate> estimates,List<AssociationFailure> failures,List<Map<String,String>> inference) { }
    static void validateDesign(double[][] covariates,int[] strata) {
        int n=covariates.length;if(strata==null||strata.length!=n)throw new IllegalArgumentException("Exact stratum labels must align with the analysis sample");
        var patterns=new LinkedHashMap<Integer,double[]>();
        for(int i=0;i<n;i++){double[] before=patterns.putIfAbsent(strata[i],covariates[i]);if(before!=null&&!java.util.Arrays.equals(before,covariates[i]))throw new IllegalArgumentException("Exact conditional inference supports stratum intercepts and known offsets; other covariates cannot be silently dropped");}
        int groups=patterns.size(),p=covariates[0].length;if(p!=groups)throw new IllegalArgumentException("The declared exact null design must span the stratum intercepts");
        var rows=new ArrayList<>(patterns.values());var indicators=new java.util.HashSet<Integer>();int intercepts=0;
        for(int j=0;j<p;j++) {
            int selected=-1,ones=0;
            for(int i=0;i<groups;i++){double value=rows.get(i)[j];if(value!=0&&value!=1)throw new IllegalArgumentException("Exact stratum intercepts require ordinary reference/indicator contrasts");if(value==1){ones++;selected=i;}}
            if(ones==groups)intercepts++;
            else if(ones!=1||!indicators.add(selected))throw new IllegalArgumentException("Exact stratum nuisance design is redundant or unsupported");
        }
        if(intercepts>1||indicators.size()+intercepts!=groups)throw new IllegalArgumentException("Exact stratum nuisance design is incomplete");
    }
    static Block scan(double[] y,int[] strata,double[] offset,List<VariantFilterResult> included,
            ExactConditionalLogit.Options controls,FirthLogistic.Options effectControls,int effectMaximumParameters,
            AssociationEngineOptions engine,VariantQualityOptions quality) {
        int count=included.size();var estimates=new AssociationEstimate[count];var errors=new RuntimeException[count];
        List<Map<String,String>> metadata=new ArrayList<>(java.util.Collections.nCopies(count,Map.of()));
        Runnable work=()->java.util.stream.IntStream.range(0,count).parallel().forEach(index->{
            var details=new LinkedHashMap<String,String>();details.put("test_method","exact_conditional_genotype_score");details.put("calibration","finite_conditional_stratum_case_counts");details.put("statistic_type","conditional_centered_allele_score");details.put("exact_alternative","two_sided_probability_ordering");
            try {
                var variant=included.get(index).variant();var origin=variant.genotypeMetadata().origin();
                if(origin==VariantGenotypeMetadata.Origin.DOSAGE||origin==VariantGenotypeMetadata.Origin.MIXED||origin==VariantGenotypeMetadata.Origin.UNSPECIFIED&&!quality.genotypeKind().equals("hard_calls"))
                    throw new IllegalArgumentException("Exact conditional inference needs observed hard calls; unspecified tables require genotype_kind: hard_calls and dosages are not rounded");
                var test=ExactConditionalLogit.test(y,variant.dosagesView(),strata,offset,Alternative.TWO_SIDED,controls);
                details.put("exact_status",test.status());details.put("exact_reason",test.reason());details.put("exact_work",Long.toString(test.work()));details.put("exact_maximum_states",Integer.toString(test.maximumStates()));
                details.put("test_n_samples",Integer.toString(test.observations()));details.put("test_n_cases",Integer.toString(test.cases()));details.put("test_n_controls",Integer.toString(test.observations()-test.cases()));details.put("test_strata",Integer.toString(test.strata()));details.put("informative_strata",Integer.toString(test.informativeStrata()));
                if(!test.estimable())throw new IllegalArgumentException("Exact conditional test unavailable: "+test.reason());
                details.put("log_p_value",Double.toString(test.logPValue()));details.put("conditional_score",Double.toString(test.centeredScore()));details.put("conditional_null_mean",Double.toString(test.nullMean()));details.put("conditional_null_variance",Double.toString(test.nullVariance()));
                details.put("exact_score_direction",test.centeredScore()>0?"+":test.centeredScore()<0?"-":"0");
                double beta=Double.NaN,se=Double.NaN;details.put("result_type","p_value_only");details.put("estimator","none_test_only");details.put("standard_error_method","not_available");
                int[] selected=test.analyzedRows();var levels=new LinkedHashMap<Integer,Integer>();for(int i:selected)levels.computeIfAbsent(strata[i],ignored->levels.size());int p=levels.size()+1;
                if(p>effectMaximumParameters)details.put("effect_estimation_status","skipped_parameter_budget");
                else try {
                    double[][] design=new double[selected.length][p];double[] response=new double[selected.length],known=new double[selected.length];
                    for(int j=0;j<selected.length;j++){int i=selected[j];design[j][levels.get(strata[i])]=1;design[j][p-1]=variant.dosagesView()[i];response[j]=y[i];known[j]=offset==null?0:offset[i];}
                    var fit=FirthLogistic.fit(response,design,known,effectControls);details.put("fit_converged",Boolean.toString(fit.converged()));details.put("fit_iterations",Integer.toString(fit.iterations()));details.put("convergence_reason",fit.reason());details.put("penalized_log_likelihood",Double.toString(fit.penalizedLogLikelihood()));
                    if(fit.converged()) {
                        beta=fit.coefficients()[p-1];se=Math.sqrt(fit.covariance()[(p-1)*p+p-1]);
                        if(!Double.isFinite(beta)||!Double.isFinite(se)||!(se>0))throw new ArithmeticException("Unrepresentable effect estimate");
                        details.put("result_type","signed_effect");details.put("estimator","firth_unconditional_stratum_logit");details.put("standard_error_method","ordinary_fisher_at_penalized_estimate");details.put("effect_estimation_status","estimated_approximate");
                    }else details.put("effect_estimation_status","nonconverged_test_retained");
                }catch(RuntimeException effectFailure){beta=se=Double.NaN;details.put("effect_estimation_status","unavailable_test_retained");details.put("convergence_reason","effect_fit_error");}
                estimates[index]=new AssociationEstimate(variant.id(),beta,se,test.centeredScore(),test.pValue(),test.logPValue()/Math.log(10),-test.logPValue()/Math.log(10),Double.NaN);
            }catch(RuntimeException failure){errors[index]=failure;details.putIfAbsent("exact_status","unsupported_input");details.putIfAbsent("exact_reason","Observed hard-call input or exact model requirements were not met");}
            metadata.set(index,Map.copyOf(details));
        });
        var pool=new java.util.concurrent.ForkJoinPool(Math.min(count,engine.parallelism()));try{pool.submit(work).join();}finally{pool.shutdown();}
        var result=new ArrayList<AssociationEstimate>();var failures=new ArrayList<AssociationFailure>();
        for(int i=0;i<count;i++) {
            if(errors[i]!=null){if(engine.failurePolicy()==AssociationFailurePolicy.FAIL_FAST)throw errors[i];failures.add(new AssociationFailure(i,included.get(i).variant().id(),errors[i].getClass().getSimpleName(),errors[i].getMessage()));result.add(new AssociationEstimate(included.get(i).variant().id(),Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN));}
            else result.add(estimates[i]);
        }
        return new Block(List.copyOf(result),List.copyOf(failures),List.copyOf(metadata));
    }
}
