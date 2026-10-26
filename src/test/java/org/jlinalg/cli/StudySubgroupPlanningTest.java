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

class StudySubgroupPlanningTest {
    @TempDir Path root;
    private List<Map<String,Object>> plan(String extra,String datasets)throws Exception {
        Path source=root.resolve("study.yaml");Files.writeString(source,"""
            schema_version: 1
            project: {id: interaction-plan, title: Synthetic interaction plan, description: No biological evidence.}
            resources: {network: offline, downloads: deny}
            """+datasets+"\nhypotheses:\n  - id: H\n    description: Prespecified independent synthetic effect modification.\n    outcome: y\n    inputs: {discovery: [a]}\n    analysis:\n      mode: manual\n      goals: [subgroup_interactions, impact_assessment]\n"+extra);
        var resolved=StudyResolver.resolve(source);var analysis=map(map(list(resolved.document().get("hypotheses")).get(0)).get("analysis"));
        assertEquals("BY",map(analysis.get("subgroup_interactions")).get("adjustment"));
        return list(StudyPlanner.compile(resolved).document().get("operations")).stream().map(StudyPublicEvidence::map).toList();
    }
    private static String matrix(){return "datasets:\n  - {id: a, type: expression, stage: processed_matrix, files: {matrix: matrix.tsv, phenotype: phenotype.tsv}}";}
    private static String nativePolicy(){return "      association: {model: ols}\n      subgroup_interactions: {groups: [{id: sex, column: sex, levels: [F, M], rationale: Prespecified independent synthetic contrast.}], independent_rows: true}\n";}
    @Test void interactionOnlyGoalCreatesVerifiedQcDependenciesWithoutRunningPrimaryAssociation()throws Exception {
        var ops=plan(nativePolicy(),matrix());var qc=ops.stream().filter(o->o.get("type").equals("participant_qc")).findFirst().orElseThrow();var interaction=ops.stream().filter(o->o.get("type").equals("subgroup_interactions")).findFirst().orElseThrow();
        assertEquals(List.of("a"),interaction.get("datasets"));assertTrue(list(interaction.get("depends_on")).contains(qc.get("id")));assertEquals(true,interaction.get("allow_incomplete_dependencies"));assertTrue(ops.stream().noneMatch(o->o.get("type").equals("association")));
        var inventory=ops.stream().filter(o->o.get("type").equals("finding_inventory")).findFirst().orElseThrow();assertTrue(list(inventory.get("depends_on")).contains(interaction.get("id")));
    }
    @Test void wholeSuppliedInteractionReplacesNativeTestsAndDoesNotRequireParticipantData()throws Exception {
        var ops=plan("""
                  supplied_results:
                    - id: external
                      kind: subgroup_interactions
                      files: {results: external.tsv}
                      scope: {phenotype: y}
                      method: {name: Externally completed interaction family}
                      family: {id: original, coverage: complete, tested_count: 20, adjustment: BY, adjusted_p_from_full_family: true}
            ""","datasets:\n  - {id: a, type: generic, stage: association_results, files: {results: primary.tsv}}");
        var interactions=ops.stream().filter(o->o.get("type").equals("subgroup_interactions")).toList();assertEquals(1,interactions.size());assertEquals("external",interactions.get(0).get("supplied_result_id"));assertTrue(ops.stream().noneMatch(o->o.get("type").equals("participant_qc")));
    }
    @Test void disabledPolicyDoesNotCreateNumericalWorkEvenWhenTheGoalRemainsRegistered()throws Exception {
        var ops=plan("      subgroup_interactions: {enabled: false}\n",matrix());assertTrue(ops.stream().noneMatch(o->o.get("type").equals("association"))); // Executor records not_applicable; no implicit model.
        assertEquals(false,org.jlinalg.study.StudySubgroupInteractionPolicy.resolve(Map.of()).get("enabled"));
    }
}
