/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jlinalg.pipeline.*;
import org.jlinalg.study.StudyNeed;
import org.jlinalg.study.StudyOperationExecutor;
import org.jlinalg.study.StudySubgroupInteractionPolicy;

/** Registered, adjusted feature-by-subgroup tests; never a replacement primary analysis. */
final class StudySubgroupInteractions implements StudyOperationExecutor {
    static final String CAVEAT="Subgroup interaction results concern the registered conditional association model and contrast. They do not establish biological causality, independent replication or a replacement primary result. Group-specific significance alone is not an interaction test. HC3 residual-df inference and logistic/Firth likelihood-ratio tails have approximation and sample-size limits; ordinary-Fisher binary intervals are not profile-likelihood intervals. Related, repeated or clustered observations require a suitable dependence-aware model. Genotype results retain source ploidy assumptions and do not validate biological sex/PAR interpretation. Outcome-defined groups cannot establish effect modification. Data-driven subgroup proposals remain exploratory.";
    static final List<String> COLUMNS=List.of("test_id","dataset_id","feature_id","group_id","reference_level","comparison_level","group_column","method","status","reason","in_testing_family","n","n_reference","n_comparison","n_cases_reference","n_cases_comparison","mac_reference","mac_comparison","beta","se","ci_lower","ci_upper","p_value","log_p_value","statistic","df","adjusted_p","adjustment","family_size","family_status","sensitivity_candidate","reference_beta","reference_se","comparison_beta","comparison_se","parameters","iterations","effect_scale","inference","required_manuscript_caveat");
    private final List<Map<String,Object>> results=new ArrayList<>();
    private final List<StudyNeed> needs=new ArrayList<>();
    private final List<Object> inputs=new ArrayList<>(),designs=new ArrayList<>();
    private final List<String[]> memberships=new ArrayList<>();
    private Map<String,Object> policy,hypothesis,association;
    private long work,attempted;private boolean knownFamily=true;private int matrixDatasets;
    private record Group(String id,String column,String reference,String comparison,int[] indices,int[] labels,double[] y,double[][] covariates,double[] offset,String error,boolean circular) { }

