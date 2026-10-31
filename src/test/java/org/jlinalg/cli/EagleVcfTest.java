/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import org.jlinalg.genetics.EagleGenotypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class EagleVcfTest {
    @Test void sampleFreeExclusionReaderIsExplicitAndCannotBecomeAGenotypeInput()throws Exception {
        var p=file("sites.vcf","##fileformat=VCFv4.2\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n22\t100\tv\tA\tG\t.\tPASS\t.\n");
        assertThrows(IOException.class,()->EagleVcf.open(p,EagleVcf.Limits.defaults(),()->{}));
        try(var r=EagleVcf.openSites(p,EagleVcf.Limits.defaults(),()->{})){assertTrue(r.samples().isEmpty());var row=r.next();assertTrue(row.calls().isEmpty());assertTrue(row.formats().isEmpty());assertNull(r.next());assertEquals(1,r.report().variants());assertEquals(0,r.report().calls());assertTrue(r.report().complete());}
    }
    @Test void sampleFreeExclusionStillRequiresExactRowWidthAndCompleteValidCoordinates()throws Exception {
        String h="##fileformat=VCFv4.2\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\n";
        for(String row:List.of("22\tBAD\tv\tA\tG\t.\tPASS\t.\n","22\t100\tv\tA\tG\t.\tPASS\t.\tEXTRA\n")) {
            var p=file("bad-sites-"+row.hashCode()+".vcf",h+row);assertThrows(IOException.class,()->{try(var r=EagleVcf.openSites(p,EagleVcf.Limits.defaults(),()->{})){consume(r);}});
        }
    }
    @TempDir Path root;
    private static final String HEADER="##fileformat=VCFv4.2\n##FORMAT=<ID=GT,Number=1,Type=String,Description=\"GT, original\">\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tA\tβ\n";
    private Path file(String name,String text)throws IOException { var p=root.resolve(name);Files.writeString(p,text,StandardCharsets.UTF_8);return p; }
    private static String row(String contig,int position,String a,String b) { return contig+"\t"+position+"\tv"+position+"\tA\tC\t.\tPASS\t.\tGT\t"+a+"\t"+b+"\n"; }
    private static void consume(EagleVcf.Reader reader)throws IOException { while(reader.next()!=null){/* complete admission */} }
    private static EagleGenotypes.Options cohort(){return new EagleGenotypes.Options(EagleGenotypes.Mode.COHORT,false,EagleGenotypes.Missing.IMPUTE,false);}
    private static EagleVcfAudit.Selection phased(){return new EagleVcfAudit.Selection(EagleVcfAudit.Action.PHASED,"declared selected chromosome");}

    @Test void streamingReaderPreservesUnphasedAndMissingGtWithoutMinimacPhaseInference()throws Exception {
        var p=file("δ target.vcf",HEADER+row("22",100,"0/1",".")+row("22",200,"0|.","1"));
        try(var r=EagleVcf.open(p,EagleVcf.Limits.defaults(),()->{})) {
            assertEquals(List.of("A","β"),r.samples());assertFalse(r.report().complete());
            var a=r.next();assertEquals("0/1",a.calls().get(0).raw());assertFalse(a.calls().get(0).phased());assertEquals(1,a.calls().get(1).ploidy());
            var b=r.next();assertTrue(b.calls().get(0).partiallyMissing());assertEquals("1",b.originalSampleFields().get(1));
            assertNull(r.next());assertTrue(r.report().complete());assertEquals(4,r.report().calls());assertEquals(2,r.report().haploidCalls());
        }
    }
    @Test void gzipCrcAndUtf8MustComplete()throws Exception {
        var p=root.resolve("target.vcf.gz");try(var out=new GZIPOutputStream(Files.newOutputStream(p))){out.write((HEADER+row("22",100,"0/1","1/1")).getBytes(StandardCharsets.UTF_8));}
        try(var r=EagleVcf.open(p,EagleVcf.Limits.defaults(),()->{})){consume(r);assertTrue(r.report().gzip());assertTrue(r.report().complete());}
        byte[] bytes=Files.readAllBytes(p);bytes[bytes.length-8]^=1;Files.write(p,bytes);
        assertThrows(IOException.class,()->{try(var r=EagleVcf.open(p,EagleVcf.Limits.defaults(),()->{})){consume(r);}});
    }
    @Test void malformedUtf8DoesNotBecomeReplacementCharacters()throws Exception {
        var p=root.resolve("bad.vcf");byte[] prefix=(HEADER+row("22",100,"0/1","1/1")).getBytes(StandardCharsets.UTF_8);var bytes=java.util.Arrays.copyOf(prefix,prefix.length+1);bytes[bytes.length-1]=(byte)0xff;Files.write(p,bytes);
        assertThrows(IOException.class,()->{try(var r=EagleVcf.open(p,EagleVcf.Limits.defaults(),()->{})){consume(r);}});
    }
    @Test void headerGtDeclarationAndUniqueSamplesAreRequired()throws Exception {
        for(String h:List.of(HEADER.replace("Number=1","Number=2"),HEADER.replace("Type=String","Type=Integer"),HEADER.replace("\tβ","\tA"),HEADER.replace("##FORMAT=<ID=GT,Number=1,Type=String,Description=\"GT, original\">\n",""))) {
            var p=file("bad-"+Math.abs(h.hashCode())+".vcf",h+row("22",100,"0/1","1/1"));assertThrows(IOException.class,()->EagleVcf.open(p,EagleVcf.Limits.defaults(),()->{}));
        }
    }
    @Test void nonGtMetadataIsRetainedOpaqueAndUndeclaredFieldsReported()throws Exception {
        var p=file("opaque.vcf",HEADER+"22\t100\tv1\tA\tC,G\t.\tPASS\tX=opaque\tXX:GT\tvalue:2/0\t.:1|2\n");
        try(var r=EagleVcf.open(p,EagleVcf.Limits.defaults(),()->{})){var row=r.next();assertEquals("value:2/0",row.originalSampleFields().get(0));assertEquals(List.of(2,0),row.calls().get(0).alleles());assertEquals("X=opaque",row.info());assertNull(r.next());assertEquals(java.util.Set.of("XX"),r.report().undeclaredNonGtFormats());}
    }
    @Test void malformedRowsGtAndDuplicatesCannotAdmitAPrefix()throws Exception {
        for(String bad:List.of(row("22",200,"0/1/1","0/1"),row("22",200,"2/0","0/1"),"BROKEN\n",row("22",100,"0/1","0/1"))) {
            var p=file("bad-"+Math.abs(bad.hashCode())+".vcf",HEADER+row("22",100,"0/1","0/1")+bad);
            try(var r=EagleVcf.open(p,EagleVcf.Limits.defaults(),()->{})){assertNotNull(r.next());assertThrows(IOException.class,()->consume(r));assertFalse(r.report().complete());assertThrows(IOException.class,r::next);}
        }
    }
    @Test void completeContigGroupsAndWithinContigOrderAreRequired()throws Exception {
        for(String rows:List.of(row("22",200,"0/1","0/1")+row("22",100,"0/1","0/1"),row("22",100,"0/1","0/1")+row("X",100,"0/1","0/1")+row("22",200,"0/1","0/1"))) {
            var p=file("order-"+Math.abs(rows.hashCode())+".vcf",HEADER+rows);assertThrows(IOException.class,()->{try(var r=EagleVcf.open(p,EagleVcf.Limits.defaults(),()->{})){consume(r);}});
        }
    }
    @Test void completeRowAndGenotypeCapacitiesAreNotTruncationPolicies()throws Exception {
        var p=file("capacity.vcf",HEADER+row("22",100,"0/1","0/1")+row("22",200,"0/1","0/1"));var d=EagleVcf.Limits.defaults();
        var limits=new EagleVcf.Limits(new MinimacVcf.Limits(2,1,2,10000,10000,1024,10000,100),100,10000,10,10,1000);
        try(var r=EagleVcf.open(p,limits,()->{})){assertNotNull(r.next());assertThrows(IOException.class,r::next);assertFalse(r.report().complete());}
        assertNotNull(d);
    }
    @Test void samePositionKeyInventoryHasAnExplicitCharacterCapacity()throws Exception {
        var p=file("keys.vcf",HEADER+row("22",100,"0/1","0/1"));var d=EagleVcf.Limits.defaults();var low=new EagleVcf.Limits(d.text(),d.headerLines(),d.headerCharacters(),d.formatFields(),d.alternateAlleles(),2);
        try(var r=EagleVcf.open(p,low,()->{})){assertThrows(IOException.class,r::next);}
    }
    @Test void closedAndCancelledReadersDoNotCertifyEof()throws Exception {
        var p=file("cancel.vcf",HEADER+row("22",100,"0/1","0/1"));var r=EagleVcf.open(p,EagleVcf.Limits.defaults(),()->{});r.close();assertFalse(r.report().complete());assertThrows(IOException.class,r::next);
        var checks=new AtomicInteger();assertThrows(IllegalStateException.class,()->EagleVcf.open(p,EagleVcf.Limits.defaults(),()->{if(checks.incrementAndGet()>2)throw new IllegalStateException("cancel");}));
    }
    @Test void sourceModificationIsRefusedAtCompleteEof()throws Exception {
        var p=file("change.vcf",HEADER+row("22",100,"0/1","0/1"));try(var r=EagleVcf.open(p,EagleVcf.Limits.defaults(),()->{})){assertNotNull(r.next());Files.setLastModifiedTime(p,java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()+60000));assertThrows(IOException.class,r::next);assertFalse(r.report().complete());}
    }
    @Test void pairedAuditChecksEveryIdentityCallAndEof()throws Exception {
        var p=file("input.vcf",HEADER+row("22",100,"0/1","1/1"));var q=file("output.vcf",HEADER+row("22",100,"1|0","1|1"));
        var r=EagleVcfAudit.compare(p,q,cohort(),EagleVcf.Limits.defaults(),row->phased(),(a,b,s)->{},()->{});assertEquals(2,r.genotypes().fullyObservedInputCalls());assertTrue(r.input().complete());assertTrue(r.output().complete());
    }
    @Test void wrongVariantExtraMissingAndChangedCallsFailCompleteAudit()throws Exception {
        var p=file("input.vcf",HEADER+row("22",100,"0/1","1/1"));
        for(String text:List.of(HEADER,HEADER+row("22",101,"1|0","1|1"),HEADER+row("22",100,"0|0","1|1"),HEADER+row("22",100,"1|0","1|1")+row("22",200,"0|0","1|1"))) {
            var q=file("out-"+Math.abs(text.hashCode())+".vcf",text);assertThrows(IOException.class,()->EagleVcfAudit.compare(p,q,cohort(),EagleVcf.Limits.defaults(),row->phased(),(a,b,s)->{},()->{}));
        }
    }
    @Test void selectionRecordsAllSourceRowsIncludingExcludedChromosomes()throws Exception {
        var p=file("selected-input.vcf",HEADER+row("21",100,"0/1","1/1")+row("22",100,"1/1","0/1"));var q=file("selected-output.vcf",HEADER+row("21",100,"1|0","1|1"));var count=new AtomicInteger();
        var r=EagleVcfAudit.compare(p,q,cohort(),EagleVcf.Limits.defaults(),row->row.contig().equals("21")?phased():new EagleVcfAudit.Selection(EagleVcfAudit.Action.EXCLUDED,"other declared chromosome"),(a,b,s)->{count.incrementAndGet();if(s.action()==EagleVcfAudit.Action.EXCLUDED)assertNull(b);},()->{});
        assertEquals(2,count.get());assertEquals(2,r.input().variants());assertEquals(1,r.excludedRows());assertEquals(1,r.output().variants());
    }
    @Test void oneOriginalRunCannotClaimPhasedResultsOnMultipleLiteralContigs()throws Exception {
        var p=file("multi-in.vcf",HEADER+row("21",100,"0/1","1/1")+row("22",100,"0/1","1/1"));var q=file("multi-out.vcf",HEADER+row("21",100,"1|0","1|1")+row("22",100,"1|0","1|1"));
        assertThrows(IOException.class,()->EagleVcfAudit.compare(p,q,cohort(),EagleVcf.Limits.defaults(),row->phased(),(a,b,s)->{},()->{}));
    }
}
