/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SalmonIndexReferencesTest {
    private static final SalmonIndexReferences.Limits LIMITS = new SalmonIndexReferences.Limits(100, 1000, 1024, 10000, 100000, 10000000);
    private static final SalmonIndexReferences.PositionConsumer DISCARD = (ordinal, contig, full, name, start, forward) -> { };
    private record Files(byte[] table, byte[] original, byte[] normalized) { }

    @ParameterizedTest @ValueSource(strings={"1.0.0", "1.1.0", "1.5.2", "1.8.0", "1.9.0"})
    void completeOriginalFilesAndEveryPositionMatchIndependentExpectations(String version) throws Exception {
        Map<String, byte[]> fixture = fixture(); Files files = new Files(fixture.get(version + "/ctable.bin"), fixture.get(version + "/complete_ref_lens.bin"), fixture.get(version + "/reflengths.bin"));
        var positions = new StringBuilder("ordinal\tcontig_ref\tfull_ref\tname\tstart\tforward\n");
        var result = read(files, LIMITS, (ordinal, contig, full, name, start, forward) -> positions.append(ordinal).append('\t').append(contig).append('\t').append(full).append('\t').append(name).append('\t').append(start).append('\t').append(forward).append('\n'), () -> { });
        assertEquals(new String(fixture.get(version + "/expected-positions.tsv"), StandardCharsets.UTF_8), positions.toString());
        var references = new StringBuilder("name\toriginal_length\tnormalized_length\n");
        for (var reference : result.references()) {
            references.append(reference.name()).append('\t').append(reference.originalLength()).append('\t').append(reference.normalizedLength()).append('\n');
            assertEquals(reference.ordinal(), reference.contigReferenceId());
        }
        assertEquals(new String(fixture.get("expected-references.tsv"), StandardCharsets.UTF_8), references.toString());
        assertEquals(41, result.references().size()); assertEquals(41, result.contigReferences()); assertEquals(44, result.positions());
        assertEquals(files.table().length, result.contigTable().bytes()); assertEquals(sha(files.table()), result.contigTable().sha256());
        assertEquals(files.original().length, result.originalLengths().bytes()); assertEquals(sha(files.original()), result.originalLengths().sha256());
        assertEquals(files.normalized().length, result.normalizedLengths().bytes()); assertEquals(sha(files.normalized()), result.normalizedLengths().sha256());
        assertThrows(UnsupportedOperationException.class, () -> result.references().clear());
    }
    @Test void extensionMapPreservesFullReferencesWithoutAContigId() throws Exception {
        var positions = new ArrayList<String>();
        var result = read(mixed(), LIMITS, (ordinal, contig, full, name, start, forward) -> positions.add(contig + ":" + full + ":" + name + ":" + start + ":" + forward), () -> { });
        assertEquals(List.of(-1, 0, -1, 1, -1), result.references().stream().map(SalmonIndexReferences.Reference::contigReferenceId).toList());
        assertEquals(List.of("0:1:long:1:true", "1:3:other:69:false"), positions);
        assertEquals(120, result.references().get(1).originalLength()); assertEquals(100, result.references().get(1).normalizedLength());
        assertEquals(2, result.contigReferences()); assertEquals(5, result.references().size());
    }
    @Test void exactUtf8NamesAndCaseArePreserved() throws Exception {
        Files files = make(List.of("β.1", "B.1"), new long[]{40, 50}, new long[]{40, 50}, new long[]{0, 0}, new long[][]{{0, 0x80000000L}, {1, 49}});
        var result = read(files); assertEquals(List.of("β.1", "B.1"), result.references().stream().map(SalmonIndexReferences.Reference::name).toList());
    }
    @Test void uint32LengthsAndOrientationAreNotSignedJavaIntegers() throws Exception {
        Files files = make(List.of("large"), new long[]{0xffffffffL}, new long[]{0xffffffffL}, new long[]{0}, new long[][]{{0, 0xffffffffL}});
        var positions = new ArrayList<Long>(); var result = read(files, LIMITS, (o, c, f, n, p, forward) -> { assertTrue(forward); positions.add(p); }, () -> { });
        assertEquals(0xffffffffL, result.references().get(0).originalLength()); assertEquals(List.of(0x7fffffffL), positions);
    }
    @ParameterizedTest @ValueSource(strings={"table", "original", "normalized"})
    void truncationOfAnyCompleteFileFails(String file) throws Exception {
        Files source = mixed(); byte[] bytes = switch (file) { case "table" -> source.table(); case "original" -> source.original(); default -> source.normalized(); };
        assertThrows(IOException.class, () -> read(replace(source, file, Arrays.copyOf(bytes, bytes.length - 1))));
    }
    @ParameterizedTest @ValueSource(strings={"table", "original", "normalized"})
    void extraDataAfterAnyCompleteFileFails(String file) throws Exception {
        Files source = mixed(); byte[] bytes = switch (file) { case "table" -> source.table(); case "original" -> source.original(); default -> source.normalized(); };
        assertThrows(IOException.class, () -> read(replace(source, file, Arrays.copyOf(bytes, bytes.length + 1))));
    }
    @ParameterizedTest @ValueSource(strings={"references", "positions", "name", "all-names", "input", "retained"})
    void explicitWholePopulationLimitsRejectPrefixes(String dimension) throws Exception {
        var limits = new SalmonIndexReferences.Limits(dimension.equals("references") ? 2 : 100, dimension.equals("positions") ? 1 : 1000,
            dimension.equals("name") ? 1 : 1024, dimension.equals("all-names") ? 2 : 10000, dimension.equals("input") ? 10 : 100000,
            dimension.equals("retained") ? 262144 : 10000000);
        assertThrows(IOException.class, () -> read(mixed(), limits, DISCARD, () -> { }));
    }
    @ParameterizedTest @ValueSource(strings={"original", "normalized"})
    void lengthPopulationMustCoverAllNames(String field) throws Exception {
        Files source = mixed(); byte[] bytes = (field.equals("original") ? source.original() : source.normalized()).clone(); bytes[0] = 4;
        assertThrows(IOException.class, () -> read(replace(source, field, bytes)));
    }
    @ParameterizedTest @ValueSource(strings={"original", "normalized"})
    void zeroReferenceLengthsAreRejected(String field) throws Exception {
        Files source = mixed(); byte[] bytes = (field.equals("original") ? source.original() : source.normalized()).clone(); Arrays.fill(bytes, 8, 12, (byte) 0);
        assertThrows(IOException.class, () -> read(replace(source, field, bytes)));
    }
    @Test void normalizedLengthCannotExceedOriginalLength() throws Exception {
        assertThrows(IOException.class, () -> read(make(List.of("x"), new long[]{40}, new long[]{41}, new long[]{0}, new long[0][])));
    }
    @Test void duplicateNamesAreRejected() throws Exception {
        assertThrows(IOException.class, () -> read(make(List.of("x", "x"), new long[]{40, 40}, new long[]{40, 40}, new long[]{0, 0}, new long[0][])));
    }
    @Test void controlsInAnIdentityAreRejected() throws Exception {
        assertThrows(IOException.class, () -> read(make(List.of("x\tbad"), new long[]{40}, new long[]{40}, new long[]{0}, new long[0][])));
    }
    @Test void malformedUtf8IsNotReplaced() throws Exception {
        Files source = make(List.of("x"), new long[]{40}, new long[]{40}, new long[]{0}, new long[0][]); source.table()[16] = (byte) 0xff;
        assertThrows(IOException.class, () -> read(source));
    }
    @Test void emptyReferencePopulationIsRejected() throws Exception {
        Files source = mixed(); Arrays.fill(source.table(), 0, 8, (byte) 0); assertThrows(IOException.class, () -> read(source));
    }
    @Test void unsignedCountOverflowIsNotWrapped() throws Exception {
        Files source = mixed(); source.table()[7] = (byte) 0x80; assertThrows(IOException.class, () -> read(source));
    }
    @Test void wrongEndianCountIsNotGuessed() throws Exception {
        Files source = mixed(); byte value = source.table()[0]; source.table()[0] = 0; source.table()[7] = value; assertThrows(IOException.class, () -> read(source));
    }
    @Test void nonmonotoneExtensionsAreRejected() throws Exception {
        assertThrows(IOException.class, () -> read(make(List.of("a", "b", "c"), new long[]{40, 40, 40}, new long[]{40, 40, 40}, new long[]{1, 0}, new long[0][])));
    }
    @Test void projectedReferenceMustRemainInRange() throws Exception {
        assertThrows(IOException.class, () -> read(make(List.of("a"), new long[]{40}, new long[]{40}, new long[]{1}, new long[0][])));
    }
    @Test void eachPositionMustHaveADeclaredContigReference() throws Exception {
        assertThrows(IOException.class, () -> read(make(List.of("a"), new long[]{40}, new long[]{40}, new long[]{0}, new long[][]{{1, 1}})));
    }
    @Test void eachPositionStartMustLieWithinNormalizedLength() throws Exception {
        assertThrows(IOException.class, () -> read(make(List.of("a"), new long[]{100}, new long[]{40}, new long[]{0}, new long[][]{{0, 40}})));
    }
    @Test void validCallbacksBeforeAHiddenTailRemainUnadmitted() throws Exception {
        Files source = mixed(); Files extra = replace(source, "table", Arrays.copyOf(source.table(), source.table().length + 1)); var seen = new ArrayList<Long>();
        assertThrows(IOException.class, () -> read(extra, LIMITS, (o, c, f, n, p, forward) -> seen.add(o), () -> { })); assertEquals(List.of(0L, 1L), seen);
    }
    @Test void exactCombinedByteCapacityPassesAndOneLessFails() throws Exception {
        Files source = mixed(); long bytes = source.table().length + source.original().length + source.normalized().length;
        var exact = new SalmonIndexReferences.Limits(100, 1000, 1024, 10000, bytes, 10000000); assertEquals(2, read(source, exact, DISCARD, () -> { }).positions());
        var shortLimit = new SalmonIndexReferences.Limits(100, 1000, 1024, 10000, bytes - 1, 10000000); assertThrows(IOException.class, () -> read(source, shortLimit, DISCARD, () -> { }));
    }
    @ParameterizedTest @ValueSource(strings={"success", "failure", "cancellation", "consumer"})
    void allThreeStreamsCloseOnEveryOutcome(String outcome) throws Exception {
        Files source = mixed(); var table = new Closed(source.table()); var original = new Closed(source.original()); var normalized = new Closed(source.normalized());
        if (outcome.equals("failure")) source.table()[7] = (byte) 0x80;
        Runnable check = outcome.equals("cancellation") ? () -> { throw new CancellationException(); } : () -> { };
        SalmonIndexReferences.PositionConsumer consumer = outcome.equals("consumer") ? (o, c, f, n, p, forward) -> { throw new IOException("staging failed"); } : DISCARD;
        if (outcome.equals("success")) SalmonIndexReferences.read(table, original, normalized, LIMITS, consumer, check);
        else assertThrows(Exception.class, () -> SalmonIndexReferences.read(table, original, normalized, LIMITS, consumer, check));
        assertTrue(table.closed); assertTrue(original.closed); assertTrue(normalized.closed);
    }
    private static final class Closed extends ByteArrayInputStream { boolean closed; Closed(byte[] bytes) { super(bytes); } @Override public void close() throws IOException { closed = true; super.close(); } }
    private static SalmonIndexReferences.Summary read(Files files) throws IOException { return read(files, LIMITS, DISCARD, () -> { }); }
    private static SalmonIndexReferences.Summary read(Files files, SalmonIndexReferences.Limits limits, SalmonIndexReferences.PositionConsumer consumer, Runnable check) throws IOException {
        return SalmonIndexReferences.read(new ByteArrayInputStream(files.table()), new ByteArrayInputStream(files.original()), new ByteArrayInputStream(files.normalized()), limits, consumer, check);
    }
    private static Files mixed() throws IOException { return make(List.of("short", "long", "short2", "other", "tail"), new long[]{11, 120, 9, 70, 3}, new long[]{11, 100, 9, 70, 3}, new long[]{1, 2}, new long[][]{{0, 0x80000001L}, {1, 69}}); }
    private static Files replace(Files source, String name, byte[] bytes) { return new Files(name.equals("table") ? bytes : source.table(), name.equals("original") ? bytes : source.original(), name.equals("normalized") ? bytes : source.normalized()); }
    private static Files make(List<String> names, long[] original, long[] normalized, long[] extensions, long[][] positions) throws IOException {
        var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes); out.writeLong(Long.reverseBytes(names.size()));
        for (String name : names) { byte[] raw = name.getBytes(StandardCharsets.UTF_8); out.writeLong(Long.reverseBytes(raw.length)); out.write(raw); }
        out.writeLong(Long.reverseBytes(extensions.length)); for (long value : extensions) out.writeInt(Integer.reverseBytes((int) value));
        out.writeLong(Long.reverseBytes(positions.length)); for (long[] row : positions) { out.writeInt(Integer.reverseBytes((int) row[0])); out.writeInt(Integer.reverseBytes((int) row[1])); }
        return new Files(bytes.toByteArray(), lengths(original), lengths(normalized));
    }
    private static byte[] lengths(long[] values) throws IOException { var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes); out.writeLong(Long.reverseBytes(values.length)); for (long value : values) out.writeInt(Integer.reverseBytes((int) value)); return bytes.toByteArray(); }
    private static String sha(byte[] raw) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)); }
    private static Map<String, byte[]> fixture() throws Exception {
        byte[] raw; try (var input = SalmonIndexReferencesTest.class.getResourceAsStream("/org/jlinalg/sequence/salmon-index-references/original-reference-tables.zip")) { assertNotNull(input); raw = input.readAllBytes(); }
        assertEquals("8a3e04ce875dbb4cb577ca940654acb821cca58b47cdea6966f1e91628ff2e73", sha(raw)); var result = new LinkedHashMap<String, byte[]>();
        try (var input = new ZipInputStream(new ByteArrayInputStream(raw))) { for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) result.put(entry.getName(), input.readAllBytes()); } return result;
    }
}
