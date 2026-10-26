/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyOperationExecutor.*;
import org.jlinalg.study.StudyPoolingPolicy;
import org.jlinalg.study.StudyProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyPoolingTest {
    @TempDir Path project;
    private static Map<String,Object> contract(){return Map.of("estimand","synthetic feature effect","orientation","feature_to_phenotype","effect_scale","linear","effect_units","synthetic phenotype units per feature unit","phenotype_transformation","none","feature_transformation","none");}
    private static Map<String,Object> dependence(String mode){return Map.of("method",mode,"matrix","sampling.tsv","rationale","Synthetic known sampling dependence for software validation.","citation","Synthetic covariance fixture; no biological source.");}
    private Context context(int count,String mode,Map<String,Object> configuration)throws Exception{
        List<Object> datasets=new ArrayList<>();List<String> ids=List.of("a","b","c").subList(0,count);
        for(String id:ids){Files.writeString(project.resolve(id+".tsv"),"feature_id\tbeta\tse\tp_value\tn\ng1\t"+(id.equals("a")?".2":".4")+"\t"+(id.equals("b")?".2":".1")+"\t.0001\t1000\n");datasets.add(new LinkedHashMap<>(Map.of("id",id,"cohort","cohort-"+id,"type","transcriptomics","stage","association_results","species","human","tissue","blood","assay","RNA-seq","genome_build","GRCh38","files",Map.of("results",id+".tsv"))));}
        var pooling=new LinkedHashMap<String,Object>();pooling.put("effect_contract",contract());pooling.put("require_complete_contract",true);pooling.put("dependence",mode.equals("independent")?Map.of("method",mode):dependence(mode));
        var roles=count==3?Map.of("discovery",List.of("a","b"),"replication",List.of("c")):Map.of("discovery",ids);
        var analysis=new LinkedHashMap<String,Object>();analysis.put("pooling",pooling);analysis.put("mode","manual");analysis.put("goals",List.of("meta_analysis","discovery_replication"));analysis.put("operations",Map.of("meta_analysis",configuration));analysis.put("harmonization",Map.of("effect_outliers",Map.of("enabled",false)));
        var study=new LinkedHashMap<String,Object>();study.put("schema_version",1);study.put("project",Map.of("id","pooling-fixture","title","Synthetic covariance pooling","description","Software contracts and numerical checks."));study.put("resources",Map.of("network","offline","downloads","deny"));study.put("datasets",datasets);study.put("hypotheses",List.of(Map.of("id","H","description","Synthetic commensurate effects.","outcome","synthetic","inputs",roles,"analysis",analysis)));
        Path output=project.resolve("output");Files.createDirectories(output);return new Context(project,output,Map.of("id","meta","type","meta_analysis","hypothesis","H","datasets",ids,"configuration",configuration),study,Map.of(),Map.of());
    }
    private Map<String,Path> inputs(int count){Map<String,Path> result=new LinkedHashMap<>();for(String id:List.of("a","b","c").subList(0,count))result.put(id,project.resolve(id+".tsv"));return result;}
    private void correlation(int count,double rho)throws Exception{
        StringBuilder matrix=new StringBuilder("feature_id\tdataset_a\tdataset_b\tvalue\n");var ids=List.of("a","b","c").subList(0,count);
        for(int i=0;i<count;i++)for(int j=i;j<count;j++)matrix.append("*\t").append(ids.get(i)).append('\t').append(ids.get(j)).append('\t').append(i==j?1:rho).append('\n');Files.writeString(project.resolve("sampling.tsv"),matrix);
    }
    private String[] row(Context context)throws Exception{return DelimitedData.readResult(context.outputDirectory().resolve("meta-analysis.tsv")).rows().get(0);}
    @Test void typedLogHazardScalesCannotBePooledAsIfTheyWereLinearOrLogOddsEffects()throws Exception{
        var c=context(2,"independent",Map.of());Files.writeString(project.resolve("a.tsv"),"feature_id\tbeta\tse\tp_value\teffect_scale\ng1\t.2\t.1\t.01\tlog_hazard_ratio\n");Files.writeString(project.resolve("b.tsv"),"feature_id\tbeta\tse\tp_value\teffect_scale\ng1\t.4\t.1\t.01\tlog_odds_ratio\n");
        assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().meta(c,inputs(2)).state());String audit=Files.readString(c.outputDirectory().resolve("pooling-audit.yaml"));assertTrue(audit.contains("source_contract_conflicts_with_declared_effect_scale"));assertTrue(audit.contains("incompatible_effect_scale"));assertFalse(Files.exists(c.outputDirectory().resolve("meta-analysis.tsv")));
    }
    @Test void fixedGlsMatchesAnIndependentTwoByTwoInverseCalculation()throws Exception{
        Context context=context(2,"sampling_correlation",Map.of("model","fixed"));correlation(2,.3);var result=new StudyCliExecutor().meta(context,inputs(2));assertEquals(State.COMPLETE,result.state(),result.details().toString());
        double v1=.01,v2=.04,c=.006,total=v1+v2-2*c;double expected=((v2-c)*.2+(v1-c)*.4)/total;double variance=(v1*v2-c*c)/total;
        var table=DelimitedData.readResult(context.outputDirectory().resolve("meta-analysis.tsv"));var row=table.rows().get(0);assertEquals(expected,Double.parseDouble(row[table.column("beta")]),1e-12);assertEquals(Math.sqrt(variance),Double.parseDouble(row[table.column("se")]),1e-12);assertEquals(.04/total,Double.parseDouble(row[table.column("q")]),1e-12);assertEquals("NA",row[table.column("i_squared")]);
        var weights=DelimitedData.readResult(context.outputDirectory().resolve("pooling-weights.tsv"));assertEquals((v2-c)/total,Double.parseDouble(weights.rows().get(0)[2]),1e-12);assertEquals(2000,Double.parseDouble(row[table.column("reported_n_sum")]));
    }
    @Test void randomRemlMatchesTheEqualVarianceTwoSourceClosedForm()throws Exception{
        Context context=context(2,"sampling_correlation",Map.of("model","random","tau_squared_estimator","reml"));correlation(2,.5);
        Files.writeString(project.resolve("b.tsv"),"feature_id\tbeta\tse\tp_value\ng1\t.4\t.1\t.001\n");
        assertEquals(State.COMPLETE,new StudyCliExecutor().meta(context,inputs(2)).state());var table=DelimitedData.readResult(context.outputDirectory().resolve("meta-analysis.tsv"));var row=table.rows().get(0);
        assertEquals(.3,Double.parseDouble(row[table.column("beta")]),1e-10);assertEquals(.015,Double.parseDouble(row[table.column("tau_squared")]),2e-8);assertEquals(Math.sqrt(.015),Double.parseDouble(row[table.column("se")]),2e-7);
    }
    @Test void suppliedCovarianceAndIndependentStudentIntervalsHonorTheDeclaredOptions()throws Exception{
        Context covariance=context(2,"sampling_covariance",Map.of("model","fixed"));Files.writeString(project.resolve("sampling.tsv"),"feature_id\tdataset_a\tdataset_b\tvalue\n*\ta\ta\t.01\n*\ta\tb\t.006\n*\tb\tb\t.04\n");assertEquals(State.COMPLETE,new StudyCliExecutor().meta(covariance,inputs(2)).state());var table=DelimitedData.readResult(covariance.outputDirectory().resolve("meta-analysis.tsv"));assertEquals(Math.sqrt(.000364/.038),Double.parseDouble(table.rows().get(0)[table.column("se")]),1e-12);
        Context source=context(2,"independent",Map.of("model","fixed","confidence_level",.9,"inference","t"));Context independent=new Context(project,project.resolve("student"),source.operation(),source.study(),Map.of(),Map.of());Files.createDirectories(independent.outputDirectory());var result=new StudyCliExecutor().meta(independent,inputs(2));assertEquals(State.COMPLETE,result.state());table=DelimitedData.readResult(independent.outputDirectory().resolve("meta-analysis.tsv"));double critical=Math.tan(Math.PI*(.95-.5));assertEquals(.24-critical*Math.sqrt(.008),Double.parseDouble(table.rows().get(0)[table.column("ci_lower")]),1e-10);assertEquals("STUDENT_T",map(map(result.details().get("method_report")).get("parameters")).get("inference"));
    }
    @Test void discordantKnownGwasAllelesCannotSilentlyCancelInThePool()throws Exception{
        Context original=context(2,"independent",Map.of());var study=new LinkedHashMap<>(original.study());List<Object> datasets=new ArrayList<>();for(Object raw:list(study.get("datasets"))){var dataset=new LinkedHashMap<>(map(raw));dataset.put("type","gwas");datasets.add(dataset);}study.put("datasets",datasets);
        Files.writeString(project.resolve("a.tsv"),"feature_id\tbeta\tse\tp_value\teffect_allele\tother_allele\nrs1\t.2\t.1\t.01\tG\tA\n");Files.writeString(project.resolve("b.tsv"),"feature_id\tbeta\tse\tp_value\teffect_allele\tother_allele\nrs1\t-.2\t.1\t.01\tA\tG\n");
        Context context=new Context(project,original.outputDirectory(),original.operation(),study,Map.of(),Map.of());assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().meta(context,inputs(2)).state());assertTrue(Files.readString(context.outputDirectory().resolve("pooling-variant-compatibility.tsv")).contains("effect_allele"));assertFalse(Files.exists(context.outputDirectory().resolve("meta-analysis.tsv")));
    }
    @Test void featureSpecificMatricesAndMissingSourcesUseTheActualSourceSubset()throws Exception{
        Context context=context(3,"sampling_correlation",Map.of());correlation(3,.3);
        Files.writeString(project.resolve("a.tsv"),Files.readString(project.resolve("a.tsv"))+"g2\t.2\t.1\t.001\t900\n");Files.writeString(project.resolve("c.tsv"),Files.readString(project.resolve("c.tsv"))+"g2\t.4\t.1\t.001\t800\n");Files.writeString(project.resolve("sampling.tsv"),Files.readString(project.resolve("sampling.tsv"))+"g2\ta\tc\t.5\n");
        assertEquals(State.COMPLETE,new StudyCliExecutor().meta(context,inputs(3)).state());var table=DelimitedData.readResult(context.outputDirectory().resolve("meta-analysis.tsv"));var row=table.rows().get(1);assertEquals("2",row[table.column("n_cohorts")]);assertEquals("+?+",row[table.column("direction")]);assertEquals(Math.sqrt(.0075),Double.parseDouble(row[table.column("se")]),1e-12);
    }
    @Test void missingPairsCannotSilentlyBecomeZeroCorrelationOrIndependentPooling()throws Exception{
        Context context=context(2,"sampling_correlation",Map.of());correlation(2,.3);Files.writeString(project.resolve("sampling.tsv"),Files.readString(project.resolve("sampling.tsv")).replace("*\ta\tb\t0.3\n",""));
        var result=new StudyCliExecutor().meta(context,inputs(2));assertEquals(State.WAITING_FOR_INPUT,result.state());var table=DelimitedData.readResult(context.outputDirectory().resolve("meta-analysis.tsv"));assertEquals("sampling_covariance_unresolved",row(context)[table.column("status")]);assertEquals("NA",row(context)[table.column("p_value")]);assertTrue(result.details().containsKey("needs"));
    }
    @Test void singularMatricesAndInconsistentSamplingVariancesRemainUnresolved()throws Exception{
        Context first=context(2,"sampling_correlation",Map.of());correlation(2,1);assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().meta(first,inputs(2)).state());
        Context original=context(2,"sampling_covariance",Map.of());Context second=new Context(project,project.resolve("covariance"),original.operation(),original.study(),Map.of(),Map.of());Files.createDirectories(second.outputDirectory());Files.writeString(project.resolve("sampling.tsv"),"feature_id\tdataset_a\tdataset_b\tvalue\n*\ta\ta\t.02\n*\ta\tb\t.006\n*\tb\tb\t.04\n");assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().meta(second,inputs(2)).state());assertTrue(Files.readString(second.outputDirectory().resolve("pooling-feature-audit.tsv")).contains("disagrees with the source standard error"));
    }
    @Test void incompatibleEffectsAndTissuesRequireSeparateCompatibleHypotheses()throws Exception{
        Context original=context(2,"independent",Map.of());var study=new LinkedHashMap<>(original.study());var datasets=new ArrayList<Object>(list(study.get("datasets")));var b=new LinkedHashMap<>(map(datasets.get(1)));b.put("tissue","liver");b.put("effect_contract",Map.of("effect_units","incompatible units"));datasets.set(1,b);study.put("datasets",datasets);Context context=new Context(project,original.outputDirectory(),original.operation(),study,Map.of(),Map.of());var result=new StudyCliExecutor().meta(context,inputs(2));assertEquals(State.WAITING_FOR_INPUT,result.state());assertFalse(Files.exists(context.outputDirectory().resolve("meta-analysis.tsv")));String audit=Files.readString(context.outputDirectory().resolve("pooling-audit.yaml"));assertTrue(audit.contains("incompatible_tissue"));assertTrue(audit.contains("effect_units"));
    }
    @Test void unknownContractsAreAuditedAndStrictModeCannotCertifyThem()throws Exception{
        Context original=context(2,"independent",Map.of());var study=new LinkedHashMap<>(original.study());var hypotheses=new ArrayList<Object>(list(study.get("hypotheses")));var hypothesis=new LinkedHashMap<>(map(hypotheses.get(0)));var analysis=new LinkedHashMap<>(map(hypothesis.get("analysis")));analysis.put("pooling",Map.of("require_complete_contract",true));hypothesis.put("analysis",analysis);hypotheses.set(0,hypothesis);study.put("hypotheses",hypotheses);Context strict=new Context(project,original.outputDirectory(),original.operation(),study,Map.of(),Map.of());assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().meta(strict,inputs(2)).state());
        analysis.put("pooling",Map.of("require_complete_contract",false));hypothesis.put("analysis",analysis);study.put("hypotheses",List.of(hypothesis));Context legacy=new Context(project,project.resolve("legacy"),original.operation(),study,Map.of(),Map.of());Files.createDirectories(legacy.outputDirectory());var result=new StudyCliExecutor().meta(legacy,inputs(2));assertEquals(State.COMPLETE,result.state());assertEquals("partially_declared_not_certified",map(result.details().get("pooling_audit")).get("compatibility_status"));
    }
    @Test void covarianceAdjustedPoolingDoesNotMakeDiscoveryValidationIndependent()throws Exception{
        Context original=context(2,"sampling_correlation",Map.of());correlation(2,.4);assertEquals(State.COMPLETE,new StudyCliExecutor().meta(original,inputs(2)).state());
        var operation=new LinkedHashMap<>(original.operation());operation.put("type","discovery_replication");operation.put("roles",Map.of("discovery",List.of("a"),"replication",List.of("b")));Context validation=new Context(project,project.resolve("validation"),operation,original.study(),Map.of(),Map.of());var result=new StudyCliExecutor().replication(validation,inputs(2),null);assertEquals(State.WAITING_FOR_INPUT,result.state());assertFalse(Files.exists(validation.outputDirectory().resolve("replication.tsv")));assertTrue(Files.readString(validation.outputDirectory().resolve("pooling-audit.yaml")).contains("sampling_matrix_links_discovery_validation"));
    }
    @Test void knownIdentifierNamespaceMismatchBlocksPoolingWhileAutoRemainsUnknown()throws Exception {
        Context original=context(2,"independent",Map.of());for(boolean compatible:List.of(false,true)){var study=new LinkedHashMap<>(original.study());var datasets=new ArrayList<Object>();int i=0;for(Object raw:list(study.get("datasets"))){var d=new LinkedHashMap<>(map(raw));d.put("feature_id_type",i++==0?(compatible?"auto":"gene_symbol"):"probe");datasets.add(d);}study.put("datasets",datasets);Path directory=project.resolve(compatible?"auto-namespace":"incompatible-namespace");Files.createDirectories(directory);var c=new Context(project,directory,original.operation(),study,Map.of(),Map.of());var result=new StudyCliExecutor().meta(c,inputs(2));assertEquals(compatible?State.COMPLETE:State.WAITING_FOR_INPUT,result.state(),result.details().toString());if(!compatible){assertFalse(Files.exists(directory.resolve("meta-analysis.tsv")));assertTrue(Files.readString(directory.resolve("pooling-audit.yaml")).contains("incompatible_feature_id_type"));}}
    }
    @Test void repeatedCohortIdentityIsNotIndependentReplicationOrPooling()throws Exception{
        Context original=context(2,"independent",Map.of());var study=new LinkedHashMap<>(original.study());var datasets=new ArrayList<Object>(list(study.get("datasets")));var b=new LinkedHashMap<>(map(datasets.get(1)));b.put("cohort","cohort-a");datasets.set(1,b);study.put("datasets",datasets);Context context=new Context(project,original.outputDirectory(),original.operation(),study,Map.of(),Map.of());assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().meta(context,inputs(2)).state());
    }
    @Test void invalidPolicyCannotWaiveEffectUnitsOrIgnoreADependenceMatrix(){assertThrows(IllegalArgumentException.class,()->StudyPoolingPolicy.resolve(Map.of("context_exceptions",Map.of("effect_units","arbitrary waiver"))));assertThrows(IllegalArgumentException.class,()->StudyPoolingPolicy.resolve(Map.of("dependence",Map.of("method","sampling_covariance","matrix","x"))));assertThrows(IllegalArgumentException.class,()->StudyPoolingPolicy.resolve(Map.of("dependence",Map.of("method","independent","matrix","x"))));}
    private String cli(String...args)throws Exception{var out=new ByteArrayOutputStream();var error=new ByteArrayOutputStream();int code=JLinAlgCli.run(args,new PrintStream(out),new PrintStream(error));assertEquals(0,code,out+"\n"+error);return out.toString();}
    @Test void dependentDiscoverySourcesPoolWithoutValidationLeakageAndMatrixChangesInvalidateCache()throws Exception{
        Context fixture=context(3,"sampling_correlation",Map.of("model","fixed"));correlation(3,0);Files.writeString(project.resolve("sampling.tsv"),Files.readString(project.resolve("sampling.tsv")).replace("*\ta\tb\t0.0","*\ta\tb\t0.5"));
        for(String id:List.of("a","b","c"))Files.writeString(project.resolve(id+".tsv"),"feature_id\tbeta\tse\tp_value\tn\ng1\t"+(id.equals("a")?".5":id.equals("b")?".6":".55")+"\t.1\t1e-8\t1000\n");
        var study=new LinkedHashMap<>(fixture.study());study.put("cohort_relationships",List.of(Map.of("between",List.of("cohort-a","cohort-b"),"type","overlapping","description","Synthetic shared samples with known sampling correlation.")));Path contract=project.resolve("study.yaml");write(contract,study);String hash=FollowupSupport.hash(contract);assertTrue(cli("study","auto",contract.toString(),"--offline").contains("state: completed"));
        String first=text(StudyProject.status(project).get("run_id"));Path folder;
        try(var paths=Files.list(project.resolve("runs").resolve(first).resolve("operations"))){folder=paths.filter(path->path.getFileName().toString().endsWith("-discovery_meta_analysis")).findFirst().orElseThrow();}
        var discovery=DelimitedData.readResult(folder.resolve("discovery-results.tsv"));assertEquals(.55,Double.parseDouble(discovery.rows().get(0)[discovery.column("beta")]),1e-12);assertEquals(Math.sqrt(.0075),Double.parseDouble(discovery.rows().get(0)[discovery.column("se")]),1e-12);
        Files.writeString(project.resolve("sampling.tsv"),Files.readString(project.resolve("sampling.tsv")).replace("*\ta\tb\t0.5","*\ta\tb\t0.2"));assertTrue(cli("study","run","--project",project.toString()).contains("state: completed"));assertEquals(hash,FollowupSupport.hash(contract));String second=text(StudyProject.status(project).get("run_id"));assertNotEquals(first,second);
        try(var paths=Files.list(project.resolve("runs").resolve(second).resolve("operations"))){folder=paths.filter(path->path.getFileName().toString().endsWith("-discovery_meta_analysis")).findFirst().orElseThrow();}
        var manifest=StudyYaml.loadArtifact(folder.resolve("manifest.yaml"));assertFalse(Boolean.TRUE.equals(manifest.get("reused_from_cache")));discovery=DelimitedData.readResult(folder.resolve("discovery-results.tsv"));assertEquals(Math.sqrt(.006),Double.parseDouble(discovery.rows().get(0)[discovery.column("se")]),1e-12);
    }
}
