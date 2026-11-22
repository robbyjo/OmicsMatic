import org.jlinalg.genetics.HaplotypeDosage;

/** Synthetic supplied probabilities; this example does not phase or impute genotypes. */
public final class HaplotypeDosageExample {
    public static void main(String[] args) {
        var sample = HaplotypeDosage.posterior(.25, .75);
        System.out.println("GP="+sample.genotypeProbabilities());
        System.out.println("DS="+sample.dosage()+"; variance="+sample.variance());
        System.out.println("copy calls="+sample.phasedCalls());
        var cohort = HaplotypeDosage.summarize(new double[][]{{.1, .2}, {.8, .9}});
        System.out.println("AF="+cohort.alleleFrequency());
        System.out.println("haplotype variance R2="+cohort.estimatedHaplotypeR2().orElseThrow());
        var monomorphic = HaplotypeDosage.summarize(new double[][]{{0}, {0, 0}});
        System.out.println("monomorphic R2 defined="+monomorphic.estimatedHaplotypeR2().isPresent());
    }
}
