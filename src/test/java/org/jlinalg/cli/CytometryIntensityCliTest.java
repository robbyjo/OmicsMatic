/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CytometryIntensityCliTest {
    @TempDir Path temporary;
    @Test void mainDispatchExplainsUnbinnedStatisticsAndFailureActions() {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();assertEquals(0,JLinAlgCli.run(new String[]{"flowjo-tools","SUMMARIZE","--help"},new PrintStream(out),new PrintStream(err)));String s=out.toString(StandardCharsets.UTF_8);assertTrue(s.contains("unbinned"));assertTrue(s.contains("R_TYPE_7"));assertTrue(s.contains("No display-transform binning"));assertTrue(s.contains("SKIP3"));assertTrue(s.contains("FAIL2"));assertEquals("",err.toString(StandardCharsets.UTF_8));
    }
    private Map<String,Object> request() { var m=new LinkedHashMap<String,Object>();m.put("schema",1);m.put("profile","native-cytometry-intensity-unbinned-v1");m.put("parent","absent");m.put("parent_manifest_pin",Map.of("bytes",0,"sha256","0".repeat(64)));m.put("out","result");m.put("binning","NONE");m.put("quantile_method","R_TYPE_7");m.put("parent_count_policy","REQUIRE_SAVED_COUNTS_AGREE");m.put("definitions",List.of());m.put("measurement_units","Explicit arbitrary measurement units");m.put("statistics_provenance","Independent boundary control");m.put("export_measurements",true);return m; }
    @Test void unsupportedVendorBinningSkipsWithoutInventingDefaultsOrCreatingOutput() throws Exception {
        var m=request();m.put("binning","FLOWJO_VENDOR_DEFAULT");Path p=temporary.resolve("request.json");Files.writeString(p,NextflowJson.json(m));var error=new ByteArrayOutputStream();assertEquals(3,CytometryIntensityCli.run(new String[]{"--request",p.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(error)));assertTrue(error.toString(StandardCharsets.UTF_8).contains("binning"));assertTrue(error.toString(StandardCharsets.UTF_8).contains("SKIP3"));assertFalse(Files.exists(temporary.resolve("result")));
    }
    @Test void unsupportedQuantileConventionIsNotReplacedByTypeSeven() throws Exception {
        var m=request();m.put("quantile_method","R_TYPE_8");Path p=temporary.resolve("request.json");Files.writeString(p,NextflowJson.json(m));var error=new ByteArrayOutputStream();assertEquals(3,CytometryIntensityCli.run(new String[]{"--request",p.toString()},new PrintStream(new ByteArrayOutputStream()),new PrintStream(error)));assertTrue(error.toString(StandardCharsets.UTF_8).contains("quantile"));assertFalse(Files.exists(temporary.resolve("result")));
    }
}
