/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.util.List;
import org.jlinalg.genetics.FumaLocusDefinition;
import org.jlinalg.genetics.HardcallLd;

public final class Example {
    private Example() { }
    public static void main(String[] args){
        var frame=HardcallLd.frame("synthetic-four-diploid-samples",4,new int[]{0,1,2,3},HardcallLd.Limits.defaults(),()->{});
        // BIM A1 counts [2,1,0,missing] and [0,1,2,missing].
        var first=HardcallLd.encode(frame,new byte[]{0x78},()->{});
        var second=HardcallLd.encode(frame,new byte[]{0x4b},()->{});
        var score=HardcallLd.pair(first,second,()->{});
        if(score.pairedCalls()!=3||score.correlation()!=-1||score.rSquared()!=1)throw new AssertionError("Unexpected complete pair");
        System.out.println("A1 frequencies: "+first.frequencies());
        System.out.println("Paired calls="+score.pairedCalls()+", r="+score.correlation()+", r2="+score.rSquared());
        var a=new FumaLocusDefinition.ReferenceVariant(1,100,"A","G","first",.5);
        var b=new FumaLocusDefinition.ReferenceVariant(1,1100,"A","G","second",.5);
        var c=new FumaLocusDefinition.ReferenceVariant(1,1101,"A","G","third",.5);
        var reference=new FumaLocusDefinition.Reference("synthetic-window",List.of(a,b,c),
                List.of(new FumaLocusDefinition.LdPair(1,100,1100,1),new FumaLocusDefinition.LdPair(1,1100,1101,1)),
                .05,new FumaLocusDefinition.LdWindow(1000,1));
        if(reference.coverage(a.uid(),c.uid())!=FumaLocusDefinition.PairCoverage.OUTSIDE_DECLARED_WINDOW)throw new AssertionError("Window was lost");
        System.out.println("100 to1100: "+reference.coverage(a.uid(),b.uid()));
        System.out.println("100 to1101: "+reference.coverage(a.uid(),c.uid()));
        System.out.println("Outside-window LD is uncomputed; it is not an observed zero or weak correlation.");
    }
}
