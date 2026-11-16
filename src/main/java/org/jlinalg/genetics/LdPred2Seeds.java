/*
 * Copyright (C) 2011 The R Core Team (stream jump matrices)
 * Copyright (C) 2026 JLinAlg contributors
 * R initialization and stream jumps: R 4.3.3 RNG.c and parallel/src/rngstream.c,
 * available under GPL-2.0-or-later; this adaptation uses GPL version 3 or later.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.jlinalg.genetics;

import java.math.BigInteger;

/** R L'Ecuyer-CMRG / Inversion initialization and independent 2^127 stream jumps. */
public final class LdPred2Seeds {
    private static final long M1=4294967087L,M2=4294944443L;
    private static final long[][] A1={{2427906178L,3580155704L,949770784L},{226153695L,1230515664L,3580155704L},{1988835001L,986791581L,1230515664L}};
    private static final long[][] A2={{1464411153L,277697599L,1610723613L},{32183930L,1464411153L,1022607788L},{2824425944L,32183930L,2093834863L}};
    private LdPred2Seeds() {}

    /** Equivalent to explicit R RNGkind followed by set.seed(integer). */
    public static int[] fromInteger(int seed) {
        if(seed==Integer.MIN_VALUE)throw new IllegalArgumentException("R's missing integer is not a seed");
        int value=seed;
        for(int j=0;j<50;j++)value=69069*value+1;
        int[] state=new int[7];state[0]=10407;
        for(int j=1;j<7;j++) {
            value=69069*value+1;
            while(Integer.toUnsignedLong(value)>=M2)value=69069*value+1;
            state[j]=value;
        }
        new LdPred2Random.RStream(state);return state;
    }

    /** Equivalent to parallel::nextRNGStream; does not mutate the supplied state. */
    public static int[] nextStream(int[] state) {
        new LdPred2Random.RStream(state);
        int[] result=new int[7];result[0]=state[0];
        multiply(A1,state,1,result,1,M1);multiply(A2,state,4,result,4,M2);
        return result;
    }
    private static void multiply(long[][] matrix,int[] state,int offset,int[] result,int outOffset,long modulus) {
        BigInteger mod=BigInteger.valueOf(modulus);
        for(int row=0;row<3;row++) {
            BigInteger sum=BigInteger.ZERO;
            for(int col=0;col<3;col++)sum=sum.add(BigInteger.valueOf(matrix[row][col]).multiply(BigInteger.valueOf(Integer.toUnsignedLong(state[offset+col]))));
            result[outOffset+row]=(int)sum.mod(mod).longValueExact();
        }
    }
}
