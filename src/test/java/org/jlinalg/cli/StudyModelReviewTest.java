/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.study.StudyOperationExecutor.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyModelReviewTest {
    @TempDir Path root;
    private final List<String> urls=new ArrayList<>();
    private Map<String,Object> dataset(String type){return new LinkedHashMap<>(Map.of("id","d","type",type,"files",Map.of("matrix","PRIVATE_missing_matrix.tsv","phenotype","PRIVATE_missing_phenotype.tsv"),"metadata",Map.of("notes","PRIVATE_notes")));}
    private Map<String,Object> hypothesis(Map<String,Object> options){return new LinkedHashMap<>(Map.of("id","H","outcome","PRIVATE_outcome_column","covariates",List.of("PRIVATE_age","PRIVATE_sex"),"analysis",Map.of("association",options,"bioinformatics",Map.of("phenotype_terms",List.of("Synthetic public trait")))));}
    private Context context(Map<String,Object> h,Map<String,Object> d,boolean online)throws Exception {return new Context(root,Files.createTempDirectory(root,"review-"),Map.of("type","association","dataset_id","d","hypothesis","H"),Map.of("datasets",List.of(d),"hypotheses",List.of(h),"resources",Map.of("network",online?"online":"offline","downloads","automatic")),Map.of(),Map.of());}
    private StudyPublicEvidence client(){return new StudyPublicEvidence(uri->{urls.add(URLDecoder.decode(uri.toString(),StandardCharsets.UTF_8));return new StudyPublicEvidence.Reply(200,"{\"hitCount\":1,\"resultList\":{\"result\":[{\"id\":\"123\",\"source\":\"MED\",\"doi\":\"10.5555/model-fixture\",\"title\":\"Synthetic method record; not a model appraisal\",\"firstPublicationDate\":\"2026-10-26\"}]}}");});}
    private Map<String,Object> execute(Map<String,Object> h,Map<String,Object> d,StudyPublicEvidence evidence,boolean online)throws Exception {
        var c=context(h,d,online);var result=new StudyModelReview(evidence).execute(c,h,d);assertEquals(State.WAITING_FOR_INPUT,result.state());assertFalse(list(result.details().get("needs")).isEmpty());assertFalse(Files.exists(c.outputDirectory().resolve("association.tsv")));assertFalse(Files.exists(root.resolve("PRIVATE_missing_matrix.tsv")));var review=map(result.details().get("model_review"));assertEquals("association_model_required",review.get("error"));assertEquals("none",review.get("selected_model"));assertEquals(false,review.get("association_executed"));assertTrue(Files.exists(c.outputDirectory().resolve("model-review.yaml")));return review;
    }
    private Map<String,Map<String,Object>> candidates(Map<String,Object> review){var result=new LinkedHashMap<String,Map<String,Object>>();for(Object raw:list(review.get("candidate_models"))){var row=map(raw);result.put(text(row.get("model")),row);assertEquals(false,row.get("automatically_selected"));assertEquals("not_appraised",row.get("literature_support"));}return result;}
    @Test void quantitativeExpressionReviewOffersActualModelNamesAndTwoRecentSearches()throws Exception {
        var review=execute(hypothesis(Map.of("family","gaussian")),dataset("expression"),client(),true);var models=candidates(review);assertEquals(Set.of("ols","lmm"),models.keySet());assertEquals("matches_declared_context_requires_review",models.get("ols").get("context_status"));assertTrue(text(models.get("lmm").get("requires")).contains("REML"));assertEquals(2,urls.size());assertTrue(urls.get(1).contains("gene expression")&&urls.get(1).contains("linear regression"));assertTrue(urls.stream().allMatch(s->s.contains("sort_date:y")&&s.contains("NOT PUB_TYPE:Review")));assertFalse(StudyAssistance.json(review).contains("PRIVATE_"));
    }
    @Test void relatedParticipantsChangeDesignSearchAndCandidateContextWithoutDisclosingTheirSource()throws Exception {
        var d=dataset("expression");d.put("files",Map.of("matrix","PRIVATE_matrix","phenotype","PRIVATE_pheno","grm","PRIVATE_grm"));d.put("metadata",Map.of("related_participants",true,"repeated_measures",true));var review=execute(hypothesis(Map.of("family","gaussian","formula","PRIVATE_y ~ PRIVATE_x + (1|PRIVATE_group)")),d,client(),true);var models=candidates(review);assertEquals("matches_declared_context_requires_review",models.get("lmm").get("context_status"));assertEquals("conditional_alternative_requires_review",models.get("ols").get("context_status"));assertTrue(urls.get(1).contains("relatedness")&&urls.get(1).contains("repeated measures"));assertTrue(urls.stream().noneMatch(s->s.contains("PRIVATE_")));assertFalse(StudyAssistance.json(review).contains("PRIVATE_"));assertEquals(false,map(review.get("review_context")).get("independence_established"));
    }
    @Test void binaryGwasUsesBinaryCandidatesAndKeepsCalibrationConditional()throws Exception {
        var d=dataset("gwas");d.put("metadata",Map.of("related_participants",true));var review=execute(hypothesis(Map.of("family","binomial","exact_strata_column","PRIVATE_strata")),d,client(),true);var models=candidates(review);assertEquals(Set.of("glm","glmm"),models.keySet());assertEquals("matches_declared_context_requires_review",models.get("glmm").get("context_status"));assertTrue(urls.get(1).contains("genome-wide association")&&urls.get(1).contains("logistic regression")&&urls.get(1).contains("conditional logistic"));assertTrue(text(models.get("glmm").get("requires")).contains("calibrated"));assertFalse(StudyAssistance.json(review).contains("PRIVATE_"));
    }
    @Test void absentFamilyNeverGetsAnInferredGaussianDefault()throws Exception {
        var review=execute(hypothesis(Map.of()),dataset("proteomics"),client(),true);assertEquals(Set.of("ols","lmm","glm","glmm","cox"),candidates(review).keySet());assertEquals("unknown",map(review.get("review_context")).get("declared_endpoint"));assertEquals(false,map(review.get("review_context")).get("family_default_applied"));assertTrue(candidates(review).values().stream().allMatch(row->row.get("context_status").equals("conditional_alternative_requires_review")));
    }
    @Test void survivalDeclarationRetainsItsEndpointAndDependenceLimitation()throws Exception {
        var d=dataset("metabolomics");d.put("metadata",Map.of("repeated_measures",true));var review=execute(hypothesis(Map.of("survival",Map.of("time_column","PRIVATE_time","event_column","PRIVATE_event"))),d,client(),true);var models=candidates(review);assertEquals(Set.of("cox"),models.keySet());assertEquals("conditional_alternative_requires_review",models.get("cox").get("context_status"));assertTrue(text(models.get("cox").get("requires")).contains("frailty"));assertTrue(urls.get(1).contains("survival analysis")&&urls.get(1).contains("metabolomics"));assertFalse(StudyAssistance.json(review).contains("PRIVATE_"));
    }
    @Test void countAndPositiveFamiliesGetTheirOwnSearchTerms()throws Exception {
        for(String family:List.of("poisson","negative-binomial","gamma")){
            urls.clear();var review=execute(hypothesis(Map.of("family",family)),dataset("ewas"),client(),true);assertEquals(Set.of("glm","glmm"),candidates(review).keySet());assertTrue(urls.get(1).contains(family.equals("gamma")?"gamma regression":"count data"));
        }
    }
    @Test void exactDuplicatePapersRetainBothSearchLinksWithoutBecomingTwoAppraisals()throws Exception {
        var review=execute(hypothesis(Map.of("family","gaussian")),dataset("expression"),client(),true);assertEquals(1,list(review.get("literature_candidates")).size());var paper=map(list(review.get("literature_candidates")).get(0));assertEquals(List.of("model-search-1","model-search-2"),paper.get("search_ids"));assertEquals(2,list(paper.get("source_audits")).size());assertEquals("not_appraised",paper.get("model_support"));
    }
    @Test void offlinePolicyStillRetainsQueriesAndBlockingNeedWithoutTransport()throws Exception {
        var review=execute(hypothesis(Map.of("family","gaussian")),dataset("expression"),new StudyPublicEvidence(uri->{fail("Offline policy must block transport");return null;}),false);assertEquals("offline_policy",review.get("search_status"));assertEquals(2,list(review.get("searches")).size());assertTrue(list(review.get("literature_candidates")).isEmpty());
    }
    @Test void missingOutcomeKeepsAllConditionalCandidatesButMakesNoSearch()throws Exception {
        var h=new LinkedHashMap<String,Object>(Map.of("id","H","analysis",Map.of()));var review=execute(h,dataset("generic"),new StudyPublicEvidence(uri->{fail("No public phenotype label");return null;}),true);assertEquals("outcome_required",review.get("search_status"));assertTrue(list(review.get("searches")).isEmpty());assertEquals(5,candidates(review).size());
    }
    @Test void conflictingEndpointDeclarationsRemainUnresolved()throws Exception {
        var review=execute(hypothesis(Map.of("family","binomial","survival",Map.of())),dataset("expression"),client(),true);assertEquals("conflicting_endpoint_declarations",map(review.get("review_context")).get("declared_endpoint"));assertEquals(5,candidates(review).size());assertTrue(candidates(review).values().stream().noneMatch(row->row.get("context_status").equals("matches_declared_context_requires_review")));
    }
    @Test void malformedProviderMetadataCannotStandForAnEmptySearch()throws Exception {
        for(String body:List.of("{}","{\"hitCount\":-1,\"resultList\":{\"result\":[]}}","{\"hitCount\":0,\"resultList\":{\"result\":[\"bad\"]}}")){
            var review=execute(hypothesis(Map.of("family","gaussian")),dataset("expression"),new StudyPublicEvidence(uri->new StudyPublicEvidence.Reply(200,body)),true);assertEquals("invalid_response_metadata",review.get("search_status"));assertTrue(list(review.get("literature_candidates")).isEmpty());assertTrue(list(review.get("searches")).stream().allMatch(raw->Boolean.TRUE.equals(map(raw).get("search_limited"))));
        }
    }
    @Test void invalidLabelsFailBeforeAnyPublicRequest()throws Exception {
        for(Object invalid:List.of("\nPRIVATE", "x".repeat(151),"\"\\",42)){
            var h=hypothesis(Map.of());h.put("analysis",Map.of("bioinformatics",Map.of("phenotype_terms",List.of(invalid))));var d=dataset("expression");var c=context(h,d,true);assertThrows(java.io.IOException.class,()->new StudyModelReview(new StudyPublicEvidence(uri->{fail("Invalid label must not be requested");return null;})).execute(c,h,d));
        }
    }
    @Test void unidentifiedRecordsAreRetainedWithoutCollapsingOnProviderName()throws Exception {
        var review=execute(hypothesis(Map.of("family","gaussian")),dataset("expression"),new StudyPublicEvidence(uri->new StudyPublicEvidence.Reply(200,"{\"hitCount\":2,\"resultList\":{\"result\":[{\"source\":\"MED\",\"title\":\"First unidentified record\"},{\"source\":\"MED\",\"title\":\"Second unidentified record\"}]}}")),true);assertEquals(4,list(review.get("literature_candidates")).size());assertTrue(list(review.get("literature_candidates")).stream().allMatch(raw->"identifier_unavailable".equals(map(raw).get("identity_status"))));
    }
    @Test void providerOverdeliveryRetainsTheBoundAndExplicitTruncation()throws Exception {
        var records=new ArrayList<Object>();for(int i=0;i<25;i++)records.add(Map.of("id",Integer.toString(100+i),"source","MED","title","Synthetic candidate "+i));String body=StudyAssistance.json(Map.of("hitCount",25,"resultList",Map.of("result",records)));var review=execute(hypothesis(Map.of("family","gaussian")),dataset("expression"),new StudyPublicEvidence(uri->new StudyPublicEvidence.Reply(200,body)),true);assertEquals(20,list(review.get("literature_candidates")).size());assertTrue(list(review.get("searches")).stream().allMatch(raw->Boolean.TRUE.equals(map(raw).get("search_limited"))&&Integer.valueOf(20).equals(map(raw).get("retained_records"))));
    }
    @Test void blankAndNullModelValuesUseTheSameAssociationGateAsAbsentModels()throws Exception {
        for(String value:java.util.Arrays.asList("", "   ", null)){
            var options=new LinkedHashMap<String,Object>();options.put("model",value);options.put("family","gaussian");var h=hypothesis(options);var d=dataset("expression");var c=context(h,d,false);var result=new StudyCliExecutor().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());assertEquals("association_model_required",map(result.details().get("model_review")).get("error"));assertFalse(Files.exists(c.outputDirectory().resolve("association.tsv")));
        }
    }
    @Test void nativeAssociationRequiresModelReviewBeforeQcReadsItsOutcomeOrMatrix()throws Exception {
        var h=hypothesis(Map.of());var d=dataset("expression");var original=context(h,d,false);var c=new Context(original.project(),original.outputDirectory(),Map.of("type","participant_qc","dataset_id","d","datasets",List.of("d"),"hypothesis","H"),original.study(),Map.of("operations",List.of(Map.of("type","association","dataset_id","d","hypothesis","H"))),Map.of());var result=new StudyPrefitQc().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());assertEquals("association_model_required",map(result.details().get("model_review")).get("error"));assertFalse(Files.exists(c.outputDirectory().resolve("analysis-phenotype.tsv")));
    }
    @Test void descriptiveQcDoesNotDemandNativeModelsForStandaloneConfiguredOrSuppliedWork()throws Exception {
        StringBuilder matrix=new StringBuilder("feature_id"),values=new StringBuilder("\ng1"),phenotype=new StringBuilder("id\ty\n");for(int i=0;i<8;i++){matrix.append("\tS").append(i);values.append('\t').append(i);phenotype.append("S").append(i).append('\t').append(i+1).append('\n');}Files.writeString(root.resolve("matrix.tsv"),matrix.append(values).append('\n'));Files.writeString(root.resolve("phenotype.tsv"),phenotype);
        var d=Map.<String,Object>of("id","d","type","expression","stage","processed_matrix","files",Map.of("matrix","matrix.tsv","phenotype","phenotype.tsv"));var h=Map.<String,Object>of("id","H","outcome","y","analysis",Map.of("association",Map.of("family","gaussian")));var study=Map.<String,Object>of("datasets",List.of(d),"hypotheses",List.of(h),"resources",Map.of("network","offline"));
        for(String mode:List.of("standalone","configured","supplied")){
            var downstream=new LinkedHashMap<String,Object>(Map.of("type",mode.equals("standalone")?"participant_qc":"association","hypothesis","H","dataset_id","d"));if(mode.equals("configured"))downstream.put("configuration",Map.of("command","differential"));if(mode.equals("supplied"))downstream.put("supplied_result_id","completed-association");
            var c=new Context(root,Files.createTempDirectory(root,"descriptive-"),Map.of("type","participant_qc","hypothesis","H","dataset_id","d","datasets",List.of("d")),study,Map.of("operations",List.of(downstream)),Map.of());var result=new StudyPrefitQc().execute(c);assertEquals(State.COMPLETE,result.state(),mode);assertTrue(Files.exists(c.outputDirectory().resolve("analysis-phenotype.tsv")));assertFalse(Files.exists(c.outputDirectory().resolve("model-review.yaml")));
        }
    }
}
