/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Separate declared decoys from the exact original selective-alignment runtime
 * assignment. The original five editions classify a record using the number of
 * short records BEFORE that record, then increment the short-record counter.
 * Trailing short nondecoys can consequently receive a source decoy assignment.
 * This ledger preserves both populations; it never repairs or imputes output.
 */
public final class SalmonDecoyAssignment {
    private SalmonDecoyAssignment() { }
    private static final BigInteger UINT64_MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    /**
     * Complete per-reference assignment.
     * @param reference exact full source reference
     * @param shortReference whether normalized length is at most index k
     * @param declaredDecoy whether the protected retained-decoy population contains this name
     * @param originalRuntimeDecoy source assignment before incrementing the short counter
     */
    public record Assignment(SalmonIndexReferences.Reference reference, boolean shortReference, boolean declaredDecoy, boolean originalRuntimeDecoy) { }
    /**
     * Complete comparison, not a user-analysis admission ticket.
     * @param assignments full original order
     * @param indexK protected index k
     * @param indexDecoys retained declared/index-metadata decoy count
     * @param firstGraphDecoy exact original uint64 boundary, including an inactive sentinel
     * @param originalRuntimeDecoys full records assigned as decoys by the original runtime
     * @param disagreements exact names whose declared/source assignments differ
     * @param activeBoundaryHasContigId whether a nonempty decoy boundary has an actual contig-reference ID
     */
    public record Ledger(List<Assignment> assignments, int indexK, long indexDecoys, BigInteger firstGraphDecoy,
                         long originalRuntimeDecoys, List<String> disagreements, boolean activeBoundaryHasContigId) {
        /** Copies both complete populations and disagreement names. */
        public Ledger { assignments = List.copyOf(assignments); disagreements = List.copyOf(disagreements); }
        /** @return whether the declared and original-runtime target populations agree exactly */
        public boolean matchesDeclaredPopulation() { return disagreements.isEmpty(); }
        /** @return complete protected declared nondecoy population, without invented zero rows */
        public List<SalmonQuantification.Target> declaredNondecoys() {
            return assignments.stream().filter(row -> !row.declaredDecoy()).map(row -> target(row.reference())).toList();
        }
        /**
         * Original-runtime output population only. This can differ from the
         * requested declared population; callers must retain that distinction.
         * @return exact predicted original nondecoy rows
         */
        public List<SalmonQuantification.Target> originalRuntimeNondecoys() {
            return assignments.stream().filter(row -> !row.originalRuntimeDecoy()).map(row -> target(row.reference())).toList();
        }
    }
    /**
     * Replays the source classifier for original mapping with selective alignment.
     * Other mapping/feature modes require a separate contract. Header/FASTA/decoy
     * identity, index provenance and original process completion are separate.
     * @param references complete independently checked Cereal reference tables
     * @param k original index k
     * @param indexDecoys original num_decoys
     * @param firstGraphDecoy original first_decoy_index as exact uint64
     * @param retainedDeclaredDecoys declared decoy names that actually remain in the index
     * @param cancellation cancellation check
     * @return complete declared/source ledger
     * @throws IOException for inconsistent complete populations or unsupported metadata
     */
    public static Ledger assign(SalmonIndexReferences.Summary references, int k, long indexDecoys, BigInteger firstGraphDecoy,
                                Set<String> retainedDeclaredDecoys, Runnable cancellation) throws IOException {
        Objects.requireNonNull(references); Objects.requireNonNull(firstGraphDecoy); Objects.requireNonNull(cancellation);
        Set<String> decoys = Set.copyOf(retainedDeclaredDecoys); List<SalmonIndexReferences.Reference> rows = references.references();
        require(!rows.isEmpty() && k >= 1 && k <= 31 && indexDecoys >= 0 && indexDecoys <= rows.size() && firstGraphDecoy.signum() >= 0 && firstGraphDecoy.compareTo(UINT64_MAX) <= 0, "Invalid original Salmon decoy/k/uint64 metadata");
        require(indexDecoys == decoys.size(), "Retained declared decoy names and original index decoy count differ");
        var seen = new HashSet<String>(); boolean sawDecoy = false; int graph = 0, graphBeforeDecoy = 0;
        for (int i = 0; i < rows.size(); i++) {
            cancellation.run(); var row = rows.get(i); boolean isShort = row.normalizedLength() <= k, declared = decoys.contains(row.name());
            require(row.ordinal() == i && seen.add(row.name()) && row.originalLength() > 0 && row.normalizedLength() > 0 && row.normalizedLength() <= row.originalLength(), "Incomplete/duplicate Salmon reference population");
            require(isShort ? row.contigReferenceId() == -1 : row.contigReferenceId() == graph, "Original short/reference-ID extension mapping disagrees with index k");
            if (declared) sawDecoy = true; else require(!sawDecoy, "A nondecoy follows a retained declared decoy");
            if (!isShort) { graph++; if (!declared) graphBeforeDecoy++; }
        }
        require(graph == references.contigReferences() && seen.containsAll(decoys), "Complete Salmon graph/declared reference populations differ");
        if (indexDecoys > 0) require(firstGraphDecoy.equals(BigInteger.valueOf(graphBeforeDecoy)), "Original decoy boundary differs from the declared graph-reference population");
        var assignments = new ArrayList<Assignment>(); var disagreements = new ArrayList<String>(); long shortBefore = 0, runtimeDecoys = 0;
        for (int i = 0; i < rows.size(); i++) {
            cancellation.run(); var row = rows.get(i); boolean isShort = row.normalizedLength() <= k, declared = decoys.contains(row.name());
            boolean source = indexDecoys > 0 && BigInteger.valueOf(i - shortBefore).compareTo(firstGraphDecoy) >= 0;
            assignments.add(new Assignment(row, isShort, declared, source)); if (declared != source) disagreements.add(row.name());
            if (isShort) shortBefore++; if (source) runtimeDecoys++;
        }
        cancellation.run(); return new Ledger(assignments, k, indexDecoys, firstGraphDecoy, runtimeDecoys, disagreements,
            indexDecoys == 0 || firstGraphDecoy.compareTo(BigInteger.valueOf(graph)) < 0);
    }
    private static SalmonQuantification.Target target(SalmonIndexReferences.Reference row) { return new SalmonQuantification.Target(row.name(), row.originalLength()); }
    private static void require(boolean value, String message) throws IOException { if (!value) throw new IOException(message); }
}
