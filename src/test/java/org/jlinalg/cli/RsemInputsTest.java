package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipInputStream;
import org.jlinalg.sequence.RsemAbundance;
import org.jlinalg.sequence.RsemReference;
import org.jlinalg.sequence.RsemSamAlignments;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

final class RsemInputsTest {
    @TempDir Path temporary;
    private Path source, alignment;
    private static final RsemInputs.Limits LIMITS = new RsemInputs.Limits(5000000, 50000000, 100, 1000000,
            new RsemReference.Limits(100, 100, 1000000, 10000, 5000000, 1000000));
    private static final RsemSamAlignments.Options OPTIONS = new RsemSamAlignments.Options(RsemAbundance.Release.V1_3_3, 3, "", 25);
    @BeforeEach void originalFiles() throws Exception {
        temporary = temporary.toRealPath(); source = Files.createDirectory(temporary.resolve("original inputs α"));
        unpackReference("polyA");
        alignment = source.resolve("reads.bam"); Files.write(alignment, new byte[]{31, (byte)139, 8, 0, 1, 2, 3, 4});
    }
    private void unpackReference(String kind) throws Exception {
        try (var stream = getClass().getResourceAsStream("/org/jlinalg/sequence/rsem-reference/reference-model-fixtures.zip")) {
            assertNotNull(stream);
            try (var zip = new ZipInputStream(stream)) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (!entry.getName().startsWith("1.3.3/" + kind + "/reference.")) continue;
                    String name = entry.getName().substring(("1.3.3/" + kind + "/").length());
                    assertFalse(name.contains("/"));
                    byte[] raw = zip.readNBytes(5000001); assertTrue(raw.length < 5000001);
                    Files.write(source.resolve(name), raw);
                }
            }
        }
    }
    private RsemInputs.Spec spec() { return new RsemInputs.Spec(source.resolve("reference"), alignment, null, OPTIONS); }
    private RsemInputs.Prepared prepare() throws IOException { return RsemInputs.prepare(spec(), temporary.resolve("owned"), LIMITS, () -> { }); }

    @Test void snapshotsExactBinaryBytesAndCompleteReferenceWithoutAdmittingAlignmentOrFit() throws Exception {
        var prepared = prepare(); prepared.verify(() -> { });
        assertArrayEquals(Files.readAllBytes(alignment), Files.readAllBytes(prepared.alignment()));
        assertEquals(1, prepared.reference().type()); assertFalse(prepared.reference().transcripts().isEmpty());
        for (var evidence : prepared.reference().evidence()) assertTrue(Path.of(evidence.path()).startsWith(prepared.home()));
        MapAssertions.notAdmitted(prepared.home().resolve("inputs.json"));
        for (var entry : prepared.sourcePins().entrySet()) assertEquals(Files.size(entry.getKey()), entry.getValue().bytes());
        assertTrue(prepared.absentSources().contains(source.resolve("reference.chrlist")));
    }
    @Test void changedOriginalAlignmentRefusesEvenWhenProtectedCopyIsUnchanged() throws Exception {
        var prepared = prepare(); byte[] original = Files.readAllBytes(alignment); original[4] ^= 1; Files.write(alignment, original);
        assertThrows(IOException.class, () -> prepared.verify(() -> { }));
    }
    @Test void changedOriginalReferenceCompanionRefusesEvenWhenNativeMetadataStillParses() throws Exception {
        var prepared = prepare(); Files.writeString(source.resolve("reference.idx.fa"), "\n", StandardOpenOption.APPEND);
        assertThrows(IOException.class, () -> prepared.verify(() -> { }));
    }
    @Test void changedStagedInputOrManifestRefuses() throws Exception {
        var prepared = prepare(); Files.writeString(prepared.home().resolve("inputs.json"), "\n", StandardOpenOption.APPEND);
        assertThrows(IOException.class, () -> prepared.verify(() -> { }));
    }
    @Test void changedStagedAlignmentBytesRefuseEvenWhenOriginalIsUnchanged() throws Exception {
        var prepared = prepare(); byte[] bytes = Files.readAllBytes(prepared.alignment()); bytes[4] ^= 1;
        Files.write(prepared.alignment(), bytes);
        assertThrows(IOException.class, () -> prepared.verify(() -> { }));
    }
    @Test void parsedReferenceFromChangedThenRestoredBytesCannotBindToTheProtectedCopy() throws Exception {
        var prepared = prepare(); Path seq = prepared.referencePrefix().resolveSibling("reference.seq");
        byte[] original = Files.readAllBytes(seq); var lines = new String(original, StandardCharsets.UTF_8).split("\n", -1);
        lines[2] = (lines[2].charAt(0) == 'A' ? "C" : "A") + lines[2].substring(1);
        Files.writeString(seq, String.join("\n", lines), StandardCharsets.UTF_8);
        var changed = RsemReference.read(prepared.referencePrefix(), LIMITS.reference());
        Files.write(seq, original); prepared.verify(() -> { });
        assertThrows(IOException.class, () -> RsemInputs.bindReferenceEvidence(changed, prepared.spec().referencePrefix(), prepared.referencePrefix(), prepared.sourcePins()));
        RsemInputs.bindReferenceEvidence(prepared.reference(), prepared.spec().referencePrefix(), prepared.referencePrefix(), prepared.sourcePins());
    }
    @Test void genomeReferenceRequiresAndProtectsItsCompleteChromosomeDictionary() throws Exception {
        unpackReference("genome"); var prepared = prepare();
        assertEquals(0, prepared.reference().type()); assertFalse(prepared.reference().chromosomes().isEmpty());
        assertTrue(prepared.sourcePins().containsKey(source.resolve("reference.chrlist")));
        Files.writeString(source.resolve("reference.chrlist"), "\n", StandardOpenOption.APPEND);
        assertThrows(IOException.class, () -> prepared.verify(() -> { }));
    }
    @Test void unexpectedEmptyDirectoryAndNewFilesAreNotHiddenFromMembershipChecks() throws Exception {
        var prepared = prepare(); Files.createDirectory(prepared.home().resolve("extra"));
        assertThrows(IOException.class, () -> prepared.verify(() -> { }));
    }
    @Test void newlyAppearingAlleleOrOptionalReferenceCompanionRefuses() throws Exception {
        var prepared = prepare(); Files.writeString(source.resolve("reference.gt"), "unreviewed");
        assertThrows(IOException.class, () -> prepared.verify(() -> { }));
    }
    @Test void alleleSpecificSourceRefusesBeforeCreatingAnOwnedDirectory() throws Exception {
        Files.writeString(source.resolve("reference.ta"), "unreviewed");
        assertThrows(IOException.class, this::prepare); assertFalse(Files.exists(temporary.resolve("owned")));
    }
    @Test void incompleteOrInconsistentReferenceCannotProduceSnapshotSuccess() throws Exception {
        Files.writeString(source.resolve("reference.grp"), "1\n999999\n");
        assertThrows(IOException.class, this::prepare);
        assertFalse(Files.exists(temporary.resolve("owned/inputs.json")));
        assertTrue(Files.readString(temporary.resolve("owned/INPUTS_FAILED.txt")).contains("Required action"));
    }
    @Test void sourceMutationDuringExactCopyIsDetectedAndRetainedWithoutCompletion() throws Exception {
        var changed = new AtomicBoolean(); Path home = temporary.resolve("owned");
        Runnable mutate = () -> {
            if (Files.exists(home.resolve("alignment/reads.input")) && changed.compareAndSet(false, true)) {
                try { Files.write(alignment, new byte[]{31, (byte)139, 8, 0, 9, 9, 9, 9}); }
                catch (IOException failure) { throw new AssertionError(failure); }
            }
        };
        assertThrows(IOException.class, () -> RsemInputs.prepare(spec(), home, LIMITS, mutate));
        assertTrue(changed.get()); assertFalse(Files.exists(home.resolve("inputs.json")));
        assertTrue(Files.isRegularFile(home.resolve("INPUTS_FAILED.txt")));
    }
    @Test void completeCapacityFailureRetainsOriginalAndPublishesNoSuccess() throws Exception {
        byte[] large = new byte[20000]; Arrays.fill(large, (byte)'A'); Files.write(alignment, large);
        var limits = new RsemInputs.Limits(8192, 500000, 100, 100000, LIMITS.reference());
        assertThrows(IOException.class, () -> RsemInputs.prepare(spec(), temporary.resolve("owned"), limits, () -> { }));
        assertArrayEquals(large, Files.readAllBytes(alignment)); assertFalse(Files.exists(temporary.resolve("owned/inputs.json")));
    }
    @Test void existingOwnedDirectoryIsPreserved() throws Exception {
        Path owned = Files.createDirectory(temporary.resolve("owned")); Files.writeString(owned.resolve("keep"), "original");
        assertThrows(IOException.class, this::prepare); assertEquals("original", Files.readString(owned.resolve("keep")));
    }
    @Test void cramRequiresExplicitLocalReferenceAndDoesNotDownloadOrInventOne() throws Exception {
        Files.write(alignment, new byte[]{'C','R','A','M',3,0});
        var failure = assertThrows(RsemProcess.UnavailableDependency.class, this::prepare);
        assertTrue(failure.getMessage().contains("FASTA and .fai")); assertFalse(Files.exists(temporary.resolve("owned/inputs.json")));
    }
    @Test void suppliedCramReferenceAndIndexAreBothProtectedWithoutClaimingBinaryDecoding() throws Exception {
        Files.write(alignment, new byte[]{'C','R','A','M',3,0});
        Path fasta = source.resolve("local.fa"); Files.writeString(fasta, ">x\nACGT\n", StandardCharsets.US_ASCII);
        Files.writeString(source.resolve("local.fa.fai"), "x\t4\t3\t4\t5\n", StandardCharsets.US_ASCII);
        var prepared = RsemInputs.prepare(new RsemInputs.Spec(spec().referencePrefix(), alignment, fasta, OPTIONS), temporary.resolve("owned"), LIMITS, () -> { });
        assertArrayEquals(Files.readAllBytes(fasta), Files.readAllBytes(prepared.cramFasta()));
        MapAssertions.notAdmitted(prepared.home().resolve("inputs.json"));
        Files.writeString(source.resolve("local.fa.fai"), "x\t5\t3\t4\t5\n", StandardCharsets.US_ASCII);
        assertThrows(IOException.class, () -> prepared.verify(() -> { }));
    }
    @Test void cancellationProducesNoSuccessfulSnapshot() throws Exception {
        assertThrows(java.util.concurrent.CancellationException.class, () -> RsemInputs.prepare(spec(), temporary.resolve("owned"), LIMITS,
                () -> { throw new java.util.concurrent.CancellationException("cancel"); }));
        assertFalse(Files.exists(temporary.resolve("owned")));
    }
    @Test @EnabledOnOs(OS.LINUX) void originalSymlinkIsRejectedWithoutFollowingItsData() throws Exception {
        Path actual = source.resolve("actual.bam"); Files.move(alignment, actual); Files.createSymbolicLink(alignment, actual.getFileName());
        assertThrows(IOException.class, this::prepare); assertFalse(Files.exists(temporary.resolve("owned/inputs.json")));
    }
    @Test @EnabledOnOs(OS.LINUX) void stagedSymlinkAndParentAliasAreRejected() throws Exception {
        var prepared = prepare(); Files.delete(prepared.alignment()); Files.createSymbolicLink(prepared.alignment(), alignment);
        assertThrows(IOException.class, () -> prepared.verify(() -> { }));
        Path alias = temporary.resolve("alias"); Files.createSymbolicLink(alias, source.getFileName());
        assertThrows(IOException.class, () -> RsemInputs.prepare(new RsemInputs.Spec(alias.resolve("reference"), alignment, null, OPTIONS), temporary.resolve("other"), LIMITS, () -> { }));
    }
    private static final class MapAssertions {
        private MapAssertions() { }
        static void notAdmitted(Path path) throws IOException {
            var record = RepeatBundle.object(Files.readAllBytes(path));
            assertEquals(false, record.get("scientific_alignment_admitted")); assertEquals(false, record.get("public_fitting_workflow_available"));
        }
    }
}
