import java.nio.file.Path;
import java.util.Arrays;
import org.jlinalg.enrichment.BroadGseaEngine;
import org.jlinalg.enrichment.BroadGseaStatistics;

/** Requires production classes to load from the exact normal executable JAR. */
public final class PackagedEngineChecks {
    public static void main(String[] args) throws Exception {
        Path expected = Path.of(System.getProperty("omicsmatic.expected.jar")).toRealPath();
        for (Class<?> type : new Class<?>[]{BroadGseaEngine.class, BroadGseaStatistics.class}) {
            Path actual = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            if (!expected.equals(actual)) throw new AssertionError("Unexpected production class origin: " + actual);
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        if (args[0].equals("sweep")) CheckEngineSweep.main(rest);
        else if (args[0].equals("complete-nulls")) CheckNativeEngine.main(rest);
        else throw new IllegalArgumentException("Choose sweep or complete-nulls");
        System.out.println("PASS_EXACT_NORMAL_JAR_NATIVE_ENGINE_SOURCE_ORIGIN");
    }
}
