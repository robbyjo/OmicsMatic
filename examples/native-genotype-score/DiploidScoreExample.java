import java.nio.file.Path;
import java.util.Set;
import org.jlinalg.genetics.DiploidScores;
import org.jlinalg.genetics.PlinkBedDataset;

/** Minimal supplied-weight scoring; the optional BED argument uses the verifier's synthetic fixture. */
public final class DiploidScoreExample {
    private DiploidScoreExample() { }

    public static void main(String[] args) throws Exception {
        var mean = DiploidScores.accumulator(3, DiploidScores.MissingPolicy.MEAN_IMPUTE);
        mean.add(new double[] {2, Double.NaN, 0}, 0.5, 0.25);
        mean.add(new double[] {Double.NaN, 1, 2}, -0.25, 0.75);
        double[] expected = {0.625, 0, -0.5};
        for (int i = 0; i < expected.length; i++) {
            var score = mean.score(i);
            if (score.sum() != expected[i] || score.average() != expected[i] / 4
                    || score.denominatorAlleles() != 4)
                throw new AssertionError(score);
            System.out.println("sample=" + i + " sum=" + score.sum()
                    + " average=" + score.average() + " observedAlleles="
                    + score.observedAlleles() + " status=" + score.status());
        }
        var omit = DiploidScores.accumulator(1, DiploidScores.MissingPolicy.OMIT);
        omit.add(new double[] {Double.NaN}, 0.5, Double.NaN);
        if (!Double.isNaN(omit.score(0).sum())
                || omit.score(0).status() != DiploidScores.Status.NO_OBSERVED_DOSAGES)
            throw new AssertionError(omit.score(0));
        if (args.length == 1) {
            try (var bed = PlinkBedDataset.open(Path.of(args[0]), Set.of("rs10000", "rs10001"))) {
                if (bed.samples().size() != 13 || bed.variantCount() != 8)
                    throw new AssertionError("Expected the packaged verifier's complete fixture");
                for (var variant : bed.selectedVariants()) {
                    double[] a1 = bed.readAllele1Dosages(variant);
                    if (a1.length != 13) throw new AssertionError("Sample order/size");
                    for (double dosage : a1)
                        if (dosage != 0 && dosage != 1 && dosage != 2)
                            throw new AssertionError("Expected complete diploid hard calls");
                }
            }
        }
        System.out.println("Native score and BED API example passed");
    }
}
