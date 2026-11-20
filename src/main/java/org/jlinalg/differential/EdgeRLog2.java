/*
 * Copyright (C) 1993 by Sun Microsystems, Inc. All rights reserved.
 * Developed at SunSoft, a Sun Microsystems, Inc. business.
 * Permission to use, copy, modify, and distribute this software is freely
 * granted, provided that this notice is preserved.
 *
 * Java adaptation of OpenLibm v0.8.7 e_log2.c and k_log.h, commit
 * 9fbeafcd4f1b6ef6aa3946c1c8faead50f38a94d. Adaptation copyright (C) 2026
 * JLinAlg contributors; same permission. See EDGER_NORMALIZATION.md.
 */
package org.jlinalg.differential;

/** Binary logarithm with split-precision reduction, rather than log(x)/log(2). */
final class EdgeRLog2 {
    private EdgeRLog2() { }
    static double value(double x) {
        long bits=Double.doubleToRawLongBits(x);int hx=(int)(bits>>>32),lx=(int)bits,k=0;
        if(hx<0x00100000) {
            if(((hx&0x7fffffff)|lx)==0)return Double.NEGATIVE_INFINITY;
            if(hx<0)return Double.NaN;
            k-=54;x*=1.80143985094819840000e16;hx=(int)(Double.doubleToRawLongBits(x)>>>32);
        }
        if(hx>=0x7ff00000)return x+x;
        if(hx==0x3ff00000&&lx==0)return 0;
        k+=(hx>>20)-1023;hx&=0x000fffff;int i=(hx+0x95f64)&0x100000;
        x=Double.longBitsToDouble((Double.doubleToRawLongBits(x)&0xffffffffL)|((long)(hx|(i^0x3ff00000))<<32));
        k+=i>>20;double y=k,f=x-1,hfsq=.5*f*f,s=f/(2+f),z=s*s,w=z*z;
        double t1=w*(3.999999999940941908e-1+w*(2.222219843214978396e-1+w*1.531383769920937332e-1));
        double t2=z*(6.666666666666735130e-1+w*(2.857142874366239149e-1+w*(1.818357216161805012e-1+w*1.479819860511658591e-1)));
        double polynomial=t2+t1,r=s*(hfsq+polynomial),hi=f-hfsq;
        hi=Double.longBitsToDouble(Double.doubleToRawLongBits(hi)&0xffffffff00000000L);
        double lo=(f-hi)-hfsq+r,valHi=hi*1.44269504072144627571,valLo=(lo+hi)*1.67517131648865118353e-10+lo*1.44269504072144627571;
        w=y+valHi;valLo+=(y-w)+valHi;return valLo+w;
    }
}
