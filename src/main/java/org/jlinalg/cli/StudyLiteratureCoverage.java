/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Source-scoped search coverage, independent of statistical and manuscript eligibility. */
final class StudyLiteratureCoverage {
    static final String CAVEAT="Literature coverage is limited to recorded queries and artifacts. It does not establish an exhaustive review, novelty, full-text appraisal or support for this finding. Reused or duplicated receipts are not independent searches. Native review allocation and separately supplied search evidence are distinct.";
    private static final List<String> STRATEGIES=List.of("direct_gene_phenotype","molecular_mechanism","contradictions_and_replication");
    private static final Set<String> ALLOCATION_STATES=Set.of("outside_review_budget","annotation_required","retrieval_incomplete","bounded_search_limited_unreviewed","bounded_search_no_matches","sources_retrieved_unreviewed","source_reported_search_no_matches","source_reported_candidates_unreviewed","provisional_priority_pending");
    record Input(String hypothesis,String producerState,String role,String summarySource,String allocationSource,
                 Map<String,Object> document,DelimitedData allocation,boolean external) { }
    private final List<Indexed> inputs=new ArrayList<>();
    private final Set<String> requested;
    StudyLiteratureCoverage(Set<String> requested){this.requested=Set.copyOf(requested);}
    void add(Input input){inputs.add(new Indexed(input));}

    /** Index each verified producer once; never rescan a full GWAS ledger for every finding. */
    private static final class Indexed {
        final Input input;
        final Map<String,List<String[]>> allocations=new LinkedHashMap<>();
        final Map<String,List<Map<String,Object>>> features=new LinkedHashMap<>(),genes=new LinkedHashMap<>();
        boolean bibliography;
        Indexed(Input input){
            this.input=input;var table=input.allocation;
            if(table!=null&&table.header().contains("feature_id"))for(String[] row:table.rows())allocations.computeIfAbsent(row[table.column("feature_id")],ignored->new ArrayList<>()).add(row);
            bibliography=number(input.document.get("reference_records"))>0;
            for(Object raw:list(input.document.get("evidence"))){var row=map(raw);
                if("supplied_bibliography".equals(row.get("kind"))&&!list(row.get("articles")).isEmpty())bibliography=true;
                if(!"literature".equals(row.get("kind")))continue;
                String feature=text(row.get("feature_id")),gene=text(row.get("gene_symbol"));
                if(!feature.isBlank())features.computeIfAbsent(feature,ignored->new ArrayList<>()).add(row);
                else if(!gene.isBlank())genes.computeIfAbsent(gene,ignored->new ArrayList<>()).add(row);
            }
        }
        List<Map<String,Object>> queries(String feature,String gene){var rows=new ArrayList<>(features.getOrDefault(feature,List.of()));rows.addAll(genes.getOrDefault(gene,List.of()));return rows;}
    }