    @Override public Result execute(Context c)throws IOException {
        Files.createDirectories(c.outputDirectory());results.clear();needs.clear();inputs.clear();designs.clear();memberships.clear();work=0;attempted=0;knownFamily=true;matrixDatasets=0;
        hypothesis=StudyBioinformatics.hypothesis(c);var analysis=map(hypothesis.get("analysis"));association=map(analysis.get("association"));
        policy=StudySubgroupInteractionPolicy.resolve(map(analysis.get("subgroup_interactions")));
        if(!Boolean.TRUE.equals(policy.get("enabled")))return new Result(State.NOT_APPLICABLE,"No registered subgroup interaction family",List.of(),Map.of());
        var datasets=StudyBioinformatics.datasets(c);
        if(datasets.isEmpty()){knownFamily=false;need("inputs","Participant-level matrices and verified participant QC are required; supplied summary results cannot reconstruct interaction models");}
        for(var dataset:datasets)analyze(c,dataset);
        if(matrixDatasets==0){knownFamily=false;need("matrices","No participant-level matrix dataset was available for the registered interaction family");}
        var probabilities=new LinkedHashMap<String,Double>();int denominator=0,pending=0,closed=0;
        for(var row:results)if(Boolean.TRUE.equals(row.get("in_testing_family"))) {
            denominator++;if(row.get("p_value") instanceof Double p)probabilities.put(text(row.get("test_id")),p);
            else if("waiting_for_input".equals(row.get("status")))pending++;else closed++;
        }
        boolean complete=knownFamily&&pending==0&&needs.isEmpty();String family=complete?"complete_registered_family":"incomplete_registered_family";
        var q=StudyMrMultiplicity.adjust(probabilities,denominator,text(policy.get("adjustment")));
        var rows=new ArrayList<String[]>();
        for(var row:results) {
            String id=text(row.get("test_id"));row.put("adjustment",policy.get("adjustment"));row.put("family_size",denominator);row.put("family_status",family);
            row.put("adjusted_p",complete&&q.containsKey(id)?q.get(id):"NA");
            row.put("sensitivity_candidate",complete&&q.getOrDefault(id,1.)<((Number)policy.get("alpha")).doubleValue());
            rows.add(COLUMNS.stream().map(k->text(row.getOrDefault(k,"NA"))).toArray(String[]::new));
        }
        FollowupSupport.table(c.outputDirectory().resolve("subgroup-interactions.tsv"),COLUMNS,rows);
        FollowupSupport.table(c.outputDirectory().resolve("participant-membership.tsv"),List.of("dataset_id","group_id","sample_id","group_label","in_contrast","reason"),memberships);
        var summary=new LinkedHashMap<String,Object>();summary.put("effective_policy",policy);summary.put("inputs",inputs);summary.put("designs",designs);
        summary.put("family_status",family);summary.put("family_size",denominator);summary.put("estimable_tests",probabilities.size());summary.put("unresolved_tests",pending);summary.put("completed_unestimable_tests",closed);
        summary.put("family_universe_known",knownFamily);summary.put("family_scope","All registered dataset-by-group-by-feature tests after explicit feature-scope selection. Prespecified QC/insufficient-support and unavailable tests retain dispositions; no primary-association P-value or histogram gate selects this family.");
        summary.put("primary_association_changed",false);summary.put("new_model_attempts",attempted);summary.put("charged_work_bound",work);
        summary.put("required_manuscript_caveat",CAVEAT);summary.put("privacy","Participant membership and raw data remain local; only aggregate canonical interaction rows enter finding dossiers.");
        summary.put("method_report",StudyMethods.record("Registered adjusted subgroup effect modification",Map.of("policy",policy,"covariates",list(hypothesis.get("covariates")),"sources",inputs,"designs",designs),List.of("https://doi.org/10.1136/bmj.326.7382.219","https://doi.org/10.1016/j.biopsych.2013.09.006","https://doi.org/10.1093/biomet/80.1.27"),policy.get("rationale"),List.of("subgroup interaction","covariate interaction confounding","HC3","Firth likelihood ratio")));
        summary.put("needs",needs.stream().map(StudyNeed::document).toList());write(c.outputDirectory().resolve("subgroup-interactions.yaml"),summary);
        return new Result(complete?State.COMPLETE:State.WAITING_FOR_INPUT,"Adjusted subgroup interactions: "+probabilities.size()+" estimable tests; "+pending+" unresolved; primary association unchanged",List.of("subgroup-interactions.tsv","subgroup-interactions.yaml","participant-membership.tsv"),summary);
    }

