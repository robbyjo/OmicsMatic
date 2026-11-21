/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
import java.util.Locale;
import org.jlinalg.genetics.MtcojoConditional;

/** Synthetic prepared parameters only: no GSMR, LDSC or prevalence fitting. */
public final class MtcojoConditionalExample {
    public static void main(String[] arguments) {
        if(arguments.length!=0)throw new IllegalArgumentException("This synthetic example takes no arguments");
        try {
            double[] targetEffects={.10,-.20},targetSE={.02,.03};
            var single=MtcojoConditional.single(targetEffects,targetSE,
                new double[]{.05,.10},new double[]{.01,.02},.40,.10);
            // Covariate genetic variances are h2 * phenotypic variance, not h2 alone.
            // The prepared genetic correlation matrix has unit diagonal.
            var multiple=MtcojoConditional.multiple(targetEffects,targetSE,
                new double[][]{{.05,-.02},{.10,.03}},
                new double[][]{{.01,.015},{.02,.012}},
                new double[]{.40,-.25},new double[]{.40,.60},
                new double[][]{{1,.20},{.20,1}},
                new double[][]{{1,.10,.05},{.10,1,.15},{.05,.15,1}});
            // Emit only after both requested calculations have completed.
            System.out.println("scope\tvariant\tb_conditional\tse_conditional\tp_conditional");
            print("single",single);print("multiple",multiple);
            double[] joint=multiple.jointCausalEffects();
            System.out.printf(Locale.ROOT,"# multiple joint causal effects: %.17g %.17g%n",joint[0],joint[1]);
        } catch(RuntimeException failure) {
            System.err.println("FAIL2: NO COMPLETE RESULT. Preserve prepared inputs and correct the reported cause: "+failure);
            System.exit(2);
        }
    }
    private static void print(String scope,MtcojoConditional.Result result) {
        for(int i=0;i<result.size();i++)System.out.printf(Locale.ROOT,"%s\trsSynthetic%d\t%.17g\t%.17g\t%.17g%n",
            scope,i+1,result.effect(i),result.standardError(i),result.pValue(i));
    }
}
