/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.util.Locale;
import org.jlinalg.genetics.AncestryMixtureLikelihood;

/** Standalone native arithmetic example; no inference or external runtime. */
public final class NativeLikelihood {
    private NativeLikelihood() { }
    public static void main(String[] args) {
        var evaluator=new AncestryMixtureLikelihood.Evaluator(new double[]{.2,.8,.7,.3},2,2,0,false);
        evaluator.addMarker(new double[]{0,2},new double[]{.1,.9},2,0);
        var report=evaluator.summary(0,.1);
        System.out.printf(Locale.ROOT,"observed_cells=%d%ncounted_alleles=%d%nlog_likelihood=%.12f%n",
                report.observedCells(),report.countedAlleles(),report.logLikelihood());
    }
}