    Map<String,Object> forFinding(String hypothesis,String feature,Map<String,Object> annotation){
        var matching=inputs.stream().filter(i->i.input.hypothesis.equals(hypothesis)).toList();
        if(matching.isEmpty()&&!requested.contains(hypothesis))return Map.of();
        var allocations=new ArrayList<Object>();var groups=new LinkedHashMap<String,Query>();var sources=new LinkedHashSet<String>();
        int bibliographyProducers=0;boolean incompleteProducer=false;
        for(var indexed:matching){var input=indexed.input;
            if(!input.summarySource.isBlank())sources.add(input.summarySource);
            incompleteProducer|=!input.producerState.equals("complete");
            if(input.allocation!=null){
                var table=input.allocation;boolean found=false;
                for(String[] values:indexed.allocations.getOrDefault(feature,List.of())){
                    found=true;var row=new LinkedHashMap<String,Object>();row.put("producer_state",state(input.producerState));row.put("source_ids",List.of(input.allocationSource));sources.add(input.allocationSource);
                    String status=cell(table,values,"retrieval_status");row.put("status",ALLOCATION_STATES.contains(status)?status:"unrecognized_coverage_status");
                    String selected=cell(table,values,"selected_for_review");row.put("selected_for_review",selected.equals("true")?true:selected.equals("false")?false:"not_reported");
                    String basis=cell(table,values,"selection_basis");row.put("selection_basis",Set.of("source_p_value","all_eligible_within_cap","provisional_AI_impact").contains(basis)?basis:basis.isBlank()?"legacy_not_reported":"unrecognized");
                    for(String key:List.of("rank","review_rank","search_records","source_reported_search_records","native_or_legacy_search_records")){
                        Long value=count(cell(table,values,key));row.put(key.equals("rank")?"statistical_rank":key,value==null?"not_reported":value);
                    }
                    boolean valid=ALLOCATION_STATES.contains(status)&&Set.of("true","false").contains(selected);
                    if(Set.of("outside_review_budget","provisional_priority_pending").contains(status)&&!selected.equals("false"))valid=false;
                    if(Set.of("bounded_search_no_matches","sources_retrieved_unreviewed","source_reported_search_no_matches","source_reported_candidates_unreviewed","bounded_search_limited_unreviewed").contains(status)&&!selected.equals("true"))valid=false;
                    Long rank=count(cell(table,values,"rank")),reviewRank=count(cell(table,values,"review_rank"));
                    if(rank==null||rank==0||selected.equals("true")&&(reviewRank==null||reviewRank==0))valid=false;
                    row.put("metadata_valid",valid);allocations.add(row);
                }
                if(!found)allocations.add(Map.of("status","outside_declared_review_population","producer_state",state(input.producerState),"source_ids",List.of(input.allocationSource)));
            }
            for(var row:indexed.queries(feature,text(annotation.get("gene_symbol")))){
                String declaredFeature=text(row.get("feature_id")),symbol=text(row.get("gene_symbol"));String ownSymbol=text(annotation.get("gene_symbol"));
                boolean exact=!declaredFeature.isBlank()&&declaredFeature.equals(feature),gene=declaredFeature.isBlank()&&!symbol.isBlank()&&symbol.equals(ownSymbol);
                if(!exact&&!gene)continue;
                boolean conflict=exact&&!symbol.isBlank()&&!ownSymbol.isBlank()&&!symbol.equals(ownSymbol);
                boolean supplied=input.external||"externally_produced".equals(row.get("execution_origin"));
                String scope=StudySuppliedLiterature.key(row),origin=supplied?text(row.get("source_sha256"))+"/"+text(row.get("supplied_query_id")):input.summarySource;
                if(supplied&&(text(row.get("source_sha256")).isBlank()||text(row.get("supplied_query_id")).isBlank()))origin=input.summarySource;
                String id=StudyFindingInventory.hash(StudyAssistance.json(List.of(supplied?"supplied":"native",origin,scope)));
                var query=groups.computeIfAbsent(id,key->new Query(id,supplied,input.role));query.add(row,input,exact?"exact_feature_id":"declared_gene_symbol",conflict);
            }
            if(indexed.bibliography)bibliographyProducers++;
        }
        var queries=groups.values().stream().map(Query::document).toList();long complete=queries.stream().filter(q->Boolean.TRUE.equals(q.get("reported_complete"))).count();
        long partial=queries.size()-complete,reported=queries.stream().filter(q->q.get("execution_origin").equals("source_reported")).count(),nativeQueries=queries.size()-reported;
        boolean matches=queries.stream().anyMatch(q->number(q.get("article_records"))>0||number(q.get("reported_hits"))>0);
        boolean scopeConflict=queries.stream().anyMatch(q->Boolean.TRUE.equals(q.get("scope_conflict")));
        String evidence=scopeConflict?"query_scope_conflict":partial>0?"incomplete_or_mixed_query_coverage":complete>0?matches?"candidate_sources_unreviewed":"bounded_queries_report_no_matches":bibliographyProducers>0?"bibliography_without_feature_search_receipts":matching.isEmpty()||matching.stream().allMatch(i->!i.input.producerState.equals("complete")&&i.input.document.isEmpty())?"producer_unavailable":"no_feature_search_receipts";
        var strategyStates=new LinkedHashMap<String,Object>();for(String strategy:STRATEGIES){var rows=queries.stream().filter(q->strategy.equals(q.get("search_strategy"))).toList();strategyStates.put(strategy,rows.isEmpty()?"not_recorded":rows.stream().allMatch(q->Boolean.TRUE.equals(q.get("reported_complete")))?"bounded_query_receipts_complete":"incomplete_or_mixed");}
        var result=new LinkedHashMap<String,Object>();result.put("schema_version",1);result.put("scope","feature_or_declared_gene_query_receipts");result.put("query_evidence_state",evidence);result.put("native_allocations",allocations);result.put("queries",queries);
        result.put("unique_queries",queries.size());result.put("reported_complete_queries",complete);result.put("incomplete_or_unknown_queries",partial);result.put("source_reported_queries",reported);result.put("native_retrieval_or_cache_queries",nativeQueries);
        result.put("duplicate_reports_collapsed",queries.stream().mapToLong(q->number(q.get("duplicate_reports_collapsed"))).sum());result.put("bibliographic_producers",bibliographyProducers);result.put("incomplete_producer_present",incompleteProducer);result.put("strategy_coverage",strategyStates);
        result.put("systematic_review_established",false);result.put("source_support_established",false);result.put("scientific_appraisal","required");result.put("source_ids",List.copyOf(sources));
        var limitations=new ArrayList<String>();
        for(Object raw:allocations){var allocation=map(raw);String status=text(allocation.get("status"));if(Boolean.FALSE.equals(allocation.get("metadata_valid")))limitations.add("Native allocation metadata is incomplete or inconsistent; it does not certify review completion.");if(status.equals("outside_review_budget"))limitations.add("This finding was outside this native retrieval allocation; separately supplied or historical searches may still exist. The cap is not negative literature evidence.");
            else if(status.equals("provisional_priority_pending"))limitations.add("Native retrieval allocation is pending; no negative search result or scientific-impact conclusion follows.");
            else if(Set.of("retrieval_incomplete","annotation_required","outside_declared_review_population","unrecognized_coverage_status").contains(status))limitations.add("This native stage does not establish completed feature-level retrieval for the finding; inspect its declared target population and prerequisites.");}
        if(evidence.equals("bounded_queries_report_no_matches"))limitations.add("No matches were reported for these bounded queries; this does not establish absence of prior work, novelty or lack of biological support.");
        else if(evidence.equals("bibliography_without_feature_search_receipts"))limitations.add("A supplied bibliography is available, but it does not establish completed feature-level search coverage.");
        else if(!evidence.equals("candidate_sources_unreviewed"))limitations.add("Feature-level search coverage is incomplete, unavailable, outside the recorded scope or not supplied; absence of receipts is not a negative result.");
        result.put("required_manuscript_caveat",CAVEAT+" "+String.join(" ",new LinkedHashSet<>(limitations)));return result;
    }