    private void analyze(Context c,Map<String,Object> dataset)throws IOException {
        String id=text(dataset.get("id"));var files=map(dataset.get("files"));var columns=map(dataset.get("columns"));
        if(!Set.of("raw_quantified","quantified_matrix","processed_matrix").contains(dataset.get("stage")))return;
        matrixDatasets++;
        String issue="";String method=method();
        if(!Boolean.TRUE.equals(policy.get("independent_rows"))||files.containsKey("grm")||files.containsKey("pedigree")||Boolean.TRUE.equals(map(dataset.get("metadata")).get("related_participants"))||Boolean.TRUE.equals(map(dataset.get("metadata")).get("repeated_measures")))issue="A documented independent-row design is required; native interaction adapters do not fit kinship, repeated measures or clustered errors";
        if(!org.jlinalg.study.StudyOperationRegistry.descriptor("association").modalities().contains(text(dataset.get("type"))))issue="Use a supported independent-unit bulk matrix; single-cell/spatial rows require a reviewed donor/cluster-level model";
        if(method.isBlank())issue="The supplied association model/family has no matching native interaction adapter; specify a supported reviewed method rather than substituting a model";
        Path matrix=StudyAssayQcSources.matrix(c,dataset).normalize();
        if(!Files.isRegularFile(matrix)){knownFamily=false;need(id,"The declared participant matrix is missing; no feature universe or null interaction was inferred");return;}
        String startHash=FollowupSupport.hash(matrix);boolean genotype=Set.of("gwas","genomics").contains(dataset.get("type"));
        VariantSource variants=null;NumericMatrixSource numeric=null;List<String> sampleIds;
        try {
            if(genotype){String sampleRole=files.containsKey("sample")?"sample":"samples";variants=VariantSources.open(matrix,DataFormat.infer(matrix),files.containsKey(sampleRole)?SampleFiles.read(c.project().resolve(text(files.get(sampleRole)))):null);sampleIds=variants.metadata().sampleIds();}
            else{numeric=DelimitedMatrixSource.open(matrix);sampleIds=numeric.metadata().sampleIds();}
        }catch(IOException|IllegalArgumentException invalid){knownFamily=false;need(id,"Matrix/identity prerequisite: "+invalid.getMessage());return;}
        DelimitedData phenotype=null;Path phenotypePath=null;Map<String,Object> qcProof=Map.of();
        try {
            var qc=findQc(c,id);phenotypePath=qc.directory.resolve("analysis-phenotype.tsv");phenotype=DelimitedData.read(StudyFindingInventory.verified(qc.directory,"analysis-phenotype.tsv",text(map(qc.manifest.get("output_sha256")).get("analysis-phenotype.tsv"))));
            for(var output:map(qc.manifest.get("output_sha256")).entrySet())StudyFindingInventory.verified(qc.directory,output.getKey(),text(output.getValue()));
            var qcInputs=map(qc.manifest.get("input_sha256"));
            for(String role:List.of("matrix","phenotype","sample","samples"))if(files.containsKey(role)) {
                String actual=FollowupSupport.hash(c.project().resolve(text(files.get(role))));
                if(!actual.equals(qcInputs.get("dataset:"+id+":"+role)))throw new IOException("Participant QC does not match the current "+role+" source version");
            }
            qcProof=Map.of("producer_manifest_sha256",FollowupSupport.hash(qc.directory.resolve("manifest.yaml")),"selected_phenotype_sha256",FollowupSupport.hash(phenotypePath));
        }catch(IOException|IllegalArgumentException missing){phenotype=null;phenotypePath=null;issue="Verified participant QC is required: "+missing.getMessage();}
        var input=new LinkedHashMap<String,Object>();input.put("dataset_id",id);input.put("matrix_sha256",startHash);input.put("participant_qc",qcProof);input.put("method",method);inputs.add(input);
        var positions=new LinkedHashMap<String,Integer>();for(int i=0;i<sampleIds.size();i++)positions.put(sampleIds.get(i),i);
        int[] order=null;var groups=new ArrayList<Group>();
        if(phenotype!=null) {
            int at=phenotype.column(text(columns.getOrDefault("sample_id","id")));
            var ids=new HashSet<String>();order=new int[phenotype.rows().size()];for(int i=0;i<order.length;i++){String sample=phenotype.rows().get(i)[at];if(!ids.add(sample)||!positions.containsKey(sample))throw new IOException("Participant-QC sample IDs do not align uniquely with the current interaction matrix");order[i]=positions.get(sample);}
            for(Object raw:list(policy.get("groups"))) {
                var g=map(raw);
                try{groups.add(group(dataset,phenotype,g,method,issue));}
                catch(IOException|IllegalArgumentException invalid){String error="Subgroup design prerequisite: "+invalid.getMessage();groups.add(new Group(text(g.get("id")),text(g.get("column")),reference(g),comparison(g),new int[0],new int[0],new double[0],new double[0][0],new double[0],error,false));need(id+":"+g.get("id"),error);}
            }
        } else for(Object raw:list(policy.get("groups"))){var g=map(raw);groups.add(new Group(text(g.get("id")),text(g.get("column")),reference(g),comparison(g),new int[0],new int[0],new double[0],new double[0][0],new double[0],issue,false));}
        if(!issue.isEmpty())need(id,issue);
        for(Group g:groups)if(!g.error().isEmpty())need(id+":"+g.id(),g.error());
        var seen=new HashSet<String>();var requested=new LinkedHashSet<String>(list(policy.get("features")).stream().map(StudyPublicEvidence::text).toList());
        try {
            if(genotype)try(var reader=variants.open(order)){VariantBlock block;while((block=reader.read(128))!=null)for(var row:block.variants())process(dataset,row.id(),row.dosages(),row,groups,seen,requested);}
            else try(var reader=numeric.open(order)){NumericBlock block;while((block=reader.read(128))!=null)for(var row:block.rows())process(dataset,row.id(),row.values(),null,groups,seen,requested);}
        }catch(IOException|IllegalArgumentException invalid){knownFamily=false;need(id,"The complete interaction feature universe could not be processed: "+invalid.getMessage());}
        for(String feature:requested)if(!seen.contains(feature))for(var group:groups){var row=base(id,feature,group,method);row.put("status","waiting_for_input");row.put("reason","Registered feature is absent from the source matrix");results.add(row);need(id,"One or more explicitly registered interaction features are absent from the matrix");}
        if(!startHash.equals(FollowupSupport.hash(matrix))||phenotypePath!=null&&!text(qcProof.get("selected_phenotype_sha256")).equals(FollowupSupport.hash(phenotypePath))){knownFamily=false;need(id,"Interaction inputs changed during execution; retain the attempt and rerun immutable inputs");}
    }

