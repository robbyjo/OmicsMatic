/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RepeatFastaTest {
    @TempDir Path directory;
    private int serial;
    static RepeatFasta.Limits limits() { return new RepeatFasta.Limits(10000000, 10000000, 9000000, 9000000, 100, 4096, 1024, 1048576); }
    private Path file(byte[] bytes) throws IOException { Path path = directory.resolve("input-" + serial++); Files.write(path, bytes); return path; }
    private Path file(String text) throws IOException { return file(text.getBytes(StandardCharsets.UTF_8)); }
    private RepeatFasta.Census plain(String text) throws IOException { return RepeatFasta.read(file(text), RepeatFasta.Encoding.PLAIN_UTF8, limits(), () -> { }); }
    private RepeatFasta.Census compressed(byte[] bytes) throws IOException { return RepeatFasta.read(file(bytes), RepeatFasta.Encoding.GZIP_UTF8, limits(), () -> { }); }
    private static byte[] gzip(String text) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var stream = new GZIPOutputStream(bytes)) { stream.write(text.getBytes(StandardCharsets.UTF_8)); }
        return bytes.toByteArray();
    }
    private static byte[] join(byte[]... parts) throws IOException { var out = new ByteArrayOutputStream(); for (byte[] part : parts) out.write(part); return out.toByteArray(); }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }

    @Test void preservesExactHeadersCaseWrappingAndCompleteDigest() throws Exception {
        String text = "\n>chr1 description β\r\nAcGt\r\nURYKmswbdhvnx\n\n>Chr1\tsecond\nNn\n";
        var census = plain(text);
        assertEquals(2, census.sequences().size()); assertEquals(19, census.residues());
        assertEquals("chr1 description β", census.sequences().get(0).header());
        assertEquals("Chr1\tsecond", census.sequences().get(1).header());
        assertEquals(hash("AcGtURYKmswbdhvnx".getBytes(StandardCharsets.US_ASCII)), census.sequences().get(0).sequenceSha256());
        assertEquals(hash(text.getBytes(StandardCharsets.UTF_8)), census.sourceSha256());
        assertEquals(text.getBytes(StandardCharsets.UTF_8).length, census.sourceBytes());
        assertEquals(census.sequences(), compressed(gzip(text)).sequences());
    }
    @Test void cursorRequiresCompleteRecordsAndStablePhysicalEofCensus() throws Exception {
        try (var cursor = RepeatFasta.open(file(">a\nAC\n>b\nT"), RepeatFasta.Encoding.PLAIN_UTF8, limits(), () -> { })) {
            assertThrows(IOException.class, cursor::census); assertTrue(cursor.nextSequence());
            assertEquals('A', cursor.base()); assertThrows(IOException.class, cursor::nextSequence);
            assertEquals('C', cursor.base()); assertEquals(-1, cursor.base());
            assertThrows(IOException.class, cursor::census); assertTrue(cursor.nextSequence());
            assertEquals('T', cursor.base()); assertEquals(-1, cursor.base()); assertFalse(cursor.nextSequence());
            assertEquals(cursor.census(), cursor.census()); assertEquals(3, cursor.census().residues());
        }
    }
    @Test void invalidDnaWhitespaceHeadersEmptyRecordsAndUtf8Fail() throws Exception {
        for (String text : new String[]{"", "\n", "AC\n>a\nTT", ">a\n", ">a\n>b\nAC", "> a\nAC", ">\nAC", ">a\nA C", ">a\nA-C", ">a\nAZ", ">a\nAC\rGT", ">a\u0000bad\nAC", ">a\u00a0b\nAC"}) {
            Path path = file(text); assertThrows(IOException.class, () -> RepeatFasta.read(path, RepeatFasta.Encoding.PLAIN_UTF8, limits(), () -> { }), text);
        }
        Path malformed = file(new byte[]{'>','a','\n','A',(byte)0xc3,0x28});
        assertThrows(IOException.class, () -> RepeatFasta.read(malformed, RepeatFasta.Encoding.PLAIN_UTF8, limits(), () -> { }));
    }
    @Test void duplicateIdentifierTokensFailWithoutCaseOrHeaderAliasRewrite() throws Exception {
        assertThrows(IOException.class, () -> plain(">x one\nAC\n>x two\nGT"));
        var census = plain(">x one\nAC\n>X two\nGT");
        assertEquals("x", census.sequences().get(0).id()); assertEquals("X", census.sequences().get(1).id());
    }
    @Test void exactSourceByteBoundIsNotAnEofTruncation() throws Exception {
        Path path = file(">a\nACGT\n"); long size = Files.size(path);
        var exact = new RepeatFasta.Limits(size, 100, 20, 20, 2, 64, 32, 100000);
        assertEquals(size, RepeatFasta.read(path, RepeatFasta.Encoding.PLAIN_UTF8, exact, () -> { }).sourceBytes());
        var shortBound = new RepeatFasta.Limits(size - 1, 100, 20, 20, 2, 64, 32, 100000);
        assertThrows(IOException.class, () -> RepeatFasta.read(path, RepeatFasta.Encoding.PLAIN_UTF8, shortBound, () -> { }));
        assertThrows(IOException.class, () -> RepeatFasta.read(directory, RepeatFasta.Encoding.PLAIN_UTF8, exact, () -> { }));
    }
    @Test void decodedResidueSequenceAndLineBoundsFailAtOneBeyond() throws Exception {
        Path path = file(">a\nACGT\n>b\nTT\n"); long chars = Files.readString(path).length();
        var exact = new RepeatFasta.Limits(1000, chars, 6, 4, 2, 64, 32, 100000);
        assertEquals(6, RepeatFasta.read(path, RepeatFasta.Encoding.PLAIN_UTF8, exact, () -> { }).residues());
        for (var bounds : new RepeatFasta.Limits[]{
                new RepeatFasta.Limits(1000, chars - 1, 6, 4, 2, 64, 32, 100000),
                new RepeatFasta.Limits(1000, chars, 5, 4, 2, 64, 32, 100000),
                new RepeatFasta.Limits(1000, chars, 6, 3, 2, 64, 32, 100000),
                new RepeatFasta.Limits(1000, chars, 6, 4, 1, 64, 32, 100000),
                new RepeatFasta.Limits(1000, chars, 6, 4, 2, 3, 2, 100000)})
            assertThrows(IOException.class, () -> RepeatFasta.read(path, RepeatFasta.Encoding.PLAIN_UTF8, bounds, () -> { }));
    }
    @Test void headerAndMetadataEstimatesHaveIndependentHardBounds() throws Exception {
        Path path = file(">long-header\nAC\n");
        var header = new RepeatFasta.Limits(1000, 1000, 100, 100, 5, 32, 3, 100000);
        assertThrows(IOException.class, () -> RepeatFasta.read(path, RepeatFasta.Encoding.PLAIN_UTF8, header, () -> { }));
        var metadata = new RepeatFasta.Limits(1000, 1000, 100, 100, 5, 32, 32, 65536 + 16 * 32);
        assertThrows(IOException.class, () -> RepeatFasta.read(path, RepeatFasta.Encoding.PLAIN_UTF8, metadata, () -> { }));
        assertThrows(IllegalArgumentException.class, () -> new RepeatFasta.Limits(1, 1, 1, 1, 1, 32, 32, 1));
    }
    @Test void concatenatedGzipMembersIncludeEmptyMembersAndSplitLines() throws Exception {
        byte[] input = join(gzip(""), gzip(">a\nAC"), gzip("GT\n>b\n"), gzip("TT\n"), gzip(""));
        var census = compressed(input);
        assertEquals(plain(">a\nACGT\n>b\nTT\n").sequences(), census.sequences());
        assertEquals(hash(input), census.sourceSha256()); assertEquals(input.length, census.sourceBytes());
    }
    @Test void everyTrailerCrcSizeAndTruncatedMemberIsChecked() throws Exception {
        byte[] valid = gzip(">a\nACGT\n");
        for (int remove = 1; remove <= 11; remove++) {
            byte[] cut = Arrays.copyOf(valid, valid.length - remove); assertThrows(IOException.class, () -> compressed(cut));
        }
        for (int offset : new int[]{valid.length - 8, valid.length - 4, 2, 3}) {
            byte[] bad = valid.clone(); bad[offset] ^= offset == 3 ? (byte)0x80 : 1;
            assertThrows(IOException.class, () -> compressed(bad));
        }
        assertThrows(IOException.class, () -> compressed(join(valid, new byte[]{31, (byte)139})));
    }
    @Test void trailingBytesAndWrongExplicitEncodingCannotHide() throws Exception {
        assertThrows(IOException.class, () -> compressed(join(gzip(">a\nAC\n"), new byte[]{0, 0, 0})));
        assertThrows(IOException.class, () -> compressed(">a\nAC\n".getBytes(StandardCharsets.UTF_8)));
        Path named = directory.resolve("plain.fa.gz"); Files.writeString(named, ">a\nAC\n");
        assertEquals(2, RepeatFasta.read(named, RepeatFasta.Encoding.PLAIN_UTF8, limits(), () -> { }).residues());
        Path compressed = file(gzip(">a\nAC\n"));
        assertThrows(IOException.class, () -> RepeatFasta.read(compressed, RepeatFasta.Encoding.PLAIN_UTF8, limits(), () -> { }));
    }
    @Test void optionalGzipHeaderFieldsAndHeaderCrcAreVerified() throws Exception {
        byte[] valid = gzip(">a\nACGT\n"); var header = new ByteArrayOutputStream();
        header.write(valid, 0, 3); header.write(30); header.write(valid, 4, 6);
        header.write(new byte[]{3,0,1,2,3}); header.write("fixture.fa\0comment\0".getBytes(StandardCharsets.US_ASCII));
        CRC32 crc = new CRC32(); byte[] prefix = header.toByteArray(); crc.update(prefix);
        header.write((int)crc.getValue() & 255); header.write((int)(crc.getValue() >>> 8) & 255);
        byte[] input = join(header.toByteArray(), Arrays.copyOfRange(valid, 10, valid.length));
        assertEquals(4, compressed(input).residues());
        input[prefix.length] ^= 1; assertThrows(IOException.class, () -> compressed(input));
    }
    @Test void cancellationWorksAcrossEmptyCompressedMembers() throws Exception {
        var bytes = new ByteArrayOutputStream(); for (int i = 0; i < 500; i++) bytes.write(gzip("")); bytes.write(gzip(">a\nAC\n"));
        Path path = file(bytes.toByteArray()); var calls = new AtomicInteger();
        assertThrows(CancellationException.class, () -> RepeatFasta.read(path, RepeatFasta.Encoding.GZIP_UTF8, limits(), () -> {
            if (calls.incrementAndGet() > 20) throw new CancellationException("test cancellation");
        }));
    }
    @Test void multiMegabaseRecordUsesBoundedLinesAndACompleteDigest() throws Exception {
        Path path = directory.resolve("large.fa"); String line = "AcgT".repeat(256); var digest = MessageDigest.getInstance("SHA-256");
        try (var writer = Files.newBufferedWriter(path)) {
            writer.write(">long\n"); for (int i = 0; i < 4096; i++) { writer.write(line); writer.write('\n'); digest.update(line.getBytes(StandardCharsets.US_ASCII)); }
        }
        var census = RepeatFasta.read(path, RepeatFasta.Encoding.PLAIN_UTF8, limits(), () -> { });
        assertEquals(4194304, census.residues()); assertEquals(HexFormat.of().formatHex(digest.digest()), census.sequences().get(0).sequenceSha256());
        assertTrue(census.estimatedRetainedBytes() < 150000);
    }
    @Test void cancellationInsideLargePlainLineDoesNotAdmitAPrefix() throws Exception {
        Path path = file(">a\n" + "A".repeat(100000)); var calls = new AtomicInteger();
        var bounds = new RepeatFasta.Limits(200000, 200000, 200000, 200000, 2, 150000, 32, 4000000);
        assertThrows(CancellationException.class, () -> RepeatFasta.read(path, RepeatFasta.Encoding.PLAIN_UTF8, bounds, () -> {
            if (calls.incrementAndGet() > 15) throw new CancellationException("mid-line cancellation");
        }));
    }
}
