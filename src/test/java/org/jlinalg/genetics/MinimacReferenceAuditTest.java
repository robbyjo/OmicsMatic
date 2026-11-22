package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MinimacReferenceAuditTest {
    @TempDir Path directory;
    private static final String HEADER = "##subfileformat=MVCFv3.0\n##fileformat=VCFv4.2\n##phasing=full\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tA\tB\n";
    private static final String BLOCK = "X\t100\tv1\tA\t<BLOCK>\t.\t.\tEND=110;VARIANTS=2;REPS=2\tUHM\t0\t1,0\n";
    private static final String FIRST = "X\t100\tv1\tA\tG\t.\t.\tAC=1;AN=3;UHA=0,1\n";
    private static final String LAST = "X\t110\tv2\tT\tC\t.\t.\tAC=2;AN=3;UHA=1,0;ERR=0.01;RECOM=0;CM=1.2\n";
    private static final String VALID = HEADER + BLOCK + FIRST + LAST;
    private Path file(String content) throws IOException { Path path = Files.createTempFile(directory, "reference-", ".mvcf"); Files.writeString(path, content); return path; }
    private void rejected(String content) { assertThrows(IOException.class, () -> MinimacReferenceAudit.audit(file(content))); }

    @Test void mixedPloidyExpansionAndMomentsAreExact() throws Exception {
        var rows = new ArrayList<MinimacReferenceAudit.Variant>();
        var s = MinimacReferenceAudit.read(file(VALID), rows::add);
        assertEquals(List.of("A", "B"), s.samples()); assertEquals(1, s.blocks()); assertEquals(2, s.variants()); assertEquals(6, s.haplotypeCells());
        assertEquals(List.of(1, 2), s.contigs().get(0).samplePloidies()); assertEquals(1, s.errorParameters()); assertEquals(1, s.recombinationParameters());
        assertEquals(List.of(0), rows.get(0).phasedAlleles(0)); assertEquals(List.of(1, 0), rows.get(0).phasedAlleles(1));
        assertEquals(List.of(1), rows.get(1).phasedAlleles(0)); assertEquals(List.of(0, 1), rows.get(1).phasedAlleles(1));
        assertTrue(rows.get(0).error().isEmpty()); assertEquals(.01, rows.get(1).error().orElseThrow()); assertEquals(0, rows.get(1).recombination().orElseThrow()); assertEquals(1.2, rows.get(1).centimorgans().orElseThrow());
    }
    @Test void immutableMappingsAndSummaryPreventPostAuditMutation() throws Exception { var rows = new ArrayList<MinimacReferenceAudit.Variant>(); var s = MinimacReferenceAudit.read(file(VALID), rows::add); assertThrows(UnsupportedOperationException.class, () -> s.samples().add("C")); assertThrows(UnsupportedOperationException.class, () -> s.contigs().get(0).samplePloidies().set(0,2)); assertThrows(UnsupportedOperationException.class, () -> rows.get(0).block().sampleMappings().get(0).set(0,1)); assertThrows(UnsupportedOperationException.class, () -> rows.get(0).uniqueAlleles().set(0,1)); }
    @Test void equivalentRepresentativePermutationPreservesContentHash() throws Exception { String swapped = VALID.replace("UHM\t0\t1,0", "UHM\t1\t0,1").replace("UHA=0,1", "UHA=TEMP").replace("UHA=1,0", "UHA=0,1").replace("UHA=TEMP", "UHA=1,0"); assertEquals(MinimacReferenceAudit.audit(file(VALID)).phasedContentSha256(), MinimacReferenceAudit.audit(file(swapped)).phasedContentSha256()); }
    @Test void phaseOrderChangeChangesFingerprintWithoutChangingAlleleCounts() throws Exception { assertNotEquals(MinimacReferenceAudit.audit(file(VALID)).phasedContentSha256(), MinimacReferenceAudit.audit(file(VALID.replace("UHM\t0\t1,0", "UHM\t0\t0,1"))).phasedContentSha256()); }
    @Test void variantIdentityChangeIsVisibleInFingerprint() throws Exception { assertNotEquals(MinimacReferenceAudit.audit(file(VALID)).phasedContentSha256(), MinimacReferenceAudit.audit(file(VALID.replace("v2", "other"))).phasedContentSha256()); }
    @Test void contigSpecificPloidyMayChangeAcrossExplicitContigs() throws Exception { var s = MinimacReferenceAudit.audit(file(VALID + (BLOCK+FIRST+LAST).replace("X\t", "Y\t").replace("UHM\t0\t1,0", "UHM\t0,0\t1").replace("AC=2;AN=3", "AC=2;AN=3"))); assertEquals(2, s.contigs().size()); assertEquals(List.of(2,1), s.contigs().get(1).samplePloidies()); }
    @Test void multipleBlocksKeepExactCountsAndPloidy() throws Exception { var s = MinimacReferenceAudit.audit(file(VALID + (BLOCK+FIRST+LAST).replace("100", "200").replace("110", "210"))); assertEquals(2, s.blocks()); assertEquals(4, s.variants()); assertEquals(12, s.haplotypeCells()); }
    @Test void fullPhasingIsRequired() { rejected(VALID.replace("phasing=full", "phasing=partial")); rejected(VALID.replace("##phasing=full\n", "")); }
    @Test void ordinaryVcfAndLegacyM3AreNotMisreadAsMvcf3() { rejected(VALID.replace("MVCFv3.0", "M3VCFv2.0")); rejected(VALID.replace("##subfileformat=MVCFv3.0\n", "")); }
    @Test void repeatedFormatMetadataIsRejected() { rejected("##phasing=full\n"+VALID); }
    @Test void duplicateSamplesAreRejected() { rejected(VALID.replace("FORMAT\tA\tB", "FORMAT\tA\tA")); }
    @Test void missingOrPolyploidMappingsAreNotFilledOrTruncated() { rejected(VALID.replace("UHM\t0\t1,0", "UHM\t.\t1,0")); rejected(VALID.replace("UHM\t0\t1,0", "UHM\t0,1,0\t1,0")); }
    @Test void mappingCannotAddressOutsideDeclaredRepresentatives() { rejected(VALID.replace("UHM\t0\t1,0", "UHM\t2\t1,0")); rejected(VALID.replace("UHM\t0\t1,0", "UHM\t-1\t1,0")); }
    @Test void referenceAllelesMustBeCompleteBiallelicBits() { rejected(VALID.replace("UHA=0,1", "UHA=0,.")); rejected(VALID.replace("UHA=0,1", "UHA=0,2")); rejected(VALID.replace("UHA=0,1", "UHA=0")); }
    @Test void expandedAlleleCountsMustAgree() { rejected(VALID.replace("AC=1;AN=3", "AC=2;AN=3")); rejected(VALID.replace("AC=1;AN=3", "AC=1;AN=4")); }
    @Test void parametersCannotBeNonfiniteOrOutOfRange() { for(String parameter : List.of("ERR=-0.1", "ERR=1.1", "ERR=NaN", "ERR=Infinity")) rejected(VALID.replace("ERR=0.01", parameter)); rejected(VALID.replace("RECOM=0", "RECOM=-1")); rejected(VALID.replace("CM=1.2", "CM=NaN")); }
    @Test void missingParametersRemainExplicitlyAbsent() throws Exception { var rows = new ArrayList<MinimacReferenceAudit.Variant>(); MinimacReferenceAudit.read(file(VALID.replace("ERR=0.01", "ERR=.").replace("RECOM=0", "RECOM=.")), rows::add); assertTrue(rows.get(1).error().isEmpty()); assertTrue(rows.get(1).recombination().isEmpty()); }
    @Test void truncatedAndOverfullBlocksAreRejected() { rejected(HEADER+BLOCK+FIRST); rejected(VALID.replace("VARIANTS=2", "VARIANTS=1")); rejected(VALID.replace("VARIANTS=2", "VARIANTS=3")); }
    @Test void endMustReflectLiteralAlleleExtent() { rejected(VALID.replace("END=110", "END=111")); rejected(VALID.replace("END=110", "END=109")); rejected(VALID.replace("\tv2\tT\tC\t", "\tv2\tTT\tC\t")); }
    @Test void insertionExtentIsAdmittedWithoutAlleleRewriting() throws Exception { assertEquals(2, MinimacReferenceAudit.audit(file(VALID.replace("END=110", "END=111").replace("\tv2\tT\tC\t", "\tv2\tT\tCC\t"))).variants()); }
    @Test void variantsCannotMoveOutsideTheirBlockOrContig() { rejected(VALID.replace("X\t110\tv2", "Y\t110\tv2")); rejected(VALID.replace("X\t110\tv2", "X\t99\tv2")); }
    @Test void duplicateKeysAreNotSilentlyCollapsed() { rejected(VALID.replace("END=110", "END=100").replace("X\t110\tv2\tT\tC", "X\t100\tv2\tA\tG")); }
    @Test void samePositionWithDistinctAllelesIsRetained() throws Exception { assertEquals(2, MinimacReferenceAudit.audit(file(VALID.replace("END=110", "END=100").replace("X\t110\tv2", "X\t100\tv2"))).variants()); }
    @Test void contigReentryAndWithinContigPloidyChangesAreRejected() { rejected(VALID + (BLOCK+FIRST+LAST).replace("X\t", "Y\t") + BLOCK+FIRST+LAST); rejected(VALID + (BLOCK+FIRST+LAST).replace("100", "200").replace("110", "210").replace("UHM\t0\t1,0", "UHM\t0,0\t1")); }
    @Test void malformedOrDuplicateInfoDoesNotPickAnArbitraryValue() { rejected(VALID.replace("AC=1;AN=3", "AC=1;AC=2;AN=3")); rejected(VALID.replace("AC=1;AN=3", "AC=1;FLAG;AN=3")); }
    @Test void symbolicAndMultiallelicRowsRequireExplicitPreparation() { rejected(VALID.replace("\tv2\tT\tC\t", "\tv2\tT\t<DEL>\t")); rejected(VALID.replace("\tv2\tT\tC\t", "\tv2\tT\tC,G\t")); }
    @Test void missingSamplesBlankRowsAndEmbeddedControlsAreRejected() { rejected(VALID.replace("FORMAT\tA\tB", "FORMAT\tA\t")); rejected(VALID+"\n"); rejected(VALID.replace("v2", "v\u0000x")); }
    @Test void consumerFailureIsPropagatedAndNoSummaryIsReturned() throws Exception { Path path=file(VALID); IOException sentinel=new IOException("downstream refusal"); assertSame(sentinel, assertThrows(IOException.class, () -> MinimacReferenceAudit.read(path, v -> {throw sentinel;}))); }
    @Test void emptyReferenceIsRejected() { rejected(HEADER); }
}
