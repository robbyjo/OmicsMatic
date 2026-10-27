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
import org.jlinalg.study.StudyOperationExecutor.*;
import org.jlinalg.study.StudyPlanner;
import org.jlinalg.study.StudyResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyPooledVariantInputsTest {
    @TempDir Path root;
    private static final String HEADER="feature_id\tbeta\tse\tp_value\tchromosome\tposition\tref\talt\n";
    private static final String ROWS="v1\t.2\t.1\t.9\t1\t100\tA\tG\nv2\t.4\t.1\t.001\t1\t101\tA\tG\nv3\t-.2\t.1\t.2\t1\t102\tA\tG\n";
    private Map<String,Object> definition() {
        return new LinkedHashMap<>(Map.of("id","external","kind","meta_analysis","files",Map.of("results","meta.tsv"),
            "scope",Map.of("modality","gwas","feature_id_type","variant","genome_build","GRCh38"),
            "method",Map.of("name","External synthetic pool"),"family",Map.of("id","all-snps","coverage","complete","tested_count",3,"adjustment","BH")));
    }
    private Map<String,Object> policy() { return new LinkedHashMap<>(Map.of("reference_fasta","reference.fa","maximum_variants_per_batch",1,
        "consequences",Map.of("engine","import","format","canonical","annotations","annotations.tsv","release","synthetic-v1"),"alphagenome",Map.of("enabled",false))); }
    private void files(String results) throws Exception {
        Files.writeString(root.resolve("meta.tsv"),results);Files.writeString(root.resolve("reference.fa"),">1\n"+"A".repeat(2000)+"\n");
        Files.writeString(root.resolve("annotations.tsv"),"genome_build\tchrom\tpos\tref\talt\tgene\ttranscript\tconsequence\nGRCh38\t1\t100\tA\tG\tENSG1\tENST1\tmissense_variant\nGRCh38\t1\t101\tA\tG\tENSG2\tENST2\tintron_variant\nGRCh38\t1\t102\tA\tG\tENSG3\tENST3\tsynonymous_variant\nGRCh38\t1\t103\tA\tG\tENSG4\tENST4\tintron_variant\n");
    }
    private static void seal(Context c,Result result) throws Exception {
        var hashes=new LinkedHashMap<String,Object>();try(var paths=Files.walk(c.outputDirectory())){for(Path file:paths.filter(Files::isRegularFile).toList())hashes.put(c.outputDirectory().relativize(file).toString().replace('\\','/'),FollowupSupport.hash(file));}
        write(c.outputDirectory().resolve("manifest.yaml"),Map.of("operation_id",c.operation().get("id"),"operation_type",c.operation().get("type"),"hypothesis",c.operation().get("hypothesis"),"state",result.state().value(),"details",result.details(),"output_sha256",hashes));
    }
    private Context context(String name,Map<String,Object> d,Map<String,Object> p,Map<String,Object> extra) throws Exception {
        var a=new LinkedHashMap<String,Object>(Map.of("supplied_results",List.of(d),"variant_followup",p));a.putAll(extra);
        var study=Map.<String,Object>of("datasets",List.of(),"resources",Map.of("directory","fixture-resources","network","offline","downloads","deny"),"hypotheses",List.of(Map.of("id","H","analysis",a)));
        String type="sensitivity".equals(d.get("role"))?"supplied_sensitivity":text(d.get("kind"));
        var imported=new Context(root,Files.createDirectory(root.resolve(name+"-import")),Map.of("id","meta","type",type,"hypothesis","H","supplied_result_id","external","datasets",List.of()),study,Map.of(),Map.of());
        var result=new StudyCliExecutor().execute(imported);seal(imported,result);
        return new Context(root,Files.createDirectory(root.resolve(name)),Map.of("id","variant","type","variant_scoring","hypothesis","H","datasets",List.of()),study,Map.of(),Map.of("meta",imported.outputDirectory()));
    }
    private Context context(String name) throws Exception { files(HEADER+ROWS);return context(name,definition(),policy(),Map.of()); }
    private static List<Map<String,Object>> sources(Result r){return list(r.details().get("pooled_sources")).stream().map(StudyPublicEvidence::map).toList();}

    @Test void fullPoolAnnotatesEveryEligibleSnpIncludingNonsignificantRowsWithProvenance() throws Exception {
        var c=context("full");var r=new StudyCliExecutor().execute(c);assertEquals(State.COMPLETE,r.state(),r.summary());
        var data=DelimitedData.readResult(c.outputDirectory().resolve("variant-inventory.tsv"));assertEquals(3,data.rows().size());
        assertTrue(data.rows().stream().allMatch(row->row[data.column("dataset_id")].isEmpty()&&row[data.column("input_source")].equals("official_meta_analysis")&&row[data.column("source_operation_id")].equals("meta")&&row[data.column("consequence_status")].equals("annotated")));
        assertEquals(3,DelimitedData.readResult(c.outputDirectory().resolve("variant-consequences.tsv")).rows().size());
        assertEquals(false,r.details().get("significance_filter_applied"));assertEquals(true,r.details().get("complete_source_coverage"));assertEquals(3,sources(r).get(0).get("eligible_statistical_rows"));
        assertEquals(FollowupSupport.hash(c.dependencyDirectories().get("meta").resolve("meta-analysis.tsv")),FollowupSupport.hash(c.outputDirectory().resolve("primary-pooled-results.tsv")));
    }
    @Test void alphaGenomeImportsKeepScorerTissueAndTranscriptForEachPoolVariant() throws Exception {
        files(HEADER+ROWS);var p=policy();p.put("alphagenome",Map.of("engine","import","scores","scores.tsv"));var rows=new ArrayList<String[]>();
        for(int i=0;i<3;i++)rows.add(new String[]{"GRCh38:1:"+(100+i)+":A:G","atlas","fixture-v1","fixture-model","RNA_SEQ","ENSG"+i,"ENST"+i,"UBERON:0002107","track"+i,"-.4","-.95","chr1:1-2000","source"+i});
        FollowupSupport.table(root.resolve("scores.tsv"),StudyAlphaGenome.FIELDS,rows);var c=context("alpha",definition(),p,Map.of());var r=new StudyCliExecutor().execute(c);assertEquals(State.COMPLETE,r.state(),r.summary());
        var scores=DelimitedData.readResult(c.outputDirectory().resolve("alphagenome-scores.tsv"));assertEquals(3,scores.rows().size());assertTrue(scores.rows().stream().allMatch(row->row[scores.column("ontology_curie")].equals("UBERON:0002107")));
    }
    @Test void missingReferenceIsAVisiblePrerequisiteRatherThanNotApplicable() throws Exception {
        var c=context("missing-reference");Files.delete(root.resolve("reference.fa"));var r=new StudyCliExecutor().execute(c);assertEquals(State.WAITING_FOR_INPUT,r.state());assertEquals(3,r.details().get("unresolved_records"));
        assertEquals(false,map(r.details().get("method_report")).get("performed"));assertTrue(r.outputs().contains("primary-pooled-results.tsv"));
    }
    @Test void geneLevelGwasPoolCannotCreateSnpQueries() throws Exception {
        files(HEADER+ROWS);var d=definition();d.put("scope",Map.of("modality","gwas","feature_id_type","gene_symbol"));var c=context("genes",d,policy(),Map.of());var r=new StudyCliExecutor().execute(c);
        assertEquals(State.NOT_APPLICABLE,r.state());assertEquals(false,sources(r).get(0).get("variant_scope_applicable"));assertFalse(Files.exists(c.outputDirectory().resolve("variant-consequences.tsv")));assertTrue(r.outputs().contains("primary-result-selection.yaml"));
    }
    @Test void knownMolecularPoolIsNotMistakenForAnUndeclaredGenomicPool() throws Exception {
        files(HEADER+ROWS);var d=definition();d.put("scope",Map.of("modality","expression"));assertEquals(State.NOT_APPLICABLE,new StudyCliExecutor().execute(context("expression",d,policy(),Map.of())).state());
    }
    @Test void explicitUnknownGenomicNamespaceNeedsReviewWithoutQueries() throws Exception {
        files(HEADER+ROWS);var d=definition();d.put("scope",Map.of("modality","gwas","feature_id_type","custom_locus_set"));var c=context("unknown",d,policy(),Map.of());var r=new StudyCliExecutor().execute(c);assertEquals(State.WAITING_FOR_INPUT,r.state());assertEquals(false,r.details().get("performed"));
    }
    @Test void discoveryAndSensitivityPoolsCannotStandInForOfficialMeta() throws Exception {
        files(HEADER+ROWS);var d=definition();d.put("kind","discovery_meta_analysis");assertEquals(State.NOT_APPLICABLE,new StudyCliExecutor().execute(context("discovery",d,policy(),Map.of())).state());
        d=definition();d.put("role","sensitivity");assertEquals(State.NOT_APPLICABLE,new StudyCliExecutor().execute(context("sensitivity",d,policy(),Map.of())).state());
    }
    @Test void registeredAssociationIsNotReplacedByAvailableMeta() throws Exception {
        files(HEADER+ROWS);var c=context("association",definition(),policy(),Map.of("primary_result",Map.of("kind","association","rationale","Registered cohort source")));
        assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(c).state());assertFalse(Files.exists(c.outputDirectory().resolve("variant-consequences.tsv")));
    }
    @Test void selectedSourceIsAnnotatedButCannotClaimFullGwasCoverage() throws Exception {
        files(HEADER.replace("\n","\tadjusted_p\n")+String.join("\n",ROWS.lines().map(row->row+"\t"+(row.startsWith("v2\t")?".1":row.startsWith("v3\t")?".8":".99")).toList())+"\n");var d=definition();d.put("family",Map.of("id","original","coverage","selected_only","tested_count",100,"adjustment","BH","adjusted_p_from_full_family",true));
        var c=context("selected",d,policy(),Map.of());var r=new StudyCliExecutor().execute(c);assertEquals(State.WAITING_FOR_INPUT,r.state(),r.summary());assertEquals(false,r.details().get("complete_source_coverage"));
        assertEquals(3,DelimitedData.readResult(c.outputDirectory().resolve("variant-consequences.tsv")).rows().size());assertEquals(100,map(sources(r).get(0).get("source_testing_family")).get("tested_count"));
    }
    @Test void unknownSourceTestingFamilyCannotBecomeAnOfficialPool() throws Exception {
        files(HEADER+ROWS);var d=definition();d.remove("family");var c=context("no-family",d,policy(),Map.of());var r=new StudyCliExecutor().execute(c);assertEquals(State.WAITING_FOR_INPUT,r.state(),r.summary());assertTrue(r.details().containsKey("needs"));assertFalse(Files.exists(c.outputDirectory().resolve("variant-consequences.tsv")));
    }
    @Test void coordinateIdAndRowAllelesMustAgreeBeforeAnyProviderRuns() throws Exception {
        files(HEADER+ROWS.replace("v1\t","GRCh38:1:150:A:G\t"));var c=context("conflict",definition(),policy(),Map.of());var r=new StudyCliExecutor().execute(c);assertEquals(State.WAITING_FOR_INPUT,r.state());assertFalse(Files.exists(c.outputDirectory().resolve("variant-consequences.tsv")));assertTrue(r.outputs().contains("primary-pooled-results.tsv"));
    }
    @Test void effectAndOtherAllelesNeverSubstituteForReferenceAndAlternate() throws Exception {
        files((HEADER+ROWS).replace("\tref\talt","\teffect_allele\tother_allele"));var c=context("effect-alleles",definition(),policy(),Map.of());var r=new StudyCliExecutor().execute(c);assertEquals(State.WAITING_FOR_INPUT,r.state());assertEquals(0,r.details().get("unique_eligible_variants"));
        assertTrue(DelimitedData.readResult(c.outputDirectory().resolve("variant-inventory.tsv")).rows().stream().allMatch(row->row[4].equals("identity_unresolved")));
    }
    @Test void explicitBuildInCanonicalIdsCanIdentifyVariantsWithoutRowCoordinates() throws Exception {
        files("feature_id\tbeta\tse\tp_value\nGRCh38:1:100:A:G\t.2\t.1\t.9\nGRCh38:1:101:A:G\t.4\t.1\t.001\nGRCh38:1:102:A:G\t-.2\t.1\t.2\n");var d=definition();d.put("scope",Map.of("modality","gwas","feature_id_type","variant"));var r=new StudyCliExecutor().execute(context("named",d,policy(),Map.of()));assertEquals(State.COMPLETE,r.state(),r.summary());assertEquals(3,r.details().get("unique_eligible_variants"));
    }
    @Test void tamperedPrimarySourceCannotBeUsedForVariantAnnotation() throws Exception {
        var c=context("tamper");var input=c.dependencyDirectories().get("meta").resolve("meta-analysis.tsv");Files.writeString(input,Files.readString(input).replace("v1","changed"));var r=new StudyCliExecutor().execute(c);assertEquals(State.WAITING_FOR_INPUT,r.state());assertFalse(Files.exists(c.outputDirectory().resolve("variant-consequences.tsv")));
    }
    @Test void foreignHypothesisPoolCannotSupplyVariants() throws Exception {
        var c=context("foreign");Path m=c.dependencyDirectories().get("meta").resolve("manifest.yaml");var manifest=StudyYaml.loadArtifact(m);manifest.put("hypothesis","other");write(m,manifest);assertEquals(State.NOT_APPLICABLE,new StudyCliExecutor().execute(c).state());
    }
    @Test void missingOrAmbiguousContrastsNeverUseTheMinimumPValue() throws Exception {
        var original=context("terms-base");Path meta=original.dependencyDirectories().get("meta");Files.delete(meta.resolve("supplied-analysis.yaml"));Files.delete(meta.resolve("manifest.yaml"));
        String header=HEADER.replace("\tbeta","\tterm\tbeta");String rows=String.join("\n",ROWS.lines().map(row->row.replaceFirst("\t","\tfirst\t")).toList())+"\n";Files.writeString(meta.resolve("meta-analysis.tsv"),header+rows+rows.replace("first","second"));
        seal(new Context(root,meta,Map.of("id","meta","type","meta_analysis","hypothesis","H"),original.study(),Map.of(),Map.of()),Result.complete("Synthetic native multi-contrast pool",List.of("meta-analysis.tsv")));
        var blocked=withCohort(original,ROWS);assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(blocked).state());
        var study=new LinkedHashMap<String,Object>(blocked.study());var hypothesis=new LinkedHashMap<String,Object>(map(list(study.get("hypotheses")).get(0)));var a=new LinkedHashMap<String,Object>(map(hypothesis.get("analysis")));a.put("primary_result",Map.of("term","first","rationale","Registered phenotype contrast"));hypothesis.put("analysis",a);study.put("hypotheses",List.of(hypothesis));
        var chosen=new Context(root,Files.createDirectory(root.resolve("terms-chosen")),blocked.operation(),study,Map.of(),blocked.dependencyDirectories());var r=new StudyCliExecutor().execute(chosen);assertEquals(State.COMPLETE,r.state(),r.summary());assertEquals("first",sources(r).get(0).get("selected_term"));assertEquals(3,sources(r).get(0).get("selected_contrast_rows"));assertEquals(3,r.details().get("unique_eligible_variants"));
    }
    private Context withCohort(Context base,String rows) throws Exception {
        Path producer=Files.createDirectory(root.resolve("cohort-producer"));Files.createDirectories(producer.resolve("standard-results"));Files.writeString(producer.resolve("standard-results/a.tsv"),HEADER+rows);Files.writeString(root.resolve("cohort.tsv"),HEADER+rows);
        var d=Map.<String,Object>of("id","a","cohort","A","type","gwas","stage","association_results","genome_build","GRCh38","files",Map.of("results","cohort.tsv"));var study=new LinkedHashMap<>(base.study());study.put("datasets",List.of(d));
        var context=new Context(root,producer,Map.of("id","cohort","type","validate_harmonize","hypothesis","H"),study,Map.of(),Map.of());seal(context,Result.complete("Synthetic cohort",List.of("standard-results/a.tsv")));
        var deps=new LinkedHashMap<>(base.dependencyDirectories());deps.put("cohort",producer);var op=new LinkedHashMap<>(base.operation());op.put("datasets",List.of("a"));return new Context(root,base.outputDirectory(),op,study,Map.of(),deps);
    }
    @Test void pooledAndCohortAliasesShareQueriesWhileRetainingSeparateProvenance() throws Exception {
        var c=withCohort(context("mixed"),ROWS.lines().findFirst().orElseThrow()+"\nv4\t.1\t.1\t.8\t1\t103\tA\tG\n");var r=new StudyCliExecutor().execute(c);assertEquals(State.COMPLETE,r.state(),r.summary());assertEquals(5,r.details().get("input_records"));assertEquals(4,r.details().get("unique_eligible_variants"));assertEquals(5,DelimitedData.readResult(c.outputDirectory().resolve("variant-evidence.tsv")).rows().size());
    }
    @Test void explicitPoolAndCohortIdentityConflictStopsBeforeProviderWork() throws Exception {
        var c=withCohort(context("mixed-conflict"),ROWS.lines().findFirst().orElseThrow().replace("\t100\t","\t103\t")+"\n");var r=new StudyCliExecutor().execute(c);assertEquals(State.WAITING_FOR_INPUT,r.state());assertFalse(Files.exists(c.outputDirectory().resolve("variant-consequences.tsv")));
    }
    @Test void excludedCohortAliasCannotBorrowTheValidPooledAliasEvidence() throws Exception {
        var c=withCohort(context("excluded-alias"),ROWS.lines().findFirst().orElseThrow()+"\nv4\t.1\t.1\t.8\t1\t103\tA\tG\n");Path producer=c.dependencyDirectories().get("cohort");Files.writeString(producer.resolve("standard-results/a.tsv"),HEADER+"v4\t.1\t.1\t.8\t1\t103\tA\tG\n");Files.delete(producer.resolve("manifest.yaml"));
        seal(new Context(root,producer,Map.of("id","cohort","type","validate_harmonize","hypothesis","H"),c.study(),Map.of(),Map.of()),Result.complete("Filtered cohort",List.of("standard-results/a.tsv")));
        var r=new StudyCliExecutor().execute(c);assertEquals(State.COMPLETE,r.state(),r.summary());var inventory=DelimitedData.readResult(c.outputDirectory().resolve("variant-inventory.tsv"));var excluded=inventory.rows().stream().filter(row->row[0].equals("a")&&row[1].equals("v1")).findFirst().orElseThrow();assertEquals("false",excluded[3]);assertEquals("excluded_from_primary",excluded[6]);
        var evidence=DelimitedData.readResult(c.outputDirectory().resolve("variant-evidence.tsv"));assertTrue(evidence.rows().stream().noneMatch(row->row[evidence.column("dataset_id")].equals("a")&&row[evidence.column("feature_id")].equals("v1")));assertEquals(4,evidence.rows().size());
    }
    @Test void nativePooledRsidsCanReuseUnambiguousCohortReferenceIdentities() throws Exception {
        var c=context("native");Path meta=c.dependencyDirectories().get("meta");Files.delete(meta.resolve("supplied-analysis.yaml"));Files.writeString(meta.resolve("meta-analysis.tsv"),"feature_id\tbeta\tse\tp_value\nv1\t.2\t.1\t.9\nv4\t.1\t.1\t.8\n");
        seal(new Context(root,meta,Map.of("id","meta","type","meta_analysis","hypothesis","H"),c.study(),Map.of(),Map.of()),Result.complete("Synthetic native pool",List.of("meta-analysis.tsv")));
        var mixed=withCohort(c,ROWS.lines().findFirst().orElseThrow()+"\nv4\t.1\t.1\t.8\t1\t103\tA\tG\n");var r=new StudyCliExecutor().execute(mixed);assertEquals(State.COMPLETE,r.state(),r.summary());assertEquals(2,r.details().get("unique_eligible_variants"));assertEquals(4,r.details().get("input_records"));
    }
    private List<Map<String,Object>> plan(Map<String,Object> a) throws Exception {
        a.put("mode","auto");Path input=root.resolve("plan.json");write(input,Map.of("schema_version",1,"project",Map.of("id","pooled","title","Pooled SNPs","description","Synthetic","objective","exploration"),"resources",Map.of("directory","fixture-resources","network","offline","downloads","deny"),"datasets",List.of(),"hypotheses",List.of(Map.of("id","H","description","Missing SNP annotation","analysis",a))));
        var resolved=StudyResolver.resolve(input);return list(StudyPlanner.compile(resolved).document().get("operations")).stream().map(StudyPublicEvidence::map).toList();
    }
    @Test void autoModeInfersSnpScoringWithoutReconstructingAssociationOrCohorts() throws Exception {
        var ops=plan(new LinkedHashMap<>(Map.of("supplied_results",List.of(definition()))));var score=ops.stream().filter(o->"variant_scoring".equals(o.get("type"))).findFirst().orElseThrow();assertTrue(list(score.get("depends_on")).contains("H-op-001-meta_analysis"));assertTrue(ops.stream().noneMatch(o->"association".equals(o.get("type"))));
    }
    @Test void disabledProvidersAndGeneNamespacesDoNotInferSnpScoring() throws Exception {
        var a=new LinkedHashMap<String,Object>(Map.of("supplied_results",List.of(definition()),"variant_followup",Map.of("consequences",Map.of("enabled",false),"alphagenome",Map.of("enabled",false))));assertTrue(plan(a).stream().noneMatch(o->"variant_scoring".equals(o.get("type"))));
        var d=definition();d.put("scope",Map.of("modality","gwas","feature_id_type","gene_symbol"));assertTrue(plan(new LinkedHashMap<>(Map.of("supplied_results",List.of(d)))).stream().noneMatch(o->"variant_scoring".equals(o.get("type"))));
    }
    @Test void suppliedCompletedScoringRetainsItsOwner() throws Exception {
        var score=definition();score.put("id","scores");score.put("kind","variant_scoring");var ops=plan(new LinkedHashMap<>(Map.of("supplied_results",List.of(definition(),score)))).stream().filter(o->"variant_scoring".equals(o.get("type"))).toList();assertEquals(1,ops.size());assertEquals("scores",ops.get(0).get("supplied_result_id"));
    }
}
