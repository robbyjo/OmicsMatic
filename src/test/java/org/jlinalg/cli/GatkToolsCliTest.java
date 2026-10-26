/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import htsjdk.samtools.*;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFileReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

class GatkToolsCliTest {
    @TempDir(cleanup=CleanupMode.ON_SUCCESS) Path directory;
    String output;
    private String runtime(){String value=System.getenv("JLINALG_GATK_JAR");assumeTrue(value!=null,"Set JLINALG_GATK_JAR for real pinned GATK tests");return value;}
    private int run(String method,String name,String... extra)throws Exception{
        var args=new ArrayList<>(List.of("gatk-tools","--method",method,"--out",directory.resolve(name).toString(),"--no-config"));
        if(!method.equals("reference")){args.addAll(List.of("--gatk",runtime(),"--memory-mb","512"));}args.addAll(List.of(extra));
        var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);int code=JLinAlgCli.run(args.toArray(String[]::new),stream,stream);output=bytes.toString();
        for(String file:List.of("WARNINGS.md","analysis.log","version.log")){Path p=directory.resolve(name).resolve(file);if(code!=0&&Files.exists(p))output+=Files.readString(p);}
        return code;
    }
    private String dna(){var random=new Random(77129);var text=new StringBuilder();for(int i=0;i<900;i++)text.append("ACGT".charAt(random.nextInt(4)));return text.toString();}
    private Path reference()throws Exception{
        Path path=directory.resolve("reference.fa");String sequence=dna();var text=new StringBuilder(">chr1\n");for(int i=0;i<sequence.length();i+=60)text.append(sequence,i,Math.min(sequence.length(),i+60)).append('\n');Files.writeString(path,text);
        assertEquals(0,run("reference","reference-bundle","--reference",path.toString()),output);
        Files.copy(directory.resolve("reference-bundle/reference.fa.fai"),Path.of(path+".fai"));Files.copy(directory.resolve("reference-bundle/reference.dict"),directory.resolve("reference.dict"));return path;
    }
    private Path bam(String name,String sample,boolean allAlternate)throws Exception{
        var header=new SAMFileHeader();header.setSortOrder(SAMFileHeader.SortOrder.coordinate);header.addSequence(new SAMSequenceRecord("chr1",900));var group=new SAMReadGroupRecord("rg1");group.setSample(sample);group.setLibrary("library");group.setPlatform("ILLUMINA");header.addReadGroup(group);
        Path path=directory.resolve(name+".bam");String reference=dna();char alternate=reference.charAt(349)=='A'?'C':'A';
        try(var writer=new SAMFileWriterFactory().setCreateIndex(true).makeBAMWriter(header,true,path.toFile())){
            for(int start=200;start<230;start++)for(int copy=0;copy<4;copy++){
                var record=new SAMRecord(header);record.setReadName(name+"_"+start+"_"+copy);record.setReferenceName("chr1");record.setAlignmentStart(start+1);record.setMappingQuality(60);record.setCigarString("200M");record.setAttribute("RG","rg1");var sequence=new StringBuilder(reference.substring(start,start+200));boolean alt=allAlternate||copy%2==0;if(alt)sequence.setCharAt(349-start,alternate);record.setReadString(sequence.toString());record.setBaseQualityString("I".repeat(200));record.setAttribute("NM",alt?1:0);writer.addAlignment(record);
            }
        }return path;
    }
    private List<VariantContext> variants(Path path){try(var reader=new VCFFileReader(path,false);var records=reader.iterator()){var result=new ArrayList<VariantContext>();while(records.hasNext())result.add(records.next());return result;}}
    @Test void nativeReferencePreservesExistingSidecarsAndNeedsNoGatk()throws Exception{
        Path fasta=directory.resolve("original.fa");Files.writeString(fasta,">chr1\r\nACGT\r\nTGCA\r\n");Files.writeString(directory.resolve("original.fa.fai"),"old index");Files.writeString(directory.resolve("original.dict"),"old dictionary");
        assertEquals(0,run("reference","native","--reference",fasta.toString()),output);assertEquals("old index",Files.readString(directory.resolve("original.fa.fai")));assertEquals("old dictionary",Files.readString(directory.resolve("original.dict")));assertTrue(Files.readString(directory.resolve("native/original.dict")).contains("SN:chr1\tLN:8"));
    }
    @Test void unavailableOrWrongRuntimeSkipsAndInvalidModelDoesNotStart()throws Exception{
        var bytes=new ByteArrayOutputStream();var stream=new PrintStream(bytes);Path missing=directory.resolve("absent.jar");
        int code=JLinAlgCli.run(new String[]{"gatk-tools","--method","haplotype-call","--gatk",missing.toString(),"--ploidy","2","--out",directory.resolve("skip").toString(),"--no-config"},stream,stream);assertEquals(3,code,bytes.toString());assertTrue(Files.readString(directory.resolve("skip/WARNINGS.md")).contains("install_gatk.py --allow-download"));assertFalse(Files.exists(directory.resolve("skip/results.tsv")));
        code=JLinAlgCli.run(new String[]{"gatk-tools","--method","haplotype-call","--gatk",missing.toString(),"--ploidy","0","--out",directory.resolve("bad-model").toString()},stream,stream);assertEquals(2,code);assertFalse(Files.exists(directory.resolve("bad-model")));
    }
    @Test void realHaploidAndTetraploidCallsRecoverKnownAlleleDosage()throws Exception{
        runtime();Path fasta=reference();
        for(int ploidy:List.of(1,4)){
            Path bam=bam("p"+ploidy,"S1",ploidy==1);assertEquals(0,run("haplotype-call","p"+ploidy,"--input",bam.toString(),"--reference",fasta.toString(),"--ploidy",Integer.toString(ploidy),"--region","chr1:150-500"),output);
            List<VariantContext> rows=variants(directory.resolve("p"+ploidy+"/variants.vcf"));assertEquals(1,rows.size());var variant=rows.get(0);assertEquals(350,variant.getStart());assertEquals(String.valueOf(dna().charAt(349)),variant.getReference().getBaseString());var gt=variant.getGenotype("S1");assertEquals(ploidy,gt.getPloidy());assertEquals(ploidy==1?1:2,gt.getAlleles().stream().filter(a->!a.isReference()).count());
        }
    }
    @Test void realGvcfCombinationAndGenotypingPreserveSamplesAndKnownDiploidGenotypes()throws Exception{
        runtime();Path fasta=reference();
        for(String sample:List.of("S1","S2")){
            Path bam=bam(sample,sample,sample.equals("S2"));assertEquals(0,run("haplotype-call",sample,"--input",bam.toString(),"--reference",fasta.toString(),"--ploidy","2","--emit","gvcf"),output);
            assertTrue(Files.readString(directory.resolve(sample+"/WARNINGS.md")).contains("INTERMEDIATE GVCF"));
        }
        Path list=directory.resolve("gvcfs.txt");Files.writeString(list,"S2/variants.g.vcf\nS1/variants.g.vcf\n");
        assertEquals(0,run("combine-gvcfs","combined","--inputs-list",list.toString(),"--reference",fasta.toString()),output);
        assertEquals(0,run("genotype-gvcfs","genotyped","--input",directory.resolve("combined/combined.g.vcf").toString(),"--reference",fasta.toString(),"--ploidy","2"),output);
        List<VariantContext> rows=variants(directory.resolve("genotyped/variants.vcf"));assertEquals(1,rows.size());var variant=rows.get(0);assertEquals(350,variant.getStart());assertTrue(variant.getGenotype("S1").isHet());assertTrue(variant.getGenotype("S2").isHomVar());assertEquals(Set.of("S1","S2"),variant.getSampleNames());
        assertEquals(2,run("genotype-gvcfs","ordinary","--input",directory.resolve("genotyped/variants.vcf").toString(),"--reference",fasta.toString(),"--ploidy","2"),output);assertFalse(Files.exists(directory.resolve("ordinary/results.tsv")));
        Files.copy(directory.resolve("S1/variants.g.vcf"),directory.resolve("same-sample.g.vcf"));Files.writeString(list,"S1/variants.g.vcf\nsame-sample.g.vcf\n");
        assertEquals(2,run("combine-gvcfs","duplicate-sample","--inputs-list",list.toString(),"--reference",fasta.toString()),output);
        assertTrue(output.contains("Duplicate sample across gVCF inputs"));assertFalse(Files.exists(directory.resolve("duplicate-sample/results.tsv")));
    }
    private Path filterFixture()throws Exception{
        Path path=directory.resolve("annotated.vcf");Files.writeString(path,"##fileformat=VCFv4.2\n##contig=<ID=chr1,length=900>\n##FILTER=<ID=OLD,Description=Existing_filter>\n##FORMAT=<ID=GT,Number=1,Type=String,Description=Genotype>\n"
            +"##INFO=<ID=QD,Number=1,Type=Float,Description=Quality_by_depth>\n##INFO=<ID=FS,Number=1,Type=Float,Description=Strand_bias>\n##INFO=<ID=MQ,Number=1,Type=Float,Description=Mapping_quality>\n##INFO=<ID=MQRankSum,Number=1,Type=Float,Description=MQ_rank_sum>\n##INFO=<ID=ReadPosRankSum,Number=1,Type=Float,Description=Read_position_rank_sum>\n"
            +"#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tS1\n"
            +"chr1\t10\tpass\tA\tC\t60\tPASS\tQD=3;FS=0;MQ=60;MQRankSum=0;ReadPosRankSum=0\tGT\t0/1\n"
            +"chr1\t20\tfail_snp\tA\tG\t60\tOLD\tQD=1;FS=80;MQ=20;MQRankSum=-13;ReadPosRankSum=-9\tGT\t1/1\n"
            +"chr1\t30\tfail_indel\tAC\tA\t60\tPASS\tQD=3;FS=201;ReadPosRankSum=-5\tGT\t0/1\n"
            +"chr1\t40\tmissing\tG\tA\t60\tPASS\tFS=0;MQ=60;MQRankSum=0;ReadPosRankSum=0\tGT\t0/1\n"
            +"chr1\t50\tboundary\tA\tT\t60\tPASS\tQD=2;FS=60;MQ=40;MQRankSum=-12.5;ReadPosRankSum=-8\tGT\t0/0\n");return path;
    }
    @Test void realTypedHardFiltersPreserveAllRecordsGenotypesAndOldFiltersWithExplicitMissingPolicy()throws Exception{
        runtime();Path input=filterFixture();
        for(String policy:List.of("pass","fail")){
            assertEquals(0,run("hard-filter",policy,"--input",input.toString(),"--missing-policy",policy),output);var rows=variants(directory.resolve(policy+"/variants.vcf"));assertEquals(5,rows.size());assertFalse(rows.get(0).isFiltered());assertEquals(Set.of("OLD","JL_SNP_HARD_FILTER"),rows.get(1).getFilters());assertEquals(Set.of("JL_INDEL_HARD_FILTER"),rows.get(2).getFilters());assertEquals(policy.equals("fail"),rows.get(3).isFiltered());assertFalse(rows.get(4).isFiltered());assertTrue(Files.readString(directory.resolve(policy+"/WARNINGS.md")).contains("lack one or more requested annotations"));
        }
    }
    @Test void referenceIndexMismatchFailsWithRetainedRecovery()throws Exception{
        runtime();Path fasta=reference(),bam=bam("source","S1",false);Files.writeString(Path.of(fasta+".fai"),"chr1\t900\t6\t60\t60\n");
        assertEquals(2,run("haplotype-call","stale","--input",bam.toString(),"--reference",fasta.toString(),"--ploidy","2"),output);assertTrue(output.contains("FASTA index does not match"));assertFalse(Files.exists(directory.resolve("stale/results.tsv")));
    }
}
