/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jlinalg.study.StudyNeed;
import org.jlinalg.study.StudyOperationExecutor;

/** Missing models produce source-linked proposals and a blocking scientific need. */
final class StudyModelReview {
    static final String CITATION="https://europepmc.org/RestfulWebService";
    static final String CAVEAT="Candidates use declared endpoint, assay and design context; they are proposals, not selected models or appraisals of retrieved papers. Search matches do not establish that a paper used or supports a candidate model. Independence, confounding control, sampling assumptions and calibration require scientific review. No input files were read and no association was executed. Searches retain at most 20 records each, sorted by publication date, and are not exhaustive.";
    private static final Set<String> BINARY=Set.of("binomial","quasi-binomial","probit","binomial-probit");
    private static final Set<String> COUNT=Set.of("poisson","quasi-poisson","negative-binomial");
    private static final Set<String> POSITIVE=Set.of("gamma","inverse-gaussian");
    private final StudyPublicEvidence client;
    StudyModelReview() { this(new StudyPublicEvidence()); }
    StudyModelReview(StudyPublicEvidence client) { this.client = client; }

    StudyOperationExecutor.Result execute(StudyOperationExecutor.Context context,
            Map<String, Object> hypothesis, Map<String, Object> dataset) throws IOException {
        Files.createDirectories(context.outputDirectory());
        var profile=profile(hypothesis,dataset);var searches=new ArrayList<Object>();var papers=new LinkedHashMap<String,Map<String,Object>>();
        var definitions=new LinkedHashMap<String,String>();
        if(!profile.phenotypes.isEmpty()){
            String phenotype=terms(profile.phenotypes),suffix=" NOT PUB_TYPE:Review sort_date:y";
            definitions.put("recent_phenotype_methods",phenotype+" AND (\"association study\" OR \"regression model\")"+suffix);
            if(!profile.assayTerms.isEmpty()||!profile.endpoint.equals("unknown")||profile.dependent||profile.stratified){
                String query=phenotype;if(!profile.assayTerms.isEmpty())query+=" AND "+terms(profile.assayTerms);
                query+=" AND "+methodTerms(profile);
                if(profile.dependent)query+=" AND (\"mixed model\" OR relatedness OR kinship OR \"repeated measures\" OR longitudinal)";
                if(profile.stratified)query+=" AND (\"conditional logistic\" OR stratified OR matched OR \"exact test\")";
                definitions.put("recent_assay_endpoint_design_methods",query+suffix);
            }
        }
        int index=0;
        for(var definition:definitions.entrySet()){
            String id="model-search-"+(++index),query=definition.getValue();
            var response=client.get(context,"Europe PMC","https://www.ebi.ac.uk/europepmc/webservices/rest/search?format=json&resultType=core&pageSize=20&query="+URLEncoder.encode(query,StandardCharsets.UTF_8),CITATION,
                "Retrieve recent primary model-method candidates using bounded public phenotype/assay labels and controlled endpoint/design terms. Dataset paths, sample identifiers, formula terms and covariate names are excluded. Retrieval does not select a model or establish method suitability.");
            var search=new LinkedHashMap<String,Object>();search.put("search_id",id);search.put("strategy",definition.getKey());search.put("query",query);search.put("status",response.status());search.put("search_audit",response.audit());search.put("sort","publication_date_descending");search.put("maximum_retained_records",20);search.put("exhaustive",false);
            if(response.ok()){
                var body=map(response.data());var resultList=map(body.get("resultList"));Long hits=StudyLiteratureCoverage.count(body.get("hitCount"));var records=list(resultList.get("result"));
                boolean valid=hits!=null&&resultList.get("result") instanceof List<?> && hits>=records.size() && records.stream().allMatch(r->r instanceof Map<?,?>);
                search.put("response_metadata_valid",valid);search.put("reported_hits",hits==null?"not_reported":hits);search.put("returned_records",records.size());search.put("retained_records",valid?Math.min(20,records.size()):0);search.put("search_limited",!valid||hits>Math.min(20,records.size()));
                if(!valid)search.put("status","invalid_response_metadata");
                else for(int i=0;i<Math.min(20,records.size());i++){
                    var raw=map(records.get(i));String key=text(raw.get("doi")).strip().toLowerCase(Locale.ROOT);
                    if(key.isBlank())key=text(raw.get("id")).isBlank()?id+"/unresolved-"+i:text(raw.get("source"))+"/"+text(raw.get("id"));
                    var paper=papers.computeIfAbsent(key,ignored->{var p=new LinkedHashMap<String,Object>();for(String field:List.of("id","source","title","authorString","pubYear","doi","firstPublicationDate","pubTypeList"))if(raw.containsKey(field))p.put(field,raw.get(field));p.put("identity_status",text(raw.get("doi")).isBlank()&&text(raw.get("id")).isBlank()?"identifier_unavailable":"declared_identifier_present");p.put("search_ids",new ArrayList<String>());p.put("source_audits",new ArrayList<String>());p.put("model_support","not_appraised");return p;});
                    addUnique(paper,"search_ids",id);addUnique(paper,"source_audits",response.audit());
                }
            }
            searches.add(search);
        }
        var review=new LinkedHashMap<String,Object>();review.put("schema_version",2);review.put("error","association_model_required");review.put("dataset",dataset.get("id"));review.put("search_date",LocalDate.now(ZoneOffset.UTC).toString());review.put("review_context",profile.document);
        review.put("query",definitions.isEmpty()?"":definitions.values().iterator().next());review.put("searches",searches);review.put("literature_candidates",List.copyOf(papers.values()));
        var statuses=searches.stream().map(raw->text(map(raw).get("status"))).distinct().toList();review.put("search_status",statuses.isEmpty()?"outcome_required":statuses.size()==1?statuses.get(0):"mixed_search_dispositions");
        if(!searches.isEmpty())review.put("search_audit",map(searches.get(0)).get("search_audit"));
        review.put("candidate_models",candidates(profile,searches.stream().map(raw->text(map(raw).get("search_id"))).toList()));review.put("selected_model","none");review.put("association_executed",false);review.put("interpretation",CAVEAT);review.put("search_method_citation",CITATION);
        write(context.outputDirectory().resolve("model-review.yaml"), review);
        List<String> outputs;
        try (var files = Files.walk(context.outputDirectory())) {
            outputs = files.filter(Files::isRegularFile).map(context.outputDirectory()::relativize)
                .map(path -> path.toString().replace('\\', '/')).sorted().toList();
        }
        StudyNeed need = StudyNeed.review("association-model:" + hypothesis.get("id") + ":" + dataset.get("id"),
            "Which explicit association model, outcome family and design should be used?",
            "Association requires analysis.association.model and justified model parameters. See model-review.yaml; conditional candidates and retrieved papers do not authorize a model.", true);
        return new StudyOperationExecutor.Result(StudyOperationExecutor.State.WAITING_FOR_INPUT,
            "Association blocked: explicit model required; retained model/literature review", outputs,
            Map.of("model_review", review, "needs", List.of(need.document())));
    }

