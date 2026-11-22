/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** One-chromosome event replay of original HOMER5.1 unsmoothed, one-base coverage.
 * Input is the complete serialized six-column tag population after a separate
 * SAM/tag audit. Equal comparator keys retain input order, unlike an unspecified
 * C qsort implementation: tied event groups are counted explicitly. This is a
 * numerical replay, not a peak caller or a cross-libc rounding certificate.
 */
public final class HomerCoverage {
    private HomerCoverage() { }
    /** One serialized original tag row, with a full integer stored length. */
    public record Tag(int position1,char strand,int storedLength,float weight) {
        public Tag {if(position1<1||position1>1_000_000_000||strand!='+'&&strand!='-'||storedLength<1||storedLength>1_000_000_000||!Float.isFinite(weight)||weight<=0)throw new IllegalArgumentException("Invalid positive original tag row");}
    }
    /** Explicit original controls: fragmentLength0 means given; strand '.' means both.
     * Last-tag clamping affects event endpoints, independently of the unconditional
     * original adjustment clamp to the input chromosome's minimum/maximum anchors.
     * A zero normalization target means raw weights; normLength0 disables the
     * otherwise independent length scaling. No smoothing, tag cap or input ratio.
     */
    public record Controls(int adjustment,int fragmentLength,char strand,boolean clampLastTag,
                           double normalizationTarget,double normalizationLength) {
        public Controls {if(adjustment< -1_000_000||adjustment>1_000_000||fragmentLength<0||fragmentLength>1_000_000_000||strand!='.'&&strand!='+'&&strand!='-'||!Double.isFinite(normalizationTarget)||normalizationTarget<0||normalizationTarget>1e12||!Double.isFinite(normalizationLength)||normalizationLength<0||normalizationLength>1e9)throw new IllegalArgumentException("Invalid explicit one-base original coverage controls");}
    }
    /** A source event interval before optional printing, with exact float32 promotion. */
    public record Bin(long start0,long end,float value,String printedValue) {
        public Bin {if(start0<0||end<=start0||!Float.isFinite(value)||printedValue==null)throw new IllegalArgumentException("Invalid original coverage bin");}
    }
    /** Complete event replay, including comparator ties and terminal float32 residue.
     * Bins retain source event boundaries and two-decimal printf output; adjacent
     * equal values are not condensed. Bounds past the chromosome remain visible.
     */
    public record Replay(List<Bin> bins,int inputTags,int selectedTags,int eventPositions,
                         int tiedComparatorGroups,int mixedValueComparatorGroups,
                         int outOfReferenceBins,float terminalResidue) {
        public Replay {bins=List.copyOf(bins);}
    }
    /** Deadline/cancellation check; no background process or resource is opened here. */
    @FunctionalInterface public interface Check {void run()throws IOException;}
    private record Shifted(int position,char strand,int length,float weight) { }
    private record Event(int position,int length,float value) { }
    private static final Comparator<Shifted> TAG_ORDER=Comparator.comparingInt(Shifted::position)
        .thenComparingInt(t->t.strand()=='+'?0:1).thenComparingInt(t->(byte)t.length());
    private static final Comparator<Event> EVENT_ORDER=Comparator.comparingInt(Event::position)
        .thenComparingInt(e->(byte)e.length());

