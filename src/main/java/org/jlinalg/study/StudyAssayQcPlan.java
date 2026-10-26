/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Attach source-only QC only to operations that actually read molecular matrices. */
final class StudyAssayQcPlan {
    private StudyAssayQcPlan() { }
    static void attach(Map<String,Object> study, List<Object> operations) {
        var hypotheses=new LinkedHashMap<String,Map<String,Object>>();
        for(Object raw:StudyIO.list(study.get("hypotheses"),"hypotheses")) {var h=StudyIO.map(raw,"hypothesis");hypotheses.put(String.valueOf(h.get("id")),h);}
        var datasets=new LinkedHashMap<String,Map<String,Object>>();
        for(Object raw:StudyIO.list(study.get("datasets"),"datasets")) {var d=StudyIO.map(raw,"dataset");datasets.put(String.valueOf(d.get("id")),d);}
        var producers=new LinkedHashMap<String,Map<String,Object>>();
        var result=new ArrayList<Object>();
        for(Object raw:operations) {
            var op=StudyIO.map(raw,"operation");var h=hypotheses.get(String.valueOf(op.get("hypothesis")));
            String type=String.valueOf(op.get("type"));
            if(h!=null&&!op.containsKey("supplied_result_id")&&Set.of("participant_qc","association","network_analysis","subgroup_interactions","risk_score_evaluation","risk_score_related_phenotypes","ranked_gsea").contains(type)
                    &&!StudyIO.optionalMap(op.get("configuration"),"configuration").containsKey("command")) {
                var analysis=StudyIO.map(h.get("analysis"),"analysis");boolean matrixScore=true;
                if(type.equals("ranked_gsea"))matrixScore=StudyIO.optionalMap(analysis.get("gsea"),"gsea").containsKey("phenotype");
                if(type.startsWith("risk_score")) {var score=StudyRiskScorePolicy.resolve(StudyIO.optionalMap(analysis.get("risk_score"),"risk_score"));matrixScore=!score.containsKey("file")&&!"scores".equals(score.get("mode"))&&!StudyIO.optionalList(score.get("features"),"features").isEmpty();}
                var bindings=new LinkedHashMap<String,Object>();var deps=new ArrayList<>(StudyIO.list(op.get("depends_on"),"depends_on"));
                if(matrixScore)for(Object id:StudyIO.list(op.get("datasets"),"datasets")) {
                    var d=datasets.get(String.valueOf(id));if(d==null||!StudyAssayQcPolicy.MODALITIES.contains(d.get("type"))||!Set.of("raw_quantified","quantified_matrix","processed_matrix").contains(d.get("stage")))continue;
                    var policy=StudyAssayQcPolicy.forDataset(StudyAssayQcPolicy.resolve(StudyIO.optionalMap(analysis.get("assay_qc"),"assay_qc")),String.valueOf(id));
                    if(!Boolean.TRUE.equals(policy.get("enabled")))continue;
                    String key=id+":"+StudyAssayQcPolicy.fingerprint(policy);var producer=producers.get(key);
                    if(producer==null) {producer=new LinkedHashMap<>();String name="assay-qc-"+StudyIO.hash(key).substring(0,20);var descriptor=StudyOperationRegistry.descriptor("assay_qc");
                        producer.put("id",name);producer.put("type","assay_qc");producer.put("hypothesis",h.get("id"));producer.put("dataset_id",id);producer.put("datasets",List.of(id));producer.put("depends_on",List.of());producer.put("executor",descriptor.executor());producer.put("outputs",descriptor.outputs());producer.put("diagnostics",descriptor.diagnostics());producer.put("status","planned");producer.put("assay_qc_policy",policy);producer.put("scope","matrix_source");producers.put(key,producer);result.add(producer);}
                    bindings.put(String.valueOf(id),producer.get("id"));if(!deps.contains(producer.get("id")))deps.add(producer.get("id"));
                }
                if(!bindings.isEmpty()){op.put("assay_qc_producers",bindings);op.put("depends_on",deps);}
            }
            result.add(op);
        }
        // Explicit source QC is useful even when no association is requested.
        for(var h:hypotheses.values())if(!"retired".equals(h.get("status"))&&StudyIO.list(StudyIO.map(h.get("analysis"),"analysis").get("goals"),"goals").contains("assay_qc")) {
            var analysis=StudyIO.map(h.get("analysis"),"analysis");
            for(Object values:StudyIO.map(h.get("inputs"),"inputs").values())for(Object id:StudyIO.list(values,"input role")) {
                var d=datasets.get(String.valueOf(id));if(d==null||!StudyAssayQcPolicy.MODALITIES.contains(d.get("type"))||!Set.of("raw_quantified","quantified_matrix","processed_matrix").contains(d.get("stage")))continue;
                var p=StudyAssayQcPolicy.forDataset(StudyAssayQcPolicy.resolve(StudyIO.optionalMap(analysis.get("assay_qc"),"assay_qc")),String.valueOf(id));String key=id+":"+StudyAssayQcPolicy.fingerprint(p);if(!Boolean.TRUE.equals(p.get("enabled"))||producers.containsKey(key))continue;
                var descriptor=StudyOperationRegistry.descriptor("assay_qc");var op=new LinkedHashMap<String,Object>();op.put("id","assay-qc-"+StudyIO.hash(key).substring(0,20));op.put("type","assay_qc");op.put("hypothesis",h.get("id"));op.put("dataset_id",id);op.put("datasets",List.of(id));op.put("depends_on",List.of());op.put("executor",descriptor.executor());op.put("outputs",descriptor.outputs());op.put("diagnostics",descriptor.diagnostics());op.put("status","planned");op.put("assay_qc_policy",p);op.put("scope","matrix_source");producers.put(key,op);result.add(op);
            }
        }
        // Reports and study-wide inventories must see newly attached source QC.
        for(int at=0;at<result.size();at++){var op=StudyIO.map(result.get(at),"operation");if(!Set.of("report","finding_inventory").contains(op.get("type")))continue;var deps=new ArrayList<>(StudyIO.list(op.get("depends_on"),"depends_on"));for(var p:producers.values())if("__study__".equals(op.get("hypothesis"))||op.get("hypothesis").equals(p.get("hypothesis")))if(!deps.contains(p.get("id")))deps.add(p.get("id"));op.put("depends_on",deps);result.set(at,op);}
        operations.clear();operations.addAll(producers.values());for(Object raw:result)if(!"assay_qc".equals(StudyIO.map(raw,"operation").get("type")))operations.add(raw);
    }
}
