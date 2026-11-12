/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import htsjdk.samtools.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeHtsCliTest {
    @TempDir Path directory;
    private String diagnostics;
    private int run(String method,Path input,String name,String... flags) {
        var args=new ArrayList<>(List.of("hts-tools","--engine","native","--method",method,"--input",input.toString(),"--out",directory.resolve(name).toString(),"--no-config"));args.addAll(List.of(flags));
        var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);int code=JLinAlgCli.run(args.toArray(String[]::new),stream,stream);diagnostics=bytes.toString();
        Path warnings=directory.resolve(name+"/WARNINGS.md");try{if(Files.exists(warnings))diagnostics+=Files.readString(warnings);}catch(Exception failure){throw new AssertionError(failure);}
        return code;
    }
    private Path sam()throws Exception {
        Path sam=directory.resolve("reads.sam");Files.writeString(sam,"@HD\tVN:1.6\tSO:unsorted\n@SQ\tSN:chr1\tLN:1000\n"
            +"read2\t1024\tchr1\t100\t50\t10M\t*\t0\t0\tACGTACGTAC\tIIIIIIIIII\tZZ:Z:retained\n"
            +"read10\t0\tchr1\t200\t60\t10M\t*\t0\t0\tAAAAAAAAAA\tHHHHHHHHHH\n"
            +"read3\t512\tchr1\t110\t10\t10M\t*\t0\t0\tCCCCCCCCCC\t!!!!!!!!!!\n"
            +"unmapped\t4\t*\t0\t0\t*\t*\t0\t0\tTTTTTTTTTT\tIIIIIIIIII\n");return sam;
    }
    private static List<String> names(Path bam)throws Exception {
        try(var reader=SamReaderFactory.makeDefault().open(bam)){return reader.iterator().stream().map(SAMRecord::getReadName).toList();}
    }
    @Test void sortingSpillsToDiskAndPreservesTagsQualitiesAndLexicalOrder()throws Exception {
        Path input=sam();assertEquals(0,run("sam-sort",input,"sorted","--max-records-in-ram","1","--create-index","true"),diagnostics);
        Path bam=directory.resolve("sorted/alignment.bam");assertEquals(List.of("read2","read3","read10","unmapped"),names(bam));
        try(var reader=SamReaderFactory.makeDefault().open(bam)){var record=reader.iterator().next();assertEquals("retained",record.getStringAttribute("ZZ"));assertEquals("IIIIIIIIII",record.getBaseQualityString());assertTrue(reader.hasIndex());}
        assertFalse(Files.exists(directory.resolve("sorted/sort-tmp")));
        assertEquals(0,run("sam-sort",input,"lex","--sort-order","queryname","--max-records-in-ram","2"),diagnostics);
        assertEquals(List.of("read10","read2","read3","unmapped"),names(directory.resolve("lex/alignment.bam")));
        assertEquals(0,run("sam-index",bam,"index"),diagnostics);assertTrue(Files.isRegularFile(directory.resolve("index/alignment.bai")));
        try(var indexed=SamReaderFactory.makeDefault().open(SamInputResource.of(bam).index(directory.resolve("index/alignment.bai")));var records=indexed.queryOverlapping("chr1",109,110)) {
            assertEquals(List.of("read2","read3"),records.stream().map(SAMRecord::getReadName).toList());
        }
    }
    @Test void filteringStreamsUnindexedInputsWithInclusiveRegionAndExactFlags()throws Exception {
        Path input=sam();assertEquals(0,run("sam-view",input,"region","--region","chr1:109-110"),diagnostics);
        assertEquals(List.of("read2","read3"),names(directory.resolve("region/alignment.bam")));
        assertEquals(0,run("sam-view",input,"flags","--exclude-flags","1024","--min-mapq","20"),diagnostics);
        assertEquals(List.of("read10"),names(directory.resolve("flags/alignment.bam")));
        assertEquals(2,run("sam-view",input,"invalid","--region","absent:1-2"),diagnostics);
        assertFalse(Files.exists(directory.resolve("invalid/results.tsv")));
    }
    @Test void fullFlagStatisticsSeparateQcGroupsAndRetainUndefinedPercentages()throws Exception {
        assertEquals(0,run("sam-flagstat",sam(),"flags"),diagnostics);
        var groups=(Map<?,?>)SimpleJson.parse(Files.readString(directory.resolve("flags/flagstat.json")));
        var passed=(Map<?,?>)groups.get("QC-passed reads");var failed=(Map<?,?>)groups.get("QC-failed reads");
        assertEquals(3L,passed.get("total"));assertEquals(2L,passed.get("mapped"));assertEquals(1L,passed.get("primary duplicates"));
        assertEquals(1L,failed.get("total"));assertEquals(1L,failed.get("primary mapped"));assertNull(passed.get("properly paired %"));
    }
    @Test void sourcePercentageTiesUseNearestEvenRounding()throws Exception {
        Path input=directory.resolve("percentage-tie.sam");var data=new StringBuilder("@HD\tVN:1.6\n@SQ\tSN:chr1\tLN:1000\n");
        data.append("mapped\t0\tchr1\t1\t60\t1M\t*\t0\t0\tA\tI\n");for(int i=0;i<31;i++)data.append("unmapped").append(i).append("\t4\t*\t0\t0\t*\t*\t0\t0\tA\tI\n");Files.writeString(input,data);
        assertEquals(0,run("sam-flagstat",input,"tie"),diagnostics);var groups=(Map<?,?>)SimpleJson.parse(Files.readString(directory.resolve("tie/flagstat.json")));assertEquals(3.12,((Map<?,?>)groups.get("QC-passed reads")).get("mapped %"));
        String executable=System.getenv("JLINALG_SAMTOOLS");
        if(executable!=null) {
            Path reference=directory.resolve("original-tie.json");var process=new ProcessBuilder(executable,"flagstat","-O","json",input.toString()).redirectOutput(reference.toFile()).redirectError(directory.resolve("original-tie.log").toFile()).start();
            try{assertTrue(process.waitFor(60,TimeUnit.SECONDS));assertEquals(0,process.exitValue());}finally{if(process.isAlive())process.destroyForcibly();}
            assertEquals(SimpleJson.parse(Files.readString(reference)),groups);
        }
    }
    @Test void wrongOrderUnsupportedMethodsAndInvalidEngineNeverAdmitResults()throws Exception {
        Path input=sam();Path falselySorted=directory.resolve("false.bam");
        var header=new SAMFileHeader();header.addSequence(new SAMSequenceRecord("chr1",1000));header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        // The SAM form permits deliberately lying header metadata; native indexing must inspect records.
        Path bad=directory.resolve("wrong.sam");Files.writeString(bad,Files.readString(input).replace("SO:unsorted","SO:coordinate").replace("read2\t1024\tchr1\t100","read2\t1024\tchr1\t300"));
        try(var reader=SamReaderFactory.makeDefault().open(bad);var writer=new SAMFileWriterFactory().makeBAMWriter(header,true,falselySorted)){
            ((SAMFileWriterImpl)writer).setSortOrderChecking(false);for(var record:reader)writer.addAlignment(record);
        }
        assertEquals(2,run("sam-index",falselySorted,"wrong-order"),diagnostics);
        assertFalse(Files.exists(directory.resolve("wrong-order/alignment.bai")));
        assertTrue(diagnostics.contains("not actually coordinate sorted"));
    }
    @Test void unsupportedNativeMethodAndOptionsAreRefusedWithoutAnExternalFallback()throws Exception {
        Path input=sam();assertEquals(2,run("sam-markdup",input,"markdup"));assertFalse(Files.exists(directory.resolve("markdup")));
        assertEquals(2,run("sam-sort",input,"threads","--threads","2"));assertFalse(Files.exists(directory.resolve("threads")));
        assertEquals(2,run("sam-sort",input,"badindex","--sort-order","queryname","--create-index","true"));
        assertFalse(Files.exists(directory.resolve("badindex/alignment.bam")));
    }
    @Test void originalSamtoolsMatchesAll4096FlagCombinations()throws Exception {
        String executable=System.getenv("JLINALG_SAMTOOLS");assumeTrue(executable!=null,"Optional independent SAMtools1.24 reference");
        Path input=directory.resolve("all-flags.bam");var header=new SAMFileHeader();header.addSequence(new SAMSequenceRecord("chr1",1000));header.addSequence(new SAMSequenceRecord("chr2",1000));
        try(var writer=new SAMFileWriterFactory().makeBAMWriter(header,true,input)) {
            for(int flags=0;flags<4096;flags++) {
                var r=new SAMRecord(header);r.setReadName("r"+flags);r.setFlags(flags);r.setReferenceIndex(0);r.setAlignmentStart(1);r.setCigarString("4M");r.setReadString("ACGT");r.setBaseQualityString("IIII");r.setMateReferenceIndex((flags&16)==0?0:1);r.setMateAlignmentStart(5);r.setMappingQuality((int)(((flags*2654435761L)>>>8)%61));writer.addAlignment(r);
            }
        }
        assertEquals(0,run("sam-flagstat",input,"native"),diagnostics);
        Path reference=directory.resolve("original.json"),log=directory.resolve("original.log");
        var process=new ProcessBuilder(executable,"flagstat","-O","json",input.toString()).redirectOutput(reference.toFile()).redirectError(log.toFile()).start();
        try{assertTrue(process.waitFor(60,TimeUnit.SECONDS));assertEquals(0,process.exitValue(),Files.readString(log));}finally{if(process.isAlive())process.destroyForcibly();}
        assertEquals(SimpleJson.parse(Files.readString(reference)),SimpleJson.parse(Files.readString(directory.resolve("native/flagstat.json")))) ;
    }
}
