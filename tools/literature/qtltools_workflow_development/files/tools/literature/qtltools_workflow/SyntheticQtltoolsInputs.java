/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.tribble.index.IndexFactory;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFFormatHeaderLine;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLine;
import htsjdk.variant.vcf.VCFHeaderLineCount;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import htsjdk.variant.vcf.VCFStandardHeaderLines;
import htsjdk.samtools.util.BlockCompressedOutputStream;

/** Reproducible artificial coordinates,genotypes,phenotypes and reads;no person data. */
public final class SyntheticQtltoolsInputs {
    private SyntheticQtltoolsInputs() { }
    public static void main(String[] args)throws Exception {
        Path out=Path.of(args[0]);Files.createDirectory(out);int n=64,m=40;Random random=new Random(20261121);int[][] genotype=new int[m][n];double[][] dosage=new double[m][n];var samples=new ArrayList<String>();for(int i=0;i<n;i++)samples.add(String.format(java.util.Locale.ROOT,"S%03d",i));
        for(int v=0;v<m;v++)for(int i=0;i<n;i++){int g=v==0?new int[]{0,1,2,1}[i%4]:v==1?new int[]{1,2,1,0}[i%4]:random.nextInt(3);genotype[v][i]=g;dosage[v][i]=Math.max(0,Math.min(2,g+.04*StrictMath.sin(i+v)));}
        var dictionary=new SAMSequenceDictionary(List.of(new SAMSequenceRecord("1",100000),new SAMSequenceRecord("2",100000),new SAMSequenceRecord("3",100000)));
        var lines=new LinkedHashSet<VCFHeaderLine>();VCFStandardHeaderLines.addStandardFormatLines(lines,true,"GT","GQ");lines.add(new VCFFormatHeaderLine("DS",1,VCFHeaderLineType.Float,"Artificial dosage"));lines.add(new VCFFormatHeaderLine("GP",VCFHeaderLineCount.G,VCFHeaderLineType.Float,"Artificial genotype probabilities"));lines.add(new VCFInfoHeaderLine("INFO",1,VCFHeaderLineType.Float,"Artificial perfect imputation score"));lines.add(new VCFInfoHeaderLine("R2",1,VCFHeaderLineType.Float,"Artificial perfect imputation score"));var header=new VCFHeader(lines,samples);header.setSequenceDictionary(dictionary);
        Allele reference=Allele.create("A",true),alternative=Allele.create("G",false);
        try(var writer=new VariantContextWriterBuilder().setOutputPath(out.resolve("genotypes.vcf.gz")).setReferenceDictionary(dictionary).setOption(Options.INDEX_ON_THE_FLY).build()) {
            writer.writeHeader(header);
            for(int v=0;v<m;v++) {
                var values=new ArrayList<htsjdk.variant.variantcontext.Genotype>();
                for(int i=0;i<n;i++){int g=genotype[v][i];var alleles=g==0?List.of(reference,reference):g==1?List.of(reference,alternative):List.of(alternative,alternative);double[] probabilities={0,0,0};probabilities[g]=1;var builder=new GenotypeBuilder(samples.get(i),alleles).GQ(99).attribute("DS",dosage[v][i]).attribute("GP",probabilities);if(v==5&&i==0)builder.attribute("DS",".");values.add(builder.make());}
                String chr=v<20?"1":"2";int position=1000+(v%20)*100;writer.add(new VariantContextBuilder("synthetic",chr,position,position,List.of(reference,alternative)).id("v"+v).genotypes(values).attribute("INFO",1.0).attribute("R2",1.0).make());
            }
        }
        var phenotypes=new StringBuilder("#chr\tstart\tend\tpid\tgid\tstrand\t"+String.join("\t",samples)+"\n");
        for(int p=0;p<6;p++){String chr=p<3?"1":p<5?"2":"3";int start=1000+(p%3)*300;phenotypes.append(chr).append('\t').append(start).append('\t').append(start+1).append("\tp").append(p).append("\tg").append(p<2?0:p).append("\t+ ".stripTrailing());for(int i=0;i<n;i++){double value=1.8*dosage[p<3?0:20][i]+.8*dosage[p<3?3:23][i]+.15*StrictMath.sin(i*.7)+.3*random.nextGaussian();phenotypes.append('\t').append(Double.toString(value));}phenotypes.append('\n');}
        try(var stream=new BlockCompressedOutputStream(out.resolve("phenotypes.bed.gz").toFile())){stream.write(phenotypes.toString().getBytes(StandardCharsets.UTF_8));}
        IndexFactory.createTabixIndex(out.resolve("phenotypes.bed.gz"),new htsjdk.tribble.bed.BEDCodec(),TabixFormat.BED,dictionary).writeBasedOnFeaturePath(out.resolve("phenotypes.bed.gz"));
        var covariates=new StringBuilder("id\t"+String.join("\t",samples)+"\n");for(int k=0;k<2;k++){covariates.append("cov").append(k);for(int i=0;i<n;i++)covariates.append('\t').append(Double.toString(StrictMath.sin((i+1)*(k+1)*.31)));covariates.append('\n');}Files.writeString(out.resolve("covariates.txt"),covariates);
        var thresholds=new StringBuilder();for(int p=0;p<6;p++)thresholds.append("p").append(p).append(" 0.01\n");thresholds.append("g0 0.01\ng2 0.01\ng3 0.01\ng4 0.01\ng5 0.01\n");Files.writeString(out.resolve("thresholds.txt"),thresholds);
        Files.writeString(out.resolve("annotation.gtf"),"1\tsynthetic\texon\t500\t2500\t.\t+\t.\tgene_id \"gene1\"; transcript_id \"tx1\"; gene_type \"protein_coding\"; gene_name \"G1\";\n1\tsynthetic\texon\t2600\t5000\t.\t+\t.\tgene_id \"gene1\"; transcript_id \"tx1\"; gene_type \"protein_coding\"; gene_name \"G1\";\n2\tsynthetic\texon\t500\t5000\t.\t-\t.\tgene_id \"gene2\"; transcript_id \"tx2\"; gene_type \"protein_coding\"; gene_name \"G2\";\n");
        var samHeader=new SAMFileHeader(dictionary);samHeader.setSortOrder(SAMFileHeader.SortOrder.coordinate);var group=new SAMReadGroupRecord("synthetic");group.setSample("S000");samHeader.addReadGroup(group);
        try(var writer=new SAMFileWriterFactory().setCreateIndex(true).makeBAMWriter(samHeader,true,out.resolve("alignment.bam").toFile())) {
            for(int v=0;v<m;v++)for(int read=0;read<20;read++) {
                int position=1000+(v%20)*100;var record=new SAMRecord(samHeader);record.setReadName("artificial-"+v+"-"+read);record.setReferenceName(v<20?"1":"2");record.setAlignmentStart(position-20);record.setCigarString("41M");record.setMappingQuality(60);char[] sequence=new char[41];Arrays.fill(sequence,'A');int g=genotype[v][0];if(g==2||g==1&&read%2==0)sequence[20]='G';record.setReadString(new String(sequence));record.setBaseQualityString("I".repeat(41));record.setAttribute("RG","synthetic");record.setAttribute("NM",sequence[20]=='G'?1:0);writer.addAlignment(record);
            }
        }
        Files.writeString(out.resolve("qtl.bed"),"1\t1000\t1001\tq1\tg1\t+\n2\t1000\t1001\tq2\tg2\t-\n");Files.writeString(out.resolve("annotations.bed"),"1\t995\t1005\n1\t999\t1002\n2\t1000\t1010\n");
        System.out.println("Generated synthetic64-sample,40-variant,6-phenotype inputs and800 artificial reads;seed20261121");
    }
}
