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
import java.util.Random;
import org.jlinalg.sequence.FastqIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

class MinimapToolsCliTest {
    @TempDir(cleanup=CleanupMode.ON_SUCCESS) Path dir;
    private String output;
    private String home() { String value=System.getenv("JLINALG_MINIMAP2_HOME");assumeTrue(value!=null,"Set JLINALG_MINIMAP2_HOME for real minimap2 tests");return value; }
    private int run(String name,String... extra) throws Exception {
        var args=new ArrayList<>(List.of("minimap-tools","--out",dir.resolve(name).toString(),"--no-config"));args.addAll(List.of(extra));
        var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);int c=JLinAlgCli.run(args.toArray(String[]::new),stream,stream);output=bytes.toString();
        for(String f:List.of("WARNINGS.md","alignment.log","version.log")) if(c!=0&&Files.exists(dir.resolve(name).resolve(f)))output+=Files.readString(dir.resolve(name).resolve(f));return c;
    }
    private String dna() {var random=new Random(1541);var b=new StringBuilder();for(int i=0;i<10000;i++)b.append("ACGT".charAt(random.nextInt(4)));b.replace(400,402,"GT");b.replace(598,600,"AG");return b.toString();}
    private Path reference() throws Exception {Path p=dir.resolve("reference with spaces.fa");Files.writeString(p,">chr1\n"+dna()+"\n");return p;}
    private Path fastq(String name,int mate,String... reads) throws Exception {Path p=dir.resolve(name);try(var w=new FastqIO.Writer(p)){for(int i=0;i<reads.length;i++)w.write(new FastqIO.Read("@q"+i+(mate==0?"":"/"+mate),reads[i],"I".repeat(reads[i].length())));}return p;}
    private List<SAMRecord> sam(String name) throws Exception {try(var r=SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT).open(dir.resolve(name+"/alignment.sam"));var it=r.iterator()){var rows=new ArrayList<SAMRecord>();while(it.hasNext())rows.add(it.next());return rows;}}
    @Test void hifiReadsRetainReverseBasesQualitiesAndUnmappedRecords() throws Exception {
        Path f=reference(),q=fastq("reads.fq.gz",0,dna().substring(1000,3000).toLowerCase(java.util.Locale.ROOT),MinimapToolsCli.reverse(dna().substring(5000,7000)),"N".repeat(1000));
        assertEquals(0,run("hifi","--home",home(),"--reference",f.toString(),"--query",q.toString(),"--query-format","fastq","--format","sam","--preset","map-hifi","--eqx","true","--cs","short","--secondary","false"),output);
        var rows=sam("hifi");assertEquals(3,rows.size());assertEquals(1001,rows.get(0).getAlignmentStart());assertEquals("2000=",rows.get(0).getCigarString());assertTrue(rows.get(1).getReadNegativeStrandFlag());assertTrue(rows.get(2).getReadUnmappedFlag());assertNotNull(rows.get(0).getAttribute("MD"));assertNull(rows.get(0).getAttribute("cs"));assertTrue(Files.readString(dir.resolve("hifi/WARNINGS.md")).contains("MD instead of cs"));
        assertEquals(0,run("cs-only","--home",home(),"--reference",f.toString(),"--query",q.toString(),"--query-format","fastq","--format","sam","--preset","map-hifi","--md","false","--cs","short"),output);assertNotNull(sam("cs-only").get(0).getAttribute("cs"));assertNull(sam("cs-only").get(0).getAttribute("MD"));
    }
    @Test void splicedRnaRecoversKnownJunctionWithExplicitNoncanonicalPenalty() throws Exception {
        Path f=reference(),q=fastq("rna.fq",0,dna().substring(350,400)+dna().substring(600,650));
        assertEquals(0,run("splice","--home",home(),"--reference",f.toString(),"--query",q.toString(),"--query-format","fastq","--format","sam","--preset","splice","--noncanonical-penalty","0"),output);
        var rows=sam("splice");assertEquals("50M200N50M",rows.get(0).getCigarString());assertEquals(351,rows.get(0).getAlignmentStart());
    }
    @Test void assemblyPafCigarsAndBedSpansIncludeReverseAndUnmappedQueries() throws Exception {
        Path f=reference(),q=dir.resolve("assemblies.fa");Files.writeString(q,">forward\n"+dna().substring(1000,4000)+"\n>reverse\n"+MinimapToolsCli.reverse(dna().substring(6000,9000))+"\n>unmapped\n"+"N".repeat(3000)+"\n");
        assertEquals(0,run("paf","--home",home(),"--reference",f.toString(),"--query",q.toString(),"--query-format","fasta","--format","paf","--preset","asm20","--secondary","false","--bed","true"),output);
        String bed=Files.readString(dir.resolve("paf/mappings.bed"));assertTrue(bed.contains("chr1\t1000\t4000\tforward\t60\t+"),bed);assertTrue(bed.contains("chr1\t6000\t9000\treverse\t60\t-"),bed);
        assertTrue(Files.readString(dir.resolve("paf/query-status.tsv")).contains("unmapped\t3000\tunmapped"));
    }
    @Test void shortReadPairsAndEmptyMappingsRetainCompleteInputAudit() throws Exception {
        Path f=reference(),a=fastq("R1.fq",1,dna().substring(1000,1150)),b=fastq("R2.fq",2,MinimapToolsCli.reverse(dna().substring(1300,1450)));
        assertEquals(0,run("paired","--home",home(),"--reference",f.toString(),"--query",a.toString(),"--query2",b.toString(),"--query-format","fastq","--format","sam","--preset","sr"),output);var rows=sam("paired");assertEquals(2,rows.size());assertTrue(rows.stream().allMatch(SAMRecord::getReadPairedFlag));assertTrue(rows.get(0).getFirstOfPairFlag());
        Path n=fastq("unmapped.fq",0,"N".repeat(200));assertEquals(0,run("empty-mapping","--home",home(),"--reference",f.toString(),"--query",n.toString(),"--query-format","fastq","--format","paf","--preset","map-ont"),output);assertTrue(Files.readString(dir.resolve("empty-mapping/WARNINGS.md")).contains("ZERO MAPPED"));assertTrue(Files.readString(dir.resolve("empty-mapping/query-status.tsv")).contains("unmapped"));
    }
    @Test void duplicatePafIdsFailBeforeAlignment() throws Exception {
        Path f=reference(),q=dir.resolve("duplicate.fq");Files.writeString(q,"@same\n"+dna().substring(1000,2000)+"\n+\n"+"I".repeat(1000)+"\n@same\n"+dna().substring(2000,3000)+"\n+\n"+"I".repeat(1000)+"\n");
        assertEquals(2,run("duplicate","--home",home(),"--reference",f.toString(),"--query",q.toString(),"--query-format","fastq","--format","paf","--preset","map-ont"),output);assertTrue(output.contains("unique query names"));assertFalse(Files.exists(dir.resolve("duplicate/alignment.log")));
    }
    @Test void missingDependencySkipsWithInstructions() throws Exception {
        assertEquals(3,run("skip","--home",dir.resolve("absent").toString(),"--reference","absent","--query","absent","--query-format","fastq","--format","sam","--preset","map-ont"),output);assertTrue(Files.readString(dir.resolve("skip/WARNINGS.md")).contains("install_rna_aligners.py --profile minimap2"));
    }
    @Test void multiPartIndexMergingRetainsPrimaryContentAndRejectsIncompatibleTags() throws Exception {
        Path f=dir.resolve("split.fa");Files.writeString(f,">chr1\n"+dna().substring(0,6000)+"\n>chr2\n"+dna().substring(6000)+"\n");Path q=fastq("reads.fq",0,dna().substring(7000,9000),dna().substring(1000,3000),"N".repeat(1000));
        assertEquals(2,run("incompatible","--home",home(),"--reference",f.toString(),"--query",q.toString(),"--query-format","fastq","--format","sam","--preset","map-hifi","--index-batch-bases","5000"),output);assertTrue(output.contains("cannot be combined"));assertFalse(Files.exists(dir.resolve("incompatible/alignment.log")));
        assertEquals(0,run("split-index","--home",home(),"--reference",f.toString(),"--query",q.toString(),"--query-format","fastq","--format","sam","--preset","map-hifi","--index-batch-bases","5000","--md","false"),output);
        var rows=sam("split-index");assertEquals(3,rows.size());assertEquals("chr2",rows.get(0).getReferenceName());assertEquals(1001,rows.get(0).getAlignmentStart());assertTrue(rows.get(2).getReadUnmappedFlag());
    }
}
