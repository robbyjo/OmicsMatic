/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.util.Objects;

/** Native fixed-span clipping, independently implemented using one-based inclusive coordinates. */
public final class FixedReadTrimmer {
    private FixedReadTrimmer() { }

    /** lastBase=0 keeps through the original end; short/empty retained spans are filtered. */
    public record Options(int firstBase, int lastBase, int minimumLength) {
        public Options {
            if (firstBase < 1 || firstBase > 10_000_000 || lastBase < 0 || lastBase > 10_000_000
                    || lastBase != 0 && lastBase < firstBase || minimumLength < 1 || minimumLength > 10_000_000)
                throw new IllegalArgumentException("Require firstBase >=1, lastBase=0 or >=firstBase, and minimumLength >=1 (all <=10 million)");
        }
    }

    /**
     * Clip sequence and qualities together, preserving the complete header.
     * Returns null when the requested span is absent or below minimumLength.
     * Fixed positions refer to this input read, making a separate post-adapter
     * invocation distinct from a pre-adapter fixed cut inside an upstream tool.
     */
    public static FastqIO.Read clip(FastqIO.Read read, Options options) {
        Objects.requireNonNull(read, "read"); Objects.requireNonNull(options, "options");
        if (read.sequence().length() != read.quality().length()) throw new IllegalArgumentException("Sequence/quality length mismatch");
        int start = options.firstBase() - 1;
        int end = options.lastBase() == 0 ? read.sequence().length() : Math.min(options.lastBase(), read.sequence().length());
        if (end - start < options.minimumLength()) return null;
        return new FastqIO.Read(read.header(), read.sequence().substring(start, end), read.quality().substring(start, end));
    }
}
