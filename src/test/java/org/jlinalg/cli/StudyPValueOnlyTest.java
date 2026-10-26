/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jlinalg.study.StudyOperationExecutor.Context;
import org.jlinalg.study.StudyOperationExecutor.State;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyPValueOnlyTest {
    @TempDir Path root;
    private Context context(String output,String type,Map<String,Object> columns,Map<String,Path> deps)throws Exception {
        var study=Map.of("resources",Map.of("directory","fixture-resources","network","offline","downloads","deny"),"datasets",List.of(Map.of("id","a","type","generic","stage","association_results","files",Map.of("results","a.tsv"),"columns",columns)),"hypotheses",List.of(Map.of("id","H","analysis",Map.of("harmonization",Map.of("effect_outliers",Map.of("enabled",false))))));
        return new Context(root,Files.createDirectory(root.resolve(output)),Map.of("id",output,"type",type,"hypothesis","H","datasets",List.of("a")),study,Map.of(),deps);
    }
    @Test void explicitTestOnlyImportsKeepAllTestsThroughQcPrimarySelectionAndInventory()throws Exception {
        Files.writeString(root.resolve("a.tsv"),"feature_id\tp_value\nG1\t1e-8\nG2\t.9\n");var c=context("import","validate_harmonize",Map.of("result_mode","p_value_only"),Map.of());var result=new StudyCliExecutor().execute(c);assertEquals(State.COMPLETE,result.state());
        var table=DelimitedData.read(c.outputDirectory().resolve("standard-results/a.tsv"));assertEquals(2,table.rows().size());assertTrue(table.rows().stream().allMatch(row->row[table.column("beta")].equals("NA")&&row[table.column("result_type")].equals("p_value_only")));
        StudyTestSources.seal(c.outputDirectory(),"validate_harmonize","import","H");var primary=context("primary","candidate_prioritization",Map.of(),Map.of("import",c.outputDirectory()));assertEquals(2,StudyPrimaryResults.official(primary).probabilities().size());assertEquals(State.COMPLETE,new StudyCliExecutor().execute(primary).state());
        var inventory=context("inventory","finding_inventory",Map.of(),Map.of("import",c.outputDirectory()));var resultInventory=new StudyFindingInventory().execute(inventory);assertTrue(Files.readString(inventory.outputDirectory().resolve("finding-inventory.jsonl")).contains("p_value_only"));assertNotEquals(State.WAITING_FOR_INPUT,resultInventory.state());
        var meta=context("meta","meta_analysis",Map.of(),Map.of("import",c.outputDirectory()));assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(meta).state());assertTrue(Files.readString(meta.outputDirectory().resolve("request.yaml")).contains("P-only"));
        var gsea=context("gsea","ranked_gsea",Map.of(),Map.of("import",c.outputDirectory()));var pending=new StudyCliExecutor().execute(gsea);assertEquals(State.WAITING_FOR_INPUT,pending.state());
    }
    @Test void mixedRowsRequireExplicitResultTypeAndContradictoryEffectsAreRejected()throws Exception {
        Files.writeString(root.resolve("a.tsv"),"feature_id\tbeta\tse\tp_value\tkind\nG1\tNA\tNA\t.01\tp_value_only\nG2\t1\t.2\t.1\tsigned_effect\n");var c=context("mixed","validate_harmonize",Map.of("result_metadata",Map.of("result_type","kind")),Map.of());assertEquals(State.COMPLETE,new StudyCliExecutor().execute(c).state());assertEquals(2,DelimitedData.read(c.outputDirectory().resolve("standard-results/a.tsv")).rows().size());
        Files.writeString(root.resolve("a.tsv"),"feature_id\tBETA\tSE\tp_value\nG1\t1\t.2\t.01\n");var bad=context("bad","validate_harmonize",Map.of("result_mode","p_value_only"),Map.of());assertThrows(java.io.IOException.class,()->new StudyCliExecutor().execute(bad));
    }
    @Test void unsignedPValuesAreNotInferredFromMissingEffectsWithoutAnExplicitDeclaration()throws Exception {
        Files.writeString(root.resolve("a.tsv"),"feature_id\tp_value\nG1\t.01\n");var c=context("undeclared","validate_harmonize",Map.of(),Map.of());assertThrows(IllegalArgumentException.class,()->new StudyCliExecutor().execute(c));
    }
    @Test void incompleteNativeAssociationCannotDisappearBehindACompletedHarmonizedSource()throws Exception {
        Path complete=Files.createDirectory(root.resolve("harmonized")),partial=Files.createDirectory(root.resolve("partial"));
        for(Path path:List.of(complete,partial)){Files.createDirectory(path.resolve("standard-results"));Files.writeString(path.resolve("standard-results/a.tsv"),"feature_id\tbeta\tse\tp_value\nG1\t1\t.1\t1e-8\n");}
        StudyTestSources.seal(complete,"validate_harmonize","harmonized","H");StudyTestSources.seal(partial,"association","partial","H");var manifest=new java.util.LinkedHashMap<String,Object>(StudyYaml.loadArtifact(partial.resolve("manifest.yaml")));manifest.put("state","waiting_for_input");manifest.put("details",Map.of("primary_selection_eligible",false));StudyPublicEvidence.write(partial.resolve("manifest.yaml"),manifest);
        var c=context("blocked","candidate_prioritization",Map.of(),Map.of("harmonized",complete,"partial",partial));assertEquals(State.WAITING_FOR_INPUT,new StudyCliExecutor().execute(c).state());assertFalse(Files.exists(c.outputDirectory().resolve("candidate-prioritization.tsv")));
    }
}
