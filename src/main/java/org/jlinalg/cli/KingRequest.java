/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.NextflowFiles.*;

/** Explicit original KING analysis, with no arbitrary flags, installations or input transformations. */
final class KingRequest {
    private KingRequest() { }
    record Limits(long inputBytes, long outputBytes, int outputFiles, int samples, int variants,
                  long pairVariantOperations, long workingBytes, int wallSeconds, int rows) { }
    record Request(String mode, int degree, int threads, Path bfile, Path profile, Path out, Limits limits) { }
    static void need(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }
    static Request parse(Object value, Path base) throws IOException {
        Map<?,?> m = object(value);
        keys(m, "schema_version", "mode", "degree", "threads", "bfile", "runtime_profile", "out", "limits");
        integer(m.get("schema_version"), 1, 1);
        String mode = text(m.get("mode"));
        need(Set.of("kinship", "unrelated", "ibdseg", "duplicate").contains(mode), "Unsupported explicit KING mode");
        int degree = (int)integer(m.get("degree"), 0, 3);
        need(mode.equals("kinship") ? Set.of(0, 2, 3).contains(degree)
                : mode.equals("unrelated") ? Set.of(2, 3).contains(degree) : degree == 0,
                "Tested degrees: kinship 0/2/3, unrelated 2/3; ibdseg/duplicate require degree0");
        Map<?,?> l = object(m.get("limits"));
        keys(l, "input_bytes", "output_bytes", "output_files", "samples", "variants",
                "pair_variant_operations", "working_bytes", "wall_seconds", "rows");
        Limits limits = new Limits(integer(l.get("input_bytes"), 1, 100_000_000_000L),
                integer(l.get("output_bytes"), 1, 100_000_000_000L),
                (int)integer(l.get("output_files"), 1, 100_000),
                (int)integer(l.get("samples"), 2, 50_000),
                (int)integer(l.get("variants"), 1, Integer.MAX_VALUE),
                integer(l.get("pair_variant_operations"), 1, Long.MAX_VALUE),
                integer(l.get("working_bytes"), 1, Long.MAX_VALUE),
                (int)integer(l.get("wall_seconds"), 1, 604_800),
                (int)integer(l.get("rows"), 1, Integer.MAX_VALUE-1));
        return new Request(mode, degree, (int)integer(m.get("threads"), 1, 64),
                path(base, m.get("bfile")), path(base, m.get("runtime_profile")), path(base, m.get("out")), limits);
    }
    private static Path path(Path base, Object value) throws IOException {
        String s = text(value);
        need(!s.isEmpty() && s.chars().noneMatch(c -> c < 32 || c == 127), "Invalid KING path");
        return base.resolve(s).toAbsolutePath().normalize();
    }
}
