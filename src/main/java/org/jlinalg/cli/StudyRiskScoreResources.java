/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyRiskScorePolicy;
/** Participant, external model, dosage-allele and development-identity contents enter cache identity. */
public final class StudyRiskScoreResources {
    private StudyRiskScoreResources() { }
    public static Map<String,String> inputHashes(Path root,Map<String,Object> study,Map<String,Object> operation)throws IOException {
        var hashes=new LinkedHashMap<String,String>();if(!List.of("risk_score_evaluation","risk_score_related_phenotypes").contains(operation.get("type"))||operation.containsKey("supplied_result_id"))return hashes;
        var hypothesis=StudyPublicEvidence.list(study.get("hypotheses")).stream().map(StudyPublicEvidence::map).filter(h->h.get("id").equals(operation.get("hypothesis"))).findFirst().orElseThrow();var p=StudyRiskScorePolicy.resolve(StudyPublicEvidence.map(StudyPublicEvidence.map(hypothesis.get("analysis")).get("risk_score")));
        for(String key:List.of("file","weights","model","alleles","training_ids"))if(p.containsKey(key)){Path path=root.resolve(String.valueOf(p.get(key)));hashes.put("risk_score:"+key,Files.isRegularFile(path)?FollowupSupport.hash(path):"missing");}
        if(p.containsKey("model")){Path model=root.resolve(String.valueOf(p.get("model")));if(Files.isRegularFile(model)&&Files.size(model)<=2097152L){var metadata=StudyYaml.loadArtifact(model);Path sidecar=model.toAbsolutePath().getParent().resolve(String.valueOf(metadata.get("training_ids"))).normalize();if(!sidecar.startsWith(model.toAbsolutePath().getParent()))throw new IOException("Frozen model identity path escapes its model folder");hashes.put("risk_score:model_training_ids",Files.isRegularFile(sidecar)?FollowupSupport.hash(sidecar):"missing");}}
        if(p.containsKey("weights")){Path sidecar=root.resolve(String.valueOf(p.get("weights"))+".training-ids.tsv");hashes.put("risk_score:weight_training_ids",Files.isRegularFile(sidecar)?FollowupSupport.hash(sidecar):"missing");}
        if(p.containsKey("genotypes")){var g=StudyPublicEvidence.map(p.get("genotypes"));long maximum=((Number)g.get("maximum_source_mib")).longValue()*1048576L;for(Object raw:StudyPublicEvidence.list(g.get("sources"))){var source=StudyPublicEvidence.map(raw);if(source.containsKey("bfile"))for(String extension:List.of("bed","bim","fam")){Path path=root.resolve(String.valueOf(source.get("bfile"))+"."+extension);hashes.put("risk_score:genotypes:"+source.get("id")+":"+extension,fingerprint(path,maximum));}else for(String role:List.of("file","samples"))if(source.containsKey(role)){Path path=root.resolve(String.valueOf(source.get(role)));hashes.put("risk_score:genotypes:"+source.get("id")+":"+role,fingerprint(path,role.equals("samples")?((Number)g.get("maximum_metadata_mib")).longValue()*1048576L:maximum));}}}
        return hashes;
    }
    private static String fingerprint(Path path,long maximum)throws IOException{return !Files.isRegularFile(path)?"missing":Files.size(path)>maximum?"over_registered_byte_limit:"+Files.size(path):FollowupSupport.hash(path);}
}
