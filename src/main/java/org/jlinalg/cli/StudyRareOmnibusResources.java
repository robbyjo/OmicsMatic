package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
/** Explicit omnibus mapping/eligibility files participate in operation cache identity. */
public final class StudyRareOmnibusResources {
    private StudyRareOmnibusResources() { }
    public static Map<String,String> inputHashes(Path project,Map<String,Object> study,Map<String,Object> operation)throws IOException {
        var out=new LinkedHashMap<String,String>();if(!"rare_set_omnibus".equals(operation.get("type")))return out;
        for(Object raw:StudyPublicEvidence.list(study.get("hypotheses"))){var h=StudyPublicEvidence.map(raw);if(!h.get("id").equals(operation.get("hypothesis")))continue;var p=org.jlinalg.study.StudyRareOmnibusPolicy.resolve(StudyPublicEvidence.map(StudyPublicEvidence.map(h.get("analysis")).get("rare_omnibus")));for(String field:List.of("gene_map","component_eligibility"))if(p.containsKey(field)){Path file=project.resolve(StudyPublicEvidence.text(p.get(field)));out.put("rare_omnibus:"+field,Files.isRegularFile(file)?FollowupSupport.hash(file):"missing");}}
        return out;
    }
}
