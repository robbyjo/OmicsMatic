package org.jlinalg.sequence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipInputStream;
import static org.junit.jupiter.api.Assertions.*;

class RsemAdmissionTest {
    @TempDir static Path fixtures;
    private static final String[] RELEASES = {"1.2.28", "1.3.0", "1.3.1", "1.3.2", "1.3.3"};
    private static final RsemReference.Limits REF = new RsemReference.Limits(1000, 1000, 1_000_000, 10000, 5_000_000, 1_000_000);
    private static final RsemModel.Limits MODEL = new RsemModel.Limits(1000, 100000, 100000, 100000, 1_000_000, 5_000_000, 1_000_000);

    @BeforeAll static void unpackVerifiedOriginalFiles() throws Exception {
        var source = RsemAdmissionTest.class.getResourceAsStream("/org/jlinalg/sequence/rsem-reference/reference-model-fixtures.zip");
        assertNotNull(source); int files = 0; long total = 0;
        try (var zip = new ZipInputStream(source)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                assertFalse(entry.isDirectory()); Path path = fixtures.resolve(entry.getName()).normalize(); assertTrue(path.startsWith(fixtures));
                assertTrue(++files <= 1000); byte[] bytes = zip.readNBytes(5_000_001); assertTrue(bytes.length <= 5_000_000);
                total += bytes.length; assertTrue(total < 20_000_000); Files.createDirectories(path.getParent()); Files.write(path, bytes);
            }
        }
        assertEquals(306, files);
        for (String line : Files.readAllLines(fixtures.resolve("SHA256SUMS"))) {
            String[] fields = line.split("  ", 2); assertEquals(fields[0], sha(fixtures.resolve(fields[1])));
        }
    }
    private static String sha(Path path) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
    private static Path reference(String release, String kind) {return fixtures.resolve(release + "/" + kind + "/reference");}
    private static Path model(String release, String kind) {return fixtures.resolve(release + "/" + kind + "/sample.stat/sample.model");}
    private static Path theta(String release, String kind) {return fixtures.resolve(release + "/" + kind + "/sample.stat/sample.theta");}
    private static Path copyReference(Path directory, String release, String kind) throws IOException {
        Path prefix = directory.resolve("reference");
        for (String suffix : new String[]{".ti", ".seq", ".grp", ".chrlist"}) {
            Path path = Path.of(reference(release, kind) + suffix);
            if (Files.exists(path)) Files.copy(path, Path.of(prefix + suffix));
        }
        return prefix;
    }
    private static void edit(Path path, String from, String to) throws IOException {
        String text = Files.readString(path); assertTrue(text.contains(from)); Files.writeString(path, text.replaceFirst(java.util.regex.Pattern.quote(from), java.util.regex.Matcher.quoteReplacement(to)), StandardCharsets.UTF_8);
    }

    @Test void allFifteenOriginalReferencePopulationsAreConsumedWithoutDroppingMembers() throws Exception {
        int transcripts = 0, genes = 0, exons = 0;
        for (String release : RELEASES) for (String kind : new String[]{"genome", "polyA", "transcripts"}) {
            var ref = RsemReference.read(reference(release, kind), REF);
            assertEquals(kind.equals("genome") ? 0 : 1, ref.type()); assertEquals(kind.equals("genome") ? 4 : 6, ref.transcripts().size());
            assertEquals(4, ref.groups().size()); assertEquals(kind.equals("genome") ? 4 : 3, ref.evidence().size());
            for (var evidence : ref.evidence()) {assertEquals(sha(Path.of(evidence.path())), evidence.sha256()); assertEquals(Files.size(Path.of(evidence.path())), evidence.bytes());}
            transcripts += ref.transcripts().size(); genes += ref.groups().size(); exons += (int) ref.totalExons();
        }
        assertEquals(80, transcripts); assertEquals(60, genes); assertEquals(90, exons);
    }
    @Test void genomeCoordinatesNamesCommaIdsAndSingletonExonsRemainExact() throws Exception {
        var ref = RsemReference.read(reference("1.3.3", "genome"), REF);
        var comma = ref.transcripts().get(0); assertEquals("iso,comma.1", comma.id()); assertEquals("gene,comma", comma.geneId());
        assertEquals("iso,comma.1_Transcript iso,comma.1", comma.displayedId(true));
        var named = ref.transcripts().get(1); assertEquals("Gene Alpha β", named.geneName()); assertEquals("geneA_Gene Alpha β", named.displayedGeneId(true));
        var minus = ref.transcripts().get(2); assertEquals('-', minus.strand()); assertEquals(200, minus.length()); assertArrayEquals(new int[]{501,550,701,850}, minus.exons());
        assertEquals(1, ref.transcripts().get(3).length()); assertEquals(3300L, ref.chromosomes().get("chrSynth"));
    }
    @Test void polyATailsAndUnsignedMaskWordsRemainSeparateFromPreTailLength() throws Exception {
        var ref = RsemReference.read(reference("1.3.3", "polyA"), REF); var item = ref.transcripts().get(0);
        assertEquals(800, item.length()); assertEquals(925, item.totalLength()); assertEquals(125, item.polyATail());
        assertEquals(4294967040L, Integer.toUnsignedLong(item.maskWords()[24])); assertEquals(4975, ref.totalBases());
        assertEquals(125, ref.abundanceReferences().get(0).polyATail());
    }
    @Test void chromosomeCapacityIsIndependentOfTranscriptCapacity(@TempDir Path dir) throws Exception {
        var prefix = copyReference(dir, "1.3.3", "genome"); Files.writeString(Path.of(prefix + ".chrlist"), "chrSynth\t3300\nunused\t9000\n");
        var limits = new RsemReference.Limits(4, 2, 1000, 10, 100000, 10000);
        assertEquals(2, RsemReference.read(prefix, limits).chromosomes().size());
    }
    @Test void changedReferenceIdentityAndLengthsFail(@TempDir Path dir) throws Exception {
        Path prefix = copyReference(dir, "1.3.3", "transcripts"); edit(Path.of(prefix + ".seq"), "tA1.1", "other");
        assertThrows(IOException.class, () -> RsemReference.read(prefix, REF));
    }
    @Test void exonLengthAndGenomeBoundsAreIndependentlyChecked(@TempDir Path dir) throws Exception {
        Path prefix = copyReference(dir, "1.3.3", "genome"); edit(Path.of(prefix + ".ti"), "+ 250", "+ 251");
        assertThrows(IOException.class, () -> RsemReference.read(prefix, REF));
        Files.copy(Path.of(reference("1.3.3", "genome") + ".ti"), Path.of(prefix + ".ti"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        edit(Path.of(prefix + ".chrlist"), "3300", "1200"); assertThrows(IOException.class, () -> RsemReference.read(prefix, REF));
    }
    @Test void groupsCannotOmitMembersOrMixGenes(@TempDir Path dir) throws Exception {
        Path prefix = copyReference(dir, "1.3.3", "transcripts"); Files.writeString(Path.of(prefix + ".grp"), "1\n3\n4\n6\n");
        assertThrows(IOException.class, () -> RsemReference.read(prefix, REF));
        Files.writeString(Path.of(prefix + ".grp"), "1\n7\n"); assertThrows(IOException.class, () -> RsemReference.read(prefix, REF));
    }
    @Test void extraSequenceRecordsAndTruncatedMetadataFail(@TempDir Path dir) throws Exception {
        Path prefix = copyReference(dir, "1.3.3", "transcripts"); Files.writeString(Path.of(prefix + ".seq"), "1 1\nextra\nA\n0\n", java.nio.file.StandardOpenOption.APPEND);
        assertThrows(IOException.class, () -> RsemReference.read(prefix, REF));
        Files.copy(Path.of(reference("1.3.3", "transcripts") + ".seq"), Path.of(prefix + ".seq"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Path ti = Path.of(prefix + ".ti"); String text = Files.readString(ti); Files.writeString(ti, text.substring(0, text.length() - 1));
        assertThrows(IOException.class, () -> RsemReference.read(prefix, REF));
    }
    @Test void invalidUtf8AndCrLfAreNeverSilentlyNormalized(@TempDir Path dir) throws Exception {
        Path prefix = copyReference(dir, "1.3.3", "genome"); Path ti = Path.of(prefix + ".ti"); String text = Files.readString(ti);
        Files.writeString(ti, text.replace("\n", "\r\n")); assertThrows(IOException.class, () -> RsemReference.read(prefix, REF));
        Files.write(ti, new byte[]{(byte) 0xc3, 0x28}); assertThrows(IOException.class, () -> RsemReference.read(prefix, REF));
    }
    @Test void wholeReferenceCapacityAndUnsupportedAlleleModeFailExplicitly(@TempDir Path dir) throws Exception {
        assertThrows(IOException.class, () -> RsemReference.read(reference("1.3.3", "transcripts"), new RsemReference.Limits(5, 10, 10000, 1000, 100000, 10000)));
        assertThrows(IOException.class, () -> RsemReference.read(reference("1.3.3", "transcripts"), new RsemReference.Limits(10, 10, 100, 1000, 100000, 10000)));
        Path prefix = copyReference(dir, "1.3.3", "transcripts"); edit(Path.of(prefix + ".ti"), "6 1", "6 2");
        var failure = assertThrows(IOException.class, () -> RsemReference.read(prefix, REF)); assertTrue(failure.getMessage().contains("Allele-specific"));
    }
    @Test void referenceCollectionsAndArraysAreImmutable() throws Exception {
        var ref = RsemReference.read(reference("1.3.3", "genome"), REF); int before = ref.transcripts().get(0).exons()[0];
        ref.transcripts().get(0).exons()[0] = -1; assertEquals(before, ref.transcripts().get(0).exons()[0]);
        assertThrows(UnsupportedOperationException.class, () -> ref.transcripts().clear()); assertThrows(UnsupportedOperationException.class, () -> ref.chromosomes().clear());
    }
    @Test void allFortyCompleteOriginalModelsAndBothThetaVectorsAreFullyRead() throws Exception {
        int count = 0; var types = new java.util.HashSet<Integer>();
        for (String release : RELEASES) {
            var cases = new ArrayList<>(List.of("single-no-quality", "paired-no-quality", "single-no-fragment-prior", "polyA-estimated-start", "paired-em", "single-em", "reverse-em", "paired-posterior"));
            for (String kind : cases) {
                var parsed = RsemModel.read(model(release, kind), 6, true, MODEL); var theta = RsemModel.readTheta(theta(release, kind), 6, MODEL);
                types.add(parsed.type()); assertEquals(7, parsed.mappability().size()); assertEquals(7, theta.polished().size()); assertEquals(7, theta.unpolished().size());
                assertEquals(sha(model(release, kind)), parsed.evidence().sha256()); assertEquals(sha(theta(release, kind)), theta.evidence().sha256());
                assertEquals(parsed.type() % 2 == 1 ? 10000 : 0, parsed.qualityTransitions().size());
                assertEquals(parsed.errorProfileLength() * 25, parsed.errorProbabilities().size());
                assertEquals(parsed.type() % 2 == 1 ? 500 : 5, parsed.noiseProbabilities().size()); count++;
            }
        }
        assertEquals(40, count); assertEquals(java.util.Set.of(0,1,2,3), types);
    }
    @Test void noAlignmentOutputsHaveVerifiedEmptyFilesRatherThanAnInventedModel() throws Exception {
        for (String release : new String[]{"1.3.2", "1.3.3"}) {
            Path path = fixtures.resolve(release + "/noise/sample.stat/sample.cnt");
            var counts = RsemCounts.read(path, 1000, 10000, 1000, 10000, 10000);
            assertEquals(2, counts.totalGroups()); assertEquals(0, counts.alignableGroups());
            var version = RsemAbundance.Release.valueOf("V" + release.replace('.', '_'));
            var state = RsemModel.readFitState(model(release, "noise"), theta(release, "noise"), counts, 6, version, MODEL);
            assertFalse(state.learned()); assertNull(state.model()); assertNull(state.theta());
            assertTrue(state.evidence().stream().allMatch(e -> e.bytes() == 0));
            assertThrows(IOException.class, () -> RsemModel.readFitState(model(release, "noise"), theta(release, "noise"), counts, 6, RsemAbundance.Release.V1_3_1, MODEL));
        }
    }
    @Test void bothOptionalLengthBranchesAndEstimatedStartBinsArePreserved() throws Exception {
        var noPrior = RsemModel.read(model("1.3.3", "single-no-fragment-prior"), 6, true, MODEL);
        var prior = RsemModel.read(model("1.3.3", "single-em"), 6, true, MODEL);
        var estimated = RsemModel.read(model("1.3.3", "polyA-estimated-start"), 6, true, MODEL);
        assertNull(noPrior.readLengths()); assertNotNull(prior.readLengths()); assertFalse(noPrior.estimateStartDistribution()); assertTrue(noPrior.startProbabilities().isEmpty());
        assertTrue(estimated.estimateStartDistribution()); assertFalse(estimated.startProbabilities().isEmpty()); assertTrue(prior.unusedProbabilityRows() > 0);
    }
    @Test void modelDimensionsAndReferenceBindingFailBeforeAdmission(@TempDir Path dir) throws Exception {
        assertThrows(IOException.class, () -> RsemModel.read(model("1.3.3", "paired-em"), 5, true, MODEL));
        Path target = dir.resolve("bad.model"); Files.copy(model("1.3.3", "paired-em"), target); edit(target, "189 220 31", "189 220 30");
        assertThrows(IOException.class, () -> RsemModel.read(target, 6, true, MODEL));
    }
    @Test void qualityMatricesAreConsumedRatherThanSkipped(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("bad.model"); Files.copy(model("1.3.3", "paired-em"), target); edit(target, "\n100\n", "\n99\n");
        assertThrows(IOException.class, () -> RsemModel.read(target, 6, true, MODEL));
        Files.copy(model("1.3.3", "paired-em"), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING); edit(target, "0.5", "NaN");
        assertThrows(IOException.class, () -> RsemModel.read(target, 6, true, MODEL));
    }
    @Test void optionalMappabilityIsNeverInventedForACompletedFit(@TempDir Path dir) throws Exception {
        String text = Files.readString(model("1.3.3", "paired-em")); String without = text.replaceFirst("\\n6\\n[^\\n]+\\n?$", "\n"); assertNotEquals(text, without);
        Path target = dir.resolve("no-mw.model"); Files.writeString(target, without);
        assertTrue(RsemModel.read(target, 6, false, MODEL).mappability().isEmpty());
        assertThrows(IOException.class, () -> RsemModel.read(target, 6, true, MODEL));
    }
    @Test void trailingAndTruncatedModelDataFail(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("bad.model"); String text = Files.readString(model("1.3.3", "paired-em")); Files.writeString(target, text + "garbage\n");
        assertThrows(IOException.class, () -> RsemModel.read(target, 6, true, MODEL));
        Files.writeString(target, text.substring(0, text.length()/2)); assertThrows(IOException.class, () -> RsemModel.read(target, 6, true, MODEL));
    }
    @Test void thetaCannotDropNoiseOrAcceptANonnormalizedVector(@TempDir Path dir) throws Exception {
        assertThrows(IOException.class, () -> RsemModel.readTheta(theta("1.3.3", "paired-em"), 5, MODEL));
        Path target = dir.resolve("bad.theta"); Files.writeString(target, "7\n1 1 0 0 0 0 0\n1 0 0 0 0 0 0\n");
        assertThrows(IOException.class, () -> RsemModel.readTheta(target, 6, MODEL));
    }
    @Test void scalarAndByteCapacitiesApplyToTheWholeModel() throws Exception {
        var small = new RsemModel.Limits(100, 10000, 1000, 10000, 20, 1000000, 1000000);
        assertThrows(IOException.class, () -> RsemModel.read(model("1.3.3", "paired-em"), 6, true, small));
        var tiny = new RsemModel.Limits(100, 10000, 1000, 10000, 100000, 10, 1000000);
        assertThrows(IOException.class, () -> RsemModel.read(model("1.3.3", "paired-em"), 6, true, tiny));
    }
    @Test void originalZeroRowsStayZeroAndModelCollectionsAreImmutable() throws Exception {
        var parsed = RsemModel.read(model("1.3.3", "paired-em"), 6, true, MODEL);
        assertTrue(parsed.unusedProbabilityRows() > 0); assertTrue(parsed.qualityTransitions().stream().anyMatch(v -> v.value() == 0 && v.lower() == 0 && v.upper() == 0));
        assertThrows(UnsupportedOperationException.class, () -> parsed.qualityTransitions().clear());
    }
    @Test void modelTokenReaderRetainsAsciiTabWhitespace(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("tabs.model"); Files.writeString(target, Files.readString(model("1.3.3", "paired-em")).replace(' ', '\t'));
        assertEquals(3, RsemModel.read(target, 6, true, MODEL).type());
    }
}
