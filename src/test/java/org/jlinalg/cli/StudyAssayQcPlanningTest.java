/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyPlanner;
import org.jlinalg.study.StudyResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyAssayQcPlanningTest {
    @TempDir Path root;
    private List<Map<String,Object>> plan(String goals,String policy,String second)throws Exception {
        Files.writeString(root.resolve("study.yaml"),"""
            schema_version: 1
            project: {id: assay-plan, title: Synthetic assay QC, description: No scientific evidence.}
            datasets:
              - {id: a, type: expression, stage: processed_matrix, files: {matrix: matrix.tsv, phenotype: phenotype.tsv}}
            hypotheses:
              - id: H
                description: Synthetic source QC
                outcome: y
                inputs: {discovery: [a]}
                analysis:
                  mode: manual
                  goals: [GOALS]
                  association: {model: ols}
                  assay_qc: POLICY
            """.replace("GOALS",goals).replace("POLICY",policy)+second);
        return list(StudyPlanner.compile(StudyResolver.resolve(root.resolve("study.yaml"))).document().get("operations")).stream().map(StudyPublicEvidence::map).toList();
    }
    @Test void associationsNetworksAndQcShareOneSourceProducerBeforeConsumers()throws Exception {
        var ops=plan("association, network_analysis, impact_assessment","{}","");var producers=ops.stream().filter(o->o.get("type").equals("assay_qc")).toList();assertEquals(1,producers.size());var producer=producers.get(0);assertEquals(producer,ops.get(0));
        for(String type:List.of("participant_qc","association","network_analysis","finding_inventory")){var op=ops.stream().filter(o->o.get("type").equals(type)).findFirst().orElseThrow();assertTrue(list(op.get("depends_on")).contains(producer.get("id")),type);}
    }
    @Test void sourceCanRunAloneOrBeExplicitlyDisabled()throws Exception {
        var ops=plan("assay_qc","{}","");assertEquals(1,ops.stream().filter(o->o.get("type").equals("assay_qc")).count());assertFalse(ops.stream().anyMatch(o->o.get("type").equals("participant_qc")));assertTrue(list(ops.stream().filter(o->o.get("type").equals("report")).findFirst().orElseThrow().get("depends_on")).contains(ops.get(0).get("id")));
        assertFalse(plan("association","{enabled: false}","").stream().anyMatch(o->o.get("type").equals("assay_qc")));
    }
    @Test void sameDatasetPolicyAcrossPhenotypesReusesOutcomeIndependentQc()throws Exception {
        String second="""
              - id: H2
                description: Second phenotype, same source quality rules
                outcome: z
                inputs: {discovery: [a]}
                analysis: {goals: [association], association: {model: ols}}
            """;var ops=plan("association","{}",second);assertEquals(1,ops.stream().filter(o->o.get("type").equals("assay_qc")).count());assertEquals(2,ops.stream().filter(o->o.get("type").equals("association")&&map(o.get("assay_qc_producers")).containsKey("a")).count());
        assertEquals(2,plan("association","{mode: filter}",second).stream().filter(o->o.get("type").equals("assay_qc")).count());
    }
    @Test void unknownDatasetOverrideIsRejected()throws Exception {assertThrows(IllegalArgumentException.class,()->plan("association","{dataset_overrides: {unknown: {mode: filter}}}",""));}
}
