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
import java.util.Set;
import org.jlinalg.study.StudyOperationExecutor.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyLiteratureCoverageTest {
    @TempDir Path root;
    private Map<String,Object> query(String strategy){return new LinkedHashMap<>(Map.ofEntries(Map.entry("kind","literature"),Map.entry("feature_id","f1"),Map.entry("gene_symbol","G1"),Map.entry("phenotype","Synthetic public trait"),Map.entry("query","PRIVATE_QUERY_TEXT"),Map.entry("search_strategy",strategy),Map.entry("provider","Europe PMC"),Map.entry("execution_origin","externally_produced"),Map.entry("source_sha256","a".repeat(64)),Map.entry("supplied_query_id",strategy),Map.entry("searched_at","2026-10-26T00:00:00Z"),Map.entry("status","supplied_no_matches"),Map.entry("query_complete",true),Map.entry("search_limited",false),Map.entry("hit_count",0),Map.entry("articles",List.of()),Map.entry("notes","PRIVATE_SOURCE_NOTE")));}
    private DelimitedData allocation(String status,boolean selected)throws Exception {Path path=Files.createTempFile(root,"allocation-",".tsv");Files.writeString(path,"rank\tfeature_id\tselected_for_review\tretrieval_status\treview_rank\tselection_basis\tsearch_records\tsource_reported_search_records\tnative_or_legacy_search_records\n1\tf1\t"+selected+"\t"+status+"\t"+(selected?"1":"NA")+"\tprovisional_AI_impact\t"+(selected?"3\t3\t0":"0\t0\t0")+"\n");return DelimitedData.readResult(path);}
    private StudyLiteratureCoverage.Input input(String id,Map<String,Object> doc,DelimitedData table,boolean external,String state){return new StudyLiteratureCoverage.Input("H",state,external?"contribution":"primary",id,table==null?"":id+"-allocation",doc,table,external);}
    private Map<String,Object> result(StudyLiteratureCoverage coverage){return coverage.forFinding("H","f1",Map.of("gene_symbol","G1"));}
    @Test void nonselectionAndPendingAllocationAreNotNegativeSearchResults()throws Exception {
        for(String status:List.of("outside_review_budget","provisional_priority_pending")){
            var coverage=new StudyLiteratureCoverage(Set.of("H"));coverage.add(input("native",Map.of("evidence",List.of()),allocation(status,false),false,status.equals("outside_review_budget")?"complete":"waiting_for_input"));var result=result(coverage);
            assertEquals(status,map(list(result.get("native_allocations")).get(0)).get("status"));assertEquals("no_feature_search_receipts",result.get("query_evidence_state"));assertEquals(0,result.get("unique_queries"));assertEquals(false,result.get("systematic_review_established"));assertTrue(text(result.get("required_manuscript_caveat")).contains(status.equals("outside_review_budget")?"cap is not negative":"allocation is pending"));
        }
    }
    @Test void aPriorCompletedSearchCanExistOutsideNativeAllocationWithoutDoubleCountingItsReuse()throws Exception {
        var queries=List.of(query("direct_gene_phenotype"),query("molecular_mechanism"),query("contradictions_and_replication"));var doc=Map.<String,Object>of("evidence",queries);
        var coverage=new StudyLiteratureCoverage(Set.of("H"));coverage.add(input("supplied",doc,null,true,"complete"));coverage.add(input("native",doc,allocation("outside_review_budget",false),false,"complete"));var result=result(coverage);
        assertEquals(3,result.get("unique_queries"));assertEquals(3L,result.get("reported_complete_queries"));assertEquals(3L,result.get("source_reported_queries"));assertEquals(0L,result.get("native_retrieval_or_cache_queries"));assertEquals(3L,result.get("duplicate_reports_collapsed"));assertEquals("bounded_queries_report_no_matches",result.get("query_evidence_state"));assertTrue(text(result.get("required_manuscript_caveat")).contains("separately supplied"));
        for(Object raw:list(result.get("queries")))assertEquals(List.of("supplied","native"),map(raw).get("source_ids"));String json=StudyAssistance.json(result);assertFalse(json.contains("PRIVATE_"));
    }
    @Test void bibliographiesDoNotEstablishFeatureSearchCoverageOrSourceSupport(){
        var coverage=new StudyLiteratureCoverage(Set.of("H"));coverage.add(input("bibliography",Map.of("reference_records",1,"evidence",List.of(Map.of("kind","supplied_bibliography","articles",List.of(Map.of("title","PRIVATE_TITLE"))))),null,true,"complete"));var result=result(coverage);assertEquals("bibliography_without_feature_search_receipts",result.get("query_evidence_state"));assertEquals(false,result.get("source_support_established"));assertFalse(StudyAssistance.json(result).contains("PRIVATE_TITLE"));
    }
    @Test void retrievedCandidatesRemainUnreviewedAndDuplicateCitationsAreNotIndependent(){
        var q=query("direct_gene_phenotype");q.put("status","supplied_candidate_matches");q.put("hit_count",2);q.put("articles",List.of(Map.of("doi","10.5555/test"),Map.of("doi","10.5555/test")));var coverage=new StudyLiteratureCoverage(Set.of("H"));coverage.add(input("source",Map.of("evidence",List.of(q)),null,true,"complete"));var result=result(coverage);assertEquals("candidate_sources_unreviewed",result.get("query_evidence_state"));var row=map(list(result.get("queries")).get(0));assertEquals(2L,row.get("article_records"));assertEquals(1,row.get("unique_citation_keys"));assertEquals("required",result.get("scientific_appraisal"));
    }
    @Test void nativePagesFormOneLogicalQueryAndAServiceFailureDoesNotBecomeNoMatches(){
        for(boolean failure:List.of(false,true)){
            var first=query("direct_gene_phenotype");first.remove("execution_origin");first.remove("query_complete");first.put("page",1);first.put("status","candidate_matches");first.put("hit_count",2);first.put("articles",List.of(Map.of("id","1")));first.put("retrieval",Map.of("response_sha256","first","finished_at","2026-10-26T00:00:00Z"));
            var second=new LinkedHashMap<>(first);second.put("page",2);second.put("retrieval",Map.of("response_sha256","second","finished_at","2026-10-26T00:00:01Z"));second.put("articles",List.of(Map.of("id","2")));if(failure){second.put("status","unavailable");second.remove("hit_count");second.remove("articles");}
            var coverage=new StudyLiteratureCoverage(Set.of("H"));coverage.add(input("native",Map.of("evidence",List.of(first,second)),null,false,failure?"waiting_for_input":"complete"));var result=result(coverage);assertEquals(1,result.get("unique_queries"));assertEquals(failure?0L:1L,result.get("reported_complete_queries"));var row=map(list(result.get("queries")).get(0));assertEquals(true,row.get("metadata_valid"));assertEquals(2,row.get("page_records"));assertEquals(failure?"incomplete_or_mixed_query_coverage":"candidate_sources_unreviewed",result.get("query_evidence_state"));
        }
    }
    @Test void truncatedOrUnacceptedSuppliedReceiptsCannotClaimCompletion(){
        for(String kind:List.of("limited","unaccepted","missing-completion")){
            var q=query("direct_gene_phenotype");if(kind.equals("limited"))q.put("search_limited",true);if(kind.equals("missing-completion"))q.remove("query_complete");var coverage=new StudyLiteratureCoverage(Set.of("H"));coverage.add(input("external",Map.of("evidence",List.of(q)),null,true,kind.equals("unaccepted")?"waiting_for_input":"complete"));assertEquals(0L,result(coverage).get("reported_complete_queries"));assertEquals("incomplete_or_mixed_query_coverage",result(coverage).get("query_evidence_state"));
        }
    }
    @Test void explicitGeneOnlyQueriesRemainGeneContextAndContradictoryFeatureMappingsAreFlagged(){
        var gene=query("direct_gene_phenotype");gene.remove("feature_id");var coverage=new StudyLiteratureCoverage(Set.of("H"));coverage.add(input("gene",Map.of("evidence",List.of(gene)),null,true,"complete"));assertEquals(List.of("declared_gene_symbol"),map(list(result(coverage).get("queries")).get(0)).get("match_basis"));
        var wrong=query("direct_gene_phenotype");wrong.put("gene_symbol","OTHER");coverage=new StudyLiteratureCoverage(Set.of("H"));coverage.add(input("wrong",Map.of("evidence",List.of(wrong)),null,true,"complete"));assertEquals("query_scope_conflict",result(coverage).get("query_evidence_state"));
        wrong.put("feature_id","different-feature");wrong.put("gene_symbol","G1");coverage=new StudyLiteratureCoverage(Set.of("H"));coverage.add(input("different",Map.of("evidence",List.of(wrong)),null,true,"complete"));assertEquals(0,result(coverage).get("unique_queries"));
    }
    @Test void malformedCountsDoNotRoundOrWrapIntoSuccessfulEmptySearches(){
        for(String invalid:List.of("1e-999","-1e-999","9223372036854775808",".5","NaN")){var q=query("direct_gene_phenotype");q.put("hit_count",invalid);var coverage=new StudyLiteratureCoverage(Set.of("H"));coverage.add(input("bad",Map.of("evidence",List.of(q)),null,true,"complete"));var row=map(list(result(coverage).get("queries")).get(0));assertEquals(false,row.get("metadata_valid"));assertEquals(0L,result(coverage).get("reported_complete_queries"));}
    }
    @Test void inconsistentAllocationCannotCertifyReviewAndKeepsItsOwnSource()throws Exception {
        var coverage=new StudyLiteratureCoverage(Set.of("H"));coverage.add(input("bad-ledger",Map.of(),allocation("bounded_search_no_matches",false),false,"complete"));var result=result(coverage);var row=map(list(result.get("native_allocations")).get(0));assertEquals(false,row.get("metadata_valid"));assertEquals(List.of("bad-ledger-allocation"),row.get("source_ids"));assertTrue(text(result.get("required_manuscript_caveat")).contains("incomplete or inconsistent"));assertEquals("no_feature_search_receipts",result.get("query_evidence_state"));
    }
    @Test void onlyRequestedMissingLiteratureProducesAnUnavailableCoverageContext(){assertTrue(new StudyLiteratureCoverage(Set.of()).forFinding("H","f",Map.of()).isEmpty());assertEquals("producer_unavailable",new StudyLiteratureCoverage(Set.of("H")).forFinding("H","f",Map.of()).get("query_evidence_state"));}
    @Test void editorialReviewRetainsSelectedCoverageWithoutSourceQueryText()throws Exception {
        var coverage=new StudyLiteratureCoverage(Set.of("H"));coverage.add(input("source",Map.of("evidence",List.of(query("direct_gene_phenotype"))),null,true,"complete"));var expected=result(coverage);
        var selected=Map.<String,Object>of("finding_id","finding-f1","hypothesis","H","feature_id","f1","literature_coverage",expected);
        var evidence=Map.of("impact",Map.<String,Object>of("operation_type","impact_assessment","state","complete","key_findings",Map.of("selected_findings",List.of(selected))));
        var payload=StudyEditorialReview.payload(Map.of("sections",List.of()),evidence);var summary=map(list(payload.get("aggregate_evidence")).get(0));assertEquals(expected,map(list(summary.get("selected_finding_literature_coverage")).get(0)).get("literature_coverage"));assertFalse(StudyAssistance.json(payload).contains("PRIVATE_"));
    }
    @Test void theFinalInventoryReceivesCoverageAndMandatoryCaveatsWithoutChangingEligibility()throws Exception {
        Path source=root.resolve("source");Files.createDirectories(source.resolve("standard-results"));Files.writeString(source.resolve("standard-results/d.tsv"),"feature_id\tbeta\tse\tp_value\tn\n f1\t.4\t.1\t.001\t100\nf2\t.3\t.1\t.002\t100\n".replace(" f1","f1"));Files.writeString(source.resolve("feature-annotation.tsv"),"feature_id\tgene_symbol\tgene_id\nf1\tG1\tGENE1\nf2\tG2\tGENE2\n");StudyTestSources.seal(source,"association","source","H");String before=FollowupSupport.hash(source.resolve("standard-results/d.tsv"));
        Path lit=root.resolve("literature");Files.createDirectories(lit);
        Files.writeString(lit.resolve("literature-selection.tsv"),"rank\tfeature_id\tselected_for_review\tretrieval_status\treview_rank\tselection_basis\tsearch_records\n1\tf1\ttrue\tbounded_search_no_matches\t1\tsource_p_value\t1\n2\tf2\tfalse\toutside_review_budget\tNA\tsource_p_value\t0\n");write(lit.resolve("literature-search.yaml"),Map.of("evidence",List.of(query("direct_gene_phenotype"))));StudyTestSources.seal(lit,"literature_search","literature","H");
        var study=Map.<String,Object>of("datasets",List.of(Map.of("id","d","type","expression")),"hypotheses",List.of(Map.of("id","H","inputs",Map.of("discovery",List.of("d")),"analysis",Map.of("association",Map.of("model","ols")))));Path out=root.resolve("inventory");var c=new Context(root,out,Map.of("id","inventory","type","finding_inventory","hypothesis","__study__"),study,Map.of("operations",List.of(Map.of("type","literature_search","hypothesis","H"))),Map.of("source",source,"literature",lit));var result=new StudyFindingInventory().execute(c);assertEquals(State.COMPLETE,result.state());
        var findings=new ArrayList<Map<String,Object>>();for(String line:Files.readAllLines(out.resolve("finding-inventory.jsonl")))findings.add(map(SimpleJson.parse(line)));assertEquals(2,findings.size());var unreviewed=findings.stream().filter(f->f.get("feature_id").equals("f2")).findFirst().orElseThrow();assertEquals(true,unreviewed.get("candidate"));var coverage=map(unreviewed.get("literature_coverage"));assertEquals("outside_review_budget",map(list(coverage.get("native_allocations")).get(0)).get("status"));assertTrue(list(unreviewed.get("caveats")).stream().map(StudyPublicEvidence::map).anyMatch(row->text(row.get("text")).contains("cap is not negative")));assertEquals(before,FollowupSupport.hash(source.resolve("standard-results/d.tsv")));
    }
}
