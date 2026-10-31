/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;
import org.jlinalg.genetics.FavorVcfReader.Chromosomes;
import org.jlinalg.genetics.FavorVcfReader.Limits;
import org.jlinalg.genetics.FavorVcfReader.Mode;
import org.jlinalg.genetics.FavorVcfReader.Options;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class FavorVcfReaderTest {
    @TempDir Path directory;
    private static final String FORMAT="##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n";
    private static final String HEADER="#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO";
    private static String diploid(){return "##fileformat=VCFv4.2\n"+FORMAT+HEADER+"\tFORMAT\tS1\tS2\n22\t100\tv1\tA\tG\t.\tPASS\t.\tGT\t0/1\t1|0\n22\t200\t.\tAT\tA\t20\t.\t.\tGT\t./.\t0|.\n";}
    private static String sites(){return "##fileformat=VCFv4.2\n"+HEADER+"\n22\t100\t.\tA\tG\t.\tPASS\t.\n";}
    private Path file(String name,String text)throws IOException {Path p=directory.resolve(name);Files.writeString(p,text,StandardCharsets.UTF_8);return p;}
    private FavorVcfReader open(Path p,Mode mode,Chromosomes chr,Limits limits)throws IOException {return FavorVcfReader.open(p,new Options(mode,22,chr,limits),()->{});}
    private FavorVcfReader open(String text)throws IOException{return open(file("input.vcf",text),Mode.DIPLOID_GT,Chromosomes.EXACT,Limits.defaults());}
    private static List<FavorVcfReader.Row> all(FavorVcfReader reader)throws IOException {var out=new ArrayList<FavorVcfReader.Row>();FavorVcfReader.Row row;while((row=reader.next())!=null)out.add(row);return out;}
    private void bad(String name,String text,Mode mode)throws Exception {Path p=file(name,text);assertThrows(IOException.class,()->{try(var r=open(p,mode,Chromosomes.EXACT,Limits.defaults())){all(r);}});Files.delete(p);}
    private static Limits limits(long bytes,long decoded,int line,int headers,long headerBytes,int samples,long variants,long calls){return new Limits(bytes,decoded,line,headers,headerBytes,samples,variants,calls);}

    @Test void fixedIdentityAlleleOrderMissingnessAndPhaseAreExact()throws Exception {
        Path p=file("complete.vcf",diploid());try(var r=open(p,Mode.DIPLOID_GT,Chromosomes.EXACT,Limits.defaults())){assertEquals(List.of("S1","S2"),r.samples());var rows=all(r);assertEquals(2,rows.size());assertEquals("22-100-A-G",rows.get(0).variant().key());assertEquals("v1",rows.get(0).sourceGdsId());assertEquals("",rows.get(1).sourceGdsId());assertEquals(".",rows.get(1).id());var call=rows.get(0).calls().get(1);assertEquals(1,call.first());assertEquals(0,call.second());assertTrue(call.phased());assertEquals(-1,rows.get(1).calls().get(0).first());assertEquals(-1,rows.get(1).calls().get(0).second());assertEquals(new FavorVcfReader.Report(Files.size(p),Files.size(p),2,4,0,false,true),r.report());}
    }
    @Test void explicitVariantOnlyModeHasNoInventedSamplesOrCalls()throws Exception {
        try(var r=open(file("sites.vcf",sites()),Mode.VARIANTS_ONLY,Chromosomes.EXACT,Limits.defaults())){assertTrue(r.samples().isEmpty());var row=r.next();assertTrue(row.calls().isEmpty());assertTrue(row.formats().isEmpty());assertEquals("",row.sourceGdsId());assertNull(r.next());assertEquals(0,r.report().genotypeCalls());}
    }
    @Test void variantOnlyModeNeverSilentlyDropsGenotypeColumns()throws Exception {bad("not-sites.vcf",diploid(),Mode.VARIANTS_ONLY);}
    @Test void genotypeModeRequiresAnActualSamplePopulation()throws Exception {bad("no-samples.vcf",sites(),Mode.DIPLOID_GT);}
    @Test void explicitChrPrefixPolicyMatchesCaseInsensitiveOriginalConversion()throws Exception {
        String text=diploid().replace("22\t","cHr22\t");try(var r=open(file("prefix.vcf",text),Mode.DIPLOID_GT,Chromosomes.SOURCE_IGNORE_CHR,Limits.defaults())){var rows=all(r);assertEquals("cHr22",rows.get(0).sourceChromosome());assertEquals(22,rows.get(0).variant().chromosome());assertEquals(2,r.report().chromosomePrefixRows());}
    }
    @Test void exactChromosomeModeRefusesPrefixesAndAliases()throws Exception {
        for(String chr:List.of("chr22","022","X","23","21"))bad("chr-"+chr+".vcf",diploid().replace("22\t",chr+"\t"),Mode.DIPLOID_GT);
    }
    @Test void originalQueryOrderAndRepeatedKeysAreRetained()throws Exception {
        String text=diploid().replace("22\t100\tv1\tA\tG","22\t200\tv1\tAT\tA");try(var r=open(text)){var rows=all(r);assertEquals(rows.get(0).variant(),rows.get(1).variant());assertEquals(List.of(1L,2L),rows.stream().map(FavorVcfReader.Row::row).toList());assertEquals("v1",rows.get(0).id());assertEquals(".",rows.get(1).id());}
    }
    @Test void gzipIsDetectedByMagicWithoutChangingInputBytes()throws Exception {
        Path p=directory.resolve("compressed.vcf");try(var out=new GZIPOutputStream(Files.newOutputStream(p))){out.write(diploid().getBytes(StandardCharsets.UTF_8));}byte[] before=Files.readAllBytes(p);try(var r=open(p,Mode.DIPLOID_GT,Chromosomes.EXACT,Limits.defaults())){assertEquals(2,all(r).size());assertTrue(r.report().gzip());assertEquals(Files.size(p),r.report().encodedBytes());assertEquals(diploid().getBytes(StandardCharsets.UTF_8).length,r.report().decodedBytes());}assertArrayEquals(before,Files.readAllBytes(p));
    }
    @Test void concatenatedGzipMembersFormOneCompleteVcfStream()throws Exception {
        Path p=directory.resolve("members.gz");String text=diploid();int split=text.indexOf("22\t100");try(var raw=Files.newOutputStream(p)){var first=new GZIPOutputStream(raw);first.write(text.substring(0,split).getBytes(StandardCharsets.UTF_8));first.finish();var second=new GZIPOutputStream(raw);second.write(text.substring(split).getBytes(StandardCharsets.UTF_8));second.finish();}try(var r=open(p,Mode.DIPLOID_GT,Chromosomes.EXACT,Limits.defaults())){assertEquals(2,all(r).size());assertTrue(r.report().complete());}
    }
    @Test void gzipCrcDamageCannotBeAdmittedAsComplete()throws Exception {
        Path p=directory.resolve("broken.gz");try(var out=new GZIPOutputStream(Files.newOutputStream(p))){out.write(diploid().getBytes(StandardCharsets.UTF_8));}byte[] bytes=Files.readAllBytes(p);bytes[bytes.length-8]^=1;Files.write(p,bytes);assertThrows(IOException.class,()->{try(var r=open(p,Mode.DIPLOID_GT,Chromosomes.EXACT,Limits.defaults())){all(r);}});
    }
    @Test void gzipDecodedBytesHaveAnIndependentExpansionBudget()throws Exception {
        String text=diploid().replace("\tPASS\t.","\tPASS\tX="+"a".repeat(20000));Path p=directory.resolve("expansion.gz");try(var out=new GZIPOutputStream(Files.newOutputStream(p))){out.write(text.getBytes(StandardCharsets.UTF_8));}assertThrows(IOException.class,()->{try(var r=open(p,Mode.DIPLOID_GT,Chromosomes.EXACT,limits(100000,1000,30000,100,100000,10,10,100))){all(r);}});
    }
    @Test void crlfAndFinalRecordWithoutNewlineAreAccepted()throws Exception {
        try(var r=open(diploid().stripTrailing().replace("\n","\r\n"))){assertEquals(2,all(r).size());assertTrue(r.report().complete());}
    }
    @Test void headerMetadataAndUnicodeSampleIdsArePreserved()throws Exception {
        String text=diploid().replace("\tS1\tS2","\tα\tβ");try(var r=open(text)){assertEquals(List.of("α","β"),r.samples());assertTrue(r.headerLines().get(1).contains("Description=\"Genotype\""));assertEquals("α",r.next().calls().get(0).sample());}
    }
    @Test void structuredFormatAttributesCanBeReorderedAndContainQuotedCommas()throws Exception {
        String text=diploid().replace(FORMAT,"##FORMAT=<Description=\"a,ID=bad, \\\"quoted\\\"\",Type=String,Number=1,ID=GT>\n");try(var r=open(text)){assertEquals(2,all(r).size());}
    }
    @Test void extraDeclaredFormatAndInfoValuesRemainOpaqueAndUnchanged()throws Exception {
        String text=diploid().replace(FORMAT,FORMAT+"##FORMAT=<ID=DP,Number=1,Type=Integer,Description=\"Depth\">\n").replace("\tGT\t0/1\t1|0","\tGT:DP\t0/1:9\t1|0").replace("\tPASS\t.","\tPASS\tX=opaque");try(var r=open(text)){var row=r.next();assertEquals(List.of("GT","DP"),row.formats());assertEquals(List.of("0/1","9"),row.calls().get(0).formatValues());assertEquals(List.of("1|0"),row.calls().get(1).formatValues());assertEquals("X=opaque",row.info());assertEquals(2,all(r).size()+1);}
    }
    @Test void fullyMissingAndPartialDiploidCallsKeepEveryExplicitPhaseFlag()throws Exception {
        String text=diploid().replace("\t0/1\t1|0","\t.|.\t./1");try(var r=open(text)){var row=r.next();assertTrue(row.calls().get(0).phased());assertEquals(-1,row.calls().get(0).first());assertFalse(row.calls().get(1).phased());assertEquals(1,row.calls().get(1).second());}
    }
    @Test void singleDotHaploidAndPolyploidGtAreRejectedWithoutPadding()throws Exception {
        for(String gt:List.of(".","0","1","0/1/0","0|1|0"))bad("ploidy-"+gt.replace('/','_').replace('|','p')+".vcf",diploid().replace("\t0/1\t1|0","\t"+gt+"\t1|0"),Mode.DIPLOID_GT);
    }
    @Test void invalidAlleleIndicesAndMalformedGtAreRejected()throws Exception {
        for(String gt:List.of("2/2","-1/0","00/01","0\\1","0:1",""))bad("bad-gt-"+Math.abs(gt.hashCode())+".vcf",diploid().replace("\t0/1\t1|0","\t"+gt+"\t1|0"),Mode.DIPLOID_GT);
    }
    @Test void missingGtFieldAndUndeclaredOrRepeatedFormatFieldsReject()throws Exception {
        bad("missing-gt.vcf",diploid().replace("\tGT\t","\tDP\t"),Mode.DIPLOID_GT);bad("unknown-format.vcf",diploid().replace("\tGT\t","\tGT:UNKNOWN\t"),Mode.DIPLOID_GT);bad("repeat-format.vcf",diploid().replace("\tGT\t","\tGT:GT\t"),Mode.DIPLOID_GT);
    }
    @Test void extraSampleFormatFieldsCannotBeTruncated()throws Exception {bad("extra-values.vcf",diploid().replace("\t0/1\t1|0","\t0/1:9\t1|0"),Mode.DIPLOID_GT);}
    @Test void duplicateSampleAndFormatHeaderIdsReject()throws Exception {
        bad("duplicate-sample.vcf",diploid().replace("\tS1\tS2","\tS1\tS1"),Mode.DIPLOID_GT);bad("duplicate-format.vcf",diploid().replace(FORMAT,FORMAT+FORMAT),Mode.DIPLOID_GT);
    }
    @Test void gtHeaderTypeAndNumberMustDeclareTheActualStringField()throws Exception {
        bad("wrong-type.vcf",diploid().replace("Type=String","Type=Integer"),Mode.DIPLOID_GT);bad("wrong-number.vcf",diploid().replace("Number=1","Number=2"),Mode.DIPLOID_GT);bad("missing-definition.vcf",diploid().replace(FORMAT,""),Mode.DIPLOID_GT);
    }
    @Test void malformedStructuredHeaderQuotesAreNotRepaired()throws Exception {bad("bad-header.vcf",diploid().replace("Description=\"Genotype\"","Description=\"Genotype"),Mode.DIPLOID_GT);}
    @Test void invalidFixedColumnsAndDataWidthsReject()throws Exception {
        bad("column-header.vcf",diploid().replace("#CHROM\tPOS","#CHROM\tPOSITION"),Mode.DIPLOID_GT);bad("row-width.vcf",diploid().replace("\t0/1\t1|0","\t0/1"),Mode.DIPLOID_GT);bad("extra-row-width.vcf",diploid().replace("\t0/1\t1|0","\t0/1\t1|0\textra"),Mode.DIPLOID_GT);
    }
    @Test void positionsMustBePositiveCanonicalSigned32Coordinates()throws Exception {
        for(String position:List.of("0","01","1.0","2147483648"))bad("position-"+position+".vcf",diploid().replace("22\t100","22\t"+position),Mode.DIPLOID_GT);try(var r=open(diploid().replace("22\t100","22\t2147483647"))){assertEquals(Integer.MAX_VALUE,r.next().variant().position());}
    }
    @Test void multiallelicSymbolicLowercaseAndIdenticalAllelesAreExplicitlyUnsupported()throws Exception {
        for(String alt:List.of("G,T","<DEL>","*","g","A"))bad("allele-"+Math.abs(alt.hashCode())+".vcf",diploid().replace("v1\tA\tG","v1\tA\t"+alt),Mode.DIPLOID_GT);
    }
    @Test void qualityMustBeMissingOrFiniteNonnegativeDecimal()throws Exception {
        for(String quality:List.of("-1","NaN","Inf","0x1.0p5","1e1000"))bad("quality-"+Math.abs(quality.hashCode())+".vcf",diploid().replace("G\t.\tPASS","G\t"+quality+"\tPASS"),Mode.DIPLOID_GT);
    }
    @Test void blankOrRepeatedHeaderLinesAfterDataAdmissionAreNotSkipped()throws Exception {
        bad("blank-data.vcf",diploid().replace("22\t100","\n22\t100"),Mode.DIPLOID_GT);bad("late-header.vcf",diploid()+HEADER+"\n",Mode.DIPLOID_GT);
    }
    @Test void invalidUtf8BomAndControlsRejectWithoutEncodingRepair()throws Exception {
        bad("bom.vcf","\ufeff"+diploid(),Mode.DIPLOID_GT);bad("control.vcf",diploid().replace("v1","v\u0000"),Mode.DIPLOID_GT);Path p=directory.resolve("utf8.vcf");byte[] bytes=diploid().getBytes(StandardCharsets.UTF_8);bytes[2]=(byte)0xff;Files.write(p,bytes);assertThrows(IOException.class,()->open(p,Mode.DIPLOID_GT,Chromosomes.EXACT,Limits.defaults()));
    }
    @Test void rowSampleCallHeaderAndLineCapacitiesRejectCompleteInputs()throws Exception {
        Path p=file("bounds.vcf",diploid());for(Limits limit:List.of(limits(100000,100000,10000,10,10000,1,10,100),limits(100000,100000,10000,2,10000,10,10,100),limits(100000,100000,10,10,10000,10,10,100),limits(100000,100000,10000,10,20,10,10,100),limits(100000,100000,10000,10,10000,10,1,100),limits(100000,100000,10000,10,10000,10,10,3)))assertThrows(IOException.class,()->{try(var r=open(p,Mode.DIPLOID_GT,Chromosomes.EXACT,limit)){all(r);}});
    }
    @Test void exactStaticEncodedAndDecodedByteCapacitiesAreSupported()throws Exception {
        Path p=file("exact-bytes.vcf",diploid());long size=Files.size(p);try(var r=open(p,Mode.DIPLOID_GT,Chromosomes.EXACT,limits(size,size,10000,10,10000,2,2,4))){assertEquals(2,all(r).size());assertEquals(size,r.report().decodedBytes());}assertThrows(IOException.class,()->open(p,Mode.DIPLOID_GT,Chromosomes.EXACT,limits(size-1,size,10000,10,10000,2,2,4)));
    }
    @Test void failedScansCannotBeResumedAndEarlyCloseIsIncomplete()throws Exception {
        var r=open(diploid().replace("\t0/1\t1|0","\t.\t1|0"));assertThrows(IOException.class,r::next);assertFalse(r.report().complete());assertTrue(assertThrows(IOException.class,r::next).getMessage().contains("cannot be resumed"));r.close();var second=open(diploid());second.close();assertFalse(second.report().complete());assertThrows(IOException.class,second::next);
    }
    @Test void cancellationAfterHeaderAdmissionInvalidatesTheScan()throws Exception {
        var cancelled=new AtomicBoolean();try(var r=FavorVcfReader.open(file("cancel.vcf",diploid()),new Options(Mode.DIPLOID_GT,22,Chromosomes.EXACT,Limits.defaults()),()->{if(cancelled.get())throw new IllegalStateException("cancelled");})){cancelled.set(true);assertThrows(IllegalStateException.class,r::next);cancelled.set(false);assertThrows(IOException.class,r::next);assertFalse(r.report().complete());}
    }
    @Test void exposedHeadersSamplesRowsAndCallValuesAreImmutable()throws Exception {
        try(var r=open(diploid())){assertThrows(UnsupportedOperationException.class,()->r.headerLines().clear());assertThrows(UnsupportedOperationException.class,()->r.samples().clear());var row=r.next();assertThrows(UnsupportedOperationException.class,()->row.calls().clear());assertThrows(UnsupportedOperationException.class,()->row.formats().clear());assertThrows(UnsupportedOperationException.class,()->row.calls().get(0).formatValues().clear());}
    }
    @Test void headerOnlyAndMissingFileFailuresRemainDistinct()throws Exception {
        Path p=file("header-only.vcf",diploid().substring(0,diploid().indexOf("22\t100")));try(var r=open(p,Mode.DIPLOID_GT,Chromosomes.EXACT,limits(100000,100000,10000,10,10000,10,0,0))){assertNull(r.next());assertTrue(r.report().complete());assertEquals(0,r.report().variants());}assertThrows(IOException.class,()->open(directory.resolve("absent.vcf"),Mode.VARIANTS_ONLY,Chromosomes.EXACT,Limits.defaults()));bad("empty.vcf","",Mode.VARIANTS_ONLY);
    }
    @Test void invalidModesChromosomesAndCapacitiesReject() {
        assertThrows(IllegalArgumentException.class,()->new Options(Mode.DIPLOID_GT,23,Chromosomes.EXACT,Limits.defaults()));assertThrows(IllegalArgumentException.class,()->limits(0,1,1,1,1,1,1,1));assertThrows(IllegalArgumentException.class,()->limits(1,1,1,1,1,1000001,1,1));assertThrows(IllegalArgumentException.class,()->limits(1,1,1,1,1,1,1,-1));
    }
}