    private static final class Query {
        final String id;final boolean supplied;final Set<String> roles=new LinkedHashSet<>(),sources=new LinkedHashSet<>(),dates=new LinkedHashSet<>(),matches=new LinkedHashSet<>(),citations=new LinkedHashSet<>();
        final Map<String,String> records=new LinkedHashMap<>();String strategy="not_reported",provider="not_reported";long articles,hits=-1,duplicates;boolean allComplete=true,limited,invalid,conflict,accepted=true;
        Query(String id,boolean supplied,String role){this.id=id;this.supplied=supplied;roles.add(role(role));}
        void add(Map<String,Object> row,Input input,String match,boolean conflict){
            sources.add(input.summarySource);roles.add(role(input.role));matches.add(match);this.conflict|=conflict;
            accepted&=input.external?input.producerState.equals("complete"):Set.of("complete","waiting_for_input").contains(input.producerState);
            String rawStrategy=text(row.get("search_strategy"));strategy=Set.of("direct_gene_phenotype","molecular_mechanism","contradictions_and_replication").contains(rawStrategy)?rawStrategy:"other_declared_query";
            provider=publicLabel(row.get("provider"),"Europe PMC");String date=supplied?text(row.get("searched_at")):text(map(row.get("retrieval")).get("finished_at"));
            if(!date.isBlank())try{dates.add(OffsetDateTime.parse(date).toString());}catch(java.time.DateTimeException ignored){invalid=true;}
            Long count=count(row.get("hit_count"));String status=text(row.get("status"));boolean known=Set.of("no_matches","candidate_matches","supplied_no_matches","supplied_candidate_matches").contains(status),noMatches=Set.of("no_matches","supplied_no_matches").contains(status);
            var articleIds=new ArrayList<String>();for(Object raw:list(row.get("articles"))){var article=map(raw);String key=text(article.get("doi")).strip().toLowerCase(java.util.Locale.ROOT);if(key.isBlank())key=text(article.getOrDefault("pmid",article.getOrDefault("id",article.get("pmcid"))));if(key.isBlank())key=StudyFindingInventory.hash(StudyAssistance.json(article));articleIds.add(key);}
            Object completion=row.get("query_complete"),bound=row.get("search_limited");
            if(completion!=null&&!(completion instanceof Boolean)||bound!=null&&!(bound instanceof Boolean))invalid=true;
            var projection=Map.of("status",status,"reported_hits",count==null?"not_reported":count,"article_ids",articleIds,"query_complete",completion instanceof Boolean?completion:"not_reported","search_limited",bound instanceof Boolean?bound:"not_reported","date",date);
            String record=supplied?"supplied-query":text(row.getOrDefault("page","legacy"))+"/"+text(map(row.get("retrieval")).get("response_sha256"));String digest=StudyFindingInventory.hash(StudyAssistance.json(projection)),prior=records.putIfAbsent(record,digest);
            if(prior!=null){duplicates++;if(!prior.equals(digest))invalid=true;return;}
            limited|=Boolean.TRUE.equals(row.get("search_limited"));allComplete&=known&&(supplied?Boolean.TRUE.equals(row.get("query_complete")):!Boolean.FALSE.equals(row.get("query_complete")));
            if(known&&count==null||row.get("hit_count")!=null&&count==null||noMatches&&count!=null&&(count!=0||!articleIds.isEmpty())||known&&!noMatches&&count!=null&&count==0)invalid=true;
            if(count!=null){if(hits>=0&&hits!=count)invalid=true;hits=count;}
            articles+=articleIds.size();citations.addAll(articleIds);
        }
        Map<String,Object> document(){
            boolean complete=allComplete&&!limited&&!invalid&&!conflict&&accepted&&hits>=0&&articles>=hits;
            var result=new LinkedHashMap<String,Object>();result.put("query_id","query-"+id.substring(0,24));result.put("search_strategy",strategy);result.put("provider",provider);result.put("execution_origin",supplied?"source_reported":"native_retrieval_or_cache");result.put("reporting_roles",List.copyOf(roles));result.put("match_basis",List.copyOf(matches));result.put("search_dates",List.copyOf(dates));
            result.put("reported_complete",complete);result.put("search_limited",limited);result.put("scope_conflict",conflict);result.put("metadata_valid",!invalid);result.put("producer_accepted",accepted);result.put("reported_hits",hits<0?"not_reported":hits);result.put("article_records",articles);result.put("unique_citation_keys",citations.size());result.put("page_records",records.size());result.put("duplicate_reports_collapsed",duplicates);result.put("source_ids",List.copyOf(sources));return result;
        }
    }
    static Long count(Object raw){try{long result=new BigDecimal(text(raw).strip()).longValueExact();return result<0?null:result;}catch(NumberFormatException|ArithmeticException invalid){return null;}}
    private static long number(Object raw){Long value=count(raw);return value==null?0:value;}
    private static String cell(DelimitedData table,String[] row,String key){return table.header().contains(key)?row[table.column(key)]:"";}
    private static String state(String raw){return Set.of("complete","waiting_for_input","failed","not_applicable","blocked").contains(raw)?raw:"unknown";}
    private static String role(String raw){return Set.of("primary","contribution","sensitivity").contains(raw)?raw:"not_reported";}
    private static String publicLabel(Object raw,String fallback){String value=text(raw);return value.isBlank()?fallback:value.length()<=200&&value.chars().noneMatch(Character::isISOControl)?value:"not_reported";}
}
