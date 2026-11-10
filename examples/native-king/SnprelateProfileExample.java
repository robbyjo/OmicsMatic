/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.util.List;
import org.jlinalg.genetics.KingRobustKinship;
import org.jlinalg.genetics.SnprelateKingRobust;
import org.jlinalg.genetics.SnprelateVariantQc;

/** Run with the standalone native API JAR; no R or upstream runtime is needed. */
public final class SnprelateProfileExample {
    private SnprelateProfileExample() { }
    public static void main(String[] args) {
        var counter=new KingRobustKinship.Accumulator(2,List.of(new KingRobustKinship.Pair(0,1)),3,100000);
        for(byte[] calls:new byte[][]{{1,0},{1,0},{-1,2}})counter.accept(calls);
        counter.finish();var counts=counter.estimate(0).counts();
        for(var context:SnprelateKingRobust.Context.values()) {
            var result=SnprelateKingRobust.estimate(counts,context);
            System.out.println(context+" kinship="+result.kinship()+" raw="+result.rawKinship()+" IBS0="+result.ibs0()+" disposition="+result.disposition());
            if(result.sourceNonfiniteNormalization())System.out.println("WARNING: Uninformative chosen denominator. The SNPRelate profile reports NaN; do not interpret or replace it.");
        }
        var dosage=new SnprelateVariantQc.Counts(10,9,4);
        var filters=new SnprelateVariantQc.Filters(false,Double.NaN,0.09999999999999999);
        for(var layout:SnprelateVariantQc.Layout.values()) {
            var statistics=SnprelateVariantQc.statistics(dosage,layout);
            System.out.println(layout+" missing_rate="+Double.toHexString(statistics.missingRate())+" selection="+SnprelateVariantQc.decide(statistics,filters));
        }
        System.out.println("Scalar/QC demonstration only. Declare family context, sample/variant population and layout explicitly for real inputs.");
    }
}
