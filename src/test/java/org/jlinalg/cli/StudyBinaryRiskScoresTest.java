/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyOperationExecutor.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyBinaryRiskScoresTest {
    @TempDir Path root;
    private void fixture()throws Exception {
        var input=DelimitedData.read(Path.of("src/test/resources/binary-score/input.tsv"));var content=new StringBuilder("sample_id\tpartition\tfamily\ty\tx0\tx1\tx2\tc0\tc1\trelated\n");int i=0;
        for(String[] r:input.rows()){content.append("p").append(i).append('\t').append(r[7]).append("\tf").append(i/2).append('\t').append(r[0]).append('\t').append(r[1]).append('\t').append(r[2]).append('\t').append(r[3]).append('\t').append(r[5]).append('\t').append(r[6].equals("0.0")?"A":"B").append('\t').append(.7*Double.parseDouble(r[2])+.3*Double.parseDouble(r[5])+Math.cos(i)).append('\n');i++;}
        Files.writeString(root.resolve("participants.tsv"),content);
    }
    private Context context(String name,Map<String,Object> changes)throws Exception {
        var policy=new LinkedHashMap<String,Object>();policy.put("file","participants.tsv");policy.put("family","binary");policy.put("outcome","y");policy.put("features",List.of("x0","x1","x2"));policy.put("covariates",List.of("c0","c1"));policy.put("categorical_covariates",List.of("c1"));policy.put("group_columns",List.of("family"));policy.put("resampling_group","family");policy.put("lambdas",List.of(.2,.02));policy.put("alpha",.5);policy.put("binary_baseline_lambda",.3);policy.put("missing","training_mean");policy.put("bootstraps",0);policy.put("refit",false);policy.put("related_phenotypes",List.of(Map.of("column","related")));policy.putAll(changes);
        if(!policy.getOrDefault("mode","train").equals("train"))policy.remove("binary_baseline_lambda");
        var study=Map.of("resources",Map.of("directory","fixture-resources","network","offline","downloads","deny"),"datasets",List.of(),"hypotheses",List.of(Map.of("id","H","outcome","y","analysis",Map.of("risk_score",policy))));
        return new Context(root,Files.createDirectory(root.resolve(name)),Map.of("id",name,"type","risk_score_evaluation","hypothesis","H","datasets",List.of()),study,Map.of(),Map.of());
    }
    @Test void nativeBinaryTrainingProducesFrozenProbabilitiesMetricsAndRelatedTests()throws Exception {
        fixture();var c=context("train",Map.of());var result=new StudyRiskScores().execute(c);assertEquals(State.COMPLETE,result.state(),result.summary());assertEquals("binary",result.details().get("family"));var metrics=DelimitedData.read(c.outputDirectory().resolve("risk-score-evaluation.tsv"));var names=metrics.rows().stream().map(r->r[metrics.column("metric")]).toList();assertTrue(names.containsAll(List.of("auc","brier","log_loss","baseline_brier","baseline_log_loss")));assertTrue(Files.readString(c.outputDirectory().resolve("tuning.tsv")).startsWith("lambda\ttuning_log_loss"));
        var model=StudyYaml.loadArtifact(c.outputDirectory().resolve("score-model.yaml"));assertEquals("jlinalg-held-out-binary-score-v1",model.get("format"));assertEquals(80,model.get("development_rows"));assertEquals("logit",model.get("prediction_link"));assertTrue(result.details().get("required_manuscript_caveat").toString().contains("sampling prevalence"));assertEquals(1,DelimitedData.read(c.outputDirectory().resolve("related-phenotypes.tsv")).rows().size());
        var rows=DelimitedData.read(c.outputDirectory().resolve("participant-scores.tsv"));for(var row:rows.rows()){double p=Double.parseDouble(row[rows.column("prediction")]),b=Double.parseDouble(row[rows.column("baseline")]);assertTrue(p>0&&p<1);assertTrue(b>0&&b<1);}
    }
    @Test void completeFrozenBinaryModelReusesPreprocessingWeightsAndComparatorExactly()throws Exception {
        fixture();var source=context("train",Map.of());assertEquals(State.COMPLETE,new StudyRiskScores().execute(source).state());var reuse=context("reuse",Map.of("mode","model","model","train/score-model.yaml","external_independence","Synthetic held-out IDs and family blocks; upstream preprocessing was generated independently"));var result=new StudyRiskScores().execute(reuse);assertEquals(State.COMPLETE,result.state(),result.summary());assertEquals(false,result.details().get("model_refitted"));var parameters=StudyPublicEvidence.map(StudyPublicEvidence.map(result.details().get("method_report")).get("parameters"));assertFalse(parameters.containsKey("lambdas"));assertEquals(.5,StudyPublicEvidence.map(parameters.get("source_frozen_model")).get("alpha"));assertEquals(.3,StudyPublicEvidence.map(parameters.get("source_frozen_model")).get("baseline_lambda"));assertEquals(Files.readString(source.outputDirectory().resolve("participant-scores.tsv")),Files.readString(reuse.outputDirectory().resolve("participant-scores.tsv")));
        var malformed=new LinkedHashMap<String,Object>(StudyYaml.loadArtifact(source.outputDirectory().resolve("score-model.yaml")));malformed.remove("missing");StudyPublicEvidence.write(source.outputDirectory().resolve("incomplete-model.yaml"),malformed);var invalid=context("invalid-model",Map.of("mode","model","model","train/incomplete-model.yaml","external_independence","Synthetic model must still contain complete preprocessing metadata"));assertEquals(State.WAITING_FOR_INPUT,new StudyRiskScores().execute(invalid).state());
    }
    @Test void optimizerFailureAndIncompleteDevelopmentDoNotPublishAFrozenScore()throws Exception {
        fixture();for(var changes:List.of(Map.<String,Object>of("binary_maximum_iterations",1),Map.<String,Object>of("max_work",1))){var c=context("limited"+changes.hashCode(),changes);var result=new StudyRiskScores().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());assertFalse(Files.exists(c.outputDirectory().resolve("score-model.yaml")));assertFalse(Files.exists(c.outputDirectory().resolve("participant-scores.tsv")));}
        var table=Files.readString(root.resolve("participants.tsv")).replaceAll("(?m)^(p[0-9]+\\ttraining\\tf[0-9]+\\t)[01]\\t","$10\t");Files.writeString(root.resolve("participants.tsv"),table);var c=context("single-class",Map.of());assertEquals(State.WAITING_FOR_INPUT,new StudyRiskScores().execute(c).state());assertFalse(Files.exists(c.outputDirectory().resolve("score-model.yaml")));
    }
    @Test void tuningIdentitiesRemainDevelopmentWhenFinalRefitWasDisabled()throws Exception {
        fixture();var source=context("train",Map.of());assertEquals(State.COMPLETE,new StudyRiskScores().execute(source).state());var model=StudyYaml.loadArtifact(source.outputDirectory().resolve("score-model.yaml"));assertEquals(130,model.get("development_identity_rows"));assertEquals(130,DelimitedData.read(source.outputDirectory().resolve("score-weights.tsv.training-ids.tsv")).rows().size());
        Files.writeString(root.resolve("participants.tsv"),Files.readString(root.resolve("participants.tsv")).replace("\ttuning\t","\tevaluation\t"));var c=context("leak",Map.of("mode","model","model","train/score-model.yaml","external_independence","Synthetic independence declaration cannot override observed model-selection participant overlap"));var result=new StudyRiskScores().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());assertTrue(result.summary().contains("development IDs overlap"));
    }
    @Test void incompleteRelatedFamilyRetainsCompletedPerformanceAsContextWithoutPromotingASignificantFinding()throws Exception {
        fixture();var lines=Files.readAllLines(root.resolve("participants.tsv"));for(int i=0;i<lines.size();i++)lines.set(i,lines.get(i)+(i==0?"\tconstant":"\t1"));Files.write(root.resolve("participants.tsv"),lines);
        var c=context("partial",Map.of("related_phenotypes",List.of(Map.of("column","related"),Map.of("column","constant"))));var result=new StudyRiskScores().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());assertEquals(true,result.details().get("score_evaluation_complete"));
        StudyTestSources.seal(c.outputDirectory(),"risk_score_evaluation","partial","H");var manifest=new LinkedHashMap<String,Object>(StudyYaml.loadArtifact(c.outputDirectory().resolve("manifest.yaml")));manifest.put("state","waiting_for_input");manifest.put("details",result.details());StudyPublicEvidence.write(c.outputDirectory().resolve("manifest.yaml"),manifest);
        var inventory=new Context(root,Files.createDirectory(root.resolve("inventory")),Map.of("id","inventory","type","finding_inventory","scope","study"),c.study(),Map.of(),Map.of("partial",c.outputDirectory()));var retained=new StudyFindingInventory().execute(inventory);assertEquals(State.COMPLETE,retained.state());assertEquals(0L,retained.details().get("candidates"));String text=Files.readString(inventory.outputDirectory().resolve("finding-inventory.jsonl"));assertTrue(text.contains("complete_performance_context_related_family_unresolved"));assertTrue(text.contains("brier"));assertFalse(text.contains("score_related_phenotype"));assertFalse(text.contains("\"p0\""));assertFalse(text.contains("\"f0\""));
    }
}
