/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyOperationExecutor.Result;
import org.jlinalg.study.StudyOperationExecutor.State;
import org.jlinalg.study.StudySuppliedResultPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudySuppliedLiteratureTest {
    @TempDir Path root;
    Path source;Map<String,Object> document,definition;
    static final String QUERY="(TITLE_ABS:\"RNA\") AND TITLE_ABS:\"BMI\"";
    @BeforeEach void setup()throws Exception {
        source=root.resolve("literature.json");
        var reference=new LinkedHashMap<String,Object>(Map.of("id","paper","doi","10.5555/synthetic-literature","pmid","123","title","Synthetic public reference","authors","A Author","year",2025,"journal","Synthetic journal","abstract_text","A supplied abstract discusses RNA regulation and a quantitative phenotype with explicit context and uncertainty. PRIVATE_FULL_ABSTRACT_SENTINEL","notes","PRIVATE_REFERENCE_NOTES"));
        document=new LinkedHashMap<>(Map.of("schema_version",1,"public_metadata",true,"notes","PRIVATE_SEARCH_NOTES","references",List.of(reference),"queries",List.of(query("complete","complete",0,List.of()))));save();
        definition=new LinkedHashMap<>(Map.of("id","papers","kind","literature_search","role","primary","reported_status","complete","files",Map.of("results","literature.json"),"scope",Map.of("phenotype","BMI"),"method",Map.of("name","External bibliography","parameters",Map.of("private_note","PRIVATE_METHOD_NOTES"))));
    }
    private Map<String,Object> query(String status,String coverage,int hits,List<String> references){var q=new LinkedHashMap<String,Object>(Map.of("id","field","provider","Europe PMC","query",QUERY,"search_strategy","field_background","phenotype","BMI","status",status,"coverage",coverage,"hit_count",hits,"references",references,"searched_at","2025-10-01T00:00:00Z"));q.put("reuse_rationale","Explicitly reuse this source's frozen search date in the synthetic exercise");return q;}
    private void save()throws Exception{StudyPublicEvidence.write(source,document);}
    private Context imported(String name)throws Exception {
        var d=StudySuppliedResultPolicy.resolve(Map.of("supplied_results",List.of(definition))).get(0);
        return new Context(root,Files.createDirectory(root.resolve(name)),Map.of("type","contribution".equals(d.get("role"))?"supplied_contribution":"literature_search","hypothesis","H","datasets",List.of(),"supplied_result_id",d.get("id")),study(),Map.of(),Map.of());
    }
    private Map<String,Object> study(){return Map.of("resources",Map.of("downloads","automatic"),"datasets",List.of(Map.of("id","d1","type","transcriptomics","species","human")),"hypotheses",List.of(Map.of("id","H","outcome","BMI","analysis",Map.of("supplied_results",List.of(definition),"bioinformatics",Map.of("literature_terms",List.of("RNA"),"max_pages",1)))));}
    private Context contribution(String name)throws Exception{definition.put("role","contribution");definition.put("reported_status","partial");definition.put("provenance",Map.of("results_sha256",FollowupSupport.hash(source)));return imported(name);}
    private void seal(Context c,Result result)throws Exception {StudyTestSources.seal(c.outputDirectory(),"supplied_contribution","provided","H");var m=StudyYaml.loadArtifact(c.outputDirectory().resolve("manifest.yaml"));m.put("state",result.state()==State.COMPLETE?"complete":"waiting_for_input");m.put("details",result.details());StudyPublicEvidence.write(c.outputDirectory().resolve("manifest.yaml"),m);}
    private Context downstream(Context c)throws Exception {
        Path harmonize=Files.createTempDirectory(root,"harmonize-");Files.createDirectory(harmonize.resolve("standard-results"));Files.writeString(harmonize.resolve("standard-results/d1.tsv"),"feature_id\tp_value\nbest\t1e-9\nnull\t.5\n");Files.writeString(harmonize.resolve("feature-annotation.tsv"),"feature_id\tgene_symbol\tgene_id\tchromosome\tstart\tend\tgenome_build\tmapping_status\nbest\tGENE1\tENSG1\t1\t100\t103\tGRCh38\tlocated\n");StudyTestSources.seal(harmonize,"validate_harmonize","harmonize","H");
        return new Context(root,Files.createTempDirectory(root,"native-"),Map.of("type","literature_search","hypothesis","H","datasets",List.of("d1")),study(),Map.of(),Map.of("harmonize",harmonize,"provided",c.outputDirectory()));
    }
    @Test void externalReferencesEnterIndexedCatalogWithoutPromotingTheirProvenanceOrNotes()throws Exception {
        String hash=FollowupSupport.hash(source);var c=imported("primary");var result=new StudySuppliedResults().execute(c);assertEquals(State.COMPLETE,result.state());assertEquals(hash,FollowupSupport.hash(source));assertEquals(hash,FollowupSupport.hash(c.outputDirectory().resolve("supplied/results.json")));
        var indexed=StudyPublicEvidence.map(org.jlinalg.study.StudyEvidenceIndex.keyFindings(c.outputDirectory(),"literature_search"));assertTrue(indexed.containsKey("evidence"));var catalog=StudyReferenceCatalog.fromEvidence(Map.of("H.literature_search",Map.of("operation_type","literature_search","key_findings",indexed,"manifest","local")));var ref=StudyReferenceCatalog.lookup(catalog,"pmid:123");assertEquals("10.5555/synthetic-literature",ref.get("doi"));assertEquals("source_reported_bounded_search_completed",StudyPublicEvidence.map(StudyPublicEvidence.list(StudyPublicEvidence.map(catalog.get("search_coverage")).get("strategies")).get(0)).get("status"));
        String payload=StudyReferenceCatalog.publicMetadata(ref).toString();assertTrue(payload.contains("caller_supplied"));for(String forbidden:List.of("PRIVATE_SEARCH_NOTES","PRIVATE_REFERENCE_NOTES","PRIVATE_METHOD_NOTES","PRIVATE_FULL_ABSTRACT_SENTINEL","supplied/results"))assertFalse(payload.contains(forbidden),forbidden);
        assertEquals("literal_substring_of_caller_supplied_abstract_not_authenticated",ref.get("excerpt_status"));
    }
    @Test void bibliographyOnlyProvidesCitationsWithoutInventingSearchCoverage()throws Exception {document.remove("queries");save();var data=StudySuppliedLiterature.normalize(source,definition);var catalog=StudyReferenceCatalog.build(StudyPublicEvidence.list(data.get("evidence")));assertFalse(StudyReferenceCatalog.lookup(catalog,"pmid:123").isEmpty());assertEquals("additional_search_or_input_required",StudyPublicEvidence.map(catalog.get("search_coverage")).get("status"));}
    @Test void completeFourStrategyExternalCoverageIsSourceReportedRatherThanNative()throws Exception {var queries=new ArrayList<Object>();for(String strategy:StudyIntroduction.SEARCH_STRATEGIES){var q=query("complete","complete",0,List.of());q.put("id",strategy);q.put("search_strategy",strategy);queries.add(q);}document.put("queries",queries);save();var catalog=StudyReferenceCatalog.build(StudyPublicEvidence.list(StudySuppliedLiterature.normalize(source,definition).get("evidence")));assertEquals("source_reported_coverage_complete_scientific_review_required",StudyPublicEvidence.map(catalog.get("search_coverage")).get("status"));}
    @Test void completedExactExternalQuerySuppressesOnlyItsNativeRequest()throws Exception {
        var c=contribution("contribution");var result=new StudySuppliedResults().execute(c);seal(c,result);var calls=new ArrayList<String>();var nativeContext=downstream(c);
        var nativeResult=new StudyBioinformatics(new StudyPublicEvidence(uri->{calls.add(uri.toString());return new StudyPublicEvidence.Reply(200,"{\"hitCount\":0,\"resultList\":{\"result\":[]}}");})).execute(nativeContext);
        assertEquals(State.COMPLETE,nativeResult.state(),nativeResult.details().toString());assertEquals(6,calls.size());assertEquals(1,StudyPublicEvidence.map(nativeResult.details().get("supplied_search_reuse")).get("reused_queries"));var evidence=StudyPublicEvidence.list(nativeResult.details().get("evidence"));assertEquals(1,evidence.stream().map(StudyPublicEvidence::map).filter(q->"exact_scope_query_reused_without_native_request".equals(q.get("reuse_disposition"))).count());
    }
    @Test void partialExportRetainsReferencesButNeverSuppressesNativeRetrieval()throws Exception {document.put("queries",List.of(query("complete","selected_only",2,List.of("paper"))));save();var c=contribution("partial");var result=new StudySuppliedResults().execute(c);seal(c,result);var context=downstream(c);var reuse=StudyLiteratureReuse.load(context);assertTrue(reuse.take("","","BMI","field_background",QUERY,Map.of()).isEmpty());assertEquals(0,reuse.report().get("reused_queries"));assertFalse(reuse.bibliography().isEmpty());}
    @Test void exactQueryCannotBeReusedAcrossPhenotypeGeneFeatureOrCohortScope()throws Exception {var c=contribution("scoped");var result=new StudySuppliedResults().execute(c);seal(c,result);var reuse=StudyLiteratureReuse.load(downstream(c));assertTrue(reuse.take("","","other","field_background",QUERY,Map.of()).isEmpty());assertTrue(reuse.take("best","GENE1","BMI","field_background",QUERY,Map.of()).isEmpty());assertTrue(reuse.take("","","BMI","field_background",QUERY,Map.of("cohort_id","A")).isEmpty());assertFalse(reuse.take("","","BMI","field_background",QUERY,Map.of()).isEmpty());}
    @Test void invalidSourceHashRemainsUnavailableAndCannotBecomeANegativeSearch()throws Exception {var c=contribution("bad-hash");definition.put("provenance",Map.of("results_sha256","0".repeat(64)));c=imported("bad-hash-current");var result=new StudySuppliedResults().execute(c);assertEquals(State.WAITING_FOR_INPUT,result.state());assertFalse(Files.exists(c.outputDirectory().resolve("literature-search.yaml")));}
    @Test void changedCanonicalSearchSnapshotFailsItsProducerHash()throws Exception {var c=contribution("tamper");var result=new StudySuppliedResults().execute(c);seal(c,result);Files.writeString(c.outputDirectory().resolve("literature-search.yaml"),"changed\n");var context=downstream(c);assertThrows(java.io.IOException.class,()->StudyLiteratureReuse.load(context));}
    @Test void aDifferentProviderOrChangedRegisteredDigestCannotSuppressNativeWork()throws Exception {
        var q=query("complete","complete",0,List.of());q.put("provider","Other public database");document.put("queries",List.of(q));save();var c=contribution("other-provider");var result=new StudySuppliedResults().execute(c);seal(c,result);assertTrue(StudyLiteratureReuse.load(downstream(c)).take("","","BMI","field_background",QUERY,Map.of()).isEmpty());
        definition.put("provenance",Map.of("results_sha256","0".repeat(64)));var reuse=StudyLiteratureReuse.load(downstream(c));assertTrue(reuse.needs().toString().contains("currently registered source hash"));
    }
    @Test void rawProvenanceAndWholeAbstractNeverEnterTheEditorialPayload()throws Exception {
        var c=imported("payload");new StudySuppliedResults().execute(c);var indexed=StudyPublicEvidence.map(org.jlinalg.study.StudyEvidenceIndex.keyFindings(c.outputDirectory(),"literature_search"));var evidence=Map.of("H.literature_search",Map.<String,Object>of("operation_type","literature_search","state","complete","manifest","LOCAL_PRIVATE_PATH","key_findings",indexed));
        var revision=Map.<String,Object>of("title","Synthetic citation reuse","sections",List.of(Map.of("id","intro","section","introduction","text","A supplied reference [[cite:pmid:123]].","references",List.of("pmid:123"))));
        String payload=StudyAssistance.json(StudyEditorialReview.payload(revision,evidence));for(String forbidden:List.of("PRIVATE_SEARCH_NOTES","PRIVATE_REFERENCE_NOTES","PRIVATE_METHOD_NOTES","PRIVATE_FULL_ABSTRACT_SENTINEL","LOCAL_PRIVATE_PATH","results_sha256"))assertFalse(payload.contains(forbidden),forbidden);
    }
    @Test void untypedEmptyAndForgedNativeEvidenceRecordsAreRejected()throws Exception {
        document.clear();document.put("evidence",List.of(Map.of("articles",List.of(Map.of("doi","10.5555/fake","excerpt_status","verified_literal_substring_of_normalized_abstract")))));save();assertThrows(java.io.IOException.class,()->StudySuppliedLiterature.normalize(source,definition));document.clear();document.put("schema_version",1);document.put("public_metadata",true);save();assertThrows(java.io.IOException.class,()->StudySuppliedLiterature.normalize(source,definition));
    }
    @Test void partialPrimaryCannotSilentlyReplaceTheWholeLiteratureStage()throws Exception {definition.put("reported_status","partial");assertTrue(assertThrows(IllegalArgumentException.class,()->StudySuppliedResultPolicy.resolve(Map.of("supplied_results",List.of(definition)))).getMessage().contains("role: contribution"));}
    @Test void callerExcerptCannotMasqueradeAsAuthenticatedCatalogPassage()throws Exception {var catalog=StudyReferenceCatalog.build(StudyPublicEvidence.list(StudySuppliedLiterature.normalize(source,definition).get("evidence")));var ref=StudyReferenceCatalog.lookup(catalog,"pmid:123");var revision=Map.<String,Object>of("sections",List.of(),"finding_review",Map.of("mode","review","passages",List.of(Map.of("id","anchor","reference","pmid:123","quote",ref.get("excerpt"),"location","Supplied abstract"))));assertTrue(assertThrows(java.io.IOException.class,()->StudyFindingDiscussion.assess(root,source,null,false,revision,Map.of(),catalog)).getMessage().contains("verified retrieved abstract"));}
    @Test void nativeAbstractSupersedesCallerExcerptWhileRetainingBothMetadataOrigins()throws Exception {var external=StudySuppliedLiterature.normalize(source,definition);var queries=new ArrayList<Object>(StudyPublicEvidence.list(external.get("evidence")));var nativeArticle=StudyLiterature.abstractEvidence(Map.of("pmid","123","abstractText","Native independently retrieved abstract text."),"","","queries/native");queries.add(Map.of("articles",List.of(nativeArticle),"retrieval",Map.of("response_sha256","source")));var ref=StudyReferenceCatalog.lookup(StudyReferenceCatalog.build(queries),"pmid:123");assertEquals("verified_literal_substring_of_normalized_abstract",ref.get("excerpt_status"));assertEquals("queries/native",ref.get("source_audit"));assertEquals(2,StudyPublicEvidence.list(ref.get("metadata_origins")).size());}
    @Test void malformedOrUnacknowledgedRecordsAndSelectedCompleteClaimsAreRejected()throws Exception {document.put("public_metadata",false);save();assertThrows(java.io.IOException.class,()->StudySuppliedLiterature.normalize(source,definition));document.put("public_metadata",true);document.put("queries",List.of(query("complete","complete",2,List.of("paper"))));save();assertThrows(java.io.IOException.class,()->StudySuppliedLiterature.normalize(source,definition));}
    @Test void unknownReferenceLinksAndDuplicateQueryScopesAreRejected()throws Exception {document.put("queries",List.of(query("partial","unknown",1,List.of("absent"))));save();assertThrows(java.io.IOException.class,()->StudySuppliedLiterature.normalize(source,definition));var a=query("complete","complete",0,List.of());var b=new LinkedHashMap<>(a);b.put("id","second");document.put("queries",List.of(a,b));save();assertThrows(java.io.IOException.class,()->StudySuppliedLiterature.normalize(source,definition));}
    private void literalCount(String token,boolean json)throws Exception {
        var q=query("partial","unknown",0,List.of());document.put("queries",List.of(q));
        String content=json?StudyAssistance.json(document):new org.yaml.snakeyaml.Yaml().dump(document);
        content=json?content.replaceAll("\\\"hit_count\\\"\\s*:\\s*0", "\"hit_count\":"+token):content.replace("hit_count: 0","hit_count: "+token);
        Files.writeString(source,content);
    }
    @Test void sourceCountFractionsUnderflowAndOverflowCannotBecomeCompletedEmptySearches()throws Exception {
        for(boolean json:List.of(false,true))for(String token:List.of("1.0e-999","-1.0e-999","0.999999999999999999999999","1.000000000000000000001","9223372036854775808","9.223372036854775808e18")){
            literalCount(token,json);String before=FollowupSupport.hash(source);assertThrows(java.io.IOException.class,()->StudySuppliedLiterature.normalize(source,definition),token);assertEquals(before,FollowupSupport.hash(source));
        }
    }
    @Test void validIntegerTokensRetainTheirExactValueBeyondDoublePrecision()throws Exception {
        for(boolean json:List.of(false,true))for(String token:List.of("0","0.0","1.0","9.007199254740993e15","9007199254740993.0","9223372036854775807.0")){
            literalCount(token,json);var normalized=StudySuppliedLiterature.normalize(source,definition);var row=StudyPublicEvidence.list(normalized.get("evidence")).stream().map(StudyPublicEvidence::map).filter(r->"literature".equals(r.get("kind"))).findFirst().orElseThrow();assertEquals(new java.math.BigDecimal(token).longValueExact(),row.get("hit_count"));assertEquals(false,row.get("query_complete"));
        }
    }
    @Test void nonfiniteAndQuotedCountTokensStayInvalid()throws Exception {
        for(String token:List.of(".nan",".inf","-.inf","'0'")){literalCount(token,false);assertThrows(java.io.IOException.class,()->StudySuppliedLiterature.normalize(source,definition));}
        literalCount("\"0\"",true);assertThrows(java.io.IOException.class,()->StudySuppliedLiterature.normalize(source,definition));
    }
    @Test void exactSourceReaderRetainsAliasDuplicateAndUnsafeTagGuards()throws Exception {
        for(String text:List.of("schema_version: 1\nschema_version: 1\npublic_metadata: true\n", "schema_version: 1\npublic_metadata: true\nreferences: &refs []\nqueries: *refs\n", "!!java.lang.ProcessBuilder {}\n")){
            Files.writeString(source,text);assertThrows(java.io.IOException.class,()->StudySuppliedLiterature.normalize(source,definition));
        }
    }
    @Test void numericPublicationYearsCannotRoundIntoAnAcceptedCitationYear()throws Exception {
        String base=StudyAssistance.json(document);for(String value:List.of("2025.000000000000000001","2024.999999999999999999","9223372036854775808")){
            Files.writeString(source,base.replaceAll("\\\"year\\\"\\s*:\\s*2025","\"year\":"+value));assertThrows(java.io.IOException.class,()->StudySuppliedLiterature.normalize(source,definition));
        }
    }
    @Test void exactIntegerAndStringPublicationYearsRemainUsable()throws Exception {
        String base=StudyAssistance.json(document);for(String value:List.of("2025.0","2.025e3","\"2025\"")){
            Files.writeString(source,base.replaceAll("\\\"year\\\"\\s*:\\s*2025","\"year\":"+value));var normalized=StudySuppliedLiterature.normalize(source,definition);var bibliography=StudyPublicEvidence.list(normalized.get("evidence")).stream().map(StudyPublicEvidence::map).filter(r->"supplied_bibliography".equals(r.get("kind"))).findFirst().orElseThrow();assertEquals("2025",StudyPublicEvidence.map(StudyPublicEvidence.list(bibliography.get("articles")).get(0)).get("pubYear"));
        }
    }
    @Test void literatureContributionsDoNotScheduleOrContaminateMR()throws Exception {contribution("planning-only");var study=new LinkedHashMap<String,Object>(study());study.put("schema_version",1);study.put("project",Map.of("id","reuse","title","Synthetic reuse","description","No real scientific analysis"));study.put("datasets",List.of());var h=new LinkedHashMap<>(StudyPublicEvidence.map(StudyPublicEvidence.list(study.get("hypotheses")).get(0)));h.put("description","Synthetic contribution planning fixture");var a=new LinkedHashMap<>(StudyPublicEvidence.map(h.get("analysis")));a.put("mode","manual");a.put("goals",List.of("literature_search"));h.put("analysis",a);study.put("hypotheses",List.of(h));Path request=root.resolve("study.json");StudyPublicEvidence.write(request,study);var plan=org.jlinalg.study.StudyPlanner.compile(org.jlinalg.study.StudyResolver.resolve(request));var types=StudyPublicEvidence.list(plan.document().get("operations")).stream().map(StudyPublicEvidence::map).map(op->op.get("type")).toList();assertTrue(types.contains("literature_search"));assertFalse(types.contains("mendelian_randomization"));var c=new Context(root,Files.createDirectory(root.resolve("mr")),Map.of("type","mendelian_randomization","hypothesis","H"),study,Map.of(),Map.of());assertTrue(StudyMrContributions.load(c).records().isEmpty());}
}