    private Group group(Map<String,Object> dataset,DelimitedData data,Map<String,Object> g,String method,String issue)throws IOException {
        var columns=map(dataset.get("columns"));String outcome=mapped(columns,text(hypothesis.get("outcome"))),column=mapped(columns,text(g.get("column")));
        boolean circular=column.equals(outcome)||list(g.get("derived_from")).stream().map(StudyPublicEvidence::text).map(v->mapped(columns,v)).anyMatch(outcome::equals);
        boolean binary=method.equals("logit-lrt")||method.equals("firth-lrt");
        int groupAt=data.column(column),outcomeAt=data.column(outcome),idAt=data.column(text(columns.getOrDefault("sample_id","id")));
        var covariates=list(hypothesis.get("covariates")).stream().map(StudyPublicEvidence::text).map(v->mapped(columns,v)).filter(v->!v.equals(column)||g.containsKey("cutoff")).toList();
        var categorical=new HashSet<String>();for(Object raw:list(policy.get("categorical_covariates"))){String cov=mapped(columns,text(raw));if(!covariates.contains(cov)&&!cov.equals(column))throw new IOException("A declared categorical interaction covariate is absent from the hypothesis covariates: "+raw);categorical.add(cov);}
        String offsetColumn=association.containsKey("offset_column")?mapped(columns,text(association.get("offset_column"))):"";
        if(!offsetColumn.isBlank()&&covariates.contains(offsetColumn))issue="Offset must not also be an estimated interaction covariate";
        var indices=new ArrayList<Integer>();var labels=new ArrayList<Integer>();var response=new ArrayList<Double>();var offsets=new ArrayList<Double>();
        for(int i=0;i<data.rows().size();i++) {
            String[] row=data.rows().get(i);int label=label(g,row[groupAt]);String reason=label<0?"missing_or_outside_registered_contrast":"";double y=Double.NaN,offset=0;
            if(reason.isEmpty())try{y=binary?binary(row[outcomeAt]):finite(row[outcomeAt]);if(!offsetColumn.isBlank())offset=finite(row[data.column(offsetColumn)]);for(String cov:covariates)if(missing(row[data.column(cov)]))throw new IllegalArgumentException("missing_covariate");}catch(IllegalArgumentException invalid){reason="missing_or_invalid_outcome_covariate_or_offset";issue="Selected participant data contain missing/invalid outcome coding, covariates or offset; regenerate compatible participant QC";}
            memberships.add(new String[]{text(dataset.get("id")),text(g.get("id")),row[idAt],label<0?"NA":label==0?reference(g):comparison(g),Boolean.toString(reason.isEmpty()),reason});
            if(reason.isEmpty()){indices.add(i);labels.add(label);response.add(y);offsets.add(offset);}
        }
        var vectors=new ArrayList<double[]>();var terms=new ArrayList<String>();
        for(String cov:covariates) {
            int at=data.column(cov);boolean numeric=true;for(int i:indices)try{finite(data.rows().get(i)[at]);}catch(IllegalArgumentException invalid){numeric=false;break;}
            if(categorical.contains(cov)||!numeric) {
                var levels=new TreeSet<String>();for(int i:indices)levels.add(data.rows().get(i)[at]);if(levels.size()>100)throw new IOException("Interaction categorical covariate exceeds 100 levels; review the independent-unit model");
                if(StudySubgroupModels.parameters(vectors.size()+Math.max(0,levels.size()-1),text(policy.get("nuisance_interactions")))>((Number)policy.get("maximum_parameters")).intValue())throw new IOException("Interaction nuisance design exceeds the registered parameter bound");
                var ordered=new ArrayList<>(levels);for(int level=1;level<ordered.size();level++){double[] values=new double[indices.size()];for(int i=0;i<values.length;i++)values[i]=data.rows().get(indices.get(i))[at].equals(ordered.get(level))?1:0;vectors.add(values);terms.add(cov+"="+ordered.get(level)+" (reference "+ordered.get(0)+")");}
            } else {double[] values=new double[indices.size()];for(int i=0;i<values.length;i++)values[i]=finite(data.rows().get(indices.get(i))[at]);vectors.add(values);terms.add(cov);}
            if(StudySubgroupModels.parameters(vectors.size(),text(policy.get("nuisance_interactions")))>((Number)policy.get("maximum_parameters")).intValue())throw new IOException("Interaction nuisance design exceeds the registered parameter bound");
        }
        double[][] cov=new double[indices.size()][vectors.size()];for(int i=0;i<cov.length;i++)for(int j=0;j<vectors.size();j++)cov[i][j]=vectors.get(j)[i];
        designs.add(Map.of("dataset_id",dataset.get("id"),"group",g,"method",method,"covariate_terms",terms,"centering","Feature and encoded covariates centered/scaled within each complete test sample; slopes refer to centered covariates, coefficients returned in feature input units","nuisance_interactions",policy.get("nuisance_interactions"),"primary_P_selection",false));
        return new Group(text(g.get("id")),column,reference(g),comparison(g),indices.stream().mapToInt(Integer::intValue).toArray(),labels.stream().mapToInt(Integer::intValue).toArray(),response.stream().mapToDouble(Double::doubleValue).toArray(),cov,offsets.stream().mapToDouble(Double::doubleValue).toArray(),issue,circular);
    }