    private record Profile(Map<String,Object> document,List<String> phenotypes,List<String> assayTerms,String endpoint,boolean dependent,boolean stratified) { }
    private static Profile profile(Map<String,Object> hypothesis,Map<String,Object> dataset)throws IOException {
        var analysis=map(hypothesis.get("analysis"));var options=map(analysis.get("association"));var bio=map(analysis.get("bioinformatics"));var metadata=map(dataset.get("metadata"));var files=map(dataset.get("files"));
        List<String> phenotype=labels(bio.get("phenotype_terms"),"phenotype_terms");boolean explicitPhenotype=!phenotype.isEmpty();if(phenotype.isEmpty()&&!text(hypothesis.get("outcome")).isBlank())phenotype=List.of(label(hypothesis.get("outcome")));
        String type=text(dataset.get("type")).toLowerCase(Locale.ROOT);boolean genotype=files.containsKey("genotypes")||Set.of("gwas","genomics","genotype").contains(type);
        String assay=genotype?"genetic_variation":Set.of("expression","transcriptomics","mirna","lincrna","lncrna","extracellular_rna").contains(type)?"RNA_abundance":Set.of("epigenomics","ewas").contains(type)?"epigenomic_measurement":type.equals("proteomics")?"protein_abundance":type.equals("metabolomics")?"metabolite_abundance":"unclassified";
        List<String> assayTerms=labels(bio.get("literature_terms"),"literature_terms");String assayBasis="explicit_public_literature_terms";
        if(assayTerms.isEmpty()){
            assayTerms=switch(assay){case "genetic_variation"->List.of("genome-wide association","genetic association");case "RNA_abundance"->List.of("gene expression","RNA sequencing","transcriptomics");case "epigenomic_measurement"->List.of("DNA methylation","epigenome-wide association");case "protein_abundance"->List.of("proteomics","protein abundance");case "metabolite_abundance"->List.of("metabolomics");default->List.of();};assayBasis=assayTerms.isEmpty()?"not_declared":"controlled_dataset_type_terms";
        }
        String family=text(options.get("family"));String endpoint=family.equals("gaussian")?"quantitative":BINARY.contains(family)?"binary":COUNT.contains(family)?"count":POSITIVE.contains(family)?"positive_continuous":"unknown";
        boolean survival=options.containsKey("survival"),conflict=survival&&!family.isBlank();if(survival)endpoint=conflict?"conflicting_endpoint_declarations":"time_to_event";
        var dependence=new ArrayList<String>();for(String role:List.of("grm","pedigree"))if(files.containsKey(role))dependence.add("declared_"+role+"_source");for(String flag:List.of("related_participants","repeated_measures"))if(Boolean.TRUE.equals(metadata.get(flag)))dependence.add(flag);
        if(text(options.get("formula")).contains("|"))dependence.add("declared_random_formula_terms");
        boolean stratified=options.containsKey("exact_strata_column");var doc=new LinkedHashMap<String,Object>();doc.put("public_phenotype_labels",phenotype);doc.put("phenotype_label_basis",explicitPhenotype?"explicit_public_phenotype_terms":"declared_outcome_label");doc.put("assay_class",assay);doc.put("assay_terms",assayTerms);doc.put("assay_term_basis",assayBasis);doc.put("declared_endpoint",endpoint);doc.put("declared_family",family.isBlank()?"not_declared":endpoint.equals("unknown")?"unrecognized_declared_family":conflict?"conflicts_with_survival_contract":family);doc.put("family_default_applied",false);doc.put("dependence_declarations",dependence);doc.put("independence_established",false);doc.put("conditional_strata_declared",stratified);doc.put("covariates_declared",!list(hypothesis.get("covariates")).isEmpty()||!list(options.get("covariates")).isEmpty());doc.put("private_design_values_transmitted",false);
        return new Profile(doc,phenotype,assayTerms,endpoint,!dependence.isEmpty(),stratified);
    }
    private static List<Object> candidates(Profile p,List<String> searches){
        var result=new ArrayList<Object>();boolean unknown=p.endpoint.equals("unknown")||p.endpoint.equals("conflicting_endpoint_declarations");
        if(unknown||p.endpoint.equals("quantitative")){
            result.add(candidate("ols",!p.dependent&&p.endpoint.equals("quantitative"),"Justified quantitative modeled scale, independent observations, explicit fixed effects and valid residual/variance assumptions; dependence must be resolved.",searches));
            result.add(candidate("lmm",p.dependent&&p.endpoint.equals("quantitative"),"Quantitative outcome, explicit supported random/covariance structure and aligned GRM/pedigree or random-effect design. REML is an estimation method; use model: lmm, not model: reml.",searches));
        }
        if(unknown||Set.of("binary","count","positive_continuous").contains(p.endpoint)){
            result.add(candidate("glm",!p.dependent&&!unknown,"Explicit outcome family/link, independence, sampling design, offsets and sparse-data calibration where needed. A conditional/exact route requires its separate registered assumptions.",searches));
            result.add(candidate("glmm",p.dependent&&!unknown,"Explicit non-Gaussian family/link and a supported dependence/covariance structure. Numerical availability does not establish calibrated sparse binary or rare-variant inference.",searches));
        }
        if(unknown||p.endpoint.equals("time_to_event"))result.add(candidate("cox",!p.dependent&&p.endpoint.equals("time_to_event"),"Explicit event/time/censoring definitions, proportional-hazards and independent-unit assumptions. The native study route does not supply a frailty/dependence model; use a supported configured or supplied result when required.",searches));
        return result;
    }
    private static Map<String,Object> candidate(String model,boolean contextMatched,String requires,List<String> searches){return Map.of("model",model,"context_status",contextMatched?"matches_declared_context_requires_review":"conditional_alternative_requires_review","requires",requires,"literature_search_ids",searches,"literature_support","not_appraised","automatically_selected",false);}
    private static String methodTerms(Profile p){return switch(p.endpoint){case "quantitative"->"(\"linear regression\" OR \"linear mixed model\" OR \"quantitative trait\")";case "binary"->"(\"logistic regression\" OR \"binary trait\" OR \"case control\")";case "count"->"(\"Poisson regression\" OR \"negative binomial\" OR \"count data\")";case "positive_continuous"->"(\"generalized linear model\" OR \"gamma regression\")";case "time_to_event"->"(\"Cox regression\" OR \"proportional hazards\" OR \"survival analysis\")";default->"(regression OR \"mixed model\" OR \"association study\")";};}
    private static List<String> labels(Object raw,String name)throws IOException {if(raw==null)return List.of();if(!(raw instanceof List<?> values)||values.size()>10)throw new IOException("Model-review "+name+" must be a list of at most 10 bounded public labels");var result=new LinkedHashSet<String>();for(Object value:values)result.add(label(value));return List.copyOf(result);}
    private static String label(Object raw)throws IOException {if(!(raw instanceof String value)||value.isBlank()||value.length()>150||value.chars().anyMatch(Character::isISOControl))throw new IOException("Model-review labels require nonblank bounded public text");String clean=value.replace('"',' ').replace('\\',' ').strip();if(clean.isEmpty())throw new IOException("Model-review public label is empty after query escaping");return clean;}
    private static String terms(List<String> values){return "("+String.join(" OR ",values.stream().map(value->"TITLE_ABS:\""+value+"\"").toList())+")";}
    private static void addUnique(Map<String,Object> paper,String key,String value){var values=new ArrayList<Object>(list(paper.get(key)));if(!values.contains(value))values.add(value);paper.put(key,values);}
}
