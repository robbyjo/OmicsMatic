/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipInputStream;
import org.jlinalg.sequence.SalmonQuantification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SalmonAlignmentsTest {
    @TempDir Path temporary;
    private static final String HEADER = "@HD\tVN:1.6\tSO:unsorted\n@SQ\tSN:a.1\tLN:100\n@SQ\tSN:b.2\tLN:200\n";
    private static final List<SalmonQuantification.Target> TARGETS = List.of(new SalmonQuantification.Target("a.1", 100), new SalmonQuantification.Target("b.2", 200));
    private static final SalmonAlignments.Limits LIMITS = new SalmonAlignments.Limits(20000000, 40000000, 100000, 100000, 10000, 1000, 10000, 65536, 1000000, 1000000, 20000000);
    @Test void completeSingleQueryGroupsIncludeUnmappedAndSecondaryRecords() throws Exception {
        Path input = sam(HEADER + single("one", 0, "4M") + single("one", 256, "4M") + "two\t4\t*\t0\t0\t*\t*\t0\t0\tACGT\tIIII\n");
        var result = inspect(input, SalmonQuantRecipe.Format.SAM, SalmonQuantRecipe.Layout.SINGLE); assertEquals(3, result.records()); assertEquals(2, result.fragments()); assertEquals(1, result.mappedFragments()); assertEquals(1, result.unmappedFragments()); assertEquals(1, result.secondaryRecords()); result.verify(() -> { });
    }
    @Test void completePairedHitsCountFragmentsRatherThanPhysicalMateRows() throws Exception {
        var result = inspect(sam(HEADER + pair("one") + pair("two")), SalmonQuantRecipe.Format.SAM, SalmonQuantRecipe.Layout.PAIRED); assertEquals(4, result.records()); assertEquals(2, result.fragments()); assertEquals(2, result.mappedFragments());
    }
    @Test void bamConversionRetainsCompletePopulationAndPhysicalCrcEnd() throws Exception {
        Path input = bam(sam(HEADER + pair("one") + pair("two"))); var result = inspect(input, SalmonQuantRecipe.Format.BAM, SalmonQuantRecipe.Layout.PAIRED); assertEquals(4, result.records()); assertEquals(2, result.fragments()); assertTrue(result.decodedBytes() > 0);
    }
    @ParameterizedTest @ValueSource(strings={"crc", "truncated-footer", "trailing-junk"})
    void damagedOrExtraPhysicalBamBytesCannotPassOnAValidRecordPrefix(String change) throws Exception {
        Path input = bam(sam(HEADER + pair("one"))); byte[] raw = Files.readAllBytes(input);
        if (change.equals("crc")) raw[raw.length - 5] ^= 1;
        else if (change.equals("truncated-footer")) raw = java.util.Arrays.copyOf(raw, raw.length - 3);
        else { raw = java.util.Arrays.copyOf(raw, raw.length + 1); raw[raw.length - 1] = 1; }
        Files.write(input, raw); assertThrows(IOException.class, () -> inspect(input, SalmonQuantRecipe.Format.BAM, SalmonQuantRecipe.Layout.PAIRED));
    }
    @Test void hostileBamHeaderLengthIsRejectedBeforeLargeHtsjdkAllocation() throws Exception {
        var bytes = new ByteArrayOutputStream(); try (var gzip = new GZIPOutputStream(bytes)) { gzip.write(new byte[]{'B', 'A', 'M', 1, -1, -1, -1, 127}); }
        Path input = temporary.resolve("hostile.bam").toAbsolutePath(); Files.write(input, bytes.toByteArray()); assertThrows(IOException.class, () -> inspect(input, SalmonQuantRecipe.Format.BAM, SalmonQuantRecipe.Layout.SINGLE));
    }
    @Test void noncontiguousQueryCannotBeCountedAsAnotherFragment() throws Exception {
        Path input = sam(HEADER + single("one", 0, "4M") + single("two", 0, "4M") + single("one", 256, "4M")); assertThrows(IOException.class, () -> inspect(input, SalmonQuantRecipe.Format.SAM, SalmonQuantRecipe.Layout.SINGLE));
    }
    @Test void incompleteAndReversedAdjacentMatePairsFail() throws Exception {
        String[] rows = pair("one").split("\n");
        for (String body : List.of(rows[0] + "\n", rows[1] + "\n" + rows[0] + "\n", rows[0] + "\n" + rows[0] + "\n")) {
            Path input = sam(HEADER + body); assertThrows(IOException.class, () -> inspect(input, SalmonQuantRecipe.Format.SAM, SalmonQuantRecipe.Layout.PAIRED));
        }
    }
    @Test void genomicSplicedCigarCannotMasqueradeAsTranscriptomeAlignment() throws Exception {
        Path input = sam(HEADER + single("one", 0, "2M10N2M")); assertThrows(IOException.class, () -> inspect(input, SalmonQuantRecipe.Format.SAM, SalmonQuantRecipe.Layout.SINGLE));
    }
    @Test void dictionaryMustMatchEveryVersionedTranscriptInOrderAndLength() throws Exception {
        for (String header : List.of(HEADER.replace("a.1", "a"), HEADER.replace("LN:100", "LN:101"), HEADER.replace("@SQ\tSN:b.2\tLN:200\n", ""))) {
            Path input = sam(header + single("one", 0, "4M")); assertThrows(IOException.class, () -> inspect(input, SalmonQuantRecipe.Format.SAM, SalmonQuantRecipe.Layout.SINGLE));
        }
    }
    @Test void missingSequenceMalformedLateRowAndBlankLinesFailCompleteInput() throws Exception {
        for (String body : List.of(single("one", 0, "4M") + "broken\t0\n", single("one", 0, "4M").replace("ACGT\tIIII", "*\t*"), single("one", 0, "4M") + "\n")) {
            Path input = sam(HEADER + body); assertThrows(IOException.class, () -> inspect(input, SalmonQuantRecipe.Format.SAM, SalmonQuantRecipe.Layout.SINGLE));
        }
    }
    @Test void wrongDeclaredBinaryFormatNeverTriggersAutomaticReplacement() throws Exception {
        Path input = sam(HEADER + pair("one")); assertThrows(IOException.class, () -> inspect(input, SalmonQuantRecipe.Format.BAM, SalmonQuantRecipe.Layout.PAIRED));
        Path binary = bam(input); assertThrows(IOException.class, () -> inspect(binary, SalmonQuantRecipe.Format.SAM, SalmonQuantRecipe.Layout.PAIRED));
    }
    @Test void capacityAndCancellationDoNotReturnAPassingPrefix() throws Exception {
        Path input = sam(HEADER + single("one", 0, "4M") + single("two", 0, "4M"));
        var limits = new SalmonAlignments.Limits(20000000, 40000000, 1, 100, 100, 100, 100, 1000, 10000, 10000, 1000000);
        assertThrows(IOException.class, () -> SalmonAlignments.inspect(input, SalmonQuantRecipe.Format.SAM, SalmonQuantRecipe.Layout.SINGLE, TARGETS, limits, () -> { }));
        assertThrows(CancellationException.class, () -> SalmonAlignments.inspect(input, SalmonQuantRecipe.Format.SAM, SalmonQuantRecipe.Layout.SINGLE, TARGETS, LIMITS, () -> { throw new CancellationException(); }));
    }
    @Test void changedCompleteSourcePinIsRejected() throws Exception {
        Path input = sam(HEADER + single("one", 0, "4M")); var result = inspect(input, SalmonQuantRecipe.Format.SAM, SalmonQuantRecipe.Layout.SINGLE); Files.writeString(input, Files.readString(input).replace("ACGT", "TGCA")); assertThrows(IOException.class, () -> result.verify(() -> { }));
    }
    @ParameterizedTest @ValueSource(booleans={false, true})
    void completeOriginalControlledSamAndItsBamConversionPreserveAllRecordsAndFragments(boolean converted) throws Exception {
        Path area = Files.createTempDirectory(temporary, "original-").toAbsolutePath();
        try (var zip = new ZipInputStream(getClass().getResourceAsStream("/org/jlinalg/cli/salmon-alignment-inputs/original-alignment-inputs.zip"))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) { NextflowFiles.member(entry.getName()); Files.write(area.resolve(entry.getName()), zip.readAllBytes()); zip.closeEntry(); }
        }
        var fasta = RepeatFasta.read(area.resolve("transcripts.fa"), RepeatFasta.Encoding.PLAIN_UTF8, new RepeatFasta.Limits(1000000, 1000000, 1000000, 10000, 100, 10000, 1000, 4000000), () -> { });
        var targets = fasta.sequences().stream().map(r -> new SalmonQuantification.Target(r.id(), r.length())).toList(); Path input = area.resolve("alignments.sam"); if (converted) input = bam(input);
        var result = SalmonAlignments.inspect(input, converted ? SalmonQuantRecipe.Format.BAM : SalmonQuantRecipe.Format.SAM, SalmonQuantRecipe.Layout.PAIRED, targets, LIMITS, () -> { });
        assertEquals(40, targets.size()); assertEquals(24240, result.records()); assertEquals(8520, result.fragments()); assertEquals(8520, result.mappedFragments()); assertEquals(0, result.unmappedFragments());
    }
    private SalmonAlignments.Context inspect(Path input, SalmonQuantRecipe.Format format, SalmonQuantRecipe.Layout layout) throws IOException { return SalmonAlignments.inspect(input, format, layout, TARGETS, LIMITS, () -> { }); }
    private Path sam(String text) throws IOException { Path file = Files.createTempFile(temporary, "input-", ".sam").toAbsolutePath(); Files.writeString(file, text, StandardCharsets.UTF_8); return file; }
    private Path bam(Path sam) throws IOException {
        Path output = Files.createTempFile(temporary, "input-", ".bam").toAbsolutePath();
        try (var reader = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT).open(sam); var writer = new SAMFileWriterFactory().makeBAMWriter(reader.getFileHeader(), true, output); var records = reader.iterator()) { while (records.hasNext()) writer.addAlignment(records.next()); }
        return output;
    }
    private static String single(String name, int flag, String cigar) { return name + "\t" + flag + "\ta.1\t1\t60\t" + cigar + "\t*\t0\t0\tACGT\tIIII\n"; }
    private static String pair(String name) { return name + "\t99\ta.1\t1\t60\t4M\t=\t10\t13\tACGT\tIIII\n" + name + "\t147\ta.1\t10\t60\t4M\t=\t1\t-13\tACGT\tIIII\n"; }
}
