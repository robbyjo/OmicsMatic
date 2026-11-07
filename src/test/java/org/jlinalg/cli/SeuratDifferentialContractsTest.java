/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.SeuratDifferentialContracts.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SeuratDifferentialContractsTest {
    @TempDir Path temp;
    static Map<String,Object> request()throws IOException {
        return object(SimpleJson.parse("""
          {"schema_version":1,"mode":"PAIR","profile":"Seurat-5.6.0-R4.6.1-base","out":"new-out",
          "counts":"counts","features":"features","cells":"cells","metadata":"metadata","group_column":"group",
          "group_levels":["a","b"],"contrast":{"numerator":["a"],"denominator":["b"]},
          "rscript":"missing-Rscript","r_libraries":["private-library"]}
          """),"request");
    }
    static Stream<Arguments> invalidSettings(){return Stream.of(
        Arguments.of("schema_version",2),Arguments.of("schema_version",1.5),Arguments.of("mode","ANALYZE"),Arguments.of("profile","latest"),
        Arguments.of("seed",true),Arguments.of("seed",-1),Arguments.of("min_pct",null),Arguments.of("min_pct",1.1),Arguments.of("min_diff_pct","NaN"),Arguments.of("min_diff_pct",2),
        Arguments.of("logfc_threshold",-1),Arguments.of("log_base",1),Arguments.of("pseudocount",-1),Arguments.of("densify","true"),
        Arguments.of("max_cells_per_group",1.2),Arguments.of("max_cells_per_group",0),Arguments.of("maximum_work",0),Arguments.of("maximum_entries",20_000_001),
        Arguments.of("test","t"),Arguments.of("test_slot","slot=system('x')"),Arguments.of("fc_normalization","guess"),Arguments.of("return_threshold",.05),
        Arguments.of("r_libraries",List.of("same","same")),Arguments.of("group_levels",List.of("a","a")),Arguments.of("extra_R_code","stop('x')"),Arguments.of("tfidf_method",5)
    );}
    @ParameterizedTest @MethodSource("invalidSettings") void invalidOrIgnoredSettingsAreRefused(String field,Object value)throws Exception {var r=request();r.put(field,value);assertThrows(IOException.class,()->validate(r));}
    @Test void defaultsAreExplicitAndNormalizationIsIdempotent()throws Exception {
        var r=validate(request());assertEquals(r,validate(r));assertEquals("native_tfidf",r.get("data_source"));assertEquals("data",r.get("test_slot"));assertEquals("data",r.get("fc_slot"));assertEquals("unrecorded",r.get("fc_normalization"));assertEquals("Inf",r.get("max_cells_per_group"));assertEquals("-Inf",r.get("min_diff_pct"));assertFalse(r.containsKey("return_threshold"));
    }
    @Test void preparedLayersAndSourceSlotsHaveIndependentExplicitContracts()throws Exception {
        var r=request();r.put("data_source","provided");r.put("data","layer");r.put("test_slot","counts");r.put("fc_slot","scale.data");r.put("scale_data","scale");var normalized=validate(r);assertEquals(normalized,validate(normalized));assertFalse(normalized.containsKey("tfidf_method"));r.put("tfidf_scale",10000);assertThrows(IOException.class,()->validate(r));
        r.remove("tfidf_scale");r.remove("scale_data");assertThrows(IOException.class,()->validate(r));r.put("fc_slot","data");r.remove("data");assertThrows(IOException.class,()->validate(r));
    }
    @Test void allRawProbabilityThresholdAndHistoryRestrictionsAreNotSilentlyReinterpreted()throws Exception {
        var r=request();r.put("mode","ALL");r.remove("contrast");r.put("return_threshold",1);assertEquals(1.0,validate(r).get("return_threshold"));r.put("fc_normalization","other");assertThrows(IOException.class,()->validate(r));r.put("fc_normalization","unrecorded");r.put("contrast",Map.of("numerator",List.of("a")));assertThrows(IOException.class,()->validate(r));
    }
    @Test void unknownOverlappingAndEmptyRestComparisonsAreRefused()throws Exception {
        for(var contrast:List.of(Map.of("numerator",List.of("unknown")),Map.of("numerator",List.of("a","b")),Map.of("numerator",List.of("a"),"denominator",List.of("a")),Map.of("numerator",List.of("a"),"denominator",List.of("unknown")))) {
            var r=request();r.put("contrast",contrast);assertThrows(IOException.class,()->validate(r));
        }
    }
    @Test void ignoredCovariatesAndUndeclaredKindsAreRejected()throws Exception {
        var r=request();r.put("covariates",List.of(Map.of("name","x","kind","numeric")));assertThrows(IOException.class,()->validate(r));r.put("test","LR");assertDoesNotThrow(()->validate(r));r.put("covariates",List.of(Map.of("name","x","kind","expression")));assertThrows(IOException.class,()->validate(r));r.put("covariates",List.of(Map.of("name","x","kind","factor","levels",List.of("u","u"))));assertThrows(IOException.class,()->validate(r));
    }
    private Map<String,Object> inputs(Path root)throws Exception {
        Files.createDirectories(root.resolve("inputs"));var folder=root.resolve("inputs");
        Files.writeString(folder.resolve("counts"),"%%MatrixMarket matrix coordinate integer general\n2 4 8\n1 1 4\n2 1 0\n1 2 5\n2 2 1\n1 3 1\n2 3 5\n1 4 0\n2 4 4\n");
        Files.writeString(folder.resolve("features"),"peak_A\n峰_2\n",StandardCharsets.UTF_8);Files.writeString(folder.resolve("cells"),"α\nβ\nγ\nδ\n",StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("metadata"),"cell\tgroup\tbatch + quote\nα\ta\tu\nβ\ta\tv\nγ\tb\tu\nδ\tb\tv\n",StandardCharsets.UTF_8);
        return request();
    }
    @Test void unicodeIdentitiesAndLiteralMetadataNamesUseExplicitSafeTransport()throws Exception {
        var r=inputs(temp);r.put("test","LR");r.put("covariates",List.of(Map.of("name","batch + quote","kind","factor","levels",List.of("u","v"))));var p=SeuratDifferentialInputs.prepare(temp,validate(r),false);
        assertEquals(List.of("peak_A","峰_2"),p.counts().features());assertEquals(List.of("α","β","γ","δ"),p.counts().cells());assertEquals("cell_index\tgroup_index\tv1\nc1\tg1\tl1\nc2\tg1\tl2\nc3\tg2\tl1\nc4\tg2\tl2\n",Files.readString(temp.resolve("worker/metadata.tsv")));
        assertFalse(Files.readString(temp.resolve("worker/parameters.json")).contains("batch + quote"));assertTrue(Files.readString(temp.resolve("prepared-inputs.json")).contains("batch + quote"));assertDoesNotThrow(()->SeuratDifferentialInputs.prepare(temp,validate(r),true));
    }
    @Test void metadataReorderingAndMissingValuesAreNotRepaired()throws Exception {
        var r=inputs(temp);Files.writeString(temp.resolve("inputs/metadata"),"cell\tgroup\nβ\ta\nα\ta\nγ\tb\nδ\tb\n",StandardCharsets.UTF_8);assertThrows(IOException.class,()->SeuratDifferentialInputs.prepare(temp,validate(r),false));
    }
    @Test void featureSelectionPreservesCallerOrderAndWholeNormalizationPopulation()throws Exception {
        var r=inputs(temp);r.put("test_features","selection");Files.writeString(temp.resolve("inputs/test_features"),"峰_2\npeak_A\n",StandardCharsets.UTF_8);var p=SeuratDifferentialInputs.prepare(temp,validate(r),false);assertEquals(List.of(1,0),p.selected());assertEquals(2,p.data().features().size());assertEquals(List.of("f2","f1"),p.parameters().get("selected_features"));
    }
    @Test void changedPreparedValuesAreRejectedWithoutR()throws Exception {
        var r=inputs(temp);SeuratDifferentialInputs.prepare(temp,validate(r),false);Path path=temp.resolve("worker/data.mtx");Files.writeString(path,Files.readString(path).replaceFirst("(?m)^1 1 .*","1 1 999"));assertThrows(IOException.class,()->SeuratDifferentialInputs.prepare(temp,validate(r),true));
    }
    @Test void missingRuntimeSkipsAndFailedCapturesCannotVerifyOrOverwrite()throws Exception {
        Path source=temp.resolve("source");var raw=inputs(source);for(String k:List.of("counts","features","cells","metadata"))raw.put(k,source.resolve("inputs/"+k).toString());raw.put("out",temp.resolve("skipped").toString());raw.put("rscript",temp.resolve("absent-Rscript.exe").toString());raw.put("min_cells_group",1);
        Path request=temp.resolve("request.json");json(request,raw);var messages=new ByteArrayOutputStream();int code=SeuratDifferentialCli.run(new String[]{"--request",request.toString()},new PrintStream(messages,true,StandardCharsets.UTF_8),new PrintStream(messages,true,StandardCharsets.UTF_8));assertEquals(3,code,messages.toString(StandardCharsets.UTF_8));var state=read(temp.resolve("skipped/analysis-status.json"));assertEquals(false,state.get("results_admitted"));assertThrows(IOException.class,()->SeuratDifferentialCli.verify(temp.resolve("skipped")));
        String before=FollowupSupport.hash(temp.resolve("skipped/capture-files.json"));assertEquals(2,SeuratDifferentialCli.run(new String[]{"--request",request.toString()},System.out,System.err));assertEquals(before,FollowupSupport.hash(temp.resolve("skipped/capture-files.json")));
    }
}
