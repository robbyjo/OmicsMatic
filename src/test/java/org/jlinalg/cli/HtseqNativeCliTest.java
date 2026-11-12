/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import htsjdk.samtools.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.jlinalg.sequence.HtseqReadCounter.Alignment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HtseqNativeCliTest {
    @TempDir Path directory;
    private String diagnostics;
    private static final Map<String,Object> ORIGINAL=original();
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object o){return (Map<String,Object>)o;}
    @SuppressWarnings("unchecked") private static List<Object> list(Object o){return (List<Object>)o;}
    private static Map<String,Object> original() {
        try(var raw=HtseqNativeCliTest.class.getResourceAsStream("/org/jlinalg/sequence/htseq-read-count/original-counts.json.gz")) {
            assertNotNull(raw);try(var gzip=new GZIPInputStream(raw)){return object(SimpleJson.parse(new String(gzip.readAllBytes(),StandardCharsets.UTF_8)));}
        }catch(Exception failure){throw new ExceptionInInitializerError(failure);}
    }
    static Stream<Arguments> controls(){return list(ORIGINAL.get("cases")).stream().map(HtseqNativeCliTest::object).map(c->Arguments.of(c.get("id"),c));}
    private Path input(String name)throws Exception {
        Path target=directory.resolve(name);if(Files.exists(target))return target;
        try(var source=getClass().getResourceAsStream("/org/jlinalg/sequence/htseq-native-files/"+name)){assertNotNull(source,name);Files.copy(source,target);}return target;
    }
    private Map<String,Object> request(Map<String,Object> config,String out)throws Exception {
        String alignment=Path.of((String)config.get("alignment")).getFileName().toString(),annotation=Path.of((String)config.get("annotation")).getFileName().toString();input(alignment);input(annotation);
        var r=new LinkedHashMap<String,Object>();r.put("schema_version",1);r.put("operation","COUNT");r.put("version","2.1.2");r.put("alignment",alignment);r.put("annotation",annotation);r.put("format",alignment.endsWith(".bam")?"bam":"sam");r.put("feature_type","exon");r.put("id_attribute","gene_id");r.put("genome_label","synthetic-known-coordinates");r.put("annotation_label","actual-original-controlled-GTF");r.put("counting_unit","original-auto");r.put("out",out);
        for(String key:List.of("stranded","overlap","nonunique","minimum_mapq","secondary","supplementary","order"))r.put(key,config.get(key));return r;
    }
    private int run(Map<String,Object> raw,String name)throws Exception {
        Path request=directory.resolve(name+".json");Files.writeString(request,NextflowJson.json(raw));var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);
        int code=JLinAlgCli.run(new String[]{"htseq-tools","--engine","native","--request",request.toString()},stream,stream);diagnostics=bytes.toString();Path warnings=directory.resolve(raw.get("out").toString()).resolve("WARNINGS.md");if(code!=0&&Files.exists(warnings))diagnostics+=Files.readString(warnings);return code;
    }
    @ParameterizedTest(name="{0}") @MethodSource("controls") void nativeFilesCountsAndEveryPreparedMateMatch40SavedOriginalControls(String name,Map<String,Object> control)throws Exception {
        var prepared=object(control.get("prepared"));var config=object(prepared.get("config"));var request=request(config,"native");
        assertEquals(0,run(request,"count"),name+": "+diagnostics);
        var nativeCounts=DelimitedData.read(directory.resolve("native/counts.tsv"));var expected=list(object(control.get("counts")).get("genes"));assertEquals(expected.size(),nativeCounts.rows().size());
        for(int i=0;i<expected.size();i++){var row=list(expected.get(i));assertEquals(row.get(0),nativeCounts.rows().get(i)[0]);assertEquals(Double.parseDouble((String)row.get(1)),Double.parseDouble(nativeCounts.rows().get(i)[1]),1e-13,name);}
        var summary=DelimitedData.read(directory.resolve("native/summary.tsv"));expected=list(object(control.get("counts")).get("summary"));
        for(int i=0;i<expected.size();i++){var row=list(expected.get(i));assertEquals(row.get(0),summary.rows().get(i)[0]);assertEquals(Long.parseLong((String)row.get(1)),Long.parseLong(summary.rows().get(i)[1]),name);}
        var units=DelimitedData.readResult(directory.resolve("native/unit-dispositions.tsv"));var sourceUnits=list(prepared.get("units"));assertEquals(sourceUnits.size(),units.rows().size(),name);
        var records=new ArrayList<SAMRecord>();try(var reader=SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT).open(directory.resolve(request.get("alignment").toString()))){for(var r:reader)records.add(r);}
        for(int i=0;i<sourceUnits.size();i++) {
            var source=object(sourceUnits.get(i));String[] actual=units.rows().get(i);
            assertAlignment(source.get("first"),actual[2].isEmpty()?null:HtseqNativePreparation.alignment(records.get(Integer.parseInt(actual[2])-1),100000),name+" unit"+i+" first");
            assertAlignment(source.get("second"),actual[3].isEmpty()?null:HtseqNativePreparation.alignment(records.get(Integer.parseInt(actual[3])-1),100000),name+" unit"+i+" second");
        }
    }
    private static void assertAlignment(Object expected,Alignment actual,String label) {
        if(expected==null){assertNull(actual,label);return;}assertNotNull(actual,label);var raw=object(expected);
        assertEquals(raw.get("aligned"),actual.aligned(),label);assertEquals(raw.get("secondary"),actual.secondary(),label);assertEquals(raw.get("supplementary"),actual.supplementary(),label);assertEquals(((Number)raw.get("mapq")).intValue(),actual.mapq(),label);
        assertEquals(raw.get("nh")==null?null:((Number)raw.get("nh")).intValue(),actual.nh(),label);var blocks=list(raw.get("blocks"));assertEquals(blocks.size(),actual.blocks().size(),label);
        for(int i=0;i<blocks.size();i++){var source=object(blocks.get(i));var block=actual.blocks().get(i);assertEquals(source.get("chromosome"),block.chromosome(),label);assertEquals(Long.parseLong((String)source.get("start")),block.start(),label);assertEquals(Long.parseLong((String)source.get("end")),block.end(),label);assertEquals(((String)source.get("strand")).charAt(0),block.strand(),label);}
    }
    private Map<String,Object> basic(String name)throws Exception {var control=object(list(ORIGINAL.get("cases")).get(0));return request(object(object(control.get("prepared")).get("config")),name);}
    @Test void verifierReparsesCompleteSavedInputsAndDetectsTampering()throws Exception {
        assertEquals(0,run(basic("count"),"count-request"),diagnostics);
        assertEquals(0,run(Map.of("schema_version",1,"operation","VERIFY","run","count","out","verify"),"verify-request"),diagnostics);
        assertTrue(Files.readString(directory.resolve("verify/verification.json")).contains("\"native_file_preparation_and_count_replayed\":true"));
        Files.writeString(directory.resolve("count/counts.tsv"),"feature\tcount\nA\t999\n");
        assertEquals(2,run(Map.of("schema_version",1,"operation","VERIFY","run","count","out","tampered"),"tampered-request"),diagnostics);assertFalse(Files.exists(directory.resolve("tampered/verification.json")));
        var yaml=new org.yaml.snakeyaml.Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(new org.yaml.snakeyaml.LoaderOptions()));
        var manifest=object(yaml.load(Files.readString(directory.resolve("count/manifest.yaml"))));object(manifest.get("output_sha256")).put("counts.tsv",FollowupSupport.hash(directory.resolve("count/counts.tsv")));
        Files.writeString(directory.resolve("count/manifest.yaml"),new org.yaml.snakeyaml.Yaml().dump(manifest));
        assertEquals(2,run(Map.of("schema_version",1,"operation","VERIFY","run","count","out","resealed"),"resealed-request"),diagnostics);assertTrue(diagnostics.contains("replay differs: counts.tsv"));assertFalse(Files.exists(directory.resolve("resealed/verification.json")));
    }
    @Test void sourceErrorsAndCapacityRefusalsAdmitNoCountTables()throws Exception {
        var missing=basic("missing");input("missing-id.gtf");missing.put("annotation","missing-id.gtf");assertEquals(2,run(missing,"missing-request"),diagnostics);assertFalse(Files.exists(directory.resolve("missing/counts.tsv")));
        var unstranded=basic("dot");input("unstranded.gtf");unstranded.put("annotation","unstranded.gtf");assertEquals(2,run(unstranded,"dot-request"),diagnostics);
        var limited=basic("capacity");limited.put("maximum_units",1);assertEquals(2,run(limited,"capacity-request"),diagnostics);assertFalse(Files.exists(directory.resolve("capacity/results.tsv")));assertTrue(Files.exists(directory.resolve("capacity/partial/unit-dispositions.tsv")));
        var historical=basic("old");historical.put("version","0.13.5");assertEquals(2,run(historical,"old-request"));assertFalse(Files.exists(directory.resolve("old")));
        var overflow=basic("overflow");Files.writeString(directory.resolve("overflow.gtf"),"chr1\tsynthetic\texon\t-9223372036854775808\t9223372036854775807\t.\t+\t.\tgene_id \"invalid\";\n");overflow.put("annotation","overflow.gtf");
        assertEquals(2,run(overflow,"overflow-request"),diagnostics);assertFalse(Files.exists(directory.resolve("overflow/counts.tsv")));
    }
    @Test void gtfQuotedSemicolonsDuplicatesAndTrailingSpacesFollowOriginalParser()throws Exception {
        assertEquals("last;value",HtseqNativePreparation.attributes("gene_id \"first\"; gene_id \"last;value\";").get("gene_id"));
        assertEquals("\"A\" ",HtseqNativePreparation.attributes("gene_id \"A\" ;").get("gene_id"));
        assertThrows(java.io.IOException.class,()->HtseqNativePreparation.attributes("gene_id \"unclosed;"));
    }
}
