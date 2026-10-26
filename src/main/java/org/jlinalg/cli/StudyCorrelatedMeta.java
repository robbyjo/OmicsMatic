/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import jdistlib.ChiSquare;
import jdistlib.Normal;
import org.jlinalg.compute.BackendPolicy;
import org.jlinalg.meta.*;
import org.jlinalg.study.StudyNeed;
import org.jlinalg.study.StudyOperationExecutor.*;
import org.jlinalg.study.StudyPoolingPolicy;

/** Native GLS using supplied sampling covariance, never overlap-fraction substitution. */
final class StudyCorrelatedMeta {
    private static final List<String> HEADER=List.of("feature_id","term","model","n_cohorts","direction","status","beta","se","statistic","p_value","neg_log10_p","ci_lower","ci_upper","tau_squared","q","q_df","q_p_value","i_squared","moderator_q","moderator_q_p_value","prediction_lower","prediction_upper","n_sources","n_unique_declared_cohorts","q_definition");
    private record Estimate(double beta,double se) { }
    private record Pair(String left,String right){static Pair of(String left,String right){return left.compareTo(right)<=0?new Pair(left,right):new Pair(right,left);}}
    static Result execute(Context context,Map<String,Path> inputs,Map<String,Object> config,StudyPoolingAudit.Audit audit)throws IOException{
        var policy=StudyPoolingPolicy.resolve(map(StudyResultTables.analysis(context).get("pooling")));var dependence=map(policy.get("dependence"));
        String mode=text(dependence.get("method"));Path source=context.project().resolve(text(dependence.get("matrix"))).normalize();
        if(!Files.isRegularFile(source))return waiting(context,audit,"sampling_matrix_unavailable","Supply the declared local sampling matrix before covariance-aware pooling",List.of());
        Map<String,Map<Pair,Double>> matrices;
        try{matrices=matrices(source,mode,context.study());}
        catch(IOException|IllegalArgumentException invalid){return waiting(context,audit,"sampling_matrix_invalid",invalid.getMessage(),List.of());}
        Files.copy(source,context.outputDirectory().resolve("sampling-matrix-input.tsv"));String hash=FollowupSupport.hash(source);
        MetaAnalysisOptions options=StudyMetaOptions.resolve(config);
        int minimum=minimum(config,inputs.size()>1?2:1);
        Map<String,Map<String,Estimate>> features=new TreeMap<>();List<String> cohortOrder=new ArrayList<>(inputs.keySet());
        for(var input:inputs.entrySet()){
            var table=DelimitedData.readResult(input.getValue());Set<String> seen=new java.util.HashSet<>();
            for(String[] row:table.rows()){
                String feature=row[table.column("feature_id")];if(!seen.add(feature))throw new IOException("Duplicate feature in covariance-aware cohort input");
                var values=features.computeIfAbsent(feature,ignored->new LinkedHashMap<>());
                double beta=numeric(row[table.column("beta")]),se=numeric(row[table.column("se")]);if(Double.isFinite(beta)&&Double.isFinite(se)&&se>0)values.put(input.getKey(),new Estimate(beta,se));
            }
        }
        int incomplete=0,fitted=0;List<String[]> diagnostics=new ArrayList<>(),weights=new ArrayList<>();Path output=context.outputDirectory().resolve("meta-analysis.tsv");
        try(BufferedWriter writer=Files.newBufferedWriter(output)){
            writer.write(String.join("\t",HEADER));writer.newLine();
            for(var feature:features.entrySet()){
                List<String> available=cohortOrder.stream().filter(feature.getValue()::containsKey).toList();int n=available.size();String[] row=new String[HEADER.size()];Arrays.fill(row,"NA");
                row[0]=feature.getKey();row[1]="pooled";row[2]=(options.method()==MetaAnalysisMethod.FIXED_EFFECT?"fixed":"random")+"_sampling_gls";row[3]=Integer.toString(n);
                row[22]=Integer.toString(n);row[24]="generalized_sampling_covariance";
                Set<String> physicalCohorts=new java.util.LinkedHashSet<>();boolean unknownCohort=false;
                for(String id:available){var dataset=StudyBioinformatics.datasets(context).stream().filter(item->id.equals(item.get("id"))).findFirst().orElse(Map.of());var declared=StudyPoolingAudit.cohorts(dataset);if(declared.isEmpty())unknownCohort=true;physicalCohorts.addAll(declared);}
                row[23]=unknownCohort?"NA":Integer.toString(physicalCohorts.size());
                StringBuilder direction=new StringBuilder();for(String id:cohortOrder){Estimate value=feature.getValue().get(id);direction.append(value==null?'?':value.beta>0?'+':value.beta<0?'-':'0');}row[4]=direction.toString();
                String reason="";
                if(n<minimum)row[5]="below_min_cohorts";
                else try{
                    double[][] covariance=covariance(feature.getKey(),available,feature.getValue(),matrices,mode);
                    if(n==1){Estimate value=feature.getValue().get(available.get(0));double z=value.beta/value.se,critical=Normal.quantile(.5+options.confidenceLevel()/2,0,1,true,false);row[5]="single_cohort";row[6]=num(value.beta);row[7]=num(value.se);row[8]=num(z);row[9]=num(2*Normal.cumulative(-Math.abs(z),0,1,true,false));row[10]=num(-(Math.log(2)+Normal.cumulative(-Math.abs(z),0,1,true,true))/Math.log(10));row[11]=num(value.beta-critical*value.se);row[12]=num(value.beta+critical*value.se);weights.add(new String[]{feature.getKey(),available.get(0),"1.0"});}
                    else{
                        List<MetaStudy> studies=available.stream().map(id->{var value=feature.getValue().get(id);return new MetaStudy(id,value.beta,value.se);}).toList();
                        var result=MetaCorrelatedAnalysis.fit(studies,covariance,options,BackendPolicy.CPU);var statistics=result.associationStatistics();
                        row[5]="ok";row[6]=num(result.pooledEffectSize());row[7]=num(result.standardError());row[8]=num(statistics.statistics()[0]);row[9]=num(statistics.pValues()[0]);row[10]=num(statistics.negativeLog10PValues()[0]);row[11]=num(result.confidenceLower());row[12]=num(result.confidenceUpper());row[13]=num(result.tauSquared());row[14]=num(result.generalizedQ());row[15]=num(result.qDegreesOfFreedom());row[16]=num(ChiSquare.cumulative(result.generalizedQ(),result.qDegreesOfFreedom(),false,false));
                        double[] normalized=result.normalizedWeights();for(int i=0;i<n;i++)weights.add(new String[]{feature.getKey(),available.get(i),num(normalized[i])});
                    }
                    fitted++;
                }catch(IllegalArgumentException|ArithmeticException|IOException invalid){row[5]="sampling_covariance_unresolved";reason=invalid.getMessage();incomplete++;}
                writer.write(String.join("\t",row));writer.newLine();diagnostics.add(new String[]{feature.getKey(),Integer.toString(n),String.join(",",available),row[5],reason==null?"unavailable":reason.replace('\t',' ').replace('\n',' ')});
            }
        }
        FollowupSupport.table(context.outputDirectory().resolve("pooling-feature-audit.tsv"),List.of("feature_id","n_sources","datasets","status","reason"),diagnostics);
        FollowupSupport.table(context.outputDirectory().resolve("pooling-weights.tsv"),List.of("feature_id","dataset_id","normalized_weight"),weights);
        var parameters=new LinkedHashMap<String,Object>();parameters.put("model",options.method().toString());parameters.put("sampling_dependence",dependence);parameters.put("sampling_matrix_sha256",hash);parameters.put("cohort_order",cohortOrder);parameters.put("minimum_cohorts",minimum);parameters.put("inference",options.inferenceMethod().toString());parameters.put("confidence_level",options.confidenceLevel());parameters.put("tau_squared_estimator",options.tauSquaredEstimator().toString());parameters.put("tolerance",options.tolerance());parameters.put("maximum_iterations",options.maximumIterations());
        parameters.put("variance_diagonal_relative_tolerance",1e-6);parameters.put("minimum_scaled_cholesky_pivot",1e-12);parameters.put("missing_sampling_pairs","unresolved; never implicitly independent");parameters.put("matrix_precedence","feature-specific pair overrides the explicitly supplied shared * pair");parameters.put("heterogeneity_covariance","sampling covariance plus tau_squared times identity");
        Files.writeString(context.outputDirectory().resolve("meta-analysis.tsv.log"),StudyAssistance.json(parameters)+"\n");Files.writeString(context.outputDirectory().resolve("console.txt"),"Native sampling-covariance GLS; "+fitted+" estimable features; "+incomplete+" unresolved matrices.\n");
        List<String> outputs=new ArrayList<>(List.of("meta-analysis.tsv","meta-analysis.tsv.log","console.txt","sampling-matrix-input.tsv","pooling-feature-audit.tsv","pooling-weights.tsv","pooling-audit.yaml","pooling-compatibility.tsv","pooling-variant-compatibility.tsv"));outputs.addAll(StudyMetaMetadata.attach(output,inputs));
        var method=new LinkedHashMap<>(StudyMethods.record("Generalized least-squares meta-analysis with supplied sampling covariance",parameters,List.of(dependence.get("citation")),dependence.get("rationale"),List.of("meta-analysis","correlated sampling errors")));method.put("performed",fitted>0);method.put("retained_estimates",fitted);
        var details=new LinkedHashMap<String,Object>();details.put("pooling_audit",audit.details());details.put("sampling_dependence",parameters);details.put("unresolved_features",incomplete);details.put("method_report",method);details.put("evidence_disposition",fitted>0?"estimable":"not_estimable");details.put("required_manuscript_caveat","Sampling dependence is conditional on the supplied matrix, not estimated from overlap fractions or effect correlations. Random-effects heterogeneity adds independent between-source variance. Generalized Q is reported; conventional independent-study I-squared and prediction intervals are unavailable. Legacy n_cohorts counts result sources, as explicitly recorded in n_sources; distinct declared cohort IDs do not certify independence. Correlated sources do not establish independent replication.");
        if(incomplete>0){var need=StudyNeed.review("sampling-covariance:"+context.operation().get("hypothesis"),"Resolve incomplete or invalid sampling covariance",incomplete+" feature matrices are missing, inconsistent with source SEs or not positive definite. No independent-cohort fallback or source deletion occurred; inspect pooling-feature-audit.tsv.",true);details.put("needs",List.of(need.document()));}
        write(context.outputDirectory().resolve("correlated-pooling.yaml"),details);outputs.add("correlated-pooling.yaml");
        return new Result(incomplete==0?State.COMPLETE:State.WAITING_FOR_INPUT,"Covariance-aware pooling: "+fitted+" estimable, "+incomplete+" unresolved features",outputs,details);
    }
    private static Result waiting(Context context,StudyPoolingAudit.Audit audit,String status,String reason,List<String> additional)throws IOException{
        var details=new LinkedHashMap<>(audit.details());details.put("status",status);details.put("needs",List.of(StudyNeed.review("sampling-covariance:"+context.operation().get("hypothesis"),"Supply valid sampling covariance",reason,true).document()));write(context.outputDirectory().resolve("correlated-pooling.yaml"),details);var outputs=new ArrayList<>(List.of("pooling-audit.yaml","pooling-compatibility.tsv","pooling-variant-compatibility.tsv","correlated-pooling.yaml"));outputs.addAll(additional);return new Result(State.WAITING_FOR_INPUT,reason,outputs,details);
    }
    private static Map<String,Map<Pair,Double>> matrices(Path source,String mode,Map<String,Object> study)throws IOException{
        Set<String> datasets=new java.util.HashSet<>();for(Object raw:list(study.get("datasets")))datasets.add(text(map(raw).get("id")));
        var table=DelimitedData.readResult(source);for(String field:List.of("feature_id","dataset_a","dataset_b","value"))table.column(field);Map<String,Map<Pair,Double>> matrices=new LinkedHashMap<>();
        for(String[] row:table.rows()){
            String feature=row[table.column("feature_id")],left=row[table.column("dataset_a")],right=row[table.column("dataset_b")];double value=numeric(row[table.column("value")]);
            if(feature.isBlank()||!datasets.contains(left)||!datasets.contains(right)||!Double.isFinite(value))throw new IOException("Sampling matrix requires finite values, nonblank feature IDs and declared dataset IDs");
            if(mode.equals("sampling_correlation")&&(Math.abs(value)>1||left.equals(right)&&Math.abs(value-1)>1e-8))throw new IOException("Sampling correlations require [-1,1] and diagonal 1");
            if(left.equals(right)&&value<=0)throw new IOException("Sampling covariance diagonal must be positive");
            Double old=matrices.computeIfAbsent(feature,ignored->new LinkedHashMap<>()).putIfAbsent(Pair.of(left,right),value);if(old!=null&&old.doubleValue()!=value)throw new IOException("Conflicting duplicate/asymmetric sampling matrix entries");
        }return matrices;
    }
    private static double[][] covariance(String feature,List<String> datasets,Map<String,Estimate> estimates,Map<String,Map<Pair,Double>> matrices,String mode)throws IOException{
        int n=datasets.size();double[][] result=new double[n][n];
        for(int i=0;i<n;i++)for(int j=0;j<n;j++){
            Pair pair=Pair.of(datasets.get(i),datasets.get(j));Double value=matrices.getOrDefault(feature,Map.of()).get(pair);if(value==null)value=matrices.getOrDefault("*",Map.of()).get(pair);if(value==null)throw new IOException("Missing sampling pair "+pair.left+"/"+pair.right);
            double left=estimates.get(datasets.get(i)).se,right=estimates.get(datasets.get(j)).se;
            result[i][j]=mode.equals("sampling_correlation")?value*left*right:value;
            if(!Double.isFinite(result[i][j])||i==j&&result[i][j]<=0)throw new IOException("Sampling covariance must be finite with positive diagonal after SE scaling");
            if(i==j&&Math.abs(result[i][j]-left*left)>1e-6*Math.max(Math.abs(result[i][j]),left*left))throw new IOException("Sampling variance disagrees with the source standard error");
        }
        // Work on a correlation scale so a tiny raw variance cannot hide singular
        // or nearly singular sampling information. No jitter or silent repair.
        double[][] factor=new double[n][n];
        for(int i=0;i<n;i++)for(int j=0;j<=i;j++){
            double value=result[i][j]/Math.sqrt(result[i][i])/Math.sqrt(result[j][j]);for(int k=0;k<j;k++)value-=factor[i][k]*factor[j][k];
            if(i==j){if(!Double.isFinite(value)||value<=1e-12)throw new IOException("Sampling covariance is singular or has a scaled Cholesky pivot <= 1e-12");factor[i][j]=Math.sqrt(value);}else factor[i][j]=value/factor[j][j];
        }
        return result;
    }
    private static int minimum(Map<String,Object> config,int fallback){Object value=config.getOrDefault("min_cohorts",fallback);if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=n.intValue()||n.intValue()<1)throw new IllegalArgumentException("min_cohorts must be a positive integer");return n.intValue();}
    private static double numeric(String value){try{return Double.parseDouble(value);}catch(NumberFormatException invalid){return Double.NaN;}}
    private static String num(double value){return Double.isFinite(value)?Double.toString(value):"NA";}
}
