/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Complete native accounting of original parse-alignments .cnt output. */
public final class RsemCounts {
    private RsemCounts() { }
    /**
     * One original positive finite mapping-multiplicity bin.
     * @param mappings alignments per read group
     * @param readGroups original group count
     */
    public record Bin(long mappings, long readGroups) { }
    /**
     * Complete original counts and histogram; no read/fragment unit conversion occurs.
     * @param unalignableGroups original N0 bucket
     * @param alignableGroups original N1 bucket
     * @param excludedGroups original N2/Inf bucket
     * @param totalGroups exact N0+N1+N2
     * @param uniqueGeneGroups original nUnique count
     * @param multipleGeneGroups original nMulti count
     * @param multipleIsoformGroups original nIsoMulti: groups with more than one alignment, including cross-gene groups
     * @param alignmentHits original complete hit count
     * @param readType original model/read type 0/1/2/3
     * @param histogram complete positive finite bins in original increasing order
     * @param evidence complete source bytes
     */
    public record Counts(long unalignableGroups, long alignableGroups, long excludedGroups, long totalGroups,
                         long uniqueGeneGroups, long multipleGeneGroups, long multipleIsoformGroups,
                         long alignmentHits, int readType, List<Bin> histogram, RsemEvidence evidence) {
        /**
         * Copies the complete histogram.
         * @param unalignableGroups N0
         * @param alignableGroups N1
         * @param excludedGroups N2
         * @param totalGroups complete group count
         * @param uniqueGeneGroups nUnique
         * @param multipleGeneGroups nMulti
         * @param multipleIsoformGroups nIsoMulti
         * @param alignmentHits nHits
         * @param readType original read type
         * @param histogram complete multiplicities
         * @param evidence source evidence
         */
        public Counts {histogram = List.copyOf(histogram);}
    }
    /**
     * Reads every count and histogram row and checks all independently reported
     * totals using exact long arithmetic. A group is a read or a fragment as
     * dictated by the original read type, never an assumed paired-read count.
     * @param path original .cnt file
     * @param maxReadGroups complete read-group capacity
     * @param maxHits complete alignment-hit capacity
     * @param maxBins complete histogram capacity
     * @param maxBytes source byte capacity
     * @param maxLineChars line capacity
     * @return complete mutually consistent original counts
     * @throws IOException malformed, truncated, over-capacity or inconsistent source
     */
    public static Counts read(Path path, long maxReadGroups, long maxHits, int maxBins, long maxBytes, int maxLineChars) throws IOException {
        if (maxReadGroups < 1 || maxHits < 1 || maxBins < 1) throw new IllegalArgumentException("Positive count capacities required");
        try (RsemText input = new RsemText(path, maxBytes, maxLineChars)) {
            String[] first = fields(input, "N0/N1/N2/total", 4);
            long n0 = number(input, first[0], maxReadGroups), n1 = number(input, first[1], maxReadGroups), n2 = number(input, first[2], maxReadGroups), total = number(input, first[3], maxReadGroups);
            String[] second = fields(input, "gene/isoform groups", 3);
            long unique = number(input, second[0], maxReadGroups), multi = number(input, second[1], maxReadGroups), iso = number(input, second[2], maxReadGroups);
            String[] third = fields(input, "hits/read type", 2);
            long hits = number(input, third[0], maxHits); int type = (int) input.integer(third[1], "original read type", 0, 3);
            String[] zero = fields(input, "zero-mapping bucket", 2);
            if (!zero[0].equals("0") || number(input, zero[1], maxReadGroups) != n0) throw input.error("Zero histogram bucket differs from N0");
            List<Bin> bins = new ArrayList<>(); long previous = 0, groups = 0, weightedHits = 0, multipleAlignmentGroups = 0; boolean terminal = false;
            String line;
            while ((line = input.line()) != null) {
                String[] row = RsemText.words(line);
                if (row.length != 2) throw input.error("Incomplete mapping histogram row");
                long count = number(input, row[1], maxReadGroups);
                if (row[0].equals("Inf")) {
                    if (count != n2) throw input.error("Inf histogram bucket differs from N2");
                    terminal = true; input.endLines(); break;
                }
                long mappings = input.integer(row[0], "mapping multiplicity", 1, maxHits);
                if (mappings <= previous || count == 0 || bins.size() >= maxBins) throw input.error("Duplicate, unordered, empty or over-capacity mapping bin");
                try {
                    groups = Math.addExact(groups, count); weightedHits = Math.addExact(weightedHits, Math.multiplyExact(mappings, count));
                    if (mappings > 1) multipleAlignmentGroups = Math.addExact(multipleAlignmentGroups, count);
                }
                catch (ArithmeticException e) {throw input.error("Exact mapping histogram count overflow");}
                bins.add(new Bin(mappings, count)); previous = mappings;
            }
            if (!terminal) throw input.error("Missing terminal Inf histogram bucket");
            try {
                if (Math.addExact(Math.addExact(n0, n1), n2) != total || Math.addExact(unique, multi) != n1
                        || iso != multipleAlignmentGroups || multi > iso || groups != n1 || weightedHits != hits) throw input.error("Original alignment/group totals disagree");
            } catch (ArithmeticException e) {throw input.error("Exact complete count overflow");}
            return new Counts(n0, n1, n2, total, unique, multi, iso, hits, type, bins, input.evidence());
        }
    }
    private static String[] fields(RsemText input, String role, int size) throws IOException {
        String[] values = RsemText.words(input.requiredLine(role)); if (values.length != size) throw input.error("Invalid " + role + " field population"); return values;
    }
    private static long number(RsemText input, String value, long maximum) throws IOException {return input.integer(value, "original group/hit count", 0, maximum);}
}