    private void process(Map<String,Object> dataset,String feature,double[] values,VariantRecord variant,List<Group> groups,Set<String> seen,Set<String> requested) {
        if(!seen.add(feature))throw new IllegalArgumentException("Duplicate interaction feature ID: "+feature);
        if(!requested.isEmpty()&&!requested.contains(feature))return;
        String id=text(dataset.get("id")),method=method();
        for(var group:groups) {
            var row=base(id,feature,group,method);results.add(row);
            if(!group.error().isEmpty()){row.put("reason",group.error());continue;}
            if(group.circular()){row.put("status","not_estimable");row.put("in_testing_family",false);row.put("reason","Outcome-defined subgroup: no effect-modification inference");continue;}
            var keep=new ArrayList<Integer>();int[] counts=new int[2],cases=new int[2];double[] alt=new double[2];
            for(int i=0;i<group.indices().length;i++){double x=values[group.indices()[i]];if(Double.isFinite(x)){keep.add(i);int k=group.labels()[i];counts[k]++;alt[k]+=x;if(group.y()[i]==1)cases[k]++;}}
            int n=keep.size();row.put("n",n);row.put("n_reference",counts[0]);row.put("n_comparison",counts[1]);
            boolean binary=method.equals("firth-lrt")||method.equals("logit-lrt");if(binary){row.put("n_cases_reference",cases[0]);row.put("n_cases_comparison",cases[1]);}
            if(counts[0]<((Number)policy.get("minimum_group_samples")).intValue()||counts[1]<((Number)policy.get("minimum_group_samples")).intValue()) {row.put("status","not_estimable");row.put("in_testing_family",false);row.put("reason","Below the registered minimum complete samples in a contrast level");continue;}
            if(variant!=null) {
                try {
                    String filter=genotypeQc(dataset,variant,values,group);if(!filter.isBlank()){row.put("status","not_estimable");row.put("in_testing_family",false);row.put("reason",filter);continue;}
                    double mac0=Math.min(alt[0],2.*counts[0]-alt[0]),mac1=Math.min(alt[1],2.*counts[1]-alt[1]);row.put("mac_reference",mac0);row.put("mac_comparison",mac1);
                    if(Math.min(mac0,mac1)<((Number)policy.get("minimum_group_mac")).doubleValue()){row.put("status","not_estimable");row.put("in_testing_family",false);row.put("reason","Below registered group-specific MAC; pooled MAC is insufficient for an interaction");continue;}
                }catch(IllegalArgumentException invalid){row.put("reason","Genotype QC unavailable: "+invalid.getMessage());need(id,"Genotype interaction QC requires compatible metadata/coding");continue;}
            }
            int columns=group.covariates()[0].length,p=StudySubgroupModels.parameters(columns,text(policy.get("nuisance_interactions")));
            long bound=(long)n*p*p*(method.startsWith("ols-")?1:2L*((Number)policy.get("maximum_iterations")).intValue());
            if(attempted>=((Number)policy.get("maximum_tests")).longValue()||bound>((Number)policy.get("maximum_work")).longValue()-work){row.put("reason","Registered interaction numerical-work bound exhausted; no truncated family adjustment");need("budget","Increase the prespecified feasible interaction work bound or explicitly amend the family; unprocessed tests are not nulls");continue;}
            attempted++;work+=bound;
            double[] y=new double[n],x=new double[n],offset=new double[n];int[] labels=new int[n];double[][] cov=new double[n][columns];
            for(int i=0;i<n;i++){int k=keep.get(i);y[i]=group.y()[k];x[i]=values[group.indices()[k]];offset[i]=group.offset()[k];labels[i]=group.labels()[k];cov[i]=group.covariates()[k];}
            try {
                if(binary&&(cases[0]+cases[1]==0||cases[0]+cases[1]==n))throw new IllegalArgumentException("The complete interaction sample has only one outcome class");
                if(binary&&(cases[0]==0||cases[0]==counts[0]||cases[1]==0||cases[1]==counts[1])) {
                    if(!method.equals("firth-lrt"))throw new StudySubgroupModels.Unresolved("A subgroup lacks both outcome classes; ordinary logistic interaction is not estimable");
                }
                var fit=StudySubgroupModels.fit(method,y,cov,labels,x,offset,policy);
                row.put("status","complete");row.put("reason","");row.put("beta",fit.beta());row.put("se",fit.se());row.put("ci_lower",fit.lower());row.put("ci_upper",fit.upper());row.put("p_value",fit.p());row.put("log_p_value",fit.logP());row.put("statistic",fit.statistic());row.put("df",fit.df());row.put("reference_beta",fit.referenceBeta());row.put("reference_se",fit.referenceSe());row.put("comparison_beta",fit.comparisonBeta());row.put("comparison_se",fit.comparisonSe());row.put("parameters",fit.parameters());row.put("iterations",fit.iterations());row.put("inference",fit.inference());row.put("effect_scale",fit.effectScale());
            }catch(StudySubgroupModels.Unresolved|ArithmeticException unresolved){row.put("reason",unresolved.getMessage());need(id,"Resolve subgroup interaction numerical/model prerequisites; no automatic method fallback was used");}
            catch(IllegalArgumentException unestimable){row.put("status","not_estimable");row.put("reason",unestimable.getMessage());}
        }
    }

