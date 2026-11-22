/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Independent final tag-count/normalization audit for disjoint original HOMER5.1 regions.
 * This does not discover regions or reconstruct their discovery scores, filters,
 * Poisson thresholds or reported FDR. It counts the original SAM half-unit tags
 * after the fragment-center shift and the source chromosome-anchor clamp.
 */
public final class HomerRegionCounts {
    private HomerRegionCounts() { }
    /** Published original one-based, inclusive region, including a possible one-base region. */
    public record Region(String id,int start1,int end1) {
        public Region {if(id==null||id.isBlank()||id.chars().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c))||start1<1||end1<start1||end1>1_000_000_000)throw new IllegalArgumentException("Invalid original inclusive region");}
    }
    /** Exact raw half-unit population and source float32 normalized/printed score. */
    public record Count(Region region,double rawTags,float normalizedTags,String printedNormalizedTags) { }
    /** Final counts only; totalInRegions sums region counts rather than estimating statistical significance. */
    public record Counts(List<Count> regions,double totalInRegions,float normalizationFactor) {
        public Counts {regions=List.copyOf(regions);}
    }
    /** Caller deadline or immutable-input check. */
    @FunctionalInterface public interface Check {void run()throws IOException;}
    private record Point(int anchor,long halfUnits) { }

    /**
     * Audit final counts of position-ordered, nonoverlapping, both-strand regions.
     * The profile accepts positive SAM-derived half-unit tag weights; arbitrary
     * fractional, methylation, Hi-C, capped or control-subtracted tags are outside
     * its contract. Half-unit prefix sums remain exact and use inclusive endpoints.
     * Fragment center adjustment is integer fragmentLength/2, independently of
     * the stored full paired fragment length used by a coverage track.
     * @param tags complete position-ordered serialized tags of one chromosome
     * @param regions position-ordered disjoint final original regions of that chromosome
     * @param referenceLength exact dictionary length
     * @param fragmentLength positive explicit original peak fragment length
     * @param totalTagMass original whole-directory TagInfo mass
     * @param normalizationTarget positive original peak normalization target
     * @param maximumTags caller memory bound, at most5000000 rows
     * @param maximumRegions caller region bound, at most1000000 rows
     * @param check caller deadline/cancellation check
     * @return independent raw/printed normalized counts for every supplied region
     * @throws IOException on caller failure or unsupported arithmetic
     */
    public static Counts count(List<HomerCoverage.Tag> tags,List<Region> regions,long referenceLength,
                               int fragmentLength,double totalTagMass,double normalizationTarget,
                               int maximumTags,int maximumRegions,Check check)throws IOException {
        if(tags==null||tags.isEmpty()||regions==null||check==null||referenceLength<1||referenceLength>1_000_000_000||fragmentLength<1||fragmentLength>1_000_000_000||!Double.isFinite(totalTagMass)||totalTagMass<=0||!Double.isFinite(normalizationTarget)||normalizationTarget<=0||normalizationTarget>1e12||maximumTags<1||maximumTags>5_000_000||tags.size()>maximumTags||maximumRegions<1||maximumRegions>1_000_000||regions.size()>maximumRegions)throw new IllegalArgumentException("Require complete bounded half-unit tags, disjoint regions and original normalization");
        check.run();int previous=0,min=tags.get(0).position1(),max=tags.get(tags.size()-1).position1(),adjustment=fragmentLength/2;var points=new ArrayList<Point>(tags.size());long totalHalf=0;
        for(int i=0;i<tags.size();i++) {
            if((i&4095)==0)check.run();var t=tags.get(i);double half=t.weight()*2.0;if(t.position1()<previous||t.position1()>referenceLength||Math.rint(half)!=half)throw new IllegalArgumentException("Require ordered in-reference SAM half-unit tag weights");previous=t.position1();totalHalf=Math.addExact(totalHalf,(long)half);if(totalHalf>(1L<<52))throw new IOException("Half-unit mass exceeds exact source-double audit capacity");
            long anchor=(long)t.position1()+(t.strand()=='+'?adjustment:-adjustment);points.add(new Point((int)Math.max(min,Math.min(max,anchor)),(long)half));
        }
        if(totalHalf/2.0>totalTagMass)throw new IllegalArgumentException("Chromosome tag mass exceeds whole-directory mass");points.sort(Comparator.comparingInt(Point::anchor));long[] prefix=new long[points.size()+1];for(int i=0;i<points.size();i++)prefix[i+1]=Math.addExact(prefix[i],points.get(i).halfUnits());
        float factor=(float)(normalizationTarget/totalTagMass);if(!Float.isFinite(factor))throw new IOException("Nonfinite source-float32 peak normalization");var result=new ArrayList<Count>(regions.size());long totalInRegions=0;int previousEnd=0;
        for(int i=0;i<regions.size();i++) {
            if((i&4095)==0)check.run();var r=regions.get(i);if(r.start1()<=previousEnd||r.end1()>referenceLength)throw new IllegalArgumentException("Region profile requires ordered disjoint inclusive in-reference intervals");previousEnd=r.end1();int lower=lower(points,r.start1()),upper=upper(points,r.end1());long half=prefix[upper]-prefix[lower];totalInRegions=Math.addExact(totalInRegions,half);double raw=half/2.0;float normalized=(float)raw*factor;if(!Float.isFinite(normalized))throw new IOException("Nonfinite source-float32 normalized peak count");int decimals=normalized<1?3:normalized<10?2:1;String printed=new BigDecimal((double)normalized).setScale(decimals,RoundingMode.HALF_EVEN).toPlainString();result.add(new Count(r,raw,normalized,printed));
        }
        check.run();return new Counts(result,totalInRegions/2.0,factor);
    }
    private static int lower(List<Point> points,int value){int low=0,high=points.size();while(low<high){int mid=(low+high)>>>1;if(points.get(mid).anchor()<value)low=mid+1;else high=mid;}return low;}
    private static int upper(List<Point> points,int value){int low=0,high=points.size();while(low<high){int mid=(low+high)>>>1;if(points.get(mid).anchor()<=value)low=mid+1;else high=mid;}return low;}
}
