/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.survival.CoxLikelihoodExistence;
import org.jlinalg.survival.CoxAdaptiveLikelihood;
import org.jlinalg.survival.CoxSurvivalData;

/** Local-only exact certificates, with one shared bound for the complete cohort family. */
final class StudySurvivalLikelihood {
    private final Context context;private final Map<String,Object> policy;
    private final Map<String,Integer> counts=new java.util.TreeMap<>();private long pairVisits,work;
    StudySurvivalLikelihood(Context context,Map<String,Object> policy){this.context=context;this.policy=policy;}
    record Assessment(CoxLikelihoodExistence.Result result,String proofSha256) { }
    Assessment assess(String role,String feature,CoxSurvivalData response,double[][] x)throws IOException {
        long pairs=((Number)policy.get("maximum_likelihood_pair_visits")).longValue()-pairVisits;
        long remaining=((Number)policy.get("maximum_likelihood_work")).longValue()-work;
        CoxAdaptiveLikelihood.Result adaptive;
        if(pairs<1||remaining<1)adaptive=new CoxAdaptiveLikelihood.Result(new CoxLikelihoodExistence.Result(CoxLikelihoodExistence.Status.UNRESOLVED,"Cohort-family likelihood-existence resource bound exhausted",x[0].length,0,0,0,0,0,Double.NaN,new double[0],new double[0],new double[0],List.of(),List.of(),List.of()),List.of(),0,0,false);
        else adaptive=CoxAdaptiveLikelihood.analyze(response,x,new CoxLikelihoodExistence.Options(pairs,remaining,((Number)policy.get("maximum_likelihood_iterations")).intValue(),((Number)policy.get("maximum_likelihood_dimension")).intValue(),((Number)policy.get("maximum_likelihood_certificate_bits")).intValue(),((Number)policy.get("likelihood_tolerance")).doubleValue()));
        CoxLikelihoodExistence.Result r=adaptive.proof();
        pairVisits+=r.pairVisits();work+=r.work();counts.merge(r.status().name(),1,Integer::sum);
        var record=new LinkedHashMap<String,Object>();record.put("schema_version",2);record.put("role",role);record.put("feature_id",feature);
        record.put("certificate_method","adaptive_support_exact_full_risk_direction");record.put("supporting_source_rows",adaptive.supportingRows());record.put("support_attempts",adaptive.attempts());record.put("full_risk_queries",adaptive.fullRiskQueries());record.put("full_direction_verified",adaptive.fullDirectionVerified());
        record.put("status",r.status().name());record.put("message",r.message());record.put("dimension",r.dimension());record.put("numerical_rank",r.numericalRank());
        record.put("contrasts",r.contrasts());record.put("pair_visits",r.pairVisits());record.put("work",r.work());record.put("iterations",r.iterations());record.put("search_residual",Double.isFinite(r.searchResidual())?r.searchResidual():"NA");
        record.put("column_centers",java.util.Arrays.stream(r.columnCenters()).boxed().toList());record.put("column_scales",java.util.Arrays.stream(r.columnScales()).boxed().toList());record.put("approximate_direction",java.util.Arrays.stream(r.direction()).boxed().toList());
        record.put("exact_direction",r.exactDirection().stream().map(v->new java.util.TreeMap<>(Map.of("column",v.column(),"numerator",v.numerator(),"denominator",v.denominator()))).toList());
        record.put("extra_weights",r.extraWeights().stream().map(v->new java.util.TreeMap<>(Map.of("event_row",v.pair().eventRow(),"risk_row",v.pair().riskRow(),"weight",v.value()))).toList());
        record.put("basis_corrections",r.basisCorrections().stream().map(v->new java.util.TreeMap<>(Map.of("event_row",v.pair().eventRow(),"risk_row",v.pair().riskRow(),"numerator",v.numerator(),"denominator",v.denominator()))).toList());
        record.put("positive_base_weight_rule","Within supporting_source_rows only, for each nonzero actual event-minus-risk contrast take 1/iterative_hypot((x_i-center)/scale-(x_j-center)/scale); include extra weights and exact basis corrections. Their positive weighted original contrasts balance exactly with full column rank. Pair row indices address the original analyzed sample order. This sufficient subset proves a finite full-model maximum; no balance over omitted contrasts is claimed.");
        record.put("scope","Local exact sufficient-support certificate for finite binary double covariates and actual (entry,time]/stratum risk sets, under fixed finite offsets and unweighted Efron/Breslow partial likelihood. A monotone direction is accepted only after exact verification against all original risk sets. No event-tail or population calibration. Row indices and certificate directions remain local; aggregate context carries only status/counts and hashes.");
        Path path=context.outputDirectory().resolve("survival-likelihood-certificates/"+(role.equals("null")?"null":StudyFindingInventory.hash(feature))+".yaml");java.nio.file.Files.createDirectories(path.getParent());write(path,record);
        return new Assessment(r,FollowupSupport.hash(path));
    }
    Map<String,Object> summary(){return Map.of("method","adaptive_support_exact_risk_contrast_certificate","status_counts",new LinkedHashMap<>(counts),"pair_visits",pairVisits,"work",work,"resource_scope","one complete cohort association family, nuisance null plus all eligible features","proof_scope","finite unique partial-likelihood maximum, not scientific assumptions or sparse-event calibration");}
}
