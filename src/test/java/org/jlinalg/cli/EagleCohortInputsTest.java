/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import org.jlinalg.genetics.EagleGeneticMap;
import org.jlinalg.genetics.EagleGenotypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class EagleCohortInputsTest {
    @TempDir Path root;
    private static final String HEADER = "##fileformat=VCFv4.2\n##contig=<ID=22>\n##contig=<ID=X>\n##FORMAT=<ID=GT,Number=1,Type=String,Description=\"GT\">\n##FORMAT=<ID=DP,Number=1,Type=Integer,Description=\"depth\">\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tA\tβ\n";
    private static String row(String chr, String pos, String a, String b) { return chr+"\t"+pos+"\tv"+pos+"\tA\tC\t1.00\tPASS\tAN=4\tDP:GT\t7:"+a+"\t8:"+b+"\n"; }
    private Path text(String name, String value) throws IOException { Path p=root.resolve(name);Files.writeString(p,value,StandardCharsets.UTF_8);return p; }
    private Path map() throws IOException { return text("map.txt",EagleGeneticMap.HEADER+"\n22 0 1 0\n22 1000 1 1\n23 0 1 0\n23 1000 1 1\n"); }
    private EagleCohortInputs.Spec spec(String contig, int chr, int start, int end, EagleCohortInputs.PartialMissing partial, long ledger, long workspace) {
        var v=new EagleVcf.Limits(new MinimacVcf.Limits(10,100,1000,1000000,1000000,8192,1000000,100),100,10000,10,10,10000);
        return new EagleCohortInputs.Spec(contig,chr,23,start,end,new EagleGenotypes.Options(EagleGenotypes.Mode.COHORT,chr==23,EagleGenotypes.Missing.KEEP,false),
                partial,EagleGeneticMap.Outside.REQUIRE_BRACKET,new EagleGeneticMap.Limits(100,100,1024,100000,10),v,1000000,ledger,workspace);
    }
    private EagleCohortInputs.Spec spec() { return spec("22",22,100,200,EagleCohortInputs.PartialMissing.REFUSE,1000000,10000000); }
    private EagleCohortInputs.Prepared prepare(Path input, Path geneticMap, String name, EagleCohortInputs.Spec s) throws IOException {
        return EagleCohortInputs.prepare(input,geneticMap,root.resolve(name),s,()->{});
    }
    @Test void protectsCompleteInputsAndPreservesEverySelectedGtAndOpaqueField() throws Exception {
        Path input=text("δ target.vcf",HEADER+row("22","000100","00/01","1|1")+row("22","200","0/0","0|1")+row("22","201","0/1","0/1")+row("X","100","1","0/1"));
        String original=Files.readString(input);var p=prepare(input,map(),"work",spec());
        assertEquals(2,p.selectedRows());assertEquals(2,p.excludedRows());assertEquals(4,p.input().variants());assertEquals(2,p.stage().variants());
        assertEquals(original,Files.readString(p.snapshots().get(0).copy()));assertEquals(original,Files.readString(input));assertTrue(Files.size(p.index())>0);
        try(var reader=EagleVcf.open(p.target(),EagleCohortInputs.stagedLimits(p.spec()),()->{})) {
            var first=reader.next();assertEquals("000100",first.positionText());assertEquals("00/01",first.calls().get(0).raw());assertEquals("7:00/01",first.originalSampleFields().get(0));
            assertEquals("AN=4",first.info());assertEquals("1.00",first.quality());assertNotNull(reader.next());assertNull(reader.next());assertTrue(reader.report().complete());
        }
        var ledger=Files.readAllLines(p.work().resolve("target-selection.tsv"));assertEquals(5,ledger.size());assertTrue(ledger.get(3).contains("outside-explicit-inclusive-region"));assertTrue(ledger.get(4).contains("outside-explicit-literal-contig"));p.verify(()->{});
    }
    @Test void compressedTargetAndMapAreDecodedThroughCompleteEof() throws Exception {
        Path input=root.resolve("target.gz"),geneticMap=root.resolve("map.gz");
        try(var out=new GZIPOutputStream(Files.newOutputStream(input))){out.write((HEADER+row("22","100","0/1","1|1")).getBytes(StandardCharsets.UTF_8));}
        try(var out=new GZIPOutputStream(Files.newOutputStream(geneticMap))){out.write(Files.readAllBytes(map()));}
        var p=prepare(input,geneticMap,"work",spec());assertEquals(1,p.selectedRows());assertTrue(p.input().gzip());assertTrue(p.stage().gzip());assertEquals(4,p.mapSummary().rows());assertTrue(p.map().getFileName().toString().endsWith(".gz"));
    }
    @Test void preparedMapSuffixFollowsProtectedEncodingBecauseOriginalEagleUsesTheSuffix() throws Exception {
        Path input=text("target.vcf",HEADER+row("22","100","0/1","1|1"));Path compressed=root.resolve("compressed-without-gz.data");Path plain=map();
        try(var out=new GZIPOutputStream(Files.newOutputStream(compressed))){out.write(Files.readAllBytes(plain));}
        var gzip=prepare(input,compressed,"gz-work",spec());assertTrue(gzip.map().getFileName().toString().endsWith(".txt.gz"));
        Path mislabeled=text("plain-with-gz.gz",Files.readString(plain));var uncompressed=prepare(input,mislabeled,"plain-work",spec());assertTrue(uncompressed.map().getFileName().toString().endsWith(".txt"));
        assertArrayEquals(Files.readAllBytes(compressed),Files.readAllBytes(gzip.map()));assertArrayEquals(Files.readAllBytes(mislabeled),Files.readAllBytes(uncompressed.map()));
    }
    @Test void partialMissingnessRequiresExplicitScientificPolicyAndReportsIgnoredAlleles() throws Exception {
        Path input=text("target.vcf",HEADER+row("22","100","0/.",".|1"));Path geneticMap=map();
        assertTrue(assertThrows(IOException.class,()->prepare(input,geneticMap,"refused",spec())).getMessage().contains("ALLOW_ORIGINAL"));
        assertTrue(Files.exists(root.resolve("refused/input-snapshots/target.vcf-source")));
        var s=spec("22",22,100,200,EagleCohortInputs.PartialMissing.ALLOW_ORIGINAL_WHOLE_CALL_MISSING,1000000,10000000);
        var p=prepare(input,geneticMap,"allowed",s);assertEquals(2,p.partiallyMissingCalls());assertEquals(2,p.observedAllelesIgnoredByInference());assertTrue(p.warnings().stream().anyMatch(w->w.contains("ignores")));
    }
    @Test void originalMutationAfterAdmissionInvalidatesPreparedRun() throws Exception {
        Path input=text("target.vcf",HEADER+row("22","100","0/1","1|1"));var p=prepare(input,map(),"work",spec());
        Files.writeString(input,Files.readString(input).replace("7:0/1","7:1/1"));assertTrue(assertThrows(IOException.class,()->p.verify(()->{})).getMessage().contains("original/snapshot"));
    }
    @Test void preparedVcfMutationInvalidatesPreparedRun() throws Exception {
        var p=prepare(text("target.vcf",HEADER+row("22","100","0/1","1|1")),map(),"work",spec());
        byte[] bytes=Files.readAllBytes(p.target());bytes[bytes.length-1]^=1;Files.write(p.target(),bytes);
        assertTrue(assertThrows(IOException.class,()->p.verify(()->{})).getMessage().contains("preparation changed"));
    }
    @Test void malformedMapTrailerCannotAdmitAValidPrefix() throws Exception {
        Path geneticMap=map();Files.writeString(geneticMap,Files.readString(geneticMap)+"BROKEN\n");
        Path input=text("target.vcf",HEADER+row("22","100","0/1","1|1"));assertThrows(IOException.class,()->prepare(input,geneticMap,"work",spec()));
    }
    @Test void unbracketedSelectedPositionIsRefusedBeforeExecution() throws Exception {
        Path input=text("target.vcf",HEADER+row("22","2000","0/1","1|1"));var s=spec("22",22,1,3000,EagleCohortInputs.PartialMissing.REFUSE,1000000,10000000);
        assertThrows(IOException.class,()->prepare(input,map(),"work",s));
    }
    @Test void explicitOriginalMapBoundaryPolicyRecordsExtrapolationAndRequiredAction() throws Exception {
        Path input=text("target.vcf",HEADER+row("22","2000","0/1","1|1"));var base=spec("22",22,1,3000,EagleCohortInputs.PartialMissing.REFUSE,1000000,10000000);
        var s=new EagleCohortInputs.Spec(base.contig(),base.chromosome(),base.chromosomeX(),base.start(),base.end(),base.genotypes(),base.partialMissing(),
                EagleGeneticMap.Outside.SOURCE_BOUNDARIES,base.mapLimits(),base.vcfLimits(),base.retainedBytes(),base.ledgerBytes(),base.workspaceBytes());
        var p=prepare(input,map(),"work",s);assertEquals(1,p.extrapolatedRows());assertTrue(p.warnings().stream().anyMatch(w->w.contains("MAP BOUNDARIES")&&w.contains("Required action")));
    }
    @Test void originalAllHaploidCohortXStorageRefusalIsPreserved() throws Exception {
        Path input=text("target.vcf",HEADER+row("X","100","1","0"));var s=spec("X",23,1,200,EagleCohortInputs.PartialMissing.REFUSE,1000000,10000000);
        assertTrue(assertThrows(IOException.class,()->prepare(input,map(),"work",s)).getMessage().contains("storage frame"));
    }
    @Test void invalidUnselectedRowStillPreventsCompleteInputAdmission() throws Exception {
        Path input=text("target.vcf",HEADER+row("22","100","0/1","1|1")+row("X","100","0/1/1","0/1"));
        assertThrows(IOException.class,()->prepare(input,map(),"work",spec()));
    }
    @Test void unselectedMultiallelicRowIsRetainedAndExcludedWithoutChangingIt() throws Exception {
        Path input=text("target.vcf",HEADER+row("22","100","0/1","1|1")+row("X","100","2/1","0/1").replace("\tA\tC\t","\tA\tC,G\t"));
        var p=prepare(input,map(),"work",spec());assertEquals(1,p.selectedRows());assertEquals(1,p.excludedRows());assertTrue(Files.readString(p.snapshots().get(0).copy()).contains("C,G"));
    }
    @Test void selectedMonomorphicAndMultiallelicRecordsRequireSeparateReviewedPreparation() throws Exception {
        Path geneticMap=map();for(String alt:new String[]{".","C,G"}) {
            Path input=text("target-"+alt.length()+".vcf",HEADER+row("22","100","0/0","0/0").replace("\tA\tC\t","\tA\t"+alt+"\t"));
            assertTrue(assertThrows(IOException.class,()->prepare(input,geneticMap,"work-"+alt.length(),spec())).getMessage().contains("biallelic"));
        }
    }
    @Test void ledgerByteCapacityFailsRatherThanTruncatesRows() throws Exception {
        Path input=text("target.vcf",HEADER+row("22","100","0/1","1|1"));var s=spec("22",22,100,200,EagleCohortInputs.PartialMissing.REFUSE,10,10000000);
        assertTrue(assertThrows(IOException.class,()->prepare(input,map(),"work",s)).getMessage().contains("byte capacity"));
    }
    @Test void completeWorkspaceByteCapacityIsEnforcedWhileProtectingSnapshots() throws Exception {
        Path input=text("target.vcf",HEADER+row("22","100","0/1","1|1"));var s=spec("22",22,100,200,EagleCohortInputs.PartialMissing.REFUSE,1000000,10);
        assertTrue(assertThrows(IOException.class,()->prepare(input,map(),"work",s)).getMessage().contains("workspace"));
    }
    @Test void existingOutputIsNeverOverwritten() throws Exception {
        Path work=Files.createDirectory(root.resolve("work"));Path sentinel=text("work/sentinel","keep");Path input=text("target.vcf",HEADER+row("22","100","0/1","1|1"));
        assertThrows(IOException.class,()->prepare(input,map(),"work",spec()));assertEquals("keep",Files.readString(sentinel));try(var files=Files.list(work)){assertEquals(1,files.count());}
    }
    @Test void cancelledPreparationCannotReturnAnAdmittedRun() throws Exception {
        Path input=text("target.vcf",HEADER+row("22","100","0/1","1|1"));Path geneticMap=map();var checks=new AtomicInteger();
        assertThrows(IllegalStateException.class,()->EagleCohortInputs.prepare(input,geneticMap,root.resolve("work"),spec(),()->{if(checks.incrementAndGet()>3)throw new IllegalStateException("cancel");}));
    }
    @Test void wrongLiteralContigOrSexClassCannotBeInferredFromLabels() {
        assertThrows(IllegalArgumentException.class,()->spec("chr23",22,1,200,EagleCohortInputs.PartialMissing.REFUSE,1000000,10000000));
        var s=spec();assertThrows(IllegalArgumentException.class,()->new EagleCohortInputs.Spec("22",22,23,1,200,
                new EagleGenotypes.Options(EagleGenotypes.Mode.COHORT,true,EagleGenotypes.Missing.KEEP,false),s.partialMissing(),s.outsideMap(),s.mapLimits(),s.vcfLimits(),1000000,1000000,10000000));
    }
    @Test void emptySelectedRegionDoesNotReturnAnAdmittedRun() throws Exception {
        Path input=text("target.vcf",HEADER+row("22","300","0/1","1|1"));assertTrue(assertThrows(IOException.class,()->prepare(input,map(),"work",spec())).getMessage().contains("nonempty"));
    }
}
