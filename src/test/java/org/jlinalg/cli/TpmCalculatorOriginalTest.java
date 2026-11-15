/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.jlinalg.sequence.TpmCalculator;
import org.jlinalg.sequence.TpmCalculator.Metrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Independent full-state original executions plus byte-equal original CLI products. */
class TpmCalculatorOriginalTest {
    @TempDir Path temp;
    static final String ARCHIVE="/org/jlinalg/sequence/tpmcalculator-original-controls.zip";
    static final String SHA="03acc35d80b0f5f8f58ff4fb25df01040f4aa8a8a7e8e5d476196de6cb42c07f";
    static byte[] archive(boolean edge) throws Exception {
        String file=edge?"/org/jlinalg/sequence/tpmcalculator-original-edges.zip":ARCHIVE;
        try (var in=TpmCalculatorOriginalTest.class.getResourceAsStream(file)) {
            assertNotNull(in); byte[] raw=in.readAllBytes();
            assertEquals(edge?"572142c0845b54b1d8201f88cb30fe49e55a506b4550c23cdccb2d7da43e573e":SHA,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw))); return raw;
        }
    }
    static String entry(String name,boolean edge) throws Exception {
        try (var zip=new ZipInputStream(new java.io.ByteArrayInputStream(archive(edge)))) {
            for (var e=zip.getNextEntry(); e!=null; e=zip.getNextEntry()) if (e.getName().equals(name)) return new String(zip.readAllBytes(),StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException("Missing original archive entry "+name);
    }
    static Stream<Arguments> cases() throws Exception {
        List<?> list=(List<?>)SimpleJson.parse(entry("CASES.json",false)); assertEquals(50,list.size());
        List<?> edges=(List<?>)SimpleJson.parse(entry("CASES.json",true)); assertEquals(7,edges.size());
        return Stream.concat(list.stream().map(row -> Arguments.of(((Map<?,?>)row).get("name"),false)),
            edges.stream().map(row -> Arguments.of(((Map<?,?>)row).get("name"),true)));
    }
    Path extract(String name,boolean edge) throws Exception {
        Path folder=temp.resolve(name); Files.createDirectory(folder);
        try (var zip=new ZipInputStream(new java.io.ByteArrayInputStream(archive(edge)))) {
            for (var e=zip.getNextEntry(); e!=null; e=zip.getNextEntry()) if (e.getName().startsWith(name+"/")) {
                Path p=temp.resolve(e.getName()).normalize(); assertTrue(p.startsWith(folder));
                if (e.isDirectory()) Files.createDirectories(p); else { Files.createDirectories(p.getParent()); Files.copy(zip,p); }
            }
        }
        return folder;
    }
    static int integer(Map<?,?> metadata,String key) { return ((Number)metadata.get(key)).intValue(); }
    static List<Object> metric(Metrics m) {
        return List.of(m.reads(),m.tpm(),m.exonReads(),m.exonTpm(),m.intronReads(),m.intronTpm(),m.uniqueReads(),m.uniqueTpm(),
            m.uniqueExonReads(),m.uniqueExonTpm(),m.uniqueIntronReads(),m.uniqueIntronTpm(),m.bridgeReads(),m.bridgeTpm());
    }
    static void row(Map<String,List<Object>> rows,String kind,String sample,String chr,String gene,String tx,int ordinal,
                    String type,char strand,long start,long end,long length,long exon,long intron,long unique,long uniqueExon,long uniqueIntron,
                    boolean processed,boolean present,Metrics metrics) {
        var values=new ArrayList<Object>(List.of(kind,sample,chr,gene,tx,ordinal,type,String.valueOf(strand),start,end,length,exon,intron,unique,uniqueExon,uniqueIntron,processed?1:0,present?1:0));
        values.addAll(metric(metrics));String key=String.join("\t",kind,sample,chr,gene,tx,Integer.toString(ordinal));
        assertNull(rows.put(key,values),key);
    }
    static void featureRows(Map<String,List<Object>> rows,String kind,String sample,String chr,String gene,String tx,List<TpmCalculator.Feature> features) {
        int n=0; for (var f:features) row(rows,kind,sample,chr,gene,tx,++n,f.type(),f.strand(),f.start(),f.end(),f.length(),0,0,0,0,0,false,f.dataPresent(),f.metrics());
    }
    static double number(String s) {
        return switch(s.toLowerCase(java.util.Locale.ROOT)) { case "nan","-nan" -> Double.NaN; case "inf","+inf" -> Double.POSITIVE_INFINITY; case "-inf" -> Double.NEGATIVE_INFINITY; default -> Double.parseDouble(s); };
    }
    @ParameterizedTest @MethodSource("cases")
    void completeOriginalAnnotationsCountersPresenceAndEveryTpmMatch(String name,boolean edge) throws Exception {
        Path folder=extract(name,edge); Map<?,?> metadata=(Map<?,?>)SimpleJson.parse(Files.readString(folder.resolve("CASE.json")));
        assertEquals(Boolean.TRUE,metadata.get("original_main_and_probe_tables_byte_equal"));
        for (Object file:(List<?>)metadata.get("table_files")) assertEquals(-1L,Files.mismatch(folder.resolve("original-main/"+file),folder.resolve("original-state/"+file)),name+" "+file);
        var options=new TpmCalculator.Options("gene_id","transcript_id",integer(metadata,"cutoff"),integer(metadata,"mapq"),integer(metadata,"overlap"),(Boolean)metadata.get("proper"));
        var samples=new ArrayList<TpmCalculator.Sample>(); for (Object n:(List<?>)metadata.get("sample_order")) samples.add(new TpmCalculator.Sample((String)n,folder.resolve("bams/"+n+".bam")));
        var result=TpmCalculator.readAnnotation(folder.resolve("annotation.gtf"),options).quantify(samples);
        var rows=new TreeMap<String,List<Object>>();var processed=new TreeMap<String,Long>();
        for (var sample:result.samples()) {
            processed.put(sample.name(),sample.processedAlignments());
            for (var g:sample.genes()) {
                row(rows,"gene",sample.name(),g.chromosome(),g.id(),"-",0,"gene",g.strand(),g.start(),g.end(),g.length(),g.exonLength(),g.intronLength(),g.uniqueLength(),g.uniqueExonLength(),g.uniqueIntronLength(),g.processed(),g.dataPresent(),g.metrics());
                featureRows(rows,"gene-feature",sample.name(),g.chromosome(),g.id(),"-",g.features());
                featureRows(rows,"gene-unique",sample.name(),g.chromosome(),g.id(),"-",g.uniqueFeatures());
                for (var t:g.transcripts()) {
                    row(rows,"transcript",sample.name(),g.chromosome(),g.id(),t.id(),0,"isoform",t.strand(),t.start(),t.end(),t.length(),t.exonLength(),t.intronLength(),0,0,0,t.processed(),t.dataPresent(),t.metrics());
                    featureRows(rows,"transcript-feature",sample.name(),g.chromosome(),g.id(),t.id(),t.features());
                }
            }
        }
        var expected=new TreeMap<String,List<String>>();
        for (String line:Files.readAllLines(folder.resolve("original-state/process.stdout"))) {
            String[] fields=line.split("\t",-1);
            if (line.startsWith("#sample\t")) { assertEquals(Long.parseLong(fields[2]),processed.get(fields[1])); continue; }
            assertEquals(32,fields.length,line);String key=String.join("\t",Arrays.copyOf(fields,6));assertNull(expected.put(key,List.of(fields)));
        }
        assertEquals(expected.keySet(),rows.keySet(),name+" complete original state inventory");
        for (var e:expected.entrySet()) {
            List<String> want=e.getValue();List<Object> got=rows.get(e.getKey());
            for (int k=0;k<32;k++) {
                String context=name+" "+e.getKey()+" column "+k;
                if (k<8 || k<18 || k%2==0) assertEquals(want.get(k),got.get(k).toString(),context);
                else {
                    double a=number(want.get(k)),b=((Number)got.get(k)).doubleValue();
                    if (Double.isNaN(a)) assertTrue(Double.isNaN(b),context);
                    else if (Double.isInfinite(a)) assertEquals(a,b,context);
                    else assertEquals(a,b,64*Math.ulp(a),context);
                }
            }
        }
    }
    @Test void originalProbeAndProducerSourceBytesRemainBoundToTheActualArchive() throws Exception {
        for (String file:List.of("OriginalProbe.cpp","make_controls.py")) assertArrayEquals(Files.readAllBytes(Path.of("tools/literature/tpmcalculator_validation/"+file)),entry(file,false).getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(Files.readAllBytes(Path.of("tools/literature/tpmcalculator_validation/make_edge_controls.py")),entry("make_edge_controls.py",true).getBytes(StandardCharsets.UTF_8));
    }
}
