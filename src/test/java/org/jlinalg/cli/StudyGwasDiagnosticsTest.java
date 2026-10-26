/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyOperationExecutor.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyGwasDiagnosticsTest {
    @TempDir Path root;
    private static final String HEADER="feature_id\tp_value\tlog_p_value\tchromosome\tposition\tgenome_build\tminor_allele_frequency\tminor_allele_count\timputation_info\n";
    private static final String ROWS="v1\t.5\tNA\tchr1\t100\tGRCh38\t.001\t10\t.7\nv2\t.5\tNA\t2\t200\thg38\t.1\t100\t.95\n";

    @Test void logsRetainUnderflowAndRejectInconsistentOrInvalidProbabilities() {
        assertEquals(-1000,StudyGwasDiagnostics.logProbability("0","-1000"));
        assertEquals(-2,StudyGwasDiagnostics.logProbability("NA","-2"));
        assertTrue(Double.isNaN(StudyGwasDiagnostics.logProbability("0","NA")));
        for(String[] values:List.of(new String[]{".01","-100"},new String[]{"Infinity","-2"},new String[]{".2","invalid"},new String[]{"-1","NA"},new String[]{".2","1"}))assertTrue(Double.isNaN(StudyGwasDiagnostics.logProbability(values[0],values[1])));
        assertEquals(1,StudyGwasDiagnostics.lambda(new double[]{Math.log(.5),Math.log(.5)}),1e-14);
    }
    @Test void completeSourceGetsExactQqRowsAndDeclaredCoordinateManhattanWithoutChangingP() throws Exception {
        var c=context("complete",HEADER+ROWS,Map.of("dataset_coverage",Map.of("a","complete"),"minimum_lambda_tests",2));
        var result=new StudyCliExecutor().execute(c);assertEquals(State.COMPLETE,result.state(),result.summary());
        var summary=DelimitedData.readResult(c.outputDirectory().resolve("gwas-diagnostic-summary.tsv"));String[] all=summary.rows().stream().filter(r->r[summary.column("stratum")].equals("all")).findFirst().orElseThrow();
        assertEquals("1.0",all[summary.column("lambda_p_chisq1")]);assertEquals("observed_coordinates",all[summary.column("coordinate_status")]);
        assertTrue(Files.isRegularFile(c.outputDirectory().resolve("source-001-group-001-qq.pdf")));
        assertTrue(Files.isRegularFile(c.outputDirectory().resolve("source-001-group-001-manhattan.png")));
        var qq=DelimitedData.readResult(c.outputDirectory().resolve("source-001-group-001-qq.tsv"));assertEquals(2,qq.rows().size());
        assertEquals(-Math.log10(1.0/3),Double.parseDouble(qq.rows().get(0)[qq.column("expected_neg_log10_uniform_order_mean")]),1e-14);
        assertEquals(Files.readString(c.dependencyDirectories().get("source").resolve("standard-results/a.tsv")),Files.readString(c.outputDirectory().resolve("source-001-statistics.tsv")));
    }
    @Test void unknownExportWithholdsLambdaAndMissingOrMixedCoordinatesRemainDistinct() throws Exception {
        var c=context("unknown",HEADER+ROWS.replace("hg38","GRCh37"),Map.of("minimum_lambda_tests",2));
        var result=new StudyCliExecutor().execute(c);assertEquals(State.COMPLETE,result.state());
        var table=DelimitedData.readResult(c.outputDirectory().resolve("gwas-diagnostic-summary.tsv"));var row=table.rows().get(0);
        assertEquals("unknown",row[table.column("testing_coverage")]);assertEquals("NA",row[table.column("lambda_p_chisq1")]);assertEquals("held_mixed_genome_builds",row[table.column("coordinate_status")]);
        assertFalse(Files.exists(c.outputDirectory().resolve("source-001-group-001-manhattan.png")));
    }
    @Test void unresolvedZerosCannotDisappearFromAnOtherwiseCompleteCalibration() throws Exception {
        var c=context("zeros",HEADER+ROWS+"v3\t0\tNA\t1\t300\tGRCh38\t.1\t50\t.9\n",Map.of("dataset_coverage",Map.of("a","complete"),"minimum_lambda_tests",2));
        assertEquals(State.COMPLETE,new StudyCliExecutor().execute(c).state());
        var table=DelimitedData.readResult(c.outputDirectory().resolve("gwas-diagnostic-summary.tsv"));var row=table.rows().get(0);
        assertEquals("3",row[table.column("observed_tests")]);assertEquals("2",row[table.column("diagnostic_tests")]);assertEquals("1",row[table.column("unresolved_tests")]);assertEquals("NA",row[table.column("lambda_p_chisq1")]);
    }
    @Test void selectedOnlyPooledSourceCannotBeCalledACompleteGenome() throws Exception {
        var c=context("selected",HEADER+ROWS,Map.of("minimum_lambda_tests",2));Path dir=Files.createDirectory(root.resolve("pool"));
        Files.writeString(dir.resolve("meta-analysis.tsv"),HEADER+ROWS);StudyPublicEvidence.write(dir.resolve("supplied-analysis.yaml"),Map.of("family",Map.of("coverage","selected_only","tested_count",400000)));
        seal(dir,"meta_analysis",Map.of("meta-analysis.tsv",FollowupSupport.hash(dir.resolve("meta-analysis.tsv")),"supplied-analysis.yaml",FollowupSupport.hash(dir.resolve("supplied-analysis.yaml"))));
        var pooled=new Context(c.project(),c.outputDirectory(),c.operation(),c.study(),c.plan(),Map.of("pool",dir));assertEquals(State.COMPLETE,new StudyCliExecutor().execute(pooled).state());
        var table=DelimitedData.readResult(c.outputDirectory().resolve("gwas-diagnostic-summary.tsv"));assertEquals("selected_only_or_unknown",table.rows().get(0)[table.column("testing_coverage")]);assertEquals("NA",table.rows().get(0)[table.column("lambda_p_chisq1")]);
    }
    @Test void tamperedSourceIsRejectedAndDisplayLimitsDoNotSelectASmallerTestingFamily() throws Exception {
        var c=context("tamper",HEADER+ROWS,Map.of("maximum_rows",2));Files.writeString(c.dependencyDirectories().get("source").resolve("standard-results/a.tsv"),HEADER);
        assertThrows(IOException.class,()->new StudyCliExecutor().execute(c));
        var bounded=context("bounded",HEADER+ROWS+ROWS.replace("v1","v3").replace("v2","v4"),Map.of("maximum_rows",2));
        var result=new StudyCliExecutor().execute(bounded);assertEquals(State.WAITING_FOR_INPUT,result.state());assertEquals(false,StudyPublicEvidence.map(result.details().get("method_report")).get("performed"));assertTrue(Files.exists(bounded.outputDirectory().resolve("source-001-statistics.tsv")));assertFalse(Files.exists(bounded.outputDirectory().resolve("source-001-group-001-qq.tsv")));
    }
    @Test void automaticGenomicGoalCanBeDisabledAndSuppliedDiagnosticsSuppressNativeWork() throws Exception {
        var document=new LinkedHashMap<>(study(Map.of()));var h=new LinkedHashMap<>(StudyPublicEvidence.map(StudyPublicEvidence.list(document.get("hypotheses")).get(0)));
        var analysis=new LinkedHashMap<String,Object>();analysis.put("mode","auto");analysis.put("goals",List.of());analysis.put("gwas_diagnostics",Map.of("enabled",false));h.put("analysis",analysis);document.put("hypotheses",List.of(h));
        Path path=root.resolve("study.yaml");StudyPublicEvidence.write(path,document);assertFalse(types(path).contains("gwas_diagnostics"));assertTrue(types(path).contains("gwas_enrichment"));
        analysis.put("gwas_diagnostics",Map.of());StudyPublicEvidence.write(path,document);assertTrue(types(path).contains("gwas_diagnostics"));assertTrue(types(path).contains("gwas_enrichment"));
        analysis.put("supplied_results",List.of(Map.of("id","external-diagnostic","kind","gwas_diagnostics","files",Map.of("results","diagnostic.tsv"),"scope",Map.of("phenotype","y"),"method",Map.of("name","Synthetic external diagnostics"))));StudyPublicEvidence.write(path,document);
        var operations=StudyPublicEvidence.list(org.jlinalg.study.StudyPlanner.compile(org.jlinalg.study.StudyResolver.resolve(path)).document().get("operations")).stream().map(StudyPublicEvidence::map).filter(op->op.get("type").equals("gwas_diagnostics")).toList();assertEquals(1,operations.size());assertEquals("external-diagnostic",operations.get(0).get("supplied_result_id"));
    }
    @Test void typedSuppliedDiagnosticsRetainExternalCountsWithoutReexecution() throws Exception {
        var document=new LinkedHashMap<>(study(Map.of()));var h=new LinkedHashMap<>(StudyPublicEvidence.map(StudyPublicEvidence.list(document.get("hypotheses")).get(0)));
        h.put("analysis",Map.of("mode","manual","goals",List.of("gwas_diagnostics"),"supplied_results",List.of(Map.of("id","external","kind","gwas_diagnostics","files",Map.of("results","external.tsv"),"scope",Map.of("phenotype","y","datasets",List.of("a"),"cohorts",List.of("A")),"method",Map.of("name","External synthetic diagnostic")))));document.put("hypotheses",List.of(h));
        String data=String.join("\t",StudyGwasDiagnostics.SUMMARY_COLUMNS)+"\ndataset:a\tpooled\tall\tunknown\t10\t10\t0\tNA\tunavailable_incomplete_or_unknown_export\t0\tunavailable_coordinates\n";Files.writeString(root.resolve("external.tsv"),data);
        var c=new Context(root,Files.createDirectory(root.resolve("external-output")),Map.of("type","gwas_diagnostics","hypothesis","H","supplied_result_id","external"),document,Map.of(),Map.of());
        var imported=new StudyCliExecutor().execute(c);assertEquals(State.COMPLETE,imported.state());assertEquals(false,imported.details().get("analysis_executed_by_jlinalg"));assertEquals(data,Files.readString(c.outputDirectory().resolve("gwas-diagnostic-summary.tsv")));
        Files.writeString(root.resolve("external.tsv"),data.replace("\tNA\tunavailable_incomplete_or_unknown_export","\t1\tdescriptive_P_transform"));
        var bad=new Context(root,Files.createDirectory(root.resolve("external-bad")),c.operation(),document,Map.of(),Map.of());assertThrows(IOException.class,()->new StudyCliExecutor().execute(bad));
    }
    @Test void sourceContrastsAreSeparateAndIncompleteProducersNeverGetLambda() throws Exception {
        String data="feature_id\tterm\tp_value\nv1\tA\t.5\nv2\tA\t.5\nv1\tB\t.01\nv2\tB\t.02\n";var c=context("contrasts",data,Map.of("minimum_lambda_tests",2,"dataset_coverage",Map.of("a","complete")));
        assertEquals(State.COMPLETE,new StudyCliExecutor().execute(c).state());var table=DelimitedData.readResult(c.outputDirectory().resolve("gwas-diagnostic-summary.tsv"));assertEquals(2,table.rows().stream().filter(r->r[table.column("stratum")].equals("all")).count());
        var partial=context("partial",HEADER+ROWS,Map.of("minimum_lambda_tests",2,"dataset_coverage",Map.of("a","complete")));Path manifest=partial.dependencyDirectories().get("source").resolve("manifest.yaml");var m=new LinkedHashMap<>(StudyYaml.loadArtifact(manifest));m.put("state","waiting_for_input");StudyPublicEvidence.write(manifest,m);
        assertEquals(State.COMPLETE,new StudyCliExecutor().execute(partial).state());var rows=DelimitedData.readResult(partial.outputDirectory().resolve("gwas-diagnostic-summary.tsv"));assertEquals("incomplete_producer",rows.rows().get(0)[rows.column("testing_coverage")]);assertEquals("NA",rows.rows().get(0)[rows.column("lambda_p_chisq1")]);
    }
    @Test void aRegisteredSourceBuildFillsMissingContextButNeverOverridesConflictingRows() throws Exception {
        for(String sourceBuild:List.of("GRCh38","GRCh37")){
            var c=context("registered-"+sourceBuild,HEADER+ROWS.replace("hg38","NA").replace("GRCh38","NA"),Map.of("minimum_lambda_tests",2));var study=new LinkedHashMap<>(c.study());var d=new LinkedHashMap<>(StudyPublicEvidence.map(StudyPublicEvidence.list(study.get("datasets")).get(0)));d.put("genome_build",sourceBuild);study.put("datasets",List.of(d));
            var context=new Context(c.project(),c.outputDirectory(),c.operation(),study,c.plan(),c.dependencyDirectories());assertEquals(State.COMPLETE,new StudyCliExecutor().execute(context).state());
            var report=StudyYaml.loadArtifact(c.outputDirectory().resolve("gwas-diagnostics.yaml"));var source=StudyPublicEvidence.map(StudyPublicEvidence.list(report.get("sources")).get(0));assertEquals(sourceBuild,source.get("registered_source_genome_build"));assertTrue(Files.exists(c.outputDirectory().resolve("source-001-producer.yaml")));
        }
        var c=context("registered-conflict",HEADER+ROWS,Map.of("minimum_lambda_tests",2));var study=new LinkedHashMap<>(c.study());var d=new LinkedHashMap<>(StudyPublicEvidence.map(StudyPublicEvidence.list(study.get("datasets")).get(0)));d.put("genome_build","GRCh37");study.put("datasets",List.of(d));var context=new Context(c.project(),c.outputDirectory(),c.operation(),study,c.plan(),c.dependencyDirectories());assertEquals(State.COMPLETE,new StudyCliExecutor().execute(context).state());var summary=DelimitedData.readResult(c.outputDirectory().resolve("gwas-diagnostic-summary.tsv"));assertEquals("held_mixed_genome_builds",summary.rows().get(0)[summary.column("coordinate_status")]);
    }
    @Test void nativePooledCoordinatesUseSealedContributorConsensusWithoutCopyingTheirTestStatistics() throws Exception {
        for(String mode:List.of("consistent","conflict","missing")){
            var base=context("projection-"+mode,HEADER+ROWS,Map.of("minimum_lambda_tests",2));Path pool=Files.createDirectory(base.project().resolve("pool"));
            Files.writeString(pool.resolve("meta-analysis.tsv"),"feature_id\tbeta\tse\tp_value\tn_cohorts\nv1\t.1\t.1\t.5\t2\nv2\t.1\t.1\t.5\t2\n");
            Files.writeString(pool.resolve("cohort-result-metadata.tsv"),"dataset_id\tfeature_id\tchromosome\tposition\tgenome_build\tlog_p_value\tminor_allele_count\na\tv1\tchr1\t100\tGRCh38\t-1000\t1\nb\tv1\t1\t100\thg38\t-2000\t2\na\tv2\tchr1\t200\tGRCh38\t-1000\t1\nb\tv2\t1\t"+(mode.equals("conflict")?"300":mode.equals("missing")?"NA":"200")+"\tGRCh38\t-2000\t2\n");
            seal(pool,"meta_analysis",Map.of("meta-analysis.tsv",FollowupSupport.hash(pool.resolve("meta-analysis.tsv")),"cohort-result-metadata.tsv",FollowupSupport.hash(pool.resolve("cohort-result-metadata.tsv"))));
            var study=new LinkedHashMap<>(base.study());var datasets=new java.util.ArrayList<Object>(StudyPublicEvidence.list(study.get("datasets")));var b=new LinkedHashMap<>(StudyPublicEvidence.map(datasets.get(0)));b.put("id","b");b.put("cohort","B");datasets.add(b);study.put("datasets",datasets);var operation=new LinkedHashMap<>(base.operation());operation.put("datasets",List.of("a","b"));var c=new Context(base.project(),base.outputDirectory(),operation,study,base.plan(),Map.of("pool",pool));assertEquals(State.COMPLETE,new StudyCliExecutor().execute(c).state());
            var summary=DelimitedData.readResult(c.outputDirectory().resolve("gwas-diagnostic-summary.tsv"));var all=summary.rows().get(0);assertEquals(mode.equals("conflict")?"held_contributor_coordinate_conflicts":mode.equals("missing")?"partial_coordinates":"observed_coordinates",all[summary.column("coordinate_status")]);
            var qq=DelimitedData.readResult(c.outputDirectory().resolve("source-001-group-001-qq.tsv"));assertEquals(-Math.log10(.5),Double.parseDouble(qq.rows().get(0)[qq.column("observed_neg_log10_p")]),1e-14);
            assertTrue(summary.rows().stream().anyMatch(r->r[summary.column("stratum")].equals("mac:unknown")));assertTrue(Files.exists(c.outputDirectory().resolve("source-001-coordinate-projection.tsv")));
        }
    }
    @Test void unknownBuildTokensAndNegativeInfoStayExplicitRatherThanAcquiringFalseRanges() throws Exception {
        var c=context("unknown-build",HEADER+ROWS.replace("GRCh38","unknown").replace("hg38","auto").replace("\t.7\n","\t-.2\n"),Map.of("minimum_lambda_tests",2));assertEquals(State.COMPLETE,new StudyCliExecutor().execute(c).state());var table=DelimitedData.readResult(c.outputDirectory().resolve("gwas-diagnostic-summary.tsv"));assertEquals("observed_coordinates_build_unknown",table.rows().get(0)[table.column("coordinate_status")]);assertTrue(table.rows().stream().anyMatch(r->r[table.column("stratum")].equals("info:negative_source_value")));
    }
    @Test void automaticSnpDiagnosticsDoNotInventScansForRareOrLaterStageOnlyInputs() throws Exception {
        for(String stage:List.of("set_association_results","variant_scores","mr_results","colocalization_results","fine_mapping_results")){
            var document=new LinkedHashMap<>(study(Map.of()));var dataset=new LinkedHashMap<>(StudyPublicEvidence.map(StudyPublicEvidence.list(document.get("datasets")).get(0)));dataset.put("stage",stage);dataset.put("metadata",Map.of("complete_testing_family",true));document.put("datasets",List.of(dataset));var h=new LinkedHashMap<>(StudyPublicEvidence.map(StudyPublicEvidence.list(document.get("hypotheses")).get(0)));h.put("analysis",Map.of("mode","auto","goals",List.of()));document.put("hypotheses",List.of(h));Path path=root.resolve(stage+".yaml");StudyPublicEvidence.write(path,document);var actual=types(path);assertFalse(actual.contains("gwas_diagnostics"),stage);assertFalse(actual.contains("gwas_enrichment"),stage);
        }
        var document=new LinkedHashMap<>(study(Map.of()));document.put("datasets",List.of(Map.of("id","a","cohort","A","type","gwas","stage","analysis_reference","supplied_result_id","pool","files",Map.of())));var h=new LinkedHashMap<>(StudyPublicEvidence.map(StudyPublicEvidence.list(document.get("hypotheses")).get(0)));h.put("analysis",Map.of("mode","auto","goals",List.of(),"supplied_results",List.of(Map.of("id","pool","kind","meta_analysis","files",Map.of("results","pool.tsv"),"scope",Map.of("datasets",List.of("a"),"cohorts",List.of("A"),"phenotype","y"),"method",Map.of("name","Synthetic genomic pool"),"family",Map.of("id","pool-family","coverage","complete","tested_count",2,"adjustment","BH")))));document.put("hypotheses",List.of(h));Path path=root.resolve("reference.yaml");StudyPublicEvidence.write(path,document);assertTrue(types(path).contains("gwas_diagnostics"));assertTrue(types(path).contains("gwas_enrichment"));
        var explicit=new LinkedHashMap<>(study(Map.of()));var dataset=new LinkedHashMap<>(StudyPublicEvidence.map(StudyPublicEvidence.list(explicit.get("datasets")).get(0)));dataset.put("stage","set_association_results");dataset.put("metadata",Map.of("complete_testing_family",true));explicit.put("datasets",List.of(dataset));var hypothesis=new LinkedHashMap<>(StudyPublicEvidence.map(StudyPublicEvidence.list(explicit.get("hypotheses")).get(0)));hypothesis.put("analysis",Map.of("mode","auto","goals",List.of(),"gwas_enrichment",Map.of("summary",Map.of("file","summary.tsv"))));explicit.put("hypotheses",List.of(hypothesis));Path summary=root.resolve("explicit-summary.yaml");StudyPublicEvidence.write(summary,explicit);assertTrue(types(summary).contains("gwas_enrichment"));assertFalse(types(summary).contains("gwas_diagnostics"));
    }
    private List<Object> types(Path path)throws Exception{return StudyPublicEvidence.list(org.jlinalg.study.StudyPlanner.compile(org.jlinalg.study.StudyResolver.resolve(path)).document().get("operations")).stream().map(StudyPublicEvidence::map).map(m->m.get("type")).toList();}
    private Context context(String name,String data,Map<String,Object> policy)throws Exception {
        Path project=Files.createDirectory(root.resolve(name)),source=Files.createDirectories(project.resolve("source/standard-results"));Files.writeString(source.resolve("a.tsv"),data);
        seal(source.getParent(),"validate_harmonize",Map.of("standard-results/a.tsv",FollowupSupport.hash(source.resolve("a.tsv"))));
        return new Context(project,Files.createDirectory(project.resolve("output")),Map.of("type","gwas_diagnostics","hypothesis","H","datasets",List.of("a")),study(policy),Map.of(),Map.of("source",source.getParent()));
    }
    private void seal(Path directory,String type,Map<String,Object> hashes)throws Exception{StudyPublicEvidence.write(directory.resolve("manifest.yaml"),Map.of("operation_id","producer-"+type,"operation_type",type,"hypothesis","H","state","complete","output_sha256",hashes));}
    private Map<String,Object> study(Map<String,Object> policy){return Map.of("schema_version",1,"project",Map.of("id","diagnostic-fixture","title","Synthetic GWAS diagnostics","description","Software fixture only."),"datasets",List.of(Map.of("id","a","cohort","A","type","gwas","stage","association_results","files",Map.of("results","results.tsv"))),"hypotheses",List.of(Map.of("id","H","description","Describe the supplied tests without changing inference.","outcome","y","analysis",Map.of("mode","manual","goals",List.of("gwas_diagnostics"),"gwas_diagnostics",policy))));}
}
