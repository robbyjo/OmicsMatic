/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.jlinalg.differential.EdgeRNormalization;
import org.jlinalg.differential.EdgeRNormalization.Edition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import static org.junit.jupiter.api.Assertions.*;

/** Original historical C++ calculations, without claiming failed full packages ran. */
final class EdgeRHistoricalCpmTest {
    private static final Map<String,Map<String,Object>> REF=reference();
    private static final AtomicLong MAX_ABSOLUTE=new AtomicLong(),MAX_SCALED=new AtomicLong(),VALUES=new AtomicLong();
    @TestFactory Stream<DynamicTest> originalHistoricalCpmRpkmProviders() {
        return REF.entrySet().stream().flatMap(entry->list(entry.getValue().get("transforms")).stream().map(EdgeRHistoricalCpmTest::map)
            .map(row->DynamicTest.dynamicTest(entry.getKey()+"/"+row.get("name"),()->{
                var data=map(map(entry.getValue().get("datasets")).get(row.get("dataset")));double[][] x=matrix(data.get("counts"));double[] libs=vector(row.get("libraries"));
                Edition edition=Edition.valueOf("EDGER_"+entry.getKey().replace('.','_'));boolean log=(Boolean)row.get("log");double prior=number(row.get("prior"));
                double[][] actual=row.get("operation").equals("CPM")?EdgeRNormalization.cpm(x,libs,edition,log,prior):
                    EdgeRNormalization.rpkm(x,libs,vector(row.get("gene_lengths")),edition,log,prior);
                double[][] expected=matrix(row.get("values"));assertEquals(expected.length,actual.length);
                for(int i=0;i<expected.length;i++)for(int j=0;j<expected[i].length;j++){
                    double e=expected[i][j],a=actual[i][j];if(Double.isNaN(e)){assertTrue(Double.isNaN(a));continue;}
                    if(!Double.isFinite(e)){assertEquals(e,a);continue;}double error=Math.abs(e-a),scale=Math.max(1,Math.abs(e));
                    assertEquals(e,a,Math.max(2e-14,Math.abs(e)*4e-13));MAX_ABSOLUTE.accumulateAndGet(Double.doubleToLongBits(error),Math::max);
                    MAX_SCALED.accumulateAndGet(Double.doubleToLongBits(error/scale),Math::max);VALUES.incrementAndGet();
                }
            })));
    }
    @Test void providersRetainTheirActualScopeAndNeverClaimFailedPackages() {
        assertEquals(Set.of("3.32.1","3.36.0","3.40.2","3.42.4"),REF.keySet());
        for(var e:REF.entrySet()){
            assertEquals(552,list(e.getValue().get("transforms")).size());assertEquals(false,e.getValue().get("historical_author_host_reproduced"));
            assertEquals(Set.of("3.32.1","3.36.0").contains(e.getKey())?"original-kernel":"package",e.getValue().get("provider"));
        }
    }
    @AfterAll static void audit()throws IOException {
        String path=System.getProperty("omicsmatic.edger.historical.audit");if(path==null)return;
        Files.write(Path.of(path),RsemJson.bytes(Map.of("historical_original_transforms",2208,"numeric_values_compared",VALUES.get(),
            "maximum_absolute_error",Double.longBitsToDouble(MAX_ABSOLUTE.get()),"maximum_scaled_error",Double.longBitsToDouble(MAX_SCALED.get()),
            "full_package_providers",List.of("3.40.2","3.42.4"),"original_kernel_providers",List.of("3.32.1","3.36.0"),"fresh_R_runs",false)),StandardOpenOption.CREATE_NEW);
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){return (Map<String,Object>)value;}
    private static List<?> list(Object value){return value==null?List.of():value instanceof List<?> l?l:List.of(value);}
    private static double number(Object value){return value instanceof Number n?n.doubleValue():switch(value.toString()){case "NA","NaN"->Double.NaN;case "Inf"->Double.POSITIVE_INFINITY;case "-Inf"->Double.NEGATIVE_INFINITY;default->Double.parseDouble(value.toString());};}
    private static double[] vector(Object value){return list(value).stream().mapToDouble(EdgeRHistoricalCpmTest::number).toArray();}
    private static double[][] matrix(Object value){return list(value).stream().map(EdgeRHistoricalCpmTest::vector).toArray(double[][]::new);}
    private static Map<String,Map<String,Object>> reference() {
        try(var in=EdgeRHistoricalCpmTest.class.getResourceAsStream("/org/jlinalg/differential/edger-normalization/historical-cpm-20261120.zip")){
            if(in==null)throw new IOException("Original historical CPM references missing");byte[] raw=in.readAllBytes();
            if(!EdgeRNormalizationCli.sha(raw).equals("0fd85e4ef9086830eb823cdaa2a825898a0a090331c23813bac98f0b662e4f0d"))throw new IOException("Original historical references changed");
            Map<String,Map<String,Object>> result=new TreeMap<>();try(var zip=new ZipInputStream(new ByteArrayInputStream(raw),StandardCharsets.UTF_8)){
                java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null)if(entry.getName().matches("3\\.(32\\.1|36\\.0|40\\.2|42\\.4)/reference\\.json")){
                    byte[] text=zip.readNBytes(32_000_001);if(text.length>32_000_000)throw new IOException("Reference capacity exceeded");
                    var row=map(SimpleJson.parse(new String(text,StandardCharsets.UTF_8)));String version=(String)row.get("producer_version");if(result.put(version,row)!=null)throw new IOException("Duplicate original reference");
                }
            }
            if(result.size()!=4)throw new IOException("Expected four original providers");return Collections.unmodifiableMap(result);
        }catch(Exception error){throw new IllegalStateException(error);}
    }
}
