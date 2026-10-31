/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

/**
 * Exact bytes consumed by a complete native RSEM reader; a digest is integrity,
 * not a claim about the source's authenticity or scientific appropriateness.
 * @param path resolved source path
 * @param sha256 SHA-256 of all consumed bytes
 * @param bytes complete byte count
 */
public record RsemEvidence(String path, String sha256, long bytes) {
    /**
     * Validates the evidence description.
     * @param path resolved source path
     * @param sha256 lowercase SHA-256
     * @param bytes source bytes
     */
    public RsemEvidence {
        if (path == null || path.isEmpty() || sha256 == null || !sha256.matches("[0-9a-f]{64}") || bytes < 0)
            throw new IllegalArgumentException("Invalid RSEM source evidence");
    }
}
