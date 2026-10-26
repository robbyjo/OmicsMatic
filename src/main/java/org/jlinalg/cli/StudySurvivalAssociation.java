/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jdistlib.ChiSquare;
import jdistlib.Normal;
import org.jlinalg.compute.BackendPolicy;
import org.jlinalg.formula.Formula;
import org.jlinalg.pipeline.*;
import org.jlinalg.study.StudyOperationExecutor.*;
import org.jlinalg.study.StudySurvivalPolicy;
import org.jlinalg.survival.*;

/** Full per-feature Cox fits on one frozen, prefit-selected participant population. */
final class StudySurvivalAssociation {
    private static final List<String> HEADER=List.of("id","beta","standard_error","p_value","status","n","n_events","n_censored","survival_strata",
        "model","effect_scale","effect_units","test_method","estimator","standard_error_method","statistic_type","statistic","log_p_value","ci_lower","ci_upper",
        "fit_iterations","fit_converged","convergence_reason","chromosome","position","reference_allele","alternate_allele","effect_allele","other_allele","genome_build",
        "minor_allele_count","minor_allele_frequency","effect_allele_frequency","called","missing","call_rate","imputation_info","hwe_p_all","qc_reason","likelihood_existence","likelihood_proof_sha256");
    private record Feature(String id,double[] values,VariantRecord variant) { }
    private record Diagnostic(String id,double statistic,double p,double globalStatistic,double globalP,int globalDf,String status) { }
    private interface Reader extends AutoCloseable { Feature next()throws IOException; @Override void close()throws IOException; }

