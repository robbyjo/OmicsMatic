/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.xwas.BinaryPredictionScores;
import org.jlinalg.xwas.HeldOutScores;

/** Nested development validation; application/evaluation participants never enter a fit or split. */
final class StudyScoreCrossValidation {
    private StudyScoreCrossValidation() { }
    static final String CAVEAT="Nested development cross-validation repeats native covariate encoding, feature imputation/scaling and penalty selection inside each training fold. It estimates the row-weighted loss of that development procedure under the declared independence units. Outer folds have overlapping training sets; their spread is not a confidence interval. The final independent evaluation remains separate. Upstream preprocessing/feature-selection leakage, undeclared relatedness, transportability and clinical utility are not certified.";
    record Result(HeldOutScores.Fit quantitative,BinaryPredictionScores.Fit binary,Map<String,Object> summary,long work) { }
    private record Model(HeldOutScores.Frozen quantitative,BinaryPredictionScores.Frozen binary,int[] training,double lambda) { }
    private record Predictions(double[] score,double[] prediction,double[] baseline,double[] linear) { }
    private record Selection(double lambda,List<Double> losses) { }

    static boolean enabled(Map<String,Object> p){return p.containsKey("nested_validation");}
    static int[] development(int[] training,int[] tuning){int[] all=Arrays.copyOf(training,training.length+tuning.length);System.arraycopy(tuning,0,all,training.length,tuning.length);return all;}

    static Result run(Context c,Map<String,Object> p,DelimitedData table,List<String> features,List<String> covariates,
            double[] y,double[][] x,int[] development,String[] units,long reservedWork)throws IOException {
        var run=new Run(c,p,table,features,covariates,y,x,development,units,reservedWork);
        try{return run.execute();}
        catch(IllegalArgumentException invalid){run.summary.put("status","incomplete");run.summary.put("reason",String.valueOf(invalid.getMessage()));run.summary.put("performed_fit_calls",run.fits);run.summary.put("charged_fit_work",run.used);write(c.outputDirectory().resolve("nested-validation.yaml"),run.summary);throw invalid;}
        finally{run.exportAudits();}
    }

