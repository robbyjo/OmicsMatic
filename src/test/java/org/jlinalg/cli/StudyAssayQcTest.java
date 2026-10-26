/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyAssayQcPolicy;
import org.jlinalg.study.StudyOperationExecutor.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyAssayQcTest {
    @TempDir Path root;
    private Map<String,Object> dataset(){return Map.of("id","a","type","expression","stage","processed_matrix","files",Map.of("matrix","matrix.tsv","phenotype","absent-phenotype.tsv","assay_detection","detect.tsv"));}
    private Map<String,Object> study(Map<String,Object> p){return Map.of("datasets",List.of(dataset()),"hypotheses",List.of(Map.of("id","H","outcome","never-read","inputs",Map.of("discovery",List.of("a")),"analysis",Map.of("assay_qc",p))));}
    private Context context(Map<String,Object> p,String output){return new Context(root,root.resolve(output),Map.of("id","qc","type","assay_qc","hypothesis","H","dataset_id","a","datasets",List.of("a"),"assay_qc_policy",StudyAssayQcPolicy.forDataset(StudyAssayQcPolicy.resolve(p),"a")),study(p),Map.of(),Map.of());}
    private Result run(Map<String,Object> p)throws Exception{return new StudyCliExecutor().execute(context(p,"qc"));}
    private void matrix(String text)throws Exception {Files.writeString(root.resolve("matrix.tsv"),text);}
    private String[] feature(String id)throws Exception {var t=DelimitedData.read(root.resolve("qc/assay-feature-qc.tsv"));return t.rows().stream().filter(r->r[0].equals(id)).findFirst().orElseThrow();}
    @Test void defaultAuditRetainsSourceUniverseAndNeverNeedsPhenotypes()throws Exception {
        String original="feature_id\tA\tB\tC\nconstant\t1\t1\t1\nmissing\tNA\tNA\t3\ngood\t1\t2\t3\n";matrix(original);
        var r=run(Map.of());assertEquals(State.COMPLETE,r.state());assertEquals(3L,r.details().get("retained_features"));assertEquals(3,r.details().get("retained_samples"));assertEquals("original_source",r.details().get("analysis_matrix"));assertEquals(2L,r.details().get("flagged_features"));assertEquals(2,r.details().get("flagged_samples"));assertEquals("constant",feature("constant")[15]);assertEquals("keep",feature("constant")[14]);assertFalse(Files.exists(root.resolve("qc/assay-matrix.tsv")));assertEquals(original,Files.readString(root.resolve("matrix.tsv")));
    }
    @Test void fixedOriginalDenominatorDoesNotChangeWhenBadFeaturesAreExcluded()throws Exception {
        matrix("feature_id\tA\tB\tC\nbad\tNA\tNA\t3\ngood\t1\t2\t3\n");
        var r=run(Map.of("mode","filter","minimum_observed",2,"maximum_sample_missing",.4));assertEquals(State.COMPLETE,r.state());assertEquals(1,r.details().get("retained_samples"));assertEquals(1L,r.details().get("retained_features"));var derived=DelimitedData.read(root.resolve("qc/assay-matrix.tsv"));assertEquals(List.of("feature_id","C"),derived.header());assertEquals("good",derived.rows().get(0)[0]);var sample=DelimitedData.read(root.resolve("qc/assay-sample-qc.tsv"));assertEquals("0.5",sample.rows().get(0)[4]);assertEquals("exclude",sample.rows().get(0)[8]);
    }
    @Test void detectionMasksCellsWithExactAlignmentAndNoImputation()throws Exception {
        matrix("id\tA\tB\tC\nG\t1\t2\t3\nH\t2\t3\t4\n");Files.writeString(root.resolve("detect.tsv"),"id\tA\tB\tC\nG\t0.001\t0.1\tNA\nH\t0\t0\t0\n");
        var p=Map.<String,Object>of("mode","filter","minimum_observed",1,"drop_constant",false,"maximum_feature_missing",1,"maximum_sample_missing",1,"detection_kind","p_value");var r=run(p);assertEquals(State.COMPLETE,r.state());assertEquals(2L,r.details().get("applied_masked_values"));assertEquals(List.of("G","1.0","NA","NA"),List.of(DelimitedData.read(root.resolve("qc/assay-matrix.tsv")).rows().get(0)));
        Files.writeString(root.resolve("detect.tsv"),"id\tB\tA\tC\nG\t0\t0\t0\nH\t0\t0\t0\n");assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(context(p,"bad" )).state());assertFalse(Files.exists(root.resolve("bad/assay-matrix.tsv")));
    }
    @Test void detectionUsesExactDecimalBoundsAndCutoffsRatherThanRoundedDoubleZerosOrOnes()throws Exception {
        matrix("id\tA\tB\tC\nG\t1\t2\t3\n");Files.writeString(root.resolve("detect.tsv"),"id\tA\tB\tC\nG\t1e-999\t0\t0.0100000000000000001\n");
        var p=new LinkedHashMap<String,Object>(Map.of("mode","filter","detection_kind","p_value","minimum_observed",1,"drop_constant",false,"maximum_feature_missing",1,"maximum_sample_missing",1,"detection_threshold",0));assertEquals(State.COMPLETE,run(p).state());assertTrue(Files.readString(root.resolve("qc/assay-matrix.tsv")).contains("G\tNA\t2.0\tNA"));
        p.put("detection_threshold",.01);assertEquals(State.COMPLETE,new StudyCliExecutor().execute(context(p,"ordinary-threshold")).state());assertTrue(Files.readString(root.resolve("ordinary-threshold/assay-matrix.tsv")).contains("G\t1.0\t2.0\tNA"));
        for(String invalid:List.of("-1e-999","1.00000000000000001")){Files.writeString(root.resolve("detect.tsv"),"id\tA\tB\tC\nG\t"+invalid+"\t0\t0\n");assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(context(p,"bad-decimal-"+Math.abs(invalid.hashCode()))).state());}
        Files.writeString(root.resolve("detect.tsv"),"id\tA\tB\tC\nG\t1e-999\t0\t1\n");p.put("detection_kind","detected");assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(context(p,"binary-decimal")).state());
        matrix("id\tA\tB\tC\nG\t0\t1.00000000000000001\t1\n");assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(context(Map.of("value_scale","proportion"),"proportion-decimal")).state());
    }
    @Test void auditDetectionOnlyFlagsAndDoesNotMaskTheOriginal()throws Exception {
        matrix("id\tA\tB\tC\nG\t1\t2\t3\n");Files.writeString(root.resolve("detect.tsv"),"id\tA\tB\tC\nG\t1\t0\t1\n");var r=run(Map.of("detection_kind","detected"));assertEquals(State.COMPLETE,r.state());assertEquals(1L,r.details().get("proposed_masked_values"));assertEquals(0L,r.details().get("applied_masked_values"));assertFalse(Files.exists(root.resolve("qc/assay-matrix.tsv")));
    }
    @Test void invalidScaleValuesHoldUntilExplicitFilterMaskAndNeverRoundCounts()throws Exception {
        matrix("id\tA\tB\tC\nG\t1\t1.5\t3\nH\t2\t3\t4\n");var p=new LinkedHashMap<String,Object>(Map.of("value_scale","counts"));assertEquals(State.WAITING_FOR_INPUT,run(p).state());assertFalse(Files.exists(root.resolve("qc/assay-matrix.tsv")));
        p.putAll(Map.of("mode","filter","invalid_values","mask","maximum_feature_missing",1,"maximum_sample_missing",1,"minimum_observed",2));var r=new StudyCliExecutor().execute(context(p,"masked"));assertEquals(State.COMPLETE,r.state());assertTrue(Files.readString(root.resolve("masked/assay-matrix.tsv")).contains("G\t1.0\tNA\t3.0"));assertTrue(Files.readString(root.resolve("matrix.tsv")).contains("1.5"));
    }
    @Test void scalesAndNumericRepresentabilityAreCheckedWithoutTreatingNegativeContinuousValuesAsInvalid()throws Exception {
        for(String raw:List.of("Infinity","-Infinity","1e-999")){matrix("id\tA\tB\tC\nG\t1\t"+raw+"\t3\n");assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(context(Map.of(),"bad-"+raw.replace('-','x'))).state());}
        matrix("id\tA\tB\tC\nG\t-1\t0\t3\n");assertEquals(State.COMPLETE,run(Map.of("value_scale","continuous")).state());assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(context(Map.of("value_scale","nonnegative"),"negative")).state());
        matrix("id\tA\tB\tC\nG\t0\t1.0001\t0.5\n");assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(context(Map.of("value_scale","proportion"),"proportion")).state());
        matrix("id\tA\tB\tC\nG\t1\t9007199254740993\t3\n");assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(context(Map.of("value_scale","counts"),"counts")).state());
    }
    @Test void numericMomentsStayUsefulAtExtremeScalesAndExactConstantFlagDoesNotUseRoundedSd()throws Exception {
        matrix("id\tA\tB\tC\nhuge\t-1e308\t0\t1e308\ntiny\t0\t4.9e-324\t0\nclose\t1e308\t1.0000000000000002e308\t1e308\n");assertEquals(State.COMPLETE,run(Map.of()).state());var huge=feature("huge");assertEquals(0,Double.parseDouble(huge[6]),1e292);assertEquals(1e308,Double.parseDouble(huge[7]),1e293);assertEquals("false",feature("tiny")[12]);assertEquals("false",feature("close")[12]);assertTrue(Double.parseDouble(feature("close")[11])>660);assertFalse(Files.readString(root.resolve("qc/assay-qc.yaml")).contains(".NaN"));
    }
    @Test void logSdThresholdDoesNotUseRoundedSubnormalSdAndAppliedMasksExcludeDroppedCells()throws Exception {
        matrix("id\tA\tB\tC\ntiny\t0\t4.9e-324\t0\nG\t1\t2\t3\n");var r=run(Map.of("mode","filter","minimum_sd",Double.MIN_VALUE));assertEquals(State.COMPLETE,r.state());assertEquals("exclude",feature("tiny")[14]);assertTrue(feature("tiny")[15].contains("low_sd"));
        matrix("id\tA\tB\tC\nbad\tInfinity\tInfinity\t2\nG\t1\t2\t3\n");var c=context(Map.of("mode","filter","invalid_values","mask","maximum_sample_missing",1),"masked-excluded");r=new StudyCliExecutor().execute(c);assertEquals(State.COMPLETE,r.state());assertEquals(2L,r.details().get("proposed_masked_values"));assertEquals(0L,r.details().get("applied_masked_values"));
    }
    @Test void integerLibrarySizesAreExactBeyondDoublePrecision()throws Exception {
        matrix("id\tA\tB\tC\nG\t9007199254740991\t1\t2\nH\t9007199254740991\t3\t4\nI\t9007199254740991\t5\t6\n");assertEquals(State.COMPLETE,run(Map.of("value_scale","counts")).state());var table=DelimitedData.read(root.resolve("qc/assay-sample-qc.tsv"));assertEquals("27021597764222973",table.rows().get(0)[6]);
    }
    @Test void countParsingRejectsHugeExponentsWithoutExpandingThemAndTotalsSurviveLongOverflow()throws Exception {
        for(String exponent:List.of("1e100000000","1e-100000000")){matrix("id\tA\tB\tC\nG\t1\t"+exponent+"\t3\n");assertTimeoutPreemptively(java.time.Duration.ofSeconds(5),()->assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(context(Map.of("value_scale","counts"),"exp-"+exponent)).state()));}
        var source=new StringBuilder("id\tA\tB\tC\n");for(int i=0;i<1025;i++)source.append("G").append(i).append("\t9007199254740991\t1\t2\n");matrix(source.toString());assertEquals(State.COMPLETE,run(Map.of("value_scale","counts")).state());assertEquals(new java.math.BigInteger("9007199254740991").multiply(java.math.BigInteger.valueOf(1025)).toString(),DelimitedData.read(root.resolve("qc/assay-sample-qc.tsv")).rows().get(0)[6]);
    }
    @Test void duplicateIdsLateMalformedRowsAndBoundsHoldWholeStage()throws Exception {
        for(String bad:List.of("id\tA\tA\nG\t1\t2\n","id\tA\tB\tC\nG\t1\t2\t3\nG\t2\t3\t4\n","id\tA\tB\tC\nG\t1\t2\t3\nH\t2\t3\n")){matrix(bad);assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(context(Map.of(),"hold-"+Math.abs(bad.hashCode()))).state());}
        matrix("id\tA\tB\tC\nG\t1\t2\t3\nH\t2\t3\t4\n");for(var bound:List.of(Map.<String,Object>of("maximum_features",1),Map.<String,Object>of("maximum_samples",2),Map.<String,Object>of("maximum_cells",5),Map.<String,Object>of("maximum_retained_mib",1))){var c=context(bound,"bound-"+bound.keySet().iterator().next());assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(c).state());assertFalse(Files.exists(c.outputDirectory().resolve("assay-matrix.tsv")));}
    }
    @Test void zeroRetainedPopulationNeverPromotesAnEmptyMatrix()throws Exception {
        matrix("id\tA\tB\tC\nG\t1\t1\t1\n");assertEquals(State.WAITING_FOR_INPUT,run(Map.of("mode","filter")).state());assertFalse(Files.exists(root.resolve("qc/assay-matrix.tsv")));
    }
    private Map<String,Object> supplied()throws Exception {
        Files.writeString(root.resolve("report.yaml"),"schema_version: 1\ndataset_id: a\nmatrix_sha256: "+FollowupSupport.hash(root.resolve("matrix.tsv"))+"\ninput_state: post_qc\nsample_count: 3\nfeature_count: 2\nmethod: {name: lab-qc, version: '1.2', parameters: {external_threshold: 0.5}}\n");Files.writeString(root.resolve("samples.tsv"),"sample_id\tdecision\treason\nA\tkeep\t\nB\texclude\tlab decision\nC\tkeep\t\n");Files.writeString(root.resolve("features.tsv"),"feature_id\tdecision\nG\tkeep\nH\texclude\n");return Map.of("source","supplied","supplied_report","report.yaml","supplied_samples","samples.tsv","supplied_features","features.tsv");
    }
    @Test void suppliedPostQcInputsAreReusedWithoutReapplyingNativeThresholds()throws Exception {
        matrix("id\tA\tB\tC\nG\t5\tInfinity\t5\nH\tbad\tbad\tbad\n");var p=supplied();var r=run(p);assertEquals(State.COMPLETE,r.state());assertFalse(r.details().containsKey("parameters"));assertEquals("source_declared_not_independently_certified",r.details().get("appraisal_status"));assertEquals("NA",feature("G")[2]);assertEquals("G\t5.0\t5.0",Files.readAllLines(root.resolve("qc/assay-matrix.tsv")).get(1));assertTrue(Files.exists(root.resolve("qc/supplied-inputs/supplied_report.yaml")));assertFalse(Files.exists(root.resolve("absent-phenotype.tsv")));
    }
    @Test void oversizedSuppliedIntegerFieldsCannotWrapIntoPlausibleSchemaOrPopulationCounts()throws Exception {
        matrix("id\tA\tB\tC\nG\t1\t2\t3\nH\t2\t3\t4\n");
        for(String key:List.of("schema_version","sample_count","feature_count")){var p=supplied();String report=Files.readString(root.resolve("report.yaml"));long value=key.equals("schema_version")?1:key.equals("sample_count")?3:2;String huge=java.math.BigInteger.ONE.shiftLeft(64).add(java.math.BigInteger.valueOf(value)).toString();Files.writeString(root.resolve("report.yaml"),report.replace(key+": "+value,key+": "+huge));assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(context(p,"overflow-"+key)).state());assertFalse(Files.exists(root.resolve("overflow-"+key+"/assay-matrix.tsv")));}
    }
    @Test void suppliedCoverageAndSourceHashCannotBeInferredOrFavourablyIntersected()throws Exception {
        matrix("id\tA\tB\tC\nG\t1\t2\t3\nH\t2\t3\t4\n");var p=supplied();Files.writeString(root.resolve("features.tsv"),"feature_id\tdecision\nG\tkeep\n");assertEquals(State.WAITING_FOR_INPUT,run(p).state());assertFalse(Files.exists(root.resolve("qc/assay-matrix.tsv")));
        supplied();Files.writeString(root.resolve("samples.tsv"),"sample_id\tdecision\nA\tkeep\nB\tkeep\nC\tkeep\nD\texclude\n");assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(context(p,"extra")).state());
        supplied();matrix("id\tA\tB\tC\nG\t9\t2\t3\nH\t2\t3\t4\n");assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(context(p,"hash")).state());
    }
    @Test void consumersRequireCompleteSealedExactSourceAndPolicy()throws Exception {
        matrix("id\tA\tB\tC\nG\t1\t2\t3\nH\t1\t1\t1\n");var p=Map.<String,Object>of("mode","filter");var r=run(p);assertEquals(State.COMPLETE,r.state());var hashes=new LinkedHashMap<String,Object>();for(String file:r.outputs())hashes.put(file,FollowupSupport.hash(root.resolve("qc").resolve(file)));write(root.resolve("qc/manifest.yaml"),Map.of("operation_id","qc","operation_type","assay_qc","hypothesis","H","state","complete","output_sha256",hashes));
        var consumer=new Context(root,root.resolve("consumer"),Map.of("type","association","hypothesis","H","assay_qc_producers",Map.of("a","qc")),study(p),Map.of(),Map.of("qc",root.resolve("qc")));
        assertEquals(root.resolve("qc/assay-matrix.tsv"),StudyAssayQcSources.matrix(consumer,dataset()));Files.writeString(root.resolve("qc/assay-matrix.tsv"),"tampered");assertThrows(java.io.IOException.class,()->StudyAssayQcSources.matrix(consumer,dataset()));
    }
    @Test void changedExternalDecisionsCannotReuseAnOtherwiseValidSealedProducer()throws Exception {
        matrix("id\tA\tB\tC\nG\t1\t2\t3\nH\t2\t3\t4\n");var p=supplied();var result=run(p);assertEquals(State.COMPLETE,result.state());var hashes=new LinkedHashMap<String,Object>();for(String file:result.outputs())hashes.put(file,FollowupSupport.hash(root.resolve("qc").resolve(file)));write(root.resolve("qc/manifest.yaml"),Map.of("operation_id","qc","operation_type","assay_qc","hypothesis","H","state","complete","output_sha256",hashes));
        var c=new Context(root,root.resolve("consumer"),Map.of("type","association","hypothesis","H","assay_qc_producers",Map.of("a","qc")),study(p),Map.of(),Map.of("qc",root.resolve("qc")));assertEquals(root.resolve("qc/assay-matrix.tsv"),StudyAssayQcSources.matrix(c,dataset()));
        Files.writeString(root.resolve("samples.tsv"),Files.readString(root.resolve("samples.tsv")).replace("B\texclude","B\tkeep"));assertThrows(java.io.IOException.class,()->StudyAssayQcSources.matrix(c,dataset()));
    }
    @Test void policyRejectsImplicitMaskingUnknownFieldsAndNestedOrInvalidOverrides() {
        for(var p:List.of(Map.<String,Object>of("invalid_values","mask"),Map.<String,Object>of("minimum_sd",Double.NaN),Map.<String,Object>of("source","supplied"),Map.<String,Object>of("unknown",true),Map.<String,Object>of("maximum_cells",1.5),Map.<String,Object>of("dataset_overrides",Map.of("a",Map.of("dataset_overrides",Map.of())))))assertThrows(IllegalArgumentException.class,()->StudyAssayQcPolicy.resolve(p));
    }
}
