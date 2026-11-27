/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.jlinalg.sequence.WaspRemapAudit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.jlinalg.cli.NextflowFiles.*;

class WaspMappingTest {
    @TempDir Path temp;
    private List<?> cases()throws Exception {
        try(var raw=getClass().getResourceAsStream("/org/jlinalg/sequence/wasp-mapping/original-oracle.json.gz")) {
            assertNotNull(raw);try(var gzip=new GZIPInputStream(raw)){var doc=object(SimpleJson.parse(new String(gzip.readAllBytes(),StandardCharsets.UTF_8)));assertEquals(WaspRemapAudit.SOURCE_REVISION,doc.get("WASP_revision"));assertEquals(false,doc.get("expected_results_from_project_Java"));return list(doc.get("cases"));}
        }
    }
    @TestFactory Stream<DynamicTest> completeOriginalSourceRecordAndIndexOracles()throws Exception {
        var tests=new ArrayList<DynamicTest>();
        for(Object item:cases()){var row=object(item);tests.add(DynamicTest.dynamicTest(text(row.get("id")),()->{
            Path root=Files.createDirectory(temp.resolve(text(row.get("id"))));write(root,row);var report=audit(root,row,new WaspRemapAudit.Limits(100000,100000000));
            var expected=new TreeMap<String,Long>();for(var entry:object(row.get("expected_original_filter_counters")).entrySet())expected.put(text(entry.getKey()),integer(entry.getValue(),0,Long.MAX_VALUE));assertEquals(expected,report.originalFilterCounters());
            assertEquals(list(object(row.get("stages")).get("final")).size(),report.recordPopulations().get("final_records").intValue());assertEquals(8,report.stageCanonicalRecordSha256().size());
        }));}
        return tests.stream();
    }
    private void write(Path root,Map<?,?> row)throws Exception {
        var stages=object(row.get("stages"));for(var entry:stages.entrySet()){String role=text(entry.getKey());var header=new SAMFileHeader();for(Object item:list(row.get("dictionary"))){var sequence=object(item);header.addSequence(new SAMSequenceRecord(text(sequence.get("SN")),(int)integer(sequence.get("LN"),1,Integer.MAX_VALUE)));}
            boolean indexed=role.equals("final");header.setSortOrder(indexed?SAMFileHeader.SortOrder.coordinate:SAMFileHeader.SortOrder.unsorted);var parser=new SAMLineParser(header);var factory=new SAMFileWriterFactory().setCreateIndex(indexed);
            try(var writer=factory.makeBAMWriter(header,true,root.resolve(role+".bam").toFile())){for(Object line:list(entry.getValue()))writer.addAlignment(parser.parseLine(text(line)));}
        }
    }
    private WaspRemapAudit.Report audit(Path root,Map<?,?> row,WaspRemapAudit.Limits limits)throws Exception {
        return WaspRemapAudit.audit(root.resolve("original.bam"),root.resolve("sorted_input.bam"),root.resolve("immediate.bam"),root.resolve("candidates.bam"),root.resolve("remapped.bam"),root.resolve("filtered.bam"),root.resolve("merged.bam"),root.resolve("final.bam"),Boolean.TRUE.equals(row.get("paired")),Boolean.TRUE.equals(row.get("deduplicated")),limits,()->{});
    }
    @Test void aRetainedWrongChromosomeCoordinateCoincidenceCannotPass()throws Exception {
        var row=object(cases().get(0));Path root=Files.createDirectory(temp.resolve("wrong-chromosome"));write(root,row);Path bam=root.resolve("remapped.bam");var records=new ArrayList<SAMRecord>();SAMFileHeader header;
        try(var input=SamReaderFactory.makeDefault().open(bam)){header=input.getFileHeader();for(var read:input){if(read.getReadName().startsWith("normal.snp."))read.setReferenceName("chr2");records.add(read);}}
        Files.delete(bam);try(var out=new SAMFileWriterFactory().makeBAMWriter(header,true,bam.toFile())){for(var record:records)out.addAlignment(record);}
        var error=assertThrows(IOException.class,()->audit(root,row,new WaspRemapAudit.Limits(100000,100000000)));assertTrue(error.getMessage().contains("different chromosome"));
    }
    @Test void missingIndexAndNativeCapacitiesProduceNoAuditResult()throws Exception {
        var row=object(cases().get(0));Path root=Files.createDirectory(temp.resolve("missing-index"));write(root,row);assertThrows(IOException.class,()->audit(root,row,new WaspRemapAudit.Limits(1,100000000)));assertThrows(IOException.class,()->audit(root,row,new WaspRemapAudit.Limits(100000,1)));
        for(Path path:List.of(root.resolve("final.bai"),root.resolve("final.bam.bai")))Files.deleteIfExists(path);assertThrows(IOException.class,()->audit(root,row,new WaspRemapAudit.Limits(100000,100000000)));
    }
    @Test void sourceRequestOptionsAreTypedAndDoNotPermitOutputRedirection()throws Exception {
        var example=exampleRequest();assertEquals("text",WaspRequest.parse(example).mode());
        for(List<String> options:List.of(List.of("-o","/outside.sam"),List.of("-t","2"),List.of("-k","1.5"),List.of("-A","2147483648"),List.of("-r","NaN"),List.of("-M"))) {
            var value=new LinkedHashMap<Object,Object>(example);var settings=new LinkedHashMap<Object,Object>(object(example.get("settings")));settings.put("additional_bwa_options",options);value.put("settings",settings);assertThrows(Exception.class,()->WaspRequest.parse(value));
        }
        for(Object insert:List.of(Map.of("mean",1000000000,"standard_deviation",1000000000),Map.of("mean",0,"standard_deviation",10),Map.of("mean",250,"standard_deviation",0))) {
            var value=new LinkedHashMap<Object,Object>(example);var settings=new LinkedHashMap<Object,Object>(object(example.get("settings")));var limits=new LinkedHashMap<Object,Object>(object(insert));limits.put("maximum",null);limits.put("minimum",null);settings.put("insert_size",limits);value.put("settings",settings);assertThrows(IOException.class,()->WaspRequest.parse(value));
        }
    }
    private Map<Object,Object> exampleRequest()throws Exception {
        var example=new LinkedHashMap<Object,Object>(object(WaspToolsCli.example("analysis")));
        example.put("runtime",temp.resolve("runtime").toAbsolutePath().normalize().toString());example.put("bam",temp.resolve("input.bam").toAbsolutePath().normalize().toString());
        var reference=new LinkedHashMap<Object,Object>(object(example.get("reference")));reference.put("path",temp.resolve("reference.fa").toAbsolutePath().normalize().toString());example.put("reference",reference);
        var variants=new LinkedHashMap<Object,Object>(object(example.get("variants")));variants.put("directory",temp.resolve("snps").toAbsolutePath().normalize().toString());example.put("variants",variants);return example;
    }
    private List<?> converterCases()throws Exception {
        try(var raw=getClass().getResourceAsStream("/org/jlinalg/sequence/wasp-mapping/original-converter-oracle.json.gz")) {
            assertNotNull(raw);try(var gzip=new GZIPInputStream(raw)){var doc=object(SimpleJson.parse(new String(gzip.readAllBytes(),StandardCharsets.UTF_8)));assertEquals(WaspRemapAudit.SOURCE_REVISION,doc.get("WASP_revision"));assertEquals(false,doc.get("expected_results_from_project_Java"));return list(doc.get("cases"));}
        }
    }
    private WaspInputs.Report converterInput() {return new WaspInputs.Report(Map.of(),Map.of("chr1",new WaspInputs.Reference(10000,"not-used",0)),Set.of("chr1"),Set.of("chr1"),List.of());}
    @TestFactory Stream<DynamicTest> completeOriginalCConverterCellsAndNativeSourceGTParsing()throws Exception {
        var tests=new ArrayList<DynamicTest>();for(Object item:converterCases()){var row=object(item);tests.add(DynamicTest.dynamicTest(text(row.get("id")),()->{
            Path root=Files.createDirectory(temp.resolve(text(row.get("id"))));Files.writeString(root.resolve("chromInfo.txt"),text(row.get("chromosomes")));Files.writeString(root.resolve("input.vcf"),text(row.get("vcf")));
            var expected=WaspInputs.vcf(root,Map.of("chromosomes","chromInfo.txt","vcfs",List.of("input.vcf")),converterInput(),WaspRequest.parse(exampleRequest()),()->{});
            WaspInputs.convertedHdf(row.get("expected_original_converted_population"),expected);
        }));}return tests.stream();
    }
    @Test void lossyCConversionAndAmbiguousGTOrCoordinatesAreRefused()throws Exception {
        var row=object(converterCases().get(1));Files.writeString(temp.resolve("chromInfo.txt"),text(row.get("chromosomes")));String valid=text(row.get("vcf"));var spec=WaspRequest.parse(exampleRequest());
        for(String vcf:List.of(valid.replace("\tA\tC\t","\t"+"A".repeat(100)+"\tC\t"),valid.replace("5:0|1:","5:1:"),valid.replace("DP:GT:AD","DP:XX:AD"),valid.replace("\t1141\t","\t1041\t"),valid.replace("\tS1\t","\t"+"X".repeat(100)+"\t"))) {
            Files.writeString(temp.resolve("input.vcf"),vcf);assertThrows(IOException.class,()->WaspInputs.vcf(temp,Map.of("chromosomes","chromInfo.txt","vcfs",List.of("input.vcf")),converterInput(),spec,()->{}));
        }
    }
    @Test void malformedHdfIndexAndUnavailableSelectedSamplesCannotBypassFiltering()throws Exception {
        var fixture=object(converterCases().get(1));var value=exampleRequest();value.put("variants",Map.of("mode","hdf5","snp_table",temp.resolve("table.h5").toString(),"snp_index",temp.resolve("index.h5").toString(),"haplotypes",temp.resolve("haps.h5").toString(),"samples",List.of("S1")));var settings=new LinkedHashMap<Object,Object>(object(value.get("settings")));settings.put("hdf_contig_policy","literal");value.put("settings",settings);var spec=WaspRequest.parse(value);var census=object(fixture.get("expected_original_converted_population"));WaspInputs.hdf(census,converterInput(),spec,()->{});
        String original=NextflowJson.json(census),mutated=original.replaceFirst("\"mismatched_cells\"\\s*:\\s*0","\"mismatched_cells\":1");assertNotEquals(original,mutated);var changed=SimpleJson.parse(mutated);
        assertThrows(IOException.class,()->WaspInputs.hdf(changed,converterInput(),spec,()->{}));
        value.put("variants",Map.of("mode","hdf5","snp_table",temp.resolve("table.h5").toString(),"snp_index",temp.resolve("index.h5").toString(),"haplotypes",temp.resolve("haps.h5").toString(),"samples",List.of("absent-sample")));var unknown=WaspRequest.parse(value);assertThrows(IOException.class,()->WaspInputs.hdf(census,converterInput(),unknown,()->{}));
    }
    @Test void missingInputHasProminentSkipAndRecoveryInstructions() {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int status=WaspToolsCli.run(new String[]{"--request","no-such-WASP-request.json","--out","unused-WASP-capture"},new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));assertEquals(3,status);assertTrue(err.toString(StandardCharsets.UTF_8).contains("SKIP3 DEPENDENT WASP ANALYSIS SKIPPED"));assertTrue(err.toString(StandardCharsets.UTF_8).contains("WARNINGS AND REQUIRED ACTION"));assertEquals(0,out.size());
    }
}
