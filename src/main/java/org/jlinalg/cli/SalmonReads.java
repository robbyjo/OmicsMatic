/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Complete unchanged four-line FASTQ census; source quality strings are validated, not interpreted. */
final class SalmonReads {
    private SalmonReads() { }
    enum Alphabet { IUPAC_DNA_RNA_OR_X, LITERAL_SOURCE_ASCII }
    enum Pairing { EXACT_SOURCE_TOKEN, TERMINAL_MATE_SUFFIX, CASAVA_ROLES, SOURCE_ORDINAL_UNVERIFIED }
    record Limits(FastpFastq.Limits file, long combinedEncodedBytes, long combinedBases) {
        Limits { Objects.requireNonNull(file); if (combinedEncodedBytes < 1 || combinedBases < 1) throw new IllegalArgumentException("Positive combined complete Salmon read capacities are required"); }
    }
    record Statistics(long reads, long bases, int minimumLength, int maximumLength, long encodedBytes, long decodedCharacters,
                      boolean gzip, boolean unterminatedFinalLine, long basesOutsideUpperAcgtn, long nonemptyPlusLines) { }
    record Context(Path first, Path second, Limits limits, Alphabet alphabet, Pairing pairing, NextflowFiles.Pin firstPin, NextflowFiles.Pin secondPin,
                   Statistics firstStats, Statistics secondStats, boolean mateIdentityRuleVerified) {
        void verify(Runnable check) throws IOException {
            require(firstPin.equals(NextflowFiles.pin(first, limits.file().encodedBytes(), check)), "Original Salmon read1/sole FASTQ changed");
            if (second != null) require(secondPin.equals(NextflowFiles.pin(second, limits.file().encodedBytes(), check)), "Original Salmon read2 FASTQ changed");
        }
        Map<String, Object> interpretation() {
            return Map.of("quality_strings", "unchanged printable ASCII; no Phred offset or quality model inferred", "sequence_case_and_symbols", "unchanged",
                "pairing", pairing.name(), "mate_identity_rule_verified", mateIdentityRuleVerified, "biological_mate_identity_certified", false,
                "source_ordinal_only_warning", second != null && pairing == Pairing.SOURCE_ORDINAL_UNVERIFIED);
        }
    }
    static Context inspect(Path first, Path second, Limits limits, Alphabet alphabet, Pairing pairing, Runnable check) throws IOException {
        Objects.requireNonNull(limits); Objects.requireNonNull(alphabet); Objects.requireNonNull(pairing); Objects.requireNonNull(check);
        NextflowFiles.Pin firstPin = NextflowFiles.pin(first, limits.file().encodedBytes(), check), secondPin = second == null ? null : NextflowFiles.pin(second, limits.file().encodedBytes(), check);
        if (second != null) require(!Files.isSameFile(first, second), "Salmon paired FASTQ sources must be distinct files");
        long encoded = add(firstPin.bytes(), secondPin == null ? 0 : secondPin.bytes()); require(encoded <= limits.combinedEncodedBytes(), "Complete paired/sole Salmon FASTQ bytes exceed combined capacity");
        Statistics a, b;
        try (var read1 = reader(first, limits, check); var read2 = second == null ? null : reader(second, limits, check)) {
            long bases = 0;
            for (FastpFastq.Read r1 = read1.next(); r1 != null; r1 = read1.next()) {
                check.run(); validateAlphabet(r1, alphabet); bases = add(bases, r1.sequence().length());
                if (read2 != null) {
                    FastpFastq.Read r2 = read2.next(); require(r2 != null, "Salmon read2 has fewer complete records than read1");
                    validateAlphabet(r2, alphabet); pair(r1, r2, pairing); bases = add(bases, r2.sequence().length());
                }
                require(bases <= limits.combinedBases(), "Complete Salmon read bases exceed combined capacity");
            }
            if (read2 != null) require(read2.next() == null, "Salmon read2 has extra complete records");
            a = statistics(read1.completeStats()); b = read2 == null ? null : statistics(read2.completeStats());
        }
        require(a.reads() > 0 && a.encodedBytes() == firstPin.bytes() && (b == null || b.reads() == a.reads() && b.encodedBytes() == secondPin.bytes()), "Complete Salmon FASTQ population or physical byte census differs");
        var result = new Context(first, second, limits, alphabet, pairing, firstPin, secondPin, a, b, second != null && pairing != Pairing.SOURCE_ORDINAL_UNVERIFIED);
        result.verify(check); return result;
    }
    private static FastpFastq.Reader reader(Path path, Limits limits, Runnable check) throws IOException {
        // PHRED33 is used only for the printable ASCII validation range. Its
        // interpreted score statistics are deliberately not exported or used.
        return new FastpFastq.Reader(path, limits.file(), FastpFastq.Encoding.PHRED33, FastpFastq.Alphabet.LITERAL_ASCII, FastpFastq.Plus.SOURCE_IGNORED, false, check);
    }
    private static Statistics statistics(FastpFastq.Stats value) {
        return new Statistics(value.reads(), value.bases(), value.minimumLength(), value.maximumLength(), value.encodedBytes(), value.decodedCharacters(),
            value.gzip(), value.unterminatedFinalLine(), value.noncanonicalBases(), value.nonemptyPlus());
    }
    private static void validateAlphabet(FastpFastq.Read read, Alphabet alphabet) throws IOException {
        if (alphabet == Alphabet.IUPAC_DNA_RNA_OR_X)
            for (int i = 0; i < read.sequence().length(); i++) require("ACGTURYKMSWBDHVNXacgturykmswbdhvnx".indexOf(read.sequence().charAt(i)) >= 0, "Salmon FASTQ contains a symbol outside the declared IUPAC DNA/RNA-or-X policy");
    }
    private static void pair(FastpFastq.Read first, FastpFastq.Read second, Pairing pairing) throws IOException {
        require(first.ordinal() == second.ordinal(), "Salmon mate ordinal differs"); if (pairing == Pairing.SOURCE_ORDINAL_UNVERIFIED) return;
        String a = token(first.header().substring(1)), b = token(second.header().substring(1));
        if (pairing == Pairing.TERMINAL_MATE_SUFFIX) {
            require(!a.endsWith("/2") && !b.endsWith("/1"), "Salmon terminal mate roles are reversed or duplicated"); boolean markedA = a.endsWith("/1"), markedB = b.endsWith("/2");
            require(markedA == markedB, "Salmon mates inconsistently declare terminal roles"); if (markedA) { a = a.substring(0, a.length() - 2); b = b.substring(0, b.length() - 2); }
        }
        if (pairing == Pairing.CASAVA_ROLES) { casava(first.header(), "1"); casava(second.header(), "2"); }
        require(a.equals(b), "Salmon mate source-token identity/order mismatch at record " + first.ordinal());
    }
    private static void casava(String header, String role) throws IOException {
        String value = header.substring(1); int end = token(value).length(); while (end < value.length() && (value.charAt(end) == ' ' || value.charAt(end) == '\t')) end++;
        String[] fields = token(value.substring(end)).split(":", -1);
        require(fields.length == 4 && fields[0].equals(role) && Set.of("Y", "N").contains(fields[1]) && fields[2].matches("[0-9]+"), "Salmon CASAVA metadata does not match the declared read1/read2 roles");
    }
    private static String token(String value) { int end = 0; while (end < value.length() && value.charAt(end) != ' ' && value.charAt(end) != '\t') end++; return value.substring(0, end); }
    private static long add(long a, long b) throws IOException { try { return Math.addExact(a, b); } catch (ArithmeticException overflow) { throw new IOException("Complete Salmon reads exceed integer capacity", overflow); } }
    private static void require(boolean value, String message) throws IOException { if (!value) throw new IOException(message); }
}
