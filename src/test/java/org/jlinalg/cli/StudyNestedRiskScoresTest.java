/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyOperationExecutor.State;
import org.jlinalg.study.StudyRiskScorePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyNestedRiskScoresTest {
    @TempDir Path root;
    private Map<String,Object> fixture(boolean binary)throws Exception {
        var s=new StringBuilder("sample_id\tpartition\tfamily\ty\tx0\tx1\tage\tsex\touter\tinner\n");
        for(int i=0;i<144;i++){
            double x0=Math.sin(i*.53),x1=Math.cos(i*.71),age=(i*7%31)/31.,y=binary?(i*17%29<13+3*x0?1:0):2+.8*x0-.4*x1+.3*age+.1*(i%2)+.15*Math.cos(i*.31);
            s.append("PRIVATE_PERSON_").append(i).append('\t').append(i<64?"training":i<96?"tuning":"evaluation").append("\tPRIVATE_FAMILY_").append(i/2).append('\t').append(y).append('\t').append(x0).append('\t').append(i%19==0?"NA":Double.toString(x1)).append('\t').append(age).append('\t').append(i%2==0?"A":"B").append('\t').append(1+i/2%3).append('\t').append(1+i/6%3).append('\n');
        }
        Files.writeString(root.resolve("participants.tsv"),s);
        return new LinkedHashMap<>(Map.of("file","participants.tsv","family",binary?"binary":"quantitative","outcome","y","features",List.of("x0","x1"),"covariates",List.of("age","sex"),"categorical_covariates",List.of("sex"),"group_columns",List.of("family"),"resampling_group","family","lambdas",List.of(.2,.02),"nested_validation",Map.of("outer_folds",3,"inner_folds",3,"outer_fold_column","outer","inner_fold_column","inner")));
    }
    private Context context(String name,Map<String,Object> input)throws Exception {
        var p=new LinkedHashMap<>(input);p.putIfAbsent("alpha",0.);p.putIfAbsent("bootstraps",0);p.putIfAbsent("missing","training_mean");p.putIfAbsent("minimum_samples",4);
        var h=Map.of("id","H","outcome","y","analysis",Map.of("risk_score",p));var study=Map.of("datasets",List.of(),"resources",Map.of("directory","fixture-resources","network","offline","downloads","deny"),"hypotheses",List.of(h));
        return new Context(root,Files.createDirectory(root.resolve(name)),Map.of("id",name,"type","risk_score_evaluation","hypothesis","H","datasets",List.of()),study,Map.of(),Map.of());
    }
    @Test void quantitativeAndBinaryNestedFamiliesFreezeCompleteDevelopmentAndReuseExactly()throws Exception {
        for(boolean binary:new boolean[]{false,true}){
            var p=fixture(binary);var c=context("native-"+binary,p);var result=new StudyCliExecutor().execute(c);assertEquals(State.COMPLETE,result.state(),result.summary());
            var nested=map(result.details().get("nested_validation"));assertEquals("complete",nested.get("status"));assertEquals(28,nested.get("performed_fit_calls"));assertEquals(96,nested.get("outer_prediction_rows"));assertEquals(false,nested.get("evaluation_used_for_selection"));
            assertEquals(96,DelimitedData.read(c.outputDirectory().resolve("nested-outer-predictions.tsv")).rows().size());var model=StudyYaml.loadArtifact(c.outputDirectory().resolve("score-model.yaml"));assertEquals(96,model.get("development_rows"));assertEquals("grouped_nested_cross_validation",map(model.get("model_selection")).get("method"));
            var reuse=new LinkedHashMap<>(p);reuse.remove("nested_validation");reuse.put("mode","model");reuse.put("model",c.outputDirectory().getFileName()+"/score-model.yaml");reuse.put("external_independence","Synthetic external evaluation held outside all development folds");
            var applied=context("reuse-"+binary,reuse);var reused=new StudyCliExecutor().execute(applied);assertEquals(State.COMPLETE,reused.state(),reused.summary());assertFalse(Files.exists(applied.outputDirectory().resolve("nested-validation.yaml")));assertEquals(Files.readString(c.outputDirectory().resolve("participant-scores.tsv")),Files.readString(applied.outputDirectory().resolve("participant-scores.tsv")));
            assertTrue(map(reused.details().get("frozen_model_parameters")).containsKey("source_model_selection_report_sha256"));
        }
    }
    @Test void externalOutcomesAndPredictorsCannotChangeAnyDevelopmentModelOrFold()throws Exception {
        for(boolean binary:new boolean[]{false,true}){
            var p=fixture(binary);var before=context("before-"+binary,p);assertEquals(State.COMPLETE,new StudyCliExecutor().execute(before).state());var input=DelimitedData.read(root.resolve("participants.tsv"));var rows=new ArrayList<String[]>();
            for(String[] old:input.rows()){var row=old.clone();if(row[input.column("partition")].equals("evaluation")){double y=Double.parseDouble(row[input.column("y")]);row[input.column("y")]=Double.toString(binary?1-y:y+12);row[input.column("x0")]=Double.toString(10+3*Double.parseDouble(row[input.column("x0")]));}rows.add(row);}FollowupSupport.table(root.resolve("participants.tsv"),input.header(),rows);
            var after=context("after-"+binary,p);var result=new StudyCliExecutor().execute(after);assertEquals(State.COMPLETE,result.state(),result.summary());
            for(String file:List.of("score-model.yaml","nested-validation.yaml","nested-fold-membership.tsv","nested-tuning.tsv","nested-outer-predictions.tsv"))assertEquals(FollowupSupport.hash(before.outputDirectory().resolve(file)),FollowupSupport.hash(after.outputDirectory().resolve(file)),file);
        }
    }
    @Test void anOuterHeldOutOutcomeCannotTuneOrPreprocessItsOwnModel()throws Exception {
        var p=fixture(false);var before=context("outer-before",p);assertEquals(State.COMPLETE,new StudyCliExecutor().execute(before).state());var input=DelimitedData.read(root.resolve("participants.tsv"));var rows=new ArrayList<String[]>();
        for(String[] old:input.rows()){var row=old.clone();if(!row[input.column("partition")].equals("evaluation")&&row[input.column("outer")].equals("1")){row[input.column("y")]=Double.toString(20+Double.parseDouble(row[input.column("y")]));row[input.column("x0")]=Double.toString(5+Double.parseDouble(row[input.column("x0")]));}rows.add(row);}FollowupSupport.table(root.resolve("participants.tsv"),input.header(),rows);
        var after=context("outer-after",p);assertEquals(State.COMPLETE,new StudyCliExecutor().execute(after).state());var a=StudyYaml.loadArtifact(before.outputDirectory().resolve("nested-models/outer-1.yaml"));var b=StudyYaml.loadArtifact(after.outputDirectory().resolve("nested-models/outer-1.yaml"));for(String key:List.of("lambda","feature_means","feature_weights","score_intercept","baseline_weights","categorical_levels"))assertEquals(a.get(key),b.get(key),key);
    }
    @Test void groupCrossingsAndUnseenFoldCategoriesHoldTheWholeFamily()throws Exception {
        for(boolean category:new boolean[]{false,true}){
            var p=fixture(false);var input=DelimitedData.read(root.resolve("participants.tsv"));var rows=new ArrayList<String[]>();for(int i=0;i<input.rows().size();i++){var row=input.rows().get(i).clone();if(category&&i<2)row[input.column("sex")]="C";else if(!category&&i==0)row[input.column("outer")]="2";rows.add(row);}FollowupSupport.table(root.resolve("participants.tsv"),input.header(),rows);
            var c=context("invalid-"+category,p);var result=new StudyCliExecutor().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());assertTrue(result.summary().contains(category?"Unseen categorical":"crosses explicit nested"),result.summary());assertFalse(Files.exists(c.outputDirectory().resolve("score-model.yaml")));assertFalse(Files.exists(c.outputDirectory().resolve("risk-score-evaluation.tsv")));assertEquals("incomplete",StudyYaml.loadArtifact(c.outputDirectory().resolve("nested-validation.yaml")).get("status"));
        }
    }
    @Test void fitAuditAndWorkBoundsNeverPublishASuccessfulSubset()throws Exception {
        var p=fixture(false);for(var bound:List.of(Map.<String,Object>of("maximum_fits",1),Map.<String,Object>of("maximum_fold_rows",1),Map.<String,Object>of("max_work",10000))){
            var changed=new LinkedHashMap<>(p);if(bound.containsKey("max_work"))changed.putAll(bound);else{var cv=new LinkedHashMap<>(map(p.get("nested_validation")));cv.putAll(bound);changed.put("nested_validation",cv);}var c=context("bound-"+bound.hashCode(),changed);var result=new StudyCliExecutor().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());assertFalse(Files.exists(c.outputDirectory().resolve("score-model.yaml")));assertFalse(Files.exists(c.outputDirectory().resolve("nested-evaluation.tsv")));assertFalse(Files.exists(c.outputDirectory().resolve("risk-score-evaluation.tsv")));
        }
    }
    @Test void administrativeSplitsCannotBecomePredictorsOrImportedRetuningControls()throws Exception {
        var p=fixture(false);for(var change:List.of(Map.<String,Object>of("refit",false),Map.<String,Object>of("mode","model"),Map.<String,Object>of("family","survival"),Map.<String,Object>of("features",List.of("outer")))){var invalid=new LinkedHashMap<>(p);invalid.putAll(change);assertThrows(IllegalArgumentException.class,()->StudyRiskScorePolicy.resolve(invalid));}
    }
    @Test void unrepresentableSquaredLossCannotMasqueradeAsPerfectValidation()throws Exception {
        var p=fixture(false);var input=DelimitedData.read(root.resolve("participants.tsv"));var rows=new ArrayList<String[]>();for(String[] old:input.rows()){var row=old.clone();row[input.column("y")]=Double.toString(Double.parseDouble(row[input.column("y")])*1e-200);rows.add(row);}FollowupSupport.table(root.resolve("participants.tsv"),input.header(),rows);var c=context("tiny-outcome",p);var result=new StudyCliExecutor().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());assertFalse(Files.exists(c.outputDirectory().resolve("nested-evaluation.tsv")));assertFalse(Files.exists(c.outputDirectory().resolve("score-model.yaml")));
    }
    @Test void theSmallestPositiveResidualCannotDisappearBeforeTheNormAccumulator()throws Exception {
        var p=fixture(false);var input=DelimitedData.read(root.resolve("participants.tsv"));var rows=new ArrayList<String[]>();for(int i=0;i<input.rows().size();i++){var row=input.rows().get(i).clone();row[input.column("y")]=i%7==0?Double.toString(Double.MIN_VALUE):"0";rows.add(row);}FollowupSupport.table(root.resolve("participants.tsv"),input.header(),rows);var c=context("subnormal-outcome",p);var result=new StudyCliExecutor().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());assertTrue(result.summary().contains("squared loss underflows"),result.summary());assertFalse(Files.exists(c.outputDirectory().resolve("nested-evaluation.tsv")));assertFalse(Files.exists(c.outputDirectory().resolve("score-model.yaml")));
    }
    @Test void generatedFoldsKeepWholeGroupsAndIgnoreParticipantRowOrder()throws Exception {
        var p=fixture(false);p.put("nested_validation",Map.of("outer_folds",3,"inner_folds",3,"seed",7));var a=context("generated",p);var first=new StudyCliExecutor().execute(a);assertEquals(State.COMPLETE,first.state(),first.summary());
        var input=DelimitedData.read(root.resolve("participants.tsv"));var rows=new ArrayList<>(input.rows());java.util.Collections.reverse(rows);FollowupSupport.table(root.resolve("participants.tsv"),input.header(),rows);var b=context("reordered",p);var second=new StudyCliExecutor().execute(b);assertEquals(State.COMPLETE,second.state(),second.summary());
        var mappings=new ArrayList<Map<String,String>>();for(Context c:List.of(a,b)){var map=new java.util.TreeMap<String,String>();var groups=new java.util.HashMap<String,String>();var t=DelimitedData.read(c.outputDirectory().resolve("nested-fold-membership.tsv"));for(String[] row:t.rows()){
            String scope=row[t.column("scope")]+"/"+row[t.column("outer_fold")],key=scope+"/"+row[t.column("sample_id")],group=scope+"/"+row[t.column("resampling_unit")],fold=row[t.column("fold")];map.put(key,fold);String old=groups.putIfAbsent(group,fold);assertTrue(old==null||old.equals(fold));assertFalse(row[t.column("sample_id")].matches("PRIVATE_PERSON_(9[6-9]|1[0-4][0-9])"));
        }mappings.add(map);}assertEquals(mappings.get(0),mappings.get(1));
    }
    @Test void binaryWorkIsSharedAcrossAllCandidateFitsAndSingleClassFoldsAreNotRedrawn()throws Exception {
        var p=fixture(true);var baseline=context("binary-budget-baseline",p);var ok=new StudyCliExecutor().execute(baseline);assertEquals(State.COMPLETE,ok.state(),ok.summary());long work=((Number)map(ok.details().get("nested_validation")).get("charged_fit_work")).longValue();var limited=new LinkedHashMap<>(p);limited.put("max_work",work-1);var c=context("binary-shared-budget",limited);assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(c).state());assertFalse(Files.exists(c.outputDirectory().resolve("score-model.yaml")));assertFalse(Files.exists(c.outputDirectory().resolve("nested-evaluation.tsv")));
        var input=DelimitedData.read(root.resolve("participants.tsv"));var rows=new ArrayList<String[]>();for(String[] old:input.rows()){var row=old.clone();if(!row[input.column("partition")].equals("evaluation"))row[input.column("y")]=row[input.column("outer")].equals("1")?"1":"0";rows.add(row);}FollowupSupport.table(root.resolve("participants.tsv"),input.header(),rows);var invalid=context("binary-single-fold-class",p);var held=new StudyCliExecutor().execute(invalid);assertEquals(State.WAITING_FOR_INPUT,held.state());assertTrue(held.summary().contains("only one outcome class"),held.summary());
    }
    @Test void nestedEvidenceContextContainsAggregateLossAndNeverLocalMembershipsOrModels()throws Exception {
        var p=fixture(false);var c=context("aggregate-source",p);var result=new StudyCliExecutor().execute(c);assertEquals(State.COMPLETE,result.state(),result.summary());var hashes=new LinkedHashMap<String,Object>();for(String name:result.outputs())hashes.put(name,FollowupSupport.hash(c.outputDirectory().resolve(name)));
        write(c.outputDirectory().resolve("manifest.yaml"),Map.of("operation_type","risk_score_evaluation","operation_id","score","hypothesis","H","state","complete","output_sha256",hashes,"details",result.details()));
        var out=Files.createDirectory(root.resolve("inventory"));var inventory=new Context(root,out,Map.of("id","inventory","type","finding_inventory","scope","study","datasets",List.of()),c.study(),Map.of(),Map.of("score",c.outputDirectory()));new StudyFindingInventory().execute(inventory);String text=Files.readString(out.resolve("finding-inventory.jsonl"));assertTrue(text.contains("nested_validation_context"));assertTrue(text.contains("outer_row_weighted_loss"));for(String forbidden:List.of("PRIVATE_PERSON_","PRIVATE_FAMILY_","feature_means","feature_weights","nested-outer-predictions.tsv","nested-fold-membership.tsv"))assertFalse(text.contains(forbidden),forbidden);
    }
}
