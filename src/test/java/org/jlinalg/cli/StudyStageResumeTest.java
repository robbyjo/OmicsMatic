/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

class StudyStageResumeTest {
    @TempDir Path root;
    private final StudyProject.Actor actor = StudyProject.Actor.human("fixture-scientist");

    @Test void downstreamFileArrivalReusesFitsAndRerunGivesIdenticalScientificResults() throws Exception {
        prepare();
        var first = run(true);
        var originals = manifests(first.runId());
        assertEquals(7, originals.size());
        var firstBytes = new LinkedHashMap<String,byte[]>();
        for (Path path : originals.values()) firstBytes.put(path.toString(),Files.readAllBytes(path));
        assertFalse(Files.readString(originals.get("validate_harmonize").resolveSibling("inputs.tsv")).contains("exposure"));
        assertTrue(Files.readString(root.resolve("runs").resolve(first.runId()).resolve("input-availability.yaml")).contains("missing"));
        Files.writeString(root.resolve("exposure.tsv"), "new downstream data\n");
        var resumed = run(true);
        assertEquals(4, resumed.reused()); assertEquals(3,resumed.completed());
        var resumedManifests = manifests(resumed.runId());
        for (String type : originals.keySet()) {
            if (!StudyStageInputs.core(type)&&!type.equals("assay_qc")) continue;
            var old = load(originals.get(type)); var now = load(resumedManifests.get(type));
            assertEquals(old.get("output_sha256"),now.get("output_sha256"));
            assertEquals(old.get("execution_resolved_study_hash"),now.get("execution_resolved_study_hash"));
            assertEquals(type.equals("assay_qc")?"native_assay_source_v1":"native_cohort_preparation_v1",now.get("input_scope"));
        }
        var rerun = run(false);
        assertEquals(0,rerun.reused()); assertEquals(7,rerun.completed());
        assertArrayEquals(Files.readAllBytes(originals.get("association").resolveSibling("standard-results/data.tsv")),
            Files.readAllBytes(manifests(rerun.runId()).get("association").resolveSibling("standard-results/data.tsv")));
        for (var entry : firstBytes.entrySet()) assertArrayEquals(entry.getValue(),Files.readAllBytes(Path.of(entry.getKey())));
        StudyProject.verify(root);
    }

    @Test void reportingAmendmentPreservesOriginalExecutionHashWhileChangedModelRerunsFits() throws Exception {
        prepare(); var first = run(true);
        String oldHash = String.valueOf(StudyProject.status(root).get("resolved_study_hash"));
        amend("reporting", "configure:\n    project:\n      manuscript_policy: {max_main_words: 10000}\n");
        var amended = run(true);
        assertEquals(4,amended.reused());
        var manifest = load(manifests(amended.runId()).get("association"));
        assertEquals(oldHash,manifest.get("execution_resolved_study_hash"));
        assertNotEquals(oldHash,manifest.get("current_resolved_study_hash"));
        amend("model", "update:\n    hypotheses:\n      - id: H1\n        analysis:\n          association: {model: ols, threads: 2}\n");
        var changed = run(true);
        assertEquals(1,changed.reused());
        assertEquals(6,changed.completed());
        assertFalse(Boolean.TRUE.equals(load(manifests(changed.runId()).get("association")).get("reused_from_cache")));
        assertTrue(Files.isDirectory(root.resolve("runs").resolve(first.runId())));
    }

    @Test void missingCoreInputWaitsAndStrictValidationRemainsStrict() throws Exception {
        prepare(); Files.delete(root.resolve("matrix.tsv"));
        assertFalse(StudyValidator.validate(root.resolve("study.yaml"),true).valid());
        assertTrue(StudyValidator.validateForExecution(root.resolve("study.yaml")).valid());
        var result = run(true);
        assertEquals(0,result.failed()); assertEquals(5,result.waiting());
        assertEquals("waiting_for_input",result.state());
        assertTrue(load(manifests(result.runId()).get("validate_harmonize")).get("summary").toString().contains("absent"));
    }

