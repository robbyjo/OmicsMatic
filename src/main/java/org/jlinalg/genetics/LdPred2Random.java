/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import jdistlib.Normal;

/** Random draws consumed by one LDpred2 Gibbs model, in the original call order. */
public interface LdPred2Random {
    double uniform();
    double standardNormal();

    /**
     * R's L'Ecuyer-CMRG generator with Inversion normals, initialized from an
     * explicit seven-integer R .Random.seed (kind 407 or 10407). This is not
     * R's set.seed(integer) initialization or doRNG's allocation of model streams.
     * Instances are mutable and must not be shared between concurrent models.
     */
    final class RStream implements LdPred2Random {
        private static final long M1 = 4294967087L, M2 = 4294944443L;
        private static final double NORM = 2.328306549295727688e-10;
        private static final double BIG = 134217728.0;
        private final int kind;
        private final long[] state = new long[6];

        public RStream(int[] rState) {
            if (rState == null || rState.length != 7 || (rState[0] != 407 && rState[0] != 10407)) {
                throw new IllegalArgumentException("R L'Ecuyer-CMRG / Inversion state of kind 407 or 10407 required");
            }
            kind = rState[0];
            for (int i = 0; i < 6; i++) {
                state[i] = Integer.toUnsignedLong(rState[i + 1]);
                if (state[i] >= (i < 3 ? M1 : M2)) throw new IllegalArgumentException("R seed component out of range");
            }
            if ((state[0] | state[1] | state[2]) == 0 || (state[3] | state[4] | state[5]) == 0) {
                throw new IllegalArgumentException("R seed substreams must not be all zero");
            }
        }

        @Override public double uniform() {
            long p1 = (1403580L * state[1] - 810728L * state[0]) % M1;
            if (p1 < 0) p1 += M1;
            long p2 = (527612L * state[5] - 1370589L * state[3]) % M2;
            if (p2 < 0) p2 += M2;
            state[0] = state[1]; state[1] = state[2]; state[2] = p1;
            state[3] = state[4]; state[4] = state[5]; state[5] = p2;
            return (p1 > p2 ? p1 - p2 : p1 - p2 + M1) * NORM;
        }

        @Override public double standardNormal() {
            double high = Math.floor(BIG * uniform());
            double probability = (high + uniform()) / BIG;
            return Normal.quantile(probability, 0, 1, true, false);
        }

        /** A defensive copy suitable for exact continuation of this stream. */
        public int[] state() {
            int[] result = new int[7]; result[0] = kind;
            for (int i = 0; i < 6; i++) result[i + 1] = (int) state[i];
            return result;
        }
    }
}
