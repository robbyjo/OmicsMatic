package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.jlinalg.sequence.RsemAbundance;
import org.jlinalg.sequence.RsemModel;
import org.jlinalg.sequence.RsemReference;
import org.jlinalg.sequence.RsemResults;
import org.jlinalg.sequence.RsemSamAlignments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RsemFitOutputsTest {
    @TempDir Path temporary;
    private static Path archivePath;
    private static Map<?,?> index, golden;
    private static final RsemInputs.Limits INPUTS = new RsemInputs.Limits(5000000, 50000000, 100, 1000000, new RsemReference.Limits(100, 100, 1000000, 1000, 5000000, 1000000));
    private static final RsemSamAlignments.Limits SAM = new RsemSamAlignments.Limits(10000, 10000, 100000, 10000, 1000, 100000, 1000000, 10000000, 10000, 1000000, 10000, 10000000);
    private static final RsemFitOutputs.Limits OUTPUT = new RsemFitOutputs.Limits(10000, 100000, 1000, 1000000, 100000,
            new RsemModel.Limits(100, 10000, 10000, 10000, 100000, 1000000, 1000000), new RsemResults.Limits(100, 100, 100, 10000, 1000000, 1000000, 1000000), new RepeatBundle.Limits(67108864, 1000));
    record Case(Path home, RsemInputs.Prepared inputs, Path projection, RsemSamAlignments.Summary summary, RsemFitRequest.Settings settings) { }
    @BeforeAll static void originalEvidence() throws Exception {
        archivePath = Path.of("docs/literature/omics-2023-2026/rsem-source-build-controls.zip").toAbsolutePath().normalize();
        try (var input = Files.newInputStream(archivePath)) { assertEquals("48608ba3a44af1a780b4436d687a166ae31bcb6a0c0cb7a175d05a1f31ce45bb", java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()))); }
        try (var zip = new ZipFile(archivePath.toFile())) { index = (Map<?,?>)RepeatBundle.object(zip.getInputStream(zip.getEntry("index.json")).readAllBytes()).get("logical_files"); }
        try (var stream = RsemFitOutputsTest.class.getResourceAsStream("/org/jlinalg/omics/rsem-health/inputs.zip")) {
            assertNotNull(stream);
            try (var zip = new ZipInputStream(stream)) { java.util.zip.ZipEntry entry; while ((entry = zip.getNextEntry()) != null) if (entry.getName().equals("expected-science.json")) golden = RepeatBundle.object(zip.readAllBytes()); }
        }
        assertNotNull(golden);
    }
    private static byte[] evidence(ZipFile zip, String name) throws IOException {
        Map<?,?> row = (Map<?,?>)index.get(name); assertNotNull(row, name); byte[] raw = zip.getInputStream(zip.getEntry("objects/" + row.get("sha256"))).readAllBytes();
        assertEquals(row.get("sha256"), AdmixtureInputs.digest(raw)); return raw;
    }
    private Case fixture(String version, String name, String suffix) throws Exception {
        Path root = Files.createDirectory(temporary.toRealPath().resolve(version + '-' + name + '-' + suffix));
        Path source = Files.createDirectory(root.resolve("reference")), health = Files.createDirectory(root.resolve("health")), home = Files.createDirectory(root.resolve("fit"));
        try (var zip = new ZipFile(archivePath.toFile())) {
            String prefix = "runtime/" + version + '/';
            for (Object key : index.keySet()) {
                String path = (String)key;
                if (path.startsWith(prefix + "prepare/reference.")) Files.write(source.resolve(path.substring((prefix + "prepare/").length())), evidence(zip, path));
                if (path.startsWith(prefix + name + '/')) {
                    Path target = home.resolve(path.substring((prefix + name + '/').length())); Files.createDirectories(target.getParent()); Files.write(target, evidence(zip, path));
                }
            }
        }
        try (var stream = getClass().getResourceAsStream("/org/jlinalg/omics/rsem-health/inputs.zip"); var zip = new ZipInputStream(stream)) {
            java.util.zip.ZipEntry entry; while ((entry = zip.getNextEntry()) != null) Files.write(health.resolve(entry.getName()), zip.readAllBytes());
        }
        int type = name.equals("single-em") ? 1 : 3; String sam = name.equals("single-em") ? "single.sam" : name.equals("reverse-em") ? "reverse.sam" : name.equals("noise") ? "noise.sam" : "paired.sam";
        var options = new RsemSamAlignments.Options(RsemAbundance.Release.valueOf("V" + version.replace('.', '_')), type, "", 25);
        var input = RsemInputs.prepare(new RsemInputs.Spec(source.resolve("reference"), health.resolve(sam), null, options), root.resolve("inputs"), INPUTS, () -> { });
        RsemSamAlignments.Summary summary; Path projection = root.resolve("native");
        try (var output = new RsemAlignmentProjection(projection, options, 5000000, 5000000, 32, () -> { }); var reader = Files.newBufferedReader(input.alignment())) {
            summary = RsemSamAlignments.read(reader, input.reference(), options, SAM, output, () -> { }); assertTrue(summary.safeForFitting()); output.finish(summary);
        }
        var settings = new RsemFitRequest.Settings(1618, 1, name.equals("reverse-em") ? RsemFitRequest.Strand.REVERSE : RsemFitRequest.Strand.NONE,
                name.equals("paired-posterior") ? RsemFitRequest.Posterior.INTERVAL : RsemFitRequest.Posterior.NONE, name.equals("paired-posterior"), false,
                name.equals("single-em") ? new RsemFitRequest.Fragment(200, 20) : null);
        return new Case(home, input, projection, summary, settings);
    }
    private RsemFitOutputs.Audit audit(Case c) throws IOException { return RsemFitOutputs.audit(c.home(), c.inputs().reference(), c.summary(), c.inputs().spec().options(), c.projection(), c.settings(), OUTPUT, () -> { }); }
    @Test void all22OriginalFittingPopulationsPassCompleteNewAdmission() throws Exception {
        Map<?,?> cases = (Map<?,?>)golden.get("cases"); int compared = 0, science = 0;
        for (String version : List.of("1.2.28", "1.3.0", "1.3.1", "1.3.2", "1.3.3")) for (String name : List.of("paired-em", "reverse-em", "single-em", "paired-posterior", "noise")) {
            if (!cases.containsKey(version + '/' + name)) continue;
            Case c = fixture(version, name, "all"); var result = audit(c);
            assertEquals(6, result.results().transcripts()); assertEquals(4, result.results().genes()); assertTrue(result.originalParserFilesCompared() >= 3);
            assertEquals(cases.get(version + '/' + name), result.scientificFiles()); compared++; science += result.scientificFiles().size();
        }
        assertEquals(22, compared); assertEquals(364, science);
    }
    @Test void alteredReadOrHitProjectionCannotHideBehindMatchingCounts() throws Exception {
        Case c = fixture("1.3.3", "paired-em", "bad-dat"); Path data = c.home().resolve("sample.temp/sample.dat");
        String raw = Files.readString(data); int newline = raw.indexOf('\n'); int hit = raw.indexOf(" 30 ", newline);
        if (hit >= 0) raw = raw.substring(0, hit) + " 31 " + raw.substring(hit + 4); else raw += "1 1 0 1\n";
        Files.writeString(data, raw); assertThrows(IOException.class, () -> audit(c));
    }
    @Test void alteredCompleteCountFileCannotPass() throws Exception {
        Case c = fixture("1.3.3", "paired-em", "bad-cnt"); Files.writeString(c.home().resolve("sample.stat/sample.cnt"), "\n", StandardOpenOption.APPEND); assertThrows(IOException.class, () -> audit(c));
    }
    @Test void forbiddenBamAndUnknownOutputsAreRejected() throws Exception {
        Case c = fixture("1.3.3", "paired-em", "extra"); Files.writeString(c.home().resolve("sample.transcript.bam"), "unexpected"); assertThrows(IOException.class, () -> audit(c));
    }
    @Test void missingModelOrIncompleteResultPopulationCannotBeAdmitted() throws Exception {
        Case c = fixture("1.3.3", "paired-em", "missing"); Files.delete(c.home().resolve("sample.stat/sample.model")); assertThrows(IOException.class, () -> audit(c));
        Case other = fixture("1.3.3", "paired-em", "short"); Path genes = other.home().resolve("sample.genes.results"); var rows = Files.readAllLines(genes); Files.write(genes, rows.subList(0, rows.size() - 1)); assertThrows(IOException.class, () -> audit(other));
    }
    @Test void posteriorRequestCannotSilentlyAcceptAnEmOnlySchema() throws Exception {
        Case c = fixture("1.3.3", "paired-em", "posterior");
        var requested = new RsemFitRequest.Settings(1618, 1, RsemFitRequest.Strand.NONE, RsemFitRequest.Posterior.INTERVAL, false, false, null);
        assertThrows(IOException.class, () -> RsemFitOutputs.audit(c.home(), c.inputs().reference(), c.summary(), c.inputs().spec().options(), c.projection(), requested, OUTPUT, () -> { }));
    }
    @Test void unexpectedEmptyDirectoriesArePartOfTheOwnedOutputPolicy() throws Exception {
        Case c = fixture("1.3.3", "paired-em", "directory"); Files.createDirectory(c.home().resolve("extra-empty")); assertThrows(IOException.class, () -> audit(c));
    }
}
