/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyProteinPolicy;

/** Hash actual protein sequences/models/confidence and configured predictor files, before cache reuse. */
public final class StudyProteinResources {
    private StudyProteinResources() { }
    public static Map<String,String> inputHashes(Path root,Map<String,Object> study,Map<String,Object> operation)throws IOException {
        var hashes=new LinkedHashMap<String,String>();if(!List.of("protein_structure","protein_structure_assessment").contains(operation.get("type"))||operation.containsKey("supplied_result_id"))return hashes;var h=list(study.get("hypotheses")).stream().map(StudyPublicEvidence::map).filter(v->v.get("id").equals(operation.get("hypothesis"))).findFirst().orElseThrow();var p=StudyProteinPolicy.resolve(map(map(h.get("analysis")).get("protein_structure")));
        for(String key:List.of("manifest","sequences"))if(p.containsKey(key))hash(hashes,root,"protein:"+key,text(p.get(key)));
        if(p.containsKey("manifest")){Path source=root.resolve(text(p.get("manifest")));if(Files.isRegularFile(source)&&Files.size(source)<=2097152L){var manifest=StudyProteinInputs.manifest(root,p);int ordinal=0;for(Object raw:list(manifest.get("targets"))){var target=map(raw);int modelNumber=0;for(Object model:list(target.get("models"))){var m=map(model);for(String key:StudyProteinInputs.MODEL_FILES)if(m.containsKey(key))hash(hashes,root,"protein:"+ordinal+":"+modelNumber+":"+key,text(m.get(key)));modelNumber++;}ordinal++;}}}
        int i=0;var prediction=map(p.get("prediction"));for(Object arg:list(prediction.get("command"))){String value=text(arg);if(!value.contains("{")){Path file=i==0?StudyProteinPrediction.executable(root,value):root.resolve(value);if(Files.isRegularFile(file))hashes.put("protein:prediction_argument:"+i,FollowupSupport.hash(file));}i++;}for(Object raw:list(prediction.get("input_files")))hash(hashes,root,"protein:prediction_input:"+text(raw),text(raw));return hashes;
    }
    private static void hash(Map<String,String> hashes,Path root,String name,String value)throws IOException{Path file=root.resolve(value);hashes.put(name,Files.isRegularFile(file)?FollowupSupport.hash(file):"missing");}
}
