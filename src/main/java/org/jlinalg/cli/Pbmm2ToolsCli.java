/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/** Original prepared-input mapping/indexing, complete captures and portable native arithmetic replay. */
public final class Pbmm2ToolsCli {
    private Pbmm2ToolsCli() { }
    static final String ACTION = "Required action: use only a session with COMPLETED.json and a successful --verify. Read WARNINGS.md and original process.stderr before downstream use. SKIP3/exit3 skips a dependent analysis when an exact prerequisite or allowed capability is missing: complete the named permitted setup separately, or retain the skip. No analysis installs or downloads software. FAIL2/exit2 admits no result: preserve the attempt, correct the named source/input/hash/runtime/process/numerical/capacity cause and use NEW output paths. Never restart a live or indeterminate job. Capacities cover whole inputs and outputs; no read/reference subset, identifier repair or fallback edition is selected. This regular-file snapshot profile skips symlinks rather than changing their meaning. Original FOFN/XML/JSON locator strings are bound at their literal declared paths. Declare every complete root, including recursively referenced inputs. CIGAR M follows the original match convention; metric equality does not certify nucleotide alignment against a reference, structural-variant calls, methylation tags, all nonmetric tags or biological accuracy. Unmapped/empty outputs remain explicit and support no negative biological inference. Hashes establish integrity, not authenticity. Saved --verify traverses all captured output BAMs and recomputes named contracts without rerunning alignment or admitting a host. --replay reruns the same request using saved whole inputs in a NEW Linux session. Private runtime/source dependencies are not a complete exact bundled-source closure; no whole-paper or private-original-runner/C++-suite reproduction is claimed. These Bioconda research binaries are not asserted identical to SMRT Link or clinically validated. Cell Ranger remains excluded.";
    static String warnings(String version) {
        String edition = version.equals("1.17.0") ? "WARNING: original1.17.0 passed106/109 unmodified public legacy functional cases; fastxinput_22/23/24 retain their three failures (expected7reads11994bases; actual9reads14102bases). The original release notes document changed HiFi DP filtering and discontinuation of corresponding public source updates. These observations do not certify complete binary/source equivalence.\n" : "Original1.13.1 passed109/109 public functional controls under explicitly selected public Cram0.7; the private original test runner and exact bundled dependency source closure remain unqualified.\n";
        return "# Original pbmm2 " + version + " - selected operation and CIGAR contracts\n\n" + edition + "\n" + ACTION + "\n";
    }
    public static void main(String[] args) { System.exit(run(args, System.out, System.err)); }
    static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            if (args.length == 0 || args.length == 1 && args[0].equals("--help")) { emit(out, "pbmm2-tools --request REQUEST.json --out NEW_LINUX_SESSION\npbmm2-tools --verify SAVED_SESSION\npbmm2-tools --replay SAVED_SESSION --out NEW_LINUX_SESSION\nOriginal1.13.1/1.17.0; explicit INDEX/ALIGN, full native prepared inputs, source options, capacities and private runtimes. See docs/pbmm2.md.\n" + ACTION); return out.checkError() ? 2 : 0; }
            if (args.length == 2 && args[0].equals("--verify")) { Path saved = Path.of(args[1]).toAbsolutePath().normalize(); var result = Pbmm2Bundle.verify(saved); emit(out, "Verified complete saved pbmm2 operation and native CIGAR contracts: " + NextflowJson.json(result) + "\n" + warnings(Pbmm2Request.read(saved.resolve("request.json")).version())); return out.checkError() ? 2 : 0; }
            var options = new LinkedHashMap<String, String>();
            for (int i = 0; i < args.length; i += 2) if (i + 1 >= args.length || !Set.of("--request", "--out", "--replay").contains(args[i]) || options.putIfAbsent(args[i], args[i + 1]) != null) throw new IOException("Require unique typed pbmm2 options; see --help");
            if (!options.containsKey("--out") || options.containsKey("--request") == options.containsKey("--replay")) throw new IOException("Select --request or --replay and a NEW --out session");
            Path result = Pbmm2Workflow.run(Path.of(options.get("--out")), options.containsKey("--request") ? Path.of(options.get("--request")) : null, options.containsKey("--replay") ? Path.of(options.get("--replay")) : null);
            emit(out, "Complete selected original pbmm2 operation and CIGAR contracts: " + result + "\n" + warnings(Pbmm2Request.read(result.resolve("request.json")).version())); return out.checkError() ? 2 : 0;
        } catch (Exception | LinkageError | OutOfMemoryError failure) { int code = Pbmm2Workflow.exit(failure); emit(err, (code == 3 ? "SKIP3 - ANALYSIS SKIPPED: " : "FAIL2 - NO RESULT ADMITTED: ") + failure + "\n" + ACTION); return code; }
    }
    private static void emit(PrintStream stream, String value) { stream.writeBytes((value + "\n").getBytes(StandardCharsets.UTF_8)); }
}
