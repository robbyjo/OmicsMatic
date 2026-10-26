/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.jlinalg.cli.StudyPublicEvidence.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.jlinalg.study.StudyAssayQcPolicy;
import org.jlinalg.study.StudyOperationExecutor.Context;

/** Exact dataset/policy/source binding; shared source QC need not share a phenotype. */
final class StudyAssayQcSources {
    private StudyAssayQcSources() { }
    static Path matrix(Context c,Map<String,Object> dataset) throws IOException {
        Path original=c.project().resolve(text(map(dataset.get("files")).get("matrix")));
        String id=text(dataset.get("id")),producer=text(map(c.operation().get("assay_qc_producers")).get(id));
        if(producer.isBlank())return original;
        Path directory=c.dependencyDirectories().get(producer);
        if(directory==null||!Files.isRegularFile(directory.resolve("manifest.yaml")))throw new IOException("Required assay QC producer is unavailable");
        var manifest=StudyYaml.loadArtifact(directory.resolve("manifest.yaml"));
        if(!"assay_qc".equals(manifest.get("operation_type"))||!producer.equals(manifest.get("operation_id"))||!"complete".equals(manifest.get("state")))throw new IOException("Required assay QC producer is incomplete or has an inconsistent identity");
        var hashes=map(manifest.get("output_sha256"));
        for(var output:hashes.entrySet())StudyFindingInventory.verified(directory,output.getKey(),text(output.getValue()));
        if(!hashes.containsKey("assay-qc.yaml"))throw new IOException("Required assay QC report is unsealed");
        var report=StudyYaml.loadArtifact(directory.resolve("assay-qc.yaml"));
        var policy=StudyAssayQcPolicy.forDataset(StudyAssayQcPolicy.resolve(map(StudyResultTables.analysis(c).get("assay_qc"))),id);
        if(!id.equals(report.get("dataset_id"))||!Boolean.TRUE.equals(report.get("complete"))||!StudyAssayQcPolicy.fingerprint(policy).equals(report.get("policy_sha256"))||!FollowupSupport.hash(original).equals(report.get("matrix_sha256")))throw new IOException("Required assay QC report does not match the exact current dataset, source and policy");
        var resources=StudyAssayQcResources.inputHashes(c.project(),c.study(),Map.of("type","assay_qc","dataset_id",id,"assay_qc_policy",policy));var sourceHashes=map(report.get("input_sha256"));
        for(var resource:resources.entrySet())if(!resource.getValue().equals(sourceHashes.get(resource.getKey().substring("assay_qc:".length()))))throw new IOException("Required assay QC inputs changed after the sealed source decisions");
        String output=text(report.get("analysis_matrix"));if(output.equals("original_source"))return original;
        if(!output.equals("assay-matrix.tsv")||!hashes.containsKey(output)||!hashes.get(output).equals(report.get("analysis_matrix_sha256")))throw new IOException("Required assay QC matrix is unavailable or inconsistently sealed");
        return StudyFindingInventory.verified(directory,output,text(hashes.get(output)));
    }
}
