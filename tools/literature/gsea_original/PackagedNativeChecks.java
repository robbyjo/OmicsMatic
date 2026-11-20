import java.nio.file.Files;
import java.nio.file.Path;
import org.jlinalg.enrichment.BroadGseaStatistics;

/** Require the production class to come from the named normal JAR, with no upstream GSEA classes. */
public final class PackagedNativeChecks {
    public static void main(String[] args) throws Exception {
        Path expected = Path.of(System.getProperty("omicsmatic.expected.jar"));
        Path actual = Path.of(BroadGseaStatistics.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (!Files.isSameFile(expected, actual)) throw new AssertionError("Native class did not load from the exact normal producer");
        try {
            Class.forName("edu.mit.broad.genome.alg.gsea.KSCore");
            throw new AssertionError("Original GSEA classes must be absent from native qualification classpath");
        } catch (ClassNotFoundException expectedAbsence) { }
        int split = java.util.Arrays.asList(args).indexOf("--primitives");
        if (split < 1) throw new IllegalArgumentException("Original full API paths --primitives original primitive paths");
        CheckNativeBroadGsea.main(java.util.Arrays.copyOfRange(args, 0, split));
        CheckNativeBroadPrimitives.main(java.util.Arrays.copyOfRange(args, split + 1, args.length));
        System.out.println("PASS_EXACT_NORMAL_JAR_NATIVE_ONLY_SOURCE_ORIGIN\t" + actual);
    }
}
