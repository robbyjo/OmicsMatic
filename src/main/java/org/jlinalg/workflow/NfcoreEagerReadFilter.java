/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
 * Filtering contract derived from nf-core/eager's MIT filter_bam_fragment_length.py,
 * written by Maxime Borry. Retained original license: META-INF/nfcore-eager244/MIT-nf-core.txt.
 */
package org.jlinalg.workflow;

import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Native eager fragment-length helper. The source tests query sequence length,
 * including soft clips; it does not test TLEN, aligned span or inferred fragments.
 * It iterates all records in input order, including secondary/supplementary reads.
 * MAPQ filtering is a separate upstream samtools step, not part of this helper.
 */
public final class NfcoreEagerReadFilter {
    private NfcoreEagerReadFilter() { }

    /** Source-compatible decision for one already decoded BAM record.
     * Negative thresholds are permitted by the original integer argument parser.
     */
    public static boolean keep(int queryLength, boolean unmapped, long minimumLength,
            boolean includeUnmapped) {
        if (queryLength < 0) throw new IllegalArgumentException("Decoded query length must be nonnegative");
        return (includeUnmapped || !unmapped) && queryLength >= minimumLength;
    }

    public record Result(long inputRecords, long outputRecords, long inputUnmappedRecords,
            long outputUnmappedRecords, long minimumLength, boolean includeUnmapped) { }

    /** Write a NEW BAM, retaining record order, flags, tags and the input header.
     * Compression/encoding may differ from pysam; semantic records must agree.
     * Existing outputs are never overwritten. A failure retains the partial new
     * output and admits no Result. CRC/strict BAM parsing and the complete-record
     * capacity apply; a capacity limit never truncates an admitted analysis.
     */
    public static Result filterBam(Path input, Path newOutput, long minimumLength,
            boolean includeUnmapped, long maximumRecords) throws IOException {
        Objects.requireNonNull(input, "input"); Objects.requireNonNull(newOutput, "newOutput");
        if (maximumRecords < 1) throw new IllegalArgumentException("Require a positive complete-record capacity");
        long inputRecords = 0, outputRecords = 0, inputUnmapped = 0, outputUnmapped = 0;
        try (var reader = SamReaderFactory.makeDefault()
                    .enable(SamReaderFactory.Option.VALIDATE_CRC_CHECKSUMS)
                    .validationStringency(ValidationStringency.STRICT).open(input);
                var stream = Files.newOutputStream(newOutput, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                var writer = new SAMFileWriterFactory().setCreateIndex(false).setCreateMd5File(false)
                    .makeBAMWriter(reader.getFileHeader().clone(), true, stream)) {
            for (var record : reader) {
                if (inputRecords == maximumRecords) throw new IOException("Complete input record capacity exceeded");
                inputRecords++; boolean unmapped = record.getReadUnmappedFlag();
                if (unmapped) inputUnmapped++;
                if (keep(record.getReadLength(), unmapped, minimumLength, includeUnmapped)) {
                    writer.addAlignment(record); outputRecords++; if (unmapped) outputUnmapped++;
                }
            }
        } catch (RuntimeException failure) {
            throw new IOException("EAGER BAM FILTER FAIL2: " + failure
                + ". Required action: retain the original input and partial new output; resolve malformed records or writer failure before retrying a NEW path. No result admitted.", failure);
        } catch (IOException failure) {
            throw new IOException("EAGER BAM FILTER FAIL2: " + failure
                + ". Required action: retain the original input and partial new output; resolve the named path/capacity/integrity error before retrying a NEW path. No result admitted.", failure);
        }
        return new Result(inputRecords, outputRecords, inputUnmapped, outputUnmapped,
            minimumLength, includeUnmapped);
    }
}
