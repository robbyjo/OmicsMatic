/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import htsjdk.samtools.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.jlinalg.sequence.FastqIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

class StarToolsCliTest {
    @TempDir(cleanup=CleanupMode.ON_SUCCESS) Path directory;
    private String output;
    private String runtime(){String value=System.getenv("JLINALG_STAR");assumeTrue(value!=null,"Set JLINALG_STAR for real pinned STAR analysis tests");return value;}
    private int run(String method,String name,String... extra)throws Exception{
        var args=new ArrayList<>(List.of("star-tools","--method",method,"--out",directory.resolve(name).toString(),"--no-config"));
        if(!method.equals("inspect-gtf"))args.addAll(List.of("--star",runtime()));args.addAll(List.of(extra));
        var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);int code=JLinAlgCli.run(args.toArray(String[]::new),stream,stream);output=bytes.toString();
        for(String file:List.of("WARNINGS.md","alignment.log","index.log","version.log")){Path p=directory.resolve(name).resolve(file);if(code!=0&&Files.exists(p))output+=Files.readString(p);}
        return code;
    }
    private String dna(){var r=new Random(68214);var b=new StringBuilder();for(int i=0;i<3000;i++)b.append("ACGT".charAt(r.nextInt(4)));b.replace(400,402,"GT");b.replace(598,600,"AG");return b.toString();}
    private Path fasta()throws Exception{Path p=directory.resolve("reference with spaces.fa");Files.writeString(p,">chr1\n"+dna()+"\n");return p;}
    private Path gtf()throws Exception{Path p=directory.resolve("genes.gtf");Files.writeString(p,"chr1\tfixture\texon\t201\t400\t.\t+\t.\tgene_id \"G1\"; transcript_id \"T1\";\nchr1\tfixture\texon\t601\t900\t.\t+\t.\tgene_id \"G1\"; transcript_id \"T1\";\n");return p;}
    private Path index(boolean annotated)throws Exception{
        Path f=fasta();var args=new ArrayList<>(List.of("--reference",f.toString(),"--memory-mb","512"));if(annotated)args.addAll(List.of("--gtf",gtf().toString(),"--sjdb-overhang","99"));assertEquals(0,run("index","index bundle",args.toArray(String[]::new)),output);return directory.resolve("index bundle");
    }
    private Path reads(String name,String... sequences)throws Exception{
        Path p=directory.resolve(name);try(var out=new FastqIO.Writer(p)){for(int i=0;i<sequences.length;i++)out.write(new FastqIO.Read("@r"+i+(name.contains("R2")?"/2":"/1")+" fixture",sequences[i],"I".repeat(sequences[i].length())));}return p;
    }
    private List<SAMRecord> sam(String name)throws Exception{try(var reader=SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT).open(directory.resolve(name+"/rna.Aligned.out.sam"));var it=reader.iterator()){var rows=new ArrayList<SAMRecord>();while(it.hasNext())rows.add(it.next());return rows;}}
    private String reverse(String s){var b=new StringBuilder();for(int i=s.length()-1;i>=0;i--)b.append(switch(s.charAt(i)){case'A'->'T';case'C'->'G';case'G'->'C';default->'A';});return b.toString();}
    @Test void nativeGtfInspectionNeedsNoRuntimeAndPreservesReferenceSidecars()throws Exception{
        Path f=fasta(),g=gtf();Files.writeString(Path.of(f+".fai"),"existing sidecar");
        assertEquals(0,run("inspect-gtf","inspect","--reference",f.toString(),"--gtf",g.toString()),output);assertTrue(Files.readString(directory.resolve("inspect/results.tsv")).contains("genes\t1"));assertEquals("existing sidecar",Files.readString(Path.of(f+".fai")));
        Files.writeString(g,"1\tfixture\texon\t1\t10\t.\t+\t.\tgene_id \"g\"; transcript_id \"t\";\n");assertEquals(2,run("inspect-gtf","wrong-contig","--reference",f.toString(),"--gtf",g.toString()),output);assertFalse(Files.exists(directory.resolve("wrong-contig/results.tsv")));assertTrue(Files.readString(directory.resolve("wrong-contig/WARNINGS.md")).contains("exact reference"));
    }
    @Test void unavailableRuntimeSkipsAndInvalidOptionsFailBeforeStarting()throws Exception{
        var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);String[] args={"star-tools","--method","index","--star",directory.resolve("absent.exe").toString(),"--reference","none.fa","--memory-mb","512","--out",directory.resolve("skip").toString(),"--no-config"};
        assertEquals(3,JLinAlgCli.run(args,stream,stream),bytes.toString());assertTrue(Files.readString(directory.resolve("skip/WARNINGS.md")).contains("install_star.py --allow-download --allow-source-build"));assertFalse(Files.exists(directory.resolve("skip/results.tsv")));
        assertEquals(2,JLinAlgCli.run(new String[]{"star-tools","--method","align","--star","missing","--index","none","--reads","none","--sample","s","--match-fraction","1.1","--out",directory.resolve("bad-option").toString()},stream,stream));assertFalse(Files.exists(directory.resolve("bad-option")));
        assertEquals(2,JLinAlgCli.run(new String[]{"star-tools","--method","align","--star","missing","--index","none","--reads","none","--sample","s","--two-pass","basic","--sj-junctions","none","--out",directory.resolve("ambiguous-pass").toString()},stream,stream));
    }
    @Test void realSplicedGzipReadsAndGeneCountsRecoverKnownTruth()throws Exception{
        Path i=index(true);String dna=dna();Path r=reads("reads.fq.gz",(dna.substring(350,400)+dna.substring(600,650)).toLowerCase(Locale.ROOT),dna.substring(650,750),"N".repeat(100));
        assertEquals(0,run("align","single","--index",i.toString(),"--reads",r.toString(),"--sample","S1","--gene-counts","true","--unmapped-fastq","true"),output);
        var rows=sam("single");assertEquals(3,rows.size());assertEquals("50M200N50M",rows.get(0).getCigarString());assertEquals(351,rows.get(0).getAlignmentStart());assertEquals(255,rows.get(0).getMappingQuality());assertTrue(rows.get(2).getReadUnmappedFlag());
        assertTrue(Files.readString(directory.resolve("single/rna.ReadsPerGene.out.tab")).contains("G1\t2\t2\t0"));assertEquals(1,FastqIO.inspect(directory.resolve("single/rna.Unmapped.out.mate1"),null).read1().reads());assertTrue(output.contains("STAR WARNINGS"));assertTrue(Files.readString(directory.resolve("single/WARNINGS.md")).contains("read-1 strand"));
        assertFalse(Files.exists(directory.resolve("single/reads1.fastq")));assertTrue(Files.readString(directory.resolve("single/session.yaml")).contains("NoSharedMemory"));
        Files.writeString(i.resolve("index/chrLength.txt"),"2999\n");assertEquals(2,run("align","corrupt","--index",i.toString(),"--reads",r.toString(),"--sample","S1"),output);assertTrue(output.contains("corrupt"));assertFalse(Files.exists(directory.resolve("corrupt/results.tsv")));
    }
    @Test void realPairedSplicedReverseAndUnmappedMatesRetainIdentitiesAndFragmentCounts()throws Exception{
        Path i=index(true);String dna=dna();Path a=reads("R1.fq",dna.substring(350,400)+dna.substring(600,650),dna.substring(650,750),"N".repeat(100));Path b=reads("R2.fq",reverse(dna.substring(700,800)),"N".repeat(100),"N".repeat(100));
        assertEquals(0,run("align","paired","--index",i.toString(),"--reads",a.toString(),"--reads2",b.toString(),"--sample","PAIR","--library","LIB","--gene-counts","true"),output);
        var rows=sam("paired");assertEquals(6,rows.size());assertEquals(3,rows.stream().filter(SAMRecord::getFirstOfPairFlag).count());assertEquals(3,rows.stream().filter(SAMRecord::getSecondOfPairFlag).count());assertEquals(1,rows.stream().filter(r->r.getCigarString().contains("N")).count());assertTrue(rows.get(1).getReadNegativeStrandFlag());assertEquals(4,rows.stream().filter(SAMRecord::getReadUnmappedFlag).count());
        assertTrue(Files.readString(directory.resolve("paired/rna.ReadsPerGene.out.tab")).contains("G1\t1\t1\t0"));
        assertEquals(0,run("align","orphan-accepted","--index",i.toString(),"--reads",a.toString(),"--reads2",b.toString(),"--sample","PAIR","--gene-counts","true","--score-fraction","0.4","--match-fraction","0.4","--filter-type","by-sj-out"),output);
        assertEquals(3,sam("orphan-accepted").stream().filter(SAMRecord::getReadUnmappedFlag).count());assertTrue(Files.readString(directory.resolve("orphan-accepted/rna.ReadsPerGene.out.tab")).contains("G1\t2\t2\t0"));
    }
    @Test void realBasicAndExplicitSecondPassUseDistinctAuditedOptions()throws Exception{
        Path i=index(true);String dna=dna();Path r=reads("reads.fq",dna.substring(350,400)+dna.substring(600,650));
        assertEquals(0,run("align","basic","--index",i.toString(),"--reads",r.toString(),"--sample","S","--two-pass","basic","--gene-counts","true","--multimap-max","1","--ends","end-to-end","--filter-type","by-sj-out","--intron-motifs","canonical-only","--match-min","100","--score-min","100","--score-fraction","0","--match-fraction","0"),output);
        assertEquals("50M200N50M",sam("basic").get(0).getCigarString());Path sj=directory.resolve("basic/rna.SJ.out.tab");assertTrue(Files.readString(sj).startsWith("chr1\t401\t600\t1"));
        assertEquals(0,run("align","explicit","--index",i.toString(),"--reads",r.toString(),"--sample","S","--sj-junctions",sj.toString(),"--gene-counts","true"),output);assertEquals("50M200N50M",sam("explicit").get(0).getCigarString());assertTrue(Files.readString(directory.resolve("explicit/WARNINGS.md")).contains("EXPLICIT SECOND PASS"));
        Files.writeString(sj,"chr1\t0\t600\t1\n");assertEquals(2,run("align","bad-junction","--index",i.toString(),"--reads",r.toString(),"--sample","S","--sj-junctions",sj.toString()),output);assertFalse(Files.exists(directory.resolve("bad-junction/results.tsv")));
    }
    @Test void realMultimappingAndUniqueOnlyFilteringPreserveRawObservations()throws Exception{
        Path f=fasta();String dna=dna();Files.writeString(f,">chr1\n"+dna+"\n>copy\n"+dna.substring(200,900)+"\n");
        assertEquals(0,run("index","duplicates","--reference",f.toString(),"--memory-mb","512"),output);Path i=directory.resolve("duplicates"),r=reads("reads.fq",dna.substring(650,750));
        assertEquals(0,run("align","multi","--index",i.toString(),"--reads",r.toString(),"--sample","S"),output);var rows=sam("multi");assertEquals(2,rows.size());assertEquals(1,rows.stream().filter(SAMRecord::isSecondaryAlignment).count());assertEquals(2,rows.get(0).getIntegerAttribute("NH"));
        assertEquals(0,run("align","unique-only","--index",i.toString(),"--reads",r.toString(),"--sample","S","--multimap-max","1"),output);assertTrue(sam("unique-only").get(0).getReadUnmappedFlag());assertTrue(Files.readString(directory.resolve("unique-only/WARNINGS.md")).contains("ZERO MAPPED PRIMARY"));
        assertEquals(2,run("align","missing-annotation","--index",i.toString(),"--reads",r.toString(),"--sample","S","--gene-counts","true"),output);assertTrue(output.contains("requires an index"));
    }
    @Test void realUnannotatedTwoPassRequiresExplicitOverhangAndRecoversNovelJunction()throws Exception{
        Path i=index(false);String dna=dna();Path r=reads("reads.fq",dna.substring(350,400)+dna.substring(600,650));
        assertEquals(2,run("align","missing-overhang","--index",i.toString(),"--reads",r.toString(),"--sample","S","--two-pass","basic"),output);assertTrue(output.contains("explicit --sjdb-overhang"));assertFalse(Files.exists(directory.resolve("missing-overhang/alignment.log")));
        assertEquals(0,run("align","novel-basic","--index",i.toString(),"--reads",r.toString(),"--sample","S","--two-pass","basic","--sjdb-overhang","99"),output);assertEquals("50M200N50M",sam("novel-basic").get(0).getCigarString());
    }
    @Test void realAdapterRejectsMismatchedPairsAndAmbiguousBasesBeforeAlignment()throws Exception{
        Path i=index(false),r=reads("R1.fq","ACGTR".repeat(20)),r2=reads("R2.fq","ACGT".repeat(25));
        assertEquals(2,run("align","ambiguous","--index",i.toString(),"--reads",r.toString(),"--sample","S"),output);assertTrue(output.contains("canonical"));assertFalse(Files.exists(directory.resolve("ambiguous/alignment.log")));
        Files.writeString(r2,"@different/2\n"+"ACGT".repeat(25)+"\n+\n"+"I".repeat(100)+"\n");assertEquals(2,run("align","mismatched","--index",i.toString(),"--reads",r.toString(),"--reads2",r2.toString(),"--sample","S"),output);assertTrue(output.contains("pair count/identity/order mismatch"));assertFalse(Files.exists(directory.resolve("mismatched/alignment.log")));
    }
}
