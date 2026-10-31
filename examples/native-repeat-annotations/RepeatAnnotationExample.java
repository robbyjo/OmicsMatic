import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jlinalg.genetics.RepeatAnnotations;

/** Controlled annotation arithmetic; this example performs no repeat search. */
public final class RepeatAnnotationExample {
    private RepeatAnnotationExample() { }
    public static void main(String[] args) throws Exception {
        var limits = new RepeatAnnotations.Limits(1000000, 1000, 10000, 10000000);
        RepeatAnnotations.Table table;
        try (var reader = Files.newBufferedReader(Path.of(args[0]), StandardCharsets.UTF_8)) {
            table = RepeatAnnotations.read(reader, "controlled-OUT", RepeatAnnotations.Format.OUT_WITH_ID, limits);
        }
        // Explicitly supplied fragments of one insertion; no cross-file ID inference.
        var query = RepeatAnnotations.coverage(table.hits(), "Q", 0, 100);
        var element = RepeatAnnotations.consensusCoverage(table.hits());
        boolean complete = RepeatAnnotations.meetsFraction(element.coveredBases(), element.denominatorBases(), 3, 4, true);
        var masked = RepeatAnnotations.mask("ACGT".repeat(25), "Q", table.hits(), RepeatAnnotations.Mask.SOFT);
        System.out.println("query_covered_bases=" + query.coveredBases());
        System.out.println("consensus_covered_bases=" + element.coveredBases());
        System.out.println("consensus_at_least_75_percent=" + complete);
        System.out.println("masked_bases=" + masked.coveredBases());
        System.out.println("masked_sequence=" + masked.sequence());
    }
}
