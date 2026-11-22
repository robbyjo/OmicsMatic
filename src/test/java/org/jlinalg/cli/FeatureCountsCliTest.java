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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

class FeatureCountsCliTest {
    @TempDir(cleanup=CleanupMode.ON_SUCCESS) Path dir;
    private String output;
    private String runtime() { String value=System.getenv("JLINALG_FEATURECOUNTS_HOME"); assumeTrue(value!=null,"Set JLINALG_FEATURECOUNTS_HOME for real upstream featureCounts tests"); return value; }
    private int run(String name,String method,String... extra) throws Exception {
        var args=new ArrayList<>(List.of("feature-counts","--method",method,"--out",dir.resolve(name).toString(),"--no-config")); args.addAll(List.of(extra));
        var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);int code=JLinAlgCli.run(args.toArray(String[]::new),stream,stream);output=bytes.toString();
        for (String f:List.of("WARNINGS.md","count.log","version.log")) if (code!=0&&Files.exists(dir.resolve(name).resolve(f))) output+=Files.readString(dir.resolve(name).resolve(f));return code;
    }
    private Path annotation(boolean overlap) throws Exception {
        Path p=dir.resolve("genes with spaces.gtf");Files.writeString(p,"chr1\tf\texon\t101\t200\t.\t+\t.\tgene_id \"G1\"; transcript_id \"T1\";\nchr1\tf\texon\t401\t500\t.\t+\t.\tgene_id \"G1\"; transcript_id \"T1\";\nchr1\tf\texon\t701\t800\t.\t-\t.\tgene_id \"G2\"; transcript_id \"T2\";\n"+(overlap?"chr1\tf\texon\t101\t200\t.\t+\t.\tgene_id \"G3\"; transcript_id \"T3\";\n":""));return p;
    }
    private SAMFileHeader header() { var h=new SAMFileHeader();h.setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("chr1",1000)))); h.setSortOrder(SAMFileHeader.SortOrder.unsorted); return h; }
    private SAMRecord record(SAMFileHeader h,String id,int start,String cigar,boolean reverse,Integer nh) {
        var r=new SAMRecord(h);r.setReadName(id);r.setReferenceName("chr1");r.setAlignmentStart(start);r.setCigarString(cigar);r.setMappingQuality(60);r.setReadNegativeStrandFlag(reverse);int n=r.getCigar().getReadLength();r.setReadString("A".repeat(n));r.setBaseQualityString("I".repeat(n));if(nh!=null)r.setAttribute("NH",nh);return r;
    }
    private Path inputs(boolean paired,boolean multi,boolean missingNh) throws Exception {
        var h=header(); Path bam=dir.resolve("alignment with spaces.bam");
        try(var writer=new SAMFileWriterFactory().makeBAMWriter(h,false,bam)) {
            if (paired) {
                var a=record(h,"pair",111,"30M",false,1);var b=record(h,"pair",161,"30M",true,1);
                a.setReadPairedFlag(true);b.setReadPairedFlag(true);a.setFirstOfPairFlag(true);b.setSecondOfPairFlag(true);a.setProperPairFlag(true);b.setProperPairFlag(true);a.setMateReferenceName("chr1");b.setMateReferenceName("chr1");a.setMateAlignmentStart(161);b.setMateAlignmentStart(111);a.setMateNegativeStrandFlag(true);a.setInferredInsertSize(80);b.setInferredInsertSize(-80);writer.addAlignment(a);writer.addAlignment(b);
            } else {
                writer.addAlignment(record(h,"spliced",181,"20M200N20M",false,missingNh?null:1));
                writer.addAlignment(record(h,"reverse",711,"30M",true,1));
                if (multi) { var a=record(h,"multi",111,"30M",false,2);var b=record(h,"multi",421,"30M",false,2);b.setSecondaryAlignment(true);writer.addAlignment(a);writer.addAlignment(b); }
            }
        }
        Path p=dir.resolve("samples.tsv");Files.writeString(p,"sample\talignment\tstrand\nS1\t"+bam.getFileName()+"\t0\n");return p;
    }
    private int count(String name,Path inputs,Path annotation,String library,String unit,String... extra) throws Exception {
        var args=new ArrayList<>(List.of("--home",runtime(),"--inputs",inputs.toString(),"--annotation",annotation.toString(),"--library",library,"--unit",unit));args.addAll(List.of(extra));return run(name,"count",args.toArray(String[]::new));
    }
    @Test void rawGeneAndJunctionCountsRecoverKnownSplitReads() throws Exception {
        Path g=annotation(false),i=inputs(false,false,false);assertEquals(0,count("gene",i,g,"single","reads","--junctions","true"),output);
        String counts=Files.readString(dir.resolve("gene/counts.tsv"));assertTrue(counts.contains("G1\t200\t1"),counts);assertTrue(counts.contains("G2\t100\t1"),counts);
        assertTrue(Files.readString(dir.resolve("gene/assignment-summary.tsv")).contains("Assigned\t2"));
        String j=Files.readString(dir.resolve("gene/junction-counts.tsv"));String[] fields=j.lines().skip(1).findFirst().orElseThrow().split("\t",-1);assertEquals("chr1",fields[6]);assertEquals("200",fields[7]);assertEquals("chr1",fields[9]);assertEquals("401",fields[10]);assertEquals("1",fields[14]);
    }
    @Test void pairedReadsAndFragmentsAreDifferentExplicitUnits() throws Exception {
        Path g=annotation(false),i=inputs(true,false,false);
        assertEquals(0,count("reads",i,g,"paired","reads"),output);assertTrue(Files.readString(dir.resolve("reads/counts.tsv")).contains("G1\t200\t2"));
        assertEquals(0,count("fragments",i,g,"paired","fragments","--both-ends","true","--check-distance","true","--min-fragment","60","--max-fragment","100"),output);assertTrue(Files.readString(dir.resolve("fragments/counts.tsv")).contains("G1\t200\t1"));
        assertTrue(Files.readString(dir.resolve("fragments/session.yaml")).contains("--countReadPairs"));assertFalse(Files.readString(dir.resolve("reads/session.yaml")).contains("--countReadPairs"));
    }
    @Test void ambiguousGenesAndMultimappingHaveExplicitFractionalPolicies() throws Exception {
        Path g=annotation(true),i=inputs(false,true,false);
        assertEquals(0,count("exclude",i,g,"single","reads"),output);assertTrue(Files.readString(dir.resolve("exclude/counts.tsv")).contains("G1\t200\t0"));
        assertEquals(0,count("fraction",i,g,"single","reads","--overlap","all","--multimapping","include","--fraction","true"),output);
        String c=Files.readString(dir.resolve("fraction/counts.tsv"));assertTrue(c.contains("G1\t200\t1.25"),c);assertTrue(c.contains("G3\t100\t0.75"),c);
        assertEquals(0,count("primary",i,g,"single","reads","--overlap","all","--multimapping","include","--fraction","true","--primary-only","true"),output);
        assertTrue(Files.readString(dir.resolve("primary/counts.tsv")).contains("G1\t200\t1"));assertTrue(Files.readString(dir.resolve("primary/WARNINGS.md")).contains("disables NH-based fractional weighting"));
    }
    @Test void safPeakCountingAndStrandPoliciesAreNotGeneOnly() throws Exception {
        Path i=inputs(false,false,false),saf=dir.resolve("peaks.saf");Files.writeString(saf,"GeneID\tChr\tStart\tEnd\tStrand\npeak1\tchr1\t180\t200\t+\npeak2\tchr1\t700\t800\t+\n");
        Files.writeString(i,Files.readString(i).replace("\t0\n","\t1\n"));assertEquals(0,count("strand1",i,saf,"single","reads","--format","SAF"),output);
        String c=Files.readString(dir.resolve("strand1/counts.tsv"));assertTrue(c.contains("peak1\t21\t1"),c);assertTrue(c.contains("peak2\t101\t0"),c);
        Files.writeString(i,Files.readString(i).replace("\t1\n","\t2\n"));assertEquals(0,count("strand2",i,saf,"single","reads","--format","SAF"),output);assertTrue(Files.readString(dir.resolve("strand2/counts.tsv")).contains("peak2\t101\t1"));
    }
    @Test void missingNhRequiresAnExplicitAssumptionAndWrongLibraryFails() throws Exception {
        Path g=annotation(false),i=inputs(false,false,true);
        assertEquals(2,count("missing-nh",i,g,"single","reads"),output);assertTrue(output.contains("lack NH"));assertFalse(Files.exists(dir.resolve("missing-nh/counts.tsv")));
        assertEquals(0,count("assumed",i,g,"single","reads","--nh-policy","assume-unique"),output);assertTrue(Files.readString(dir.resolve("assumed/WARNINGS.md")).contains("does not establish unique mapping"));
        assertEquals(2,count("wrong-library",i,g,"paired","fragments","--nh-policy","assume-unique"),output);assertTrue(output.contains("library flags"));
    }
    @Test void nativeAnnotationSpliceAndNormalizationWorkWithoutUpstreamRuntime() throws Exception {
        Path g=annotation(false),f=dir.resolve("ref.fa");Files.writeString(f,">chr1\n"+"A".repeat(1000)+"\n");
        assertEquals(0,run("saf","annotation","--annotation",g.toString(),"--reference",f.toString()),output);assertTrue(Files.readString(dir.resolve("saf/results.tsv")).contains("G1\t200"));
        assertEquals(0,run("splice","splice-sites","--annotation",g.toString(),"--reference",f.toString()),output);assertEquals("chr1\t199\t400\t+\n",Files.readString(dir.resolve("splice/splice-sites.tsv")));
        Path c=dir.resolve("counts.tsv");Files.writeString(c,"feature_id\tlength\tS\ng1\t1000\t10\ng2\t2000\t20\n");
        assertEquals(0,run("tpm","normalize","--counts",c.toString(),"--normalization","tpm"),output);assertTrue(Files.readString(dir.resolve("tpm/results.tsv")).contains("g1\t1000\t500000.0"));
        assertEquals(0,run("fpkm","normalize","--counts",c.toString(),"--normalization","fpkm","--library-units","1000000"),output);assertTrue(Files.readString(dir.resolve("fpkm/results.tsv")).contains("g2\t2000\t10.0"));
        Files.writeString(c,"feature_id\tlength\tS\ng1\t1000\t0\n");assertEquals(2,run("zero","normalize","--counts",c.toString(),"--normalization","tpm"),output);assertFalse(Files.exists(dir.resolve("zero/results.tsv")));assertTrue(output.contains("positive finite total"));
    }
    @Test void unavailableRuntimeSkipsWithActionAndInvalidOptionsFailEarly() throws Exception {
        assertEquals(3,run("skip","count","--home",dir.resolve("absent").toString(),"--inputs","absent","--annotation","absent","--library","single","--unit","reads"),output);assertTrue(Files.readString(dir.resolve("skip/WARNINGS.md")).contains("install_rna_aligners.py --profile featurecounts --allow-download"));
        assertEquals(2,run("bad","count","--home","absent","--inputs","absent","--annotation","absent","--library","single","--unit","fragments"),output);assertFalse(Files.exists(dir.resolve("bad")));
    }
    @Test void multiSampleStrandColumnsAndDuplicateFilteringKeepSeparateCounts() throws Exception {
        Path g=annotation(false),i=inputs(false,false,false);Path first=dir.resolve("alignment with spaces.bam"),second=dir.resolve("second.bam");
        try(var r=SamReaderFactory.makeDefault().open(first);var w=new SAMFileWriterFactory().makeBAMWriter(r.getFileHeader(),false,second);var it=r.iterator()) {while(it.hasNext()){var record=it.next();record.setDuplicateReadFlag(true);w.addAlignment(record);}}
        Files.writeString(i,"sample\talignment\tstrand\nS1\t"+first.getFileName()+"\t1\nS2\t"+second.getFileName()+"\t2\n");
        assertEquals(0,count("multi",i,g,"single","reads"),output);String c=Files.readString(dir.resolve("multi/counts.tsv"));assertTrue(c.contains("feature_id\tlength\tS1\tS2"),c);assertTrue(c.contains("G1\t200\t1\t0"),c);
        assertEquals(0,count("dedup-filter",i,g,"single","reads","--ignore-duplicates","true"),output);assertTrue(Files.readString(dir.resolve("dedup-filter/counts.tsv")).contains("G2\t100\t1\t0"));assertTrue(Files.readString(dir.resolve("dedup-filter/assignment-summary.tsv")).contains("Unassigned_Duplicate\t0\t2"));
    }
}
