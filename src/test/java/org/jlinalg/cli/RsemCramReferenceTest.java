package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipInputStream;
import org.jlinalg.sequence.RsemAbundance;
import org.jlinalg.sequence.RsemReference;
import org.jlinalg.sequence.RsemSamAlignments;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RsemCramReferenceTest {
    @TempDir Path temporary;
    private Path source, fasta, fai, alignment;
    private static final RsemInputs.Limits INPUTS = new RsemInputs.Limits(5000000, 50000000, 100, 1000000,
            new RsemReference.Limits(100, 100, 1000000, 10000, 5000000, 1000000));
    private static final RsemCramReference.Limits LIMITS = new RsemCramReference.Limits(100, 100000, 10000, 100000, 100000, 100000);
    private static final RsemSamAlignments.Options OPTIONS = new RsemSamAlignments.Options(RsemAbundance.Release.V1_3_3, 3, "", 25);
    @BeforeEach void sources() throws Exception {
        temporary = temporary.toRealPath(); source = Files.createDirectory(temporary.resolve("source α"));
        try (var stream = getClass().getResourceAsStream("/org/jlinalg/sequence/rsem-reference/reference-model-fixtures.zip")) {
            assertNotNull(stream);
            try (var zip = new ZipInputStream(stream)) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) if (entry.getName().startsWith("1.3.3/polyA/reference.")) {
                    String name = entry.getName().substring("1.3.3/polyA/".length()); assertFalse(name.contains("/"));
                    byte[] bytes = zip.readNBytes(5000001); assertTrue(bytes.length < 5000001); Files.write(source.resolve(name), bytes);
                }
            }
        }
        alignment = source.resolve("reads.cram"); Files.write(alignment, new byte[]{'C','R','A','M',3,0});
        fasta = source.resolve("local reference.fa"); fai = source.resolve("local reference.fa.fai");
        Files.writeString(fasta, ">x desc\nacgt\nnn\n>y\nTT\n", StandardCharsets.UTF_8);
        Files.writeString(fai, "x\t6\t8\t4\t5\ny\t2\t19\t2\t3\n", StandardCharsets.UTF_8);
    }
    private RsemInputs.Prepared prepare() throws IOException {
        return RsemInputs.prepare(new RsemInputs.Spec(source.resolve("reference"), alignment, fasta, OPTIONS), temporary.resolve("owned"), INPUTS, () -> { });
    }
    private RsemCramReference.Reference audit() throws IOException { return RsemCramReference.audit(prepare(), LIMITS, () -> { }); }
    private Path header(String text) throws IOException { Path path = temporary.resolve("header.sam"); Files.writeString(path, text, StandardCharsets.UTF_8); return path; }
    private String x() { return "@SQ\tSN:x\tLN:6\tM5:247326f3ddab5b675f000e844a6dde4b"; }

    @Test void completeFastaAndLiteralFaiBindActualOffsetsAndSequenceHashes() throws Exception {
        var reference = audit(); reference.verify(() -> { }); assertEquals(2, reference.sequences().size());
        var x = reference.sequences().get("x"); assertEquals(6, x.length());
        assertEquals("247326f3ddab5b675f000e844a6dde4b", x.md5());
        assertEquals("bfe319fabdfbf2d6076d3ad9781a2771cffec5a0a5fda8739c6fca8f323982c0", x.sha256());
        assertEquals("df1f3edb9115acb0a1e04209b7a9937b", reference.sequences().get("y").md5());
        assertArrayEquals(Files.readAllBytes(fasta), Files.readAllBytes(reference.fasta()));
    }
    @Test void actualCrlfWidthsMustMatchTheSuppliedIndex() throws Exception {
        Files.writeString(fasta, ">x\r\nACGT\r\nNN\r\n", StandardCharsets.US_ASCII);
        Files.writeString(fai, "x\t6\t4\t4\t6\n", StandardCharsets.US_ASCII);
        assertEquals(6, audit().sequences().get("x").length());
    }
    @Test void wrongOffsetsAreRejectedBeforeAnyDecoderIsLaunched() throws Exception {
        Files.writeString(fai, "x\t6\t9\t4\t5\ny\t2\t19\t2\t3\n");
        assertThrows(IOException.class, this::audit); assertFalse(Files.exists(temporary.resolve("decode")));
    }
    @Test void wrongLineWidthsAreNotTreatedAsEquivalentIndexes() throws Exception {
        Files.writeString(fai, "x\t6\t8\t4\t6\ny\t2\t19\t2\t3\n"); assertThrows(IOException.class, this::audit);
    }
    @Test void duplicateIndexNamesAreRefused() throws Exception {
        Files.writeString(fai, "x\t6\t8\t4\t5\nx\t2\t19\t2\t3\n"); assertThrows(IOException.class, this::audit);
    }
    @Test void extraIndexColumnsAreNotSilentlyIgnored() throws Exception {
        Files.writeString(fai, "x\t6\t8\t4\t5\textra\ny\t2\t19\t2\t3\n"); assertThrows(IOException.class, this::audit);
    }
    @Test void missingIndexPopulationCannotPassCompleteComparison() throws Exception {
        Files.writeString(fai, "x\t6\t8\t4\t5\n"); assertThrows(IOException.class, this::audit);
    }
    @Test void allSequencesAndBasesCountTowardExplicitCapacities() throws Exception {
        var prepared = prepare();
        assertThrows(IOException.class, () -> RsemCramReference.audit(prepared, new RsemCramReference.Limits(1, 10000, 1000, 1000, 10000, 10000), () -> { }));
        assertThrows(IOException.class, () -> RsemCramReference.audit(prepared, new RsemCramReference.Limits(100, 5, 1000, 1000, 10000, 10000), () -> { }));
    }
    @Test void metadataLineCapStopsAnOversizedIndexBeforeRetainedMapConstruction() throws Exception {
        Files.writeString(fai, "x".repeat(1000) + "\t6\t8\t4\t5\n"); var prepared = prepare();
        assertThrows(IOException.class, () -> RsemCramReference.audit(prepared, new RsemCramReference.Limits(100, 10000, 100, 10000, 10000, 10000), () -> { }));
    }
    @Test void malformedUtf8DoesNotNormalizeReferenceIdentity() throws Exception {
        Files.write(fasta, new byte[]{'>','x',(byte)255,'\n','A','C','G','T','\n'}); assertThrows(IOException.class, this::audit);
    }
    @Test void repeatedFastaSequencesCannotPass() throws Exception {
        Files.writeString(fasta, ">x\nACGT\n>x\nNN\n"); assertThrows(IOException.class, this::audit);
    }
    @Test void emptyFastaSequenceCannotPass() throws Exception {
        Files.writeString(fasta, ">x\n>y\nTT\n"); assertThrows(IOException.class, this::audit);
    }
    @Test void matchingHeaderRetainsRemoteUriWithoutOpeningIt() throws Exception {
        var reference = audit(); String uri = "http://127.0.0.1:9/never-fetch-this-reference";
        var result = RsemCramReference.header(header("@HD\tVN:1.6\n" + x() + "\tUR:" + uri + "\n"), reference, () -> { });
        assertEquals(1, result.targets().size()); assertEquals(uri, result.targets().get(0).uri()); assertTrue(result.warnings().isEmpty());
    }
    @Test void wrongHeaderMd5AndMissingLocalTargetCannotUseUriFallback() throws Exception {
        var reference = audit();
        assertThrows(IOException.class, () -> RsemCramReference.header(header(x().replace("247326f3ddab5b675f000e844a6dde4b", "00000000000000000000000000000000") + "\tUR:http://127.0.0.1:9/reference\n"), reference, () -> { }));
        assertThrows(IOException.class, () -> RsemCramReference.header(header("@SQ\tSN:absent\tLN:10\tUR:http://127.0.0.1:9/reference\n"), reference, () -> { }));
    }
    @Test void headerLengthDisagreementRemainsExplicitForThePerRecordDecoderAudit() throws Exception {
        var reference = audit(); var result = RsemCramReference.header(header(x().replace("LN:6", "LN:5") + "\n"), reference, () -> { });
        assertEquals(5, result.targets().get(0).headerLength()); assertEquals(6, result.targets().get(0).fastaLength());
        assertTrue(result.warnings().stream().anyMatch(w -> w.startsWith("HEADER_FASTA_LENGTH_DISAGREEMENT:")));
    }
    @Test void missingMd5IsRetainedAsAProvenanceWarning() throws Exception {
        var reference = audit(); var result = RsemCramReference.header(header("@SQ\tSN:x\tLN:6\n"), reference, () -> { });
        assertTrue(result.warnings().stream().anyMatch(w -> w.startsWith("MISSING_HEADER_M5:")));
    }
    @Test void duplicateLookupFieldsAndBodyRecordsAreRefused() throws Exception {
        var reference = audit();
        assertThrows(IOException.class, () -> RsemCramReference.header(header(x() + "\tSN:x\n"), reference, () -> { }));
        assertThrows(IOException.class, () -> RsemCramReference.header(header(x() + "\nread\t4\t*\n"), reference, () -> { }));
    }
    @Test void changedProtectedReferenceIsRefusedBeforeHeaderAuditing() throws Exception {
        var reference = audit(); Files.writeString(reference.fasta(), ">x\nAAAAAA\n>y\nTT\n");
        assertThrows(IOException.class, () -> RsemCramReference.header(header(x() + "\n"), reference, () -> { }));
    }
    @Test void cancellationNeverReturnsReferenceAdmission() throws Exception {
        var prepared = prepare(); assertThrows(java.util.concurrent.CancellationException.class, () -> RsemCramReference.audit(prepared, LIMITS,
                () -> { throw new java.util.concurrent.CancellationException("cancel"); }));
    }
}