    /**
     * Replay every event of one original, positive, six-column tag chromosome.
     * The input must already be position ordered, as original readTagFile retains
     * such rows without reoptimizing. Full tag key aggregation is a separate audit;
     * this method must receive the actual serialized rows, including any aliases.
     * Java stable sorting makes equal-key order explicit. Independently compare
     * the original bedGraph before claiming exact agreement for a pinned runtime.
     * Capacity is per chromosome and requires memory for at most two events/tag.
     * @param tags complete original serialized rows of exactly one chromosome
     * @param referenceLength exact dictionary length used to flag overruns
     * @param totalTagMass original whole-directory TagInfo mass, including other contigs
     * @param controls explicit original settings
     * @param maximumTags caller memory bound, at most5000000 serialized rows
     * @param check caller deadline/cancellation check
     * @return complete printed bins and ordering/endpoint diagnostics
     * @throws IOException on caller failure or nonfinite arithmetic
     */
    public static Replay replay(List<Tag> tags,long referenceLength,double totalTagMass,Controls controls,
                                int maximumTags,Check check)throws IOException {
        if(tags==null||tags.isEmpty()||controls==null||check==null||referenceLength<1||referenceLength>1_000_000_000||!Double.isFinite(totalTagMass)||totalTagMass<=0||maximumTags<1||maximumTags>5_000_000||tags.size()>maximumTags)throw new IllegalArgumentException("Require complete bounded chromosome tags, reference and original directory mass");
        check.run();int previous=0,min=tags.get(0).position1(),max=tags.get(tags.size()-1).position1();var shifted=new ArrayList<Shifted>(tags.size());
        for(int i=0;i<tags.size();i++) {
            if((i&4095)==0)check.run();Tag t=tags.get(i);if(t.position1()<previous||t.position1()>referenceLength)throw new IllegalArgumentException("Original tag rows must be ordered and anchored within exact reference");previous=t.position1();
            long position=(long)t.position1()+(t.strand()=='+'?controls.adjustment():-controls.adjustment());position=Math.max(min,Math.min(max,position));shifted.add(new Shifted((int)position,t.strand(),t.storedLength(),t.weight()));
        }
        if(controls.adjustment()!=0)shifted.sort(TAG_ORDER);int last=shifted.get(shifted.size()-1).position();float factor=(float)(controls.normalizationTarget()<0.00001?1:controls.normalizationTarget()/totalTagMass);finite(factor);var events=new ArrayList<Event>(Math.multiplyExact(tags.size(),2));int selected=0;
        for(int i=0;i<shifted.size();i++) {
            if((i&4095)==0)check.run();var t=shifted.get(i);if(controls.strand()!='.'&&t.strand()!=controls.strand())continue;selected++;int length=controls.fragmentLength()==0?t.length():controls.fragmentLength();float value=t.weight()*factor;
            if(controls.normalizationLength()>1e-10)value=(float)(value*(controls.normalizationLength()/Math.max(25,length)));finite(value);
            int anchor=t.position()+(t.strand()=='+'?-1:0);long endpoint=(long)anchor+(t.strand()=='+'?length:-length);if(t.strand()=='-'&&endpoint<0)endpoint=1;
            if(controls.clampLastTag()){anchor=Math.min(anchor,last);endpoint=Math.min(endpoint,last);}if(endpoint<Integer.MIN_VALUE||endpoint>Integer.MAX_VALUE)throw new IOException("Original coverage endpoint exceeds source integer arithmetic");
            events.add(new Event(anchor,t.length(),t.strand()=='+'?value:-value));events.add(new Event((int)endpoint,t.length(),t.strand()=='+'?-value:value));
        }
        if(selected==0)throw new IllegalArgumentException("No selected-strand tags; original zero-event behavior is outside this profile");events.sort(EVENT_ORDER);int tied=0,mixed=0;
        for(int i=0;i<events.size();) {int end=i+1;boolean different=false;while(end<events.size()&&EVENT_ORDER.compare(events.get(i),events.get(end))==0){different|=Float.floatToIntBits(events.get(i).value())!=Float.floatToIntBits(events.get(end).value());end++;}if(end-i>1){tied++;if(different)mixed++;}i=end;}
        var positions=new ArrayList<Event>();
        for(int i=0;i<events.size();) {int end=i+1;float net=events.get(i).value();while(end<events.size()&&events.get(end).position()==events.get(i).position()){net+=events.get(end).value();finite(net);end++;}positions.add(new Event(events.get(i).position(),0,net));i=end;}
        var bins=new ArrayList<Bin>();float running=0;int overruns=0;
        for(int i=0;i<positions.size();i++) {if((i&4095)==0)check.run();var e=positions.get(i);running+=e.value();finite(running);if(i==positions.size()-1)break;long start=Math.max(0,e.position()),end=positions.get(i+1).position();if((running>0.05||running< -0.05)&&start<end){if(end>referenceLength)overruns++;bins.add(new Bin(start,end,running,printed(running)));}}
        check.run();return new Replay(bins,tags.size(),selected,positions.size(),tied,mixed,overruns,running);
    }
    /** Exact float32-to-two-decimal nearest/even rendering, matching C printf's default rounding. */
    private static String printed(float value){return new BigDecimal((double)value).setScale(2,RoundingMode.HALF_EVEN).toPlainString();}
    private static void finite(float value)throws IOException{if(!Float.isFinite(value))throw new IOException("Nonfinite source-float32 coverage arithmetic");}
}
