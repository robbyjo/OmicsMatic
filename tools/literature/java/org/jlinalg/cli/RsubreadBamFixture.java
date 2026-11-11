/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Test-fixture encoding only. Complete decoded alignment fields must remain equal. */
public final class RsubreadBamFixture {
    private RsubreadBamFixture() { }
    private static String literal(SAMRecord record) {
        var fields = new ArrayList<>(Arrays.asList(record.getSAMString().stripTrailing().split("\t", -1)));
        List<String> tags = new ArrayList<>(fields.subList(11, fields.size())); Collections.sort(tags);
        return String.join("\t", fields.subList(0, 11)) + "\t" + String.join("\t", tags);
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Require original SAM and NEW BAM");
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        if (Files.exists(output)) throw new IllegalArgumentException("BAM output must be NEW");
        var factory = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.STRICT);
        var before = new ArrayList<String>();
        try (var reader = factory.open(input); var writer = new SAMFileWriterFactory().makeBAMWriter(reader.getFileHeader(), false, output.toFile())) {
            for (SAMRecord record : reader) { before.add(literal(record)); writer.addAlignment(record); }
        }
        var after = new ArrayList<String>();
        try (var reader = factory.open(output)) { for (SAMRecord record : reader) after.add(literal(record)); }
        if (!before.equals(after)) throw new AssertionError("BAM encoding changed literal alignments or order");
        System.out.println("PASS_COMPLETE_SAM_BAM_RECORDS_AND_ORDER " + before.size());
    }
}
