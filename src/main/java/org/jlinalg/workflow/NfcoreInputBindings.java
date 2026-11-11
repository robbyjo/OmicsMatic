/* Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Predictable POSIX bindings for one explicitly declared common Nextflow source root.
 * The complete materialized root must be declared as a resource, including the
 * original workflow, samplesheet, reads, references and runtime-only config.
 * All original child roots then reduce to this single captured root, r0.
 * This is path planning only: it does not inspect files, certify provenance,
 * rename identifiers, install dependencies or admit a scientific result.
 */
public final class NfcoreInputBindings {
    private NfcoreInputBindings() { }

    /** Immutable producer and capture path relation; paths describe Linux execution. */
    public record Layout(String engineSession, String materializedRoot, String capturedRoot) {
        /** Validate public construction against the same declared single-root relation. */
        public Layout {
            String leaf = validateRoots(engineSession, materializedRoot);
            if (!Objects.equals(capturedRoot, engineSession + "/workspace/resources/r0/" + leaf))
                throw new IllegalArgumentException("Captured root does not match the explicit single-root binding");
        }
        /** Exact captured location of an unchanged relative member name. */
        public String captured(String relative) {
            List<String> parts = parts(relative, false);
            return capturedRoot + (parts.isEmpty() ? "" : "/" + String.join("/", parts));
        }
        /** Original materialized location; source files are retained separately from their capture. */
        public String original(String relative) {
            List<String> parts = parts(relative, false);
            return materializedRoot + (parts.isEmpty() ? "" : "/" + String.join("/", parts));
        }
    }

    /** Plan bindings only when the explicit common source root and session are disjoint. */
    public static Layout plan(String engineSession, String materializedRoot) {
        String leaf = validateRoots(engineSession, materializedRoot);
        return new Layout(engineSession, materializedRoot,
            engineSession + "/workspace/resources/r0/" + leaf);
    }
    private static String validateRoots(String engineSession, String materializedRoot) {
        List<String> session = parts(engineSession, true);
        List<String> source = parts(materializedRoot, true);
        if (session.isEmpty() || source.isEmpty()) throw new IllegalArgumentException("Require named producer/source roots");
        if (prefix(session, source) || prefix(source, session))
            throw new IllegalArgumentException("Engine session and common source root must be disjoint");
        if (engineSession.codePoints().anyMatch(c -> c > 127 || Character.isWhitespace(c) || c == ','))
            throw new IllegalArgumentException("Require a new ASCII engine session path without whitespace or commas");
        return source.get(source.size() - 1);
    }

    private static boolean prefix(List<String> a, List<String> b) {
        return a.size() <= b.size() && b.subList(0, a.size()).equals(a);
    }
    private static List<String> parts(String path, boolean absolute) {
        Objects.requireNonNull(path, "path");
        if (path.codePoints().anyMatch(c -> c < 32 || c == 127) || path.indexOf('\\') >= 0)
            throw new IllegalArgumentException("Require literal POSIX paths without control characters or backslashes");
        if (path.startsWith("/") != absolute || path.endsWith("/") || path.contains("//"))
            throw new IllegalArgumentException("Require canonical named POSIX paths");
        if (path.isEmpty()) {
            if (absolute) throw new IllegalArgumentException("Require an absolute path");
            return List.of();
        }
        var result = new ArrayList<String>();
        for (String part : (absolute ? path.substring(1) : path).split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals(".."))
                throw new IllegalArgumentException("No empty, dot or parent path members are permitted");
            result.add(part);
        }
        return List.copyOf(result);
    }
}
