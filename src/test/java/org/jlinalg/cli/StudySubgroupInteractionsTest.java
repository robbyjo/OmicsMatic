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
import org.jlinalg.study.StudySubgroupInteractionPolicy;
import org.jlinalg.study.StudySuppliedResultPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudySubgroupInteractionsTest {
    @TempDir Path root;
    private Map<String,Object> group(){return Map.of("id","sex","column","group","levels",List.of("0","1"),"rationale","Prespecified synthetic contrast; not a biological sex claim");}
    private Context context(String name,boolean genotype,Map<String,Object> overrides,Map<String,Object> extraAssociation,Map<String,Object> metadata)throws Exception {
        Path project=Files.createDirectories(root.resolve(name));var data=DelimitedData.readResult(Path.of(getClass().getResource("/subgroup-interactions/data.tsv").toURI()));
        var pheno=new StringBuilder("id\tcontinuous\tbinary\tage\tgroup\toffset\tderived\tpatient_note\n");
        double[] sorted=data.rows().stream().mapToDouble(row->Double.parseDouble(row[data.column("continuous")])).sorted().toArray();double median=(sorted[47]+sorted[48])/2;
        var matrix=new StringBuilder("feature_id");var values=new StringBuilder("\nsignal");var constant=new StringBuilder("\nconstant");
        var vcf=new StringBuilder("##fileformat=VCFv4.2\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT");var common=new StringBuilder("\n1\t100\tcommon\tA\tG\t.\tPASS\t.\tGT");var rare=new StringBuilder("\n1\t200\trare-group\tA\tG\t.\tPASS\t.\tGT");
        for(int i=0;i<data.rows().size();i++) {
            String[] row=data.rows().get(i);String id=row[data.column("id")];matrix.append('\t').append(id);values.append('\t').append(row[data.column("feature")]);constant.append("\t1");vcf.append('\t').append(id);
            int dosage=i%3,rareDosage=i%2==0?dosage:i==1?1:0;common.append('\t').append(dosage==0?"0/0":dosage==1?"0/1":"1/1");rare.append('\t').append(rareDosage==0?"0/0":rareDosage==1?"0/1":"1/1");
            pheno.append(id);for(String field:List.of("continuous","binary","age","group","offset"))pheno.append('\t').append(row[data.column(field)]);pheno.append('\t').append(Double.parseDouble(row[data.column("continuous")])>median?1:0).append("\tPRIVATE_PARTICIPANT_SENTINEL\n");
        }
        Files.writeString(project.resolve("phenotype.tsv"),pheno);String matrixName=genotype?"matrix.vcf":"matrix.tsv";Files.writeString(project.resolve(matrixName),genotype?vcf.append(common).append(rare).append('\n'):matrix.append(values).append(constant).append('\n'));
        var policy=new LinkedHashMap<String,Object>(Map.of("enabled",true,"independent_rows",true,"groups",List.of(group())));policy.putAll(overrides);
        var association=new LinkedHashMap<String,Object>(Map.of("model","ols","offset_column","offset"));association.putAll(extraAssociation);
        var study=Map.<String,Object>of("resources",Map.of("network","offline","downloads","deny"),"datasets",List.of(Map.of("id","a","type",genotype?"gwas":"expression","stage","processed_matrix","files",Map.of("matrix",matrixName,"phenotype","phenotype.tsv"),"columns",Map.of("sample_id","id"),"metadata",metadata)),"hypotheses",List.of(Map.of("id","H","outcome",extraAssociation.containsKey("family")?"binary":"continuous","covariates",List.of("age","group"),"analysis",Map.of("association",association,"subgroup_interactions",policy))));
        var qc=new Context(project,Files.createDirectories(project.resolve("qc")),Map.of("id","qc","type","participant_qc","dataset_id","a","datasets",List.of("a"),"hypothesis","H"),study,Map.of(),Map.of());var result=new StudyPrefitQc().execute(qc);assertEquals(State.COMPLETE,result.state(),result.details().toString());seal(qc,result,true);
        return new Context(project,Files.createDirectories(project.resolve("interactions")),Map.of("id","interactions","type","subgroup_interactions","datasets",List.of("a"),"hypothesis","H"),study,Map.of(),Map.of("qc",qc.outputDirectory()));
    }
    private void seal(Context c,Result result,boolean qc)throws Exception {
        var hashes=new LinkedHashMap<String,Object>();try(var paths=Files.walk(c.outputDirectory())){for(Path path:paths.filter(Files::isRegularFile).toList())hashes.put(c.outputDirectory().relativize(path).toString().replace('\\','/'),FollowupSupport.hash(path));}
        var input=new LinkedHashMap<String,Object>();if(qc){var dataset=map(list(c.study().get("datasets")).get(0));for(var entry:map(dataset.get("files")).entrySet())input.put("dataset:a:"+entry.getKey(),FollowupSupport.hash(c.project().resolve(text(entry.getValue()))));}
        write(c.outputDirectory().resolve("manifest.yaml"),Map.of("operation_id",c.operation().get("id"),"operation_type",c.operation().get("type"),"hypothesis","H","state",result.state().value(),"details",result.details(),"output_sha256",hashes,"input_sha256",input));
    }
    private static Map<String,Object> policy(Context c){return StudySubgroupInteractionPolicy.resolve(map(map(map(list(c.study().get("hypotheses")).get(0)).get("analysis")).get("subgroup_interactions")));}
    private static String[] feature(DelimitedData table,String id){return table.rows().stream().filter(row->row[table.column("feature_id")].equals(id)).findFirst().orElseThrow();}

    @Test void nativeInteractionUsesTheFullPrespecifiedFamilyAndKeepsInputsImmutable()throws Exception {
        var c=context("native",false,Map.of(),Map.of(),Map.of());String original=FollowupSupport.hash(c.project().resolve("phenotype.tsv"));var result=new StudySubgroupInteractions().execute(c);assertEquals(State.COMPLETE,result.state(),result.details().toString());assertEquals(2,result.details().get("family_size"));assertEquals(1,result.details().get("completed_unestimable_tests"));
        var table=DelimitedData.readResult(c.outputDirectory().resolve("subgroup-interactions.tsv"));String[] signal=feature(table,"signal");assertEquals("complete",signal[table.column("status")]);assertEquals("96",signal[table.column("n")]);assertEquals("48",signal[table.column("n_reference")]);assertEquals("48",signal[table.column("n_comparison")]);
        var expected=DelimitedData.readResult(Path.of(getClass().getResource("/subgroup-interactions/expected.tsv").toURI()));String[] reference=expected.rows().stream().filter(row->row[0].equals("ols-hc3")).findFirst().orElseThrow();assertEquals(Double.parseDouble(reference[expected.column("beta")]),Double.parseDouble(signal[table.column("beta")]),1e-7);assertEquals(3*Double.parseDouble(signal[table.column("p_value")]),Double.parseDouble(signal[table.column("adjusted_p")]),1e-12);assertEquals("true",signal[table.column("sensitivity_candidate")]);
        assertEquals("not_estimable",feature(table,"constant")[table.column("status")]);assertEquals(original,FollowupSupport.hash(c.project().resolve("phenotype.tsv")));assertFalse(Files.readString(c.outputDirectory().resolve("subgroup-interactions.tsv")).contains("PRIVATE_PARTICIPANT_SENTINEL"));
    }
    @Test void bothBinaryRoutesRespectTheDeclaredCaseCodingAndOffset()throws Exception {
        for(String method:List.of("logit-lrt","firth-lrt")){
            var c=context(method,false,Map.of("method",method),Map.of("model","glm","family","binomial","case_value",1,"control_value",0),Map.of());var result=new StudySubgroupInteractions().execute(c);assertEquals(State.COMPLETE,result.state(),result.details().toString());var table=DelimitedData.readResult(c.outputDirectory().resolve("subgroup-interactions.tsv"));var row=feature(table,"signal");assertEquals("22",row[table.column("n_cases_reference")]);assertEquals("29",row[table.column("n_cases_comparison")]);assertEquals("complete",row[table.column("status")]);
            var expected=DelimitedData.readResult(Path.of(getClass().getResource("/subgroup-interactions/expected.tsv").toURI()));var ref=expected.rows().stream().filter(r->r[0].equals(method)).findFirst().orElseThrow();assertEquals(Double.parseDouble(ref[expected.column("beta")]),Double.parseDouble(row[table.column("beta")]),2e-5);assertEquals(Double.parseDouble(ref[expected.column("p_value")]),Double.parseDouble(row[table.column("p_value")]),2e-5);
        }
    }
    @Test void groupMacIsRecomputedAndPooledMacDoesNotRescueAnUnsupportedStratum()throws Exception {
        var c=context("genotype",true,Map.of(),Map.of(),Map.of());var result=new StudySubgroupInteractions().execute(c);assertEquals(State.COMPLETE,result.state(),result.details().toString());var table=DelimitedData.readResult(c.outputDirectory().resolve("subgroup-interactions.tsv"));var common=feature(table,"common");var rare=feature(table,"rare-group");assertEquals("complete",common[table.column("status")]);assertEquals("1.0",rare[table.column("mac_comparison")]);assertEquals("false",rare[table.column("in_testing_family")]);assertEquals("NA",rare[table.column("p_value")]);assertTrue(rare[table.column("reason")].contains("group-specific MAC"));assertEquals(1,result.details().get("family_size"));assertTrue(Files.readString(c.project().resolve("matrix.vcf")).contains("rare-group"));
    }
    @Test void numericalBudgetCannotTurnAnUnprocessedFamilyIntoSignificance()throws Exception {
        var c=context("budget",false,Map.of("maximum_work",1),Map.of(),Map.of());var result=new StudySubgroupInteractions().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());assertEquals(2,result.details().get("unresolved_tests"));var table=DelimitedData.readResult(c.outputDirectory().resolve("subgroup-interactions.tsv"));assertTrue(table.rows().stream().allMatch(row->row[table.column("adjusted_p")].equals("NA")&&row[table.column("sensitivity_candidate")].equals("false")));assertEquals(0L,result.details().get("new_model_attempts"));
    }
    @Test void staleParticipantQcCannotBeReusedWithChangedMatrixBytes()throws Exception {
        var c=context("stale",false,Map.of(),Map.of(),Map.of());Files.writeString(c.project().resolve("matrix.tsv"),Files.readString(c.project().resolve("matrix.tsv")).replace("\t1\t1\t1","\t2\t1\t1"));var result=new StudySubgroupInteractions().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());assertEquals(0L,result.details().get("new_model_attempts"));assertTrue(result.details().toString().contains("source version"));
    }
    @Test void relatedRowsAndUnsupportedMainModelsRequireAnExplicitAppropriateAdapter()throws Exception {
        var dependent=context("related",false,Map.of(),Map.of(),Map.of("related_participants",true));var a=new StudySubgroupInteractions().execute(dependent);assertEquals(State.WAITING_FOR_INPUT,a.state());assertEquals(0L,a.details().get("new_model_attempts"));
        var unsupported=context("mixed",false,Map.of(),Map.of("model","lmm"),Map.of());var b=new StudySubgroupInteractions().execute(unsupported);assertEquals(State.WAITING_FOR_INPUT,b.state());assertEquals(0L,b.details().get("new_model_attempts"));
    }
    @Test void outcomeDerivedGroupsDoNotProduceInteractionOrWelchEvidence()throws Exception {
        var g=Map.<String,Object>of("id","derived","column","derived","levels",List.of("0","1"),"derived_from",List.of("continuous"),"rationale","Synthetic circular-control group");var c=context("circular",false,Map.of("groups",List.of(g)),Map.of(),Map.of());var result=new StudySubgroupInteractions().execute(c);assertEquals(State.COMPLETE,result.state());assertEquals(0,result.details().get("family_size"));var table=DelimitedData.readResult(c.outputDirectory().resolve("subgroup-interactions.tsv"));assertTrue(table.rows().stream().allMatch(row->row[table.column("p_value")].equals("NA")&&row[table.column("in_testing_family")].equals("false")));
        var qc=StudyYaml.loadArtifact(c.dependencyDirectories().get("qc").resolve("prefit-qc.yaml"));var diagnostics=list(qc.get("subgroup_comparisons")).stream().map(StudyPublicEvidence::map).filter(row->row.get("variable").equals("continuous")).toList();assertFalse(diagnostics.isEmpty());assertEquals("NA",diagnostics.get(0).get("p_value"));assertEquals(false,diagnostics.get(0).get("effect_modification_established"));
    }
    @Test void anUnknownFeatureOrGroupPrerequisiteHoldsTheWholeAdjustmentFamily()throws Exception {
        var c=context("missing-feature",false,Map.of("features",List.of("signal","absent")),Map.of(),Map.of());var result=new StudySubgroupInteractions().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());var table=DelimitedData.readResult(c.outputDirectory().resolve("subgroup-interactions.tsv"));assertEquals("complete",feature(table,"signal")[table.column("status")]);assertEquals("NA",feature(table,"signal")[table.column("adjusted_p")]);assertEquals("waiting_for_input",feature(table,"absent")[table.column("status")]);
    }
    @Test void aCompleteInteractionCanReachImpactReviewWithoutChangingPrimarySignificance()throws Exception {
        var c=context("inventory",false,Map.of(),Map.of(),Map.of());var result=new StudySubgroupInteractions().execute(c);seal(c,result,false);Path primary=Files.createDirectories(c.project().resolve("primary/standard-results"));Files.writeString(primary.resolve("a.tsv"),"feature_id\tbeta\tse\tp_value\nsignal\t.01\t.3\t.9\nconstant\t.01\t.3\t.8\n");StudyTestSources.seal(primary.getParent(),"validate_harmonize","primary","H");
        var inventory=new Context(c.project(),Files.createDirectories(c.project().resolve("inventory")),Map.of("type","finding_inventory","hypothesis","H","datasets",List.of("a")),c.study(),Map.of(),Map.of("primary",primary.getParent(),"interactions",c.outputDirectory()));new StudyFindingInventory().execute(inventory);
        var dossiers=new ArrayList<Map<String,Object>>();for(String line:Files.readAllLines(inventory.outputDirectory().resolve("finding-inventory.jsonl")))dossiers.add(map(SimpleJson.parse(line)));var signal=dossiers.stream().filter(f->f.get("feature_id").equals("signal")).findFirst().orElseThrow();assertEquals(true,signal.get("candidate"));assertEquals(false,signal.get("significant"));assertEquals("separate_registered_interaction_family_eligible",signal.get("candidate_reason"));assertTrue(list(signal.get("observations")).stream().map(StudyPublicEvidence::map).filter(o->"subgroup_interaction".equals(o.get("kind"))).allMatch(o->Boolean.FALSE.equals(o.get("primary"))&&!list(o.get("source_ids")).isEmpty()));assertFalse(Files.readString(inventory.outputDirectory().resolve("finding-inventory.jsonl")).contains("PRIVATE_PARTICIPANT_SENTINEL"));
    }
    private Map<String,Object> suppliedDefinition(String file){return Map.of("id","done","kind","subgroup_interactions","files",Map.of("results",file),"scope",Map.of("phenotype","BMI"),"method",Map.of("name","Externally completed registered interaction"),"family",Map.of("id","source-interactions","coverage","complete","tested_count",2,"adjustment","BY","adjusted_p_from_full_family",true));}
    @Test void nominalInteractionEvidenceCarriesItsOwnCaveatWithoutInventingAnMrAnalysis()throws Exception {
        var c=context("nominal",false,Map.of("adjustment","NONE"),Map.of(),Map.of());var result=new StudySubgroupInteractions().execute(c);seal(c,result,false);
        var inventory=new Context(c.project(),Files.createDirectories(c.project().resolve("inventory")),Map.of("type","finding_inventory","hypothesis","H"),c.study(),Map.of(),Map.of("interactions",c.outputDirectory()));new StudyFindingInventory().execute(inventory);
        for(String line:Files.readAllLines(inventory.outputDirectory().resolve("finding-inventory.jsonl"))) {
            var f=map(SimpleJson.parse(line));assertTrue(list(f.get("caveats")).stream().map(StudyPublicEvidence::map).noneMatch(v->"mr_multiplicity".equals(v.get("kind"))));
            assertTrue(list(f.get("observations")).stream().map(StudyPublicEvidence::map).allMatch(o->text(o.get("required_manuscript_caveat")).contains("not FDR control")));
        }
    }
    private Context importContext(String status)throws Exception {
        Path project=Files.createDirectories(root.resolve("import-"+status));Files.writeString(project.resolve("results.tsv"),"feature_id\tdataset_id\tgroup_id\treference_level\tcomparison_level\tbeta\tse\tp_value\tadjusted_p\tpatient_note\nsignal\ta\tsex\tF\tM\t1\t.1\t.001\t.003\tPRIVATE_PARTICIPANT_SENTINEL\nsignal\ta\tage\tyoung\told\t.1\t.2\t.6\t.9\tPRIVATE_PARTICIPANT_SENTINEL\n");var definition=new LinkedHashMap<>(suppliedDefinition("results.tsv"));definition.put("reported_status",status);var analysis=Map.<String,Object>of("supplied_results",List.of(definition));StudySuppliedResultPolicy.resolve(analysis);var study=Map.<String,Object>of("datasets",List.of(),"hypotheses",List.of(Map.of("id","H","outcome","BMI","analysis",analysis)));return new Context(project,Files.createDirectories(project.resolve("import")),Map.of("id","done","type","subgroup_interactions","hypothesis","H","supplied_result_id","done"),study,Map.of(),Map.of());
    }
    @Test void suppliedContrastsRetainTheirOwnJointFamilyWithoutRefitting()throws Exception {
        var c=importContext("complete");var result=new StudySuppliedResults().execute(c);assertEquals(State.COMPLETE,result.state(),result.details().toString());var table=DelimitedData.readResult(c.outputDirectory().resolve("subgroup-interactions.tsv"));assertEquals(2,table.rows().size());assertEquals(".003",table.rows().get(0)[table.column("adjusted_p")]);assertFalse(Files.readString(c.outputDirectory().resolve("subgroup-interactions.tsv")).contains("PRIVATE_PARTICIPANT_SENTINEL"));seal(c,result,false);
        var inventory=new Context(c.project(),Files.createDirectories(c.project().resolve("inventory")),Map.of("type","finding_inventory","hypothesis","H"),c.study(),Map.of(),Map.of("done",c.outputDirectory()));var inv=new StudyFindingInventory().execute(inventory);assertEquals(1L,inv.details().get("candidates"));var dossier=map(SimpleJson.parse(Files.readString(inventory.outputDirectory().resolve("finding-inventory.jsonl")).strip()));assertEquals(false,dossier.get("significant"));assertTrue(list(dossier.get("observations")).stream().map(StudyPublicEvidence::map).allMatch(o->!Boolean.TRUE.equals(o.get("primary"))));
    }
    @Test void partialSuppliedInteractionFamiliesRemainContextWithoutDiscoveryClaims()throws Exception {
        var c=importContext("partial");var result=new StudySuppliedResults().execute(c);assertEquals(State.COMPLETE,result.state());assertEquals(false,result.details().get("primary_selection_eligible"));assertEquals("supplied-results-limited.tsv",result.details().get("canonical_results"));seal(c,result,false);var inventory=new Context(c.project(),Files.createDirectories(c.project().resolve("inventory")),Map.of("type","finding_inventory","hypothesis","H"),c.study(),Map.of(),Map.of("done",c.outputDirectory()));var inv=new StudyFindingInventory().execute(inventory);assertEquals(0L,inv.details().get("candidates"));
    }
    @Test void suppliedGroupOrientationCannotChangeSilentlyAcrossFeatures()throws Exception {
        var c=importContext("complete");String source=Files.readString(c.project().resolve("results.tsv"));source=source.replace("signal\ta\tage\tyoung\told","another\ta\tsex\tM\tF");Files.writeString(c.project().resolve("results.tsv"),source);var failure=assertThrows(java.io.IOException.class,()->new StudySuppliedResults().execute(c));assertTrue(failure.getMessage().contains("orientation"));
    }
    @Test void policyRejectsUnregisteredContrastsOrSilentOmissionOfNuisanceInteractions() {
        assertThrows(IllegalArgumentException.class,()->StudySubgroupInteractionPolicy.resolve(Map.of("enabled",true)));
        assertThrows(IllegalArgumentException.class,()->StudySubgroupInteractionPolicy.resolve(Map.of("nuisance_interactions","none")));
        var g=new LinkedHashMap<>(group());g.put("levels",List.of("a","b","c"));assertThrows(IllegalArgumentException.class,()->StudySubgroupInteractionPolicy.resolve(Map.of("groups",List.of(g))));
        assertEquals("all_two_way",policyUnchecked().get("nuisance_interactions"));assertEquals("BY",policyUnchecked().get("adjustment"));
    }
    private Map<String,Object> policyUnchecked(){return StudySubgroupInteractionPolicy.resolve(Map.of());}
}
