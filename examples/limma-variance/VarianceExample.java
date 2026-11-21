/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import org.jlinalg.differential.LimmaVarianceModeration;

/** Prepared variances only; explicit legacy, untrended and nonrobust estimation. */
public final class VarianceExample {
    private VarianceExample() { }
    public static void main(String[] args) {
        var result = LimmaVarianceModeration.squeezeLegacy(
            new double[]{0,1,2,4,9},new double[]{7});
        for (String warning : result.warnings()) System.err.println("WARNING: " + warning);
        if (!result.finite()) {
            System.err.println("FAIL2: NO COMPLETE RESULT. Preserve the input and diagnostics; inspect the named variance/df cause and retry new output.");
            System.exit(2);
        }
        System.out.println("prior_scale\tprior_df");
        System.out.println(result.priorVariances()[0] + "\t" + result.priorDegreesOfFreedom());
        System.out.println("feature\tposterior_variance");
        double[] post = result.posteriorVariances();
        for (int i=0;i<post.length;i++) System.out.println((i+1)+"\t"+post[i]);
    }
}
