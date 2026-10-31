/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jlinalg.genetics.FavorVcfReader.Chromosomes;
import org.jlinalg.genetics.FavorVcfReader.Limits;
import org.jlinalg.genetics.FavorVcfReader.Mode;
import org.jlinalg.genetics.FavorVcfReader.Options;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class FavorVcfAuditTest {
    @TempDir Path directory;
    private static final String HEADER="##fileformat=VCFv4.2\n##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO";
    private static String diploid(){return HEADER+"\tFORMAT\tα\tS2\n22\t100\tv1\tA\tG\t.\tPASS\t.\tGT\t0/1\t1|0\n22\t200\t.\tAT\tA\t.\tPASS\t.\tGT\t./.\t0|.\n";}
    private FavorVcfAudit.Result scan(String name,String text,Mode mode)throws IOException {
        Path p=directory.resolve(name);Files.writeString(p,text,StandardCharsets.UTF_8);return FavorVcfAudit.scan(p,new Options(mode,22,Chromosomes.EXACT,Limits.defaults()),()->{});
    }
    private static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static byte[] ints(int... values){var out=ByteBuffer.allocate(values.length*4).order(ByteOrder.LITTLE_ENDIAN);for(int value:values)out.putInt(value);return out.array();}
    @Test void completeGenotypeAndPhaseUseSourceArrayOrderAndInt32Missing()throws Exception {
        var r=scan("gt.vcf",diploid(),Mode.DIPLOID_GT);assertEquals("favor-gds-values-v1",r.schema());assertEquals(2,r.variants());assertEquals(2,r.samples());
        assertEquals(new FavorVcfAudit.Digest(FavorVcfAudit.Kind.INT32_LE,8,hash(ints(0,1,1,0,Integer.MIN_VALUE,Integer.MIN_VALUE,0,Integer.MIN_VALUE))),r.digests().get("genotype"));
        assertEquals(new FavorVcfAudit.Digest(FavorVcfAudit.Kind.INT32_LE,4,hash(ints(0,1,0,1))),r.digests().get("phase"));assertTrue(r.input().complete());
    }
    @Test void sampleUnicodeUsesUtf8ByteLengthsRatherThanJavaCharacterLengths()throws Exception {
        var r=scan("unicode.vcf",diploid(),Mode.DIPLOID_GT);assertEquals(hash(new byte[]{2,0,0,0,(byte)0xce,(byte)0xb1,2,0,0,0,'S','2'}),r.digests().get("sample.id").sha256());assertEquals(2,r.digests().get("sample.id").values());
    }
    @Test void coordinatesAndSequentialVariantIdsHaveIndependentCompleteHashes()throws Exception {
        var r=scan("ids.vcf",diploid(),Mode.DIPLOID_GT);assertEquals(hash(ints(1,2)),r.digests().get("variant.id").sha256());assertEquals(hash(ints(100,200)),r.digests().get("position").sha256());assertEquals(2,r.digests().get("chromosome").values());
    }
    @Test void missingAnnotationIdUsesObservedEmptySourceStringButKeepsOtherIdentityValues()throws Exception {
        var r=scan("id.vcf",diploid(),Mode.DIPLOID_GT);assertEquals(hash(new byte[]{2,0,0,0,'v','1',0,0,0,0}),r.digests().get("annotation/id").sha256());assertEquals(hash(new byte[]{1,0,0,0,'A',2,0,0,0,'A','T'}),r.digests().get("$ref").sha256());
    }
    @Test void changingOnlyPhaseChangesPhaseDigestAndPreservesEveryAllele()throws Exception {
        var a=scan("phase-a.vcf",diploid(),Mode.DIPLOID_GT);var b=scan("phase-b.vcf",diploid().replace("0/1","0|1"),Mode.DIPLOID_GT);assertEquals(a.digests().get("genotype"),b.digests().get("genotype"));assertNotEquals(a.digests().get("phase"),b.digests().get("phase"));
    }
    @Test void genotypeAlleleOrderIsNeverSortedOrCollapsed()throws Exception {
        var a=scan("order-a.vcf",diploid(),Mode.DIPLOID_GT);var b=scan("order-b.vcf",diploid().replace("1|0","0|1"),Mode.DIPLOID_GT);assertNotEquals(a.digests().get("genotype"),b.digests().get("genotype"));assertEquals(a.digests().get("phase"),b.digests().get("phase"));
    }
    @Test void variantOnlyNeverInventsGenotypeOrPhaseEntries()throws Exception {
        String header=HEADER.substring(0,HEADER.indexOf("\n##FORMAT"))+"\n"+HEADER.substring(HEADER.indexOf("#CHROM"));var r=scan("sites.vcf",header+"\n22\t100\t.\tA\tG\t.\tPASS\t.\n",Mode.VARIANTS_ONLY);assertEquals(0,r.samples());assertFalse(r.digests().containsKey("genotype"));assertFalse(r.digests().containsKey("phase"));assertEquals(hash(new byte[0]),r.digests().get("sample.id").sha256());
    }
    @Test void explicitPrefixPolicyHashesSourceConvertedIdentity()throws Exception {
        Path p=directory.resolve("prefix.vcf");Files.writeString(p,diploid().replace("22\t","cHr22\t"),StandardCharsets.UTF_8);var a=FavorVcfAudit.scan(p,new Options(Mode.DIPLOID_GT,22,Chromosomes.SOURCE_IGNORE_CHR,Limits.defaults()),()->{});var b=scan("exact.vcf",diploid(),Mode.DIPLOID_GT);assertEquals(a.digests(),b.digests());assertEquals(2,a.input().chromosomePrefixRows());
    }
    @Test void repeatedValuesCrossTheInternalBufferWithoutTruncation()throws Exception {
        String header=HEADER+"\tFORMAT\tα\tS2\n";var text=new StringBuilder(header);var expected=ByteBuffer.allocate(3000*16).order(ByteOrder.LITTLE_ENDIAN);for(int i=0;i<3000;i++){text.append("22\t").append(i+1).append("\t.\tA\tG\t.\tPASS\t.\tGT\t0/1\t1|0\n");expected.putInt(0).putInt(1).putInt(1).putInt(0);}var r=scan("large.vcf",text.toString(),Mode.DIPLOID_GT);assertEquals(3000,r.variants());assertEquals(12000,r.digests().get("genotype").values());assertEquals(hash(expected.array()),r.digests().get("genotype").sha256());
    }
    @Test void failedLastRecordReturnsNoCompleteAudit()throws Exception {assertThrows(IOException.class,()->scan("bad.vcf",diploid()+"22\t300\t.\tA\tG\t.\tPASS\t.\tGT\t.\t0/1\n",Mode.DIPLOID_GT));}
    @Test void completeCapacityFailureReturnsNoTruncatedAudit()throws Exception {
        Path p=directory.resolve("capacity.vcf");Files.writeString(p,diploid(),StandardCharsets.UTF_8);var l=Limits.defaults();var bound=new Limits(l.maximumBytes(),l.maximumDecodedBytes(),l.maximumLineBytes(),l.maximumHeaderLines(),l.maximumHeaderBytes(),l.maximumSamples(),1,l.maximumCalls());assertThrows(IOException.class,()->FavorVcfAudit.scan(p,new Options(Mode.DIPLOID_GT,22,Chromosomes.EXACT,bound),()->{}));
    }
    @Test void cancellationStopsTheCompleteAudit()throws Exception {
        Path p=directory.resolve("cancel.vcf");Files.writeString(p,diploid(),StandardCharsets.UTF_8);var checks=new AtomicInteger();assertThrows(IllegalStateException.class,()->FavorVcfAudit.scan(p,new Options(Mode.DIPLOID_GT,22,Chromosomes.EXACT,Limits.defaults()),()->{if(checks.incrementAndGet()>20)throw new IllegalStateException("cancelled");}));
    }
    @Test void completedDigestMapCannotBeChangedByCaller()throws Exception {var r=scan("immutable.vcf",diploid(),Mode.DIPLOID_GT);assertThrows(UnsupportedOperationException.class,()->r.digests().clear());}
    @Test void emptyHeaderOnlyPopulationHasExplicitZeroCounts()throws Exception {
        var r=scan("empty.vcf",HEADER+"\tFORMAT\tα\tS2\n",Mode.DIPLOID_GT);assertEquals(0,r.variants());assertEquals(hash(new byte[0]),r.digests().get("genotype").sha256());assertEquals(0,r.digests().get("phase").values());assertTrue(r.input().complete());
    }
    @Test void digestMetadataRequiresNonnegativeCountsAndCanonicalSha256() {
        for(String invalid:List.of("","abc","F".repeat(64)))assertThrows(IllegalArgumentException.class,()->new FavorVcfAudit.Digest(FavorVcfAudit.Kind.INT32_LE,0,invalid));assertThrows(IllegalArgumentException.class,()->new FavorVcfAudit.Digest(FavorVcfAudit.Kind.INT32_LE,-1,"0".repeat(64)));
    }
}
