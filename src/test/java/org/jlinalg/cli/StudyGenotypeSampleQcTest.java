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
import org.jlinalg.study.StudyOperationExecutor.Result;
import org.jlinalg.study.StudyOperationExecutor.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyGenotypeSampleQcTest {
    @TempDir Path root;
    private Path resource(String name)throws Exception{return Path.of(getClass().getResource("/genotype-sample-qc/"+name).toURI());}
    private Context context(String name,Map<String,Object> overrides,Map<String,Object> association,Map<String,Object> metadata,boolean imputed,boolean separate)throws Exception {
        return StudyGenotypeQcFixtures.context(root,name,overrides,association,metadata,imputed,separate);
    }
    private static Map<String,Object> qc(Result result){return map(result.details().get("genotype_sample_qc"));}
    private static void seal(Context c,Result result)throws Exception {var hashes=new LinkedHashMap<String,Object>();for(String output:result.outputs())hashes.put(output,FollowupSupport.hash(c.outputDirectory().resolve(output)));write(c.outputDirectory().resolve("manifest.yaml"),Map.of("operation_id","qc","operation_type","participant_qc","hypothesis","H","state",result.state().value(),"details",result.details(),"output_sha256",hashes));}
    @Test void fullPanelSelectionPruningProjectionAndPhenotypeJoinMatchIndependentReferences()throws Exception {
        var c=context("native",Map.of(),Map.of(),Map.of(),false,true);String raw=FollowupSupport.hash(c.project().resolve("phenotype.tsv")),matrix=FollowupSupport.hash(c.project().resolve("main.vcf")),panel=FollowupSupport.hash(c.project().resolve("panel.vcf"));var result=new StudyPrefitQc().execute(c);assertEquals(State.COMPLETE,result.state(),result.details().toString());
        assertEquals(62,result.details().get("analysis_samples"));assertEquals(2023,qc(result).get("ld_pruned_panel_markers"));assertEquals(4,qc(result).get("related_pairs"));assertEquals(62,qc(result).get("unrelated_basis_samples"));assertEquals(false,qc(result).get("association_markers_removed"));
        var actual=DelimitedData.readResult(c.outputDirectory().resolve("genotype-qc/panel.tsv"));var expected=DelimitedData.readResult(resource("expected-panel.tsv"));assertEquals(expected.rows().size(),actual.rows().size());for(int i=0;i<actual.rows().size();i++){assertEquals(expected.rows().get(i)[0],actual.rows().get(i)[0]);assertEquals(expected.rows().get(i)[1],actual.rows().get(i)[actual.column("disposition")]);assertEquals(expected.rows().get(i)[2],actual.rows().get(i)[actual.column("ld_partner")]);}
        var pcs=DelimitedData.readResult(resource("expected-pcs.tsv"));var selected=DelimitedData.read(c.outputDirectory().resolve("analysis-phenotype.tsv"));for(String[] row:selected.rows()){var ref=pcs.rows().stream().filter(r->r[0].equals(row[0])).findFirst().orElseThrow();assertEquals("true",ref[pcs.column("pca_basis")]);for(String field:List.of("GPC1","GPC2"))assertEquals(Double.parseDouble(ref[pcs.column(field)]),Double.parseDouble(row[selected.column(field)]),2e-8);}
        assertEquals(raw,FollowupSupport.hash(c.project().resolve("phenotype.tsv")));assertEquals(matrix,FollowupSupport.hash(c.project().resolve("main.vcf")));assertEquals(panel,FollowupSupport.hash(c.project().resolve("panel.vcf")));assertTrue(Files.readString(c.project().resolve("main.vcf")).contains("rare"));assertTrue(Files.isRegularFile(c.outputDirectory().resolve("genotype-qc/pca.png")));assertTrue(Files.readString(c.outputDirectory().resolve("genotype-qc-summary.yaml")).contains("required_manuscript_caveat"));
    }
    @Test void mainHardCallSourceCanSupplyTheRegisteredPanelWithoutSeparateInputs()throws Exception {
        var c=context("same-source",Map.of(),Map.of(),Map.of(),false,false);var result=new StudyPrefitQc().execute(c);assertEquals(State.COMPLETE,result.state(),result.details().toString());assertEquals(62,result.details().get("analysis_samples"));assertEquals(false,qc(result).get("separate_hard_call_panel"));
    }
    @Test void imputedAssociationUsesASeparatelyAlignedObservedHardCallPanel()throws Exception {
        var c=context("imputed",Map.of(),Map.of(),Map.of(),true,true);var result=new StudyPrefitQc().execute(c);assertEquals(State.COMPLETE,result.state(),result.details().toString());assertEquals(62,result.details().get("analysis_samples"));assertEquals(true,qc(result).get("separate_hard_call_panel"));
    }
    @Test void dosagesAreNotRoundedIntoObservedHardCallsWhenNoPanelExists()throws Exception {
        var c=context("dosage-only",Map.of(),Map.of(),Map.of(),true,false);var result=new StudyPrefitQc().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());assertFalse(Files.exists(c.outputDirectory().resolve("analysis-phenotype.tsv")));assertEquals(4,map(qc(result).get("marker_dispositions")).get("observed_hard_calls_unavailable"));
    }
    @Test void reviewAndExcessiveExclusionActionsRetainDiagnosticsWithoutAnApprovedPopulation()throws Exception {
        for(var policy:List.of(Map.<String,Object>of("relatedness_action","review"),Map.<String,Object>of("maximum_excluded_fraction",.001))) {
            var c=context("hold-"+root.toFile().list().length,policy,Map.of(),Map.of(),false,true);var result=new StudyPrefitQc().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());assertEquals(0,result.details().get("analysis_samples"));assertFalse(Files.exists(c.outputDirectory().resolve("analysis-phenotype.tsv")));assertTrue(Files.exists(c.outputDirectory().resolve("diagnostic-phenotype.tsv")));assertFalse(list(result.details().get("needs")).isEmpty());
        }
    }
    @Test void exhaustedSourceBudgetCannotCreateACompleteUnrelatednessScreen()throws Exception {
        var c=context("budget",Map.of("maximum_source_cells",1),Map.of(),Map.of(),false,true);var result=new StudyPrefitQc().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());assertEquals(false,qc(result).get("source_universe_complete"));assertEquals(false,qc(result).get("complete"));assertFalse(Files.exists(c.outputDirectory().resolve("analysis-phenotype.tsv")));
    }
    @Test void partialSampleCoverageCannotSilentlyChangeTheRegisteredAnalysisPopulation()throws Exception {
        var c=context("missing-sample",Map.of(),Map.of(),Map.of(),false,true);var lines=Files.readAllLines(c.project().resolve("panel.vcf"));var output=new ArrayList<String>();for(String line:lines){if(line.startsWith("##")){output.add(line);continue;}String[] cells=line.split("\t",-1);var keep=new ArrayList<String>(List.of(cells));keep.remove(9);output.add(String.join("\t",keep));}Files.write(c.project().resolve("panel.vcf"),output);var result=new StudyPrefitQc().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());assertTrue(qc(result).toString().contains("missing one or more"));assertEquals(64,qc(result).get("candidate_samples"));
    }
    @Test void pcaBudgetChargesTheGramAndProjectionAsWellAsTheEigensolve()throws Exception {
        var c=context("pca-work",Map.of("maximum_pca_work",500000),Map.of(),Map.of(),false,true);var result=new StudyPrefitQc().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());assertTrue(qc(result).toString().contains("Gram/eigen/projection"));assertEquals(4,qc(result).get("related_pairs"));assertFalse(Files.exists(c.outputDirectory().resolve("analysis-phenotype.tsv")));
    }
    @Test void anImpossibleRegisteredNativeBoundStopsBeforeTheInitialFullCallRateScan()throws Exception {
        var c=context("early-bound",Map.of("maximum_samples",10),Map.of(),Map.of(),false,true);var result=new StudyPrefitQc().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());assertEquals(0L,result.details().get("examined_genotype_variants"));assertEquals("not_examined_registered_native_qc_bound",result.details().get("sample_call_rate_status"));assertEquals(false,qc(result).get("source_universe_complete"));
    }
    @Test void heldGeneratedPcGroupsRemainUnavailableInsteadOfBreakingIndependentDiagnostics()throws Exception {
        var c=context("pc-group-held",Map.of("maximum_samples",10),Map.of(),Map.of(),false,true);var study=new LinkedHashMap<>(c.study());var h=new LinkedHashMap<>(map(list(study.get("hypotheses")).get(0)));var a=new LinkedHashMap<>(map(h.get("analysis")));a.put("subgroups",List.of(Map.of("id","pc_group","column","GPC1","cutoff",0)));h.put("analysis",a);study.put("hypotheses",List.of(h));var result=new StudyPrefitQc().execute(new Context(c.project(),c.outputDirectory(),c.operation(),study,c.plan(),c.dependencyDirectories()));assertEquals(State.WAITING_FOR_INPUT,result.state());assertTrue(list(result.details().get("distributions")).stream().map(StudyPublicEvidence::map).anyMatch(row->"unavailable_generated_pc_group".equals(row.get("status"))));assertTrue(Files.exists(c.outputDirectory().resolve("distribution-0.png")));assertFalse(Files.exists(c.outputDirectory().resolve("analysis-phenotype.tsv")));
    }
    @Test void retainingRelatedRowsRequiresAnExplicitSupportedCovarianceModel()throws Exception {
        var wrong=context("wrong-model",Map.of("relatedness_action","model"),Map.of(),Map.of(),false,true);assertEquals(State.WAITING_FOR_INPUT,new StudyPrefitQc().execute(wrong).state());
        var correct=context("mixed-model",Map.of("relatedness_action","model"),Map.of("model","lmm"),Map.of(),false,true);var result=new StudyPrefitQc().execute(correct);assertEquals(State.COMPLETE,result.state(),result.details().toString());assertEquals(64,result.details().get("analysis_samples"));assertEquals(62,qc(result).get("unrelated_basis_samples"));
    }
    @Test void aNegativeScreenDoesNotEraseKnownDependenceAndPcsAreNotImplicitCovariates()throws Exception {
        var dependent=context("known",Map.of(),Map.of(),Map.of("related_participants",true),false,true);assertEquals(State.WAITING_FOR_INPUT,new StudyPrefitQc().execute(dependent).state());
        var diagnostic=context("diagnostic-pcs",Map.of("include_pcs",false),Map.of(),Map.of(),false,true);var result=new StudyPrefitQc().execute(diagnostic);assertEquals(State.COMPLETE,result.state(),result.details().toString());assertEquals(true,qc(result).get("pca_computed"));assertFalse(DelimitedData.read(diagnostic.outputDirectory().resolve("analysis-phenotype.tsv")).header().contains("GPC1"));
    }
    @Test void generatedPcsCannotOverwriteSuppliedColumns()throws Exception {
        var c=context("collision",Map.of(),Map.of(),Map.of(),false,true);var lines=Files.readAllLines(c.project().resolve("phenotype.tsv"));for(int i=0;i<lines.size();i++)lines.set(i,lines.get(i)+(i==0?"\tGPC1":"\t123"));Files.write(c.project().resolve("phenotype.tsv"),lines);var result=new StudyPrefitQc().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());assertTrue(qc(result).toString().contains("collide"));assertTrue(Files.readString(c.project().resolve("phenotype.tsv")).contains("123"));
    }
    private static void replacePanelCalls(Context c,String sample,boolean all)throws Exception {
        var lines=Files.readAllLines(c.project().resolve("panel.vcf"));int column=-1,count=0;
        for(int i=0;i<lines.size();i++){if(lines.get(i).startsWith("##"))continue;String[] cells=lines.get(i).split("\t",-1);if(lines.get(i).startsWith("#CHROM")){column=List.of(cells).indexOf(sample);continue;}if(all)java.util.Arrays.fill(cells,9,cells.length,"./.");else if(count++<150)cells[column]="./.";lines.set(i,String.join("\t",cells));}Files.write(c.project().resolve("panel.vcf"),lines);
    }
    @Test void observedPanelCallRateExcludesPoorCallsEvenWhenImputedDosagesAreComplete()throws Exception {
        var c=context("observed-call-rate",Map.of(),Map.of(),Map.of(),true,true);replacePanelCalls(c,"S04",false);var result=new StudyPrefitQc().execute(c);assertEquals(State.COMPLETE,result.state(),result.details().toString());assertEquals(1,map(result.details().get("excluded_by_reason")).get("sample_call_rate"));assertEquals(61,result.details().get("analysis_samples"));assertFalse(DelimitedData.read(c.outputDirectory().resolve("analysis-phenotype.tsv")).rows().stream().anyMatch(row->row[0].equals("S04")));var initial=map(result.details().get("observed_genotype_call_rate"));assertEquals(true,initial.get("selection_complete"));assertEquals("present_existence_probe",initial.get("imputation_quality_status"));
    }
    @Test void missingSexChromosomeCallsDoNotEnterTheObservedAutosomalCallRateDenominator()throws Exception {
        var c=context("sex-missing",Map.of(),Map.of(),Map.of(),false,true);var text=new StringBuilder(Files.readString(c.project().resolve("panel.vcf")));for(int i=0;i<150;i++)text.append("X\t").append(i+1).append("\tmissingX").append(i).append("\tA\tG\t.\tPASS\t.\tGT").append("\t./.".repeat(64)).append('\n');Files.writeString(c.project().resolve("panel.vcf"),text);var result=new StudyPrefitQc().execute(c);assertEquals(State.COMPLETE,result.state(),result.details().toString());assertEquals(62,result.details().get("analysis_samples"));assertEquals(3072L,map(result.details().get("observed_genotype_call_rate")).get("eligible_call_rate_sites"));assertFalse(map(result.details().get("excluded_by_reason")).containsKey("sample_call_rate"));
    }
    @Test void anEmptyCallRatePopulationRetainsAllExclusionsAndPrivateEvidenceWithoutApprovedRows()throws Exception {
        var c=context("all-missing",Map.of(),Map.of(),Map.of(),false,true);replacePanelCalls(c,"S04",true);var result=new StudyPrefitQc().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());assertEquals(0,result.details().get("analysis_samples"));assertEquals(64,DelimitedData.readResult(c.outputDirectory().resolve("sample-exclusions.tsv")).rows().size());assertEquals(64,DelimitedData.readResult(c.outputDirectory().resolve("genotype-qc/initial-call-rates.tsv")).rows().size());assertTrue(result.outputs().contains("genotype-qc-summary.yaml"));assertFalse(Files.exists(c.outputDirectory().resolve("analysis-phenotype.tsv")));
    }
    @Test void anObservedPanelDoesNotSupplyMissingImputationQualityForTheMainMatrix()throws Exception {
        var c=context("missing-info",Map.of(),Map.of(),Map.of(),true,true);Path main=c.project().resolve("main.vcf");Files.writeString(main,Files.readString(main).replace("R2=0.99","."));var result=new StudyPrefitQc().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());assertEquals("absent",map(result.details().get("observed_genotype_call_rate")).get("imputation_quality_status"));assertEquals("unresolved",result.details().get("sample_call_rate_status"));assertFalse(Files.exists(c.outputDirectory().resolve("analysis-phenotype.tsv")));
    }
    @Test void omittedNativePcaDoesNotClaimToHaveRegeneratedSuppliedPcCovariates()throws Exception {
        var c=context("provided-pcs",Map.of("pca_components",0,"include_pcs",false),Map.of(),Map.of(),false,true);var result=new StudyPrefitQc().execute(c);assertEquals(State.COMPLETE,result.state(),result.details().toString());assertEquals("not_requested",qc(result).get("pca_status"));assertEquals(false,qc(result).get("pca_computed"));assertTrue(text(map(qc(result).get("method_report")).get("method")).contains("not requested"));assertFalse(Files.exists(c.outputDirectory().resolve("genotype-qc/pcs.tsv")));
    }
    @Test void findingContextUsesOnlySealedAggregateQcEvidence()throws Exception {
        var c=context("inventory",Map.of(),Map.of(),Map.of(),false,true);var result=new StudyPrefitQc().execute(c);assertEquals(State.COMPLETE,result.state());seal(c,result);Path primary=Files.createDirectories(c.project().resolve("primary/standard-results"));Files.writeString(primary.resolve("a.tsv"),"feature_id\tbeta\tse\tp_value\nassoc1\t1\t.1\t1e-12\n");StudyTestSources.seal(primary.getParent(),"validate_harmonize","primary","H");
        var inv=new Context(c.project(),Files.createDirectories(c.project().resolve("inventory")),Map.of("type","finding_inventory","hypothesis","H","datasets",List.of("a")),c.study(),Map.of(),Map.of("qc",c.outputDirectory(),"primary",primary.getParent()));new StudyFindingInventory().execute(inv);String body=Files.readString(inv.outputDirectory().resolve("finding-inventory.jsonl"));assertFalse(body.contains("PRIVATE_PARTICIPANT_SENTINEL"));assertFalse(body.contains("S00"));var f=map(SimpleJson.parse(body.strip()));var context=list(f.get("observations")).stream().map(StudyPublicEvidence::map).filter(o->"genotype_sample_qc".equals(o.get("kind"))).findFirst().orElseThrow();assertEquals(false,context.get("primary"));assertFalse(list(context.get("source_ids")).isEmpty());assertEquals(62,((Number)context.get("analysis_samples")).intValue());assertTrue(text(context.get("required_manuscript_caveat")).contains("do not assign ancestry labels"));
    }
}
