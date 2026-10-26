/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jlinalg.study.StudyGwasEnrichmentPolicy;

/** External full-summary and LD/reference moments are part of study cache identity. */
public final class StudyGwasEnrichmentResources {
    private StudyGwasEnrichmentResources() { }
    public static Map<String,String> inputHashes(Path root,Map<String,Object> study,Map<String,Object> operation)throws IOException{
        var hashes=new LinkedHashMap<String,String>();if(!"gwas_enrichment".equals(operation.get("type"))||operation.containsKey("supplied_result_id"))return hashes;
        var hypothesis=StudyPublicEvidence.list(study.get("hypotheses")).stream().map(StudyPublicEvidence::map).filter(h->h.get("id").equals(operation.get("hypothesis"))).findFirst().orElseThrow();
        var p=StudyGwasEnrichmentPolicy.resolve(StudyPublicEvidence.map(StudyPublicEvidence.map(hypothesis.get("analysis")).get("gwas_enrichment")));
        for(String role:new String[]{"ld_scores","annotations","overlap"})if(p.containsKey(role))add(hashes,root,role,p.get(role));
        var s=StudyPublicEvidence.map(p.get("summary"));if(s.containsKey("file"))add(hashes,root,"summary",s.get("file"));return hashes;
    }
    private static void add(Map<String,String> hashes,Path root,String role,Object value)throws IOException{Path path=root.resolve(String.valueOf(value));hashes.put("gwas_enrichment:"+role,Files.isRegularFile(path)?FollowupSupport.hash(path):"missing");}
}
