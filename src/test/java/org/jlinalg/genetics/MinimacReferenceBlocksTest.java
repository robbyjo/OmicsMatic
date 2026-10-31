package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MinimacReferenceBlocksTest {
    @TempDir Path directory;
    private static final MinimacReferenceBlocks.BoundaryPolicy KEEP = MinimacReferenceBlocks.BoundaryPolicy.VERIFY_SHARED_KEEP_LATER;
    private static final String V1_HEADER = "##fileformat=M3VCF\n##version=1.2\n##compression=block\n##n_blocks=2\n##n_haps=4\n##n_markers=3\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tA_h1\tA_h2\tB_h1\tB_h2\n";
    private static final String V1 = V1_HEADER
            + "1\t10-20\t<BLOCK:0-1>\t.\t.\t.\t.\tB1;VARIANTS=2;REPS=2\t.\t0\t1\t1\t0\n"
            + "1\t10\tv1\tA\tG\t.\t.\tB1.M1;Err=0.01;Recom=0.1\t01\n"
            + "1\t20\told-id\tC\tT\t.\t.\tB1.M2;Err=0.02;Recom=0.2\t10\n"
            + "1\t20-30\t<BLOCK:1-2>\t.\t.\t.\t.\tB2;VARIANTS=2;REPS=2\t.\t1\t0\t0\t1\n"
            + "1\t20\tnew-id\tC\tT\t.\t.\tB2.M1;Err=0.03;Recom=0.3\t01\n"
            + "1\t30\tv3\tT\tG\t.\t.\tB2.M2;Err=0.04;Recom=0\t11\n";
    private static final String V2 = "##fileformat=M3VCFv2.0\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tA\tB\n"
            + "1\t10-30\t<BLOCK>\t.\t.\t.\t.\tVARIANTS=3;REPS=2\t.\t0|1\t1|0\n"
            + "1\t10\tv1\tA\tG\t.\t.\tErr=0.01;Recom=0.1\t1\n"
            + "1\t20\tnew-id\tC\tT\t.\t.\tErr=0.03;Recom=0.3\t0\n"
            + "1\t30\tv3\tT\tG\t.\t.\tErr=0.04;Recom=0\t0,1\n";
    private static final String MVCF = "##fileformat=VCFv4.2\n##subfileformat=MVCFv3.0\n##phasing=full\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tA\tB\n"
            + "1\t10\tv1\tA\t<BLOCK>\t.\t.\tEND=30;VARIANTS=3;REPS=2\tUHM\t0,1\t1,0\n"
            + "1\t10\tv1\tA\tG\t.\t.\tAC=2;AN=4;UHA=0,1;ERR=0.01;RECOM=0.1\n"
            + "1\t20\tnew-id\tC\tT\t.\t.\tAC=2;AN=4;UHA=1,0;ERR=0.03;RECOM=0.3\n"
            + "1\t30\tv3\tT\tG\t.\t.\tAC=4;AN=4;UHA=1,1;ERR=0.04;RECOM=0\n";
    private Path file(String text) throws IOException { Path path=Files.createTempFile(directory,"reference-",".txt");Files.writeString(path,text);return path; }
    private MinimacReferenceBlocks.Summary audit(String text) throws IOException { return MinimacReferenceBlocks.audit(file(text),KEEP); }
    private void reject(String text) { assertThrows(IOException.class,()->audit(text)); }

    @Test void legacySharedBoundaryRetainsEveryPhysicalRowAndSelectsLaterIdentityAndParameters() throws Exception {
        var selected=new ArrayList<MinimacReferenceBlocks.Row>();var physical=new ArrayList<MinimacReferenceBlocks.Row>();var events=new ArrayList<MinimacReferenceBlocks.Boundary>();
        var s=MinimacReferenceBlocks.read(file(V1),KEEP,new MinimacReferenceBlocks.Visitor(){public void selected(MinimacReferenceBlocks.Row row){selected.add(row);}public void physical(MinimacReferenceBlocks.Row row){physical.add(row);}public void boundary(MinimacReferenceBlocks.Boundary event){events.add(event);}});
        assertEquals(4,s.physicalRows());assertEquals(3,s.selectedVariants());assertEquals(1,s.sharedBoundaries());assertEquals(0,s.disjointBoundaries());assertEquals(12,s.selectedHaplotypeCells());assertEquals(4,physical.size());assertEquals(List.of("v1","new-id","v3"),selected.stream().map(MinimacReferenceBlocks.Row::id).toList());assertEquals(.03,selected.get(1).error().orElseThrow());assertTrue(events.get(0).idChanged());assertTrue(events.get(0).parametersChanged());assertEquals("old-id",events.get(0).earlier().id());assertEquals("new-id",events.get(0).later().id());assertEquals(List.of(1),selected.get(1).phasedAlleles(0));assertEquals(MinimacReferenceBlocks.ColumnKind.HAPLOTYPE,s.columnKind());
    }
    @Test void threeFormatsHaveEqualOrderedMatricesAndEngineModelValuesWithDifferentExplicitColumnSemantics() throws Exception { var a=audit(V1);var b=audit(V2);var c=audit(MVCF);assertEquals(a.orderedHaplotypeMatrixSha256(),b.orderedHaplotypeMatrixSha256());assertEquals(b.orderedHaplotypeMatrixSha256(),c.orderedHaplotypeMatrixSha256());assertEquals(a.float32ModelParametersSha256(),c.float32ModelParametersSha256());assertEquals(b.float32ModelParametersSha256(),c.float32ModelParametersSha256());assertNotEquals(a.identityAndPhaseSha256(),b.identityAndPhaseSha256());assertEquals(b.identityAndPhaseSha256(),c.identityAndPhaseSha256());assertEquals(List.of(2,2),b.contigs().get(0).columnPloidies()); }
    @Test void representativeRenumberingPreservesAllThreeFingerprints() throws Exception {String text=MVCF.replace("UHM\t0,1\t1,0","UHM\t1,0\t0,1").replace("UHA=0,1","UHA=TEMP").replace("UHA=1,0","UHA=0,1").replace("UHA=TEMP","UHA=1,0");var a=audit(MVCF);var b=audit(text);assertEquals(a.identityAndPhaseSha256(),b.identityAndPhaseSha256());assertEquals(a.orderedHaplotypeMatrixSha256(),b.orderedHaplotypeMatrixSha256());assertEquals(a.float32ModelParametersSha256(),b.float32ModelParametersSha256());}
    @Test void phaseSwapChangesMatrixWithoutChangingAlleleCounts() throws Exception {assertNotEquals(audit(V2).orderedHaplotypeMatrixSha256(),audit(V2.replace("0|1\t1|0","1|0\t1|0")).orderedHaplotypeMatrixSha256());}
    @Test void strictDuplicatePolicyRefusesValidSharedBoundaryRatherThanSilentlyCollapsingIt() throws Exception {Path path=file(V1);assertThrows(IOException.class,()->MinimacReferenceBlocks.audit(path,MinimacReferenceBlocks.BoundaryPolicy.REJECT_DUPLICATES));}
    @Test void legacyConsumerCanRequireOverlapWhileCurrentConsumerAdmitsExplicitDisjointBlocks() throws Exception {String text=V1.replace("n_markers=3","n_markers=4").replace("1\t20-30","1\t21-30").replace("1\t20\tnew-id","1\t21\tnew-id");assertEquals(1,audit(text).disjointBoundaries());Path path=file(text);assertThrows(IOException.class,()->MinimacReferenceBlocks.audit(path,MinimacReferenceBlocks.BoundaryPolicy.REQUIRE_SHARED_KEEP_LATER));assertEquals(3,MinimacReferenceBlocks.audit(file(V1),MinimacReferenceBlocks.BoundaryPolicy.REQUIRE_SHARED_KEEP_LATER).selectedVariants());}
    @Test void conflictingBoundaryHaplotypesFailEvenWhenCountsRemainEqual() {reject(V1.replace("B2.M1;Err=0.03;Recom=0.3\t01","B2.M1;Err=0.03;Recom=0.3\t10"));}
    @Test void repeatedKeyInsideBlockIsNotACompressionBoundary() {reject(V2.replace("1\t20\tnew-id\tC\tT","1\t10\tnew-id\tA\tG"));}
    @Test void declaredGlobalCountsReferToUniqueMarkersRatherThanPhysicalOverlapRows() {reject(V1.replace("n_markers=3","n_markers=4"));reject(V1.replace("n_blocks=2","n_blocks=1"));reject(V1.replace("n_haps=4","n_haps=3"));}
    @Test void legacyBlockIndexSpanAndPositionExtentAreChecked() {reject(V1.replace("<BLOCK:0-1>","<BLOCK:0-2>"));reject(V1.replace("1\t10-20","1\t10-21"));reject(V1.replace("1\t10-20","1\t9-20"));}
    @Test void missingOrTruncatedBlocksProduceNoSuccessfulSummary() {reject(V1.substring(0,V1.lastIndexOf("1\t30")));reject(V2.replace("VARIANTS=3","VARIANTS=4"));reject(V1_HEADER);}
    @Test void v2CumulativeDeltaBoundsAndDuplicateIndicesAreValidatedBeforeUnsafeUpstreamConversion() {reject(V2.replace("\t0,1\n","\t1,1\n"));reject(V2.replace("\t0,1\n","\t0,0\n"));reject(V2.replace("\t0,1\n","\t2\n"));reject(V2.replace("\t0,1\n","\t-1\n"));}
    @Test void missingV2DeltasAreNeverTurnedIntoInventedAltAtZero() {for(String value:List.of("",".","-"))reject(V2.replace("\t0,1\n","\t"+value+"\n"));}
    @Test void v2NoncontiguousCumulativeIndicesExpandInOriginalColumnAndCopyOrder() throws Exception {
        String text=V2.replace("REPS=2","REPS=5").replace("0|1\t1|0","0|2\t3|4").replace("Recom=0.1\t1\n","Recom=0.1\t0,2,2\n").replace("Recom=0.3\t0\n","Recom=0.3\t1,2\n").replace("Recom=0\t0,1\n","Recom=0\t0,1,1,1,1\n");
        var rows=new ArrayList<MinimacReferenceBlocks.Row>();MinimacReferenceBlocks.read(file(text),KEEP,rows::add);assertEquals(List.of(1,0,1,0,1),rows.get(0).representativeAlleles());assertEquals(List.of(1,1),rows.get(0).phasedAlleles(0));assertEquals(List.of(0,1),rows.get(0).phasedAlleles(1));assertEquals(3,rows.get(0).alternateCopies());assertEquals(1,rows.get(1).alternateCopies());assertEquals(4,rows.get(2).alternateCopies());
    }
    @Test void incompletePolyploidOrOutOfRangeMappingsAreNotInferred() {reject(V2.replace("0|1\t1|0","0\t1|0"));reject(V2.replace("0|1\t1|0","0|1|0\t1|0"));reject(V2.replace("0|1\t1|0","0|2\t1|0"));reject(V1.replace(".\t0\t1\t1\t0\n",".\t.\t1\t1\t0\n"));}
    @Test void mvcfMixedPloidyUsesActualMappingsAndRejectsPaddedAn() throws Exception {String text=MVCF.replace("UHM\t0,1\t1,0","UHM\t0\t1,0").replace("AN=4","AN=3").replace("AC=4","AC=3").replace("AC=2;AN=3;UHA=0,1","AC=1;AN=3;UHA=0,1");assertEquals(9,audit(text).selectedHaplotypeCells());assertEquals(List.of(1,2),audit(text).contigs().get(0).columnPloidies());reject(text.replace("AN=3","AN=4"));}
    @Test void modelMissingnessIsDistinctFromExplicitZeroWithoutChangingGenotypes() throws Exception {var a=audit(V2);var b=audit(V2.replace("Err=0.01;Recom=0.1","OTHER=raw"));assertEquals(a.orderedHaplotypeMatrixSha256(),b.orderedHaplotypeMatrixSha256());assertNotEquals(a.float32ModelParametersSha256(),b.float32ModelParametersSha256());assertEquals(2,b.errorParameters());assertEquals(3,a.errorParameters());}
    @Test void parameterAliasesNonfiniteValuesAndProbabilityRangesAreStrict() {reject(V2.replace("Err=0.01","Err=0.01;ERR=0.01"));for(String value:List.of("NaN","Infinity","-0.01","1.01","oops"))reject(V2.replace("Err=0.01","Err="+value));reject(MVCF.replace("RECOM=0.1","RECOM=2"));}
    @Test void unknownInfoFlagsAndNumericTextArePreservedWithoutNewSemantics() throws Exception {var rows=new ArrayList<MinimacReferenceBlocks.Row>();MinimacReferenceBlocks.read(file(V2.replace("Err=0.01","TAG;OTHER=0.010000;Err=0.01")),KEEP,rows::add);assertEquals("0.010000",rows.get(0).info().get("OTHER"));assertEquals(List.of("TAG"),rows.get(0).flags());assertThrows(UnsupportedOperationException.class,()->rows.get(0).info().put("x","y"));}
    @Test void sourceM3CmTextIsPreservedButNotClaimedUsedByTheOriginalM3Reader() throws Exception {var rows=new ArrayList<MinimacReferenceBlocks.Row>();MinimacReferenceBlocks.read(file(V2.replace("Err=0.01","CM=1.2;Err=0.01")),KEEP,rows::add);assertEquals("1.2",rows.get(0).info().get("CM"));assertTrue(rows.get(0).centimorgans().isEmpty());assertEquals(1,audit(MVCF.replace("ERR=0.01","CM=1.2;ERR=0.01")).centimorganParameters());}
    @Test void sourceColumnNamesAreNotPairedByHaplotypeLookingSuffixes() throws Exception {var s=audit(V2.replace("FORMAT\tA\tB","FORMAT\todd_HAP_2\todd_HAP_1"));assertEquals(2,s.columns().size());assertEquals(MinimacReferenceBlocks.ColumnKind.SAMPLE,s.columnKind());assertEquals(List.of(2,2),s.contigs().get(0).columnPloidies());}
    @Test void duplicateIdsAndContradictoryHeadersFail() {reject(V2.replace("FORMAT\tA\tB","FORMAT\tA\tA"));reject("##fileformat=M3VCFv2.0\n"+V2);reject(V1.replace("##n_haps=4\n",""));reject(MVCF.replace("phasing=full","phasing=partial"));}
    @Test void contigsCannotReenterAndColumnPloidyCannotChangeWithinContig() {String body=MVCF.substring(MVCF.indexOf("1\t10\tv1\tA\t<BLOCK>"));reject(MVCF+body.replace("1\t","2\t")+body);reject(MVCF+body.replace("1\t10","1\t40").replace("1\t20","1\t50").replace("1\t30","1\t60").replace("END=30","END=60").replace("UHM\t0,1\t1,0","UHM\t0\t1,0"));}
    @Test void nonDnaAlleleLabelsRemainOpaqueAndAreCounted() throws Exception {var s=audit(V2.replace("\tv1\tA\tG\t","\tv1\tA\t<DEL>\t"));assertEquals(1,s.nonDnaAlleleRows());reject(V2.replace("\tv1\tA\tG\t","\tv1\tA\tG,T\t"));reject(V2.replace("\tv1\tA\tG\t","\tv1\t.\tG\t"));}
    @Test void mvcfExtentIncludesAlleleStringsWhileM3ExtentNamesLastVariantPosition() throws Exception {assertEquals(3,audit(V2.replace("\tv3\tT\tG\t","\tv3\tT\tGG\t")).selectedVariants());reject(MVCF.replace("\tv3\tT\tG\t","\tv3\tT\tGG\t"));assertEquals(3,audit(MVCF.replace("END=30","END=31").replace("\tv3\tT\tG\t","\tv3\tT\tGG\t")).selectedVariants());}
    @Test void gzipAndPlainHaveEqualFingerprintsWithExplicitNewlineInventory() throws Exception {Path compressed=directory.resolve("ordinary.gz");try(var out=new GZIPOutputStream(Files.newOutputStream(compressed))){out.write(V1.replace("\n","\r\n").getBytes(StandardCharsets.UTF_8));}var a=audit(V1);var b=MinimacReferenceBlocks.audit(compressed,KEEP);assertTrue(b.gzip());assertTrue(b.crlfLines()>0);assertTrue(b.finalLineHasLf());assertEquals(a.identityAndPhaseSha256(),b.identityAndPhaseSha256());assertFalse(audit(V1.stripTrailing()).finalLineHasLf());}
    @Test void concatenatedGzipMembersAreDecodedWithoutLosingBoundaryRows() throws Exception {Path compressed=directory.resolve("members.gz");byte[] first,second;try(var bytes=new java.io.ByteArrayOutputStream();var gzip=new GZIPOutputStream(bytes)){gzip.write(V1.substring(0,V1.indexOf("1\t20-30")).getBytes(StandardCharsets.UTF_8));gzip.finish();first=bytes.toByteArray();}try(var bytes=new java.io.ByteArrayOutputStream();var gzip=new GZIPOutputStream(bytes)){gzip.write(V1.substring(V1.indexOf("1\t20-30")).getBytes(StandardCharsets.UTF_8));gzip.finish();second=bytes.toByteArray();}try(var out=Files.newOutputStream(compressed)){out.write(first);out.write(second);}assertEquals(audit(V1).identityAndPhaseSha256(),MinimacReferenceBlocks.audit(compressed,KEEP).identityAndPhaseSha256());}
    @Test void malformedUtf8AndCorruptCompressedDataFailAndReleaseTheirFiles() throws Exception {Path invalid=directory.resolve("invalid.txt");Files.write(invalid,new byte[]{(byte)0xff});assertThrows(IOException.class,()->MinimacReferenceBlocks.audit(invalid,KEEP));Files.delete(invalid);Path gzip=directory.resolve("broken.gz");Files.write(gzip,new byte[]{31,(byte)139,0,0});assertThrows(IOException.class,()->MinimacReferenceBlocks.audit(gzip,KEEP));Files.delete(gzip);}
    @Test void consumersCanAbortWithoutSuccessfulSummaryAndNoInputMutation() throws Exception {Path path=file(V1);byte[] before=Files.readAllBytes(path);IOException sentinel=new IOException("reject downstream");assertSame(sentinel,assertThrows(IOException.class,()->MinimacReferenceBlocks.read(path,KEEP,row->{throw sentinel;})));assertArrayEquals(before,Files.readAllBytes(path));Files.delete(path);}
    @Test void returnedColumnAndMappingInventoriesAreImmutable() throws Exception {var rows=new ArrayList<MinimacReferenceBlocks.Row>();var s=MinimacReferenceBlocks.read(file(V2),KEEP,rows::add);assertThrows(UnsupportedOperationException.class,()->s.columns().add("x"));assertThrows(UnsupportedOperationException.class,()->rows.get(0).block().mappings().get(0).set(0,1));assertThrows(UnsupportedOperationException.class,()->rows.get(0).representativeAlleles().set(0,1));}
    @Test void emptyBlankControlAndUnsupportedBinaryHeadersFail() {reject("");reject(V2+"\n");reject(V2.replace("Err=0.01","OTHER=raw\u0001;Err=0.01"));reject(V2.replace("new-id","new\u0000id"));reject(V2.replace("M3VCFv2.0","M3VCFv9.0"));reject(V2.replace("M3VCFv2.0","SAV"));}
    @Test void controlsAreRejectedAcrossMetadataAndIgnoredFieldsWithTheCorrectSourceLine() throws Exception {
        for(String text:List.of(V2.replace("##fileformat=M3VCFv2.0","##fileformat=M3VCFv2.0\n##unused=bad\u0001"),V2.replace("\tv1\tA\tG\t.\t.","\tv1\tA\tG\t\u0007\t.")))reject(text);
        Path path=file(V2.replace("new-id","new\u0000id"));assertTrue(assertThrows(IOException.class,()->MinimacReferenceBlocks.audit(path,KEEP)).getMessage().contains("line 5:"));
    }
    @Test void originalMvcfAliasIsRecognizedWithoutChangingMatrixMeaning() throws Exception {assertEquals(audit(MVCF).orderedHaplotypeMatrixSha256(),audit(MVCF.replace("subfileformat=MVCFv3.0","subfileformat=M3VCFv3.0")).orderedHaplotypeMatrixSha256());}
}
