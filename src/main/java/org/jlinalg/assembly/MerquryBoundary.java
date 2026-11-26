/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.assembly;

import java.util.*;

/**
 * Effective minimum-depth operation used by Merqury's filt.sh.
 * Derived from the embedded javax.arang.kmer.HistToPloidyDepth.java in the
 * unmodified Merqury 1.1/1.3/1.4.1 helper (United States Government Work;
 * accompanying MERQURY-LICENSE.txt). This operation does not format or infer
 * a complete ploidy table. Native selection is explicit, never a fallback.
 */
public final class MerquryBoundary {
    private MerquryBoundary() { }
    public record Bin(int depth,long distinct) {
        public Bin {if(depth<1||distinct<1)throw new IllegalArgumentException("Positive original int32 depth and int64 distinct count required");}
    }
    public record Result(int effectiveMinimumDepth,boolean firstValleyFound,int completeBins) { }
    /**
     * Same first strict slope-sign crossing as the original getEffectiveMinDepth
     * field. Plateaus do not form a crossing. At a sparse-bin crossing the
     * original records CURRENT depth minus one, even if that bin is absent.
     * No crossing leaves the original zero boundary. Every supplied row is
     * validated; trailing data is not cropped after locating the boundary.
     */
    public static Result calculate(List<Bin> bins,Runnable check) {
        Objects.requireNonNull(bins);Objects.requireNonNull(check);double previousCount=Double.MAX_VALUE,previousSlope=-1;int previousDepth=0,cutoff=0;boolean first=true,found=false;
        for(Bin bin:bins) {
            check.run();Objects.requireNonNull(bin);if(bin.depth()<=previousDepth)throw new IllegalArgumentException("Complete histogram depths must be strictly increasing; no sorting, merging or interpolation");
            double count=(double)bin.distinct(),slope=count-previousCount;
            if(!first&&!found&&slope*previousSlope<0&&slope>previousSlope){cutoff=bin.depth()-1;found=true;}
            first=false;previousDepth=bin.depth();previousCount=count;previousSlope=slope;
        }
        check.run();return new Result(cutoff,found,bins.size());
    }
}
