/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jlinalg.study.StudyAssayQcPolicy;

/** Content identity of non-matrix assay-QC resources. */
public final class StudyAssayQcResources {
    private StudyAssayQcResources() { }
    public static Map<String,String> inputHashes(Path project,Map<String,Object> study,Map<String,Object> operation) throws IOException {
        var hashes=new LinkedHashMap<String,String>();if(!"assay_qc".equals(operation.get("type")))return hashes;
        var p=StudyAssayQcPolicy.resolve(StudyPublicEvidence.map(operation.get("assay_qc_policy")));
        for(String key:java.util.List.of("supplied_report","supplied_samples","supplied_features"))if(p.containsKey(key))add(project,hashes,key,String.valueOf(p.get(key)),p);
        for(Object raw:StudyPublicEvidence.list(study.get("datasets"))){var d=StudyPublicEvidence.map(raw);if(!d.get("id").equals(operation.get("dataset_id")))continue;var files=StudyPublicEvidence.map(d.get("files"));if(!"none".equals(p.get("detection_kind"))&&files.containsKey("assay_detection"))add(project,hashes,"detection",String.valueOf(files.get("assay_detection")),p);}
        return hashes;
    }
    private static void add(Path project,Map<String,String> hashes,String key,String file,Map<String,Object> p)throws IOException {Path path=project.resolve(file);String value="missing";if(Files.isRegularFile(path)){long size=Files.size(path);value=size>((Number)p.get("maximum_source_mib")).longValue()*1048576?"oversize:"+size+":"+Files.getLastModifiedTime(path).toMillis():FollowupSupport.hash(path);}hashes.put("assay_qc:"+key,value);}
}