    private static final class Run {
        final Context c;final Map<String,Object> p,cv;final DelimitedData table;final List<String> features,covariates,categorical;
        final double[] y;final double[][] x;final int[] development;final String[] units;final boolean binary,impute;
        final int outer,inner,minimum;final long seed,limit;final double[] lambdas;
        final List<String[]> assignments=new ArrayList<>(),lossRows=new ArrayList<>(),outerRows=new ArrayList<>();
        final Map<String,Object> summary=new LinkedHashMap<>();long used,optimizerWork;int fits;
        Run(Context context,Map<String,Object> policy,DelimitedData input,List<String> featureNames,List<String> covariateNames,
                double[] response,double[][] predictors,int[] rows,String[] independenceUnits,long reservedWork){
            c=context;p=policy;cv=map(p.get("nested_validation"));table=input;features=featureNames;covariates=covariateNames;
            categorical=names(p,"categorical_covariates");y=response;x=predictors;development=rows.clone();units=independenceUnits;
            binary="binary".equals(p.get("family"));impute="training_mean".equals(p.get("missing"));
            outer=integer(cv,"outer_folds");inner=integer(cv,"inner_folds");minimum=integer(p,"minimum_samples");seed=((Number)cv.get("seed")).longValue();
            limit=((Number)p.get("max_work")).longValue()-reservedWork;lambdas=list(p.get("lambdas")).stream().mapToDouble(v->((Number)v).doubleValue()).toArray();
            summary.put("method","grouped_nested_cross_validation");summary.put("status","planned");summary.put("policy",cv);summary.put("family",p.get("family"));
            summary.put("development_rows",development.length);summary.put("development_units",Arrays.stream(development).mapToObj(i->units[i]).distinct().count());
            summary.put("selection_loss",binary?"individual_Bernoulli_log_loss":"mean_squared_prediction_error");summary.put("loss_weighting","pooled_rows; group boundaries control splitting, not row weights");
            summary.put("tie_break","first in registered descending lambda grid");summary.put("final_refit_scope","all registered training and tuning participants; evaluation/excluded rows absent");
            summary.put("required_manuscript_caveat",CAVEAT);summary.put("evaluation_used_for_selection",false);
        }
        Result execute()throws IOException {
            long planned=(long)(outer+1)*(inner*lambdas.length+1);summary.put("planned_fit_calls",planned);
            if(planned>integer(cv,"maximum_fits"))throw new IllegalArgumentException("Complete nested fit family exceeds maximum_fits; no favorable subset is reported");
            if((outer+1L)*development.length>integer(cv,"maximum_fold_rows"))throw new IllegalArgumentException("Complete local fold-membership audit exceeds maximum_fold_rows; no rows are omitted");
            if(limit<1)throw new IllegalArgumentException("No score work budget remains after the registered evaluation/related family");
            int[] outerAssignment=split(development,outer,seed,"outer",-1,cv.get("outer_fold_column"));
            var outPredictions=new LinkedHashMap<Integer,Predictions>();var outModels=new LinkedHashMap<Integer,Model>();var outTests=new LinkedHashMap<Integer,int[]>();
            double totalOuterLoss=0;int predicted=0;
            for(int fold=1;fold<=outer;fold++){
                int[] test=select(development,outerAssignment,fold,true),train=select(development,outerAssignment,fold,false);
                requirePopulation(train,"outer training "+fold,true);requirePopulation(test,"outer assessment "+fold,false);
                Selection selection=selectLambda(train,"outer",fold,mix(seed,fold));Model fitted=fit(train,selection.lambda());
                Predictions values=predict(fitted,test);double error=loss(test,values);totalOuterLoss+=weighted(error,test.length,development.length);predicted+=test.length;
                outPredictions.put(fold,values);outModels.put(fold,fitted);outTests.put(fold,test);
                saveOuter(fold,fitted,test,error);
            }
            if(predicted!=development.length||!Double.isFinite(totalOuterLoss))throw new IllegalArgumentException("Nested outer coverage/loss is incomplete");
            Selection finalSelection=selectLambda(development,"final",0,mix(seed,0));Model finalModel=fit(development,finalSelection.lambda());
            for(int fold=1;fold<=outer;fold++){
                int[] test=outTests.get(fold);Predictions values=outPredictions.get(fold);Model model=outModels.get(fold);
                for(int j=0;j<test.length;j++){int row=test[j];outerRows.add(new String[]{table.rows().get(row)[table.column(text(p.get("id_column")))],units[row],Integer.toString(fold),Double.toString(model.lambda()),value(y[row]),value(values.score()[j]),value(values.prediction()[j]),value(values.baseline()[j]),binary?value(values.linear()[j]):"NA"});}
            }
            FollowupSupport.table(c.outputDirectory().resolve("nested-outer-predictions.tsv"),List.of("sample_id","resampling_unit","outer_fold","selected_lambda","outcome","score","prediction","baseline","linear_predictor"),outerRows);
            FollowupSupport.table(c.outputDirectory().resolve("nested-evaluation.tsv"),List.of("score_id","family","population","metric","estimate","n","independent_units","uncertainty"),
                java.util.Collections.singletonList(new String[]{text(p.get("score_id")),text(p.get("family")),"out_of_fold_registered_development",binary?"log_loss":"mean_squared_error",value(totalOuterLoss),Integer.toString(predicted),summary.get("development_units").toString(),"not_estimated; outer folds are dependent"}));
            summary.put("status","complete");summary.put("performed_fit_calls",fits);summary.put("charged_fit_work",used);summary.put("outer_prediction_rows",predicted);summary.put("outer_row_weighted_loss",totalOuterLoss);summary.put("selected_final_lambda",finalSelection.lambda());
            if(binary)summary.put("binary_optimizer_work",optimizerWork);
            summary.put("work_accounting",binary?"shared optimizer work plus encoding/prediction accounting across every inner, outer and final fit":"sum of n*(p+q)^2 design/fit accounting and prediction cells; estimate, not CPU operations or elapsed time; each solver has its own convergence bound");
            summary.put("local_only_artifacts",List.of("nested-fold-membership.tsv","nested-outer-predictions.tsv","nested-models/*.yaml"));
            write(c.outputDirectory().resolve("nested-validation.yaml"),summary);
            return new Result(binary?null:new HeldOutScores.Fit(finalModel.quantitative(),finalSelection.losses(),finalSelection.lambda()),
                binary?new BinaryPredictionScores.Fit(finalModel.binary(),finalSelection.losses(),finalSelection.lambda(),optimizerWork):null,new LinkedHashMap<>(summary),used);
        }
        Selection selectLambda(int[] rows,String scope,int outerFold,long localSeed)throws IOException {
            int[] assigned=split(rows,inner,localSeed,scope+"_inner",outerFold,cv.get("inner_fold_column"));
            double[] errors=new double[lambdas.length];
            for(int fold=1;fold<=inner;fold++){
                int[] train=select(rows,assigned,fold,false),test=select(rows,assigned,fold,true);
                requirePopulation(train,"inner training "+scope+"/"+outerFold+"/"+fold,true);requirePopulation(test,"inner assessment "+scope+"/"+outerFold+"/"+fold,false);
                for(int j=0;j<lambdas.length;j++){
                    Model model=fit(train,lambdas[j]);double error=loss(test,predict(model,test));errors[j]+=weighted(error,test.length,rows.length);
                    lossRows.add(new String[]{scope,Integer.toString(outerFold),Integer.toString(fold),Double.toString(lambdas[j]),Integer.toString(train.length),Integer.toString(test.length),value(error),"complete"});
                }
            }
            int best=0;for(int j=0;j<errors.length;j++){if(!Double.isFinite(errors[j]))throw new IllegalArgumentException("Incomplete inner loss family");if(errors[j]<errors[best])best=j;}
            for(int j=0;j<errors.length;j++)lossRows.add(new String[]{scope,Integer.toString(outerFold),"pooled",Double.toString(lambdas[j]),Integer.toString(rows.length),Integer.toString(rows.length),value(errors[j]),j==best?"selected":"assessed"});
            return new Selection(lambdas[best],Arrays.stream(errors).boxed().toList());
        }
        Model fit(int[] rows,double lambda){
            if(++fits>integer(cv,"maximum_fits"))throw new IllegalArgumentException("Nested candidate fit bound exhausted");
            double[][] design=StudyRiskScores.design(table,covariates,categorical,rows,rows);int q=design[0].length;charge((long)rows.length*(features.size()+q));
            double[] response=HeldOutScores.values(y,rows);double[][] predictors=HeldOutScores.rows(x,rows);int[] local=java.util.stream.IntStream.range(0,rows.length).toArray();
            if(binary){
                long remaining=limit-used;if(remaining<1)throw new IllegalArgumentException("Nested optimizer work budget exhausted");
                var controls=new BinaryPredictionScores.Options(integer(p,"binary_maximum_iterations"),number(p,"binary_tolerance"),number(p,"binary_baseline_lambda"),remaining);
                var trained=BinaryPredictionScores.train(response,predictors,design,local,new int[0],new double[]{lambda},number(p,"alpha"),false,impute,controls);charge(trained.work());optimizerWork+=trained.work();
                return new Model(null,trained.frozen(),rows.clone(),lambda);
            }
            long width=features.size()+q;charge((long)rows.length*width*width);
            var trained=HeldOutScores.train(response,predictors,design,local,new int[0],new double[]{lambda},number(p,"alpha"),false,impute);
            return new Model(trained.frozen(),null,rows.clone(),lambda);
        }
        Predictions predict(Model model,int[] rows){
            double[][] design=StudyRiskScores.design(table,covariates,categorical,model.training(),rows);double[][] predictors=HeldOutScores.rows(x,rows);charge((long)rows.length*(features.size()+design[0].length));
            if(!binary){double[] scores=model.quantitative().score(predictors),base=model.quantitative().baseline(design),prediction=new double[rows.length];for(int j=0;j<rows.length;j++)prediction[j]=scores[j]+base[j];return new Predictions(scores,prediction,base,new double[0]);}
            double[] scores=model.binary().score(predictors),base=model.binary().baseline(design),eta=new double[rows.length],prediction=new double[rows.length],beta=model.binary().covariateWeights();
            for(int i=0;i<rows.length;i++){eta[i]=scores[i];for(int j=0;j<beta.length;j++)eta[i]+=design[i][j]*beta[j];if(!Double.isFinite(eta[i]))throw new IllegalArgumentException("Nested binary linear predictor is unrepresentable");prediction[i]=BinaryPredictionScores.sigmoid(eta[i]);}
            return new Predictions(scores,prediction,base,eta);
        }
        double loss(int[] rows,Predictions prediction){
            double total=0;
            if(binary){for(int j=0;j<rows.length;j++){double eta=prediction.linear()[j];total+=(Math.max(0,eta)-y[rows[j]]*eta+Math.log1p(Math.exp(-Math.abs(eta))))/rows.length;}}
            else{double root=0;boolean nonzero=false;for(int j=0;j<rows.length;j++){double difference=y[rows[j]]-prediction.prediction()[j];nonzero|=difference!=0;root=Math.hypot(root,difference/Math.sqrt(rows.length));}total=root*root;if(nonzero&&!(total>0))throw new IllegalArgumentException("Nested squared loss underflows in source units; no candidate is assigned a false zero error");}
            if(!Double.isFinite(total)||binary&&!(total>0))throw new IllegalArgumentException("Nested loss cannot be represented; no candidate/fold is silently omitted");return total;
        }
        int[] split(int[] rows,int folds,long localSeed,String scope,int outerFold,Object suppliedColumn){
            var members=new TreeMap<String,List<Integer>>();for(int row:rows)members.computeIfAbsent(units[row],key->new ArrayList<>()).add(row);
            if(members.size()<folds)throw new IllegalArgumentException("Fewer independent units than requested "+scope+" folds");
            Map<String,Integer> allocation=new LinkedHashMap<>();
            if(suppliedColumn!=null){int column=table.column(text(suppliedColumn));for(var unit:members.entrySet())for(int row:unit.getValue()){
                double value=StudyRiskScores.number(table,table.rows().get(row),text(suppliedColumn));if(!Double.isFinite(value)||value!=Math.rint(value)||value<1||value>folds)throw new IllegalArgumentException("Explicit nested fold values must be integers 1..folds in development rows");
                Integer old=allocation.putIfAbsent(unit.getKey(),(int)value);if(old!=null&&old!=(int)value)throw new IllegalArgumentException("An independence unit crosses explicit nested fold boundaries: "+text(suppliedColumn));
            }}else{var ordered=new ArrayList<>(members.keySet());Collections.shuffle(ordered,new Random(localSeed));for(int j=0;j<ordered.size();j++)allocation.put(ordered.get(j),1+j%folds);}
            int[] result=new int[rows.length];boolean[] seen=new boolean[folds];
            for(int j=0;j<rows.length;j++){int row=rows[j],fold=allocation.get(units[row]);result[j]=fold;seen[fold-1]=true;assignments.add(new String[]{scope,Integer.toString(outerFold),table.rows().get(row)[table.column(text(p.get("id_column")))],units[row],Integer.toString(fold),suppliedColumn==null?"seeded_group_assignment":"registered_column"});}
            for(boolean present:seen)if(!present)throw new IllegalArgumentException("An explicit nested fold is empty in "+scope+"/"+outerFold+"; the protocol is not redrawn");
            return result;
        }
        void requirePopulation(int[] rows,String role,boolean training){
            if(rows.length<minimum||Arrays.stream(rows).mapToObj(i->units[i]).distinct().count()<2)throw new IllegalArgumentException("Insufficient rows/independent units for "+role);
            int cases=0;for(int row:rows){if(!Double.isFinite(y[row])||binary&&y[row]!=0&&y[row]!=1)throw new IllegalArgumentException("Invalid development outcomes in "+role);if(binary)cases+=(int)y[row];}
            if(binary&&training&&(cases==0||cases==rows.length))throw new IllegalArgumentException("A binary nested training fold has only one outcome class: "+role+"; folds are not redrawn after inspecting outcomes");
        }
        void saveOuter(int fold,Model model,int[] test,double error)throws IOException {
            var document=new LinkedHashMap<String,Object>();document.put("schema_version",1);document.put("family",p.get("family"));document.put("outer_fold",fold);document.put("lambda",model.lambda());document.put("alpha",p.get("alpha"));document.put("training_rows",model.training().length);document.put("assessment_rows",test.length);document.put("assessment_loss",error);document.put("features",features);document.put("covariates",covariates);
            var levels=new LinkedHashMap<String,Object>();for(String cov:categorical){var values=new java.util.TreeSet<String>();for(int row:model.training())values.add(table.rows().get(row)[table.column(cov)]);levels.put(cov,new ArrayList<>(values));}document.put("categorical_levels",levels);
            if(binary){var fit=model.binary();document.put("feature_weights",boxed(fit.featureWeights()));document.put("feature_means",boxed(fit.featureMeans()));document.put("feature_scales",boxed(fit.featureScales()));document.put("score_intercept",fit.scoreIntercept());document.put("covariate_weights",boxed(fit.covariateWeights()));document.put("baseline_weights",boxed(fit.baselineWeights()));document.put("baseline_lambda",fit.baselineLambda());}
            else{var fit=model.quantitative();document.put("feature_weights",boxed(fit.model().weights()));document.put("feature_means",boxed(fit.featureMeans()));document.put("score_intercept",fit.model().intercept());document.put("baseline_weights",boxed(fit.baselineWeights()));}
            var path=c.outputDirectory().resolve("nested-models/outer-"+fold+".yaml");Files.createDirectories(path.getParent());write(path,document);
        }
        void charge(long value){if(value<0||value>limit-used)throw new IllegalArgumentException("Complete nested fit/assessment family exceeds remaining max_work");used+=value;}
        void exportAudits()throws IOException {
            FollowupSupport.table(c.outputDirectory().resolve("nested-fold-membership.tsv"),List.of("scope","outer_fold","sample_id","resampling_unit","fold","assignment"),assignments);
            FollowupSupport.table(c.outputDirectory().resolve("nested-tuning.tsv"),List.of("scope","outer_fold","inner_fold","lambda","training_rows","assessment_rows","loss","status"),lossRows);
        }
    }
    private static int[] select(int[] rows,int[] assigned,int fold,boolean equal){return java.util.stream.IntStream.range(0,rows.length).filter(i->(assigned[i]==fold)==equal).map(i->rows[i]).toArray();}
    private static long mix(long seed,int fold){return seed^(0x9e3779b97f4a7c15L*(fold+1L));}
    private static int integer(Map<String,Object> p,String name){return ((Number)p.get(name)).intValue();}
    private static double number(Map<String,Object> p,String name){return ((Number)p.get(name)).doubleValue();}
    private static String value(double v){return Double.isFinite(v)?Double.toString(v):"NA";}
    private static List<Double> boxed(double[] values){return Arrays.stream(values).boxed().toList();}
    private static List<String> names(Map<String,Object> p,String key){return list(p.get(key)).stream().map(StudyPublicEvidence::text).toList();}
    private static double weighted(double loss,int rows,int total){double result=loss*((double)rows/total);if(loss>0&&!(result>0))throw new IllegalArgumentException("Nested pooled loss underflows; no fold is omitted");return result;}
}
