/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Typed external bibliography/search declarations; no native retrieval or source authentication implied. */
final class StudySuppliedLiterature {
    private StudySuppliedLiterature() { }
    static final String CAVEAT="Bibliographic metadata and search completion are supplied declarations, not authenticated provider responses or independently reproduced searches. Literal excerpts are checked only against caller-supplied abstracts. Review identity, primary sources, dates, query coverage and scientific relevance before use.";
    static final List<String> SCOPE=List.of("search_strategy","query","phenotype","gene_symbol","feature_id","methods_role","cohort_id","cohort_name","requested_reference");
    static final Set<String> STRATEGIES=Set.of("field_background","hypothesis_relevance","similar_studies","field_conflicting_evidence","direct_gene_phenotype","molecular_mechanism","contradictions_and_replication","cohort_description","analysis_method","method_reference");

    static Map<String,Object> normalize(Path source,Map<String,Object> definition)throws IOException {
        if(Files.size(source)>8*1024*1024)throw new IOException("Supplied literature exceeds the 8 MiB document bound");
        var input=StudyYaml.loadExactNumericArtifact(source);
        String sourceHash=FollowupSupport.hash(source);
        notes(input);
        keys(input,Set.of("schema_version","public_metadata","references","queries","notes"),"supplied literature");
        if(!Integer.valueOf(1).equals(input.get("schema_version"))||!Boolean.TRUE.equals(input.get("public_metadata")))throw new IOException("Typed supplied literature needs schema_version: 1 and explicit public_metadata: true for its bibliography/search labels; raw provenance and notes stay local");
        var refs=new LinkedHashMap<String,Map<String,Object>>();
        for(Object raw:sequence(input.get("references"),"references",5000)) {
            var r=object(raw,"reference");keys(r,Set.of("id","doi","pmid","pmcid","title","authors","year","journal","abstract_text","notes"),"reference");
            notes(r);
            String id=identifier(required(r,"id",100));if(refs.containsKey(id))throw new IOException("Duplicate supplied reference key");
            var article=new LinkedHashMap<String,Object>();
            for(String field:List.of("doi","pmid","pmcid"))if(r.containsKey(field)) {
                String value=required(r,field,1000);if(field.equals("doi")){value=StudyReferenceCatalog.normalize(value);if(!value.matches("10\\.[0-9]{4,9}/\\S+"))throw new IOException("Invalid supplied DOI");}
                if(field.equals("pmid")&&!value.matches("[1-9][0-9]*"))throw new IOException("Invalid supplied PMID");
                if(field.equals("pmcid")){value=value.toUpperCase(java.util.Locale.ROOT);if(!value.matches("PMC[0-9]+"))throw new IOException("Invalid supplied PMCID");}article.put(field,value);
            }
            if(article.isEmpty())throw new IOException("Supplied reference requires DOI, PMID or PMCID; titles and local reference keys do not establish identity");
            article.put("title",required(r,"title",4000));if(r.containsKey("authors"))article.put("authorString",required(r,"authors",4000));
            if(r.containsKey("year")){Object rawYear=r.get("year");String year=rawYear instanceof Number?Long.toString(integer(rawYear,"reference year")):text(rawYear);if(!year.matches("[12][0-9]{3}"))throw new IOException("Supplied reference year must be a four-digit year");article.put("pubYear",year);}
            if(r.containsKey("journal"))article.put("journalInfo",Map.of("journal",Map.of("title",required(r,"journal",1000))));
            if(r.containsKey("abstract_text")){Object abstractText=r.get("abstract_text");if(!(abstractText instanceof String s)||s.length()>40000||s.indexOf('\0')>=0)throw new IOException("Supplied abstract must be bounded text");article.put("abstractText",s);}
            var normalized=new LinkedHashMap<>(StudyLiterature.abstractEvidence(article,"","","supplied/results"));
            if(normalized.containsKey("excerpt")){normalized.put("excerpt_status","literal_substring_of_caller_supplied_abstract_not_authenticated");normalized.put("excerpt_scope","Caller-supplied abstract excerpt, not an authenticated retrieval or a full-text read");}
            normalized.put("metadata_origins",List.of("caller_supplied_bibliography"));normalized.put("source_authentication","not_independently_authenticated");
            refs.put(id,normalized);
        }
        var queries=new ArrayList<Object>();var ids=new LinkedHashSet<String>();var identities=new LinkedHashSet<String>();
        for(Object raw:sequence(input.get("queries"),"queries",2000)) {
            var q=object(raw,"query");var allowed=new LinkedHashSet<String>(SCOPE);allowed.addAll(Set.of("id","provider","status","coverage","hit_count","references","searched_at","reuse_rationale","notes"));keys(q,allowed,"query");
            notes(q);
            String id=identifier(required(q,"id",100));if(!ids.add(id))throw new IOException("Duplicate supplied query ID");
            var row=new LinkedHashMap<String,Object>();row.put("supplied_query_id",id);row.put("provider",required(q,"provider",200));
            for(String field:SCOPE)if(q.containsKey(field))row.put(field,required(q,field,field.equals("query")?8000:1000));
            if(!STRATEGIES.contains(required(q,"search_strategy",100)))throw new IOException("Unknown supplied search strategy");required(q,"query",8000);
            String status=required(q,"status",100),coverage=required(q,"coverage",100);
            if(!Set.of("complete","partial","unavailable").contains(status)||!Set.of("complete","selected_only","unknown").contains(coverage))throw new IOException("Invalid supplied query status/coverage");
            String date=q.get("searched_at") instanceof java.util.Date parsedDate?parsedDate.toInstant().toString():required(q,"searched_at",100);try{OffsetDateTime.parse(date);}catch(java.time.DateTimeException bad){throw new IOException("Supplied searched_at requires an ISO timestamp with offset",bad);}
            row.put("searched_at",date);row.put("declared_status",status);row.put("declared_coverage",coverage);
            long hits=integer(q.get("hit_count"));var articles=new ArrayList<Object>();var used=new LinkedHashSet<String>();
            for(Object value:sequence(q.get("references"),"query references",5000)){if(!(value instanceof String key)||!refs.containsKey(key)||!used.add(key))throw new IOException("Query references must be unique known reference keys");articles.add(refs.get(key));}
            if(hits<articles.size())throw new IOException("Supplied hit_count is smaller than its reference records");
            boolean complete=status.equals("complete")&&coverage.equals("complete");
            if(complete&&hits!=articles.size())throw new IOException("Complete query coverage requires all hit records; selected bibliography exports cannot suppress missing retrieval");
            String rationale=required(q,"reuse_rationale",4000);row.put("reuse_rationale",rationale);
            row.put("kind","literature");row.put("query_complete",complete);row.put("search_limited",!complete);row.put("hit_count",hits);row.put("articles",articles);
            row.put("status",complete?hits==0?"supplied_no_matches":"supplied_candidate_matches":status.equals("unavailable")?"supplied_unavailable":"supplied_partial");
            row.put("execution_origin","externally_produced");row.put("native_request_attempted",false);row.put("supplied_artifact",definition.get("id"));row.put("source_sha256",sourceHash);row.put("interpretation",CAVEAT);
            if(!identities.add(row.get("provider")+"/"+key(row)))throw new IOException("Repeated supplied search scope/query; retain one explicitly chosen source version per query");queries.add(row);
        }
        if(refs.isEmpty()&&queries.isEmpty())throw new IOException("Supply references or explicit searched queries; an empty bibliography is not a completed search");
        // Every supplied reference remains available, including those without query coverage.
        var bibliography=new LinkedHashMap<String,Object>();bibliography.put("kind","supplied_bibliography");bibliography.put("status","supplied_bibliography_only");bibliography.put("execution_origin","externally_produced");bibliography.put("supplied_artifact",definition.get("id"));bibliography.put("articles",new ArrayList<>(refs.values()));bibliography.put("interpretation",CAVEAT);
        var evidence=new ArrayList<Object>(queries);evidence.add(bibliography);
        return Map.of("schema_version",1,"execution_origin","externally_produced","analysis_executed_by_jlinalg",false,"public_metadata_declared",true,"reference_records",refs.size(),"query_records",queries.size(),"evidence",evidence,"required_manuscript_caveat",CAVEAT);
    }
    static String key(Map<String,Object> row){return StudyAssistance.json(SCOPE.stream().map(field->text(row.get(field))).toList());}
    static boolean completed(Map<String,Object> row){return Set.of("candidate_matches","no_matches","supplied_candidate_matches","supplied_no_matches").contains(row.get("status"));}
    private static long integer(Object raw)throws IOException{return integer(raw,"hit_count");}
    private static long integer(Object raw,String field)throws IOException{
        if(raw instanceof Number)try{long value=new java.math.BigDecimal(raw.toString()).longValueExact();if(value>=0)return value;}catch(NumberFormatException|ArithmeticException ignored){/* Reject fractional, nonfinite and out-of-range counts. */}
        throw new IOException("Supplied "+field+" must be an exact nonnegative integer within the signed 64-bit range");
    }
    private static Map<String,Object> object(Object raw,String where)throws IOException{if(!(raw instanceof Map<?,?>))throw new IOException(where+" must be a mapping");return map(raw);}
    private static List<?> sequence(Object raw,String where,int maximum)throws IOException{if(raw==null)return List.of();if(!(raw instanceof List<?> l)||l.size()>maximum)throw new IOException("Invalid or oversized "+where);return l;}
    private static void keys(Map<String,Object> value,Set<String> allowed,String where)throws IOException{for(String key:value.keySet())if(!allowed.contains(key))throw new IOException("Unknown "+where+" field: "+key);}
    private static String required(Map<String,Object> value,String key,int maximum)throws IOException{if(!(value.get(key) instanceof String s)||s.isBlank()||s.length()>maximum||s.chars().anyMatch(Character::isISOControl))throw new IOException("Missing or unbounded supplied literature "+key);return s;}
    private static String identifier(String value)throws IOException{if(!value.matches("[A-Za-z][A-Za-z0-9_.-]{0,99}"))throw new IOException("Invalid supplied literature ID");return value;}
    private static void notes(Map<String,Object> value)throws IOException{if(value.containsKey("notes")&&(!(value.get("notes") instanceof String s)||s.length()>16000||s.indexOf('\0')>=0))throw new IOException("Local supplied notes must be bounded text");}
}
