/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jlinalg.study.StudyFindingGroupingPolicy;

/** External LD-table edits invalidate the editorial inventory and its downstream comparisons. */
public final class StudyFindingGroupingResources {
    private StudyFindingGroupingResources() { }
    public static Map<String,String> inputHashes(Path project,Map<String,Object> study,Map<String,Object> operation)throws IOException {
        var hashes=new LinkedHashMap<String,String>();if(!"finding_inventory".equals(operation.get("type")))return hashes;var p=StudyFindingGroupingPolicy.resolve(StudyPublicEvidence.map(StudyPublicEvidence.map(study.get("project")).get("finding_grouping")));if(!Boolean.TRUE.equals(p.get("enabled")))return hashes;
        for(Object raw:StudyFindingGrouping.activeReferences(study,p)){var r=StudyPublicEvidence.map(raw);for(String role:java.util.List.of("variants","pairs")){Path path=project.resolve(StudyPublicEvidence.text(r.get(role))).normalize();hashes.put("finding_ld:"+r.get("id")+":"+role,Files.isRegularFile(path)?FollowupSupport.hash(path):"missing");}}return hashes;
    }
}
