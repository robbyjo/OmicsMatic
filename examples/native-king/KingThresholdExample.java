/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import org.jlinalg.genetics.KingKinshipThreshold;
import org.jlinalg.genetics.KingRobustKinship;

/** Fresh external consumer of the exact decimal comparison API; no KING runtime is required. */
public final class KingThresholdExample {
    private KingThresholdExample() { }
    public static void main(String[] args) {
        if(args.length!=0)throw new IllegalArgumentException("This example uses explicitly declared count controls, without input transformations");
        var counts=new KingRobustKinship.Counts(1200,625,200,229,0);
        var comparison=KingKinshipThreshold.evaluate(counts,KingKinshipThreshold.Formula.BETWEEN_MINIMUM,"0.0884");
        System.out.println(comparison.numerator()+"/"+comparison.denominator()+" "+comparison.comparison()
                +" strict="+comparison.passes(KingKinshipThreshold.Rule.GREATER_THAN)
                +" inclusive="+comparison.passes(KingKinshipThreshold.Rule.GREATER_THAN_OR_EQUAL));
        System.err.println("Use complete independently counted hard calls and an explicit formula/rule. This API does not repair original source results or choose participants for removal. Unassessable pairs cannot become unrelatedness decisions.");
    }
}
