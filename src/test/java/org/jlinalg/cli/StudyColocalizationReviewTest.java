/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyColocalizationPolicy;
import org.jlinalg.study.StudyNeed;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyColocalizationReviewTest {
    @TempDir Path root;
    private static final String HEADER="variant_id\tchromosome\tposition\teffect_allele\tother_allele\tbeta\tse\tp_value\n";
    private Context context(Map<String,Object> policy)throws Exception {
        return new Context(root,Files.createDirectories(root.resolve("out")),Map.of("type","colocalization","hypothesis","H","datasets",List.of("d")),Map.of("resources",Map.of("directory","fixture-resources","network","offline","downloads","deny"),"hypotheses",List.of(Map.of("id","H","analysis",Map.of("colocalization",policy)))),Map.of(),Map.of());
    }
    private Path source()throws Exception {Path p=root.resolve("regional.tsv");Files.writeString(p,HEADER+"rs1\t1\t100\tA\tG\t1\t.1\t1e-10\nrs2\t1\t200\tA\tG\t1\t.1\t1e-10\nrs3\t1\t300\tA\tG\t1\t.1\t1e-10\nrs4\t1\t400\tA\tG\t2\t.1\t1e-30\n");return p;}
    private static StudyGeneticEvidence.Aligned aligned(String id,long pos){var v=new StudyGeneticEvidence.Variant(id,"1",pos,"A","G",1,.1,1e-10);return new StudyGeneticEvidence.Aligned(v,v,1,1);}
    @Test void preRestrictionCountsLeadLossAndPositionGapsUseTheCompleteObservedSource()throws Exception {
        Path p=source();var declaration=Map.<String,Object>of("complete_region",false,"export_p_threshold",1e-7,"regional_variant_count",8);
        var x=StudyRegionalCoverage.scan(p,root.resolve("x.tsv"),"1",1,500,null,java.util.Set.of("rs1","rs2","rs3"),3,10,declaration);
        var y=StudyRegionalCoverage.scan(p,root.resolve("y.tsv"),"1",1,500,null,java.util.Set.of("rs1","rs2","rs3"),3,10,declaration);
        assertEquals(4,y.observed());assertEquals(3,y.retained().size());assertEquals("rs4",y.lead());assertEquals(.5,y.summary().get("observed_fraction_of_declared_universe"));
        var summary=StudyRegionalCoverage.write(root,x,y,List.of(aligned("rs1",100),aligned("rs2",200)),List.of(Map.of("reason","absent_from_ld_alleles")),1,500,5);
        assertEquals(.5,summary.get("outcome_observed_match_fraction"));assertEquals(2.0/3,summary.get("outcome_candidate_match_fraction"));assertEquals(false,summary.get("outcome_lead_aligned"));
        assertEquals(300L,StudyPublicEvidence.map(summary.get("aligned_position_gaps")).get("right_edge_bp"));assertEquals(100L,StudyPublicEvidence.map(summary.get("aligned_position_gaps")).get("largest_between_observed_positions_bp"));
        var inventory=DelimitedData.readResult(root.resolve("y.tsv"));assertEquals(4,inventory.rows().size());assertEquals("false",inventory.rows().get(3)[inventory.column("retained_for_alignment")]);
        var bins=DelimitedData.readResult(root.resolve("regional-coverage-bins.tsv"));assertEquals(4,bins.rows().stream().mapToInt(row->Integer.parseInt(row[bins.column("outcome_observed_positions")])).sum());
    }
    @Test void coverageCannotCertifyConflictingDeclarationsOrSilentlyTruncateItsBudget()throws Exception {
        Path p=source();assertThrows(java.io.IOException.class,()->StudyRegionalCoverage.scan(p,root.resolve("conflict.tsv"),"1",1,500,null,null,10,10,Map.of("complete_region",true,"export_p_threshold",1e-7)));
        assertThrows(java.io.IOException.class,()->StudyRegionalCoverage.scan(p,root.resolve("bounded.tsv"),"1",1,500,null,java.util.Set.of("rs1"),10,3,Map.of()));
        Files.writeString(p,Files.readString(p)+"chr1:400:A:G:rs4\t1\t400\tA\tG\t2\t.1\t1e-30\n");
        assertThrows(java.io.IOException.class,()->StudyRegionalCoverage.scan(p,root.resolve("duplicate.tsv"),"1",1,500,null,java.util.Set.of("rs1"),10,10,Map.of()));
    }
    @Test void strictPolicyRejectsImpossibleOrAmbiguousPriorGrids() {
        assertThrows(IllegalArgumentException.class,()->StudyColocalizationPolicy.resolve(Map.of("shared_prior",.01)));
        assertThrows(IllegalArgumentException.class,()->StudyColocalizationPolicy.resolve(Map.of("shared_prior_sensitivity",List.of(1e-6,1e-6))));
        assertThrows(IllegalArgumentException.class,()->StudyColocalizationPolicy.resolve(Map.of("shared_prior_sensitivity",List.of(5e-6))));
        assertThrows(IllegalArgumentException.class,()->StudyColocalizationPolicy.resolve(Map.of("maximum_regional_variants",1)));
        assertThrows(IllegalArgumentException.class,()->StudyColocalizationPolicy.resolve(Map.of("credible_set_coverage",1)));
        assertThrows(IllegalArgumentException.class,()->StudyColocalizationPolicy.resolve(Map.of("choose_best_prior",true)));
    }
    private void effects(Path directory,int count)throws Exception {
        var data=new StringBuilder("effect_index\tvariant_id\tlog_bayes_factor\n");for(int e=0;e<count;e++)for(int v=0;v<3;v++)data.append(e).append("\trs").append(v+1).append('\t').append(v==0?10+e:v==1?2:0).append('\n');
        Files.writeString(directory.resolve("exposure.effects.tsv"),data);Files.writeString(directory.resolve("outcome.effects.tsv"),data);
    }
    private static Map<String,Object> record(){var r=new LinkedHashMap<String,Object>();r.put("pair_id","pair");r.put("feature_id","probe1");r.put("aligned_variants",3);r.put("evidence_grade","exploratory_partial_region_major_limitation");r.put("required_manuscript_caveat",StudyColocalizationReview.PARTIAL);r.put("effective_parameters",Map.of("exposure_fine_mapping",Map.of("converged",true,"fallback_used",false),"outcome_fine_mapping",Map.of("converged",true,"fallback_used",false)));r.put("regional_coverage",Map.of("exposure",Map.of("observed_regional_variants",3),"outcome",Map.of("observed_regional_variants",4),"exposure_observed_match_fraction",1.,"outcome_observed_match_fraction",.75,"outcome_candidate_match_fraction",1.,"exposure_lead_aligned",true,"outcome_lead_aligned",false));return r;}
    @Test void fullSignalGridMatchesIndependentEnumerationAndPreservesTheRegisteredPrior()throws Exception {
        var c=context(Map.of("shared_prior_sensitivity",List.of(1e-8,1e-6,1e-5),"posterior_threshold",.9));Path directory=Files.createDirectories(c.outputDirectory().resolve("pair"));effects(directory,5);var record=record();var pair=Map.<String,Object>of("id","pair","feature_id","probe1","partial_colocalization",true);
        StudyColocalizationReview.analyze(c,pair,directory,record);var table=DelimitedData.readResult(directory.resolve("colocalization-results.tsv"));assertEquals(25,table.rows().size());assertEquals(20,StudyPublicEvidence.list(record.get("colocalization")).size());
        var grid=DelimitedData.readResult(directory.resolve("colocalization-prior-grid.tsv"));assertEquals(100,grid.rows().size());
        for(String[] row:grid.rows()){
            int a=Integer.parseInt(row[grid.column("trait1_effect_index")]),b=Integer.parseInt(row[grid.column("trait2_effect_index")]);double p12=Double.parseDouble(row[grid.column("shared_prior")]);double[] first={Math.exp(10+a),Math.exp(2),1},second={Math.exp(10+b),Math.exp(2),1};double sum1=0,sum2=0,shared=0,distinct=0;
            for(int i=0;i<3;i++){sum1+=first[i];sum2+=second[i];shared+=first[i]*second[i];for(int j=0;j<3;j++)if(i!=j)distinct+=first[i]*second[j];}
            double[] h={1,1e-4*sum1,1e-4*sum2,1e-8*distinct,p12*shared};double denominator=java.util.Arrays.stream(h).sum();for(int i=0;i<5;i++)assertEquals(h[i]/denominator,Double.parseDouble(row[grid.column("pp_h"+i)]),1e-12);
        }
        String[] first=table.rows().get(0);assertEquals("crosses_registered_threshold",first[table.column("prior_stability")]);assertEquals(5e-6,Double.parseDouble(first[table.column("shared_prior")]));assertEquals("0.75",first[table.column("outcome_variant_match_fraction")]);assertEquals("false",first[table.column("outcome_lead_aligned")]);assertTrue(Files.isRegularFile(directory.resolve("colocalization-prior-sensitivity-3.tsv")));
        var details=new LinkedHashMap<String,Object>();record.put("status","complete");var needs=new ArrayList<StudyNeed>();StudyColocalizationReview.finish(c,List.of(record),Map.of("probe1",new StudyBioinformatics.Feature("probe1","GENE","ENSG1","1","100","300","GRCh38","located",.001,.001)),details,needs);assertTrue(needs.isEmpty());assertEquals(25,DelimitedData.readResult(c.outputDirectory().resolve("colocalization-results.tsv")).rows().size());
    }
    private void seal(Path directory,String type,Map<String,Object> details)throws Exception {var hashes=new LinkedHashMap<String,Object>();try(var paths=Files.walk(directory)){for(Path p:paths.filter(Files::isRegularFile).filter(p->!p.getFileName().toString().equals("manifest.yaml")).toList())hashes.put(directory.relativize(p).toString().replace('\\','/'),FollowupSupport.hash(p));}StudyPublicEvidence.write(directory.resolve("manifest.yaml"),Map.of("operation_id",type,"operation_type",type,"hypothesis","H","state","complete","details",details,"output_sha256",hashes));}
    @Test void dossiersRetainEverySignalBeyondThePreviewCapWithoutCreatingAssociationSignificance()throws Exception {
        var c=context(Map.of());Path directory=Files.createDirectories(c.outputDirectory().resolve("pair"));effects(directory,5);var r=record();StudyColocalizationReview.analyze(c,Map.of("id","pair","feature_id","probe1"),directory,r);r.put("status","complete");var details=new LinkedHashMap<String,Object>();StudyColocalizationReview.finish(c,List.of(r),Map.of(),details,new ArrayList<>());seal(c.outputDirectory(),"colocalization",details);
        Path upstream=Files.createDirectories(root.resolve("upstream/standard-results"));Files.writeString(upstream.resolve("d.tsv"),"feature_id\tbeta\tse\tp_value\nprobe1\t1\t.1\t.001\n");seal(upstream.getParent(),"association",Map.of());
        var inventory=new Context(root,Files.createDirectories(root.resolve("inventory")),Map.of("type","finding_inventory","hypothesis","H"),c.study(),Map.of(),Map.of("association",upstream.getParent(),"colocalization",c.outputDirectory()));var result=new StudyFindingInventory().execute(inventory);assertEquals(1L,result.details().get("candidates"));
        var dossier=StudyPublicEvidence.map(SimpleJson.parse(Files.readString(inventory.outputDirectory().resolve("finding-inventory.jsonl")).strip()));var signals=StudyPublicEvidence.list(dossier.get("observations")).stream().map(StudyPublicEvidence::map).filter(v->"colocalization_signal_pair".equals(v.get("kind"))).toList();assertEquals(25,signals.size());assertTrue(signals.stream().allMatch(v->Boolean.FALSE.equals(v.get("primary"))&&v.get("required_manuscript_caveat").toString().contains("not evidence against")&&!StudyPublicEvidence.list(v.get("source_ids")).isEmpty()));
    }
    @Test void declaredByFamilyControlsFollowupAndManuscriptEligibilityConsistently()throws Exception {
        var c=context(Map.of());Path upstream=Files.createDirectories(root.resolve("by/standard-results"));Files.writeString(upstream.resolve("d.tsv"),"feature_id\tbeta\tse\tp_value\nprobe1\t1\t.1\t.02\nother\t1\t.1\t.8\n");Files.writeString(upstream.getParent().resolve("feature-annotation.tsv"),"feature_id\tgene_symbol\tgene_id\tchromosome\tstart\tend\tgenome_build\tmapping_status\nprobe1\tGENE\tENSG1\t1\t100\t300\tGRCh38\tlocated\n");seal(upstream.getParent(),"association",Map.of());
        var study=Map.<String,Object>of("hypotheses",List.of(Map.of("id","H","analysis",Map.of("multiple_testing",Map.of("method","BY","alpha",.05)))));var by=new Context(root,c.outputDirectory(),c.operation(),study,Map.of(),Map.of("association",upstream.getParent()));assertTrue(StudyBioinformatics.colocalizationFeatures(by).isEmpty());
        var inv=new Context(root,Files.createDirectories(root.resolve("by-inventory")),Map.of("type","finding_inventory","hypothesis","H"),study,Map.of(),by.dependencyDirectories());assertEquals(0L,new StudyFindingInventory().execute(inv).details().get("candidates"));
    }
}
