/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.util.Locale;
import org.jlinalg.genetics.SmartpcaPrepared;

/** Small prepared-panel API example; synthetic hard calls, not ancestry evidence. */
public final class PreparedExample {
    private PreparedExample() { }
    public static void main(String[] arguments) {
        byte[][] markerBySample = {
            {0,1,2,0,2}, {2,0,1,2,0}, {1,2,0,0,1},
            {0,0,2,1,9}, {2,1,0,1,2}, {1,0,1,2,0}
        };
        // Samples 0..3 define the axes and allele frequencies; sample4 is projected.
        int[] fittingSamples = {0,1,2,3};
        var options = SmartpcaPrepared.Options.prepared(2,
            SmartpcaPrepared.Projection.LEAST_SQUARES);
        var result = SmartpcaPrepared.fit(markerBySample, fittingSamples, options);
        double[][] coordinates = result.scores();
        System.out.println("sample\tstatus\tPC1\tPC2");
        for (int sample = 0; sample < coordinates.length; sample++) {
            if (result.scoreStatus().get(sample) != SmartpcaPrepared.ScoreStatus.COMPUTED)
                throw new IllegalStateException("Read the score status; an unreportable sample is not a zero coordinate");
            System.out.printf(Locale.ROOT,"sample%d\t%s\t%.9g\t%.9g%n",sample,
                result.scoreStatus().get(sample),coordinates[sample][0],coordinates[sample][1]);
        }
    }
}