    Result execute(Context c,Map<String,Object> dataset,Map<String,Object> h,Map<String,Object> options)throws IOException {
        var policy=StudySurvivalPolicy.resolve(map(options.get("survival")));var prerequisite=StudySurvivalSupport.prerequisite(c,policy);if(prerequisite!=null)return prerequisite;
        var files=map(dataset.get("files"));var columns=map(dataset.get("columns"));boolean genotype=Set.of("gwas","genomics").contains(dataset.get("type"));
        if(files.containsKey("grm")||files.containsKey("pedigree")||Boolean.TRUE.equals(map(dataset.get("metadata")).get("related_participants")))
            return StudySurvivalSupport.waiting(c,"Native study Cox fits require independent participants; a GRM/pedigree or known related design needs a matching frailty/cluster workflow or compatible supplied results.");
        if(!org.jlinalg.study.StudyHumanPloidyPolicy.resolve(options).isEmpty()||genotype&&!"human_autosomes".equals(options.getOrDefault("chromosome_scope","human_autosomes")))
            return StudySurvivalSupport.waiting(c,"Native study Cox genotype fits require the registered human_autosomes scope; sex/PAR, other ploidy and mixed survival designs need a matching reviewed workflow.");
        if(genotype&&StudyVariantUniverse.build(text(dataset.get("genome_build"))).isBlank())return StudySurvivalSupport.waiting(c,"Declare GRCh37/GRCh38 for the native human-autosome Cox genotype route.");
        if(genotype&&(!"all".equals(options.getOrDefault("hwe_filter_scope","all"))||number(options,"minimum_missingness_p",0)>0))
            return StudySurvivalSupport.waiting(c,"Cox event/censor indicators are not case/control groups. Use all-sample or explicit-panel HWE; binary case/control HWE and differential-missingness filters require a matching survival-aware QC contract.");
        Path phenotype=StudyResultTables.artifact(c,"participant_qc","analysis-phenotype.tsv");
        if(phenotype==null)return StudySurvivalSupport.waiting(c,"A verified prefit-selected survival phenotype table must precede Cox association.");
        Path matrix=StudyAssayQcSources.matrix(c,dataset);var table=DelimitedData.read(phenotype);
        var byId=new LinkedHashMap<String,String[]>();int idColumn=table.column(text(columns.getOrDefault("sample_id","id")));
        for(String[] row:table.rows())if(byId.put(row[idColumn],row)!=null)throw new IOException("Repeated survival participants require a cluster/recurrent-event workflow");
        VariantSource variantSource=null;NumericMatrixSource numericSource=null;List<String> sourceIds;long sourceCount;
        if(genotype){List<String> external=null;String role=files.containsKey("sample")?"sample":"samples";if(files.containsKey(role))external=SampleFiles.read(c.project().resolve(text(files.get(role))));variantSource=VariantSources.open(matrix,DataFormat.infer(matrix),external);sourceIds=variantSource.metadata().sampleIds();sourceCount=variantSource.metadata().variantCount();}
        else{numericSource=DelimitedMatrixSource.open(matrix);sourceIds=numericSource.metadata().sampleIds();sourceCount=numericSource.metadata().rowCount();}
        var aligned=new ArrayList<String[]>();var order=new ArrayList<Integer>();for(int i=0;i<sourceIds.size();i++)if(byId.containsKey(sourceIds.get(i))){aligned.add(byId.get(sourceIds.get(i)));order.add(i);}
        if(aligned.size()!=table.rows().size())throw new IOException("Prefit survival participants no longer match the source matrix");
        StudySurvivalSupport.Population population;
        try{population=StudySurvivalSupport.population(table,aligned,columns,policy);}catch(IOException invalid){return StudySurvivalSupport.waiting(c,invalid.getMessage());}
        int n=aligned.size();var names=new ArrayList<String>();
        for(Object raw:list(h.get("covariates"))){String key=text(raw);String name=text(map(columns.get("covariates")).getOrDefault(key,key));if(List.of(StudySurvivalSupport.column(columns,policy,"time_column"),StudySurvivalSupport.column(columns,policy,"event_column")).contains(name)||policy.containsKey("strata_column")&&StudySurvivalSupport.column(columns,policy,"strata_column").equals(name))throw new IOException("Survival outcome/strata columns cannot be estimated baseline covariates");if(names.contains(name))throw new IOException("Duplicate Cox covariate");names.add(name);}
        String offset=options.containsKey("offset_column")?text(map(columns.get("covariates")).getOrDefault(text(options.get("offset_column")),options.get("offset_column"))):"";
        if(names.contains(offset))throw new IOException("A Cox offset must be separate from estimated covariates");
        String time=StudySurvivalSupport.column(columns,policy,"time_column");String formula=time+" ~ "+(names.isEmpty()?"1":String.join(" + ",names))+(offset.isBlank()?"":" + offset("+offset+")");
        var prepared=PhenotypeData.read(phenotype,text(columns.getOrDefault("sample_id","id"))).prepare(population.ids(),time,false,null,null,policy.containsKey("strata_column")?StudySurvivalSupport.column(columns,policy,"strata_column"):null);
        var compiled=Formula.compile(formula,prepared.modelTable());int intercept=compiled.coefficientNames().indexOf("(Intercept)"),q=compiled.columns()-(intercept>=0?1:0);double[][] x=new double[n][q];
        double[] design=compiled.design();for(int i=0;i<n;i++)for(int j=0,k=0;j<compiled.columns();j++)if(j!=intercept)x[i][k++]=design[i*compiled.columns()+j];
        var coefficientNames=new ArrayList<>(compiled.coefficientNames());if(intercept>=0)coefficientNames.remove(intercept);
        var droppedCovariates=new ArrayList<String>();var keptCovariates=new ArrayList<Integer>();
        for(int j=0;j<q;j++){double[] values=new double[n];for(int i=0;i<n;i++)values[i]=x[i][j];if(withinStratumVariation(values,population.response().strata()))keptCovariates.add(j);else droppedCovariates.add(coefficientNames.get(j));}
        if(keptCovariates.size()!=q){double[][] reduced=new double[n][keptCovariates.size()];for(int i=0;i<n;i++)for(int j=0;j<keptCovariates.size();j++)reduced[i][j]=x[i][keptCovariates.get(j)];var keptNames=new ArrayList<String>();for(int index:keptCovariates)keptNames.add(coefficientNames.get(index));x=reduced;q=keptCovariates.size();coefficientNames=keptNames;}
        var fitOptions=new CoxOptions(CoxTies.valueOf(text(policy.get("ties")).toUpperCase(java.util.Locale.ROOT)),((Number)policy.get("maximum_iterations")).intValue(),number(policy,"relative_tolerance",1e-9),number(policy,"score_tolerance",1e-7),((Number)policy.get("maximum_step_halvings")).intValue(),0,number(policy,"confidence_level",.95));
        int maximum=((Number)policy.get("maximum_features")).intValue();if(sourceCount>maximum)return StudySurvivalSupport.waiting(c,"The full Cox source exceeds maximum_features; increase the registered bound or supply a prespecified source universe. No favorable prefix is tested.");
        double fitCost=(double)n*(q+1)*(q+1)*(((double)fitOptions.maximumIterations()*(fitOptions.maximumStepHalvings()+1))+2.*n),limit=number(policy,"maximum_fit_work",1e10),charged=fitCost;
        if(!Double.isFinite(fitCost)||fitCost>limit)return StudySurvivalSupport.waiting(c,"The Cox null/full-fit work estimate exceeds maximum_fit_work before any association fit. Increase the registered bound only for a feasible complete analysis.");
        var likelihood=new StudySurvivalLikelihood(c,policy);var nullExistence=likelihood.assess("null","nuisance_null",population.response(),x);
        if(!nullExistence.result().finiteMaximum())return StudySurvivalSupport.waiting(c,"Cox nuisance null has no certified finite maximum: "+nullExistence.result().status()+": "+nullExistence.result().message());
        CoxResult nullFit;try{nullFit=CoxRegression.fit(population.response(),x,compiled.offset(),fitOptions,BackendPolicy.CPU);if(!nullFit.converged())return StudySurvivalSupport.waiting(c,"Cox nuisance null did not converge: "+nullFit.convergenceMessage());}catch(IllegalArgumentException invalid){return StudySurvivalSupport.waiting(c,"Cox nuisance null is not estimable: "+invalid.getMessage());}
        var nullRows=new ArrayList<String[]>();for(int j=0;j<q;j++)nullRows.add(new String[]{coefficientNames.get(j),value(nullFit.beta()[j]),value(nullFit.standardErrors()[j])});
        FollowupSupport.table(c.outputDirectory().resolve("cox-null-coefficients.tsv"),List.of("term","beta","standard_error"),nullRows);
        var baseline=new ArrayList<String[]>();for(var point:nullFit.baselineHazard())baseline.add(new String[]{Integer.toString(point.stratum()),value(point.time()),Integer.toString(point.events()),value(point.hazardIncrement()),value(point.cumulativeHazard()),value(point.baselineSurvival())});
        FollowupSupport.table(c.outputDirectory().resolve("cox-null-baseline.tsv"),List.of("stratum_index","time","events","hazard_increment","cumulative_hazard","baseline_survival"),baseline);
        FollowupSupport.table(c.outputDirectory().resolve("survival-population.tsv"),List.of("stratum_index","n","events","censored","total_at_risk_time"),population.strataRows());
        VariantFilterOptions filters=genotype?filters(c,files,population.ids(),options):null;
        var diagnostics=new ArrayList<Diagnostic>();var seen=new HashSet<String>();var dispositions=new java.util.TreeMap<String,Integer>();int tested=0,unresolved=0,examined=0;boolean bounds=false;
        Path raw=c.outputDirectory().resolve("association.tsv");int[] sampleOrder=order.stream().mapToInt(Integer::intValue).toArray();
        try(Reader reader=reader(variantSource,numericSource,sampleOrder);var writer=Files.newBufferedWriter(raw)){
            writer.write(String.join("\t",HEADER)+"\n");Feature feature;
            while((feature=reader.next())!=null){
                if(++examined>maximum){bounds=true;break;}if(!seen.add(feature.id()))throw new IOException("Duplicate survival feature ID: "+feature.id());
                var row=new LinkedHashMap<String,String>();for(String key:HEADER)row.put(key,"NA");row.put("id",feature.id());row.put("n",Integer.toString(n));row.put("n_events",population.summary().get("events").toString());row.put("n_censored",population.summary().get("censored").toString());row.put("survival_strata",population.summary().get("strata").toString());
                row.put("model","cox");row.put("effect_scale","log_hazard_ratio");row.put("effect_units",genotype?"per_ALT_allele":"per_source_feature_unit");row.put("estimator","cox_full_partial_likelihood");row.put("standard_error_method","ordinary_inverse_observed_information");
                row.put("test_method",policy.get("test").equals("wald")?"cox_wald_normal":"cox_partial_likelihood_ratio");row.put("statistic_type",policy.get("test").equals("wald")?"z":"chisq_1df");
                String reason="";double[] predictor=feature.values().clone();
                if(genotype){var variant=feature.variant();var qc=VariantFilters.evaluate(variant,filters);var stats=qc.statistics();reason=String.join(";",qc.reasons().stream().map(Enum::name).toList());
                    row.put("chromosome",variant.chromosome());row.put("position",Long.toString(variant.position()));row.put("reference_allele",variant.referenceAllele());row.put("alternate_allele",variant.alternateAllele());row.put("effect_allele",variant.alternateAllele());row.put("other_allele",variant.referenceAllele());row.put("genome_build",StudyVariantUniverse.build(text(dataset.get("genome_build"))));
                    row.put("minor_allele_count",value(stats.minorAlleleCount()));row.put("minor_allele_frequency",value(stats.minorAlleleFrequency()));row.put("effect_allele_frequency",value(stats.alternateAlleleFrequency()));row.put("imputation_info",value(variant.imputationQuality()));
                    row.put("hwe_p_all",value(VariantQualityDiagnostics.calculate(variant,stats,filters.quality()).hweAll().pValue()));
                }
                int called=0;double mean=0,scale=0;for(double v:predictor)if(Double.isFinite(v)){called++;scale=Math.max(scale,Math.abs(v));}
                row.put("called",Integer.toString(called));row.put("missing",Integer.toString(n-called));row.put("call_rate",value((double)called/n));
                if(reason.isEmpty()&&(called==0||!genotype&&(double)(n-called)/n>number(policy,"maximum_feature_missing",.02)))reason="MISSING_PREDICTOR_QC";
                if(reason.isEmpty()){
                    if(scale>0){for(double v:predictor)if(Double.isFinite(v))mean+=v/scale;mean=(mean/called)*scale;}if(!Double.isFinite(mean))reason="UNREPRESENTABLE_PREDICTOR_MEAN";
                    else{for(int i=0;i<n;i++)if(!Double.isFinite(predictor[i]))predictor[i]=mean;if(!withinStratumVariation(predictor,population.response().strata()))reason="NO_WITHIN_STRATUM_VARIATION";}
                }
                if(!reason.isEmpty()){row.put("status","qc_excluded");row.put("qc_reason",reason);}
                else if(charged+fitCost>limit){bounds=true;row.put("status","unresolved_work_bound");unresolved++;}
                else{
                    charged+=fitCost;double[][] full=new double[n][q+1];for(int i=0;i<n;i++){System.arraycopy(x[i],0,full[i],0,q);full[i][q]=predictor[i];}
                    try{
                        var existence=likelihood.assess("feature",feature.id(),population.response(),full);row.put("likelihood_existence",existence.result().status().name());row.put("likelihood_proof_sha256",existence.proofSha256());
                        if(!existence.result().finiteMaximum())throw new IllegalArgumentException("finite_maximum_not_certified: "+existence.result().message());
                        CoxResult fit=CoxRegression.fit(population.response(),full,compiled.offset(),fitOptions,BackendPolicy.CPU);
                        row.put("fit_iterations",Integer.toString(fit.iterations()));row.put("fit_converged",Boolean.toString(fit.converged()));row.put("convergence_reason",fit.convergenceMessage());
                        if(!fit.converged())throw new IllegalArgumentException("full_fit_not_converged");
                        double beta=fit.beta()[q],se=fit.standardErrors()[q],lrt=2*(fit.logPartialLikelihood()-nullFit.logPartialLikelihood());
                        if(!Double.isFinite(beta)||!Double.isFinite(se)||!(se>0)||!Double.isFinite(lrt)||lrt < -1e-7*(1+Math.abs(nullFit.logPartialLikelihood())))throw new IllegalArgumentException("full_fit_numerical_unavailable");
                        lrt=Math.max(0,lrt);boolean wald=policy.get("test").equals("wald");double statistic=wald?beta/se:lrt;
                        double logp=wald?Math.log(2)+Normal.cumulative(-Math.abs(statistic),0,1,true,true):ChiSquare.cumulative(statistic,1,false,true),p=Math.exp(logp);
                        if(!Double.isFinite(logp)||!Double.isFinite(p))throw new IllegalArgumentException("tail_unavailable");
                        row.put("beta",value(beta));row.put("standard_error",value(se));row.put("p_value",value(p));row.put("statistic",value(statistic));row.put("log_p_value",value(Math.min(0,logp)));
                        double critical=Normal.quantile((1+fitOptions.confidenceLevel())/2,0,1,true,false);row.put("ci_lower",value(beta-critical*se));row.put("ci_upper",value(beta+critical*se));row.put("status","ok");tested++;
                        try{var ph=CoxDiagnostics.analyze(population.response(),full,fit,compiled.offset(),null).proportionalHazards();diagnostics.add(new Diagnostic(feature.id(),ph.statistics()[q],ph.pValues()[q],ph.globalStatistic(),ph.globalPValue(),ph.globalDegreesOfFreedom(),Double.isFinite(ph.pValues()[q])?"assessed":"not_estimable"));}
                        catch(IllegalArgumentException invalid){diagnostics.add(new Diagnostic(feature.id(),Double.NaN,Double.NaN,Double.NaN,Double.NaN,q+1,"not_estimable"));}
                    }catch(IllegalArgumentException invalid){row.put("status","unresolved_fit");row.put("qc_reason",invalid.getClass().getSimpleName()+":"+text(invalid.getMessage()).replaceAll("[\\t\\r\\n]"," "));unresolved++;}
                }
                dispositions.merge(row.get("status"),1,Integer::sum);writer.write(String.join("\t",HEADER.stream().map(key->row.get(key).isBlank()?"NA":row.get(key)).toList())+"\n");
            }
        }
        boolean complete=unresolved==0&&!bounds;var probabilities=new LinkedHashMap<String,Double>();for(var d:diagnostics)if(Double.isFinite(d.p()))probabilities.put(d.id(),d.p());
        boolean phComplete=complete&&probabilities.size()==diagnostics.size();var adjusted=phComplete?StudyMrMultiplicity.adjust(probabilities,diagnostics.size(),text(policy.get("ph_adjustment"))):Map.<String,Double>of();var phRows=new ArrayList<String[]>();
        for(var d:diagnostics)phRows.add(new String[]{d.id(),value(d.statistic()),value(d.p()),value(adjusted.getOrDefault(d.id(),Double.NaN)),text(policy.get("ph_adjustment")),Integer.toString(diagnostics.size()),phComplete?"complete":"incomplete",Boolean.toString(adjusted.getOrDefault(d.id(),Double.NaN)<number(policy,"ph_alpha",.05)),value(d.globalStatistic()),Integer.toString(d.globalDf()),value(d.globalP()),d.status()});
        FollowupSupport.table(c.outputDirectory().resolve("survival-diagnostics.tsv"),List.of("feature_id","ph_statistic","ph_p_value","ph_adjusted_p","ph_adjustment","ph_family_size","ph_family_status","ph_alert","global_ph_statistic","global_ph_df","global_ph_p_value","status"),phRows);
        Path standard=c.outputDirectory().resolve("standard-results/"+dataset.get("id")+".tsv");var metadata=new LinkedHashMap<String,Object>();
        for(String key:List.of("model","effect_scale","effect_units","test_method","estimator","standard_error_method","statistic_type","convergence_reason","likelihood_existence","likelihood_proof_sha256"))metadata.put(key,key);
        StudyCliExecutor.normalizeNativeAssociation(c,raw,standard,metadata);
        var effectQc=StudyEffectQc.apply(Map.of(text(dataset.get("id")),standard),map(map(h.get("analysis")).get("harmonization")),c.outputDirectory());StudyFeatureAnnotations.write(c,Map.of(text(dataset.get("id")),standard));
        var parameters=new LinkedHashMap<String,Object>();parameters.put("survival_policy",policy);parameters.put("formula",formula);parameters.put("tested_predictor","appended separately to each full model");parameters.put("null_covariate_columns",coefficientNames);parameters.put("baseline_absorbed_covariate_columns",droppedCovariates);parameters.put("population",population.summary());parameters.put("information_ridge",0);parameters.put("null_log_partial_likelihood",nullFit.logPartialLikelihood());parameters.put("null_iterations",nullFit.iterations());parameters.put("missing_predictors","analysis-population mean imputation after source QC");parameters.put("execution_threads",1);
        parameters.put("requested_threads",options.getOrDefault("threads",1));
        parameters.put("likelihood_existence",likelihood.summary());
        if(genotype){var qc=new LinkedHashMap<String,Object>();qc.put("minimum_mac",filters.minimumMac());qc.put("maximum_mac",Double.isFinite(filters.maximumMac())?filters.maximumMac():"not_set");qc.put("minimum_maf",filters.minimumMaf());qc.put("maximum_maf",filters.maximumMaf());qc.put("maximum_marker_missing",filters.maximumMissingRate());qc.put("minimum_info",Double.isFinite(filters.minimumImputationQuality())?filters.minimumImputationQuality():"not_requested");qc.put("minimum_hwe_p",filters.quality().minimumHweP());qc.put("hwe_scope","all_or_explicit_panel");qc.put("hwe_panel",files.containsKey("hwe_samples")?FollowupSupport.hash(c.project().resolve(text(files.get("hwe_samples")))):"aligned_analysis_population");qc.put("genotype_kind",filters.quality().genotypeKind());qc.put("chromosome_scope","human_autosomes");qc.put("genome_build",StudyVariantUniverse.build(text(dataset.get("genome_build"))));parameters.put("genotype_qc",qc);}
        var method=new LinkedHashMap<>(StudyMethods.record("Full baseline-covariate independent Cox proportional-hazards association",parameters,list(options.get("references")),options.getOrDefault("rationale",policy.get("rationale")),List.of("Cox proportional hazards partial likelihood Efron Breslow","proportional hazards log-time Schoenfeld diagnostics")));method.put("performed",tested>0);method.put("effect_outlier_qc",effectQc.document());
        var details=new LinkedHashMap<String,Object>();details.put("dataset",dataset.get("id"));details.put("method_report",method);details.put("survival_population",population.summary());details.put("source_features_reported",sourceCount);details.put("examined_features",examined);details.put("tested_features",tested);details.put("unresolved_tests",unresolved);details.put("source_or_work_bound_hit",bounds);details.put("charged_fit_work_estimate",charged);details.put("fit_work_unit","n*(q+1)^2*(maximum_iterations*(maximum_step_halvings+1)+2*n) per null/full diagnostic fit; conservative accounting units, not elapsed time");details.put("variant_dispositions",dispositions);details.put("association_family_status",complete?"complete":"incomplete_survival_tests");details.put("primary_selection_eligible",complete);details.put("effect_outlier_qc",effectQc.document());details.put("required_manuscript_caveat",StudySurvivalSupport.CAVEAT);details.put("evidence_disposition",complete?(tested>0?"estimable":"not_estimable"):"incomplete_test_family");
        details.put("ph_diagnostics",Map.of("family_status",phComplete?"complete":"incomplete","tested_feature_count",diagnostics.size(),"available_tests",probabilities.size(),"adjustment",policy.get("ph_adjustment"),"alpha",policy.get("ph_alpha"),"alerts",adjusted.values().stream().filter(p->p<number(policy,"ph_alpha",.05)).count(),"global_tests","Nominal model-wide diagnostic P values; not members of the predictor PH correction family","time_transform","log(time)","primary_test_changed",false));
        if("NONE".equals(policy.get("ph_adjustment")))details.put("required_manuscript_caveat",StudySurvivalSupport.CAVEAT+" Registered PH alerts use nominal P values under NONE and do not control FDR.");
        details.put("likelihood_existence",likelihood.summary());details.put("prefit_phenotype_sha256",FollowupSupport.hash(phenotype));write(c.outputDirectory().resolve("survival-analysis.yaml"),details);
        if(!complete){var pending=StudySurvivalSupport.waiting(c,"Cox association family is incomplete: resolve fit/source/work bounds before official FDR selection; available raw estimates remain retained without a successful-subset family.");details.put("needs",pending.details().get("needs"));return new Result(State.WAITING_FOR_INPUT,pending.summary(),StudySurvivalSupport.outputs(c),details);}
        return new Result(State.COMPLETE,tested>0?"Fitted registered Cox survival associations":"No estimable Cox associations remained after declared predictor QC",StudySurvivalSupport.outputs(c),details);
    }

