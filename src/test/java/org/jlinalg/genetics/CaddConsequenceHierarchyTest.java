/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CaddConsequenceHierarchyTest {
    static final String HEADER = "#Chrom\tPos\tRef\tAlt\tType\tConsequence\tGrantham\tPolyPhenCat\tPolyPhenVal\tSIFTcat\tSIFTval\tRawScore\n";
    static final String NA = "NA\tNA\tNA\tNA\tNA";
    static String row(String position, String consequence, String gps, String score) { return "1\t" + position + "\tA\tC\tSNV\t" + consequence + "\t" + gps + "\t" + score + "\n"; }
    static CaddConsequenceHierarchy.Options options(boolean all, boolean noHeader, boolean phred) { return new CaddConsequenceHierarchy.Options(all, noHeader, phred, new CaddConsequenceHierarchy.Limits(10000000, 1000000, 100000, 1000)); }
    record Result(String text, CaddConsequenceHierarchy.Statistics statistics) { }
    static Result run(String text, boolean all, boolean noHeader, boolean phred) throws IOException {
        var output = new StringBuilder(); var statistics = CaddConsequenceHierarchy.transform(new StringReader(text), output, options(all, noHeader, phred)); return new Result(output.toString(), statistics);
    }
    @Test void consequencePriorityPrecedesNumericMaximum() throws Exception {
        String selected = row("01", "SYNONYMOUS", NA, "-5");
        var result = run(HEADER + row("01", "OTHER", NA, "999") + row("01", "REGULATORY", NA, "100") + selected, false, false, false);
        assertEquals(HEADER + selected, result.text()); assertEquals(3, result.statistics().inputRows()); assertEquals(1, result.statistics().consecutiveGroups());
    }
    @Test void sameTierKeepsFirstTieAndSignedZeroToken() throws Exception {
        String first = row("1", "SYNONYMOUS", NA, "-0.0");
        assertEquals(HEADER + first, run(HEADER + first + row("1", "STOP_GAINED", NA, "+0.0"), false, false, false).text());
    }
    @Test void nanDoesNotAcquireGenericSortedMaximumSemantics() throws Exception {
        String missing = row("1", "SYNONYMOUS", NA, "NaN"), finite = row("1", "STOP_GAINED", NA, "100");
        var firstNan = run(HEADER + missing + finite, false, false, false); assertEquals(HEADER + missing, firstNan.text()); assertEquals(1, firstNan.statistics().nonfiniteSelectedGroups());
        var laterNan = run(HEADER + finite + missing, false, false, false); assertEquals(HEADER + finite, laterNan.text()); assertEquals(0, laterNan.statistics().nonfiniteSelectedGroups()); assertTrue(laterNan.statistics().hasNonfiniteComparisons());
    }
    @Test void infinitiesRemainLiteralAndFirstInfiniteTieWins() throws Exception {
        String first = row("1", "INTRONIC", NA, "+Inf");
        var result = run(HEADER + row("1", "INTRONIC", NA, "-infinity") + first + row("1", "UPSTREAM", NA, "1e999"), false, false, false);
        assertEquals(HEADER + first, result.text()); assertEquals(3, result.statistics().nonfiniteInputScores()); assertEquals(1, result.statistics().nonfiniteSelectedGroups());
    }
    @Test void gpsTupleComesFromFirstAvailableRowAcrossTiers() throws Exception {
        String gps = "23\tpossibly_damaging\t0.1\tNA\tNA";
        String input = HEADER + row("1", "OTHER", gps, "100") + row("1", "SYNONYMOUS", NA, "1") + row("1", "OTHER", "99\tbenign\t0.9\ttolerated\t0.8", "900");
        assertEquals(HEADER + row("1", "SYNONYMOUS", gps, "1"), run(input, false, false, false).text());
    }
    @Test void allModePropagatesChosenScoreSuffixWithoutBorrowingOtherFields() throws Exception {
        String gps = "23\tbenign\t0.1\ttolerated\t0.2";
        var result = run(HEADER + row("1", "OTHER", gps, "999") + row("1", "SYNONYMOUS", NA, "-2"), true, false, false);
        assertEquals(HEADER + row("1", "OTHER", gps, "-2") + row("1", "SYNONYMOUS", NA, "-2"), result.text()); assertEquals(2, result.statistics().outputRows());
    }
    @Test void phredColumnSuffixAndSourceDefaultPositionsAreRetained() throws Exception {
        String header = HEADER.substring(0, HEADER.length() - 1) + "\tPHRED\n";
        String a = row("1", "SYNONYMOUS", NA, "1\t99"), b = row("1", "STOP_GAINED", NA, "2\t1");
        assertEquals(header + row("1", "SYNONYMOUS", NA, "2\t1") + b, run(header + a + b, true, false, true).text());
        String[] fields = new String[117]; Arrays.fill(fields, "NA"); fields[0] = "1"; fields[1] = "01"; fields[2] = "A"; fields[3] = "C"; fields[4] = "SNV"; fields[7] = "SYNONYMOUS"; fields[115] = "1"; fields[116] = "99"; String first = String.join("\t", fields) + "\n";
        fields[115] = "2"; fields[116] = "1"; String second = String.join("\t", fields) + "\n";
        assertEquals(second, run(first + second, false, true, true).text());
    }
    @Test void literalKeysAndNonconsecutiveRepeatedVariantsAreNotRepairedOrMerged() throws Exception {
        String a = row("01", "SYNONYMOUS", NA, "7"), b = row("1", "SYNONYMOUS", NA, "8"), c = row("01", "SYNONYMOUS", NA, "1");
        var result = run(HEADER + a + b + c, false, false, false); assertEquals(HEADER + a + b + c, result.text()); assertEquals(3, result.statistics().consecutiveGroups());
    }
    @Test void sourceHeaderEarlyBreakIsNotReplacedByAnUnconditionalColumnMap() throws Exception {
        String header = "#Chrom\tPos\tRef\tAlt\tType\tRawScore\tGrantham\tPolyPhenCat\tPolyPhenVal\tSIFTcat\tSIFTval\tConsequence\n";
        String a = "1\t1\tA\tC\tSNV\t1\tNA\tREGULATORY\tNA\tNA\tNA\tOTHER\n", b = "1\t1\tA\tC\tSNV\t999\tNA\tOTHER\tNA\tNA\tNA\tSYNONYMOUS\n";
        assertEquals(header + a, run(header + a + b, false, false, false).text());
    }
    @Test void headersPreserveCrLfAndDataUsesOriginalTrailingWhitespaceRules() throws Exception {
        String header = "## source café\r\n" + HEADER.replace("\n", "\r\n"), body = row("1", "SYNONYMOUS", NA, " 1.00");
        String input = header + body.substring(0, body.length() - 1) + " \t \r\n";
        assertEquals(header + body, run(input, false, false, false).text()); assertEquals(body, run(input, false, true, false).text());
    }
    @Test void malformedScoresAndMissingGpsFailWithoutSubstitutedValues() {
        for (String token : List.of("NA", "1f", "0x1.0p0", "1_0")) assertThrows(IOException.class, () -> run(HEADER + row("1", "SYNONYMOUS", NA, token), false, false, false));
        assertThrows(IOException.class, () -> run("#Chrom\tPos\tRef\tAlt\tType\tConsequence\tRawScore\n1\t1\tA\tC\tSNV\tSYNONYMOUS\t1\n", true, false, false));
    }
    @Test void wholeGroupCapacityFailureReturnsNoTruncatedSuccess() {
        var out = new StringBuilder(); var limits = new CaddConsequenceHierarchy.Limits(100000, 100000, 10000, 1);
        var options = new CaddConsequenceHierarchy.Options(true, false, false, limits);
        assertThrows(IOException.class, () -> CaddConsequenceHierarchy.transform(new StringReader(HEADER + row("1", "SYNONYMOUS", NA, "1") + row("1", "STOP_GAINED", NA, "2")), out, options));
        assertEquals(HEADER, out.toString());
    }
}