    @Test void actualInputChangesInvalidateUpstreamAndDefaultExecutorsRetainFullScope() throws Exception {
        prepare(); run(true);
        Files.writeString(root.resolve("phenotype.tsv"), Files.readString(root.resolve("phenotype.tsv")).replace("S11\t11.2","S11\t12.2"));
        var changed = run(true); assertEquals(1,changed.reused()); assertEquals(6,changed.completed());
        assertTrue(Boolean.TRUE.equals(load(manifests(changed.runId()).get("assay_qc")).get("reused_from_cache")));
        assertFalse(Boolean.TRUE.equals(load(manifests(changed.runId()).get("association")).get("reused_from_cache")));
        StudyOperationExecutor wrapper = context -> new StudyCliExecutor().execute(context);
        assertFalse(wrapper.usesNativeStageInputContracts());
        var full = StudyExecutionEngine.run(root,actor,wrapper,true);
        assertEquals(0,full.reused());
        for (Path path : manifests(full.runId()).values()) assertEquals("full_study",load(path).get("input_scope"));
    }

    @Test void autoAllowsUnavailableDownstreamFilesWithoutRewritingInputs() throws Exception {
        prepare();
        byte[] original = Files.readAllBytes(root.resolve("study.yaml"));
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
        int status = JLinAlgCli.run(new String[]{"study","auto",root.resolve("study.yaml").toString(),"--project",root.toString()},new PrintStream(out),new PrintStream(err));
        assertEquals(0,status,err.toString());
        assertTrue(out.toString().contains("state: completed"),out.toString());
        assertArrayEquals(original,Files.readAllBytes(root.resolve("study.yaml")));
    }

    private void prepare() throws Exception {
        StringBuilder matrix = new StringBuilder("feature_id"), values = new StringBuilder("\nG1"), phenotype = new StringBuilder("id\ty\n");
        for(int i=0;i<12;i++){ matrix.append("\tS").append(i); values.append('\t').append(i); phenotype.append("S").append(i).append('\t').append(i+(i%2==0?-.2:.2)).append('\n'); }
        Files.writeString(root.resolve("matrix.tsv"),matrix.append(values).append('\n'));
        Files.writeString(root.resolve("phenotype.tsv"),phenotype);
        Files.writeString(root.resolve("annotation.tsv"),"feature_id\tgene_symbol\tgene_id\nG1\tSYNTH1\tENSG1\n");
        Files.writeString(root.resolve("study.yaml"),"""
            schema_version: 1
            project: {id: fixture, title: Stage resume fixture, description: Synthetic execution inputs.}
            resources: {network: offline, downloads: deny}
            datasets:
              - id: data
                type: expression
                stage: processed_matrix
                files: {matrix: matrix.tsv, phenotype: phenotype.tsv, annotation: annotation.tsv, exposure: exposure.tsv}
            hypotheses:
              - id: H1
                description: Synthetic linear association.
                outcome: y
                inputs: {discovery: [data]}
                analysis:
                  mode: manual
                  goals: [association]
                  association: {model: ols}
            """);
        StudyProject.resolve(root.resolve("study.yaml"),root,actor); approve();
    }
    private void approve() throws Exception {var plan=StudyPlanner.create(root,actor);StudyPlanner.approve(root,plan.id(),actor,true);}
    private StudyExecutionEngine.RunResult run(boolean resume) throws Exception {return StudyExecutionEngine.run(root,actor,new StudyCliExecutor(),resume);}
    private void amend(String id,String changes) throws Exception {
        Path file=root.resolve("amendment-"+id+".yaml");
        Files.writeString(file,"schema_version: 1\namendment:\n  id: "+id+"\n  base_study_hash: "+StudyProject.status(root).get("resolved_study_hash")+"\nchanges:\n  "+changes);
        StudyAmendments.apply(file,root,actor);approve();
    }
    private Map<String,Path> manifests(String run) throws Exception {
        var result=new LinkedHashMap<String,Path>();
        try(var paths=Files.walk(root.resolve("runs").resolve(run).resolve("operations"))){for(Path p:paths.filter(p->p.getFileName().toString().equals("manifest.yaml")).toList()) result.put(String.valueOf(load(p).get("operation_type")),p);}
        return result;
    }
    private static Map<String,Object> load(Path path) throws Exception {try(var reader=Files.newBufferedReader(path)){return new Yaml().load(reader);}}
}
