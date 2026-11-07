// Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
import java.util.List;
import org.jlinalg.sequence.HtseqFeatureAssignment.Feature;
import org.jlinalg.sequence.HtseqFeatureAssignment.Interval;
import org.jlinalg.sequence.HtseqFeatureAssignment.Mode;
import org.jlinalg.sequence.HtseqReadCounter;
import org.jlinalg.sequence.HtseqReadCounter.*;

/** Synthetic already-parsed reads; SAM/GTF parsing and pairing are separate. */
public final class CountPreparedReads {
    private CountPreparedReads() { }
    private static Interval iv(String chromosome,long start,long end,char strand) { return new Interval(chromosome,start,end,strand); }
    private static Alignment read(boolean aligned,int mapq,Integer nh,Interval...blocks) { return new Alignment(aligned,false,false,mapq,nh,List.of(blocks)); }
    public static void main(String[] args) {
        var features=List.of(new Feature(iv("chr1",0,100,'+'),"A"),new Feature(iv("chr1",20,40,'+'),"B"));
        var units=List.of(new Unit("unique",Layout.SINGLE,read(true,60,null,iv("chr1",0,5,'+')),null),
            new Unit("overlap",Layout.SINGLE,read(true,60,1,iv("chr1",20,25,'+')),null),
            new Unit("low-multi",Layout.SINGLE,read(true,0,2,iv("chr1",0,5,'+')),null),
            new Unit("unknown-chromosome",Layout.SINGLE,read(true,60,1,iv("absent",0,5,'+')),null),
            new Unit("unaligned",Layout.SINGLE,read(false,0,null),null));
        var none=Config.source212Defaults(Layout.SINGLE);var model=HtseqReadCounter.prepare(features,none);
        var noneCounts=HtseqReadCounter.count(model,units,none,100);
        if(noneCounts.counts().get("A")!=1.0 || noneCounts.counts().get("B")!=0.0)throw new AssertionError("NONE counts");
        System.out.println("NONE A="+noneCounts.counts().get("A")+" B="+noneCounts.counts().get("B"));
        var fraction=new Config(Layout.SINGLE,Strand.YES,Mode.UNION,Nonunique.FRACTION,10,FlagPolicy.IGNORE,FlagPolicy.IGNORE);
        var fractionCounts=HtseqReadCounter.count(model,units,fraction,100);
        if(fractionCounts.counts().get("A")!=1.5 || fractionCounts.counts().get("B")!=0.5)throw new AssertionError("FRACTION counts");
        var stats=fractionCounts.statistics();
        if(stats.noFeature()!=1 || stats.ambiguous()!=1 || stats.tooLowAQual()!=1 || stats.notAligned()!=1 || stats.alignmentNotUnique()!=1)throw new AssertionError("Source summary counters");
        System.out.println("FRACTION A="+fractionCounts.counts().get("A")+" B="+fractionCounts.counts().get("B"));
        System.out.println("FRACTION summary no_feature=1 ambiguous=1 too_low_aQual=1 not_aligned=1 alignment_not_unique=1");
        var paired=Config.source212Defaults(Layout.PAIRED);var pairedModel=HtseqReadCounter.prepare(features,paired);
        var first=read(true,60,null,iv("chr1",0,5,'+'));var second=read(true,60,2,iv("chr1",50,55,'-'));
        var decision=HtseqReadCounter.assess(pairedModel,new Unit("missing-first-NH",Layout.PAIRED,first,second),paired);
        if(decision.nhCheck()!=NhCheck.FIRST_MATE_MISSING_TAG_ABORTED || decision.alignmentNotUnique())throw new AssertionError("Original NH short circuit");
        System.out.println("PAIRED missing-first-NH "+decision.nhCheck()+" "+decision.state());
        try{HtseqReadCounter.count(model,units,none,1);throw new AssertionError("Exceeded unit bound admitted");}
        catch(IllegalArgumentException expected){System.out.println("unit-capacity REFUSED");}
    }
}
