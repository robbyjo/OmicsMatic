/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.util.Locale;
import org.jlinalg.genetics.PrsiceHardCallScores;
import org.jlinalg.genetics.PrsiceHardCallScores.*;

/** Artificial 12-sample fixture; runs the packaged API without an upstream runtime. */
public final class ScoreProbe {
    public static void main(String[] args) {
        int[][] calls = {
            {2,2,1,0,-1,0,1,2,1,2,0,1}, {0,0,1,2,2,1,-1,1,0,2,1,0},
            {0,1,2,2,0,0,1,-1,2,1,0,2}, {2,2,2,2,2,2,2,2,2,2,2,2},
            {1,1,1,1,1,1,1,1,1,1,1,1}, {0,2,1,0,1,2,1,0,2,1,0,2}
        };
        double[] effects = {.1,-.2,.3,-.4,.5,.125};
        boolean[] flip = {false,true,false,false,true,false};
        boolean[] founders = {true,true,true,true,true,true,true,true,true,true,true,false};
        boolean[] eligible = {true,true,true,true,true,true,true,true,true,true,true,true};
        boolean[] controls = {true,false,true,false,true,false,true,false,true,false,true,false};
        System.out.println("model\tmissing\tscaling\tstatus\tsample\tscore\traw_sum\tallele_count\tmean\tsample_sd\tstandardization_samples");
        for (Model model : Model.values()) for (Missing missing : Missing.values()) for (Scaling scaling : Scaling.values()) {
            var result = PrsiceHardCallScores.score(calls,effects,flip,founders,eligible,controls,model,missing,scaling);
            double[] values=result.scores(),sums=result.rawSums();long[] counts=result.alleleCounts();
            for (int i=0;i<values.length;i++) System.out.printf(Locale.ROOT,"%s\t%s\t%s\t%s\tS%02d\t%s\t%s\t%d\t%s\t%s\t%d%n",
                    model,missing,scaling,result.status(),i,Double.toString(values[i]),Double.toString(sums[i]),counts[i],
                    Double.toString(result.standardizationMean()),Double.toString(result.standardizationSd()),result.standardizationSamples());
        }
    }
}
