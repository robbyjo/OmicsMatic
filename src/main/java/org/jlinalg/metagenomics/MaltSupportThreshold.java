/* Copyright (C) 2018 Daniel H. Huson; Copyright (C) 2026 JLinAlg contributors;
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.metagenomics;

/** Original default serial DataProcessor support-percent selection.
 * Attribution: Daniel H. Huson, megan.algorithms.DataProcessor (GPL3+),
 * historical context commit24e70bf6cd0a434ff40a62b98cf0b54d72954908.
 * Total weight is accumulated after ReadAssignmentCalculator, over all stored
 * reads, including reads without assignments. MALT0.4.1 selects readCount,
 * so this denominator is the number of stored reads. This preserves the
 * double computation, lower bound and narrowing conversion, not rounding.
 * The experimental parallel processor uses a different rule and is not selected here.
 */
public final class MaltSupportThreshold {
    private MaltSupportThreshold(){ }
    /** Raw source API: nonpositive/NaN percentages retain the configured integer.
     * Nonfinite totals and conversions retain Java/source behavior; they do not
     * establish validity of scientific inputs or permission to admit a result.
     */
    public static int serial(float percent,int configured,double totalWeight) {
        return percent>0?(int)Math.max(1,(percent/100.0)*totalWeight):configured;
    }
}
