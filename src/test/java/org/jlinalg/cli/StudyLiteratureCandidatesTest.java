/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StudyLiteratureCandidatesTest {
    private Map<String,Object> query(String id, boolean eligible, Object... articles) {
        return new LinkedHashMap<>(Map.of("query_id",id,"candidate_context_eligible",eligible,"source_ids",List.of("source-"+id),
            "match_basis",List.of("exact_feature_id"),"articles",List.of(articles),"query","PRIVATE_QUERY","notes","PRIVATE_QUERY_NOTE"));
    }
    private Map<String,Object> project(Object... queries) { return StudyLiteratureCandidates.project(java.util.Arrays.stream(queries).map(StudyPublicEvidence::map).toList(),"test_query_candidates"); }
    private Map<String,Object> first(Map<String,Object> result) { return map(list(result.get("references")).get(0)); }
    @Test void aliasesAcrossQueriesProduceOneReferenceWithAllSourceLinks() {
        var result=project(query("a",true,Map.of("doi","10.5555/ONE","pmid","12345","title","Public paper")),query("b",true,Map.of("pmid","12345","authorString","Public Author")));
        assertEquals(1,result.get("unique_candidate_references"));var r=first(result);
        assertEquals("10.5555/one",r.get("doi"));assertEquals(List.of("a","b"),r.get("query_ids"));assertEquals(List.of("source-a","source-b"),r.get("source_ids"));
        assertEquals("Public Author",r.get("authorString"));assertEquals(false,result.get("source_support_established"));assertEquals(false,result.get("full_text_appraisal_established"));
    }
    @Test void privateNotesAbstractsQueryProseAndUnknownNestedFieldsAreExcluded() {
        var article=new LinkedHashMap<String,Object>(Map.of("doi","10.5555/one","title","Public title","authorString","Public Author","pubYear",2021));
        for(String field:List.of("abstractText","abstract_text","excerpt","full_text","notes","path","metadata_origins","source_authentication"))article.put(field,"PRIVATE_"+field);
        article.put("journalInfo",Map.of("private","PRIVATE_JOURNAL_OUTER","journal",Map.of("title","Public Journal","notes","PRIVATE_JOURNAL_NOTE")));
        article.put("commentCorrectionList",Map.of("notes","PRIVATE_CORRECTION_OUTER","commentCorrection",List.of(Map.of("id","123","source","MED","type","Retraction in","notes","PRIVATE_CORRECTION_NOTE"))));
        var result=project(query("public",true,article));String encoded=StudyAssistance.json(result);assertFalse(encoded.contains("PRIVATE_"),encoded);
        var r=first(result);assertEquals("Public title",r.get("title"));assertEquals("Public Journal",r.get("journal"));assertEquals("2021",r.get("pubYear"));
        assertEquals("Retraction in",map(list(map(r.get("commentCorrectionList")).get("commentCorrection")).get(0)).get("type"));
    }
    @Test void conflictingMetadataSurvivesAsAnUnreviewedConflict() {
        var result=project(query("a",true,Map.of("doi","10.5555/one","title","First public title")),query("b",true,Map.of("doi","10.5555/one","title","Conflicting public title")));
        assertEquals(1,result.get("unique_candidate_references"));assertFalse(list(first(result).get("metadata_conflicts")).isEmpty());
        assertEquals("unreviewed_candidate_not_hypothesis_support",first(result).get("review_status"));
    }
    @Test void distinctIdentifiersDoNotMergeOnTheSameTitle() {
        var result=project(query("a",true,Map.of("doi","10.5555/one","title","Same public title"),Map.of("doi","10.5555/two","title","Same public title")));
        assertEquals(2,result.get("unique_candidate_references"));
    }
    @Test void quarantinedQueryContextWithholdsItsMetadataWithoutClaimingNoLiterature() {
        var result=project(query("bad",false,Map.of("doi","10.5555/private","title","WITHHELD_TITLE")));
        assertEquals(0,result.get("unique_candidate_references"));assertEquals(1L,result.get("withheld_article_records"));assertFalse(StudyAssistance.json(result).contains("WITHHELD_TITLE"));
        assertEquals(false,result.get("source_support_established"));assertEquals("required",result.get("scientific_review"));
    }
    @Test void acceptedAndQuarantinedRecordsKeepSeparateLineage() {
        var result=project(query("good",true,Map.of("doi","10.5555/one")),query("bad",false,Map.of("doi","10.5555/two")));
        assertEquals(1,result.get("unique_candidate_references"));assertEquals(List.of("source-good"),result.get("source_ids"));assertEquals(1L,result.get("withheld_article_records"));
    }
    @Test void titleOnlyCandidatesRemainIneligibleForFormalIdentifierCitation() {
        var result=project(query("a",true,Map.of("title","Unresolved public title")));
        assertEquals(false,first(result).get("citation_eligible"));assertFalse(first(result).containsKey("source_url"));
    }
    @Test void unknownFieldsCannotChangeAnUnresolvedPublicIdentity() {
        var first=project(query("a",true,Map.of("title","Public title","notes","PRIVATE_A")));
        var second=project(query("a",true,Map.of("title","Public title","notes","PRIVATE_B")));
        assertEquals(first(first).get("reference_id"),first(second).get("reference_id"));
    }
    @Test void emptyBibliographyAddsNoPayload() { assertTrue(project(query("a",true)).isEmpty()); }
    @Test void candidateInventoryDoesNotSilentlyCapAtTwentyReferences() {
        var articles=new java.util.ArrayList<Object>();for(int i=0;i<31;i++)articles.add(Map.of("doi","10.5555/paper"+i,"title","Public paper "+i));
        var q=query("a",true);q.put("articles",articles);assertEquals(31,project(q).get("unique_candidate_references"));
    }
    @Test void sharedBackgroundIsFilteredBySelectedHypotheses() {
        var background=Map.<String,Object>of("H1",Map.of("source_ids",List.of("s1")),"H2",Map.of("source_ids",List.of("s2")));
        var selected=StudyLiteratureCandidates.forFindings(background,List.of(Map.of("hypothesis","H2")));
        assertEquals(java.util.Set.of("H2"),selected.keySet());assertEquals(java.util.Set.of("s2"),StudyLiteratureCandidates.sourceIds(selected));
    }
}