    private String genotypeQc(Map<String,Object> dataset,VariantRecord variant,double[] values,Group group) {
        double[] dosage=new double[group.indices().length];byte[] original=variant.genotypeMetadata().observedPloidies(),ploidies=original==null?null:new byte[dosage.length];
        for(int i=0;i<dosage.length;i++){dosage[i]=values[group.indices()[i]];if(ploidies!=null)ploidies[i]=original[group.indices()[i]];}
        var metadata=new VariantGenotypeMetadata(variant.genotypeMetadata().origin(),variant.genotypeMetadata().splitMultiallelic(),ploidies);
        var selected=new VariantRecord(variant.id(),variant.chromosome(),variant.position(),variant.referenceAllele(),variant.alternateAllele(),dosage,variant.imputationQuality(),metadata);
        boolean binary=method().equals("logit-lrt")||method().equals("firth-lrt");int[] outcomes=binary?Arrays.stream(group.y()).mapToInt(v->(int)v).toArray():null;
        if(map(dataset.get("files")).containsKey("hwe_samples"))throw new IllegalArgumentException("A separate HWE sample panel needs a reviewed interaction-specific QC export");
        var quality=new VariantQualityOptions(number(association,"minimum_hwe_p",0),text(association.getOrDefault("hwe_filter_scope",binary?"controls":"all")),outcomes,null,number(association,"minimum_missingness_p",0),(int)number(association,"missingness_max_states",100000),text(association.getOrDefault("genotype_kind","auto")),"human_autosomes".equals(association.get("chromosome_scope")));
        var filter=new VariantFilterOptions(number(association,"minimum_maf",0),number(association,"maximum_maf",.5),number(association,"minimum_mac",20),number(association,"maximum_mac",Double.POSITIVE_INFINITY),number(association,"maximum_marker_missing",.02),number(association,"minimum_info","imputed".equals(association.get("genotype_kind"))?.8:Double.NaN),true,quality);
        var result=VariantFilters.evaluate(selected,filter);return result.reasons().isEmpty()?"":"Registered genotype QC: "+result.reasons();
    }
    private Map<String,Object> base(String dataset,String feature,Group g,String method) {
        var row=new LinkedHashMap<String,Object>();row.put("test_id","interaction-"+StudyFindingInventory.hash(dataset+"\n"+g.id()+"\n"+feature).substring(0,24));row.put("dataset_id",dataset);row.put("feature_id",feature);row.put("group_id",g.id());row.put("group_column",g.column());row.put("reference_level",g.reference());row.put("comparison_level",g.comparison());row.put("method",method);row.put("status","waiting_for_input");row.put("reason","Unresolved registered interaction");row.put("in_testing_family",true);row.put("required_manuscript_caveat",CAVEAT);return row;
    }
    private String method(){String configured=text(policy.get("method")),model=text(association.get("model"));boolean linear=model.equals("ols")&&(!association.containsKey("family")||Set.of("gaussian","normal").contains(text(association.get("family"))));boolean binary=model.equals("glm")&&"binomial".equals(association.get("family"))&&(!association.containsKey("link")||"logit".equals(association.get("link")));if(!configured.equals("inherit"))return linear&&configured.startsWith("ols-")||binary&&Set.of("logit-lrt","firth-lrt").contains(configured)?configured:"";if(linear)return "ols-hc3";if(binary)return "exact-conditional".equals(association.get("binary_test"))?"":"firth-lrt".equals(association.get("binary_test"))?"firth-lrt":"logit-lrt";return "";}
    private record Qc(Path directory,Map<String,Object> manifest) { }
    private static Qc findQc(Context c,String dataset)throws IOException {var candidates=new ArrayList<Qc>();for(Path dir:c.dependencyDirectories().values())if(Files.isRegularFile(dir.resolve("manifest.yaml"))){var m=StudyYaml.loadArtifact(dir.resolve("manifest.yaml"));if("participant_qc".equals(m.get("operation_type"))&&c.operation().get("hypothesis").equals(m.get("hypothesis"))&&dataset.equals(map(m.get("details")).get("dataset_id"))&&"complete".equals(m.get("state")))candidates.add(new Qc(dir,m));}if(candidates.size()!=1)throw new IOException("One completed participant_qc producer for "+dataset+" is required");return candidates.get(0);}
    private void need(String key,String reason){String id="subgroup-interaction:"+key;if(needs.stream().noneMatch(n->n.key().equals(id)))needs.add(StudyNeed.review(id,"Resolve the registered subgroup interaction prerequisite",reason,true));}
    private static String mapped(Map<String,Object> columns,String key){return text(map(columns.get("covariates")).getOrDefault(key,map(columns.get("phenotypes")).getOrDefault(key,key)));}
    private static String reference(Map<String,Object> g){return g.containsKey("cutoff")?"criterion_not_met":text(list(g.get("levels")).get(0));}
    private static String comparison(Map<String,Object> g){return g.containsKey("cutoff")?"criterion_met":text(list(g.get("levels")).get(1));}
    private static int label(Map<String,Object> g,String raw){if(missing(raw))return -1;if(!g.containsKey("cutoff"))return raw.equals(reference(g))?0:raw.equals(comparison(g))?1:-1;double value;try{value=finite(raw);}catch(IllegalArgumentException bad){return -1;}double cutoff=((Number)g.get("cutoff")).doubleValue();boolean yes=switch(text(g.get("comparison"))){case "greater_than"->value>cutoff;case "greater_than_or_equal"->value>=cutoff;case "less_than"->value<cutoff;case "less_than_or_equal"->value<=cutoff;default->throw new IllegalArgumentException("Unknown subgroup comparison");};return yes?1:0;}
    private double binary(String raw){String value=raw.strip();String yes=text(association.getOrDefault("case_value",1)),no=text(association.getOrDefault("control_value",0));if(code(value,yes))return 1;if(code(value,no))return 0;throw new IllegalArgumentException("Binary coding must match the registered case/control values");}
    private static boolean code(String a,String b){try{return Double.parseDouble(a)==Double.parseDouble(b);}catch(NumberFormatException bad){return a.equals(b);}}
    private static boolean missing(String raw){return Set.of("",".","na","n/a","nan","null","inf","-inf","infinity","-infinity").contains(raw.strip().toLowerCase(java.util.Locale.ROOT));}
    private static double finite(String raw){try{double value=Double.parseDouble(raw);if(!Double.isFinite(value))throw new NumberFormatException();return value;}catch(NumberFormatException bad){throw new IllegalArgumentException("Expected finite numeric value",bad);}}
    private static double number(Map<String,Object> p,String field,double fallback){return p.get(field) instanceof Number n?n.doubleValue():fallback;}
    static void validateSupplied(List<String> header,String[] row)throws IOException {
        if(row[header.indexOf("reference_level")].equals(row[header.indexOf("comparison_level")]))throw new IOException("Supplied interaction contrast levels must differ");
        if(!missing(row[header.indexOf("p_value")]))try{finite(row[header.indexOf("beta")]);if(!(finite(row[header.indexOf("se")])>0))throw new IllegalArgumentException("Invalid SE");}catch(IllegalArgumentException invalid){throw new IOException("Estimable supplied interactions require a finite signed coefficient and positive SE",invalid);}
        for(String field:List.of("n","n_reference","n_comparison","n_cases_reference","n_cases_comparison","parameters","iterations","mac_reference","mac_comparison","reference_beta","reference_se","comparison_beta","comparison_se","ci_lower","ci_upper","log_p_value","statistic","df"))if(header.contains(field)&&!missing(row[header.indexOf(field)])) {
            double value;try{value=finite(row[header.indexOf(field)]);}catch(IllegalArgumentException invalid){throw new IOException("Invalid supplied interaction "+field,invalid);}
            if((field.startsWith("n")||Set.of("parameters","iterations","mac_reference","mac_comparison").contains(field))&&value<0||Set.of("n","n_reference","n_comparison","n_cases_reference","n_cases_comparison","parameters","iterations").contains(field)&&value!=Math.rint(value)||field.endsWith("_se")&&value<=0||field.equals("log_p_value")&&value>0)throw new IOException("Invalid supplied interaction "+field);
        }
        if(header.contains("status")&&Set.of("not_estimable","waiting_for_input","failed").contains(row[header.indexOf("status")])&&!missing(row[header.indexOf("p_value")]))throw new IOException("Unavailable supplied interaction rows cannot carry an estimable P value");
        if(header.contains("in_testing_family")){String flag=row[header.indexOf("in_testing_family")];if(!Set.of("true","false").contains(flag))throw new IOException("Supplied interaction family membership must be true/false");if(flag.equals("false")&&!missing(row[header.indexOf("p_value")]))throw new IOException("An excluded supplied interaction cannot contribute a P value to the registered primary interaction family");}
    }
}
