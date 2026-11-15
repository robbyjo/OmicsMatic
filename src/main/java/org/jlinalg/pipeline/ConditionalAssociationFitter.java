/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.pipeline;

import org.jlinalg.compute.BackendPolicy;
import org.jlinalg.inference.AssociationStatistics;

/** Exact per-feature fit, with original observation indices for covariance,
 * weight and offset subsetting. Implementations must be thread-safe. */
@FunctionalInterface
public interface ConditionalAssociationFitter {
    AssociationStatistics fit(double[] response, double[] design, int columns,
        int[] originalRows, BackendPolicy backend);
}
