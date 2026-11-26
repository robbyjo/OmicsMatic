/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.epigenomics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Native result projection for the pre-indexed DESeq2 branch of DiffBind3.24.0,
 * R/analyze_deseq2.R, pv.DESeq2ContrastResults (Rory Stark and Gord Brown;
 * original package Artistic-2.0). Original-runtime fitting and shrinkage supply
 * the rows. Source NA/NaN probabilities become one, adjusted probabilities order
 * rows stably, and fold values retain their original IEEE representation.
 */
public final class DiffbindResults {
    private DiffbindResults() { }

    /** Exact positive source indices; a double can represent each accepted ID. */
    public record Row(long id,double pvalue,double adjustedPvalue,double log2FoldChange) {
        public Row {
            if(id<1||id>9_007_199_254_740_991L)
                throw new IllegalArgumentException("Source row ID must be positive and exactly representable");
        }
    }

    /** Complete source projection, including stable ties between signed zeroes. */
    public static List<Row> fromDeseq2(List<Row> original) {
        Objects.requireNonNull(original,"original");
        var ids=new HashSet<Long>();var projected=new ArrayList<Row>(original.size());
        for(Row row:original) {
            Objects.requireNonNull(row,"row");
            if(!ids.add(row.id()))throw new IllegalArgumentException("Duplicate source result row ID: "+row.id());
            projected.add(new Row(row.id(),sourceProbability(row.pvalue()),
                    sourceProbability(row.adjustedPvalue()),row.log2FoldChange()));
        }
        // R's numeric order preserves input order for equal values, including -0/+0.
        projected.sort((a,b)->a.adjustedPvalue()<b.adjustedPvalue()?-1:
                a.adjustedPvalue()>b.adjustedPvalue()?1:0);
        return List.copyOf(projected);
    }

    private static double sourceProbability(double value) {
        return Double.isNaN(value)?1:value;
    }
}
