/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Map;

/** Lossless original metadata numbers, including unused uint64 index sentinels. */
final class SalmonMetadata {
    private SalmonMetadata() { }
    private static final BigInteger UINT64_MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    static Map<?, ?> read(Path path, int maximumBytes, int maximumNodes) throws IOException {
        return parse(NextflowFiles.utf8(NextflowFiles.bytes(path, maximumBytes)), maximumBytes, maximumNodes);
    }
    static Map<?, ?> parse(String text, int maximumCharacters, int maximumNodes) throws IOException {
        return NextflowFiles.object(NextflowParameters.parse(text, maximumCharacters, maximumNodes));
    }
    static BigInteger unsigned(Object value) throws IOException {
        String token = number(value);
        if (token.length() > 20 || !token.matches("(?:0|[1-9][0-9]*)")) throw new IOException("Expected exact original Salmon uint64 metadata token");
        BigInteger integer = new BigInteger(token);
        if (integer.compareTo(UINT64_MAX) > 0) throw new IOException("Original Salmon metadata exceeds uint64 capacity");
        return integer;
    }
    static long integer(Object value, long maximum) throws IOException {
        if (maximum < 0) throw new IOException("Nonnegative Salmon metadata integer bound is required");
        BigInteger integer = unsigned(value);
        if (integer.compareTo(BigInteger.valueOf(maximum)) > 0) throw new IOException("Original Salmon metadata integer exceeds its declared bound");
        return integer.longValueExact();
    }
    static BigDecimal decimal(Object value) throws IOException {
        String token = number(value); if (token.length() > 1024) throw new IOException("Original Salmon metadata decimal exceeds token capacity");
        try { return new BigDecimal(token); } catch (NumberFormatException invalid) { throw new IOException("Original Salmon metadata decimal exceeds exact scale capacity", invalid); }
    }
    private static String number(Object value) throws IOException {
        if (!(value instanceof NextflowParameters.NumberToken token)) throw new IOException("Expected an original numeric Salmon metadata token"); return token.text();
    }
}
