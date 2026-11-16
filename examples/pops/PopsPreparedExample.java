/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.util.Set;
import org.jlinalg.compute.BackendPolicy;
import org.jlinalg.genetics.Pops;

/** Small finite prepared-matrix example; no patient data or external PoPS runtime. */
public final class PopsPreparedExample {
    public static void main(String[] args) {
        double[][] features = {{-1, 0}, {0, 1}, {1, 0}, {2, 1}, {3, -1}, {4, 1}, {5, 0}, {6, -1}};
        double[] target = {-1.2, .1, 1.1, 2.3, 2.8, 4.2, 5.1, 6.2};
        String[] chromosome = {"1", "1", "1", "2", "2", "2", "6", "6"};
        long[] tss = {100, 200, 300, 100, 200, 300, 19000000, 30000000};
        boolean[] eligible = Pops.geneMask(chromosome, tss, Set.of("1", "2", "6"), true);
        // Separate masks may be supplied for target projection, selection and training.
        // This synthetic example explicitly supplies no target covariates or error covariance.
        var model = Pops.fit(target, features, null, null, eligible, eligible, eligible,
            Pops.Selection.defaults(), Pops.Method.RIDGE, BackendPolicy.CPU);
        System.out.println("Synthetic example: selected alpha=" + model.selectedAlpha());
        double[] scores = model.predictions();
        for (int i = 0; i < scores.length; i++) System.out.println("gene_ordinal_" + (i + 1) + "\t" + scores[i]);
    }
}
