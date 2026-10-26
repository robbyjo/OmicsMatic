/* SPDX-License-Identifier: GPL-3.0-or-later */
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
import org.jlinalg.study.StudySurvivalPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudySurvivalAssociationTest {
    @TempDir Path root;
    private Context fixture(boolean genotype,boolean adjust,Map<String,Object> overrides)throws Exception{
        Path project=Files.createTempDirectory(root,"survival-");var phenotype=new StringBuilder("id\ttime\tevent\tentry\tage\tsex\n");var x=new double[100];var z=new double[100];
        for(int i=0;i<100;i++){
            x[i]=genotype?i%3:Math.sin(i*.53);z[i]=Math.cos(i*.31);double age=(i*7%31)/31.,entry=i%3*.05;
            double t=-Math.log(((i*37%101)+.5)/101.)/Math.exp(.3*x[i]+.2*age),censor=.8+(i*11%19)/19.;boolean event=t<=censor;
            phenotype.append("S"+i+"\t"+(entry+Math.min(t,censor))+"\t"+(event?1:0)+"\t"+entry+"\t"+age+"\t"+(i%2==0?"A":"B")+"\n");
        }
        Files.writeString(project.resolve("phenotype.tsv"),phenotype);
        if(genotype){var vcf=new StringBuilder("##fileformat=VCFv4.2\n##contig=<ID=1>\n##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT");for(int i=0;i<100;i++)vcf.append("\tS"+i);vcf.append('\n');
            for(int j=0;j<2;j++){vcf.append("1\t"+(100+j*10)+"\t"+(j==0?"common":"rare")+"\tA\tG\t.\tPASS\t.\tGT");for(int i=0;i<100;i++)vcf.append('\t').append(j==0?(i%3==0?"0/0":i%3==1?"0/1":"1/1"):i<5?"0/1":"0/0");vcf.append('\n');}Files.writeString(project.resolve("matrix.vcf"),vcf);
        }else{var matrix=new StringBuilder("feature_id");for(int i=0;i<100;i++)matrix.append("\tS"+i);matrix.append('\n');for(int j=0;j<3;j++){matrix.append("g"+j);for(int i=0;i<100;i++)matrix.append('\t').append(j==0?x[i]:j==1?z[i]:1);matrix.append('\n');}Files.writeString(project.resolve("matrix.tsv"),matrix);}
        var p=new LinkedHashMap<String,Object>(Map.of("time_column","time","event_column","event","entry_column","entry","strata_column","sex","time_units","years","event_definition","Synthetic endpoint","censoring_definition","Synthetic administrative censoring","rationale","Synthetic software fixture only","independent_censoring_rationale","Fixed generated data; no scientific assertion","proportional_hazards_rationale","Software comparison, not assumption verification"));p.putAll(overrides);
        var association=Map.<String,Object>of("model","cox","chromosome_scope","human_autosomes","survival",p);
        var dataset=Map.<String,Object>of("id","a","cohort","A","type",genotype?"gwas":"expression","stage","processed_matrix","genome_build","GRCh38","files",Map.of("matrix",genotype?"matrix.vcf":"matrix.tsv","phenotype","phenotype.tsv"));
        var h=Map.<String,Object>of("id","H","description","Synthetic survival association","outcome","incident_endpoint","covariates",adjust?List.of("age"):List.of(),"inputs",Map.of("discovery",List.of("a")),"analysis",Map.of("mode","manual","goals",List.of("association"),"association",association));
        var study=Map.<String,Object>of("schema_version",1,"project",Map.of("id","cox-fixture","title","Synthetic Cox","description","No biological claim"),"resources",Map.of("network","offline","downloads","deny"),"datasets",List.of(dataset),"hypotheses",List.of(h));
        Path prefit=Files.createDirectory(project.resolve("prefit"));Files.copy(project.resolve("phenotype.tsv"),prefit.resolve("analysis-phenotype.tsv"));seal(prefit);
        return new Context(project,Files.createDirectory(project.resolve("output")),Map.of("id","association","type","association","hypothesis","H","dataset_id","a","datasets",List.of("a")),study,Map.of(),Map.of("prefit",prefit));
    }
    private static void seal(Path prefit)throws Exception{write(prefit.resolve("manifest.yaml"),Map.of("operation_type","participant_qc","operation_id","prefit","hypothesis","H","state","complete","output_sha256",Map.of("analysis-phenotype.tsv",FollowupSupport.hash(prefit.resolve("analysis-phenotype.tsv")))));}
    private static Result execute(Context c)throws Exception{return new StudyCliExecutor().execute(c);}
    @Test void molecularCoxProducesFullFitsSeparatePHDiagnosticsAndTypedLogHazardMetadata()throws Exception{
        var c=fixture(false,true,Map.of());var r=execute(c);assertEquals(State.COMPLETE,r.state(),r.details().toString());var t=DelimitedData.readResult(c.outputDirectory().resolve("standard-results/a.tsv"));assertEquals(2,t.rows().size());
        for(String[] row:t.rows()){assertEquals("log_hazard_ratio",row[t.column("effect_scale")]);assertEquals("cox_partial_likelihood_ratio",row[t.column("test_method")]);assertEquals("100",row[t.column("n")]);assertTrue(Double.parseDouble(row[t.column("n_events")])>0);}
        var ph=DelimitedData.readResult(c.outputDirectory().resolve("survival-diagnostics.tsv"));assertEquals(2,ph.rows().size());assertEquals(false,map(r.details().get("ph_diagnostics")).get("primary_test_changed"));assertTrue(Files.readString(c.outputDirectory().resolve("association.tsv")).contains("NO_WITHIN_STRATUM_VARIATION"));
    }
    @Test void anUnadjustedCoxModelDoesNotRequireAnInventedNuisanceCovariate()throws Exception{
        var c=fixture(false,false,Map.of());var r=execute(c);assertEquals(State.COMPLETE,r.state(),r.details().toString());assertTrue(DelimitedData.readResult(c.outputDirectory().resolve("cox-null-coefficients.tsv")).rows().isEmpty());
    }
    @Test void genotypeCoxUsesIndependentMacTwentyBeforeMeanImputation()throws Exception{
        var c=fixture(true,true,Map.of());var r=execute(c);assertEquals(State.COMPLETE,r.state(),r.details().toString());var t=DelimitedData.readResult(c.outputDirectory().resolve("standard-results/a.tsv"));assertEquals(1,t.rows().size());assertEquals("common",t.rows().get(0)[0]);assertEquals("G",t.rows().get(0)[t.column("effect_allele")]);assertTrue(Files.readString(c.outputDirectory().resolve("association.tsv")).contains("BELOW_MINIMUM_MAC"));
    }
    @Test void registeredWaldAndBreslowControlsAreUsedAndNotSubstituted()throws Exception{
        var c=fixture(false,true,Map.of("test","wald","ties","breslow"));var r=execute(c);assertEquals(State.COMPLETE,r.state(),r.details().toString());var t=DelimitedData.readResult(c.outputDirectory().resolve("standard-results/a.tsv"));for(String[] row:t.rows())assertEquals("cox_wald_normal",row[t.column("test_method")]);
    }
    @Test void workAndSourceBoundsHoldOfficialSelectionRatherThanTestingAPreferredPrefix()throws Exception{
        for(var p:List.of(Map.<String,Object>of("maximum_features",1),Map.<String,Object>of("maximum_fit_work",1))){var c=fixture(false,true,p);var r=execute(c);assertEquals(State.WAITING_FOR_INPUT,r.state());assertEquals(false,r.details().get("primary_selection_eligible"));}
    }
    @Test void anUnresolvedFullFitWithholdsOfficialSelectionWhileRetainingRawAndStandardFiles()throws Exception{
        var c=fixture(false,false,Map.of("maximum_iterations",1,"score_tolerance",1e-12));var r=execute(c);assertEquals(State.WAITING_FOR_INPUT,r.state(),r.details().toString());assertEquals(false,r.details().get("primary_selection_eligible"));assertTrue(Files.isRegularFile(c.outputDirectory().resolve("association.tsv")));assertTrue(Files.isRegularFile(c.outputDirectory().resolve("standard-results/a.tsv")));
    }
    @Test void unsupportedRelatedDesignIsNotSilentlyFittedAsIndependent()throws Exception{
        var c=fixture(false,true,Map.of());var s=new LinkedHashMap<>(c.study());var d=new LinkedHashMap<>(map(list(s.get("datasets")).get(0)));d.put("metadata",Map.of("related_participants",true));s.put("datasets",List.of(d));var r=execute(new Context(c.project(),c.outputDirectory(),c.operation(),s,c.plan(),c.dependencyDirectories()));assertEquals(State.WAITING_FOR_INPUT,r.state());assertFalse(Files.exists(c.outputDirectory().resolve("association.tsv")));
    }
    @Test void invalidIntervalsCodesAndNoEventsRequireEndpointResolution()throws Exception{
        for(String defect:List.of("interval","code","no-events")){var c=fixture(false,true,Map.of());Path p=c.dependencyDirectories().get("prefit").resolve("analysis-phenotype.tsv");var t=DelimitedData.read(p);var rows=new ArrayList<String[]>();for(String[] original:t.rows()){var row=original.clone();if(defect.equals("interval"))row[t.column("time")]=row[t.column("entry")];else row[t.column("event")]=defect.equals("code")?"3":"0";rows.add(row);}FollowupSupport.table(p,t.header(),rows);seal(p.getParent());assertEquals(State.WAITING_FOR_INPUT,execute(c).state(),defect);}
    }
    @Test void prefitQcUsesDeclaredSurvivalColumnsRatherThanGuessingAnOutcomeField()throws Exception{
        var c=fixture(false,true,Map.of());var op=new LinkedHashMap<>(c.operation());op.put("type","participant_qc");var q=new Context(c.project(),Files.createDirectory(c.project().resolve("qc")),op,c.study(),Map.of(),Map.of());var r=new StudyPrefitQc().execute(q);assertEquals(State.COMPLETE,r.state(),r.details().toString());assertEquals(100,((Number)map(r.details().get("survival")).get("analyzed_samples")).intValue());assertTrue(Files.isRegularFile(q.outputDirectory().resolve("analysis-phenotype.tsv")));
    }
    @Test void survivalPoliciesRejectSilentCodingAndModelConflicts(){
        assertThrows(IllegalArgumentException.class,()->StudySurvivalPolicy.resolve(Map.of("event_value",1,"censor_value","1.0")));
        assertThrows(IllegalArgumentException.class,()->StudySurvivalPolicy.resolve(Map.of("time_column","x","event_column","x")));
        assertThrows(IllegalArgumentException.class,()->StudySurvivalPolicy.resolve(Map.of("test","auto")));
        assertThrows(IllegalArgumentException.class,()->org.jlinalg.study.StudyWorkflowPolicy.validateAssociation(Map.of("model","ols","survival",Map.of())));
        assertThrows(IllegalArgumentException.class,()->org.jlinalg.study.StudyWorkflowPolicy.validateAssociation(Map.of("model","cox","family","binomial")));
        assertThrows(IllegalArgumentException.class,()->org.jlinalg.study.StudyWorkflowPolicy.validateAssociation(Map.of("model","cox","weights_column","weights")));
    }
    @Test void baselineAbsorbedConstantCovariatesAreRecordedRatherThanInventedAsCoxEffects()throws Exception{
        var c=fixture(false,true,Map.of());Path p=c.dependencyDirectories().get("prefit").resolve("analysis-phenotype.tsv");var t=DelimitedData.read(p);var rows=new ArrayList<String[]>();for(String[] original:t.rows()){var row=original.clone();row[t.column("age")]="1";rows.add(row);}FollowupSupport.table(p,t.header(),rows);seal(p.getParent());
        var r=execute(c);assertEquals(State.COMPLETE,r.state(),r.details().toString());assertEquals(List.of("age"),map(map(r.details().get("method_report")).get("parameters")).get("baseline_absorbed_covariate_columns"));assertTrue(DelimitedData.readResult(c.outputDirectory().resolve("cox-null-coefficients.tsv")).rows().isEmpty());
    }
    @Test void missingEndpointDocumentationRequestsInputWithoutGuessingAModel()throws Exception{
        var c=fixture(false,true,Map.of());var s=new LinkedHashMap<>(c.study());var h=new LinkedHashMap<>(map(list(s.get("hypotheses")).get(0)));var a=new LinkedHashMap<>(map(h.get("analysis")));var o=new LinkedHashMap<>(map(a.get("association")));var p=new LinkedHashMap<>(map(o.get("survival")));p.remove("event_definition");o.put("survival",p);a.put("association",o);h.put("analysis",a);s.put("hypotheses",List.of(h));
        var result=execute(new Context(c.project(),c.outputDirectory(),c.operation(),s,c.plan(),c.dependencyDirectories()));assertEquals(State.WAITING_FOR_INPUT,result.state());assertTrue(result.summary().contains("event_definition"));assertFalse(Files.exists(c.outputDirectory().resolve("association.tsv")));
    }
    @Test void aggregateFindingContextRetainsSurvivalCaveatsAndNeverParticipantColumns()throws Exception{
        var c=fixture(false,true,Map.of());Path phenotype=c.dependencyDirectories().get("prefit").resolve("analysis-phenotype.tsv");var t=DelimitedData.read(phenotype);var header=new ArrayList<>(t.header());header.add("private_label");var rows=new ArrayList<String[]>();for(String[] row:t.rows()){var extended=java.util.Arrays.copyOf(row,row.length+1);extended[row.length]="PATIENT_ROW_SENTINEL";rows.add(extended);}FollowupSupport.table(phenotype,header,rows);seal(phenotype.getParent());
        var result=execute(c);assertEquals(State.COMPLETE,result.state(),result.details().toString());var hashes=new LinkedHashMap<String,Object>();for(String file:result.outputs())hashes.put(file,FollowupSupport.hash(c.outputDirectory().resolve(file)));
        write(c.outputDirectory().resolve("manifest.yaml"),Map.of("operation_type","association","operation_id","association","hypothesis","H","dataset_id","a","datasets",List.of("a"),"state","complete","output_sha256",hashes,"details",result.details()));
        var out=Files.createDirectory(c.project().resolve("inventory"));var inventory=new Context(c.project(),out,Map.of("id","inventory","type","finding_inventory","scope","study","datasets",List.of("a")),c.study(),Map.of(),Map.of("association",c.outputDirectory()));new StudyFindingInventory().execute(inventory);
        String text=Files.readString(out.resolve("finding-inventory.jsonl"));assertTrue(text.contains("survival_model_context"),"survival context missing");assertTrue(text.toLowerCase(java.util.Locale.ROOT).contains("log hazard ratios"),"survival caveat missing");assertFalse(text.contains("PATIENT_ROW_SENTINEL"));assertTrue(text.contains("ph_family_status"),"PH family context missing");
    }
    @Test void anExplicitInventoryGoalBuildsOfflineDossiersWithoutRequestingAI()throws Exception{
        var c=fixture(false,true,Map.of());var s=new LinkedHashMap<>(c.study());var h=new LinkedHashMap<>(map(list(s.get("hypotheses")).get(0)));var a=new LinkedHashMap<>(map(h.get("analysis")));a.put("goals",List.of("association","finding_inventory"));h.put("analysis",a);s.put("hypotheses",List.of(h));Path file=c.project().resolve("study.yaml");write(file,s);
        var ops=list(org.jlinalg.study.StudyPlanner.compile(org.jlinalg.study.StudyResolver.resolve(file)).document().get("operations")).stream().map(StudyPublicEvidence::map).toList();assertEquals(1,ops.stream().filter(op->"finding_inventory".equals(op.get("type"))).count());assertFalse(ops.stream().anyMatch(op->List.of("impact_assessment","manuscript_draft").contains(op.get("type"))));
    }
    @Test void customEventLabelsAndLogicalEndpointMappingsAreActuallyApplied()throws Exception{
        var c=fixture(false,true,Map.of("time_column","clock","event_column","occurrence","entry_column","entered","strata_column","baseline","event_value","observed","censor_value","censored"));var s=new LinkedHashMap<>(c.study());var d=new LinkedHashMap<>(map(list(s.get("datasets")).get(0)));d.put("columns",Map.of("phenotypes",Map.of("clock","time","occurrence","status","entered","entry"),"covariates",Map.of("baseline","sex")));s.put("datasets",List.of(d));
        Path p=c.dependencyDirectories().get("prefit").resolve("analysis-phenotype.tsv");var table=DelimitedData.read(p);var header=new ArrayList<>(table.header());header.set(table.column("event"),"status");var rows=new ArrayList<String[]>();for(String[] row:table.rows()){var copy=row.clone();copy[table.column("event")]=row[table.column("event")].equals("1")?"observed":"censored";rows.add(copy);}FollowupSupport.table(p,header,rows);seal(p.getParent());
        var result=execute(new Context(c.project(),c.outputDirectory(),c.operation(),s,c.plan(),c.dependencyDirectories()));assertEquals(State.COMPLETE,result.state(),result.details().toString());assertTrue(((Number)map(result.details().get("survival_population")).get("events")).intValue()>0);
    }
    @Test void aSurvivalEndpointWithoutAModelRetainsTheLiteratureReviewGate()throws Exception{
        var c=fixture(false,true,Map.of());var s=new LinkedHashMap<>(c.study());var h=new LinkedHashMap<>(map(list(s.get("hypotheses")).get(0)));var a=new LinkedHashMap<>(map(h.get("analysis")));var o=new LinkedHashMap<>(map(a.get("association")));o.remove("model");a.put("association",o);h.put("analysis",a);s.put("hypotheses",List.of(h));var op=new LinkedHashMap<>(c.operation());op.put("type","participant_qc");
        var r=new StudyPrefitQc().execute(new Context(c.project(),c.outputDirectory(),op,s,Map.of(),Map.of()));assertEquals(State.WAITING_FOR_INPUT,r.state());assertTrue(Files.readString(c.outputDirectory().resolve("model-review.yaml")).contains("association_model_required"));assertFalse(Files.exists(c.outputDirectory().resolve("analysis-phenotype.tsv")));
    }
    @Test void aMonotonePredictorHoldsTheWholeFamilyAndRetainsTheExactLocalProof()throws Exception{
        var c=fixture(false,false,Map.of());var phen=DelimitedData.read(c.dependencyDirectories().get("prefit").resolve("analysis-phenotype.tsv"));var matrix=DelimitedData.read(c.project().resolve("matrix.tsv"));
        var rows=new ArrayList<String[]>();for(String[] row:matrix.rows()){var copy=row.clone();if(copy[0].equals("g1"))for(int i=0;i<phen.rows().size();i++)copy[i+1]=Double.toString(-Double.parseDouble(phen.rows().get(i)[phen.column("time")]));rows.add(copy);}FollowupSupport.table(c.project().resolve("matrix.tsv"),matrix.header(),rows);
        var r=execute(c);assertEquals(State.WAITING_FOR_INPUT,r.state());assertEquals(false,r.details().get("primary_selection_eligible"));
        var raw=DelimitedData.read(c.outputDirectory().resolve("association.tsv"));var rejected=raw.rows().stream().filter(row->row[0].equals("g1")).findFirst().orElseThrow();assertEquals("MONOTONE_LIKELIHOOD",rejected[raw.column("likelihood_existence")]);assertEquals("NA",rejected[raw.column("p_value")]);
        var proof=StudyYaml.loadArtifact(c.outputDirectory().resolve("survival-likelihood-certificates/"+StudyFindingInventory.hash("g1")+".yaml"));assertFalse(list(proof.get("exact_direction")).isEmpty());
        var diagnostics=DelimitedData.read(c.outputDirectory().resolve("survival-diagnostics.tsv"));assertTrue(diagnostics.rows().stream().allMatch(row->row[diagnostics.column("ph_family_status")].equals("incomplete")));
    }
    @Test void aMonotoneNuisanceNullAndAnExhaustedLikelihoodBudgetCannotLaunchAssociations()throws Exception{
        var c=fixture(false,true,Map.of());Path p=c.dependencyDirectories().get("prefit").resolve("analysis-phenotype.tsv");var t=DelimitedData.read(p);var rows=new ArrayList<String[]>();for(String[] row:t.rows()){var copy=row.clone();copy[t.column("age")]=Double.toString(-Double.parseDouble(row[t.column("time")]));rows.add(copy);}FollowupSupport.table(p,t.header(),rows);seal(p.getParent());
        var r=execute(c);assertEquals(State.WAITING_FOR_INPUT,r.state());assertFalse(Files.exists(c.outputDirectory().resolve("association.tsv")));assertTrue(r.summary().contains("MONOTONE_LIKELIHOOD"));
        c=fixture(false,true,Map.of("maximum_likelihood_work",1));r=execute(c);assertEquals(State.WAITING_FOR_INPUT,r.state());assertFalse(Files.exists(c.outputDirectory().resolve("association.tsv")));
    }
    @Test void familyLikelihoodWorkIsSharedRatherThanResetForEachFeature()throws Exception{
        var baseline=fixture(false,false,Map.of());assertEquals(State.COMPLETE,execute(baseline).state());var proof=StudyYaml.loadArtifact(baseline.outputDirectory().resolve("survival-likelihood-certificates/"+StudyFindingInventory.hash("g0")+".yaml"));long limit=((Number)proof.get("work")).longValue();
        var c=fixture(false,false,Map.of("maximum_likelihood_work",limit));var r=execute(c);assertEquals(State.WAITING_FOR_INPUT,r.state());var raw=DelimitedData.read(c.outputDirectory().resolve("association.tsv"));
        assertEquals("FINITE_MAXIMUM",raw.rows().get(0)[raw.column("likelihood_existence")]);assertEquals("UNRESOLVED",raw.rows().get(1)[raw.column("likelihood_existence")]);assertTrue(((Number)map(r.details().get("likelihood_existence")).get("work")).longValue()<=limit);
    }
}
