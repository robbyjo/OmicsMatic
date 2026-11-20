import org.jlinalg.enrichment.BroadGseaStatistics;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Source-counterpart arithmetic checks and explicit no-repair admission boundaries. */
public final class CheckNativeBroadPrimitives {
    private static long comparisons;
    private static int walks, families, walkRefusals, familyRefusals;
    public static void main(String[] args) throws Exception {
        for (String name : args) {
            for (String line : Files.readAllLines(Path.of(name))) {
                String[] f = line.split("\t", -1);
                if (f[0].equals("WALK")) walk(f, name);
                if (f[0].equals("FAMILY")) family(f, name);
            }
        }
        System.out.println("PASS_NATIVE_BROAD_PRIMITIVES\tcomparisons=" + comparisons + "\twalks=" + walks
                + "\tfamilies=" + families + "\twalkRefusals=" + walkRefusals + "\tfamilyRefusals=" + familyRefusals);
    }
    private static void walk(String[] f, String path) {
        String label = path + "/walk/" + f[1] + "/" + f[2];
        float[] ranks = floats(f[3]), weights = floats(f[11]); int[] members = ints(f[4]);
        double mass = 0;
        for (float value : weights) mass += value;
        boolean admitted = Double.isFinite(mass) && Math.abs(mass - 1) <= 5e-6;
        try {
            var result = BroadGseaStatistics.score(ranks, members, BroadGseaStatistics.Weight.valueOf(f[2]));
            if (!admitted) throw new AssertionError(label + " source has invalid/substituted hit mass " + mass);
            equal(Float.parseFloat(f[5]), result.es(), label + "/ES");
            equal(Float.parseFloat(f[6]), result.rankAtEs(), label + "/rank");
            equal(Float.parseFloat(f[7]), result.rankScoreAtEs(), label + "/rankScore");
            int[] hits = ints(f[8]);
            if (!Arrays.equals(hits, result.hits())) throw new AssertionError(label + "/hitIndices");
            comparisons += hits.length;
            array(floats(f[9]), result.hitProfile(), label + "/hitProfile");
            array(floats(f[10]), result.fullProfile(), label + "/fullProfile");
            walks++;
        } catch (ArithmeticException error) {
            if (admitted) throw new AssertionError(label + " original finite normalized hit mass=" + mass, error);
            walkRefusals++;
        }
    }
    private static void family(String[] f, String path) {
        String label = path + "/family/" + f[1];
        float[] observed = floats(f[2]); float[][] random = matrix(f[3]);
        boolean admitted = f[4].equals("FINITE");
        try {
            var result = BroadGseaStatistics.meandiv(observed, random, 10_000_000L);
            if (!admitted) throw new AssertionError(label + " original has no finite complete result: " + f[4]);
            array(floats(f[5]), result.nes(), label + "/NES");
            array(floats(f[6]), result.nominalP(), label + "/NP");
            array(floats(f[7]), result.fdr(), label + "/FDR");
            array(floats(f[8]), result.fwer(), label + "/FWER");
            float[][] expected = matrix(f[9]), actual = result.normalizedNull();
            if (expected.length != actual.length) throw new AssertionError(label + "/normalizedNull row count");
            for (int r = 0; r < expected.length; r++) array(expected[r], actual[r], label + "/normalizedNull/" + r);
            families++;
        } catch (IllegalArgumentException | ArithmeticException error) {
            if (admitted) throw new AssertionError(label + " original finite result was refused", error);
            familyRefusals++;
        }
    }
    private static float[] floats(String text) {
        if (text.isEmpty()) return new float[0];
        String[] fields = text.split(",", -1); float[] result = new float[fields.length];
        for (int i = 0; i < result.length; i++) result[i] = Float.parseFloat(fields[i]);
        return result;
    }
    private static int[] ints(String text) {
        String[] fields = text.split(",", -1); int[] result = new int[fields.length];
        for (int i = 0; i < result.length; i++) result[i] = Integer.parseInt(fields[i]);
        return result;
    }
    private static float[][] matrix(String text) {
        String[] rows = text.split(";", -1); float[][] result = new float[rows.length][];
        for (int i = 0; i < rows.length; i++) result[i] = floats(rows[i]);
        return result;
    }
    private static void array(float[] expected, float[] actual, String label) {
        if (expected.length != actual.length) throw new AssertionError(label + " length");
        for (int i = 0; i < actual.length; i++) equal(expected[i], actual[i], label + "/" + i);
    }
    private static void equal(float expected, float actual, String label) {
        comparisons++;
        if (Float.floatToIntBits(expected) != Float.floatToIntBits(actual))
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual
                    + " expectedBits=" + Integer.toHexString(Float.floatToIntBits(expected))
                    + " actualBits=" + Integer.toHexString(Float.floatToIntBits(actual)));
    }
}
