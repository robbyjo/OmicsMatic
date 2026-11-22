import java.nio.file.Path;
import java.util.ArrayList;
import org.jlinalg.genetics.MinimacReferenceBlocks;

/** Complete-file audit with a deliberately explicit shared-boundary policy. */
public final class MinimacBlocksExample {
    private MinimacBlocksExample() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException(
                "Usage: MinimacBlocksExample REFERENCE_TEXT BOUNDARY_POLICY; "
                + "REJECT_DUPLICATES, VERIFY_SHARED_KEEP_LATER or REQUIRE_SHARED_KEEP_LATER");
        var policy = MinimacReferenceBlocks.BoundaryPolicy.valueOf(args[1]);
        var notices = new ArrayList<String>();
        var summary = MinimacReferenceBlocks.read(Path.of(args[0]), policy,
                new MinimacReferenceBlocks.Visitor() {
                    public void selected(MinimacReferenceBlocks.Row row) { }
                    public void boundary(MinimacReferenceBlocks.Boundary event) {
                        if (notices.size() < 12) notices.add("Shared boundary at "
                                + event.later().block().contig() + ":" + event.later().position()
                                + "; ID changed=" + event.idChanged()
                                + "; model parameters changed=" + event.parametersChanged()
                                + "; retained later row, source line " + event.later().sourceLine());
                    }
                });
        // All visitor data was provisional until read() returned successfully.
        System.out.println("Complete audit passed: " + summary.format()
                + ", physical rows=" + summary.physicalRows()
                + ", selected markers=" + summary.selectedVariants()
                + ", shared boundaries=" + summary.sharedBoundaries()
                + ", ordered haplotype alleles=" + summary.selectedHaplotypeCells());
        System.out.println("Column semantics: " + summary.columnKind()
                + "; no original person/copy identities inferred from names.");
        for (String notice : notices) System.out.println(notice);
        System.out.println("Identity/phase SHA256: " + summary.identityAndPhaseSha256());
        System.out.println("Ordered matrix SHA256: " + summary.orderedHaplotypeMatrixSha256());
        System.out.println("Binary32 model SHA256: " + summary.float32ModelParametersSha256());
        System.out.println("REVIEW REQUIRED: preserve input hashes and both boundary rows. "
                + "Matrix equality alone does not prove sample identity. This audit does not impute.");
    }
}
