/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CancellationException;

/** Real native validation driver; not packaged or exposed as a user-analysis workflow. */
public final class StringTieRuntimeChecks {
    private StringTieRuntimeChecks() { }
    public static void main(String[] args)throws Exception {
        String mode = args[0], profile = args[1]; Path home = Path.of(args[2]), setup = Path.of(args[3]), work = Path.of(args[4]);
        var limits = StringTieRuntime.Limits.defaults();
        if (mode.equals("unsupported")) {
            try { StringTieRuntime.admit(profile, home, setup, work, limits, () -> { }); throw new AssertionError("Unsupported native host admitted"); }
            catch (StringTieRuntime.Unavailable expected) { require(!Files.exists(work), "Unsupported host created work"); System.out.println("EXPECTED_SKIP3_UNSUPPORTED_HOST"); return; }
        }
        if (mode.equals("admit") || mode.equals("tamper")) {
            var admitted = StringTieRuntime.admit(profile, home, setup, work, limits, () -> { }); admitted.verify(() -> { });
            require(admitted.profile().equals(profile) && !admitted.userAnalysisAdmitted() && admitted.home().equals(home) && !Files.exists(work.resolve("INCOMPLETE.json")), "Invalid admission token/scope/guard");
            if (mode.equals("tamper")) { Files.writeString(work.resolve("metadata/health.json"), "changed"); try { admitted.verify(() -> { }); throw new AssertionError("Changed evidence accepted"); } catch (IOException expected) { System.out.println("EXPECTED_CHANGED_ADMISSION_REJECTED"); } }
            else System.out.println("PASSED_FRESH_ORIGINAL_STRINGTIE_NATIVE_HEALTH " + admitted.version());
            return;
        }
        boolean unavailable = mode.equals("missing-home") || mode.equals("forbidden-home");
        Runnable check = () -> { };
        if (mode.equals("cancel")) check = () -> { if (Files.exists(work.resolve("metadata/request.json"))) throw new CancellationException("controlled cancellation after owned workspace creation"); };
        if (mode.equals("capacity")) limits = new StringTieRuntime.Limits(new RepeatHostFiles.Limits(1024, 1024, 4, 1, 1024), limits.process(), limits.setup(), 1);
        try { StringTieRuntime.admit(profile, home, setup, work, limits, check); throw new AssertionError("Invalid/cancelled/unavailable attempt admitted: " + mode); }
        catch (IOException | CancellationException expected) {
            require((expected instanceof StringTieRuntime.Unavailable) == unavailable, "Incorrect SKIP3/FAIL2 classification: " + expected);
            require(Files.exists(work.resolve("INCOMPLETE.json")) && !Files.exists(work.resolve("COMPLETED.json")), "Failure left a successful completion or lost guard");
            Map<?, ?> failure = NextflowFiles.object(NextflowFiles.json(work.resolve("FAILURE.json"), 1048576));
            require(failure.get("status").toString().startsWith(unavailable ? "SKIP3" : "FAIL2"), "Wrong retained failure classification");
            require(Files.readString(work.resolve("WARNINGS.md")).contains("NEW paths"), "Missing recovery instructions");
            System.out.println("EXPECTED_" + (unavailable ? "SKIP3" : "FAIL2") + " " + mode);
        }
    }
    private static void require(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
}
