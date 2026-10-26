/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudySuppliedResultPolicy;

/** Reuse only complete, explicitly chosen, exact-scope external searches. */
final class StudyLiteratureReuse {
    private final Map<String,Map<String,Object>> reusable=new LinkedHashMap<>();
    private final Set<String> used=new LinkedHashSet<>();
    private final List<Object> audit=new ArrayList<>(),bibliography=new ArrayList<>();
    private final List<String> needs=new ArrayList<>();
    static StudyLiteratureReuse load(Context c)throws IOException {
        var result=new StudyLiteratureReuse();
        for(var d:StudySuppliedResultPolicy.resolve(StudyResultTables.analysis(c))) {
            if(!"literature_search".equals(d.get("kind"))||!"contribution".equals(d.get("role")))continue;
            String id=text(d.get("id"));
            var matches=new ArrayList<Path>();
            for(Path dir:c.dependencyDirectories().values()) {
                Path manifest=dir.resolve("manifest.yaml");if(!Files.isRegularFile(manifest))continue;var m=StudyYaml.loadArtifact(manifest);
                if(id.equals(map(m.get("details")).get("artifact_id"))&&"supplied_contribution".equals(m.get("operation_type")))matches.add(dir);
            }
            if(matches.size()!=1){result.needs.add("Literature contribution "+id+" has no unique dependency receipt");continue;}
            Path dir=matches.get(0);var manifest=StudyYaml.loadArtifact(dir.resolve("manifest.yaml"));
            if(!"complete".equals(manifest.get("state"))){result.needs.add("Literature contribution "+id+" remains incomplete; its searches were not reused");continue;}
            Path file=StudyFindingInventory.verified(dir,"literature-search.yaml",text(map(manifest.get("output_sha256")).get("literature-search.yaml")));
            var normalized=StudyYaml.loadArtifact(file);var details=map(manifest.get("details"));
            if(!"literature_search".equals(details.get("kind"))||!Boolean.TRUE.equals(normalized.get("public_metadata_declared")))throw new IOException("Invalid supplied literature contribution receipt");
            if(!text(map(d.get("provenance")).get("results_sha256")).equalsIgnoreCase(text(map(map(details.get("source_files")).get("results")).get("sha256")))){result.needs.add("Literature contribution "+id+" does not match its currently registered source hash");continue;}
            var origin=Map.of("supplied_artifact",id,"manifest",c.project().relativize(dir.resolve("manifest.yaml")).toString().replace('\\','/'),"manifest_sha256",FollowupSupport.hash(dir.resolve("manifest.yaml")),"artifact_sha256",FollowupSupport.hash(file));
            for(Object raw:list(normalized.get("evidence"))) {
                var q=new LinkedHashMap<String,Object>(map(raw));q.put("supplied_source",origin);
                if("supplied_bibliography".equals(q.get("kind"))){result.bibliography.add(q);continue;}
                boolean eligible=Boolean.TRUE.equals(q.get("query_complete"))&&"Europe PMC".equals(q.get("provider"))&&StudySuppliedLiterature.completed(q);
                String key=StudySuppliedLiterature.key(q);
                if(eligible&&result.reusable.putIfAbsent(key,q)!=null)throw new IOException("Ambiguous supplied literature search versions for one exact scope/query; choose one source explicitly");
                result.audit.add(Map.of("supplied_artifact",id,"supplied_query_id",q.get("supplied_query_id"),"scope_key",key,"eligible_for_exact_reuse",eligible,"declared_status",q.get("declared_status"),"declared_coverage",q.get("declared_coverage"),"source",origin));
            }
        }
        return result;
    }
    List<Object> bibliography(){return bibliography;}
    List<String> needs(){return needs;}
    Map<String,Object> take(String feature,String symbol,String term,String strategy,String query,Map<String,Object> scope) {
        var expected=new LinkedHashMap<String,Object>(scope);expected.put("feature_id",feature);expected.put("gene_symbol",symbol);expected.put("phenotype",scope.isEmpty()?term:"");expected.put("search_strategy",strategy);expected.put("query",query);
        String key=StudySuppliedLiterature.key(expected);var found=reusable.get(key);if(found==null)return Map.of();used.add(key);
        var row=new LinkedHashMap<String,Object>(found);row.put("reuse_disposition","exact_scope_query_reused_without_native_request");return row;
    }
    Map<String,Object> report(){return Map.of("schema_version",1,"supplied_queries",audit,"reused_queries",used.size(),"eligible_unused_queries",reusable.keySet().stream().filter(k->!used.contains(k)).toList(),"needs",needs,"interpretation","Only exact public query, strategy, feature/gene/phenotype and method/cohort/reference scope matches suppress a native request. Provider must be Europe PMC and all declared hit records must be supplied. Source dates and reuse reasons remain explicit; metadata identity and scientific/search sufficiency require review. Unmatched or partial searches remain contextual bibliography and do not establish current query coverage.");}
}
