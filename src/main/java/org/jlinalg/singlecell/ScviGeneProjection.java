/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.singlecell;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/** Explicit ordered gene policies for scVI reference training and query preparation. */
public final class ScviGeneProjection {
    private ScviGeneProjection() { }

    /** Shared-gene selection changes the training population; it cannot shrink a pretrained model. */
    public enum Policy {
        /** Both lists must be identical, including order. */
        EXACT_ORDER,
        /** Retain shared genes in reference order, before fitting the reference model. */
        SHARED_BEFORE_TRAINING,
        /** Keep every reference gene, pad missing genes with zero and explicitly drop extras. */
        PAD_MISSING_AND_DROP_EXTRA
    }

    /**
     * Complete mapping, with -1 for padded/dropped columns. Arrays and lists are protected copies.
     * @param policy explicitly requested gene policy
     * @param outputGenes ordered model genes after projection
     * @param queryColumnForOutput source query index for each output gene, or -1 for padding
     * @param outputColumnForQuery output index for each query gene, or -1 for dropping
     * @param missingReferenceGenes reference genes absent in the query, in reference order
     * @param extraQueryGenes query genes absent in the reference, in query order
     */
    public record Plan(Policy policy, List<String> outputGenes, int[] queryColumnForOutput,
                       int[] outputColumnForQuery, List<String> missingReferenceGenes,
                       List<String> extraQueryGenes) {
        /** Constructs an immutable projection snapshot. */
        public Plan {
            Objects.requireNonNull(policy); outputGenes = List.copyOf(outputGenes);
            queryColumnForOutput = queryColumnForOutput.clone(); outputColumnForQuery = outputColumnForQuery.clone();
            missingReferenceGenes = List.copyOf(missingReferenceGenes); extraQueryGenes = List.copyOf(extraQueryGenes);
            if (outputGenes.size() != queryColumnForOutput.length) throw new IllegalArgumentException("Projection dimensions differ");
            for (int i = 0; i < queryColumnForOutput.length; i++) {
                int source = queryColumnForOutput[i];
                if (source < -1 || source >= outputColumnForQuery.length
                        || source >= 0 && outputColumnForQuery[source] != i) throw new IllegalArgumentException("Inconsistent forward projection");
            }
            for (int i = 0; i < outputColumnForQuery.length; i++) {
                int target = outputColumnForQuery[i];
                if (target < -1 || target >= outputGenes.size()
                        || target >= 0 && queryColumnForOutput[target] != i) throw new IllegalArgumentException("Inconsistent reverse projection");
            }
        }
        @Override public int[] queryColumnForOutput() { return queryColumnForOutput.clone(); }
        @Override public int[] outputColumnForQuery() { return outputColumnForQuery.clone(); }
    }

    /**
     * Plans a projection without changing either input list or silently sorting identifiers.
     * Matching uses exact case-sensitive identifiers; symbols, stable IDs and orthotypes are distinct.
     * @param referenceGenes unique ordered reference/model identifiers
     * @param queryGenes unique ordered query identifiers
     * @param policy explicitly chosen policy
     * @return full mapping and removed/missing populations
     */
    public static Plan plan(List<String> referenceGenes, List<String> queryGenes, Policy policy) {
        Objects.requireNonNull(policy);
        List<String> reference = checked(referenceGenes), query = checked(queryGenes);
        if (policy == Policy.EXACT_ORDER && !reference.equals(query)) {
            throw new IllegalArgumentException("EXACT_ORDER requires identical reference/query gene IDs and order");
        }
        var referenceIndex = index(reference); var queryIndex = index(query);
        var missing = new ArrayList<String>(); var extra = new ArrayList<String>(); var output = new ArrayList<String>();
        for (String gene : reference) {
            boolean present = queryIndex.containsKey(gene);
            if (!present) missing.add(gene);
            if (present || policy != Policy.SHARED_BEFORE_TRAINING) output.add(gene);
        }
        for (String gene : query) if (!referenceIndex.containsKey(gene)) extra.add(gene);
        if (output.isEmpty()) throw new IllegalArgumentException("The explicit shared-gene policy leaves no model genes");
        int[] source = new int[output.size()], target = new int[query.size()]; Arrays.fill(target, -1);
        for (int i = 0; i < output.size(); i++) {
            source[i] = queryIndex.getOrDefault(output.get(i), -1);
            if (source[i] >= 0) target[source[i]] = i;
        }
        return new Plan(policy, output, source, target, missing, extra);
    }

    private static List<String> checked(List<String> genes) {
        List<String> copy = List.copyOf(genes);
        if (copy.isEmpty()) throw new IllegalArgumentException("Gene identifiers cannot be empty");
        var seen = new java.util.HashSet<String>();
        for (String gene : copy) {
            if (gene.isEmpty() || gene.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))
                    || !seen.add(gene)) throw new IllegalArgumentException("Gene identifiers must be unique, nonempty and free of whitespace/control characters");
        }
        return copy;
    }

    private static HashMap<String,Integer> index(List<String> genes) {
        var result = new HashMap<String,Integer>();
        for (int i = 0; i < genes.size(); i++) result.put(genes.get(i), i);
        return result;
    }
}
