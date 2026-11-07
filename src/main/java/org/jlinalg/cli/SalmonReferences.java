/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jlinalg.sequence.SalmonDecoyAssignment;
import org.jlinalg.sequence.SalmonIndexReferences;

/** Complete bounded raw-reference census and explicit input/index/decoy identities. */
final class SalmonReferences {
    private SalmonReferences() { }
    enum Headers { SOURCE_WHITESPACE_TOKEN, GENCODE_FIRST_PIPE }
    record Limits(RepeatFasta.Limits fasta, int decoyBytes, int decoys, int decoyNameCharacters, long retainedBytes) {
        Limits {
            Objects.requireNonNull(fasta);
            if (decoyBytes < 1 || decoyBytes == Integer.MAX_VALUE || decoys < 1 || decoyNameCharacters < 1 || retainedBytes < 262144)
                throw new IllegalArgumentException("Require bounded complete Salmon reference/decoy capacities");
        }
    }
    record Reference(RepeatFasta.Sequence raw, String indexName, boolean declaredDecoy) { }
    record Context(Path fasta, RepeatFasta.Encoding encoding, Headers headers, Path decoyFile, Limits limits,
                   NextflowFiles.Pin fastaPin, NextflowFiles.Pin decoyPin, RepeatFasta.Census census, List<Reference> references,
                   Set<String> declaredRawDecoys, long estimatedRetainedBytes) {
        Context { references = List.copyOf(references); declaredRawDecoys = Set.copyOf(declaredRawDecoys); }
        void verify(Runnable check) throws IOException {
            require(fastaPin.equals(NextflowFiles.pin(fasta, limits.fasta().inputBytes(), check)), "Original Salmon reference FASTA changed");
            if (decoyFile != null) require(decoyPin.equals(NextflowFiles.pin(decoyFile, limits.decoyBytes(), check)), "Original Salmon declared-decoy file changed");
        }
    }
    record IndexRow(Reference input, SalmonIndexReferences.Reference retained, String status) { }
    record Ledger(List<IndexRow> inputs, SalmonDecoyAssignment.Ledger decoys, int retained, int notRetained) {
        Ledger { inputs = List.copyOf(inputs); }
    }

