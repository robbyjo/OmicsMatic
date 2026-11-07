/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class Monocle3WorkflowTest {
    @TempDir Path temp;
    private Map<String,Object> raw(){
        var m=new LinkedHashMap<String,Object>();m.put("schema_version",1);m.put("mode","RUN");m.put("version","1.4.27");m.put("count_units","raw-integer-counts");
        m.put("counts","counts.tsv");m.put("cell_metadata","cell-metadata.tsv");m.put("gene_metadata","gene-metadata.tsv");
        m.put("metadata_types",Map.of("age",Map.of("type","numeric"),"batch",Map.of("type","factor","levels",List.of("NA","β"))));
        m.put("out","run");m.put("rscript","missing-Rscript");m.put("r_libraries",List.of("private-library"));
        m.put("analyses",List.of(Map.of("id","models","seed",20261107,"fit_models",Map.of("family","quasipoisson","intercept",true,"terms",List.of(Map.of("column","age"),Map.of("column","batch"))))));return m;
    }
    private Path inputs()throws IOException {
        Files.writeString(temp.resolve("counts.tsv"),"gene_id\t cell\"β \tNA\ngene β\t1\t2\nNA\t3\t4\n",StandardCharsets.UTF_8);
        Files.writeString(temp.resolve("cell-metadata.tsv"),"cell_id\tage\tbatch\n cell\"β \t1.5\tNA\nNA\t2e0\tβ\n",StandardCharsets.UTF_8);
        Files.writeString(temp.resolve("gene-metadata.tsv"),"gene_id\tgene_short_name\ngene β\t symbol\" β \nNA\tNA\n",StandardCharsets.UTF_8);return temp;
    }
    @Test void literalAxesAndFactorLevelsRemainExact()throws Exception {
        var r=Monocle3WorkflowRequest.parse(raw(),temp);var p=Monocle3WorkflowCli.population(inputs(),r);
        assertEquals(List.of(" cell\"β ","NA"),p.cells());assertEquals(List.of("gene β","NA"),p.genes());assertEquals(temp.resolve("counts.tsv"),r.counts());
    }
    @Test void metadataCannotBeReorderedToFitCounts()throws Exception {
        inputs();Files.writeString(temp.resolve("cell-metadata.tsv"),"cell_id\tage\tbatch\nNA\t2\tβ\n cell\"β \t1\tNA\n");
        assertThrows(IOException.class,()->Monocle3WorkflowCli.population(temp,Monocle3WorkflowRequest.parse(raw(),temp)));
    }
    @Test void transformedNonfiniteAndInexactCountsAreRefused()throws Exception {
        var r=Monocle3WorkflowRequest.parse(raw(),temp);inputs();
        for(String x:List.of("NaN","Inf","NA","-1","0.5","1e3","9007199254740992","9223372036854775808","")){
            Files.writeString(temp.resolve("counts.tsv"),"gene_id\t cell\"β \tNA\ngene β\t"+x+"\t2\nNA\t3\t4\n");
            assertThrows(IOException.class,()->Monocle3WorkflowCli.population(temp,r),x);
        }
    }
    @Test void zeroTotalCellsDuplicatesRaggedRowsAndUnknownLevelsAreRefused()throws Exception {
        var r=Monocle3WorkflowRequest.parse(raw(),temp);
        for(String counts:List.of("gene_id\t cell\"β \tNA\ngene β\t0\t2\nNA\t0\t4\n","gene_id\t cell\"β \tNA\ngene β\t1\t2\ngene β\t3\t4\n","gene_id\t cell\"β \tNA\ngene β\t1\nNA\t3\t4\n")){
            inputs();Files.writeString(temp.resolve("counts.tsv"),counts);assertThrows(IOException.class,()->Monocle3WorkflowCli.population(temp,r));
        }
        inputs();Files.writeString(temp.resolve("cell-metadata.tsv"),"cell_id\tage\tbatch\n cell\"β \t1\tnew\nNA\t2\tβ\n");assertThrows(IOException.class,()->Monocle3WorkflowCli.population(temp,r));
    }
    @Test void typedContractRefusesCodeAndImplicitUnits()throws Exception {
        for(var e:List.of(Map.entry("count_units",(Object)"TPM"),Map.entry("mode",(Object)"VERIFY"),Map.entry("script",(Object)"system('x')"),Map.entry("version",(Object)"latest"))){
            var m=raw();m.put(e.getKey(),e.getValue());assertThrows(IOException.class,()->Monocle3WorkflowRequest.parse(m,temp));
        }
        var m=raw();m.put("analyses",List.of(Map.of("id","models","seed",1,"fit_models",Map.of("family","quasipoisson","intercept",true,"terms",List.of(Map.of("column","system('x')"))))));
        assertThrows(IOException.class,()->Monocle3WorkflowRequest.parse(m,temp));
    }
    @Test void everyExposedFamilyParsesWithoutChangingItsName()throws Exception {
        for(String family:List.of("poisson","quasipoisson","negbinomial","gaussian","binomial","zipoisson","zinegbinomial")){
            var m=raw();m.put("analyses",List.of(Map.of("id","models","seed",1,"fit_models",Map.of("family",family,"intercept",true,"terms",List.of(Map.of("column","age","spline_df",3))))));
            assertEquals(family,Monocle3WorkflowRequest.object(Monocle3WorkflowRequest.parse(m,temp).analyses().get(0).get("fit_models")).get("family"));
        }
    }
    @Test void finiteCovariatesAndDeclaredCountCapacityAreRequired()throws Exception {
        inputs();var limited=raw();limited.put("maximum_entries",3);assertThrows(IOException.class,()->Monocle3WorkflowCli.population(temp,Monocle3WorkflowRequest.parse(limited,temp)));
        var r=Monocle3WorkflowRequest.parse(raw(),temp);
        for(String bad:List.of("Inf","NaN","NA","1e999")){Files.writeString(temp.resolve("cell-metadata.tsv"),"cell_id\tage\tbatch\n cell\"β \t"+bad+"\tNA\nNA\t2\tβ\n");assertThrows(IOException.class,()->Monocle3WorkflowCli.population(temp,r));}
    }
    @Test void missingRuntimeReturnsProminentSkipWithoutResults()throws Exception {
        inputs();NextflowFiles.writeJson(temp.resolve("request.json"),raw());var stdout=new ByteArrayOutputStream();var stderr=new ByteArrayOutputStream();
        assertEquals(3,Monocle3WorkflowCli.run(new String[]{"--request",temp.resolve("request.json").toString()},new PrintStream(stdout,true,StandardCharsets.UTF_8),new PrintStream(stderr,true,StandardCharsets.UTF_8)));
        var state=Monocle3WorkflowCli.read(temp.resolve("run/analysis-status.json"),1048576);assertEquals(false,state.get("result_admitted"));assertEquals("SKIP3_DEPENDENT_ANALYSIS_SKIPPED",state.get("status"));
        assertFalse(Files.exists(temp.resolve("run/results")));assertTrue(Files.readString(temp.resolve("run/WARNINGS.md")).contains("REQUIRED ACTION:"));assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("SKIP3"));
        assertTrue(Files.exists(temp.resolve("run/source/1.4.27/R/expr_models.R")));assertTrue(Files.exists(temp.resolve("run/inputs/counts.tsv")));
    }
}
