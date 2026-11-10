/*
 * Copyright 1984, 1987, 1988, 1992, 1995, 2000 by Stephen L. Moshier.
 * Copyright (C) 2026 JLinAlg contributors. SPDX-License-Identifier: BSD-3-Clause
 *
 * Derived from the Cephes Math Library, incorporated into SciPy by permission
 * of the author. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the organization nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.jlinalg.genetics;

/**
 * Finite-Z MTAG1.0.8 two-sided normal P, preserving SciPy1.2.1 Cephes ndtr/erfc
 * branches, coefficients and MAXLOG underflow. Source: SciPy v1.2.1
 * {@code scipy/special/cephes/ndtr.c}, {@code const.c}, {@code polevl.h}.
 * The expression is exactly {@code 2 * ndtr(-abs(z))}, including original
 * multiplication order. The zero above the Cephes exponential cutoff is an
 * original algorithm outcome; no generic P-value clipping is performed.
 *
 * <p>This is separate from the historical JDistlib-tail MTAG estimator
 * component. It does not alter that component or its saved evidence.</p>
 */
public final class MtagScipy121NormalTail {
    private MtagScipy121NormalTail() { }
    private static final double SQRT_HALF=0.707106781186547524400844362104849039;
    private static final double MAXLOG=7.09782712893383996732E2;
    private static final double[] P={2.46196981473530512524E-10,5.64189564831068821977E-1,7.46321056442269912687E0,
        4.86371970985681366614E1,1.96520832956077098242E2,5.26445194995477358631E2,9.34528527171957607540E2,
        1.02755188689515710272E3,5.57535335369399327526E2};
    private static final double[] Q={1.32281951154744992508E1,8.67072140885989742329E1,3.54937778887819891062E2,
        9.75708501743205489753E2,1.82390916687909736289E3,2.24633760818710981792E3,1.65666309194161350182E3,5.57535340817727675546E2};
    private static final double[] R={5.64189583547755073984E-1,1.27536670759978104416E0,5.01905042251180477414E0,
        6.16021097993053585195E0,7.40974269950448939160E0,2.97886665372100240670E0};
    private static final double[] S={2.26052863220117276590E0,9.39603524938001434673E0,1.20489539808096656605E1,
        1.70814450747565897222E1,9.60896809063285878198E0,3.36907645100081516050E0};
    private static final double[] T={9.60497373987051638749E0,9.00260197203842689217E1,2.23200534594684319226E3,
        7.00332514112805075473E3,5.55923013010394962768E4};
    private static final double[] U={3.35617141647503099647E1,5.21357949780152679795E2,4.59432382970980127987E3,
        2.26290000613890934246E4,4.92673942608635921086E4};

    /** Original SciPy1.2.1 expression on a finite standardized Z score. */
    public static double twoSidedP(double z) {
        if(!Double.isFinite(z))throw new IllegalArgumentException("Finite original MTAG Z required; no nonfinite replacement");
        double x=-Math.abs(z)*SQRT_HALF;
        double y=Math.abs(x)<SQRT_HALF ? .5+.5*erf(x) : .5*erfc(Math.abs(x));
        return 2*y;
    }
    private static double erf(double x) {
        if(x<0)return -erf(-x);
        if(Math.abs(x)>1)return 1-erfc(x);
        double z=x*x;return x*polevl(z,T)/p1evl(z,U);
    }
    private static double erfc(double a) {
        double x=Math.abs(a);
        if(x<1)return 1-erf(a);
        double z=-a*a;
        if(z < -MAXLOG)return a<0?2:0;
        z=Math.exp(z);
        double p=x<8?polevl(x,P):polevl(x,R),q=x<8?p1evl(x,Q):p1evl(x,S);
        double y=(z*p)/q;
        if(a<0)y=2-y;
        if(y==0)return a<0?2:0;
        return y;
    }
    private static double polevl(double x,double[] coefficients) {
        double answer=coefficients[0];for(int i=1;i<coefficients.length;i++)answer=answer*x+coefficients[i];return answer;
    }
    private static double p1evl(double x,double[] coefficients) {
        double answer=x+coefficients[0];for(int i=1;i<coefficients.length;i++)answer=answer*x+coefficients[i];return answer;
    }
}
