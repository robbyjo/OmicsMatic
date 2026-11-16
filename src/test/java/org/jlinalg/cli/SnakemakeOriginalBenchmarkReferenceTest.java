/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.jlinalg.workflow.SnakemakeBenchmarks;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SnakemakeOriginalBenchmarkReferenceTest {
    private static final SnakemakeBenchmarks.Limits LIMITS=new SnakemakeBenchmarks.Limits(1_000_000,1000,10000);
    private static Map<String,byte[]> controls() throws Exception {
        var result=new HashMap<String,byte[]>();
        try(var input=SnakemakeOriginalBenchmarkReferenceTest.class.getResourceAsStream("/org/jlinalg/workflow/snakemake-9271-benchmarks.zip")) {
            assertNotNull(input);try(var zip=new ZipInputStream(input)) {java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null)assertNull(result.put(entry.getName(),zip.readAllBytes()));}
        }
        return result;
    }
    private static List<SnakemakeBenchmarks.Measurement> parse(byte[] raw) throws IOException { return SnakemakeBenchmarks.read(new InputStreamReader(new ByteArrayInputStream(raw),StandardCharsets.UTF_8),LIMITS); }
    private static void decimal(Object expected,BigDecimal actual) { assertEquals(0,new BigDecimal(expected.toString()).compareTo(actual)); }
    @Test void allPinnedOriginalSerializationControlsMatchActualRecordedDecimals() throws Exception {
        var files=controls();var reference=(Map<?,?>)SimpleJson.parse(new String(files.get("REFERENCE.json"),StandardCharsets.UTF_8));
        assertEquals("f79d9eb5e2b679e1191818f24335907b041578be",reference.get("source_commit"));assertEquals(Boolean.FALSE,reference.get("unrounded_input_precision_recovered"));
        assertTrue(files.containsKey("source-zero-time-failure.txt"));
        for(Object value:(List<?>)reference.get("cases")) {
            var row=(Map<?,?>)value;byte[] raw=files.get(row.get("file").toString());assertNotNull(raw);
            if(row.get("outcome").equals("valid")) { var measurement=parse(raw);assertEquals(1,measurement.size());decimal(row.get("wall"),measurement.get(0).wallSeconds());decimal(row.get("cpu"),measurement.get(0).cpuSeconds());decimal(row.get("rss"),measurement.get(0).maxRssMiB()); }
            else assertThrows(IOException.class,()->parse(raw));
        }
        var expected=(Map<?,?>)reference.get("two_jobs_same_group");
        var summary=SnakemakeBenchmarks.aggregate(Map.of("a",parse(files.get("case-0.tsv")),"b",parse(files.get("case-1.tsv"))),List.of(new SnakemakeBenchmarks.Assignment("A","1","j1","a",1),new SnakemakeBenchmarks.Assignment("A","1","j2","b",1))).get(new SnakemakeBenchmarks.Group("A","1"));
        decimal(expected.get("cpu_seconds"),summary.cpuSeconds());decimal(expected.get("max_job_rss_mib"),summary.maxJobRssMiB());decimal(expected.get("summed_job_wall_seconds"),summary.summedJobWallSeconds());
    }
    @Test void sourceRepeatRowsMustKeepAJobIdentityAndSeparateGroups() throws Exception {
        var values=parse(controls().get("two-repeats.tsv"));assertEquals(2,values.size());
        var result=SnakemakeBenchmarks.aggregate(Map.of("a",values),List.of(new SnakemakeBenchmarks.Assignment("A","1","j","a",1),new SnakemakeBenchmarks.Assignment("A","2","j","a",2)));assertEquals(2,result.size());
        assertThrows(IllegalArgumentException.class,()->SnakemakeBenchmarks.aggregate(Map.of("a",values),List.of(new SnakemakeBenchmarks.Assignment("A","1","j1","a",1),new SnakemakeBenchmarks.Assignment("A","1","j2","a",2))));
    }
}
