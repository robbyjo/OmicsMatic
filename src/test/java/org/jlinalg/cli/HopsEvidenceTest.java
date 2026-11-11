/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.jlinalg.metagenomics.HopsEvidence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Exact original R function/score witnesses, including source character-conversion precision loss. */
class HopsEvidenceTest {
    @TempDir Path temporary;
    private static final HopsEvidence.Limits LIMITS=new HopsEvidence.Limits(67108864,1000000,100000000,10000,100000);

    @Test void allOriginalStatisticsAndMatricesMatchCompleteOriginalRReferences() throws Exception {
        Path root=extract();int pairs=0;
        for(String group:List.of("source","boundaries","metadata")) {
            Path data=root.resolve(group),expected=root.resolve(group+"-expected");
            var result=HopsEvidence.inspect(data,Files.readAllLines(data.resolve("literal-nodes.txt")),LIMITS);
            List<String> rows=Files.readAllLines(expected.resolve("statistics.tsv"));assertEquals(rows.size()-1,result.pairs().size());
            for(int i=0;i<result.pairs().size();i++) {
                var pair=result.pairs().get(i);String[] fields=rows.get(i+1).split("\t",-1);
                assertEquals(fields[0],pair.sample());assertEquals(fields[1],pair.taxon());
                compare(pair.defaults(),fields,2);compare(pair.ancient(),fields,9);pairs++;
            }
            assertEquals(matrix(expected.resolve("full-matrix.tsv")),result.fullMatrix());
            assertEquals(matrix(expected.resolve("reduced-matrix.tsv")),result.reducedMatrix());
        }
        assertEquals(2901,pairs);
    }

    @Test void originalClassBoundariesMissingValuesAndRefusalsArePreserved() throws Exception {
        Path root=extract();List<String> cases=Files.readAllLines(root.resolve("classification/cases.tsv")),expected=Files.readAllLines(root.resolve("classification/expected.tsv"));
        assertEquals(421,cases.size());assertEquals(cases.size(),expected.size());
        for(int i=1;i<cases.size();i++) {
            String[] row=cases.get(i).split("\t",-1),wanted=expected.get(i).split("\t",-1);assertEquals(row[0],wanted[0]);
            var defaults=new HopsEvidence.Statistics("literal",null,null,number(row[1]),null,number(row[2]),null);
            var ancient=new HopsEvidence.Statistics("literal",null,null,number(row[3]),null,null,null);
            assertEquals(Integer.parseInt(wanted[1]),HopsEvidence.evidenceClass(defaults,ancient),row[0]);
        }
        Path data=root.resolve("source");var nodes=Files.readAllLines(data.resolve("literal-nodes.txt"));
        for(var limits:List.of(new HopsEvidence.Limits(1,1000000,100000000,10000,100000),new HopsEvidence.Limits(67108864,1,100000000,10000,100000),new HopsEvidence.Limits(67108864,1000000,1,10000,100000),new HopsEvidence.Limits(67108864,1000000,100000000,1,100000),new HopsEvidence.Limits(67108864,1000000,100000000,10000,1)))
            assertTrue(assertThrows(IOException.class,()->HopsEvidence.inspect(data,nodes,limits)).getMessage().contains("Required action:"));
        assertThrows(IOException.class,()->HopsEvidence.inspect(data,List.of(nodes.get(0),nodes.get(0)),LIMITS));
        assertThrows(IllegalArgumentException.class,()->HopsEvidence.statistics("literal",new long[]{-1,2,0,0,0,0},new Double[]{0.,0.,0.,0.},0.,false));
        var absent=HopsEvidence.inspect(data,List.of("MISSING_LITERAL_NODE"),LIMITS);assertEquals(2,absent.pairs().size());
        assertTrue(absent.pairs().stream().allMatch(x->x.evidenceClass()==1 && x.defaults().node()==null && x.defaults().dr4()==null));
        assertTrue(absent.warnings().stream().anyMatch(x->x.startsWith("UNASSESSABLE:")));
        assertNull(HopsEvidence.token(null));assertEquals("NaN",HopsEvidence.token(Double.NaN));assertEquals("Inf",HopsEvidence.token(Double.POSITIVE_INFINITY));assertEquals("-Inf",HopsEvidence.token(Double.NEGATIVE_INFINITY));
    }
    private void compare(HopsEvidence.Statistics value,String[] row,int start) {
        assertEquals(row[start].equals("NA")?null:row[start],value.node());numeric(value.dr6(),row[start+1]);integer(value.n6(),row[start+2]);
        numeric(value.dr4(),row[start+3]);integer(value.n4(),row[start+4]);numeric(value.maximumTerminalDamage(),row[start+5]);numeric(value.uniquePerReference(),row[start+6]);
    }
    private void numeric(Double actual,String token) {Double expected=number(token);if(expected==null)assertNull(actual);else {assertNotNull(actual);assertEquals(Double.doubleToLongBits(expected),Double.doubleToLongBits(actual),token);}}
    private void integer(Long actual,String token) {assertEquals(token.equals("NA")?null:new java.math.BigDecimal(token).longValueExact(),actual);}
    private static Double number(String token) {return switch(token) {case "NA"->null;case "NaN"->Double.NaN;case "Inf"->Double.POSITIVE_INFINITY;case "-Inf"->Double.NEGATIVE_INFINITY;default->Double.valueOf(token);};}
    private Map<String,Map<String,Integer>> matrix(Path path)throws IOException {
        var lines=Files.readAllLines(path);String[] header=lines.get(0).split("\t",-1);var out=new LinkedHashMap<String,Map<String,Integer>>();
        for(int i=1;i<lines.size();i++) {String[] fields=lines.get(i).split("\t",-1);assertEquals(header.length,fields.length);var row=new LinkedHashMap<String,Integer>();for(int j=1;j<header.length;j++)row.put(header[j],Integer.valueOf(fields[j]));assertNull(out.putIfAbsent(fields[0],row));}return out;
    }
    private Path extract()throws Exception {
        Path out=Files.createDirectory(temporary.resolve("reference"));Map<?,?> metadata;
        try(var input=getClass().getResourceAsStream("/org/jlinalg/metagenomics/hops-evidence/SOURCE.json")) {assertNotNull(input);metadata=NextflowFiles.object(SimpleJson.parse(new String(input.readAllBytes(),StandardCharsets.UTF_8)));}
        var expected=NextflowFiles.object(metadata.get("reference_files"));var seen=new HashSet<String>();
        try(var input=getClass().getResourceAsStream("/org/jlinalg/metagenomics/hops-evidence/reference.zip")) {
            assertNotNull(input);byte[] all=input.readNBytes(16777217);assertTrue(all.length<=16777216);assertEquals(NextflowFiles.fingerprint(metadata.get("reference_zip"),16777216),new NextflowFiles.Pin(all.length,NextflowFiles.sha(all)));
            try(var zip=new ZipInputStream(new java.io.ByteArrayInputStream(all))) {int count=0;long total=0;
                for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) {Path path=out.resolve(entry.getName()).normalize();assertTrue(path.startsWith(out));assertFalse(entry.isDirectory());assertTrue(++count<1000);byte[] raw=zip.readNBytes(16777217);assertTrue(raw.length<=16777216);total+=raw.length;assertTrue(total<=67108864);assertTrue(seen.add(entry.getName()));assertEquals(NextflowFiles.fingerprint(expected.get(entry.getName()),16777216),new NextflowFiles.Pin(raw.length,NextflowFiles.sha(raw)));Files.createDirectories(path.getParent());Files.write(path,raw);}
            }
        }
        assertEquals(expected.keySet(),seen);return out;
    }
}