    static Context inspect(Path fasta, RepeatFasta.Encoding encoding, Headers headers, Path decoyFile, Limits limits, Runnable check) throws IOException {
        Objects.requireNonNull(encoding); Objects.requireNonNull(headers); Objects.requireNonNull(limits); Objects.requireNonNull(check);
        NextflowFiles.Pin fastaPin = NextflowFiles.pin(fasta, limits.fasta().inputBytes(), check), decoyPin = null;
        var decoys = new HashSet<String>(); long retained = 262144;
        if (decoyFile != null) {
            decoyPin = NextflowFiles.pin(decoyFile, limits.decoyBytes(), check); byte[] raw = NextflowFiles.bytes(decoyFile, limits.decoyBytes());
            require(new NextflowFiles.Pin(raw.length, NextflowFiles.sha(raw)).equals(decoyPin), "Salmon decoy input changed during complete read");
            retained = add(retained, Math.multiplyExact(16L, raw.length)); require(retained <= limits.retainedBytes(), "Salmon decoy metadata exceeds retained capacity");
            String text = NextflowFiles.utf8(raw); require(!text.isEmpty() && text.indexOf('\r') < 0, "Salmon decoys require nonempty literal LF-delimited names; convert CRLF explicitly into a NEW file");
            int start = 0;
            while (start < text.length()) {
                check.run(); int end = text.indexOf('\n', start); if (end < 0) end = text.length();
                require(end > start && end - start <= limits.decoyNameCharacters() && decoys.size() < limits.decoys(), "Empty/oversized/out-of-capacity Salmon decoy name population");
                String name = text.substring(start, end);
                require(name.codePoints().noneMatch(c -> c < 33 || c == 127 || Character.isWhitespace(c) || Character.isSpaceChar(c)) && decoys.add(name), "Duplicate/control/whitespace Salmon raw decoy identity");
                retained = add(retained, 512); require(retained <= limits.retainedBytes(), "Salmon decoy-set metadata exceeds retained capacity"); start = end + 1;
            }
        }
        RepeatFasta.Census census = RepeatFasta.read(fasta, encoding, limits.fasta(), check);
        require(census.sourceBytes() == fastaPin.bytes() && census.sourceSha256().equals(fastaPin.sha256()), "Raw Salmon reference census does not bind the protected complete file");
        retained = add(retained, census.estimatedRetainedBytes()); require(retained <= limits.retainedBytes(), "Complete Salmon FASTA metadata exceeds retained capacity");
        var references = new ArrayList<Reference>(); var rawNames = new HashSet<String>(); var indexNames = new HashSet<String>(); boolean sawDecoy = false; int nondecoys = 0;
        for (RepeatFasta.Sequence raw : census.sequences()) {
            check.run(); retained = add(retained, add(512, Math.multiplyExact(16L, raw.id().length()))); require(retained <= limits.retainedBytes(), "Complete Salmon processed-identity metadata exceeds capacity");
            String id = raw.id(); if (headers == Headers.GENCODE_FIRST_PIPE) { int pipe = id.indexOf('|'); if (pipe >= 0) id = id.substring(0, pipe); }
            require(!id.isEmpty() && indexNames.add(id), "Empty/colliding Salmon processed FASTA identities; do not merge or strip transcript versions implicitly");
            rawNames.add(raw.id()); boolean decoy = decoys.contains(raw.id());
            if (decoy) sawDecoy = true; else { require(!sawDecoy, "Original Salmon requires all declared decoy records consecutively after nondecoys"); nondecoys++; }
            references.add(new Reference(raw, id, decoy));
        }
        require(rawNames.containsAll(decoys), "Declared Salmon decoy names must match exact raw FASTA identifier tokens before GENCODE splitting");
        require(nondecoys > 0, "Salmon reference input needs at least one declared nondecoy");
        var result = new Context(fasta, encoding, headers, decoyFile, limits, fastaPin, decoyPin, census, references, decoys, retained); result.verify(check); return result;
    }

    static Ledger bind(Context input, SalmonIndexReferences.Summary index, int k, long indexDecoys, BigInteger firstGraphDecoy, Runnable check) throws IOException {
        input.verify(check); var source = new LinkedHashMap<String, Reference>();
        for (Reference reference : input.references()) source.put(reference.indexName(), reference);
        var retained = new LinkedHashMap<String, SalmonIndexReferences.Reference>(); var retainedDecoys = new HashSet<String>(); int previous = 0, retainedNondecoys = 0;
        for (SalmonIndexReferences.Reference row : index.references()) {
            check.run(); Reference raw = source.get(row.name()); require(raw != null && row.originalLength() == raw.raw().length(), "Original Salmon index reference identity/complete length is not in the protected input population");
            require(raw.raw().ordinal() > previous, "Original Salmon index reordered the protected full reference population"); previous = raw.raw().ordinal();
            require(retained.put(row.name(), row) == null, "Duplicate full Salmon index identity");
            if (raw.declaredDecoy()) retainedDecoys.add(row.name()); else retainedNondecoys++;
        }
        require(retainedNondecoys > 0, "No declared nondecoy remains in the original Salmon index; no expression population can be admitted");
        var assignments = SalmonDecoyAssignment.assign(index, k, indexDecoys, firstGraphDecoy, retainedDecoys, check); var rows = new ArrayList<IndexRow>(); int missing = 0;
        for (Reference raw : input.references()) {
            check.run(); SalmonIndexReferences.Reference kept = retained.get(raw.indexName());
            if (kept == null) missing++;
            rows.add(new IndexRow(raw, kept, kept == null ? "not-retained-by-original-index-reason-separate" : "retained-in-complete-original-index"));
        }
        input.verify(check); return new Ledger(rows, assignments, retained.size(), missing);
    }
    private static long add(long a, long b) throws IOException { try { return Math.addExact(a, b); } catch (ArithmeticException overflow) { throw new IOException("Complete Salmon input metadata capacity overflow", overflow); } }
    private static void require(boolean value, String message) throws IOException { if (!value) throw new IOException(message); }
}
