/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.study;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/** Shared writing instructions and exact identity for draft, revision and cache provenance. */
public final class StudyManuscriptPolicy {
    public static final String VERSION = "2026-11-14.6";
    private static final String RESOURCE = "manuscript-writing-v1.txt";
    private static final String INSTRUCTIONS = load();
    private StudyManuscriptPolicy() { }

    private static String load() {
        try (var input = StudyManuscriptPolicy.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing manuscript writing policy resource");
            // Normalize checkout line endings so a source and packaged build have the same identity.
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException failure) { throw new ExceptionInInitializerError(failure); }
    }

    public static String instructions() { return INSTRUCTIONS; }

    public static Map<String, Object> identity() {
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(INSTRUCTIONS.getBytes(StandardCharsets.UTF_8)));
            return Map.of("template_id", "omicsmatic-research-manuscript", "version", VERSION,
                "prompt_sha256", hash, "resource", RESOURCE);
        } catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
}
