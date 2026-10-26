/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFIteratorBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BcfCompressionTest {
    @TempDir Path root;
    private byte[] raw()throws Exception {
        Path vcf=root.resolve("input.vcf"),bcf=root.resolve("original.bcf");Files.writeString(vcf,"##fileformat=VCFv4.2\n##contig=<ID=1,length=100>\n##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n##FORMAT=<ID=DS,Number=1,Type=Float,Description=\"ALT dosage\">\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\ts1\ts2\ts3\n1\t10\tv1\tA\tC\t.\t.\t.\tGT:DS\t0/0:.25\t0/1:1.25\t1/1:1.75\n1\t20\tv2\tG\tT\t.\t.\t.\tGT:DS\t0/1:1\t0/0:0\t1/1:2\n");
        try(var input=new VCFIteratorBuilder().open(vcf);var output=new VariantContextWriterBuilder().setOutputPath(bcf).unsetOption(Options.INDEX_ON_THE_FLY).setOption(Options.FORCE_BCF).build()){output.writeHeader(input.getHeader());while(input.hasNext())output.add(input.next());}return Files.readAllBytes(bcf);
    }
    @Test void bcf21And22PlainGzipAndStandardBgzfHaveTheSameMetadataAndReorderedDosages()throws Exception {
        byte[] raw=raw();for(int minor:List.of(1,2))for(String compression:List.of("plain","gzip","bgzf")){
            raw[4]=(byte)minor;Path file=root.resolve("v"+minor+"-"+compression+".bcf");if(compression.equals("plain"))Files.write(file,raw);else if(compression.equals("gzip"))try(var out=new GZIPOutputStream(Files.newOutputStream(file))){out.write(raw);}else try(var out=new BlockCompressedOutputStream(file.toFile())){out.write(raw);}
            var source=VariantSources.open(file);assertEquals(List.of("s1","s2","s3"),source.metadata().sampleIds());for(int repeat=0;repeat<2;repeat++)try(var reader=source.open(new int[]{2,0})){var rows=reader.read(8).variants();assertEquals(2,rows.size());assertArrayEquals(new double[]{1.75,.25},rows.get(0).dosages());assertArrayEquals(new double[]{2,1},rows.get(1).dosages());assertEquals(10,rows.get(0).position());assertEquals("C",rows.get(0).alternateAllele());assertEquals(VariantGenotypeMetadata.Origin.DOSAGE,rows.get(0).genotypeMetadata().origin());assertNull(reader.read(8));}
        }
    }
    @Test void corruptCompressedBcfIsRejectedWithoutTreatingGzipAsBcfBytes()throws Exception {
        Path file=root.resolve("corrupt.bcf");try(var out=new GZIPOutputStream(Files.newOutputStream(file))){out.write(raw());}byte[] compressed=Files.readAllBytes(file);compressed[compressed.length-8]^=1;Files.write(file,compressed);
        assertThrows(Exception.class,()->{var source=VariantSources.open(file);try(var reader=source.open()){while(reader.read(1)!=null){}}});
    }
}