    private static Reader reader(VariantSource variants,NumericMatrixSource matrix,int[] order)throws IOException {
        if(variants!=null){var input=variants.open(order);return new Reader(){public Feature next()throws IOException{var block=input.read(1);if(block==null)return null;var v=block.variants().get(0);return new Feature(v.id(),v.dosages(),v);}public void close()throws IOException{input.close();}};}
        var input=matrix.open(order);return new Reader(){public Feature next()throws IOException{var block=input.read(1);if(block==null)return null;var row=block.rows().get(0);return new Feature(row.id(),row.values(),null);}public void close()throws IOException{input.close();}};
    }
    private static VariantFilterOptions filters(Context c,Map<String,Object> files,List<String> ids,Map<String,Object> options)throws IOException {
        boolean[] panel=null;if(files.containsKey("hwe_samples")){var samples=new HashSet<>(SampleFiles.read(c.project().resolve(text(files.get("hwe_samples")))));panel=new boolean[ids.size()];for(int i=0;i<ids.size();i++)panel[i]=samples.contains(ids.get(i));}
        var quality=new VariantQualityOptions(number(options,"minimum_hwe_p",0),"all",null,panel,0,((Number)options.getOrDefault("missingness_max_states",100000)).intValue(),text(options.getOrDefault("genotype_kind","auto")),true);
        return new VariantFilterOptions(number(options,"minimum_maf",0),number(options,"maximum_maf",.5),number(options,"minimum_mac",20),number(options,"maximum_mac",Double.POSITIVE_INFINITY),number(options,"maximum_marker_missing",.02),number(options,"minimum_info",Double.NaN),true,quality);
    }
    private static boolean withinStratumVariation(double[] values,int[] strata){var low=new java.util.HashMap<Integer,Double>();var high=new java.util.HashMap<Integer,Double>();for(int i=0;i<values.length;i++){low.merge(strata[i],values[i],Math::min);high.merge(strata[i],values[i],Math::max);}return low.keySet().stream().anyMatch(key->high.get(key)>low.get(key));}
    private static double number(Map<String,Object> p,String key,double fallback){return p.get(key) instanceof Number n?n.doubleValue():fallback;}
    private static String value(double value){return Double.isFinite(value)?Double.toString(value):"NA";}
}
