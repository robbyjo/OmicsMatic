/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

/** Original hyphen-delimited Mbp option and independent printed source-range check. */
final class RfmixRange {
    private RfmixRange() { }
    private static final String PREFIX = "NOTICE: Analysis restricted to positions in range ";
    private static final Pattern NOTICE = Pattern.compile(Pattern.quote(PREFIX) + "(-?[0-9]+) to (-?[0-9]+)");

    static String argument(Map<?,?> range) throws IOException {
        keys(range, "start_mbp", "end_mbp");
        double start = RfmixRequest.real(range.get("start_mbp"), 0, 2147.483647, true);
        double end = RfmixRequest.real(range.get("end_mbp"), 0, 2147.483647, true);
        require(start <= end && start * 1e6 <= Integer.MAX_VALUE && end * 1e6 <= Integer.MAX_VALUE,
                "Source Mbp-to-int range exceeds ordered signed-coordinate domain");
        // Plain round-trip decimal preserves the validated binary64 values. An exponent
        // minus would instead be consumed by the original first-hyphen range parser.
        return "--analyze-range=" + decimal(start) + "-" + decimal(end);
    }

    private static String decimal(double value) {
        return value == 0 ? "0" : BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    static Map<String,Object> auditSourceNotice(String log, boolean restricted, int[] expected) throws IOException {
        require(expected.length == 2, "Explicit two-coordinate native range required");
        String notice = null;
        int[] actual = null;
        for (String line : log.split("\\r?\\n", -1)) {
            if (!line.startsWith(PREFIX)) continue;
            require(notice == null, "Repeated original restricted-range notice; preserve original stderr and investigate");
            var match = NOTICE.matcher(line);
            require(match.matches(), "Malformed original restricted-range notice");
            try { actual = new int[]{Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2))}; }
            catch (NumberFormatException e) { throw new IOException("Original range notice exceeds signed coordinate domain", e); }
            notice = line;
        }
        require(restricted == (notice != null), "Original range restriction presence differs from typed request; inspect original stderr");
        if (restricted) require(Arrays.equals(actual, expected),
                "Original parsed range differs from native Mbp-to-bp truncation. No result admitted; preserve stderr and retry corrected NEW inputs/settings");
        return map("restriction_requested", restricted, "original_notice", notice,
                "source_internal_effective_range_bp", restricted ? List.of(actual[0], actual[1]) : List.of(Integer.MIN_VALUE, Integer.MAX_VALUE),
                "positive_VCF_selection_domain_bp", List.of(expected[0], expected[1]),
                "format", "plain round-trip decimal Mbp with one original hyphen delimiter; original binary64 multiplication and signed-int truncation unchanged");
    }
}
