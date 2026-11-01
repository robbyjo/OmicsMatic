package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.jlinalg.sequence.RsemAbundance;
import org.jlinalg.sequence.RsemReference;
import org.jlinalg.sequence.RsemSamAlignments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RsemAlignmentProjectionTest {
    @TempDir static Path fixtures;
    @TempDir Path temporary;
    private static List<?> cases;
    private static final Map<String,RsemReference.Reference> REFERENCES = new HashMap<>();
    private static final RsemReference.Limits REF = new RsemReference.Limits(1000, 1000, 1000000, 10000, 5000000, 1000000);
    private static final RsemSamAlignments.Limits LIMITS = new RsemSamAlignments.Limits(10000, 10000, 100000, 10000, 1000, 100000, 1000000, 10000000, 10000, 1000000, 10000, 10000000);
    @BeforeAll static void originalCorpus() throws Exception {
        byte[] raw;
        try (var input = RsemAlignmentProjectionTest.class.getResourceAsStream("/org/jlinalg/sequence/rsem-reference/alignment-fixtures.zip")) { assertNotNull(input); raw = input.readAllBytes(); }
        assertEquals("c22c99f8e414d864df86367b01a825237810c4c9a744eb8f1c100dbfb901f72c", hash(raw));
        fixtures = fixtures.toRealPath(); int count = 0; long bytes = 0;
        try (var zip = new ZipInputStream(new java.io.ByteArrayInputStream(raw))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                assertFalse(entry.isDirectory()); Path target = fixtures.resolve(entry.getName()).normalize(); assertTrue(target.startsWith(fixtures));
                byte[] member = zip.readNBytes(5000001); assertTrue(member.length <= 5000000); bytes += member.length;
                assertTrue(bytes < 40000000); assertTrue(++count < 6000); Files.createDirectories(target.getParent()); Files.write(target, member);
            }
        }
        assertEquals(3993, count);
        for (String line : Files.readAllLines(fixtures.resolve("SHA256SUMS"))) { String[] fields = line.split("  ", 2); assertEquals(fields[0], hash(Files.readAllBytes(fixtures.resolve(fields[1])))); }
        Object value = SimpleJson.parse(Files.readString(fixtures.resolve("cases.json"))); assertInstanceOf(List.class, value); cases = (List<?>)value; assertEquals(580, cases.size());
    }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static String text(Map<?,?> c, String key) { return (String)c.get(key); }
    private static int integer(Map<?,?> c, String key) { return ((Number)c.get(key)).intValue(); }
    private static RsemReference.Reference reference(Map<?,?> c) throws IOException {
        String key = text(c, "version") + '/' + text(c, "reference"); var result = REFERENCES.get(key);
        if (result == null) { result = RsemReference.read(fixtures.resolve("references/" + key + "/reference"), REF); REFERENCES.put(key, result); }
        return result;
    }
    private static RsemSamAlignments.Options options(Map<?,?> c) { return new RsemSamAlignments.Options(RsemAbundance.Release.valueOf("V" + text(c, "version").replace('.', '_')), integer(c, "readtype"), text(c, "tag"), 25); }
    private static RsemSamAlignments.Summary render(Map<?,?> c, RsemAlignmentProjection projection) throws IOException {
        try (var sam = Files.newBufferedReader(fixtures.resolve(text(c, "decoded_sam"))); var metadata = Files.newBufferedReader(fixtures.resolve(text(c, "decoded_metadata")))) {
            var summary = RsemSamAlignments.read(sam, reference(c), options(c), LIMITS, projection, () -> { }, RsemSamAlignments.decodedRecordMetadata(metadata, LIMITS, () -> { }));
            projection.finish(summary); return summary;
        }
    }
    @Test void everySuccessfulOriginalParserPopulationMatchesExactStreamedOutputBytes() throws Exception {
        int matched = 0;
        for (Object value : cases) {
            Map<?,?> c = (Map<?,?>)value; if (integer(c, "decoder_exit") != 0 || integer(c, "parser_exit") != 0) continue;
            Path home = temporary.toRealPath().resolve("case-" + matched);
            try (var projection = new RsemAlignmentProjection(home, options(c), 5000000, 5000000, 32, () -> { })) {
                render(c, projection); assertTrue(projection.finished());
            }
            Map<?,?> outputs = (Map<?,?>)c.get("outputs");
            for (var entry : outputs.entrySet()) {
                String name = (String)entry.getKey(); Path actual = home.resolve("expected" + name.substring("sample".length()));
                assertArrayEquals(Files.readAllBytes(fixtures.resolve((String)entry.getValue())), Files.readAllBytes(actual), text(c, "version") + '/' + text(c, "format") + '/' + text(c, "case") + ':' + name);
            }
            try (var files = Files.list(home)) {
                for (Path file : files.toList()) if (!file.getFileName().toString().equals("blocking-findings.jsonl"))
                    assertTrue(outputs.containsKey("sample" + file.getFileName().toString().substring("expected".length())), file.toString());
            }
            matched++;
        }
        assertEquals(478, matched);
    }
    @Test void blockingFindingsKeepPhysicalRecordAndRequiredDetail() throws Exception {
        Map<?,?> c = cases.stream().map(v -> (Map<?,?>)v).filter(v -> text(v, "version").equals("1.3.3") && text(v, "case").equals("paired-same-strand") && text(v, "format").equals("sam")).findFirst().orElseThrow();
        Path home = temporary.toRealPath().resolve("blocked");
        try (var projection = new RsemAlignmentProjection(home, options(c), 5000000, 5000000, 32, () -> { })) {
            assertFalse(render(c, projection).safeForFitting());
        }
        String details = Files.readString(home.resolve("blocking-findings.jsonl"));
        assertTrue(details.contains("SAME_STRAND_MATES")); assertTrue(details.contains("physical_record")); assertTrue(details.contains("detail"));
    }
    @Test void completeOutputLimitFailsInsteadOfTruncatingOrAdmittingAResult() throws Exception {
        Map<?,?> c = cases.stream().map(v -> (Map<?,?>)v).filter(v -> text(v, "version").equals("1.3.3") && text(v, "case").equals("paired-basic") && text(v, "format").equals("sam")).findFirst().orElseThrow();
        Path home = temporary.toRealPath().resolve("limited");
        assertThrows(IOException.class, () -> { try (var projection = new RsemAlignmentProjection(home, options(c), 100, 100, 32, () -> { })) { render(c, projection); } });
        assertFalse(Files.exists(home.resolve("ADMITTED.json"))); assertFalse(Files.exists(home.resolve("COMPLETED.json")));
    }
    @Test void anExistingProjectionDirectoryIsNeverOverwritten() throws Exception {
        Path home = Files.createDirectory(temporary.toRealPath().resolve("existing")); Files.writeString(home.resolve("keep"), "original", StandardCharsets.UTF_8);
        var options = new RsemSamAlignments.Options(RsemAbundance.Release.V1_3_3, 3, "", 25);
        assertThrows(IOException.class, () -> new RsemAlignmentProjection(home, options, 5000000, 5000000, 32, () -> { }));
        assertEquals("original", Files.readString(home.resolve("keep")));
    }
}
