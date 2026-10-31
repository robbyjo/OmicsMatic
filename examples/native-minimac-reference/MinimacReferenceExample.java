import java.nio.file.Path;
import org.jlinalg.genetics.MinimacReferenceAudit;
import org.jlinalg.genetics.VcfCsiIndex;

/** Read an original exported reference and optionally create a new target CSI. */
public final class MinimacReferenceExample {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 && args.length != 3) {
            throw new IllegalArgumentException("Usage: MinimacReferenceExample reference.mvcf [target.vcf.gz NEW-target.vcf.gz.csi]");
        }
        var summary = MinimacReferenceAudit.audit(Path.of(args[0]));
        System.out.println("Reference samples=" + summary.samples().size() + ", contigs="
                + summary.contigs().size() + ", blocks=" + summary.blocks() + ", variants=" + summary.variants());
        System.out.println("Identity/phase SHA-256=" + summary.phasedContentSha256());
        for (var contig : summary.contigs()) {
            System.out.println("Contig " + contig.id() + ": ordered sample ploidies=" + contig.samplePloidies());
        }
        if (args.length == 3) {
            var index = VcfCsiIndex.create(Path.of(args[1]), Path.of(args[2]));
            System.out.println("New CSI contigs=" + index.contigs() + ", bins=" + index.bins() + ", chunks=" + index.chunks());
        }
        System.out.println("WARNING: These native helpers do not run imputation or certify assembly, reference-panel identity, allele alignment or target phasing.");
    }
}
