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
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReadAlignmentCliTest {
    @TempDir Path dir;
    String output,chr1,chr2;
    private String bowtie(){String x=System.getenv("JLINALG_BOWTIE2_HOME");assumeTrue(x!=null,"Set JLINALG_BOWTIE2_HOME for real upstream checks");return x;}
    private String bwa(){String x=System.getenv("JLINALG_BWA");assumeTrue(x!=null,"Set JLINALG_BWA for real upstream checks");return x;}
    private int run(String name,String... arguments)throws Exception{
        var args=new ArrayList<>(List.of("read-align"));args.addAll(List.of(arguments));args.addAll(List.of("--out",dir.resolve(name).toString(),"--no-config"));
        var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);int result=JLinAlgCli.run(args.toArray(String[]::new),stream,stream);output=bytes.toString();
        if(result!=0)for(String file:List.of("WARNINGS.md","alignment.log","index.log","aln1.log","version.log")){Path p=dir.resolve(name).resolve(file);if(Files.exists(p))output+=Files.readString(p);}
        return result;
    }
    private Path reference()throws Exception{
        var random=new Random(722);var a=new StringBuilder();var b=new StringBuilder();
        for(int i=0;i<6000;i++){a.append("ACGT".charAt(random.nextInt(4)));b.append("ACGT".charAt(random.nextInt(4)));}chr1=a.toString();chr2=b.toString();
        Path p=dir.resolve("reference with spaces.fa");Files.writeString(p,">chr1 description\n"+chr1+"\n>chr2\n"+chr2+"\n");return p;
    }
    private Path fastq(String name,List<String> ids,List<String> sequences,boolean gz)throws Exception{
        Path p=dir.resolve(name+(gz?".fastq.gz":".fastq"));var data=new StringBuilder();
        for(int i=0;i<ids.size();i++)data.append('@').append(ids.get(i)).append(" comment\n").append(sequences.get(i)).append("\n+\n").append(quality(sequences.get(i).length())).append('\n');
        if(gz)try(var out=new GZIPOutputStream(Files.newOutputStream(p))){out.write(data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}else Files.writeString(p,data);
        return p;
    }
    private String quality(int n){var s=new StringBuilder();for(int i=0;i<n;i++)s.append((char)(63+i%15));return s.toString();}
    private String reverse(String sequence){var b=new StringBuilder();for(int i=sequence.length()-1;i>=0;i--)b.append(switch(sequence.charAt(i)){case'A'->'T';case'T'->'A';case'C'->'G';case'G'->'C';default->'N';});return b.toString();}
    private Path single()throws Exception{
        var mismatch=new StringBuilder(chr1.substring(1500,1600));mismatch.setCharAt(50,mismatch.charAt(50)=='A'?'C':'A');
        return fastq("single",List.of("plus/1","minus/2","mismatch","unmapped"),List.of(chr1.substring(100,200),reverse(chr2.substring(900,1000)),mismatch.toString(),"N".repeat(100)),true);
    }
    private List<SAMRecord> records(String name)throws Exception{
        var result=new ArrayList<SAMRecord>();try(var reader=SamReaderFactory.makeDefault().open(dir.resolve(name+"/alignment.sam"));var iterator=reader.iterator()){while(iterator.hasNext())result.add(iterator.next());}return result;
    }
    private List<String> alignArgs(String method,String runtime,Path reads,String index){return new ArrayList<>(List.of("--method",method,method.equals("bowtie2")?"--bowtie-home":"--bwa",runtime,"--index",dir.resolve(index).toString(),"--reads",reads.toString(),"--sample","S1"));}
    private void assertSingle(String name)throws Exception{
        var primary=records(name).stream().filter(r->!r.isSecondaryOrSupplementary()).toList();assertEquals(4,primary.size());
        assertEquals(List.of("plus","minus","mismatch","unmapped"),primary.stream().map(r->r.getReadName().replaceFirst("/[12]$","")).toList());
        assertEquals(101,primary.get(0).getAlignmentStart());assertEquals("chr1",primary.get(0).getReferenceName());
        assertEquals(901,primary.get(1).getAlignmentStart());assertEquals("chr2",primary.get(1).getReferenceName());assertTrue(primary.get(1).getReadNegativeStrandFlag());
        assertEquals(1501,primary.get(2).getAlignmentStart());assertEquals(1,primary.get(2).getIntegerAttribute("NM"));assertTrue(primary.get(3).getReadUnmappedFlag());
        assertEquals("S1",primary.get(0).getReadGroup().getSample());assertEquals(new StringBuilder(quality(100)).reverse().toString(),primary.get(1).getBaseQualityString());
    }
    @Test void missingRuntimeAndInvalidTimeoutHaveDistinctActionableOutcomes()throws Exception{
        Path ref=reference();assertEquals(3,run("missing","--method","bowtie2-index","--reference",ref.toString(),"--bowtie-home",dir.resolve("absent").toString()),output);
        assertTrue(output.contains("SKIPPED ANALYSIS"));assertTrue(output.contains("Required action"));assertFalse(Files.exists(dir.resolve("missing/results.tsv")));
        assertEquals(2,run("timeout","--method","bwa-index","--reference",ref.toString(),"--bwa","missing","--timeout","0"));assertFalse(Files.exists(dir.resolve("timeout")));
    }
    @Test void realBowtieSmallAndLargeIndexesMapGzipReadsAtKnownPositions()throws Exception{
        String home=bowtie();Path ref=reference(),reads=single();
        for(String mode:List.of("small","large")){
            String index="index-"+mode;assertEquals(0,run(index,"--method","bowtie2-index","--bowtie-home",home,"--reference",ref.toString(),"--index-mode",mode),output);
            var args=alignArgs("bowtie2",home,reads,index);args.addAll(List.of("--threads","2","--preset","very-sensitive"));
            assertEquals(0,run(mode,args.toArray(String[]::new)),output);assertSingle(mode);
            assertTrue(Files.readString(dir.resolve(index+"/aligner-index.json")).contains("reference_sha256"));
        }
    }
    @Test void realBowtieLocalSoftClipsAndPairedDovetailSettingsPreserveMates()throws Exception{
        String home=bowtie();Path ref=reference();assertEquals(0,run("index","--method","bowtie2-index","--bowtie-home",home,"--reference",ref.toString()),output);
        Path clipped=fastq("clipped",List.of("clip"),List.of("TTTTTTTT"+chr1.substring(2000,2080)+"GGGGGGGGGGGG"),false);
        var local=alignArgs("bowtie2",home,clipped,"index");local.addAll(List.of("--mode","local","--preset","very-sensitive"));assertEquals(0,run("local",local.toArray(String[]::new)),output);
        assertFalse(records("local").get(0).getReadUnmappedFlag());assertTrue(records("local").get(0).getCigarString().contains("S"));
        var first=new ArrayList<String>();var second=new ArrayList<String>();var ids1=new ArrayList<String>();var ids2=new ArrayList<String>();
        for(int i=0;i<16;i++){int start=500+i*25;ids1.add("pair"+i+"/1");ids2.add("pair"+i+"/2");first.add(chr1.substring(start,start+100));second.add(reverse(chr1.substring(start+300,start+400)));}
        Path r1=fastq("pairs1",ids1,first,true),r2=fastq("pairs2",ids2,second,true);var paired=alignArgs("bowtie2",home,r1,"index");
        paired.addAll(List.of("--reads2",r2.toString(),"--threads","2","--preset","very-sensitive","--min-insert","0","--max-insert","700","--dovetail","true"));
        assertEquals(0,run("paired",paired.toArray(String[]::new)),output);var outputRecords=records("paired");assertEquals(32,outputRecords.size());assertTrue(outputRecords.stream().allMatch(SAMRecord::getProperPairFlag));
        assertTrue(Files.readString(dir.resolve("paired/session.yaml")).contains("--dovetail"));
    }
    @Test void indexCorruptionAndMismatchedPairsFailBeforePublishingAlignment()throws Exception{
        String home=bowtie();Path ref=reference(),reads=single();assertEquals(0,run("index","--method","bowtie2-index","--bowtie-home",home,"--reference",ref.toString()),output);
        Path mate=fastq("wrong",List.of("other"),List.of(chr1.substring(300,400)),false);var paired=alignArgs("bowtie2",home,reads,"index");paired.addAll(List.of("--reads2",mate.toString()));
        assertEquals(2,run("bad-pair",paired.toArray(String[]::new)),output);assertFalse(Files.exists(dir.resolve("bad-pair/alignment.log")));
        Files.writeString(dir.resolve("index/reference.1.bt2"),"corrupt",java.nio.file.StandardOpenOption.APPEND);
        assertEquals(2,run("bad-index",alignArgs("bowtie2",home,reads,"index").toArray(String[]::new)),output);assertTrue(output.contains("corrupt"));assertFalse(Files.exists(dir.resolve("bad-index/results.tsv")));
    }
    @Test void realBwaMemAndBacktrackMapKnownSingleReadsFromUnmodifiedBuild()throws Exception{
        String executable=bwa();Path ref=reference(),reads=single();assertEquals(0,run("index","--method","bwa-index","--bwa",executable,"--reference",ref.toString()),output);
        for(String method:List.of("bwa-mem","bwa-backtrack")){
            var args=alignArgs(method,executable,reads,"index");args.addAll(List.of("--threads","2"));assertEquals(0,run(method,args.toArray(String[]::new)),output);assertSingle(method);
        }
    }
    @Test void realBwaPairedMemAndSampeRetainReadPairsAndKnownOrigins()throws Exception{
        String executable=bwa();Path ref=reference();assertEquals(0,run("index","--method","bwa-index","--bwa",executable,"--reference",ref.toString()),output);
        var first=new ArrayList<String>();var second=new ArrayList<String>();var ids1=new ArrayList<String>();var ids2=new ArrayList<String>();
        for(int i=0;i<24;i++){int start=500+i*25;ids1.add("pair"+i+"/1");ids2.add("pair"+i+"/2");first.add(chr1.substring(start,start+100));second.add(reverse(chr1.substring(start+300,start+400)));}
        Path r1=fastq("pairs1",ids1,first,true),r2=fastq("pairs2",ids2,second,true);
        for(String method:List.of("bwa-mem","bwa-backtrack")){
            var args=alignArgs(method,executable,r1,"index");args.addAll(List.of("--reads2",r2.toString(),"--threads","2"));assertEquals(0,run(method,args.toArray(String[]::new)),output);
            var actual=records(method);assertEquals(48,actual.size());assertTrue(actual.stream().allMatch(SAMRecord::getProperPairFlag));
            assertEquals(501,actual.get(0).getAlignmentStart());assertEquals(801,actual.get(1).getAlignmentStart());assertTrue(actual.get(1).getReadNegativeStrandFlag());
        }
    }
    @Test void realBwaSplitReadAndPicardCompatibilityHaveExplicitSecondaryStatus()throws Exception{
        String executable=bwa();Path ref=reference();assertEquals(0,run("index","--method","bwa-index","--bwa",executable,"--reference",ref.toString()),output);
        Path reads=fastq("split",List.of("chimera"),List.of(chr1.substring(2500,2600)+chr2.substring(3500,3600)),true);
        for(boolean compatibility:List.of(false,true)){
            String name=compatibility?"secondary":"supplementary";var args=alignArgs("bwa-mem",executable,reads,"index");args.addAll(List.of("--mark-shorter-split-secondary",Boolean.toString(compatibility)));
            assertEquals(0,run(name,args.toArray(String[]::new)),output);var actual=records(name);assertEquals(2,actual.size());assertEquals(1,actual.stream().filter(r->!r.isSecondaryOrSupplementary()).count());
            assertTrue(actual.stream().anyMatch(r->compatibility?r.isSecondaryAlignment():r.getSupplementaryAlignmentFlag()));
        }
    }
}
